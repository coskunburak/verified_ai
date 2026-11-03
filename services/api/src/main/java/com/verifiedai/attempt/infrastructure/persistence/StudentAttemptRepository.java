package com.verifiedai.attempt.infrastructure.persistence;

import com.fasterxml.jackson.databind.*;
import com.verifiedai.attempt.domain.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StudentAttemptRepository {
    private final JdbcTemplate jdbc;
    private final AttemptLearningRepository learning;
    private final ObjectMapper mapper=new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
    public StudentAttemptRepository(JdbcTemplate jdbc) { this(jdbc,new AttemptLearningRepository(jdbc)); }
    @org.springframework.beans.factory.annotation.Autowired
    public StudentAttemptRepository(JdbcTemplate jdbc,AttemptLearningRepository learning) { this.jdbc=jdbc; this.learning=learning; }
    /** History/privacy depend on actual storage, never on the submission rollout flag. */
    public boolean available() {
        int tables=jdbc.queryForObject("select (case when to_regclass('student_attempts') is not null then 1 else 0 end) + (case when to_regclass('student_attempt_steps') is not null then 1 else 0 end)",Integer.class);
        if(tables==1) throw new org.springframework.dao.DataAccessResourceFailureException("Incomplete attempt schema");
        return tables==2;
    }
    public Optional<StudentAttempt> replay(UUID owner,UUID problem,String key,String hash) {
        var rows=jdbc.query("select * from student_attempts where user_id=? and problem_session_id=? and create_key=?",
            (rs,n)-> {
                if(!hash.equals(rs.getString("request_hash"))) throw AttemptAnalysisPolicy.error(
                    com.verifiedai.sharedkernel.error.ApiErrorCode.IDEMPOTENCY_KEY_REUSED,org.springframework.http.HttpStatus.CONFLICT);
                return readRow(rs);
            },owner,problem,key);
        return rows.stream().findFirst().map(s->withSteps(owner,s));
    }
    public Optional<StudentAttempt> get(UUID owner,UUID problem,UUID id) {
        return jdbc.query("select * from student_attempts where user_id=? and problem_session_id=? and id=?",
            (rs,n)->readRow(rs),owner,problem,id).stream().findFirst().map(s->withSteps(owner,s));
    }
    public List<StudentAttempt> history(UUID owner,UUID problem) {
        return jdbc.query("select * from student_attempts where user_id=? and problem_session_id=? order by created_at,id",
            (rs,n)->readRow(rs),owner,problem).stream().map(s->withSteps(owner,s)).toList();
    }
    public int count(UUID owner) { return jdbc.queryForObject("select count(*) from student_attempts where user_id=?",Integer.class,owner); }
    public void insert(UUID owner,String key,String hash,StudentAttempt s,String request) {
        jdbc.update("""
            insert into student_attempts(id,user_id,problem_session_id,canonical_id,canonical_revision,parse_id,parse_revision,
                source_type,schema_version,normalization_version,status,create_key,request_hash,verifier_request_jsonb,created_at,updated_at,capture_review)
            values(?,?,?,?,?,?,?,'MANUAL','student-attempt-v1',?,'SUBMITTED',?,?,?::jsonb,?,?,?::jsonb)
            """,s.id(),owner,s.problemSessionId(),s.source().canonicalId(),s.source().canonicalRevision(),s.source().parseId(),
            s.source().parseRevision(),s.normalizationVersion(),key,hash,request,Timestamp.from(s.createdAt()),Timestamp.from(s.updatedAt()),encodeReview(s.captureReview()));
        for(var step:s.steps()) jdbc.update("""
            insert into student_attempt_steps(id,attempt_id,user_id,step_index,content,normalized_statement)
            values(?,?,?,?,?,?::jsonb)
            """,UUID.randomUUID(),s.id(),owner,step.index(),step.content(),step.normalizedStatement());
    }
    public String request(UUID owner,UUID id) {
        return jdbc.queryForObject("select verifier_request_jsonb::text from student_attempts where user_id=? and id=?",String.class,owner,id);
    }
    public void publish(UUID owner,UUID id,AttemptAnalysisResult result,Instant now) {
        int changed=jdbc.update("update student_attempts set status='ANALYZED',analysis_jsonb=?::jsonb,updated_at=? where user_id=? and id=? and status='SUBMITTED'",
            encode(result),Timestamp.from(now),owner,id);
        if(changed==1) learning.attemptEvidence(owner,id,result,now);
    }
    public void stale(UUID owner,UUID id,Instant now) {
        jdbc.update("update student_attempts set status='STALE_SOURCE',updated_at=? where user_id=? and id=? and status='SUBMITTED'",Timestamp.from(now),owner,id);
    }
    public List<StudentAttempt> export(UUID owner) {
        if(count(owner)>30) throw AttemptAnalysisPolicy.error(com.verifiedai.sharedkernel.error.ApiErrorCode.ATTEMPT_CAPACITY_EXCEEDED,
            org.springframework.http.HttpStatus.CONFLICT);
        return jdbc.query("select * from student_attempts where user_id=? order by created_at,id",(rs,n)->readRow(rs),owner)
            .stream().map(s->withSteps(owner,s)).toList();
    }
    public Map<String,Object> learningExport(UUID owner) { return learning.export(owner); }
    public void delete(UUID owner) { learning.delete(owner); jdbc.update("delete from student_attempts where user_id=?",owner); }
    private StudentAttempt withSteps(UUID owner,StudentAttempt s) {
        var steps=jdbc.query("select step_index,content,normalized_statement::text from student_attempt_steps where user_id=? and attempt_id=? order by step_index",
            (rs,n)->new StudentAttemptStep(rs.getInt(1),rs.getString(2),rs.getString(3)),owner,s.id());
        return new StudentAttempt(s.schemaVersion(),s.id(),s.problemSessionId(),s.source(),s.sourceType(),s.normalizationVersion(),s.status(),steps,s.analysis(),s.createdAt(),s.updatedAt(),false,s.captureReview());
    }
    private StudentAttempt readRow(ResultSet rs) throws SQLException {
        var source=new AttemptSourceIdentity(rs.getObject("canonical_id",UUID.class),rs.getInt("canonical_revision"),rs.getObject("parse_id",UUID.class),rs.getInt("parse_revision"));
        return new StudentAttempt(rs.getString("schema_version"),rs.getObject("id",UUID.class),rs.getObject("problem_session_id",UUID.class),source,
            AttemptSourceType.MANUAL,rs.getString("normalization_version"),AttemptStatus.valueOf(rs.getString("status")),List.of(),decode(rs.getString("analysis_jsonb")),
            rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("updated_at").toInstant(),rs.getBoolean("training_eligible"),decodeReview(rs.getString("capture_review")));
    }
    private String encodeReview(AttemptCaptureReview review) { try { return review==null?null:mapper.writeValueAsString(review); } catch(java.io.IOException ex) { throw new IllegalArgumentException("Invalid review"); } }
    private AttemptCaptureReview decodeReview(String json) { try { return json==null?null:mapper.readValue(json,AttemptCaptureReview.class); } catch(java.io.IOException ex) { throw new IllegalArgumentException("Invalid review"); } }
    private String encode(AttemptAnalysisResult result) {
        try { return mapper.writeValueAsString(result); } catch(java.io.IOException e) { throw new IllegalArgumentException("Invalid attempt result"); }
    }
    private AttemptAnalysisResult decode(String json) {
        if(json==null) return null;
        try { return mapper.readValue(json,AttemptAnalysisResult.class); } catch(java.io.IOException e) { throw new IllegalArgumentException("Invalid stored result"); }
    }
}
