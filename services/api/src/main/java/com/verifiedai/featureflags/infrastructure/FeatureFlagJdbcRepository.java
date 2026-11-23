package com.verifiedai.featureflags.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.verifiedai.featureflags.domain.ExperimentStatus;
import com.verifiedai.featureflags.domain.ExperimentVariantSpec;
import com.verifiedai.featureflags.domain.FeatureFlagDefinitionSnapshot;
import com.verifiedai.featureflags.domain.FlagCategory;
import com.verifiedai.featureflags.domain.FlagValueType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL JdbcClient persistence adapter for feature flags, experiments,
 * deterministic user assignments, immutable audit logs, and GDPR lifecycle operations.
 */
@Repository
public class FeatureFlagJdbcRepository {

    private static final TypeReference<List<ExperimentVariantSpec>> VARIANT_LIST_TYPE = new TypeReference<>() {};
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().findAndAddModules().build();

    private final JdbcClient jdbcClient;

    public FeatureFlagJdbcRepository(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient must not be null");
    }

    public void acquireAdvisoryLock(String lockKey) {
        jdbcClient.sql("SELECT pg_advisory_xact_lock(hashtextextended(:lockKey, 1070))")
            .param("lockKey", lockKey, Types.VARCHAR)
            .query(rs -> {
            });
    }

    public boolean isAccountActive(UUID userId) {
        Optional<String> status = jdbcClient.sql("SELECT status FROM users WHERE id = :userId")
            .param("userId", userId, Types.OTHER)
            .query(String.class)
            .optional();
        return status.map("ACTIVE"::equals).orElse(false);
    }

    public Optional<FeatureFlagDefinitionSnapshot> findFlagByKey(String flagKey) {
        return jdbcClient.sql(
                """
                SELECT flag_key, category, value_type, client_safe, enabled, kill_switch_active,
                       rollout_basis_points, bucketing_salt,
                       default_value_json::text AS default_value_json_text,
                       current_value_json::text AS current_value_json_text,
                       owner, purpose, planned_removal_at, version, training_eligible, created_at, updated_at
                FROM feature_flag_definitions
                WHERE flag_key = :flagKey
                """
            )
            .param("flagKey", flagKey, Types.VARCHAR)
            .query((rs, rowNum) -> mapFlagRow(rs))
            .optional();
    }

    public List<FeatureFlagDefinitionSnapshot> findAllFlags() {
        return jdbcClient.sql(
                """
                SELECT flag_key, category, value_type, client_safe, enabled, kill_switch_active,
                       rollout_basis_points, bucketing_salt,
                       default_value_json::text AS default_value_json_text,
                       current_value_json::text AS current_value_json_text,
                       owner, purpose, planned_removal_at, version, training_eligible, created_at, updated_at
                FROM feature_flag_definitions
                ORDER BY flag_key ASC
                """
            )
            .query((rs, rowNum) -> mapFlagRow(rs))
            .list();
    }

    public List<FeatureFlagDefinitionSnapshot> findClientSafeFlags(int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        return jdbcClient.sql(
                """
                SELECT flag_key, category, value_type, client_safe, enabled, kill_switch_active,
                       rollout_basis_points, bucketing_salt,
                       default_value_json::text AS default_value_json_text,
                       current_value_json::text AS current_value_json_text,
                       owner, purpose, planned_removal_at, version, training_eligible, created_at, updated_at
                FROM feature_flag_definitions
                WHERE client_safe = TRUE
                ORDER BY flag_key ASC
                LIMIT :limit
                """
            )
            .param("limit", boundedLimit, Types.INTEGER)
            .query((rs, rowNum) -> mapFlagRow(rs))
            .list();
    }

    public int updateFlagOptimistic(
        String flagKey,
        boolean enabled,
        int rolloutBasisPoints,
        String currentValueJson,
        Instant plannedRemovalAt,
        long expectedVersion,
        Instant updatedAt
    ) {
        return jdbcClient.sql(
                """
                UPDATE feature_flag_definitions
                SET enabled = :enabled,
                    rollout_basis_points = :rolloutBasisPoints,
                    current_value_json = CAST(:currentValueJson AS jsonb),
                    planned_removal_at = :plannedRemovalAt,
                    version = version + 1,
                    updated_at = :updatedAt
                WHERE flag_key = :flagKey AND version = :expectedVersion
                """
            )
            .param("enabled", enabled, Types.BOOLEAN)
            .param("rolloutBasisPoints", rolloutBasisPoints, Types.INTEGER)
            .param("currentValueJson", currentValueJson, Types.VARCHAR)
            .param("plannedRemovalAt", plannedRemovalAt != null ? Timestamp.from(plannedRemovalAt) : null, Types.TIMESTAMP)
            .param("updatedAt", Timestamp.from(updatedAt), Types.TIMESTAMP)
            .param("flagKey", flagKey, Types.VARCHAR)
            .param("expectedVersion", expectedVersion, Types.BIGINT)
            .update();
    }

