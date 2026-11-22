package com.verifiedai.billing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.verifiedai.ai.application.AiCapability;
import com.verifiedai.ai.application.AiModelGateway;
import com.verifiedai.ai.application.AiRoutePlan;
import com.verifiedai.ai.application.AiRoutePlanner;
import com.verifiedai.ai.application.AiRoutePolicy;
import com.verifiedai.problem.application.solving.SolveSource;
import com.verifiedai.problem.application.solving.SolveSourceAccess;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.solving.application.PrimarySolveService;
import com.verifiedai.solving.application.SolutionCandidateValidator;
import com.verifiedai.solving.application.SolveCommand;
import com.verifiedai.solving.application.SolveMetrics;
import com.verifiedai.solving.infrastructure.persistence.SolveStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

final class QuotaLedgerServiceUnitTest {

    private static final Instant NOW = Instant.parse("2026-11-22T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void disabledFlagReturnsDeterministicDefaultSummaryWithZeroDatabaseAccess() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        FairUsePolicy policy = new FairUsePolicy(CLOCK);
        QuotaLedgerService service = new QuotaLedgerService(
            jdbc,
            null,
            policy,
            new SimpleMeterRegistry(),
            CLOCK,
            false
        );

        UUID userId = UUID.randomUUID();
        EffectiveEntitlementAccess free = FairUsePolicy.syntheticAccessForTier("FREE", NOW);

        QuotaSummary summary = service.getOrInitializeSummary(userId, free);
        assertThat(summary.periodKey()).isEqualTo("2026-11-22");
        assertThat(summary.aiVerificationsUsed()).isZero();
        assertThat(summary.aiVerificationsLimit()).isEqualTo(3);
        assertThat(summary.remainingAiChecks()).isEqualTo(3);
        assertThat(summary.deterministicTwinsUnlimited()).isTrue();
        assertThat(summary.costCircuitBreakerTripped()).isFalse();
        assertThat(summary.degradedToDeterministicOnly()).isFalse();

        QuotaConsumptionDecision decision = service.reserveAndConsume(
            userId,
            free,
            QuotaOperationType.AI_VERIFICATION,
            "unit-disabled-key",
            5_000L
        );
        assertThat(decision.allowed()).isTrue();
        assertThat(decision.reason()).isEqualTo("QUOTA_LEDGER_DISABLED");
        verifyNoInteractions(jdbc);
    }

    @Test
    void deterministicOperationsStayExemptWithoutConsumingOrRequiringIdempotencyKey() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        FairUsePolicy policy = new FairUsePolicy(CLOCK);
        QuotaLedgerService service = new QuotaLedgerService(
            jdbc,
            null,
            policy,
            new SimpleMeterRegistry(),
            CLOCK,
            false
        );

        UUID userId = UUID.randomUUID();
        EffectiveEntitlementAccess pro = FairUsePolicy.syntheticAccessForTier("PRO", NOW);

        for (QuotaOperationType op : new QuotaOperationType[]{
            QuotaOperationType.DETERMINISTIC_TWIN,
            QuotaOperationType.DETERMINISTIC_SYMBOLIC_VERIFY
        }) {
            QuotaConsumptionDecision decision = service.reserveAndConsume(userId, pro, op, null, 0L);
            assertThat(decision.allowed()).isTrue();
            assertThat(decision.deterministicExempt()).isTrue();
            assertThat(decision.summary().deterministicTwinsUnlimited()).isTrue();
        }
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsInvalidIdempotencyKeysAndNegativeCosts() {
        FairUsePolicy policy = new FairUsePolicy(CLOCK);
        QuotaLedgerService service = new QuotaLedgerService(
            mock(JdbcTemplate.class),
            null,
            policy,
            new SimpleMeterRegistry(),
            CLOCK,
            false
        );
        UUID userId = UUID.randomUUID();
        EffectiveEntitlementAccess free = FairUsePolicy.syntheticAccessForTier("FREE", NOW);

        for (String badKey : new String[]{null, "", "   ", "k".repeat(129)}) {
            assertThatThrownBy(() -> service.reserveAndConsume(
                userId, free, QuotaOperationType.AI_VERIFICATION, badKey, 1_000L
            ))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> {
                    ApiProblemException apiEx = (ApiProblemException) ex;
                    assertThat(apiEx.status().value()).isEqualTo(400);
                    assertThat(apiEx.code()).isEqualTo(ApiErrorCode.IDEMPOTENCY_KEY_REQUIRED);
                });
        }

