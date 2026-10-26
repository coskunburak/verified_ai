package com.verifiedai.solving.application;

import com.verifiedai.ai.application.*;
import com.verifiedai.billing.application.CapabilityAccessPolicy;
import com.verifiedai.problem.application.solving.SolveSource;
import com.verifiedai.problem.application.solving.SolveSourceAccess;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.solving.domain.model.*;
import com.verifiedai.solving.infrastructure.persistence.SolveStore;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class PrimarySolveService {
    private final SolveSourceAccess sources;
    private final SolveStore store;
    private final CapabilityAccessPolicy access;
    private final AiModelGateway gateway;
    private final AiRoutePlanner routes;
    private final SolutionCandidateValidator validator;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final SolveMetrics metrics;
    private final boolean enabled;

    public PrimarySolveService(SolveSourceAccess sources,SolveStore store,CapabilityAccessPolicy access,
        AiModelGateway gateway,AiRoutePlanner routes,SolutionCandidateValidator validator,TransactionTemplate tx,
        Clock clock,SolveMetrics metrics,@Value("${app.solving.enabled:false}") boolean enabled) {
        this.sources=sources;this.store=store;this.access=access;this.gateway=gateway;this.routes=routes;
        this.validator=validator;this.tx=tx;this.clock=clock;this.metrics=metrics;this.enabled=enabled;
    }

    public SolveView start(SolveCommand command) {
        UUID id=tx.execute(status->{
            SolveSource source=sources.current(command.userId(),command.problemSessionId());
            access.requireBasicSolve(command.userId());
            var duplicate=store.key(command.userId(),command.idempotencyKey());
            if(duplicate.isPresent()) {
                var job=duplicate.get();
                if(!job.sessionId().equals(command.problemSessionId()) || !job.canonicalId().equals(source.canonicalProblemId())
                    || !java.util.Objects.equals(job.retryOf(),command.retryOf())) throw conflict(ApiErrorCode.IDEMPOTENCY_KEY_REUSED);
                return job.id();
            }
            if(!enabled || !routes.enabled(AiCapability.SOLVE)) throw conflict(ApiErrorCode.SOLVE_DISABLED);
            try { validator.sourceDocument(source); }
            catch(CandidateRejected e) { throw conflict(ApiErrorCode.PROBLEM_UNSUPPORTED); }
            var latest=store.latest(source.canonicalProblemId());
            int attempt=1;
            if(command.retryOf()==null && latest.isPresent()) throw conflict(ApiErrorCode.SOLVE_STATE_CONFLICT);
            if(command.retryOf()!=null) {
                if(latest.isEmpty() || !latest.get().id().equals(command.retryOf())
                    || latest.get().state()!=SolveState.RETRYABLE_FAILURE) throw conflict(ApiErrorCode.SOLVE_STATE_CONFLICT);
                attempt=latest.get().attempt()+1;
                if(attempt>3) throw conflict(ApiErrorCode.SOLVE_RETRY_LIMIT);
            }
            if(store.active(source.problemSessionId()).isPresent()) throw conflict(ApiErrorCode.SOLVE_STATE_CONFLICT);
            var now=clock.instant();
            return store.insert(source,command.idempotencyKey(),command.retryOf(),attempt,now,now.plusSeconds(60)).id();
        });
        return get(command.userId(),command.problemSessionId(),id);
    }

    public SolveView get(UUID user,UUID session,UUID jobId) {
        return tx.execute(status->{
            sources.lockOwner(user,session);
            SolveJob job=owned(user,session,jobId);
            SolveState state=job.state();
            try { if(!matches(job,sources.current(user,session))) state=SolveState.STALE; }
            catch(ApiProblemException e) { state=SolveState.STALE; }
            var stored=store.candidate(jobId);
            var candidate=stored.map(row->validator.parse((String)row.get("artifact"))).orElse(null);
            var provenance=stored.map(row->validator.parse((String)row.get("provenance"))).orElse(null);
            return new SolveView(job.id(),job.canonicalId(),job.parseId(),job.parseRevision(),job.attempt(),state,
                job.failureCode(),job.createdAt(),candidate,provenance==null?null:provenance.path("provider").asText(),
                provenance==null?null:provenance.path("model").asText(),
                provenance==null?null:provenance.path("promptVersion").asText(),
                provenance==null?null:provenance.path("routePolicyVersion").asText(),
                "AI-generated candidate; mathematical verification has not been performed.");
        });
    }

    public SolveView cancel(UUID user,UUID session,UUID id) {
        tx.executeWithoutResult(status->{sources.lockOwner(user,session);owned(user,session,id);store.cancel(id,clock.instant());});
        return get(user,session,id);
    }

    /** No automatic retry of inference. Recovery only closes uncertain/expired jobs. */
    public void runDueJobs(int limit) {
        tx.executeWithoutResult(status->store.recover(clock.instant()));
        if(!enabled || !routes.enabled(AiCapability.SOLVE)) return;
        for(UUID id:store.due(Math.min(Math.max(limit,1),10))) {
            try { run(id); }
            catch(RuntimeException e) { metrics.workerFailure(); } // No raw model/student/SQL data in logs.
        }
    }

    public void run(UUID id) {
        if(!enabled || !routes.enabled(AiCapability.SOLVE)) return;
        SolveJob snapshot=store.get(id).orElse(null);
        if(snapshot==null) return;
        SolveSource source=tx.execute(status->{
            sources.lockOwner(snapshot.userId(),snapshot.sessionId());
            if(!store.claim(id,clock.instant())) return null;
            try {
                var current=sources.current(snapshot.userId(),snapshot.sessionId());
                if(!matches(snapshot,current)) { store.finish(id,SolveState.STALE,"SOURCE_CHANGED",null,clock.instant()); return null; }
                access.requireBasicSolve(snapshot.userId()); validator.sourceDocument(current); return current;
            } catch(ApiProblemException | CandidateRejected e) {
                store.finish(id,SolveState.POLICY_BLOCKED,"SOURCE_OR_ENTITLEMENT_BLOCKED",null,clock.instant()); return null;
            }
        });
        if(source==null || !store.running(id)) return;
        long started=System.nanoTime();
        var context=new AiRouteContext(AiCapability.SOLVE,source.problemType(),source.taskType(),null,null,false,
            "LOW",false,null,Locale.ENGLISH,Duration.ofSeconds(30),0L,null,Map.of(),null,null,
            "NO_TRAINING_BY_DEFAULT",AiRouteContext.RouteRole.PRIMARY_SOLVER);
        var command=new AiExecutionCommand(AiCapability.SOLVE,
            new AiSolveRequest(source.canonicalProblemId(),source.problemParseId(),source.problemParseRevision(),source.canonicalJson()),
            new AiExecutionContext(snapshot.operationId(),snapshot.userId(),snapshot.sessionId(),id.toString(),null,
                snapshot.idempotencyKey(),snapshot.deadline(),Map.of()),context,"application/json","canonical-problem-v1",
            "solver","v001","solution-candidate-v1",0L);
        AiExecutionResult result=gateway.execute(command); // No surrounding database transaction.
        SolutionCandidate candidate=null;
        SolveState state; String code=null;
        if(result.succeeded()) {
            try {
                if(!(result.output() instanceof AiSolveResult)) throw new CandidateRejected("SCHEMA_INVALID");
                candidate=validator.validate(result.output().rawOutputJson(),source,result.provenance().provider());
                state=candidate.ambiguities().isEmpty() && candidate.unresolvedIssues().isEmpty()
                    ? SolveState.GENERATED_UNVERIFIED : SolveState.REVIEW_REQUIRED;
            } catch(CandidateRejected e) { state=SolveState.TERMINAL_FAILURE;code=e.code(); }
        } else {
            code=result.failureClass()==null?"PROVIDER_FAILED":result.failureClass().name();
            state=failureState(result);
        }
        final SolutionCandidate accepted=candidate;
        final SolveState proposed=state;
        final String failure=code;
        SolveState completed=tx.execute(status->{
            sources.lockOwner(snapshot.userId(),snapshot.sessionId());
            SolveState terminal=proposed;
            try { if(!matches(snapshot,sources.current(snapshot.userId(),snapshot.sessionId()))) terminal=SolveState.STALE; }
            catch(ApiProblemException e) { terminal=SolveState.STALE; }
            if(!store.finish(id,terminal,failure,result.ledgerRecordId(),clock.instant())) return null;
            if(accepted!=null) store.candidate(id,validator.serialize(accepted),validator.serialize(result.provenance()),clock.instant());
            return terminal;
        });
        if(completed!=null) metrics.outcome(completed,failure,result,System.nanoTime()-started);
    }

    private SolveState failureState(AiExecutionResult r) {
        if(r.failureClass()==AiProviderFailureClass.TIMEOUT || r.failureClass()==AiProviderFailureClass.DEADLINE_EXCEEDED
            || r.failureClass()==AiProviderFailureClass.UNKNOWN || r.failureClass()==AiProviderFailureClass.LEDGER_UNAVAILABLE)
            return SolveState.OUTCOME_UNKNOWN;
        if(r.failureClass()==AiProviderFailureClass.CONFIGURATION_DISABLED || r.failureClass()==AiProviderFailureClass.POLICY_BLOCKED
            || r.failureClass()==AiProviderFailureClass.BUDGET_EXCEEDED) return SolveState.POLICY_BLOCKED;
        if(r.failureClass()==AiProviderFailureClass.UNSUPPORTED_PAYLOAD) return SolveState.UNSUPPORTED;
        return r.retryable()?SolveState.RETRYABLE_FAILURE:SolveState.TERMINAL_FAILURE;
    }
    private SolveJob owned(UUID user,UUID session,UUID id) {
        return store.get(id).filter(j->j.userId().equals(user)&&j.sessionId().equals(session))
            .orElseThrow(()->new ApiProblemException(HttpStatus.NOT_FOUND,ApiErrorCode.RESOURCE_FORBIDDEN,"Solve job not found",false,"REVIEW"));
    }
    private static boolean matches(SolveJob job,SolveSource source) {
        return job.canonicalId().equals(source.canonicalProblemId()) && job.parseId().equals(source.problemParseId())
            && job.parseRevision()==source.problemParseRevision();
    }
    private static ApiProblemException conflict(ApiErrorCode code) {
        return new ApiProblemException(HttpStatus.CONFLICT,code,"Solve request cannot proceed",false,"REVIEW");
    }
}
