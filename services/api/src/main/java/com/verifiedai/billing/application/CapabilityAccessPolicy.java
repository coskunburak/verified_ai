package com.verifiedai.billing.application;

import java.util.UUID;

public interface CapabilityAccessPolicy {
    void requireBasicSolve(UUID userId);
}
