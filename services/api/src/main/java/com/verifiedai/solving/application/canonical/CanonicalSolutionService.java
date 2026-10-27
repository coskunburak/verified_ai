package com.verifiedai.solving.application.canonical;

import com.verifiedai.problem.application.solving.SolveSource;
import com.verifiedai.problem.application.solving.SolveSourceAccess;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.solving.domain.model.canonical.CanonicalSolution;
import com.verifiedai.solving.domain.model.canonical.CanonicalSolution.*;
import com.verifiedai.solving.domain.repository.CanonicalSolutionRepository;
import com.verifiedai.solving.domain.repository.CanonicalSolutionRepository.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Transactional(propagation=Propagation.NOT_SUPPORTED)
public class CanonicalSolutionService implements CanonicalSolutions {
    private final SolveSourceAccess sources;
    private final CanonicalSolutionRepository repository;
    private final CanonicalCandidateMapper mapper;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final MeterRegistry metrics;
    public CanonicalSolutionService(SolveSourceAccess sources,CanonicalSolutionRepository repository,CanonicalCandidateMapper mapper,
        TransactionTemplate transactions,Clock clock,MeterRegistry metrics) {
        this.sources=sources;this.repository=repository;this.mapper=mapper;this.clock=clock;this.metrics=metrics;
        this.tx=new TransactionTemplate(transactions.getTransactionManager());
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public Materialization materialize(UUID user,UUID session,Origin origin) {
        long started=System.nanoTime();
        try {
            var result=tx.execute(status->{
                sources.lockOwner(user,session);
                // Resolve acceptance before reuse: deleted/forged/role-confused source never returns a solution.
                var artifact=repository.accepted(user,session,origin).orElseThrow(CanonicalSolutionService::unavailable);
                var previous=repository.find(user,session,origin,Version.CURRENT);
                if(previous.isPresent()) return new Materialization(MaterializationState.REUSED,previous.get().id(),null);
                var failed=repository.failure(user,session,origin,Version.CURRENT);
                if(failed.isPresent()) return new Materialization(MaterializationState.REJECTED,null,failed.get());
                CanonicalSolution solution;
                try {
                    var source=sources.historical(user,session,artifact.canonicalProblemId());
                    solution=mapper.map(artifact,source,Version.CURRENT,clock.instant());
                } catch(CanonicalCandidateMapper.ProjectionRejected e) {
                    repository.fail(artifact,Version.CURRENT,e.failure(),clock.instant());
                    return new Materialization(MaterializationState.REJECTED,null,e.failure());
                } catch(ApiProblemException e) {
                    repository.fail(artifact,Version.CURRENT,Failure.SOURCE_UNAVAILABLE,clock.instant());
                    return new Materialization(MaterializationState.REJECTED,null,Failure.SOURCE_UNAVAILABLE);
                }
                var persisted=repository.insert(solution);
                return new Materialization(MaterializationState.MATERIALIZED,persisted.id(),null);
            });
            outcome(result.state().name(),started);
            if(result.failure()!=null) metrics.counter("solving.canonical.rejections","reason",result.failure().name()).increment();
            return result;
        } catch(RuntimeException e) {
            outcome(e instanceof ApiProblemException?"ACCESS_REJECTED":"RECOVERABLE_FAILURE",started);
            throw e; // Failed transaction leaves immutable accepted evidence eligible for bounded recovery.
        }
    }
    /** Integration for both existing V018/V019 candidates and future accepted rows; never invokes a solver. */
    public int recover(int requested) {
        int limit=Math.min(Math.max(requested,1),100);
        var pending=tx.execute(status->repository.pending(Version.CURRENT,limit));
        for(var artifact:pending) {
            try { materialize(artifact.userId(),artifact.sessionId(),artifact.origin()); }
            catch(RuntimeException e) { metrics.counter("solving.canonical.recovery","outcome","DEFERRED").increment(); }
        }
        return pending.size();
    }
    public Current current(UUID user,UUID session) {
        return tx.execute(status->{
            sources.lockOwner(user,session);
            SolveSource source;
            try { source=sources.current(user,session); }
            catch(ApiProblemException e) { metrics.counter("solving.canonical.reads","state","SOURCE_UNAVAILABLE").increment(); return new Current(ReadState.SOURCE_UNAVAILABLE,List.of()); }
            var solutions=repository.forSource(user,session,source.canonicalProblemId(),source.canonicalRevision(),Version.CURRENT);
            // Check exact parse lineage again at the internal consumer boundary.
            if(solutions.stream().anyMatch(s->!matches(s,source))) throw new IllegalStateException("Canonical read lineage mismatch");
            if(solutions.isEmpty()) return new Current(ReadState.NOT_MATERIALIZED,List.of());
            var state=solutions.stream().anyMatch(s->!s.ambiguities().isEmpty() || !s.unresolvedIssues().isEmpty())?ReadState.REVIEW_REQUIRED:ReadState.GENERATED_UNVERIFIED;
            return new Current(state,solutions);
        });
    }
    public Read historical(UUID user,UUID session,UUID solutionId) {
        return tx.execute(status->{
            sources.lockOwner(user,session);
            CanonicalSolution solution;
            try { solution=repository.find(user,session,solutionId).orElseThrow(CanonicalSolutionService::unavailable); }
            catch(UnsupportedRepresentation e) { return new Read(solutionId,ReadState.UNSUPPORTED_REPRESENTATION,null); }
            try {
                if(!matches(solution,sources.current(user,session))) {
                    metrics.counter("solving.canonical.reads","state","HISTORICAL_SOURCE").increment();
                    return new Read(solutionId,ReadState.HISTORICAL_SOURCE,solution);
                }
            } catch(ApiProblemException e) { return new Read(solutionId,ReadState.SOURCE_UNAVAILABLE,solution); }
            if(!Version.CURRENT.equals(solution.version())) return new Read(solutionId,ReadState.HISTORICAL_VERSION,solution);
            var state=solution.ambiguities().isEmpty() && solution.unresolvedIssues().isEmpty()?ReadState.GENERATED_UNVERIFIED:ReadState.REVIEW_REQUIRED;
            return new Read(solutionId,state,solution);
        });
    }
    private static boolean matches(CanonicalSolution s,SolveSource source) {
        var l=s.lineage();
        return l.userId().equals(source.userId()) && l.sessionId().equals(source.problemSessionId())
            && l.canonicalProblemId().equals(source.canonicalProblemId()) && l.parseId().equals(source.problemParseId())
            && l.parseRevision()==source.problemParseRevision() && l.canonicalRevision()==source.canonicalRevision();
    }
    private void outcome(String state,long start) {
        metrics.counter("solving.canonical.materialization","state",state).increment();
        metrics.timer("solving.canonical.latency","state",state).record(System.nanoTime()-start,TimeUnit.NANOSECONDS);
    }
    private static ApiProblemException unavailable() {
        return new ApiProblemException(HttpStatus.NOT_FOUND,ApiErrorCode.RESOURCE_FORBIDDEN,"Canonical solution source unavailable",false,"REVIEW");
    }
}
