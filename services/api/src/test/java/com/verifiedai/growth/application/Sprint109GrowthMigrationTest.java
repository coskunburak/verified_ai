package com.verifiedai.growth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

final class Sprint109GrowthMigrationTest {

    private static final String HASH_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String HASH_DEV = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Test
    void upgradeV072ToV073EnforcesConstraintsUniqueIndexesAndCascadeDeletion() throws Exception {
        try (var db = new PostgreSQLContainer<>("postgres:16-alpine")) {
            db.start();

            Flyway.configure()
                .dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                .locations("classpath:db/migration/platform")
                .target("72")
                .load()
                .migrate();

            var upgradeResult = Flyway.configure()
                .dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                .locations("classpath:db/migration/platform")
                .target("73")
                .load()
                .migrate();
            assertThat(upgradeResult.migrationsExecuted).isEqualTo(1);

            var replayResult = Flyway.configure()
                .dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                .locations("classpath:db/migration/platform")
                .target("73")
                .load()
                .migrate();
            assertThat(replayResult.migrationsExecuted).isZero();

            try (var conn = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
                 var st = conn.createStatement()) {

                UUID user1 = UUID.fromString("00000000-0000-0000-0000-000000001091");
                UUID user2 = UUID.fromString("00000000-0000-0000-0000-000000001092");
                UUID user3 = UUID.fromString("00000000-0000-0000-0000-000000001093");
                UUID codeId1 = UUID.fromString("00000000-0000-0000-0000-000000002091");
                UUID codeId2 = UUID.fromString("00000000-0000-0000-0000-000000002092");

                st.executeUpdate(
                    "INSERT INTO users (id, status, created_at, updated_at) VALUES "
                        + "('" + user1 + "', 'ACTIVE', NOW(), NOW()), "
                        + "('" + user2 + "', 'ACTIVE', NOW(), NOW()), "
                        + "('" + user3 + "', 'ACTIVE', NOW(), NOW())"
                );

                st.executeUpdate(
                    "INSERT INTO growth_referral_codes (id, user_id, referral_code) VALUES "
                        + "('" + codeId1 + "', '" + user1 + "', 'CODEAAA2'), "
                        + "('" + codeId2 + "', '" + user2 + "', 'CODEBBB3')"
                );

                // CHECK (NOT training_eligible) on all 4 V073 tables
                assertThatThrownBy(() -> st.executeUpdate(
                    "INSERT INTO growth_referral_codes (user_id, referral_code, training_eligible) "
                        + "VALUES ('" + user3 + "', 'CODECCC4', TRUE)"
                )).isInstanceOf(SQLException.class);

                assertThatThrownBy(() -> st.executeUpdate(
                    "INSERT INTO growth_referral_attributions "
                        + "(referral_code_id, referrer_user_id, referred_user_id, referral_code_snapshot, idempotency_key, payload_hash, attribution_source, correlation_id, training_eligible) "
                        + "VALUES ('" + codeId1 + "', '" + user1 + "', '" + user3 + "', 'CODEAAA2', 'idem-train-1', '" + HASH_A + "', 'DEEP_LINK', 'corr-1', TRUE)"
                )).isInstanceOf(SQLException.class);

                assertThatThrownBy(() -> st.executeUpdate(
                    "INSERT INTO growth_shareable_artifacts "
                        + "(user_id, share_token, idempotency_key, payload_hash, artifact_type, summary_payload_json, deep_link_url, correlation_id, expires_at, training_eligible) "
                        + "VALUES ('" + user1 + "', 'ValidShareToken12345', 'share-train-1', '" + HASH_A + "', 'MASTERY_MILESTONE', '{}'::jsonb, 'verified-ai://share/ValidShareToken12345', 'corr-1', NOW() + INTERVAL '30 days', TRUE)"
                )).isInstanceOf(SQLException.class);

                assertThatThrownBy(() -> st.executeUpdate(
                    "INSERT INTO growth_store_review_events "
                        + "(user_id, idempotency_key, payload_hash, trigger_moment, outcome, app_version, correlation_id, training_eligible) "
                        + "VALUES ('" + user1 + "', 'rev-train-1', '" + HASH_A + "', 'INDEPENDENT_TWIN_MASTERED', 'PROMPT_SHOWN', '1.0.0', 'corr-1', TRUE)"
                )).isInstanceOf(SQLException.class);

                // ck_grw_ref_attr_no_self
                assertThatThrownBy(() -> st.executeUpdate(
                    "INSERT INTO growth_referral_attributions "
                        + "(referral_code_id, referrer_user_id, referred_user_id, referral_code_snapshot, idempotency_key, payload_hash, attribution_source, correlation_id) "
                        + "VALUES ('" + codeId1 + "', '" + user1 + "', '" + user1 + "', 'CODEAAA2', 'idem-self-1', '" + HASH_A + "', 'DEEP_LINK', 'corr-1')"
                )).isInstanceOf(SQLException.class);

                // ck_grw_share_redacted_mandatory
                assertThatThrownBy(() -> st.executeUpdate(
                    "INSERT INTO growth_shareable_artifacts "
                        + "(user_id, share_token, idempotency_key, payload_hash, artifact_type, summary_payload_json, deep_link_url, raw_content_redacted, correlation_id, expires_at) "
                        + "VALUES ('" + user1 + "', 'ValidShareToken12346', 'share-redact-1', '" + HASH_A + "', 'MASTERY_MILESTONE', '{}'::jsonb, 'verified-ai://share/ValidShareToken12346', FALSE, 'corr-1', NOW() + INTERVAL '30 days')"
                )).isInstanceOf(SQLException.class);

                // ck_grw_share_expiry_bounds (> 90 days rejected)
                assertThatThrownBy(() -> st.executeUpdate(
                    "INSERT INTO growth_shareable_artifacts "
                        + "(user_id, share_token, idempotency_key, payload_hash, artifact_type, summary_payload_json, deep_link_url, correlation_id, expires_at) "
                        + "VALUES ('" + user1 + "', 'ValidShareToken12347', 'share-exp-91', '" + HASH_A + "', 'MASTERY_MILESTONE', '{}'::jsonb, 'verified-ai://share/ValidShareToken12347', 'corr-1', NOW() + INTERVAL '91 days')"
                )).isInstanceOf(SQLException.class);

                // Insert valid rows for user1 and user2, plus unique index checks
                st.executeUpdate(
                    "INSERT INTO growth_referral_attributions "
                        + "(referral_code_id, referrer_user_id, referred_user_id, referral_code_snapshot, idempotency_key, payload_hash, device_fingerprint_hash, attribution_source, correlation_id) "
                        + "VALUES ('" + codeId1 + "', '" + user1 + "', '" + user3 + "', 'CODEAAA2', 'idem-valid-1', '" + HASH_A + "', '" + HASH_DEV + "', 'DEEP_LINK', 'corr-1')"
                );

                // Duplicate (referrer_user_id, device_fingerprint_hash) rejected
                UUID user4 = UUID.fromString("00000000-0000-0000-0000-000000001094");
                st.executeUpdate("INSERT INTO users (id, status, created_at, updated_at) VALUES ('" + user4 + "', 'ACTIVE', NOW(), NOW())");
                assertThatThrownBy(() -> st.executeUpdate(
                    "INSERT INTO growth_referral_attributions "
                        + "(referral_code_id, referrer_user_id, referred_user_id, referral_code_snapshot, idempotency_key, payload_hash, device_fingerprint_hash, attribution_source, correlation_id) "
                        + "VALUES ('" + codeId1 + "', '" + user1 + "', '" + user4 + "', 'CODEAAA2', 'idem-valid-2', '" + HASH_A + "', '" + HASH_DEV + "', 'DEEP_LINK', 'corr-2')"
                )).isInstanceOf(SQLException.class);

                st.executeUpdate(
                    "INSERT INTO growth_shareable_artifacts "
                        + "(user_id, share_token, idempotency_key, payload_hash, artifact_type, summary_payload_json, deep_link_url, correlation_id, expires_at) VALUES "
                        + "('" + user1 + "', 'ShareTokenUserOne123', 'share-u1-1', '" + HASH_A + "', 'MASTERY_MILESTONE', '{\"skillCode\":\"MATH.1\"}'::jsonb, 'verified-ai://share/ShareTokenUserOne123', 'corr-1', NOW() + INTERVAL '30 days'), "
                        + "('" + user2 + "', 'ShareTokenUserTwo123', 'share-u2-1', '" + HASH_A + "', 'MASTERY_MILESTONE', '{\"skillCode\":\"MATH.2\"}'::jsonb, 'verified-ai://share/ShareTokenUserTwo123', 'corr-2', NOW() + INTERVAL '30 days')"
                );
                st.executeUpdate(
                    "INSERT INTO growth_store_review_events "
                        + "(user_id, idempotency_key, payload_hash, trigger_moment, outcome, app_version, correlation_id) VALUES "
                        + "('" + user1 + "', 'rev-u1-1', '" + HASH_A + "', 'INDEPENDENT_TWIN_MASTERED', 'PROMPT_SHOWN', '1.0.0', 'corr-1'), "
                        + "('" + user2 + "', 'rev-u2-1', '" + HASH_A + "', 'INDEPENDENT_TWIN_MASTERED', 'PROMPT_SHOWN', '1.0.0', 'corr-2')"
                );

                // ON DELETE CASCADE isolation: deleting user1 purges user1 rows only, preserving user2 rows
                st.executeUpdate("DELETE FROM users WHERE id = '" + user1 + "'");

                try (var rs = st.executeQuery("SELECT COUNT(*) FROM growth_referral_codes WHERE user_id = '" + user1 + "'")) {
                    rs.next();
                    assertThat(rs.getInt(1)).isZero();
                }
                try (var rs = st.executeQuery("SELECT COUNT(*) FROM growth_referral_codes WHERE user_id = '" + user2 + "'")) {
                    rs.next();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                }
                try (var rs = st.executeQuery("SELECT COUNT(*) FROM growth_shareable_artifacts WHERE user_id = '" + user2 + "'")) {
                    rs.next();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                }
                try (var rs = st.executeQuery("SELECT COUNT(*) FROM growth_store_review_events WHERE user_id = '" + user2 + "'")) {
                    rs.next();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                }
            }
        }
    }
}
