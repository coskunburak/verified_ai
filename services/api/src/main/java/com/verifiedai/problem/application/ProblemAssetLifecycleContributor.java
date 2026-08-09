package com.verifiedai.problem.application;

import com.verifiedai.problem.domain.port.ProblemAssetStorage;
import com.verifiedai.problem.infrastructure.persistence.ProblemAssetJpaRepository;
import com.verifiedai.sharedkernel.privacy.AccountDataLifecycleContributor;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class ProblemAssetLifecycleContributor implements AccountDataLifecycleContributor {
    private final ProblemAssetJpaRepository assetRepository;
    private final ProblemAssetStorage storage;
    private final JdbcTemplate jdbcTemplate;

    ProblemAssetLifecycleContributor(
        ProblemAssetJpaRepository assetRepository,
        ProblemAssetStorage storage,
        JdbcTemplate jdbcTemplate
    ) {
        this.assetRepository = assetRepository;
        this.storage = storage;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String category() {
        return "problemAssets";
    }

    @Override
    public Map<String, Object> exportUserData(UUID userId) {
        List<Map<String, Object>> assets = assetRepository.findByUserIdOrderByCreatedAtDesc(userId)
            .stream()
            .map(asset -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("problemSessionId", asset.problemSessionId().toString());
                row.put("problemAssetId", asset.id().toString());
                row.put("sourceType", asset.sourceType());
                row.put("assetKind", asset.assetKind());
                row.put("status", asset.status());
                row.put("contentType", asset.contentType());
                row.put("sizeBytes", asset.sizeBytes());
                row.put("checksumAlgorithm", asset.checksumAlgorithm());
                row.put("checksumSha256", asset.checksumValue());
                row.put("retentionClass", asset.retentionClass());
                row.put("createdAt", asset.createdAt().toString());
                row.put("availableAt", asset.availableAt() == null ? null : asset.availableAt().toString());
                return row;
            })
            .toList();
        List<Map<String, Object>> derivatives = jdbcTemplate.query(
            """
            select source_asset_id,
                   id,
                   derivative_kind,
                   status,
                   selected_for_recognition,
                   content_type,
                   size_bytes,
                   checksum_algorithm,
                   checksum_value,
                   quality_outcome,
                   failure_code,
                   processor_name,
                   processor_version,
                   configuration_version,
                   created_at,
                   completed_at
            from problem_asset_derivatives
            where user_id = ?
            order by created_at desc
            """,
            (resultSet, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("sourceAssetId", resultSet.getObject("source_asset_id").toString());
                row.put("derivativeId", resultSet.getObject("id").toString());
                row.put("derivativeKind", resultSet.getString("derivative_kind"));
                row.put("status", resultSet.getString("status"));
                row.put("selectedForRecognition", resultSet.getBoolean("selected_for_recognition"));
                row.put("contentType", resultSet.getString("content_type"));
                row.put("sizeBytes", resultSet.getObject("size_bytes"));
                row.put("checksumAlgorithm", resultSet.getString("checksum_algorithm"));
                row.put("checksumSha256", resultSet.getString("checksum_value"));
                row.put("qualityOutcome", resultSet.getString("quality_outcome"));
                row.put("failureCode", resultSet.getString("failure_code"));
                row.put("processorName", resultSet.getString("processor_name"));
                row.put("processorVersion", resultSet.getString("processor_version"));
                row.put("configurationVersion", resultSet.getString("configuration_version"));
                row.put("createdAt", resultSet.getTimestamp("created_at").toInstant().toString());
                row.put("completedAt", resultSet.getTimestamp("completed_at") == null ? null : resultSet.getTimestamp("completed_at").toInstant().toString());
                return row;
            },
            userId
        );
        List<Map<String, Object>> qualityEvidence = jdbcTemplate.query(
            """
            select source_asset_id,
                   derivative_id,
                   signal_type,
                   severity,
                   score,
                   threshold,
                   policy_version,
                   message_code,
                   created_at
            from problem_asset_quality_evidence
            where user_id = ?
            order by created_at desc
            """,
            (resultSet, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("sourceAssetId", resultSet.getObject("source_asset_id").toString());
                row.put("derivativeId", resultSet.getObject("derivative_id").toString());
                row.put("signalType", resultSet.getString("signal_type"));
                row.put("severity", resultSet.getString("severity"));
                row.put("score", resultSet.getBigDecimal("score"));
                row.put("threshold", resultSet.getBigDecimal("threshold"));
                row.put("policyVersion", resultSet.getString("policy_version"));
                row.put("messageCode", resultSet.getString("message_code"));
                row.put("createdAt", resultSet.getTimestamp("created_at").toInstant().toString());
                return row;
            },
            userId
        );
        List<Map<String, Object>> recognitionJobs = jdbcTemplate.query(
            """
            select id,
                   problem_session_id,
                   source_asset_id,
                   input_derivative_id,
                   status,
                   capability,
                   prompt_id,
                   prompt_version,
                   schema_version,
                   route_policy_version,
                   attempt_count,
                   max_attempts,
                   last_error_code,
                   last_failure_class,
                   review_required,
                   created_at,
                   completed_at
            from recognition_jobs
            where user_id = ?
            order by created_at desc
            """,
            (resultSet, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("recognitionJobId", resultSet.getObject("id").toString());
                row.put("problemSessionId", resultSet.getObject("problem_session_id").toString());
                row.put("sourceAssetId", resultSet.getObject("source_asset_id").toString());
                row.put("inputDerivativeId", resultSet.getObject("input_derivative_id").toString());
                row.put("status", resultSet.getString("status"));
                row.put("capability", resultSet.getString("capability"));
                row.put("promptId", resultSet.getString("prompt_id"));
                row.put("promptVersion", resultSet.getString("prompt_version"));
                row.put("schemaVersion", resultSet.getString("schema_version"));
                row.put("routePolicyVersion", resultSet.getString("route_policy_version"));
                row.put("attemptCount", resultSet.getInt("attempt_count"));
                row.put("maxAttempts", resultSet.getInt("max_attempts"));
                row.put("lastErrorCode", resultSet.getString("last_error_code"));
                row.put("lastFailureClass", resultSet.getString("last_failure_class"));
                row.put("reviewRequired", resultSet.getBoolean("review_required"));
                row.put("createdAt", resultSet.getTimestamp("created_at").toInstant().toString());
                row.put("completedAt", resultSet.getTimestamp("completed_at") == null ? null : resultSet.getTimestamp("completed_at").toInstant().toString());
                return row;
            },
            userId
        );
        List<Map<String, Object>> recognitionEvidence = jdbcTemplate.query(
            """
            select id,
                   recognition_job_id,
                   problem_session_id,
                   source_asset_id,
                   input_derivative_id,
                   revision,
                   schema_version,
                   normalized_evidence_jsonb::text as normalized_evidence_json,
                   upstream_quality_evidence_jsonb::text as upstream_quality_evidence_json,
                   provider,
                   model,
                   route_policy_version,
                   prompt_id,
                   prompt_version,
                   input_tokens,
                   output_tokens,
                   image_units,
                   request_units,
                   provider_latency_ms,
                   total_latency_ms,
                   estimated_cost_micros,
                   currency,
                   pricing_version,
                   created_at
            from recognition_evidence
            where user_id = ?
            order by created_at desc
            """,
            (resultSet, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("recognitionEvidenceId", resultSet.getObject("id").toString());
                row.put("recognitionJobId", resultSet.getObject("recognition_job_id").toString());
                row.put("problemSessionId", resultSet.getObject("problem_session_id").toString());
                row.put("sourceAssetId", resultSet.getObject("source_asset_id").toString());
                row.put("inputDerivativeId", resultSet.getObject("input_derivative_id").toString());
                row.put("revision", resultSet.getInt("revision"));
                row.put("schemaVersion", resultSet.getString("schema_version"));
                row.put("normalizedEvidenceJson", resultSet.getString("normalized_evidence_json"));
                row.put("upstreamQualityEvidenceJson", resultSet.getString("upstream_quality_evidence_json"));
                row.put("provider", resultSet.getString("provider"));
                row.put("model", resultSet.getString("model"));
                row.put("routePolicyVersion", resultSet.getString("route_policy_version"));
                row.put("promptId", resultSet.getString("prompt_id"));
                row.put("promptVersion", resultSet.getString("prompt_version"));
                row.put("inputTokens", resultSet.getObject("input_tokens"));
                row.put("outputTokens", resultSet.getObject("output_tokens"));
                row.put("imageUnits", resultSet.getObject("image_units"));
                row.put("requestUnits", resultSet.getInt("request_units"));
                row.put("providerLatencyMs", resultSet.getLong("provider_latency_ms"));
                row.put("totalLatencyMs", resultSet.getLong("total_latency_ms"));
                row.put("estimatedCostMicros", resultSet.getLong("estimated_cost_micros"));
                row.put("currency", resultSet.getString("currency"));
                row.put("pricingVersion", resultSet.getString("pricing_version"));
                row.put("createdAt", resultSet.getTimestamp("created_at").toInstant().toString());
                row.put("rawProviderOutputIncluded", false);
                return row;
            },
            userId
        );
        return Map.of(
            "assets", assets,
            "derivatives", derivatives,
            "qualityEvidence", qualityEvidence,
            "recognitionJobs", recognitionJobs,
            "recognitionEvidence", recognitionEvidence,
            "rawBinaryIncluded", false,
            "derivedBinaryIncluded", false,
            "rawRecognitionProviderOutputIncluded", false,
            "retentionNote", "Raw source image/PDF objects and derived preprocessing objects are private object-storage assets and are removed during account deletion. Recognition rows cascade with problem sessions; raw provider output is stored separately and excluded from export payloads."
        );
    }

    @Override
    public void deleteUserData(UUID userId, Instant now) {
        List<String> objectKeys = jdbcTemplate.query(
            """
            select object_key
            from problem_assets
            where user_id = ? and status <> 'DELETED'
            union all
            select object_key
            from problem_asset_derivatives
            where user_id = ? and object_key is not null
            """,
            (resultSet, rowNum) -> resultSet.getString("object_key"),
            userId,
            userId
        );
        for (String objectKey : objectKeys) {
            storage.deleteIfExists(objectKey);
        }
        jdbcTemplate.update("delete from problem_sessions where user_id = ?", userId);
    }
}
