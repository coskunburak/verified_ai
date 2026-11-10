package com.verifiedai.mastery.infrastructure;

import com.verifiedai.mastery.*;
import com.verifiedai.sharedkernel.error.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MasteryRepository {
    private final JdbcTemplate jdbc;
    public MasteryRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public boolean available() { return Boolean.TRUE.equals(jdbc.queryForObject("select to_regclass('learner_skill_mastery_transitions') is not null",Boolean.class)); }
    public void lock(UUID owner) {
        if(jdbc.queryForList("select id from users where id=? and status='ACTIVE' for update",UUID.class,owner).isEmpty())
            throw new ApiProblemException(HttpStatus.FORBIDDEN,ApiErrorCode.ACCOUNT_NOT_ACTIVE,"Account unavailable",false,"REAUTHENTICATE");
    }
    public void clearProjection(UUID owner) { jdbc.update("delete from learner_skill_mastery where user_id=? and mastery_version='bkt-lite-v2'",owner); }
    public int persist(UUID owner,String skill,String fingerprint,EvidenceStratifiedMasteryPolicy.Projection p,Instant now) {
        int inserted=0;
        for(var step:p.steps()) {
            var e=step.evidence();
            inserted+=jdbc.update("""
                insert into learner_skill_mastery_transitions(id,user_id,canonical_skill_code,attempt_id,evidence_id,evaluation_id,
                    execution_id,skill_evidence_id,evidence_type,prior_score,posterior_score,prior_half_life_days,posterior_half_life_days,
                    policy_version,evidence_fingerprint,provenance,event_time,created_at)
                values(?,?,?,?,?,?,?,?,?,?,?,?,?,'bkt-lite-v2',?,?,?,?)
                on conflict(user_id,canonical_skill_code,policy_version,evidence_fingerprint,evidence_id) do nothing
                """,UUID.randomUUID(),owner,skill,e.attemptId(),e.id(),e.evaluationId(),e.executionId(),e.skillEvidenceId(),e.type().name(),
                step.prior().score(),step.next().score(),step.prior().halfLife(),step.next().halfLife(),fingerprint,e.provenance(),
                Timestamp.from(e.observedAt()),Timestamp.from(now));
        }
        var s=p.state();
        jdbc.update("""
            insert into learner_skill_mastery(user_id,skill_id,mastery_version,score,half_life_days,evidence_count,confidence,last_assessed_at,
                independent_evidence_count,assisted_evidence_count,independent_competence_score,independent_pass_count,capped_by_assistance,evidence_fingerprint,projected_at,last_confidence_evidence_at)
            values(?,?,'bkt-lite-v2',?,?,?,?,?,?,?,?,?,?,?,?,?)
            on conflict(user_id,skill_id) do update set mastery_version=excluded.mastery_version,score=excluded.score,
                half_life_days=excluded.half_life_days,evidence_count=excluded.evidence_count,confidence=excluded.confidence,
                last_assessed_at=excluded.last_assessed_at,independent_evidence_count=excluded.independent_evidence_count,
                assisted_evidence_count=excluded.assisted_evidence_count,independent_competence_score=excluded.independent_competence_score,
                independent_pass_count=excluded.independent_pass_count,capped_by_assistance=excluded.capped_by_assistance,
                evidence_fingerprint=excluded.evidence_fingerprint,projected_at=excluded.projected_at,last_confidence_evidence_at=excluded.last_confidence_evidence_at
            """,owner,skill,s.score(),s.halfLife(),s.count(),s.confidence(),Timestamp.from(s.observedAt()),s.independent(),s.assisted(),
            p.independentCompetence(),s.independentPasses(),s.cappedByAssistance(),fingerprint,Timestamp.from(now),s.confidenceObservedAt()==null?null:Timestamp.from(s.confidenceObservedAt()));
        return inserted;
    }
    public Map<String,Object> export(UUID owner) {
        return Map.of("schemaVersion","mastery-export-v1","trainingEligible",false,
            "projections",jdbc.queryForList("select * from learner_skill_mastery where user_id=? order by skill_id",owner),
            "transitions",jdbc.queryForList("select * from learner_skill_mastery_transitions where user_id=? order by created_at,id",owner),
            "contexts",jdbc.queryForList("select * from attempt_mastery_context where user_id=? order by captured_at,evidence_id",owner),
            "disclosures",jdbc.queryForList("select * from solution_learning_disclosures where user_id=? order by problem_session_id",owner));
    }
    public void delete(UUID owner) {
        jdbc.update("delete from learner_skill_mastery_transitions where user_id=?",owner);
        jdbc.update("delete from learner_skill_mastery where user_id=?",owner);
        jdbc.update("delete from attempt_mastery_context where user_id=?",owner);
        jdbc.update("delete from solution_learning_disclosures where user_id=?",owner);
    }
}
