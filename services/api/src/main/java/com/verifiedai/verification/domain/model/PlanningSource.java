package com.verifiedai.verification.domain.model;

import com.verifiedai.solving.domain.model.canonical.MathExpression;
import com.verifiedai.solving.domain.model.canonical.MathStatement;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Ephemeral typed view of the problem module's authorized snapshot. Never persisted. */
public record PlanningSource(Identity identity, ProblemType problemType, TaskType taskType,
    List<MathStatement> statements, Map<String, Domain> variables, List<Restriction> restrictions,
    List<String> assumptionIds, List<String> constraintIds, List<String> derivedRestrictionIds, boolean reviewRequired) {
    public enum ProblemType { ARITHMETIC_EXPRESSION, ALGEBRAIC_EXPRESSION, EQUATION, INEQUALITY }
    public enum TaskType { EVALUATE, SIMPLIFY, SOLVE_EQUATION, SOLVE_INEQUALITY }
    public enum Domain { UNKNOWN, REAL, INTEGER }
    public enum Relation { EQUALS, NOT_EQUALS, LESS_THAN, LESS_THAN_OR_EQUAL, GREATER_THAN, GREATER_THAN_OR_EQUAL }
    public record Identity(UUID userId, UUID sessionId, UUID canonicalProblemId, UUID parseId,
        int parseRevision, int canonicalRevision) {
        public Identity {
            Objects.requireNonNull(userId); Objects.requireNonNull(sessionId); Objects.requireNonNull(canonicalProblemId);
            Objects.requireNonNull(parseId);
            if (parseRevision < 1 || canonicalRevision < 1) throw new IllegalArgumentException("Invalid source revision");
        }
    }
    public record Restriction(String id, Relation relation, MathExpression left, MathExpression right) {
        public Restriction {
            Objects.requireNonNull(id); Objects.requireNonNull(relation);
            if (id.isBlank() || id.length() > 96) throw new IllegalArgumentException("Invalid restriction identity");
            MathExpression.bounded(left, 1, 0, new int[]{0}); MathExpression.bounded(right, 1, 0, new int[]{0});
        }
    }
    public PlanningSource {
        Objects.requireNonNull(identity); Objects.requireNonNull(problemType); Objects.requireNonNull(taskType);
        statements = List.copyOf(statements); variables = Map.copyOf(variables); restrictions = List.copyOf(restrictions);
        assumptionIds = List.copyOf(assumptionIds); constraintIds = List.copyOf(constraintIds);
        derivedRestrictionIds = List.copyOf(derivedRestrictionIds);
        if (statements.isEmpty() || statements.size() > 8 || variables.size() > 16 || restrictions.size() > 48
            || assumptionIds.size() > 16 || constraintIds.size() > 16 || derivedRestrictionIds.size() > 32)
            throw new IllegalArgumentException("Source complexity exceeded");
        variables.keySet().forEach(MathExpression.Variable::new);
        var ids = new java.util.HashSet<String>();
        for (var group : List.of(assumptionIds, constraintIds, derivedRestrictionIds)) {
            for (var id : group) {
                if (id.isBlank() || id.length() > 96 || !ids.add(id)) throw new IllegalArgumentException("Invalid source reference");
            }
        }
        var restrictionIds = new java.util.HashSet<>(constraintIds); restrictionIds.addAll(derivedRestrictionIds);
        if (restrictions.size() != restrictionIds.size()
            || !restrictionIds.equals(restrictions.stream().map(Restriction::id).collect(java.util.stream.Collectors.toSet())))
            throw new IllegalArgumentException("Restriction reference mismatch");
    }
}
