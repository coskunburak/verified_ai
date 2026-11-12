package com.verifiedai.studyplan.spaced;

import com.verifiedai.sharedkernel.privacy.AccountDataLifecycleContributor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Component
public class SpacedReviewPrivacyPurgeContributor implements AccountDataLifecycleContributor {

    private final SpacedReviewQueueRepository repository;

    public SpacedReviewPrivacyPurgeContributor(SpacedReviewQueueRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
    }

    @Override
    public String category() {
        return "spaced_review_queue";
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> exportUserData(UUID userId) {
        Objects.requireNonNull(userId, "userId cannot be null");
        List<SpacedReviewQueueEntity> items = repository.findAllByUserId(userId);

        List<Map<String, Object>> records = items.stream()
                .map(item -> {
                    Map<String, Object> record = new LinkedHashMap<>();
                    record.put("id", item.getId().toString());
                    record.put("canonicalSkillCode", item.getCanonicalSkillCode());
                    record.put("targetMistakeCode", item.getTargetMistakeCode());
                    record.put("triggerType", item.getTriggerType().name());
                    record.put("estimatedRetention", item.getEstimatedRetention().toPlainString());
                    record.put("halfLifeDays", item.getHalfLifeDays().toPlainString());
                    record.put("dueAt", item.getDueAt().toString());
                    record.put("status", item.getStatus().name());
                    record.put("createdAt", item.getCreatedAt().toString());
                    record.put("completedAt", item.getCompletedAt() != null ? item.getCompletedAt().toString() : null);
                    return record;
                })
                .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("category", category());
        payload.put("recordCount", records.size());
        payload.put("items", records);
        return Collections.unmodifiableMap(payload);
    }

    @Override
    @Transactional
    public void deleteUserData(UUID userId, Instant now) {
        Objects.requireNonNull(userId, "userId cannot be null");
        // INV-SR-006: Single-query bulk delete executing in < 500 ms SLA
        repository.purgeAllByUserId(userId);
    }
}
