package com.verifiedai.studyplan.session;

import java.time.Instant;
import java.util.UUID;

public record StudySessionCheckpointRecord(
    UUID checkpointId,
    UUID sessionId,
    UUID userId,
    long sequenceNumber,
    int stepIndex,
    StudySessionStepType stepType,
    StudySessionStepStatus stepStatus,
    int reportedActiveDurationSeconds,
    int acceptedActiveDurationSeconds,
    boolean durationClamped,
    boolean offlineQueued,
    Instant clientRecordedAt,
    Instant serverReceivedAt,
    String idempotencyKey,
    StudySessionResumeSnapshot checkpointPayload,
    boolean trainingEligible
) {
}
