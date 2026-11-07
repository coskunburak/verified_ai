package com.verifiedai.attempt.infrastructure.persistence;

import com.fasterxml.jackson.databind.*;
import com.verifiedai.attempt.domain.*;
import com.verifiedai.sharedkernel.contracts.VersionedJsonContract;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AttemptEvaluationRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper=new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    public AttemptEvaluationRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public record Operation(UUID id, UUID attemptId, UUID executionId, Instant leaseUntil, AttemptEvaluation artifact) {}
    public boolean available() { return Boolean.TRUE.equals(jdbc.queryForObject("select to_regclass('attempt_evaluations') is not null",Boolean.class)); }
    public Optional<Operation> get(UUID owner,UUID attempt) {
        return jdbc.query("select id,attempt_id,execution_id,lease_until,artifact::text from attempt_evaluations where user_id=? and attempt_id=?",
            (r,n)->new Operation(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getObject(3,UUID.class),r.getTimestamp(4).toInstant(),r.getString(5)==null?null:decode(r.getString(5))),owner,attempt).stream().findFirst();
    }
    public void checkKey(UUID owner,UUID problem,UUID attempt,String key,boolean confirmed) {
        if(jdbc.queryForObject("select count(*) from attempt_evaluations where user_id=? and attempt_id=? and real_domain_confirmed<>?",Integer.class,owner,attempt,confirmed)>0)
            throw AttemptAnalysisPolicy.error(com.verifiedai.sharedkernel.error.ApiErrorCode.IDEMPOTENCY_KEY_REUSED,org.springframework.http.HttpStatus.CONFLICT);
        var keys=jdbc.query("select create_key from attempt_evaluations where user_id=? and attempt_id=?",(r,n)->r.getString(1),owner,attempt);
        if(!keys.isEmpty() && !keys.getFirst().equals(key)) throw AttemptAnalysisPolicy.error(
            com.verifiedai.sharedkernel.error.ApiErrorCode.IDEMPOTENCY_KEY_REUSED,org.springframework.http.HttpStatus.CONFLICT);
        var ids=jdbc.query("select attempt_id from attempt_evaluations where user_id=? and problem_session_id=? and create_key=?",
            (r,n)->r.getObject(1,UUID.class),owner,problem,key);
        if(!ids.isEmpty() && !ids.getFirst().equals(attempt)) throw AttemptAnalysisPolicy.error(
            com.verifiedai.sharedkernel.error.ApiErrorCode.IDEMPOTENCY_KEY_REUSED,org.springframework.http.HttpStatus.CONFLICT);
    }
    public Operation create(UUID owner,UUID problem,String key,AttemptStructuredWork work,boolean confirmed,Instant now) {
        UUID id=UUID.randomUUID(),execution=UUID.randomUUID(); Instant lease=now.plusSeconds(20);
        jdbc.update("insert into attempt_evaluations(id,attempt_id,user_id,problem_session_id,work_revision,source,create_key,real_domain_confirmed,policy_version,execution_id,lease_until,created_at) values(?,?,?,?,?,?::jsonb,?,?,'structured-evaluation-v1',?,?,?)",
            id,work.attemptId(),owner,problem,work.revision(),encode(work.source()),key,confirmed,execution,java.sql.Timestamp.from(lease),java.sql.Timestamp.from(now));
        return new Operation(id,work.attemptId(),execution,lease,null);
    }
    public Operation reclaim(UUID owner,Operation old,Instant now) {
        UUID execution=UUID.randomUUID(); Instant lease=now.plusSeconds(20);
        int changed=jdbc.update("update attempt_evaluations set execution_id=?,lease_until=? where user_id=? and id=? and execution_id=? and lease_until<=? and artifact is null",
            execution,java.sql.Timestamp.from(lease),owner,old.id(),old.executionId(),java.sql.Timestamp.from(now));
        if(changed!=1) throw AttemptAnalysisPolicy.error(com.verifiedai.sharedkernel.error.ApiErrorCode.OPTIMISTIC_CONFLICT,org.springframework.http.HttpStatus.CONFLICT);
        return new Operation(old.id(),old.attemptId(),execution,lease,null);
    }
    public void release(UUID owner,Operation op,Instant now) {
        jdbc.update("update attempt_evaluations set lease_until=? where user_id=? and id=? and execution_id=? and artifact is null",
            java.sql.Timestamp.from(now),owner,op.id(),op.executionId());
    }
    public void publish(UUID owner,Operation op,AttemptEvaluation result) {
        String json=encode(result); VersionedJsonContract.validate("attempt-evaluation-v1",json);
        jdbc.update("update attempt_evaluations set artifact=?::jsonb where user_id=? and id=? and execution_id=? and artifact is null",
            json,owner,op.id(),op.executionId());
    }
    public List<AttemptEvaluation> export(UUID owner) {
        if(!available()) return List.of();
        return jdbc.query("select artifact::text from attempt_evaluations where user_id=? and artifact is not null order by created_at,id",(r,n)->decode(r.getString(1)),owner);
    }
    private String encode(Object value) {
        try { return mapper.writeValueAsString(value); } catch(java.io.IOException ex) { throw new IllegalArgumentException("Invalid evaluation"); }
    }
    private AttemptEvaluation decode(String json) {
        VersionedJsonContract.validate("attempt-evaluation-v1",json);
        try { return mapper.readValue(json,AttemptEvaluation.class); } catch(java.io.IOException ex) { throw new IllegalArgumentException("Invalid stored evaluation"); }
    }
}
