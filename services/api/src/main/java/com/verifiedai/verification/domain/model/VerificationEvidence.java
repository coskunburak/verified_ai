package com.verifiedai.verification.domain.model;

import com.verifiedai.solving.domain.model.canonical.CanonicalSolution;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/** Immutable method claim and its complete publication provenance. No raw input or explanation. */
public record VerificationEvidence(UUID id, UUID executionId, UUID planId, String policyVersion,
    String capabilityVersion, PlanningSource.Identity source, UUID solutionId,
    CanonicalSolution.Version solutionVersion, VerificationMethod.Purpose claim,
    Map<String, PlanningSource.Domain> domainAssumptions, MathematicalExecution.Result result,
    String correlationId, Instant createdAt) {
    public VerificationEvidence {
        Objects.requireNonNull(id); Objects.requireNonNull(executionId); Objects.requireNonNull(planId);
        Objects.requireNonNull(policyVersion); Objects.requireNonNull(capabilityVersion); Objects.requireNonNull(source);
        Objects.requireNonNull(solutionId); Objects.requireNonNull(solutionVersion); Objects.requireNonNull(result);
        Objects.requireNonNull(createdAt); domainAssumptions = Map.copyOf(domainAssumptions);
        if (!id.equals(identity(executionId, result.method())) || claim != result.method().purpose()
            || correlationId == null || !correlationId.matches("[A-Za-z0-9_-]{1,64}"))
            throw new IllegalArgumentException("Invalid evidence provenance");
    }
    public static UUID identity(UUID execution, VerificationMethod method) {
        return UUID.nameUUIDFromBytes((execution + "/" + method.name()).getBytes(StandardCharsets.UTF_8));
    }
}
