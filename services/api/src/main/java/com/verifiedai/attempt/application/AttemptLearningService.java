package com.verifiedai.attempt.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.verifiedai.attempt.domain.*;
import com.verifiedai.attempt.infrastructure.persistence.*;
import com.verifiedai.problem.application.solving.SolveSourceAccess;
import com.verifiedai.solving.application.canonical.CanonicalMathCodec;
import com.verifiedai.tutoring.application.learning.TutorAssistanceReader;
import com.verifiedai.sharedkernel.error.*;
import com.verifiedai.sharedkernel.ratelimit.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import io.micrometer.core.instrument.MeterRegistry;

@Service
public class AttemptLearningService {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(AttemptLearningService.class);
    private final StudentAttemptRepository attempts;
    private final AttemptLearningRepository store;
    private final SolveSourceAccess sources;
    private final AttemptVerifierClient verifier;
    private final CanonicalMathCodec codec;
    private final TutorAssistanceReader assistance;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final RateLimiter limiter;
    private final MeterRegistry metrics;
    private final boolean enabled;
    private final com.verifiedai.verification.application.VerificationSourceReader reader;
    public AttemptLearningService(StudentAttemptRepository attempts,AttemptLearningRepository store,SolveSourceAccess sources,
        AttemptVerifierClient verifier,CanonicalMathCodec codec,TutorAssistanceReader assistance,PlatformTransactionManager transactions,
        Clock clock,RateLimiter limiter,MeterRegistry metrics,@Value("${verifiedai.attempt.enabled:false}") boolean enabled, com.verifiedai.verification.application.VerificationSourceReader reader) {
        this.reader=reader;
        this.attempts=attempts; this.store=store; this.sources=sources; this.verifier=verifier; this.codec=codec; this.assistance=assistance;
        this.tx=new TransactionTemplate(transactions); tx.setTimeout(5); this.clock=clock; this.limiter=limiter; this.metrics=metrics; this.enabled=enabled;
    }
    public record PublicDrill(String schemaVersion,UUID id,UUID attemptId,JsonNode problem,List<JsonNode> options,
        String domainLabel,String difficulty,String status,Integer selectedOption,Boolean correct,Integer correctOption,
        String explanationKey,String assistance,Instant createdAt,Instant completedAt,boolean trainingEligible) { }
    public PublicDrill create(UUID owner,UUID problem,UUID attempt,String key) {
        key(key);
        return operation(owner,()-> {
            var prior=tx.execute(t->{ owned(owner,problem,attempt,false); return store.drill(owner,attempt); });
            if(prior.isPresent()) return expose(prior.get());
            var evidence=generate(owner,problem,attempt,"TWIN");
            return tx.execute(t->{
                var current=owned(owner,problem,attempt,true);
                var replay=store.drill(owner,attempt); if(replay.isPresent()) return expose(replay.get());
                var code=current.analysis().steps().get(current.analysis().firstWrongStepIndex()-1).mistakeCode();
                if(evidence.twin()==null || evidence.twin().mistakeCode()!=code) throw unavailable();
                store.insertDrill(owner,attempt,evidence.executionId(),evidence.twin(),clock.instant());
                metrics.counter("attempt.learning","action","twin_created").increment();
                return expose(store.drill(owner,attempt).orElseThrow());
            });
        });
    }
    public PublicDrill drill(UUID owner,UUID problem,UUID attempt) {
        return operation(owner,()->tx.execute(t->{ owned(owner,problem,attempt,false); return expose(store.drill(owner,attempt).orElseThrow(()->notFound())); }));
    }
    public PublicDrill answer(UUID owner,UUID problem,UUID attempt,String key,int selected) {
        key(key); if(selected<0 || selected>3) throw validation();
        return operation(owner,()->tx.execute(t->{
            owned(owner,problem,attempt,false); var drill=store.drill(owner,attempt).orElseThrow(()->notFound());
            if(drill.completedAt()!=null) {
                if(!key.equals(drill.answerKey()) || selected!=drill.selectedOption()) throw AttemptAnalysisPolicy.error(ApiErrorCode.IDEMPOTENCY_KEY_REUSED,HttpStatus.CONFLICT);
                return expose(drill);
            }
            if(!enabled) throw unavailable();
            // Immutable verified question can be completed after its original problem is revised.
            store.complete(owner,drill,selected,key,assistance.hasAssistance(owner,problem),clock.instant());
            metrics.counter("attempt.learning","action","twin_completed").increment();
            return expose(store.drill(owner,attempt).orElseThrow());
        }));
    }
    public List<PublicDrill> history(UUID owner,UUID problem) {
        return operation(owner,()->tx.execute(t->{ sources.lockOwner(owner,problem); return attempts.history(owner,problem).stream()
            .map(a->store.drill(owner,a.id())).flatMap(Optional::stream).map(this::expose).toList(); }));
    }
    public AttemptLearningEvidence methods(UUID owner,UUID problem,UUID attempt,String key) {
        key(key);
        return operation(owner,()-> {
            var cached=tx.execute(t->{ owned(owner,problem,attempt,false); return store.methods(owner,attempt); });
            if(cached.isPresent()) return cached.get();
            var evidence=generate(owner,problem,attempt,"METHODS");
            return tx.execute(t->{ owned(owner,problem,attempt,true); var replay=store.methods(owner,attempt); if(replay.isPresent()) return replay.get();
                store.saveMethods(owner,attempt,evidence,clock.instant()); return evidence; });
        });
    }
    public record PublicComparison(String schemaVersion,UUID problemId,UUID attemptId,AttemptSourceIdentity source,
        String freshness,boolean trainingEligible,MethodComparisonEvidence evidence) { }
    public PublicComparison comparison(UUID owner,UUID problem,UUID attempt,AttemptSourceIdentity expected,String key) {
        long started=System.nanoTime(); String outcome="INTERNAL_ERROR";
        try {
            var result=compareOwned(owner,problem,attempt,expected,key); outcome=result.evidence().status(); return result;
        } catch(ApiProblemException ex) { outcome=ex.code().name(); throw ex; }
        finally {
            metrics.counter("attempt.methods.requests","outcome",outcome).increment();
            io.micrometer.core.instrument.Timer.builder("attempt.methods.request.duration").publishPercentileHistogram()
                .serviceLevelObjectives(Duration.ofMillis(250),Duration.ofSeconds(1),Duration.ofSeconds(5))
                .register(metrics).record(System.nanoTime()-started,java.util.concurrent.TimeUnit.NANOSECONDS);
            LOG.info("method_comparison request={} outcome={} elapsed_ms={}",
                com.verifiedai.sharedkernel.observability.CorrelationIds.current(),outcome,(System.nanoTime()-started)/1_000_000);
        }
    }
    private PublicComparison compareOwned(UUID owner,UUID problem,UUID attempt,AttemptSourceIdentity expected,String key) {
        key(key);
        return operation(owner,()-> {
            // Ephemeral deterministic projection: no write, cache, synthetic attempt or idempotency-key ledger.
            record Prepared(AttemptSourceIdentity identity,JsonNode source,UUID execution,String request) { }
            var prepared=tx.execute(t->{
                var current=sources.current(owner,problem);
                if(!enabled) throw unavailable();
                var identity=identity(current);
                JsonNode source;
                if(attempt!=null) {
                    var saved=owned(owner,problem,attempt,true);
                    if(expected!=null && !expected.equals(saved.source())) throw stale();
                    String stored=attempts.request(owner,attempt);
                    if(stored==null) throw unavailable();
                    var original=codec.parse(stored);
                    if(!"attempt-verification-v2".equals(original.path("schemaVersion").asText())) throw unavailable();
                    var selected=codec.object();
                    for(String field:List.of("original","variables","restrictions","domainBasis")) selected.set(field,original.path(field));
                    source=selected;
                } else {
                    if(expected==null || !identity.equals(expected)) throw stale();
                    try { source=comparisonSource(current); }
                    catch(IllegalArgumentException | com.verifiedai.verification.application.VerificationSourceReader.SourceRejected ex) { throw unavailable(); }
                }
                UUID execution=UUID.nameUUIDFromBytes(("method-comparison-policy-v3:"+owner+":"+problem+":"+identity+":"+attempt+":"+source)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                var request=codec.object().put("schemaVersion","method-comparison-v2").put("executionId",execution.toString())
                    .put("correlationId",execution.toString()).put("action","METHODS");
                request.set("source",source);
                try { com.verifiedai.sharedkernel.contracts.VersionedJsonContract.validate("method-comparison-v2",request.toString()); }
                catch(IllegalArgumentException ex) { throw unavailable(); }
                return new Prepared(identity,source,execution,request.toString());
            });
            MethodComparisonEvidence evidence;
            long started=System.nanoTime();
            try {
                evidence=verifier.comparison(prepared.execution(),prepared.request());
                com.verifiedai.sharedkernel.contracts.VersionedJsonContract.validate("method-comparison-result-v2",codec.encode(evidence));
                evidence.validateFor(prepared.execution(),prepared.source());
            } catch(AttemptVerifierClient.ComparisonFailure ex) {
                methodFailure(prepared.execution(),ex.reason().name()); throw unavailable();
            } catch(IllegalArgumentException | IllegalStateException ex) {
                methodFailure(prepared.execution(),"INVALID_RESPONSE"); throw unavailable();
            }
            finally { metrics.timer("attempt.methods.duration").record(System.nanoTime()-started,java.util.concurrent.TimeUnit.NANOSECONDS); }
            metrics.counter("attempt.methods.outcome","status",evidence.status()).increment();
            if("UNAVAILABLE".equals(evidence.status())) { methodFailure(prepared.execution(),evidence.reasonCode()); throw unavailable(); }
            return tx.execute(t->{
                if(!prepared.identity().equals(identity(sources.current(owner,problem)))) throw stale();
                if(attempt!=null) owned(owner,problem,attempt,true);
                return new PublicComparison("method-comparison-public-v2",problem,attempt,prepared.identity(),"CURRENT_AT_PUBLICATION",false,evidence);
            });
        });
    }
    private void methodFailure(UUID execution,String reason) {
        metrics.counter("attempt.methods.failure","reason",reason).increment();
        LOG.warn("method_comparison request={} execution={} reason={}",
            com.verifiedai.sharedkernel.observability.CorrelationIds.current(),execution,reason);
    }
    private JsonNode comparisonSource(com.verifiedai.problem.application.solving.SolveSource current) {
        var result=codec.object(); var variables=result.putArray("variables");
        if("canonical-problem-v3".equals(codec.parse(current.canonicalJson()).path("schemaVersion").asText())) {
            var root=com.verifiedai.problem.application.attempt.AttemptSourceDocument.read(current);
            com.verifiedai.problem.application.attempt.AttemptSourceDocument.variables(root);
            for(var v:root.path("variables")) variables.addObject().put("symbol",v.path("symbol").asText()).put("domain",v.path("domain").asText());
            result.set("original",root.path("original")); result.set("restrictions",root.path("restrictions"));
        } else {
            var parsed=reader.readForExecution(current);
            if(parsed.reviewRequired() || parsed.statements().size()!=1 || parsed.variables().size()!=1
                || !parsed.assumptionIds().isEmpty() || parsed.calculus()!=null) throw unavailable();
            result.set("original",codec.json(parsed.statements().getFirst()));
            parsed.variables().forEach((v,d)->variables.addObject().put("symbol",v).put("domain",d.name()));
            var restrictions=result.putArray("restrictions");
            for(var r:parsed.restrictions()) {
                if(parsed.derivedRestrictionIds().contains(r.id())) continue;
                var item=restrictions.addObject().put("id",r.id()).put("relation",r.relation().name());
                item.set("left",codec.json(r.left())); item.set("right",codec.json(r.right()));
            }
        }
        boolean explicit=true;
        for(var v:variables) explicit &= "REAL".equals(v.path("domain").asText());
        result.put("domainBasis",explicit?"SOURCE_EXPLICIT":"UNRESOLVED");
        return result;
    }
    private static AttemptSourceIdentity identity(com.verifiedai.problem.application.solving.SolveSource s) {
        return new AttemptSourceIdentity(s.canonicalProblemId(),s.canonicalRevision(),s.problemParseId(),s.problemParseRevision());
    }
    private static ApiProblemException stale() { return AttemptAnalysisPolicy.error(ApiErrorCode.ATTEMPT_STALE_SOURCE,HttpStatus.CONFLICT); }
    public Map<String,Object> passport(UUID owner,UUID problem) {
        return operation(owner,()->tx.execute(t->{ sources.lockOwner(owner,problem); return store.passport(owner); }));
    }
    private AttemptLearningEvidence generate(UUID owner,UUID problem,UUID attempt,String action) {
        UUID execution=UUID.nameUUIDFromBytes(("attempt-learning-v1:"+action+":"+attempt).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String request=tx.execute(t->{
            var current=owned(owner,problem,attempt,true);
            if(!enabled || current.analysis()==null) throw unavailable();
            var root=codec.object().put("schemaVersion","attempt-learning-v1").put("executionId",execution.toString())
                .put("correlationId",execution.toString()).put("action",action);
            if(action.equals("TWIN")) {
                if(current.analysis().firstWrongStepIndex()==null) throw unavailable();
                var wrong=current.analysis().steps().get(current.analysis().firstWrongStepIndex()-1);
                if(wrong.diagnosis()==null || Set.of(MistakeCode.INVALID_TRANSFORMATION,MistakeCode.ALG_TRANSFORMATION_INVALID).contains(wrong.mistakeCode())) throw unavailable();
                root.put("mistakeCode",wrong.mistakeCode().name()); root.putNull("source");
            } else {
                String original=attempts.request(owner,attempt);
                if(original==null || !codec.parse(original).path("schemaVersion").asText().equals("attempt-verification-v2")) throw unavailable();
                root.set("source",codec.parse(original)); root.putNull("mistakeCode");
            }
            return root.toString();
        });
        AttemptLearningEvidence evidence;
        try { evidence=verifier.learning(execution,action,request); evidence.validateFor(execution,action); }
        catch(IllegalArgumentException | IllegalStateException ex) { throw unavailable(); }
        if(!"VERIFIED".equals(evidence.status())) throw unavailable();
        return evidence;
    }
    private StudentAttempt owned(UUID owner,UUID problem,UUID attempt,boolean fresh) {
        sources.lockOwner(owner,problem);
        var result=attempts.get(owner,problem,attempt).orElseThrow(()->notFound());
        if(fresh) {
            var current=sources.current(owner,problem); var identity=new AttemptSourceIdentity(current.canonicalProblemId(),current.canonicalRevision(),current.problemParseId(),current.problemParseRevision());
            if(!identity.equals(result.source())) throw AttemptAnalysisPolicy.error(ApiErrorCode.ATTEMPT_STALE_SOURCE,HttpStatus.CONFLICT);
        }
        return result;
    }
    private PublicDrill expose(AttemptLearningRepository.Drill d) {
        boolean done=d.completedAt()!=null; var a=d.artifact();
        return new PublicDrill("counterfactual-twin-v1",d.id(),d.attemptId(),a.problem(),a.options(),a.domainLabel(),a.difficulty(),done?"COMPLETED":"OPEN",
            d.selectedOption(),done?d.selectedOption()==a.correctOption():null,done?a.correctOption():null,done?a.explanationKey():null,
            d.assistance(),d.createdAt(),d.completedAt(),false);
    }
    private <T> T operation(UUID owner,Supplier<T> work) {
        if(!limiter.check(new RateLimitPolicy("attempt-learning",30,Duration.ofMinutes(1),true),owner.toString()).allowed())
            throw AttemptAnalysisPolicy.error(ApiErrorCode.RATE_LIMIT_EXCEEDED,HttpStatus.TOO_MANY_REQUESTS);
        try { return work.get(); }
        catch(org.springframework.dao.DataAccessException ex) { throw unavailable(); }
    }
    private static void key(String key) { if(key==null || !key.matches("[A-Za-z0-9_-]{8,128}")) throw validation(); }
    private static ApiProblemException validation() { return AttemptAnalysisPolicy.error(ApiErrorCode.REQUEST_VALIDATION_FAILED,HttpStatus.UNPROCESSABLE_ENTITY); }
    private static ApiProblemException unavailable() { return AttemptAnalysisPolicy.error(ApiErrorCode.ATTEMPT_UNAVAILABLE,HttpStatus.SERVICE_UNAVAILABLE); }
    private static ApiProblemException notFound() { return AttemptAnalysisPolicy.error(ApiErrorCode.ATTEMPT_NOT_FOUND,HttpStatus.NOT_FOUND); }
}
