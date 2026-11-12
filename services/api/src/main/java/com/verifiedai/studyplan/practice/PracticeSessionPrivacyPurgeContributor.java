package com.verifiedai.studyplan.practice;

import com.verifiedai.sharedkernel.privacy.AccountDataLifecycleContributor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Component
public class PracticeSessionPrivacyPurgeContributor implements AccountDataLifecycleContributor {

    private final PracticeSessionRepository sessionRepository;

    public PracticeSessionPrivacyPurgeContributor(PracticeSessionRepository sessionRepository) {
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository cannot be null");
    }

    @Override
    public String category() {
        return "practice_sessions";
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> exportUserData(UUID userId) {
        Objects.requireNonNull(userId, "userId cannot be null");
        List<PracticeSessionEntity> sessions = sessionRepository.findAllByUserIdOrderByStartedAtDesc(userId);

        List<Map<String, Object>> records = sessions.stream()
                .map(s -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", s.getId().toString());
                    map.put("sessionMode", s.getSessionMode().name());
                    map.put("status", s.getStatus().name());
                    map.put("targetSkillId", s.getTargetSkillId());
                    map.put("activeTrapFamily", s.getActiveTrapFamily());
                    map.put("totalItems", s.getTotalItems());
                    map.put("completedItems", s.getCompletedItems());
                    map.put("verifiedCorrectItems", s.getVerifiedCorrectItems());
                    map.put("trapClosureAchieved", s.isTrapClosureAchieved());
                    map.put("startedAt", s.getStartedAt().toString());
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
        // Cascades to practice_session_items via database ON DELETE CASCADE in a single statement
        sessionRepository.deleteAllByUserId(userId);
    }
}
