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

    @Test
    void problemAssetUploadLifecycleMigrationCreatesCanonicalTablesConstraintsAndIndexes() {
        assertThat(tableExists("problem_sessions")).isTrue();
        assertThat(tableExists("problem_assets")).isTrue();
        assertThat(constraintExists("ck_problem_sessions_status")).isTrue();
        assertThat(constraintExists("ck_problem_assets_checksum_algorithm")).isTrue();
        assertThat(constraintExists("ck_problem_assets_checksum_value")).isTrue();
        assertThat(constraintExists("uq_problem_assets_user_idempotency")).isTrue();
        assertThat(indexExists("ix_problem_assets_pending_expiry")).isTrue();
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from information_schema.tables where table_schema = 'public' and table_name = ?",
            Integer.class,
            tableName
        );
        return count != null && count == 1;
    }

    private boolean constraintExists(String constraintName) {
        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from information_schema.table_constraints where constraint_schema = 'public' and constraint_name = ?",
            Integer.class,
            constraintName
        );
        return count != null && count == 1;
    }

    private boolean indexExists(String indexName) {
        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from pg_indexes where schemaname = 'public' and indexname = ?",
            Integer.class,
            indexName
        );
        return count != null && count == 1;
    }
}
