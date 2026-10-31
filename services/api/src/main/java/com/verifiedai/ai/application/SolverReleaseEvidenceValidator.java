package com.verifiedai.ai.application;

/** Validates reviewed evidence bytes; a reference string is never release evidence. */
@FunctionalInterface
public interface SolverReleaseEvidenceValidator {
    void validate(PromptRegistryEntry prompt, OutputSchemaRegistryEntry schema, AiRoutePlan route);

    static SolverReleaseEvidenceValidator unavailable() {
        return (prompt, schema, route) -> {
            throw new PromptRegistryValidationException(
                PromptRegistryFailureCode.AI_SOLVER_RELEASE_EVIDENCE_INVALID,
                "Solver release blocked: reviewed evidence resolver is unavailable");
        };
    }
}
