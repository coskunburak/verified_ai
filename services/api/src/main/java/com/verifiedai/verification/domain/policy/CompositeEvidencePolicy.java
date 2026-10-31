package com.verifiedai.verification.domain.policy;

import com.verifiedai.solving.domain.model.canonical.CanonicalSolution;
import com.verifiedai.verification.domain.model.*;
import com.verifiedai.verification.domain.model.CompositeEvidenceAssessment.*;
import com.verifiedai.verification.domain.model.MathematicalExecution.Outcome;
import java.util.*;

/** Pure composition of validated, single-run evidence against current authorized snapshots. */
public final class CompositeEvidencePolicy {
    private final VerificationPlanner planner;
    public CompositeEvidencePolicy(VerificationPlanner planner) { this.planner = Objects.requireNonNull(planner); }

    public CompositeEvidenceAssessment assess(PlanningSource source, CanonicalSolution solution,
        VerificationPlan plan, VerificationRun run, NumericDiagnostic.Evidence numeric) {
        Objects.requireNonNull(source); Objects.requireNonNull(solution); Objects.requireNonNull(plan); Objects.requireNonNull(run);
        if (source.calculus() != null || CanonicalSolution.Version.CALCULUS.equals(solution.version())) {
            if (numeric != null) throw new IllegalArgumentException("Generic diagnostics cannot bind a calculus claim");
            return new CalculusCompositePolicy(planner).assess(source, solution, plan, run);
        }
        if (!CanonicalSolution.Version.CURRENT.equals(solution.version()))
            throw new IllegalArgumentException("Unsupported composite representation");
        if (!plan.equals(planner.plan(source, solution).plan()) || !run.source().equals(source.identity())
            || !run.planId().equals(plan.id()) || !run.solutionId().equals(solution.id())
            || !run.solutionVersion().equals(solution.version()) || !run.policyVersion().equals(plan.policyVersion())
            || !run.capabilityVersion().equals(plan.capabilityVersion()))
            throw new IllegalArgumentException("Stale or incompatible composite lineage");
        var required = plan.methods().stream().map(VerificationPlan.Selection::method).toList();
        var available = plan.methods().stream().filter(s -> s.availability() == VerificationPlan.Availability.AVAILABLE)
            .map(VerificationPlan.Selection::method).toList();
        var evidence = new EnumMap<VerificationMethod, MathematicalExecution.Result>(VerificationMethod.class);
        for (var item : run.evidence()) {
            if (item.calculus() != null || !required.contains(item.method()) || !item.domainAssumptions().equals(source.variables())
                || evidence.putIfAbsent(item.method(), item.result()) != null)
                throw new IllegalArgumentException("Incompatible evidence claim or scope");
            item.result().validateFor(new MathematicalExecution.Request(run.id(), run.correlationId(), item.method(),
                source.statements().getFirst(), solution.finalStatement(), source.variables(), source.restrictions()));
            if (!available.contains(item.method()) && item.result().outcome() != Outcome.BLOCKED
                && item.result().outcome() != Outcome.UNSUPPORTED && item.result().outcome() != Outcome.EXECUTION_FAILED)
                throw new IllegalArgumentException("Evidence from an unavailable capability");
        }
        NumericDiagnostic.Result diagnostic = null;
        if (numeric != null) {
            var request = numeric.request();
            var expected = new MathematicalExecution.Request(run.id(), run.correlationId(), VerificationMethod.NUMERIC_SAMPLING,
                source.statements().getFirst(), solution.finalStatement(), source.variables(), source.restrictions());
            if (!request.scopeId().equals(NumericDiagnostic.scope(plan, source)) || !request.input().equals(expected))
                throw new IllegalArgumentException("Incompatible diagnostic claim or scope");
            diagnostic = numeric.result();
        }
        var missing = new ArrayList<VerificationMethod>();
        var unresolved = new ArrayList<VerificationMethod>();
        var unsupported = new ArrayList<VerificationMethod>();
        var failed = new ArrayList<VerificationMethod>();
        boolean proof = false, contradiction = false, unsatisfiable = false;
        for (var method : required) {
            var result = evidence.get(method);
            if (result == null) { missing.add(method); continue; }
            proof |= result.outcome() == Outcome.PROVEN;
            contradiction |= result.outcome() == Outcome.DISPROVEN;
            unsatisfiable |= result.reasonCode() == MathematicalExecution.Reason.CONTRADICTORY_CONSTRAINTS;
            if (result.outcome() != Outcome.PROVEN && result.outcome() != Outcome.DISPROVEN) unresolved.add(method);
            if (result.outcome() == Outcome.UNSUPPORTED) unsupported.add(method);
            if (result.outcome() == Outcome.EXECUTION_FAILED) failed.add(method);
        }
        var feasibility = CompositeSourceScope.inspect(source);
        unsatisfiable |= feasibility == CompositeSourceScope.State.EMPTY;
        var domainResult = evidence.get(VerificationMethod.DOMAIN_CONSTRAINT_CHECK);
        var domain = unsatisfiable ? DomainCoverage.UNSATISFIABLE
            : feasibility == CompositeSourceScope.State.UNKNOWN ? DomainCoverage.UNRESOLVED
            : !required.contains(VerificationMethod.DOMAIN_CONSTRAINT_CHECK) ? DomainCoverage.NOT_REQUIRED
            : domainResult == null ? DomainCoverage.UNRESOLVED
            : domainResult.outcome() == Outcome.PROVEN && feasibility == CompositeSourceScope.State.NONEMPTY
                ? DomainCoverage.ESTABLISHED
            : domainResult.outcome() == Outcome.DISPROVEN ? DomainCoverage.VIOLATED : DomainCoverage.UNRESOLVED;
        boolean domainProven = domainResult != null && domainResult.outcome() == Outcome.PROVEN;
        boolean domainDisproven = evidence.values().stream().anyMatch(r -> r.outcome() == Outcome.DISPROVEN
            && r.reasonCode() == MathematicalExecution.Reason.DOMAIN_VIOLATION);
        boolean scopedClaimProven = evidence.values().stream().anyMatch(r -> r.outcome() == Outcome.PROVEN
            && r.method() != VerificationMethod.DOMAIN_CONSTRAINT_CHECK);
        var completeness = evidence.get(VerificationMethod.SOLUTION_SET_COMPLETENESS);
        var substitution = evidence.get(VerificationMethod.EQUATION_SUBSTITUTION);
        boolean exactConflict = domainDisproven && (domainProven || scopedClaimProven)
            || completeness != null && completeness.outcome() == Outcome.PROVEN
                && substitution != null && substitution.outcome() == Outcome.DISPROVEN;
        boolean complete = !required.isEmpty() && missing.isEmpty() && unresolved.isEmpty()
            && !contradiction && (domain == DomainCoverage.ESTABLISHED || domain == DomainCoverage.NOT_REQUIRED);
        // A numerical conflict is operational evidence, never a competing exact certificate.
        boolean exactEquality = evidence.values().stream().anyMatch(r -> r.outcome() == Outcome.PROVEN
            && Set.of(VerificationMethod.ARITHMETIC_EVALUATION, VerificationMethod.SYMBOLIC_EQUIVALENCE).contains(r.method()));
        boolean conflict = diagnostic != null && (exactEquality && diagnostic.status() == NumericDiagnostic.Status.MISMATCH
            || contradiction && diagnostic.status() == NumericDiagnostic.Status.AGREEMENT);
        var reasons = EnumSet.noneOf(Reason.class);
        if (unsatisfiable) reasons.add(Reason.CONTRADICTORY_SOURCE_SCOPE);
        if (contradiction) reasons.add(Reason.EXACT_CONTRADICTION_PRESENT);
        if (exactConflict) reasons.add(Reason.EXACT_EVIDENCE_CONFLICT);
        if (conflict) reasons.add(Reason.NUMERIC_CONFLICT);
        if (domain == DomainCoverage.UNRESOLVED) reasons.add(Reason.DOMAIN_UNRESOLVED);
        if (missing.contains(VerificationMethod.SOLUTION_SET_COMPLETENESS) || unresolved.contains(VerificationMethod.SOLUTION_SET_COMPLETENESS))
            reasons.add(Reason.COMPLETENESS_UNRESOLVED);
        if (!missing.isEmpty()) reasons.add(Reason.MISSING_METHOD);
        if (!unresolved.isEmpty()) reasons.add(Reason.UNRESOLVED_METHOD);
        if (!unsupported.isEmpty()) reasons.add(Reason.UNSUPPORTED_METHOD);
        if (!failed.isEmpty()) reasons.add(Reason.FAILED_METHOD);
        if (available.isEmpty()) reasons.add(Reason.NO_EXECUTABLE_METHOD);
        if (complete) reasons.add(Reason.REQUIRED_EXACT_CHECKS_PROVEN);
        var category = unsatisfiable ? Category.BLOCKED_SCOPE
            : feasibility == CompositeSourceScope.State.UNKNOWN ? Category.INCOMPLETE_EVIDENCE
            : exactConflict ? Category.CONFLICTING_EVIDENCE
            : contradiction ? Category.EXACT_CONTRADICTION
            : conflict ? Category.CONFLICTING_EVIDENCE : complete ? Category.EXACT_SUPPORT
            : !failed.isEmpty() || available.isEmpty() ? Category.EXECUTION_UNAVAILABLE
            : !unsupported.isEmpty() ? Category.UNSUPPORTED_SCOPE : Category.INCOMPLETE_EVIDENCE;
        // Canonical evidence order is the plan order, independent of caller collection order.
        var orderedRun = new VerificationRun(run.id(), run.requestKey(), run.planId(), run.policyVersion(), run.capabilityVersion(),
            run.source(), run.solutionId(), run.solutionVersion(), run.correlationId(), run.createdAt(),
            required.stream().flatMap(m -> run.evidence().stream().filter(e -> e.method() == m)).toList());
        return new CompositeEvidenceAssessment(CompositeEvidenceAssessment.VERSION, orderedRun, category, domain, proof, contradiction,
            required, available, missing, unresolved, unsupported, failed, List.copyOf(reasons), diagnostic);
    }
}
