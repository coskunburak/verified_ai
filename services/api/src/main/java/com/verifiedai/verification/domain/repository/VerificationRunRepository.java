package com.verifiedai.verification.domain.repository;

import com.verifiedai.verification.domain.model.VerificationRun;
import java.util.*;

/** All operations require a short owner-authorized transaction. */
public interface VerificationRunRepository {
    Optional<VerificationRun> find(UUID user, UUID session, UUID requestKey);
    Optional<VerificationRun> latest(UUID user, UUID session, UUID solution);
    VerificationRun publish(VerificationRun run);
    List<VerificationRun> export(UUID user);
}
