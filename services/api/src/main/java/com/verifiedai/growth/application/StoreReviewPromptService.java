package com.verifiedai.growth.application;

import com.verifiedai.featureflags.application.FeatureFlagEvaluationPort;
import com.verifiedai.growth.domain.GrowthContentSanitizer;
import com.verifiedai.growth.domain.StoreReviewEligibilityPolicy;
import com.verifiedai.growth.domain.StoreReviewEligibilityPolicy.Decision;
import com.verifiedai.growth.domain.StoreReviewOutcome;
import com.verifiedai.growth.domain.StoreReviewTriggerMoment;
import com.verifiedai.growth.infrastructure.GrowthProperties;
import com.verifiedai.growth.infrastructure.GrowthReadJdbcRepository;
import com.verifiedai.growth.infrastructure.GrowthWriteJdbcRepository;
import com.verifiedai.growth.infrastructure.GrowthWriteJdbcRepository.StoredReviewEvent;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.sharedkernel.observability.CorrelationIds;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Server-authoritative store review prompt eligibility evaluator & outcome recorder
 * enforcing INV-GRW-004 and INV-MON-001 (active timer/exam suppression, friction guard,
 * annual cap 3, and 60-day cooldown).
 */
@Service
public class StoreReviewPromptService {

    private static final String REVIEW_KILL_SWITCH_KEY = "growth_store_review_enabled";

    private final GrowthWriteJdbcRepository writeRepository;
    private final GrowthReadJdbcRepository readRepository;
    private final GrowthProperties properties;
    private final FeatureFlagEvaluationPort featureFlagEvaluationPort;
    private final GrowthMetrics metrics;
    private final Clock clock;

