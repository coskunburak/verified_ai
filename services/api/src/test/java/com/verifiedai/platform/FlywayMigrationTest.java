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

    @Test
    void problemParseLifecycleMigrationCreatesCanonicalTablesConstraintsAndIndexes() {
        assertThat(tableExists("problem_parse_jobs")).isTrue();
        assertThat(tableExists("problem_parses")).isTrue();
        assertThat(constraintExists("uq_recognition_evidence_id_user_session_revision")).isTrue();
        assertThat(constraintExists("ck_problem_parse_jobs_status")).isTrue();
        assertThat(constraintExists("ck_problem_parse_jobs_capability")).isTrue();
        assertThat(constraintExists("uq_problem_parse_jobs_logical_input")).isTrue();
        assertThat(constraintExists("ck_problem_parses_json_objects")).isTrue();
        assertThat(constraintExists("ck_problem_parses_support_status")).isTrue();
        assertThat(constraintExists("uq_problem_parses_session_revision")).isTrue();
        assertThat(indexExists("ix_problem_parse_jobs_due")).isTrue();
        assertThat(indexExists("ix_problem_parses_evidence")).isTrue();
    }

    @Test
    void canonicalProblemLifecycleMigrationCreatesSafeVerifierTablesConstraintsAndIndexes() {
        assertThat(tableExists("canonical_problems")).isTrue();
        assertThat(constraintExists("fk_canonical_problems_session_user")).isTrue();
        assertThat(constraintExists("fk_canonical_problems_parse")).isTrue();
        assertThat(constraintExists("ck_canonical_problems_schema_versions")).isTrue();
        assertThat(constraintExists("ck_canonical_problems_problem_type")).isTrue();
        assertThat(constraintExists("ck_canonical_problems_json_objects")).isTrue();
        assertThat(constraintExists("uq_canonical_problems_parse_schema")).isTrue();
        assertThat(indexExists("ix_canonical_problems_session_revision")).isTrue();
        assertThat(indexExists("ix_canonical_problems_parse")).isTrue();
    }

    @Test
    void emailAndGuestIdentityMigrationCreatesCredentialTableAndProviderConstraint() {
        assertThat(tableExists("user_password_credentials")).isTrue();
        assertThat(constraintExists("uq_user_password_credentials_email")).isTrue();
        assertThat(constraintExists("ck_user_password_credentials_algorithm")).isTrue();
        assertThat(constraintExists("ck_user_identities_provider")).isTrue();
        assertThat(indexExists("ix_user_password_credentials_email")).isTrue();
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
