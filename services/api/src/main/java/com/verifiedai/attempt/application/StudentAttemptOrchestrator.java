package com.verifiedai.attempt.application;

import com.verifiedai.attempt.domain.*;
import com.verifiedai.attempt.infrastructure.persistence.StudentAttemptRepository;
import com.verifiedai.problem.application.solving.*;
import com.verifiedai.problem.application.attempt.ManualStepNormalizer;
import com.verifiedai.problem.application.attempt.AttemptSourceDocument;
import com.verifiedai.problem.application.attempt.AttemptExtractionReader;
import com.verifiedai.solving.application.canonical.CanonicalMathCodec;
import com.verifiedai.verification.application.VerificationSourceReader;
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

/** Durable prepare / unlocked deterministic execution / reauthorized publication. */
@Service
public class StudentAttemptOrchestrator {
    private final SolveSourceAccess sources;
    private final VerificationSourceReader reader;
    private final ManualStepNormalizer normalizer;
    private final CanonicalMathCodec codec;
    private final StudentAttemptRepository store;
    private final AttemptVerifierClient verifier;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final MeterRegistry metrics;
    private final RateLimiter limiter;
    private final boolean enabled;
    private final AttemptExtractionReader extraction;
    private final boolean visionEnabled;
    @Value("${verifiedai.attempt.structured-work-enabled:false}") private boolean structuredWorkEnabled;
    public StudentAttemptOrchestrator(SolveSourceAccess sources,VerificationSourceReader reader,ManualStepNormalizer normalizer,
        CanonicalMathCodec codec,StudentAttemptRepository store,AttemptVerifierClient verifier,PlatformTransactionManager transactions,
        Clock clock,MeterRegistry metrics,RateLimiter limiter,boolean enabled,String environment) {
        this(sources,reader,normalizer,codec,store,verifier,transactions,clock,metrics,limiter,enabled,environment,false,false,null);
    }
    @org.springframework.beans.factory.annotation.Autowired
    public StudentAttemptOrchestrator(SolveSourceAccess sources,VerificationSourceReader reader,ManualStepNormalizer normalizer,
        CanonicalMathCodec codec,StudentAttemptRepository store,AttemptVerifierClient verifier,PlatformTransactionManager transactions,
        Clock clock,MeterRegistry metrics,RateLimiter limiter,@Value("${verifiedai.attempt.enabled:false}") boolean enabled,
        @Value("${app.environment:local}") String environment,@Value("${verifiedai.attempt.production-authorized:false}") boolean productionAuthorized,
        @Value("${verifiedai.attempt.vision-enabled:false}") boolean visionEnabled,AttemptExtractionReader extraction) {
        if(enabled && !Set.of("local","test").contains(environment) && !productionAuthorized) throw new IllegalArgumentException("Attempt production authorization pending");
        if(visionEnabled && extraction==null) throw new IllegalArgumentException("Attempt extraction dependency required");
        this.extraction=extraction; this.visionEnabled=visionEnabled;
        this.sources=sources; this.reader=reader; this.normalizer=normalizer; this.codec=codec; this.store=store; this.verifier=verifier;
        this.tx=new TransactionTemplate(transactions); this.tx.setTimeout(5); this.clock=clock; this.metrics=metrics; this.limiter=limiter; this.enabled=enabled;
    }
    public record Context(String schemaVersion,AttemptSourceIdentity source,String originalStatement,boolean enabled,String handwriting,String twinDrill,String methodComparison,boolean structuredWorkEnabled) {}
    public Context context(UUID owner,UUID problem) {
        return operation(owner,()->tx.execute(s->{
            var source=sources.current(owner,problem); String original=null;
            try {
                if("canonical-problem-v3".equals(codec.parse(source.canonicalJson()).path("schemaVersion").asText())) original=AttemptSourceDocument.read(source).path("original").toString();
                else { var parsed=reader.readForExecution(source); if(parsed.statements().size()==1) original=codec.json(parsed.statements().getFirst()).toString(); }
            }
            catch(IllegalArgumentException | VerificationSourceReader.SourceRejected ignored) { }
            return new Context("attempt-context-v2",identity(source),original,enabled && store.available(),visionEnabled?"NATIVE_REVIEW_AND_GATEWAY":"NATIVE_REVIEW",enabled?"VERIFIED_TEMPLATES":"UNAVAILABLE",enabled?"VERIFIED_METHODS":"UNAVAILABLE",enabled && structuredWorkEnabled);
        }));
    }
    public List<StudentAttempt> list(UUID owner,UUID problem) {
        return operation(owner,()->tx.execute(s->{ sources.lockOwner(owner,problem); return store.available()?store.history(owner,problem):List.of(); }));
    }
    public StudentAttempt read(UUID owner,UUID problem,UUID id) {
        return operation(owner,()->tx.execute(s->{ sources.lockOwner(owner,problem); return owned(owner,problem,id); }));
    }
    public StudentAttempt submit(UUID owner,UUID problem,String key,AttemptSourceIdentity expected,List<String> lines) {
        return submit(owner,problem,key,expected,lines,false);
    }
    public StudentAttempt submit(UUID owner,UUID problem,String key,AttemptSourceIdentity expected,List<String> lines,boolean realDomainConfirmed) {
        return submit(owner,problem,key,expected,lines,realDomainConfirmed,null);
    }
    public StudentAttempt submit(UUID owner,UUID problem,String key,AttemptSourceIdentity expected,List<String> lines,boolean realDomainConfirmed,AttemptCaptureReview captureReview) {
        return operation(owner,()-> {
            AttemptAnalysisPolicy.submission(key,lines);
            if(expected==null) throw AttemptAnalysisPolicy.error(ApiErrorCode.REQUEST_VALIDATION_FAILED,HttpStatus.UNPROCESSABLE_ENTITY);
            final List<String> content=List.copyOf(lines);
            // Length-delimited JSON includes the expected source identity and exact untrimmed learner text.
            if(captureReview!=null && captureReview.rawLines().size()!=content.size()) throw AttemptAnalysisPolicy.error(ApiErrorCode.REQUEST_VALIDATION_FAILED,HttpStatus.UNPROCESSABLE_ENTITY);
            String hash=hash(codec.encode(captureReview!=null?List.of(expected,content,realDomainConfirmed,captureReview):realDomainConfirmed?List.of(expected,content,true):List.of(expected,content)));
            var prepared=tx.execute(s->{
                sources.lockOwner(owner,problem);
                if(!store.available()) throw unavailable();
                var replay=store.replay(owner,problem,key,hash);
                if(replay.isPresent()) { count("replay"); return replay.get(); }
                if(!enabled) throw unavailable();
                if(store.count(owner)>=30) throw AttemptAnalysisPolicy.error(ApiErrorCode.ATTEMPT_CAPACITY_EXCEEDED,HttpStatus.CONFLICT);
                var source=sources.current(owner,problem);
                if(!expected.equals(identity(source))) throw AttemptAnalysisPolicy.error(ApiErrorCode.ATTEMPT_STALE_SOURCE,HttpStatus.CONFLICT);
                if(captureReview!=null && captureReview.engine().equals("GATEWAY_RECOGNITION")) {
                    if(extraction==null || !visionEnabled) throw unavailable();
                    var recognized=extraction.read(owner,captureReview.assetSessionId(),Set.of(),normalizer);
                    if(!recognized.recognitionJobId().equals(captureReview.recognitionJobId())
                        || !recognized.steps().stream().map(com.verifiedai.problem.application.attempt.AttemptExtractionReader.Line::rawTranscription).toList().equals(captureReview.rawLines()))
                        throw AttemptAnalysisPolicy.error(ApiErrorCode.REQUEST_VALIDATION_FAILED,HttpStatus.UNPROCESSABLE_ENTITY);
                }
                UUID id=UUID.randomUUID(); var now=clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
                var normalized=new ArrayList<StudentAttemptStep>(); String request=null;
                try {
                    if("canonical-problem-v3".equals(codec.parse(source.canonicalJson()).path("schemaVersion").asText())) {
                        request=setRequest(source,id,content,normalized,realDomainConfirmed);
                    } else {
                    var parsed=reader.readForExecution(source);
                    if(parsed.reviewRequired() || parsed.statements().size()!=1 || parsed.variables().size()>1
                        || !parsed.assumptionIds().isEmpty()) throw new IllegalArgumentException("Unsupported source");
                    if(parsed.variables().values().stream().anyMatch(d->d.name().equals("INTEGER"))) throw new IllegalArgumentException("Integer scope is not real scope");
                    boolean explicit=parsed.variables().values().stream().allMatch(d->d.name().equals("REAL"));
                    // Confirmation is an explicit scope claim, retained in immutable evidence, never inferred from UNKNOWN.
                    boolean v2=explicit || realDomainConfirmed || (parsed.statements().getFirst() instanceof com.verifiedai.solving.domain.model.canonical.MathStatement.Comparison comparison
                        && comparison.relation()!=com.verifiedai.solving.domain.model.canonical.MathStatement.Relation.EQUALS);
                    var n=codec.object().put("schemaVersion",v2?"attempt-verification-v2":"attempt-verification-v1").put("executionId",id.toString()).put("correlationId",id.toString());
                    if(v2) n.put("domainBasis",explicit?"SOURCE_EXPLICIT":realDomainConfirmed?"REVIEWED_REAL_TASK":"UNRESOLVED");
                    var original=codec.json(parsed.statements().getFirst());
                    if(parsed.calculus()!=null) {
                        var c=codec.parse(source.canonicalJson()).path("calculus");
                        var function=codec.object().put("kind","FUNCTION").put("function",switch(parsed.taskType()) {
                            case DIFFERENTIATE -> "DIFF"; case FIND_LIMIT -> "LIMIT"; case INTEGRATE -> "INTEGRAL";
                            default -> throw new IllegalArgumentException("Unsupported calculus task"); });
                        var args=function.putArray("args"); args.add(c.path("original"));
                        if(parsed.taskType().name().equals("FIND_LIMIT")) {
                            if(!c.path("approach").path("direction").asText().equals("TWO_SIDED")) throw new IllegalArgumentException("Two sided limits only");
                            args.add(exactNode(c.path("approach").path("target")));
                        }
                        if(parsed.taskType().name().equals("INTEGRATE")) {
                            if(!c.path("integralScope").asText().equals("DEFINITE")) throw new IllegalArgumentException("Definite integrals only");
                            args.add(exactNode(c.path("interval").path("lower"))); args.add(exactNode(c.path("interval").path("upper")));
                        }
                        original=codec.object().put("kind","EXPRESSION").set("expression",function);
                    }
                    n.set("original",original);
                    var vars=n.putArray("variables"); parsed.variables().forEach((v,d)->vars.addObject().put("symbol",v).put("domain",explicit || realDomainConfirmed?"REAL":d.name()));
                    var restrictions=n.putArray("restrictions");
                    if(parsed.calculus()!=null && parsed.taskType().name().equals("DIFFERENTIATE")) {
                        var interval=codec.parse(source.canonicalJson()).path("calculus").path("interval");
                        String symbol=parsed.variables().keySet().iterator().next();
                        for(String bound:List.of("lower","upper")) if(!interval.path(bound).isNull()) {
                            var r=restrictions.addObject().put("id","source-interval-"+bound).put("relation",bound.equals("lower")?"GREATER_THAN_OR_EQUAL":"LESS_THAN_OR_EQUAL");
                            r.set("left",codec.object().put("kind","VARIABLE").put("symbol",symbol)); r.set("right",exactNode(interval.path(bound)));
                        }
                    }
                    for(var r:parsed.restrictions()) {
                        // Derived exclusions must be recomputed from each original syntax tree. Applying them to a
                        // transformed equation would conceal extraneous roots, e.g. x/x=1 -> x=x.
                        if(v2 && parsed.derivedRestrictionIds().contains(r.id())) continue;
                        var item=restrictions.addObject().put("id",r.id()).put("relation",r.relation().name());
                        item.set("left",codec.json(r.left())); item.set("right",codec.json(r.right()));
                    }
                    var steps=n.putArray("steps");
                    for(int i=0;i<content.size();i++) {
                        String statement=v2?normalizer.normalizeV2(content.get(i),parsed.variables().keySet()):normalizer.normalize(content.get(i),parsed.variables().keySet());
                        normalized.add(new StudentAttemptStep(i+1,content.get(i),statement));
                        steps.addObject().set("statement",statement==null?com.fasterxml.jackson.databind.node.NullNode.instance:codec.parse(statement));
                    }
                    request=n.toString();
                    // Leave room for PostgreSQL JSONB formatting within the persisted/wire bound.
                    if(request.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>32768) request=null;
                    }
                } catch(IllegalArgumentException | VerificationSourceReader.SourceRejected ignored) { normalized.clear(); }
                if(normalized.isEmpty()) for(int i=0;i<content.size();i++) normalized.add(new StudentAttemptStep(i+1,content.get(i),null));
                var attempt=new StudentAttempt("student-attempt-v1",id,problem,expected,AttemptSourceType.MANUAL,request!=null && request.contains("attempt-verification-v2")?"manual-canonical-v2":"manual-canonical-v1",AttemptStatus.SUBMITTED,normalized,null,now,now,false,captureReview);
                store.insert(owner,key,hash,attempt,request); accepted(); return attempt;
            });
            if(prepared.status()!=AttemptStatus.SUBMITTED) return prepared;
            if(prepared.finalAnswer()!=null || !enabled) return prepared; // rollback preserves history and does not dispatch work
            String request=tx.execute(s->{ sources.lockOwner(owner,problem); owned(owner,problem,prepared.id()); return store.request(owner,prepared.id()); });
            // No transaction/owner/source lock spans this bounded internal HTTP call.
            AttemptAnalysisResult result;
            try {
                result=verifier.verify(prepared.id(),prepared.steps().size(),request);
                result.validateFor(prepared.id(),prepared.steps().size());
            } catch(RuntimeException ex) {
                // Acceptance is already durable. Reauthorize before returning a recoverable saved state.
                count("recovery");
                return tx.execute(s->{
                    sources.lockOwner(owner,problem); var current=owned(owner,problem,prepared.id());
                    if(current.status()==AttemptStatus.SUBMITTED && !fresh(owner,problem,current)) {
                        store.stale(owner,current.id(),clock.instant()); count("stale_source");
                    }
                    return owned(owner,problem,current.id());
                });
            }
            return tx.execute(s->{
                sources.lockOwner(owner,problem); var current=owned(owner,problem,prepared.id());
                if(current.status()!=AttemptStatus.SUBMITTED) return current;
                if(!fresh(owner,problem,current)) { store.stale(owner,current.id(),clock.instant()); count("stale_source"); }
                else if(result.steps().stream().anyMatch(step->Set.of(AttemptAnalysisResult.Reason.TIMEOUT,
                    AttemptAnalysisResult.Reason.RESOURCE_FAILURE,AttemptAnalysisResult.Reason.WORKER_FAILURE,
                    AttemptAnalysisResult.Reason.ADMISSION_LIMIT,AttemptAnalysisResult.Reason.DISABLED,AttemptAnalysisResult.Reason.INVALID_RESPONSE).contains(step.reasonCode()))) {
                    // Preserve SUBMITTED and exact request for safe same-key recovery after a crash/timeout.
                    count("verifier_unavailable");
                } else {
                    store.publish(owner,current.id(),result,clock.instant());
                    for(var step:result.steps()) {
                        metrics.counter("attempt.step","verdict",step.verdict().name(),"reason",step.reasonCode().name()).increment();
                        metrics.counter("attempt.counterexample","status",step.counterexampleStatus().name()).increment();
                    }
                    count(result.outcome().name());
                }
                return owned(owner,problem,current.id());
            });
        });
    }
    /** Capture-only v3: no normalization, verifier call or learning projection. */
    public StudentAttempt submitAnswer(UUID owner,UUID problem,String key,AttemptSourceIdentity expected,List<String> lines,String finalAnswer) {
        return operation(owner,()-> {
            AttemptAnalysisPolicy.answerSubmission(key,lines,finalAnswer);
            if(expected==null) throw AttemptAnalysisPolicy.error(ApiErrorCode.REQUEST_VALIDATION_FAILED,HttpStatus.UNPROCESSABLE_ENTITY);
            var content=List.copyOf(lines);
            String hash=hash(codec.encode(List.of("attempt-submission-v3",expected,content,finalAnswer)));
            return tx.execute(s->{
                sources.lockOwner(owner,problem);
                if(!store.available()) throw unavailable();
                var replay=store.replay(owner,problem,key,hash);
                if(replay.isPresent()) { count("replay"); return replay.get(); }
                if(!enabled) throw unavailable();
                if(store.count(owner)>=30) throw AttemptAnalysisPolicy.error(ApiErrorCode.ATTEMPT_CAPACITY_EXCEEDED,HttpStatus.CONFLICT);
                if(!expected.equals(identity(sources.current(owner,problem))))
                    throw AttemptAnalysisPolicy.error(ApiErrorCode.ATTEMPT_STALE_SOURCE,HttpStatus.CONFLICT);
                var steps=new ArrayList<StudentAttemptStep>();
                for(int i=0;i<content.size();i++) steps.add(new StudentAttemptStep(i+1,content.get(i),null));
                var now=clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
                var attempt=new StudentAttempt("student-attempt-v2",UUID.randomUUID(),problem,expected,AttemptSourceType.MANUAL,
                    "manual-canonical-v1",AttemptStatus.SUBMITTED,steps,null,now,now,false,null,finalAnswer);
                store.insert(owner,key,hash,attempt,null); accepted(); return attempt;
            });
        });
    }
    private String setRequest(SolveSource source,UUID id,List<String> content,List<StudentAttemptStep> normalized,boolean confirmed) {
        var doc=AttemptSourceDocument.read(source); var symbols=AttemptSourceDocument.variables(doc);
        boolean explicit=true; for(var v:doc.path("variables")) if(!v.path("domain").asText().equals("REAL")) explicit=false;
        var root=codec.object().put("schemaVersion","attempt-verification-v2").put("executionId",id.toString()).put("correlationId",id.toString())
            .put("domainBasis",explicit?"SOURCE_EXPLICIT":confirmed?"REVIEWED_REAL_TASK":"UNRESOLVED");
        root.set("original",doc.path("original")); root.set("restrictions",doc.path("restrictions"));
        var vars=root.putArray("variables"); for(var v:doc.path("variables")) vars.addObject().put("symbol",v.path("symbol").asText()).put("domain",explicit || confirmed?"REAL":"UNKNOWN");
        var steps=root.putArray("steps");
        for(int i=0;i<content.size();i++) {
            String statement=normalizer.normalizeV2(content.get(i),symbols); normalized.add(new StudentAttemptStep(i+1,content.get(i),statement));
            steps.addObject().set("statement",statement==null?com.fasterxml.jackson.databind.node.NullNode.instance:codec.parse(statement));
        }
        String request=root.toString(); return request.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=32768?request:null;
    }
    private com.fasterxml.jackson.databind.JsonNode exactNode(com.fasterxml.jackson.databind.JsonNode value) {
        var fraction=codec.object().put("kind","BINARY").put("operator","DIVIDE");
        fraction.set("left",codec.object().put("kind","NUMBER").put("numericType","INTEGER").put("value",value.path("numerator").asText()));
        fraction.set("right",codec.object().put("kind","NUMBER").put("numericType","INTEGER").put("value",value.path("denominator").asText()));
        return fraction;
    }
    private boolean fresh(UUID owner,UUID problem,StudentAttempt current) {
        try { return current.source().equals(identity(sources.current(owner,problem))); }
        catch(ApiProblemException ex) { if(ex.code()!=ApiErrorCode.SOLVE_SOURCE_NOT_READY) throw ex; return false; }
    }
    private StudentAttempt owned(UUID owner,UUID problem,UUID id) {
        if(!store.available()) throw AttemptAnalysisPolicy.error(ApiErrorCode.ATTEMPT_NOT_FOUND,HttpStatus.NOT_FOUND);
        return store.get(owner,problem,id).orElseThrow(()->AttemptAnalysisPolicy.error(ApiErrorCode.ATTEMPT_NOT_FOUND,HttpStatus.NOT_FOUND));
    }
    private AttemptSourceIdentity identity(SolveSource source) { return new AttemptSourceIdentity(source.canonicalProblemId(),source.canonicalRevision(),source.problemParseId(),source.problemParseRevision()); }
    private ApiProblemException unavailable() { count("disabled"); return AttemptAnalysisPolicy.error(ApiErrorCode.ATTEMPT_UNAVAILABLE,HttpStatus.SERVICE_UNAVAILABLE); }
    private <T> T operation(UUID owner,Supplier<T> action) {
        long started=System.nanoTime();
        try {
            if(!limiter.check(new RateLimitPolicy("attempt-requests",60,Duration.ofMinutes(1),true),owner.toString()).allowed())
                throw AttemptAnalysisPolicy.error(ApiErrorCode.RATE_LIMIT_EXCEEDED,HttpStatus.TOO_MANY_REQUESTS);
            return action.get();
        } catch(org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException ex) {
            count("persistence_failure");
            throw AttemptAnalysisPolicy.error(ApiErrorCode.TEMPORARY_UNAVAILABLE,HttpStatus.SERVICE_UNAVAILABLE);
        } catch(ApiProblemException ex) { count(ex.code().name()); throw ex; }
        finally {
            io.micrometer.core.instrument.Timer.builder("attempt.request").publishPercentileHistogram()
                .serviceLevelObjectives(Duration.ofMillis(100),Duration.ofMillis(500),Duration.ofSeconds(2),Duration.ofSeconds(15))
                .register(metrics).record(System.nanoTime()-started,TimeUnit.NANOSECONDS);
        }
    }
    private void accepted() {
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
                @Override public void afterCommit() { count("submitted"); }
            });
    }
    private void count(String outcome) { metrics.counter("attempt.outcome","outcome",outcome).increment(); }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
}
