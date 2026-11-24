package com.verifiedai.growth.infrastructure;

import com.verifiedai.growth.domain.ReferralAttributionSource;
import com.verifiedai.growth.domain.ReferralAttributionStatus;
import com.verifiedai.growth.domain.ShareableArtifactStatus;
import com.verifiedai.growth.domain.ShareableArtifactType;
import com.verifiedai.growth.domain.StoreReviewOutcome;
import com.verifiedai.growth.domain.StoreReviewTriggerMoment;
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
 * Persistence repository strictly scoped to V073 growth tables:
 * growth_referral_codes, growth_referral_attributions, growth_shareable_artifacts,
 * and growth_store_review_events (INV-GRW-002, INV-GRW-003, INV-GRW-006).
 */
@Repository
public class GrowthWriteJdbcRepository {

    private final JdbcClient jdbcClient;

    public GrowthWriteJdbcRepository(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient must not be null");
    }

    public void acquireAdvisoryLock(String lockKey) {
        jdbcClient.sql("SELECT pg_advisory_xact_lock(hashtextextended(:lockKey, 1090))")
            .param("lockKey", lockKey, Types.VARCHAR)
            .query(rs -> {
            });
    }

    // --- Referral Codes ---

    public Optional<StoredReferralCode> findReferralCodeByUserId(UUID userId) {
        return jdbcClient.sql(
                """
                SELECT id, user_id, referral_code, active, version, training_eligible, created_at, updated_at
                FROM growth_referral_codes
                WHERE user_id = :userId
                """
            )
            .param("userId", userId, Types.OTHER)
            .query(this::mapReferralCode)
            .optional();
    }

    public Optional<StoredReferralCode> findReferralCodeByCode(String referralCode) {
        return jdbcClient.sql(
                """
                SELECT id, user_id, referral_code, active, version, training_eligible, created_at, updated_at
                FROM growth_referral_codes
                WHERE referral_code = :referralCode
                """
            )
            .param("referralCode", referralCode, Types.VARCHAR)
            .query(this::mapReferralCode)
            .optional();
    }

    public void insertReferralCode(StoredReferralCode row) {
        jdbcClient.sql(
                """
                INSERT INTO growth_referral_codes (
                    id, user_id, referral_code, active, version, training_eligible, created_at, updated_at
                ) VALUES (
                    :id, :userId, :referralCode, :active, :version, FALSE, :createdAt, :updatedAt
                )
                """
            )
            .param("id", row.id(), Types.OTHER)
            .param("userId", row.userId(), Types.OTHER)
            .param("referralCode", row.referralCode(), Types.VARCHAR)
            .param("active", row.active(), Types.BOOLEAN)
            .param("version", row.version(), Types.BIGINT)
            .param("createdAt", Timestamp.from(row.createdAt()), Types.TIMESTAMP)
            .param("updatedAt", Timestamp.from(row.updatedAt()), Types.TIMESTAMP)
            .update();
    }

    // --- Referral Attributions ---

    public Optional<StoredReferralAttribution> findAttributionByReferredUserId(UUID referredUserId) {
        return jdbcClient.sql(
                """
                SELECT id, referral_code_id, referrer_user_id, referred_user_id, referral_code_snapshot,
                       idempotency_key, payload_hash, device_fingerprint_hash, attribution_source, status,
                       abuse_reason_code, reward_type, reward_units, correlation_id, version,
                       training_eligible, attributed_at, qualified_at, reward_granted_at, updated_at
                FROM growth_referral_attributions
                WHERE referred_user_id = :referredUserId
                """
            )
            .param("referredUserId", referredUserId, Types.OTHER)
            .query(this::mapReferralAttribution)
            .optional();
    }

