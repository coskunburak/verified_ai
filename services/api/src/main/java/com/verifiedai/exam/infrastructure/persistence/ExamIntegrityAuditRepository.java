package com.verifiedai.exam.infrastructure.persistence;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Owner-scoped persistence repository for V064 {@code exam_integrity_audit_logs}
 * and {@code exam_integrity_used_nonces}.
 */
@Repository
public class ExamIntegrityAuditRepository {

    public enum IntegrityEventType {
        VERIFIED_SUBMIT,
        SIGNATURE_REJECTED,
        NONCE_REPLAY_REJECTED,
        CLOCK_SKEW_REJECTED,
        LEGACY_BYPASS_DISABLED
    }

    public record ExamIntegrityAuditRecord(
            UUID id,
            UUID sessionId,
            UUID userId,
            IntegrityEventType eventType,
            boolean signatureVerified,
            Long clientClockSkewMs,
            String payloadSha256,
            String nonceHash,
            String integrityKeyId,
            String idempotencyKeyHash,
            String protocolVersion,
            String reasonCode,
            boolean trainingEligible,
            Instant createdAt
    ) {
        public ExamIntegrityAuditRecord {
            Objects.requireNonNull(id, "id is required");
            Objects.requireNonNull(sessionId, "sessionId is required");
            Objects.requireNonNull(userId, "userId is required");
            Objects.requireNonNull(eventType, "eventType is required");
            Objects.requireNonNull(payloadSha256, "payloadSha256 is required");
            Objects.requireNonNull(protocolVersion, "protocolVersion is required");
            Objects.requireNonNull(reasonCode, "reasonCode is required");
            Objects.requireNonNull(createdAt, "createdAt is required");
            if (trainingEligible) {
                throw new IllegalArgumentException("trainingEligible must be false per ADR-006");
            }
        }
    }

    public record ConsumedNonceRecord(
            UUID sessionId,
            UUID userId,
            String nonceHash,
            String idempotencyKeyHash,
            String payloadSha256,
            String integrityKeyId,
            boolean trainingEligible,
            Instant consumedAt,
            Instant expiresAt
    ) {
        public ConsumedNonceRecord {
            Objects.requireNonNull(sessionId, "sessionId is required");
            Objects.requireNonNull(userId, "userId is required");
            Objects.requireNonNull(nonceHash, "nonceHash is required");
            Objects.requireNonNull(idempotencyKeyHash, "idempotencyKeyHash is required");
            Objects.requireNonNull(payloadSha256, "payloadSha256 is required");
            Objects.requireNonNull(integrityKeyId, "integrityKeyId is required");
            Objects.requireNonNull(consumedAt, "consumedAt is required");
            Objects.requireNonNull(expiresAt, "expiresAt is required");
            if (trainingEligible) {
                throw new IllegalArgumentException("trainingEligible must be false per ADR-006");
            }
        }
    }

    private final JdbcClient jdbc;

