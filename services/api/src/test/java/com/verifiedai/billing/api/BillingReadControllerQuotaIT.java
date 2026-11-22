package com.verifiedai.billing.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.verifiedai.billing.application.EffectiveEntitlementAccess;
import com.verifiedai.billing.application.FairUsePolicy;
import com.verifiedai.billing.application.QuotaLedgerService;
import com.verifiedai.billing.application.QuotaOperationType;
import com.verifiedai.billing.application.QuotaSummary;
import com.verifiedai.identity.application.AppleSignInCommand;
import com.verifiedai.identity.application.AuthSessionResult;
import com.verifiedai.identity.application.IdentityApplicationService;
import com.verifiedai.identity.domain.model.AppleIdentityVerifier;
import com.verifiedai.identity.domain.model.VerifiedAppleIdentity;
import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.sharedkernel.privacy.AccountDataLifecycleContributor;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

@Import(BillingReadControllerQuotaIT.AuthTestConfiguration.class)
@TestPropertySource(properties = {
    "verifiedai.billing.learning-os-tiers-enabled=true",
    "verifiedai.billing.quota-ledger-enabled=true"
})
final class BillingReadControllerQuotaIT extends PostgresIntegrationTestSupport {

    @Value("${local.server.port}")
    int port;

    @Autowired
    IdentityApplicationService identityApplicationService;

    @Autowired
    QuotaLedgerService quotaLedgerService;

