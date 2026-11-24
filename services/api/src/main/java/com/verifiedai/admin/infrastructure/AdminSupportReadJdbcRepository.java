package com.verifiedai.admin.infrastructure;

import com.verifiedai.admin.domain.AdminLookupType;
import com.verifiedai.admin.domain.AdminRedactionPolicy;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Strictly read-only JDBC projection repository (INV-ADM-003 & INV-ADM-007).
 * Executes ONLY SELECT queries across existing platform tables and never mutates foreign-owned state.
 */
@Repository
public class AdminSupportReadJdbcRepository {

    private final JdbcClient jdbcClient;

    public AdminSupportReadJdbcRepository(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient must not be null");
    }

    public Optional<UUID> resolveUserId(AdminLookupType type, String value) {
        return switch (type) {
            case USER_ID -> parseUuid(value).flatMap(id -> jdbcClient.sql("SELECT id FROM users WHERE id = :id")
                .param("id", id, Types.OTHER)
                .query(UUID.class)
                .optional());
            case SESSION_ID -> parseUuid(value).flatMap(sid -> jdbcClient.sql("SELECT user_id FROM sessions WHERE id = :sid")
                .param("sid", sid, Types.OTHER)
                .query(UUID.class)
                .optional());
            case PROBLEM_SESSION_ID -> parseUuid(value).flatMap(psid -> jdbcClient.sql("SELECT user_id FROM problem_sessions WHERE id = :psid")
                .param("psid", psid, Types.OTHER)
                .query(UUID.class)
                .optional());
            case ORIGINAL_TRANSACTION_ID -> {
                Optional<UUID> fromSubs = jdbcClient.sql(
                        """
                        SELECT user_id
                        FROM app_store_subscriptions
                        WHERE original_transaction_id = :txId
                        ORDER BY updated_at DESC
                        LIMIT 1
                        """
                    )
                    .param("txId", value, Types.VARCHAR)
                    .query(UUID.class)
                    .optional();
                if (fromSubs.isPresent()) {
                    yield fromSubs;
                }
                yield jdbcClient.sql(
                        """
                        SELECT user_id
                        FROM entitlements
                        WHERE original_transaction_id = :txId
                        ORDER BY updated_at DESC
                        LIMIT 1
                        """
                    )
                    .param("txId", value, Types.VARCHAR)
                    .query(UUID.class)
                    .optional();
            }
        };
    }

