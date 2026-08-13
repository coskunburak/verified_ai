package com.verifiedai.ai.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;

final class AiCapabilityRegistryTest {

    @Test
    void containsDescriptorForEveryCapability() {
        AiCapabilityRegistry registry =
            AiCapabilityRegistry.defaults();

        assertThat(
            registry.all().keySet()
        ).containsExactlyInAnyOrderElementsOf(
            EnumSet.allOf(
                AiCapability.class
            )
        );
    }

    @Test
    void phaseFiveFutureCapabilitiesAreRegistered() {
        AiCapabilityRegistry registry =
            AiCapabilityRegistry.defaults();

        assertThat(
            registry.require(
                AiCapability.SOLVE
            )
        ).isNotNull();

        assertThat(
            registry.require(
                AiCapability.ARBITRATE
            )
        ).isNotNull();

        assertThat(
            registry.require(
                AiCapability.TUTOR
            )
        ).isNotNull();
    }
}
