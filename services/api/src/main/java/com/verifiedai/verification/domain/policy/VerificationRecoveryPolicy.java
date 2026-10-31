package com.verifiedai.verification.domain.policy;

import com.verifiedai.verification.domain.model.VerificationDecision.Recovery;
import com.verifiedai.verification.domain.model.MathematicalExecution.Reason;
import java.time.Instant;
import java.util.Set;

/** Advice only: never dispatches work or reserves usage. Retries require an explicit command. */
public final class VerificationRecoveryPolicy {
    public static final int MAX_ATTEMPTS = 3;
    public Recovery infrastructure(Reason reason, int attempts, Instant now, Instant deadline,
        boolean budgetAvailable, boolean rateAllowed) {
        if (!Set.of(Reason.TIMEOUT, Reason.NETWORK_FAILURE, Reason.WORKER_FAILURE,
            Reason.RESOURCE_FAILURE, Reason.ADMISSION_LIMIT).contains(reason)) return Recovery.INVALID_INPUT;
        if (attempts < 1 || !now.isBefore(deadline) || attempts >= MAX_ATTEMPTS) return Recovery.RETRY_LIMIT;
        if (!budgetAvailable) return Recovery.BUDGET_BLOCKED;
        if (!rateAllowed || reason == Reason.ADMISSION_LIMIT) return Recovery.RATE_LIMITED;
        return Recovery.RETRY_VERIFICATION;
    }
}
