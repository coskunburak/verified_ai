package com.verifiedai.verification.application;

import com.verifiedai.verification.domain.model.VerificationPlan.Result;
import java.util.UUID;

/** Internal query only. Call outside a transaction; execution must later reauthorize this lineage. */
public interface VerificationPlanning {
    Result plan(UUID userId, UUID sessionId, UUID solutionId);
}
