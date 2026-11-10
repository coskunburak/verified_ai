package com.verifiedai.mistake;

import com.verifiedai.mistake.infrastructure.MistakeDnaRepository;
import com.verifiedai.sharedkernel.error.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;

@Service
public class LearnerMistakeDnaService {
    public record Entry(UUID eventId,UUID attemptId,UUID evaluationId,UUID executionId,UUID skillEvidenceId,int workRevision,String mistakeCode,
        String skillId,String effectiveStatus,Instant observedAt) { }
    public record View(String schemaVersion,boolean enabled,String policyVersion,Instant asOf,
        List<LearnerMistakeDnaProfile> profiles,List<Entry> entries,boolean limited,boolean trainingEligible) { }
    public record DisputeInput(String schemaVersion,UUID evaluationId,int workRevision) {
        public DisputeInput { if(!"mistake-dispute-request-v1".equals(schemaVersion) || evaluationId==null || workRevision<1 || workRevision>30) throw invalid(); }
    }
    public record Result(String schemaVersion,UUID operationId,UUID attemptId,UUID classificationId,
        UUID replacementAttemptId,String status,Instant createdAt,boolean trainingEligible) { }
    private final MistakeDnaRepository store;
    private final MistakeDnaEvidenceSource source;
    private final Clock clock;
    private final MeterRegistry metrics;
    private final boolean enabled;
    private final com.verifiedai.sharedkernel.ratelimit.RateLimiter limiter;
    public LearnerMistakeDnaService(MistakeDnaRepository store,MistakeDnaEvidenceSource source,Clock clock,MeterRegistry metrics,com.verifiedai.sharedkernel.ratelimit.RateLimiter limiter,
        @Value("${verifiedai.mistake.dna-enabled:false}") boolean enabled,
        @Value("${verifiedai.attempt.enabled:false}") boolean attemptsEnabled,
        @Value("${app.environment:local}") String environment,
        @Value("${verifiedai.attempt.production-authorized:false}") boolean authorized) {
        if(enabled && !Set.of("local","test").contains(environment) && !authorized) throw new IllegalArgumentException("DNA production authorization pending");
        this.limiter=limiter; this.store=store; this.source=source; this.clock=clock; this.metrics=metrics; this.enabled=enabled && attemptsEnabled;
    }
    public boolean enabled() { return enabled; }
    @Transactional(timeout=5)
    public View read(UUID owner,boolean full) {
        limit(owner); store.lock(owner); var now=clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        if(!enabled || !store.available()) return new View("mistake-dna-view-v1",false,EmpiricalBayesMistakeRateCalculator.POLICY,now,List.of(),List.of(),!full,false);
        var evidence=source.evidence(owner); var excluded=store.excluded(owner);
        project(owner,evidence,excluded,now);
        var profiles=store.profiles(owner);
        var entries=evidence.stream().sorted(Comparator.comparing(MistakeDnaEvidenceSource.Evidence::observedAt).reversed()
            .thenComparing(MistakeDnaEvidenceSource.Evidence::classificationId)).map(e->new Entry(e.classificationId(),e.attemptId(),e.evaluationId(),e.executionId(),e.skillEvidenceId(),e.workRevision(),
                e.decision().code(),e.decision().skillId(),status(e,excluded,now),e.observedAt())).toList();
        // FREE receives two grouped insights; private raw history remains a PRO capability.
        return new View("mistake-dna-view-v1",true,EmpiricalBayesMistakeRateCalculator.POLICY,now,
            full?profiles:profiles.stream().limit(2).toList(),full?entries:List.of(),!full,false);
    }
    public record DiagnosisView(String schemaVersion,UUID eventId,boolean enabled,String effectiveStatus) { }
    @Transactional(timeout=5)
    public DiagnosisView diagnosis(UUID owner,UUID event) {
        limit(owner); store.lock(owner);
        var e=source.evidence(owner).stream().filter(v->v.classificationId().equals(event)).findFirst().orElseThrow(LearnerMistakeDnaService::notFound);
        boolean active=enabled && store.available();
        return new DiagnosisView("mistake-effective-state-v1",event,active,
            active?status(e,store.excluded(owner),clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS)):"DISABLED");
    }
    private void limit(UUID owner) {
        if(!limiter.check(new com.verifiedai.sharedkernel.ratelimit.RateLimitPolicy("mistake-dna",30,Duration.ofMinutes(1),true),owner.toString()).allowed())
            throw error(HttpStatus.TOO_MANY_REQUESTS,ApiErrorCode.RATE_LIMIT_EXCEEDED);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void refresh(UUID owner) {
        if(enabled && store.available()) { store.lock(owner); project(owner,source.evidence(owner),store.excluded(owner),clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS)); }
    }
    private void project(UUID owner,List<MistakeDnaEvidenceSource.Evidence> evidence,Set<UUID> excluded,Instant now) {
        var active=evidence.stream().filter(e->"EFFECTIVE".equals(status(e,excluded,now))).toList();
        var next=new ArrayList<LearnerMistakeDnaProfile>();
        for(var mapping:new TreeMap<>(MistakeTaxonomy.SKILLS).entrySet()) {
            String code=mapping.getKey(),skill=mapping.getValue();
            var errors=active.stream().filter(e->code.equals(e.decision().code())).toList();
            if(errors.isEmpty()) continue;
            var observations=active.stream().filter(e->skill.equals(e.exposureSkill())).map(e->
                new EmpiricalBayesMistakeRateCalculator.Observation(e.evaluationId(),e.observedAt(),code.equals(e.decision().code()))).toList();
            var rate=new EmpiricalBayesMistakeRateCalculator().calculate(observations,now);
            next.add(new LearnerMistakeDnaProfile(code,skill,"attempt-taxonomy-v2",MistakeTaxonomy.VERSION,EmpiricalBayesMistakeRateCalculator.POLICY,
                rate.errors(),rate.exposures(),rate.propensity(),rate.recentErrors(),MistakeRecurrenceState.project(rate),
                errors.stream().map(MistakeDnaEvidenceSource.Evidence::observedAt).max(Comparator.naturalOrder()).orElseThrow(),now));
        }
        var prior=store.profiles(owner); store.replace(owner,next);
        for(var value:next) prior.stream().filter(p->p.mistakeCode().equals(value.mistakeCode()) && p.recurrenceState()!=value.recurrenceState()).findFirst()
            .ifPresent(p->{ p.recurrenceState().requireObservationTransition(value.recurrenceState()); afterTransition(p.recurrenceState(),value.recurrenceState()); });
        if(!prior.isEmpty() || !next.isEmpty()) afterCommit("mistake_dna_updated");
    }
    private static String status(MistakeDnaEvidenceSource.Evidence e,Set<UUID> excluded,Instant now) {
        if(excluded.contains(e.attemptId())) return "DISPUTED_OR_CORRECTED";
        if(e.superseded()) return "SUPERSEDED";
        if(e.observedAt().isAfter(now)) return "FUTURE_EVIDENCE";
        if(e.decision().status()==MistakeClassificationDecision.Status.SIGNATURE_SUPPORTED && e.decision().skillId()==null) return "UNMAPPED_SKILL";
        return e.verifiedExposure()?"EFFECTIVE":"INSUFFICIENT_EXPOSURE_EVIDENCE";
    }
    @Transactional(timeout=5)
    public Result dispute(UUID owner,UUID event,String key,DisputeInput input) {
        long start=System.nanoTime();
        try {
            limit(owner); key(key); store.lock(owner); available();
            String hash=hash("dispute:"+event+":"+input.evaluationId()+":"+input.workRevision());
            var replay=replay(owner,key,hash); if(replay.isPresent()) return replay.get();
            if(!enabled) throw unavailable();
            var e=source.evidence(owner).stream().filter(v->v.classificationId().equals(event)).findFirst().orElseThrow(LearnerMistakeDnaService::notFound);
            if(!e.evaluationId().equals(input.evaluationId()) || e.workRevision()!=input.workRevision()) throw conflict();
            if(store.hasOperation(owner,e.attemptId(),"DISPUTED_BY_LEARNER")) throw conflict();
            var operation=new MistakeDnaRepository.Operation(UUID.randomUUID(),e.attemptId(),event,null,null,"DISPUTED_BY_LEARNER",hash,clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
            store.insert(owner,key,operation); refresh(owner); afterCommit("attempt_diagnosis_disputed"); return result(operation);
        } finally { io.micrometer.core.instrument.Timer.builder("mistake.dna.rollback.duration").publishPercentileHistogram()
            .serviceLevelObjectives(Duration.ofMillis(250)).register(metrics).record(System.nanoTime()-start,java.util.concurrent.TimeUnit.NANOSECONDS); }
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public Optional<Result> correctionReplay(UUID owner,String key,String hash) {
        limit(owner); key(key); store.lock(owner); available();
        var replay=replay(owner,key,hash); if(replay.isEmpty() && !enabled) throw unavailable(); return replay;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public Result corrected(UUID owner,UUID attempt,UUID replacement,int step,String key,String hash) {
        var operation=new MistakeDnaRepository.Operation(UUID.randomUUID(),attempt,null,replacement,step,"SUPERSEDED_BY_OCR_CORRECTION",hash,clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        store.insert(owner,key,operation); refresh(owner); afterCommit("attempt_step_ocr_corrected"); return result(operation);
    }
    private Optional<Result> replay(UUID owner,String key,String hash) {
        return store.replay(owner,key).map(o->{if(!o.requestHash().equals(hash)) throw conflict(); return result(o);});
    }
    private static Result result(MistakeDnaRepository.Operation o) {
        return new Result("mistake-dispute-result-v1",o.id(),o.attemptId(),o.classificationId(),o.replacementAttemptId(),
            o.replacementAttemptId()==null?"EXCLUDED":"REEVALUATION_REQUIRED",o.createdAt(),false);
    }
    private void available() { if(!store.available()) throw unavailable(); }
    private void afterTransition(MistakeRecurrenceState from,MistakeRecurrenceState to) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            public void afterCommit() { metrics.counter("mistake_dna_state_transitioned","policy",EmpiricalBayesMistakeRateCalculator.POLICY,
                "from_state",from.name(),"to_state",to.name()).increment(); }
        });
    }
    private void afterCommit(String name) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            public void afterCommit() { metrics.counter(name,"policy",EmpiricalBayesMistakeRateCalculator.POLICY).increment(); }
        });
    }
    public static String hash(String value) {
        try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private static void key(String key) { if(key==null || !key.matches("[A-Za-z0-9_-]{8,128}")) throw invalid(); }
    private static ApiProblemException invalid() { return error(HttpStatus.UNPROCESSABLE_ENTITY,ApiErrorCode.REQUEST_VALIDATION_FAILED); }
    private static ApiProblemException conflict() { return error(HttpStatus.CONFLICT,ApiErrorCode.IDEMPOTENCY_KEY_REUSED); }
    private static ApiProblemException notFound() { return error(HttpStatus.NOT_FOUND,ApiErrorCode.ATTEMPT_NOT_FOUND); }
    private static ApiProblemException unavailable() { return error(HttpStatus.SERVICE_UNAVAILABLE,ApiErrorCode.ATTEMPT_UNAVAILABLE); }
    private static ApiProblemException error(HttpStatus status,ApiErrorCode code) { return new ApiProblemException(status,code,"Mistake DNA operation unavailable",true,"REVIEW"); }
}
