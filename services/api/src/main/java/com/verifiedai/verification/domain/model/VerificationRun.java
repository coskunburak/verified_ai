package com.verifiedai.verification.domain.model;

import com.verifiedai.solving.domain.model.canonical.CanonicalSolution;
import java.time.Instant;
import java.util.*;

/** Atomically published aggregate. Currentness is resolved at read time, never by mutating history. */
public record VerificationRun(UUID id, UUID requestKey, UUID planId, String policyVersion, String capabilityVersion,
    PlanningSource.Identity source, UUID solutionId, CanonicalSolution.Version solutionVersion,
    String correlationId, Instant createdAt, List<VerificationEvidence> evidence) {
    public VerificationRun {
        Objects.requireNonNull(id); Objects.requireNonNull(requestKey); Objects.requireNonNull(source);
        Objects.requireNonNull(solutionId); Objects.requireNonNull(solutionVersion); Objects.requireNonNull(createdAt);
        evidence = List.copyOf(evidence);
        if (evidence.isEmpty() || evidence.size() > 5 || evidence.stream().map(VerificationEvidence::method).distinct().count() != evidence.size())
            throw new IllegalArgumentException("Invalid evidence count");
        for (var e : evidence) {
            if (!id.equals(e.executionId()) || !planId.equals(e.planId()) || !policyVersion.equals(e.policyVersion())
                || !capabilityVersion.equals(e.capabilityVersion()) || !source.equals(e.source())
                || !solutionId.equals(e.solutionId()) || !solutionVersion.equals(e.solutionVersion())
                || !correlationId.equals(e.correlationId()) || !createdAt.equals(e.createdAt()))
                throw new IllegalArgumentException("Inconsistent evidence lineage");
        }
    }
}
