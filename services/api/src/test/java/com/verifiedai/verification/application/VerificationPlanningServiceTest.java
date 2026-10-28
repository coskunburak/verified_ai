package com.verifiedai.verification.application;

import com.verifiedai.problem.application.solving.*;
import com.verifiedai.sharedkernel.error.*;
import com.verifiedai.solving.application.canonical.CanonicalSolutions;
import com.verifiedai.solving.application.canonical.CanonicalSolutions.*;
import com.verifiedai.solving.domain.model.canonical.CanonicalSolution;
import com.verifiedai.solving.domain.model.canonical.CanonicalSolution.*;
import com.verifiedai.verification.domain.model.VerificationPlan.*;
import com.verifiedai.verification.domain.policy.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import static com.verifiedai.verification.PlanningTestData.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VerificationPlanningServiceTest {
    SolveSource source = equation(); CanonicalSolution solution = answer(source);
    CanonicalSolutions canonical = mock(CanonicalSolutions.class);
    SolveSourceAccess sources = mock(SolveSourceAccess.class);
    PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    SimpleMeterRegistry metrics = new SimpleMeterRegistry();
    VerificationPlanningService service;
    @BeforeEach void setup() {
        when(transactions.getTransaction(any())).thenAnswer(i -> new SimpleTransactionStatus());
        when(canonical.historical(source.userId(), source.problemSessionId(), solution.id())).thenReturn(new Read(solution.id(), ReadState.GENERATED_UNVERIFIED, solution));
        when(sources.current(source.userId(), source.problemSessionId())).thenReturn(source);
        service = new VerificationPlanningService(canonical, sources, new VerificationSourceReader(CODEC),
            new VerificationPlanner(new VerificationCapabilities()), metrics, new TransactionTemplate(transactions));
    }
    Result plan() { return service.plan(source.userId(), source.problemSessionId(), solution.id()); }
    @Test void readsCanonicalBeforeOpeningSourceTransactionAndRechecksSource() {
        assertThat(plan().state()).isEqualTo(State.PLANNED);
        var order = inOrder(canonical, transactions, sources);
        order.verify(canonical).historical(source.userId(), source.problemSessionId(), solution.id());
        order.verify(transactions).getTransaction(any()); order.verify(sources).current(source.userId(), source.problemSessionId());
        order.verify(transactions).commit(any());
        assertThat(metrics.get("verification.planning.completed").tag("state", "PLANNED").counter().count()).isEqualTo(1);
    }
    @Test void sourceSupersededBetweenReadsFailsClosed() {
        when(sources.current(any(), any())).thenReturn(new SolveSource(source.canonicalProblemId(), source.userId(), source.problemSessionId(),
            source.problemParseId(), 1, 2, source.problemType(), source.taskType(), source.canonicalJson()));
        assertThat(plan().reason()).isEqualTo(Reason.LINEAGE_MISMATCH);
    }
    @Test void allCanonicalReadStatesRetainTheirMeaning() {
        for (var state : ReadState.values()) {
            if (state == ReadState.GENERATED_UNVERIFIED) continue;
            when(canonical.historical(any(), any(), any())).thenReturn(new Read(solution.id(), state, solution));
            assertThat(plan().plan()).isNull();
        }
    }
    @Test void unauthorizedOrInactiveAccountNeverBecomesUnsupportedMath() {
        var denied = new ApiProblemException(HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_FORBIDDEN, "Unavailable", false, "REVIEW");
        when(canonical.historical(any(), any(), any())).thenThrow(denied);
        assertThatThrownBy(this::plan).isSameAs(denied); verifyNoInteractions(sources, transactions);
    }
    @Test void accountDeletionAfterCanonicalReadIsRechecked() {
        var denied = new ApiProblemException(HttpStatus.FORBIDDEN, ApiErrorCode.ACCOUNT_NOT_ACTIVE, "Unavailable", false, "REVIEW");
        when(sources.current(any(), any())).thenThrow(denied);
        assertThatThrownBy(this::plan).isSameAs(denied);
    }
    @Test void unavailableSourceReturnsExplicitOutcomeAndMetricsContainNoIdentities() {
        when(sources.current(any(), any())).thenThrow(new ApiProblemException(HttpStatus.CONFLICT, ApiErrorCode.SOLVE_SOURCE_NOT_READY, "Unavailable", false, "REVIEW"));
        assertThat(plan().state()).isEqualTo(State.SOURCE_UNAVAILABLE);
        assertThat(metrics.getMeters().stream().flatMap(m -> m.getId().getTags().stream()).map(t -> t.getValue()))
            .doesNotContain(source.userId().toString(), source.problemSessionId().toString(), solution.id().toString(), source.canonicalJson());
    }
    @Test void secondaryRequiresMatchingCurrentPrimaryParent() {
        var primary = solution;
        solution = solution(source, primary.finalStatement(), Role.SECONDARY, primary.lineage().origin().jobId());
        when(canonical.historical(source.userId(), source.problemSessionId(), solution.id())).thenReturn(new Read(solution.id(), ReadState.GENERATED_UNVERIFIED, solution));
        assertThat(plan().state()).isEqualTo(State.PLANNED);
        when(canonical.historical(source.userId(), source.problemSessionId(), primary.id())).thenReturn(new Read(primary.id(), ReadState.HISTORICAL_SOURCE, primary));
        assertThat(plan().reason()).isEqualTo(Reason.PARENT_NOT_CURRENT);
        var wrong = answer(source);
        when(canonical.historical(source.userId(), source.problemSessionId(), primary.id())).thenReturn(new Read(wrong.id(), ReadState.GENERATED_UNVERIFIED, wrong));
        assertThat(plan().reason()).isEqualTo(Reason.PARENT_NOT_CURRENT);
    }
    @Test void missingSecondaryParentCannotBeFabricated() {
        solution = solution(source, solution.finalStatement(), Role.SECONDARY, UUID.randomUUID());
        when(canonical.historical(any(), any(), any())).thenThrow(new ApiProblemException(HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_FORBIDDEN, "Unavailable", false, "REVIEW"));
        doReturn(new Read(solution.id(), ReadState.GENERATED_UNVERIFIED, solution)).when(canonical).historical(source.userId(), source.problemSessionId(), solution.id());
        assertThat(plan().reason()).isEqualTo(Reason.PARENT_NOT_CURRENT);
    }
}
