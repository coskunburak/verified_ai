package com.verifiedai.verification.application;

import com.verifiedai.problem.application.solving.*;
import com.verifiedai.solving.application.canonical.CanonicalSolutions;
import com.verifiedai.solving.domain.model.canonical.CanonicalSolution;
import com.verifiedai.verification.domain.model.*;
import com.verifiedai.verification.domain.model.MathematicalExecution.*;
import com.verifiedai.verification.domain.policy.*;
import com.verifiedai.verification.domain.repository.VerificationRunRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import static com.verifiedai.verification.PlanningTestData.*;
import static com.verifiedai.verification.application.VerificationExecution.State;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VerificationExecutionServiceTest {
    SolveSource source = equation(); CanonicalSolution solution = answer(source);
    VerificationPlanning planning = mock(VerificationPlanning.class);
    CanonicalSolutions canonical = mock(CanonicalSolutions.class);
    SolveSourceAccess sources = mock(SolveSourceAccess.class);
    MathematicalExecutor executor = mock(MathematicalExecutor.class);
    VerificationRunRepository repository = mock(VerificationRunRepository.class);
    PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    VerificationSourceReader reader = new VerificationSourceReader(CODEC);
    VerificationPlanner planner = new VerificationPlanner(new VerificationCapabilities(true, true,
        "DOMAIN_CONSTRAINT_CHECK,EQUATION_SUBSTITUTION,SOLUTION_SET_COMPLETENESS"));
    AtomicInteger depth = new AtomicInteger(); AtomicReference<VerificationRun> stored = new AtomicReference<>();
    UUID key = UUID.randomUUID(); VerificationExecutionService service;
    @BeforeEach void setup() {
        when(planning.plan(any(), any(), any())).thenReturn(planner.plan(reader.read(source), solution));
        when(canonical.historical(any(), any(), any())).thenAnswer(i -> {
            assertThat(depth.get()).isZero();
            return new CanonicalSolutions.Read(solution.id(), CanonicalSolutions.ReadState.GENERATED_UNVERIFIED, solution);
        });
        when(transactions.getTransaction(any())).thenAnswer(i -> { depth.incrementAndGet(); return new SimpleTransactionStatus(); });
        doAnswer(i -> { depth.decrementAndGet(); return null; }).when(transactions).commit(any());
        doAnswer(i -> { depth.decrementAndGet(); return null; }).when(transactions).rollback(any());
        when(sources.current(any(), any())).thenReturn(source);
        when(repository.find(any(), any(), any())).thenAnswer(i -> Optional.ofNullable(stored.get()));
        when(repository.publish(any())).thenAnswer(i -> { stored.set(i.getArgument(0)); return stored.get(); });
        when(executor.execute(any())).thenAnswer(i -> {
            assertThat(depth.get()).isZero();
            return Result.failed(i.getArgument(0), Reason.TIMEOUT);
        });
        service = new VerificationExecutionService(planning, canonical, sources, reader, planner, executor,
            repository, new TransactionTemplate(transactions), Clock.fixed(Instant.parse("2026-10-29T10:00:00Z"), ZoneOffset.UTC),
            new SimpleMeterRegistry());
    }
    VerificationExecution.Publication execute() { return service.execute(source.userId(), source.problemSessionId(), solution.id(), key); }
    @Test void canonicalReadsAndNetworkNeverRunUnderOwnerLocks() {
        var publication = execute();
        assertThat(publication.state()).isEqualTo(State.PUBLISHED);
        assertThat(depth.get()).isZero();
        assertThat(publication.run().evidence()).hasSize(3).allSatisfy(e -> {
            assertThat(e.result().outcome()).isEqualTo(Outcome.EXECUTION_FAILED);
            assertThat(e.result().proof()).isNull(); assertThat(e.solutionId()).isEqualTo(solution.id());
            assertThat(e.source().parseId()).isEqualTo(source.problemParseId());
        });
        verify(transactions, times(2)).commit(any());
    }
    @Test void idempotentRetryReauthorizesButDoesNotCallVerifierAgain() {
        var first = execute(); clearInvocations(executor, planning, canonical);
        assertThat(execute().state()).isEqualTo(State.REUSED);
        verifyNoInteractions(executor); verify(planning).plan(any(), any(), any()); verify(canonical).historical(any(), any(), any());
        assertThat(stored.get()).isEqualTo(first.run());
    }
    @Test void changedSourceDuringNetworkWorkPreventsPublication() {
        doAnswer(i -> {
            doReturn(new SolveSource(source.canonicalProblemId(), source.userId(), source.problemSessionId(),
                source.problemParseId(), 2, 2, source.problemType(), source.taskType(), source.canonicalJson()))
                .when(sources).current(any(), any());
            return Result.failed(i.getArgument(0), Reason.TIMEOUT);
        }).when(executor).execute(any());
        // A mismatching canonical snapshot is rejected before it can acquire proof authority.
        assertThat(execute().state()).isEqualTo(State.STALE_SOURCE);
        verify(repository, never()).publish(any());
    }
    @Test void disabledCapabilitiesDoNotCallExecutorOrWriteEvidence() {
        when(planning.plan(any(), any(), any())).thenReturn(new VerificationPlanner(new VerificationCapabilities()).plan(reader.read(source), solution));
        assertThat(execute().state()).isEqualTo(State.BLOCKED);
        verifyNoInteractions(executor, repository, canonical);
    }
    @Test void nullOrBrokenGatewayResponsesRemainFailures() {
        doReturn(null).when(executor).execute(any());
        assertThat(execute().run().evidence()).allSatisfy(e -> assertThat(e.result().reasonCode()).isEqualTo(Reason.INVALID_RESPONSE));
    }
    @Test void denialDuringPublicationCannotPublishPreviouslyComputedEvidence() {
        doAnswer(i -> {
            doThrow(new com.verifiedai.sharedkernel.error.ApiProblemException(
                org.springframework.http.HttpStatus.FORBIDDEN, com.verifiedai.sharedkernel.error.ApiErrorCode.ACCOUNT_NOT_ACTIVE,
                "Unavailable", false, "REVIEW")).when(sources).current(any(), any());
            return Result.failed(i.getArgument(0), Reason.TIMEOUT);
        }).when(executor).execute(any());
        assertThatThrownBy(this::execute).isInstanceOf(com.verifiedai.sharedkernel.error.ApiProblemException.class);
        verify(repository, never()).publish(any());
    }
    @Test void historyIsExplicitAndStillRequiresOwnerAuthorization() {
        execute();
        assertThat(service.history(source.userId(), source.problemSessionId(), solution.id(), key).currentSource()).isTrue();
        verify(sources).lockOwner(source.userId(), source.problemSessionId());
    }
}
