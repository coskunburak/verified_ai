package com.verifiedai.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.verifiedai.integration.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

final class FlywayMigrationTest extends PostgresIntegrationTestSupport {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void platformMigrationCreatesFoundationMarker() {
        String marker = jdbcTemplate.queryForObject(
            "select marker_value from platform_foundation_marker where marker_key = ?",
            String.class,
            "phase"
        );

        assertThat(marker).isEqualTo("phase-2-platform-foundation");
    }
}

