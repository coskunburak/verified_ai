package com.verifiedai.verification.domain;

import com.verifiedai.solving.domain.model.canonical.*;
import com.verifiedai.verification.application.VerificationSourceReader;
import com.verifiedai.verification.domain.model.*;
import com.verifiedai.verification.domain.model.MathematicalExecution.*;
import com.verifiedai.verification.domain.policy.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.verifiedai.verification.PlanningTestData.*;
import static com.verifiedai.verification.domain.model.MathematicalExecution.VERIFIER_VERSION;
import static org.assertj.core.api.Assertions.*;

class VerificationPolicyTest {
    final VerificationPlanner planner = new VerificationPlanner(new VerificationCapabilities(true, true,
        "ARITHMETIC_EVALUATION,SYMBOLIC_EQUIVALENCE,EQUATION_SUBSTITUTION,DOMAIN_CONSTRAINT_CHECK,SOLUTION_SET_COMPLETENESS"));
    final VerificationPolicy policy = new VerificationPolicy(planner);
    final UUID execution = UUID.randomUUID();
    final Instant now = Instant.parse("2026-10-30T00:00:00Z");
    record Scenario(PlanningSource source, CanonicalSolution solution, VerificationPlan plan) { }
    Scenario scenario(boolean equation) {
        var raw = equation ? equation() : source("ALGEBRAIC_EXPRESSION", "SIMPLIFY", new MathStatement.Expression(x()));
        var candidate = equation ? answer(raw) : solution(raw, new MathStatement.Expression(x()));
        var source = new VerificationSourceReader(CODEC).read(raw);
        return new Scenario(source, candidate, planner.plan(source, candidate).plan());
    }
    Result result(VerificationMethod method, Outcome outcome) {
        if (outcome == Outcome.UNSUPPORTED) return new Result(method, "v1", VERIFIER_VERSION, outcome, Reason.UNSUPPORTED_SUBSET, List.of(), null);
        if (outcome == Outcome.EXECUTION_FAILED) return new Result(method, "v1", VERIFIER_VERSION, outcome, Reason.TIMEOUT, List.of(), null);
        if (outcome == Outcome.BLOCKED) return new Result(method, "v1", VERIFIER_VERSION, outcome, Reason.DISABLED, List.of(), null);
        boolean proven = outcome == Outcome.PROVEN;
        Descriptor descriptor = switch (method) {
            case DOMAIN_CONSTRAINT_CHECK -> Descriptor.DOMAIN_CHECK;
            case SYMBOLIC_EQUIVALENCE -> Descriptor.POLYNOMIAL_COEFFICIENT_COMPARISON;
            case EQUATION_SUBSTITUTION -> Descriptor.ORIGINAL_EQUATION_SUBSTITUTION;
            case SOLUTION_SET_COMPLETENESS -> Descriptor.BOUNDED_POLYNOMIAL_ROOT_SET;
            default -> Descriptor.EXACT_RATIONAL_COMPARISON;
        };
        Reason reason = switch (method) {
            case DOMAIN_CONSTRAINT_CHECK -> proven ? Reason.DOMAIN_SATISFIED : Reason.DOMAIN_VIOLATION;
            case SYMBOLIC_EQUIVALENCE -> proven ? Reason.POLYNOMIAL_IDENTITY : Reason.POLYNOMIAL_NONIDENTITY;
            case EQUATION_SUBSTITUTION -> proven ? Reason.VALID_ROOT : Reason.INVALID_ROOT;
            case SOLUTION_SET_COMPLETENESS -> proven ? Reason.UNIQUE_SOLUTION : Reason.INCOMPLETE_SOLUTION_SET;
            default -> proven ? Reason.EXACT_EQUAL : Reason.EXACT_UNEQUAL;
        };
        boolean values = method == VerificationMethod.EQUATION_SUBSTITUTION || method == VerificationMethod.ARITHMETIC_EVALUATION;
        return new Result(method, "v1", VERIFIER_VERSION, outcome, reason, List.of(), new Proof(descriptor,
            values ? new ExactValue("1", "1") : null, values ? new ExactValue(proven ? "1" : "2", "1") : null,
            method == VerificationMethod.SOLUTION_SET_COMPLETENESS ? proven ? 1 : 2 : null));
    }
    VerificationRun run(Scenario scenario, Result... results) {
        var plan = scenario.plan();
        var evidence = Arrays.stream(results).map(r -> new VerificationEvidence(VerificationEvidence.identity(execution, r.method()),
            execution, plan.id(), plan.policyVersion(), plan.capabilityVersion(), scenario.source().identity(), scenario.solution().id(),
            scenario.solution().version(), r.method().purpose(), scenario.source().variables(), r, "composite-test", now)).toList();
        return new VerificationRun(execution, execution, plan.id(), plan.policyVersion(), plan.capabilityVersion(), scenario.source().identity(),
            scenario.solution().id(), scenario.solution().version(), "composite-test", now, evidence);
    }
    VerificationDecision decide(Scenario s, VerificationDecision.SecondarySnapshot secondary, Result... results) {
        return policy.decide(s.source(), s.solution(), s.plan(), run(s, results), secondary, execution, now, true, 1);
    }
    VerificationDecision decide(Scenario s, Result... results) { return decide(s, VerificationDecision.SecondarySnapshot.absent(), results); }
    @Test void completeEquationAndAlgebraAreScopedToFinalAnswerNotExplanationSteps() {
        for (boolean equation : List.of(false, true)) {
            var s = scenario(equation);
            var results = s.plan().methods().stream().map(m -> result(m.method(), Outcome.PROVEN)).toArray(Result[]::new);
            var d = decide(s, results);
            assertThat(d.status()).isEqualTo(VerificationDecision.Status.VERIFIED);
            assertThat(d.scope()).isEqualTo(VerificationDecision.Scope.FINAL_ANSWER);
            assertThat(d.unchecked()).isEmpty();
            assertThat(d.uncertainty()).containsExactly(VerificationDecision.Uncertainty.EXPLANATION_STEPS_UNCHECKED);
            assertThat(d.recovery()).isEqualTo(VerificationDecision.Recovery.NONE);
        }
    }
    @Test void arithmeticUsesTheSameAuthorizedPolicy() {
        var raw = source("ARITHMETIC_EXPRESSION", "EVALUATE", new MathStatement.Expression(number("1")));
        var source = new VerificationSourceReader(CODEC).read(raw);
        var solution = solution(raw, new MathStatement.Expression(number("1")));
        var s = new Scenario(source, solution, planner.plan(source, solution).plan());
        assertThat(decide(s, result(VerificationMethod.ARITHMETIC_EVALUATION, Outcome.PROVEN)).status()).isEqualTo(VerificationDecision.Status.VERIFIED);
    }
    @Test void domainCompletenessAndRootSoundnessAreSeparateObligations() {
        var algebra = scenario(false);
        var partial = decide(algebra, result(VerificationMethod.SYMBOLIC_EQUIVALENCE, Outcome.PROVEN));
        assertThat(partial.status()).isEqualTo(VerificationDecision.Status.PARTIALLY_VERIFIED);
        assertThat(partial.unchecked()).containsExactly(VerificationMethod.DOMAIN_CONSTRAINT_CHECK);
        var equation = scenario(true);
        var root = decide(equation, result(VerificationMethod.EQUATION_SUBSTITUTION, Outcome.PROVEN));
        assertThat(root.status()).isEqualTo(VerificationDecision.Status.PARTIALLY_VERIFIED);
        assertThat(root.unchecked()).contains(VerificationMethod.SOLUTION_SET_COMPLETENESS, VerificationMethod.DOMAIN_CONSTRAINT_CHECK);
        assertThat(decide(equation, result(VerificationMethod.DOMAIN_CONSTRAINT_CHECK, Outcome.PROVEN)).status())
            .isEqualTo(VerificationDecision.Status.UNVERIFIED);
    }
    @Test void deterministicDisproofCannotBeHiddenBySuccessfulUnrelatedChecksOrAgreement() {
        var s = scenario(true);
        var d = decide(s, new VerificationDecision.SecondarySnapshot(List.of(), List.of(VerificationDecision.SecondarySignal.AGREEMENT)),
            result(VerificationMethod.DOMAIN_CONSTRAINT_CHECK, Outcome.PROVEN), result(VerificationMethod.EQUATION_SUBSTITUTION, Outcome.PROVEN),
            result(VerificationMethod.SOLUTION_SET_COMPLETENESS, Outcome.DISPROVEN));
        assertThat(d.status()).isEqualTo(VerificationDecision.Status.UNVERIFIED);
        assertThat(d.contradictions()).containsExactly(VerificationMethod.SOLUTION_SET_COMPLETENESS);
        assertThat(d.recovery()).isEqualTo(VerificationDecision.Recovery.REVIEW_CONTRADICTION);
    }
    @Test void secondaryMatrixIsConservativeAndDoesNotVote() {
        var s = scenario(false);
        for (var signal : VerificationDecision.SecondarySignal.values()) {
            var secondary = new VerificationDecision.SecondarySnapshot(List.of(), List.of(signal));
            var d = decide(s, secondary, result(VerificationMethod.DOMAIN_CONSTRAINT_CHECK, Outcome.PROVEN),
                result(VerificationMethod.SYMBOLIC_EQUIVALENCE, Outcome.PROVEN));
            boolean compatible = signal == VerificationDecision.SecondarySignal.NONE || signal == VerificationDecision.SecondarySignal.AGREEMENT;
            assertThat(d.status()).as(signal.name()).isEqualTo(compatible ? VerificationDecision.Status.VERIFIED : VerificationDecision.Status.UNVERIFIED);
            var noProof = decide(s, secondary, result(VerificationMethod.SYMBOLIC_EQUIVALENCE, Outcome.UNSUPPORTED));
            assertThat(noProof.status()).isEqualTo(VerificationDecision.Status.UNVERIFIED);
            assertThat(noProof.contradictions()).isEmpty();
        }
    }
    @Test void failuresUnsupportedAndDisabledNeverAcquireProof() {
        var s = scenario(false);
        for (var outcome : List.of(Outcome.EXECUTION_FAILED, Outcome.UNSUPPORTED, Outcome.BLOCKED)) {
            var d = decide(s, result(VerificationMethod.SYMBOLIC_EQUIVALENCE, outcome));
            assertThat(d.status()).isEqualTo(VerificationDecision.Status.UNVERIFIED);
            assertThat(d.contradictions()).isEmpty();
        }
    }
    @Test void orderAndRepeatedEvaluationAreExactlyReproducible() {
        var s = scenario(false);
        var domain = result(VerificationMethod.DOMAIN_CONSTRAINT_CHECK, Outcome.PROVEN);
        var identity = result(VerificationMethod.SYMBOLIC_EQUIVALENCE, Outcome.PROVEN);
        assertThat(decide(s, domain, identity)).isEqualTo(decide(s, identity, domain));
        assertThat(decide(s, domain, identity).reasons()).isSortedAccordingTo(Comparator.naturalOrder());
        assertThatThrownBy(() -> decide(s, domain, identity).checked().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void staleLineageAmbiguousSourceAndUnknownVersionsFailClosed() {
        var s = scenario(false); var other = scenario(false);
        var run = run(s, result(VerificationMethod.SYMBOLIC_EQUIVALENCE, Outcome.PROVEN));
        assertThat(policy.decide(other.source(), other.solution(), other.plan(), run, VerificationDecision.SecondarySnapshot.absent(), execution, now, true, 1).status())
            .isEqualTo(VerificationDecision.Status.UNVERIFIED);
        var source = s.source();
        var ambiguous = new PlanningSource(source.identity(), source.problemType(), source.taskType(), source.statements(), source.variables(),
            source.restrictions(), source.assumptionIds(), source.constraintIds(), source.derivedRestrictionIds(), true);
        assertThat(policy.decide(ambiguous, s.solution(), s.plan(), run, VerificationDecision.SecondarySnapshot.absent(), execution, now, true, 1).reasons())
            .contains(VerificationDecision.Reason.REVIEW_REQUIRED);
        assertThatIllegalArgumentException().isThrownBy(() -> new Result(VerificationMethod.SYMBOLIC_EQUIVALENCE, "v999", VERIFIER_VERSION,
            Outcome.UNSUPPORTED, Reason.UNSUPPORTED_SUBSET, List.of(), null));
    }
    @Test void releaseGateCannotBeBypassedByExactEvidence() {
        var s = scenario(false);
        var run = run(s, result(VerificationMethod.DOMAIN_CONSTRAINT_CHECK, Outcome.PROVEN), result(VerificationMethod.SYMBOLIC_EQUIVALENCE, Outcome.PROVEN));
        var d = policy.decide(s.source(), s.solution(), s.plan(), run, VerificationDecision.SecondarySnapshot.absent(), execution, now, false, 1);
        assertThat(d.status()).isEqualTo(VerificationDecision.Status.UNVERIFIED);
        assertThat(d.recovery()).isEqualTo(VerificationDecision.Recovery.RELEASE_BLOCKED);
    }
    @Test void recoveryDistinguishesInfrastructureInvalidInputBudgetRateAndBoundedAttempts() {
        var recovery = new VerificationRecoveryPolicy(); var deadline = now.plusSeconds(60);
        assertThat(recovery.infrastructure(Reason.TIMEOUT, 1, now, deadline, true, true)).isEqualTo(VerificationDecision.Recovery.RETRY_VERIFICATION);
        assertThat(recovery.infrastructure(Reason.INVALID_RESPONSE, 1, now, deadline, true, true)).isEqualTo(VerificationDecision.Recovery.INVALID_INPUT);
        assertThat(recovery.infrastructure(Reason.INVALID_RESPONSE, 3, deadline, deadline, true, true)).isEqualTo(VerificationDecision.Recovery.INVALID_INPUT);
        assertThat(recovery.infrastructure(Reason.TIMEOUT, 3, now, deadline, true, true)).isEqualTo(VerificationDecision.Recovery.RETRY_LIMIT);
        assertThat(recovery.infrastructure(Reason.TIMEOUT, 1, deadline, deadline, true, true)).isEqualTo(VerificationDecision.Recovery.RETRY_LIMIT);
        assertThat(recovery.infrastructure(Reason.TIMEOUT, 1, now, deadline, false, true)).isEqualTo(VerificationDecision.Recovery.BUDGET_BLOCKED);
        assertThat(recovery.infrastructure(Reason.ADMISSION_LIMIT, 1, now, deadline, true, true)).isEqualTo(VerificationDecision.Recovery.RATE_LIMITED);
    }
    @Test void numericOnlyAndNumericConflictNeverPromoteCertainty() {
        var s = scenario(false);
        var input = new Request(execution, "composite-test", VerificationMethod.NUMERIC_SAMPLING,
            s.source().statements().getFirst(), s.solution().finalStatement(), s.source().variables(), s.source().restrictions());
        var request = new NumericDiagnostic.Request(NumericDiagnostic.scope(s.plan(), s.source()), input);
        for (var status : List.of(NumericDiagnostic.Status.AGREEMENT, NumericDiagnostic.Status.MISMATCH)) {
            var numeric = new NumericDiagnostic.Evidence(request, new NumericDiagnostic.Result(execution, "composite-test", request.scopeId(), status,
                status == NumericDiagnostic.Status.AGREEMENT ? NumericDiagnostic.Reason.SAMPLES_AGREE : NumericDiagnostic.Reason.SAMPLES_DIFFER,
                List.of(), 16, 16, 16, status == NumericDiagnostic.Status.AGREEMENT ? 16 : 0, status == NumericDiagnostic.Status.MISMATCH ? 16 : 0, 0));
            var run = run(s, result(VerificationMethod.DOMAIN_CONSTRAINT_CHECK, Outcome.PROVEN),
                result(VerificationMethod.SYMBOLIC_EQUIVALENCE, status == NumericDiagnostic.Status.MISMATCH ? Outcome.PROVEN : Outcome.UNSUPPORTED));
            var d = policy.decide(s.source(), s.solution(), s.plan(), run, VerificationDecision.SecondarySnapshot.absent(), execution, now, true, 1, numeric);
            assertThat(d.status()).isEqualTo(VerificationDecision.Status.UNVERIFIED);
            assertThat(d.contradictions()).isEmpty();
            if (status == NumericDiagnostic.Status.MISMATCH) assertThat(d.reasons()).contains(VerificationDecision.Reason.EVIDENCE_CONFLICT);
        }
    }

    @Test void calculusCompositeDependencyCannotBeSubstitutedWithGenericMethodEvidence() {
        var s = scenario(false); var source = s.source();
        var calculus = new PlanningSource(source.identity(), source.problemType(), source.taskType(), source.statements(), source.variables(),
            source.restrictions(), source.assumptionIds(), source.constraintIds(), source.derivedRestrictionIds(), false,
            new PlanningSource.CalculusMetadata(null, null, null));
        var d = policy.decide(calculus, s.solution(), s.plan(), run(s, result(VerificationMethod.SYMBOLIC_EQUIVALENCE, Outcome.PROVEN)),
            VerificationDecision.SecondarySnapshot.absent(), execution, now, true, 1);
        assertThat(d.status()).isEqualTo(VerificationDecision.Status.UNVERIFIED);
        assertThat(d.reasons()).contains(VerificationDecision.Reason.INVALID_EVIDENCE);
    }

    @Test void mixedFailureEvidenceAndSecondaryInputOrderingDoNotChangeRecovery() {
        var s = scenario(false);
        var timeout = result(VerificationMethod.DOMAIN_CONSTRAINT_CHECK, Outcome.EXECUTION_FAILED);
        var invalid = new Result(VerificationMethod.SYMBOLIC_EQUIVALENCE, "v1", VERIFIER_VERSION, Outcome.EXECUTION_FAILED,
            Reason.INVALID_RESPONSE, List.of(), null);
        assertThat(decide(s, timeout, invalid)).isEqualTo(decide(s, invalid, timeout));
        assertThat(decide(s, timeout, invalid).recovery()).isEqualTo(VerificationDecision.Recovery.INVALID_INPUT);
        var a = UUID.randomUUID(); var b = UUID.randomUUID();
        assertThat(new VerificationDecision.SecondarySnapshot(List.of(a, b), List.of(VerificationDecision.SecondarySignal.AGREEMENT, VerificationDecision.SecondarySignal.DISAGREEMENT)))
            .isEqualTo(new VerificationDecision.SecondarySnapshot(List.of(b, a), List.of(VerificationDecision.SecondarySignal.DISAGREEMENT, VerificationDecision.SecondarySignal.AGREEMENT)));
    }

}
