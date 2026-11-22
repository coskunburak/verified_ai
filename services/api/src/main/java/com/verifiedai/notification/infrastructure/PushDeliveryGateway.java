package com.verifiedai.notification.infrastructure;

import com.verifiedai.notification.domain.DevicePlatform;
import com.verifiedai.notification.domain.NotificationCategory;
import com.verifiedai.notification.domain.NotificationIntentType;
import com.verifiedai.notification.domain.PushEnvironment;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Infrastructure port for delivering push notifications to a registered device token.
 */
public interface PushDeliveryGateway {

    PushDispatchOutcome send(PushDispatchRequest request);

    record PushDispatchRequest(
        UUID deliveryId,
        UUID userId,
        UUID deviceId,
        DevicePlatform platform,
        PushEnvironment environment,
        String deviceToken,
        String tokenLast4,
        NotificationCategory category,
        NotificationIntentType intentType,
        String locale,
        String templateKey,
        Map<String, String> templateParams,
        String deepLinkPath,
        String correlationId
    ) {
        public PushDispatchRequest {
            Objects.requireNonNull(deliveryId, "deliveryId must not be null");
            Objects.requireNonNull(userId, "userId must not be null");
            Objects.requireNonNull(deviceId, "deviceId must not be null");
            Objects.requireNonNull(platform, "platform must not be null");
            Objects.requireNonNull(environment, "environment must not be null");
            Objects.requireNonNull(deviceToken, "deviceToken must not be null");
            Objects.requireNonNull(tokenLast4, "tokenLast4 must not be null");
            Objects.requireNonNull(category, "category must not be null");
            Objects.requireNonNull(intentType, "intentType must not be null");
            templateParams = templateParams == null ? Map.of() : Map.copyOf(templateParams);
        }

        @Override
        public String toString() {
            return "PushDispatchRequest["
                + "deliveryId=" + deliveryId
                + ", userId=" + userId
                + ", deviceId=" + deviceId
                + ", platform=" + platform
                + ", environment=" + environment
                + ", tokenLast4=" + tokenLast4
                + ", category=" + category
                + ", intentType=" + intentType
                + ", locale=" + locale
                + ", templateKey=" + templateKey
                + ", deepLinkPath=" + deepLinkPath
                + ", correlationId=" + correlationId
                + "]";
        }
    }

    record PushDispatchOutcome(
        OutcomeStatus status,
        String providerMessageId,
        String errorCode
    ) {
        public static PushDispatchOutcome sent(String providerMessageId) {
            return new PushDispatchOutcome(OutcomeStatus.SENT, providerMessageId, null);
        }

        public static PushDispatchOutcome retryableFailure(String errorCode) {
            return new PushDispatchOutcome(OutcomeStatus.RETRYABLE_FAILURE, null, errorCode);
        }

        public static PushDispatchOutcome permanentFailure(String errorCode) {
            return new PushDispatchOutcome(OutcomeStatus.PERMANENT_FAILURE, null, errorCode);
        }

        public static PushDispatchOutcome invalidToken(String errorCode) {
            return new PushDispatchOutcome(OutcomeStatus.INVALID_TOKEN, null, errorCode);
        }
    }

    enum OutcomeStatus {
        SENT,
        RETRYABLE_FAILURE,
        PERMANENT_FAILURE,
        INVALID_TOKEN
    }
}
