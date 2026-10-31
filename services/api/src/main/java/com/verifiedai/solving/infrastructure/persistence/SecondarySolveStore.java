package com.verifiedai.solving.infrastructure.persistence;

import com.verifiedai.solving.domain.model.SolveJob;
import com.verifiedai.solving.domain.model.secondary.*;
import com.verifiedai.solving.application.secondary.SecondaryEscalationPolicy.Decision;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SecondarySolveStore {
    private final JdbcTemplate jdbc;
    public SecondarySolveStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public record Corroboration(UUID id, UUID canonicalId, UUID parseId, int parseRevision,
        int canonicalRevision, SecondaryState state, String agreement) { }
    /** Owner scope is mandatory; the application holds the existing owner/session lock. */
    public List<Corroboration> corroboration(UUID user, UUID session, UUID parent) {
        return jdbc.query("""
            select j.id,j.canonical_problem_id,j.problem_parse_id,j.problem_parse_revision,j.canonical_revision,
                j.state,e.agreement_jsonb::text as agreement
            from secondary_solve_jobs j left join secondary_solution_evidence e on e.job_id=j.id
            where j.user_id=? and j.problem_session_id=? and j.primary_job_id=? order by j.id limit 17
            """, (r,n) -> new Corroboration(r.getObject("id", UUID.class), r.getObject("canonical_problem_id", UUID.class),
                r.getObject("problem_parse_id", UUID.class), r.getInt("problem_parse_revision"), r.getInt("canonical_revision"),
                SecondaryState.valueOf(r.getString("state")), r.getString("agreement")), user, session, parent);
    }
    public Optional<SecondaryJob> get(UUID id) { return one("select * from secondary_solve_jobs where id=?",id); }
    public Optional<SecondaryJob> forPrimary(UUID primary,String version) {
        return one("select * from secondary_solve_jobs where primary_job_id=? and policy_version=?",primary,version);
    }
    public boolean previouslyScheduled(UUID primary) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            select exists(select 1 from secondary_solve_jobs where primary_job_id=?
                and (attempt_number=1 or state in ('QUEUED','RUNNING')))
            """,Boolean.class,primary));
    }
    public List<UUID> undecided(String version,int limit) {
        return jdbc.queryForList("""
            select j.id from solve_jobs j join solution_candidates c on c.job_id=j.id
            where j.state in ('GENERATED_UNVERIFIED','REVIEW_REQUIRED') and not exists
              (select 1 from secondary_solve_jobs s where s.primary_job_id=j.id and s.policy_version=?)
            order by j.created_at,j.id limit ?
            """,UUID.class,version,limit);
    }
    public SecondaryJob insert(SolveJob parent,Decision d,SecondaryState state,
        String route,Instant now) {
        UUID id=UUID.randomUUID();
        jdbc.update("""
            insert into secondary_solve_jobs(id,primary_job_id,user_id,problem_session_id,canonical_problem_id,
                problem_parse_id,problem_parse_revision,canonical_revision,operation_id,policy_version,decision,
                decision_reason,state,route_jsonb,created_at,deadline,completed_at)
            values (?,?,?,?,?,?,?,?,?,?,?,?,?,cast(? as jsonb),?,?,?)
            on conflict(primary_job_id,policy_version) do nothing
            """,id,parent.id(),parent.userId(),parent.sessionId(),parent.canonicalId(),parent.parseId(),parent.parseRevision(),
            jdbc.queryForObject("select canonical_revision from canonical_problems where id=?",Integer.class,parent.canonicalId()),UUID.randomUUID(),d.policyVersion(),d.outcome().name(),d.reason(),
            state.name(),route,Timestamp.from(now),Timestamp.from(now.plusSeconds(60)),state==SecondaryState.QUEUED?null:Timestamp.from(now));
        return forPrimary(parent.id(),d.policyVersion()).orElseThrow();
    }
    public List<UUID> due(int limit) {
        return jdbc.queryForList("select id from secondary_solve_jobs where state='QUEUED' order by created_at,id limit ?",UUID.class,limit);
    }
    /** Caller holds account/session transaction. Global lock makes concurrency bounds cross-process. */
    public boolean claim(UUID id,Instant now,int globalLimit) {
        jdbc.execute("select pg_advisory_xact_lock(550055)");
        return jdbc.update("""
            update secondary_solve_jobs j set state='RUNNING',attempt_number=1,started_at=?
            where j.id=? and j.state='QUEUED' and j.attempt_number=0 and j.deadline>?
              and (select count(*) from secondary_solve_jobs where state='RUNNING')<?
              and not exists(select 1 from secondary_solve_jobs r where r.user_id=j.user_id and r.state='RUNNING')
            """,Timestamp.from(now),id,Timestamp.from(now),globalLimit)==1;
    }
    public boolean running(UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from secondary_solve_jobs where id=? and state='RUNNING')",Boolean.class,id));
    }
    public boolean finish(UUID id,SecondaryState state,String code,UUID ledger,Instant now) {
        return jdbc.update("""
            update secondary_solve_jobs set state=?,failure_code=?,ledger_record_id=?,completed_at=?
            where id=? and state='RUNNING' and deadline>?
            """,state.name(),code,ledger,Timestamp.from(now),id,Timestamp.from(now))==1;
    }
    public void evidence(UUID id,String candidate,String provenance,String agreement,Instant now) {
        jdbc.update("""
            insert into secondary_solution_evidence(job_id,schema_version,artifact_jsonb,provenance_jsonb,agreement_jsonb,created_at)
            values (?,'solution-candidate-v1',cast(? as jsonb),cast(? as jsonb),cast(? as jsonb),?)
            """,id,candidate,provenance,agreement,Timestamp.from(now));
    }
    public Optional<String> agreement(UUID id) {
        return jdbc.queryForList("select agreement_jsonb::text from secondary_solution_evidence where job_id=?",String.class,id).stream().findFirst();
    }
    public void cancel(UUID id,Instant now) {
        jdbc.update("update secondary_solve_jobs set state='CANCELLED',completed_at=? where id=? and state in ('QUEUED','RUNNING')",Timestamp.from(now),id);
    }
    public Map<SecondaryState,Integer> recover(Instant now) {
        var recovered=jdbc.queryForList("""
            update secondary_solve_jobs set state=case when state='RUNNING' then 'OUTCOME_UNKNOWN' else 'TERMINAL_FAILURE' end,
                failure_code=case when state='RUNNING' then 'EXECUTION_OUTCOME_UNKNOWN' else 'QUEUE_DEADLINE_EXCEEDED' end,
                completed_at=? where state in ('QUEUED','RUNNING') and deadline<=? returning state
            """,String.class,Timestamp.from(now),Timestamp.from(now));
        Map<SecondaryState,Integer> counts=new EnumMap<>(SecondaryState.class);
        recovered.forEach(state->counts.merge(SecondaryState.valueOf(state),1,Integer::sum));
        return counts;
    }
    public List<Map<String,Object>> export(UUID user) {
        return jdbc.queryForList("""
            select j.id,j.primary_job_id,j.problem_session_id,j.canonical_problem_id,j.problem_parse_id,
                j.problem_parse_revision,j.policy_version,j.decision,j.decision_reason,j.state,j.failure_code,
                j.operation_id,j.ledger_record_id,j.created_at,j.completed_at,j.route_jsonb::text as route,
                e.artifact_jsonb::text as candidate,e.provenance_jsonb::text as provenance,e.agreement_jsonb::text as agreement
            from secondary_solve_jobs j left join secondary_solution_evidence e on e.job_id=j.id
            where j.user_id=? order by j.created_at,j.id
            """,user);
    }
    private Optional<SecondaryJob> one(String sql,Object... args) { return jdbc.query(sql,this::row,args).stream().findFirst(); }
    private SecondaryJob row(ResultSet r,int n) throws SQLException {
        return new SecondaryJob(r.getObject("id",UUID.class),r.getObject("primary_job_id",UUID.class),r.getObject("user_id",UUID.class),
            r.getObject("problem_session_id",UUID.class),r.getObject("canonical_problem_id",UUID.class),r.getObject("problem_parse_id",UUID.class),
            r.getInt("problem_parse_revision"),r.getInt("canonical_revision"),r.getObject("operation_id",UUID.class),r.getString("policy_version"),
            r.getString("decision"),r.getString("decision_reason"),SecondaryState.valueOf(r.getString("state")),r.getString("failure_code"),
            r.getInt("attempt_number"),r.getString("route_jsonb"),r.getObject("ledger_record_id",UUID.class),
            r.getTimestamp("created_at").toInstant(),r.getTimestamp("deadline").toInstant());
    }
}
