package com.verifiedai.studyplan.session;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pure deterministic state machine enforcing INV-SESS-001 (legal transitions),
 * INV-SESS-002 (monotonic checkpoint sequence numbers), and INV-SESS-003
 * (server-authoritative active duration clamping).
 */
public class StudySessionStateMachine {

    private static final Map<StudySessionState, Set<StudySessionState>> LEGAL_TRANSITIONS = createTransitionTable();

    private static Map<StudySessionState, Set<StudySessionState>> createTransitionTable() {
        EnumMap<StudySessionState, Set<StudySessionState>> map = new EnumMap<>(StudySessionState.class);
        map.put(StudySessionState.INITIALIZED, EnumSet.of(StudySessionState.ACTIVE));
        map.put(
            StudySessionState.ACTIVE,
            EnumSet.of(StudySessionState.PAUSED, StudySessionState.COMPLETED, StudySessionState.ABANDONED)
        );
        map.put(
            StudySessionState.PAUSED,
            EnumSet.of(StudySessionState.ACTIVE, StudySessionState.COMPLETED, StudySessionState.ABANDONED)
        );
        map.put(StudySessionState.COMPLETED, EnumSet.noneOf(StudySessionState.class));
        map.put(StudySessionState.ABANDONED, EnumSet.noneOf(StudySessionState.class));
        return map;
    }

    public boolean canTransition(StudySessionState from, StudySessionState to) {
        if (from == null || to == null) {
            return false;
        }
        return LEGAL_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public void assertTransition(StudySessionState from, StudySessionState to) {
        if (!canTransition(from, to)) {
            throw new IllegalSessionStateTransitionException(from, to);
        }
    }

    public boolean isExpired(Instant expiresAt, Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    public boolean isStaleSequence(long incomingSequenceNumber, long currentLastSequenceNumber) {
        return incomingSequenceNumber <= currentLastSequenceNumber;
    }

    public DurationClampResult clampActiveDuration(
        Instant startedAt,
        int pausedDurationSeconds,
        int previousAcceptedActiveSeconds,
        int reportedActiveSeconds,
        Instant serverNow,
        int clockSkewToleranceSeconds,
        int maxActiveDurationSeconds
    ) {
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        Objects.requireNonNull(serverNow, "serverNow must not be null");

        if (reportedActiveSeconds < 0) {
            throw new StudySessionInvalidCheckpointException("reported_active_duration_seconds must be >= 0");
        }
        if (reportedActiveSeconds > maxActiveDurationSeconds) {
            throw new StudySessionInvalidCheckpointException(
                "reported_active_duration_seconds exceeds max session limit of " + maxActiveDurationSeconds
            );
        }

        long wallElapsed = Math.max(0L, Duration.between(startedAt, serverNow).getSeconds());
        long effectiveElapsed = Math.max(
            0L,
            wallElapsed - Math.max(0L, pausedDurationSeconds) + Math.max(0L, clockSkewToleranceSeconds)
        );
        long maxAllowed = Math.min(maxActiveDurationSeconds, effectiveElapsed);

        if (reportedActiveSeconds > maxAllowed) {
            int clampedValue = (int) Math.max(previousAcceptedActiveSeconds, Math.min(maxActiveDurationSeconds, maxAllowed));
            return new DurationClampResult(clampedValue, true);
        }

        int monotonicAccepted = Math.max(previousAcceptedActiveSeconds, reportedActiveSeconds);
        return new DurationClampResult(Math.min(maxActiveDurationSeconds, monotonicAccepted), false);
    }

    public record DurationClampResult(int acceptedSeconds, boolean clamped) {
    }
}
