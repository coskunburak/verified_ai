package com.verifiedai.studyplan.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class StudySessionOrchestrationServiceTest {

    private static final UUID USER_A = UUID.fromString("00000000-0000-4000-8000-000000000870");
    private static final UUID USER_B = UUID.fromString("00000000-0000-4000-8000-000000000879");

    private MutableClock clock;
    private InMemoryStudySessionRepository repository;
    private SimpleMeterRegistry meterRegistry;
    private StudySessionOrchestrationService service;
    private StudySessionPrivacyLifecycleContributor privacyContributor;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-11-13T07:00:00Z"));
        repository = new InMemoryStudySessionRepository();
        meterRegistry = new SimpleMeterRegistry();
        service = new StudySessionOrchestrationService(
            repository,
            new StudySessionStateMachine(),
            StudySessionOrchestrationProperties.defaults(),
            meterRegistry,
            clock
        );
        privacyContributor = new StudySessionPrivacyLifecycleContributor(repository);
    }

    @Test
    void startSessionAutoPausesPriorActiveAndExpiresStalePriorSession() {
        StudySessionStartResponse first = service.startSession(USER_A, startRequest("topic.first"));
        UUID firstId = first.session().sessionId();
        assertNull(first.autoPausedPriorSessionId());

        clock.advanceSeconds(120);
        StudySessionStartResponse second = service.startSession(USER_A, startRequest("topic.second"));
        assertEquals(firstId, second.autoPausedPriorSessionId());
        assertEquals(1.0, meterRegistry.counter("study_session.auto_paused.count").count());

        StudySessionStateDto pausedFirst = service.getSessionById(USER_A, firstId);
        assertEquals(StudySessionState.PAUSED, pausedFirst.state());
        assertEquals(StudySessionInterruptionReason.SUPERSEDED_BY_NEW_SESSION, pausedFirst.interruptionReason());

        // Advance past 24h TTL from second start => second & first expire into ABANDONED on next start
        clock.advance(25, ChronoUnit.HOURS);
        StudySessionStartResponse third = service.startSession(USER_A, startRequest("topic.third"));
        assertNull(third.autoPausedPriorSessionId());
        assertNotNull(third.expiredPriorSessionId());

        StudySessionStateDto abandonedSecond = service.getSessionById(USER_A, second.session().sessionId());
        assertEquals(StudySessionState.ABANDONED, abandonedSecond.state());
        assertEquals(StudySessionInterruptionReason.EXPIRED_INACTIVITY, abandonedSecond.interruptionReason());
    }

    @Test
    void checkpointsEnforceIdempotencyStaleIgnoreAndDurationClamp() {
        StudySessionStartResponse started = service.startSession(USER_A, startRequest("math.derivatives"));
        UUID sessionId = started.session().sessionId();

        clock.advanceSeconds(100);
        StudySessionCheckpointRequest req1 = new StudySessionCheckpointRequest(
            2L,
            1,
            StudySessionStepType.SOLVE_ITEM,
            StudySessionStepStatus.COMPLETED,
            500, // wall=100, skew=30 => maxAllowed=130 -> clamped
            true,
            clock.instant().minusSeconds(2),
            "sess_00000871_step_01_seq_0002",
            StudySessionInterruptionReason.NETWORK_LOST,
            "ios-device-0870",
            new StudySessionResumeSnapshot(1, "Chain rule note", 1, "substep_a", "anchor_1", null)
        );
        StudySessionCheckpointResponse resp1 = service.checkpointSession(
            USER_A,
            sessionId,
            "sess_00000871_step_01_seq_0002",
            req1
        );
        assertTrue(resp1.durationClamped());
        assertEquals(130, resp1.acceptedActiveDurationSeconds());
        assertFalse(resp1.idempotentReplay());
        assertFalse(resp1.staleCheckpointIgnored());
        assertEquals(2L, resp1.acceptedSequenceNumber());

        // Duplicate idempotency key => idempotent_replay=true without double counting
        StudySessionCheckpointResponse replay = service.checkpointSession(
            USER_A,
            sessionId,
            "sess_00000871_step_01_seq_0002",
            req1
        );
        assertTrue(replay.idempotentReplay());
        assertFalse(replay.staleCheckpointIgnored());
        assertEquals(130, replay.acceptedActiveDurationSeconds());
        assertEquals(1.0, meterRegistry.counter("study_session.checkpoint.accepted").count());
        assertEquals(1.0, meterRegistry.counter("study_session.checkpoint.idempotent_replay").count());

        // Out-of-order / stale sequence number with a distinct idempotency key
        StudySessionCheckpointRequest staleReq = new StudySessionCheckpointRequest(
            1L,
            0,
            StudySessionStepType.CONCEPT_PRIMER,
            StudySessionStepStatus.IN_PROGRESS,
            40,
            true,
            clock.instant().minusSeconds(60),
            "sess_00000871_step_00_seq_0001",
            null,
            null,
            new StudySessionResumeSnapshot(0, "Old note", 1, null, null, null)
        );
        StudySessionCheckpointResponse staleResp = service.checkpointSession(
            USER_A,
            sessionId,
            "sess_00000871_step_00_seq_0001",
            staleReq
        );
        assertTrue(staleResp.staleCheckpointIgnored());
        assertFalse(staleResp.idempotentReplay());
        assertEquals(2L, staleResp.acceptedSequenceNumber());
        assertEquals(130, staleResp.acceptedActiveDurationSeconds());
        assertEquals(1, staleResp.session().currentStepIndex());
        assertEquals(1.0, meterRegistry.counter("study_session.checkpoint.stale_ignored").count());
    }

    @Test
    void enforcesBolaIsolationKillSwitchAndGdprPurgeUnder500Ms() {
        StudySessionStartResponse started = service.startSession(USER_A, startRequest("math.integrals"));
        UUID sessionId = started.session().sessionId();

        // Cross-user BOLA returns 404-equivalent not found
        assertThrows(StudySessionNotFoundException.class, () -> service.getSessionById(USER_B, sessionId));
        assertThrows(
            StudySessionNotFoundException.class,
            () -> service.pauseSession(USER_B, sessionId, new StudySessionPauseRequest(StudySessionInterruptionReason.USER_PAUSED, null, null))
        );

        // Pause -> Resume accumulates paused duration
        clock.advanceSeconds(60);
        StudySessionStateDto paused = service.pauseSession(
            USER_A,
            sessionId,
            new StudySessionPauseRequest(StudySessionInterruptionReason.APP_BACKGROUNDED, 60, null)
        );
        assertEquals(StudySessionState.PAUSED, paused.state());

        clock.advanceSeconds(90);
        StudySessionStateDto resumed = service.resumeSession(USER_A, sessionId, new StudySessionResumeRequest("ios-device-0870", null));
        assertEquals(StudySessionState.ACTIVE, resumed.state());
        assertEquals(90, resumed.pausedDurationSeconds());

        // Add checkpoint before testing privacy export & purge
        clock.advanceSeconds(30);
        service.checkpointSession(
            USER_A,
            sessionId,
            "sess_00000871_step_01_seq_0001",
            new StudySessionCheckpointRequest(
                1L,
                1,
                StudySessionStepType.SOLVE_ITEM,
                StudySessionStepStatus.COMPLETED,
                80,
                false,
                clock.instant(),
                "sess_00000871_step_01_seq_0001",
                null,
                null,
                new StudySessionResumeSnapshot(1, "Integral u-sub", 0, null, null, null)
            )
        );

        // User B also owns a session that must survive User A's purge
        StudySessionStartResponse userBSession = service.startSession(USER_B, startRequest("math.limits"));

        Map<String, Object> exported = privacyContributor.exportUserData(USER_A);
        assertEquals("studySessions", privacyContributor.category());
        assertEquals(1, exported.get("sessionsCount"));
        assertEquals(1L, exported.get("checkpointsCount"));
        assertEquals(Boolean.FALSE, exported.get("trainingEligible"));

        long t0 = System.nanoTime();
        int purgedRows = privacyContributor.purgeUserData(USER_A);
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
        assertEquals(2, purgedRows);
        assertTrue(elapsedMs < 500, "GDPR purge took " + elapsedMs + " ms (limit 500 ms)");
        assertThrows(StudySessionNotFoundException.class, () -> service.getSessionById(USER_A, sessionId));
        assertNotNull(service.getSessionById(USER_B, userBSession.session().sessionId()));

        // Kill switch returns 503 STUDY_SESSION_ORCHESTRATION_DISABLED
        StudySessionOrchestrationService disabledService = new StudySessionOrchestrationService(
            repository,
            new StudySessionStateMachine(),
            StudySessionOrchestrationProperties.defaults().withEnabled(false),
            meterRegistry,
            clock
        );
        ApiProblemException ex = assertThrows(
            ApiProblemException.class,
            () -> disabledService.startSession(USER_B, startRequest("disabled.topic"))
        );
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.status());
        assertEquals(ApiErrorCode.STUDY_SESSION_ORCHESTRATION_DISABLED, ex.code());
    }

    @Test
    void completeSessionSupportsSameKeyReplayAndRejectsDifferentKey() {
        StudySessionStartResponse started = service.startSession(USER_A, startRequest("math.series"));
        UUID sessionId = started.session().sessionId();

        clock.advanceSeconds(240);
        StudySessionCompleteRequest req = new StudySessionCompleteRequest(
            2,
            240,
            "sess_00000871_complete_v1_0001",
            "Finished nicely",
            new StudySessionResumeSnapshot(2, "All verified", 0, null, null, null)
        );
        StudySessionCompleteResponse completed = service.completeSession(
            USER_A,
            sessionId,
            "sess_00000871_complete_v1_0001",
            req
        );
        assertEquals(StudySessionState.COMPLETED, completed.session().state());
        assertFalse(completed.idempotentReplay());
        assertEquals("study_session.complete.summary", completed.supportiveSummaryKey());
        assertFalse(completed.trainingEligible());

        // Same Idempotency-Key replay returns 200 equivalent without throwing 409
        StudySessionCompleteResponse replay = service.completeSession(
            USER_A,
            sessionId,
            "sess_00000871_complete_v1_0001",
            req
        );
        assertTrue(replay.idempotentReplay());
        assertEquals(StudySessionState.COMPLETED, replay.session().state());

        // Different Idempotency-Key on already COMPLETED throws 409 conflict
        StudySessionCompleteRequest differentKey = new StudySessionCompleteRequest(
            2,
            240,
            "sess_00000871_complete_v1_0002",
            null,
            null
        );
        assertThrows(
            IllegalSessionStateTransitionException.class,
            () -> service.completeSession(USER_A, sessionId, "sess_00000871_complete_v1_0002", differentKey)
        );
    }

    private static StudySessionStartRequest startRequest(String topicCode) {
        return new StudySessionStartRequest(
            null,
            StudySessionSourceContext.DAILY_COACH,
            topicCode,
            "tr-TR",
            3,
            "ios-device-00000875",
            List.of(
                new StudySessionStepBlueprintItem(0, StudySessionStepType.CONCEPT_PRIMER, "Step 0", 60, StudySessionBlueprintStatus.IN_PROGRESS, "SKILL_0", null),
                new StudySessionStepBlueprintItem(1, StudySessionStepType.SOLVE_ITEM, "Step 1", 120, StudySessionBlueprintStatus.PENDING, "SKILL_1", "prob-1"),
                new StudySessionStepBlueprintItem(2, StudySessionStepType.REFLECTION, "Step 2", 60, StudySessionBlueprintStatus.PENDING, "SKILL_1", null)
            ),
            null
        );
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        MutableClock(Instant initial) {
            this.current = initial;
        }

        void advanceSeconds(long seconds) {
            this.current = this.current.plusSeconds(seconds);
        }

        void advance(long amount, ChronoUnit unit) {
            this.current = this.current.plus(amount, unit);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
