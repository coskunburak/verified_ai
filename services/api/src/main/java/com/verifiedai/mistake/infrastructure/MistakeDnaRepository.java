package com.verifiedai.mistake.infrastructure;

import com.verifiedai.mistake.*;
import com.verifiedai.sharedkernel.error.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MistakeDnaRepository {
    private final JdbcTemplate jdbc;
    public MistakeDnaRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public boolean available() { return Boolean.TRUE.equals(jdbc.queryForObject("select to_regclass('learner_mistake_dna_profiles') is not null",Boolean.class)); }
    public void lock(UUID owner) {
        if(jdbc.queryForList("select id from users where id=? and status='ACTIVE' for update",UUID.class,owner).isEmpty())
            throw new ApiProblemException(HttpStatus.FORBIDDEN,ApiErrorCode.ACCOUNT_NOT_ACTIVE,"Account unavailable",false,"REAUTHENTICATE");
    }
    public record Operation(UUID id, UUID attemptId, UUID classificationId, UUID replacementAttemptId,
        Integer stepIndex, String disputeType, String requestHash, Instant createdAt) { }
    public Optional<Operation> replay(UUID owner,String key) {
        return jdbc.query("select * from learner_mistake_dispute_events where user_id=? and create_key=?",(r,n)->
            new Operation(r.getObject("id",UUID.class),r.getObject("attempt_id",UUID.class),r.getObject("classification_id",UUID.class),
                r.getObject("replacement_attempt_id",UUID.class),(Integer)r.getObject("step_index"),r.getString("dispute_type"),
                r.getString("request_hash"),r.getTimestamp("created_at").toInstant()),owner,key).stream().findFirst();
    }
    public boolean hasOperation(UUID owner,UUID attempt,String type) {
        return jdbc.queryForObject("select count(*) from learner_mistake_dispute_events where user_id=? and attempt_id=? and dispute_type=?",Integer.class,owner,attempt,type)>0;
    }
    public Set<UUID> excluded(UUID owner) {
        return Set.copyOf(jdbc.queryForList("select distinct attempt_id from learner_mistake_dispute_events where user_id=?",UUID.class,owner));
    }
    public void insert(UUID owner,String key,Operation o) {
        jdbc.update("""
            insert into learner_mistake_dispute_events(id,user_id,attempt_id,classification_id,replacement_attempt_id,step_index,
                dispute_type,create_key,request_hash,created_at) values(?,?,?,?,?,?,?,?,?,?)
            """,o.id(),owner,o.attemptId(),o.classificationId(),o.replacementAttemptId(),o.stepIndex(),o.disputeType(),key,o.requestHash(),Timestamp.from(o.createdAt()));
    }
    public List<LearnerMistakeDnaProfile> profiles(UUID owner) {
        return jdbc.query("select * from learner_mistake_dna_profiles where user_id=? order by smoothed_propensity desc,mistake_code,skill_id",(r,n)->
            new LearnerMistakeDnaProfile(r.getString("mistake_code"),r.getString("skill_id"),r.getString("taxonomy_version"),
                r.getString("category_version"),r.getString("policy_version"),r.getBigDecimal("decayed_error_count"),r.getBigDecimal("decayed_exposure_count"),
                r.getBigDecimal("smoothed_propensity"),r.getInt("recent_error_count"),MistakeRecurrenceState.valueOf(r.getString("recurrence_state")),
                r.getTimestamp("last_occurred_at").toInstant(),r.getTimestamp("as_of").toInstant()),owner);
    }
    public void replace(UUID owner,List<LearnerMistakeDnaProfile> profiles) {
        jdbc.update("delete from learner_mistake_dna_profiles where user_id=?",owner);
        for(var p:profiles) jdbc.update("""
            insert into learner_mistake_dna_profiles(user_id,mistake_code,skill_id,taxonomy_version,category_version,policy_version,
                decayed_error_count,decayed_exposure_count,smoothed_propensity,recent_error_count,recurrence_state,last_occurred_at,as_of)
            values(?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,owner,p.mistakeCode(),p.skillId(),p.taxonomyVersion(),p.categoryVersion(),p.policyVersion(),p.decayedErrorCount(),
            p.decayedExposureCount(),p.smoothedPropensity(),p.recentErrorCount(),p.recurrenceState().name(),Timestamp.from(p.lastOccurredAt()),Timestamp.from(p.asOf()));
    }
    public boolean retentionAvailable() {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "select to_regclass('learner_mistake_retention_transitions') is not null", Boolean.class));
    }

    public boolean record(UUID owner, String code, String skill, MistakeRetentionResolutionListener.Transition t, Instant now) {
        return jdbc.update("""
            insert into learner_mistake_retention_transitions(id,user_id,mistake_code,skill_id,from_state,to_state,
                first_drill,pending_drill,check_drill,classification_id,causal_attempt,pending_at,event_at,processed_at)
            values(?,?,?,?,?,?,?,?,?,?,?,?,?,?) on conflict do nothing
            """, UUID.randomUUID(), owner, code, skill, t.from().name(), t.to().name(), t.firstDrill(), t.pendingDrill(),
            t.checkDrill(), t.classificationId(), t.causalAttempt(), Timestamp.from(t.pendingAt()), Timestamp.from(t.eventAt()),
            Timestamp.from(now)) == 1;
    }

    public Map<String,Object> export(UUID owner) {
        return Map.of("schemaVersion","mistake-dna-export-v1","trainingEligible",false,"profiles",profiles(owner),
            "retentionTransitions",retentionAvailable() ? jdbc.queryForList(
                "select * from learner_mistake_retention_transitions where user_id=? order by event_at,id", owner) : List.of(),
            "operations",jdbc.queryForList("select id,attempt_id,classification_id,replacement_attempt_id,step_index,dispute_type,policy_version,created_at,training_eligible from learner_mistake_dispute_events where user_id=? order by created_at,id",owner));
    }
    public void deleteProfiles(UUID owner) {
        if (retentionAvailable()) jdbc.update("delete from learner_mistake_retention_transitions where user_id=?", owner);
        jdbc.update("delete from learner_mistake_dna_profiles where user_id=?",owner);
    }
}
