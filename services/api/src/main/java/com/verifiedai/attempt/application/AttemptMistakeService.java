package com.verifiedai.attempt.application;

import com.verifiedai.attempt.domain.*;
import com.verifiedai.attempt.infrastructure.persistence.*;
import com.verifiedai.mistake.MistakeTaxonomy;
import com.verifiedai.problem.application.solving.SolveSourceAccess;
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
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AttemptMistakeService {
    public record View(String schemaVersion, UUID attemptId, String status, boolean historical,
        boolean creationEnabled, AttemptMistakeClassification classification) { }
    private final SolveSourceAccess sources;
    private final StudentAttemptRepository attempts;
    private final AttemptWorkRepository works;
    private final AttemptEvaluationRepository evaluations;
    private final AttemptMistakeRepository store;
    private final AttemptMistakeClassifier classifier;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final RateLimiter limiter;
    private final MeterRegistry metrics;
    private final boolean enabled;
    public AttemptMistakeService(SolveSourceAccess sources, StudentAttemptRepository attempts, AttemptWorkRepository works,
        AttemptEvaluationRepository evaluations, AttemptMistakeRepository store, AttemptMistakeClassifier classifier,
        PlatformTransactionManager manager, Clock clock, RateLimiter limiter, MeterRegistry metrics,
        @Value("${verifiedai.attempt.classification-enabled:false}") boolean enabled,
        @Value("${verifiedai.attempt.enabled:false}") boolean attemptsEnabled,
        @Value("${app.environment:local}") String environment,
        @Value("${verifiedai.attempt.production-authorized:false}") boolean authorized) {
        if(enabled && !Set.of("local", "test").contains(environment) && !authorized)
            throw new IllegalArgumentException("Classification production authorization pending");
        this.sources=sources; this.attempts=attempts; this.works=works; this.evaluations=evaluations;
        this.store=store; this.classifier=classifier; this.clock=clock; this.limiter=limiter; this.metrics=metrics;
        this.enabled=enabled && attemptsEnabled; tx=new TransactionTemplate(manager); tx.setTimeout(5);
    }
    public View read(UUID owner, UUID problem, UUID attempt) {
        return operation(owner, () -> tx.execute(t -> {
            var work=owned(owner,problem,attempt);
            var value=store.available()?store.get(owner,attempt).orElse(null):null;
            return view(owner,problem,work,value);
        }));
    }
    public View classify(UUID owner, UUID problem, UUID attempt, String key, AttemptMistakeInput input) {
        return operation(owner, () -> tx.execute(t -> {
            AttemptAnalysisPolicy.submission(key,List.of("classification"));
            var work=owned(owner,problem,attempt);
            if(input==null || !work.source().equals(input.source()) || work.revision()!=input.workRevision()) throw conflict();
            if(!store.available() || !evaluations.available()) throw unavailable();
            var evaluation=evaluations.get(owner,attempt).map(AttemptEvaluationRepository.Operation::artifact).orElse(null);
            if(evaluation==null) throw error(ApiErrorCode.ATTEMPT_CLASSIFICATION_NOT_READY,HttpStatus.CONFLICT);
            if(!evaluation.id().equals(input.evaluationId()) || !evaluation.ownerId().equals(owner)
                || !evaluation.source().equals(work.source()) || evaluation.workRevision()!=work.revision()) {
                count("evidence_rejected"); throw conflict();
            }
            store.checkKey(owner,problem,attempt,key,evaluation.id());
            var prior=store.get(owner,attempt).orElse(null);
            if(prior!=null && prior.policyVersion().equals(MistakeTaxonomy.POLICY)) { count("replay"); return view(owner,problem,work,prior); }
            if(!fresh(owner,problem,work)) throw error(ApiErrorCode.ATTEMPT_STALE_SOURCE,HttpStatus.CONFLICT);
            if(!enabled) throw unavailable();
            var result=classifier.classify(evaluation,work,clock.instant());
            store.insert(key,result,evaluation);
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    public void afterCommit() { count(result.decision().status().name()); }
                });
            return view(owner,problem,work,result);
        }));
    }
    private AttemptStructuredWork owned(UUID owner, UUID problem, UUID attempt) {
        sources.lockOwner(owner,problem);
        if(!attempts.available() || attempts.get(owner,problem,attempt).isEmpty() || !works.available())
            throw error(ApiErrorCode.ATTEMPT_NOT_FOUND,HttpStatus.NOT_FOUND);
        return works.get(owner,attempt).orElseThrow(() -> error(ApiErrorCode.ATTEMPT_NOT_FOUND,HttpStatus.NOT_FOUND));
    }
    private boolean fresh(UUID owner, UUID problem, AttemptStructuredWork work) {
        if(works.superseded(owner,work.attemptId())) return false;
        try {
            var s=sources.current(owner,problem);
            return work.source().equals(new AttemptSourceIdentity(s.canonicalProblemId(),s.canonicalRevision(),s.problemParseId(),s.problemParseRevision()));
        } catch(ApiProblemException ex) { if(ex.code()!=ApiErrorCode.SOLVE_SOURCE_NOT_READY) throw ex; return false; }
    }
    private View view(UUID owner, UUID problem, AttemptStructuredWork work, AttemptMistakeClassification value) {
        boolean historical=!fresh(owner,problem,work);
        return new View("attempt-mistake-view-v1",work.attemptId(),value!=null?"COMPLETED":historical?"STALE":"NOT_CLASSIFIED",
            historical,enabled && store.available(),value);
    }
    private <T> T operation(UUID owner, Supplier<T> action) {
        long start=System.nanoTime(); count("request");
        try {
            if(!limiter.check(new RateLimitPolicy("attempt-classification",15,Duration.ofMinutes(1),true),owner.toString()).allowed())
                throw error(ApiErrorCode.RATE_LIMIT_EXCEEDED,HttpStatus.TOO_MANY_REQUESTS);
            return action.get();
        } catch(org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException ex) {
            count("persistence_failure"); throw unavailable();
        } catch(IllegalArgumentException ex) { count("evidence_rejected"); throw unavailable(); }
        catch(ApiProblemException ex) { count(ex.code().name()); throw ex; }
        finally { metrics.timer("attempt.classification.duration","policy",MistakeTaxonomy.POLICY).record(System.nanoTime()-start,TimeUnit.NANOSECONDS); }
    }
    private void count(String outcome) {
        metrics.counter("attempt.classification.outcome","outcome",outcome,"policy",MistakeTaxonomy.POLICY,
            "authority","DETERMINISTIC_EVIDENCE","taxonomy","attempt-taxonomy-v2").increment();
    }
    private static ApiProblemException conflict() { return error(ApiErrorCode.IDEMPOTENCY_KEY_REUSED,HttpStatus.CONFLICT); }
    private static ApiProblemException unavailable() { return error(ApiErrorCode.ATTEMPT_UNAVAILABLE,HttpStatus.SERVICE_UNAVAILABLE); }
    private static ApiProblemException error(ApiErrorCode code,HttpStatus status) { return AttemptAnalysisPolicy.error(code,status); }
}
