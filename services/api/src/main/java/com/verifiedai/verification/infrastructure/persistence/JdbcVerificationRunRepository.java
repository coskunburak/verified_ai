package com.verifiedai.verification.infrastructure.persistence;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.verifiedai.verification.domain.model.VerificationRun;
import com.verifiedai.verification.domain.repository.VerificationRunRepository;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class JdbcVerificationRunRepository implements VerificationRunRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public JdbcVerificationRunRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        var time = new SimpleModule();
        time.addSerializer(Instant.class, new JsonSerializer<Instant>() {
            public void serialize(Instant value, JsonGenerator generator, SerializerProvider provider) throws IOException {
                generator.writeString(value.toString());
            }
        });
        time.addDeserializer(Instant.class, new JsonDeserializer<Instant>() {
            public Instant deserialize(JsonParser parser, DeserializationContext context) throws IOException {
                if (!parser.hasToken(JsonToken.VALUE_STRING)) throw new IOException("Invalid evidence timestamp");
                return Instant.parse(parser.getText());
            }
        });
        mapper = new ObjectMapper(JsonFactory.builder().streamReadConstraints(StreamReadConstraints.builder()
            .maxNestingDepth(80).maxStringLength(4096).build()).build())
            .registerModule(time).registerModule(EvidenceMathJson.module()).enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS);
    }
    public Optional<VerificationRun> find(UUID user, UUID session, UUID key) {
        return jdbc.query("select run_jsonb::text from verification_runs where user_id=? and problem_session_id=? and request_key=?",
            (rs, n) -> decode(rs.getString(1)), user, session, key).stream().findFirst();
    }
    public VerificationRun publish(VerificationRun run) {
        var s = run.source();
        jdbc.update("""
            insert into verification_runs(id,request_key,user_id,problem_session_id,solution_id,canonical_problem_id,
                problem_parse_id,problem_parse_revision,canonical_revision,representation_version,normalizer_version,
                plan_id,policy_version,capability_version,correlation_id,run_jsonb,created_at)
            values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,cast(? as jsonb),?)
            on conflict(user_id,problem_session_id,request_key) do nothing
            """, run.id(), run.requestKey(), s.userId(), s.sessionId(), run.solutionId(), s.canonicalProblemId(), s.parseId(),
            s.parseRevision(), s.canonicalRevision(), run.solutionVersion().representation(), run.solutionVersion().normalizer(),
            run.planId(), run.policyVersion(), run.capabilityVersion(), run.correlationId(), encode(run), Timestamp.from(run.createdAt()));
        var stored = find(s.userId(), s.sessionId(), run.requestKey()).orElseThrow();
        if (!stored.id().equals(run.id()) || !stored.planId().equals(run.planId()) || !stored.solutionId().equals(run.solutionId()))
            throw new IllegalArgumentException("Idempotency key already bound to another execution scope");
        return stored;
    }
    public List<VerificationRun> export(UUID user) {
        return jdbc.query("select run_jsonb::text from verification_runs where user_id=? order by created_at,id",
            (rs, n) -> decode(rs.getString(1)), user);
    }
    private String encode(VerificationRun run) {
        try {
            String json = mapper.writeValueAsString(run);
            if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 65536) throw new IllegalArgumentException("Evidence limit exceeded");
            return json;
        } catch (IOException e) { throw new IllegalArgumentException("Cannot encode verification evidence"); }
    }
    private VerificationRun decode(String json) {
        try { return mapper.readValue(json, VerificationRun.class); }
        catch (IOException e) { throw new IllegalArgumentException("Invalid stored verification evidence"); }
    }
}
