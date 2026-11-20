package com.verifiedai.billing.application;

import com.verifiedai.billing.domain.model.EntitlementTier;
import com.verifiedai.integration.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.BeforeEach;
import com.verifiedai.billing.infrastructure.configuration.AppleBillingProperties;
import com.verifiedai.billing.domain.model.AppStoreEnvironment;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@Transactional
class BillingOfferCatalogTest extends PostgresIntegrationTestSupport {
    BillingOfferCatalogService service;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void configureLocalCatalog() {
        var item = new AppleBillingProperties.Product("pro_monthly", "local_dev_monthly", EntitlementTier.PRO, "local_dev_group", "P1M");
        configure(List.of(item));
    }

    private void configure(List<AppleBillingProperties.Product> products) {
        var apple = new AppleBillingProperties(true, true, AppStoreEnvironment.XCODE, "local_dev", null,
            null, null, null, List.of(), products);
        service = new BillingOfferCatalogService(new BillingOfferCatalogService.Settings("true"), apple, jdbc, new SimpleMeterRegistry());
    }

    @Test void duplicateConfigurationIsSuppressed() {
        var item = new AppleBillingProperties.Product("pro_monthly", "local_dev_monthly", EntitlementTier.PRO, null, "P1M");
        configure(List.of(item, item));
        jdbc.update("update billing_offer_catalog set enabled=true, product_id='local_dev_monthly' where offer_code='pro_monthly'");
        assertThat(service.offers("en-US").offers()).isEmpty();
    }

    @Test void requiresConfiguredProductAndCurrentWindow() {
        assertThat(service.offers("tr-TR").offers()).isEmpty();
        jdbc.update("update billing_offer_catalog set enabled=true, product_id='local_dev_monthly' where offer_code='pro_monthly'");
        assertThat(service.offers("tr-TR").offers()).hasSize(1);
        assertThat(service.offers("en-US").offers()).isEqualTo(service.offers("tr-TR").offers());
        jdbc.update("update billing_offer_catalog set ends_at=now() where offer_code='pro_monthly'");
        assertThat(service.offers("en-US").enabled()).isFalse();
    }

    @Test void unmatchedProductStaysHidden() {
        jdbc.update("update billing_offer_catalog set enabled=true, product_id='local_dev_unknown' where offer_code='coach_annual'");
        assertThat(service.offers("tr-TR").reason()).isEqualTo("UNAVAILABLE");
    }

    @Test void unsafeTrainingFlagFailsAtDatabase() {
        assertThatThrownBy(() -> jdbc.update("update billing_offer_catalog set training_eligible=true"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void duplicateProductFailsAtDatabase() {
        assertThatThrownBy(() -> jdbc.update("update billing_offer_catalog set product_id='local_dev_duplicate'"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void seasonCannotBeAnAnnualOffer() {
        assertThatThrownBy(() -> jdbc.update("update billing_offer_catalog set billing_period='P1Y' where family='YKS_SEASON_PASS'"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void malformedOrAbsentFlagFailsClosed() {
        assertThat(new BillingOfferCatalogService.Settings(null).enabled()).isFalse();
        assertThat(new BillingOfferCatalogService.Settings("typo").enabled()).isFalse();
    }

    @Test void mapsStorageTiersWithoutRewritingThem() {
        assertThat(TierResolutionPolicy.storageTier("COACH_PRO")).isEqualTo(EntitlementTier.PRO_PLUS);
        assertThat(TierResolutionPolicy.presentation(EntitlementTier.PRO_PLUS)).isEqualTo("COACH_PRO");
        assertThat(TierResolutionPolicy.presentation(EntitlementTier.PRO)).isEqualTo("PRO");
        assertThat(TierResolutionPolicy.presentation(EntitlementTier.FREE)).isEqualTo("FREE");
    }
}
