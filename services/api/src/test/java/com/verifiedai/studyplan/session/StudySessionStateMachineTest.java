package com.verifiedai.studyplan.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class StudySessionStateMachineTest {

    private final StudySessionStateMachine stateMachine = new StudySessionStateMachine();

    @Test
    void allowsAllLegalTransitionsAndRejectsIllegalTransitions() {
        assertTrue(stateMachine.canTransition(StudySessionState.INITIALIZED, StudySessionState.ACTIVE));
        assertTrue(stateMachine.canTransition(StudySessionState.ACTIVE, StudySessionState.PAUSED));
        assertTrue(stateMachine.canTransition(StudySessionState.ACTIVE, StudySessionState.COMPLETED));
        assertTrue(stateMachine.canTransition(StudySessionState.ACTIVE, StudySessionState.ABANDONED));
        assertTrue(stateMachine.canTransition(StudySessionState.PAUSED, StudySessionState.ACTIVE));
        assertTrue(stateMachine.canTransition(StudySessionState.PAUSED, StudySessionState.COMPLETED));
        assertTrue(stateMachine.canTransition(StudySessionState.PAUSED, StudySessionState.ABANDONED));

        assertFalse(stateMachine.canTransition(StudySessionState.INITIALIZED, StudySessionState.PAUSED));
        assertFalse(stateMachine.canTransition(StudySessionState.COMPLETED, StudySessionState.ACTIVE));
        assertFalse(stateMachine.canTransition(StudySessionState.COMPLETED, StudySessionState.PAUSED));
        assertFalse(stateMachine.canTransition(StudySessionState.ABANDONED, StudySessionState.ACTIVE));
        assertFalse(stateMachine.canTransition(StudySessionState.PAUSED, StudySessionState.PAUSED));

        assertThrows(
            IllegalSessionStateTransitionException.class,
            () -> stateMachine.assertTransition(StudySessionState.COMPLETED, StudySessionState.ACTIVE)
        );
        assertThrows(
            IllegalSessionStateTransitionException.class,
            () -> stateMachine.assertTransition(StudySessionState.ABANDONED, StudySessionState.COMPLETED)
        );
    }

    @Test
    void enforcesExpiryAndMonotonicSequenceNumbers() {
        Instant startedAt = Instant.parse("2026-11-13T08:00:00Z");
        Instant expiresAt = startedAt.plus(24, ChronoUnit.HOURS);

        assertFalse(stateMachine.isExpired(expiresAt, expiresAt.minusSeconds(1)));
        assertTrue(stateMachine.isExpired(expiresAt, expiresAt));
        assertTrue(stateMachine.isExpired(expiresAt, expiresAt.plusSeconds(60)));

        assertTrue(stateMachine.isStaleSequence(2L, 2L));
        assertTrue(stateMachine.isStaleSequence(1L, 2L));
        assertFalse(stateMachine.isStaleSequence(3L, 2L));
    }

    @Test
    void clampsInflatedActiveDurationAndNeverRegresses() {
        Instant startedAt = Instant.parse("2026-11-13T08:00:00Z");
        Instant now = startedAt.plusSeconds(120);

        StudySessionStateMachine.DurationClampResult normal = stateMachine.clampActiveDuration(
            startedAt, 20, 40, 110, now, 30, 14400
        );
        // wall=120, paused=20, tolerance=30 => maxAllowed = 130 => 110 accepted without clamp
        assertEquals(110, normal.acceptedSeconds());
        assertFalse(normal.clamped());

        StudySessionStateMachine.DurationClampResult inflated = stateMachine.clampActiveDuration(
            startedAt, 20, 40, 600, now, 30, 14400
        );
        // maxAllowed = 130 => 600 clamped to 130
        assertEquals(130, inflated.acceptedSeconds());
        assertTrue(inflated.clamped());

        StudySessionStateMachine.DurationClampResult nonRegressing = stateMachine.clampActiveDuration(
            startedAt, 0, 100, 60, now, 30, 14400
        );
        // Never regresses below previous accepted 100
        assertEquals(100, nonRegressing.acceptedSeconds());
        assertFalse(nonRegressing.clamped());

        assertThrows(
            StudySessionInvalidCheckpointException.class,
            () -> stateMachine.clampActiveDuration(startedAt, 0, 0, -5, now, 30, 14400)
        );
        assertThrows(
            StudySessionInvalidCheckpointException.class,
            () -> stateMachine.clampActiveDuration(startedAt, 0, 0, 14401, now, 30, 14400)
        );
    }
}
