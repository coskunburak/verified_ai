package com.verifiedai.studyplan.session;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates durable study sessions, offline-safe monotonic checkpoints,
 * server-authoritative duration clamping, BOLA isolation, and interruption recovery
 * (Sprint 8.7, INV-SESS-001..005).
 */
@Service
public class StudySessionOrchestrationService {

    private static final String SCHEMA_VERSION = "1.0.0";
    private static final String SUPPORTIVE_SUMMARY_KEY = "study_session.complete.summary";

    private final StudySessionRepository repository;
    private final StudySessionStateMachine stateMachine;
    private final StudySessionOrchestrationProperties properties;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    @Autowired
    public StudySessionOrchestrationService(
        StudySessionRepository repository,
        StudySessionStateMachine stateMachine,
        StudySessionOrchestrationProperties properties,
        MeterRegistry meterRegistry
    ) {
        this(repository, stateMachine, properties, meterRegistry, Clock.systemUTC());
    }

    public StudySessionOrchestrationService(
        StudySessionRepository repository,
        StudySessionStateMachine stateMachine,
        StudySessionOrchestrationProperties properties,
        MeterRegistry meterRegistry,
        Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.stateMachine = Objects.requireNonNull(stateMachine, "stateMachine must not be null");
        this.properties = properties != null ? properties : StudySessionOrchestrationProperties.defaults();
        this.meterRegistry = meterRegistry != null ? meterRegistry : new SimpleMeterRegistry();
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    @Transactional
    public StudySessionStartResponse startSession(UUID userId, StudySessionStartRequest request) {
        ensureEnabled();
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(request, "request must not be null");
        validateStepBlueprint(request.totalSteps(), request.stepBlueprint());

        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.sessionInactivityTtlHours(), ChronoUnit.HOURS);

        UUID autoPausedPriorId = null;
        UUID expiredPriorId = null;

        List<StudySessionRecord> openSessions = repository.findOpenSessionsByUserId(userId);
        for (StudySessionRecord prior : openSessions) {
            if (stateMachine.isExpired(prior.expiresAt(), now)) {
                StudySessionRecord expired = expireSession(prior, now);
                if (expiredPriorId == null) {
                    expiredPriorId = expired.sessionId();
                }
            } else if (prior.state() == StudySessionState.ACTIVE) {
                stateMachine.assertTransition(prior.state(), StudySessionState.PAUSED);
                StudySessionRecord pausedPrior = new StudySessionRecord(
                    prior.sessionId(),
                    prior.userId(),
                    prior.coachSessionId(),
                    prior.sourceContext(),
                    prior.topicCode(),
                    prior.locale(),
                    StudySessionState.PAUSED,
                    prior.totalSteps(),
                    prior.currentStepIndex(),
                    prior.completedStepsCount(),
                    prior.lastSequenceNumber(),
                    prior.activeDurationSeconds(),
                    prior.pausedDurationSeconds(),
                    prior.clientInstanceId(),
                    StudySessionInterruptionReason.SUPERSEDED_BY_NEW_SESSION,
                    prior.stepBlueprint(),
                    prior.resumeSnapshot(),
                    prior.completeIdempotencyKey(),
                    false,
                    prior.startedAt(),
                    prior.lastCheckpointAt(),
                    now,
                    prior.resumedAt(),
                    prior.completedAt(),
                    expiresAt,
                    prior.createdAt(),
                    now
                );
                repository.updateSession(pausedPrior);
                meterRegistry.counter("study_session.auto_paused.count").increment();
                if (autoPausedPriorId == null) {
                    autoPausedPriorId = pausedPrior.sessionId();
                }
            }
        }

        List<StudySessionStepBlueprintItem> normalizedBlueprint = List.copyOf(request.stepBlueprint());
        StudySessionResumeSnapshot initialSnapshot = request.resumeSnapshot() != null
            ? request.resumeSnapshot()
            : StudySessionResumeSnapshot.empty();

        StudySessionRecord created = new StudySessionRecord(
            UUID.randomUUID(),
            userId,
            request.coachSessionId(),
            request.sourceContext(),
            request.topicCode().trim(),
            request.locale(),
            StudySessionState.ACTIVE,
            request.totalSteps(),
            0,
            countCompletedSteps(normalizedBlueprint),
            0L,
            0,
            0,
            request.clientInstanceId().trim(),
            null,
            normalizedBlueprint,
            initialSnapshot,
            null,
            false,
            now,
            null,
            null,
            null,
            null,
            expiresAt,
            now,
            now
        );
        repository.insertSession(created);
        meterRegistry.counter("study_session.start.count").increment();

        return new StudySessionStartResponse(
            SCHEMA_VERSION,
            created.toDto(),
            autoPausedPriorId,
            expiredPriorId,
            false
        );
    }

