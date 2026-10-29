package com.verifiedai.verification.domain;

import com.verifiedai.verification.domain.model.*;
import com.verifiedai.verification.domain.model.MathematicalExecution.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MathematicalExecutionTest {
    @Test void exactFractionsMustBeCanonicalAndBounded() {
        assertThat(new ExactValue("3", "10").denominator()).isEqualTo("10");
        assertThatThrownBy(() -> new ExactValue("2", "4")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExactValue("1", "0")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExactValue("9".repeat(320), "1")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void operationalSuccessWithoutProofCannotBecomeProven() {
        assertThatThrownBy(() -> new Result(VerificationMethod.ARITHMETIC_EVALUATION, "v1", "canonical-executor-v1",
            Outcome.PROVEN, Reason.EXACT_EQUAL, List.of(), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Result(VerificationMethod.ARITHMETIC_EVALUATION, "v1", "canonical-executor-v1",
            Outcome.EXECUTION_FAILED, Reason.TIMEOUT, List.of(),
            new Proof(Descriptor.EXACT_RATIONAL_COMPARISON, new ExactValue("1", "1"), new ExactValue("1", "1"), null)))
            .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void methodAndProofCategoryMustAgree() {
        assertThatThrownBy(() -> new Result(VerificationMethod.SOLUTION_SET_COMPLETENESS, "v1", "canonical-executor-v1",
            Outcome.PROVEN, Reason.UNIQUE_SOLUTION, List.of(),
            new Proof(Descriptor.ORIGINAL_EQUATION_SUBSTITUTION, new ExactValue("1", "1"), new ExactValue("1", "1"), null)))
            .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void duplicateRestrictionReferencesAreRejected() {
        assertThatThrownBy(() -> new Result(VerificationMethod.DOMAIN_CONSTRAINT_CHECK, "v1", "canonical-executor-v1",
            Outcome.BLOCKED, Reason.UNKNOWN_DOMAIN, List.of("r", "r"), null)).isInstanceOf(IllegalArgumentException.class);
    }
}
