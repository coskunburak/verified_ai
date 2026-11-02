package com.verifiedai.tutoring.infrastructure.persistence;

import com.verifiedai.tutoring.domain.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** All calls are made under the application account -> problem lock. No cross-module table reads. */
@Repository
public class TutorStore {
    private final JdbcTemplate jdbc;
    public TutorStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Optional<TutorSession> get(UUID owner, UUID problem, UUID id) {
        return jdbc.query("select * from tutor_sessions where user_id=? and problem_session_id=? and id=?",
            this::session,owner,problem,id).stream().findFirst();
    }
    public Optional<TutorSession> creation(UUID owner, UUID problem, String key) {
        return jdbc.query("select * from tutor_sessions where user_id=? and problem_session_id=? and create_key=?",
            this::session,owner,problem,key).stream().findFirst();
    }
    public List<TutorSession> list(UUID owner, UUID problem, int offset, int limit) {
        return jdbc.query("select * from tutor_sessions where user_id=? and problem_session_id=? order by created_at,id limit ? offset ?",
            this::session,owner,problem,limit,offset);
    }
    public long count(UUID owner) {
        return jdbc.queryForObject("select count(*) from tutor_sessions where user_id=?",Long.class,owner);
    }
    public void insert(UUID owner, String key, TutorSession s) {
        jdbc.update("""
            insert into tutor_sessions(id,user_id,problem_session_id,canonical_id,canonical_revision,parse_id,parse_revision,
                primary_skill_id,context_schema_version,profile_version,explanation_depth,language,mastery,safety_policy_version,
                schema_version,status,create_key,created_at,updated_at)
            values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,s.id(),owner,s.problemSessionId(),s.canonicalId(),s.canonicalRevision(),s.parseId(),s.parseRevision(),
            s.primarySkillId(),s.contextSchemaVersion(),s.profileVersion(),s.explanationDepth(),s.language(),s.mastery(),
            s.safetyPolicyVersion(),s.schemaVersion(),s.status().name(),key,Timestamp.from(s.createdAt()),Timestamp.from(s.updatedAt()));
    }
    public Optional<TutorMessage> replay(UUID owner, UUID session, String key) {
        return jdbc.query("select * from tutor_messages where user_id=? and tutor_session_id=? and idempotency_key=?",
            this::message,owner,session,key).stream().findFirst();
    }
    public List<TutorMessage> messages(UUID owner, UUID session, long after, int limit) {
        return jdbc.query("select * from tutor_messages where user_id=? and tutor_session_id=? and sequence_number>? order by sequence_number limit ?",
            this::message,owner,session,after,limit);
    }
    public TutorMessage append(UUID owner, TutorSession session, String key, String content, Instant now) {
        var message=new TutorMessage("tutor-message-v1",UUID.randomUUID(),session.id(),TutorPolicy.Role.LEARNER,
            session.messageCount()+1,content,now);
        jdbc.update("""
            insert into tutor_messages(id,user_id,tutor_session_id,role,schema_version,sequence_number,content,idempotency_key,created_at)
            values (?,?,?,'LEARNER','tutor-message-v1',?,?,?,?)
            """,message.id(),owner,session.id(),message.sequenceNumber(),content,key,Timestamp.from(now));
        jdbc.update("update tutor_sessions set message_count=message_count+1,version=version+1,updated_at=? where id=? and user_id=?",
            Timestamp.from(now),session.id(),owner);
        return message;
    }
    public Optional<TutorTurn> turnForMessage(UUID owner, UUID session, UUID message) {
        return jdbc.query("select * from tutor_turns where user_id=? and tutor_session_id=? and learner_message_id=?",
            this::turn, owner, session, message).stream().findFirst();
    }
    public List<TutorTurn> turns(UUID owner, UUID session, long after, int limit) {
        return jdbc.query("select * from tutor_turns where user_id=? and tutor_session_id=? and learner_sequence_number>? order by learner_sequence_number limit ?",
            this::turn, owner, session, after, limit);
    }
    public SocraticPolicy.Progress progress(UUID owner, UUID session) {
        return jdbc.query("select * from tutor_turns where user_id=? and tutor_session_id=? order by learner_sequence_number desc limit 1",
            this::turn, owner, session).stream().findFirst().map(TutorTurn::progress).orElseGet(SocraticPolicy.Progress::initial);
    }
    public long replyCount(UUID owner, UUID session) {
        return jdbc.queryForObject("select count(*) from tutor_turns where user_id=? and tutor_session_id=?", Long.class, owner, session);
    }
    public void insertTurn(UUID owner, TutorTurn t) {
        jdbc.update("""
            insert into tutor_turns(id,user_id,tutor_session_id,learner_message_id,learner_sequence_number,reply_id,
                role,schema_version,policy_version,previous_phase,phase,unproductive_count,turn_count,intent,action,
                outcome,content,language,correctness,reference_status,uncertain,provenance,created_at,training_eligible)
            values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,false)
            """, t.id(),owner,t.tutorSessionId(),t.learnerMessageId(),t.learnerSequenceNumber(),t.replyId(),t.role(),
            t.schemaVersion(),t.policyVersion(),t.previousPhase().name(),t.progress().phase().name(),
            t.progress().unproductiveCount(),t.progress().turnCount(),t.intent().name(),t.action().name(),t.outcome().name(),
            t.content(),t.language(),t.correctness(),t.referenceStatus(),t.uncertain(),t.provenance(),Timestamp.from(t.createdAt()));
    }
    private TutorTurn turn(ResultSet r, int n) throws SQLException {
        return new TutorTurn(r.getString("schema_version"),r.getObject("id",UUID.class),r.getObject("tutor_session_id",UUID.class),
            r.getObject("learner_message_id",UUID.class),r.getLong("learner_sequence_number"),r.getObject("reply_id",UUID.class),
            r.getString("role"),r.getString("content"),r.getString("language"),r.getString("policy_version"),
            SocraticPolicy.Phase.valueOf(r.getString("previous_phase")),
            new SocraticPolicy.Progress(SocraticPolicy.Phase.valueOf(r.getString("phase")),r.getInt("unproductive_count"),r.getInt("turn_count")),
            SocraticPolicy.Intent.valueOf(r.getString("intent")),SocraticPolicy.Action.valueOf(r.getString("action")),
            SocraticPolicy.Outcome.valueOf(r.getString("outcome")),r.getString("correctness"),r.getString("reference_status"),
            r.getBoolean("uncertain"),r.getString("provenance"),r.getTimestamp("created_at").toInstant(),false);
    }
    public void close(UUID owner, UUID id, Instant now) {
        jdbc.update("update tutor_sessions set status='CLOSED',version=version+1,updated_at=? where user_id=? and id=? and status='ACTIVE'",
            Timestamp.from(now),owner,id);
    }
    public long exportBytes(UUID owner) {
        return jdbc.queryForObject("select coalesce(sum(bytes),0) from (select octet_length(content) as bytes from tutor_messages where user_id=? union all select octet_length(content) from tutor_turns where user_id=?) contents",Long.class,owner,owner);
    }
    public List<TutorSession> exportSessions(UUID owner) {
        return jdbc.query("select * from tutor_sessions where user_id=? order by created_at,id",this::session,owner);
    }
    public void delete(UUID owner) { jdbc.update("delete from tutor_sessions where user_id=?",owner); }
    private TutorSession session(ResultSet r, int n) throws SQLException {
        return new TutorSession(r.getString("schema_version"),r.getObject("id",UUID.class),r.getObject("problem_session_id",UUID.class),
            r.getObject("canonical_id",UUID.class),r.getInt("canonical_revision"),r.getObject("parse_id",UUID.class),r.getInt("parse_revision"),
            r.getString("primary_skill_id"),r.getString("context_schema_version"),(Long)r.getObject("profile_version"),
            r.getString("explanation_depth"),r.getString("language"),r.getString("mastery"),r.getString("safety_policy_version"),
            TutorPolicy.Status.valueOf(r.getString("status")),false,r.getLong("message_count"),r.getLong("version"),
            r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant(),false);
    }
    private TutorMessage message(ResultSet r, int n) throws SQLException {
        return new TutorMessage(r.getString("schema_version"),r.getObject("id",UUID.class),r.getObject("tutor_session_id",UUID.class),
            TutorPolicy.Role.valueOf(r.getString("role")),r.getLong("sequence_number"),r.getString("content"),r.getTimestamp("created_at").toInstant());
    }
}