    @Transactional
    public StudySessionStateDto getActiveSession(UUID userId) {
        ensureEnabled();
        Objects.requireNonNull(userId, "userId must not be null");
        Instant now = clock.instant();

        List<StudySessionRecord> openSessions = repository.findOpenSessionsByUserId(userId);
        for (StudySessionRecord session : openSessions) {
            if (stateMachine.isExpired(session.expiresAt(), now)) {
                expireSession(session, now);
                continue;
            }
            if (session.state().isResumable()) {
                return session.toDto();
            }
        }
        throw new StudySessionNotFoundException("No active or paused study session found");
    }

    @Transactional
    public StudySessionStateDto getSessionById(UUID userId, UUID sessionId) {
        ensureEnabled();
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");

        StudySessionRecord session = repository.findBySessionIdAndUserId(sessionId, userId)
            .orElseThrow(() -> new StudySessionNotFoundException(sessionId));

        Instant now = clock.instant();
        if (!session.state().isTerminal() && stateMachine.isExpired(session.expiresAt(), now)) {
            session = expireSession(session, now);
        }
        return session.toDto();
    }

    @Transactional
    public StudySessionCheckpointResponse checkpointSession(
        UUID userId,
        UUID sessionId,
        String headerIdempotencyKey,
        StudySessionCheckpointRequest request
    ) {
        ensureEnabled();
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(request, "request must not be null");
        validateIdempotencyKeyHeader(headerIdempotencyKey, request.idempotencyKey());

        StudySessionRecord session = repository.findBySessionIdAndUserId(sessionId, userId)
            .orElseThrow(() -> new StudySessionNotFoundException(sessionId));

        Optional<StudySessionCheckpointRecord> replay = repository.findCheckpointBySessionAndIdempotencyKey(
            sessionId,
            userId,
            request.idempotencyKey()
        );
        if (replay.isPresent()) {
            meterRegistry.counter("study_session.checkpoint.idempotent_replay").increment();
            StudySessionCheckpointRecord existing = replay.get();
            return new StudySessionCheckpointResponse(
                SCHEMA_VERSION,
                session.toDto(),
                existing.sequenceNumber(),
                existing.acceptedActiveDurationSeconds(),
                existing.durationClamped(),
                true,
                false,
                false
            );
        }

        Instant now = clock.instant();
        if (!session.state().isTerminal() && stateMachine.isExpired(session.expiresAt(), now)) {
            expireSession(session, now);
            throw new IllegalSessionStateTransitionException(
                session.state(),
                StudySessionState.ABANDONED,
                "Session expired after inactivity TTL"
            );
        }

        if (session.state().isTerminal()) {
            throw new IllegalSessionStateTransitionException(
                session.state(),
                session.state(),
                "Cannot checkpoint terminal study session"
            );
        }

        if (request.stepIndex() < 0 || request.stepIndex() >= session.totalSteps()) {
            throw new StudySessionInvalidCheckpointException(
                "step_index " + request.stepIndex() + " out of bounds for total_steps=" + session.totalSteps()
            );
        }

        if (stateMachine.isStaleSequence(request.sequenceNumber(), session.lastSequenceNumber())) {
            meterRegistry.counter("study_session.checkpoint.stale_ignored").increment();
            return new StudySessionCheckpointResponse(
                SCHEMA_VERSION,
                session.toDto(),
                session.lastSequenceNumber(),
                session.activeDurationSeconds(),
                false,
                false,
                true,
                false
            );
        }

        StudySessionStateMachine.DurationClampResult clamp = stateMachine.clampActiveDuration(
            session.startedAt(),
            session.pausedDurationSeconds(),
            session.activeDurationSeconds(),
            request.reportedActiveDurationSeconds(),
            now,
            properties.sessionClockSkewToleranceSeconds(),
            properties.sessionMaxActiveDurationSeconds()
        );
        if (clamp.clamped()) {
            meterRegistry.counter("study_session.duration.clamped").increment();
        }

        StudySessionState nextState = session.state();
        if (nextState == StudySessionState.INITIALIZED) {
            stateMachine.assertTransition(nextState, StudySessionState.ACTIVE);
            nextState = StudySessionState.ACTIVE;
        }

        List<StudySessionStepBlueprintItem> updatedBlueprint = applyBlueprintProgress(
            session.stepBlueprint(),
            request.stepIndex(),
            request.stepStatus()
        );
        int completedCount = countCompletedSteps(updatedBlueprint);
        String nextClientInstanceId = request.clientInstanceId() != null && !request.clientInstanceId().isBlank()
            ? request.clientInstanceId().trim()
            : session.clientInstanceId();
        Instant newExpiresAt = now.plus(properties.sessionInactivityTtlHours(), ChronoUnit.HOURS);

        StudySessionResumeSnapshot persistedSnapshot = request.resumeSnapshot().withOfflineQueuedCount(0);

        StudySessionCheckpointRecord checkpoint = new StudySessionCheckpointRecord(
            UUID.randomUUID(),
            session.sessionId(),
            userId,
            request.sequenceNumber(),
            request.stepIndex(),
            request.stepType(),
            request.stepStatus(),
            request.reportedActiveDurationSeconds(),
            clamp.acceptedSeconds(),
            clamp.clamped(),
            request.offlineQueued(),
            request.clientRecordedAt(),
            now,
            request.idempotencyKey(),
            persistedSnapshot,
            false
        );

        try {
            repository.insertCheckpoint(checkpoint);
        } catch (DuplicateKeyException duplicateEx) {
            Optional<StudySessionCheckpointRecord> raceReplay = repository.findCheckpointBySessionAndIdempotencyKey(
                sessionId,
                userId,
                request.idempotencyKey()
            );
            if (raceReplay.isPresent()) {
                meterRegistry.counter("study_session.checkpoint.idempotent_replay").increment();
                StudySessionCheckpointRecord existing = raceReplay.get();
                return new StudySessionCheckpointResponse(
                    SCHEMA_VERSION,
                    session.toDto(),
                    existing.sequenceNumber(),
                    existing.acceptedActiveDurationSeconds(),
                    existing.durationClamped(),
                    true,
                    false,
                    false
                );
            }
            throw duplicateEx;
        }

        StudySessionRecord updatedSession = new StudySessionRecord(
            session.sessionId(),
            session.userId(),
            session.coachSessionId(),
            session.sourceContext(),
            session.topicCode(),
            session.locale(),
            nextState,
            session.totalSteps(),
            request.stepIndex(),
            completedCount,
            request.sequenceNumber(),
            clamp.acceptedSeconds(),
            session.pausedDurationSeconds(),
            nextClientInstanceId,
            request.interruptionReason() != null ? request.interruptionReason() : session.interruptionReason(),
            updatedBlueprint,
            persistedSnapshot,
            session.completeIdempotencyKey(),
            false,
            session.startedAt(),
            now,
            session.pausedAt(),
            session.resumedAt(),
            session.completedAt(),
            newExpiresAt,
            session.createdAt(),
            now
        );
        repository.updateSession(updatedSession);
        meterRegistry.counter("study_session.checkpoint.accepted").increment();

        return new StudySessionCheckpointResponse(
            SCHEMA_VERSION,
            updatedSession.toDto(),
            request.sequenceNumber(),
            clamp.acceptedSeconds(),
            clamp.clamped(),
            false,
            false,
            false
        );
    }

