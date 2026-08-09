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
        return Map.of(
            "assets", assets,
            "derivatives", derivatives,
            "qualityEvidence", qualityEvidence,
            "rawBinaryIncluded", false,
            "derivedBinaryIncluded", false,
            "retentionNote", "Raw source image/PDF objects and derived preprocessing objects are private object-storage assets and are removed during account deletion."
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
