package com.verifiedai.verification.domain.model;

import com.verifiedai.solving.domain.model.canonical.MathStatement;
import java.math.BigInteger;
import java.util.*;

/** Method evidence only. No product verification status exists in this contract. */
public final class MathematicalExecution {
    private MathematicalExecution() { }
    public static final String REQUEST_VERSION = "verification-execution-v1";
    public static final String RESPONSE_VERSION = "verification-execution-result-v1";
    public static final String VERIFIER_VERSION = "canonical-executor-v1";
    public enum Outcome { PROVEN, DISPROVEN, INCONCLUSIVE, UNSUPPORTED, BLOCKED, EXECUTION_FAILED }
    public enum Reason {
        EXACT_EQUAL, EXACT_UNEQUAL, POLYNOMIAL_IDENTITY, POLYNOMIAL_NONIDENTITY,
        VALID_ROOT, INVALID_ROOT, DOMAIN_SATISFIED, DOMAIN_VIOLATION, UNDEFINED_SOURCE,
        UNDEFINED_VALUE, UNSUPPORTED_SUBSET, COMPLEXITY_LIMIT, UNKNOWN_DOMAIN,
        MISSING_RESTRICTION, CONTRADICTORY_CONSTRAINTS, ASSIGNMENT_REQUIRED,
        UNIQUE_SOLUTION, INCOMPLETE_SOLUTION_SET, ROOT_SET_NOT_REPRESENTABLE,
        TIMEOUT, RESOURCE_FAILURE, WORKER_FAILURE, ADMISSION_LIMIT, DISABLED, NETWORK_FAILURE, INVALID_RESPONSE
    }
    public enum Descriptor {
        EXACT_RATIONAL_COMPARISON, POLYNOMIAL_COEFFICIENT_COMPARISON,
        ORIGINAL_EQUATION_SUBSTITUTION, DOMAIN_CHECK, BOUNDED_POLYNOMIAL_ROOT_SET
    }
    public record ExactValue(String numerator, String denominator) {
        public ExactValue {
            if (numerator == null || denominator == null || numerator.equals("-0") || numerator.length() > 320 || denominator.length() > 320
                || !numerator.matches("-?(0|[1-9][0-9]*)") || !denominator.matches("[1-9][0-9]*"))
                throw new IllegalArgumentException("Invalid exact value");
            var n = new BigInteger(numerator); var d = new BigInteger(denominator);
            if (n.abs().bitLength() > 1024 || d.bitLength() > 1024 || !n.gcd(d).equals(BigInteger.ONE))
                throw new IllegalArgumentException("Unbounded or noncanonical fraction");
        }
    }
    public record Proof(Descriptor descriptor, ExactValue leftValue, ExactValue rightValue, Integer distinctRootCount) {
        public Proof {
            Objects.requireNonNull(descriptor);
            boolean values = descriptor == Descriptor.EXACT_RATIONAL_COMPARISON || descriptor == Descriptor.ORIGINAL_EQUATION_SUBSTITUTION;
            if (values != (leftValue != null && rightValue != null) || (leftValue == null) != (rightValue == null)
                || (descriptor == Descriptor.BOUNDED_POLYNOMIAL_ROOT_SET) != (distinctRootCount != null)
                || distinctRootCount != null && (distinctRootCount < 0 || distinctRootCount > 2))
                throw new IllegalArgumentException("Invalid proof descriptor");
        }
    }
    public record Request(UUID executionId, String correlationId, VerificationMethod method,
        MathStatement original, MathStatement candidate, Map<String, PlanningSource.Domain> variables,
        List<PlanningSource.Restriction> restrictions) {
        public Request {
            Objects.requireNonNull(executionId); Objects.requireNonNull(method);
            Objects.requireNonNull(original); Objects.requireNonNull(candidate);
            if (correlationId == null || !correlationId.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid correlation ID");
            variables = Map.copyOf(variables); restrictions = List.copyOf(restrictions);
            if (variables.size() > 16 || restrictions.size() > 48) throw new IllegalArgumentException("Execution limits exceeded");
        }
    }
    public record Result(VerificationMethod method, String methodVersion, String verifierVersion,
        Outcome outcome, Reason reasonCode, List<String> restrictionIds, Proof proof) {
        public Result {
            Objects.requireNonNull(method); Objects.requireNonNull(outcome); Objects.requireNonNull(reasonCode);
            restrictionIds = List.copyOf(restrictionIds);
            if (!"v1".equals(methodVersion) || !VERIFIER_VERSION.equals(verifierVersion)
                || restrictionIds.size() > 48 || new HashSet<>(restrictionIds).size() != restrictionIds.size()
                || ((outcome == Outcome.PROVEN || outcome == Outcome.DISPROVEN) != (proof != null)))
                throw new IllegalArgumentException("Invalid mathematical result");
            if (proof != null) {
                var expected = switch (method) {
                    case ARITHMETIC_EVALUATION -> Descriptor.EXACT_RATIONAL_COMPARISON;
                    case SYMBOLIC_EQUIVALENCE -> Descriptor.POLYNOMIAL_COEFFICIENT_COMPARISON;
                    case EQUATION_SUBSTITUTION -> Descriptor.ORIGINAL_EQUATION_SUBSTITUTION;
                    case DOMAIN_CONSTRAINT_CHECK -> Descriptor.DOMAIN_CHECK;
                    case SOLUTION_SET_COMPLETENESS -> Descriptor.BOUNDED_POLYNOMIAL_ROOT_SET;
                    default -> throw new IllegalArgumentException("Unapproved executable method");
                };
                if (proof.descriptor() != expected && !(outcome == Outcome.DISPROVEN
                    && reasonCode == Reason.DOMAIN_VIOLATION && proof.descriptor() == Descriptor.DOMAIN_CHECK))
                    throw new IllegalArgumentException("Method/proof mismatch");
                Reason expectedReason = switch (method) {
                    case ARITHMETIC_EVALUATION -> outcome == Outcome.PROVEN ? Reason.EXACT_EQUAL : Reason.EXACT_UNEQUAL;
                    case SYMBOLIC_EQUIVALENCE -> outcome == Outcome.PROVEN ? Reason.POLYNOMIAL_IDENTITY : Reason.POLYNOMIAL_NONIDENTITY;
                    case EQUATION_SUBSTITUTION -> outcome == Outcome.PROVEN ? Reason.VALID_ROOT : Reason.INVALID_ROOT;
                    case DOMAIN_CONSTRAINT_CHECK -> outcome == Outcome.PROVEN ? Reason.DOMAIN_SATISFIED : Reason.DOMAIN_VIOLATION;
                    case SOLUTION_SET_COMPLETENESS -> outcome == Outcome.PROVEN ? Reason.UNIQUE_SOLUTION : Reason.INCOMPLETE_SOLUTION_SET;
                    default -> throw new IllegalArgumentException("Unapproved executable method");
                };
                boolean domainFailure = outcome == Outcome.DISPROVEN && reasonCode == Reason.DOMAIN_VIOLATION
                    && Set.of(VerificationMethod.DOMAIN_CONSTRAINT_CHECK, VerificationMethod.EQUATION_SUBSTITUTION,
                        VerificationMethod.SOLUTION_SET_COMPLETENESS).contains(method);
                if (reasonCode == Reason.DOMAIN_VIOLATION && proof.descriptor() != Descriptor.DOMAIN_CHECK)
                    throw new IllegalArgumentException("Domain contradiction requires domain evidence");
                if (reasonCode != expectedReason && !domainFailure) throw new IllegalArgumentException("Proof/reason mismatch");
                if (proof.leftValue() != null && (proof.leftValue().equals(proof.rightValue()) != (outcome == Outcome.PROVEN)))
                    throw new IllegalArgumentException("Exact witness contradicts outcome");
                if (outcome == Outcome.PROVEN && proof.distinctRootCount() != null && proof.distinctRootCount() != 1)
                    throw new IllegalArgumentException("Singleton completeness requires one root");
            } else {
                Set<Reason> allowed = switch (outcome) {
                    case INCONCLUSIVE -> Set.of(Reason.UNDEFINED_SOURCE, Reason.UNDEFINED_VALUE, Reason.MISSING_RESTRICTION,
                        Reason.POLYNOMIAL_NONIDENTITY, Reason.ROOT_SET_NOT_REPRESENTABLE);
                    case UNSUPPORTED -> Set.of(Reason.UNSUPPORTED_SUBSET, Reason.COMPLEXITY_LIMIT);
                    case BLOCKED -> Set.of(Reason.UNKNOWN_DOMAIN, Reason.CONTRADICTORY_CONSTRAINTS, Reason.ASSIGNMENT_REQUIRED, Reason.DISABLED);
                    case EXECUTION_FAILED -> Set.of(Reason.TIMEOUT, Reason.RESOURCE_FAILURE, Reason.WORKER_FAILURE,
                        Reason.ADMISSION_LIMIT, Reason.DISABLED, Reason.NETWORK_FAILURE, Reason.INVALID_RESPONSE);
                    default -> Set.of();
                };
                if (!allowed.contains(reasonCode)) throw new IllegalArgumentException("Outcome/reason mismatch");
            }
        }
        public void validateFor(Request request) {
            if (method != request.method() || !restrictionIds.equals(request.restrictions().stream().map(PlanningSource.Restriction::id).toList()))
                throw new IllegalArgumentException("Response scope mismatch");
        }
        public static Result failed(Request request, Reason reason) {
            return new Result(request.method(), "v1", VERIFIER_VERSION, Outcome.EXECUTION_FAILED, reason,
                request.restrictions().stream().map(PlanningSource.Restriction::id).toList(), null);
        }
    }
}
