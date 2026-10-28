package com.verifiedai.verification.domain;

import com.verifiedai.verification.domain.model.*;
import com.verifiedai.verification.domain.model.VerificationPlan.*;
import com.verifiedai.verification.domain.policy.VerificationCapabilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class VerificationCapabilitiesTest {
    @Test void existingStringEquivalenceIsBlockedWithoutApprovedCanonicalAdapter() {
        var registry = new VerificationCapabilities();
        assertThat(registry.availability(VerificationMethod.SYMBOLIC_EQUIVALENCE)).isEqualTo(Availability.BLOCKED_BY_PRECONDITION);
        assertThat(registry.reason(VerificationMethod.SYMBOLIC_EQUIVALENCE)).isEqualTo(Reason.CANONICAL_EXECUTION_ADAPTER_UNAVAILABLE);
        assertThat(registry.availableMethods()).isEmpty();
    }
    @Test void futureMethodsAndEmptyInventoryNeverAdvertiseExecution() {
        for (var method : VerificationMethod.values()) {
            assertThat(new VerificationCapabilities().availability(method)).isNotEqualTo(Availability.AVAILABLE);
            assertThat(VerificationCapabilities.none().availability(method)).isEqualTo(Availability.PLANNED_NOT_EXECUTABLE);
        }
        assertThat(VerificationMethod.NUMERIC_SAMPLING.purpose()).isEqualTo(VerificationMethod.Purpose.SUPPORTING_EVIDENCE_ONLY);
        assertThat(VerificationMethod.SOLUTION_SET_COMPLETENESS.requiredInputs()).contains(VerificationMethod.Input.COMPLETE_PROPOSED_SOLUTION_SET);
    }
}