    public StoreReviewPromptService(
        GrowthWriteJdbcRepository writeRepository,
        GrowthReadJdbcRepository readRepository,
        GrowthProperties properties,
        FeatureFlagEvaluationPort featureFlagEvaluationPort,
        GrowthMetrics metrics,
        Clock clock
    ) {
        this.writeRepository = Objects.requireNonNull(writeRepository, "writeRepository must not be null");
        this.readRepository = Objects.requireNonNull(readRepository, "readRepository must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.featureFlagEvaluationPort = featureFlagEvaluationPort;
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional(readOnly = true)
    public StoreReviewEligibilityView evaluateEligibility(
        UUID userId,
        String triggerMomentRaw,
        boolean activeTimerRunning
    ) {
        Objects.requireNonNull(userId, "userId must not be null");
        ensureStoreReviewEnabled(userId);
        requireActiveAccount(userId);

        StoreReviewTriggerMoment triggerMoment = StoreReviewTriggerMoment.parse(triggerMomentRaw);
        Instant now = clock.instant();

        boolean hasActiveExam = readRepository.hasActiveExamSession(userId);
        boolean declinedPermanently = writeRepository.hasPermanentDecline(userId);
        boolean hasRecentFriction = readRepository.hasRecentFriction(
            userId,
            now.minus(Duration.ofDays(properties.reviewFrictionLookbackDays()))
        );
        Instant createdAt = readRepository.findUserCreatedAt(userId).orElse(now);
        long accountAgeDays = Math.max(0L, ChronoUnit.DAYS.between(createdAt, now));
        long verifiedLearningActions = readRepository.countVerifiedLearningActions(userId);
        int promptsShown365 = writeRepository.countPromptsShownSince(userId, now.minus(Duration.ofDays(365)));
        Instant lastShownOrDismissedAt = writeRepository.findLatestShownOrDismissedAt(userId).orElse(null);

        Decision decision = StoreReviewEligibilityPolicy.evaluate(
            new StoreReviewEligibilityPolicy.Inputs(
                triggerMoment,
                activeTimerRunning,
                hasActiveExam,
                declinedPermanently,
                hasRecentFriction,
                accountAgeDays,
                verifiedLearningActions,
                promptsShown365,
                lastShownOrDismissedAt,
                properties.reviewMinAccountAgeDays(),
                properties.reviewMinVerifiedActions(),
                properties.reviewMaxPromptsPerYear(),
                properties.reviewCooldownDays(),
                now
            )
        );
        metrics.recordReviewPrompt(triggerMoment.name(), decision.reason().name());
        return new StoreReviewEligibilityView(
            decision.eligible(),
            decision.reason().name(),
            decision.nextEligibleAt(),
            triggerMoment.name(),
            promptsShown365,
            false,
            0
        );
    }

    @Transactional
    public StoreReviewEventResultView recordOutcome(
        UUID userId,
        String triggerMomentRaw,
        String outcomeRaw,
        String appVersionRaw,
        String localeRaw,
        String idempotencyKeyRaw
    ) {
        Objects.requireNonNull(userId, "userId must not be null");
        ensureStoreReviewEnabled(userId);
        requireActiveAccount(userId);

        String idempotencyKey = GrowthContentSanitizer.requireIdempotencyKey(idempotencyKeyRaw);
        StoreReviewTriggerMoment triggerMoment = StoreReviewTriggerMoment.parse(triggerMomentRaw);
        StoreReviewOutcome outcome = StoreReviewOutcome.parse(outcomeRaw);
        String appVersion = GrowthContentSanitizer.validateAppVersion(appVersionRaw);
        String locale = GrowthContentSanitizer.normalizeLocale(localeRaw);

        Map<String, Object> canonicalPayload = new LinkedHashMap<>();
        canonicalPayload.put("triggerMoment", triggerMoment.name());
        canonicalPayload.put("outcome", outcome.name());
        canonicalPayload.put("appVersion", appVersion);
        canonicalPayload.put("locale", locale);
        String payloadHash = GrowthContentSanitizer.sha256Hex(canonicalPayload);

        writeRepository.acquireAdvisoryLock("review:" + userId);

        Optional<StoredReviewEvent> existingOpt = writeRepository.findReviewEventByUserAndIdempotencyKey(
            userId,
            idempotencyKey
        );
        if (existingOpt.isPresent()) {
            StoredReviewEvent existing = existingOpt.get();
            if (!existing.payloadHash().equals(payloadHash)) {
                throw new ApiProblemException(
                    HttpStatus.CONFLICT,
                    ApiErrorCode.GROWTH_IDEMPOTENCY_CONFLICT,
                    "Idempotency-Key already used with a different review event payload",
                    false,
                    "USE_NEW_IDEMPOTENCY_KEY"
                );
            }
            metrics.recordReviewPrompt(existing.triggerMoment().name(), "REPLAYED");
            return StoreReviewEventResultView.from(existing, true);
        }

        Instant now = clock.instant();
        StoredReviewEvent row = new StoredReviewEvent(
            UUID.randomUUID(),
            userId,
            idempotencyKey,
            payloadHash,
            triggerMoment,
            outcome,
            appVersion,
            locale,
            CorrelationIds.current(),
            false,
            now
        );
        writeRepository.insertReviewEvent(row);
        metrics.recordReviewPrompt(triggerMoment.name(), outcome.name());
        return StoreReviewEventResultView.from(row, false);
    }

    private void ensureStoreReviewEnabled(UUID userContextId) {
        boolean killSwitchAllowed = featureFlagEvaluationPort == null
            || featureFlagEvaluationPort.evaluateBoolean(REVIEW_KILL_SWITCH_KEY, userContextId, true);
        if (!properties.storeReviewEnabled() || !killSwitchAllowed) {
            throw new ApiProblemException(
                HttpStatus.SERVICE_UNAVAILABLE,
                ApiErrorCode.GROWTH_FEATURE_DISABLED,
                "Growth store review prompt checks are currently disabled",
                true,
                "TRY_AGAIN_LATER"
            );
        }
    }

    private void requireActiveAccount(UUID userId) {
        if (!readRepository.isAccountActive(userId)) {
            throw new ApiProblemException(
                HttpStatus.FORBIDDEN,
                ApiErrorCode.ACCOUNT_NOT_ACTIVE,
                "Active account required",
                false,
                "SIGN_IN"
            );
        }
    }
}
