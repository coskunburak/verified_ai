package com.verifiedai.studyplan.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC persistence adapter for study_sessions and study_session_checkpoints
 * with strict (session_id, user_id) BOLA scoping and training_eligible=FALSE (INV-SESS-004).
 */
@Repository
public class StudySessionJdbcRepository implements StudySessionRepository {

    private static final TypeReference<List<StudySessionStepBlueprintItem>> BLUEPRINT_LIST_TYPE =
        new TypeReference<>() {};

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RowMapper<StudySessionRecord> sessionRowMapper;
    private final RowMapper<StudySessionCheckpointRecord> checkpointRowMapper;

    public StudySessionJdbcRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.sessionRowMapper = this::mapSessionRow;
        this.checkpointRowMapper = this::mapCheckpointRow;
    }

    @Override
    public void insertSession(StudySessionRecord session) {
        String sql = """
            INSERT INTO study_sessions (
                session_id, user_id, coach_session_id, source_context, topic_code, locale, state,
                total_steps, current_step_index, completed_steps_count, last_sequence_number,
                active_duration_seconds, paused_duration_seconds, client_instance_id, interruption_reason,
                step_blueprint_json, resume_snapshot_json, complete_idempotency_key, training_eligible,
                started_at, last_checkpoint_at, paused_at, resumed_at, completed_at, expires_at,
                created_at, updated_at
            ) VALUES (
                :sessionId, :userId, :coachSessionId, :sourceContext, :topicCode, :locale, :state,
                :totalSteps, :currentStepIndex, :completedStepsCount, :lastSequenceNumber,
                :activeDurationSeconds, :pausedDurationSeconds, :clientInstanceId, :interruptionReason,
                CAST(:stepBlueprintJson AS jsonb), CAST(:resumeSnapshotJson AS jsonb), :completeIdempotencyKey, FALSE,
                :startedAt, :lastCheckpointAt, :pausedAt, :resumedAt, :completedAt, :expiresAt,
                :createdAt, :updatedAt
            )
            """;
        jdbcTemplate.update(sql, sessionParams(session));
    }

    @Override
    public void updateSession(StudySessionRecord session) {
        String sql = """
            UPDATE study_sessions
            SET state = :state,
                current_step_index = :currentStepIndex,
                completed_steps_count = :completedStepsCount,
                last_sequence_number = :lastSequenceNumber,
                active_duration_seconds = :activeDurationSeconds,
                paused_duration_seconds = :pausedDurationSeconds,
                client_instance_id = :clientInstanceId,
                interruption_reason = :interruptionReason,
                step_blueprint_json = CAST(:stepBlueprintJson AS jsonb),
                resume_snapshot_json = CAST(:resumeSnapshotJson AS jsonb),
                complete_idempotency_key = :completeIdempotencyKey,
                last_checkpoint_at = :lastCheckpointAt,
                paused_at = :pausedAt,
                resumed_at = :resumedAt,
                completed_at = :completedAt,
                expires_at = :expiresAt,
                updated_at = :updatedAt
            WHERE session_id = :sessionId AND user_id = :userId
            """;
        jdbcTemplate.update(sql, sessionParams(session));
    }

    @Override
    public Optional<StudySessionRecord> findBySessionIdAndUserId(UUID sessionId, UUID userId) {
        String sql = """
            SELECT session_id, user_id, coach_session_id, source_context, topic_code, locale, state,
                   total_steps, current_step_index, completed_steps_count, last_sequence_number,
                   active_duration_seconds, paused_duration_seconds, client_instance_id, interruption_reason,
                   step_blueprint_json, resume_snapshot_json, complete_idempotency_key, training_eligible,
                   started_at, last_checkpoint_at, paused_at, resumed_at, completed_at, expires_at,
                   created_at, updated_at
            FROM study_sessions
            WHERE session_id = :sessionId AND user_id = :userId
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("sessionId", sessionId, Types.OTHER)
            .addValue("userId", userId, Types.OTHER);
        List<StudySessionRecord> rows = jdbcTemplate.query(sql, params, sessionRowMapper);
        return rows.stream().findFirst();
    }

    @Override
    public List<StudySessionRecord> findOpenSessionsByUserId(UUID userId) {
        String sql = """
            SELECT session_id, user_id, coach_session_id, source_context, topic_code, locale, state,
                   total_steps, current_step_index, completed_steps_count, last_sequence_number,
                   active_duration_seconds, paused_duration_seconds, client_instance_id, interruption_reason,
                   step_blueprint_json, resume_snapshot_json, complete_idempotency_key, training_eligible,
                   started_at, last_checkpoint_at, paused_at, resumed_at, completed_at, expires_at,
                   created_at, updated_at
            FROM study_sessions
            WHERE user_id = :userId AND state IN ('INITIALIZED', 'ACTIVE', 'PAUSED')
            ORDER BY updated_at DESC
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("userId", userId, Types.OTHER);
        return jdbcTemplate.query(sql, params, sessionRowMapper);
    }

    @Override
    public Optional<StudySessionCheckpointRecord> findCheckpointBySessionAndIdempotencyKey(
        UUID sessionId,
        UUID userId,
        String idempotencyKey
    ) {
        String sql = """
            SELECT checkpoint_id, session_id, user_id, sequence_number, step_index, step_type, step_status,
                   reported_active_duration_seconds, accepted_active_duration_seconds, duration_clamped,
                   offline_queued, client_recorded_at, server_received_at, idempotency_key,
                   checkpoint_payload_json, training_eligible
            FROM study_session_checkpoints
            WHERE session_id = :sessionId AND user_id = :userId AND idempotency_key = :idempotencyKey
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("sessionId", sessionId, Types.OTHER)
            .addValue("userId", userId, Types.OTHER)
            .addValue("idempotencyKey", idempotencyKey, Types.VARCHAR);
        List<StudySessionCheckpointRecord> rows = jdbcTemplate.query(sql, params, checkpointRowMapper);
        return rows.stream().findFirst();
    }

    @Override
    public void insertCheckpoint(StudySessionCheckpointRecord checkpoint) {
        String sql = """
            INSERT INTO study_session_checkpoints (
                checkpoint_id, session_id, user_id, sequence_number, step_index, step_type, step_status,
                reported_active_duration_seconds, accepted_active_duration_seconds, duration_clamped,
                offline_queued, client_recorded_at, server_received_at, idempotency_key,
                checkpoint_payload_json, training_eligible
            ) VALUES (
                :checkpointId, :sessionId, :userId, :sequenceNumber, :stepIndex, :stepType, :stepStatus,
                :reportedActiveDurationSeconds, :acceptedActiveDurationSeconds, :durationClamped,
                :offlineQueued, :clientRecordedAt, :serverReceivedAt, :idempotencyKey,
                CAST(:checkpointPayloadJson AS jsonb), FALSE
            )
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("checkpointId", checkpoint.checkpointId(), Types.OTHER)
            .addValue("sessionId", checkpoint.sessionId(), Types.OTHER)
            .addValue("userId", checkpoint.userId(), Types.OTHER)
            .addValue("sequenceNumber", checkpoint.sequenceNumber(), Types.BIGINT)
            .addValue("stepIndex", checkpoint.stepIndex(), Types.INTEGER)
            .addValue("stepType", checkpoint.stepType().name(), Types.VARCHAR)
            .addValue("stepStatus", checkpoint.stepStatus().name(), Types.VARCHAR)
            .addValue("reportedActiveDurationSeconds", checkpoint.reportedActiveDurationSeconds(), Types.INTEGER)
            .addValue("acceptedActiveDurationSeconds", checkpoint.acceptedActiveDurationSeconds(), Types.INTEGER)
            .addValue("durationClamped", checkpoint.durationClamped(), Types.BOOLEAN)
            .addValue("offlineQueued", checkpoint.offlineQueued(), Types.BOOLEAN)
            .addValue("clientRecordedAt", toTimestamp(checkpoint.clientRecordedAt()), Types.TIMESTAMP)
            .addValue("serverReceivedAt", toTimestamp(checkpoint.serverReceivedAt()), Types.TIMESTAMP)
            .addValue("idempotencyKey", checkpoint.idempotencyKey(), Types.VARCHAR)
            .addValue("checkpointPayloadJson", toJson(checkpoint.checkpointPayload()), Types.VARCHAR);
        jdbcTemplate.update(sql, params);
    }

    @Override
    public List<StudySessionRecord> findAllSessionsByUserId(UUID userId) {
        String sql = """
            SELECT session_id, user_id, coach_session_id, source_context, topic_code, locale, state,
                   total_steps, current_step_index, completed_steps_count, last_sequence_number,
                   active_duration_seconds, paused_duration_seconds, client_instance_id, interruption_reason,
                   step_blueprint_json, resume_snapshot_json, complete_idempotency_key, training_eligible,
                   started_at, last_checkpoint_at, paused_at, resumed_at, completed_at, expires_at,
                   created_at, updated_at
            FROM study_sessions
            WHERE user_id = :userId
            ORDER BY created_at DESC
            """;
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("userId", userId, Types.OTHER);
        return jdbcTemplate.query(sql, params, sessionRowMapper);
    }

    @Override
    public long countCheckpointsByUserId(UUID userId) {
        String sql = "SELECT COUNT(*) FROM study_session_checkpoints WHERE user_id = :userId";
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("userId", userId, Types.OTHER);
        Long count = jdbcTemplate.queryForObject(sql, params, Long.class);
        return count == null ? 0L : count;
    }

    @Override
    public int purgeAllForUser(UUID userId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("userId", userId, Types.OTHER);
        int deletedCheckpoints = jdbcTemplate.update(
            "DELETE FROM study_session_checkpoints WHERE user_id = :userId",
            params
        );
        int deletedSessions = jdbcTemplate.update(
            "DELETE FROM study_sessions WHERE user_id = :userId",
            params
        );
        return deletedCheckpoints + deletedSessions;
    }

    private MapSqlParameterSource sessionParams(StudySessionRecord session) {
        return new MapSqlParameterSource()
            .addValue("sessionId", session.sessionId(), Types.OTHER)
            .addValue("userId", session.userId(), Types.OTHER)
            .addValue("coachSessionId", session.coachSessionId(), Types.OTHER)
            .addValue("sourceContext", session.sourceContext().name(), Types.VARCHAR)
            .addValue("topicCode", session.topicCode(), Types.VARCHAR)
            .addValue("locale", session.locale(), Types.VARCHAR)
            .addValue("state", session.state().name(), Types.VARCHAR)
            .addValue("totalSteps", session.totalSteps(), Types.INTEGER)
            .addValue("currentStepIndex", session.currentStepIndex(), Types.INTEGER)
            .addValue("completedStepsCount", session.completedStepsCount(), Types.INTEGER)
            .addValue("lastSequenceNumber", session.lastSequenceNumber(), Types.BIGINT)
            .addValue("activeDurationSeconds", session.activeDurationSeconds(), Types.INTEGER)
            .addValue("pausedDurationSeconds", session.pausedDurationSeconds(), Types.INTEGER)
            .addValue("clientInstanceId", session.clientInstanceId(), Types.VARCHAR)
            .addValue(
                "interruptionReason",
                session.interruptionReason() != null ? session.interruptionReason().name() : null,
                Types.VARCHAR
            )
            .addValue("stepBlueprintJson", toJson(session.stepBlueprint()), Types.VARCHAR)
            .addValue("resumeSnapshotJson", toJson(session.resumeSnapshot()), Types.VARCHAR)
            .addValue("completeIdempotencyKey", session.completeIdempotencyKey(), Types.VARCHAR)
            .addValue("startedAt", toTimestamp(session.startedAt()), Types.TIMESTAMP)
            .addValue("lastCheckpointAt", toTimestamp(session.lastCheckpointAt()), Types.TIMESTAMP)
            .addValue("pausedAt", toTimestamp(session.pausedAt()), Types.TIMESTAMP)
            .addValue("resumedAt", toTimestamp(session.resumedAt()), Types.TIMESTAMP)
            .addValue("completedAt", toTimestamp(session.completedAt()), Types.TIMESTAMP)
            .addValue("expiresAt", toTimestamp(session.expiresAt()), Types.TIMESTAMP)
            .addValue("createdAt", toTimestamp(session.createdAt()), Types.TIMESTAMP)
            .addValue("updatedAt", toTimestamp(session.updatedAt()), Types.TIMESTAMP);
    }

    private StudySessionRecord mapSessionRow(ResultSet rs, int rowNum) throws SQLException {
        String reasonRaw = rs.getString("interruption_reason");
        return new StudySessionRecord(
            rs.getObject("session_id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getObject("coach_session_id", UUID.class),
            StudySessionSourceContext.valueOf(rs.getString("source_context")),
            rs.getString("topic_code"),
            rs.getString("locale"),
            StudySessionState.valueOf(rs.getString("state")),
            rs.getInt("total_steps"),
            rs.getInt("current_step_index"),
            rs.getInt("completed_steps_count"),
            rs.getLong("last_sequence_number"),
            rs.getInt("active_duration_seconds"),
            rs.getInt("paused_duration_seconds"),
            rs.getString("client_instance_id"),
            reasonRaw != null ? StudySessionInterruptionReason.valueOf(reasonRaw) : null,
            fromJson(rs.getString("step_blueprint_json"), BLUEPRINT_LIST_TYPE),
            fromJson(rs.getString("resume_snapshot_json"), StudySessionResumeSnapshot.class),
            rs.getString("complete_idempotency_key"),
            rs.getBoolean("training_eligible"),
            toInstant(rs, "started_at"),
            toInstant(rs, "last_checkpoint_at"),
            toInstant(rs, "paused_at"),
            toInstant(rs, "resumed_at"),
            toInstant(rs, "completed_at"),
            toInstant(rs, "expires_at"),
            toInstant(rs, "created_at"),
            toInstant(rs, "updated_at")
        );
    }

    private StudySessionCheckpointRecord mapCheckpointRow(ResultSet rs, int rowNum) throws SQLException {
        return new StudySessionCheckpointRecord(
            rs.getObject("checkpoint_id", UUID.class),
            rs.getObject("session_id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getLong("sequence_number"),
            rs.getInt("step_index"),
            StudySessionStepType.valueOf(rs.getString("step_type")),
            StudySessionStepStatus.valueOf(rs.getString("step_status")),
            rs.getInt("reported_active_duration_seconds"),
            rs.getInt("accepted_active_duration_seconds"),
            rs.getBoolean("duration_clamped"),
            rs.getBoolean("offline_queued"),
            toInstant(rs, "client_recorded_at"),
            toInstant(rs, "server_received_at"),
            rs.getString("idempotency_key"),
            fromJson(rs.getString("checkpoint_payload_json"), StudySessionResumeSnapshot.class),
            rs.getBoolean("training_eligible")
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize study session JSON payload", ex);
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize study session JSON payload", ex);
        }
    }

    private <T> T fromJson(String json, TypeReference<T> typeRef) {
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize study session JSON array payload", ex);
        }
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant toInstant(ResultSet rs, String col) throws SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts == null ? null : ts.toInstant();
    }
}
