package com.verifiedai.attempt.application;

import com.verifiedai.attempt.domain.*;
import com.verifiedai.attempt.infrastructure.persistence.*;
import com.verifiedai.problem.application.attempt.*;
import com.verifiedai.problem.application.solving.*;
import com.verifiedai.verification.application.VerificationSourceReader;
import com.verifiedai.sharedkernel.error.*;
import com.verifiedai.sharedkernel.ratelimit.*;
import com.verifiedai.solving.application.canonical.CanonicalMathCodec;
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

/** Capture and syntax only. Owner/source locks cover the entire deterministic transaction. */
@Service
public class AttemptWorkService {
    private final SolveSourceAccess sources;
    private final StudentAttemptRepository attempts;
    private final AttemptWorkRepository works;
    private final AttemptWorkNormalizer normalizer;
    private final ManualStepNormalizer parser;
    private final AttemptExtractionReader extraction;
    private final VerificationSourceReader reader;
    private final CanonicalMathCodec codec;
    private final MeterRegistry metrics;
    private final RateLimiter limiter;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final boolean enabled;
    public AttemptWorkService(SolveSourceAccess sources,StudentAttemptRepository attempts,AttemptWorkRepository works,
        AttemptWorkNormalizer normalizer,ManualStepNormalizer parser,AttemptExtractionReader extraction,VerificationSourceReader reader,
        CanonicalMathCodec codec,MeterRegistry metrics,RateLimiter limiter,Clock clock,PlatformTransactionManager manager,
        @Value("${verifiedai.attempt.structured-work-enabled:false}") boolean enabled,
        @Value("${verifiedai.attempt.enabled:false}") boolean attemptEnabled,
        @Value("${app.environment:local}") String environment,
        @Value("${verifiedai.attempt.production-authorized:false}") boolean productionAuthorized) {
        if(enabled && !Set.of("local","test").contains(environment) && !productionAuthorized)
            throw new IllegalArgumentException("Structured work production authorization pending");
        this.enabled=enabled && attemptEnabled; this.sources=sources; this.attempts=attempts; this.works=works;
        this.normalizer=normalizer; this.parser=parser; this.extraction=extraction; this.reader=reader; this.codec=codec;
        this.metrics=metrics; this.limiter=limiter; this.clock=clock; tx=new TransactionTemplate(manager); tx.setTimeout(5);
    }
    public boolean enabled() { return enabled; }
    public AttemptStructuredWork read(UUID owner,UUID problem,UUID attempt) {
        return operation(owner,()->tx.execute(t->{
            sources.lockOwner(owner,problem);
            owned(owner,problem,attempt);
            return works.available()?works.get(owner,attempt).orElseThrow(AttemptWorkService::notFound):missing();
        }));
    }
    public AttemptStructuredWork submit(UUID owner,UUID problem,String key,AttemptWorkInput input) {
        return operation(owner,()->{
            AttemptAnalysisPolicy.submission(key,input.steps().stream().map(AttemptWorkInput.Step::text).toList());
            String encoded=codec.encode(input);
            if(encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>16384) throw error(ApiErrorCode.REQUEST_TOO_LARGE,HttpStatus.PAYLOAD_TOO_LARGE);
            String hash=java.util.HexFormat.of().formatHex(digest(encoded));
            return tx.execute(t->{
                sources.lockOwner(owner,problem);
                if(!attempts.available() || !works.available()) throw error(ApiErrorCode.ATTEMPT_UNAVAILABLE,HttpStatus.SERVICE_UNAVAILABLE);
                var replay=attempts.replay(owner,problem,key,hash);
                if(replay.isPresent()) { count("replay"); return works.get(owner,replay.get().id()).orElseThrow(AttemptWorkService::notFound); }
                if(!enabled) throw error(ApiErrorCode.ATTEMPT_UNAVAILABLE,HttpStatus.SERVICE_UNAVAILABLE);
                if(attempts.count(owner)>=30) throw error(ApiErrorCode.ATTEMPT_CAPACITY_EXCEEDED,HttpStatus.CONFLICT);
                var source=sources.current(owner,problem);
                if(!input.source().equals(identity(source))) throw error(ApiErrorCode.ATTEMPT_STALE_SOURCE,HttpStatus.CONFLICT);
                int revision=1; AttemptStructuredWork previous=null;
                if(input.supersedesAttemptId()!=null) {
                    var prior=owned(owner,problem,input.supersedesAttemptId());
                    if(!prior.source().equals(input.source())) throw error(ApiErrorCode.ATTEMPT_STALE_SOURCE,HttpStatus.CONFLICT);
                    previous=works.get(owner,prior.id()).orElseThrow(AttemptWorkService::notFound);
                    if(works.superseded(owner,prior.id())) throw error(ApiErrorCode.OPTIMISTIC_CONFLICT,HttpStatus.CONFLICT);
                    if(previous.engine()!=input.engine() || !Objects.equals(previous.captureId(),input.captureId())
                        || !Objects.equals(previous.assetSessionId(),input.assetSessionId()) || !Objects.equals(previous.recognitionJobId(),input.recognitionJobId())
                        || !codec.parse(codec.encode(previous.observations())).equals(codec.parse(codec.encode(input.observations())))) invalid();
                    revision=previous.revision()+1;
                }
                AttemptExtractionReader.Extraction recognized=null;
                if(input.engine()==AttemptWorkInput.Engine.GATEWAY_RECOGNITION && previous==null) {
                    recognized=extraction.read(owner,input.assetSessionId(),Set.of(),parser);
                    validateRecognition(input,recognized);
                }
                Set<String> variables=null;
                try {
                    variables=source.canonicalJson().contains("canonical-problem-v3")?
                        AttemptSourceDocument.variables(AttemptSourceDocument.read(source)):reader.readForExecution(source).variables().keySet();
                } catch(IllegalArgumentException | VerificationSourceReader.SourceRejected ignored) { /* Explicit unsupported syntax below. */ }
                var steps=normalizer.normalize(input,variables);
                UUID id=UUID.randomUUID(); var now=clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
                var work=new AttemptStructuredWork("attempt-structured-work-v1",id,owner,problem,input.source(),revision,
                    input.supersedesAttemptId(),"structured-canonical-v1",now.toString(),input.engine(),
                    input.engine()==AttemptWorkInput.Engine.GATEWAY_RECOGNITION?"SERVER_RECORDED":"CLIENT_ATTESTED",
                    input.captureId(),input.assetSessionId(),input.recognitionJobId(),recognized==null?(previous==null?null:previous.provider()):recognized.provider(),
                    recognized==null?(previous==null?null:previous.model()):recognized.model(),recognized==null?(previous==null?null:previous.promptVersion()):recognized.promptVersion(),input.observations(),steps,false);
                var legacy=new ArrayList<StudentAttemptStep>(); String answer=null;
                for(var step:input.steps()) {
                    if(step.role()==AttemptWorkInput.Role.FINAL_ANSWER) answer=step.text();
                    else legacy.add(new StudentAttemptStep(legacy.size()+1,step.text(),null));
                }
                var attempt=new StudentAttempt(answer==null?"student-attempt-v1":"student-attempt-v2",id,problem,input.source(),
                    AttemptSourceType.MANUAL,"manual-canonical-v1",AttemptStatus.SUBMITTED,legacy,null,now,now,false,null,answer);
                attempts.insert(owner,key,hash,attempt,null); works.insert(work);
                // Publish metrics only after durable acceptance.
                org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        public void afterCommit() {
                            count("saved");
                            for(var step:steps) metrics.counter("attempt.structured.normalization","outcome",step.normalizationStatus().name()).increment();
                        }
                    });
                return work;
            });
        });
    }
    static void validateRecognition(AttemptWorkInput input,AttemptExtractionReader.Extraction evidence) {
        if(!input.assetSessionId().equals(evidence.assetSessionId()) || !input.recognitionJobId().equals(evidence.recognitionJobId()) || input.observations().size()!=evidence.steps().size()) invalid();
        for(int i=0;i<input.observations().size();i++) {
            var observed=input.observations().get(i); var recorded=evidence.steps().get(i); var box=recorded.boundingBox();
            var expected=new AttemptWorkInput.Box(box.x(),box.y(),box.width(),box.height());
            if(!observed.rawText().equals(recorded.rawTranscription()) || !same(observed.confidence(),recorded.confidence())
                || observed.box()==null || !same(observed.box().x(),expected.x()) || !same(observed.box().y(),expected.y())
                || !same(observed.box().width(),expected.width()) || !same(observed.box().height(),expected.height())) invalid();
        }
    }
    private static boolean same(java.math.BigDecimal a,java.math.BigDecimal b) { return a==null?b==null:b!=null && a.compareTo(b)==0; }
    private StudentAttempt owned(UUID owner,UUID problem,UUID id) { return attempts.get(owner,problem,id).orElseThrow(AttemptWorkService::notFound); }
    private static AttemptSourceIdentity identity(SolveSource s) { return new AttemptSourceIdentity(s.canonicalProblemId(),s.canonicalRevision(),s.problemParseId(),s.problemParseRevision()); }
    private <T> T operation(UUID owner,Supplier<T> operation) {
        long start=System.nanoTime();
        try {
            if(!limiter.check(new RateLimitPolicy("attempt-structured-work",30,Duration.ofMinutes(1),true),owner.toString()).allowed())
                throw error(ApiErrorCode.RATE_LIMIT_EXCEEDED,HttpStatus.TOO_MANY_REQUESTS);
            return operation.get();
        } catch(ApiProblemException ex) { count(ex.code().name()); throw ex; }
        catch(org.springframework.dao.DataAccessException ex) { count("persistence_failure"); throw error(ApiErrorCode.ATTEMPT_UNAVAILABLE,HttpStatus.SERVICE_UNAVAILABLE); }
        finally { metrics.timer("attempt.structured.duration").record(System.nanoTime()-start,TimeUnit.NANOSECONDS); }
    }
    private void count(String outcome) { metrics.counter("attempt.structured.outcome","outcome",outcome).increment(); }
    private static byte[] digest(String input) {
        try { return java.security.MessageDigest.getInstance("SHA-256").digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
        catch(java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private static void invalid() { throw error(ApiErrorCode.REQUEST_VALIDATION_FAILED,HttpStatus.UNPROCESSABLE_ENTITY); }
    private static ApiProblemException error(ApiErrorCode code,HttpStatus status) { return AttemptAnalysisPolicy.error(code,status); }
    private static ApiProblemException notFound() { return error(ApiErrorCode.ATTEMPT_NOT_FOUND,HttpStatus.NOT_FOUND); }
    private static AttemptStructuredWork missing() { throw notFound(); }
}