    @Transactional
    public StudySessionStateDto pauseSession(
        UUID userId,
        UUID sessionId,
        StudySessionPauseRequest request
    ) {
        ensureEnabled();
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");

        StudySessionRecord session = repository.findBySessionIdAndUserId(sessionId, userId)
            .orElseThrow(() -> new StudySessionNotFoundException(sessionId));

        Instant now = clock.instant();
        if (!session.state().isTerminal() && stateMachine.isExpired(session.expiresAt(), now)) {
            expireSession(session, now);
            throw new IllegalSessionStateTransitionException(
                session.state(),
                StudySessionState.ABANDONED,
                "Cannot pause expired study session"
            );
        }

        stateMachine.assertTransition(session.state(), StudySessionState.PAUSED);

        int nextActiveSeconds = session.activeDurationSeconds();
        if (request != null && request.reportedActiveDurationSeconds() != null) {
            StudySessionStateMachine.DurationClampResult clamp = stateMachine.clampActiveDuration(
                session.startedAt(),
                session.pausedDurationSeconds(),
                session.activeDurationSeconds(),
                request.reportedActiveDurationSeconds(),
                now,
                properties.sessionClockSkewToleranceSeconds(),
                properties.sessionMaxActiveDurationSeconds()
            );
            nextActiveSeconds = clamp.acceptedSeconds();
            if (clamp.clamped()) {
                meterRegistry.counter("study_session.duration.clamped").increment();
            }
        }

        StudySessionInterruptionReason reason = request != null && request.interruptionReason() != null
            ? request.interruptionReason()
            : StudySessionInterruptionReason.USER_PAUSED;
        StudySessionResumeSnapshot snapshot = request != null && request.resumeSnapshot() != null
            ? request.resumeSnapshot()
            : session.resumeSnapshot();
        Instant newExpiresAt = now.plus(properties.sessionInactivityTtlHours(), ChronoUnit.HOURS);

        StudySessionRecord paused = new StudySessionRecord(
            session.sessionId(),
            session.userId(),
            session.coachSessionId(),
            session.sourceContext(),
            session.topicCode(),
            session.locale(),
            StudySessionState.PAUSED,
            session.totalSteps(),
            session.currentStepIndex(),
            session.completedStepsCount(),
            session.lastSequenceNumber(),
            nextActiveSeconds,
            session.pausedDurationSeconds(),
            session.clientInstanceId(),
            reason,
            session.stepBlueprint(),
            snapshot,
            session.completeIdempotencyKey(),
            false,
            session.startedAt(),
            session.lastCheckpointAt(),
            now,
            session.resumedAt(),
            session.completedAt(),
            newExpiresAt,
            session.createdAt(),
            now
        );
        repository.updateSession(paused);
        meterRegistry.counter("study_session.pause.count").increment();
        return paused.toDto();
    }

