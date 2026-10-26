package com.verifiedai.ai.infrastructure.configuration;

import static org.assertj.core.api.Assertions.*;
import com.verifiedai.ai.application.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AiRoutingPolicyConfigurationTest {
    @Test
    void defaultPaidAllowancesAreZeroForEveryTier() {
        var limits = new AiBudgetProperties(null, 0, 0, 0, 0, 0, 0, 0, null);
        assertThat(limits.policyVersion()).isEqualTo("paid-disabled-v1");
        assertThat(limits.tierRequestMicros()).containsEntry("FREE", 0L).containsEntry("PRO", 0L).containsEntry("PRO_PLUS", 0L);
    }
    @Test
    void malformedBudgetAndHealthPoliciesFailClosed() {
        assertThatThrownBy(() -> new AiBudgetProperties("v1", -1, 0, 0, 0, 0, 0, 0, Map.of()))
            .isInstanceOf(IllegalArgumentException.class);
        for (Duration age : List.of(Duration.ZERO, Duration.ofSeconds(-1), Duration.ofMinutes(6))) {
            assertThatThrownBy(() -> new AiRoutingProperties(age, List.of())).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test
    void duplicateExactModelsCannotReceiveConflictingApprovals() {
        var entry = new AiRouteEligibility(AiCapability.PROBLEM_CLASSIFY, new AiRouteTarget("TEST", "exact-v1"),
            "quality-v1", 10, Duration.ofSeconds(1), Set.of("UNKNOWN"), Set.of("UNKNOWN"), Set.of("FREE"),
            "EU", "ZERO_RETENTION", true);
        assertThatThrownBy(() -> new AiRoutingProperties(null, List.of(entry, entry))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test
    void retryConfigurationIsBounded() {
        assertThatThrownBy(() -> new AiRoutePlan(AiCapability.VISION_PARSE, "v1", "TEST", null,
            "vision-recognition", "v001", "recognition-evidence-v1", Duration.ofSeconds(1), 9, 100, 0, "v1"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