    public Optional<UserAccountRow> findUserAccount(UUID userId) {
        return jdbcClient.sql(
                """
                SELECT id, status, created_at, updated_at, deletion_requested_at, deleted_at
                FROM users
                WHERE id = :userId
                """
            )
            .param("userId", userId, Types.OTHER)
            .query((rs, rowNum) -> new UserAccountRow(
                rs.getObject("id", UUID.class),
                rs.getString("status"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at")),
                toInstant(rs.getTimestamp("deletion_requested_at")),
                toInstant(rs.getTimestamp("deleted_at"))
            ))
            .optional();
    }

    public boolean isAccountActive(UUID userId) {
        Optional<String> status = jdbcClient.sql("SELECT status FROM users WHERE id = :userId")
            .param("userId", userId, Types.OTHER)
            .query(String.class)
            .optional();
        return status.map("ACTIVE"::equals).orElse(false);
    }

    public List<UserSessionMetadataRow> findRecentSessions(UUID userId, int limit) {
        return jdbcClient.sql(
                """
                SELECT id, status, created_at, last_seen_at, expires_at, revoked_at, revocation_reason
                FROM sessions
                WHERE user_id = :userId
                ORDER BY created_at DESC
                LIMIT :limit
                """
            )
            .param("userId", userId, Types.OTHER)
            .param("limit", limit, Types.INTEGER)
            .query((rs, rowNum) -> new UserSessionMetadataRow(
                rs.getObject("id", UUID.class),
                rs.getString("status"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("last_seen_at")),
                toInstant(rs.getTimestamp("expires_at")),
                toInstant(rs.getTimestamp("revoked_at")),
                AdminRedactionPolicy.redactString(rs.getString("revocation_reason"))
            ))
            .list();
    }

    public Optional<EntitlementSnapshotRow> findUserEntitlement(UUID userId) {
        return jdbcClient.sql(
                """
                SELECT id, tier, package_code, source, status, effective_at, expires_at,
                       grace_period_expires_at, original_transaction_id, environment,
                       last_verified_at, updated_at, version
                FROM entitlements
                WHERE user_id = :userId
                ORDER BY updated_at DESC
                LIMIT 1
                """
            )
            .param("userId", userId, Types.OTHER)
            .query((rs, rowNum) -> new EntitlementSnapshotRow(
                rs.getObject("id", UUID.class),
                rs.getString("tier"),
                rs.getString("package_code"),
                rs.getString("source"),
                rs.getString("status"),
                toInstant(rs.getTimestamp("effective_at")),
                toInstant(rs.getTimestamp("expires_at")),
                toInstant(rs.getTimestamp("grace_period_expires_at")),
                rs.getString("original_transaction_id"),
                rs.getString("environment"),
                toInstant(rs.getTimestamp("last_verified_at")),
                toInstant(rs.getTimestamp("updated_at")),
                rs.getLong("version")
            ))
            .optional();
    }

    public List<QuotaLedgerRow> findQuotaLedgers(UUID userId, int limit) {
        return jdbcClient.sql(
                """
                SELECT id, period_key, tier_code, ai_verifications_used, ai_verifications_limit,
                       hybrid_twins_used, hybrid_twins_limit, accumulated_ai_cost_micros,
                       accumulated_ai_cost_usd, ai_cost_cap_micros, cost_circuit_breaker_tripped,
                       resets_at, updated_at
                FROM entitlement_quota_ledgers
                WHERE user_id = :userId
                ORDER BY updated_at DESC
                LIMIT :limit
                """
            )
            .param("userId", userId, Types.OTHER)
            .param("limit", limit, Types.INTEGER)
            .query((rs, rowNum) -> {
                BigDecimal usd = rs.getBigDecimal("accumulated_ai_cost_usd");
                return new QuotaLedgerRow(
                    rs.getObject("id", UUID.class),
                    rs.getString("period_key"),
                    rs.getString("tier_code"),
                    rs.getInt("ai_verifications_used"),
                    rs.getInt("ai_verifications_limit"),
                    rs.getInt("hybrid_twins_used"),
                    rs.getInt("hybrid_twins_limit"),
                    rs.getLong("accumulated_ai_cost_micros"),
                    usd != null ? usd.doubleValue() : rs.getLong("accumulated_ai_cost_micros") / 1_000_000.0,
                    rs.getLong("ai_cost_cap_micros"),
                    rs.getBoolean("cost_circuit_breaker_tripped"),
                    toInstant(rs.getTimestamp("resets_at")),
                    toInstant(rs.getTimestamp("updated_at"))
                );
            })
            .list();
    }

    public List<AppStoreSubscriptionRow> findAppStoreSubscriptions(UUID userId, int limit) {
        return jdbcClient.sql(
                """
                SELECT id, original_transaction_id, current_transaction_id, product_id, environment,
                       status, auto_renew_status, expires_at, grace_period_expires_at,
                       revoked_at, last_reconciled_at, updated_at
                FROM app_store_subscriptions
                WHERE user_id = :userId
                ORDER BY updated_at DESC
                LIMIT :limit
                """
            )
            .param("userId", userId, Types.OTHER)
            .param("limit", limit, Types.INTEGER)
            .query((rs, rowNum) -> new AppStoreSubscriptionRow(
                rs.getObject("id", UUID.class),
                rs.getString("original_transaction_id"),
                rs.getString("current_transaction_id"),
                rs.getString("product_id"),
                rs.getString("environment"),
                rs.getString("status"),
                rs.getObject("auto_renew_status", Boolean.class),
                toInstant(rs.getTimestamp("expires_at")),
                toInstant(rs.getTimestamp("grace_period_expires_at")),
                toInstant(rs.getTimestamp("revoked_at")),
                toInstant(rs.getTimestamp("last_reconciled_at")),
                toInstant(rs.getTimestamp("updated_at"))
            ))
            .list();
    }

    public List<AppStoreTransactionRow> findAppStoreTransactions(UUID userId, int limit) {
        return jdbcClient.sql(
                """
                SELECT id, transaction_id, original_transaction_id, product_id, environment,
                       purchase_date, expires_date, revocation_date, transaction_reason,
                       ownership_type, access_revoked, offer_family, created_at
                FROM app_store_transactions
                WHERE user_id = :userId
                ORDER BY created_at DESC
                LIMIT :limit
                """
            )
            .param("userId", userId, Types.OTHER)
            .param("limit", limit, Types.INTEGER)
            .query((rs, rowNum) -> new AppStoreTransactionRow(
                rs.getObject("id", UUID.class),
                rs.getString("transaction_id"),
                rs.getString("original_transaction_id"),
                rs.getString("product_id"),
                rs.getString("environment"),
                toInstant(rs.getTimestamp("purchase_date")),
                toInstant(rs.getTimestamp("expires_date")),
                toInstant(rs.getTimestamp("revocation_date")),
                rs.getString("transaction_reason"),
                rs.getString("ownership_type"),
                rs.getBoolean("access_revoked"),
                rs.getString("offer_family"),
                toInstant(rs.getTimestamp("created_at"))
            ))
            .list();
    }

    public List<BillingTransitionRow> findBillingTransitions(UUID userId, int limit) {
        return jdbcClient.sql(
                """
                SELECT id, from_package, to_package, from_status, to_status, reason, occurred_at, recorded_at
                FROM billing_entitlement_transition_audit
                WHERE user_id = :userId
                ORDER BY occurred_at DESC
                LIMIT :limit
                """
            )
            .param("userId", userId, Types.OTHER)
            .param("limit", limit, Types.INTEGER)
            .query((rs, rowNum) -> new BillingTransitionRow(
                rs.getObject("id", UUID.class),
                rs.getString("from_package"),
                rs.getString("to_package"),
                rs.getString("from_status"),
                rs.getString("to_status"),
                rs.getString("reason"),
                toInstant(rs.getTimestamp("occurred_at")),
                toInstant(rs.getTimestamp("recorded_at"))
            ))
            .list();
    }

    public int countWrongAnswerReportsForUser(UUID userId) {
        Integer count = jdbcClient.sql("SELECT COUNT(*)::int FROM wrong_answer_reports WHERE user_id = :userId")
            .param("userId", userId, Types.OTHER)
            .query(Integer.class)
            .single();
        return count == null ? 0 : count;
    }

    public Optional<UUID> resolveProblemSessionId(UUID problemSessionId, String traceId, String correlationId) {
        if (problemSessionId != null) {
            return jdbcClient.sql("SELECT id FROM problem_sessions WHERE id = :id")
                .param("id", problemSessionId, Types.OTHER)
                .query(UUID.class)
                .optional();
        }
        if (traceId != null && !traceId.isBlank()) {
            Optional<UUID> fromUsage = jdbcClient.sql(
                    """
                    SELECT problem_session_id
                    FROM ai_usage_records
                    WHERE trace_id = :traceId AND problem_session_id IS NOT NULL
                    ORDER BY created_at DESC
                    LIMIT 1
                    """
                )
                .param("traceId", traceId.trim(), Types.VARCHAR)
                .query(UUID.class)
                .optional();
            if (fromUsage.isPresent()) {
                return fromUsage;
            }
        }
        if (correlationId != null && !correlationId.isBlank()) {
            Optional<UUID> fromUsage = jdbcClient.sql(
                    """
                    SELECT problem_session_id
                    FROM ai_usage_records
                    WHERE correlation_id = :correlationId AND problem_session_id IS NOT NULL
                    ORDER BY created_at DESC
                    LIMIT 1
                    """
                )
                .param("correlationId", correlationId.trim(), Types.VARCHAR)
                .query(UUID.class)
                .optional();
            if (fromUsage.isPresent()) {
                return fromUsage;
            }
            return jdbcClient.sql(
                    """
                    SELECT problem_session_id
                    FROM verification_runs
                    WHERE correlation_id = :correlationId
                    ORDER BY created_at DESC
                    LIMIT 1
                    """
                )
                .param("correlationId", correlationId.trim(), Types.VARCHAR)
                .query(UUID.class)
                .optional();
        }
        return Optional.empty();
    }

    public Optional<ProblemSessionRow> findProblemSession(UUID problemSessionId) {
        return jdbcClient.sql(
                """
                SELECT id, user_id, status, input_mode, current_parse_id, problem_id, solve_job_id,
                       created_at, updated_at, completed_at, version
                FROM problem_sessions
                WHERE id = :id
                """
            )
            .param("id", problemSessionId, Types.OTHER)
            .query(this::mapProblemSession)
            .optional();
    }

    public Optional<ProblemSessionRow> findOwnedProblemSession(UUID problemSessionId, UUID userId) {
        return jdbcClient.sql(
                """
                SELECT id, user_id, status, input_mode, current_parse_id, problem_id, solve_job_id,
                       created_at, updated_at, completed_at, version
                FROM problem_sessions
                WHERE id = :id AND user_id = :userId
                """
            )
            .param("id", problemSessionId, Types.OTHER)
            .param("userId", userId, Types.OTHER)
            .query(this::mapProblemSession)
            .optional();
    }

    public List<RedactedAssetRow> findRedactedProblemAssets(UUID problemSessionId, int limit) {
        return jdbcClient.sql(
                """
                SELECT id, source_type, asset_kind, status, content_type, size_bytes,
                       image_width, image_height, page_count, retention_class,
                       available_at, deleted_at, created_at
                FROM problem_assets
                WHERE problem_session_id = :sessionId
                ORDER BY created_at DESC
                LIMIT :limit
                """
            )
            .param("sessionId", problemSessionId, Types.OTHER)
            .param("limit", limit, Types.INTEGER)
            .query((rs, rowNum) -> new RedactedAssetRow(
                rs.getObject("id", UUID.class),
                rs.getString("source_type"),
                rs.getString("asset_kind"),
                rs.getString("status"),
                rs.getString("content_type"),
                rs.getObject("size_bytes", Long.class),
                rs.getObject("image_width", Integer.class),
                rs.getObject("image_height", Integer.class),
                rs.getObject("page_count", Integer.class),
                rs.getString("retention_class"),
                toInstant(rs.getTimestamp("available_at")),
                toInstant(rs.getTimestamp("deleted_at")),
                toInstant(rs.getTimestamp("created_at"))
            ))
            .list();
    }

    public List<RedactedParseRow> findRedactedProblemParses(UUID problemSessionId, int limit) {
        return jdbcClient.sql(
                """
                SELECT id, parse_job_id, revision, source, support_status, unsupported_reason,
                       review_required, schema_version, provider, model, route_policy_version,
                       prompt_id, prompt_version, fallback_used, input_tokens, output_tokens,
                       provider_latency_ms, total_latency_ms, estimated_cost_micros, created_at
                FROM problem_parses
                WHERE problem_session_id = :sessionId
                ORDER BY revision DESC, created_at DESC
                LIMIT :limit
                """
            )
            .param("sessionId", problemSessionId, Types.OTHER)
            .param("limit", limit, Types.INTEGER)
            .query((rs, rowNum) -> new RedactedParseRow(
                rs.getObject("id", UUID.class),
                rs.getObject("parse_job_id", UUID.class),
                rs.getInt("revision"),
                rs.getString("source"),
                rs.getString("support_status"),
                AdminRedactionPolicy.redactString(rs.getString("unsupported_reason")),
                rs.getBoolean("review_required"),
                rs.getString("schema_version"),
                rs.getString("provider"),
                rs.getString("model"),
                rs.getString("route_policy_version"),
                rs.getString("prompt_id"),
                rs.getString("prompt_version"),
                rs.getBoolean("fallback_used"),
                rs.getObject("input_tokens", Integer.class),
                rs.getObject("output_tokens", Integer.class),
                toInteger(rs, "provider_latency_ms"),
                toInteger(rs, "total_latency_ms"),
                rs.getObject("estimated_cost_micros", Long.class),
                toInstant(rs.getTimestamp("created_at"))
            ))
            .list();
    }

    public List<AiUsageTraceRow> findAiUsageRecords(UUID problemSessionId, int limit) {
        return jdbcClient.sql(
                """
                SELECT id, operation_id, capability, route_policy_version, route_id, provider, model,
                       prompt_id, prompt_version, schema_version, status, failure_class, retryable,
                       attempt_count, fallback_used, fallback_chain, escalation_reason, cache_status,
                       input_token_count, output_token_count, estimated_cost_micros,
                       provider_latency_ms, gateway_latency_ms, correlation_id, trace_id,
                       created_at, completed_at
                FROM ai_usage_records
                WHERE problem_session_id = :sessionId
                ORDER BY created_at DESC
                LIMIT :limit
                """
            )
            .param("sessionId", problemSessionId, Types.OTHER)
            .param("limit", limit, Types.INTEGER)
            .query((rs, rowNum) -> new AiUsageTraceRow(
                rs.getObject("id", UUID.class),
                rs.getString("operation_id"),
                rs.getString("capability"),
                rs.getString("route_policy_version"),
                rs.getString("route_id"),
                rs.getString("provider"),
                rs.getString("model"),
                rs.getString("prompt_id"),
                rs.getString("prompt_version"),
                rs.getString("schema_version"),
                rs.getString("status"),
                rs.getString("failure_class"),
                rs.getObject("retryable", Boolean.class),
                rs.getObject("attempt_count", Integer.class),
                rs.getObject("fallback_used", Boolean.class),
                rs.getString("fallback_chain"),
                rs.getString("escalation_reason"),
                rs.getString("cache_status"),
                rs.getObject("input_token_count", Integer.class),
                rs.getObject("output_token_count", Integer.class),
                rs.getObject("estimated_cost_micros", Long.class),
                toInteger(rs, "provider_latency_ms"),
                toInteger(rs, "gateway_latency_ms"),
                rs.getString("correlation_id"),
                rs.getString("trace_id"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("completed_at"))
            ))
            .list();
    }

    public List<VerificationRunSummaryRow> findRedactedVerificationRuns(UUID problemSessionId, int limit) {
        return jdbcClient.sql(
                """
                SELECT id, solution_id, canonical_problem_id, problem_parse_id,
                       problem_parse_revision, canonical_revision, policy_version,
                       capability_version, correlation_id,
                       COALESCE(
                           NULLIF(run_jsonb->>'status', ''),
                           NULLIF(run_jsonb->>'overallStatus', ''),
                           'RECORDED'
                       ) AS derived_status,
                       created_at
                FROM verification_runs
                WHERE problem_session_id = :sessionId
                ORDER BY created_at DESC
                LIMIT :limit
                """
            )
            .param("sessionId", problemSessionId, Types.OTHER)
            .param("limit", limit, Types.INTEGER)
            .query((rs, rowNum) -> new VerificationRunSummaryRow(
                rs.getObject("id", UUID.class),
                rs.getObject("solution_id", UUID.class),
                rs.getObject("canonical_problem_id", UUID.class),
                rs.getObject("problem_parse_id", UUID.class),
                rs.getInt("problem_parse_revision"),
                rs.getInt("canonical_revision"),
                rs.getString("policy_version"),
                rs.getString("capability_version"),
                rs.getString("correlation_id"),
                rs.getString("derived_status"),
                toInstant(rs.getTimestamp("created_at"))
            ))
            .list();
    }

    public List<MistakeDisputeRow> findMistakeDisputesForUser(UUID userId, int limit) {
        return jdbcClient.sql(
                """
                SELECT id, attempt_id, classification_id, replacement_attempt_id,
                       step_index, dispute_type, policy_version, created_at
                FROM learner_mistake_dispute_events
                WHERE user_id = :userId
                ORDER BY created_at DESC
                LIMIT :limit
                """
            )
            .param("userId", userId, Types.OTHER)
            .param("limit", limit, Types.INTEGER)
            .query((rs, rowNum) -> new MistakeDisputeRow(
                rs.getObject("id", UUID.class),
                rs.getObject("attempt_id", UUID.class),
                rs.getObject("classification_id", UUID.class),
                rs.getObject("replacement_attempt_id", UUID.class),
                rs.getObject("step_index", Integer.class),
                rs.getString("dispute_type"),
                rs.getString("policy_version"),
                toInstant(rs.getTimestamp("created_at"))
            ))
            .list();
    }

    public ProblemReportSnapshot findProblemSnapshotsForReport(UUID problemSessionId) {
        String verificationStatus = jdbcClient.sql(
                """
                SELECT COALESCE(
                    NULLIF(run_jsonb->>'status', ''),
                    NULLIF(run_jsonb->>'overallStatus', ''),
                    'RECORDED'
                )
                FROM verification_runs
                WHERE problem_session_id = :sessionId
                ORDER BY created_at DESC
                LIMIT 1
                """
            )
            .param("sessionId", problemSessionId, Types.OTHER)
            .query(String.class)
            .optional()
            .orElse("UNKNOWN");

        String policyVersion = jdbcClient.sql(
                """
                SELECT policy_version
                FROM verification_runs
                WHERE problem_session_id = :sessionId
                ORDER BY created_at DESC
                LIMIT 1
                """
            )
            .param("sessionId", problemSessionId, Types.OTHER)
            .query(String.class)
            .optional()
            .orElse(null);

        String modelVersion = jdbcClient.sql(
                """
                SELECT COALESCE(provider || ':' || model, model)
                FROM ai_usage_records
                WHERE problem_session_id = :sessionId
                ORDER BY created_at DESC
                LIMIT 1
                """
            )
            .param("sessionId", problemSessionId, Types.OTHER)
            .query(String.class)
            .optional()
            .orElse(null);

        if (modelVersion == null) {
            modelVersion = jdbcClient.sql(
                    """
                    SELECT COALESCE(provider || ':' || model, model)
                    FROM problem_parses
                    WHERE problem_session_id = :sessionId
                    ORDER BY revision DESC, created_at DESC
                    LIMIT 1
                    """
                )
                .param("sessionId", problemSessionId, Types.OTHER)
                .query(String.class)
                .optional()
                .orElse(null);
        }

        String problemType = jdbcClient.sql(
                """
                SELECT input_mode
                FROM problem_sessions
                WHERE id = :sessionId
                """
            )
            .param("sessionId", problemSessionId, Types.OTHER)
            .query(String.class)
            .optional()
            .orElse(null);

        return new ProblemReportSnapshot(verificationStatus, policyVersion, modelVersion, problemType);
    }

    /**
     * Elevated-only projection reading raw object_key and parse JSONB payloads.
     * Caller MUST validate unexpired admin_raw_content_access_grants and elevated RBAC beforehand.
     */
    public ElevatedRawArtifacts findElevatedRawArtifacts(UUID problemSessionId, int limit) {
        List<ElevatedRawAssetRow> assets = jdbcClient.sql(
                """
                SELECT id, asset_kind, status, object_key, content_type, created_at
                FROM problem_assets
                WHERE problem_session_id = :sessionId
                ORDER BY created_at DESC
                LIMIT :limit
                """
            )
            .param("sessionId", problemSessionId, Types.OTHER)
            .param("limit", limit, Types.INTEGER)
            .query((rs, rowNum) -> new ElevatedRawAssetRow(
                rs.getObject("id", UUID.class),
                rs.getString("asset_kind"),
                rs.getString("status"),
                rs.getString("object_key"),
                rs.getString("content_type"),
                toInstant(rs.getTimestamp("created_at"))
            ))
            .list();

        List<ElevatedRawParseRow> parses = jdbcClient.sql(
                """
                SELECT id, revision, source, support_status,
                       raw_output_jsonb::text AS raw_output_json,
                       normalized_problem_jsonb::text AS normalized_problem_json,
                       created_at
                FROM problem_parses
                WHERE problem_session_id = :sessionId
                ORDER BY revision DESC, created_at DESC
                LIMIT :limit
                """
            )
            .param("sessionId", problemSessionId, Types.OTHER)
            .param("limit", limit, Types.INTEGER)
            .query((rs, rowNum) -> new ElevatedRawParseRow(
                rs.getObject("id", UUID.class),
                rs.getInt("revision"),
                rs.getString("source"),
                rs.getString("support_status"),
                rs.getString("raw_output_json"),
                rs.getString("normalized_problem_json"),
                toInstant(rs.getTimestamp("created_at"))
            ))
            .list();

        return new ElevatedRawArtifacts(assets, parses);
    }

    public AiOpsSnapshot loadAiOpsOverview(Instant fromTs, Instant toTs, int maxSamples) {
        List<ProviderHealthRow> providerHealth = jdbcClient.sql(
                """
                SELECT provider,
                       model,
                       COUNT(*)::bigint AS total_calls,
                       COUNT(*) FILTER (WHERE status = 'SUCCEEDED' OR status = 'SUCCESS')::bigint AS success_calls,
                       COUNT(*) FILTER (WHERE status <> 'SUCCEEDED' AND status <> 'SUCCESS')::bigint AS failure_calls,
                       COALESCE(SUM(estimated_cost_micros), 0)::bigint AS total_cost_micros,
                       COALESCE(ROUND(AVG(provider_latency_ms)), 0)::integer AS avg_latency_ms
                FROM ai_usage_records
                WHERE created_at >= :fromTs AND created_at < :toTs
                GROUP BY provider, model
                ORDER BY total_calls DESC, provider ASC, model ASC
                LIMIT :limit
                """
            )
            .param("fromTs", Timestamp.from(fromTs), Types.TIMESTAMP)
            .param("toTs", Timestamp.from(toTs), Types.TIMESTAMP)
            .param("limit", maxSamples, Types.INTEGER)
            .query((rs, rowNum) -> new ProviderHealthRow(
                rs.getString("provider"),
                rs.getString("model"),
                rs.getLong("total_calls"),
                rs.getLong("success_calls"),
                rs.getLong("failure_calls"),
                rs.getLong("total_cost_micros"),
                rs.getInt("avg_latency_ms")
            ))
            .list();

        List<ActiveRouteConfigRow> activeRoutes = jdbcClient.sql(
                """
                SELECT DISTINCT ON (capability)
                       capability,
                       route_policy_version,
                       route_id,
                       provider,
                       model,
                       prompt_id,
                       prompt_version,
                       schema_version
                FROM ai_usage_records
                WHERE created_at >= :fromTs AND created_at < :toTs
                ORDER BY capability, created_at DESC
                """
            )
            .param("fromTs", Timestamp.from(fromTs), Types.TIMESTAMP)
            .param("toTs", Timestamp.from(toTs), Types.TIMESTAMP)
            .query((rs, rowNum) -> new ActiveRouteConfigRow(
                rs.getString("capability"),
                rs.getString("route_policy_version"),
                rs.getString("route_id"),
                rs.getString("provider"),
                rs.getString("model"),
                rs.getString("prompt_id"),
                rs.getString("prompt_version"),
                rs.getString("schema_version")
            ))
            .list();

        List<RedactedFailureSampleRow> failureSamples = jdbcClient.sql(
                """
                SELECT id, capability, provider, model, status, failure_class,
                       retryable, fallback_used, correlation_id, trace_id, created_at
                FROM ai_usage_records
                WHERE created_at >= :fromTs AND created_at < :toTs
                  AND status NOT IN ('SUCCEEDED', 'SUCCESS')
                ORDER BY created_at DESC
                LIMIT :limit
                """
            )
            .param("fromTs", Timestamp.from(fromTs), Types.TIMESTAMP)
            .param("toTs", Timestamp.from(toTs), Types.TIMESTAMP)
            .param("limit", maxSamples, Types.INTEGER)
            .query((rs, rowNum) -> new RedactedFailureSampleRow(
                rs.getObject("id", UUID.class),
                rs.getString("capability"),
                rs.getString("provider"),
                rs.getString("model"),
                rs.getString("status"),
                AdminRedactionPolicy.redactString(rs.getString("failure_class")),
                rs.getObject("retryable", Boolean.class),
                rs.getObject("fallback_used", Boolean.class),
                rs.getString("correlation_id"),
                rs.getString("trace_id"),
                toInstant(rs.getTimestamp("created_at"))
            ))
            .list();

        List<KillSwitchStateRow> killSwitchStates = jdbcClient.sql(
                """
                SELECT flag_key, category, enabled, kill_switch_active, rollout_basis_points,
                       current_value_json::text AS current_value_json, version, updated_at
                FROM feature_flag_definitions
                WHERE category IN ('KILL_SWITCH', 'REMOTE_CONFIG')
                   OR flag_key IN ('secondary_solver_enabled', 'advanced_verification_enabled', 'pdf_upload_enabled')
                ORDER BY flag_key ASC
                """
            )
            .query((rs, rowNum) -> new KillSwitchStateRow(
                rs.getString("flag_key"),
                rs.getString("category"),
                rs.getBoolean("enabled"),
                rs.getBoolean("kill_switch_active"),
                rs.getInt("rollout_basis_points"),
                rs.getString("current_value_json"),
                rs.getLong("version"),
                toInstant(rs.getTimestamp("updated_at"))
            ))
            .list();

        Long recentFlagAuditCount = jdbcClient.sql(
                """
                SELECT COUNT(*)::bigint
                FROM feature_flag_audit_logs
                WHERE occurred_at >= :fromTs AND occurred_at < :toTs
                """
            )
            .param("fromTs", Timestamp.from(fromTs), Types.TIMESTAMP)
            .param("toTs", Timestamp.from(toTs), Types.TIMESTAMP)
            .query(Long.class)
            .single();

        return new AiOpsSnapshot(
            providerHealth,
            activeRoutes,
            failureSamples,
            killSwitchStates,
            recentFlagAuditCount == null ? 0L : recentFlagAuditCount
        );
    }

    public Optional<KillSwitchStateRow> findFeatureFlagSummary(String flagKey) {
        return jdbcClient.sql(
                """
                SELECT flag_key, category, enabled, kill_switch_active, rollout_basis_points,
                       current_value_json::text AS current_value_json, version, updated_at
                FROM feature_flag_definitions
                WHERE flag_key = :flagKey
                """
            )
            .param("flagKey", flagKey, Types.VARCHAR)
            .query((rs, rowNum) -> new KillSwitchStateRow(
                rs.getString("flag_key"),
                rs.getString("category"),
                rs.getBoolean("enabled"),
                rs.getBoolean("kill_switch_active"),
                rs.getInt("rollout_basis_points"),
                rs.getString("current_value_json"),
                rs.getLong("version"),
                toInstant(rs.getTimestamp("updated_at"))
            ))
            .optional();
    }

    private ProblemSessionRow mapProblemSession(ResultSet rs, int rowNum) throws SQLException {
        return new ProblemSessionRow(
            rs.getObject("id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getString("status"),
            rs.getString("input_mode"),
            rs.getObject("current_parse_id", UUID.class),
            rs.getObject("problem_id", UUID.class),
            rs.getObject("solve_job_id", UUID.class),
            toInstant(rs.getTimestamp("created_at")),
            toInstant(rs.getTimestamp("updated_at")),
            toInstant(rs.getTimestamp("completed_at")),
            rs.getLong("version")
        );
    }

    private static Optional<UUID> parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw.trim()));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Integer toInteger(ResultSet rs, String column) throws SQLException {
        Long value = rs.getObject(column, Long.class);
        return value == null ? null : Math.toIntExact(value);
    }

