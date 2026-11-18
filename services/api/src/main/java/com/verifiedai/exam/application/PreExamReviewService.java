package com.verifiedai.exam.application;

import com.verifiedai.exam.config.ExamProperties;
import com.verifiedai.exam.domain.*;
import com.verifiedai.exam.infrastructure.persistence.PreExamStudySessionRepository;
import com.verifiedai.exam.infrastructure.persistence.PreExamStudySessionRepository.Snapshot;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

@Service
public class PreExamReviewService {
    public static class Failure extends RuntimeException {
        public final int status;
        public final String code;
        public Failure(int status, String code) { super(code); this.status=status; this.code=code; }
    }
    private final ExamProperties flags;
    private final ExamRepository exams;
    private final CandidateProfileRepository profiles;
    private final ExamReadinessSignalReader signals;
    private final PreExamStudySessionRepository sessions;
    private final PreExamCatalog catalog;
    private final JsonMapper mapper;
    private final Clock clock;
    private final MeterRegistry metrics;
    public PreExamReviewService(ExamProperties flags, ExamRepository exams, CandidateProfileRepository profiles,
            ExamReadinessSignalReader signals, PreExamStudySessionRepository sessions, PreExamCatalog catalog,
            JsonMapper mapper, ObjectProvider<Clock> clocks, MeterRegistry metrics) {
        this.flags=flags; this.exams=exams; this.profiles=profiles; this.signals=signals; this.sessions=sessions;
        this.catalog=catalog; this.mapper=mapper; this.clock=clocks.getIfAvailable(Clock::systemUTC); this.metrics=metrics;
    }
    private void enabled() { if (!flags.isPreExamModeEnabled()) throw new Failure(403,"FEATURE_DISABLED"); }
    @Transactional
    public Snapshot review(UUID user, String code) {
        enabled();
        code=ExamReadinessProjectionService.canonicalExamCode(code);
        if (code == null || !Set.of("TR_YKS_TYT_MATH","TR_YKS_AYT_MATH").contains(code)) throw new Failure(404,"EXAM_NOT_FOUND");
        var definition=exams.findLatestByCode(code,false).orElseThrow(()->new Failure(404,"EXAM_NOT_FOUND"));
        sessions.lockOwnerExam(user,code);
        Instant now=clock.instant();
        var profile=profiles.findProfileByUserId(user).orElse(null);
        LocalDate date=profile != null && code.equals(ExamReadinessProjectionService.canonicalExamCode(profile.activeExamCode())) ? profile.targetDate() : null;
        var time=new ExamStartInstantResolver(clock,ZoneId.of("Europe/Istanbul")).resolveDate(date);
        var mastery=signals.loadSkillMasterySignals(user);
        var traps=signals.loadActiveTrapSignals(user);
        var selection=new PreExamReviewPolicy().select(mastery,traps,definition.topicWeights(),catalog.keys());
        var selected=selection.traps().stream().map(t->catalog.cards().stream()
                .filter(c->c.skillCode().equals(t.signal().skillCode()) && c.mistakeCode().equals(t.signal().mistakeCode()))
                .findFirst().orElseThrow()).toList();
        // Sort raw evidence representations so upstream iteration order cannot invalidate ETags.
        Map<String,Object> source=new TreeMap<>();
        source.put("user",user.toString()); source.put("exam",code); source.put("blueprint",definition.blueprintHash());
        source.put("weights",definition.topicWeights().stream().map(this::json).sorted().toList());
        source.put("mastery",new TreeMap<>(mastery)); source.put("traps",traps.stream().map(this::json).sorted().toList());
        source.put("time",time); source.put("catalog",catalog.contentHash()); source.put("policy","H15_V1");
        String fingerprint=digest(source);
        var existing=sessions.findFresh(user,code,fingerprint,now);
        if (existing.isPresent()) return existing.get();
        Instant midnight=LocalDate.now(clock.withZone(ZoneId.of("Europe/Istanbul"))).plusDays(1)
                .atStartOfDay(ZoneId.of("Europe/Istanbul")).toInstant();
        Instant expiry=now.plusSeconds(3600).isBefore(midnight) ? now.plusSeconds(3600) : midnight;
        UUID id=UUID.randomUUID();
        Map<String,Object> payload=new TreeMap<>();
        payload.put("sessionId",id.toString()); payload.put("userId",user.toString()); payload.put("examCode",code);
        payload.put("generatedAt",now.toString()); payload.put("expiresAt",expiry.toString());
        payload.put("algorithmVersion","H15_V1"); payload.put("catalogVersion","pre-exam-catalog-v1");
        payload.put("catalogContentHash",catalog.contentHash()); payload.put("targetExamDate",date==null?null:date.toString());
        payload.put("targetExamAt",null); payload.put("timePrecision",time.timePrecision().name());
        payload.put("windowStatus",time.windowStatus().name()); payload.put("hoursRemaining",null);
        payload.put("evidenceStatus",selected.isEmpty()?"INSUFFICIENT_EVIDENCE":"SUFFICIENT_EVIDENCE");
        payload.put("panicInhibitionActive",true); payload.put("masteryFloor",0.5);
        payload.put("excludedLowMasteryTopicCount",selection.excludedLowMasteryTopicCount());
        payload.put("activeTraps",selected); payload.put("highYieldFormulas",selected.stream().map(PreExamCatalog::formula).distinct().limit(12).toList());
        payload.put("genericReferences",selected.isEmpty()?catalog.cards().stream().map(PreExamCatalog::formula).limit(5).toList():List.of());
        payload.put("llmTokensUsed",0); payload.put("trainingEligible",false);
        payload.put("schemaVersion","pre-exam-review-v1");
        String reviewTag=digest(List.of(fingerprint,payload)); payload.put("etag",reviewTag); String reviewJson=json(payload);
        payload.remove("etag"); payload.put("schemaVersion","exam-cheat-sheet-v1");
        String sheetTag=digest(List.of(fingerprint,payload)); payload.put("etag",sheetTag);
        Snapshot row=new Snapshot(id,user,code,fingerprint,reviewTag,sheetTag,reviewJson,json(payload),now,expiry);
        sessions.insert(row); afterCommit("pre_exam_mode_activated"); return row;
    }
    @Transactional
    public void export(UUID user, UUID session, String key) {
        enabled();
        if (key==null || key.isBlank() || key.length()>128) throw new Failure(400,"INVALID_IDEMPOTENCY_KEY");
        boolean inserted=sessions.recordExport(user,session,key,clock.instant()).orElseThrow(()->new Failure(404,"SESSION_NOT_FOUND"));
        if (inserted) afterCommit("cheat_sheet_exported");
    }
    private void afterCommit(String event) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { metrics.counter(event).increment(); }
        });
    }
    private String json(Object value) { return mapper.writeValueAsString(value); }
    private String digest(Object value) { return PreExamCatalog.hash(json(value).getBytes(StandardCharsets.UTF_8)); }
}
