package com.verifiedai.verification.domain.model;

import java.util.List;
import java.util.Objects;

/** A bounded evidence assessment, never a product correctness decision or durable aggregate. */
public record CompositeEvidenceAssessment(String version, VerificationRun run, Category category,
    DomainCoverage domainCoverage, boolean exactProofPresent, boolean exactContradictionPresent,
    List<VerificationMethod> required, List<VerificationMethod> available, List<VerificationMethod> missing,
    List<VerificationMethod> unresolved, List<VerificationMethod> unsupported, List<VerificationMethod> failed,
    List<Reason> reasons, NumericDiagnostic.Result diagnostic) {
    public static final String VERSION = "composite-evidence-v1";
    public static final String CALCULUS_VERSION = "composite-calculus-evidence-v1";
    public enum Category { EXACT_SUPPORT, EXACT_CONTRADICTION, INCOMPLETE_EVIDENCE, CONFLICTING_EVIDENCE, UNSUPPORTED_SCOPE, EXECUTION_UNAVAILABLE, BLOCKED_SCOPE }
    public enum DomainCoverage { NOT_REQUIRED, ESTABLISHED, UNRESOLVED, VIOLATED, UNSATISFIABLE }
    public enum Reason {
        CONTRADICTORY_SOURCE_SCOPE, EXACT_CONTRADICTION_PRESENT, EXACT_EVIDENCE_CONFLICT, NUMERIC_CONFLICT,
        DOMAIN_UNRESOLVED, COMPLETENESS_UNRESOLVED, MISSING_METHOD, UNRESOLVED_METHOD,
        UNSUPPORTED_METHOD, FAILED_METHOD, NO_EXECUTABLE_METHOD, REQUIRED_EXACT_CHECKS_PROVEN
    }
    public CompositeEvidenceAssessment {
        if (!VERSION.equals(version) && !CALCULUS_VERSION.equals(version)) throw new IllegalArgumentException("Unsupported assessment version");
        Objects.requireNonNull(run); Objects.requireNonNull(category); Objects.requireNonNull(domainCoverage);
        required = List.copyOf(required); available = List.copyOf(available); missing = List.copyOf(missing);
        unresolved = List.copyOf(unresolved); unsupported = List.copyOf(unsupported); failed = List.copyOf(failed);
        reasons = List.copyOf(reasons);
        for (var methods : List.of(required, available, missing, unresolved, unsupported, failed))
            if (methods.size() > 5 || methods.stream().distinct().count() != methods.size())
                throw new IllegalArgumentException("Invalid assessment method count");
        if (required.isEmpty() || !required.containsAll(available) || !required.containsAll(missing)
            || !required.containsAll(unresolved) || !unresolved.containsAll(unsupported) || !unresolved.containsAll(failed))
            throw new IllegalArgumentException("Invalid assessment obligations");
        for (var item : run.evidence())
            if (CALCULUS_VERSION.equals(version) != (item.calculus() != null) || !required.contains(item.method()))
                throw new IllegalArgumentException("Unplanned assessment evidence");
        if (CALCULUS_VERSION.equals(version) && (required.size() != 1 || diagnostic != null))
            throw new IllegalArgumentException("Invalid calculus assessment scope");
        if (exactProofPresent != run.evidence().stream().anyMatch(e -> e.outcome() == MathematicalExecution.Outcome.PROVEN)
            || exactContradictionPresent != run.evidence().stream().anyMatch(e -> e.outcome() == MathematicalExecution.Outcome.DISPROVEN))
            throw new IllegalArgumentException("Invalid exact evidence presence");
        if (category == Category.EXACT_SUPPORT && (!exactProofPresent || exactContradictionPresent
            || !missing.isEmpty() || !unresolved.isEmpty() || run.evidence().size() != required.size()
            || !available.containsAll(required)
            || run.evidence().stream().anyMatch(e -> e.outcome() != MathematicalExecution.Outcome.PROVEN)
            || domainCoverage != DomainCoverage.ESTABLISHED && domainCoverage != DomainCoverage.NOT_REQUIRED
            || diagnostic != null && diagnostic.status() == NumericDiagnostic.Status.MISMATCH))
            throw new IllegalArgumentException("Incomplete evidence cannot claim exact support");
        if (category == Category.EXACT_CONTRADICTION && !exactContradictionPresent
            || category == Category.BLOCKED_SCOPE && domainCoverage != DomainCoverage.UNSATISFIABLE)
            throw new IllegalArgumentException("Assessment category lacks evidence");
        if (diagnostic != null && (!diagnostic.executionId().equals(run.id()) || !diagnostic.correlationId().equals(run.correlationId())))
            throw new IllegalArgumentException("Unbound assessment diagnostic");
        if (reasons.isEmpty() || reasons.size() > Reason.values().length || reasons.stream().distinct().count() != reasons.size())
            throw new IllegalArgumentException("Invalid assessment reasons");
    }
}