    @Transactional
    public StudySessionStateDto resumeSession(
        UUID userId,
        UUID sessionId,
        StudySessionResumeRequest request
    ) {
        ensureEnabled();
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");

        StudySessionRecord session = repository.findBySessionIdAndUserId(sessionId, userId)
            .orElseThrow(() -> new StudySessionNotFoundException(sessionId));

        Instant now = clock.instant();
        if (!session.state().isTerminal() && stateMachine.isExpired(session.expiresAt(), now)) {
            expireSession(session, now);
            throw new IllegalSessionStateTransitionException(
                session.state(),
                StudySessionState.ABANDONED,
                "Cannot resume expired study session"
            );
        }

        stateMachine.assertTransition(session.state(), StudySessionState.ACTIVE);

        for (StudySessionRecord other : repository.findOpenSessionsByUserId(userId)) {
            if (!other.sessionId().equals(sessionId) && other.state() == StudySessionState.ACTIVE) {
                Instant otherExpiresAt = now.plus(properties.sessionInactivityTtlHours(), ChronoUnit.HOURS);
                repository.updateSession(new StudySessionRecord(
                    other.sessionId(),
                    other.userId(),
                    other.coachSessionId(),
                    other.sourceContext(),
                    other.topicCode(),
                    other.locale(),
                    StudySessionState.PAUSED,
                    other.totalSteps(),
                    other.currentStepIndex(),
                    other.completedStepsCount(),
                    other.lastSequenceNumber(),
                    other.activeDurationSeconds(),
                    other.pausedDurationSeconds(),
                    other.clientInstanceId(),
                    StudySessionInterruptionReason.SUPERSEDED_BY_NEW_SESSION,
                    other.stepBlueprint(),
                    other.resumeSnapshot(),
                    other.completeIdempotencyKey(),
                    false,
                    other.startedAt(),
                    other.lastCheckpointAt(),
                    now,
                    other.resumedAt(),
                    other.completedAt(),
                    otherExpiresAt,
                    other.createdAt(),
                    now
                ));
                meterRegistry.counter("study_session.auto_paused.count").increment();
            }
        }

        int additionalPaused = computeAdditionalPausedSeconds(session, now);
        int totalPausedSeconds = session.pausedDurationSeconds() + additionalPaused;
        String nextClientInstanceId = request != null && request.clientInstanceId() != null && !request.clientInstanceId().isBlank()
            ? request.clientInstanceId().trim()
            : session.clientInstanceId();
        StudySessionResumeSnapshot snapshot = request != null && request.resumeSnapshot() != null
            ? request.resumeSnapshot()
            : session.resumeSnapshot();
        Instant newExpiresAt = now.plus(properties.sessionInactivityTtlHours(), ChronoUnit.HOURS);

        StudySessionRecord resumed = new StudySessionRecord(
            session.sessionId(),
            session.userId(),
            session.coachSessionId(),
            session.sourceContext(),
            session.topicCode(),
            session.locale(),
            StudySessionState.ACTIVE,
            session.totalSteps(),
            session.currentStepIndex(),
            session.completedStepsCount(),
            session.lastSequenceNumber(),
            session.activeDurationSeconds(),
            totalPausedSeconds,
            nextClientInstanceId,
            null,
            session.stepBlueprint(),
            snapshot,
            session.completeIdempotencyKey(),
            false,
            session.startedAt(),
            session.lastCheckpointAt(),
            null,
            now,
            session.completedAt(),
            newExpiresAt,
            session.createdAt(),
            now
        );
        repository.updateSession(resumed);
        meterRegistry.counter("study_session.resume.count").increment();
        return resumed.toDto();
    }

