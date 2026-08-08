package com.verifiedai.billing.infrastructure.configuration;

import com.verifiedai.billing.domain.model.AppStoreEnvironment;
import com.verifiedai.billing.domain.model.EntitlementTier;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.billing.apple")
public record AppleBillingProperties(
    boolean enabled,
    boolean purchaseAvailable,
    AppStoreEnvironment environment,
    String bundleId,
    Long appAppleId,
    String issuerId,
    String keyId,
    String privateKeyPem,
    List<String> rootCertificatePem,
    List<Product> products
) {
    public AppleBillingProperties {
        environment = environment == null ? AppStoreEnvironment.XCODE : environment;
        rootCertificatePem = rootCertificatePem == null ? List.of() : List.copyOf(rootCertificatePem);
        products = products == null ? List.of() : List.copyOf(products);
    }

    public record Product(
        String internalPlanId,
        String appStoreProductId,
        EntitlementTier entitlementTier,
        String subscriptionGroupId,
        String billingPeriod
    ) {
    }

    public boolean strictServerConfigurationRequired() {
        return enabled && (environment == AppStoreEnvironment.SANDBOX || environment == AppStoreEnvironment.PRODUCTION);
    }

    public Long normalizedAppAppleId() {
        return appAppleId == null || appAppleId <= 0 ? null : appAppleId;
    }
}