    public record UserAccountRow(
        UUID userId,
        String status,
        Instant createdAt,
        Instant updatedAt,
        Instant deletionRequestedAt,
        Instant deletedAt
    ) {
    }

    public record UserSessionMetadataRow(
        UUID sessionId,
        String status,
        Instant createdAt,
        Instant lastSeenAt,
        Instant expiresAt,
        Instant revokedAt,
        String revocationReason
    ) {
    }

    public record EntitlementSnapshotRow(
        UUID entitlementId,
        String tier,
        String packageCode,
        String source,
        String status,
        Instant effectiveAt,
        Instant expiresAt,
        Instant gracePeriodExpiresAt,
        String originalTransactionId,
        String environment,
        Instant lastVerifiedAt,
        Instant updatedAt,
        long version
    ) {
    }

    public record QuotaLedgerRow(
        UUID ledgerId,
        String periodKey,
        String tierCode,
        int aiVerificationsUsed,
        int aiVerificationsLimit,
        int hybridTwinsUsed,
        int hybridTwinsLimit,
        long accumulatedAiCostMicros,
        double accumulatedAiCostUsd,
        long aiCostCapMicros,
        boolean costCircuitBreakerTripped,
        Instant resetsAt,
        Instant updatedAt
    ) {
    }

    public record AppStoreSubscriptionRow(
        UUID subscriptionId,
        String originalTransactionId,
        String currentTransactionId,
        String productId,
        String environment,
        String status,
        Boolean autoRenewStatus,
        Instant expiresAt,
        Instant gracePeriodExpiresAt,
        Instant revokedAt,
        Instant lastReconciledAt,
        Instant updatedAt
    ) {
    }

