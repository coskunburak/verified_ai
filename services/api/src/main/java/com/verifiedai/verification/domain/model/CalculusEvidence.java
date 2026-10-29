package com.verifiedai.verification.domain.model;

import java.util.*;

/** Tagged persistence variant. Scope metadata is retained even for non-proof outcomes. */
public record CalculusEvidence(String schemaVersion, String sourceVersion, String candidateVersion,
    String variable, PlanningSource.Domain domain, CalculusExecution.Interval interval,
    CalculusExecution.Approach approach, CalculusExecution.IntegralScope integralScope,
    List<PlanningSource.Restriction> restrictions, CalculusExecution.Result result) {
    public CalculusEvidence {
        if (!"calculus-evidence-v1".equals(schemaVersion) || !"canonical-problem-v2".equals(sourceVersion)
            || !"solution-candidate-v2".equals(candidateVersion) || domain!=PlanningSource.Domain.REAL
            || variable==null || !variable.matches("[A-Za-z][A-Za-z0-9_]{0,15}"))
            throw new IllegalArgumentException("Invalid calculus evidence version/domain");
        Objects.requireNonNull(interval); Objects.requireNonNull(result); restrictions=List.copyOf(restrictions);
        if (restrictions.size()>48 || !restrictions.stream().map(PlanningSource.Restriction::id).toList().equals(result.restrictionIds()))
            throw new IllegalArgumentException("Calculus evidence restrictions mismatch");
        int nodes=0;
        for (var restriction : restrictions) {
            nodes+=symbols(restriction.left(),variable)+symbols(restriction.right(),variable);
        }
        if (nodes>1200) throw new IllegalArgumentException("Evidence restriction complexity exceeded");
        if ((result.method()==CalculusExecution.Method.FINITE_LIMIT)!=(approach!=null)
            || (result.method()==CalculusExecution.Method.INTEGRAL_DIFFERENTIATION_BACKCHECK)!=(integralScope!=null))
            throw new IllegalArgumentException("Invalid calculus evidence scope");
    }
    private static int symbols(com.verifiedai.solving.domain.model.canonical.MathExpression expression,String variable) {
        return switch(expression) {
            case com.verifiedai.solving.domain.model.canonical.MathExpression.Variable v -> {
                if (!variable.equals(v.symbol())) throw new IllegalArgumentException("Evidence variable mismatch"); yield 1;
            }
            case com.verifiedai.solving.domain.model.canonical.MathExpression.Unary u -> 1+symbols(u.operand(),variable);
            case com.verifiedai.solving.domain.model.canonical.MathExpression.Binary b -> 1+symbols(b.left(),variable)+symbols(b.right(),variable);
            case com.verifiedai.solving.domain.model.canonical.MathExpression.Function f -> 1+symbols(f.args().getFirst(),variable);
            default -> 1;
        };
    }
    public static CalculusEvidence of(CalculusExecution.Request request, CalculusExecution.Result result) {
        result.validateFor(request);
        return new CalculusEvidence("calculus-evidence-v1","canonical-problem-v2","solution-candidate-v2",
            request.variable(),request.domain(),request.interval(),request.approach(),request.integralScope(),request.restrictions(),result);
    }
    public static CalculusExecution.Scope scope(PlanningSource.Identity source, UUID solution) {
        String key=String.join("/","calculus-source-v1",source.userId().toString(),source.sessionId().toString(),
            source.canonicalProblemId().toString(),source.parseId().toString(),Integer.toString(source.parseRevision()),
            Integer.toString(source.canonicalRevision()),solution.toString());
        var identity=UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new CalculusExecution.Scope(identity,source.canonicalRevision(),1,CalculusExecution.TASK_VERSION,CalculusExecution.TASK_VERSION);
    }
    public static VerificationMethod method(CalculusExecution.Method method) {
        return switch(method) {
            case DERIVATIVE_BACKCHECK -> VerificationMethod.DERIVATIVE_BACKCHECK;
            case INTEGRAL_DIFFERENTIATION_BACKCHECK -> VerificationMethod.INTEGRAL_DIFFERENTIATION_BACKCHECK;
            case FINITE_LIMIT -> VerificationMethod.FINITE_LIMIT;
        };
    }
}