    @Transactional
    public StudySessionCompleteResponse completeSession(
        UUID userId,
        UUID sessionId,
        String headerIdempotencyKey,
        StudySessionCompleteRequest request
    ) {
        ensureEnabled();
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(request, "request must not be null");
        validateIdempotencyKeyHeader(headerIdempotencyKey, request.idempotencyKey());

        StudySessionRecord session = repository.findBySessionIdAndUserId(sessionId, userId)
            .orElseThrow(() -> new StudySessionNotFoundException(sessionId));

        if (session.state() == StudySessionState.COMPLETED) {
            if (request.idempotencyKey().equals(session.completeIdempotencyKey())) {
                return new StudySessionCompleteResponse(
                    SCHEMA_VERSION,
                    session.toDto(),
                    session.activeDurationSeconds(),
                    false,
                    true,
                    SUPPORTIVE_SUMMARY_KEY,
                    false
                );
            }
            throw new IllegalSessionStateTransitionException(
                session.state(),
                StudySessionState.COMPLETED,
                "Study session already completed under a different Idempotency-Key"
            );
        }

        Instant now = clock.instant();
        if (!session.state().isTerminal() && stateMachine.isExpired(session.expiresAt(), now)) {
            expireSession(session, now);
            throw new IllegalSessionStateTransitionException(
                session.state(),
                StudySessionState.ABANDONED,
                "Cannot complete expired study session"
            );
        }

        stateMachine.assertTransition(session.state(), StudySessionState.COMPLETED);

        if (request.finalStepIndex() < 0 || request.finalStepIndex() >= session.totalSteps()) {
            throw new StudySessionInvalidCheckpointException(
                "final_step_index " + request.finalStepIndex() + " out of bounds for total_steps=" + session.totalSteps()
            );
        }

        int additionalPaused = computeAdditionalPausedSeconds(session, now);
        int totalPausedSeconds = session.pausedDurationSeconds() + additionalPaused;

        StudySessionStateMachine.DurationClampResult clamp = stateMachine.clampActiveDuration(
            session.startedAt(),
            totalPausedSeconds,
            session.activeDurationSeconds(),
            request.reportedActiveDurationSeconds(),
            now,
            properties.sessionClockSkewToleranceSeconds(),
            properties.sessionMaxActiveDurationSeconds()
        );
        if (clamp.clamped()) {
            meterRegistry.counter("study_session.duration.clamped").increment();
        }

        List<StudySessionStepBlueprintItem> completedBlueprint = applyBlueprintProgress(
            session.stepBlueprint(),
            request.finalStepIndex(),
            StudySessionStepStatus.COMPLETED
        );
        int completedCount = countCompletedSteps(completedBlueprint);
        StudySessionResumeSnapshot finalSnapshot = (request.resumeSnapshot() != null
            ? request.resumeSnapshot()
            : session.resumeSnapshot()).withOfflineQueuedCount(0);

        StudySessionRecord completed = new StudySessionRecord(
            session.sessionId(),
            session.userId(),
            session.coachSessionId(),
            session.sourceContext(),
            session.topicCode(),
            session.locale(),
            StudySessionState.COMPLETED,
            session.totalSteps(),
            request.finalStepIndex(),
            completedCount,
            session.lastSequenceNumber(),
            clamp.acceptedSeconds(),
            totalPausedSeconds,
            session.clientInstanceId(),
            null,
            completedBlueprint,
            finalSnapshot,
            request.idempotencyKey(),
            false,
            session.startedAt(),
            now,
            null,
            session.resumedAt(),
            now,
            session.expiresAt(),
            session.createdAt(),
            now
        );
        repository.updateSession(completed);
        meterRegistry.counter("study_session.complete.count").increment();

        return new StudySessionCompleteResponse(
            SCHEMA_VERSION,
            completed.toDto(),
            clamp.acceptedSeconds(),
            clamp.clamped(),
            false,
            SUPPORTIVE_SUMMARY_KEY,
            false
        );
    }

