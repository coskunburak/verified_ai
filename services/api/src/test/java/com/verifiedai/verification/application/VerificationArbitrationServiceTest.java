package com.verifiedai.verification.application;

import com.verifiedai.problem.application.solving.*;
import com.verifiedai.solving.application.SecondaryCorroborationQuery;
import com.verifiedai.solving.application.canonical.CanonicalSolutions;
import com.verifiedai.verification.domain.model.*;
import com.verifiedai.verification.domain.policy.*;
import com.verifiedai.verification.domain.repository.*;
import com.verifiedai.sharedkernel.error.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import static com.verifiedai.verification.PlanningTestData.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class VerificationArbitrationServiceTest {
    final SolveSource raw = equation();
    final VerificationSourceReader reader = new VerificationSourceReader(CODEC);
    final VerificationPlanner planner = new VerificationPlanner(new VerificationCapabilities(true, true,
        "DOMAIN_CONSTRAINT_CHECK,EQUATION_SUBSTITUTION,SOLUTION_SET_COMPLETENESS"));
    final CanonicalSolutions solutions = mock(CanonicalSolutions.class);
    final SolveSourceAccess sources = mock(SolveSourceAccess.class);
    final VerificationRunRepository runs = mock(VerificationRunRepository.class);
    final VerificationDecisionRepository decisions = mock(VerificationDecisionRepository.class);
    final SecondaryCorroborationQuery secondary = mock(SecondaryCorroborationQuery.class);
    final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    final AtomicInteger depth = new AtomicInteger();
    final com.verifiedai.solving.domain.model.canonical.CanonicalSolution solution = answer(raw);
    final UUID runKey = UUID.randomUUID(), request = UUID.randomUUID();
    VerificationRun run;
    VerificationArbitrationService service;
    @BeforeEach void setup() {
        var source = reader.read(raw); var plan = planner.plan(source, solution).plan(); var now = Instant.parse("2026-10-30T00:00:00Z");
        run = new VerificationRun(runKey, runKey, plan.id(), plan.policyVersion(), plan.capabilityVersion(), source.identity(), solution.id(),
            solution.version(), "test-policy", now, plan.methods().stream().map(m -> new VerificationEvidence(
                VerificationEvidence.identity(runKey, m.method()), runKey, plan.id(), plan.policyVersion(), plan.capabilityVersion(), source.identity(),
                solution.id(), solution.version(), m.method().purpose(), source.variables(), new MathematicalExecution.Result(m.method(), "v1",
                    MathematicalExecution.VERIFIER_VERSION, MathematicalExecution.Outcome.EXECUTION_FAILED, MathematicalExecution.Reason.TIMEOUT,
                    List.of(), null), "test-policy", now)).toList());
        when(transactions.getTransaction(any())).thenAnswer(i -> { depth.incrementAndGet(); return new SimpleTransactionStatus(); });
        doAnswer(i -> { depth.decrementAndGet(); return null; }).when(transactions).commit(any());
        doAnswer(i -> { depth.decrementAndGet(); return null; }).when(transactions).rollback(any());
        when(solutions.historical(any(), any(), any())).thenAnswer(i -> {
            assertThat(depth.get()).isZero();
            return new CanonicalSolutions.Read(solution.id(), CanonicalSolutions.ReadState.GENERATED_UNVERIFIED, solution);
        });
        when(solutions.current(any(), any())).thenAnswer(i -> {
            assertThat(depth.get()).isZero();
            return new CanonicalSolutions.Current(CanonicalSolutions.ReadState.GENERATED_UNVERIFIED, List.of(solution));
        });
        when(sources.current(any(), any())).thenReturn(raw);
        when(secondary.currentPrimary(any(), any(), any())).thenReturn(true);
        when(secondary.snapshot(any())).thenReturn(new SecondaryCorroborationQuery.Snapshot(List.of(), List.of(SecondaryCorroborationQuery.Signal.NONE)));
        when(runs.find(any(), any(), any())).thenReturn(Optional.of(run));
        when(runs.latest(any(), any(), any())).thenReturn(Optional.of(run));
        when(decisions.find(any(), any(), any())).thenReturn(Optional.empty());
        when(decisions.attempts(any(), any(), any())).thenReturn(1);
        when(decisions.publish(any())).thenAnswer(i -> i.getArgument(0));
        service = new VerificationArbitrationService(solutions, sources, reader, planner, runs, decisions, secondary,
            new TransactionTemplate(transactions), Clock.fixed(now, ZoneOffset.UTC), new SimpleMeterRegistry(), true, true, "test");
    }
    VerificationDecision evaluate() { return service.evaluate(raw.userId(), raw.problemSessionId(), solution.id(), runKey, request); }
    @Test void readIsOwnedDoesNotPublishAndCanonicalReadFinishesBeforeOwnerLock() {
        var d = evaluate(); clearInvocations(decisions);
        when(decisions.latest(any(), any(), any())).thenReturn(Optional.of(d));
        assertThat(service.currentForJob(raw.userId(), raw.problemSessionId(), solution.lineage().origin().jobId())).contains(d);
        verify(decisions, never()).publish(any()); assertThat(depth.get()).isZero();
    }
    @Test void reauthorizationFailureBeforePublicationCannotPublish() {
        when(sources.current(any(), any())).thenReturn(raw, raw).thenThrow(new ApiProblemException(
            org.springframework.http.HttpStatus.FORBIDDEN, ApiErrorCode.ACCOUNT_NOT_ACTIVE, "Unavailable", false, "REVIEW"));
        assertThatThrownBy(this::evaluate).isInstanceOf(ApiProblemException.class);
        verify(decisions, never()).publish(any()); assertThat(depth.get()).isZero();
    }
    @Test void concurrentSolutionReplacementCannotPublish() {
        when(secondary.currentPrimary(any(), any(), any())).thenReturn(true, true, false);
        assertThatThrownBy(this::evaluate).isInstanceOf(ApiProblemException.class);
        verify(decisions, never()).publish(any());
    }
    @Test void secondaryCompletionDuringEvaluationCannotPublishAnOutdatedSnapshot() {
        var absent = new SecondaryCorroborationQuery.Snapshot(List.of(), List.of(SecondaryCorroborationQuery.Signal.NONE));
        var conflict = new SecondaryCorroborationQuery.Snapshot(List.of(UUID.randomUUID()), List.of(SecondaryCorroborationQuery.Signal.DISAGREEMENT));
        when(secondary.snapshot(any())).thenReturn(absent, absent, conflict);
        assertThatThrownBy(this::evaluate).isInstanceOf(ApiProblemException.class);
        verify(decisions, never()).publish(any());
    }
    @Test void selectedParseCorrectionDuringEvaluationCannotPublish() {
        when(sources.current(any(), any())).thenReturn(raw, raw).thenThrow(new ApiProblemException(
            org.springframework.http.HttpStatus.CONFLICT, ApiErrorCode.SOLVE_SOURCE_NOT_READY, "Changed", false, "REVIEW"));
        assertThatThrownBy(this::evaluate).isInstanceOf(ApiProblemException.class);
        verify(decisions, never()).publish(any());
    }
    @Test void productionFlagCannotEnableAnUnapprovedRelease() {
        assertThatIllegalArgumentException().isThrownBy(() -> new VerificationArbitrationService(solutions, sources, reader, planner, runs,
            decisions, secondary, new TransactionTemplate(transactions), Clock.systemUTC(), new SimpleMeterRegistry(), true, true, "production"));
    }
}
