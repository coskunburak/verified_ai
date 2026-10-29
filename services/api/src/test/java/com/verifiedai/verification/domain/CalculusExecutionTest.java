package com.verifiedai.verification.domain;

import com.verifiedai.verification.domain.model.CalculusExecution.*;
import com.verifiedai.verification.domain.model.MathematicalExecution.ExactValue;
import com.verifiedai.verification.domain.model.MathematicalExecution.Outcome;
import com.verifiedai.verification.domain.policy.VerificationCapabilities;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CalculusExecutionTest {
    @Test void canonicalPlannerCannotActivateInternalCalculusContracts() {
        assertThat(new VerificationCapabilities().availableMethods()).isEmpty();
        for (var method : List.of("DERIVATIVE_BACKCHECK", "INTEGRAL_DIFFERENTIATION_BACKCHECK", "NUMERIC_SAMPLING", "FINITE_LIMIT"))
            assertThatIllegalArgumentException().isThrownBy(() -> new VerificationCapabilities(true, true, method));
    }
    @Test void intervalIsOpenConnectedNonemptyAndExact() {
        var zero = new ExactValue("0", "1");
        assertThatIllegalArgumentException().isThrownBy(() -> new Interval(zero, zero));
        assertThatIllegalArgumentException().isThrownBy(() -> new Interval(new ExactValue("2", "1"), zero));
        assertThat(new Interval(null, zero).upper()).isEqualTo(zero);
        assertThatIllegalArgumentException().isThrownBy(() -> new ExactValue("2", "2"));
    }
    @Test void timeoutIsNotDisproofAndLimitWitnessCannotContradictOutcome() {
        var scope = new Scope(UUID.randomUUID(), 1, 1, "calculus-task-v1", "calculus-task-v1");
        var id = UUID.randomUUID();
        assertThatIllegalArgumentException().isThrownBy(() -> new Result(id, "test", scope, Method.FINITE_LIMIT,
            "v1", "bounded-calculus-v1", Outcome.DISPROVEN, Reason.TIMEOUT, List.of(), null, null));
        var proof = new Certificate(Descriptor.RATIONAL_FINITE_LIMIT, ProofScope.PUNCTURED_NEIGHBORHOOD,
            new ExactValue("2", "1"), new ExactValue("3", "1"));
        assertThatIllegalArgumentException().isThrownBy(() -> new Result(id, "test", scope, Method.FINITE_LIMIT,
            "v1", "bounded-calculus-v1", Outcome.PROVEN, Reason.EXACT_FINITE_LIMIT, List.of(), proof, null));
    }
    @Test void diagnosticsHaveFinitePolicyAndCannotReportMoreAgreementsThanSamples() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Diagnostic("calculus-binary64-v1", 16, 2, 3));
        assertThatIllegalArgumentException().isThrownBy(() -> new Diagnostic("calculus-binary64-v1", 17, 17, 17));
        assertThatIllegalArgumentException().isThrownBy(() -> new Diagnostic("unbounded", 16, 16, 16));
    }
}
