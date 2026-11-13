package com.verifiedai.studyplan.session;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.dao.DuplicateKeyException;

final class InMemoryStudySessionRepository implements StudySessionRepository {

    private final Map<UUID, StudySessionRecord> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, StudySessionCheckpointRecord> checkpoints = new ConcurrentHashMap<>();

    @Override
    public void insertSession(StudySessionRecord session) {
        if (sessions.putIfAbsent(session.sessionId(), session) != null) {
            throw new DuplicateKeyException("Duplicate session_id: " + session.sessionId());
        }
    }

    @Override
    public void updateSession(StudySessionRecord session) {
        sessions.compute(session.sessionId(), (id, existing) -> {
            if (existing == null || !existing.userId().equals(session.userId())) {
                return existing;
            }
            return session;
        });
    }

    @Override
    public Optional<StudySessionRecord> findBySessionIdAndUserId(UUID sessionId, UUID userId) {
        StudySessionRecord record = sessions.get(sessionId);
        if (record == null || !record.userId().equals(userId)) {
            return Optional.empty();
        }
        return Optional.of(record);
    }

    @Override
    public List<StudySessionRecord> findOpenSessionsByUserId(UUID userId) {
        return sessions.values().stream()
            .filter(s -> s.userId().equals(userId) && !s.state().isTerminal())
            .sorted(Comparator.comparing(StudySessionRecord::updatedAt).reversed())
            .toList();
    }

    @Override
    public Optional<StudySessionCheckpointRecord> findCheckpointBySessionAndIdempotencyKey(
        UUID sessionId,
        UUID userId,
        String idempotencyKey
    ) {
        return checkpoints.values().stream()
            .filter(c -> c.sessionId().equals(sessionId)
                && c.userId().equals(userId)
                && c.idempotencyKey().equals(idempotencyKey))
            .findFirst();
    }

    @Override
    public synchronized void insertCheckpoint(StudySessionCheckpointRecord checkpoint) {
        boolean dupSessionIdem = checkpoints.values().stream().anyMatch(c ->
            c.sessionId().equals(checkpoint.sessionId()) && c.idempotencyKey().equals(checkpoint.idempotencyKey()));
        boolean dupUserIdem = checkpoints.values().stream().anyMatch(c ->
            c.userId().equals(checkpoint.userId()) && c.idempotencyKey().equals(checkpoint.idempotencyKey()));
        boolean dupSeq = checkpoints.values().stream().anyMatch(c ->
            c.sessionId().equals(checkpoint.sessionId()) && c.sequenceNumber() == checkpoint.sequenceNumber());
        if (dupSessionIdem || dupUserIdem || dupSeq) {
            throw new DuplicateKeyException("Duplicate checkpoint constraint triggered");
        }
        checkpoints.put(checkpoint.checkpointId(), checkpoint);
    }

    @Override
    public List<StudySessionRecord> findAllSessionsByUserId(UUID userId) {
        return sessions.values().stream()
            .filter(s -> s.userId().equals(userId))
            .sorted(Comparator.comparing(StudySessionRecord::createdAt).reversed())
            .toList();
    }

    @Override
    public long countCheckpointsByUserId(UUID userId) {
        return checkpoints.values().stream().filter(c -> c.userId().equals(userId)).count();
    }

    @Override
    public int purgeAllForUser(UUID userId) {
        int removed = 0;
        for (UUID id : List.copyOf(checkpoints.keySet())) {
            StudySessionCheckpointRecord ckpt = checkpoints.get(id);
            if (ckpt != null && ckpt.userId().equals(userId) && checkpoints.remove(id) != null) {
                removed++;
            }
        }
        for (UUID id : List.copyOf(sessions.keySet())) {
            StudySessionRecord sess = sessions.get(id);
            if (sess != null && sess.userId().equals(userId) && sessions.remove(id) != null) {
                removed++;
            }
        }
        return removed;
    }
}
