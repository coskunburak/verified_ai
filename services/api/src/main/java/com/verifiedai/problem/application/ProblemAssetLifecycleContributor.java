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
        return Map.of(
            "assets", assets,
            "rawBinaryIncluded", false,
            "retentionNote", "Raw source image/PDF objects are private object-storage assets and are removed during account deletion."
        );
    }

    @Override
    public void deleteUserData(UUID userId, Instant now) {
        List<String> objectKeys = jdbcTemplate.query(
            """
            select object_key
            from problem_assets
            where user_id = ? and status <> 'DELETED'
            """,
            (resultSet, rowNum) -> resultSet.getString("object_key"),
            userId
        );
        for (String objectKey : objectKeys) {
            storage.deleteIfExists(objectKey);
        }
        jdbcTemplate.update("delete from problem_sessions where user_id = ?", userId);
    }
}
