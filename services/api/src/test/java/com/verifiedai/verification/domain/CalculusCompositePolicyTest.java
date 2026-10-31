package com.verifiedai.verification.domain;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.verifiedai.calculus.CalculusIntegrationData;
import com.verifiedai.solving.application.SolutionCandidateValidator;
import com.verifiedai.solving.application.canonical.*;
import com.verifiedai.solving.domain.model.canonical.CanonicalSolution;
import com.verifiedai.solving.domain.model.canonical.CanonicalSolution.*;
import com.verifiedai.solving.domain.repository.CanonicalSolutionRepository.AcceptedArtifact;
import com.verifiedai.verification.application.VerificationSourceReader;
import com.verifiedai.verification.domain.model.*;
import com.verifiedai.verification.domain.model.CalculusExecution.*;
import com.verifiedai.verification.domain.model.MathematicalExecution.Outcome;
import com.verifiedai.verification.domain.model.MathematicalExecution.ExactValue;
import com.verifiedai.verification.domain.policy.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CalculusCompositePolicyTest {
    final VerificationCapabilities capabilities = new VerificationCapabilities();
    final VerificationPlanner planner = new VerificationPlanner(capabilities);
    final VerificationPolicy policy = new VerificationPolicy(planner);
    final Instant now = Instant.parse("2026-10-30T00:00:00Z");
    record Scenario(PlanningSource source, CanonicalSolution solution, VerificationPlan plan) { }
    CalculusCompositePolicyTest() {
        capabilities.configureCalculus(true, true, CalculusExecution.ALGORITHM, "test");
        capabilities.calculusReadiness(true);
    }
    Scenario scenario(int index) { return scenario((ObjectNode) CalculusIntegrationData.cases().get(index)); }
    Scenario scenario(ObjectNode example) {
        var raw = CalculusIntegrationData.source(example.path("parse"));
        var candidate = CalculusIntegrationData.candidate(raw, example.path("candidateExpression"));
        var origin = new Origin(Role.PRIMARY, UUID.randomUUID());
        var artifact = new AcceptedArtifact(raw.userId(), raw.problemSessionId(), raw.canonicalProblemId(), raw.problemParseId(),
            raw.problemParseRevision(), raw.canonicalRevision(), origin, null, UUID.randomUUID(), null,
            "solution-candidate-v2", candidate.toString(), "{\"provider\":\"LOCAL_FIXTURE\",\"model\":\"owner-calculus-fixture-v1\"}", now);
        var codec = new CanonicalMathCodec();
        var solution = new CanonicalCandidateMapper(new SolutionCandidateValidator(), codec).map(artifact, raw, Version.CALCULUS, now);
        var source = new VerificationSourceReader(codec).read(raw);
        return new Scenario(source, solution, planner.plan(source, solution).plan());
    }
    VerificationRun run(Scenario s, Outcome outcome, Reason reason) {
        var id = UUID.randomUUID();
        var method = Method.valueOf(s.plan().methods().getFirst().method().name());
        Certificate proof = null;
        if (outcome == Outcome.PROVEN || outcome == Outcome.DISPROVEN) {
            boolean proven = outcome == Outcome.PROVEN;
            var scope = switch (method) {
                case DERIVATIVE_BACKCHECK -> ProofScope.OPEN_INTERVAL;
                case INTEGRAL_DIFFERENTIATION_BACKCHECK -> ProofScope.ANTIDERIVATIVE_REPRESENTATIVE;
                case FINITE_LIMIT -> ProofScope.PUNCTURED_NEIGHBORHOOD;
            };
            var descriptor = method == Method.FINITE_LIMIT ? Descriptor.RATIONAL_FINITE_LIMIT
                : !proven ? Descriptor.RATIONAL_NONIDENTITY : method == Method.DERIVATIVE_BACKCHECK
                ? Descriptor.DERIVATIVE_IDENTITY : Descriptor.ANTIDERIVATIVE_IDENTITY;
            proof = new Certificate(descriptor, scope, method == Method.FINITE_LIMIT ? new ExactValue("1", "1") : null,
                method == Method.FINITE_LIMIT ? new ExactValue(proven ? "1" : "2", "1") : null);
        }
        var result = new Result(id, "calculus-composite", CalculusEvidence.scope(s.source().identity(), s.solution().id()), method,
            "v1", CalculusExecution.ALGORITHM, outcome, reason, s.source().restrictions().stream().map(PlanningSource.Restriction::id).toList(),
            proof, reason == Reason.NUMERICAL_DIAGNOSTIC ? new Diagnostic("calculus-binary64-v1", 16, 16, 16) : null);
        var metadata = s.source().calculus();
        var calculus = new CalculusEvidence("calculus-evidence-v1", "canonical-problem-v2", "solution-candidate-v2", "x",
            PlanningSource.Domain.REAL, metadata.interval(), metadata.approach(), metadata.integralScope(), s.source().restrictions(), result);
        return withEvidence(s, calculus);
    }
    VerificationRun withEvidence(Scenario s, CalculusEvidence calculus) {
        var id = calculus.result().executionId();
        var method = CalculusEvidence.method(calculus.result().method());
        var e = new VerificationEvidence(VerificationEvidence.identity(id, method), id, s.plan().id(), s.plan().policyVersion(),
            s.plan().capabilityVersion(), s.source().identity(), s.solution().id(), s.solution().version(), method.purpose(),
            s.source().variables(), null, "calculus-composite", now, calculus);
        return new VerificationRun(id, id, s.plan().id(), s.plan().policyVersion(), s.plan().capabilityVersion(),
            s.source().identity(), s.solution().id(), s.solution().version(), "calculus-composite", now, List.of(e));
    }
    VerificationDecision decide(Scenario s, VerificationRun run) {
        return policy.decide(s.source(), s.solution(), s.plan(), run, VerificationDecision.SecondarySnapshot.absent(), run.requestKey(), now, true, 1);
    }
    @Test void exactCertificatesRetainAllThreeScopesWithoutClaimingStepsOrIntegralFamilies() {
        for (int i : List.of(0, 2, 3)) {
            var s = scenario(i);
            var reason = i == 0 ? Reason.EXACT_DERIVATIVE : i == 2 ? Reason.EXACT_ANTIDERIVATIVE : Reason.EXACT_FINITE_LIMIT;
            var run = run(s, Outcome.PROVEN, reason);
            var composite = new CompositeEvidencePolicy(planner).assess(s.source(), s.solution(), s.plan(), run, null);
            assertThat(composite.version()).isEqualTo(CompositeEvidenceAssessment.CALCULUS_VERSION);
            assertThat(composite.category()).isEqualTo(CompositeEvidenceAssessment.Category.EXACT_SUPPORT);
            assertThat(composite.domainCoverage()).isEqualTo(CompositeEvidenceAssessment.DomainCoverage.ESTABLISHED);
            var d = decide(s, run);
            assertThat(d).isEqualTo(decide(s, run));
            assertThat(d.status()).isEqualTo(VerificationDecision.Status.VERIFIED);
            assertThat(d.scope().name()).isEqualTo(run.evidence().getFirst().calculus().result().proof().scope().name());
            assertThat(d.scope()).isNotEqualTo(VerificationDecision.Scope.FINAL_ANSWER);
            assertThat(d.checked()).containsExactly(s.plan().methods().getFirst().method());
            assertThat(d.uncertainty()).containsExactly(VerificationDecision.Uncertainty.EXPLANATION_STEPS_UNCHECKED);
            var blocked = policy.decide(s.source(), s.solution(), s.plan(), run, VerificationDecision.SecondarySnapshot.absent(),
                run.requestKey(), now, false, 1);
            assertThat(blocked.status()).isEqualTo(VerificationDecision.Status.UNVERIFIED);
            assertThat(blocked.recovery()).isEqualTo(VerificationDecision.Recovery.RELEASE_BLOCKED);
        }
    }
    @Test void numericalAgreementDomainObstructionUnsupportedAndFailuresNeverBecomeProof() {
        var s = scenario(3);
        for (var reason : List.of(Reason.NUMERICAL_DIAGNOSTIC, Reason.NONFINITE_LIMIT, Reason.SYMBOLIC_UNRESOLVED,
            Reason.DOMAIN_OBSTRUCTION, Reason.UNSUPPORTED_SUBSET, Reason.TIMEOUT, Reason.INVALID_RESPONSE, Reason.ADMISSION_LIMIT)) {
            var outcome = switch (reason) {
                case DOMAIN_OBSTRUCTION -> Outcome.BLOCKED;
                case UNSUPPORTED_SUBSET -> Outcome.UNSUPPORTED;
                case TIMEOUT, INVALID_RESPONSE, ADMISSION_LIMIT -> Outcome.EXECUTION_FAILED;
                default -> Outcome.INCONCLUSIVE;
            };
            var d = decide(s, run(s, outcome, reason));
            assertThat(d.status()).as(reason.name()).isEqualTo(VerificationDecision.Status.UNVERIFIED);
            assertThat(d.checked()).isEmpty(); assertThat(d.contradictions()).isEmpty();
            assertThat(d.unchecked()).containsExactly(VerificationMethod.FINITE_LIMIT);
            if (reason == Reason.TIMEOUT) assertThat(d.recovery()).isEqualTo(VerificationDecision.Recovery.RETRY_VERIFICATION);
            if (reason == Reason.INVALID_RESPONSE) assertThat(d.recovery()).isEqualTo(VerificationDecision.Recovery.INVALID_INPUT);
            if (reason == Reason.ADMISSION_LIMIT) assertThat(d.recovery()).isEqualTo(VerificationDecision.Recovery.RATE_LIMITED);
            if (reason == Reason.DOMAIN_OBSTRUCTION) assertThat(d.recovery()).isEqualTo(VerificationDecision.Recovery.REVIEW_SOURCE);
        }
    }
    @Test void secondaryAgreementCannotOverrideDisproofAndDisagreementBlocksExactPromotion() {
        for (int i : List.of(0, 2, 3)) {
            var s = scenario(i); var run = run(s, Outcome.DISPROVEN, Reason.EXACT_CONTRADICTION);
            for (var signal : VerificationDecision.SecondarySignal.values()) {
                var d = policy.decide(s.source(), s.solution(), s.plan(), run,
                    new VerificationDecision.SecondarySnapshot(List.of(), List.of(signal)), run.requestKey(), now, true, 1);
                assertThat(d.status()).isEqualTo(VerificationDecision.Status.UNVERIFIED);
                assertThat(d.contradictions()).containsExactly(s.plan().methods().getFirst().method());
                assertThat(d.recovery()).isEqualTo(VerificationDecision.Recovery.REVIEW_CONTRADICTION);
            }
        }
        var s = scenario(0); var run = run(s, Outcome.PROVEN, Reason.EXACT_DERIVATIVE);
        var d = policy.decide(s.source(), s.solution(), s.plan(), run,
            new VerificationDecision.SecondarySnapshot(List.of(), List.of(VerificationDecision.SecondarySignal.DISAGREEMENT)),
            run.requestKey(), now, true, 1);
        assertThat(d.status()).isEqualTo(VerificationDecision.Status.UNVERIFIED);
        assertThat(d.recovery()).isEqualTo(VerificationDecision.Recovery.RECONCILE_SOLVERS);
    }
    @Test void intervalApproachAndRestrictionAstCannotBeSubstitutedUnderMatchingIds() {
        var s = scenario(3); var run = run(s, Outcome.PROVEN, Reason.EXACT_FINITE_LIMIT);
        var e = run.evidence().getFirst().calculus();
        var changedInterval = new CalculusEvidence(e.schemaVersion(), e.sourceVersion(), e.candidateVersion(), e.variable(), e.domain(),
            new Interval(new ExactValue("-7", "1"), new ExactValue("7", "1")), e.approach(), e.integralScope(), e.restrictions(), e.result());
        var changedDirection = new CalculusEvidence(e.schemaVersion(), e.sourceVersion(), e.candidateVersion(), e.variable(), e.domain(),
            e.interval(), new Approach(e.approach().target(), Direction.LEFT), e.integralScope(), e.restrictions(), e.result());
        var changedTarget = new CalculusEvidence(e.schemaVersion(), e.sourceVersion(), e.candidateVersion(), e.variable(), e.domain(),
            e.interval(), new Approach(new ExactValue("9", "1"), e.approach().direction()), e.integralScope(), e.restrictions(), e.result());
        for (var forged : List.of(changedInterval, changedDirection, changedTarget))
            assertThat(decide(s, withEvidence(s, forged)).reasons()).contains(VerificationDecision.Reason.INVALID_EVIDENCE);
        var example = (ObjectNode) CalculusIntegrationData.cases().getFirst().deepCopy();
        var r = ((ObjectNode) example.path("parse").path("calculus")).withArray("restrictions").addObject();
        r.put("id", "scope-r").put("relation", "GREATER_THAN");
        r.putObject("left").put("kind", "VARIABLE").put("symbol", "x");
        r.putObject("right").put("kind", "NUMBER").put("numericType", "INTEGER").put("value", "0");
        var constrained = scenario(example);
        var c = run(constrained, Outcome.BLOCKED, Reason.DOMAIN_OBSTRUCTION).evidence().getFirst().calculus();
        var restriction = c.restrictions().getFirst();
        var substituted = new PlanningSource.Restriction(restriction.id(), PlanningSource.Relation.LESS_THAN, restriction.left(), restriction.right());
        var forged = new CalculusEvidence(c.schemaVersion(), c.sourceVersion(), c.candidateVersion(), c.variable(), c.domain(),
            c.interval(), c.approach(), c.integralScope(), List.of(substituted), c.result());
        assertThat(decide(constrained, withEvidence(constrained, forged)).reasons()).contains(VerificationDecision.Reason.INVALID_EVIDENCE);
    }
    @Test void staleSourcePlanAndCapabilityNeverReuseProofAndRetryRemainsBounded() {
        var s = scenario(0); var run = run(s, Outcome.PROVEN, Reason.EXACT_DERIVATIVE);
        var other = scenario(0);
        assertThat(policy.decide(other.source(), other.solution(), other.plan(), run, VerificationDecision.SecondarySnapshot.absent(),
            run.requestKey(), now, true, 1).reasons()).contains(VerificationDecision.Reason.STALE_LINEAGE);
        var timeout = run(s, Outcome.EXECUTION_FAILED, Reason.TIMEOUT);
        assertThat(policy.decide(s.source(), s.solution(), s.plan(), timeout, VerificationDecision.SecondarySnapshot.absent(),
            timeout.requestKey(), now, true, 3).recovery()).isEqualTo(VerificationDecision.Recovery.RETRY_LIMIT);
        capabilities.configureCalculus(false, true, CalculusExecution.ALGORITHM, "test");
        assertThat(decide(s, run).reasons()).contains(VerificationDecision.Reason.INVALID_EVIDENCE);
    }
}
