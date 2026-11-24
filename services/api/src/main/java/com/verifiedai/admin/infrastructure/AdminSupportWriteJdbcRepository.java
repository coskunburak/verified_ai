package com.verifiedai.admin.infrastructure;

import com.verifiedai.admin.domain.AdminAuditAction;
import com.verifiedai.admin.domain.AdminAuditTargetType;
import com.verifiedai.admin.domain.WrongAnswerReportReason;
import com.verifiedai.admin.domain.WrongAnswerReportStatus;
import com.verifiedai.admin.domain.WrongAnswerResolutionCode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
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
 * Persistence repository strictly scoped to V072 admin/support tables
 * (admin_support_audit_logs, admin_raw_content_access_grants, wrong_answer_reports).
 * Never mutates any foreign-owned module table (INV-ADM-003).
 */
@Repository
public class AdminSupportWriteJdbcRepository {

    private final JdbcClient jdbcClient;

    public AdminSupportWriteJdbcRepository(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient must not be null");
    }

    public void acquireAdvisoryLock(String lockKey) {
        jdbcClient.sql("SELECT pg_advisory_xact_lock(hashtextextended(:lockKey, 1080))")
            .param("lockKey", lockKey, Types.VARCHAR)
            .query(rs -> {
            });
    }

    public void insertAuditLog(StoredAuditLog log) {
        jdbcClient.sql(
                """
                INSERT INTO admin_support_audit_logs (
                    id, action, target_type, target_id, actor_user_id, actor_role,
                    idempotency_key, payload_hash, reason, before_state_json,
                    after_state_json, correlation_id, training_eligible, occurred_at
                ) VALUES (
                    :id, :action, :targetType, :targetId, :actorUserId, :actorRole,
                    :idempotencyKey, :payloadHash, :reason, CAST(:beforeStateJson AS jsonb),
                    CAST(:afterStateJson AS jsonb), :correlationId, FALSE, :occurredAt
                )
                """
            )
            .param("id", log.id(), Types.OTHER)
            .param("action", log.action().name(), Types.VARCHAR)
            .param("targetType", log.targetType().name(), Types.VARCHAR)
            .param("targetId", log.targetId(), Types.VARCHAR)
            .param("actorUserId", log.actorUserId(), Types.OTHER)
            .param("actorRole", log.actorRole(), Types.VARCHAR)
            .param("idempotencyKey", log.idempotencyKey(), Types.VARCHAR)
            .param("payloadHash", log.payloadHash(), Types.CHAR)
            .param("reason", log.reason(), Types.VARCHAR)
            .param("beforeStateJson", log.beforeStateJson(), Types.VARCHAR)
            .param("afterStateJson", log.afterStateJson(), Types.VARCHAR)
            .param("correlationId", log.correlationId(), Types.VARCHAR)
            .param("occurredAt", Timestamp.from(log.occurredAt()), Types.TIMESTAMP)
            .update();
    }

    public Optional<StoredAuditLog> findAuditByIdempotencyKey(String idempotencyKey) {
        return jdbcClient.sql(
                """
                SELECT id, action, target_type, target_id, actor_user_id, actor_role,
                       idempotency_key, payload_hash, reason,
                       before_state_json::text AS before_state_json,
                       after_state_json::text AS after_state_json,
                       correlation_id, training_eligible, occurred_at
                FROM admin_support_audit_logs
                WHERE idempotency_key = :idempotencyKey
                """
            )
            .param("idempotencyKey", idempotencyKey, Types.VARCHAR)
            .query(this::mapAuditLog)
            .optional();
    }

    public Optional<StoredAuditLog> findAuditById(UUID auditId) {
        return jdbcClient.sql(
                """
                SELECT id, action, target_type, target_id, actor_user_id, actor_role,
                       idempotency_key, payload_hash, reason,
                       before_state_json::text AS before_state_json,
                       after_state_json::text AS after_state_json,
                       correlation_id, training_eligible, occurred_at
                FROM admin_support_audit_logs
                WHERE id = :id
                """
            )
            .param("id", auditId, Types.OTHER)
            .query(this::mapAuditLog)
            .optional();
    }

    public List<StoredAuditLog> findRecentAuditLogs(int limit) {
        return jdbcClient.sql(
                """
                SELECT id, action, target_type, target_id, actor_user_id, actor_role,
                       idempotency_key, payload_hash, reason,
                       before_state_json::text AS before_state_json,
                       after_state_json::text AS after_state_json,
                       correlation_id, training_eligible, occurred_at
                FROM admin_support_audit_logs
                ORDER BY occurred_at DESC, id DESC
                LIMIT :limit
                """
            )
            .param("limit", limit, Types.INTEGER)
            .query(this::mapAuditLog)
            .list();
    }

