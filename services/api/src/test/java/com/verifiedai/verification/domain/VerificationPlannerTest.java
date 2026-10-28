package com.verifiedai.verification.domain;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.verifiedai.solving.CanonicalTestData;
import com.verifiedai.solving.domain.model.canonical.*;
import com.verifiedai.verification.application.VerificationSourceReader;
import com.verifiedai.verification.domain.model.*;
import com.verifiedai.verification.domain.model.VerificationPlan.*;
import com.verifiedai.verification.domain.policy.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static com.verifiedai.verification.PlanningTestData.*;
import static org.assertj.core.api.Assertions.*;

class VerificationPlannerTest {
    final VerificationSourceReader reader = new VerificationSourceReader(CODEC);
    final VerificationPlanner planner = new VerificationPlanner(new VerificationCapabilities());
    @Test void arithmeticSelectsExactEvaluationWithoutJudgingAnIncorrectAnswer() {
        var source = source("ARITHMETIC_EXPRESSION", "EVALUATE", new MathStatement.Expression(
            new MathExpression.Binary(MathExpression.BinaryOperator.ADD, number("0.1"), number("0.2"))));
        var result = planner.plan(reader.read(source), solution(source, new MathStatement.Expression(number("999"))));
        assertThat(result.state()).isEqualTo(State.PLANNED);
        assertThat(result.plan().methods()).extracting(Selection::method).containsExactly(VerificationMethod.ARITHMETIC_EVALUATION);
        assertThat(result.plan().executionAvailability()).isEqualTo(ExecutionAvailability.NOT_EXECUTABLE);
        assertThat(((MathExpression.Binary)((MathStatement.Expression)reader.read(source).statements().getFirst()).expression()).left()).isEqualTo(number("0.1"));
    }
    @Test void algebraSelectsEquivalenceAndPreservesDomainRequirements() {
        var source = source("ALGEBRAIC_EXPRESSION", "SIMPLIFY", new MathStatement.Expression(x()));
        var result = planner.plan(reader.read(source), solution(source, new MathStatement.Expression(x())));
        assertThat(result.plan().methods()).extracting(Selection::method)
            .containsExactly(VerificationMethod.DOMAIN_CONSTRAINT_CHECK, VerificationMethod.SYMBOLIC_EQUIVALENCE);
        assertThat(result.plan().methods().getLast().availability()).isEqualTo(Availability.BLOCKED_BY_PRECONDITION);
    }
    @Test void equationsSeparateRootSoundnessCompletenessAndDomain() {
        var source = equation(); var result = planner.plan(reader.read(source), answer(source));
        assertThat(result.plan().methods()).extracting(Selection::method).containsExactly(
            VerificationMethod.DOMAIN_CONSTRAINT_CHECK, VerificationMethod.EQUATION_SUBSTITUTION, VerificationMethod.SOLUTION_SET_COMPLETENESS);
        assertThat(result.plan().methods()).extracting(Selection::priority).containsExactly(1, 2, 3);
        assertThat(result.plan().limitations()).contains(Reason.COMPLETENESS_EXECUTOR_REQUIRED);
    }
    @Test void inequalitiesAndTaskMismatchNeverDefaultToEquivalence() {
        var inequality = source("INEQUALITY", "SOLVE_INEQUALITY", new MathStatement.Comparison(MathStatement.Relation.LESS_THAN, x(), number("1")));
        assertThat(planner.plan(reader.read(inequality), solution(inequality, reader.read(inequality).statements().getFirst())).reason()).isEqualTo(Reason.INEQUALITY_NOT_SUPPORTED);
        var mismatch = source("ALGEBRAIC_EXPRESSION", "EVALUATE", new MathStatement.Expression(x()));
        assertThat(planner.plan(reader.read(mismatch), solution(mismatch, new MathStatement.Expression(x()))).reason()).isEqualTo(Reason.UNSUPPORTED_TASK_TYPE);
    }
    @Test void sourceAndCandidateUncertaintyBlockPlanning() {
        var source = equation(); var candidate = answer(source); var root = (ObjectNode) CODEC.parse(source.canonicalJson());
        ((ObjectNode)root.path("display")).put("reviewRequired", true);
        assertThat(planner.plan(reader.read(json(source, root)), candidate).reason()).isEqualTo(Reason.AMBIGUOUS_SOURCE);
        var uncertain = new CanonicalSolution(candidate.id(), candidate.lineage(), candidate.version(), candidate.provenance(),
            candidate.generationOrigin(), candidate.problemType(), candidate.taskType(), candidate.method(), candidate.finalStatement(),
            candidate.steps(), candidate.sourceAssumptionIds(), candidate.sourceConstraintIds(), candidate.derivedRestrictionIds(), List.of(), List.of("Unresolved"), candidate.createdAt());
        assertThat(planner.plan(reader.read(source), uncertain).reason()).isEqualTo(Reason.UNRESOLVED_SOLUTION);
    }
    @Test void missingOrUnrelatedDenominatorRestrictionRequiresReview() {
        var division = new MathExpression.Binary(MathExpression.BinaryOperator.DIVIDE, number("1"), x());
        var source = source("ALGEBRAIC_EXPRESSION", "SIMPLIFY", new MathStatement.Expression(division));
        assertThat(planner.plan(reader.read(source), solution(source, new MathStatement.Expression(division))).reason()).isEqualTo(Reason.MISSING_DOMAIN_RESTRICTION);
        var root = (ObjectNode)CODEC.parse(source.canonicalJson());
        var r = root.withArray("derivedRestrictions").addObject();
        r.put("id", "d1").put("relation", "NOT_EQUALS").put("reason", "DENOMINATOR_NON_ZERO").put("provenance", "DERIVED");
        r.set("left", CODEC.json(number("1"))); r.set("right", CODEC.json(number("0")));
        var unrelated = json(source, root);
        assertThat(planner.plan(reader.read(unrelated), solution(unrelated, new MathStatement.Expression(division))).reason()).isEqualTo(Reason.MISSING_DOMAIN_RESTRICTION);
        r.set("left", CODEC.json(x())); var restricted = json(source, root);
        assertThat(planner.plan(reader.read(restricted), solution(restricted, new MathStatement.Expression(division))).state()).isEqualTo(State.PLANNED);
    }
    @Test void eachRestrictedFunctionRequiresItsExactArgumentConstraint() {
        for (var name : List.of(MathExpression.FunctionName.SQRT, MathExpression.FunctionName.LOG, MathExpression.FunctionName.TAN)) {
            var f = new MathExpression.Function(name, List.of(x()));
            var source = source("ALGEBRAIC_EXPRESSION", "SIMPLIFY", new MathStatement.Expression(f));
            assertThat(planner.plan(reader.read(source), solution(source, new MathStatement.Expression(f))).reason()).isEqualTo(Reason.MISSING_DOMAIN_RESTRICTION);
            var root = (ObjectNode)CODEC.parse(source.canonicalJson()); var r = root.withArray("derivedRestrictions").addObject();
            r.put("id", "domain").put("provenance", "DERIVED").put("reason", name == MathExpression.FunctionName.SQRT ? "SQRT_DOMAIN_NON_NEGATIVE" : name == MathExpression.FunctionName.LOG ? "LOG_DOMAIN_POSITIVE" : "TAN_DOMAIN_COS_NON_ZERO")
                .put("relation", name == MathExpression.FunctionName.SQRT ? "GREATER_THAN_OR_EQUAL" : name == MathExpression.FunctionName.LOG ? "GREATER_THAN" : "NOT_EQUALS");
            r.set("left", CODEC.json(name == MathExpression.FunctionName.TAN ? new MathExpression.Function(MathExpression.FunctionName.COS, List.of(x())) : x()));
            r.set("right", CODEC.json(number("0"))); var restricted = json(source, root);
            assertThat(planner.plan(reader.read(restricted), solution(restricted, new MathStatement.Expression(f))).state()).isEqualTo(State.PLANNED);
        }
    }
    @Test void undeclaredSymbolsAndUnsafePowersAreUnsupported() {
        var source = source("ALGEBRAIC_EXPRESSION", "SIMPLIFY", new MathStatement.Expression(x()));
        assertThat(planner.plan(reader.read(source), solution(source, new MathStatement.Expression(new MathExpression.Variable("y")))).reason()).isEqualTo(Reason.UNDECLARED_VARIABLE);
        for (var power : List.of(number("13"), number("0.5"), x())) {
            var answer = new MathStatement.Expression(new MathExpression.Binary(MathExpression.BinaryOperator.POWER, x(), power));
            assertThat(planner.plan(reader.read(source), solution(source, answer)).reason()).isEqualTo(Reason.UNSUPPORTED_POWER);
        }
    }
    @Test void candidateStepsCannotIntroduceUnrestrictedDivision() {
        var source = equation(); var candidate = answer(source);
        var claim = new MathStatement.Expression(new MathExpression.Binary(MathExpression.BinaryOperator.DIVIDE, number("1"), x()));
        var step = new CanonicalSolution.Step(CanonicalSolution.stepIdentity(candidate.id(), "s1"), "s1", 1,
            CanonicalSolution.Category.CONCLUDE, claim, "Synthetic", List.of());
        assertThat(planner.plan(reader.read(source), CanonicalTestData.copy(candidate, candidate.version(), List.of(step))).reason()).isEqualTo(Reason.MISSING_DOMAIN_RESTRICTION);
    }
    @Test void sourceAssumptionsAreNotInferredAndSourceReferencesCannotBeDropped() {
        var source = equation(); var original = answer(source); var root = (ObjectNode)CODEC.parse(source.canonicalJson());
        root.withArray("sourceAssumptions").addObject().put("id", "a1").put("provenance", "SOURCE").put("text", "assume something");
        var assumed = json(source, root);
        assertThat(planner.plan(reader.read(assumed), solution(assumed, original.finalStatement())).reason()).isEqualTo(Reason.UNTYPED_SOURCE_ASSUMPTION);
        root.withArray("sourceAssumptions").removeAll();
        var constraint = root.withArray("sourceConstraints").addObject().put("id", "c1").put("provenance", "SOURCE")
            .put("reason", "SOURCE_EXPLICIT").put("relation", "GREATER_THAN");
        constraint.set("left", CODEC.json(x())); constraint.set("right", CODEC.json(number("0")));
        assertThat(planner.plan(reader.read(json(source, root)), original).reason()).isEqualTo(Reason.SOURCE_REFERENCE_MISMATCH);
    }
    @Test void unknownVariableDomainRemainsAnExplicitLimitation() {
        var source = equation(); var root = (ObjectNode)CODEC.parse(source.canonicalJson());
        ((ObjectNode)root.path("variables").get(0)).put("domain", "UNKNOWN");
        var result = planner.plan(reader.read(json(source, root)), answer(source));
        assertThat(result.plan().limitations()).contains(Reason.UNKNOWN_VARIABLE_DOMAIN);
        assertThat(result.plan().executionAvailability()).isEqualTo(ExecutionAvailability.NOT_EXECUTABLE);
    }
    @Test void repeatedPlansAreImmutableAndPolicyAndCapabilityVersionsAreExplicit() {
        var source = equation(); var candidate = answer(source); var first = planner.plan(reader.read(source), candidate);
        for (int i = 0; i < 30; i++) assertThat(planner.plan(reader.read(source), candidate)).isEqualTo(first);
        assertThat(first.plan().policyVersion()).isEqualTo("verification-planner-v1");
        var empty = new VerificationPlanner(VerificationCapabilities.none()).plan(reader.read(source), candidate);
        assertThat(empty.plan().id()).isNotEqualTo(first.plan().id());
        assertThat(empty.plan().executionAvailability()).isEqualTo(ExecutionAvailability.NOT_EXECUTABLE);
        assertThat(empty.plan().methods()).extracting(Selection::availability).containsOnly(Availability.PLANNED_NOT_EXECUTABLE);
        assertThatThrownBy(() -> first.plan().methods().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(State.values()).extracting(Enum::name).doesNotContain("VERIFIED", "PARTIALLY_VERIFIED");
    }
    @Test void unknownRepresentationAndStaleRevisionFailClosed() {
        var source = equation(); var candidate = answer(source);
        var other = CanonicalTestData.copy(candidate, new CanonicalSolution.Version("canonical-solution-v99", "normalizer-v99"), candidate.steps());
        assertThat(planner.plan(reader.read(source), other).reason()).isEqualTo(Reason.UNSUPPORTED_REPRESENTATION);
        var stale = new com.verifiedai.problem.application.solving.SolveSource(source.canonicalProblemId(), source.userId(), source.problemSessionId(), source.problemParseId(), 1, 2, source.problemType(), source.taskType(), source.canonicalJson());
        assertThat(planner.plan(reader.read(stale), candidate).reason()).isEqualTo(Reason.LINEAGE_MISMATCH);
    }
}
