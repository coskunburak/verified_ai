package com.verifiedai.attempt.domain;
import java.util.UUID;
public record AttemptSourceIdentity(UUID canonicalId, int canonicalRevision, UUID parseId, int parseRevision) {
    public AttemptSourceIdentity {
        java.util.Objects.requireNonNull(canonicalId); java.util.Objects.requireNonNull(parseId);
        if(canonicalRevision<1 || parseRevision<1) throw new IllegalArgumentException("Invalid source revision");
    }
}
