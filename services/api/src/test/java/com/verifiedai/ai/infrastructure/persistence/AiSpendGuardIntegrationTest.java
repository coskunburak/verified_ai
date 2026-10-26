package com.verifiedai.ai.infrastructure.persistence;

import static org.assertj.core.api.Assertions.*;
import com.verifiedai.ai.application.*;
import com.verifiedai.ai.infrastructure.configuration.AiBudgetProperties;
import com.verifiedai.integration.PostgresIntegrationTestSupport;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@org.springframework.test.context.TestPropertySource(properties = "app.ai.usage-ledger.enabled=true")
class AiSpendGuardIntegrationTest extends PostgresIntegrationTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired AiUsageRecordJpaRepository ledger;
    @Autowired java.util.List<com.verifiedai.sharedkernel.privacy.AccountDataLifecycleContributor> lifecycle;
    private final Instant now = Instant.parse("2090-01-31T23:59:59Z");
    private final AiRouteTarget target = new AiRouteTarget("TEST", "exact-model-v1");

    @BeforeEach
    void clearTestCommitments() {
        // Isolated Testcontainers database only; no application cleanup operation exists.
        jdbc.update("DELETE FROM ai_spend_allocations");
        jdbc.update("DELETE FROM ai_spend_attempts");
        jdbc.update("DELETE FROM ai_spend_accounts");
    }

    @Test
    void concurrentInstancesCannotOverrunSharedMonthlyBudget() throws Exception {
        var first = command(); var second = command();
        start(first); start(second);
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> work = new ArrayList<>();
            for (var c : List.of(first, second)) {
                work.add(pool.submit(() -> {
                    ready.countDown(); go.await();
                    try { reserve(guard(now), c, 1, 60); return true; }
                    catch (AiProviderException blocked) { return false; }
                }));
            }
            boolean bothReady = ready.await(5, TimeUnit.SECONDS);
            go.countDown();
            assertThat(bothReady).isTrue();
            int accepted = 0;
            for (var result : work) if (result.get(10, TimeUnit.SECONDS)) accepted++;
            assertThat(accepted).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT committed_micros FROM ai_spend_accounts WHERE account_key = 'GLOBAL_MONTH:2090-01-01'", Long.class)).isEqualTo(60);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_spend_attempts", Integer.class)).isEqualTo(1);
    }

    @Test
    void duplicateAttemptCannotDoubleReserveAndFailureRetainsCommitment() {
        var c = command(); start(c);
        reserve(guard(now), c, 1, 60);
        assertThatThrownBy(() -> reserve(guard(now), c, 1, 60)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        new TransactionTemplate(transactions).executeWithoutResult(s -> guard(now).report(c, 1, null, AiProviderFailureClass.TIMEOUT));
        assertThat(jdbc.queryForObject("SELECT state FROM ai_spend_attempts WHERE operation_id = ?", String.class, c.executionContext().operationId())).isEqualTo("UNKNOWN");
        assertThat(jdbc.queryForObject("SELECT reported_micros FROM ai_spend_attempts WHERE operation_id = ?", Long.class, c.executionContext().operationId())).isNull();
        assertThatThrownBy(() -> reserve(guard(now), c, 2, 60)).isInstanceOf(AiProviderException.class);
    }

    @Test
    void monthlyRolloverAndLateReportsPreserveOriginalAllocationAndPrice() {
        var old = command(); start(old); reserve(guard(now), old, 1, 80);
        var next = command(); start(next); reserve(guard(now.plusSeconds(2)), next, 1, 80);
        new TransactionTemplate(transactions).executeWithoutResult(s -> guard(now.plusSeconds(2)).report(old, 1,
            new AiUsage(2, 3, null, 1, 20, "USD", "old-price-v1"), null));
        assertThat(jdbc.queryForObject("SELECT committed_micros FROM ai_spend_accounts WHERE account_key = 'GLOBAL_MONTH:2090-01-01'", Long.class)).isEqualTo(80);
        assertThat(jdbc.queryForObject("SELECT committed_micros FROM ai_spend_accounts WHERE account_key = 'GLOBAL_MONTH:2090-02-01'", Long.class)).isEqualTo(80);
        assertThat(jdbc.queryForObject("SELECT pricing_version FROM ai_spend_attempts WHERE operation_id = ?", String.class, old.executionContext().operationId())).isEqualTo("old-price-v1");
    }

    @Test
    void failedReservationRollsBackEveryCounterAndDoesNotCreateAttempt() {
        var c = command(); start(c);
        assertThatThrownBy(() -> reserve(guard(now), c, 1, 101)).isInstanceOf(AiProviderException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_spend_accounts", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_spend_attempts", Integer.class)).isZero();
    }

    @Test
    void zeroDefaultBusinessPolicyBlocksPaidRequests() {
        var c = command(); start(c);
        var zero = new AiBudgetProperties(null, 0, 0, 0, 0, 0, 0, 0, null);
        var service = new PostgresAiSpendGuard(jdbc, zero, user -> "PRO_PLUS", Clock.fixed(now, ZoneOffset.UTC));
        assertThatThrownBy(() -> reserve(service, c, 1, 1)).isInstanceOf(AiProviderException.class);
    }

    @Test
    void accountDeletionAnonymizesBudgetKeysWithoutReleasingSpend() {
        var c = command(); start(c); reserve(guard(now), c, 1, 60);
        lifecycle.stream().filter(item -> item.category().equals("aiUsage")).findFirst().orElseThrow()
            .deleteUserData(c.executionContext().userId(), now);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_spend_accounts WHERE user_id = ?", Integer.class,
            c.executionContext().userId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_spend_accounts WHERE account_key LIKE ?", Integer.class,
            "%" + c.executionContext().userId() + "%")).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_spend_allocations WHERE operation_id = ?", Integer.class,
            c.executionContext().operationId())).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT committed_micros FROM ai_spend_accounts WHERE account_key = 'GLOBAL_MONTH:2090-01-01'", Long.class)).isEqualTo(60);
    }

    @Test
    void userAndSessionLimitsHoldAcrossDistinctOperationIds() {
        var first = command(); start(first); reserve(guard(now), first, 1, 60);
        var context = first.executionContext();
        var second = new AiExecutionCommand(first.capability(), first.request(),
            new AiExecutionContext(UUID.randomUUID(), context.userId(), context.problemSessionId(), "test", null, null, null, Map.of()),
            first.routeContext(), "application/json", null, null, null, null, null);
        start(second);
        var broadProvider = new PostgresAiSpendGuard(jdbc,
            new AiBudgetProperties("user-limits-v1", 100, 100, 100, 100, 100, 1000, 1000, Map.of("FREE", 100L)),
            user -> "FREE", Clock.fixed(now, ZoneOffset.UTC));
        assertThatThrownBy(() -> reserve(broadProvider, second, 1, 60)).isInstanceOf(AiProviderException.class);
        assertThat(jdbc.queryForObject("SELECT committed_micros FROM ai_spend_accounts WHERE account_key = 'GLOBAL_MONTH:2090-01-01'", Long.class)).isEqualTo(60);
    }

    private PostgresAiSpendGuard guard(Instant time) {
        return new PostgresAiSpendGuard(jdbc,
            new AiBudgetProperties("test-limits-v1", 100, 100, 100, 100, 100, 100, 100, Map.of("FREE", 100L)),
            user -> "FREE", Clock.fixed(time, ZoneOffset.UTC));
    }
    private void reserve(PostgresAiSpendGuard guard, AiExecutionCommand c, int attempt, long amount) {
        new TransactionTemplate(transactions).executeWithoutResult(s -> guard.reserve(c, plan(), target, attempt, amount, 100));
    }
    private void start(AiExecutionCommand c) {
        ledger.saveAndFlush(AiUsageRecordJpaEntity.from(AiUsageRecord.started(UUID.randomUUID(), c, plan(), now)));
    }
    private AiExecutionCommand command() {
        return new AiExecutionCommand(AiCapability.VISION_PARSE, new AiCapabilityRequest() {},
            new AiExecutionContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "test", null, null, null, Map.of()),
            AiRouteContext.basic(AiCapability.VISION_PARSE, Duration.ofSeconds(1)), "application/json", null, null, null, null, null);
    }
    private AiRoutePlan plan() {
        return new AiRoutePlan(AiCapability.VISION_PARSE, "vision-route-v1", "TEST", null,
            "vision-recognition", "v001", "recognition-evidence-v1", Duration.ofSeconds(1), 2, 1024, 100, "old-price-v1")
            .selected(List.of(target), Duration.ofSeconds(1), 100, null);
    }
}
