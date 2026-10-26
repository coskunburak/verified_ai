package com.verifiedai.solving.infrastructure.persistence;

import com.verifiedai.problem.application.solving.SolveSource;
import com.verifiedai.solving.domain.model.SolveJob;
import com.verifiedai.solving.domain.model.SolveState;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SolveStore {
    private final JdbcTemplate jdbc;
    public SolveStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Optional<SolveJob> get(UUID id) { return one("select * from solve_jobs where id=?",id); }
    public Optional<SolveJob> key(UUID user,String key) { return one("select * from solve_jobs where user_id=? and idempotency_key=?",user,key); }
    public Optional<SolveJob> latest(UUID canonical) {
        return one("select * from solve_jobs where canonical_problem_id=? order by attempt_number desc limit 1",canonical);
    }
    public Optional<SolveJob> active(UUID session) {
        return one("select * from solve_jobs where problem_session_id=? and state in ('QUEUED','RUNNING')",session);
    }
    public Optional<SolveJob> latestForSession(UUID user, UUID session) {
        return one("select * from solve_jobs where user_id=? and problem_session_id=? order by created_at desc, attempt_number desc, id desc limit 1", user, session);
    }
    public SolveJob insert(SolveSource s,String key,UUID retry,int attempt,Instant now,Instant deadline) {
        UUID id=UUID.randomUUID(),operation=UUID.randomUUID();
        jdbc.update("""
            insert into solve_jobs(id,user_id,problem_session_id,canonical_problem_id,problem_parse_id,
              problem_parse_revision,idempotency_key,attempt_number,retry_of,operation_id,state,created_at,deadline)
            values (?,?,?,?,?,?,?,?,?,?,'QUEUED',?,?)
            """,id,s.userId(),s.problemSessionId(),s.canonicalProblemId(),s.problemParseId(),s.problemParseRevision(),
            key,attempt,retry,operation,Timestamp.from(now),Timestamp.from(deadline));
        return get(id).orElseThrow();
    }
    public List<UUID> due(int limit) {
        return jdbc.queryForList("select id from solve_jobs where state='QUEUED' order by created_at limit ?",UUID.class,limit);
    }
    public boolean claim(UUID id,Instant now) {
        return jdbc.update("update solve_jobs set state='RUNNING',started_at=? where id=? and state='QUEUED' and deadline>?",
            Timestamp.from(now),id,Timestamp.from(now))==1;
    }
    public boolean running(UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from solve_jobs where id=? and state='RUNNING')",Boolean.class,id));
    }
    public boolean finish(UUID id,SolveState state,String code,UUID ledger,Instant now) {
        return jdbc.update("""
            update solve_jobs set state=?,failure_code=?,ledger_record_id=?,completed_at=?
            where id=? and state='RUNNING' and deadline>?
            """,state.name(),code,ledger,Timestamp.from(now),id,Timestamp.from(now))==1;
    }
    public void candidate(UUID job,String artifact,String provenance,Instant now) {
        jdbc.update("insert into solution_candidates values (?,'solution-candidate-v1',cast(? as jsonb),cast(? as jsonb),?)",
            job,artifact,provenance,Timestamp.from(now));
    }
    public Optional<Map<String,Object>> candidate(UUID job) {
        var rows=jdbc.queryForList("select artifact_jsonb::text as artifact, provenance_jsonb::text as provenance from solution_candidates where job_id=?",job);
        return rows.stream().findFirst();
    }
    public void cancel(UUID id,Instant now) {
        jdbc.update("update solve_jobs set state='CANCELLED',completed_at=? where id=? and state in ('QUEUED','RUNNING')",Timestamp.from(now),id);
    }
    public void recover(Instant now) {
        jdbc.update("""
            update solve_jobs set state=case when state='RUNNING' then 'OUTCOME_UNKNOWN' else 'TERMINAL_FAILURE' end,
              failure_code=case when state='RUNNING' then 'EXECUTION_OUTCOME_UNKNOWN' else 'QUEUE_DEADLINE_EXCEEDED' end,
              completed_at=? where state in ('QUEUED','RUNNING') and deadline<=?
            """,Timestamp.from(now),Timestamp.from(now));
    }
    public List<Map<String,Object>> export(UUID user) {
        return jdbc.queryForList("""
            select j.id,j.problem_session_id,j.canonical_problem_id,j.problem_parse_id,j.problem_parse_revision,
              j.state,j.failure_code,j.operation_id,j.created_at,j.completed_at,c.artifact_jsonb::text as candidate
            from solve_jobs j left join solution_candidates c on c.job_id=j.id where j.user_id=? order by j.created_at
            """,user);
    }
    public void delete(UUID user) { jdbc.update("delete from solve_jobs where user_id=?",user); }
    private Optional<SolveJob> one(String sql,Object... args) { return jdbc.query(sql,this::row,args).stream().findFirst(); }
    private SolveJob row(ResultSet rs,int n) throws SQLException {
        return new SolveJob(rs.getObject("id",UUID.class),rs.getObject("user_id",UUID.class),rs.getObject("problem_session_id",UUID.class),
            rs.getObject("canonical_problem_id",UUID.class),rs.getObject("problem_parse_id",UUID.class),rs.getInt("problem_parse_revision"),
            rs.getString("idempotency_key"),rs.getInt("attempt_number"),rs.getObject("retry_of",UUID.class),rs.getObject("operation_id",UUID.class),
            SolveState.valueOf(rs.getString("state")),rs.getString("failure_code"),rs.getObject("ledger_record_id",UUID.class),
            rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("deadline").toInstant());
    }
}
