package com.verifiedai.studyplan.session;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StudySessionRecord(
    UUID sessionId,
    UUID userId,
    UUID coachSessionId,
    StudySessionSourceContext sourceContext,
    String topicCode,
    String locale,
    StudySessionState state,
    int totalSteps,
    int currentStepIndex,
    int completedStepsCount,
    long lastSequenceNumber,
    int activeDurationSeconds,
    int pausedDurationSeconds,
    String clientInstanceId,
    StudySessionInterruptionReason interruptionReason,
    List<StudySessionStepBlueprintItem> stepBlueprint,
    StudySessionResumeSnapshot resumeSnapshot,
    String completeIdempotencyKey,
    boolean trainingEligible,
    Instant startedAt,
    Instant lastCheckpointAt,
    Instant pausedAt,
    Instant resumedAt,
    Instant completedAt,
    Instant expiresAt,
    Instant createdAt,
    Instant updatedAt
) {
    public StudySessionStateDto toDto() {
        return new StudySessionStateDto(
            "1.0.0",
            sessionId,
            userId,
            coachSessionId,
            sourceContext,
            topicCode,
            locale,
            state,
            totalSteps,
            currentStepIndex,
            completedStepsCount,
            lastSequenceNumber,
            activeDurationSeconds,
            pausedDurationSeconds,
            clientInstanceId,
            interruptionReason,
            stepBlueprint,
            resumeSnapshot,
            startedAt,
            lastCheckpointAt,
            pausedAt,
            resumedAt,
            completedAt,
            expiresAt,
            false
        );
    }
}
