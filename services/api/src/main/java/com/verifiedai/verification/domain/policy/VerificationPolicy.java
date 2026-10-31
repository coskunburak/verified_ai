package com.verifiedai.verification.domain.policy;

import com.verifiedai.solving.domain.model.canonical.CanonicalSolution;
import com.verifiedai.verification.domain.model.*;
import com.verifiedai.verification.domain.model.VerificationDecision.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/** Sole aggregate status authority. All inputs are server-assembled; no inference or I/O. */
public final class VerificationPolicy {
    private final VerificationPlanner planner;
    public VerificationPolicy(VerificationPlanner planner) { this.planner = Objects.requireNonNull(planner); }
    public VerificationDecision decide(PlanningSource source, CanonicalSolution solution, VerificationPlan plan,
        VerificationRun run, SecondarySnapshot secondary, UUID key, Instant now, boolean releaseAccepted, int attempts) {
        return decide(source, solution, plan, run, secondary, key, now, releaseAccepted, attempts, null);
    }
    /** Optional diagnostic must already be bound to this exact run. It never creates proof. */
    public VerificationDecision decide(PlanningSource source, CanonicalSolution solution, VerificationPlan plan,
        VerificationRun run, SecondarySnapshot secondary, UUID key, Instant now, boolean releaseAccepted, int attempts,
        NumericDiagnostic.Evidence diagnostic) {
        var reasons = EnumSet.noneOf(Reason.class);
        var uncertainty = EnumSet.of(Uncertainty.EXPLANATION_STEPS_UNCHECKED);
        var checked = new ArrayList<VerificationMethod>();
        var unchecked = new ArrayList<VerificationMethod>();
        var contradictions = new ArrayList<VerificationMethod>();
        Recovery recovery = Recovery.ADDITIONAL_EVIDENCE;
        Status status = Status.UNVERIFIED;
        Scope scope = Scope.NONE;
        CompositeEvidenceAssessment composite = null;
        boolean valid = true;
        if (!VerificationPlanner.matches(source.identity(), solution) || !run.source().equals(source.identity())
            || !run.solutionId().equals(solution.id()) || !run.solutionVersion().equals(solution.version())) {
            reasons.add(Reason.STALE_LINEAGE); recovery = Recovery.FRESH_CALCULATION; valid = false;
        } else if (source.reviewRequired() || !solution.ambiguities().isEmpty() || !solution.unresolvedIssues().isEmpty()
            || !source.assumptionIds().isEmpty()) {
            reasons.add(Reason.REVIEW_REQUIRED); recovery = Recovery.REVIEW_SOURCE; valid = false;
        } else {
            try { composite = new CompositeEvidencePolicy(planner).assess(source, solution, plan, run, diagnostic); }
            catch (IllegalArgumentException | NullPointerException e) {
                reasons.add(Reason.INVALID_EVIDENCE); recovery = Recovery.INVALID_INPUT; valid = false;
            }
        }
        if (valid) {
            for (var method : composite.required()) {
                var item = run.evidence().stream().filter(e -> e.method() == method).findFirst();
                if (item.isPresent() && item.get().outcome() == MathematicalExecution.Outcome.PROVEN) checked.add(method);
                else {
                    unchecked.add(method);
                    if (item.isPresent() && item.get().outcome() == MathematicalExecution.Outcome.DISPROVEN) contradictions.add(method);
                }
            }
            boolean sourceInvalid = composite.domainCoverage() == CompositeEvidenceAssessment.DomainCoverage.UNSATISFIABLE;
            boolean conflict = composite.category() == CompositeEvidenceAssessment.Category.CONFLICTING_EVIDENCE;
            if (composite.domainCoverage() == CompositeEvidenceAssessment.DomainCoverage.UNRESOLVED
                || composite.domainCoverage() == CompositeEvidenceAssessment.DomainCoverage.VIOLATED || sourceInvalid) {
                reasons.add(Reason.SOURCE_SCOPE_UNRESOLVED); uncertainty.add(Uncertainty.DOMAIN_UNRESOLVED);
            }
            if (unchecked.contains(VerificationMethod.DOMAIN_CONSTRAINT_CHECK)) {
                reasons.add(Reason.DOMAIN_UNCHECKED); uncertainty.add(Uncertainty.DOMAIN_UNRESOLVED);
            }
            if (unchecked.contains(VerificationMethod.SOLUTION_SET_COMPLETENESS)) {
                reasons.add(Reason.COMPLETENESS_UNCHECKED); uncertainty.add(Uncertainty.COMPLETENESS_UNRESOLVED);
            }
            if (!composite.available().containsAll(composite.required())) reasons.add(Reason.CAPABILITY_UNAVAILABLE);
            if (!composite.failed().isEmpty()) {
                reasons.add(Reason.EXECUTION_FAILURE); uncertainty.add(Uncertainty.EXECUTION_UNAVAILABLE);
                var failures = composite.run().evidence().stream().filter(e -> e.outcome() == MathematicalExecution.Outcome.EXECUTION_FAILED).toList();
                var failure = failures.stream().filter(e -> failureReason(e) == MathematicalExecution.Reason.INVALID_RESPONSE)
                    .findFirst().orElse(failures.getFirst());
                recovery = new VerificationRecoveryPolicy().infrastructure(failureReason(failure), attempts, now,
                    run.createdAt().plusSeconds(300), true, true);
            } else if (!composite.unsupported().isEmpty() || reasons.contains(Reason.CAPABILITY_UNAVAILABLE)) recovery = Recovery.UNSUPPORTED_CAPABILITY;
            boolean secondaryConflict = false;
            for (var signal : secondary.signals()) {
                switch (signal) {
                    case DISAGREEMENT -> { reasons.add(Reason.SECONDARY_DISAGREEMENT); secondaryConflict = true; }
                    case INCOMPATIBLE_ASSUMPTIONS -> { reasons.add(Reason.SECONDARY_ASSUMPTIONS); secondaryConflict = true; }
                    case INVALID_INDEPENDENCE, PENDING, INDETERMINATE -> { reasons.add(Reason.SECONDARY_INVALID); secondaryConflict = true; }
                    case STALE_LINEAGE -> { reasons.add(Reason.SECONDARY_STALE); secondaryConflict = true; }
                    case AGREEMENT -> reasons.add(Reason.SECONDARY_AGREEMENT);
                    case NONE -> { }
                }
            }
            if (secondaryConflict) { uncertainty.add(Uncertainty.SECONDARY_CONFLICT); recovery = Recovery.RECONCILE_SOLVERS; }
            if (sourceInvalid || source.calculus() != null && composite.domainCoverage() == CompositeEvidenceAssessment.DomainCoverage.VIOLATED)
                recovery = Recovery.REVIEW_SOURCE;
            if (conflict) { reasons.add(Reason.EVIDENCE_CONFLICT); recovery = Recovery.REVIEW_CONTRADICTION; }
            if (!contradictions.isEmpty()) { reasons.add(Reason.DETERMINISTIC_CONTRADICTION); recovery = Recovery.REVIEW_CONTRADICTION; }
            if (unchecked.isEmpty() && composite.category() == CompositeEvidenceAssessment.Category.EXACT_SUPPORT
                && !secondaryConflict && !conflict && !sourceInvalid) {
                if (releaseAccepted) { status = Status.VERIFIED; scope = source.calculus() == null ? Scope.FINAL_ANSWER
                    : Scope.forCalculus(composite.required().getFirst()); reasons.add(Reason.REQUIRED_CLAIM_PROVEN); recovery = Recovery.NONE; }
                else { reasons.add(Reason.RELEASE_DISABLED); uncertainty.add(Uncertainty.RELEASE_NOT_ACCEPTED); recovery = Recovery.RELEASE_BLOCKED; }
            } else if (!unchecked.isEmpty() && contradictions.isEmpty() && !conflict && !secondaryConflict && !sourceInvalid
                && checked.stream().anyMatch(m -> m != VerificationMethod.DOMAIN_CONSTRAINT_CHECK)) {
                status = Status.PARTIALLY_VERIFIED; scope = Scope.CHECKED_METHOD_CLAIMS; reasons.add(Reason.SUBSET_PROVEN);
            }
        }
        if (status == Status.UNVERIFIED && !reasons.contains(Reason.RELEASE_DISABLED)) {
            reasons.add(Reason.MISSING_PROOF); uncertainty.add(Uncertainty.MISSING_MATHEMATICAL_PROOF);
        }
        UUID id = UUID.nameUUIDFromBytes((VerificationDecision.POLICY + "/" + run.source().userId() + "/"
            + run.source().sessionId() + "/" + key).getBytes(StandardCharsets.UTF_8));
        return new VerificationDecision(VerificationDecision.SCHEMA, id, key, VerificationDecision.POLICY,
            run.source(), run.solutionId(), run.solutionVersion(), run.id(), run.requestKey(), run.planId(), run.capabilityVersion(),
            run.evidence().stream().map(VerificationEvidence::id).sorted().toList(), secondary, status, scope, List.copyOf(reasons),
            checked, unchecked, contradictions, List.copyOf(uncertainty), recovery, run.correlationId(), now);
    }
    private static MathematicalExecution.Reason failureReason(VerificationEvidence evidence) {
        if (evidence.result() != null) return evidence.result().reasonCode();
        return switch (evidence.calculus().result().reasonCode()) {
            case TIMEOUT -> MathematicalExecution.Reason.TIMEOUT;
            case RESOURCE_FAILURE -> MathematicalExecution.Reason.RESOURCE_FAILURE;
            case WORKER_FAILURE -> MathematicalExecution.Reason.WORKER_FAILURE;
            case ADMISSION_LIMIT -> MathematicalExecution.Reason.ADMISSION_LIMIT;
            default -> MathematicalExecution.Reason.INVALID_RESPONSE;
        };
    }
}
