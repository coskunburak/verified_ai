package com.verifiedai.verification.domain.model;

import java.util.List;

/** Catalog identities are requirements, not promises of an installed executor. */
public enum VerificationMethod {
    ARITHMETIC_EVALUATION(Purpose.FINAL_VALUE),
    SYMBOLIC_EQUIVALENCE(Purpose.EQUIVALENCE),
    EQUATION_SUBSTITUTION(Purpose.PROPOSED_ROOT_SOUNDNESS),
    SOLUTION_SET_COMPLETENESS(Purpose.ALL_ROOTS),
    DOMAIN_CONSTRAINT_CHECK(Purpose.DOMAIN_PRESERVATION),
    NUMERIC_SAMPLING(Purpose.SUPPORTING_EVIDENCE_ONLY),
    DERIVATIVE_BACKCHECK(Purpose.DERIVATIVE),
    INTEGRAL_DIFFERENTIATION_BACKCHECK(Purpose.ANTIDERIVATIVE);

    public enum Purpose { FINAL_VALUE, EQUIVALENCE, PROPOSED_ROOT_SOUNDNESS, ALL_ROOTS,
        DOMAIN_PRESERVATION, SUPPORTING_EVIDENCE_ONLY, DERIVATIVE, ANTIDERIVATIVE }
    public enum Input { ORIGINAL_STATEMENT, FINAL_STATEMENT, ORDERED_STEPS, SOURCE_DOMAINS,
        SOURCE_RESTRICTIONS, COMPLETE_PROPOSED_SOLUTION_SET }
    private final Purpose purpose;
    VerificationMethod(Purpose purpose) { this.purpose = purpose; }
    public String version() { return "v1"; }
    public Purpose purpose() { return purpose; }
    public List<Input> requiredInputs() {
        return this == SOLUTION_SET_COMPLETENESS
            ? List.of(Input.ORIGINAL_STATEMENT, Input.COMPLETE_PROPOSED_SOLUTION_SET,
                Input.SOURCE_DOMAINS, Input.SOURCE_RESTRICTIONS)
            : List.of(Input.ORIGINAL_STATEMENT, Input.FINAL_STATEMENT, Input.ORDERED_STEPS,
                Input.SOURCE_DOMAINS, Input.SOURCE_RESTRICTIONS);
    }
}
