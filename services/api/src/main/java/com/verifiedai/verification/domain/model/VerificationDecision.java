package com.verifiedai.verification.domain.model;

import com.verifiedai.solving.domain.model.canonical.CanonicalSolution;
import java.time.Instant;
import java.util.*;

/** Immutable policy verdict. Mathematical content stays in its original evidence aggregate. */
public record VerificationDecision(String schemaVersion, UUID id, UUID requestKey, String policyVersion,
    PlanningSource.Identity source, UUID solutionId, CanonicalSolution.Version solutionVersion,
    UUID runId, UUID runRequestKey, UUID planId, String capabilityVersion, List<UUID> evidenceIds,
    SecondarySnapshot secondary, Status status, Scope scope, List<Reason> reasons,
    List<VerificationMethod> checked, List<VerificationMethod> unchecked, List<VerificationMethod> contradictions,
    List<Uncertainty> uncertainty, Recovery recovery, String correlationId, Instant createdAt) {
    public static final String SCHEMA = "verification-decision-v1";
    public static final String POLICY = "verification-policy-v1";
    public enum Status { VERIFIED, PARTIALLY_VERIFIED, UNVERIFIED }
    public enum Scope {
        NONE, CHECKED_METHOD_CLAIMS, FINAL_ANSWER, OPEN_INTERVAL, ANTIDERIVATIVE_REPRESENTATIVE, PUNCTURED_NEIGHBORHOOD;
        public static Scope forCalculus(VerificationMethod method) {
            return switch (method) {
                case DERIVATIVE_BACKCHECK -> OPEN_INTERVAL;
                case INTEGRAL_DIFFERENTIATION_BACKCHECK -> ANTIDERIVATIVE_REPRESENTATIVE;
                case FINITE_LIMIT -> PUNCTURED_NEIGHBORHOOD;
                default -> throw new IllegalArgumentException("Not a calculus claim");
            };
        }
    }
    // Declaration order is the public, stable priority order.
    public enum Reason {
        INVALID_EVIDENCE, STALE_LINEAGE, REVIEW_REQUIRED, COMPOSITE_UNAVAILABLE,
        SOURCE_SCOPE_UNRESOLVED, DETERMINISTIC_CONTRADICTION, EVIDENCE_CONFLICT,
        SECONDARY_DISAGREEMENT, SECONDARY_ASSUMPTIONS, SECONDARY_INVALID, SECONDARY_STALE,
        CAPABILITY_UNAVAILABLE, DOMAIN_UNCHECKED, COMPLETENESS_UNCHECKED, EXECUTION_FAILURE,
        MISSING_PROOF, RELEASE_DISABLED, SUBSET_PROVEN, REQUIRED_CLAIM_PROVEN, SECONDARY_AGREEMENT
    }
    public enum Uncertainty { EXPLANATION_STEPS_UNCHECKED, DOMAIN_UNRESOLVED, COMPLETENESS_UNRESOLVED,
        MISSING_MATHEMATICAL_PROOF, SECONDARY_CONFLICT, EXECUTION_UNAVAILABLE, RELEASE_NOT_ACCEPTED }
    public enum Recovery { NONE, REVIEW_SOURCE, REVIEW_CONTRADICTION, RECONCILE_SOLVERS,
        FRESH_CALCULATION, ADDITIONAL_EVIDENCE, UNSUPPORTED_CAPABILITY, RETRY_VERIFICATION,
        RETRY_LIMIT, BUDGET_BLOCKED, RATE_LIMITED, RELEASE_BLOCKED, INVALID_INPUT }
    public enum SecondarySignal { NONE, AGREEMENT, DISAGREEMENT, INDETERMINATE,
        INCOMPATIBLE_ASSUMPTIONS, INVALID_INDEPENDENCE, STALE_LINEAGE, PENDING }
    public record SecondarySnapshot(List<UUID> references, List<SecondarySignal> signals) {
        public SecondarySnapshot {
            references = references.stream().sorted().toList(); signals = signals.stream().sorted().toList();
            if (references.size() > 16 || references.stream().distinct().count() != references.size()
                || signals.isEmpty() || signals.size() > SecondarySignal.values().length
                || signals.stream().distinct().count() != signals.size()) throw new IllegalArgumentException("Invalid secondary snapshot");
        }
        public static SecondarySnapshot absent() { return new SecondarySnapshot(List.of(), List.of(SecondarySignal.NONE)); }
    }
    public VerificationDecision {
        if (!SCHEMA.equals(schemaVersion) || !POLICY.equals(policyVersion)) throw new IllegalArgumentException("Unsupported decision version");
        Objects.requireNonNull(id); Objects.requireNonNull(requestKey); Objects.requireNonNull(source);
        Objects.requireNonNull(solutionId); Objects.requireNonNull(solutionVersion); Objects.requireNonNull(runId);
        Objects.requireNonNull(runRequestKey); Objects.requireNonNull(planId); Objects.requireNonNull(secondary);
        Objects.requireNonNull(status); Objects.requireNonNull(scope); Objects.requireNonNull(recovery); Objects.requireNonNull(createdAt);
        evidenceIds = bounded(evidenceIds, 5); checked = bounded(checked, 5); unchecked = bounded(unchecked, 5);
        contradictions = bounded(contradictions, 5); reasons = bounded(reasons, Reason.values().length);
        uncertainty = bounded(uncertainty, Uncertainty.values().length);
        if (evidenceIds.isEmpty() || reasons.isEmpty() || capabilityVersion == null || capabilityVersion.length() > 64
            || correlationId == null || !correlationId.matches("[A-Za-z0-9_-]{1,64}")
            || checked.size() + unchecked.size() > 5 || !unchecked.containsAll(contradictions)
            || !Set.of(VerificationMethod.ARITHMETIC_EVALUATION, VerificationMethod.SYMBOLIC_EQUIVALENCE,
                VerificationMethod.EQUATION_SUBSTITUTION, VerificationMethod.DOMAIN_CONSTRAINT_CHECK,
                VerificationMethod.SOLUTION_SET_COMPLETENESS, VerificationMethod.DERIVATIVE_BACKCHECK,
                VerificationMethod.INTEGRAL_DIFFERENTIATION_BACKCHECK, VerificationMethod.FINITE_LIMIT).containsAll(checked)
            || !Collections.disjoint(checked, unchecked) || !Collections.disjoint(checked, contradictions)
            || !uncertainty.contains(Uncertainty.EXPLANATION_STEPS_UNCHECKED)) throw new IllegalArgumentException("Invalid decision scope");
        boolean calculus = CanonicalSolution.Version.CALCULUS.equals(solutionVersion);
        var calculusMethods = Set.of(VerificationMethod.DERIVATIVE_BACKCHECK,
            VerificationMethod.INTEGRAL_DIFFERENTIATION_BACKCHECK, VerificationMethod.FINITE_LIMIT);
        if (calculus ? checked.size() + unchecked.size() > 1 || !calculusMethods.containsAll(checked) || !calculusMethods.containsAll(unchecked)
            : checked.stream().anyMatch(calculusMethods::contains) || unchecked.stream().anyMatch(calculusMethods::contains))
            throw new IllegalArgumentException("Mixed decision evidence variants");
        if (status == Status.VERIFIED && (checked.isEmpty() || scope != (calculus ? Scope.forCalculus(checked.getFirst()) : Scope.FINAL_ANSWER)
            || checked.stream().noneMatch(m -> m != VerificationMethod.DOMAIN_CONSTRAINT_CHECK) || !unchecked.isEmpty()
            || !contradictions.isEmpty() || recovery != Recovery.NONE || !reasons.contains(Reason.REQUIRED_CLAIM_PROVEN)
            || uncertainty.size() != 1)) throw new IllegalArgumentException("Incomplete verified claim");
        if (status == Status.PARTIALLY_VERIFIED && (scope != Scope.CHECKED_METHOD_CLAIMS || checked.stream().noneMatch(m -> m != VerificationMethod.DOMAIN_CONSTRAINT_CHECK)
            || unchecked.isEmpty() || !contradictions.isEmpty())) throw new IllegalArgumentException("Invalid partial claim");
        if (status == Status.UNVERIFIED && scope != Scope.NONE) throw new IllegalArgumentException("Unverified claim scope");
    }
    private static <T> List<T> bounded(List<T> items, int max) {
        var copy = List.copyOf(items);
        if (copy.size() > max || copy.stream().distinct().count() != copy.size()) throw new IllegalArgumentException("Invalid decision list");
        return copy;
    }
}
