package com.verifiedai.notification.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.verifiedai.notification.domain.NotificationDeliveryStatus;
import com.verifiedai.notification.domain.NotificationPolicyEvaluator;
import com.verifiedai.notification.domain.NotificationPolicyEvaluator.PolicyDecision;
import com.verifiedai.notification.domain.NotificationPreferenceSnapshot;
import com.verifiedai.notification.domain.NotificationSuppressionReason;
import com.verifiedai.notification.domain.QuietHoursPolicy;
import com.verifiedai.notification.domain.QuietHoursPolicy.LocalDayBounds;
import com.verifiedai.notification.infrastructure.NotificationDeliveryJdbcRepository;
import com.verifiedai.notification.infrastructure.NotificationDeliveryJdbcRepository.DeliveryRecord;
import com.verifiedai.notification.infrastructure.NotificationDeviceJdbcRepository;
import com.verifiedai.notification.infrastructure.NotificationDeviceJdbcRepository.DispatchDeviceTarget;
import com.verifiedai.notification.infrastructure.NotificationPreferenceJdbcRepository;
import com.verifiedai.notification.infrastructure.NotificationProperties;
import com.verifiedai.notification.infrastructure.PushDeliveryGateway;
import com.verifiedai.notification.infrastructure.PushDeliveryGateway.PushDispatchOutcome;
import com.verifiedai.notification.infrastructure.PushDeliveryGateway.PushDispatchRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Worker dispatch service claiming due deliveries via {@code FOR UPDATE SKIP LOCKED},
 * evaluating runtime policy, executing {@link PushDeliveryGateway}, and managing retries/stale locks.
 */
