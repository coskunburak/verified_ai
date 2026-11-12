package com.verifiedai.studyplan.privacy;

import com.verifiedai.sharedkernel.privacy.AccountDataLifecycleContributor;
import com.verifiedai.studyplan.domain.LearnerRecommendationEntity;
import com.verifiedai.studyplan.domain.LearnerRecommendationRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Component
public class StudyPlanPrivacyLifecycleContributor implements AccountDataLifecycleContributor {

    private final LearnerRecommendationRepository repository;

    public StudyPlanPrivacyLifecycleContributor(LearnerRecommendationRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
    }

    @Override
    public String category() {
        return "study_plan";
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> exportUserData(UUID userId) {
        Objects.requireNonNull(userId, "userId cannot be null");
        List<LearnerRecommendationEntity> records = repository.findAllByUserIdOrderByCreatedAtDesc(userId);

        List<Map<String, Object>> exportRecords = records.stream()
                .map(r -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", r.getId().toString());
                    map.put("recommendationType", r.getRecommendationType().name());
                    map.put("targetSkillCode", r.getTargetSkillCode());
                    map.put("targetMistakeCode", r.getTargetMistakeCode());
                    map.put("priorityScore", r.getPriorityScore().doubleValue());
                    map.put("rationaleCode", r.getRationaleCode().name());
                    map.put("status", r.getStatus().name());
                    map.put("expiresAt", r.getExpiresAt().toString());
                    map.put("createdAt", r.getCreatedAt().toString());
                    return map;
                })
                .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("category", category());
        payload.put("recordCount", exportRecords.size());
        payload.put("recommendations", exportRecords);
        return Collections.unmodifiableMap(payload);
    }

    @Override
    @Transactional
    public void deleteUserData(UUID userId, Instant now) {
        Objects.requireNonNull(userId, "userId cannot be null");
        repository.deleteAllByUserId(userId);
    }
}