    public record AppStoreTransactionRow(
        UUID id,
        String transactionId,
        String originalTransactionId,
        String productId,
        String environment,
        Instant purchaseDate,
        Instant expiresDate,
        Instant revocationDate,
        String transactionReason,
        String ownershipType,
        boolean accessRevoked,
        String offerFamily,
        Instant createdAt
    ) {
    }

    public record BillingTransitionRow(
        UUID id,
        String fromPackage,
        String toPackage,
        String fromStatus,
        String toStatus,
        String reason,
        Instant occurredAt,
        Instant recordedAt
    ) {
    }

    public record ProblemSessionRow(
        UUID id,
        UUID userId,
        String status,
        String inputMode,
        UUID currentParseId,
        UUID problemId,
        UUID solveJobId,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt,
        long version
    ) {
    }

    public record RedactedAssetRow(
        UUID assetId,
        String sourceType,
        String assetKind,
        String status,
        String contentType,
        Long sizeBytes,
        Integer imageWidth,
        Integer imageHeight,
        Integer pageCount,
        String retentionClass,
        Instant availableAt,
        Instant deletedAt,
        Instant createdAt
    ) {
    }

    public record RedactedParseRow(
        UUID parseId,
        UUID parseJobId,
        int revision,
        String source,
        String supportStatus,
        String unsupportedReason,
        boolean reviewRequired,
        String schemaVersion,
        String provider,
        String model,
        String routePolicyVersion,
        String promptId,
        String promptVersion,
        boolean fallbackUsed,
        Integer inputTokens,
        Integer outputTokens,
        Integer providerLatencyMs,
        Integer totalLatencyMs,
        Long estimatedCostMicros,
        Instant createdAt
    ) {
    }

