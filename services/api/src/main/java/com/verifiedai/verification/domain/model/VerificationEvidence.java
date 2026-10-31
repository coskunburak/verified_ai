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
    String correlationId, Instant createdAt, CalculusEvidence calculus) {
    public VerificationEvidence(UUID id, UUID executionId, UUID planId, String policyVersion,
        String capabilityVersion, PlanningSource.Identity source, UUID solutionId,
        CanonicalSolution.Version solutionVersion, VerificationMethod.Purpose claim,
        Map<String, PlanningSource.Domain> domainAssumptions, MathematicalExecution.Result result,
        String correlationId, Instant createdAt) {
        this(id,executionId,planId,policyVersion,capabilityVersion,source,solutionId,solutionVersion,claim,
            domainAssumptions,result,correlationId,createdAt,null);
    }
    public VerificationMethod method() { return result!=null ? result.method() : CalculusEvidence.method(calculus.result().method()); }
    public MathematicalExecution.Outcome outcome() { return result != null ? result.outcome() : calculus.result().outcome(); }
    public VerificationEvidence {
        Objects.requireNonNull(id); Objects.requireNonNull(executionId); Objects.requireNonNull(planId);
        Objects.requireNonNull(policyVersion); Objects.requireNonNull(capabilityVersion); Objects.requireNonNull(source);
        Objects.requireNonNull(solutionId); Objects.requireNonNull(solutionVersion);
        if (CanonicalSolution.Version.CALCULUS.equals(solutionVersion)!=(calculus!=null))
            throw new IllegalArgumentException("Evidence/solution version mismatch");
        if ((result==null)==(calculus==null)) throw new IllegalArgumentException("Exactly one evidence variant required");
        VerificationMethod method=result!=null ? result.method() : CalculusEvidence.method(calculus.result().method());
        if (calculus!=null && (!CanonicalSolution.Version.CALCULUS.equals(solutionVersion)
            || !com.verifiedai.verification.domain.policy.VerificationPlanner.CALCULUS_POLICY.equals(policyVersion)
            || !executionId.equals(calculus.result().executionId()) || !correlationId.equals(calculus.result().correlationId())
            || !calculus.result().scope().equals(CalculusEvidence.scope(source,solutionId))
            || !domainAssumptions.equals(Map.of(calculus.variable(),calculus.domain()))))
            throw new IllegalArgumentException("Invalid calculus publication lineage");
        Objects.requireNonNull(createdAt); domainAssumptions = Map.copyOf(domainAssumptions);
        if (!id.equals(identity(executionId, method)) || claim != method.purpose()
            || correlationId == null || !correlationId.matches("[A-Za-z0-9_-]{1,64}"))
            throw new IllegalArgumentException("Invalid evidence provenance");
    }
    public static UUID identity(UUID execution, VerificationMethod method) {
        return UUID.nameUUIDFromBytes((execution + "/" + method.name()).getBytes(StandardCharsets.UTF_8));
    }
}
