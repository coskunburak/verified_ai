package com.verifiedai.attempt.application;

import com.verifiedai.attempt.domain.*;
import com.verifiedai.attempt.infrastructure.persistence.*;
import com.verifiedai.problem.application.solving.*;
import com.verifiedai.sharedkernel.error.*;
import com.verifiedai.sharedkernel.ratelimit.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AttemptEvaluationService {
    public record View(String schemaVersion,UUID attemptId,String status,boolean historical,boolean creationEnabled,AttemptEvaluation evaluation) {}
    private record Prepared(AttemptStructuredWork work,AttemptEvaluationRepository.Operation operation,AttemptEvaluationPreparer.Prepared requests,View view) {}
    private final SolveSourceAccess sources;
    private final StudentAttemptRepository attempts;
    private final AttemptWorkRepository works;
    private final AttemptEvaluationRepository store;
    private final AttemptEvaluationPreparer preparer;
    private final AttemptVerifierClient verifier;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final RateLimiter limiter;
    private final MeterRegistry metrics;
    private final boolean enabled;
    public AttemptEvaluationService(SolveSourceAccess sources,StudentAttemptRepository attempts,AttemptWorkRepository works,
        AttemptEvaluationRepository store,AttemptEvaluationPreparer preparer,AttemptVerifierClient verifier,
        PlatformTransactionManager manager,Clock clock,RateLimiter limiter,MeterRegistry metrics,
        @Value("${verifiedai.attempt.evaluation-enabled:false}") boolean enabled,
        @Value("${verifiedai.attempt.enabled:false}") boolean attemptsEnabled,
        @Value("${app.environment:local}") String environment,
        @Value("${verifiedai.attempt.production-authorized:false}") boolean productionAuthorized) {
        if(enabled && !Set.of("local","test").contains(environment) && !productionAuthorized)
            throw new IllegalArgumentException("Evaluation production authorization pending");
        this.sources=sources; this.attempts=attempts; this.works=works; this.store=store; this.preparer=preparer; this.verifier=verifier;
        this.clock=clock; this.limiter=limiter; this.metrics=metrics; this.enabled=enabled && attemptsEnabled;
        tx=new TransactionTemplate(manager); tx.setTimeout(5);
    }
    public View read(UUID owner,UUID problem,UUID attempt) {
        return operation(owner,()->tx.execute(t->{
            sources.lockOwner(owner,problem); var work=owned(owner,problem,attempt);
            return view(owner,problem,work,store.available()?store.get(owner,attempt).orElse(null):null);
        }));
    }
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public View evaluate(UUID owner,UUID problem,UUID attempt,String key,AttemptEvaluationInput input) {
        return operation(owner,()->{
            AttemptAnalysisPolicy.submission(key,List.of("evaluation"));
            var prepared=tx.execute(t->{
                sources.lockOwner(owner,problem); var work=owned(owner,problem,attempt);
                if(input==null || !input.source().equals(work.source()) || input.workRevision()!=work.revision())
                    throw error(ApiErrorCode.IDEMPOTENCY_KEY_REUSED,HttpStatus.CONFLICT);
                if(!store.available()) throw unavailable();
                store.checkKey(owner,problem,attempt,key,input.realDomainConfirmed());
                var old=store.get(owner,attempt).orElse(null);
                if(old!=null && old.artifact()!=null) return new Prepared(work,old,null,view(owner,problem,work,old));
                if(!fresh(owner,problem,work)) throw error(ApiErrorCode.ATTEMPT_STALE_SOURCE,HttpStatus.CONFLICT);
                if(!enabled) throw unavailable();
                if(old!=null && old.leaseUntil().isAfter(clock.instant())) return new Prepared(work,old,null,view(owner,problem,work,old));
                var op=old==null?store.create(owner,problem,key,work,input.realDomainConfirmed(),clock.instant()):store.reclaim(owner,old,clock.instant());
                return new Prepared(work,op,preparer.prepare(sources.current(owner,problem),work,op.executionId(),input.realDomainConfirmed()),null);
            });
            if(prepared.view()!=null) { count("replay"); return prepared.view(); }
            var op=prepared.operation(); var work=prepared.work(); var requests=prepared.requests();
            AttemptEvaluation result;
            try {
                if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
                    throw new IllegalStateException("Verifier cannot run in a transaction");
                var analysis=verify(op.executionId(),work.steps().size(),requests.sequence());
                AttemptAnalysisResult finalEvidence=requests.finalAnswer()==null?null:verify(op.executionId(),1,requests.finalAnswer());
                var status=requests.finalStepId()==null?AttemptEvaluation.FinalStatus.NOT_PROVIDED:
                    requests.reviewRequired()?AttemptEvaluation.FinalStatus.REVIEW_REQUIRED:AttemptEvaluation.FinalStatus.INCONCLUSIVE;
                if(finalEvidence!=null && finalEvidence.steps().getFirst().verdict()==StepVerdict.FIRST_WRONG_STEP)
                    status=AttemptEvaluation.FinalStatus.DISPROVEN;
                else if(finalEvidence!=null && requests.completeForm() && finalEvidence.steps().getFirst().verdict()==StepVerdict.VALID_EQUIVALENT)
                    status=AttemptEvaluation.FinalStatus.VERIFIED;
                result=new AttemptEvaluation("attempt-evaluation-v1",op.id(),attempt,owner,problem,work.source(),work.revision(),
                    "structured-evaluation-v1",op.executionId(),clock.instant().toString(),work.steps().stream().map(AttemptStructuredWork.Step::id).toList(),
                    analysis,input.realDomainConfirmed(),requests.finalStepId(),status,finalEvidence,false);
            } catch(RuntimeException ex) {
                count("recoverable_failure");
                return tx.execute(t->{ sources.lockOwner(owner,problem); var current=owned(owner,problem,attempt);
                    store.release(owner,op,clock.instant()); return view(owner,problem,current,store.get(owner,attempt).orElseThrow()); });
            }
            return tx.execute(t->{
                sources.lockOwner(owner,problem); var current=owned(owner,problem,attempt);
                if(fresh(owner,problem,current)) store.publish(owner,op,result);
                else { store.release(owner,op,clock.instant()); count("stale_source"); }
                var response=view(owner,problem,current,store.get(owner,attempt).orElseThrow());
                org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        public void afterCommit() { count(response.status()); }
                    });
                return response;
            });
        });
    }
    private AttemptAnalysisResult verify(UUID execution,int count,String request) {
        if(request==null) {
            var unknown=AttemptAnalysisResult.unavailable(execution,count,AttemptAnalysisResult.Reason.UNSUPPORTED_MATH);
            return new AttemptAnalysisResult("attempt-analysis-v2","bounded-attempt-v2","attempt-taxonomy-v2",execution,
                execution.toString(),unknown.outcome(),null,"UNSUPPORTED",unknown.steps(),null,"UNRESOLVED",null);
        }
        var result=verifier.verify(execution,count,request); result.validateFor(execution,count);
        if(!"attempt-analysis-v2".equals(result.schemaVersion()) || result.steps().stream().anyMatch(s->Set.of(
            AttemptAnalysisResult.Reason.TIMEOUT,AttemptAnalysisResult.Reason.RESOURCE_FAILURE,AttemptAnalysisResult.Reason.WORKER_FAILURE,
            AttemptAnalysisResult.Reason.ADMISSION_LIMIT,AttemptAnalysisResult.Reason.DISABLED,AttemptAnalysisResult.Reason.INVALID_RESPONSE).contains(s.reasonCode()))) throw unavailable();
        var input=com.verifiedai.sharedkernel.contracts.VersionedJsonContract.parse(request);
        if(!input.path("domainBasis").asText().equals(result.domainBasis())) throw unavailable();
        if("REAL_DOMAIN".equals(result.domain()) && (result.naturalDomain()==null || result.naturalDomain().isNull()
            || "RELATION".equals(input.path("original").path("kind").asText()) && (result.solutionSet()==null || result.solutionSet().isNull()))) throw unavailable();
        // A null statement may never be blessed by a malformed/compromised adapter response.
        for(int i=0;i<count;i++) if(input.path("steps").get(i).path("statement").isNull()
            && !Set.of(StepVerdict.UNCERTAIN,StepVerdict.NOT_ASSESSED).contains(result.steps().get(i).verdict())) throw unavailable();
        return result;
    }
    private AttemptStructuredWork owned(UUID owner,UUID problem,UUID attempt) {
        if(!attempts.available() || attempts.get(owner,problem,attempt).isEmpty() || !works.available()) throw error(ApiErrorCode.ATTEMPT_NOT_FOUND,HttpStatus.NOT_FOUND);
        return works.get(owner,attempt).orElseThrow(()->error(ApiErrorCode.ATTEMPT_NOT_FOUND,HttpStatus.NOT_FOUND));
    }
    private boolean fresh(UUID owner,UUID problem,AttemptStructuredWork work) {
        if(works.superseded(owner,work.attemptId())) return false;
        try { var s=sources.current(owner,problem); return work.source().equals(new AttemptSourceIdentity(s.canonicalProblemId(),s.canonicalRevision(),s.problemParseId(),s.problemParseRevision())); }
        catch(ApiProblemException ex) { if(ex.code()!=ApiErrorCode.SOLVE_SOURCE_NOT_READY) throw ex; return false; }
    }
    private View view(UUID owner,UUID problem,AttemptStructuredWork work,AttemptEvaluationRepository.Operation op) {
        boolean historical=!fresh(owner,problem,work);
        return new View("attempt-evaluation-view-v1",work.attemptId(),op!=null && op.artifact()!=null?"COMPLETED":historical?"STALE":op==null?"NOT_EVALUATED":op.leaseUntil().isAfter(clock.instant())?"IN_PROGRESS":"RETRYABLE",
            historical,enabled && store.available(),op==null?null:op.artifact());
    }
    private <T> T operation(UUID owner,Supplier<T> action) {
        long start=System.nanoTime();
        try {
            if(!limiter.check(new RateLimitPolicy("attempt-evaluation",15,Duration.ofMinutes(1),true),owner.toString()).allowed()) throw error(ApiErrorCode.RATE_LIMIT_EXCEEDED,HttpStatus.TOO_MANY_REQUESTS);
            return action.get();
        } catch(org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException ex) { count("persistence_failure"); throw unavailable(); }
        catch(ApiProblemException ex) { count(ex.code().name()); throw ex; }
        finally { metrics.timer("attempt.evaluation.duration").record(System.nanoTime()-start,TimeUnit.NANOSECONDS); }
    }
    private void count(String status) { metrics.counter("attempt.evaluation.outcome","status",status).increment(); }
    private static ApiProblemException error(ApiErrorCode code,HttpStatus status) { return AttemptAnalysisPolicy.error(code,status); }
    private static ApiProblemException unavailable() { return error(ApiErrorCode.ATTEMPT_UNAVAILABLE,HttpStatus.SERVICE_UNAVAILABLE); }
}