    public record AiUsageTraceRow(
        UUID usageRecordId,
        String operationId,
        String capability,
        String routePolicyVersion,
        String routeId,
        String provider,
        String model,
        String promptId,
        String promptVersion,
        String schemaVersion,
        String status,
        String failureClass,
        Boolean retryable,
        Integer attemptCount,
        Boolean fallbackUsed,
        String fallbackChain,
        String escalationReason,
        String cacheStatus,
        Integer inputTokenCount,
        Integer outputTokenCount,
        Long estimatedCostMicros,
        Integer providerLatencyMs,
        Integer gatewayLatencyMs,
        String correlationId,
        String traceId,
        Instant createdAt,
        Instant completedAt
    ) {
    }

    public record VerificationRunSummaryRow(
        UUID runId,
        UUID solutionId,
        UUID canonicalProblemId,
        UUID problemParseId,
        int problemParseRevision,
        int canonicalRevision,
        String policyVersion,
        String capabilityVersion,
        String correlationId,
        String status,
        Instant createdAt
    ) {
    }

    public record MistakeDisputeRow(
        UUID id,
        UUID attemptId,
        UUID classificationId,
        UUID replacementAttemptId,
        Integer stepIndex,
        String disputeType,
        String policyVersion,
        Instant createdAt
    ) {
    }

