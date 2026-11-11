package com.verifiedai.analytics;

import com.verifiedai.sharedkernel.privacy.AccountDataLifecycleContributor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Component
public class AnalyticsPrivacyLifecycleContributor implements AccountDataLifecycleContributor {

    private final LearnerMasteryCalibrationRepository repository;

    public AnalyticsPrivacyLifecycleContributor(LearnerMasteryCalibrationRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
    }

    @Override
    public String category() {
        return "analytics";
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> exportUserData(UUID userId) {
        Objects.requireNonNull(userId, "userId cannot be null");
        List<LearnerMasteryCalibrationRecordEntity> records = repository.findByUserIdOrderByCreatedAtDesc(userId);

        List<Map<String, Object>> exportRecords = records.stream()
            .map(r -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", r.getId() != null ? r.getId().toString() : "");
                map.put("attemptId", r.getAttemptId().toString());
                map.put("canonicalSkillCode", r.getCanonicalSkillCode());
                map.put("predictedRetentionProb", r.getPredictedRetentionProb().toString());
                map.put("observedIndependentOutcome", (int) r.getObservedIndependentOutcome());
                map.put("brierResidual", r.getBrierResidual().toString());
                map.put("createdAt", r.getCreatedAt().toString());
                return map;
            })
            .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("category", category());
        payload.put("recordCount", exportRecords.size());
        payload.put("records", exportRecords);
        return Collections.unmodifiableMap(payload);
    }

    @Override
    @Transactional
    public void deleteUserData(UUID userId, Instant now) {
        Objects.requireNonNull(userId, "userId cannot be null");
        repository.deleteAllByUserId(userId);
    }
}
