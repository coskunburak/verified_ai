package com.verifiedai.verification.domain;

import com.verifiedai.solving.domain.model.canonical.*;
import com.verifiedai.verification.application.VerificationSourceReader;
import com.verifiedai.verification.domain.model.*;
import com.verifiedai.verification.domain.model.CompositeEvidenceAssessment.Category;
import com.verifiedai.verification.domain.model.CompositeEvidenceAssessment.DomainCoverage;
import com.verifiedai.verification.domain.model.MathematicalExecution.*;
import com.verifiedai.verification.domain.policy.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.verifiedai.verification.PlanningTestData.*;
import static com.verifiedai.verification.domain.model.MathematicalExecution.VERIFIER_VERSION;
import static org.assertj.core.api.Assertions.*;

class CompositeEvidencePolicyTest {
    final VerificationPlanner planner = new VerificationPlanner(new VerificationCapabilities(true, true,
        "ARITHMETIC_EVALUATION,SYMBOLIC_EQUIVALENCE,EQUATION_SUBSTITUTION,DOMAIN_CONSTRAINT_CHECK,SOLUTION_SET_COMPLETENESS"));
    final CompositeEvidencePolicy policy = new CompositeEvidencePolicy(planner);
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
    CompositeEvidenceAssessment assess(Scenario s, Result... results) { return policy.assess(s.source(), s.solution(), s.plan(), run(s, results), null); }
    NumericDiagnostic.Evidence numeric(Scenario s, NumericDiagnostic.Status status) {
        var input = new Request(execution, "composite-test", VerificationMethod.NUMERIC_SAMPLING, s.source().statements().getFirst(),
            s.solution().finalStatement(), s.source().variables(), s.source().restrictions());
        var request = new NumericDiagnostic.Request(NumericDiagnostic.scope(s.plan(), s.source()), input);
        var diagnostic = new NumericDiagnostic.Result(execution, "composite-test", request.scopeId(), status,
            status == NumericDiagnostic.Status.AGREEMENT ? NumericDiagnostic.Reason.SAMPLES_AGREE : NumericDiagnostic.Reason.SAMPLES_DIFFER,
            List.of(), 16, 16, 16, status == NumericDiagnostic.Status.AGREEMENT ? 16 : 0, status == NumericDiagnostic.Status.MISMATCH ? 16 : 0, 0);
        return new NumericDiagnostic.Evidence(request, diagnostic);
    }
    @Test void exactCompositionIsOrderedImmutableAndNotAProductDecision() {
        var s = scenario(false);
        var domain = result(VerificationMethod.DOMAIN_CONSTRAINT_CHECK, Outcome.PROVEN);
        var identity = result(VerificationMethod.SYMBOLIC_EQUIVALENCE, Outcome.PROVEN);
        var a = assess(s, domain, identity); var b = assess(s, identity, domain);
        assertThat(a).isEqualTo(b);
        assertThat(a.category()).isEqualTo(Category.EXACT_SUPPORT);
        assertThat(a.domainCoverage()).isEqualTo(DomainCoverage.ESTABLISHED);
        assertThat(a.exactProofPresent()).isTrue(); assertThat(a.exactContradictionPresent()).isFalse();
        assertThatThrownBy(() -> a.required().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> a.run().evidence().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> a.reasons().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void exactContradictionOverridesNumericalAgreementAndKeepsItsClaim() {
        var s = scenario(false);
        var run = run(s, result(VerificationMethod.DOMAIN_CONSTRAINT_CHECK, Outcome.PROVEN), result(VerificationMethod.SYMBOLIC_EQUIVALENCE, Outcome.DISPROVEN));
        var assessed = policy.assess(s.source(), s.solution(), s.plan(), run, numeric(s, NumericDiagnostic.Status.AGREEMENT));
        assertThat(assessed.category()).isEqualTo(Category.EXACT_CONTRADICTION);
        assertThat(assessed.exactContradictionPresent()).isTrue();
        assertThat(assessed.reasons()).contains(CompositeEvidenceAssessment.Reason.NUMERIC_CONFLICT);
        assertThat(assessed.run().evidence().getLast().claim()).isEqualTo(VerificationMethod.SYMBOLIC_EQUIVALENCE.purpose());
    }
    @Test void numericMismatchAgainstCompleteExactSupportIsDiagnosticConflict() {
        var s = scenario(false);
        var run = run(s, result(VerificationMethod.DOMAIN_CONSTRAINT_CHECK, Outcome.PROVEN), result(VerificationMethod.SYMBOLIC_EQUIVALENCE, Outcome.PROVEN));
        var assessed = policy.assess(s.source(), s.solution(), s.plan(), run, numeric(s, NumericDiagnostic.Status.MISMATCH));
        assertThat(assessed.category()).isEqualTo(Category.CONFLICTING_EVIDENCE);
        assertThat(assessed.exactContradictionPresent()).isFalse();
    }
    @Test void domainAndCompletenessCannotBeInferredFromOtherProofs() {
        var s = scenario(false);
        var a = assess(s, result(VerificationMethod.SYMBOLIC_EQUIVALENCE, Outcome.PROVEN));
        assertThat(a.category()).isEqualTo(Category.INCOMPLETE_EVIDENCE);
        assertThat(a.domainCoverage()).isEqualTo(DomainCoverage.UNRESOLVED);
        assertThat(a.missing()).containsExactly(VerificationMethod.DOMAIN_CONSTRAINT_CHECK);
        var equation = scenario(true);
        var b = assess(equation, result(VerificationMethod.DOMAIN_CONSTRAINT_CHECK, Outcome.PROVEN), result(VerificationMethod.EQUATION_SUBSTITUTION, Outcome.PROVEN));
        assertThat(b.reasons()).contains(CompositeEvidenceAssessment.Reason.COMPLETENESS_UNRESOLVED);
        assertThat(b.category()).isEqualTo(Category.INCOMPLETE_EVIDENCE);
        var incomplete = assess(equation, result(VerificationMethod.DOMAIN_CONSTRAINT_CHECK, Outcome.PROVEN),
            result(VerificationMethod.EQUATION_SUBSTITUTION, Outcome.PROVEN), result(VerificationMethod.SOLUTION_SET_COMPLETENESS, Outcome.DISPROVEN));
        assertThat(incomplete.category()).isEqualTo(Category.EXACT_CONTRADICTION);
    }
    @Test void unsupportedAndFailureNeverBecomeProofEvenWithSampling() {
        var s = scenario(false);
        for (var status : List.of(NumericDiagnostic.Status.AGREEMENT, NumericDiagnostic.Status.MISMATCH)) {
            var run = run(s, result(VerificationMethod.SYMBOLIC_EQUIVALENCE, Outcome.UNSUPPORTED));
            var a = policy.assess(s.source(), s.solution(), s.plan(), run, numeric(s, status));
            assertThat(a.category()).isEqualTo(Category.UNSUPPORTED_SCOPE);
            assertThat(a.exactProofPresent()).isFalse(); assertThat(a.exactContradictionPresent()).isFalse();
        }
        var a = assess(s, result(VerificationMethod.SYMBOLIC_EQUIVALENCE, Outcome.EXECUTION_FAILED));
        assertThat(a.failed()).containsExactly(VerificationMethod.SYMBOLIC_EQUIVALENCE);
        assertThat(a.category()).isEqualTo(Category.EXECUTION_UNAVAILABLE);
    }
    @Test void staleCandidatePlanAndSourceAreRejected() {
        var s = scenario(false); var run = run(s, result(VerificationMethod.SYMBOLIC_EQUIVALENCE, Outcome.PROVEN));
        var different = scenario(false);
        assertThatIllegalArgumentException().isThrownBy(() -> policy.assess(different.source(), different.solution(), different.plan(), run, null));
        var plan = s.plan();
        var forged = new VerificationPlan(plan.id(), "future-policy", plan.capabilityVersion(), plan.lineage(), plan.solutionId(), plan.solutionVersion(), plan.methods(), plan.limitations());
        assertThatIllegalArgumentException().isThrownBy(() -> policy.assess(s.source(), s.solution(), forged, run, null));
        var identity = s.source().identity();
        var stale = new PlanningSource(new PlanningSource.Identity(identity.userId(), identity.sessionId(), identity.canonicalProblemId(), identity.parseId(), 2, 2),
            s.source().problemType(), s.source().taskType(), s.source().statements(), s.source().variables(), List.of(), List.of(), List.of(), List.of(), false);
        assertThatIllegalArgumentException().isThrownBy(() -> policy.assess(stale, s.solution(), plan, run, null));
        var wrongVersion = new VerificationPlan(plan.id(), plan.policyVersion(), plan.capabilityVersion(), plan.lineage(), plan.solutionId(), CanonicalSolution.Version.CALCULUS, plan.methods(), plan.limitations());
        assertThatIllegalArgumentException().isThrownBy(() -> policy.assess(s.source(), s.solution(), wrongVersion, run, null));
    }
    @Test void duplicateEmptyOversizedAndUnplannedEvidenceAreRejected() {
        var s = scenario(false); var proof = result(VerificationMethod.SYMBOLIC_EQUIVALENCE, Outcome.PROVEN);
        assertThatIllegalArgumentException().isThrownBy(() -> run(s));
        assertThatIllegalArgumentException().isThrownBy(() -> run(s, proof, proof));
        assertThatIllegalArgumentException().isThrownBy(() -> run(s, proof, proof, proof, proof, proof, proof));
        assertThatIllegalArgumentException().isThrownBy(() -> assess(s, result(VerificationMethod.ARITHMETIC_EVALUATION, Outcome.PROVEN)));
        assertThatIllegalArgumentException().isThrownBy(() -> new Result(VerificationMethod.SYMBOLIC_EQUIVALENCE, "v1", VERIFIER_VERSION,
            Outcome.PROVEN, Reason.POLYNOMIAL_IDENTITY, List.of(), new Proof(Descriptor.DOMAIN_CHECK, null, null, null)));
        assertThatIllegalArgumentException().isThrownBy(() -> new Result(VerificationMethod.NUMERIC_SAMPLING, "v1", VERIFIER_VERSION,
            Outcome.PROVEN, Reason.POLYNOMIAL_IDENTITY, List.of(), new Proof(Descriptor.POLYNOMIAL_COEFFICIENT_COMPARISON, null, null, null)));
    }
    @Test void restrictionScopeAndNumericCandidateMismatchAreRejected() {
        var s = scenario(false);
        var result = new Result(VerificationMethod.SYMBOLIC_EQUIVALENCE, "v1", VERIFIER_VERSION, Outcome.PROVEN, Reason.POLYNOMIAL_IDENTITY,
            List.of("different-domain"), new Proof(Descriptor.POLYNOMIAL_COEFFICIENT_COMPARISON, null, null, null));
        assertThatIllegalArgumentException().isThrownBy(() -> assess(s, result));
        var numeric = numeric(s, NumericDiagnostic.Status.AGREEMENT);
        var wrong = new NumericDiagnostic.Request(numeric.request().scopeId(), new Request(execution, "composite-test", VerificationMethod.NUMERIC_SAMPLING,
            s.source().statements().getFirst(), new MathStatement.Expression(number("999")), s.source().variables(), List.of()));
        var forged = new NumericDiagnostic.Evidence(wrong, numeric.result());
        assertThatIllegalArgumentException().isThrownBy(() -> policy.assess(s.source(), s.solution(), s.plan(),
            run(s, result(VerificationMethod.SYMBOLIC_EQUIVALENCE, Outcome.PROVEN)), forged));
    }
    @Test void allDisabledIsUnavailableAndCannotAcceptSuccessfulEvidence() {
        var s = scenario(false); var disabledPlanner = new VerificationPlanner(VerificationCapabilities.none());
        var disabled = new Scenario(s.source(), s.solution(), disabledPlanner.plan(s.source(), s.solution()).plan());
        var policy = new CompositeEvidencePolicy(disabledPlanner);
        var blocked = run(disabled, result(VerificationMethod.SYMBOLIC_EQUIVALENCE, Outcome.BLOCKED));
        assertThat(policy.assess(disabled.source(), disabled.solution(), disabled.plan(), blocked, null).category()).isEqualTo(Category.EXECUTION_UNAVAILABLE);
        assertThatIllegalArgumentException().isThrownBy(() -> policy.assess(disabled.source(), disabled.solution(), disabled.plan(),
            run(disabled, result(VerificationMethod.SYMBOLIC_EQUIVALENCE, Outcome.PROVEN)), null));
    }
    @Test void incompatibleExactDomainClaimsAreExplicitlyConflicting() {
        var s = scenario(true);
        var domainFailure = new Result(VerificationMethod.EQUATION_SUBSTITUTION, "v1", VERIFIER_VERSION, Outcome.DISPROVEN,
            Reason.DOMAIN_VIOLATION, List.of(), new Proof(Descriptor.DOMAIN_CHECK, null, null, null));
        var assessment = assess(s, result(VerificationMethod.DOMAIN_CONSTRAINT_CHECK, Outcome.PROVEN), domainFailure);
        assertThat(assessment.category()).isEqualTo(Category.CONFLICTING_EVIDENCE);
        assertThat(assessment.exactContradictionPresent()).isTrue();
        assertThat(assessment.reasons()).contains(CompositeEvidenceAssessment.Reason.EXACT_EVIDENCE_CONFLICT);
    }
    @Test void contradictorySourceIsBlockedBeforeCandidateDomainFailureIsInterpreted() {
        var s = scenario(true);
        var restrictions = List.of(
            new PlanningSource.Restriction("positive", PlanningSource.Relation.GREATER_THAN, x(), number("0")),
            new PlanningSource.Restriction("negative", PlanningSource.Relation.LESS_THAN, x(), number("0")));
        var ids = List.of("positive", "negative");
        var source = new PlanningSource(s.source().identity(), s.source().problemType(), s.source().taskType(), s.source().statements(),
            s.source().variables(), restrictions, List.of(), ids, List.of(), false);
        var old = s.solution();
        var candidate = new CanonicalSolution(old.id(), old.lineage(), old.version(), old.provenance(), old.generationOrigin(), old.problemType(),
            old.taskType(), old.method(), old.finalStatement(), old.steps(), List.of(), ids, List.of(), List.of(), List.of(), old.createdAt());
        var scenario = new Scenario(source, candidate, planner.plan(source, candidate).plan());
        var failure = new Result(VerificationMethod.DOMAIN_CONSTRAINT_CHECK, "v1", VERIFIER_VERSION, Outcome.DISPROVEN,
            Reason.DOMAIN_VIOLATION, ids, new Proof(Descriptor.DOMAIN_CHECK, null, null, null));
        var assessment = assess(scenario, failure);
        assertThat(assessment.category()).isEqualTo(Category.BLOCKED_SCOPE);
        assertThat(assessment.domainCoverage()).isEqualTo(DomainCoverage.UNSATISFIABLE);
        assertThat(assessment.reasons().getFirst()).isEqualTo(CompositeEvidenceAssessment.Reason.CONTRADICTORY_SOURCE_SCOPE);
    }

    @Test void diagnosticImplementationDoesNotApproveAnExactMethod() {
        var capabilities = new VerificationCapabilities();
        assertThat(capabilities.numericCapability().implemented()).isTrue();
        assertThat(capabilities.numericCapability().runtimeEnabled()).isFalse();
        capabilities.configureNumeric(true, true, "test");
        assertThat(capabilities.numericCapability().productionApproved()).isFalse();
        assertThat(capabilities.availableMethods()).doesNotContain(VerificationMethod.NUMERIC_SAMPLING);
        assertThatIllegalArgumentException().isThrownBy(() -> capabilities.configureNumeric(true, true, "production"));
    }

    @Test void evidenceCannotChangeTheDeclaredDomainEvenWithinOneRun() {
        var s = scenario(false); var original = run(s, result(VerificationMethod.SYMBOLIC_EQUIVALENCE, Outcome.PROVEN));
        var old = original.evidence().getFirst();
        var changed = new VerificationEvidence(old.id(), old.executionId(), old.planId(), old.policyVersion(), old.capabilityVersion(),
            old.source(), old.solutionId(), old.solutionVersion(), old.claim(), Map.of("x", PlanningSource.Domain.INTEGER), old.result(), old.correlationId(), old.createdAt());
        var forged = new VerificationRun(original.id(), original.requestKey(), original.planId(), original.policyVersion(), original.capabilityVersion(),
            original.source(), original.solutionId(), original.solutionVersion(), original.correlationId(), original.createdAt(), List.of(changed));
        assertThatIllegalArgumentException().isThrownBy(() -> policy.assess(s.source(), s.solution(), s.plan(), forged, null));
    }

    @Test void completeRootSetCannotCoexistWithAnInvalidRoot() {
        var s = scenario(true);
        var assessed = assess(s, result(VerificationMethod.DOMAIN_CONSTRAINT_CHECK, Outcome.PROVEN),
            result(VerificationMethod.EQUATION_SUBSTITUTION, Outcome.DISPROVEN),
            result(VerificationMethod.SOLUTION_SET_COMPLETENESS, Outcome.PROVEN));
        assertThat(assessed.category()).isEqualTo(Category.CONFLICTING_EVIDENCE);
        assertThat(assessed.exactContradictionPresent()).isTrue();
        assertThat(assessed.reasons()).contains(CompositeEvidenceAssessment.Reason.EXACT_EVIDENCE_CONFLICT);
    }

    @Test void assessmentCannotBeForgedIntoVacuousOrIncompleteSupport() {
        var s = scenario(false);
        var incomplete = assess(s, result(VerificationMethod.SYMBOLIC_EQUIVALENCE, Outcome.PROVEN));
        assertThatIllegalArgumentException().isThrownBy(() -> new CompositeEvidenceAssessment(CompositeEvidenceAssessment.VERSION,
            incomplete.run(), Category.EXACT_SUPPORT, DomainCoverage.ESTABLISHED, true, false,
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), incomplete.reasons(), null));
        assertThatIllegalArgumentException().isThrownBy(() -> new CompositeEvidenceAssessment(CompositeEvidenceAssessment.VERSION,
            incomplete.run(), Category.EXACT_SUPPORT, DomainCoverage.ESTABLISHED, true, false,
            incomplete.required(), incomplete.available(), List.of(), List.of(), List.of(), List.of(), incomplete.reasons(), null));
    }

    @Test void unknownSourceDomainRemainsIncompleteWhileOperationalFailuresStayVisible() {
        var s = scenario(true);
        var source = new PlanningSource(s.source().identity(), s.source().problemType(), s.source().taskType(),
            s.source().statements(), Map.of("x", PlanningSource.Domain.UNKNOWN), List.of(), List.of(), List.of(), List.of(), false);
        var scenario = new Scenario(source, s.solution(), planner.plan(source, s.solution()).plan());
        var assessment = assess(scenario, result(VerificationMethod.DOMAIN_CONSTRAINT_CHECK, Outcome.EXECUTION_FAILED),
            result(VerificationMethod.EQUATION_SUBSTITUTION, Outcome.EXECUTION_FAILED),
            result(VerificationMethod.SOLUTION_SET_COMPLETENESS, Outcome.EXECUTION_FAILED));
        assertThat(assessment.category()).isEqualTo(Category.INCOMPLETE_EVIDENCE);
        assertThat(assessment.domainCoverage()).isEqualTo(DomainCoverage.UNRESOLVED);
        assertThat(assessment.failed()).containsExactlyElementsOf(assessment.required());
        assertThat(assessment.reasons()).contains(CompositeEvidenceAssessment.Reason.DOMAIN_UNRESOLVED,
            CompositeEvidenceAssessment.Reason.FAILED_METHOD, CompositeEvidenceAssessment.Reason.COMPLETENESS_UNRESOLVED);
        assertThat(assessment.exactProofPresent()).isFalse();
        assertThat(assessment.exactContradictionPresent()).isFalse();
    }

}
