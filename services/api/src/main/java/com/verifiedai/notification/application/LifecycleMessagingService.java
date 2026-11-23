package com.verifiedai.notification.application;

import com.verifiedai.notification.domain.LifecycleEvaluationDecision;
import com.verifiedai.notification.domain.LifecycleTemplateRegistry;
import com.verifiedai.notification.domain.NotificationCategory;
import com.verifiedai.notification.domain.NotificationDeliveryStatus;
import com.verifiedai.notification.domain.NotificationIntentType;
import com.verifiedai.notification.domain.NotificationPayloadHasher;
import com.verifiedai.notification.domain.NotificationSuppressionReason;
import com.verifiedai.notification.infrastructure.LifecycleMessagingJdbcRepository;
import com.verifiedai.notification.infrastructure.LifecycleMessagingJdbcRepository.CooldownRecord;
import com.verifiedai.notification.infrastructure.LifecycleMessagingJdbcRepository.EvaluationRecord;
import com.verifiedai.notification.infrastructure.NotificationDeliveryJdbcRepository;
import com.verifiedai.notification.infrastructure.NotificationDeliveryJdbcRepository.DeliveryRecord;
import com.verifiedai.notification.infrastructure.NotificationDeviceJdbcRepository;
import com.verifiedai.notification.infrastructure.NotificationDeviceJdbcRepository.DeviceSummaryRow;
import com.verifiedai.notification.infrastructure.NotificationPreferenceJdbcRepository;
import com.verifiedai.notification.infrastructure.NotificationProperties;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.sharedkernel.observability.CorrelationIds;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Orchestration service implementing {@link LifecycleMessagingPort} with advisory-locked cooldowns,
 * strict template allowlist validation, obsolete delivery cancellation, and fail-closed feature gating.
 */
@Service
public class LifecycleMessagingService implements LifecycleMessagingPort {