@Service
public class NotificationDispatchService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationDispatchService.class);
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().findAndAddModules().build();
    private static final TypeReference<Map<String, String>> STRING_MAP_TYPE = new TypeReference<>() {};

    private final NotificationDeliveryJdbcRepository deliveryRepository;
    private final NotificationPreferenceJdbcRepository preferenceRepository;
    private final NotificationDeviceJdbcRepository deviceRepository;
    private final NotificationPolicyEvaluator policyEvaluator;
    private final PushDeliveryGateway pushDeliveryGateway;
    private final NotificationProperties properties;
    private final NotificationMetrics metrics;
    private final Clock clock;
    private final TransactionTemplate requiresNewTx;

    public NotificationDispatchService(
        NotificationDeliveryJdbcRepository deliveryRepository,
        NotificationPreferenceJdbcRepository preferenceRepository,
        NotificationDeviceJdbcRepository deviceRepository,
        NotificationPolicyEvaluator policyEvaluator,
        PushDeliveryGateway pushDeliveryGateway,
        NotificationProperties properties,
        NotificationMetrics metrics,
        Clock clock,
        PlatformTransactionManager transactionManager
    ) {
        this.deliveryRepository = deliveryRepository;
        this.preferenceRepository = preferenceRepository;
        this.deviceRepository = deviceRepository;
        this.policyEvaluator = policyEvaluator;
        this.pushDeliveryGateway = pushDeliveryGateway;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.requiresNewTx = tx;
    }

    public int dispatchDueBatch() {
        return dispatchDueBatch(properties.dispatchBatchSize());
    }

    public int dispatchDueBatch(int batchSize) {
        if (!properties.enabled()) {
            return 0;
        }
        int boundedBatchSize = Math.max(1, Math.min(batchSize, 200));
        Instant claimNow = clock.instant();
        Instant lockedUntil = claimNow.plusSeconds(properties.dispatchLockTtlSeconds());
        List<DeliveryRecord> claimed = requiresNewTx.execute(
            status -> deliveryRepository.claimDueBatch(claimNow, lockedUntil, boundedBatchSize)
        );
        if (claimed == null || claimed.isEmpty()) {
            return 0;
        }
        for (DeliveryRecord delivery : claimed) {
            processClaimedDelivery(delivery);
        }
        return claimed.size();
    }

    public int recoverStaleLocks() {
        if (!properties.enabled()) {
            return 0;
        }
        Instant now = clock.instant();
        Integer recovered = requiresNewTx.execute(
            status -> deliveryRepository.recoverStaleLocks(now, properties.dispatchBatchSize())
        );
        return recovered == null ? 0 : recovered;
    }

    private void processClaimedDelivery(DeliveryRecord delivery) {
        long startNano = System.nanoTime();
        Instant evalNow = clock.instant();

        PreDispatchCheck preCheck = requiresNewTx.execute(status -> {
            boolean accountActive = preferenceRepository.isAccountActive(delivery.userId());
            NotificationPreferenceSnapshot preferences = preferenceRepository.findByUserId(delivery.userId())
                .orElseGet(() -> NotificationPreferenceSnapshot.defaultFor(
                    delivery.userId(),
                    properties.defaultDailyCap(),
                    evalNow
                ));
            List<DispatchDeviceTarget> targets = deviceRepository.findDeliverableActiveByUserId(delivery.userId());
            ZoneId userZone = QuietHoursPolicy.parseAndValidateZone(preferences.timezone());
            LocalDayBounds dayBounds = QuietHoursPolicy.localDayBounds(evalNow, userZone);
            int sentToday = deliveryRepository.countSentNonSecurityInWindow(
                delivery.userId(),
                dayBounds.startInclusive(),
                dayBounds.endExclusive()
            );
            PolicyDecision decision = policyEvaluator.evaluateAtDispatch(
                properties.enabled(),
                accountActive,
                delivery.category(),
                delivery.intentType(),
                preferences,
                !targets.isEmpty(),
                sentToday,
                evalNow,
                delivery.expiresAt()
            );
            return new PreDispatchCheck(decision, targets.isEmpty() ? null : targets.getFirst());
        });

        Objects.requireNonNull(preCheck, "preCheck must not be null");
        PolicyDecision decision = preCheck.decision();

        if (decision.status() == NotificationDeliveryStatus.SUPPRESSED) {
            requiresNewTx.executeWithoutResult(status -> deliveryRepository.markSuppressed(
                delivery,
                delivery.attemptCount(),
                decision.suppressionReason(),
                null,
                clock.instant()
            ));
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startNano);
            metrics.recordSuppressed(delivery.category(), decision.suppressionReason());
            metrics.recordDispatch(delivery.category(), "suppressed", elapsed);
            return;
        }

        if (decision.status() == NotificationDeliveryStatus.DEFERRED_QUIET_HOURS) {
            Instant deferredSendAt = decision.effectiveSendAt().isBefore(delivery.scheduledFor())
                ? delivery.scheduledFor()
                : decision.effectiveSendAt();
            requiresNewTx.executeWithoutResult(status ->
                deliveryRepository.markDeferredFromDispatching(delivery, deferredSendAt, clock.instant())
            );
            return;
        }

        DispatchDeviceTarget target = Objects.requireNonNull(preCheck.target(), "deliverable target must exist");
        Map<String, String> templateParams = parseTemplateParams(delivery.templateParamsJson());
        PushDispatchOutcome outcome = pushDeliveryGateway.send(new PushDispatchRequest(
            delivery.id(),
            delivery.userId(),
            target.deviceId(),
            target.platform(),
            target.environment(),
            target.deviceToken(),
            target.tokenLast4(),
            delivery.category(),
            delivery.intentType(),
            delivery.locale(),
            delivery.templateKey(),
            templateParams,
            delivery.deepLinkPath(),
            delivery.correlationId()
        ));

        Instant commitNow = clock.instant();
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startNano);
        int nextAttemptCount = delivery.attemptCount() + 1;

        switch (outcome.status()) {
            case SENT -> {
                requiresNewTx.executeWithoutResult(status ->
                    deliveryRepository.markSent(delivery, nextAttemptCount, outcome.providerMessageId(), commitNow)
                );
                metrics.recordDispatch(delivery.category(), "sent", elapsed);
            }
            case INVALID_TOKEN -> {
                requiresNewTx.executeWithoutResult(status -> {
                    deviceRepository.revokeByIdAndUserId(target.deviceId(), delivery.userId(), commitNow);
                    deliveryRepository.markFailedPermanent(
                        delivery,
                        nextAttemptCount,
                        sanitizeCode(outcome.errorCode(), "INVALID_DEVICE_TOKEN"),
                        "INVALID_DEVICE_TOKEN",
                        commitNow
                    );
                });
                metrics.recordDeviceLifecycle("revoked");
                metrics.recordDispatch(delivery.category(), "failed_permanent", elapsed);
            }
            case PERMANENT_FAILURE -> {
                String code = sanitizeCode(outcome.errorCode(), "PERMANENT_PUSH_ERROR");
                requiresNewTx.executeWithoutResult(status ->
                    deliveryRepository.markFailedPermanent(delivery, nextAttemptCount, code, code, commitNow)
                );
                metrics.recordDispatch(delivery.category(), "failed_permanent", elapsed);
            }
            case RETRYABLE_FAILURE -> {
                String code = sanitizeCode(outcome.errorCode(), "TRANSIENT_PUSH_ERROR");
                if (nextAttemptCount >= delivery.maxAttempts()) {
                    requiresNewTx.executeWithoutResult(status -> deliveryRepository.markFailedPermanent(
                        delivery,
                        nextAttemptCount,
                        code,
                        "MAX_ATTEMPTS_EXHAUSTED",
                        commitNow
                    ));
                    metrics.recordDispatch(delivery.category(), "failed_permanent", elapsed);
                    return;
                }
                int shift = Math.min(nextAttemptCount - 1, 10);
                long delaySeconds = (long) properties.retryBaseDelaySeconds() * (1L << shift);
                Instant nextSendAt = commitNow.plusSeconds(delaySeconds);
                if (nextSendAt.isBefore(delivery.scheduledFor())) {
                    nextSendAt = delivery.scheduledFor();
                }
                if (!nextSendAt.isBefore(delivery.expiresAt())) {
                    requiresNewTx.executeWithoutResult(status -> deliveryRepository.markSuppressed(
                        delivery,
                        nextAttemptCount,
                        NotificationSuppressionReason.INTENT_EXPIRED,
                        code,
                        commitNow
                    ));
                    metrics.recordSuppressed(delivery.category(), NotificationSuppressionReason.INTENT_EXPIRED);
                    metrics.recordDispatch(delivery.category(), "suppressed", elapsed);
                    return;
                }
                final Instant finalNextSendAt = nextSendAt;
                requiresNewTx.executeWithoutResult(status -> deliveryRepository.markRetryScheduled(
                    delivery,
                    nextAttemptCount,
                    finalNextSendAt,
                    code,
                    commitNow
                ));
                metrics.recordDispatch(delivery.category(), "retry", elapsed);
            }
        }
    }

    private static Map<String, String> parseTemplateParams(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, STRING_MAP_TYPE);
        } catch (Exception exception) {
            LOGGER.warn("notification.template_params.parse_fallback");
            return Map.of();
        }
    }

    private static String sanitizeCode(String code, String fallback) {
        if (code == null || code.isBlank()) {
            return fallback;
        }
        String trimmed = code.trim();
        return trimmed.length() > 64 ? trimmed.substring(0, 64) : trimmed;
    }

    private record PreDispatchCheck(PolicyDecision decision, DispatchDeviceTarget target) {
    }
}
