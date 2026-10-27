package com.verifiedai.problem.application.solving;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Caller must hold a short transaction. Lock order: account, session, solve job. */
@Service
public class SolveSourceAccess {
    private final JdbcTemplate jdbc;
    public SolveSourceAccess(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = ApiProblemException.class)
    public void lockOwner(UUID userId, UUID sessionId) {
        var active = jdbc.queryForList("select id from users where id=? and status='ACTIVE' for update", UUID.class, userId);
        if (active.isEmpty()) throw error(ApiErrorCode.ACCOUNT_NOT_ACTIVE);
        var owned = jdbc.queryForList("select id from problem_sessions where id=? and user_id=? for update", UUID.class, sessionId, userId);
        if (owned.isEmpty()) throw error(ApiErrorCode.RESOURCE_FORBIDDEN);
    }

    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = ApiProblemException.class)
    public SolveSource current(UUID userId, UUID sessionId) {
        lockOwner(userId, sessionId);
        var sources = jdbc.query("""
            select c.* from problem_sessions s
            join problem_parses p on p.id=s.current_parse_id and p.user_id=s.user_id and p.problem_session_id=s.id
            join canonical_problems c on c.problem_parse_id=p.id and c.problem_parse_revision=p.revision
              and c.user_id=s.user_id and c.problem_session_id=s.id
            where s.id=? and s.user_id=? and p.support_status='SUPPORTED' and p.review_required=false
              and c.schema_version='canonical-problem-v1'
              and coalesce((c.display_jsonb->>'reviewRequired')::boolean, true)=false
            """, (rs, n) -> new SolveSource(rs.getObject("id", UUID.class), userId, sessionId,
                rs.getObject("problem_parse_id", UUID.class), rs.getInt("problem_parse_revision"),
                rs.getInt("canonical_revision"), rs.getString("problem_type"), rs.getString("task_type"),
                rs.getString("canonical_problem_jsonb")), sessionId, userId);
        if (sources.size()!=1) throw error(ApiErrorCode.SOLVE_SOURCE_NOT_READY);
        return sources.getFirst();
    }

    /** Historical identity is explicit; this method never promotes a previous parse to current. */
    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = ApiProblemException.class)
    public SolveSource historical(UUID userId, UUID sessionId, UUID canonicalId) {
        lockOwner(userId, sessionId);
        var rows=jdbc.query("""
            select * from canonical_problems where id=? and user_id=? and problem_session_id=?
            """, (rs,n)->new SolveSource(rs.getObject("id",UUID.class),userId,sessionId,
                rs.getObject("problem_parse_id",UUID.class),rs.getInt("problem_parse_revision"),
                rs.getInt("canonical_revision"),rs.getString("problem_type"),rs.getString("task_type"),
                rs.getString("canonical_problem_jsonb")),canonicalId,userId,sessionId);
        if(rows.size()!=1) throw error(ApiErrorCode.SOLVE_SOURCE_NOT_READY);
        return rows.getFirst();
    }

    private static ApiProblemException error(ApiErrorCode code) {
        return new ApiProblemException(code==ApiErrorCode.RESOURCE_FORBIDDEN ? HttpStatus.NOT_FOUND
                : code==ApiErrorCode.ACCOUNT_NOT_ACTIVE ? HttpStatus.FORBIDDEN : HttpStatus.CONFLICT,
            code, "Solve source is unavailable", false, "REVIEW");
    }
}