    public int updateKillSwitchOptimistic(
        String flagKey,
        boolean killSwitchActive,
        long expectedVersion,
        Instant updatedAt
    ) {
        return jdbcClient.sql(
                """
                UPDATE feature_flag_definitions
                SET kill_switch_active = :killSwitchActive,
                    version = version + 1,
                    updated_at = :updatedAt
                WHERE flag_key = :flagKey AND version = :expectedVersion
                """
            )
            .param("killSwitchActive", killSwitchActive, Types.BOOLEAN)
            .param("updatedAt", Timestamp.from(updatedAt), Types.TIMESTAMP)
            .param("flagKey", flagKey, Types.VARCHAR)
            .param("expectedVersion", expectedVersion, Types.BIGINT)
            .update();
    }

    public Optional<StoredExperimentDefinition> findExperimentByKey(String experimentKey) {
        return jdbcClient.sql(
                """
                SELECT experiment_key, linked_flag_key, status, client_safe, rollout_basis_points,
                       bucketing_salt, control_variant, variants_json::text AS variants_json_text,
                       owner, purpose, planned_removal_at, experiment_version, version,
                       training_eligible, created_at, updated_at
                FROM experiment_definitions
                WHERE experiment_key = :experimentKey
                """
            )
            .param("experimentKey", experimentKey, Types.VARCHAR)
            .query((rs, rowNum) -> mapExperimentRow(rs))
            .optional();
    }

    public List<StoredExperimentDefinition> findAllExperiments() {
        return jdbcClient.sql(
                """
                SELECT experiment_key, linked_flag_key, status, client_safe, rollout_basis_points,
                       bucketing_salt, control_variant, variants_json::text AS variants_json_text,
                       owner, purpose, planned_removal_at, experiment_version, version,
                       training_eligible, created_at, updated_at
                FROM experiment_definitions
                ORDER BY experiment_key ASC
                """
            )
            .query((rs, rowNum) -> mapExperimentRow(rs))
            .list();
    }

    public List<StoredExperimentDefinition> findClientSafeExperiments() {
        return jdbcClient.sql(
                """
                SELECT experiment_key, linked_flag_key, status, client_safe, rollout_basis_points,
                       bucketing_salt, control_variant, variants_json::text AS variants_json_text,
                       owner, purpose, planned_removal_at, experiment_version, version,
                       training_eligible, created_at, updated_at
                FROM experiment_definitions
                WHERE client_safe = TRUE
                ORDER BY experiment_key ASC
                """
            )
            .query((rs, rowNum) -> mapExperimentRow(rs))
            .list();
    }

    public int updateExperimentOptimistic(
        String experimentKey,
        ExperimentStatus status,
        int rolloutBasisPoints,
        long expectedVersion,
        Instant updatedAt
    ) {
        return jdbcClient.sql(
                """
                UPDATE experiment_definitions
                SET status = :status,
                    rollout_basis_points = :rolloutBasisPoints,
                    version = version + 1,
                    updated_at = :updatedAt
                WHERE experiment_key = :experimentKey AND version = :expectedVersion
                """
            )
            .param("status", status.name(), Types.VARCHAR)
            .param("rolloutBasisPoints", rolloutBasisPoints, Types.INTEGER)
            .param("updatedAt", Timestamp.from(updatedAt), Types.TIMESTAMP)
            .param("experimentKey", experimentKey, Types.VARCHAR)
            .param("expectedVersion", expectedVersion, Types.BIGINT)
            .update();
    }

    public Optional<StoredExperimentAssignment> findAssignment(
        UUID userId,
        String experimentKey,
        int experimentVersion
    ) {
        return jdbcClient.sql(
                """
                SELECT id, user_id, experiment_key, experiment_version, variant_key,
                       bucket_value, assignment_reason, assigned_at, training_eligible
                FROM experiment_assignments
                WHERE user_id = :userId
                  AND experiment_key = :experimentKey
                  AND experiment_version = :experimentVersion
                """
            )
            .param("userId", userId, Types.OTHER)
            .param("experimentKey", experimentKey, Types.VARCHAR)
            .param("experimentVersion", experimentVersion, Types.INTEGER)
            .query((rs, rowNum) -> new StoredExperimentAssignment(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("experiment_key"),
                rs.getInt("experiment_version"),
                rs.getString("variant_key"),
                rs.getInt("bucket_value"),
                rs.getString("assignment_reason"),
                rs.getTimestamp("assigned_at").toInstant(),
                rs.getBoolean("training_eligible")
            ))
            .optional();
    }

