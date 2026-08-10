package com.verifiedai.billing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.verifiedai.billing.domain.model.EntitlementStatus;
import com.verifiedai.billing.domain.model.EntitlementTier;
import com.verifiedai.billing.domain.model.PremiumCapability;
import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

final class EntitlementApplicationServiceTest extends PostgresIntegrationTestSupport {

    @Autowired
    EntitlementApplicationService entitlementApplicationService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.execute("truncate table entitlements, learning_profiles, auth_security_events, refresh_tokens, sessions, user_identities, users cascade");
    }

    @Test
    void currentEntitlementCreatesDefaultFreeAccess() {
        UUID userId = insertUser();

        EntitlementResult result = entitlementApplicationService.getCurrent(userId);

        assertThat(result.tier()).isEqualTo(EntitlementTier.FREE);
        assertThat(result.status()).isEqualTo(EntitlementStatus.ACTIVE);
        assertThat(result.capabilities()).containsExactly(PremiumCapability.BASIC_SOLVE);
        assertThat(count("entitlements")).isEqualTo(1);
    }

    @Test
    void accessPolicyAllowsFreeAndDeniesProtectedCapabilities() {
        UUID userId = insertUser();

        EntitlementResult allowed = entitlementApplicationService.requireCapability(userId, PremiumCapability.BASIC_SOLVE);

        assertThat(allowed.tier()).isEqualTo(EntitlementTier.FREE);
        assertThatThrownBy(() -> entitlementApplicationService.requireCapability(userId, PremiumCapability.VERIFIED_SOLVE))
            .isInstanceOf(ApiProblemException.class)
            .extracting(exception -> ((ApiProblemException) exception).code())
            .isEqualTo(ApiErrorCode.ENTITLEMENT_REQUIRED);
        assertThat(count("entitlements")).isEqualTo(1);
    }

    @Test
    void explicitLifecycleStatusesDriveAccessWithoutBooleanShortcuts() {
        UUID userId = insertUser();
        insertEntitlement(userId, "PRO", "APP_STORE_SUBSCRIPTION", "GRACE_PERIOD");

        EntitlementResult grace = entitlementApplicationService.requireCapability(userId, PremiumCapability.VERIFIED_SOLVE);
        assertThat(grace.status()).isEqualTo(EntitlementStatus.GRACE_PERIOD);

        jdbcTemplate.update("update entitlements set status = 'BILLING_RETRY' where user_id = ?", userId);
        EntitlementResult retry = entitlementApplicationService.requireCapability(userId, PremiumCapability.VERIFIED_SOLVE);
        assertThat(retry.status()).isEqualTo(EntitlementStatus.BILLING_RETRY);

        jdbcTemplate.update("update entitlements set status = 'EXPIRED' where user_id = ?", userId);
        assertThatThrownBy(() -> entitlementApplicationService.requireCapability(userId, PremiumCapability.VERIFIED_SOLVE))
            .isInstanceOf(ApiProblemException.class)
            .extracting(exception -> ((ApiProblemException) exception).code())
            .isEqualTo(ApiErrorCode.ENTITLEMENT_REQUIRED);

        jdbcTemplate.update("update entitlements set tier = 'PRO_PLUS', status = 'ACTIVE' where user_id = ?", userId);
        EntitlementResult proPlus = entitlementApplicationService.requireCapability(userId, PremiumCapability.MOCK_EXAM);
        assertThat(proPlus.tier()).isEqualTo(EntitlementTier.PRO_PLUS);
    }

    @Test
    void concurrentDefaultInitializationCreatesOneRow() throws Exception {
        UUID userId = insertUser();
        var executor = Executors.newFixedThreadPool(2);
        var barrier = new CyclicBarrier(2);
        try {
            var futures = new ArrayList<Future<EntitlementResult>>();
            for (int index = 0; index < 2; index++) {
                futures.add(executor.submit(() -> {
                    barrier.await();
                    return entitlementApplicationService.getCurrent(userId);
                }));
            }

            futures.get(0).get();
            futures.get(1).get();

            assertThat(count("entitlements")).isEqualTo(1);
            assertThat(countWhere("entitlements", "user_id = '" + userId + "'")).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentBasicSolveAccessCreatesOneDefaultEntitlementRow()
        throws Exception {

        UUID userId = insertUser();

        var executor =
            Executors.newFixedThreadPool(2);

        var barrier =
            new CyclicBarrier(2);

        try {
            var futures =
                new ArrayList<Future<Void>>();

            for (int index = 0; index < 2; index++) {
                futures.add(
                    executor.submit(() -> {
                        barrier.await();

                        entitlementApplicationService
                            .requireBasicSolve(
                                userId
                            );

                        return null;
                    })
                );
            }

            futures.get(0).get();
            futures.get(1).get();

            assertThat(
                count("entitlements")
            ).isEqualTo(1);

            assertThat(
                countWhere(
                    "entitlements",
                    "user_id = '" + userId + "'"
                )
            ).isEqualTo(1);

            EntitlementResult result =
                entitlementApplicationService
                    .getCurrent(userId);

            assertThat(
                result.tier()
            ).isEqualTo(
                EntitlementTier.FREE
            );

            assertThat(
                result.status()
            ).isEqualTo(
                EntitlementStatus.ACTIVE
            );

            assertThat(
                result.capabilities()
            ).contains(
                PremiumCapability.BASIC_SOLVE
            );

        } finally {
            executor.shutdownNow();
        }
    }

    private UUID insertUser() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-08T00:00:00Z");
        jdbcTemplate.update(
            "insert into users (id, status, created_at, updated_at) values (?, 'ACTIVE', ?, ?)",
            userId,
            Timestamp.from(now),
            Timestamp.from(now)
        );
        return userId;
    }

    private void insertEntitlement(UUID userId, String tier, String source, String status) {
        Instant now = Instant.parse("2026-08-08T00:00:00Z");
        jdbcTemplate.update(
            """
            insert into entitlements (
                id, user_id, tier, source, status, effective_at, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            UUID.randomUUID(),
            userId,
            tier,
            source,
            status,
            Timestamp.from(now),
            Timestamp.from(now),
            Timestamp.from(now)
        );
    }

    private Integer count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
    }

    private Integer countWhere(String table, String predicate) {
        return jdbcTemplate.queryForObject("select count(*) from " + table + " where " + predicate, Integer.class);
    }
}
