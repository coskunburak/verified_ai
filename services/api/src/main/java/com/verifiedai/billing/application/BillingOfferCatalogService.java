package com.verifiedai.billing.application;

import com.verifiedai.billing.infrastructure.configuration.AppleBillingProperties;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@EnableConfigurationProperties(BillingOfferCatalogService.Settings.class)
public class BillingOfferCatalogService {
    @ConfigurationProperties("verifiedai.billing")
    public record Settings(String contextualPaywallEnabled) {
        boolean enabled() { return "true".equalsIgnoreCase(contextualPaywallEnabled); }
    }

    public record Offer(String offerCode, String productId, String family, String billingPeriod, String copyKey) {}
    public record Catalog(String locale, boolean enabled, String reason, List<Offer> offers) {}

    private final io.micrometer.core.instrument.MeterRegistry metrics;
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(BillingOfferCatalogService.class);
    private final Settings settings;
    private final AppleBillingProperties apple;
    private final JdbcTemplate database;

    BillingOfferCatalogService(Settings settings, AppleBillingProperties apple, JdbcTemplate database,
                              io.micrometer.core.instrument.MeterRegistry metrics) {
        this.metrics = metrics;
        this.settings = settings;
        this.apple = apple;
        this.database = database;
    }

    public boolean recordInteraction(String kind) {
        switch (kind) {
            case "paywall_viewed" -> {
                if (!settings.enabled() || !apple.enabled() || !apple.purchaseAvailable()) return false;
            }
            case "restore_purchases_tapped" -> { }
            default -> throw new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.REQUEST_VALIDATION_FAILED,
                "Unknown billing interaction", false, "NONE");
        }
        metrics.counter("billing." + kind).increment();
        return true;
    }

    public Catalog offers(String locale) {
        if (!Set.of("tr-TR", "en-US").contains(locale)) {
            throw new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.REQUEST_VALIDATION_FAILED,
                "Unsupported catalog locale", true, "CHANGE_LOCALE");
        }
        if (!settings.enabled()) return new Catalog(locale, false, "FEATURE_DISABLED", List.of());
        if (!apple.enabled() || !apple.purchaseAvailable()) return new Catalog(locale, false, "UNAVAILABLE", List.of());
        try {
            List<Offer> offers = currentOffers();
            return new Catalog(locale, !offers.isEmpty(), offers.isEmpty() ? "UNAVAILABLE" : "AVAILABLE", offers);
        } catch (org.springframework.dao.DataAccessException ex) {
            LOG.warn("Billing offer catalog unavailable");
            return new Catalog(locale, false, "UNAVAILABLE", List.of());
        }
    }

    private List<Offer> currentOffers() {
        return database.query("""
            select offer_code, product_id, family, billing_period from billing_offer_catalog
            where enabled and (starts_at is null or starts_at <= current_timestamp)
              and (ends_at is null or ends_at > current_timestamp) order by offer_code
            """, (row, index) -> new Offer(row.getString(1), row.getString(2), row.getString(3), row.getString(4),
                "billing.plan." + row.getString(3).toLowerCase(java.util.Locale.ROOT)))
            .stream().filter(this::matchesVerifiedConfiguration).toList();
    }

    private boolean matchesVerifiedConfiguration(Offer offer) {
        var matches = apple.products().stream().filter(p -> offer.productId().equals(p.appStoreProductId())).toList();
        if (matches.size() != 1) return false;
        var mapping = matches.get(0);
        return mapping.entitlementTier() != null && offer.billingPeriod().equals(mapping.billingPeriod())
            && offer.offerCode().equals(mapping.internalPlanId())
            && TierResolutionPolicy.presentation(mapping.entitlementTier()).equals(
                offer.family().equals("YKS_SEASON_PASS") ? "COACH_PRO" : offer.family());
    }
}
