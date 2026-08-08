package com.verifiedai.billing.infrastructure.appstore;

import com.apple.itunes.storekit.model.Environment;
import com.apple.itunes.storekit.model.AutoRenewStatus;
import com.apple.itunes.storekit.model.JWSRenewalInfoDecodedPayload;
import com.apple.itunes.storekit.model.JWSTransactionDecodedPayload;
import com.apple.itunes.storekit.model.ResponseBodyV2DecodedPayload;
import com.apple.itunes.storekit.verification.SignedDataVerifier;
import com.apple.itunes.storekit.verification.VerificationException;
import com.verifiedai.billing.domain.model.AppStoreEnvironment;
import com.verifiedai.billing.domain.model.VerifiedAppStoreNotification;
import com.verifiedai.billing.domain.model.VerifiedAppStoreRenewalInfo;
import com.verifiedai.billing.domain.model.VerifiedAppStoreTransaction;
import com.verifiedai.billing.domain.port.AppStoreSignedDataVerifier;
import com.verifiedai.billing.domain.port.AppStoreVerificationFailedException;
import com.verifiedai.billing.infrastructure.configuration.AppleBillingProperties;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.billing.apple", name = "enabled", havingValue = "true")
class AppleAppStoreSignedDataVerifier implements AppStoreSignedDataVerifier {
    private final SignedDataVerifier verifier;

    AppleAppStoreSignedDataVerifier(AppleBillingProperties properties) {
        Set<InputStream> rootCertificates = properties.rootCertificatePem().stream()
            .map(value -> new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)))
            .collect(Collectors.toUnmodifiableSet());
        this.verifier = new SignedDataVerifier(
            rootCertificates,
            properties.bundleId(),
            properties.normalizedAppAppleId(),
            toAppleEnvironment(properties.environment()),
            true
        );
    }

    @Override
    public VerifiedAppStoreTransaction verifyTransaction(String signedTransactionInfo) {
        try {
            return mapTransaction(verifier.verifyAndDecodeTransaction(signedTransactionInfo));
        } catch (VerificationException exception) {
            throw new AppStoreVerificationFailedException("Apple transaction JWS verification failed", exception);
        }
    }

    @Override
    public VerifiedAppStoreRenewalInfo verifyRenewalInfo(String signedRenewalInfo) {
        try {
            return mapRenewal(verifier.verifyAndDecodeRenewalInfo(signedRenewalInfo));
        } catch (VerificationException exception) {
            throw new AppStoreVerificationFailedException("Apple renewal JWS verification failed", exception);
        }
    }

    @Override
    public VerifiedAppStoreNotification verifyNotification(String signedPayload) {
        try {
            ResponseBodyV2DecodedPayload payload = verifier.verifyAndDecodeNotification(signedPayload);
            var data = payload.getData();
            VerifiedAppStoreTransaction transaction = data == null || data.getSignedTransactionInfo() == null
                ? null
                : verifyTransaction(data.getSignedTransactionInfo());
            VerifiedAppStoreRenewalInfo renewalInfo = data == null || data.getSignedRenewalInfo() == null
                ? null
                : verifyRenewalInfo(data.getSignedRenewalInfo());
            AppStoreEnvironment environment = data != null && data.getEnvironment() != null
                ? toDomainEnvironment(data.getEnvironment())
                : AppStoreEnvironment.PRODUCTION;
            return new VerifiedAppStoreNotification(
                payload.getNotificationUUID(),
                stringOrRaw(payload.getRawNotificationType(), payload.getNotificationType()),
                stringOrRaw(payload.getRawSubtype(), payload.getSubtype()),
                environment,
                data == null ? null : data.getRawStatus(),
                instant(payload.getSignedDate()),
                transaction,
                renewalInfo
            );
        } catch (VerificationException exception) {
            throw new AppStoreVerificationFailedException("Apple notification JWS verification failed", exception);
        }
    }

    private static VerifiedAppStoreTransaction mapTransaction(JWSTransactionDecodedPayload payload) {
        return new VerifiedAppStoreTransaction(
            payload.getTransactionId(),
            payload.getOriginalTransactionId(),
            payload.getProductId(),
            payload.getSubscriptionGroupIdentifier(),
            payload.getAppAccountToken(),
            toDomainEnvironment(payload.getEnvironment()),
            instant(payload.getPurchaseDate()),
            instant(payload.getOriginalPurchaseDate()),
            instant(payload.getExpiresDate()),
            instant(payload.getRevocationDate()),
            instant(payload.getSignedDate()),
            stringOrRaw(payload.getRawTransactionReason(), payload.getTransactionReason()),
            stringOrRaw(payload.getRawInAppOwnershipType(), payload.getInAppOwnershipType())
        );
    }

    private static VerifiedAppStoreRenewalInfo mapRenewal(JWSRenewalInfoDecodedPayload payload) {
        return new VerifiedAppStoreRenewalInfo(
            payload.getOriginalTransactionId(),
            payload.getProductId(),
            payload.getAutoRenewProductId(),
            payload.getAutoRenewStatus() == null ? null : payload.getAutoRenewStatus() == AutoRenewStatus.ON,
            payload.getIsInBillingRetryPeriod(),
            instant(payload.getGracePeriodExpiresDate()),
            instant(payload.getRenewalDate()),
            instant(payload.getSignedDate())
        );
    }

    private static Environment toAppleEnvironment(AppStoreEnvironment environment) {
        return switch (environment) {
            case XCODE -> Environment.XCODE;
            case LOCAL_TESTING -> Environment.LOCAL_TESTING;
            case SANDBOX -> Environment.SANDBOX;
            case PRODUCTION -> Environment.PRODUCTION;
        };
    }

    private static AppStoreEnvironment toDomainEnvironment(Environment environment) {
        if (environment == null) {
            return AppStoreEnvironment.PRODUCTION;
        }
        return switch (environment) {
            case XCODE -> AppStoreEnvironment.XCODE;
            case LOCAL_TESTING -> AppStoreEnvironment.LOCAL_TESTING;
            case SANDBOX -> AppStoreEnvironment.SANDBOX;
            case PRODUCTION -> AppStoreEnvironment.PRODUCTION;
        };
    }

    private static Instant instant(Long epochMillis) {
        return epochMillis == null ? null : Instant.ofEpochMilli(epochMillis);
    }

    private static String stringOrRaw(String raw, Object value) {
        if (raw != null && !raw.isBlank()) {
            return raw;
        }
        return value == null ? null : value.toString();
    }
}