    public void insertAssignmentIdempotent(StoredExperimentAssignment assignment) {
        jdbcClient.sql(
                """
                INSERT INTO experiment_assignments (
                    id, user_id, experiment_key, experiment_version, variant_key,
                    bucket_value, assignment_reason, assigned_at, training_eligible
                ) VALUES (
                    :id, :userId, :experimentKey, :experimentVersion, :variantKey,
                    :bucketValue, :assignmentReason, :assignedAt, FALSE
                )
                ON CONFLICT (user_id, experiment_key, experiment_version) DO NOTHING
                """
            )
            .param("id", assignment.id(), Types.OTHER)
            .param("userId", assignment.userId(), Types.OTHER)
            .param("experimentKey", assignment.experimentKey(), Types.VARCHAR)
            .param("experimentVersion", assignment.experimentVersion(), Types.INTEGER)
            .param("variantKey", assignment.variantKey(), Types.VARCHAR)
            .param("bucketValue", assignment.bucketValue(), Types.INTEGER)
            .param("assignmentReason", assignment.assignmentReason(), Types.VARCHAR)
            .param("assignedAt", Timestamp.from(assignment.assignedAt()), Types.TIMESTAMP)
            .update();
    }

    public Optional<StoredAuditLog> findAuditByIdempotencyKey(String idempotencyKey) {
        return jdbcClient.sql(
                """
                SELECT id, target_type, target_key, action, actor_user_id, actor_role,
                       idempotency_key, payload_hash, reason,
                       before_state_json::text AS before_state_json_text,
                       after_state_json::text AS after_state_json_text,
                       correlation_id, training_eligible, occurred_at
                FROM feature_flag_audit_logs
                WHERE idempotency_key = :idempotencyKey
                """
            )
            .param("idempotencyKey", idempotencyKey, Types.VARCHAR)
            .query((rs, rowNum) -> mapAuditRow(rs))
            .optional();
    }

    public void insertAuditLog(StoredAuditLog auditLog) {
        jdbcClient.sql(
                """
                INSERT INTO feature_flag_audit_logs (
                    id, target_type, target_key, action, actor_user_id, actor_role,
                    idempotency_key, payload_hash, reason, before_state_json, after_state_json,
                    correlation_id, training_eligible, occurred_at
                ) VALUES (
                    :id, :targetType, :targetKey, :action, :actorUserId, :actorRole,
                    :idempotencyKey, :payloadHash, :reason,
                    CAST(:beforeStateJson AS jsonb), CAST(:afterStateJson AS jsonb),
                    :correlationId, FALSE, :occurredAt
                )
                """
            )
            .param("id", auditLog.id(), Types.OTHER)
            .param("targetType", auditLog.targetType(), Types.VARCHAR)
            .param("targetKey", auditLog.targetKey(), Types.VARCHAR)
            .param("action", auditLog.action(), Types.VARCHAR)
            .param("actorUserId", auditLog.actorUserId(), Types.OTHER)
            .param("actorRole", auditLog.actorRole(), Types.VARCHAR)
            .param("idempotencyKey", auditLog.idempotencyKey(), Types.VARCHAR)
            .param("payloadHash", auditLog.payloadHash(), Types.CHAR)
            .param("reason", auditLog.reason(), Types.VARCHAR)
            .param("beforeStateJson", auditLog.beforeStateJson(), Types.VARCHAR)
            .param("afterStateJson", auditLog.afterStateJson(), Types.VARCHAR)
            .param("correlationId", auditLog.correlationId(), Types.VARCHAR)
            .param("occurredAt", Timestamp.from(auditLog.occurredAt()), Types.TIMESTAMP)
            .update();
    }

