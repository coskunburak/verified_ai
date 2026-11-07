package com.verifiedai.attempt.infrastructure.persistence;

import com.fasterxml.jackson.databind.*;
import com.verifiedai.attempt.domain.*;
import com.verifiedai.mistake.MistakeTaxonomy;
import com.verifiedai.sharedkernel.contracts.VersionedJsonContract;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AttemptMistakeRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    public AttemptMistakeRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public boolean available() { return Boolean.TRUE.equals(jdbc.queryForObject("select to_regclass('attempt_mistake_classifications') is not null", Boolean.class)); }
    public Optional<AttemptMistakeClassification> get(UUID owner, UUID attempt) {
        return jdbc.query("""
            select c.artifact::text,e.artifact::text from attempt_mistake_classifications c
            join attempt_evaluations e on e.id=c.evaluation_id and e.user_id=c.user_id and e.attempt_id=c.attempt_id
            where c.user_id=? and c.attempt_id=? order by c.created_at desc,c.id desc limit 1
            """, (r,n) -> decode(r.getString(1),r.getString(2)), owner, attempt).stream().findFirst();
    }
    public void checkKey(UUID owner, UUID problem, UUID attempt, String key, UUID evaluation) {
        Integer conflicts = jdbc.queryForObject("""
            select count(*) from attempt_mistake_classifications where user_id=? and
            ((problem_session_id=? and create_key=? and (attempt_id<>? or evaluation_id<>?))
             or (attempt_id=? and policy_version=? and (create_key<>? or evaluation_id<>?)))
            """, Integer.class, owner, problem, key, attempt, evaluation, attempt, MistakeTaxonomy.POLICY, key, evaluation);
        if(conflicts != null && conflicts > 0) throw AttemptAnalysisPolicy.error(ApiErrorCode.IDEMPOTENCY_KEY_REUSED, HttpStatus.CONFLICT);
    }
    public void insert(String key, AttemptMistakeClassification value, AttemptEvaluation evaluation) {
        value.validateFor(evaluation);
        String json;
        try { json = mapper.writeValueAsString(value); } catch(java.io.IOException ex) { throw new IllegalArgumentException("Invalid classification"); }
        VersionedJsonContract.validate("attempt-mistake-classification-v1", json);
        jdbc.update("""
            insert into attempt_mistake_classifications(id,user_id,problem_session_id,attempt_id,evaluation_id,policy_version,create_key,created_at,artifact)
            values(?,?,?,?,?,?,?,?::timestamptz,?::jsonb)
            """, value.id(), value.ownerId(), value.problemSessionId(), value.attemptId(), value.evaluationId(), value.policyVersion(), key, value.createdAt(), json);
    }
    public List<AttemptMistakeClassification> export(UUID owner) {
        if(!available()) return List.of();
        return jdbc.query("""
            select c.artifact::text,e.artifact::text from attempt_mistake_classifications c
            join attempt_evaluations e on e.id=c.evaluation_id and e.user_id=c.user_id and e.attempt_id=c.attempt_id
            where c.user_id=? order by c.created_at,c.id
            """, (r,n)->decode(r.getString(1),r.getString(2)), owner);
    }
    private AttemptMistakeClassification decode(String json,String evaluationJson) {
        VersionedJsonContract.validate("attempt-mistake-classification-v1", json);
        VersionedJsonContract.validate("attempt-evaluation-v1", evaluationJson);
        try {
            var value=mapper.readValue(json, AttemptMistakeClassification.class);
            value.validateFor(mapper.readValue(evaluationJson,AttemptEvaluation.class));
            return value;
        }
        catch(java.io.IOException ex) { throw new IllegalArgumentException("Invalid stored classification"); }
    }
}
