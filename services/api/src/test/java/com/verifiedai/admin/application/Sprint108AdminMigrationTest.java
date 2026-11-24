package com.verifiedai.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

final class Sprint108AdminMigrationTest {

    private static final String HASH_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Test
    void upgradeV071ToV072EnforcesConstraintsAndCascadeDeletion() throws Exception {
        try (var db = new PostgreSQLContainer<>("postgres:16-alpine")) {
            db.start();

            Flyway.configure()
                .dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                .locations("classpath:db/migration/platform")
                .target("71")
                .load()
                .migrate();

            var upgradeResult = Flyway.configure()
                .dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                .locations("classpath:db/migration/platform")
                .target("72")
                .load()
                .migrate();
            assertThat(upgradeResult.migrationsExecuted).isEqualTo(1);

            var replayResult = Flyway.configure()
                .dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                .locations("classpath:db/migration/platform")
                .target("72")
                .load()
                .migrate();
            assertThat(replayResult.migrationsExecuted).isZero();

            try (var conn = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
                 var st = conn.createStatement()) {

                UUID userId = UUID.fromString("00000000-0000-0000-0000-000000001081");
                UUID otherUserId = UUID.fromString("00000000-0000-0000-0000-000000001082");
                UUID adminId = UUID.fromString("00000000-0000-0000-0000-00000000AA81");
                UUID sessionId = UUID.fromString("00000000-0000-0000-0000-000000002081");
                UUID otherSessionId = UUID.fromString("00000000-0000-0000-0000-000000002082");
                UUID auditId = UUID.fromString("00000000-0000-0000-0000-000000003081");

                st.executeUpdate(
                    "INSERT INTO users (id, status, created_at, updated_at) VALUES "
                        + "('" + userId + "', 'ACTIVE', NOW(), NOW()), "
                        + "('" + otherUserId + "', 'ACTIVE', NOW(), NOW())"
                );
                st.executeUpdate(
                    "INSERT INTO problem_sessions (id, user_id, status, input_mode, created_at, updated_at, version) VALUES "
                        + "('" + sessionId + "', '" + userId + "', 'CREATED', 'CAMERA', NOW(), NOW(), 0), "
                        + "('" + otherSessionId + "', '" + otherUserId + "', 'CREATED', 'CAMERA', NOW(), NOW(), 0)"
                );

                // Seed valid audit row
                st.executeUpdate(
                    "INSERT INTO admin_support_audit_logs "
                        + "(id, action, target_type, target_id, actor_user_id, actor_role, idempotency_key, payload_hash, reason, correlation_id) "
                        + "VALUES ('" + auditId + "', 'RAW_CONTENT_GRANT_CREATED', 'PROBLEM_SESSION', '" + sessionId + "', '"
                        + adminId + "', 'ROLE_ADMIN_ENGINEERING', 'adm-mig-audit-1', '" + HASH_A + "', 'Inspect parse mismatch', 'corr-mig-1')"
                );

                // CHECK (NOT training_eligible) across all 3 V072 tables
                assertThatThrownBy(() -> st.executeUpdate(
                    "INSERT INTO admin_support_audit_logs "
                        + "(action, target_type, target_id, actor_user_id, actor_role, reason, correlation_id, training_eligible) "
                        + "VALUES ('USER_LOOKUP_READ', 'USER', '" + userId + "', '" + adminId + "', 'ROLE_ADMIN', 'Lookup user status', 'corr-1', TRUE)"
                )).isInstanceOf(SQLException.class);

                assertThatThrownBy(() -> st.executeUpdate(
                    "INSERT INTO admin_raw_content_access_grants "
                        + "(actor_user_id, actor_role, target_user_id, problem_session_id, ticket_reference, reason, idempotency_key, payload_hash, audit_log_id, granted_at, expires_at, correlation_id, training_eligible) "
                        + "VALUES ('" + adminId + "', 'ROLE_ADMIN_ENGINEERING', '" + userId + "', '" + sessionId + "', 'SUP-108', 'Inspect parse failure ticket', 'grant-train-bad-1', '" + HASH_A + "', '" + auditId + "', NOW(), NOW() + INTERVAL '15 minutes', 'corr-1', TRUE)"
                )).isInstanceOf(SQLException.class);

                assertThatThrownBy(() -> st.executeUpdate(
                    "INSERT INTO wrong_answer_reports "
                        + "(user_id, problem_session_id, idempotency_key, payload_hash, report_reason, correlation_id, training_eligible) "
                        + "VALUES ('" + userId + "', '" + sessionId + "', 'war-train-bad-1', '" + HASH_A + "', 'WRONG_FINAL_ANSWER', 'corr-1', TRUE)"
                )).isInstanceOf(SQLException.class);

                // TTL > 60 minutes rejected on admin_raw_content_access_grants
                assertThatThrownBy(() -> st.executeUpdate(
                    "INSERT INTO admin_raw_content_access_grants "
                        + "(actor_user_id, actor_role, target_user_id, problem_session_id, ticket_reference, reason, idempotency_key, payload_hash, audit_log_id, granted_at, expires_at, correlation_id) "
                        + "VALUES ('" + adminId + "', 'ROLE_ADMIN_ENGINEERING', '" + userId + "', '" + sessionId + "', 'SUP-108', 'Inspect parse failure ticket', 'grant-ttl-bad-1', '" + HASH_A + "', '" + auditId + "', NOW(), NOW() + INTERVAL '61 minutes', 'corr-1')"
                )).isInstanceOf(SQLException.class);

                // Resolution consistency CHECK on wrong_answer_reports
                assertThatThrownBy(() -> st.executeUpdate(
                    "INSERT INTO wrong_answer_reports "
                        + "(user_id, problem_session_id, idempotency_key, payload_hash, report_reason, status, correlation_id) "
                        + "VALUES ('" + userId + "', '" + sessionId + "', 'war-res-bad-1', '" + HASH_A + "', 'WRONG_FINAL_ANSWER', 'RESOLVED_CONFIRMED_DEFECT', 'corr-1')"
                )).isInstanceOf(SQLException.class);

                // Unique idempotency indexes
                assertThatThrownBy(() -> st.executeUpdate(
                    "INSERT INTO admin_support_audit_logs "
                        + "(action, target_type, target_id, actor_user_id, actor_role, idempotency_key, payload_hash, reason, correlation_id) "
                        + "VALUES ('QUOTA_SYNC_EXECUTED', 'USER', '" + userId + "', '" + adminId + "', 'ROLE_ADMIN', 'adm-mig-audit-1', '" + HASH_A + "', 'Duplicate audit key', 'corr-2')"
                )).isInstanceOf(SQLException.class);

                // Insert valid user-scoped records for both users
                st.executeUpdate(
                    "INSERT INTO admin_raw_content_access_grants "
                        + "(actor_user_id, actor_role, target_user_id, problem_session_id, ticket_reference, reason, idempotency_key, payload_hash, audit_log_id, granted_at, expires_at, correlation_id) "
                        + "VALUES "
                        + "('" + adminId + "', 'ROLE_ADMIN_ENGINEERING', '" + userId + "', '" + sessionId + "', 'SUP-1081', 'Inspect parse failure ticket 1', 'grant-valid-1', '" + HASH_A + "', '" + auditId + "', NOW(), NOW() + INTERVAL '15 minutes', 'corr-1'), "
                        + "('" + adminId + "', 'ROLE_ADMIN_ENGINEERING', '" + otherUserId + "', '" + otherSessionId + "', 'SUP-1082', 'Inspect parse failure ticket 2', 'grant-valid-2', '" + HASH_A + "', '" + auditId + "', NOW(), NOW() + INTERVAL '15 minutes', 'corr-2')"
                );
                st.executeUpdate(
                    "INSERT INTO wrong_answer_reports "
                        + "(user_id, problem_session_id, idempotency_key, payload_hash, report_reason, correlation_id) "
                        + "VALUES "
                        + "('" + userId + "', '" + sessionId + "', 'war-valid-1', '" + HASH_A + "', 'WRONG_FINAL_ANSWER', 'corr-1'), "
                        + "('" + otherUserId + "', '" + otherSessionId + "', 'war-valid-2', '" + HASH_A + "', 'PARSE_MISMATCH', 'corr-2')"
                );

                // ON DELETE CASCADE from problem_sessions(id, user_id) purges user's grants + reports;
                // user deletion succeeds cleanly without affecting other user or non-PII audit logs.
                st.executeUpdate("DELETE FROM problem_sessions WHERE id = '" + sessionId + "'");
                st.executeUpdate("DELETE FROM users WHERE id = '" + userId + "'");

                var userGrantsRs = st.executeQuery(
                    "SELECT count(*) FROM admin_raw_content_access_grants WHERE target_user_id = '" + userId + "'"
                );
                userGrantsRs.next();
                assertThat(userGrantsRs.getInt(1)).isZero();

                var userReportsRs = st.executeQuery(
                    "SELECT count(*) FROM wrong_answer_reports WHERE user_id = '" + userId + "'"
                );
                userReportsRs.next();
                assertThat(userReportsRs.getInt(1)).isZero();

                var otherReportsRs = st.executeQuery(
                    "SELECT count(*) FROM wrong_answer_reports WHERE user_id = '" + otherUserId + "'"
                );
                otherReportsRs.next();
                assertThat(otherReportsRs.getInt(1)).isEqualTo(1);

                var auditRs = st.executeQuery(
                    "SELECT count(*) FROM admin_support_audit_logs WHERE id = '" + auditId + "'"
                );
                auditRs.next();
                assertThat(auditRs.getInt(1)).isEqualTo(1);
            }
        }
    }
}
