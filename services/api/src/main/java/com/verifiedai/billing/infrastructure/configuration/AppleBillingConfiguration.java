package com.verifiedai.billing.infrastructure.configuration;

import com.verifiedai.billing.domain.port.AppStoreGatewayUnavailableException;
import com.verifiedai.billing.domain.port.AppStoreServerGateway;
import com.verifiedai.billing.domain.port.AppStoreSignedDataVerifier;
import com.verifiedai.billing.domain.port.AppStoreSubscriptionStatusRecord;
import com.verifiedai.billing.domain.port.AppStoreVerificationFailedException;
import java.util.List;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@EnableConfigurationProperties(AppleBillingProperties.class)
class AppleBillingConfiguration {

    @Bean
    ApplicationRunner appleBillingConfigurationValidator(AppleBillingProperties properties, Environment environment) {
        return arguments -> {
            if (!properties.enabled()) {
                return;
            }
            if (isStrictEnvironment(environment) || properties.strictServerConfigurationRequired()) {
                require("APPLE_BILLING_BUNDLE_ID", properties.bundleId());
                require("APP_STORE_CONNECT_ISSUER_ID", properties.issuerId());
                require("APP_STORE_CONNECT_KEY_ID", properties.keyId());
                require("APP_STORE_CONNECT_PRIVATE_KEY_PEM", properties.privateKeyPem());
                if (properties.environment() == com.verifiedai.billing.domain.model.AppStoreEnvironment.PRODUCTION
                    && properties.normalizedAppAppleId() == null) {
                    throw new IllegalStateException("APPLE_APP_APPLE_ID is required when Apple billing runs in production");
                }
                if (properties.rootCertificatePem().isEmpty()) {
                    throw new IllegalStateException("APPLE_ROOT_CERTIFICATE_PEM is required when Apple billing is enabled");
                }
            }
            if (properties.purchaseAvailable() && properties.products().isEmpty()) {
                throw new IllegalStateException("At least one App Store product mapping is required when purchase availability is enabled");
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(AppStoreSignedDataVerifier.class)
    AppStoreSignedDataVerifier unavailableSignedDataVerifier() {
        return new AppStoreSignedDataVerifier() {
            @Override
            public com.verifiedai.billing.domain.model.VerifiedAppStoreTransaction verifyTransaction(String signedTransactionInfo) {
                throw new AppStoreVerificationFailedException("Apple signed-data verifier is not configured");
            }

            @Override
            public com.verifiedai.billing.domain.model.VerifiedAppStoreRenewalInfo verifyRenewalInfo(String signedRenewalInfo) {
                throw new AppStoreVerificationFailedException("Apple signed-data verifier is not configured");
            }

            @Override
            public com.verifiedai.billing.domain.model.VerifiedAppStoreNotification verifyNotification(String signedPayload) {
                throw new AppStoreVerificationFailedException("Apple signed-data verifier is not configured");
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(AppStoreServerGateway.class)
    AppStoreServerGateway unavailableAppStoreServerGateway() {
        return new AppStoreServerGateway() {
            @Override
            public String getTransactionInfo(String transactionId) {
                throw new AppStoreGatewayUnavailableException("App Store Server API gateway is not configured");
            }

            @Override
            public List<String> getTransactionHistory(String anyTransactionId) {
                throw new AppStoreGatewayUnavailableException("App Store Server API gateway is not configured");
            }

            @Override
            public List<AppStoreSubscriptionStatusRecord> getAllSubscriptionStatuses(String originalTransactionId) {
                throw new AppStoreGatewayUnavailableException("App Store Server API gateway is not configured");
            }

            @Override
            public String requestTestNotification() {
                throw new AppStoreGatewayUnavailableException("App Store Server API gateway is not configured");
            }

            @Override
            public String getTestNotificationStatus(String testNotificationToken) {
                throw new AppStoreGatewayUnavailableException("App Store Server API gateway is not configured");
            }
        };
    }

    private static void require(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required when Apple billing is enabled");
        }
    }

    private static boolean isStrictEnvironment(Environment environment) {
        String appEnvironment = environment.getProperty("app.environment", "local");
        List<String> activeProfiles = List.of(environment.getActiveProfiles());
        return "prod".equalsIgnoreCase(appEnvironment)
            || "production".equalsIgnoreCase(appEnvironment)
            || "staging".equalsIgnoreCase(appEnvironment)
            || activeProfiles.contains("prod")
            || activeProfiles.contains("staging");
    }
}
