package com.verifiedai.mastery;

import com.verifiedai.mastery.infrastructure.MasteryRepository;
import com.verifiedai.sharedkernel.ratelimit.*;
import com.verifiedai.sharedkernel.error.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;

@Service
public class MasteryApplicationService {
    public record Skill(String canonicalSkillCode,double posteriorMastery,double independentCompetence,double evidenceConfidence,
        int independentEvidenceCount,int assistedEvidenceCount,String status,boolean cappedByAssistance,double retention,
        double halfLifeDays,String algorithmVersion,Instant observedAt) { }
    public record Outcome(UUID attemptId,LearningEvidenceType evidenceType,Boolean observedCorrectness) { }
    public record View(String schemaVersion,boolean enabled,String algorithmVersion,Instant asOf,List<Skill> skills,List<Outcome> outcomes) { }
    private final MasteryRepository store;
    private final MasteryEvidenceSource source;
    private final Clock clock;
    private final MeterRegistry metrics;
    private final RateLimiter limiter;
    private final boolean enabled;
    private final EvidenceStratifiedMasteryPolicy policy=new EvidenceStratifiedMasteryPolicy();
    public MasteryApplicationService(MasteryRepository store,MasteryEvidenceSource source,Clock clock,MeterRegistry metrics,RateLimiter limiter,
        @Value("${verifiedai.mastery.evidence-stratified-enabled:false}") boolean enabled,
        @Value("${app.environment:local}") String environment) {
        // No production authorization is implied by enabling a flag before the owner release gate.
        if(enabled && !Set.of("local","test").contains(environment)) throw new IllegalArgumentException("Mastery v2 production gate pending");
        this.store=store; this.source=source; this.clock=clock; this.metrics=metrics; this.limiter=limiter; this.enabled=enabled;
    }
    @Transactional(timeout=5)
    public View read(UUID owner) {
        if(!limiter.check(new RateLimitPolicy("mastery-read",30,Duration.ofMinutes(1),true),owner.toString()).allowed())
            throw new ApiProblemException(HttpStatus.TOO_MANY_REQUESTS,ApiErrorCode.RATE_LIMIT_EXCEEDED,"Mastery read limit",true,"RETRY");
        store.lock(owner);
        return rebuild(owner);
    }
    @EventListener
    @Transactional(propagation=Propagation.MANDATORY)
    public void changed(MasteryEvidenceChanged event) {
        if(enabled) { store.lock(event.owner()); rebuild(event.owner()); }
    }
    private View rebuild(UUID owner) {
        Instant now=clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        if(!enabled) return new View("mastery-skills-v1",false,EvidenceStratifiedMasteryPolicy.VERSION,now,List.of(),List.of());
        if(!store.available()) throw new ApiProblemException(HttpStatus.SERVICE_UNAVAILABLE,ApiErrorCode.ATTEMPT_UNAVAILABLE,"Mastery migration unavailable",true,"RETRY");
        long start=System.nanoTime();
        try {
            var evidence=source.evidence(owner); var grouped=new TreeMap<String,List<MasteryEvidenceSource.Evidence>>();
            var outcomes=new ArrayList<Outcome>();
            for(var e:evidence) {
                boolean supported=e.type()!=LearningEvidenceType.UNSUPPORTED && e.skill()!=null && e.skillEvidenceId()!=null && !e.observedAt().isAfter(now);
                // Twin completion has a separate identity and must not overwrite the original attempt badge.
                if(!e.verifiedTwin()) outcomes.add(new Outcome(e.attemptId(),supported?e.type():LearningEvidenceType.UNSUPPORTED,supported?e.correct():null));
                if(supported) grouped.computeIfAbsent(e.skill(),k->new ArrayList<>()).add(e);
                else afterCommit("unsupported",1);
            }
            var skills=new ArrayList<Skill>();
            store.clearProjection(owner);
            for(var entry:grouped.entrySet()) {
                var p=policy.project(entry.getValue(),now); var s=p.state();
                String fingerprint=fingerprint(p.steps().stream().map(step->step.evidence().toString()).toList());
                int inserted=store.persist(owner,entry.getKey(),fingerprint,p,now);
                afterCommit(inserted==0?"duplicate":"accepted",inserted==0?1:inserted);
                if(inserted>0) for(var step:p.steps()) afterCommit(step.evidence().type().name(),1);
                skills.add(new Skill(entry.getKey(),s.score(),p.independentCompetence(),p.confidence(),s.independent(),s.assisted(),s.status(),
                    s.cappedByAssistance(),p.retention(),s.halfLife(),EvidenceStratifiedMasteryPolicy.VERSION,s.observedAt()));
            }
            outcomes.sort(Comparator.comparing(o->o.attemptId().toString()));
            return new View("mastery-skills-v1",true,EvidenceStratifiedMasteryPolicy.VERSION,now,List.copyOf(skills),List.copyOf(outcomes));
        } catch(RuntimeException ex) { metrics.counter("mastery.processing","policy",EvidenceStratifiedMasteryPolicy.VERSION,"outcome","failure").increment(); throw ex; }
        finally { metrics.timer("mastery.duration","policy",EvidenceStratifiedMasteryPolicy.VERSION).record(System.nanoTime()-start,java.util.concurrent.TimeUnit.NANOSECONDS); }
    }
    private void afterCommit(String outcome,int count) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            public void afterCommit() { metrics.counter("mastery.processing","policy",EvidenceStratifiedMasteryPolicy.VERSION,"outcome",outcome).increment(count); }
        });
    }
    static String fingerprint(List<String> values) {
        try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(
            (EvidenceStratifiedMasteryPolicy.VERSION+"\n"+String.join("\n",values)).getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
