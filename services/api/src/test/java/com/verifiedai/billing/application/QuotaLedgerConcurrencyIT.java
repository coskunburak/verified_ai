package com.verifiedai.billing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.verifiedai.ai.application.AiCapability;
import com.verifiedai.ai.application.AiCapabilityRequest;
import com.verifiedai.ai.application.AiExecutionCommand;
import com.verifiedai.ai.application.AiExecutionContext;
import com.verifiedai.ai.application.AiProviderException;
import com.verifiedai.ai.application.AiRouteContext;
import com.verifiedai.ai.application.AiRoutePlan;
import com.verifiedai.ai.application.AiRouteTarget;
import com.verifiedai.ai.application.AiUsage;
import com.verifiedai.ai.application.AiUsageRecord;
import com.verifiedai.ai.infrastructure.configuration.AiBudgetProperties;
import com.verifiedai.ai.infrastructure.persistence.AiUsageRecordJpaEntity;
import com.verifiedai.ai.infrastructure.persistence.AiUsageRecordJpaRepository;
import com.verifiedai.ai.infrastructure.persistence.PostgresAiSpendGuard;
import com.verifiedai.integration.PostgresIntegrationTestSupport;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@TestPropertySource(properties = {
    "verifiedai.billing.quota-ledger-enabled=true",
    "verifiedai.billing.learning-os-tiers-enabled=true",
    "spring.datasource.hikari.maximum-pool-size=25",
    "spring.datasource.hikari.connection-timeout=10000"
})
final class QuotaLedgerConcurrencyIT extends PostgresIntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-11-22T12:00:00Z");

    @Autowired
    QuotaLedgerService quotaLedgerService;

    @Autowired
    EntitlementApplicationService entitlementApplicationService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    AiUsageRecordJpaRepository usageLedgerRepository;

    @Autowired
    MeterRegistry meterRegistry;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.execute("""
            truncate table
                entitlement_quota_consumptions,
                entitlement_quota_ledgers,
                ai_spend_allocations,
                ai_spend_attempts,
                ai_spend_accounts,
                ai_usage_records,
                entitlements,
                users
            cascade
            """);
    }

    @Test
    void twentyParallelThreadsRespectFreeDailyLimitOfThreeAndDeterministicExemption() throws Exception {
        UUID userId = insertUser();
        EffectiveEntitlementAccess freeAccess = FairUsePolicy.syntheticAccessForTier("FREE", NOW);

        int threads = 20;
        var ready = new CountDownLatch(threads);
        var go = new CountDownLatch(1);
        List<Future<QuotaConsumptionDecision>> futures = new ArrayList<>();
        try (var pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                final String idemKey = "free-parallel-" + i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await(5, TimeUnit.SECONDS);
                    return quotaLedgerService.reserveAndConsume(
                        userId,
                        freeAccess,
                        QuotaOperationType.AI_VERIFICATION,
                        idemKey,
                        5_000L
                    );
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            int allowed = 0;
            int degraded = 0;
            for (var future : futures) {
                QuotaConsumptionDecision d = future.get(15, TimeUnit.SECONDS);
                if (d.allowed()) {
                    allowed++;
                } else {
                    degraded++;
                    assertThat(d.degradedToDeterministicOnly()).isTrue();
                }
            }
            assertThat(allowed).isEqualTo(3);
            assertThat(degraded).isEqualTo(17);
        }

        Integer used = jdbcTemplate.queryForObject(
            "select ai_verifications_used from entitlement_quota_ledgers where user_id = ?",
            Integer.class,
            userId
        );
        Integer consumptions = jdbcTemplate.queryForObject(
            "select count(*) from entitlement_quota_consumptions where user_id = ?",
            Integer.class,
            userId
        );
        assertThat(used).isEqualTo(3);
        assertThat(consumptions).isEqualTo(3);

        // INV-QUOTA-001: deterministic twin and symbolic verify remain allowed even when exhausted
        QuotaConsumptionDecision twinDecision = quotaLedgerService.reserveAndConsume(
            userId, freeAccess, QuotaOperationType.DETERMINISTIC_TWIN, "unused-key", 0L
        );
        assertThat(twinDecision.allowed()).isTrue();
        assertThat(twinDecision.deterministicExempt()).isTrue();
        assertThat(twinDecision.summary().deterministicTwinsUnlimited()).isTrue();
    }

    @Test
    void twentyParallelThreadsRespectProMonthlyAiCostCircuitBreaker() throws Exception {
        UUID userId = insertUser();
        EffectiveEntitlementAccess proAccess = FairUsePolicy.syntheticAccessForTier("PRO", NOW);

        int threads = 20;
        var ready = new CountDownLatch(threads);
        var go = new CountDownLatch(1);
        List<Future<QuotaConsumptionDecision>> futures = new ArrayList<>();
        try (var pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                final String idemKey = "pro-breaker-" + i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await(5, TimeUnit.SECONDS);
                    return quotaLedgerService.reserveAndConsume(
                        userId,
                        proAccess,
                        QuotaOperationType.AI_VERIFICATION,
                        idemKey,
                        500_000L
                    );
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            int allowed = 0;
            int breakerBlocked = 0;
            for (var future : futures) {
                QuotaConsumptionDecision d = future.get(15, TimeUnit.SECONDS);
                if (d.allowed()) {
                    allowed++;
                } else if (d.costCircuitBreakerTripped()) {
                    breakerBlocked++;
                }
            }
            assertThat(allowed).isEqualTo(5);
            assertThat(breakerBlocked).isEqualTo(15);
        }

        Map<String, Object> row = jdbcTemplate.queryForMap(
            """
            select ai_verifications_used, accumulated_ai_cost_micros, cost_circuit_breaker_tripped
            from entitlement_quota_ledgers
            where user_id = ?
            """,
            userId
        );
        assertThat(((Number) row.get("ai_verifications_used")).intValue()).isEqualTo(5);
        assertThat(((Number) row.get("accumulated_ai_cost_micros")).longValue()).isEqualTo(2_500_000L);
        assertThat((Boolean) row.get("cost_circuit_breaker_tripped")).isTrue();
        assertThat(meterRegistry.find("user_ai_cost_circuit_breaker_tripped").counter()).isNotNull();
        assertThat(meterRegistry.find("user_ai_cost_circuit_breaker_tripped").counter().count()).isGreaterThanOrEqualTo(1.0);
    }

    @Test
    void twentyParallelThreadsWithSameIdempotencyKeyConsumeExactlyOnce() throws Exception {
        UUID userId = insertUser();
        EffectiveEntitlementAccess coachAccess = FairUsePolicy.syntheticAccessForTier("COACH_PRO", NOW);
        String sharedKey = "same-idem-key-1";

        int threads = 20;
        var ready = new CountDownLatch(threads);
        var go = new CountDownLatch(1);
        List<Future<QuotaConsumptionDecision>> futures = new ArrayList<>();
        try (var pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await(5, TimeUnit.SECONDS);
                    return quotaLedgerService.reserveAndConsume(
                        userId,
                        coachAccess,
                        QuotaOperationType.HYBRID_TWIN,
                        sharedKey,
                        10_000L
                    );
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            int freshAllowed = 0;
            int replays = 0;
            for (var future : futures) {
                QuotaConsumptionDecision d = future.get(15, TimeUnit.SECONDS);
                assertThat(d.allowed()).isTrue();
                if (d.idempotentReplay()) {
                    replays++;
                } else {
                    freshAllowed++;
                }
            }
            assertThat(freshAllowed).isEqualTo(1);
            assertThat(replays).isEqualTo(19);
        }

        Integer hybridUsed = jdbcTemplate.queryForObject(
            "select hybrid_twins_used from entitlement_quota_ledgers where user_id = ?",
            Integer.class,
            userId
        );
        Integer rows = jdbcTemplate.queryForObject(
            "select count(*) from entitlement_quota_consumptions where user_id = ?",
            Integer.class,
            userId
        );
        assertThat(hybridUsed).isEqualTo(1);
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void freeMonthlyCapAggregatesAcrossMultipleDaysAndReconcileReleaseAdjustsState() {
        UUID userId = insertUser();
        Instant dayOne = Instant.parse("2026-11-10T10:00:00Z");
        Instant dayTwo = Instant.parse("2026-11-11T10:00:00Z");
        FairUsePolicy dayOnePolicy = new FairUsePolicy(Clock.fixed(dayOne, ZoneOffset.UTC));
        FairUsePolicy dayTwoPolicy = new FairUsePolicy(Clock.fixed(dayTwo, ZoneOffset.UTC));

        QuotaLedgerService dayOneService = new QuotaLedgerService(
            jdbcTemplate, transactionManager, dayOnePolicy, new SimpleMeterRegistry(), Clock.fixed(dayOne, ZoneOffset.UTC), true
        );
        QuotaLedgerService dayTwoService = new QuotaLedgerService(
            jdbcTemplate, transactionManager, dayTwoPolicy, new SimpleMeterRegistry(), Clock.fixed(dayTwo, ZoneOffset.UTC), true
        );
        EffectiveEntitlementAccess free = FairUsePolicy.syntheticAccessForTier("FREE", dayOne);

        var d1 = dayOneService.reserveAndConsume(userId, free, QuotaOperationType.AI_VERIFICATION, "d1-k1", 50_000L);
        assertThat(d1.allowed()).isTrue();
        dayOneService.reconcileActualCost(userId, "d1-k1", 60_000L);

        var d2 = dayTwoService.reserveAndConsume(userId, free, QuotaOperationType.AI_VERIFICATION, "d2-k1", 30_000L);
        assertThat(d2.allowed()).isFalse();
        assertThat(d2.costCircuitBreakerTripped()).isTrue();

        var d2Small = dayTwoService.reserveAndConsume(userId, free, QuotaOperationType.AI_VERIFICATION, "d2-k2", 10_000L);
        // Once tripped on day 2 row, remains tripped until monthly reset
        assertThat(d2Small.allowed()).isFalse();
        assertThat(d2Small.costCircuitBreakerTripped()).isTrue();

        // Release on a fresh user restores counter and cost
        UUID releaseUser = insertUser();
        var r1 = dayOneService.reserveAndConsume(releaseUser, free, QuotaOperationType.AI_VERIFICATION, "rel-1", 40_000L);
        assertThat(r1.allowed()).isTrue();
        dayOneService.releaseReservation(releaseUser, "rel-1");
        QuotaSummary afterRelease = dayOneService.getOrInitializeSummary(releaseUser, free);
        assertThat(afterRelease.aiVerificationsUsed()).isZero();
        assertThat(afterRelease.remainingAiChecks()).isEqualTo(3);
        assertThat(afterRelease.costCircuitBreakerTripped()).isFalse();
    }

    @Test
    void postgresAiSpendGuardCoordinatesWithQuotaLedgerServiceAndReconcilesCost() {
        UUID userId = insertUser();
        AiRouteTarget target = new AiRouteTarget("TEST", "exact-model-v1");
        AiRoutePlan plan = new AiRoutePlan(
            AiCapability.VISION_PARSE, "vision-route-v1", "TEST", null,
            "vision-recognition", "v001", "recognition-evidence-v1", Duration.ofSeconds(1), 2, 1024, 50_000L, "price-v1"
        ).selected(List.of(target), Duration.ofSeconds(1), 50_000L, null);

        PostgresAiSpendGuard guard = new PostgresAiSpendGuard(
            jdbcTemplate,
            new AiBudgetProperties(
                "coord-v1", 50_000L, 50_000L, 1_000_000L, 1_000_000L, 1_000_000L, 10_000_000L, 10_000_000L,
                Map.of("FREE", 50_000L)
            ),
            u -> "FREE",
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
        guard.setQuotaLedgerService(quotaLedgerService);
        guard.setEntitlementApplicationService(entitlementApplicationService);

        UUID sessionId = UUID.randomUUID();
        AiExecutionCommand op1 = new AiExecutionCommand(
            AiCapability.VISION_PARSE,
            new AiCapabilityRequest() {},
            new AiExecutionContext(UUID.randomUUID(), userId, sessionId, "op1", null, null, null, Map.of()),
            AiRouteContext.basic(AiCapability.VISION_PARSE, Duration.ofSeconds(1)),
            "application/json", null, null, null, null, null
        );
        usageLedgerRepository.saveAndFlush(AiUsageRecordJpaEntity.from(AiUsageRecord.started(UUID.randomUUID(), op1, plan, NOW)));
        new TransactionTemplate(transactionManager).executeWithoutResult(s -> guard.reserve(op1, plan, target, 1, 50_000L, 50_000L));
        new TransactionTemplate(transactionManager).executeWithoutResult(
            s -> guard.report(op1, 1, new AiUsage(10, 20, 1, 1, 45_000L, "USD", "price-v1"), null)
        );

        // Second handwriting diagnostic exceeds FREE daily limit of 1 (and remaining $0.035 cap)
        AiExecutionCommand op2 = new AiExecutionCommand(
            AiCapability.VISION_PARSE,
            new AiCapabilityRequest() {},
            new AiExecutionContext(UUID.randomUUID(), userId, sessionId, "op2", null, null, null, Map.of()),
            AiRouteContext.basic(AiCapability.VISION_PARSE, Duration.ofSeconds(1)),
            "application/json", null, null, null, null, null
        );
        usageLedgerRepository.saveAndFlush(AiUsageRecordJpaEntity.from(AiUsageRecord.started(UUID.randomUUID(), op2, plan, NOW)));
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(
            s -> guard.reserve(op2, plan, target, 1, 40_000L, 50_000L)
        )).isInstanceOf(AiProviderException.class);
    }

    private UUID insertUser() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into users (id, status, created_at, updated_at) values (?, 'ACTIVE', ?, ?)",
            userId,
            Timestamp.from(NOW),
            Timestamp.from(NOW)
        );
        return userId;
    }
}
