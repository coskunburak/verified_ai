package com.verifiedai.mastery.domain;

import java.util.Objects;
import java.util.UUID;

public record OwnerKey(UUID tenantId, UUID userId) {
    public OwnerKey {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(userId, "userId cannot be null");
    }
}