    public Optional<StoredReferralAttribution> findAttributionByReferredUserAndIdempotencyKey(
        UUID referredUserId,
        String idempotencyKey
    ) {
        return jdbcClient.sql(
                """
                SELECT id, referral_code_id, referrer_user_id, referred_user_id, referral_code_snapshot,
                       idempotency_key, payload_hash, device_fingerprint_hash, attribution_source, status,
                       abuse_reason_code, reward_type, reward_units, correlation_id, version,
                       training_eligible, attributed_at, qualified_at, reward_granted_at, updated_at
                FROM growth_referral_attributions
                WHERE referred_user_id = :referredUserId
                  AND idempotency_key = :idempotencyKey
                """
            )
            .param("referredUserId", referredUserId, Types.OTHER)
            .param("idempotencyKey", idempotencyKey, Types.VARCHAR)
            .query(this::mapReferralAttribution)
            .optional();
    }

    public boolean existsAttributionBetween(UUID referrerUserId, UUID referredUserId) {
        Boolean exists = jdbcClient.sql(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM growth_referral_attributions
                    WHERE referrer_user_id = :referrerUserId
                      AND referred_user_id = :referredUserId
                )
                """
            )
            .param("referrerUserId", referrerUserId, Types.OTHER)
            .param("referredUserId", referredUserId, Types.OTHER)
            .query(Boolean.class)
            .single();
        return Boolean.TRUE.equals(exists);
    }

    public boolean existsAttributionForReferrerAndDevice(UUID referrerUserId, String deviceHash) {
        Boolean exists = jdbcClient.sql(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM growth_referral_attributions
                    WHERE referrer_user_id = :referrerUserId
                      AND device_fingerprint_hash = :deviceHash
                )
                """
            )
            .param("referrerUserId", referrerUserId, Types.OTHER)
            .param("deviceHash", deviceHash, Types.CHAR)
            .query(Boolean.class)
            .single();
        return Boolean.TRUE.equals(exists);
    }

    public long countAttributionsByDeviceHash(String deviceHash) {
        Long count = jdbcClient.sql(
                """
                SELECT COUNT(*)
                FROM growth_referral_attributions
                WHERE device_fingerprint_hash = :deviceHash
                """
            )
            .param("deviceHash", deviceHash, Types.CHAR)
            .query(Long.class)
            .single();
        return count != null ? count : 0L;
    }

    public long countReferrerAttributionsSince(UUID referrerUserId, Instant since) {
        Long count = jdbcClient.sql(
                """
                SELECT COUNT(*)
                FROM growth_referral_attributions
                WHERE referrer_user_id = :referrerUserId
                  AND attributed_at >= :since
                  AND status <> 'REJECTED_ABUSE'
                """
            )
            .param("referrerUserId", referrerUserId, Types.OTHER)
            .param("since", Timestamp.from(since), Types.TIMESTAMP)
            .query(Long.class)
            .single();
        return count != null ? count : 0L;
    }

    public long countReferrerRewardsGrantedSince(UUID referrerUserId, Instant since) {
        Long count = jdbcClient.sql(
                """
                SELECT COUNT(*)
                FROM growth_referral_attributions
                WHERE referrer_user_id = :referrerUserId
                  AND status = 'REWARD_GRANTED'
                  AND reward_granted_at >= :since
                """
            )
            .param("referrerUserId", referrerUserId, Types.OTHER)
            .param("since", Timestamp.from(since), Types.TIMESTAMP)
            .query(Long.class)
            .single();
        return count != null ? count : 0L;
    }

    public ReferrerAggregateStats summarizeReferrerStats(UUID referrerUserId) {
        return jdbcClient.sql(
                """
                SELECT
                    COUNT(*) FILTER (WHERE status <> 'REJECTED_ABUSE') AS total_attributions,
                    COUNT(*) FILTER (WHERE status IN ('QUALIFIED', 'REWARD_GRANTED')) AS qualified_count,
                    COUNT(*) FILTER (WHERE status = 'REWARD_GRANTED') AS rewarded_count,
                    COALESCE(SUM(reward_units) FILTER (WHERE status = 'REWARD_GRANTED'), 0) AS total_reward_credits
                FROM growth_referral_attributions
                WHERE referrer_user_id = :referrerUserId
                """
            )
            .param("referrerUserId", referrerUserId, Types.OTHER)
            .query((rs, rowNum) -> new ReferrerAggregateStats(
                rs.getInt("total_attributions"),
                rs.getInt("qualified_count"),
                rs.getInt("rewarded_count"),
                rs.getInt("total_reward_credits")
            ))
            .single();
    }

    public void insertAttribution(StoredReferralAttribution row) {
        jdbcClient.sql(
                """
                INSERT INTO growth_referral_attributions (
                    id, referral_code_id, referrer_user_id, referred_user_id, referral_code_snapshot,
                    idempotency_key, payload_hash, device_fingerprint_hash, attribution_source, status,
                    abuse_reason_code, reward_type, reward_units, correlation_id, version,
                    training_eligible, attributed_at, qualified_at, reward_granted_at, updated_at
                ) VALUES (
                    :id, :referralCodeId, :referrerUserId, :referredUserId, :referralCodeSnapshot,
                    :idempotencyKey, :payloadHash, :deviceFingerprintHash, :attributionSource, :status,
                    :abuseReasonCode, :rewardType, :rewardUnits, :correlationId, :version,
                    FALSE, :attributedAt, :qualifiedAt, :rewardGrantedAt, :updatedAt
                )
                """
            )
            .param("id", row.id(), Types.OTHER)
            .param("referralCodeId", row.referralCodeId(), Types.OTHER)
            .param("referrerUserId", row.referrerUserId(), Types.OTHER)
            .param("referredUserId", row.referredUserId(), Types.OTHER)
            .param("referralCodeSnapshot", row.referralCodeSnapshot(), Types.VARCHAR)
            .param("idempotencyKey", row.idempotencyKey(), Types.VARCHAR)
            .param("payloadHash", row.payloadHash(), Types.CHAR)
            .param("deviceFingerprintHash", row.deviceFingerprintHash(), Types.CHAR)
            .param("attributionSource", row.attributionSource().name(), Types.VARCHAR)
            .param("status", row.status().name(), Types.VARCHAR)
            .param("abuseReasonCode", row.abuseReasonCode(), Types.VARCHAR)
            .param("rewardType", row.rewardType(), Types.VARCHAR)
            .param("rewardUnits", row.rewardUnits(), Types.INTEGER)
            .param("correlationId", row.correlationId(), Types.VARCHAR)
            .param("version", row.version(), Types.BIGINT)
            .param("attributedAt", Timestamp.from(row.attributedAt()), Types.TIMESTAMP)
            .param("qualifiedAt", toTimestamp(row.qualifiedAt()), Types.TIMESTAMP)
            .param("rewardGrantedAt", toTimestamp(row.rewardGrantedAt()), Types.TIMESTAMP)
            .param("updatedAt", Timestamp.from(row.updatedAt()), Types.TIMESTAMP)
            .update();
    }

    public int updateAttributionStatusAndReward(
        UUID id,
        long expectedVersion,
        ReferralAttributionStatus nextStatus,
        String abuseReasonCode,
        String rewardType,
        int rewardUnits,
        Instant qualifiedAt,
        Instant rewardGrantedAt,
        Instant updatedAt
    ) {
        return jdbcClient.sql(
                """
                UPDATE growth_referral_attributions
                SET status = :status,
                    abuse_reason_code = :abuseReasonCode,
                    reward_type = :rewardType,
                    reward_units = :rewardUnits,
                    qualified_at = :qualifiedAt,
                    reward_granted_at = :rewardGrantedAt,
                    updated_at = :updatedAt,
                    version = version + 1
                WHERE id = :id
                  AND version = :expectedVersion
                """
            )
            .param("status", nextStatus.name(), Types.VARCHAR)
            .param("abuseReasonCode", abuseReasonCode, Types.VARCHAR)
            .param("rewardType", rewardType, Types.VARCHAR)
            .param("rewardUnits", rewardUnits, Types.INTEGER)
            .param("qualifiedAt", toTimestamp(qualifiedAt), Types.TIMESTAMP)
            .param("rewardGrantedAt", toTimestamp(rewardGrantedAt), Types.TIMESTAMP)
            .param("updatedAt", Timestamp.from(updatedAt), Types.TIMESTAMP)
            .param("id", id, Types.OTHER)
            .param("expectedVersion", expectedVersion, Types.BIGINT)
            .update();
    }

    // --- Shareable Artifacts ---

    public Optional<StoredShareableArtifact> findShareByUserAndIdempotencyKey(UUID userId, String idempotencyKey) {
        return jdbcClient.sql(
                """
                SELECT id, user_id, share_token, idempotency_key, payload_hash, artifact_type, locale,
                       display_alias, referral_code, summary_payload_json::text AS summary_payload_json,
                       deep_link_url, status, raw_content_redacted, open_count, correlation_id,
                       version, training_eligible, expires_at, revoked_at, created_at, updated_at
                FROM growth_shareable_artifacts
                WHERE user_id = :userId
                  AND idempotency_key = :idempotencyKey
                """
            )
            .param("userId", userId, Types.OTHER)
            .param("idempotencyKey", idempotencyKey, Types.VARCHAR)
            .query(this::mapShareableArtifact)
            .optional();
    }

    public Optional<StoredShareableArtifact> findShareByIdAndUserId(UUID shareId, UUID userId) {
        return jdbcClient.sql(
                """
                SELECT id, user_id, share_token, idempotency_key, payload_hash, artifact_type, locale,
                       display_alias, referral_code, summary_payload_json::text AS summary_payload_json,
                       deep_link_url, status, raw_content_redacted, open_count, correlation_id,
                       version, training_eligible, expires_at, revoked_at, created_at, updated_at
                FROM growth_shareable_artifacts
                WHERE id = :shareId
                  AND user_id = :userId
                """
            )
            .param("shareId", shareId, Types.OTHER)
            .param("userId", userId, Types.OTHER)
            .query(this::mapShareableArtifact)
            .optional();
    }

    public Optional<StoredShareableArtifact> findShareByToken(String shareToken) {
        return jdbcClient.sql(
                """
                SELECT id, user_id, share_token, idempotency_key, payload_hash, artifact_type, locale,
                       display_alias, referral_code, summary_payload_json::text AS summary_payload_json,
                       deep_link_url, status, raw_content_redacted, open_count, correlation_id,
                       version, training_eligible, expires_at, revoked_at, created_at, updated_at
                FROM growth_shareable_artifacts
                WHERE share_token = :shareToken
                """
            )
            .param("shareToken", shareToken, Types.VARCHAR)
            .query(this::mapShareableArtifact)
            .optional();
    }

    public long countActiveSharesForUser(UUID userId, Instant now) {
        Long count = jdbcClient.sql(
                """
                SELECT COUNT(*)
                FROM growth_shareable_artifacts
                WHERE user_id = :userId
                  AND status = 'ACTIVE'
                  AND expires_at > :now
                """
            )
            .param("userId", userId, Types.OTHER)
            .param("now", Timestamp.from(now), Types.TIMESTAMP)
            .query(Long.class)
            .single();
        return count != null ? count : 0L;
    }

    public List<StoredShareableArtifact> listSharesForUser(UUID userId, int limit) {
        return jdbcClient.sql(
                """
                SELECT id, user_id, share_token, idempotency_key, payload_hash, artifact_type, locale,
                       display_alias, referral_code, summary_payload_json::text AS summary_payload_json,
                       deep_link_url, status, raw_content_redacted, open_count, correlation_id,
                       version, training_eligible, expires_at, revoked_at, created_at, updated_at
                FROM growth_shareable_artifacts
                WHERE user_id = :userId
                ORDER BY created_at DESC, id DESC
                LIMIT :limit
                """
            )
            .param("userId", userId, Types.OTHER)
            .param("limit", limit, Types.INTEGER)
            .query(this::mapShareableArtifact)
            .list();
    }

    public void insertShare(StoredShareableArtifact row) {
        jdbcClient.sql(
                """
                INSERT INTO growth_shareable_artifacts (
                    id, user_id, share_token, idempotency_key, payload_hash, artifact_type, locale,
                    display_alias, referral_code, summary_payload_json, deep_link_url, status,
                    raw_content_redacted, open_count, correlation_id, version, training_eligible,
                    expires_at, revoked_at, created_at, updated_at
                ) VALUES (
                    :id, :userId, :shareToken, :idempotencyKey, :payloadHash, :artifactType, :locale,
                    :displayAlias, :referralCode, CAST(:summaryPayloadJson AS jsonb), :deepLinkUrl, :status,
                    TRUE, :openCount, :correlationId, :version, FALSE,
                    :expiresAt, :revokedAt, :createdAt, :updatedAt
                )
                """
            )
            .param("id", row.id(), Types.OTHER)
            .param("userId", row.userId(), Types.OTHER)
            .param("shareToken", row.shareToken(), Types.VARCHAR)
            .param("idempotencyKey", row.idempotencyKey(), Types.VARCHAR)
            .param("payloadHash", row.payloadHash(), Types.CHAR)
            .param("artifactType", row.artifactType().name(), Types.VARCHAR)
            .param("locale", row.locale(), Types.VARCHAR)
            .param("displayAlias", row.displayAlias(), Types.VARCHAR)
            .param("referralCode", row.referralCode(), Types.VARCHAR)
            .param("summaryPayloadJson", row.summaryPayloadJson(), Types.VARCHAR)
            .param("deepLinkUrl", row.deepLinkUrl(), Types.VARCHAR)
            .param("status", row.status().name(), Types.VARCHAR)
            .param("openCount", row.openCount(), Types.BIGINT)
            .param("correlationId", row.correlationId(), Types.VARCHAR)
            .param("version", row.version(), Types.BIGINT)
            .param("expiresAt", Timestamp.from(row.expiresAt()), Types.TIMESTAMP)
            .param("revokedAt", toTimestamp(row.revokedAt()), Types.TIMESTAMP)
            .param("createdAt", Timestamp.from(row.createdAt()), Types.TIMESTAMP)
            .param("updatedAt", Timestamp.from(row.updatedAt()), Types.TIMESTAMP)
            .update();
    }

    public int revokeShare(UUID shareId, UUID userId, long expectedVersion, Instant now) {
        return jdbcClient.sql(
                """
                UPDATE growth_shareable_artifacts
                SET status = 'REVOKED',
                    revoked_at = :now,
                    updated_at = :now,
                    version = version + 1
                WHERE id = :shareId
                  AND user_id = :userId
                  AND version = :expectedVersion
                  AND status = 'ACTIVE'
                """
            )
            .param("now", Timestamp.from(now), Types.TIMESTAMP)
            .param("shareId", shareId, Types.OTHER)
            .param("userId", userId, Types.OTHER)
            .param("expectedVersion", expectedVersion, Types.BIGINT)
            .update();
    }

    public void markShareExpired(UUID shareId, Instant now) {
        jdbcClient.sql(
                """
                UPDATE growth_shareable_artifacts
                SET status = 'EXPIRED',
                    updated_at = :now,
                    version = version + 1
                WHERE id = :shareId
                  AND status = 'ACTIVE'
                """
            )
            .param("now", Timestamp.from(now), Types.TIMESTAMP)
            .param("shareId", shareId, Types.OTHER)
            .update();
    }

    public void incrementShareOpenCount(UUID shareId, Instant now) {
        jdbcClient.sql(
                """
                UPDATE growth_shareable_artifacts
                SET open_count = open_count + 1,
                    updated_at = :now
                WHERE id = :shareId
                  AND status = 'ACTIVE'
                """
            )
            .param("now", Timestamp.from(now), Types.TIMESTAMP)
            .param("shareId", shareId, Types.OTHER)
            .update();
    }

    // --- Store Review Events ---

    public Optional<StoredReviewEvent> findReviewEventByUserAndIdempotencyKey(UUID userId, String idempotencyKey) {
        return jdbcClient.sql(
                """
                SELECT id, user_id, idempotency_key, payload_hash, trigger_moment, outcome,
                       app_version, locale, correlation_id, training_eligible, occurred_at
                FROM growth_store_review_events
                WHERE user_id = :userId
                  AND idempotency_key = :idempotencyKey
                """
            )
            .param("userId", userId, Types.OTHER)
            .param("idempotencyKey", idempotencyKey, Types.VARCHAR)
            .query(this::mapReviewEvent)
            .optional();
    }

    public boolean hasPermanentDecline(UUID userId) {
        Boolean exists = jdbcClient.sql(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM growth_store_review_events
                    WHERE user_id = :userId
                      AND outcome = 'PROMPT_DECLINED_PERMANENTLY'
                )
                """
            )
            .param("userId", userId, Types.OTHER)
            .query(Boolean.class)
            .single();
        return Boolean.TRUE.equals(exists);
    }

    public int countPromptsShownSince(UUID userId, Instant since) {
        Integer count = jdbcClient.sql(
                """
                SELECT COUNT(*)
                FROM growth_store_review_events
                WHERE user_id = :userId
                  AND outcome = 'PROMPT_SHOWN'
                  AND occurred_at >= :since
                """
            )
            .param("userId", userId, Types.OTHER)
            .param("since", Timestamp.from(since), Types.TIMESTAMP)
            .query(Integer.class)
            .single();
        return count != null ? count : 0;
    }

    public Optional<Instant> findLatestShownOrDismissedAt(UUID userId) {
        return jdbcClient.sql(
                """
                SELECT occurred_at
                FROM growth_store_review_events
                WHERE user_id = :userId
                  AND outcome IN ('PROMPT_SHOWN', 'PROMPT_DISMISSED')
                ORDER BY occurred_at DESC
                LIMIT 1
                """
            )
            .param("userId", userId, Types.OTHER)
            .query((rs, rowNum) -> {
                Timestamp ts = rs.getTimestamp("occurred_at");
                return ts != null ? ts.toInstant() : null;
            })
            .optional()
            .filter(Objects::nonNull);
    }

    public void insertReviewEvent(StoredReviewEvent row) {
        jdbcClient.sql(
                """
                INSERT INTO growth_store_review_events (
                    id, user_id, idempotency_key, payload_hash, trigger_moment, outcome,
                    app_version, locale, correlation_id, training_eligible, occurred_at
                ) VALUES (
                    :id, :userId, :idempotencyKey, :payloadHash, :triggerMoment, :outcome,
                    :appVersion, :locale, :correlationId, FALSE, :occurredAt
                )
                """
            )
            .param("id", row.id(), Types.OTHER)
            .param("userId", row.userId(), Types.OTHER)
            .param("idempotencyKey", row.idempotencyKey(), Types.VARCHAR)
            .param("payloadHash", row.payloadHash(), Types.CHAR)
            .param("triggerMoment", row.triggerMoment().name(), Types.VARCHAR)
            .param("outcome", row.outcome().name(), Types.VARCHAR)
            .param("appVersion", row.appVersion(), Types.VARCHAR)
            .param("locale", row.locale(), Types.VARCHAR)
            .param("correlationId", row.correlationId(), Types.VARCHAR)
            .param("occurredAt", Timestamp.from(row.occurredAt()), Types.TIMESTAMP)
            .update();
    }

    // --- GDPR Privacy Export & Deletion (INV-GRW-006) ---

    public List<Map<String, Object>> findShareableArtifactsForPrivacyExport(UUID userId) {
        return jdbcClient.sql(
                """
                SELECT id, artifact_type, locale, status, raw_content_redacted, open_count,
                       expires_at, revoked_at, created_at
                FROM growth_shareable_artifacts
                WHERE user_id = :userId
                ORDER BY created_at DESC, id DESC
                LIMIT 200
                """
            )
            .param("userId", userId, Types.OTHER)
            .query((rs, rowNum) -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("shareId", rs.getObject("id", UUID.class).toString());
                item.put("artifactType", rs.getString("artifact_type"));
                item.put("locale", rs.getString("locale"));
                item.put("status", rs.getString("status"));
                item.put("rawContentRedacted", rs.getBoolean("raw_content_redacted"));
                item.put("openCount", rs.getLong("open_count"));
                item.put("expiresAt", toIso(rs.getTimestamp("expires_at")));
                item.put("revokedAt", toIso(rs.getTimestamp("revoked_at")));
                item.put("createdAt", toIso(rs.getTimestamp("created_at")));
                item.put("trainingEligible", false);
                return Collections.unmodifiableMap(item);
            })
            .list();
    }

    public List<Map<String, Object>> findReviewEventsForPrivacyExport(UUID userId) {
        return jdbcClient.sql(
                """
                SELECT id, trigger_moment, outcome, app_version, locale, occurred_at
                FROM growth_store_review_events
                WHERE user_id = :userId
                ORDER BY occurred_at DESC, id DESC
                LIMIT 100
                """
            )
            .param("userId", userId, Types.OTHER)
            .query((rs, rowNum) -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("eventId", rs.getObject("id", UUID.class).toString());
                item.put("triggerMoment", rs.getString("trigger_moment"));
                item.put("outcome", rs.getString("outcome"));
                item.put("appVersion", rs.getString("app_version"));
                item.put("locale", rs.getString("locale"));
                item.put("occurredAt", toIso(rs.getTimestamp("occurred_at")));
                item.put("trainingEligible", false);
                return Collections.unmodifiableMap(item);
            })
            .list();
    }

    public void deleteAllUserGrowthData(UUID userId) {
        jdbcClient.sql("DELETE FROM growth_store_review_events WHERE user_id = :userId")
            .param("userId", userId, Types.OTHER)
            .update();
        jdbcClient.sql("DELETE FROM growth_shareable_artifacts WHERE user_id = :userId")
            .param("userId", userId, Types.OTHER)
            .update();
        jdbcClient.sql(
                """
                DELETE FROM growth_referral_attributions
                WHERE referrer_user_id = :userId
                   OR referred_user_id = :userId
                """
            )
            .param("userId", userId, Types.OTHER)
            .update();
        jdbcClient.sql("DELETE FROM growth_referral_codes WHERE user_id = :userId")
            .param("userId", userId, Types.OTHER)
            .update();
    }

    // --- Row Mappers & Storage Records ---

    private StoredReferralCode mapReferralCode(ResultSet rs, int rowNum) throws SQLException {
        return new StoredReferralCode(
            rs.getObject("id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getString("referral_code"),
            rs.getBoolean("active"),
            rs.getLong("version"),
            rs.getBoolean("training_eligible"),
            toInstant(rs.getTimestamp("created_at")),
            toInstant(rs.getTimestamp("updated_at"))
        );
    }

    private StoredReferralAttribution mapReferralAttribution(ResultSet rs, int rowNum) throws SQLException {
        return new StoredReferralAttribution(
            rs.getObject("id", UUID.class),
            rs.getObject("referral_code_id", UUID.class),
            rs.getObject("referrer_user_id", UUID.class),
            rs.getObject("referred_user_id", UUID.class),
            rs.getString("referral_code_snapshot"),
            rs.getString("idempotency_key"),
            rs.getString("payload_hash"),
            rs.getString("device_fingerprint_hash"),
            ReferralAttributionSource.valueOf(rs.getString("attribution_source")),
            ReferralAttributionStatus.valueOf(rs.getString("status")),
            rs.getString("abuse_reason_code"),
            rs.getString("reward_type"),
            rs.getInt("reward_units"),
            rs.getString("correlation_id"),
            rs.getLong("version"),
            rs.getBoolean("training_eligible"),
            toInstant(rs.getTimestamp("attributed_at")),
            toInstant(rs.getTimestamp("qualified_at")),
            toInstant(rs.getTimestamp("reward_granted_at")),
            toInstant(rs.getTimestamp("updated_at"))
        );
    }

    private StoredShareableArtifact mapShareableArtifact(ResultSet rs, int rowNum) throws SQLException {
        return new StoredShareableArtifact(
            rs.getObject("id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getString("share_token"),
            rs.getString("idempotency_key"),
            rs.getString("payload_hash"),
            ShareableArtifactType.valueOf(rs.getString("artifact_type")),
            rs.getString("locale"),
            rs.getString("display_alias"),
            rs.getString("referral_code"),
            rs.getString("summary_payload_json"),
            rs.getString("deep_link_url"),
            ShareableArtifactStatus.valueOf(rs.getString("status")),
            rs.getBoolean("raw_content_redacted"),
            rs.getLong("open_count"),
            rs.getString("correlation_id"),
            rs.getLong("version"),
            rs.getBoolean("training_eligible"),
            toInstant(rs.getTimestamp("expires_at")),
            toInstant(rs.getTimestamp("revoked_at")),
            toInstant(rs.getTimestamp("created_at")),
            toInstant(rs.getTimestamp("updated_at"))
        );
    }

    private StoredReviewEvent mapReviewEvent(ResultSet rs, int rowNum) throws SQLException {
        return new StoredReviewEvent(
            rs.getObject("id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getString("idempotency_key"),
            rs.getString("payload_hash"),
            StoreReviewTriggerMoment.valueOf(rs.getString("trigger_moment")),
            StoreReviewOutcome.valueOf(rs.getString("outcome")),
            rs.getString("app_version"),
            rs.getString("locale"),
            rs.getString("correlation_id"),
            rs.getBoolean("training_eligible"),
            toInstant(rs.getTimestamp("occurred_at"))
        );
    }

    private static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : null;
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant != null ? Timestamp.from(instant) : null;
    }

    private static String toIso(Timestamp ts) {
        return ts != null ? ts.toInstant().toString() : null;
    }

    public record StoredReferralCode(
        UUID id,
        UUID userId,
        String referralCode,
        boolean active,
        long version,
        boolean trainingEligible,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record StoredReferralAttribution(
        UUID id,
        UUID referralCodeId,
        UUID referrerUserId,
        UUID referredUserId,
        String referralCodeSnapshot,
        String idempotencyKey,
        String payloadHash,
        String deviceFingerprintHash,
        ReferralAttributionSource attributionSource,
        ReferralAttributionStatus status,
        String abuseReasonCode,
        String rewardType,
        int rewardUnits,
        String correlationId,
        long version,
        boolean trainingEligible,
        Instant attributedAt,
        Instant qualifiedAt,
        Instant rewardGrantedAt,
        Instant updatedAt
    ) {
    }

    public record ReferrerAggregateStats(
        int totalAttributions,
        int qualifiedCount,
        int rewardedCount,
        int totalRewardCredits
    ) {
    }

    public record StoredShareableArtifact(
        UUID id,
        UUID userId,
        String shareToken,
        String idempotencyKey,
        String payloadHash,
        ShareableArtifactType artifactType,
        String locale,
        String displayAlias,
        String referralCode,
        String summaryPayloadJson,
        String deepLinkUrl,
        ShareableArtifactStatus status,
        boolean rawContentRedacted,
        long openCount,
        String correlationId,
        long version,
        boolean trainingEligible,
        Instant expiresAt,
        Instant revokedAt,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record StoredReviewEvent(
        UUID id,
        UUID userId,
        String idempotencyKey,
        String payloadHash,
        StoreReviewTriggerMoment triggerMoment,
        StoreReviewOutcome outcome,
        String appVersion,
        String locale,
        String correlationId,
        boolean trainingEligible,
        Instant occurredAt
    ) {
    }
}
