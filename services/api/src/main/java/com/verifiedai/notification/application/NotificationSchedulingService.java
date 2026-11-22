package com.verifiedai.notification.application;

import com.verifiedai.notification.domain.NotificationDeliveryStatus;
import com.verifiedai.notification.domain.NotificationPayloadHasher;
import com.verifiedai.notification.domain.NotificationPolicyEvaluator;
import com.verifiedai.notification.domain.NotificationPolicyEvaluator.PolicyDecision;
import com.verifiedai.notification.domain.NotificationPolicyEvaluator.PolicyEvaluationInput;
import com.verifiedai.notification.domain.NotificationPreferenceSnapshot;
import com.verifiedai.notification.domain.NotificationSuppressionReason;
import com.verifiedai.notification.domain.QuietHoursPolicy;
import com.verifiedai.notification.domain.QuietHoursPolicy.LocalDayBounds;
import com.verifiedai.notification.infrastructure.NotificationDeliveryJdbcRepository;
import com.verifiedai.notification.infrastructure.NotificationDeliveryJdbcRepository.DeliveryRecord;
import com.verifiedai.notification.infrastructure.NotificationDeviceJdbcRepository;
import com.verifiedai.notification.infrastructure.NotificationPreferenceJdbcRepository;
import com.verifiedai.notification.infrastructure.NotificationProperties;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.sharedkernel.observability.CorrelationIds;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Application service implementing {@link NotificationSchedulingPort} with idempotent payload hashing and policy gating.
 */
@Service
public class NotificationSchedulingService implements NotificationSchedulingPort {

    private final NotificationDeliveryJdbcRepository deliveryRepository;
    private final NotificationPreferenceJdbcRepository preferenceRepository;
    private final NotificationDeviceJdbcRepository deviceRepository;
    private final NotificationPolicyEvaluator policyEvaluator;
    private final NotificationPreferenceService preferenceService;
    private final NotificationProperties properties;
    private final NotificationMetrics metrics;
    private final Clock clock;
    private final TransactionTemplate requiresNewTx;

    public NotificationSchedulingService(
        NotificationDeliveryJdbcRepository deliveryRepository,
        NotificationPreferenceJdbcRepository preferenceRepository,
        NotificationDeviceJdbcRepository deviceRepository,
        NotificationPolicyEvaluator policyEvaluator,
        NotificationPreferenceService preferenceService,
        NotificationProperties properties,
        NotificationMetrics metrics,
        Clock clock,
        PlatformTransactionManager transactionManager
    ) {
        this.deliveryRepository = deliveryRepository;
        this.preferenceRepository = preferenceRepository;
        this.deviceRepository = deviceRepository;
        this.policyEvaluator = policyEvaluator;
        this.preferenceService = preferenceService;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.requiresNewTx = tx;
    }

