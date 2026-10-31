package com.verifiedai.verification.domain.repository;

import com.verifiedai.verification.domain.model.VerificationDecision;
import java.util.*;

/** Methods participate in the caller's short owner-locked transaction. */
public interface VerificationDecisionRepository {
    Optional<VerificationDecision> find(UUID user, UUID session, UUID key);
    Optional<VerificationDecision> latest(UUID user, UUID session, UUID solution);
    VerificationDecision publish(VerificationDecision decision);
    List<VerificationDecision> export(UUID user);
    int attempts(UUID user, UUID session, UUID solution);
}
