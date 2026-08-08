package com.verifiedai.sharedkernel.privacy;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public interface AccountDataLifecycleContributor {
    String category();

    Map<String, Object> exportUserData(UUID userId);

    void deleteUserData(UUID userId, Instant now);
}
