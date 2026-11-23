package com.verifiedai.notification.infrastructure;

import com.verifiedai.notification.domain.LifecycleEvaluationDecision;
import com.verifiedai.notification.domain.NotificationCategory;
import com.verifiedai.notification.domain.NotificationIntentType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC persistence repository for Sprint 10.5 lifecycle messaging cooldowns and evaluation audit records.
 */
@Repository
public class LifecycleMessagingJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public LifecycleMessagingJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void lockLifecycleKey(UUID userId, NotificationCategory category, NotificationIntentType intentType) {
        String lockKey = "notif-life:" + userId + ":" + category.name() + ":" + intentType.name();
        jdbcTemplate.query(
            "select pg_advisory_xact_lock(hashtextextended(?, 1050))",
            preparedStatement -> preparedStatement.setString(1, lockKey),
            resultSet -> {
            }
        );
    }

    public Optional<CooldownRecord> findCooldown(
        UUID userId,
        NotificationCategory category,
        NotificationIntentType intentType
    ) {
        List<CooldownRecord> rows = jdbcTemplate.query(
            """
            select user_id, category, intent_type, last_idempotency_key, last_delivery_id,
                   last_triggered_at, cooldown_until, version, training_eligible, created_at, updated_at
            from lifecycle_messaging_cooldowns
            where user_id = ? and category = ? and intent_type = ?
            """,
            (rs, rowNum) -> mapCooldown(rs),
            userId,
            category.name(),
            intentType.name()
        );
        return rows.stream().findFirst();
    }

    public List<CooldownRecord> findCooldownsByUserId(UUID userId) {
        return jdbcTemplate.query(
            """
            select user_id, category, intent_type, last_idempotency_key, last_delivery_id,
                   last_triggered_at, cooldown_until, version, training_eligible, created_at, updated_at
            from lifecycle_messaging_cooldowns
            where user_id = ?
            order by cooldown_until desc, category asc, intent_type asc
            """,
            (rs, rowNum) -> mapCooldown(rs),
            userId
        );
    }

    public void upsertCooldown(
        UUID userId,
        NotificationCategory category,
        NotificationIntentType intentType,
        String lastIdempotencyKey,
        UUID lastDeliveryId,
        Instant lastTriggeredAt,
        Instant cooldownUntil,
        Instant now
    ) {
        jdbcTemplate.update(
            """
            insert into lifecycle_messaging_cooldowns (
                user_id, category, intent_type, last_idempotency_key, last_delivery_id,
                last_triggered_at, cooldown_until, version, training_eligible, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, 0, false, ?, ?)
            on conflict (user_id, category, intent_type) do update set
                last_idempotency_key = excluded.last_idempotency_key,
                last_delivery_id = excluded.last_delivery_id,
                last_triggered_at = excluded.last_triggered_at,
                cooldown_until = excluded.cooldown_until,
                version = lifecycle_messaging_cooldowns.version + 1,
                updated_at = excluded.updated_at
            """,
            userId,
            category.name(),
            intentType.name(),
            lastIdempotencyKey,
            lastDeliveryId,
            Timestamp.from(lastTriggeredAt),
            Timestamp.from(cooldownUntil),
            Timestamp.from(now),
            Timestamp.from(now)
        );
    }

    public void insertEvaluation(EvaluationRecord record) {
        jdbcTemplate.update(
            """
            insert into lifecycle_messaging_evaluations (
                id, user_id, source_module, category, intent_type, idempotency_key,
                template_key, decision, reason_code, delivery_id, correlation_id,
                training_eligible, evaluated_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, false, ?)
            """,
            record.id(),
            record.userId(),
            record.sourceModule(),
            record.category().name(),
            record.intentType().name(),
            record.idempotencyKey(),
            record.templateKey(),
            record.decision().name(),
            record.reasonCode(),
            record.deliveryId(),
            record.correlationId(),
            Timestamp.from(record.evaluatedAt())
        );
    }

    public List<EvaluationRecord> findRecentEvaluationsByUserId(UUID userId, int limit) {
        return jdbcTemplate.query(
            """
            select id, user_id, source_module, category, intent_type, idempotency_key,
                   template_key, decision, reason_code, delivery_id, correlation_id,
                   training_eligible, evaluated_at
            from lifecycle_messaging_evaluations
            where user_id = ?
            order by evaluated_at desc, id desc
            limit ?
            """,
            (rs, rowNum) -> mapEvaluation(rs),
            userId,
            limit
        );
    }

    public List<String> findPendingIdempotencyKeysByUserAndCategory(
        UUID userId,
        NotificationCategory category,
        NotificationIntentType intentType
    ) {
        return jdbcTemplate.queryForList(
            """
            select idempotency_key
            from notification_deliveries
            where user_id = ?
              and category = ?
              and intent_type = ?
              and status in ('SCHEDULED', 'DEFERRED_QUIET_HOURS', 'RETRY_SCHEDULED')
            order by created_at asc, id asc
            """,
            String.class,
            userId,
            category.name(),
            intentType.name()
        );
    }

    public List<Map<String, Object>> findCooldownsByUserIdForExport(UUID userId) {
        return jdbcTemplate.query(
            """
            select category, intent_type, last_idempotency_key, last_delivery_id,
                   last_triggered_at, cooldown_until, version, updated_at
            from lifecycle_messaging_cooldowns
            where user_id = ?
            order by cooldown_until desc, category asc, intent_type asc
            """,
            (rs, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("category", rs.getString("category"));
                row.put("intentType", rs.getString("intent_type"));
                row.put("lastIdempotencyKey", rs.getString("last_idempotency_key"));
                UUID deliveryId = rs.getObject("last_delivery_id", UUID.class);
                row.put("lastDeliveryId", deliveryId == null ? null : deliveryId.toString());
                row.put("lastTriggeredAt", rs.getTimestamp("last_triggered_at").toInstant().toString());
                row.put("cooldownUntil", rs.getTimestamp("cooldown_until").toInstant().toString());
                row.put("version", rs.getLong("version"));
                row.put("updatedAt", rs.getTimestamp("updated_at").toInstant().toString());
                row.put("trainingEligible", false);
                return row;
            },
            userId
        );
    }

    public List<Map<String, Object>> findRecentEvaluationsByUserIdForExport(UUID userId, int limit) {
        return jdbcTemplate.query(
            """
            select id, source_module, category, intent_type, idempotency_key,
                   template_key, decision, reason_code, delivery_id, evaluated_at
            from lifecycle_messaging_evaluations
            where user_id = ?
            order by evaluated_at desc, id desc
            limit ?
            """,
            (rs, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("evaluationId", rs.getObject("id", UUID.class).toString());
                row.put("sourceModule", rs.getString("source_module"));
                row.put("category", rs.getString("category"));
                row.put("intentType", rs.getString("intent_type"));
                row.put("idempotencyKey", rs.getString("idempotency_key"));
                row.put("templateKey", rs.getString("template_key"));
                row.put("decision", rs.getString("decision"));
                row.put("reasonCode", rs.getString("reason_code"));
                UUID deliveryId = rs.getObject("delivery_id", UUID.class);
                row.put("deliveryId", deliveryId == null ? null : deliveryId.toString());
                row.put("evaluatedAt", rs.getTimestamp("evaluated_at").toInstant().toString());
                row.put("trainingEligible", false);
                return row;
            },
            userId,
            limit
        );
    }

    public int deleteEvaluationsByUserId(UUID userId) {
        return jdbcTemplate.update("delete from lifecycle_messaging_evaluations where user_id = ?", userId);
    }

    public int deleteCooldownsByUserId(UUID userId) {
        return jdbcTemplate.update("delete from lifecycle_messaging_cooldowns where user_id = ?", userId);
    }

    private static CooldownRecord mapCooldown(ResultSet rs) throws SQLException {
        return new CooldownRecord(
            rs.getObject("user_id", UUID.class),
            NotificationCategory.valueOf(rs.getString("category")),
            NotificationIntentType.valueOf(rs.getString("intent_type")),
            rs.getString("last_idempotency_key"),
            rs.getObject("last_delivery_id", UUID.class),
            rs.getTimestamp("last_triggered_at").toInstant(),
            rs.getTimestamp("cooldown_until").toInstant(),
            rs.getLong("version"),
            rs.getBoolean("training_eligible"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
        );
    }

    private static EvaluationRecord mapEvaluation(ResultSet rs) throws SQLException {
        return new EvaluationRecord(
            rs.getObject("id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getString("source_module"),
            NotificationCategory.valueOf(rs.getString("category")),
            NotificationIntentType.valueOf(rs.getString("intent_type")),
            rs.getString("idempotency_key"),
            rs.getString("template_key"),
            LifecycleEvaluationDecision.valueOf(rs.getString("decision")),
            rs.getString("reason_code"),
            rs.getObject("delivery_id", UUID.class),
            rs.getString("correlation_id"),
            rs.getBoolean("training_eligible"),
            rs.getTimestamp("evaluated_at").toInstant()
        );
    }

    public record CooldownRecord(
        UUID userId,
        NotificationCategory category,
        NotificationIntentType intentType,
        String lastIdempotencyKey,
        UUID lastDeliveryId,
        Instant lastTriggeredAt,
        Instant cooldownUntil,
        long version,
        boolean trainingEligible,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record EvaluationRecord(
        UUID id,
        UUID userId,
        String sourceModule,
        NotificationCategory category,
        NotificationIntentType intentType,
        String idempotencyKey,
        String templateKey,
        LifecycleEvaluationDecision decision,
        String reasonCode,
        UUID deliveryId,
        String correlationId,
        boolean trainingEligible,
        Instant evaluatedAt
    ) {
    }
}
