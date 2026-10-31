package com.verifiedai.verification.domain.policy;

import com.verifiedai.solving.domain.model.canonical.CanonicalSolution;
import com.verifiedai.solving.domain.model.canonical.MathStatement;
import com.verifiedai.verification.domain.model.*;
import com.verifiedai.verification.domain.model.CompositeEvidenceAssessment.*;
import com.verifiedai.verification.domain.model.MathematicalExecution.Outcome;
import java.util.*;

/** Composes the single source-scoped calculus obligation without broadening its certificate. */
public final class CalculusCompositePolicy {
    private final VerificationPlanner planner;
    public CalculusCompositePolicy(VerificationPlanner planner) { this.planner = Objects.requireNonNull(planner); }

    public CompositeEvidenceAssessment assess(PlanningSource source, CanonicalSolution solution,
        VerificationPlan plan, VerificationRun run) {
        if (source.calculus() == null || !CanonicalSolution.Version.CALCULUS.equals(solution.version())
            || !VerificationPlanner.CALCULUS_POLICY.equals(plan.policyVersion()) || plan.methods().size() != 1
            || !plan.equals(planner.plan(source, solution).plan()) || !run.source().equals(source.identity())
            || !run.planId().equals(plan.id()) || !run.solutionId().equals(solution.id())
            || !run.solutionVersion().equals(solution.version()) || !run.policyVersion().equals(plan.policyVersion())
            || !run.capabilityVersion().equals(plan.capabilityVersion()) || run.evidence().size() != 1)
            throw new IllegalArgumentException("Stale or incompatible calculus composite lineage");
        var selection = plan.methods().getFirst();
        var method = selection.method();
        var item = run.evidence().getFirst();
        if (item.calculus() == null || item.method() != method || !item.domainAssumptions().equals(source.variables()))
            throw new IllegalArgumentException("Incompatible calculus evidence variant");
        var metadata = source.calculus();
        var request = new CalculusExecution.Request(run.id(), run.correlationId(),
            CalculusEvidence.scope(source.identity(), solution.id()), CalculusExecution.Method.valueOf(method.name()),
            source.variables().keySet().iterator().next(), PlanningSource.Domain.REAL, metadata.interval(),
            ((MathStatement.Expression) source.statements().getFirst()).expression(),
            ((MathStatement.Expression) solution.finalStatement()).expression(), source.restrictions(),
            metadata.approach(), metadata.integralScope());
        // Compare full typed metadata, not just IDs: interval, direction, target and restriction ASTs matter.
        var result = item.calculus().result();
        if (!CalculusEvidence.of(request, result).equals(item.calculus()))
            throw new IllegalArgumentException("Calculus evidence does not cover the selected source scope");
        boolean available = selection.availability() == VerificationPlan.Availability.AVAILABLE;
        var outcome = result.outcome();
        if (!available && (outcome == Outcome.PROVEN || outcome == Outcome.DISPROVEN || outcome == Outcome.INCONCLUSIVE))
            throw new IllegalArgumentException("Calculus evidence from an unavailable capability");
        boolean proven = outcome == Outcome.PROVEN, disproven = outcome == Outcome.DISPROVEN;
        boolean exact = proven || disproven;
        // bounded-calculus-v1 issues exact certificates only after checking all original domain
        // obligations on the open interval or the specified punctured approach neighborhood.
        // Its integral certificate proves one representative, never a complete family or definite integral.
        var domain = exact ? DomainCoverage.ESTABLISHED
            : result.reasonCode() == CalculusExecution.Reason.DOMAIN_OBSTRUCTION ? DomainCoverage.VIOLATED
            : DomainCoverage.UNRESOLVED;
        var reasons = EnumSet.noneOf(Reason.class);
        if (proven) reasons.add(Reason.REQUIRED_EXACT_CHECKS_PROVEN);
        if (disproven) reasons.add(Reason.EXACT_CONTRADICTION_PRESENT);
        if (!exact) { reasons.add(Reason.UNRESOLVED_METHOD); reasons.add(Reason.DOMAIN_UNRESOLVED); }
        if (outcome == Outcome.UNSUPPORTED) reasons.add(Reason.UNSUPPORTED_METHOD);
        if (outcome == Outcome.EXECUTION_FAILED) reasons.add(Reason.FAILED_METHOD);
        if (!available) reasons.add(Reason.NO_EXECUTABLE_METHOD);
        var category = proven ? Category.EXACT_SUPPORT : disproven ? Category.EXACT_CONTRADICTION
            : !available || outcome == Outcome.EXECUTION_FAILED ? Category.EXECUTION_UNAVAILABLE
            : outcome == Outcome.UNSUPPORTED ? Category.UNSUPPORTED_SCOPE : Category.INCOMPLETE_EVIDENCE;
        var required = List.of(method);
        return new CompositeEvidenceAssessment(CompositeEvidenceAssessment.CALCULUS_VERSION, run, category, domain,
            proven, disproven, required, available ? required : List.of(), List.of(), exact ? List.of() : required,
            outcome == Outcome.UNSUPPORTED ? required : List.of(), outcome == Outcome.EXECUTION_FAILED ? required : List.of(),
            List.copyOf(reasons), null);
    }
}
