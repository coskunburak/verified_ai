package com.verifiedai.studyplan.rebalance;

import com.verifiedai.sharedkernel.privacy.AccountDataLifecycleContributor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Component
@Order(27)
public class RebalancePrivacyPurgeContributor implements AccountDataLifecycleContributor {

    private final RebalanceEventRepository rebalanceEventRepository;

    public RebalancePrivacyPurgeContributor(RebalanceEventRepository rebalanceEventRepository) {
        this.rebalanceEventRepository = Objects.requireNonNull(
                rebalanceEventRepository,
                "rebalanceEventRepository cannot be null"
        );
    }

    @Override
    public String category() {
        return "study_plan_rebalance_events";
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> exportUserData(UUID userId) {
        Objects.requireNonNull(userId, "userId cannot be null");
        List<RebalanceEventEntity> events = rebalanceEventRepository.findByUserIdOrderByCreatedAtDesc(userId);

        List<Map<String, Object>> records = events.stream()
                .map(event -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("rebalanceEventId", event.getId().toString());
                    map.put("recoveryDate", event.getRecoveryDate().toString());
                    map.put("triggerType", event.getTriggerType().name());
                    map.put("missedDaysCount", event.getMissedDaysCount());
                    map.put("daysUntilExam", event.getDaysUntilExam());
                    map.put("previousDailyBudget", event.getPreviousDailyBudget());
                    map.put("rebalancedDailyBudget", event.getRebalancedDailyBudget());
                    map.put("prunedTasksCount", event.getPrunedTasksCount());
                    map.put("rescheduledReviewsCount", event.getRescheduledReviewsCount());
                    map.put("activeTodayTasksCount", event.getActiveTodayTasksCount());
                    map.put("miniModeActivated", event.isMiniModeActivated());
                    map.put("pruneReasonCode", event.getPruneReasonCode().name());
                    map.put("createdAt", event.getCreatedAt().toString());
                    return map;
                })
                .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("category", category());
        payload.put("recordCount", records.size());
        payload.put("events", records);
        return Collections.unmodifiableMap(payload);
    }

    @Override
    @Transactional
    public void deleteUserData(UUID userId, Instant now) {
        Objects.requireNonNull(userId, "userId cannot be null");
        // INV-REBAL-005: Single indexed delete statement (< 500 ms SLA)
        rebalanceEventRepository.deleteAllByUserId(userId);
    }
}