    @Override
    public ScheduledNotificationResult schedule(ScheduleNotificationCommand command) {
        if (command == null
            || command.userId() == null
            || command.category() == null
            || command.intentType() == null
            || command.channel() == null
            || command.scheduledFor() == null
            || command.expiresAt() == null) {
            throw new ApiProblemException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.NOTIFICATION_VALIDATION_FAILED,
                "Notification schedule command is missing required fields",
                true,
                "RETRY"
            );
        }
        if (!command.intentType().matchesCategory(command.category())) {
            throw new ApiProblemException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.NOTIFICATION_VALIDATION_FAILED,
                "Notification intentType does not match category",
                true,
                "RETRY"
            );
        }
        if (!command.expiresAt().isAfter(command.scheduledFor())) {
            throw new ApiProblemException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.NOTIFICATION_VALIDATION_FAILED,
                "Notification expiresAt must be strictly after scheduledFor",
                true,
                "RETRY"
            );
        }

        NotificationPayloadHasher.validateScheduleFields(
            command.idempotencyKey(),
            command.locale(),
            command.templateKey(),
            command.templateParams(),
            command.deepLinkPath(),
            command.sourceModule(),
            command.correlationId()
        );
        String canonicalParamsJson = NotificationPayloadHasher.canonicalizeParams(command.templateParams());
        String payloadHash = NotificationPayloadHasher.computePayloadHash(
            command.category(),
            command.intentType(),
            command.channel(),
            command.locale(),
            command.templateKey(),
            canonicalParamsJson,
            command.deepLinkPath(),
            command.scheduledFor(),
            command.expiresAt()
        );

        if (!properties.enabled()) {
            metrics.recordScheduled(command.category(), NotificationDeliveryStatus.SUPPRESSED);
            metrics.recordSuppressed(command.category(), NotificationSuppressionReason.FEATURE_DISABLED);
            return new ScheduledNotificationResult(
                null,
                command.userId(),
                command.idempotencyKey(),
                command.category(),
                command.intentType(),
                NotificationDeliveryStatus.SUPPRESSED,
                NotificationSuppressionReason.FEATURE_DISABLED,
                command.scheduledFor(),
                command.scheduledFor(),
                command.expiresAt(),
                false,
                false
            );
        }

        return requiresNewTx.execute(status -> {
            deliveryRepository.lockUser(command.userId());
            Optional<DeliveryRecord> existingOpt = deliveryRepository.findByUserIdAndIdempotencyKey(
                command.userId(),
                command.idempotencyKey()
            );
            if (existingOpt.isPresent()) {
                DeliveryRecord existing = existingOpt.get();
                if (!existing.payloadHash().equals(payloadHash)) {
                    throw new ApiProblemException(
                        HttpStatus.CONFLICT,
                        ApiErrorCode.NOTIFICATION_IDEMPOTENCY_CONFLICT,
                        "Notification idempotencyKey was already used with a different payload",
                        false,
                        "RETRY_WITH_NEW_KEY"
                    );
                }
                return new ScheduledNotificationResult(
                    existing.id(),
                    existing.userId(),
                    existing.idempotencyKey(),
                    existing.category(),
                    existing.intentType(),
                    existing.status(),
                    existing.suppressionReason(),
                    existing.scheduledFor(),
                    existing.effectiveSendAt(),
                    existing.expiresAt(),
                    true,
                    false
                );
            }

            Instant now = clock.instant();
            boolean accountActive = preferenceRepository.isAccountActive(command.userId());
            NotificationPreferenceSnapshot preferences = preferenceRepository.findByUserId(command.userId())
                .orElseGet(() -> NotificationPreferenceSnapshot.defaultFor(
                    command.userId(),
                    properties.defaultDailyCap(),
                    now
                ));
            boolean hasDeliverableDevice = !deviceRepository.findDeliverableActiveByUserId(command.userId()).isEmpty();
            ZoneId userZone = QuietHoursPolicy.parseAndValidateZone(preferences.timezone());
            LocalDayBounds dayBounds = QuietHoursPolicy.localDayBounds(now, userZone);
            int sentToday = deliveryRepository.countSentNonSecurityInWindow(
                command.userId(),
                dayBounds.startInclusive(),
                dayBounds.endExclusive()
            );

            PolicyDecision decision = policyEvaluator.evaluate(new PolicyEvaluationInput(
                true,
                accountActive,
                command.category(),
                command.intentType(),
                preferences,
                hasDeliverableDevice,
                sentToday,
                now,
                command.scheduledFor(),
                command.expiresAt()
            ));

            UUID deliveryId = UUID.randomUUID();
            DeliveryRecord record = new DeliveryRecord(
                deliveryId,
                command.userId(),
                command.idempotencyKey(),
                payloadHash,
                command.category(),
                command.intentType(),
                command.channel(),
                command.locale(),
                command.templateKey().trim(),
                canonicalParamsJson,
                command.deepLinkPath(),
                decision.status(),
                decision.suppressionReason(),
                null,
                command.scheduledFor(),
                decision.effectiveSendAt(),
                command.expiresAt(),
                0,
                properties.maxDeliveryAttempts(),
                null,
                null,
                null,
                command.correlationId(),
                command.sourceModule(),
                0L,
                false,
                now,
                now
            );
            deliveryRepository.insertDeliveryAndEvent(record, decision.reasonCode());

            metrics.recordScheduled(command.category(), decision.status());
            if (decision.suppressionReason() != null) {
                metrics.recordSuppressed(command.category(), decision.suppressionReason());
            }

            return new ScheduledNotificationResult(
                deliveryId,
                command.userId(),
                command.idempotencyKey(),
                command.category(),
                command.intentType(),
                decision.status(),
                decision.suppressionReason(),
                command.scheduledFor(),
                decision.effectiveSendAt(),
                command.expiresAt(),
                false,
                false
            );
        });
    }

    @Override
    public CancelNotificationResult cancel(UUID userId, UUID deliveryId, String reasonCode, String correlationId) {
        Objects.requireNonNull(userId, "userId must not be null");
        if (deliveryId == null) {
            throw deliveryNotFound();
        }
        String sanitizedReason = (reasonCode == null || reasonCode.isBlank()) ? "CANCELLED" : reasonCode.trim();
        if (sanitizedReason.length() > 64) {
            sanitizedReason = sanitizedReason.substring(0, 64);
        }
        String sanitizedCorrelation = (correlationId == null || correlationId.isBlank())
            ? CorrelationIds.current()
            : correlationId;
        if (sanitizedCorrelation.length() > 64) {
            sanitizedCorrelation = sanitizedCorrelation.substring(0, 64);
        }

        final String finalReason = sanitizedReason;
        final String finalCorrelation = sanitizedCorrelation;
        return requiresNewTx.execute(status -> {
            DeliveryRecord record = deliveryRepository.findByIdAndUserIdForUpdate(deliveryId, userId)
                .orElseThrow(NotificationSchedulingService::deliveryNotFound);
            Instant now = clock.instant();
            deliveryRepository.markCancelled(record, finalReason, finalCorrelation, now);
            return new CancelNotificationResult(deliveryId, NotificationDeliveryStatus.CANCELLED, now, false);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationPreferenceView getPreferences(UUID userId) {
        return preferenceService.getPreferences(userId);
    }

    @Transactional(readOnly = true)
    public List<NotificationDeliverySummaryView> listDeliveriesForApi(UUID userId, int limit) {
        requireActiveAccount(userId);
        requireFeatureEnabled();
        if (limit < 1 || limit > 50) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.REQUEST_VALIDATION_FAILED,
                "Notification delivery limit must be between 1 and 50",
                true,
                "RETRY"
            );
        }
        return deliveryRepository.findRecentByUserId(userId, limit).stream()
            .map(NotificationSchedulingService::toSummaryView)
            .toList();
    }

    public CancelNotificationResult cancelForApi(UUID userId, UUID deliveryId) {
        requireActiveAccount(userId);
        requireFeatureEnabled();
        return cancel(userId, deliveryId, "USER_CANCELLED", CorrelationIds.current());
    }

    private void requireActiveAccount(UUID userId) {
        if (!preferenceRepository.isAccountActive(userId)) {
            throw new ApiProblemException(
                HttpStatus.FORBIDDEN,
                ApiErrorCode.ACCOUNT_NOT_ACTIVE,
                "Account is not active",
                false,
                "SIGN_IN"
            );
        }
    }

    private void requireFeatureEnabled() {
        if (!properties.enabled()) {
            throw new ApiProblemException(
                HttpStatus.SERVICE_UNAVAILABLE,
                ApiErrorCode.NOTIFICATION_FEATURE_DISABLED,
                "Notification infrastructure is disabled",
                true,
                "RETRY_LATER"
            );
        }
    }

    private static NotificationDeliverySummaryView toSummaryView(DeliveryRecord record) {
        return new NotificationDeliverySummaryView(
            record.id(),
            record.category().name(),
            record.intentType().name(),
            record.channel().name(),
            record.locale(),
            record.templateKey(),
            record.deepLinkPath(),
            record.status().name(),
            record.suppressionReason() == null ? null : record.suppressionReason().name(),
            record.scheduledFor(),
            record.effectiveSendAt(),
            record.expiresAt(),
            record.sentAt(),
            record.attemptCount(),
            record.createdAt()
        );
    }

    private static ApiProblemException deliveryNotFound() {
        return new ApiProblemException(
            HttpStatus.NOT_FOUND,
            ApiErrorCode.NOTIFICATION_DELIVERY_NOT_FOUND,
            "Notification delivery not found",
            false,
            "REFRESH"
        );
    }
}
