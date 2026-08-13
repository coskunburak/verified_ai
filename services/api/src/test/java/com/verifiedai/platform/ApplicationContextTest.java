package com.verifiedai.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.verifiedai.integration.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

final class ApplicationContextTest extends PostgresIntegrationTestSupport {

    @Autowired
    Environment environment;

    @Test
    void contextLoadsWithPostgreSQLAndFlyway() {
        assertThat(
            environment.getProperty(
                "app.ai.usage-ledger.enabled",
                Boolean.class
            )
        ).isFalse();
    }
}
