package com.verifiedai.verification.infrastructure.persistence;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.verifiedai.verification.domain.model.VerificationDecision;
import com.verifiedai.verification.domain.repository.VerificationDecisionRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class JdbcVerificationDecisionRepository implements VerificationDecisionRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(timeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private static SimpleModule timeModule() {
        var module = new SimpleModule();
        module.addSerializer(Instant.class, new JsonSerializer<Instant>() {
            public void serialize(Instant value, com.fasterxml.jackson.core.JsonGenerator out, SerializerProvider context) throws java.io.IOException { out.writeString(value.toString()); }
        });
        module.addDeserializer(Instant.class, new JsonDeserializer<Instant>() {
            public Instant deserialize(com.fasterxml.jackson.core.JsonParser in, DeserializationContext context) throws java.io.IOException {
                if (!in.hasToken(com.fasterxml.jackson.core.JsonToken.VALUE_STRING)) throw new java.io.IOException("Invalid timestamp");
                return Instant.parse(in.getText());
            }
        });
        return module;
    }
    public JdbcVerificationDecisionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Optional<VerificationDecision> find(UUID user, UUID session, UUID key) {
        return jdbc.query("select decision_jsonb::text from verification_decisions where user_id=? and problem_session_id=? and request_key=?",
            (rs,n) -> decode(rs.getString(1)), user, session, key).stream().findFirst();
    }
    public Optional<VerificationDecision> latest(UUID user, UUID session, UUID solution) {
        return jdbc.query("select decision_jsonb::text from verification_decisions where user_id=? and problem_session_id=? and solution_id=? order by created_at desc,id desc limit 1",
            (rs,n) -> decode(rs.getString(1)), user, session, solution).stream().findFirst();
    }
    public VerificationDecision publish(VerificationDecision d) {
        jdbc.update("""
            insert into verification_decisions(id,request_key,user_id,problem_session_id,solution_id,run_id,
                policy_version,decision_jsonb,created_at)
            values (?,?,?,?,?,?,?,cast(? as jsonb),?) on conflict(user_id,problem_session_id,request_key) do nothing
            """, d.id(), d.requestKey(), d.source().userId(), d.source().sessionId(), d.solutionId(), d.runId(),
            d.policyVersion(), encode(d), Timestamp.from(d.createdAt()));
        var stored = find(d.source().userId(), d.source().sessionId(), d.requestKey()).orElseThrow();
        if (!stored.runId().equals(d.runId()) || !stored.solutionId().equals(d.solutionId())
            || !stored.policyVersion().equals(d.policyVersion())) throw new IllegalArgumentException("Decision key scope conflict");
        return stored;
    }
    public List<VerificationDecision> export(UUID user) {
        return jdbc.query("select decision_jsonb::text from verification_decisions where user_id=? order by created_at,id",
            (rs,n) -> decode(rs.getString(1)), user);
    }
    public int attempts(UUID user, UUID session, UUID solution) {
        return jdbc.queryForObject("select count(*) from (select 1 from verification_runs where user_id=? and problem_session_id=? and solution_id=? limit 3) bounded_attempts",
            Integer.class, user, session, solution);
    }
    private String encode(VerificationDecision d) {
        try { return mapper.writeValueAsString(d); }
        catch (java.io.IOException e) { throw new IllegalArgumentException("Invalid decision encoding"); }
    }
    private VerificationDecision decode(String json) {
        try {
            if (json.length() > 16384) throw new IllegalArgumentException("Decision too large");
            return mapper.readValue(json, VerificationDecision.class);
        } catch (java.io.IOException e) { throw new IllegalArgumentException("Invalid stored decision"); }
    }
}
