package com.verifiedai.studyplan.session;

import com.verifiedai.sharedkernel.privacy.AccountDataLifecycleContributor;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * GDPR/KVKK account export and purge contributor for study_sessions and
 * study_session_checkpoints (Sprint 8.7, INV-SESS-004).
 */
@Component
@Order(29)
public class StudySessionPrivacyLifecycleContributor implements AccountDataLifecycleContributor {

    private final StudySessionRepository repository;

    public StudySessionPrivacyLifecycleContributor(StudySessionRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    public String category() {
        return "studySessions";
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> exportUserData(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        List<StudySessionRecord> sessions = repository.findAllSessionsByUserId(userId);
        long checkpointsCount = repository.countCheckpointsByUserId(userId);

        List<Map<String, Object>> summaries = sessions.stream()
            .map(session -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("sessionId", session.sessionId().toString());
                row.put("sourceContext", session.sourceContext().name());
                row.put("topicCode", session.topicCode());
                row.put("locale", session.locale());
                row.put("state", session.state().name());
                row.put("totalSteps", session.totalSteps());
                row.put("completedStepsCount", session.completedStepsCount());
                row.put("activeDurationSeconds", session.activeDurationSeconds());
                row.put("pausedDurationSeconds", session.pausedDurationSeconds());
                row.put("startedAt", session.startedAt().toString());
                row.put("trainingEligible", false);
                return row;
            })
            .toList();

        Map<String, Object> export = new LinkedHashMap<>();
        export.put("sessionsCount", sessions.size());
        export.put("checkpointsCount", checkpointsCount);
        export.put("sessions", summaries);
        export.put("trainingEligible", false);
        return export;
    }

    @Override
    @Transactional
    public void deleteUserData(UUID userId, Instant now) {
        purgeUserData(userId);
    }

    @Transactional
    public int purgeUserData(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        return repository.purgeAllForUser(userId);
    }
}