    public void insertRawContentGrant(StoredRawContentGrant grant) {
        jdbcClient.sql(
                """
                INSERT INTO admin_raw_content_access_grants (
                    id, actor_user_id, actor_role, target_user_id, problem_session_id,
                    ticket_reference, reason, idempotency_key, payload_hash, audit_log_id,
                    granted_at, expires_at, revoked_at, correlation_id, training_eligible
                ) VALUES (
                    :id, :actorUserId, :actorRole, :targetUserId, :problemSessionId,
                    :ticketReference, :reason, :idempotencyKey, :payloadHash, :auditLogId,
                    :grantedAt, :expiresAt, :revokedAt, :correlationId, FALSE
                )
                """
            )
            .param("id", grant.id(), Types.OTHER)
            .param("actorUserId", grant.actorUserId(), Types.OTHER)
            .param("actorRole", grant.actorRole(), Types.VARCHAR)
            .param("targetUserId", grant.targetUserId(), Types.OTHER)
            .param("problemSessionId", grant.problemSessionId(), Types.OTHER)
            .param("ticketReference", grant.ticketReference(), Types.VARCHAR)
            .param("reason", grant.reason(), Types.VARCHAR)
            .param("idempotencyKey", grant.idempotencyKey(), Types.VARCHAR)
            .param("payloadHash", grant.payloadHash(), Types.CHAR)
            .param("auditLogId", grant.auditLogId(), Types.OTHER)
            .param("grantedAt", Timestamp.from(grant.grantedAt()), Types.TIMESTAMP)
            .param("expiresAt", Timestamp.from(grant.expiresAt()), Types.TIMESTAMP)
            .param("revokedAt", grant.revokedAt() == null ? null : Timestamp.from(grant.revokedAt()), Types.TIMESTAMP)
            .param("correlationId", grant.correlationId(), Types.VARCHAR)
            .update();
    }

    public Optional<StoredRawContentGrant> findRawContentGrantByIdempotencyKey(String idempotencyKey) {
        return jdbcClient.sql(
                """
                SELECT id, actor_user_id, actor_role, target_user_id, problem_session_id,
                       ticket_reference, reason, idempotency_key, payload_hash, audit_log_id,
                       granted_at, expires_at, revoked_at, correlation_id, training_eligible
                FROM admin_raw_content_access_grants
                WHERE idempotency_key = :idempotencyKey
                """
            )
            .param("idempotencyKey", idempotencyKey, Types.VARCHAR)
            .query(this::mapRawGrant)
            .optional();
    }

    public Optional<StoredRawContentGrant> findRawContentGrantById(UUID grantId) {
        return jdbcClient.sql(
                """
                SELECT id, actor_user_id, actor_role, target_user_id, problem_session_id,
                       ticket_reference, reason, idempotency_key, payload_hash, audit_log_id,
                       granted_at, expires_at, revoked_at, correlation_id, training_eligible
                FROM admin_raw_content_access_grants
                WHERE id = :id
                """
            )
            .param("id", grantId, Types.OTHER)
            .query(this::mapRawGrant)
            .optional();
    }

    public void deleteRawContentGrantsByUserId(UUID userId) {
        jdbcClient.sql("DELETE FROM admin_raw_content_access_grants WHERE target_user_id = :userId")
            .param("userId", userId, Types.OTHER)
            .update();
    }