    public ExamIntegrityAuditRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc cannot be null");
    }

    public void insertAuditLog(ExamIntegrityAuditRecord record) {
        Objects.requireNonNull(record, "record cannot be null");
        jdbc.sql("""
                INSERT INTO exam_integrity_audit_logs (
                    id, session_id, user_id, event_type, signature_verified,
                    client_clock_skew_ms, payload_sha256, nonce_hash, integrity_key_id,
                    idempotency_key_hash, protocol_version, reason_code, training_eligible, created_at
                ) VALUES (
                    :id, :sessionId, :userId, :eventType, :signatureVerified,
                    :clientClockSkewMs, :payloadSha256, :nonceHash, :integrityKeyId,
                    :idempotencyKeyHash, :protocolVersion, :reasonCode, FALSE, :createdAt
                )
                """)
                .param("id", record.id())
                .param("sessionId", record.sessionId())
                .param("userId", record.userId())
                .param("eventType", record.eventType().name())
                .param("signatureVerified", record.signatureVerified())
                .param("clientClockSkewMs", record.clientClockSkewMs(), Types.BIGINT)
                .param("payloadSha256", record.payloadSha256())
                .param("nonceHash", record.nonceHash(), Types.CHAR)
                .param("integrityKeyId", record.integrityKeyId(), Types.VARCHAR)
                .param("idempotencyKeyHash", record.idempotencyKeyHash(), Types.CHAR)
                .param("protocolVersion", record.protocolVersion())
                .param("reasonCode", record.reasonCode())
                .param("createdAt", Timestamp.from(record.createdAt()))
                .update();
    }

    public boolean tryConsumeNonce(ConsumedNonceRecord record) {
        Objects.requireNonNull(record, "record cannot be null");
        int inserted = jdbc.sql("""
                INSERT INTO exam_integrity_used_nonces (
                    session_id, user_id, nonce_hash, idempotency_key_hash, payload_sha256,
                    integrity_key_id, training_eligible, consumed_at, expires_at
                ) VALUES (
                    :sessionId, :userId, :nonceHash, :idempotencyKeyHash, :payloadSha256,
                    :integrityKeyId, FALSE, :consumedAt, :expiresAt
                )
                ON CONFLICT (session_id, nonce_hash) DO NOTHING
                """)
                .param("sessionId", record.sessionId())
                .param("userId", record.userId())
                .param("nonceHash", record.nonceHash())
                .param("idempotencyKeyHash", record.idempotencyKeyHash())
                .param("payloadSha256", record.payloadSha256())
                .param("integrityKeyId", record.integrityKeyId())
                .param("consumedAt", Timestamp.from(record.consumedAt()))
                .param("expiresAt", Timestamp.from(record.expiresAt()))
                .update();
        return inserted == 1;
    }

    public Optional<ConsumedNonceRecord> findConsumedNonce(UUID sessionId, String nonceHash) {
        return jdbc.sql("""
                SELECT session_id, user_id, nonce_hash, idempotency_key_hash, payload_sha256,
                       integrity_key_id, training_eligible, consumed_at, expires_at
                FROM exam_integrity_used_nonces
                WHERE session_id = :sessionId AND nonce_hash = :nonceHash
                """)
                .param("sessionId", sessionId)
                .param("nonceHash", nonceHash)
                .query((rs, rowNum) -> new ConsumedNonceRecord(
                        rs.getObject("session_id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getString("nonce_hash"),
                        rs.getString("idempotency_key_hash"),
                        rs.getString("payload_sha256"),
                        rs.getString("integrity_key_id"),
                        rs.getBoolean("training_eligible"),
                        rs.getTimestamp("consumed_at").toInstant(),
                        rs.getTimestamp("expires_at").toInstant()
                ))
                .optional();
    }

    public List<ExamIntegrityAuditRecord> findByUserId(UUID userId, int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 500));
        return jdbc.sql("""
                SELECT id, session_id, user_id, event_type, signature_verified,
                       client_clock_skew_ms, payload_sha256, nonce_hash, integrity_key_id,
                       idempotency_key_hash, protocol_version, reason_code, training_eligible, created_at
                FROM exam_integrity_audit_logs
                WHERE user_id = :userId
                ORDER BY created_at DESC
                LIMIT :limit
                """)
                .param("userId", userId)
                .param("limit", boundedLimit)
                .query((rs, rowNum) -> mapAuditRow(rs))
                .list();
    }

    public List<ExamIntegrityAuditRecord> findBySessionId(UUID sessionId) {
        return jdbc.sql("""
                SELECT id, session_id, user_id, event_type, signature_verified,
                       client_clock_skew_ms, payload_sha256, nonce_hash, integrity_key_id,
                       idempotency_key_hash, protocol_version, reason_code, training_eligible, created_at
                FROM exam_integrity_audit_logs
                WHERE session_id = :sessionId
                ORDER BY created_at DESC
                """)
                .param("sessionId", sessionId)
                .query((rs, rowNum) -> mapAuditRow(rs))
                .list();
    }

    /**
     * Returns owner-scoped sanitized metadata for KVKK/GDPR data export (no secret, nonce, or raw hashes).
     */
    public List<Map<String, Object>> exportByUserId(UUID userId) {
        return findByUserId(userId, 200).stream()
                .map(row -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("auditId", row.id().toString());
                    m.put("sessionId", row.sessionId().toString());
                    m.put("eventType", row.eventType().name());
                    m.put("signatureVerified", row.signatureVerified());
                    m.put("clientClockSkewMs", row.clientClockSkewMs());
                    m.put("protocolVersion", row.protocolVersion());
                    m.put("reasonCode", row.reasonCode());
                    m.put("trainingEligible", false);
                    m.put("createdAt", row.createdAt().toString());
                    return m;
                })
                .toList();
    }

    public int deleteByUserId(UUID userId) {
        int deletedNonces = jdbc.sql("DELETE FROM exam_integrity_used_nonces WHERE user_id = :userId")
                .param("userId", userId)
                .update();
        int deletedLogs = jdbc.sql("DELETE FROM exam_integrity_audit_logs WHERE user_id = :userId")
                .param("userId", userId)
                .update();
        return deletedNonces + deletedLogs;
    }

    public int purgeExpired(Instant now, Duration auditRetention) {
        Objects.requireNonNull(now, "now is required");
        Objects.requireNonNull(auditRetention, "auditRetention is required");
        Instant auditCutoff = now.minus(auditRetention);
        int deletedNonces = jdbc.sql("""
                DELETE FROM exam_integrity_used_nonces n
                WHERE n.expires_at < :now
                  AND NOT EXISTS (
                      SELECT 1 FROM exam_sessions s
                      WHERE s.id = n.session_id
                        AND s.status IN ('IN_PROGRESS', 'OFFLINE_GRACE_BUFFER')
                        AND s.grace_deadline_at >= :now
                  )
                """)
                .param("now", Timestamp.from(now))
                .update();
        int deletedLogs = jdbc.sql("DELETE FROM exam_integrity_audit_logs WHERE created_at < :cutoff")
                .param("cutoff", Timestamp.from(auditCutoff))
                .update();
        return deletedNonces + deletedLogs;
    }

    private static ExamIntegrityAuditRecord mapAuditRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ExamIntegrityAuditRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("session_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                IntegrityEventType.valueOf(rs.getString("event_type")),
                rs.getBoolean("signature_verified"),
                (Long) rs.getObject("client_clock_skew_ms"),
                rs.getString("payload_sha256"),
                rs.getString("nonce_hash"),
                rs.getString("integrity_key_id"),
                rs.getString("idempotency_key_hash"),
                rs.getString("protocol_version"),
                rs.getString("reason_code"),
                rs.getBoolean("training_eligible"),
                rs.getTimestamp("created_at").toInstant()
        );
    }
}
