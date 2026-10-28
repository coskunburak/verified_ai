package com.verifiedai.verification.domain.policy;

import com.verifiedai.solving.domain.model.canonical.CanonicalSolution;
import com.verifiedai.solving.domain.model.canonical.MathExpression;
import com.verifiedai.solving.domain.model.canonical.MathStatement;
import com.verifiedai.verification.domain.model.*;
import com.verifiedai.verification.domain.model.PlanningSource.*;
import com.verifiedai.verification.domain.model.VerificationPlan.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Bounded structural eligibility only. Never evaluates the proposed answer or invokes a verifier. */
@Component
public final class VerificationPlanner {
    public static final String POLICY_VERSION = "verification-planner-v1";
    private final VerificationCapabilities capabilities;
    public VerificationPlanner(VerificationCapabilities capabilities) { this.capabilities = capabilities; }

    public Result plan(PlanningSource source, CanonicalSolution solution) {
        if (!matches(source.identity(), solution)) return Result.rejected(State.STALE_SOURCE, Reason.LINEAGE_MISMATCH);
        if (!CanonicalSolution.Version.CURRENT.equals(solution.version()))
            return Result.rejected(State.UNSUPPORTED, Reason.UNSUPPORTED_REPRESENTATION);
        if (source.reviewRequired()) return Result.rejected(State.REVIEW_REQUIRED, Reason.AMBIGUOUS_SOURCE);
        if (!solution.ambiguities().isEmpty() || !solution.unresolvedIssues().isEmpty())
            return Result.rejected(State.REVIEW_REQUIRED, Reason.UNRESOLVED_SOLUTION);
        if (!source.assumptionIds().isEmpty()) return Result.rejected(State.REVIEW_REQUIRED, Reason.UNTYPED_SOURCE_ASSUMPTION);
        if (!sameIds(source.assumptionIds(), solution.sourceAssumptionIds())
            || !sameIds(source.constraintIds(), solution.sourceConstraintIds())
            || !sameIds(source.derivedRestrictionIds(), solution.derivedRestrictionIds()))
            return Result.rejected(State.REVIEW_REQUIRED, Reason.SOURCE_REFERENCE_MISMATCH);
        if (!source.problemType().name().equals(solution.problemType()) || !source.taskType().name().equals(solution.taskType()))
            return Result.rejected(State.STALE_SOURCE, Reason.LINEAGE_MISMATCH);
        if (source.statements().size() != 1) return Result.rejected(State.UNSUPPORTED, Reason.UNSUPPORTED_STATEMENT);
        if (source.problemType() == ProblemType.INEQUALITY)
            return Result.rejected(State.UNSUPPORTED, Reason.INEQUALITY_NOT_SUPPORTED);
        var sourceStatement = source.statements().getFirst();
        var methods = new ArrayList<VerificationMethod>();
        switch (source.problemType()) {
            case ARITHMETIC_EXPRESSION -> {
                if (source.taskType() != TaskType.EVALUATE) return Result.rejected(State.UNSUPPORTED, Reason.UNSUPPORTED_TASK_TYPE);
                if (!(sourceStatement instanceof MathStatement.Expression) || !(solution.finalStatement() instanceof MathStatement.Expression))
                    return Result.rejected(State.UNSUPPORTED, Reason.UNSUPPORTED_STATEMENT);
                methods.add(VerificationMethod.ARITHMETIC_EVALUATION);
            }
            case ALGEBRAIC_EXPRESSION -> {
                if (source.taskType() != TaskType.SIMPLIFY) return Result.rejected(State.UNSUPPORTED, Reason.UNSUPPORTED_TASK_TYPE);
                if (!(sourceStatement instanceof MathStatement.Expression) || !(solution.finalStatement() instanceof MathStatement.Expression))
                    return Result.rejected(State.UNSUPPORTED, Reason.UNSUPPORTED_STATEMENT);
                methods.add(VerificationMethod.SYMBOLIC_EQUIVALENCE);
            }
            case EQUATION -> {
                if (source.taskType() != TaskType.SOLVE_EQUATION) return Result.rejected(State.UNSUPPORTED, Reason.UNSUPPORTED_TASK_TYPE);
                if (!equality(sourceStatement) || !equality(solution.finalStatement()))
                    return Result.rejected(State.UNSUPPORTED, Reason.UNSUPPORTED_STATEMENT);
                methods.add(VerificationMethod.EQUATION_SUBSTITUTION);
                methods.add(VerificationMethod.SOLUTION_SET_COMPLETENESS);
            }
            default -> { return Result.rejected(State.UNSUPPORTED, Reason.UNSUPPORTED_PROBLEM_TYPE); }
        }
        var analysis = new Analysis(source);
        analysis.statement(sourceStatement); analysis.statement(solution.finalStatement());
        for (var step : solution.steps()) analysis.statement(step.claim());
        for (var restriction : source.restrictions()) { analysis.expression(restriction.left()); analysis.expression(restriction.right()); }
        if (analysis.failure != null) return Result.rejected(analysis.failure == Reason.MISSING_DOMAIN_RESTRICTION
            ? State.REVIEW_REQUIRED : State.UNSUPPORTED, analysis.failure);
        if (source.problemType() == ProblemType.ARITHMETIC_EXPRESSION && (analysis.hasVariable || analysis.hasFunction))
            return Result.rejected(State.UNSUPPORTED, Reason.UNSUPPORTED_STATEMENT);

        var limitations = EnumSet.noneOf(Reason.class);
        if (analysis.domainNeeded || !source.restrictions().isEmpty() || !source.variables().isEmpty()
            || source.problemType() == ProblemType.EQUATION) {
            methods.addFirst(VerificationMethod.DOMAIN_CONSTRAINT_CHECK);
            limitations.add(Reason.DOMAIN_EXECUTOR_REQUIRED);
        }
        if (methods.contains(VerificationMethod.SOLUTION_SET_COMPLETENESS)) limitations.add(Reason.COMPLETENESS_EXECUTOR_REQUIRED);
        if (source.variables().containsValue(Domain.UNKNOWN)) limitations.add(Reason.UNKNOWN_VARIABLE_DOMAIN);
        var selections = new ArrayList<Selection>();
        for (var method : methods) {
            selections.add(new Selection(method, method.version(), selections.size() + 1, method.purpose(),
                method.requiredInputs(), capabilities.availability(method), capabilities.reason(method)));
            limitations.add(capabilities.reason(method));
        }
        var s = source.identity();
        String identity = String.join("/", POLICY_VERSION, capabilities.version(), s.userId().toString(), s.sessionId().toString(),
            s.canonicalProblemId().toString(), s.parseId().toString(), Integer.toString(s.parseRevision()),
            Integer.toString(s.canonicalRevision()), solution.id().toString(), solution.version().representation(), solution.version().normalizer());
        return Result.planned(new VerificationPlan(UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)),
            POLICY_VERSION, capabilities.version(), solution.lineage(), solution.id(), solution.version(), selections, List.copyOf(limitations)));
    }
    public static boolean matches(Identity source, CanonicalSolution solution) {
        var l = solution.lineage();
        return source.userId().equals(l.userId()) && source.sessionId().equals(l.sessionId())
            && source.canonicalProblemId().equals(l.canonicalProblemId()) && source.parseId().equals(l.parseId())
            && source.parseRevision() == l.parseRevision() && source.canonicalRevision() == l.canonicalRevision();
    }
    private static boolean sameIds(List<String> expected, List<String> actual) {
        return expected.size() == actual.size() && new HashSet<>(expected).equals(new HashSet<>(actual));
    }
    private static boolean equality(MathStatement s) {
        return s instanceof MathStatement.Comparison c && c.relation() == MathStatement.Relation.EQUALS;
    }
    private static BigDecimal literal(MathExpression e) {
        if (e instanceof MathExpression.Number n) return new BigDecimal(n.value());
        if (e instanceof MathExpression.Unary u) { var value = literal(u.operand()); return value == null ? null : value.negate(); }
        return null;
    }
    private static final class Analysis {
        private final PlanningSource source;
        private Reason failure;
        private boolean domainNeeded, hasVariable, hasFunction;
        Analysis(PlanningSource source) { this.source = source; }
        void statement(MathStatement statement) {
            switch (statement) {
                case MathStatement.Expression e -> expression(e.expression());
                case MathStatement.Comparison c -> { expression(c.left()); expression(c.right()); }
            }
        }
        void expression(MathExpression expression) {
            if (failure != null) return;
            switch (expression) {
                case MathExpression.Number n -> { }
                case MathExpression.Variable v -> {
                    hasVariable = true;
                    if (!source.variables().containsKey(v.symbol())) failure = Reason.UNDECLARED_VARIABLE;
                }
                case MathExpression.Unary u -> expression(u.operand());
                case MathExpression.Binary b -> {
                    expression(b.left()); expression(b.right());
                    if (b.operator() == MathExpression.BinaryOperator.DIVIDE) nonzero(b.right());
                    if (b.operator() == MathExpression.BinaryOperator.POWER) {
                        var exponent = literal(b.right());
                        if (exponent == null || exponent.stripTrailingZeros().scale() > 0 || exponent.abs().compareTo(BigDecimal.valueOf(12)) > 0)
                            fail(Reason.UNSUPPORTED_POWER);
                        else if (exponent.signum() <= 0) nonzero(b.left());
                    }
                }
                case MathExpression.Function f -> {
                    hasFunction = true; var arg = f.args().getFirst(); expression(arg);
                    switch (f.function()) {
                        case SQRT -> restriction(arg, Relation.GREATER_THAN_OR_EQUAL);
                        case LOG -> restriction(arg, Relation.GREATER_THAN);
                        case TAN -> restriction(new MathExpression.Function(MathExpression.FunctionName.COS, List.of(arg)), Relation.NOT_EQUALS);
                        default -> { }
                    }
                }
            }
        }
        void nonzero(MathExpression expression) {
            var value = literal(expression);
            if (value != null && value.signum() != 0) return;
            domainNeeded = true;
            if (hasRestriction(expression, Relation.NOT_EQUALS)) return;
            if (expression instanceof MathExpression.Binary b && b.operator() == MathExpression.BinaryOperator.MULTIPLY) {
                nonzero(b.left()); nonzero(b.right());
            } else fail(Reason.MISSING_DOMAIN_RESTRICTION);
        }
        void restriction(MathExpression expression, Relation relation) {
            domainNeeded = true;
            if (!hasRestriction(expression, relation)) fail(Reason.MISSING_DOMAIN_RESTRICTION);
        }
        boolean hasRestriction(MathExpression expression, Relation relation) {
            return source.restrictions().stream().anyMatch(r -> r.relation() == relation && r.left().equals(expression)
                && literal(r.right()) != null && literal(r.right()).signum() == 0);
        }
        void fail(Reason reason) { if (failure == null) failure = reason; }
    }
}
