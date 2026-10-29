package com.verifiedai.verification.application;

import com.verifiedai.verification.domain.model.VerificationRun;
import java.util.UUID;

/** Internal owner-scoped operation; invoke outside transactions. */
public interface VerificationExecution {
    enum State { PUBLISHED, REUSED, BLOCKED, STALE_SOURCE }
    record Publication(State state, VerificationRun run) { }
    record History(VerificationRun run, boolean currentSource) { }
    Publication execute(UUID user, UUID session, UUID solution, UUID requestKey);
    History history(UUID user, UUID session, UUID solution, UUID requestKey);
}
