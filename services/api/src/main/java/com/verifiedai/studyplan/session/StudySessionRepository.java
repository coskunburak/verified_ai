package com.verifiedai.studyplan.session;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudySessionRepository {

    void insertSession(StudySessionRecord session);

    void updateSession(StudySessionRecord session);

    Optional<StudySessionRecord> findBySessionIdAndUserId(UUID sessionId, UUID userId);

    List<StudySessionRecord> findOpenSessionsByUserId(UUID userId);

    Optional<StudySessionCheckpointRecord> findCheckpointBySessionAndIdempotencyKey(
        UUID sessionId,
        UUID userId,
        String idempotencyKey
    );

    void insertCheckpoint(StudySessionCheckpointRecord checkpoint);

    List<StudySessionRecord> findAllSessionsByUserId(UUID userId);

    long countCheckpointsByUserId(UUID userId);

    int purgeAllForUser(UUID userId);
}