    @Autowired
    List<AccountDataLifecycleContributor> lifecycleContributors;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.execute("""
            truncate table
                entitlement_quota_consumptions,
                entitlement_quota_ledgers,
                billing_entitlement_transition_audit,
                billing_events,
                app_store_notifications,
                app_store_subscriptions,
                app_store_transactions,
                commerce_account_tokens,
                entitlements,
                learning_profiles,
                auth_security_events,
                refresh_tokens,
                sessions,
                user_identities,
                users
            cascade
            """);
    }

    @Test
    void entitlementEndpointsReturnLiveQuotaSummaryAndPreserveLegacyContract() throws Exception {
        AuthSessionResult session = signIn("quota-api-user");

        String initialV1 = null;
        for (String prefix : new String[]{"/v1", "/api/v1"}) {
            HttpResponse<String> response = httpClient.send(
                authorized(session, prefix + "/billing/entitlements").GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body())
                .contains("\"quotaSummary\":{")
                .contains("\"aiVerificationsUsed\":0")
                .contains("\"aiVerificationsLimit\":3")
                .contains("\"remainingAiChecks\":3")
                .contains("\"deterministicTwinsUnlimited\":true")
                .contains("\"costCircuitBreakerTripped\":false")
                .contains("\"degradedToDeterministicOnly\":false");
            String normalized = response.body().replaceAll("\"accessEvaluatedAt\":\"[^\"]+\"", "\"accessEvaluatedAt\":\"T\"");
            if (initialV1 != null) {
                assertThat(normalized).isEqualTo(initialV1);
            }
            initialV1 = normalized;
        }

        EffectiveEntitlementAccess freeAccess = FairUsePolicy.syntheticAccessForTier("FREE", Instant.now());
        for (int i = 0; i < 3; i++) {
            var decision = quotaLedgerService.reserveAndConsume(
                session.userId(), freeAccess, QuotaOperationType.AI_VERIFICATION, "api-use-" + i, 5_000L
            );
            assertThat(decision.allowed()).isTrue();
        }

        HttpResponse<String> exhausted = httpClient.send(
            authorized(session, "/v1/billing/entitlements").GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertThat(exhausted.statusCode()).isEqualTo(200);
        assertThat(exhausted.body())
            .contains("\"aiVerificationsUsed\":3")
            .contains("\"remainingAiChecks\":0")
            .contains("\"deterministicTwinsUnlimited\":true")
            .contains("\"degradedToDeterministicOnly\":true");

        // Legacy endpoint remains strictly unchanged
        HttpResponse<String> legacy = httpClient.send(
            authorized(session, "/api/v1/me/entitlements").GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertThat(legacy.statusCode()).isEqualTo(200);
        assertThat(legacy.body())
            .contains("\"tier\":\"FREE\"")
            .contains("\"BASIC_SOLVE\"")
            .doesNotContain("quotaSummary")
            .doesNotContain("effectiveTier");
    }

    @Test
    void gdprExportAndDeletionIsolateQuotaLedgersAndConsumptionsBetweenUsers() {
        AuthSessionResult owner = signIn("quota-gdpr-owner");
        AuthSessionResult other = signIn("quota-gdpr-other");
        EffectiveEntitlementAccess freeAccess = FairUsePolicy.syntheticAccessForTier("FREE", Instant.now());

        quotaLedgerService.reserveAndConsume(
            owner.userId(), freeAccess, QuotaOperationType.AI_VERIFICATION, "owner-idem-1", 4_000L
        );
        quotaLedgerService.reserveAndConsume(
            other.userId(), freeAccess, QuotaOperationType.AI_VERIFICATION, "other-idem-1", 4_000L
        );

        AccountDataLifecycleContributor billingLifecycle = lifecycleContributors.stream()
            .filter(c -> "billing".equals(c.category()))
            .findFirst()
            .orElseThrow();

        Map<String, Object> exportedOwner = billingLifecycle.exportUserData(owner.userId());
        assertThat(exportedOwner.get("quotaLedgers")).asList().hasSize(1);
        assertThat(exportedOwner.get("quotaConsumptions")).asList().hasSize(1);
        assertThat(exportedOwner.toString())
            .contains("owner-idem-1")
            .doesNotContain("other-idem-1");

        billingLifecycle.deleteUserData(owner.userId(), Instant.now());

        Integer ownerLedgers = jdbcTemplate.queryForObject(
            "select count(*) from entitlement_quota_ledgers where user_id = ?",
            Integer.class,
            owner.userId()
        );
        Integer ownerConsumptions = jdbcTemplate.queryForObject(
            "select count(*) from entitlement_quota_consumptions where user_id = ?",
            Integer.class,
            owner.userId()
        );
        Integer otherLedgers = jdbcTemplate.queryForObject(
            "select count(*) from entitlement_quota_ledgers where user_id = ?",
            Integer.class,
            other.userId()
        );
        Integer otherConsumptions = jdbcTemplate.queryForObject(
            "select count(*) from entitlement_quota_consumptions where user_id = ?",
            Integer.class,
            other.userId()
        );
        assertThat(ownerLedgers).isZero();
        assertThat(ownerConsumptions).isZero();
        assertThat(otherLedgers).isEqualTo(1);
        assertThat(otherConsumptions).isEqualTo(1);
    }

    @Test
    void disabledQuotaFlagReturnsDeterministicSummaryWithZeroDbWrites() {
        Instant fixed = Instant.parse("2026-11-22T12:00:00Z");
        Clock fixedClock = Clock.fixed(fixed, ZoneOffset.UTC);
        QuotaLedgerService disabledService = new QuotaLedgerService(
            jdbcTemplate,
            transactionManager,
            new FairUsePolicy(fixedClock),
            new SimpleMeterRegistry(),
            fixedClock,
            false
        );

        UUID userId = signIn("quota-flag-off-user").userId();
        QuotaSummary summary = disabledService.getOrInitializeSummary(
            userId,
            FairUsePolicy.syntheticAccessForTier("FREE", fixed)
        );
        assertThat(summary.periodKey()).isEqualTo("2026-11-22");
        assertThat(summary.remainingAiChecks()).isEqualTo(3);
        assertThat(summary.deterministicTwinsUnlimited()).isTrue();
        assertThat(jdbcTemplate.queryForObject("select count(*) from entitlement_quota_ledgers", Integer.class)).isZero();
    }

    private AuthSessionResult signIn(String subject) {
        return identityApplicationService.signInWithApple(new AppleSignInCommand(subject, "unused-code", "nonce"));
    }

    private HttpRequest.Builder authorized(AuthSessionResult session, String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Authorization", "Bearer " + session.accessToken())
            .header("Accept", "application/json");
    }

    @TestConfiguration
    static class AuthTestConfiguration {
        @Bean
        @Primary
        AppleIdentityVerifier appleIdentityVerifier() {
            return (identityToken, rawNonce) -> new VerifiedAppleIdentity(identityToken);
        }
    }
}