    @Transactional
    public StudySessionStateDto abandonSession(UUID userId, UUID sessionId, StudySessionInterruptionReason reason) {
        ensureEnabled();
        StudySessionRecord session = repository.findBySessionIdAndUserId(sessionId, userId)
            .orElseThrow(() -> new StudySessionNotFoundException(sessionId));
        stateMachine.assertTransition(session.state(), StudySessionState.ABANDONED);
        Instant now = clock.instant();
        StudySessionRecord abandoned = new StudySessionRecord(
            session.sessionId(),
            session.userId(),
            session.coachSessionId(),
            session.sourceContext(),
            session.topicCode(),
            session.locale(),
            StudySessionState.ABANDONED,
            session.totalSteps(),
            session.currentStepIndex(),
            session.completedStepsCount(),
            session.lastSequenceNumber(),
            session.activeDurationSeconds(),
            session.pausedDurationSeconds(),
            session.clientInstanceId(),
            reason != null ? reason : StudySessionInterruptionReason.USER_PAUSED,
            session.stepBlueprint(),
            session.resumeSnapshot(),
            session.completeIdempotencyKey(),
            false,
            session.startedAt(),
            session.lastCheckpointAt(),
            session.pausedAt(),
            session.resumedAt(),
            session.completedAt(),
            session.expiresAt(),
            session.createdAt(),
            now
        );
        repository.updateSession(abandoned);
        return abandoned.toDto();
    }

