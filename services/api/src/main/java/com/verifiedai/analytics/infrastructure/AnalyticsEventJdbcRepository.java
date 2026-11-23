package com.verifiedai.analytics.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
 * JDBC persistence repository for Sprint 10.6 privacy-safe analytics events,
 * GDPR export/deletion, and anonymized daily metric rollups.
 */
@Repository
public class AnalyticsEventJdbcRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().findAndAddModules().build();

    private final JdbcClient jdbcClient;

    public AnalyticsEventJdbcRepository(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient must not be null");
    }

    public void lockUserClientEvent(UUID userId, String clientEventId) {
        String lockKey = "anl-evt:" + userId + ":" + clientEventId;
        jdbcClient.sql("SELECT pg_advisory_xact_lock(hashtextextended(:lockKey, 1060))")
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

    public String resolveUserEntitlementTier(UUID userId) {
        Optional<String> tier = jdbcClient.sql(
                """
                SELECT tier
                FROM entitlements
                WHERE user_id = :userId
                  AND status IN ('ACTIVE', 'GRACE_PERIOD', 'BILLING_RETRY')
                ORDER BY updated_at DESC
                LIMIT 1
                """
            )
            .param("userId", userId, Types.OTHER)
            .query(String.class)
            .optional();
        if (tier.isEmpty()) {
            return "FREE";
        }
        return switch (tier.get()) {
            case "FREE", "PRO", "COACH_PRO", "YKS_SEASON_PASS", "UNKNOWN" -> tier.get();
            default -> "UNKNOWN";
        };
    }

    public Optional<StoredAnalyticsEvent> findByUserIdAndClientEventId(UUID userId, String clientEventId) {
        return jdbcClient.sql(
                """
                SELECT id, user_id, client_event_id, payload_hash, event_name, event_domain,
                       catalog_version, emitted_by, source_module, entitlement_tier, locale,
                       app_version, session_ref_hash, latency_ms, ai_cost_micros,
                       properties_json::text AS properties_json_text, correlation_id,
                       occurred_at, received_at, training_eligible
                FROM analytics_events
                WHERE user_id = :userId AND client_event_id = :clientEventId
                """
            )
            .param("userId", userId, Types.OTHER)
            .param("clientEventId", clientEventId, Types.VARCHAR)
            .query((rs, rowNum) -> mapRow(rs))
            .optional();
    }

    public void insertEvent(StoredAnalyticsEvent event) {
        jdbcClient.sql(
                """
                INSERT INTO analytics_events (
                    id, user_id, client_event_id, payload_hash, event_name, event_domain,
                    catalog_version, emitted_by, source_module, entitlement_tier, locale,
                    app_version, session_ref_hash, latency_ms, ai_cost_micros, properties_json,
                    correlation_id, occurred_at, received_at, training_eligible
                ) VALUES (
                    :id, :userId, :clientEventId, :payloadHash, :eventName, :eventDomain,
                    :catalogVersion, :emittedBy, :sourceModule, :entitlementTier, :locale,
                    :appVersion, :sessionRefHash, :latencyMs, :aiCostMicros, CAST(:propertiesJson AS jsonb),
                    :correlationId, :occurredAt, :receivedAt, FALSE
                )
                """
            )
            .param("id", event.id(), Types.OTHER)
            .param("userId", event.userId(), Types.OTHER)
            .param("clientEventId", event.clientEventId(), Types.VARCHAR)
            .param("payloadHash", event.payloadHash(), Types.CHAR)
            .param("eventName", event.eventName(), Types.VARCHAR)
            .param("eventDomain", event.eventDomain(), Types.VARCHAR)
            .param("catalogVersion", event.catalogVersion(), Types.VARCHAR)
            .param("emittedBy", event.emittedBy(), Types.VARCHAR)
            .param("sourceModule", event.sourceModule(), Types.VARCHAR)
            .param("entitlementTier", event.entitlementTier(), Types.VARCHAR)
            .param("locale", event.locale(), Types.VARCHAR)
            .param("appVersion", event.appVersion(), Types.VARCHAR)
            .param("sessionRefHash", event.sessionRefHash(), Types.CHAR)
            .param("latencyMs", event.latencyMs(), Types.INTEGER)
            .param("aiCostMicros", event.aiCostMicros(), Types.BIGINT)
            .param("propertiesJson", event.propertiesJson(), Types.VARCHAR)
            .param("correlationId", event.correlationId(), Types.VARCHAR)
            .param("occurredAt", Timestamp.from(event.occurredAt()), Types.TIMESTAMP)
            .param("receivedAt", Timestamp.from(event.receivedAt()), Types.TIMESTAMP)
            .update();
    }

    /**
     * Bounded GDPR export (INV-ANL-006): excludes operational payload_hash, session_ref_hash,
     * and correlation_id, returning strictly PII-free event metadata.
     */
    public List<Map<String, Object>> findRecentByUserIdForExport(UUID userId, int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 200));
        return jdbcClient.sql(
                """
                SELECT id, client_event_id, event_name, event_domain, catalog_version,
                       emitted_by, source_module, entitlement_tier, locale, app_version,
                       latency_ms, ai_cost_micros, properties_json::text AS properties_json_text,
                       occurred_at, received_at
                FROM analytics_events
                WHERE user_id = :userId
                ORDER BY occurred_at DESC, id DESC
                LIMIT :limit
                """
            )
            .param("userId", userId, Types.OTHER)
            .param("limit", boundedLimit, Types.INTEGER)
            .query((rs, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("eventId", rs.getObject("id", UUID.class).toString());
                row.put("clientEventId", rs.getString("client_event_id"));
                row.put("eventName", rs.getString("event_name"));
                row.put("eventDomain", rs.getString("event_domain"));
                row.put("catalogVersion", rs.getString("catalog_version"));
                row.put("emittedBy", rs.getString("emitted_by"));
                row.put("sourceModule", rs.getString("source_module"));
                row.put("entitlementTier", rs.getString("entitlement_tier"));
                row.put("locale", rs.getString("locale"));
                row.put("appVersion", rs.getString("app_version"));
                Integer latencyMs = rs.getObject("latency_ms", Integer.class);
                row.put("latencyMs", latencyMs);
                row.put("aiCostMicros", rs.getLong("ai_cost_micros"));
                row.put("properties", parseProperties(rs.getString("properties_json_text")));
                row.put("occurredAt", rs.getTimestamp("occurred_at").toInstant().toString());
                row.put("receivedAt", rs.getTimestamp("received_at").toInstant().toString());
                row.put("trainingEligible", false);
                return Collections.unmodifiableMap(row);
            })
            .list();
    }

    public int deleteByUserId(UUID userId) {
        return jdbcClient.sql("DELETE FROM analytics_events WHERE user_id = :userId")
            .param("userId", userId, Types.OTHER)
            .update();
    }

    public void refreshDailyRollups(LocalDate metricDate, Instant computedAt) {
        Objects.requireNonNull(metricDate, "metricDate must not be null");
        Objects.requireNonNull(computedAt, "computedAt must not be null");
        Instant startTs = metricDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endTs = metricDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        jdbcClient.sql(
                """
                INSERT INTO analytics_daily_metric_rollups (
                    metric_date, entitlement_tier, event_domain, event_name,
                    event_count, distinct_user_count, total_ai_cost_micros,
                    p50_latency_ms, p95_latency_ms, computed_at, training_eligible
                )
                SELECT
                    :metricDate,
                    entitlement_tier,
                    event_domain,
                    event_name,
                    COUNT(*)::bigint AS event_count,
                    COUNT(DISTINCT user_id)::bigint AS distinct_user_count,
                    COALESCE(SUM(ai_cost_micros), 0)::bigint AS total_ai_cost_micros,
                    ROUND(CAST(percentile_cont(0.50) WITHIN GROUP (ORDER BY latency_ms) AS numeric))::integer AS p50_latency_ms,
                    ROUND(CAST(percentile_cont(0.95) WITHIN GROUP (ORDER BY latency_ms) AS numeric))::integer AS p95_latency_ms,
                    :computedAt,
                    FALSE
                FROM analytics_events
                WHERE occurred_at >= :startTs AND occurred_at < :endTs
                GROUP BY entitlement_tier, event_domain, event_name
                ON CONFLICT (metric_date, entitlement_tier, event_domain, event_name) DO UPDATE SET
                    event_count = EXCLUDED.event_count,
                    distinct_user_count = EXCLUDED.distinct_user_count,
                    total_ai_cost_micros = EXCLUDED.total_ai_cost_micros,
                    p50_latency_ms = EXCLUDED.p50_latency_ms,
                    p95_latency_ms = EXCLUDED.p95_latency_ms,
                    computed_at = EXCLUDED.computed_at,
                    training_eligible = FALSE
                """
            )
            .param("metricDate", Date.valueOf(metricDate), Types.DATE)
            .param("computedAt", Timestamp.from(computedAt), Types.TIMESTAMP)
            .param("startTs", Timestamp.from(startTs), Types.TIMESTAMP)
            .param("endTs", Timestamp.from(endTs), Types.TIMESTAMP)
            .update();

        jdbcClient.sql(
                """
                INSERT INTO analytics_daily_metric_rollups (
                    metric_date, entitlement_tier, event_domain, event_name,
                    event_count, distinct_user_count, total_ai_cost_micros,
                    p50_latency_ms, p95_latency_ms, computed_at, training_eligible
                )
                SELECT
                    :metricDate,
                    'ALL',
                    event_domain,
                    event_name,
                    COUNT(*)::bigint AS event_count,
                    COUNT(DISTINCT user_id)::bigint AS distinct_user_count,
                    COALESCE(SUM(ai_cost_micros), 0)::bigint AS total_ai_cost_micros,
                    ROUND(CAST(percentile_cont(0.50) WITHIN GROUP (ORDER BY latency_ms) AS numeric))::integer AS p50_latency_ms,
                    ROUND(CAST(percentile_cont(0.95) WITHIN GROUP (ORDER BY latency_ms) AS numeric))::integer AS p95_latency_ms,
                    :computedAt,
                    FALSE
                FROM analytics_events
                WHERE occurred_at >= :startTs AND occurred_at < :endTs
                GROUP BY event_domain, event_name
                ON CONFLICT (metric_date, entitlement_tier, event_domain, event_name) DO UPDATE SET
                    event_count = EXCLUDED.event_count,
                    distinct_user_count = EXCLUDED.distinct_user_count,
                    total_ai_cost_micros = EXCLUDED.total_ai_cost_micros,
                    p50_latency_ms = EXCLUDED.p50_latency_ms,
                    p95_latency_ms = EXCLUDED.p95_latency_ms,
                    computed_at = EXCLUDED.computed_at,
                    training_eligible = FALSE
                """
            )
            .param("metricDate", Date.valueOf(metricDate), Types.DATE)
            .param("computedAt", Timestamp.from(computedAt), Types.TIMESTAMP)
            .param("startTs", Timestamp.from(startTs), Types.TIMESTAMP)
            .param("endTs", Timestamp.from(endTs), Types.TIMESTAMP)
            .update();
    }

    private static Map<String, Object> parseProperties(String jsonText) {
        if (jsonText == null || jsonText.isBlank()) {
            return Map.of();
        }
        try {
            return Collections.unmodifiableMap(JSON_MAPPER.readValue(jsonText, MAP_TYPE));
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private static StoredAnalyticsEvent mapRow(ResultSet rs) throws SQLException {
        return new StoredAnalyticsEvent(
            rs.getObject("id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getString("client_event_id"),
            rs.getString("payload_hash"),
            rs.getString("event_name"),
            rs.getString("event_domain"),
            rs.getString("catalog_version"),
            rs.getString("emitted_by"),
            rs.getString("source_module"),
            rs.getString("entitlement_tier"),
            rs.getString("locale"),
            rs.getString("app_version"),
            rs.getString("session_ref_hash"),
            rs.getObject("latency_ms", Integer.class),
            rs.getLong("ai_cost_micros"),
            rs.getString("properties_json_text"),
            rs.getString("correlation_id"),
            rs.getTimestamp("occurred_at").toInstant(),
            rs.getTimestamp("received_at").toInstant(),
            rs.getBoolean("training_eligible")
        );
    }

    public record StoredAnalyticsEvent(
        UUID id,
        UUID userId,
        String clientEventId,
        String payloadHash,
        String eventName,
        String eventDomain,
        String catalogVersion,
        String emittedBy,
        String sourceModule,
        String entitlementTier,
        String locale,
        String appVersion,
        String sessionRefHash,
        Integer latencyMs,
        long aiCostMicros,
        String propertiesJson,
        String correlationId,
        Instant occurredAt,
        Instant receivedAt,
        boolean trainingEligible
    ) {
    }
}
