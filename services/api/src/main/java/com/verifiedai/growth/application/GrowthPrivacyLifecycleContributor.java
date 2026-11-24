package com.verifiedai.growth.application;

import com.verifiedai.growth.infrastructure.GrowthWriteJdbcRepository;
import com.verifiedai.growth.infrastructure.GrowthWriteJdbcRepository.ReferrerAggregateStats;
import com.verifiedai.growth.infrastructure.GrowthWriteJdbcRepository.StoredReferralAttribution;
import com.verifiedai.growth.infrastructure.GrowthWriteJdbcRepository.StoredReferralCode;
import com.verifiedai.sharedkernel.privacy.AccountDataLifecycleContributor;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * GDPR / ADR-006 account lifecycle contributor for Sprint 10.9 growth records (INV-GRW-006).
 * Exports PII-free growth metadata (strictly omitting payload_hash, idempotency_key,
 * device_fingerprint_hash, and counterparty user IDs) and explicitly deletes all owned V073 rows.
 */
@Component
@Order(34)
public class GrowthPrivacyLifecycleContributor implements AccountDataLifecycleContributor {

    private final GrowthWriteJdbcRepository writeRepository;

    public GrowthPrivacyLifecycleContributor(GrowthWriteJdbcRepository writeRepository) {
        this.writeRepository = Objects.requireNonNull(writeRepository, "writeRepository must not be null");
    }

    @Override
    public String category() {
        return "growth";
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> exportUserData(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");

        Optional<StoredReferralCode> referralCodeOpt = writeRepository.findReferralCodeByUserId(userId);
        Optional<StoredReferralAttribution> attributedByOpt = writeRepository.findAttributionByReferredUserId(userId);
        ReferrerAggregateStats referrerStats = writeRepository.summarizeReferrerStats(userId);
        List<Map<String, Object>> shareArtifacts = writeRepository.findShareableArtifactsForPrivacyExport(userId);
        List<Map<String, Object>> reviewEvents = writeRepository.findReviewEventsForPrivacyExport(userId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("category", category());
        payload.put("referralCode", referralCodeOpt.map(StoredReferralCode::referralCode).orElse(null));
        payload.put("referredStatus", attributedByOpt.map(attr -> attr.status().name()).orElse(null));
        payload.put("referrerStats", Map.of(
            "totalAttributions", referrerStats.totalAttributions(),
            "qualifiedCount", referrerStats.qualifiedCount(),
            "rewardedCount", referrerStats.rewardedCount(),
            "totalRewardCredits", referrerStats.totalRewardCredits()
        ));
        payload.put("shareableArtifacts", shareArtifacts);
        payload.put("storeReviewEvents", reviewEvents);
        payload.put("trainingEligible", false);
        return Collections.unmodifiableMap(payload);
    }

    @Override
    @Transactional
    public void deleteUserData(UUID userId, Instant now) {
        Objects.requireNonNull(userId, "userId must not be null");
        writeRepository.deleteAllUserGrowthData(userId);
    }
}
