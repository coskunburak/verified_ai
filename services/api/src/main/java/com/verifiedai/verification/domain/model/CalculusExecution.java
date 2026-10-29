package com.verifiedai.verification.domain.model;

import com.verifiedai.solving.domain.model.canonical.MathExpression;
import com.verifiedai.verification.domain.model.MathematicalExecution.ExactValue;
import com.verifiedai.verification.domain.model.MathematicalExecution.Outcome;
import java.math.BigInteger;
import java.util.*;

/** Internal typed calculus scope only; no canonical source authority or publication capability. */
public final class CalculusExecution {
    private CalculusExecution() { }
    public static final String REQUEST_VERSION = "calculus-execution-v1";
    public static final String RESPONSE_VERSION = "calculus-execution-result-v1";
    public static final String TASK_VERSION = "calculus-task-v1";
    public static final String ALGORITHM = "bounded-calculus-v1";
    public enum Method { DERIVATIVE_BACKCHECK, INTEGRAL_DIFFERENTIATION_BACKCHECK, FINITE_LIMIT }
    public enum Direction { LEFT, RIGHT, TWO_SIDED }
    public enum IntegralScope { ANTIDERIVATIVE_REPRESENTATIVE }
    public enum ProofScope { OPEN_INTERVAL, ANTIDERIVATIVE_REPRESENTATIVE, PUNCTURED_NEIGHBORHOOD }
    public enum Descriptor { DERIVATIVE_IDENTITY, ANTIDERIVATIVE_IDENTITY, RATIONAL_NONIDENTITY, RATIONAL_FINITE_LIMIT }
    public enum Reason {
        EXACT_DERIVATIVE, EXACT_ANTIDERIVATIVE, EXACT_FINITE_LIMIT, EXACT_CONTRADICTION,
        UNKNOWN_DOMAIN, NON_REAL_DOMAIN, DOMAIN_OBSTRUCTION, UNSUPPORTED_SUBSET, COMPLEXITY_LIMIT,
        NONFINITE_LIMIT, NUMERICAL_DIAGNOSTIC, SYMBOLIC_UNRESOLVED,
        TIMEOUT, RESOURCE_FAILURE, WORKER_FAILURE, INVALID_RESPONSE, ADMISSION_LIMIT, DISABLED
    }
    public record Scope(UUID identity, int sourceRevision, int candidateRevision, String sourceVersion, String candidateVersion) {
        public Scope {
            Objects.requireNonNull(identity);
            if (sourceRevision < 1 || candidateRevision < 1 || !TASK_VERSION.equals(sourceVersion) || !TASK_VERSION.equals(candidateVersion))
                throw new IllegalArgumentException("Unsupported calculus scope");
        }
    }
    public record Interval(ExactValue lower, ExactValue upper) {
        public Interval {
            if (lower != null && upper != null && compare(lower, upper) >= 0)
                throw new IllegalArgumentException("Empty calculus interval");
        }
    }
    public record Approach(ExactValue target, Direction direction) {
        public Approach { Objects.requireNonNull(target); Objects.requireNonNull(direction); }
    }
    public record Request(UUID executionId, String correlationId, Scope scope, Method method,
        String variable, PlanningSource.Domain domain, Interval interval, MathExpression original,
        MathExpression candidate, List<PlanningSource.Restriction> restrictions, Approach approach, IntegralScope integralScope) {
        public Request {
            Objects.requireNonNull(executionId); Objects.requireNonNull(scope); Objects.requireNonNull(method);
            Objects.requireNonNull(domain); Objects.requireNonNull(interval);
            if (correlationId == null || !correlationId.matches("[A-Za-z0-9_-]{1,64}")
                || variable == null || !variable.matches("[A-Za-z][A-Za-z0-9_]{0,15}"))
                throw new IllegalArgumentException("Invalid calculus identity");
            if ((method == Method.FINITE_LIMIT) != (approach != null)
                || (method == Method.INTEGRAL_DIFFERENTIATION_BACKCHECK) != (integralScope != null))
                throw new IllegalArgumentException("Missing calculus task metadata");
            restrictions = List.copyOf(restrictions);
            if (restrictions.size() > 48 || restrictions.stream().map(PlanningSource.Restriction::id).distinct().count() != restrictions.size())
                throw new IllegalArgumentException("Invalid calculus restrictions");
            int total = expression(original, variable) + expression(candidate, variable);
            for (var r : restrictions) {
                if (!r.id().matches("[A-Za-z0-9_-]{1,96}")) throw new IllegalArgumentException("Invalid restriction ID");
                total += expression(r.left(), variable) + expression(r.right(), variable);
            }
            if (total > 1200) throw new IllegalArgumentException("Calculus complexity exceeded");
        }
    }
    public record Certificate(Descriptor descriptor, ProofScope scope, ExactValue leftValue, ExactValue rightValue) {
        public Certificate { Objects.requireNonNull(descriptor); Objects.requireNonNull(scope); }
    }
    public record Diagnostic(String policy, int attempted, int evaluated, int agreeing) {
        public Diagnostic {
            if (!"calculus-binary64-v1".equals(policy) || attempted < 0 || attempted > 16
                || evaluated < 0 || evaluated > attempted || agreeing < 0 || agreeing > evaluated)
                throw new IllegalArgumentException("Invalid numerical diagnostic");
        }
    }
    public record Result(UUID executionId, String correlationId, Scope scope, Method method,
        String methodVersion, String verifierVersion, Outcome outcome, Reason reasonCode,
        List<String> restrictionIds, Certificate proof, Diagnostic diagnostic) {
        public Result {
            Objects.requireNonNull(executionId); Objects.requireNonNull(scope); Objects.requireNonNull(method);
            Objects.requireNonNull(outcome); Objects.requireNonNull(reasonCode);
            restrictionIds = List.copyOf(restrictionIds);
            if (!"v1".equals(methodVersion) || !ALGORITHM.equals(verifierVersion)
                || correlationId == null || !correlationId.matches("[A-Za-z0-9_-]{1,64}")
                || restrictionIds.size() > 48 || new HashSet<>(restrictionIds).size() != restrictionIds.size()
                || restrictionIds.stream().anyMatch(id -> !id.matches("[A-Za-z0-9_-]{1,96}")))
                throw new IllegalArgumentException("Invalid calculus result");
            boolean proven = outcome == Outcome.PROVEN;
            if (proven || outcome == Outcome.DISPROVEN) {
                if (proof == null || diagnostic != null) throw new IllegalArgumentException("Exact evidence required");
                var expectedScope = switch (method) {
                    case DERIVATIVE_BACKCHECK -> ProofScope.OPEN_INTERVAL;
                    case INTEGRAL_DIFFERENTIATION_BACKCHECK -> ProofScope.ANTIDERIVATIVE_REPRESENTATIVE;
                    case FINITE_LIMIT -> ProofScope.PUNCTURED_NEIGHBORHOOD;
                };
                var expectedDescriptor = switch (method) {
                    case DERIVATIVE_BACKCHECK -> proven ? Descriptor.DERIVATIVE_IDENTITY : Descriptor.RATIONAL_NONIDENTITY;
                    case INTEGRAL_DIFFERENTIATION_BACKCHECK -> proven ? Descriptor.ANTIDERIVATIVE_IDENTITY : Descriptor.RATIONAL_NONIDENTITY;
                    case FINITE_LIMIT -> Descriptor.RATIONAL_FINITE_LIMIT;
                };
                var expectedReason = !proven ? Reason.EXACT_CONTRADICTION : switch (method) {
                    case DERIVATIVE_BACKCHECK -> Reason.EXACT_DERIVATIVE;
                    case INTEGRAL_DIFFERENTIATION_BACKCHECK -> Reason.EXACT_ANTIDERIVATIVE;
                    case FINITE_LIMIT -> Reason.EXACT_FINITE_LIMIT;
                };
                if (proof.scope() != expectedScope || proof.descriptor() != expectedDescriptor || reasonCode != expectedReason)
                    throw new IllegalArgumentException("Calculus proof/method mismatch");
                if (method == Method.FINITE_LIMIT) {
                    if (proof.leftValue() == null || proof.rightValue() == null || (compare(proof.leftValue(), proof.rightValue()) == 0) != proven)
                        throw new IllegalArgumentException("Limit witness contradiction");
                } else if (proof.leftValue() != null || proof.rightValue() != null)
                    throw new IllegalArgumentException("Unexpected scalar certificate");
            } else {
                Set<Reason> allowed = switch (outcome) {
                    case BLOCKED -> Set.of(Reason.UNKNOWN_DOMAIN, Reason.NON_REAL_DOMAIN, Reason.DOMAIN_OBSTRUCTION);
                    case UNSUPPORTED -> Set.of(Reason.UNSUPPORTED_SUBSET, Reason.COMPLEXITY_LIMIT);
                    case INCONCLUSIVE -> Set.of(Reason.NONFINITE_LIMIT, Reason.NUMERICAL_DIAGNOSTIC, Reason.SYMBOLIC_UNRESOLVED);
                    case EXECUTION_FAILED -> Set.of(Reason.TIMEOUT, Reason.RESOURCE_FAILURE, Reason.WORKER_FAILURE, Reason.INVALID_RESPONSE, Reason.ADMISSION_LIMIT, Reason.DISABLED);
                    default -> Set.of();
                };
                if (proof != null || !allowed.contains(reasonCode) || (reasonCode == Reason.NUMERICAL_DIAGNOSTIC) != (diagnostic != null)
                    || diagnostic != null && method != Method.FINITE_LIMIT)
                    throw new IllegalArgumentException("Invalid calculus nonproof");
            }
        }
        public void validateFor(Request request) {
            if (!executionId.equals(request.executionId()) || !correlationId.equals(request.correlationId())
                || !scope.equals(request.scope()) || method != request.method()
                || !restrictionIds.equals(request.restrictions().stream().map(PlanningSource.Restriction::id).toList()))
                throw new IllegalArgumentException("Calculus response scope mismatch");
        }
    }
    private static int compare(ExactValue a, ExactValue b) {
        return new BigInteger(a.numerator()).multiply(new BigInteger(b.denominator()))
            .compareTo(new BigInteger(b.numerator()).multiply(new BigInteger(a.denominator())));
    }
    private static int expression(MathExpression expression, String variable) {
        int[] count = {0}; MathExpression.bounded(expression, 1, 0, count);
        symbols(expression, variable); return count[0];
    }
    private static void symbols(MathExpression expression, String variable) {
        switch (expression) {
            case MathExpression.Variable v -> { if (!variable.equals(v.symbol())) throw new IllegalArgumentException("Undeclared calculus variable"); }
            case MathExpression.Unary u -> symbols(u.operand(), variable);
            case MathExpression.Binary b -> { symbols(b.left(), variable); symbols(b.right(), variable); }
            case MathExpression.Function f -> symbols(f.args().getFirst(), variable);
            default -> { }
        }
    }
}
