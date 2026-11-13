package com.verifiedai.analytics;

import com.verifiedai.sharedkernel.privacy.AccountDataLifecycleContributor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Component
@Order(31)
public class AnalyticsPrivacyLifecycleContributor implements AccountDataLifecycleContributor {

    private final LearnerMasteryCalibrationRepository repository;
    private final WeeklyLearnerDigestRepository weeklyDigestRepository;

    public AnalyticsPrivacyLifecycleContributor(LearnerMasteryCalibrationRepository repository) {
        this(repository, null);
    }

    @Autowired
    public AnalyticsPrivacyLifecycleContributor(
        LearnerMasteryCalibrationRepository repository,
        WeeklyLearnerDigestRepository weeklyDigestRepository
    ) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.weeklyDigestRepository = weeklyDigestRepository;
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

        List<WeeklyLearnerDigest> digests = weeklyDigestRepository != null
            ? weeklyDigestRepository.findAllByUserId(userId)
            : List.of();
        List<Map<String, Object>> exportDigests = digests.stream()
            .map(d -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("digestId", d.digestId().toString());
                item.put("weekIso", d.weekIso());
                item.put("weekStartDate", d.weekStartDate().toString());
                item.put("weekEndDate", d.weekEndDate().toString());
                item.put("locale", d.locale());
                item.put("status", d.status().name());
                item.put("solvedAttemptsCount", d.solvedAttemptsCount());
                item.put("independentTwinsSolvedCount", d.independentTwinsSolvedCount());
                item.put("studyMinutes", d.studyMinutes());
                item.put("activeDaysCount", d.activeDaysCount());
                item.put("recommendations", d.recommendations());
                item.put("generatedAt", d.generatedAt().toString());
                item.put("trainingEligible", false);
                return Collections.unmodifiableMap(item);
            })
            .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("category", category());
        payload.put("recordCount", exportRecords.size());
        payload.put("records", exportRecords);
        payload.put("weeklyLearnerDigestCount", exportDigests.size());
        payload.put("weeklyLearnerDigests", exportDigests);
        return Collections.unmodifiableMap(payload);
    }

    @Override
    @Transactional
    public void deleteUserData(UUID userId, Instant now) {
        Objects.requireNonNull(userId, "userId cannot be null");
        repository.deleteAllByUserId(userId);
        if (weeklyDigestRepository != null) {
            weeklyDigestRepository.deleteByUserId(userId);
        }
    }
}