    public List<StoredAuditLog> findRecentAuditLogs(int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 200));
        return jdbcClient.sql(
                """
                SELECT id, target_type, target_key, action, actor_user_id, actor_role,
                       idempotency_key, payload_hash, reason,
                       before_state_json::text AS before_state_json_text,
                       after_state_json::text AS after_state_json_text,
                       correlation_id, training_eligible, occurred_at
                FROM feature_flag_audit_logs
                ORDER BY occurred_at DESC, id DESC
                LIMIT :limit
                """
            )
            .param("limit", boundedLimit, Types.INTEGER)
            .query((rs, rowNum) -> mapAuditRow(rs))
            .list();
    }

    /**
     * PII-free GDPR export of user-scoped experiment assignments (INV-FF-006).
     */
    public List<Map<String, Object>> findUserAssignmentsForExport(UUID userId) {
        return jdbcClient.sql(
                """
                SELECT experiment_key, experiment_version, variant_key,
                       bucket_value, assignment_reason, assigned_at
                FROM experiment_assignments
                WHERE user_id = :userId
                ORDER BY assigned_at DESC, experiment_key ASC
                """
            )
            .param("userId", userId, Types.OTHER)
            .query((rs, rowNum) -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("experimentKey", rs.getString("experiment_key"));
                item.put("experimentVersion", rs.getInt("experiment_version"));
                item.put("variantKey", rs.getString("variant_key"));
                item.put("bucketValue", rs.getInt("bucket_value"));
                item.put("assignmentReason", rs.getString("assignment_reason"));
                item.put("assignedAt", rs.getTimestamp("assigned_at").toInstant().toString());
                item.put("trainingEligible", false);
                return Collections.unmodifiableMap(item);
            })
            .list();
    }

    public int deleteAssignmentsByUserId(UUID userId) {
        return jdbcClient.sql("DELETE FROM experiment_assignments WHERE user_id = :userId")
            .param("userId", userId, Types.OTHER)
            .update();
    }

    private static FeatureFlagDefinitionSnapshot mapFlagRow(ResultSet rs) throws SQLException {
        Timestamp planned = rs.getTimestamp("planned_removal_at");
        return new FeatureFlagDefinitionSnapshot(
            rs.getString("flag_key"),
            FlagCategory.valueOf(rs.getString("category")),
            FlagValueType.valueOf(rs.getString("value_type")),
            rs.getBoolean("client_safe"),
            rs.getBoolean("enabled"),
            rs.getBoolean("kill_switch_active"),
            rs.getInt("rollout_basis_points"),
            rs.getString("bucketing_salt"),
            rs.getString("default_value_json_text"),
            rs.getString("current_value_json_text"),
            rs.getString("owner"),
            rs.getString("purpose"),
            planned != null ? planned.toInstant() : null,
            rs.getLong("version"),
            rs.getBoolean("training_eligible"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
        );
    }

    private static StoredExperimentDefinition mapExperimentRow(ResultSet rs) throws SQLException {
        Timestamp planned = rs.getTimestamp("planned_removal_at");
        return new StoredExperimentDefinition(
            rs.getString("experiment_key"),
            rs.getString("linked_flag_key"),
            ExperimentStatus.valueOf(rs.getString("status")),
            rs.getBoolean("client_safe"),
            rs.getInt("rollout_basis_points"),
            rs.getString("bucketing_salt"),
            rs.getString("control_variant"),
            parseVariants(rs.getString("variants_json_text")),
            rs.getString("owner"),
            rs.getString("purpose"),
            planned != null ? planned.toInstant() : null,
            rs.getInt("experiment_version"),
            rs.getLong("version"),
            rs.getBoolean("training_eligible"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
        );
    }

    private static StoredAuditLog mapAuditRow(ResultSet rs) throws SQLException {
        return new StoredAuditLog(
            rs.getObject("id", UUID.class),
            rs.getString("target_type"),
            rs.getString("target_key"),
            rs.getString("action"),
            rs.getObject("actor_user_id", UUID.class),
            rs.getString("actor_role"),
            rs.getString("idempotency_key"),
            rs.getString("payload_hash"),
            rs.getString("reason"),
            rs.getString("before_state_json_text"),
            rs.getString("after_state_json_text"),
            rs.getString("correlation_id"),
            rs.getBoolean("training_eligible"),
            rs.getTimestamp("occurred_at").toInstant()
        );
    }

    private static List<ExperimentVariantSpec> parseVariants(String jsonText) {
        try {
            return List.copyOf(JSON_MAPPER.readValue(jsonText, VARIANT_LIST_TYPE));
        } catch (Exception exception) {
            throw new IllegalStateException("Corrupt experiment variants_json payload in database", exception);
        }
    }

    public record StoredExperimentDefinition(
        String experimentKey,
        String linkedFlagKey,
        ExperimentStatus status,
        boolean clientSafe,
        int rolloutBasisPoints,
        String bucketingSalt,
        String controlVariant,
        List<ExperimentVariantSpec> variants,
        String owner,
        String purpose,
        Instant plannedRemovalAt,
        int experimentVersion,
        long version,
        boolean trainingEligible,
        Instant createdAt,
        Instant updatedAt
    ) {
        public StoredExperimentDefinition {
            variants = variants == null ? List.of() : List.copyOf(variants);
        }
    }

    public record StoredExperimentAssignment(
        UUID id,
        UUID userId,
        String experimentKey,
        int experimentVersion,
        String variantKey,
        int bucketValue,
        String assignmentReason,
        Instant assignedAt,
        boolean trainingEligible
    ) {
    }

    public record StoredAuditLog(
        UUID id,
        String targetType,
        String targetKey,
        String action,
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
}
