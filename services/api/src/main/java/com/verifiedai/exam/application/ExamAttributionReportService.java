package com.verifiedai.exam.application;

import tools.jackson.databind.json.JsonMapper;
import com.verifiedai.exam.config.ExamProperties;
import com.verifiedai.exam.domain.*;
import com.verifiedai.exam.domain.ExamAttributionModels.*;
import com.verifiedai.exam.events.*;
import com.verifiedai.exam.events.ExamAttributionGeneratedEvent.HighRoiTopicDirective;
import com.verifiedai.exam.infrastructure.persistence.*;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExamAttributionReportService {
    public static final String VERSION="H03_ATTRIBUTION_V1";
    public static class Failure extends RuntimeException {
        public final int status; public final String code;
        public Failure(int status,String code) { super(code);this.status=status;this.code=code; }
    }
    private final ExamProperties flags;
    private final ExamSessionRepository sessions;
    private final ExamScoringRepository scores;
    private final QuestionBankRepository questions;
    private final ExamAttributionRepository repository;
    private final ExamReadinessSignalReader signals;
    private final ExamPostAssessmentAttributionService classifier;
    private final ExamNetGainRoiSimulatorService roi;
    private final JdbcClient jdbc;
    private final JsonMapper mapper;
    private final ApplicationEventPublisher events;
    private final ObjectProvider<ExamStudyRebalancePort> rebalance;
    private final Clock clock;
    public ExamAttributionReportService(ExamProperties flags,ExamSessionRepository sessions,
            ExamScoringRepository scores,QuestionBankRepository questions,ExamAttributionRepository repository,
            ExamReadinessSignalReader signals,ExamPostAssessmentAttributionService classifier,
            ExamNetGainRoiSimulatorService roi,JdbcClient jdbc,JsonMapper mapper,
            ApplicationEventPublisher events,ObjectProvider<ExamStudyRebalancePort> rebalance,ObjectProvider<Clock> clock) {
        this.flags=flags;this.sessions=sessions;this.scores=scores;this.questions=questions;this.repository=repository;
        this.signals=signals;this.classifier=classifier;this.roi=roi;this.jdbc=jdbc;this.mapper=mapper;
        this.events=events;this.rebalance=rebalance;this.clock=clock.getIfAvailable(Clock::systemUTC);
    }
    private ExamSession owned(UUID user,UUID session) {
        if(!flags.isAttributionEnabled()) throw new Failure(403,"FEATURE_DISABLED");
        var found=sessions.findById(session).orElseThrow(()->new Failure(404,"SESSION_NOT_FOUND"));
        if(!found.userId().equals(user)) throw new Failure(404,"SESSION_NOT_FOUND");
        if(found.status()!=ExamSessionStatus.SUBMITTED && found.status()!=ExamSessionStatus.EXPIRED_AUTO_SUBMITTED)
            throw new Failure(409,"SESSION_NOT_SCORED");
        return found;
    }
    private void lock(UUID user) {
        // All concurrent H-03 mutations for one learner share one transaction-scoped lock.
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:user, 97))").param("user",user.toString()).query().singleRow();
    }
    @Transactional
    public Report report(UUID user,UUID sessionId,String language) {
        var session=owned(user,sessionId); lock(user);
        var score=scores.findScoreSnapshotBySessionId(sessionId).orElseThrow(()->new Failure(409,"SESSION_NOT_SCORED"));
        var mastery=signals.loadSkillMasterySignals(user);
        var traps=signals.loadActiveTrapSignals(user);
        Map<String,BigDecimal> active=new HashMap<>();
        for(var t:traps) active.merge(t.mistakeCode(),BigDecimal.valueOf(t.posteriorRate()),BigDecimal::max);
        var existing=repository.find(user,sessionId);
        Set<UUID> persisted=existing.stream().map(StoredAttribution::itemEvaluationId).collect(Collectors.toSet());
        Map<String,List<ExamSessionItemEvaluation>> bySkill=new TreeMap<>();
        Map<String,QuestionBankItem> skillQuestions=new HashMap<>();
        boolean generated=false;
        for(var evaluation:score.itemEvaluations()) {
            var question=questions.findById(evaluation.questionItemId()).orElseThrow(()->new Failure(409,"QUESTION_NOT_FOUND"));
            String skill=question.subtopicCode()==null||question.subtopicCode().isBlank()?question.topicCode():question.subtopicCode();
            bySkill.computeIfAbsent(skill,k->new ArrayList<>()).add(evaluation);skillQuestions.putIfAbsent(skill,question);
            if(persisted.contains(evaluation.evaluationId())) continue;
            var distractors=new HashMap<String,String>();
            jdbc.sql("SELECT option_key,distractor_mistake_code FROM exam_question_options WHERE question_item_id=:id AND distractor_mistake_code IS NOT NULL")
                    .param("id",question.id()).query().listOfRows().forEach(r->distractors.put((String)r.get("option_key"),(String)r.get("distractor_mistake_code")));
            String trap=distractors.values().stream().filter(active::containsKey).sorted().findFirst().orElse(null);
            var signal=mastery.getOrDefault(skill,mastery.get(question.topicCode()));
            BigDecimal m=signal==null?new BigDecimal("0.35"):BigDecimal.valueOf(signal.masteryScore());
            // V057 scratchpads contain metadata only; no diagnosis is invented from an attachment.
            var a=classifier.classify(new ExamPostAssessmentAttributionService.Input(evaluation.evaluationOutcome().name(),
                    evaluation.numericResponseValue()!=null?evaluation.numericResponseValue().toPlainString():evaluation.selectedOptionKey(),
                    question.topicCode(),question.subtopicCode(),skill,session.examCode(),question.estimatedTimeSeconds(),
                    evaluation.timeSpentSeconds(),m,null,distractors,trap,active));
            if(a==null) continue;
            String section=session.items().stream().filter(i->i.id().equals(evaluation.sessionItemId()))
                    .map(ExamSessionItem::sectionCode).findFirst().orElseThrow();
            repository.insert(new StoredAttribution(UUID.randomUUID(),sessionId,evaluation.evaluationId(),user,
                    evaluation.positionIndex(),section,question.topicCode(),question.subtopicCode(),a,clock.instant()));generated=true;
        }
        var stored=repository.find(user,sessionId);
        boolean en=language!=null && language.toLowerCase(Locale.ROOT).startsWith("en");
        BigDecimal total=stored.stream().map(r->r.attribution().estimatedNetImpact()).reduce(BigDecimal.ZERO,BigDecimal::add).setScale(2,RoundingMode.HALF_UP);
        var summaries=new ArrayList<CategorySummary>();
        for(var category:ExamPostAssessmentAttributionService.Category.values()) {
            var rows=stored.stream().filter(r->r.attribution().category()==category).toList();
            BigDecimal loss=rows.stream().map(r->r.attribution().estimatedNetImpact()).reduce(BigDecimal.ZERO,BigDecimal::add);
            String title=switch(category) {
                case CONCEPTUAL_GAP -> en?"Conceptual gaps":"Kavramsal Eksiklik";
                case RECURRING_MISTAKE_TRAP -> en?"Recurring mistake traps":"Tekrarlayan Tuzaklar";
                case TIME_PRESSURE_SLIP -> en?"Pacing and calculation slips":"Süre ve İşlem Kaymaları";
                case STRATEGIC_OMISSION -> en?"Strategic omissions":"Stratejik Boşlar";
            };
            summaries.add(new CategorySummary(category.name(),rows.size(),loss,
                    total.signum()==0?BigDecimal.ZERO:loss.multiply(BigDecimal.valueOf(100)).divide(total,2,RoundingMode.HALF_UP),title));
        }
        var inputs=new ArrayList<ExamNetGainRoiSimulatorService.SkillInput>();
        for(var entry:bySkill.entrySet()) {
            var q=skillQuestions.get(entry.getKey());var sig=mastery.getOrDefault(entry.getKey(),mastery.get(q.topicCode()));
            BigDecimal m=sig==null?new BigDecimal("0.35"):BigDecimal.valueOf(sig.masteryScore());
            BigDecimal theta=traps.stream().filter(t->entry.getKey().equals(t.skillCode())||q.topicCode().equals(t.skillCode()))
                    .map(t->BigDecimal.valueOf(t.posteriorRate())).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            BigDecimal loss=stored.stream().filter(r->r.attribution().skillId().equals(entry.getKey()))
                    .map(r->r.attribution().estimatedNetImpact()).reduce(BigDecimal.ZERO,BigDecimal::add);
            inputs.add(new ExamNetGainRoiSimulatorService.SkillInput(entry.getKey(),m,theta,
                    (int)entry.getValue().stream().filter(e->e.evaluationOutcome().name().equals("CORRECT")).count(),
                    entry.getValue().size(),BigDecimal.valueOf(entry.getValue().size()),loss));
        }
        var allocations=new ArrayList<RoiAllocation>();
        if(flags.isNetGainSimulatorEnabled()) for(var calc:roi.topThree(inputs)) {
            var q=skillQuestions.get(calc.skillId());
            var dominant=stored.stream().filter(r->r.attribution().skillId().equals(calc.skillId()))
                    .collect(Collectors.groupingBy(r->r.attribution().category(),()->new EnumMap<>(ExamPostAssessmentAttributionService.Category.class),Collectors.toList()))
                    .entrySet().stream().sorted(Comparator.<Map.Entry<ExamPostAssessmentAttributionService.Category,List<StoredAttribution>>>comparingInt(e->e.getValue().size()).reversed()
                            .thenComparing(e->e.getKey().name())).map(e->e.getValue().getFirst().attribution()).findFirst().orElse(null);
            var in=inputs.stream().filter(i->i.skillId().equals(calc.skillId())).findFirst().orElseThrow();
            String action=dominant==null?"MICRO_LESSON":dominant.recommendedActionType();
            String target=dominant==null?"microlesson:"+calc.skillId():dominant.recommendedTargetRef();
            String title=bySkill.get(calc.skillId()).getFirst().topicTitle();
            String rationale=en?String.format(Locale.ROOT,"%s: %.2f net lost; %.2f hours of practice, %.3f expected net/hour.",title,calc.sessionLostNet(),calc.estimatedRemediationHours(),calc.expectedNetGainPerHour())
                    :String.format(Locale.ROOT,"%s: %.2f net kayıp; %.2f saatlik çalışmayla saat başına +%.3f net bekleniyor.",title,calc.sessionLostNet(),calc.estimatedRemediationHours(),calc.expectedNetGainPerHour());
            allocations.add(new RoiAllocation(allocations.size()+1,q.topicCode(),title,calc.skillId(),title,
                    dominant==null?"CONCEPTUAL_GAP":dominant.category().name(),dominant==null?null:dominant.matchedMistakeCode(),
                    calc.sessionLostNet(),in.mastery(),calc.examSuccessProbability(),calc.expectedNetGainPerHour(),calc.estimatedRemediationHours(),
                    calc.estimatedNetRecoveryTotal(),action,target,rationale));
        }
        var details=stored.stream().map(r->{var a=r.attribution();return new ItemAttribution(r.attributionId(),r.itemEvaluationId(),r.itemOrder(),r.sectionCode(),r.topicId(),a.skillId(),a.itemOutcome(),a.category().name(),a.matchedMistakeCode(),a.evidenceSource(),a.timeSpentSeconds(),a.expectedSolveSeconds(),a.masterySnapshot(),a.estimatedNetImpact(),a.recommendedActionType(),a.recommendedTargetRef(),en?a.rationaleEn():a.rationaleTr());}).toList();
        var latest=jdbc.sql("SELECT batch_id FROM exam_study_reallocation_batches WHERE user_id=:u AND source_session_id=:s AND applied_to_study_plan=TRUE ORDER BY applied_at DESC,batch_id LIMIT 1")
                .param("u",user).param("s",sessionId).query(UUID.class).optional();
        Instant generatedAt=stored.stream().map(StoredAttribution::createdAt).min(Instant::compareTo).orElse(score.scoredAt());
        var report=new Report(sessionId,user,session.examCode(),generatedAt,VERSION,total,summaries,allocations,details,latest.isPresent(),latest.orElse(null),false);
        if(generated) events.publishEvent(new ExamAttributionGeneratedEvent(UUID.randomUUID(),user,sessionId,session.examCode(),total,allocations.stream().map(this::directive).toList(),clock.instant()));
        return report;
    }
    private HighRoiTopicDirective directive(RoiAllocation a) { return new HighRoiTopicDirective(a.rank(),a.topicId(),a.skillId(),a.dominantCategory(),a.matchedMistakeCode(),a.expectedNetGainPerHour(),a.estimatedRemediationHours(),a.recommendedActionType(),a.recommendedTargetRef()); }
    @Transactional
    public RebalanceResponse apply(UUID user,UUID session,String key,RebalanceRequest request) {
        owned(user,session);
        if(!flags.isNetGainSimulatorEnabled()) throw new Failure(403,"FEATURE_DISABLED");
        if(key==null||key.isBlank()||key.length()>128||request==null) throw new Failure(400,"INVALID_REBALANCE_REQUEST");
        lock(user);
        var previous=jdbc.sql("SELECT category_breakdown_json::text FROM exam_study_reallocation_batches WHERE user_id=:u AND source_session_id=:s AND idempotency_key=:k")
                .param("u",user).param("s",session).param("k",key).query(String.class).optional();
        if(previous.isPresent()) {
            try {
                var payload=mapper.readTree(previous.get());
                if(!payload.get("request").equals(mapper.valueToTree(request))) throw new Failure(409,"IDEMPOTENCY_KEY_CONFLICT");
                var r=mapper.treeToValue(payload.get("response"),RebalanceResponse.class);
                return new RebalanceResponse(r.batchId(),r.sourceSessionId(),r.userId(),r.examCode(),r.appliedToStudyPlan(),true,r.rebalancedStudyPlanId(),r.rebalancedTasksCount(),r.totalExpectedNetRecovery(),r.appliedDirectives(),r.appliedAt(),false);
            } catch(Failure ex) {throw ex;} catch(Exception ex) {throw new IllegalStateException("Invalid persisted rebalance response",ex);}
        }
        var report=report(user,session,"tr");
        var chosen=report.topRoiAllocations().stream().limit(request.maxTopicsToApply()).toList();
        if(chosen.isEmpty()) throw new Failure(409,"NO_REMEDIATION_NEEDED");
        var port=rebalance.getIfAvailable();if(port==null) throw new Failure(503,"STUDY_PLAN_UNAVAILABLE");
        UUID batch=UUID.randomUUID();Instant now=clock.instant();
        var result=port.apply(new ExamStudyRebalanceRequestedEvent(batch,user,session,report.examCode(),key,
                chosen.stream().map(this::directive).toList(),now,request.targetHorizonDays(),request.preferredDailyMinutes()));
        if(result.tasksCount()==0) throw new Failure(409,"NO_AVAILABLE_STUDY_CAPACITY");
        var applied=chosen.stream().filter(a->result.scheduledMinutes().containsKey(a.skillId())).toList();
        BigDecimal recovery=applied.stream().map(a->a.expectedNetGainPerHour().multiply(BigDecimal.valueOf(result.scheduledMinutes().get(a.skillId())))
                .divide(BigDecimal.valueOf(60),8,RoundingMode.HALF_UP).min(a.estimatedNetRecoveryTotal())).reduce(BigDecimal.ZERO,BigDecimal::add).setScale(2,RoundingMode.HALF_UP);
        var directives=applied.stream().map(a->new RebalanceDirective(a.rank(),a.topicId(),a.skillId(),a.expectedNetGainPerHour(),a.estimatedRemediationHours(),a.recommendedActionType(),a.recommendedTargetRef())).toList();
        var response=new RebalanceResponse(batch,session,user,report.examCode(),true,false,result.planId(),result.tasksCount(),recovery,directives,now,false);
        jdbc.sql("""
                INSERT INTO exam_study_reallocation_batches(batch_id,user_id,source_session_id,exam_code,idempotency_key,
                total_lost_net,category_breakdown_json,top_roi_plan_json,rebalanced_study_plan_id,applied_to_study_plan,applied_at)
                VALUES(:b,:u,:s,:e,:k,:loss,CAST(:payload AS jsonb),CAST(:plan AS jsonb),:p,TRUE,:now)
                """).param("b",batch).param("u",user).param("s",session).param("e",report.examCode()).param("k",key)
                .param("loss",report.totalLostNet()).param("payload",json(Map.of("request",request,"response",response,"categories",report.categorySummary())))
                .param("plan",json(directives)).param("p",result.planId()).param("now",java.sql.Timestamp.from(now)).update();
        return response;
    }
    private String json(Object value) {try{return mapper.writeValueAsString(value);}catch(Exception ex){throw new IllegalStateException(ex);}}
}
