package com.verifiedai.mastery;

import java.util.Optional;
import java.util.UUID;

public interface AttemptQueryPort {
    Optional<VerifiedAttemptSnapshot> getVerifiedAttemptForTwinGeneration(UUID tenantId, UUID userId, UUID attemptId);
}
