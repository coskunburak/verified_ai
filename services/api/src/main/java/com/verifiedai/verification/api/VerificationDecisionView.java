package com.verifiedai.verification.api;

import com.verifiedai.verification.domain.model.VerificationDecision;
import com.verifiedai.verification.domain.model.VerificationDecision.*;
import com.verifiedai.verification.domain.model.VerificationMethod;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Public allowlist: no raw problem, answer, secondary payload or model reasoning. */
record VerificationDecisionView(String schemaVersion, UUID id, String policyVersion, UUID solutionId,
    UUID canonicalProblemId, UUID problemParseId, int problemParseRevision, UUID runId,
    List<UUID> evidenceIds, Status status, Scope scope, List<Reason> reasons,
    List<VerificationMethod> checked, List<VerificationMethod> unchecked, List<VerificationMethod> contradictions,
    List<Uncertainty> uncertainty, Recovery recovery, String correlationId, Instant createdAt) {
    static VerificationDecisionView of(VerificationDecision d) {
        return new VerificationDecisionView(d.schemaVersion(), d.id(), d.policyVersion(), d.solutionId(),
            d.source().canonicalProblemId(), d.source().parseId(), d.source().parseRevision(), d.runId(), d.evidenceIds(),
            d.status(), d.scope(), d.reasons(), d.checked(), d.unchecked(), d.contradictions(), d.uncertainty(),
            d.recovery(), d.correlationId(), d.createdAt());
    }
}
