package com.verifiedai.mistake;

import java.time.Instant;
import java.util.*;

/** Attempt-owned, validated evidence. No attempt implementation types cross this port. */
public interface MistakeDnaEvidenceSource {
    record Evidence(UUID classificationId, UUID attemptId, UUID evaluationId, UUID executionId,
        int workRevision, Instant observedAt, MistakeClassificationDecision decision,
        UUID skillEvidenceId, String exposureSkill, boolean verifiedExposure, boolean superseded) { }
    List<Evidence> evidence(UUID owner);
}
