package com.verifiedai.verification.domain;

import com.verifiedai.verification.domain.model.*;
import com.verifiedai.verification.domain.model.VerificationPlan.*;
import com.verifiedai.verification.domain.policy.VerificationCapabilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class VerificationCapabilitiesTest {
    @Test void installedTypedExecutorRemainsBlockedPendingReleaseApproval() {
        var registry = new VerificationCapabilities();
        assertThat(registry.availability(VerificationMethod.SYMBOLIC_EQUIVALENCE)).isEqualTo(Availability.BLOCKED_BY_PRECONDITION);
        assertThat(registry.reason(VerificationMethod.SYMBOLIC_EQUIVALENCE)).isEqualTo(Reason.CAPABILITY_RELEASE_NOT_APPROVED);
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
    @Test void activationRequiresBothOwnerAcceptanceAndFeatureFlagAndChangesVersion() {
        assertThat(new VerificationCapabilities(true, false, "ARITHMETIC_EVALUATION").availableMethods()).isEmpty();
        assertThat(new VerificationCapabilities(false, true, "ARITHMETIC_EVALUATION").availableMethods()).isEmpty();
        var active = new VerificationCapabilities(true, true, "ARITHMETIC_EVALUATION");
        assertThat(active.version()).startsWith("canonical-capabilities-v2-");
        assertThat(active.availableMethods()).containsExactly(VerificationMethod.ARITHMETIC_EVALUATION);
        assertThat(active.availability(VerificationMethod.SYMBOLIC_EQUIVALENCE)).isNotEqualTo(Availability.AVAILABLE);
        assertThatThrownBy(() -> new VerificationCapabilities(true, true, "NUMERIC_SAMPLING"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
