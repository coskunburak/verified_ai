package com.verifiedai.tutoring.application.learning;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
/** Evidence from delivered hints only; denied or merely opened hints never count. */
@Component
public class TutorAssistanceReader {
    private final JdbcTemplate jdbc;
    public TutorAssistanceReader(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public boolean hasAssistance(UUID owner,UUID problem) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from tutor_hints h join tutor_sessions s on s.id=h.tutor_session_id and s.user_id=h.user_id where s.user_id=? and s.problem_session_id=?)",Boolean.class,owner,problem));
    }
}