    public void insertWrongAnswerReport(StoredWrongAnswerReport report) {
        jdbcClient.sql(
                """
                INSERT INTO wrong_answer_reports (
                    id, user_id, problem_session_id, idempotency_key, payload_hash,
                    report_reason, user_note, verification_status_snapshot,
                    policy_version_snapshot, model_version_snapshot, problem_type_snapshot,
                    status, reviewer_user_id, resolution_code, resolution_note,
                    linked_remediation_audit_id, correlation_id, version,
                    training_eligible, created_at, updated_at, resolved_at
                ) VALUES (
                    :id, :userId, :problemSessionId, :idempotencyKey, :payloadHash,
                    :reportReason, :userNote, :verificationStatusSnapshot,
                    :policyVersionSnapshot, :modelVersionSnapshot, :problemTypeSnapshot,
                    :status, :reviewerUserId, :resolutionCode, :resolutionNote,
                    :linkedRemediationAuditId, :correlationId, :version,
                    FALSE, :createdAt, :updatedAt, :resolvedAt
                )
                """
            )
            .param("id", report.id(), Types.OTHER)
            .param("userId", report.userId(), Types.OTHER)
            .param("problemSessionId", report.problemSessionId(), Types.OTHER)
            .param("idempotencyKey", report.idempotencyKey(), Types.VARCHAR)
            .param("payloadHash", report.payloadHash(), Types.CHAR)
            .param("reportReason", report.reportReason().name(), Types.VARCHAR)
            .param("userNote", report.userNote(), Types.VARCHAR)
            .param("verificationStatusSnapshot", report.verificationStatusSnapshot(), Types.VARCHAR)
            .param("policyVersionSnapshot", report.policyVersionSnapshot(), Types.VARCHAR)
            .param("modelVersionSnapshot", report.modelVersionSnapshot(), Types.VARCHAR)
            .param("problemTypeSnapshot", report.problemTypeSnapshot(), Types.VARCHAR)
            .param("status", report.status().name(), Types.VARCHAR)
            .param("reviewerUserId", report.reviewerUserId(), Types.OTHER)
            .param(
                "resolutionCode",
                report.resolutionCode() != null ? report.resolutionCode().name() : null,
                Types.VARCHAR
            )
            .param("resolutionNote", report.resolutionNote(), Types.VARCHAR)
            .param("linkedRemediationAuditId", report.linkedRemediationAuditId(), Types.OTHER)
            .param("correlationId", report.correlationId(), Types.VARCHAR)
            .param("version", report.version(), Types.BIGINT)
            .param("createdAt", Timestamp.from(report.createdAt()), Types.TIMESTAMP)
            .param("updatedAt", Timestamp.from(report.updatedAt()), Types.TIMESTAMP)
            .param("resolvedAt", report.resolvedAt() == null ? null : Timestamp.from(report.resolvedAt()), Types.TIMESTAMP)
            .update();
    }

    public Optional<StoredWrongAnswerReport> findWrongAnswerReportByUserAndIdempotencyKey(
        UUID userId,
        String idempotencyKey
    ) {
        return jdbcClient.sql(
                """
                SELECT id, user_id, problem_session_id, idempotency_key, payload_hash,
                       report_reason, user_note, verification_status_snapshot,
                       policy_version_snapshot, model_version_snapshot, problem_type_snapshot,
                       status, reviewer_user_id, resolution_code, resolution_note,
                       linked_remediation_audit_id, correlation_id, version,
                       training_eligible, created_at, updated_at, resolved_at
                FROM wrong_answer_reports
                WHERE user_id = :userId AND idempotency_key = :idempotencyKey
                """
            )
            .param("userId", userId, Types.OTHER)
            .param("idempotencyKey", idempotencyKey, Types.VARCHAR)
            .query(this::mapWrongAnswerReport)
            .optional();
    }

    public Optional<StoredWrongAnswerReport> findWrongAnswerReportById(UUID reportId) {
        return jdbcClient.sql(
                """
                SELECT id, user_id, problem_session_id, idempotency_key, payload_hash,
                       report_reason, user_note, verification_status_snapshot,
                       policy_version_snapshot, model_version_snapshot, problem_type_snapshot,
                       status, reviewer_user_id, resolution_code, resolution_note,
                       linked_remediation_audit_id, correlation_id, version,
                       training_eligible, created_at, updated_at, resolved_at
                FROM wrong_answer_reports
                WHERE id = :id
                """
            )
            .param("id", reportId, Types.OTHER)
            .query(this::mapWrongAnswerReport)
            .optional();
    }

    public List<StoredWrongAnswerReport> listWrongAnswerReports(WrongAnswerReportStatus statusFilter, int limit) {
        if (statusFilter != null) {
            return jdbcClient.sql(
                    """
                    SELECT id, user_id, problem_session_id, idempotency_key, payload_hash,
                           report_reason, user_note, verification_status_snapshot,
                           policy_version_snapshot, model_version_snapshot, problem_type_snapshot,
                           status, reviewer_user_id, resolution_code, resolution_note,
                           linked_remediation_audit_id, correlation_id, version,
                           training_eligible, created_at, updated_at, resolved_at
                    FROM wrong_answer_reports
                    WHERE status = :status
                    ORDER BY created_at DESC, id DESC
                    LIMIT :limit
                    """
                )
                .param("status", statusFilter.name(), Types.VARCHAR)
                .param("limit", limit, Types.INTEGER)
                .query(this::mapWrongAnswerReport)
                .list();
        }
        return jdbcClient.sql(
                """
                SELECT id, user_id, problem_session_id, idempotency_key, payload_hash,
                       report_reason, user_note, verification_status_snapshot,
                       policy_version_snapshot, model_version_snapshot, problem_type_snapshot,
                       status, reviewer_user_id, resolution_code, resolution_note,
                       linked_remediation_audit_id, correlation_id, version,
                       training_eligible, created_at, updated_at, resolved_at
                FROM wrong_answer_reports
                ORDER BY created_at DESC, id DESC
                LIMIT :limit
                """
            )
            .param("limit", limit, Types.INTEGER)
            .query(this::mapWrongAnswerReport)
            .list();
    }