    private StudySessionRecord expireSession(StudySessionRecord session, Instant now) {
        StudySessionRecord expired = new StudySessionRecord(
            session.sessionId(),
            session.userId(),
            session.coachSessionId(),
            session.sourceContext(),
            session.topicCode(),
            session.locale(),
            StudySessionState.ABANDONED,
            session.totalSteps(),
            session.currentStepIndex(),
            session.completedStepsCount(),
            session.lastSequenceNumber(),
            session.activeDurationSeconds(),
            session.pausedDurationSeconds(),
            session.clientInstanceId(),
            StudySessionInterruptionReason.EXPIRED_INACTIVITY,
            session.stepBlueprint(),
            session.resumeSnapshot(),
            session.completeIdempotencyKey(),
            false,
            session.startedAt(),
            session.lastCheckpointAt(),
            session.pausedAt(),
            session.resumedAt(),
            session.completedAt(),
            session.expiresAt(),
            session.createdAt(),
            now
        );
        repository.updateSession(expired);
        return expired;
    }

    private void ensureEnabled() {
        if (!properties.isEnabled()) {
            throw new ApiProblemException(
                HttpStatus.SERVICE_UNAVAILABLE,
                ApiErrorCode.STUDY_SESSION_ORCHESTRATION_DISABLED,
                "Study session orchestration is temporarily unavailable",
                true,
                "RETRY_LATER"
            );
        }
    }

    private static void validateIdempotencyKeyHeader(String headerKey, String bodyKey) {
        if (headerKey == null || headerKey.isBlank() || headerKey.length() < 16 || headerKey.length() > 128) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.REQUEST_VALIDATION_FAILED,
                "Idempotency-Key header is required and must be 16..128 characters",
                true,
                "PROVIDE_IDEMPOTENCY_KEY"
            );
        }
        if (!headerKey.equals(bodyKey)) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.REQUEST_VALIDATION_FAILED,
                "Idempotency-Key header must match body idempotency_key",
                true,
                "MATCH_IDEMPOTENCY_KEY"
            );
        }
    }

    private static void validateStepBlueprint(int totalSteps, List<StudySessionStepBlueprintItem> items) {
        if (items == null || items.size() != totalSteps) {
            throw new StudySessionInvalidCheckpointException(
                "step_blueprint size must equal total_steps (" + totalSteps + ")"
            );
        }
        for (int i = 0; i < totalSteps; i++) {
            if (items.get(i).stepIndex() != i) {
                throw new StudySessionInvalidCheckpointException(
                    "step_blueprint step_index must be contiguous 0.." + (totalSteps - 1)
                );
            }
        }
    }

    private static List<StudySessionStepBlueprintItem> applyBlueprintProgress(
        List<StudySessionStepBlueprintItem> current,
        int stepIndex,
        StudySessionStepStatus reportedStatus
    ) {
        List<StudySessionStepBlueprintItem> updated = new ArrayList<>(current.size());
        StudySessionBlueprintStatus targetStatus = switch (reportedStatus) {
            case IN_PROGRESS -> StudySessionBlueprintStatus.IN_PROGRESS;
            case COMPLETED -> StudySessionBlueprintStatus.COMPLETED;
            case SKIPPED -> StudySessionBlueprintStatus.SKIPPED;
        };
        for (StudySessionStepBlueprintItem item : current) {
            if (item.stepIndex() == stepIndex && item.status() != StudySessionBlueprintStatus.COMPLETED) {
                updated.add(item.withStatus(targetStatus));
            } else {
                updated.add(item);
            }
        }
        return List.copyOf(updated);
    }

    private static int countCompletedSteps(List<StudySessionStepBlueprintItem> blueprint) {
        int count = 0;
        for (StudySessionStepBlueprintItem item : blueprint) {
            if (item.status() == StudySessionBlueprintStatus.COMPLETED) {
                count++;
            }
        }
        return count;
    }

    private static int computeAdditionalPausedSeconds(StudySessionRecord session, Instant now) {
        if (session.state() != StudySessionState.PAUSED || session.pausedAt() == null) {
            return 0;
        }
        long elapsed = Math.max(0L, Duration.between(session.pausedAt(), now).getSeconds());
        return (int) Math.min(Integer.MAX_VALUE - session.pausedDurationSeconds(), elapsed);
    }
}
