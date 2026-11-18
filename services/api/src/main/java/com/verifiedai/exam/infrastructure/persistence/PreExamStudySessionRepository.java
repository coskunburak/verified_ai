package com.verifiedai.exam.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** H-15 private snapshots. All learner operations include an explicit owner predicate. */
@Repository
public class PreExamStudySessionRepository {
    public record Snapshot(UUID sessionId, UUID userId, String examCode, String sourceFingerprint,
                           String reviewEtag, String sheetEtag, String reviewJson, String sheetJson,
                           Instant createdAt, Instant expiresAt) {}
    private final JdbcClient jdbc;
    private final JsonMapper mapper;

    public PreExamStudySessionRepository(JdbcClient jdbc, JsonMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /** Transaction-scoped lock also serializes the first insert when no row yet exists. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void lockOwnerExam(UUID user, String examCode) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))")
                .param("key", "h15:" + user + ":" + examCode).query((rs, n) -> Boolean.TRUE).single();
    }

    public Optional<Snapshot> findFresh(UUID user, String examCode, String fingerprint, Instant now) {
        return jdbc.sql("""
                SELECT * FROM pre_exam_study_sessions
                WHERE user_id=:user AND exam_code=:exam AND source_fingerprint=:fingerprint AND expires_at>:now
                ORDER BY created_at DESC,session_id LIMIT 1
                """).param("user", user).param("exam", examCode).param("fingerprint", fingerprint)
                .param("now", Timestamp.from(now)).query((rs, n) -> new Snapshot(
                        rs.getObject("session_id", UUID.class), rs.getObject("user_id", UUID.class),
                        rs.getString("exam_code"), rs.getString("source_fingerprint"),
                        rs.getString("payload_etag"), rs.getString("sheet_etag"),
                        rs.getString("review_payload"), rs.getString("cheat_sheet_payload"),
                        rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("expires_at").toInstant())).optional();
    }

    public void insert(Snapshot row) {
        JsonNode review = mapper.readTree(row.reviewJson());
        JsonNode sheet = mapper.readTree(row.sheetJson());
        validateIdentity(review, row, "pre-exam-review-v1", row.reviewEtag());
        validateIdentity(sheet, row, "exam-cheat-sheet-v1", row.sheetEtag());
        jdbc.sql("""
                INSERT INTO pre_exam_study_sessions
                (session_id,user_id,exam_code,target_exam_date,target_exam_at,time_precision,window_status,
                 evidence_status,hours_remaining,excluded_low_mastery_topic_count,active_trap_count,formula_count,
                 source_fingerprint,content_fingerprint,algorithm_version,catalog_version,review_payload,
                 cheat_sheet_payload,payload_etag,sheet_etag,created_at,updated_at,expires_at)
                VALUES (:session,:user,:exam,CAST(:date AS date),CAST(:start AS timestamptz),:precision,:window,
                 :evidence,CAST(:hours AS numeric),:excluded,:traps,:formulas,:source,:content,:algorithm,:catalog,
                 CAST(:review AS jsonb),CAST(:sheet AS jsonb),:etag,:sheetEtag,:created,:created,:expires)
                """).param("session", row.sessionId()).param("user", row.userId()).param("exam", row.examCode())
                .param("date", nullableText(review, "targetExamDate"), java.sql.Types.VARCHAR)
                .param("start", nullableText(review, "targetExamAt"), java.sql.Types.VARCHAR)
                .param("hours", nullableText(review, "hoursRemaining"), java.sql.Types.VARCHAR)
                .param("precision", review.path("timePrecision").asText())
                .param("window", review.path("windowStatus").asText())
                .param("evidence", review.path("evidenceStatus").asText())
                .param("excluded", review.path("excludedLowMasteryTopicCount").asInt())
                .param("traps", review.path("activeTraps").size()).param("formulas", review.path("highYieldFormulas").size())
                .param("source", row.sourceFingerprint()).param("content", row.reviewEtag())
                .param("algorithm", review.path("algorithmVersion").asText())
                .param("catalog", review.path("catalogVersion").asText())
                .param("review", row.reviewJson()).param("sheet", row.sheetJson())
                .param("etag", row.reviewEtag()).param("sheetEtag", row.sheetEtag())
                .param("created", Timestamp.from(row.createdAt())).param("expires", Timestamp.from(row.expiresAt())).update();
    }

    private static String nullableText(JsonNode json, String name) {
        JsonNode value = json.get(name);
        if (value == null) throw new IllegalArgumentException("Missing snapshot field: " + name);
        return value.isNull() ? null : value.asText();
    }

    private static void validateIdentity(JsonNode json, Snapshot row, String version, String etag) {
        if (!row.userId().toString().equals(json.path("userId").asText())
                || !row.sessionId().toString().equals(json.path("sessionId").asText())
                || !row.examCode().equals(json.path("examCode").asText())
                || !version.equals(json.path("schemaVersion").asText())
                || !etag.equals(json.path("etag").asText())
                || !json.path("trainingEligible").isBoolean() || json.path("trainingEligible").asBoolean()
                || !json.path("llmTokensUsed").isIntegralNumber() || json.path("llmTokensUsed").asInt() != 0
                || !row.createdAt().equals(Instant.parse(json.path("generatedAt").asText()))
                || !row.expiresAt().equals(Instant.parse(json.path("expiresAt").asText()))) {
            throw new IllegalArgumentException("Snapshot identity or privacy invariant violated");
        }
    }

    /** Empty means absent/expired/foreign; false means an accepted replay. */
    @Transactional
    public Optional<Boolean> recordExport(UUID user, UUID session, String key, Instant now) {
        if (key == null || key.isBlank() || key.length() > 128) throw new IllegalArgumentException("Invalid export idempotency key");
        var found = jdbc.sql("""
                SELECT session_id FROM pre_exam_study_sessions
                WHERE user_id=:user AND session_id=:session AND expires_at>:now FOR UPDATE
                """).param("user", user).param("session", session).param("now", Timestamp.from(now))
                .query(UUID.class).optional();
        if (found.isEmpty()) return Optional.empty();
        int inserted = jdbc.sql("""
                INSERT INTO pre_exam_export_events (session_id,user_id,idempotency_key,created_at)
                VALUES (:session,:user,:key,:now) ON CONFLICT DO NOTHING
                """).param("session", session).param("user", user).param("key", key)
                .param("now", Timestamp.from(now)).update();
        return Optional.of(inserted == 1);
    }

    public List<Map<String, Object>> exportByUserId(UUID user) {
        return jdbc.sql("SELECT * FROM pre_exam_study_sessions WHERE user_id=:user ORDER BY created_at,session_id")
                .param("user", user).query().listOfRows();
    }
    public List<Map<String, Object>> exportEventsByUserId(UUID user) {
        return jdbc.sql("SELECT * FROM pre_exam_export_events WHERE user_id=:user ORDER BY created_at,session_id,idempotency_key")
                .param("user", user).query().listOfRows();
    }
    public int deleteByUserId(UUID user) {
        return jdbc.sql("DELETE FROM pre_exam_study_sessions WHERE user_id=:user").param("user", user).update();
    }
    public int purgeExpired(Instant now) {
        return jdbc.sql("DELETE FROM pre_exam_study_sessions WHERE expires_at<=:now")
                .param("now", Timestamp.from(now)).update();
    }
}