        assertThatThrownBy(() -> service.reserveAndConsume(
            userId, free, QuotaOperationType.AI_VERIFICATION, "valid-key", -1L
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void primarySolveServiceMapsExhaustionAndCircuitBreakerToEthical429ProblemDetails() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        SolveSourceAccess sources = mock(SolveSourceAccess.class);
        SolveStore store = mock(SolveStore.class);
        CapabilityAccessPolicy access = mock(CapabilityAccessPolicy.class);
        AiModelGateway gateway = mock(AiModelGateway.class);
        AiRoutePlanner routes = mock(AiRoutePlanner.class);
        QuotaLedgerService quotaLedger = mock(QuotaLedgerService.class);
        EntitlementApplicationService entitlementService = mock(EntitlementApplicationService.class);

        SolveSource source = new SolveSource(
            UUID.randomUUID(), userId, sessionId, UUID.randomUUID(), 1, 1,
            "EQUATION", "SOLVE_EQUATION", "{\"schemaVersion\":\"canonical-problem-v1\"}"
        );
        when(sources.current(userId, sessionId)).thenReturn(source);
        when(store.key(userId, "solve-key-123")).thenReturn(Optional.empty());
        when(routes.enabled(AiCapability.SOLVE)).thenReturn(true);
        AiRoutePlan paidPlan = new AiRoutePlan(
            AiCapability.SOLVE, "solve-v1", "GEMINI", null,
            "solver", "v001", "solution-candidate-v1", Duration.ofSeconds(10),
            1, 65536, 10_000L, "gemini-v1"
        );
        when(routes.policies()).thenReturn(Map.of(AiCapability.SOLVE, AiRoutePolicy.enabled(paidPlan)));
        when(quotaLedger.isEnabled()).thenReturn(true);
        EffectiveEntitlementAccess freeAccess = FairUsePolicy.syntheticAccessForTier("FREE", NOW);
        when(entitlementService.resolveAccess(userId)).thenReturn(freeAccess);

        PrimarySolveService solveService = new PrimarySolveService(
            sources,
            store,
            access,
            gateway,
            routes,
            new SolutionCandidateValidator(),
            new TransactionTemplate(mock(PlatformTransactionManager.class)),
            CLOCK,
            new SolveMetrics(new SimpleMeterRegistry()),
            true,
            10_000L,
            "en"
        );
        solveService.setQuotaLedgerService(quotaLedger);
        solveService.setEntitlementApplicationService(entitlementService);

        when(quotaLedger.getOrInitializeSummary(userId, freeAccess)).thenReturn(
            QuotaSummary.of("2026-11-22", 3, 3, 0, 1, false, NOW.plusSeconds(3600))
        );
        assertThatThrownBy(() -> solveService.start(new SolveCommand(userId, sessionId, "solve-key-123", null)))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> {
                ApiProblemException apiEx = (ApiProblemException) ex;
                assertThat(apiEx.status().value()).isEqualTo(429);
                assertThat(apiEx.code()).isEqualTo(ApiErrorCode.QUOTA_EXHAUSTED);
                assertThat(apiEx.details()).containsEntry("recoverable", true);
            });

        when(quotaLedger.getOrInitializeSummary(userId, freeAccess)).thenReturn(
            QuotaSummary.of("2026-11-22", 2, 3, 0, 1, true, NOW.plusSeconds(3600))
        );
        assertThatThrownBy(() -> solveService.start(new SolveCommand(userId, sessionId, "solve-key-123", null)))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> {
                ApiProblemException apiEx = (ApiProblemException) ex;
                assertThat(apiEx.status().value()).isEqualTo(429);
                assertThat(apiEx.code()).isEqualTo(ApiErrorCode.AI_COST_CIRCUIT_BREAKER_TRIPPED);
                assertThat(apiEx.details()).containsEntry("recoverable", true);
            });
    }
}