    public record ProblemReportSnapshot(
        String verificationStatus,
        String policyVersion,
        String modelVersion,
        String problemType
    ) {
    }

    public record ElevatedRawAssetRow(
        UUID assetId,
        String assetKind,
        String status,
        String objectKey,
        String contentType,
        Instant createdAt
    ) {
    }

    public record ElevatedRawParseRow(
        UUID parseId,
        int revision,
        String source,
        String supportStatus,
        String rawOutputJson,
        String normalizedProblemJson,
        Instant createdAt
    ) {
    }

    public record ElevatedRawArtifacts(
        List<ElevatedRawAssetRow> assets,
        List<ElevatedRawParseRow> parses
    ) {
    }

    public record ProviderHealthRow(
        String provider,
        String model,
        long totalCalls,
        long successCalls,
        long failureCalls,
        long totalCostMicros,
        int avgLatencyMs
    ) {
    }

    public record ActiveRouteConfigRow(
        String capability,
        String routePolicyVersion,
        String routeId,
        String provider,
        String model,
        String promptId,
        String promptVersion,
        String schemaVersion
    ) {
    }

    public record RedactedFailureSampleRow(
        UUID usageRecordId,
        String capability,
        String provider,
        String model,
        String status,
        String failureClass,
        Boolean retryable,
        Boolean fallbackUsed,
        String correlationId,
        String traceId,
        Instant createdAt
    ) {
    }

    public record KillSwitchStateRow(
        String flagKey,
        String category,
        boolean enabled,
        boolean killSwitchActive,
        int rolloutBasisPoints,
        String currentValueJson,
        long version,
        Instant updatedAt
    ) {
    }

    public record AiOpsSnapshot(
        List<ProviderHealthRow> providerHealth,
        List<ActiveRouteConfigRow> activeRoutes,
        List<RedactedFailureSampleRow> failureSamples,
        List<KillSwitchStateRow> killSwitchStates,
        long recentFlagAuditCount
    ) {
    }
}
