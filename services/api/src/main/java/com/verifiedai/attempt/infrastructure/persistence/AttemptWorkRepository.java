package com.verifiedai.attempt.infrastructure.persistence;

import com.fasterxml.jackson.databind.*;
import com.verifiedai.attempt.domain.AttemptStructuredWork;
import com.verifiedai.sharedkernel.contracts.VersionedJsonContract;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AttemptWorkRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper=new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    public AttemptWorkRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public boolean available() { return Boolean.TRUE.equals(jdbc.queryForObject("select to_regclass('attempt_structured_work') is not null",Boolean.class)); }
    public Optional<AttemptStructuredWork> get(UUID owner,UUID attempt) {
        return jdbc.query("select artifact::text from attempt_structured_work where user_id=? and attempt_id=?",
            (rs,n)->decode(rs.getString(1)),owner,attempt).stream().findFirst();
    }
    public boolean superseded(UUID owner,UUID attempt) {
        return jdbc.queryForObject("select count(*) from attempt_structured_work where user_id=? and supersedes_attempt_id=?",Integer.class,owner,attempt)>0;
    }
    public void insert(AttemptStructuredWork work) {
        jdbc.update("insert into attempt_structured_work(attempt_id,user_id,revision,supersedes_attempt_id,artifact) values(?,?,?,?,?::jsonb)",
            work.attemptId(),work.ownerId(),work.revision(),work.supersedesAttemptId(),encode(work));
    }
    public List<AttemptStructuredWork> export(UUID owner) {
        if(!available()) return List.of();
        return jdbc.query("select artifact::text from attempt_structured_work where user_id=? order by attempt_id",(rs,n)->decode(rs.getString(1)),owner);
    }
    private String encode(AttemptStructuredWork work) {
        try { var value=mapper.writeValueAsString(work); VersionedJsonContract.validate("attempt-structured-work-v1",value); return value; }
        catch(java.io.IOException ex) { throw new IllegalArgumentException("Invalid structured artifact"); }
    }
    private AttemptStructuredWork decode(String json) {
        try { VersionedJsonContract.validate("attempt-structured-work-v1",json); return mapper.readValue(json,AttemptStructuredWork.class); }
        catch(java.io.IOException ex) { throw new IllegalArgumentException("Invalid stored structured artifact"); }
    }
}