    public int updateWrongAnswerReportOptimistic(
        UUID reportId,
        WrongAnswerReportStatus targetStatus,
        UUID reviewerUserId,
        WrongAnswerResolutionCode resolutionCode,
        String resolutionNote,
        UUID linkedRemediationAuditId,
        long expectedVersion,
        Instant updatedAt,
        Instant resolvedAt
    ) {
        return jdbcClient.sql(
                """
                UPDATE wrong_answer_reports
                SET status = :status,
                    reviewer_user_id = :reviewerUserId,
                    resolution_code = :resolutionCode,
                    resolution_note = :resolutionNote,
                    linked_remediation_audit_id = COALESCE(:linkedRemediationAuditId, linked_remediation_audit_id),
                    version = version + 1,
                    updated_at = :updatedAt,
                    resolved_at = :resolvedAt
                WHERE id = :id AND version = :expectedVersion
                """
            )
            .param("status", targetStatus.name(), Types.VARCHAR)
            .param("reviewerUserId", reviewerUserId, Types.OTHER)
            .param("resolutionCode", resolutionCode != null ? resolutionCode.name() : null, Types.VARCHAR)
            .param("resolutionNote", resolutionNote, Types.VARCHAR)
            .param("linkedRemediationAuditId", linkedRemediationAuditId, Types.OTHER)
            .param("updatedAt", Timestamp.from(updatedAt), Types.TIMESTAMP)
            .param("resolvedAt", resolvedAt == null ? null : Timestamp.from(resolvedAt), Types.TIMESTAMP)
            .param("id", reportId, Types.OTHER)
            .param("expectedVersion", expectedVersion, Types.BIGINT)
            .update();
    }

    public List<Map<String, Object>> findWrongAnswerReportsForPrivacyExport(UUID userId) {
        return jdbcClient.sql(
                """
                SELECT id, problem_session_id, report_reason, user_note,
                       verification_status_snapshot, policy_version_snapshot,
                       model_version_snapshot, problem_type_snapshot, status,
                       resolution_code, resolution_note, training_eligible,
                       created_at, updated_at, resolved_at
                FROM wrong_answer_reports
                WHERE user_id = :userId
                ORDER BY created_at DESC
                LIMIT 200
                """
            )
            .param("userId", userId, Types.OTHER)
            .query((rs, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("reportId", rs.getObject("id", UUID.class).toString());
                row.put("problemSessionId", rs.getObject("problem_session_id", UUID.class).toString());
                row.put("reportReason", rs.getString("report_reason"));
                row.put("userNote", rs.getString("user_note"));
                row.put("verificationStatusSnapshot", rs.getString("verification_status_snapshot"));
                row.put("policyVersionSnapshot", rs.getString("policy_version_snapshot"));
                row.put("modelVersionSnapshot", rs.getString("model_version_snapshot"));
                row.put("problemTypeSnapshot", rs.getString("problem_type_snapshot"));
                row.put("status", rs.getString("status"));
                row.put("resolutionCode", rs.getString("resolution_code"));
                row.put("resolutionNote", rs.getString("resolution_note"));
                row.put("trainingEligible", rs.getBoolean("training_eligible"));
                row.put("createdAt", toInstant(rs.getTimestamp("created_at")).toString());
                row.put("updatedAt", toInstant(rs.getTimestamp("updated_at")).toString());
                Timestamp resolvedTs = rs.getTimestamp("resolved_at");
                row.put("resolvedAt", resolvedTs == null ? null : resolvedTs.toInstant().toString());
                return row;
            })
            .list();
    }

    public void deleteWrongAnswerReportsByUserId(UUID userId) {
        jdbcClient.sql("DELETE FROM wrong_answer_reports WHERE user_id = :userId")
            .param("userId", userId, Types.OTHER)
            .update();
    }

