package com.verifiedai.billing.application;

import com.verifiedai.billing.domain.model.AppStoreEnvironment;
import com.verifiedai.billing.domain.model.AppStoreProductMapping;
import com.verifiedai.billing.infrastructure.configuration.AppleBillingProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class AppleProductCatalog {
    private final AppleBillingProperties properties;
    private final List<AppStoreProductMapping> products;
    private final Map<String, AppStoreProductMapping> byAppStoreProductId;

    AppleProductCatalog(AppleBillingProperties properties) {
        this.properties = properties;
        this.products = properties.products().stream()
            .filter(product -> product.appStoreProductId() != null && !product.appStoreProductId().isBlank())
            .map(product -> new AppStoreProductMapping(
                product.internalPlanId(),
                product.appStoreProductId(),
                product.entitlementTier(),
                product.subscriptionGroupId(),
                product.billingPeriod()
            ))
            .toList();
        Map<String, AppStoreProductMapping> mappings = new LinkedHashMap<>();
        for (AppStoreProductMapping product : products) {
            mappings.put(product.appStoreProductId(), product);
        }
        this.byAppStoreProductId = Map.copyOf(mappings);
    }

    AppleBillingConfigurationResult configurationFor(java.util.UUID appAccountToken) {
        return new AppleBillingConfigurationResult(
            appAccountToken,
            properties.purchaseAvailable() && !products.isEmpty(),
            properties.environment(),
            products.stream()
                .map(product -> new AppleBillingProductResult(
                    product.internalPlanId(),
                    product.appStoreProductId(),
                    product.entitlementTier(),
                    product.subscriptionGroupId(),
                    product.billingPeriod()
                ))
                .toList()
        );
    }

    Optional<AppStoreProductMapping> findByAppStoreProductId(String appStoreProductId) {
        return Optional.ofNullable(byAppStoreProductId.get(appStoreProductId));
    }

    AppStoreEnvironment environment() {
        return properties.environment();
    }
}
