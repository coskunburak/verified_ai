package com.verifiedai.featureflags.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

final class Sprint107FeatureFlagMigrationTest {

    private static final String HASH_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Test
    void upgradeV070ToV071EnforcesConstraintsSeedsAndCascadeDeletion() throws Exception {
        try (var db = new PostgreSQLContainer<>("postgres:16-alpine")) {
            db.start();

            Flyway.configure()
                .dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                .locations("classpath:db/migration/platform")
                .target("70")
                .load()
                .migrate();

            Flyway.configure()
                .dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                .locations("classpath:db/migration/platform")
                .target("71")
                .load()
                .migrate();

            try (var conn = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
                 var st = conn.createStatement()) {

                var flagCountRs = st.executeQuery("SELECT count(*) FROM feature_flag_definitions");
                flagCountRs.next();
                assertThat(flagCountRs.getInt(1)).isEqualTo(11);

                var expCountRs = st.executeQuery("SELECT count(*) FROM experiment_definitions");
                expCountRs.next();
                assertThat(expCountRs.getInt(1)).isEqualTo(4);

                var riskyDefaultsRs = st.executeQuery("""
                    SELECT flag_key, enabled, kill_switch_active, training_eligible
                    FROM feature_flag_definitions
                    WHERE flag_key IN (
                        'secondary_solver_enabled',
                        'advanced_verification_enabled',
                        'tutor_v2_enabled',
                        'adaptive_plan_v2_enabled',
                        'client_platform_health_screen',
                        'client_mistake_moment_paywall_ui'
                    )
                    """);
                int riskyRows = 0;
                while (riskyDefaultsRs.next()) {
                    riskyRows++;
                    assertThat(riskyDefaultsRs.getBoolean("enabled")).isFalse();
                    assertThat(riskyDefaultsRs.getBoolean("kill_switch_active")).isFalse();
                    assertThat(riskyDefaultsRs.getBoolean("training_eligible")).isFalse();
                }
                assertThat(riskyRows).isEqualTo(6);

                var expDefaultsRs = st.executeQuery("SELECT experiment_key, status, training_eligible FROM experiment_definitions");
                int expRows = 0;
                while (expDefaultsRs.next()) {
                    expRows++;
                    assertThat(expDefaultsRs.getString("status")).isEqualTo("DRAFT");
                    assertThat(expDefaultsRs.getBoolean("training_eligible")).isFalse();
                }
                assertThat(expRows).isEqualTo(4);

                // Seed a user for FK checks & ON DELETE CASCADE
                UUID userId = UUID.fromString("00000000-0000-0000-0000-000000001071");
                st.executeUpdate(
                    "INSERT INTO users (id, status, created_at, updated_at) VALUES ('" + userId + "', 'ACTIVE', NOW(), NOW()) ON CONFLICT (id) DO NOTHING"
                );

                // Rollout bounds (> 10000) rejected on flags & experiments
                assertThatThrownBy(() -> st.executeUpdate("""
                    INSERT INTO feature_flag_definitions (
                        flag_key, category, value_type, rollout_basis_points, default_value_json, current_value_json, owner, purpose
                    ) VALUES (
                        'bad_rollout_flag', 'RELEASE_FLAG', 'BOOLEAN', 10001, 'false'::jsonb, 'false'::jsonb, 'qa', 'Invalid rollout test'
                    )
                    """)).isInstanceOf(SQLException.class);

                assertThatThrownBy(() -> st.executeUpdate("""
                    INSERT INTO experiment_definitions (
                        experiment_key, status, rollout_basis_points, control_variant, variants_json, owner, purpose
                    ) VALUES (
                        'EXP-BAD-01', 'DRAFT', 10001, 'control',
                        '[{"key":"control","weightBasisPoints":5000},{"key":"treatment","weightBasisPoints":5000}]'::jsonb,
                        'qa', 'Invalid rollout experiment'
                    )
                    """)).isInstanceOf(SQLException.class);

                // training_eligible = TRUE rejected across all four tables
                assertThatThrownBy(() -> st.executeUpdate("""
                    INSERT INTO feature_flag_definitions (
                        flag_key, category, value_type, rollout_basis_points, default_value_json, current_value_json, owner, purpose, training_eligible
                    ) VALUES (
                        'bad_train_flag', 'RELEASE_FLAG', 'BOOLEAN', 0, 'false'::jsonb, 'false'::jsonb, 'qa', 'Invalid training eligible', TRUE
                    )
                    """)).isInstanceOf(SQLException.class);

                assertThatThrownBy(() -> st.executeUpdate("""
                    INSERT INTO experiment_definitions (
                        experiment_key, status, rollout_basis_points, control_variant, variants_json, owner, purpose, training_eligible
                    ) VALUES (
                        'EXP-BAD-02', 'DRAFT', 0, 'control',
                        '[{"key":"control","weightBasisPoints":5000},{"key":"treatment","weightBasisPoints":5000}]'::jsonb,
                        'qa', 'Invalid training eligible', TRUE
                    )
                    """)).isInstanceOf(SQLException.class);

                assertThatThrownBy(() -> st.executeUpdate(
                    "INSERT INTO experiment_assignments "
                        + "(user_id, experiment_key, experiment_version, variant_key, bucket_value, assignment_reason, training_eligible) "
                        + "VALUES ('" + userId + "', 'EXP-LOS-01', 1, 'control', 100, 'BUCKET_MATCH', TRUE)"
                )).isInstanceOf(SQLException.class);

                assertThatThrownBy(() -> st.executeUpdate(
                    "INSERT INTO feature_flag_audit_logs "
                        + "(target_type, target_key, action, actor_user_id, actor_role, idempotency_key, payload_hash, reason, before_state_json, after_state_json, correlation_id, training_eligible) "
                        + "VALUES ('FLAG', 'pdf_upload_enabled', 'UPDATED', '" + userId + "', 'ROLE_ADMIN', 'idem-train-check-1', '" + HASH_A + "', 'Invalid training audit row', '{}'::jsonb, '{}'::jsonb, 'corr-1', TRUE)"
                )).isInstanceOf(SQLException.class);

                // Unique constraints on experiment_assignments and feature_flag_audit_logs
                st.executeUpdate(
                    "INSERT INTO experiment_assignments "
                        + "(user_id, experiment_key, experiment_version, variant_key, bucket_value, assignment_reason) "
                        + "VALUES ('" + userId + "', 'EXP-LOS-01', 1, 'treatment', 420, 'BUCKET_MATCH')"
                );
                assertThatThrownBy(() -> st.executeUpdate(
                    "INSERT INTO experiment_assignments "
                        + "(user_id, experiment_key, experiment_version, variant_key, bucket_value, assignment_reason) "
                        + "VALUES ('" + userId + "', 'EXP-LOS-01', 1, 'control', 420, 'BUCKET_MATCH')"
                )).isInstanceOf(SQLException.class);

                st.executeUpdate(
                    "INSERT INTO feature_flag_audit_logs "
                        + "(target_type, target_key, action, actor_user_id, actor_role, idempotency_key, payload_hash, reason, before_state_json, after_state_json, correlation_id) "
                        + "VALUES ('FLAG', 'pdf_upload_enabled', 'UPDATED', '" + userId + "', 'ROLE_ADMIN', 'idem-unique-check-1', '" + HASH_A + "', 'Valid audit baseline entry', '{\"enabled\":true}'::jsonb, '{\"enabled\":true}'::jsonb, 'corr-1')"
                );
                assertThatThrownBy(() -> st.executeUpdate(
                    "INSERT INTO feature_flag_audit_logs "
                        + "(target_type, target_key, action, actor_user_id, actor_role, idempotency_key, payload_hash, reason, before_state_json, after_state_json, correlation_id) "
                        + "VALUES ('FLAG', 'pdf_upload_enabled', 'UPDATED', '" + userId + "', 'ROLE_ADMIN', 'idem-unique-check-1', '" + HASH_A + "', 'Duplicate idempotency key', '{\"enabled\":true}'::jsonb, '{\"enabled\":true}'::jsonb, 'corr-2')"
                )).isInstanceOf(SQLException.class);

                // ON DELETE CASCADE from users(id) removes user's experiment assignments
                st.executeUpdate("DELETE FROM users WHERE id = '" + userId + "'");
                var afterDeleteRs = st.executeQuery(
                    "SELECT count(*) FROM experiment_assignments WHERE user_id = '" + userId + "'"
                );
                afterDeleteRs.next();
                assertThat(afterDeleteRs.getInt(1)).isZero();

                // Verify indexes created by V071
                var idxRs = st.executeQuery("""
                    SELECT count(*) FROM pg_indexes
                    WHERE schemaname = 'public'
                      AND indexname IN (
                        'idx_feature_flag_definitions_client_safe',
                        'idx_experiment_definitions_status_client',
                        'idx_experiment_assignments_user_time',
                        'idx_experiment_assignments_exp_variant',
                        'idx_feature_flag_audit_logs_target_time',
                        'idx_feature_flag_audit_logs_occurred'
                      )
                    """);
                idxRs.next();
                assertThat(idxRs.getInt(1)).isEqualTo(6);
            }
        }
    }
}
