package com.verifiedai.identity.application;

import com.verifiedai.notification.application.LifecycleEvaluationResult;
import com.verifiedai.notification.application.LifecycleMessagingPort;
import com.verifiedai.notification.application.LifecycleTriggerCommand;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.sharedkernel.observability.CorrelationIds;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Owning-module trigger service emitting mandatory, cooldown-exempt {@code ACCOUNT_SECURITY_ALERT} notifications.
 */
@Service
public class IdentitySecurityNotificationService {

    public static final Set<String> ALLOWED_EVENT_TYPES = Set.of(
        "REFRESH_TOKEN_REUSE",
        "ACCOUNT_DELETION_REQUESTED",
        "SESSION_REVOKED_ALL"
    );

    private static final String SOURCE_MODULE = "identity";
    private static final String CATEGORY_ACCOUNT_SECURITY = "ACCOUNT_SECURITY";
    private static final String INTENT_SECURITY_ALERT = "ACCOUNT_SECURITY_ALERT";
    private static final String TEMPLATE_SECURITY_ALERT = "notification.template.account.security_alert";
    private static final String DEEP_LINK_SECURITY = "verified-ai://account/security";

    private final LifecycleMessagingPort lifecycleMessagingPort;
    private final Clock clock;

    public IdentitySecurityNotificationService(LifecycleMessagingPort lifecycleMessagingPort, Clock clock) {
        this.lifecycleMessagingPort = Objects.requireNonNull(lifecycleMessagingPort, "lifecycleMessagingPort");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public LifecycleEvaluationResult notifySecurityEvent(
        UUID userId,
        String eventType,
        String eventId,
        Instant now
    ) {
        Objects.requireNonNull(userId, "userId must not be null");
        if (eventType == null || !ALLOWED_EVENT_TYPES.contains(eventType)) {
            throw new ApiProblemException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.NOTIFICATION_VALIDATION_FAILED,
                "Unsupported account security eventType: " + eventType,
                true,
                "RETRY"
            );
        }
        String sanitizedEventId = (eventId == null || eventId.isBlank())
            ? UUID.randomUUID().toString()
            : eventId.trim().replaceAll("[^A-Za-z0-9:._-]", "_");
        String idempotencyKey = securityAlertIdempotencyKey(userId, eventType, sanitizedEventId);
        Instant evalTime = now != null ? now : clock.instant();
        String locale = lifecycleMessagingPort.resolvePreferredLocale(userId);
        return lifecycleMessagingPort.evaluateAndSchedule(new LifecycleTriggerCommand(
            userId,
            SOURCE_MODULE,
            CATEGORY_ACCOUNT_SECURITY,
            INTENT_SECURITY_ALERT,
            idempotencyKey,
            locale,
            TEMPLATE_SECURITY_ALERT,
            Map.of("eventType", eventType),
            DEEP_LINK_SECURITY,
            evalTime,
            evalTime.plus(Duration.ofHours(24)),
            CorrelationIds.current()
        ));
    }

    public static String securityAlertIdempotencyKey(UUID userId, String eventType, String eventId) {
        String sanitizedId = eventId.trim().replaceAll("[^A-Za-z0-9:._-]", "_");
        return "identity:sec:" + userId + ":" + eventType + ":" + sanitizedId;
    }
}
