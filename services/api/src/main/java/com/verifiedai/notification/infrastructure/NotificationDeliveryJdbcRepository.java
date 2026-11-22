package com.verifiedai.notification.infrastructure;

import com.verifiedai.notification.domain.NotificationCategory;
import com.verifiedai.notification.domain.NotificationChannel;
import com.verifiedai.notification.domain.NotificationDeliveryStateMachine;
import com.verifiedai.notification.domain.NotificationDeliveryStatus;
import com.verifiedai.notification.domain.NotificationIntentType;
import com.verifiedai.notification.domain.NotificationSuppressionReason;
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

@Repository
public class NotificationDeliveryJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public NotificationDeliveryJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void lockUser(UUID userId) {
        jdbcTemplate.query(
            "select pg_advisory_xact_lock(hashtextextended(?, 1044))",
            preparedStatement -> preparedStatement.setString(1, userId.toString()),
            resultSet -> {
            }
        );
    }

    public Optional<DeliveryRecord> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey) {
        List<DeliveryRecord> rows = jdbcTemplate.query(
            BASE_SELECT + " where user_id = ? and idempotency_key = ?",
            (rs, rowNum) -> mapDelivery(rs),
            userId,
            idempotencyKey
        );
        return rows.stream().findFirst();
    }

    public Optional<DeliveryRecord> findByIdAndUserId(UUID deliveryId, UUID userId) {
        List<DeliveryRecord> rows = jdbcTemplate.query(
            BASE_SELECT + " where id = ? and user_id = ?",
            (rs, rowNum) -> mapDelivery(rs),
            deliveryId,
            userId
        );
        return rows.stream().findFirst();
    }

    public Optional<DeliveryRecord> findByIdAndUserIdForUpdate(UUID deliveryId, UUID userId) {
        List<DeliveryRecord> rows = jdbcTemplate.query(
            BASE_SELECT + " where id = ? and user_id = ? for update",
            (rs, rowNum) -> mapDelivery(rs),
            deliveryId,
            userId
        );
        return rows.stream().findFirst();
    }

    public int countSentNonSecurityInWindow(UUID userId, Instant startInclusive, Instant endExclusive) {
        Integer count = jdbcTemplate.queryForObject(
            """
            select count(*)
            from notification_deliveries
            where user_id = ?
              and status = 'SENT'
              and category <> 'ACCOUNT_SECURITY'
              and sent_at >= ?
              and sent_at < ?
            """,
            Integer.class,
            userId,
            Timestamp.from(startInclusive),
            Timestamp.from(endExclusive)
        );
        return count == null ? 0 : count;
    }

    public void insertDeliveryAndEvent(
        DeliveryRecord record,
        String eventReasonCode
    ) {
        NotificationDeliveryStateMachine.assertTransition(null, record.status());
        jdbcTemplate.update(
            """
            insert into notification_deliveries (
                id, user_id, idempotency_key, payload_hash, category, intent_type, channel,
                locale, template_key, template_params_json, deep_link_path, status,
                suppression_reason, failure_code, scheduled_for, effective_send_at, expires_at,
                attempt_count, max_attempts, locked_until, sent_at, provider_message_id,
                correlation_id, source_module, version, training_eligible, created_at, updated_at
            ) values (
                ?, ?, ?, ?, ?, ?, ?,
                ?, ?, ?::jsonb, ?, ?,
                ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?,
                ?, ?, ?, false, ?, ?
            )
            """,
            record.id(),
            record.userId(),
            record.idempotencyKey(),
            record.payloadHash(),
            record.category().name(),
            record.intentType().name(),
            record.channel().name(),
            record.locale(),
            record.templateKey(),
            record.templateParamsJson(),
            record.deepLinkPath(),
            record.status().name(),
            record.suppressionReason() == null ? null : record.suppressionReason().name(),
            record.failureCode(),
            Timestamp.from(record.scheduledFor()),
            Timestamp.from(record.effectiveSendAt()),
            Timestamp.from(record.expiresAt()),
            record.attemptCount(),
            record.maxAttempts(),
            record.lockedUntil() == null ? null : Timestamp.from(record.lockedUntil()),
            record.sentAt() == null ? null : Timestamp.from(record.sentAt()),
            record.providerMessageId(),
            record.correlationId(),
            record.sourceModule(),
            record.version(),
            Timestamp.from(record.createdAt()),
            Timestamp.from(record.updatedAt())
        );

        insertEvent(
            record.id(),
            record.userId(),
            null,
            record.status(),
            eventReasonCode,
            record.correlationId(),
            record.createdAt()
        );
    }

    public List<DeliveryRecord> claimDueBatch(Instant now, Instant lockedUntil, int batchSize) {
        List<DeliveryRecord> candidates = jdbcTemplate.query(
            BASE_SELECT + """
             where status in ('SCHEDULED', 'DEFERRED_QUIET_HOURS', 'RETRY_SCHEDULED')
               and effective_send_at <= ?
             order by effective_send_at asc, id asc
             for update skip locked
             limit ?
            """,
            (rs, rowNum) -> mapDelivery(rs),
            Timestamp.from(now),
            batchSize
        );
        for (DeliveryRecord candidate : candidates) {
            NotificationDeliveryStateMachine.assertTransition(candidate.status(), NotificationDeliveryStatus.DISPATCHING);
            jdbcTemplate.update(
                """
                update notification_deliveries
                   set status = 'DISPATCHING',
                       locked_until = ?,
                       version = version + 1,
                       updated_at = ?
                 where id = ?
                """,
                Timestamp.from(lockedUntil),
                Timestamp.from(now),
                candidate.id()
            );
            insertEvent(
                candidate.id(),
                candidate.userId(),
                candidate.status(),
                NotificationDeliveryStatus.DISPATCHING,
                "CLAIMED_BY_WORKER",
                candidate.correlationId(),
                now
            );
        }
        return candidates.stream()
            .map(c -> c.withStatus(NotificationDeliveryStatus.DISPATCHING, lockedUntil, now))
            .toList();
    }

    public int recoverStaleLocks(Instant now, int batchSize) {
        List<DeliveryRecord> stale = jdbcTemplate.query(
            BASE_SELECT + """
             where status = 'DISPATCHING'
               and locked_until <= ?
             order by locked_until asc, id asc
             for update skip locked
             limit ?
            """,
            (rs, rowNum) -> mapDelivery(rs),
            Timestamp.from(now),
            batchSize
        );
        for (DeliveryRecord record : stale) {
            NotificationDeliveryStateMachine.assertTransition(record.status(), NotificationDeliveryStatus.SCHEDULED);
            jdbcTemplate.update(
                """
                update notification_deliveries
                   set status = 'SCHEDULED',
                       locked_until = null,
                       version = version + 1,
                       updated_at = ?
                 where id = ?
                """,
                Timestamp.from(now),
                record.id()
            );
            insertEvent(
                record.id(),
                record.userId(),
                record.status(),
                NotificationDeliveryStatus.SCHEDULED,
                "STALE_LOCK_RECOVERED",
                record.correlationId(),
                now
            );
        }
        return stale.size();
    }

    public void markSent(
        DeliveryRecord record,
        int attemptCount,
        String providerMessageId,
        Instant now
    ) {
        NotificationDeliveryStateMachine.assertTransition(record.status(), NotificationDeliveryStatus.SENT);
        jdbcTemplate.update(
            """
            update notification_deliveries
               set status = 'SENT',
                   attempt_count = ?,
                   sent_at = ?,
                   provider_message_id = ?,
                   failure_code = null,
                   locked_until = null,
                   version = version + 1,
                   updated_at = ?
             where id = ?
            """,
            attemptCount,
            Timestamp.from(now),
            providerMessageId,
            Timestamp.from(now),
            record.id()
        );
        insertEvent(
            record.id(),
            record.userId(),
            record.status(),
            NotificationDeliveryStatus.SENT,
            "DELIVERY_SENT",
            record.correlationId(),
            now
        );
    }

    public void markRetryScheduled(
        DeliveryRecord record,
        int attemptCount,
        Instant nextEffectiveSendAt,
        String failureCode,
        Instant now
    ) {
        NotificationDeliveryStateMachine.assertTransition(record.status(), NotificationDeliveryStatus.RETRY_SCHEDULED);
        jdbcTemplate.update(
            """
            update notification_deliveries
               set status = 'RETRY_SCHEDULED',
                   attempt_count = ?,
                   effective_send_at = ?,
                   failure_code = ?,
                   locked_until = null,
                   version = version + 1,
                   updated_at = ?
             where id = ?
            """,
            attemptCount,
            Timestamp.from(nextEffectiveSendAt),
            failureCode,
            Timestamp.from(now),
            record.id()
        );
        insertEvent(
            record.id(),
            record.userId(),
            record.status(),
            NotificationDeliveryStatus.RETRY_SCHEDULED,
            "RETRY_SCHEDULED",
            record.correlationId(),
            now
        );
    }

    public void markFailedPermanent(
        DeliveryRecord record,
        int attemptCount,
        String failureCode,
        String reasonCode,
        Instant now
    ) {
        NotificationDeliveryStateMachine.assertTransition(record.status(), NotificationDeliveryStatus.FAILED_PERMANENT);
        jdbcTemplate.update(
            """
            update notification_deliveries
               set status = 'FAILED_PERMANENT',
                   attempt_count = ?,
                   failure_code = ?,
                   locked_until = null,
                   version = version + 1,
                   updated_at = ?
             where id = ?
            """,
            attemptCount,
            failureCode,
            Timestamp.from(now),
            record.id()
        );
        insertEvent(
            record.id(),
            record.userId(),
            record.status(),
            NotificationDeliveryStatus.FAILED_PERMANENT,
            reasonCode,
            record.correlationId(),
            now
        );
    }

    public void markSuppressed(
        DeliveryRecord record,
        int attemptCount,
        NotificationSuppressionReason suppressionReason,
        String failureCode,
        Instant now
    ) {
        NotificationDeliveryStateMachine.assertTransition(record.status(), NotificationDeliveryStatus.SUPPRESSED);
        jdbcTemplate.update(
            """
            update notification_deliveries
               set status = 'SUPPRESSED',
                   attempt_count = ?,
                   suppression_reason = ?,
                   failure_code = ?,
                   locked_until = null,
                   version = version + 1,
                   updated_at = ?
             where id = ?
            """,
            attemptCount,
            suppressionReason.name(),
            failureCode,
            Timestamp.from(now),
            record.id()
        );
        insertEvent(
            record.id(),
            record.userId(),
            record.status(),
            NotificationDeliveryStatus.SUPPRESSED,
            suppressionReason.name(),
            record.correlationId(),
            now
        );
    }

    public void markDeferredFromDispatching(
        DeliveryRecord record,
        Instant deferredSendAt,
        Instant now
    ) {
        NotificationDeliveryStateMachine.assertTransition(record.status(), NotificationDeliveryStatus.SCHEDULED);
        NotificationDeliveryStateMachine.assertTransition(
            NotificationDeliveryStatus.SCHEDULED,
            NotificationDeliveryStatus.DEFERRED_QUIET_HOURS
        );
        jdbcTemplate.update(
            """
            update notification_deliveries
               set status = 'DEFERRED_QUIET_HOURS',
                   effective_send_at = ?,
                   locked_until = null,
                   version = version + 1,
                   updated_at = ?
             where id = ?
            """,
            Timestamp.from(deferredSendAt),
            Timestamp.from(now),
            record.id()
        );
        insertEvent(
            record.id(),
            record.userId(),
            record.status(),
            NotificationDeliveryStatus.SCHEDULED,
            "DISPATCH_REQUEUED_FOR_QUIET_HOURS",
            record.correlationId(),
            now
        );
        insertEvent(
            record.id(),
            record.userId(),
            NotificationDeliveryStatus.SCHEDULED,
            NotificationDeliveryStatus.DEFERRED_QUIET_HOURS,
            "QUIET_HOURS_DEFERRED",
            record.correlationId(),
            now
        );
    }

    public void markCancelled(
        DeliveryRecord record,
        String reasonCode,
        String correlationId,
        Instant now
    ) {
        NotificationDeliveryStateMachine.assertTransition(record.status(), NotificationDeliveryStatus.CANCELLED);
        jdbcTemplate.update(
            """
            update notification_deliveries
               set status = 'CANCELLED',
                   locked_until = null,
                   version = version + 1,
                   updated_at = ?
             where id = ?
            """,
            Timestamp.from(now),
            record.id()
        );
        insertEvent(
            record.id(),
            record.userId(),
            record.status(),
            NotificationDeliveryStatus.CANCELLED,
            reasonCode,
            correlationId,
            now
        );
    }

    public List<DeliveryRecord> findRecentByUserId(UUID userId, int limit) {
        return jdbcTemplate.query(
            BASE_SELECT + " where user_id = ? order by created_at desc, id desc limit ?",
            (rs, rowNum) -> mapDelivery(rs),
            userId,
            limit
        );
    }

    public List<Map<String, Object>> findRecentByUserIdForExport(UUID userId, int limit) {
        return jdbcTemplate.query(
            """
            select id, category, intent_type, channel, locale, deep_link_path,
                   status, suppression_reason, scheduled_for, effective_send_at,
                   expires_at, sent_at, created_at
            from notification_deliveries
            where user_id = ?
            order by created_at desc, id desc
            limit ?
            """,
            (rs, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("deliveryId", rs.getObject("id", UUID.class).toString());
                row.put("category", rs.getString("category"));
                row.put("intentType", rs.getString("intent_type"));
                row.put("channel", rs.getString("channel"));
                row.put("locale", rs.getString("locale"));
                row.put("deepLinkPath", rs.getString("deep_link_path"));
                row.put("status", rs.getString("status"));
                row.put("suppressionReason", rs.getString("suppression_reason"));
                row.put("scheduledFor", instantString(rs.getTimestamp("scheduled_for")));
                row.put("effectiveSendAt", instantString(rs.getTimestamp("effective_send_at")));
                row.put("expiresAt", instantString(rs.getTimestamp("expires_at")));
                row.put("sentAt", instantString(rs.getTimestamp("sent_at")));
                row.put("createdAt", instantString(rs.getTimestamp("created_at")));
                return row;
            },
            userId,
            limit
        );
    }

    public int deleteEventsByUserId(UUID userId) {
        return jdbcTemplate.update("delete from notification_delivery_events where user_id = ?", userId);
    }

    public int deleteDeliveriesByUserId(UUID userId) {
        return jdbcTemplate.update("delete from notification_deliveries where user_id = ?", userId);
    }

    private void insertEvent(
        UUID deliveryId,
        UUID userId,
        NotificationDeliveryStatus fromStatus,
        NotificationDeliveryStatus toStatus,
        String reasonCode,
        String correlationId,
        Instant occurredAt
    ) {
        jdbcTemplate.update(
            """
            insert into notification_delivery_events (
                id, delivery_id, user_id, from_status, to_status, reason_code,
                correlation_id, training_eligible, occurred_at
            ) values (?, ?, ?, ?, ?, ?, ?, false, ?)
            """,
            UUID.randomUUID(),
            deliveryId,
            userId,
            fromStatus == null ? null : fromStatus.name(),
            toStatus.name(),
            reasonCode,
            correlationId,
            Timestamp.from(occurredAt)
        );
    }

    private static DeliveryRecord mapDelivery(ResultSet rs) throws SQLException {
        String suppression = rs.getString("suppression_reason");
        Timestamp lockedUntil = rs.getTimestamp("locked_until");
        Timestamp sentAt = rs.getTimestamp("sent_at");
        return new DeliveryRecord(
            rs.getObject("id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getString("idempotency_key"),
            rs.getString("payload_hash"),
            NotificationCategory.valueOf(rs.getString("category")),
            NotificationIntentType.valueOf(rs.getString("intent_type")),
            NotificationChannel.valueOf(rs.getString("channel")),
            rs.getString("locale"),
            rs.getString("template_key"),
            rs.getString("template_params_json"),
            rs.getString("deep_link_path"),
            NotificationDeliveryStatus.valueOf(rs.getString("status")),
            suppression == null ? null : NotificationSuppressionReason.valueOf(suppression),
            rs.getString("failure_code"),
            rs.getTimestamp("scheduled_for").toInstant(),
            rs.getTimestamp("effective_send_at").toInstant(),
            rs.getTimestamp("expires_at").toInstant(),
            rs.getInt("attempt_count"),
            rs.getInt("max_attempts"),
            lockedUntil == null ? null : lockedUntil.toInstant(),
            sentAt == null ? null : sentAt.toInstant(),
            rs.getString("provider_message_id"),
            rs.getString("correlation_id"),
            rs.getString("source_module"),
            rs.getLong("version"),
            rs.getBoolean("training_eligible"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
        );
    }

    private static String instantString(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant().toString();
    }

    private static final String BASE_SELECT = """
        select id, user_id, idempotency_key, payload_hash, category, intent_type, channel, locale,
               template_key, template_params_json::text as template_params_json, deep_link_path,
               status, suppression_reason, failure_code, scheduled_for, effective_send_at, expires_at,
               attempt_count, max_attempts, locked_until, sent_at, provider_message_id,
               correlation_id, source_module, version, training_eligible, created_at, updated_at
        from notification_deliveries
        """;

    public record DeliveryRecord(
        UUID id,
        UUID userId,
        String idempotencyKey,
        String payloadHash,
        NotificationCategory category,
        NotificationIntentType intentType,
        NotificationChannel channel,
        String locale,
        String templateKey,
        String templateParamsJson,
        String deepLinkPath,
        NotificationDeliveryStatus status,
        NotificationSuppressionReason suppressionReason,
        String failureCode,
        Instant scheduledFor,
        Instant effectiveSendAt,
        Instant expiresAt,
        int attemptCount,
        int maxAttempts,
        Instant lockedUntil,
        Instant sentAt,
        String providerMessageId,
        String correlationId,
        String sourceModule,
        long version,
        boolean trainingEligible,
        Instant createdAt,
        Instant updatedAt
    ) {
        DeliveryRecord withStatus(NotificationDeliveryStatus newStatus, Instant newLockedUntil, Instant now) {
            return new DeliveryRecord(
                id,
                userId,
                idempotencyKey,
                payloadHash,
                category,
                intentType,
                channel,
                locale,
                templateKey,
                templateParamsJson,
                deepLinkPath,
                newStatus,
                suppressionReason,
                failureCode,
                scheduledFor,
                effectiveSendAt,
                expiresAt,
                attemptCount,
                maxAttempts,
                newLockedUntil,
                sentAt,
                providerMessageId,
                correlationId,
                sourceModule,
                version + 1,
                trainingEligible,
                createdAt,
                now
            );
        }
    }
}
