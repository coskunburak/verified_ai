package com.verifiedai.architecture;

import com.verifiedai.VerifiedAiApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

final class ModularityTest {

    @Test
    void verifiesSpringModulithBoundaries() {
        ApplicationModules.of(VerifiedAiApplication.class).verify();
    }
}

