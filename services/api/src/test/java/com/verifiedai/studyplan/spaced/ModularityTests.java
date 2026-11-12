package com.verifiedai.studyplan.spaced;

import com.verifiedai.VerifiedAiApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

    @Test
    @DisplayName("Spring Modulith Boundaries: verify module boundaries remain clean and acyclic")
    void verifiesSpringModulithBoundaries() {
        ApplicationModules.of(VerifiedAiApplication.class).verify();
    }
}