    private StoredAuditLog mapAuditLog(ResultSet rs, int rowNum) throws SQLException {
        return new StoredAuditLog(
            rs.getObject("id", UUID.class),
            AdminAuditAction.valueOf(rs.getString("action")),
            AdminAuditTargetType.valueOf(rs.getString("target_type")),
            rs.getString("target_id"),
            rs.getObject("actor_user_id", UUID.class),
            rs.getString("actor_role"),
            rs.getString("idempotency_key"),
            rs.getString("payload_hash"),
            rs.getString("reason"),
            rs.getString("before_state_json"),
            rs.getString("after_state_json"),
            rs.getString("correlation_id"),
            rs.getBoolean("training_eligible"),
            toInstant(rs.getTimestamp("occurred_at"))
        );
    }

    private StoredRawContentGrant mapRawGrant(ResultSet rs, int rowNum) throws SQLException {
        return new StoredRawContentGrant(
            rs.getObject("id", UUID.class),
            rs.getObject("actor_user_id", UUID.class),
            rs.getString("actor_role"),
            rs.getObject("target_user_id", UUID.class),
            rs.getObject("problem_session_id", UUID.class),
            rs.getString("ticket_reference"),
            rs.getString("reason"),
            rs.getString("idempotency_key"),
            rs.getString("payload_hash"),
            rs.getObject("audit_log_id", UUID.class),
            toInstant(rs.getTimestamp("granted_at")),
            toInstant(rs.getTimestamp("expires_at")),
            toInstant(rs.getTimestamp("revoked_at")),
            rs.getString("correlation_id"),
            rs.getBoolean("training_eligible")
        );
    }

    private StoredWrongAnswerReport mapWrongAnswerReport(ResultSet rs, int rowNum) throws SQLException {
        String rawResCode = rs.getString("resolution_code");
        return new StoredWrongAnswerReport(
            rs.getObject("id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getObject("problem_session_id", UUID.class),
            rs.getString("idempotency_key"),
            rs.getString("payload_hash"),
            WrongAnswerReportReason.valueOf(rs.getString("report_reason")),
            rs.getString("user_note"),
            rs.getString("verification_status_snapshot"),
            rs.getString("policy_version_snapshot"),
            rs.getString("model_version_snapshot"),
            rs.getString("problem_type_snapshot"),
            WrongAnswerReportStatus.valueOf(rs.getString("status")),
            rs.getObject("reviewer_user_id", UUID.class),
            rawResCode == null ? null : WrongAnswerResolutionCode.valueOf(rawResCode),
            rs.getString("resolution_note"),
            rs.getObject("linked_remediation_audit_id", UUID.class),
            rs.getString("correlation_id"),
            rs.getLong("version"),
            rs.getBoolean("training_eligible"),
            toInstant(rs.getTimestamp("created_at")),
            toInstant(rs.getTimestamp("updated_at")),
            toInstant(rs.getTimestamp("resolved_at"))
        );
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    public record StoredAuditLog(
        UUID id,
        AdminAuditAction action,
        AdminAuditTargetType targetType,
        String targetId,
        UUID actorUserId,
        String actorRole,
        String idempotencyKey,
        String payloadHash,
        String reason,
        String beforeStateJson,
        String afterStateJson,
        String correlationId,
        boolean trainingEligible,
        Instant occurredAt
    ) {
    }

    public record StoredRawContentGrant(
        UUID id,
        UUID actorUserId,
        String actorRole,
        UUID targetUserId,
        UUID problemSessionId,
        String ticketReference,
        String reason,
        String idempotencyKey,
        String payloadHash,
        UUID auditLogId,
        Instant grantedAt,
        Instant expiresAt,
        Instant revokedAt,
        String correlationId,
        boolean trainingEligible
    ) {
    }

    public record StoredWrongAnswerReport(
        UUID id,
        UUID userId,
        UUID problemSessionId,
        String idempotencyKey,
        String payloadHash,
        WrongAnswerReportReason reportReason,
        String userNote,
        String verificationStatusSnapshot,
        String policyVersionSnapshot,
        String modelVersionSnapshot,
        String problemTypeSnapshot,
        WrongAnswerReportStatus status,
        UUID reviewerUserId,
        WrongAnswerResolutionCode resolutionCode,
        String resolutionNote,
        UUID linkedRemediationAuditId,
        String correlationId,
        long version,
        boolean trainingEligible,
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt
    ) {
    }
}
