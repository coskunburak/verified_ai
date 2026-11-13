package com.verifiedai.studyplan.coach;

import com.verifiedai.sharedkernel.privacy.AccountDataLifecycleContributor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Component
public class DailyCoachPrivacyPurgeContributor implements AccountDataLifecycleContributor {

    private final DailyCoachSessionRepository sessionRepository;
    private final DailyCoachTaskRepository taskRepository;

    public DailyCoachPrivacyPurgeContributor(
            DailyCoachSessionRepository sessionRepository,
            DailyCoachTaskRepository taskRepository
    ) {
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository cannot be null");
        this.taskRepository = Objects.requireNonNull(taskRepository, "taskRepository cannot be null");
    }

    @Override
    public String category() {
        return "daily_coach_sessions";
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> exportUserData(UUID userId) {
        Objects.requireNonNull(userId, "userId cannot be null");
        List<DailyCoachSessionEntity> sessions = sessionRepository.findAllByUserIdOrderByLocalDateDesc(userId);

        List<Map<String, Object>> records = sessions.stream()
                .map(session -> {
                    List<Map<String, Object>> taskRecords = taskRepository
                            .findAllBySessionIdOrderByTaskOrderAsc(session.getId())
                            .stream()
                            .map(task -> {
                                Map<String, Object> tMap = new LinkedHashMap<>();
                                tMap.put("taskId", task.getId().toString());
                                tMap.put("taskOrder", task.getTaskOrder());
                                tMap.put("taskType", task.getTaskType().name());
                                tMap.put("targetSkillCode", task.getTargetSkillCode());
                                tMap.put("targetMistakeCode", task.getTargetMistakeCode());
                                tMap.put("status", task.getStatus().name());
                                return tMap;
                            })
                            .toList();

                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("sessionId", session.getId().toString());
                    map.put("localDate", session.getLocalDate().toString());
                    map.put("budgetMinutes", session.getBudgetMinutes());
                    map.put("status", session.getStatus().name());
                    map.put("preferredMode", session.getPreferredMode().name());
                    map.put("isDownshifted", session.isDownshifted());
                    map.put("isColdStart", session.isColdStart());
                    map.put("tasks", taskRecords);
                    return map;
                })
                .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("category", category());
        payload.put("recordCount", records.size());
        payload.put("sessions", records);
        return Collections.unmodifiableMap(payload);
    }

    @Override
    @Transactional
    public void deleteUserData(UUID userId, Instant now) {
        Objects.requireNonNull(userId, "userId cannot be null");
        // INV-COACH-005: Single indexed statement relying on ON DELETE CASCADE (< 500 ms SLA)
        sessionRepository.deleteAllByUserId(userId);
    }
}