    public static final String SUMMARY_SCHEMA_VERSION = "lifecycle-summary-v1";
    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9:._-]{8,128}$");
    private static final Set<String> ALLOWED_SOURCE_MODULES =
        Set.of("notification", "billing", "studyplan", "exam", "identity");

    private final NotificationSchedulingPort schedulingPort;
    private final NotificationDeliveryJdbcRepository deliveryRepository;
    private final NotificationPreferenceJdbcRepository preferenceRepository;
    private final NotificationDeviceJdbcRepository deviceRepository;
    private final LifecycleMessagingJdbcRepository lifecycleRepository;
    private final NotificationProperties properties;
    private final NotificationMetrics metrics;
    private final Clock clock;
    private final TransactionTemplate requiresNewTx;

    public LifecycleMessagingService(
        NotificationSchedulingPort schedulingPort,
        NotificationDeliveryJdbcRepository deliveryRepository,
        NotificationPreferenceJdbcRepository preferenceRepository,
        NotificationDeviceJdbcRepository deviceRepository,
        LifecycleMessagingJdbcRepository lifecycleRepository,
        NotificationProperties properties,
        NotificationMetrics metrics,
        Clock clock,
        PlatformTransactionManager transactionManager
    ) {
        this.schedulingPort = Objects.requireNonNull(schedulingPort, "schedulingPort must not be null");
        this.deliveryRepository = Objects.requireNonNull(deliveryRepository, "deliveryRepository must not be null");
        this.preferenceRepository = Objects.requireNonNull(preferenceRepository, "preferenceRepository must not be null");
        this.deviceRepository = Objects.requireNonNull(deviceRepository, "deviceRepository must not be null");
        this.lifecycleRepository = Objects.requireNonNull(lifecycleRepository, "lifecycleRepository must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        TransactionTemplate tx = new TransactionTemplate(
            Objects.requireNonNull(transactionManager, "transactionManager must not be null")
        );
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.requiresNewTx = tx;
    }

    @Override
    public LifecycleEvaluationResult evaluateAndSchedule(LifecycleTriggerCommand command) {
        if (command == null
            || command.userId() == null
            || command.scheduledFor() == null
            || command.expiresAt() == null) {
            throw validationFailed("Lifecycle trigger command is missing required fields");
        }
        NotificationCategory category = parseCategory(command.category());
        NotificationIntentType intentType = parseIntentType(command.intentType());
        LifecycleTemplateRegistry.validate(
            category,
            intentType,
            command.templateKey(),
            command.templateParams(),
            command.deepLinkPath()
        );
        if (!command.expiresAt().isAfter(command.scheduledFor())) {
            throw validationFailed("Lifecycle notification expiresAt must be strictly after scheduledFor");
        }
        String correlationId = normalizeCorrelationId(command.correlationId());
        String effectiveLocale = (command.locale() == null || command.locale().isBlank())
            ? resolvePreferredLocale(command.userId())
            : command.locale();

        NotificationPayloadHasher.validateScheduleFields(
            command.idempotencyKey(),
            effectiveLocale,
            command.templateKey(),
            command.templateParams(),
            command.deepLinkPath(),
            command.sourceModule(),
            correlationId
        );

        if (!properties.enabled() || !properties.lifecycleCampaignsEnabled()) {
            Instant now = clock.instant();
            metrics.recordLifecycleEvaluated(category.name(), LifecycleEvaluationDecision.SKIPPED_FEATURE_DISABLED.name());
            return new LifecycleEvaluationResult(
                null,
                command.userId(),
                category.name(),
                intentType.name(),
                command.idempotencyKey(),
                LifecycleEvaluationDecision.SKIPPED_FEATURE_DISABLED.name(),
                "FEATURE_DISABLED",
                null,
                null,
                now,
                false
            );
        }

        return requiresNewTx.execute(status -> {
            lifecycleRepository.lockLifecycleKey(command.userId(), category, intentType);
            Instant now = clock.instant();

            Optional<DeliveryRecord> existingOpt = deliveryRepository.findByUserIdAndIdempotencyKey(
                command.userId(),
                command.idempotencyKey()
            );
            if (existingOpt.isPresent()) {
                ScheduledNotificationResult replayResult = schedulingPort.schedule(ScheduleNotificationCommand.of(
                    command.userId(),
                    command.idempotencyKey(),
                    category.name(),
                    intentType.name(),
                    "PUSH",
                    effectiveLocale,
                    command.templateKey(),
                    command.templateParams(),
                    command.deepLinkPath(),
                    command.scheduledFor(),
                    command.expiresAt(),
                    command.sourceModule(),
                    correlationId
                ));
                UUID evalId = UUID.randomUUID();
                lifecycleRepository.insertEvaluation(new EvaluationRecord(
                    evalId,
                    command.userId(),
                    command.sourceModule(),
                    category,
                    intentType,
                    command.idempotencyKey(),
                    command.templateKey().trim(),
                    LifecycleEvaluationDecision.REPLAYED,
                    "IDEMPOTENT_REPLAY",
                    replayResult.deliveryId(),
                    correlationId,
                    false,
                    now
                ));
                metrics.recordLifecycleEvaluated(category.name(), LifecycleEvaluationDecision.REPLAYED.name());
                return new LifecycleEvaluationResult(
                    evalId,
                    command.userId(),
                    category.name(),
                    intentType.name(),
                    command.idempotencyKey(),
                    LifecycleEvaluationDecision.REPLAYED.name(),
                    "IDEMPOTENT_REPLAY",
                    replayResult.deliveryId(),
                    null,
                    now,
                    false
                );
            }

            if (!category.isSecurityMandatory()) {
                Optional<CooldownRecord> cooldownOpt = lifecycleRepository.findCooldown(
                    command.userId(),
                    category,
                    intentType
                );
                if (cooldownOpt.isPresent() && cooldownOpt.get().cooldownUntil().isAfter(now)) {
                    CooldownRecord activeCooldown = cooldownOpt.get();
                    UUID evalId = UUID.randomUUID();
                    lifecycleRepository.insertEvaluation(new EvaluationRecord(
                        evalId,
                        command.userId(),
                        command.sourceModule(),
                        category,
                        intentType,
                        command.idempotencyKey(),
                        command.templateKey().trim(),
                        LifecycleEvaluationDecision.SKIPPED_COOLDOWN_ACTIVE,
                        "COOLDOWN_WINDOW_ACTIVE",
                        activeCooldown.lastDeliveryId(),
                        correlationId,
                        false,
                        now
                    ));
                    metrics.recordLifecycleEvaluated(
                        category.name(),
                        LifecycleEvaluationDecision.SKIPPED_COOLDOWN_ACTIVE.name()
                    );
                    return new LifecycleEvaluationResult(
                        evalId,
                        command.userId(),
                        category.name(),
                        intentType.name(),
                        command.idempotencyKey(),
                        LifecycleEvaluationDecision.SKIPPED_COOLDOWN_ACTIVE.name(),
                        "COOLDOWN_WINDOW_ACTIVE",
                        activeCooldown.lastDeliveryId(),
                        activeCooldown.cooldownUntil(),
                        now,
                        false
                    );
                }
            }

            ScheduledNotificationResult scheduled = schedulingPort.schedule(ScheduleNotificationCommand.of(
                command.userId(),
                command.idempotencyKey(),
                category.name(),
                intentType.name(),
                "PUSH",
                effectiveLocale,
                command.templateKey(),
                command.templateParams(),
                command.deepLinkPath(),
                command.scheduledFor(),
                command.expiresAt(),
                command.sourceModule(),
                correlationId
            ));

            LifecycleEvaluationDecision decision;
            String reasonCode;
            if (scheduled.replayed()) {
                decision = LifecycleEvaluationDecision.REPLAYED;
                reasonCode = "IDEMPOTENT_REPLAY";
            } else if (scheduled.status() == NotificationDeliveryStatus.SCHEDULED) {
                decision = LifecycleEvaluationDecision.SCHEDULED;
                reasonCode = "SCHEDULED";
            } else if (scheduled.status() == NotificationDeliveryStatus.DEFERRED_QUIET_HOURS) {
                decision = LifecycleEvaluationDecision.DEFERRED_QUIET_HOURS;
                reasonCode = "QUIET_HOURS_DEFERRED";
            } else if (scheduled.suppressionReason() == NotificationSuppressionReason.USER_OPTED_OUT
                || scheduled.suppressionReason() == NotificationSuppressionReason.PUSH_GLOBALLY_DISABLED) {
                decision = LifecycleEvaluationDecision.SKIPPED_OPTED_OUT;
                reasonCode = scheduled.suppressionReason().name();
            } else {
                decision = LifecycleEvaluationDecision.SUPPRESSED;
                reasonCode = scheduled.suppressionReason() == null
                    ? "SUPPRESSED"
                    : scheduled.suppressionReason().name();
            }

            Instant cooldownUntil = null;
            if (scheduled.status() == NotificationDeliveryStatus.SCHEDULED
                || scheduled.status() == NotificationDeliveryStatus.DEFERRED_QUIET_HOURS) {
                int cooldownHours = cooldownHoursFor(category);
                cooldownUntil = now.plus(Duration.ofHours(cooldownHours));
                lifecycleRepository.upsertCooldown(
                    command.userId(),
                    category,
                    intentType,
                    command.idempotencyKey(),
                    scheduled.deliveryId(),
                    now,
                    cooldownUntil,
                    now
                );
            }

            UUID evalId = UUID.randomUUID();
            lifecycleRepository.insertEvaluation(new EvaluationRecord(
                evalId,
                command.userId(),
                command.sourceModule(),
                category,
                intentType,
                command.idempotencyKey(),
                command.templateKey().trim(),
                decision,
                reasonCode,
                scheduled.deliveryId(),
                correlationId,
                false,
                now
            ));
            metrics.recordLifecycleEvaluated(category.name(), decision.name());

            return new LifecycleEvaluationResult(
                evalId,
                command.userId(),
                category.name(),
                intentType.name(),
                command.idempotencyKey(),
                decision.name(),
                reasonCode,
                scheduled.deliveryId(),
                cooldownUntil,
                now,
                false
            );
        });
    }

    @Override
    public LifecycleEvaluationResult recordSkip(LifecycleSkipCommand command) {
        if (command == null || command.userId() == null) {
            throw validationFailed("Lifecycle skip command userId is required");
        }
        NotificationCategory category = parseCategory(command.category());
        NotificationIntentType intentType = parseIntentType(command.intentType());
        if (!intentType.matchesCategory(category)) {
            throw validationFailed("Lifecycle skip intentType does not match category");
        }
        if (command.sourceModule() == null || !ALLOWED_SOURCE_MODULES.contains(command.sourceModule())) {
            throw validationFailed("Unsupported lifecycle sourceModule");
        }
        if (command.idempotencyKey() == null || !IDEMPOTENCY_KEY_PATTERN.matcher(command.idempotencyKey()).matches()) {
            throw validationFailed("Lifecycle idempotencyKey must match ^[A-Za-z0-9:._-]{8,128}$");
        }
        if (command.templateKey() == null
            || command.templateKey().isBlank()
            || LifecycleTemplateRegistry.findByTemplateKey(command.templateKey()).isEmpty()) {
            throw validationFailed("Unknown lifecycle templateKey");
        }
        LifecycleEvaluationDecision decision = parseSkipDecision(command.decision());
        String reasonCode = sanitizeCode(command.reasonCode(), decision.name());
        String correlationId = normalizeCorrelationId(command.correlationId());

        if (!properties.enabled() || !properties.lifecycleCampaignsEnabled()) {
            Instant now = clock.instant();
            metrics.recordLifecycleEvaluated(category.name(), LifecycleEvaluationDecision.SKIPPED_FEATURE_DISABLED.name());
            return new LifecycleEvaluationResult(
                null,
                command.userId(),
                category.name(),
                intentType.name(),
                command.idempotencyKey(),
                LifecycleEvaluationDecision.SKIPPED_FEATURE_DISABLED.name(),
                "FEATURE_DISABLED",
                null,
                null,
                now,
                false
            );
        }

        return requiresNewTx.execute(status -> {
            Instant now = clock.instant();
            UUID evalId = null;
            if (preferenceRepository.isAccountActive(command.userId())) {
                evalId = UUID.randomUUID();
                lifecycleRepository.insertEvaluation(new EvaluationRecord(
                    evalId,
                    command.userId(),
                    command.sourceModule(),
                    category,
                    intentType,
                    command.idempotencyKey(),
                    command.templateKey().trim(),
                    decision,
                    reasonCode,
                    null,
                    correlationId,
                    false,
                    now
                ));
            }
            metrics.recordLifecycleEvaluated(category.name(), decision.name());
            return new LifecycleEvaluationResult(
                evalId,
                command.userId(),
                category.name(),
                intentType.name(),
                command.idempotencyKey(),
                decision.name(),
                reasonCode,
                null,
                null,
                now,
                false
            );
        });
    }

    @Override
    public LifecycleCancellationResult cancelPendingByIdempotencyKey(
        UUID userId,
        String sourceModule,
        String category,
        String intentType,
        String idempotencyKey,
        String reasonCode,
        String correlationId
    ) {
        Objects.requireNonNull(userId, "userId must not be null");
        NotificationCategory cat = parseCategory(category);
        NotificationIntentType intent = parseIntentType(intentType);
        if (!intent.matchesCategory(cat)) {
            throw validationFailed("Lifecycle cancellation intentType does not match category");
        }
        String effectiveSource = (sourceModule == null || !ALLOWED_SOURCE_MODULES.contains(sourceModule))
            ? "notification"
            : sourceModule;
        String effectiveReason = sanitizeCode(reasonCode, "OBSOLETE");
        String effectiveCorrelation = normalizeCorrelationId(correlationId);

        return requiresNewTx.execute(status -> {
            lifecycleRepository.lockLifecycleKey(userId, cat, intent);
            Instant now = clock.instant();
            Optional<DeliveryRecord> deliveryOpt = deliveryRepository.findByUserIdAndIdempotencyKey(
                userId,
                idempotencyKey
            );
            if (deliveryOpt.isEmpty()) {
                return new LifecycleCancellationResult(
                    userId,
                    cat.name(),
                    intent.name(),
                    idempotencyKey,
                    null,
                    false,
                    "NOT_FOUND",
                    now,
                    false
                );
            }
            DeliveryRecord existing = deliveryOpt.get();
            if (!existing.status().isDispatchable()) {
                return new LifecycleCancellationResult(
                    userId,
                    cat.name(),
                    intent.name(),
                    idempotencyKey,
                    existing.id(),
                    false,
                    "ALREADY_" + existing.status().name(),
                    now,
                    false
                );
            }
            schedulingPort.cancel(userId, existing.id(), effectiveReason, effectiveCorrelation);
            lifecycleRepository.insertEvaluation(new EvaluationRecord(
                UUID.randomUUID(),
                userId,
                effectiveSource,
                cat,
                intent,
                idempotencyKey,
                existing.templateKey(),
                LifecycleEvaluationDecision.CANCELLED_OBSOLETE,
                effectiveReason,
                existing.id(),
                effectiveCorrelation,
                false,
                now
            ));
            metrics.recordLifecycleEvaluated(cat.name(), LifecycleEvaluationDecision.CANCELLED_OBSOLETE.name());
            metrics.recordLifecycleCancelled(cat.name(), effectiveReason);
            return new LifecycleCancellationResult(
                userId,
                cat.name(),
                intent.name(),
                idempotencyKey,
                existing.id(),
                true,
                effectiveReason,
                now,
                false
            );
        });
    }

    @Override
    public List<LifecycleCancellationResult> cancelPendingByCategoryExceptPrefix(
        UUID userId,
        String sourceModule,
        String category,
        String intentType,
        String retainIdempotencyKeyPrefix,
        String reasonCode,
        String correlationId
    ) {
        Objects.requireNonNull(userId, "userId must not be null");
        NotificationCategory cat = parseCategory(category);
        NotificationIntentType intent = parseIntentType(intentType);
        List<String> pendingKeys = lifecycleRepository.findPendingIdempotencyKeysByUserAndCategory(userId, cat, intent);
        List<LifecycleCancellationResult> results = new ArrayList<>();
        for (String key : pendingKeys) {
            if (retainIdempotencyKeyPrefix != null
                && !retainIdempotencyKeyPrefix.isBlank()
                && key.startsWith(retainIdempotencyKeyPrefix)) {
                continue;
            }
            results.add(cancelPendingByIdempotencyKey(
                userId,
                sourceModule,
                category,
                intentType,
                key,
                reasonCode,
                correlationId
            ));
        }
        return results;
    }

    @Override
    @Transactional(readOnly = true)
    public LifecycleSummaryView getLifecycleSummary(UUID userId, int limit) {
        Objects.requireNonNull(userId, "userId must not be null");
        if (limit < 1 || limit > 50) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.REQUEST_VALIDATION_FAILED,
                "Lifecycle summary limit must be between 1 and 50",
                true,
                "RETRY"
            );
        }
        Instant now = clock.instant();
        List<LifecycleSummaryView.CooldownItem> cooldowns = lifecycleRepository.findCooldownsByUserId(userId).stream()
            .filter(c -> c.cooldownUntil().isAfter(now))
            .map(c -> new LifecycleSummaryView.CooldownItem(
                c.category().name(),
                c.intentType().name(),
                c.lastIdempotencyKey(),
                c.lastDeliveryId(),
                c.lastTriggeredAt(),
                c.cooldownUntil(),
                true
            ))
            .toList();
        List<LifecycleSummaryView.EvaluationItem> evaluations = lifecycleRepository
            .findRecentEvaluationsByUserId(userId, limit)
            .stream()
            .map(e -> new LifecycleSummaryView.EvaluationItem(
                e.id(),
                e.sourceModule(),
                e.category().name(),
                e.intentType().name(),
                e.idempotencyKey(),
                e.templateKey(),
                e.decision().name(),
                e.reasonCode(),
                e.deliveryId(),
                e.evaluatedAt()
            ))
            .toList();
        return new LifecycleSummaryView(
            SUMMARY_SCHEMA_VERSION,
            userId,
            properties.enabled() && properties.lifecycleCampaignsEnabled(),
            cooldowns,
            evaluations,
            0,
            false,
            now
        );
    }

    @Transactional(readOnly = true)
    public LifecycleSummaryView getLifecycleSummaryForApi(UUID userId, int limit) {
        if (!preferenceRepository.isAccountActive(userId)) {
            throw new ApiProblemException(
                HttpStatus.FORBIDDEN,
                ApiErrorCode.ACCOUNT_NOT_ACTIVE,
                "Account is not active",
                false,
                "SIGN_IN"
            );
        }
        if (!properties.enabled() || !properties.lifecycleCampaignsEnabled()) {
            throw new ApiProblemException(
                HttpStatus.SERVICE_UNAVAILABLE,
                ApiErrorCode.NOTIFICATION_FEATURE_DISABLED,
                "Lifecycle messaging campaigns are disabled",
                true,
                "RETRY_LATER"
            );
        }
        return getLifecycleSummary(userId, limit);
    }

    @Override
    public void recordScanDuration(String module, Duration duration) {
        if (module != null && duration != null && !duration.isNegative()) {
            metrics.recordLifecycleScanDuration(module, duration);
        }
    }

    @Override
    public int trialReminderLeadHours() {
        return properties.trialReminderLeadHours();
    }

    @Override
    public int scanBatchSize() {
        return properties.lifecycleScanBatchSize();
    }

    @Override
    @Transactional(readOnly = true)
    public String resolvePreferredLocale(UUID userId) {
        if (userId == null) {
            return "en";
        }
        List<DeviceSummaryRow> activeDevices = deviceRepository.findActiveByUserId(userId);
        if (!activeDevices.isEmpty()) {
            String locale = activeDevices.get(0).locale();
            if ("en".equals(locale) || "tr".equals(locale)) {
                return locale;
            }
        }
        return "en";
    }

    private int cooldownHoursFor(NotificationCategory category) {
        return switch (category) {
            case STUDY_PLAN -> properties.studyPlanCooldownHours();
            case SPACED_REVIEW -> properties.spacedReviewCooldownHours();
            case EXAM_DEADLINE -> properties.examDeadlineCooldownHours();
            case BILLING_TRIAL_REMINDER, ACCOUNT_SECURITY -> 0;
        };
    }

    private static NotificationCategory parseCategory(String value) {
        if (value == null || value.isBlank()) {
            throw validationFailed("Notification category is required");
        }
        try {
            return NotificationCategory.valueOf(value.trim());
        } catch (IllegalArgumentException exception) {
            throw validationFailed("Unsupported notification category: " + value);
        }
    }

    private static NotificationIntentType parseIntentType(String value) {
        if (value == null || value.isBlank()) {
            throw validationFailed("Notification intentType is required");
        }
        try {
            return NotificationIntentType.valueOf(value.trim());
        } catch (IllegalArgumentException exception) {
            throw validationFailed("Unsupported notification intentType: " + value);
        }
    }

    private static LifecycleEvaluationDecision parseSkipDecision(String value) {
        if (value == null || value.isBlank()) {
            throw validationFailed("Lifecycle skip decision is required");
        }
        try {
            LifecycleEvaluationDecision parsed = LifecycleEvaluationDecision.valueOf(value.trim());
            if (!parsed.isSkipDecision()) {
                throw validationFailed("Lifecycle skip decision must be a SKIPPED_* value");
            }
            return parsed;
        } catch (IllegalArgumentException exception) {
            throw validationFailed("Unsupported lifecycle skip decision: " + value);
        }
    }

    private static String normalizeCorrelationId(String correlationId) {
        String effective = (correlationId == null || correlationId.isBlank())
            ? CorrelationIds.current()
            : correlationId.trim();
        return effective.length() > 64 ? effective.substring(0, 64) : effective;
    }

    private static String sanitizeCode(String value, String fallback) {
        String effective = (value == null || value.isBlank()) ? fallback : value.trim();
        return effective.length() > 64 ? effective.substring(0, 64) : effective;
    }

    private static ApiProblemException validationFailed(String message) {
        return new ApiProblemException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ApiErrorCode.NOTIFICATION_VALIDATION_FAILED,
            message,
            true,
            "RETRY"
        );
    }
}
