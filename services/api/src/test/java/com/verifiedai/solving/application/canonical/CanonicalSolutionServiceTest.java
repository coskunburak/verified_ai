package com.verifiedai.solving.application.canonical;

import com.verifiedai.problem.application.solving.*;
import com.verifiedai.sharedkernel.error.*;
import com.verifiedai.solving.*;
import com.verifiedai.solving.domain.model.canonical.CanonicalSolution;
import com.verifiedai.solving.domain.model.canonical.CanonicalSolution.*;
import com.verifiedai.solving.domain.repository.CanonicalSolutionRepository;
import com.verifiedai.solving.domain.repository.CanonicalSolutionRepository.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CanonicalSolutionServiceTest {
    SolveSource source=SolverTestData.source();
    SolveSourceAccess sources=mock(SolveSourceAccess.class);
    CanonicalSolutionRepository repository=mock(CanonicalSolutionRepository.class);
    SimpleMeterRegistry metrics=new SimpleMeterRegistry();
    CanonicalSolutionService service;
    AcceptedArtifact artifact=CanonicalTestData.artifact(source,Role.PRIMARY,UUID.randomUUID(),null,SolverTestData.candidate(source).toString());
    Map<Origin,CanonicalSolution> rows=new HashMap<>();
    @BeforeEach void setup() {
        var manager=mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(i->new SimpleTransactionStatus());
        service=new CanonicalSolutionService(sources,repository,CanonicalTestData.mapper(),new TransactionTemplate(manager),Clock.fixed(CanonicalTestData.NOW,ZoneOffset.UTC),metrics);
        when(sources.current(source.userId(),source.problemSessionId())).thenReturn(source);
        when(sources.historical(source.userId(),source.problemSessionId(),source.canonicalProblemId())).thenReturn(source);
        when(repository.accepted(source.userId(),source.problemSessionId(),artifact.origin())).thenReturn(Optional.of(artifact));
        when(repository.find(any(),any(),any(Origin.class),eq(Version.CURRENT))).thenAnswer(i->Optional.ofNullable(rows.get(i.getArgument(2))));
        doAnswer(i->{CanonicalSolution s=i.getArgument(0);rows.put(s.lineage().origin(),s);return s;})
            .when(repository).insert(any());
        when(repository.forSource(any(),any(),any(),anyInt(),any())).thenAnswer(i->List.copyOf(rows.values()));
    }
    @Test void acceptedFixtureMaterializesReadsAndReusesWithoutGatewayOrProvider() {
        var first=service.materialize(source.userId(),source.problemSessionId(),artifact.origin());
        assertThat(first.state()).isEqualTo(CanonicalSolutions.MaterializationState.MATERIALIZED);
        assertThat(service.current(source.userId(),source.problemSessionId()).solutions().getFirst().steps()).hasSize(1);
        assertThat(service.materialize(source.userId(),source.problemSessionId(),artifact.origin()).solutionId()).isEqualTo(first.solutionId());
        verify(repository,times(1)).insert(any());
        assertThat(metrics.get("solving.canonical.materialization").tag("state","REUSED").counter().count()).isEqualTo(1);
    }
    @Test void failedDatabaseWriteRemainsRecoverableAndDoesNotRecordInvalidCandidate() {
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("synthetic")).when(repository).insert(any());
        assertThatThrownBy(()->service.materialize(source.userId(),source.problemSessionId(),artifact.origin())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        verify(repository,never()).fail(any(),any(),any(),any());
        doAnswer(i->i.getArgument(0)).when(repository).insert(any());
        assertThat(service.materialize(source.userId(),source.problemSessionId(),artifact.origin()).state()).isEqualTo(CanonicalSolutions.MaterializationState.MATERIALIZED);
    }
    @Test void unsupportedHistoricalCandidateGetsTypedFailureAndNoSuccess() {
        var bad=new AcceptedArtifact(artifact.userId(),artifact.sessionId(),artifact.canonicalProblemId(),artifact.parseId(),artifact.parseRevision(),null,
            artifact.origin(),null,artifact.operationId(),null,"solution-candidate-v99",artifact.artifactJson(),artifact.provenanceJson(),artifact.acceptedAt());
        when(repository.accepted(any(),any(),eq(artifact.origin()))).thenReturn(Optional.of(bad));
        var result=service.materialize(source.userId(),source.problemSessionId(),artifact.origin());
        assertThat(result.failure()).isEqualTo(Failure.UNSUPPORTED_SCHEMA);assertThat(result.solutionId()).isNull();
        verify(repository).fail(eq(bad),eq(Version.CURRENT),eq(Failure.UNSUPPORTED_SCHEMA),any());verify(repository,never()).insert(any());
    }
    @Test void calculusSchemaLabelCannotReinterpretLegacyCandidateContent() {
        var bad=new AcceptedArtifact(artifact.userId(),artifact.sessionId(),artifact.canonicalProblemId(),artifact.parseId(),artifact.parseRevision(),null,
            artifact.origin(),null,artifact.operationId(),null,"solution-candidate-v2",artifact.artifactJson(),artifact.provenanceJson(),artifact.acceptedAt());
        when(repository.accepted(any(),any(),eq(artifact.origin()))).thenReturn(Optional.of(bad));
        var result=service.materialize(source.userId(),source.problemSessionId(),artifact.origin());
        assertThat(result.state()).isEqualTo(CanonicalSolutions.MaterializationState.REJECTED);
        assertThat(result.failure()).isEqualTo(Failure.INVALID_CANDIDATE);
        assertThat(result.solutionId()).isNull();
        verify(repository).fail(eq(bad),eq(Version.CALCULUS),eq(Failure.INVALID_CANDIDATE),any());
        verify(repository,never()).insert(any());
    }
    @Test void ownershipCheckedBeforeReadingOrReturningExistingArtifacts() {
        doThrow(new ApiProblemException(HttpStatus.NOT_FOUND,ApiErrorCode.RESOURCE_FORBIDDEN,"Unavailable",false,"REVIEW"))
            .when(sources).lockOwner(any(),any());
        assertThatThrownBy(()->service.materialize(UUID.randomUUID(),source.problemSessionId(),artifact.origin())).isInstanceOf(ApiProblemException.class);
        verifyNoInteractions(repository);
    }
    @Test void cachedRejectionIsExplicitAndNeverWritesAProjection() {
        when(repository.failure(source.userId(),source.problemSessionId(),artifact.origin(),Version.CURRENT))
            .thenReturn(Optional.of(Failure.INVALID_CANDIDATE));
        assertThat(service.materialize(source.userId(),source.problemSessionId(),artifact.origin()).failure()).isEqualTo(Failure.INVALID_CANDIDATE);
        verify(repository,never()).insert(any());verify(repository,never()).fail(any(),any(),any(),any());
    }
    @Test void acceptedUncertaintyRemainsReviewRequired() {
        var candidate=SolverTestData.candidate(source);candidate.withArray("unresolvedIssues").add("Requires human review");
        var a=new AcceptedArtifact(artifact.userId(),artifact.sessionId(),artifact.canonicalProblemId(),artifact.parseId(),artifact.parseRevision(),null,
            artifact.origin(),null,artifact.operationId(),null,artifact.schema(),candidate.toString(),artifact.provenanceJson(),artifact.acceptedAt());
        when(repository.accepted(any(),any(),eq(artifact.origin()))).thenReturn(Optional.of(a));
        service.materialize(source.userId(),source.problemSessionId(),artifact.origin());
        assertThat(service.current(source.userId(),source.problemSessionId()).state()).isEqualTo(CanonicalSolutions.ReadState.REVIEW_REQUIRED);
    }
    @Test void unavailableSelectedSourceReturnsNoCurrentSolution() {
        service.materialize(source.userId(),source.problemSessionId(),artifact.origin());
        when(sources.current(any(),any())).thenThrow(new ApiProblemException(HttpStatus.CONFLICT,ApiErrorCode.SOLVE_SOURCE_NOT_READY,"Unavailable",false,"REVIEW"));
        var result=service.current(source.userId(),source.problemSessionId());
        assertThat(result.state()).isEqualTo(CanonicalSolutions.ReadState.SOURCE_UNAVAILABLE);assertThat(result.solutions()).isEmpty();
    }
    @Test void recoveryClampsBatchAndContinuesAfterIndividualFailure() {
        when(repository.pending(Version.CURRENT,100)).thenReturn(List.of(artifact));
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("synthetic")).when(repository).insert(any());
        assertThat(service.recover(Integer.MAX_VALUE)).isEqualTo(1);
        verify(repository).pending(Version.CURRENT,100);
        assertThat(metrics.get("solving.canonical.recovery").tag("outcome","DEFERRED").counter().count()).isEqualTo(1);
        assertThat(metrics.getMeters().stream().flatMap(m->m.getId().getTags().stream()).map(t->t.getValue()))
            .doesNotContain(source.userId().toString(),source.problemSessionId().toString());
    }
}
