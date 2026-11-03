package com.verifiedai.attempt.infrastructure.persistence;

import com.fasterxml.jackson.databind.*;
import com.verifiedai.attempt.domain.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Called under the owning account lock and a short transaction, including privacy operations. */
@Repository
public class AttemptLearningRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper=new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    public AttemptLearningRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public record Drill(UUID id,UUID attemptId,AttemptLearningEvidence.Twin artifact,Integer selectedOption,
        String answerKey,String assistance,Instant createdAt,Instant completedAt) { }
    public Optional<Drill> drill(UUID owner,UUID attempt) {
        return jdbc.query("select * from counterfactual_twin_drills where user_id=? and attempt_id=?",(r,n)->new Drill(
            r.getObject("id",UUID.class),attempt,decode(r.getString("artifact"),AttemptLearningEvidence.Twin.class),
            (Integer)r.getObject("selected_option"),r.getString("answer_key"),r.getString("assistance"),r.getTimestamp("created_at").toInstant(),
            r.getTimestamp("completed_at")==null?null:r.getTimestamp("completed_at").toInstant()),owner,attempt).stream().findFirst();
    }
    public void insertDrill(UUID owner,UUID attempt,UUID id,AttemptLearningEvidence.Twin twin,Instant now) {
        jdbc.update("insert into counterfactual_twin_drills(id,user_id,attempt_id,artifact,correct_option,created_at) values(?,?,?,?::jsonb,?,?)",
            id,owner,attempt,encode(twin),twin.correctOption(),Timestamp.from(now));
    }
    public void complete(UUID owner,Drill drill,int selected,String key,boolean assisted,Instant now) {
        int changed=jdbc.update("update counterfactual_twin_drills set selected_option=?,answer_key=?,assistance=?,completed_at=? where id=? and user_id=? and completed_at is null",
            selected,key,assisted?"HINT_ASSISTED":"INDEPENDENT",Timestamp.from(now),drill.id(),owner);
        if(changed!=1) throw new IllegalStateException("Concurrent drill completion");
        observe(owner,drill.attemptId(),drill.id(),drill.artifact().mistakeCode(),selected==drill.artifact().correctOption(),
            assisted?"HINT_ASSISTED":"INDEPENDENT","bounded-learning-v1",now);
    }
    public Optional<AttemptLearningEvidence> methods(UUID owner,UUID attempt) {
        return jdbc.query("select evidence::text from attempt_verified_methods where user_id=? and attempt_id=?",
            (r,n)->decode(r.getString(1),AttemptLearningEvidence.class),owner,attempt).stream().findFirst();
    }
    public void saveMethods(UUID owner,UUID attempt,AttemptLearningEvidence evidence,Instant now) {
        jdbc.update("insert into attempt_verified_methods(attempt_id,user_id,evidence,created_at) values(?,?,?::jsonb,?)",attempt,owner,encode(evidence),Timestamp.from(now));
    }
    public void attemptEvidence(UUID owner,UUID attempt,AttemptAnalysisResult evidence,Instant now) {
        if(!"attempt-analysis-v2".equals(evidence.schemaVersion()) || evidence.firstWrongStepIndex()==null) return;
        var wrong=evidence.steps().get(evidence.firstWrongStepIndex()-1);
        if(wrong.diagnosis()==null || wrong.mistakeCode()==MistakeCode.INVALID_TRANSFORMATION || wrong.mistakeCode()==MistakeCode.ALG_TRANSFORMATION_INVALID) return;
        observe(owner,attempt,null,wrong.mistakeCode(),false,"UNKNOWN",evidence.verifierVersion(),now);
    }
    private void observe(UUID owner,UUID attempt,UUID drill,MistakeCode code,boolean correct,String assistance,String verifier,Instant now) {
        String skill="attempt-skill-v1:"+code.name();
        String evidenceKey=drill==null?"attempt:"+attempt:"twin:"+drill;
        int inserted=jdbc.update("""
            insert into learner_mistake_events(id,user_id,attempt_id,drill_id,evidence_source,evidence_key,skill_id,mistake_code,correct,assistance,verifier_version,assessed_at)
            values(?,?,?,?,?,?,?,?,?,?,?,?) on conflict(user_id,evidence_key) do nothing
            """,UUID.randomUUID(),owner,attempt,drill,drill==null?"ATTEMPT_DISPROOF":"TWIN_COMPLETION",evidenceKey,skill,code.name(),correct,assistance,verifier,Timestamp.from(now));
        if(inserted==0) return;
        var prior=jdbc.query("select * from learner_skill_mastery where user_id=? and skill_id=? for update",(r,n)->
            new LearnerMasteryPolicy.State(r.getDouble("score"),r.getDouble("half_life_days"),r.getInt("evidence_count"),r.getDouble("confidence"),r.getTimestamp("last_assessed_at").toInstant()),owner,skill).stream().findFirst().orElse(null);
        var next=LearnerMasteryPolicy.observe(prior,correct,!"INDEPENDENT".equals(assistance),now);
        jdbc.update("""
            insert into learner_skill_mastery(user_id,skill_id,mastery_version,score,half_life_days,evidence_count,confidence,last_assessed_at)
            values(?,?,'bkt-lite-v1',?,?,?,?,?) on conflict(user_id,skill_id) do update set score=excluded.score,
            half_life_days=excluded.half_life_days,evidence_count=excluded.evidence_count,confidence=excluded.confidence,last_assessed_at=excluded.last_assessed_at
            """,owner,skill,next.score(),next.halfLife(),next.count(),next.confidence(),Timestamp.from(now));
    }
    public Map<String,Object> passport(UUID owner) {
        return Map.of("schemaVersion","mistake-passport-v1","trainingEligible",false,
            "events",rows("select id,attempt_id,drill_id,evidence_source,skill_id,mistake_code,correct,assistance,verifier_version,assessed_at from learner_mistake_events where user_id=? order by assessed_at,id",owner),
            "mastery",rows("select skill_id,mastery_version,score,half_life_days,evidence_count,confidence,last_assessed_at from learner_skill_mastery where user_id=? order by skill_id",owner));
    }
    public Map<String,Object> export(UUID owner) {
        return Map.of("passport",passport(owner),"drills",rows("select id,attempt_id,artifact::text,correct_option,selected_option,assistance,created_at,completed_at,training_eligible from counterfactual_twin_drills where user_id=? order by created_at,id",owner),
            "methods",rows("select attempt_id,evidence::text,created_at,training_eligible from attempt_verified_methods where user_id=? order by created_at,attempt_id",owner));
    }
    public void delete(UUID owner) {
        jdbc.update("delete from learner_skill_mastery where user_id=?",owner);
        jdbc.update("delete from learner_mistake_events where user_id=?",owner);
        jdbc.update("delete from counterfactual_twin_drills where user_id=?",owner);
        jdbc.update("delete from attempt_verified_methods where user_id=?",owner);
    }
    private List<Map<String,Object>> rows(String sql,UUID owner) {
        return jdbc.queryForList(sql,owner).stream().map(row->{
            var result=new LinkedHashMap<String,Object>();
            row.forEach((key,value)->result.put(key,value instanceof Timestamp time?time.toInstant():
                value instanceof String text && Set.of("artifact","evidence").contains(key)?decode(text,JsonNode.class):value));
            return (Map<String,Object>)result;
        }).toList();
    }
    private String encode(Object value) { try { return mapper.writeValueAsString(value); } catch(java.io.IOException ex) { throw new IllegalArgumentException("Invalid artifact"); } }
    private <T> T decode(String value,Class<T> type) { try { return mapper.readValue(value,type); } catch(java.io.IOException ex) { throw new IllegalArgumentException("Invalid artifact"); } }
}
