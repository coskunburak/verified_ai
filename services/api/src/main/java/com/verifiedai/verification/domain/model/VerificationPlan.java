package com.verifiedai.verification.domain.model;

import com.verifiedai.solving.domain.model.canonical.CanonicalSolution;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** A local selection decision. It contains neither executed evidence nor a correctness verdict. */
public record VerificationPlan(UUID id, String policyVersion, String capabilityVersion,
    CanonicalSolution.Lineage lineage, UUID solutionId, CanonicalSolution.Version solutionVersion,
    List<Selection> methods, List<Reason> limitations) {
    public enum State { PLANNED, NOT_MATERIALIZED, SOURCE_UNAVAILABLE, STALE_SOURCE, REVIEW_REQUIRED, UNSUPPORTED }
    public enum Availability { AVAILABLE, PLANNED_NOT_EXECUTABLE, UNSUPPORTED, BLOCKED_BY_PRECONDITION }
    public enum ExecutionAvailability { EXECUTABLE, PARTIALLY_EXECUTABLE, NOT_EXECUTABLE }
    public enum Reason {
        METHODS_SELECTED, NOT_MATERIALIZED, SOURCE_UNAVAILABLE, LINEAGE_MISMATCH,
        HISTORICAL_SOURCE, HISTORICAL_VERSION, UNSUPPORTED_REPRESENTATION,
        AMBIGUOUS_SOURCE, UNRESOLVED_SOLUTION, INVALID_CANONICAL_STRUCTURE,
        UNSUPPORTED_PROBLEM_TYPE, UNSUPPORTED_TASK_TYPE, UNSUPPORTED_STATEMENT,
        UNKNOWN_FUNCTION, UNDECLARED_VARIABLE, UNSUPPORTED_POWER, MISSING_DOMAIN_RESTRICTION,
        SOURCE_REFERENCE_MISMATCH, UNTYPED_SOURCE_ASSUMPTION, INEQUALITY_NOT_SUPPORTED,
        PARENT_NOT_CURRENT, CAPABILITY_NOT_IMPLEMENTED, CANONICAL_EXECUTION_ADAPTER_UNAVAILABLE,
        DOMAIN_EXECUTOR_REQUIRED, COMPLETENESS_EXECUTOR_REQUIRED, UNKNOWN_VARIABLE_DOMAIN
    }
    public record Selection(VerificationMethod method, String version, int priority,
        VerificationMethod.Purpose purpose, List<VerificationMethod.Input> requiredInputs,
        Availability availability, Reason reason) {
        public Selection {
            Objects.requireNonNull(method); Objects.requireNonNull(availability); Objects.requireNonNull(reason);
            requiredInputs = List.copyOf(requiredInputs);
            if (!method.version().equals(version) || method.purpose() != purpose || priority < 1
                || !method.requiredInputs().equals(requiredInputs)) throw new IllegalArgumentException("Invalid method selection");
        }
    }
    public record Result(State state, Reason reason, VerificationPlan plan) {
        public Result {
            Objects.requireNonNull(state); Objects.requireNonNull(reason);
            if ((state == State.PLANNED) != (plan != null)) throw new IllegalArgumentException("Invalid planning result");
        }
        public static Result rejected(State state, Reason reason) { return new Result(state, reason, null); }
        public static Result planned(VerificationPlan plan) { return new Result(State.PLANNED, Reason.METHODS_SELECTED, plan); }
    }
    public VerificationPlan {
        Objects.requireNonNull(id); Objects.requireNonNull(policyVersion); Objects.requireNonNull(capabilityVersion);
        Objects.requireNonNull(lineage); Objects.requireNonNull(solutionId); Objects.requireNonNull(solutionVersion);
        methods = List.copyOf(methods); limitations = List.copyOf(limitations);
        if (methods.isEmpty() || methods.size() > VerificationMethod.values().length)
            throw new IllegalArgumentException("Invalid method count");
        var unique = java.util.EnumSet.noneOf(VerificationMethod.class);
        for (int i=0; i<methods.size(); i++) {
            if (!unique.add(methods.get(i).method()) || methods.get(i).priority() != i+1)
                throw new IllegalArgumentException("Invalid method order");
        }
    }
    public ExecutionAvailability executionAvailability() {
        long available = methods.stream().filter(m -> m.availability() == Availability.AVAILABLE).count();
        return available == 0 ? ExecutionAvailability.NOT_EXECUTABLE
            : available == methods.size() ? ExecutionAvailability.EXECUTABLE : ExecutionAvailability.PARTIALLY_EXECUTABLE;
    }
}
