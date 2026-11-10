package com.verifiedai.mastery;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
/** Existing legacy namespace only; no synthetic v1 fallback for canonical v2 records. */
@Component
public class LegacyMasteryWriter {
    private final JdbcTemplate jdbc;
    private final boolean v2;
    public LegacyMasteryWriter(JdbcTemplate jdbc,@Value("${verifiedai.mastery.evidence-stratified-enabled:false}") boolean v2) { this.jdbc=jdbc; this.v2=v2; }
    public void observe(UUID owner,String skill,boolean correct,String assistance,Instant now) {
        if(v2) return;
        if(!skill.startsWith("attempt-skill-v1:")) throw new IllegalArgumentException("Legacy skill namespace");
        var prior=jdbc.query("select * from learner_skill_mastery where user_id=? and skill_id=? for update",(r,n)->
            new LegacyMasteryPolicy.State(r.getDouble("score"),r.getDouble("half_life_days"),r.getInt("evidence_count"),r.getDouble("confidence"),r.getTimestamp("last_assessed_at").toInstant()),owner,skill).stream().findFirst().orElse(null);
        var next=LegacyMasteryPolicy.observe(prior,correct,!"INDEPENDENT".equals(assistance),now);
        jdbc.update("""
            insert into learner_skill_mastery(user_id,skill_id,mastery_version,score,half_life_days,evidence_count,confidence,last_assessed_at)
            values(?,?,'bkt-lite-v1',?,?,?,?,?) on conflict(user_id,skill_id) do update set score=excluded.score,
            half_life_days=excluded.half_life_days,evidence_count=excluded.evidence_count,confidence=excluded.confidence,last_assessed_at=excluded.last_assessed_at
            """,owner,skill,next.score(),next.halfLife(),next.count(),next.confidence(),Timestamp.from(now));
    }
}
