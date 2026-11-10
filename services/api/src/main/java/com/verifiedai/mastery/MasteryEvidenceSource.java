package com.verifiedai.mastery;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Producer-owned accepted evidence. Implementations must validate lineage, not infer missing facts. */
public interface MasteryEvidenceSource {
    record Evidence(UUID id, UUID attemptId, UUID evaluationId, UUID executionId, UUID skillEvidenceId,
        String skill, Instant observedAt, LearningEvidenceType type, boolean correct, boolean verifiedTwin,
        String provenance) { }
    List<Evidence> evidence(UUID owner);
}
