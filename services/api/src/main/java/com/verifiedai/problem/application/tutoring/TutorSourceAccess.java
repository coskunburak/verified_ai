package com.verifiedai.problem.application.tutoring;

import com.verifiedai.problem.application.solving.SolveSourceAccess;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Published read boundary. No generation or verification, and no canonical content leaves this contract. */
@Service
@Transactional(propagation = Propagation.MANDATORY, noRollbackFor = ApiProblemException.class)
public class TutorSourceAccess {
    private final SolveSourceAccess source;
    private final JdbcTemplate jdbc;
    public TutorSourceAccess(SolveSourceAccess source, JdbcTemplate jdbc) { this.source=source; this.jdbc=jdbc; }
    public void lockOwner(UUID owner, UUID problem) { source.lockOwner(owner, problem); }
    public Snapshot current(UUID owner, UUID problem) {
        var current=source.current(owner,problem);
        var skills=jdbc.query("""
            select status, primary_skill_id from problem_classifications
            where user_id=? and problem_session_id=? and canonical_problem_id=? order by revision desc limit 1
            """, (rs,n)->"CLASSIFIED".equals(rs.getString(1)) ? rs.getString(2) : "NOT_AVAILABLE",
            owner,problem,current.canonicalProblemId());
        return new Snapshot(current.canonicalProblemId(),current.canonicalRevision(),current.problemParseId(),
            current.problemParseRevision(),skills.isEmpty() ? "NOT_AVAILABLE" : skills.getFirst());
    }
    public boolean isCurrent(UUID owner, UUID problem, Snapshot historical) {
        try {
            var now=current(owner,problem);
            return now.canonicalId().equals(historical.canonicalId()) && now.canonicalRevision()==historical.canonicalRevision()
                && now.parseId().equals(historical.parseId()) && now.parseRevision()==historical.parseRevision();
        } catch (ApiProblemException ex) {
            if (ex.code()==ApiErrorCode.SOLVE_SOURCE_NOT_READY) return false;
            throw ex;
        }
    }
    public record Snapshot(UUID canonicalId, int canonicalRevision, UUID parseId, int parseRevision, String primarySkillId) {}
}
