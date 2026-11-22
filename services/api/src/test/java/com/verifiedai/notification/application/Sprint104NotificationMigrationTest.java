package com.verifiedai.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

final class Sprint104NotificationMigrationTest {

    @Test
    void upgradeV067ToV068EnforcesConstraintsPartialIndexesAndCascade() throws Exception {
        try (var db = new PostgreSQLContainer<>("postgres:16-alpine")) {
            db.start();
            Flyway.configure()
                .dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                .locations("classpath:db/migration/platform")
                .target("67")
                .load()
                .migrate();

            var upgrade = Flyway.configure()
                .dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                .locations("classpath:db/migration/platform")
                .target("68")
                .load();
            assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(upgrade.migrate().migrationsExecuted).isZero();

            try (var connection = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
                 var statement = connection.createStatement()) {
                try (var v = statement.executeQuery(
                    "select count(*) from flyway_schema_history where version in ('068', '68') and success = true")) {
                    assertThat(v.next()).isTrue();
                    assertThat(v.getInt(1)).isEqualTo(1);
                }

                statement.execute("""
                    insert into users (id, status, created_at, updated_at) values
                        ('00000000-0000-4000-8000-000000000601', 'ACTIVE', now(), now()),
                        ('00000000-0000-4000-8000-000000000602', 'ACTIVE', now(), now())
                    """);

                // Preference constraints: account_security_enabled=false, daily_cap=6, start==end, training_eligible=true
                assertThatThrownBy(() -> statement.execute("""
                    insert into notification_preferences (user_id, account_security_enabled)
                    values ('00000000-0000-4000-8000-000000000601', false)
                    """)).isInstanceOf(SQLException.class);

                assertThatThrownBy(() -> statement.execute("""
                    insert into notification_preferences (user_id, daily_cap)
                    values ('00000000-0000-4000-8000-000000000601', 6)
                    """)).isInstanceOf(SQLException.class);

                assertThatThrownBy(() -> statement.execute("""
                    insert into notification_preferences (user_id, quiet_hours_start_local, quiet_hours_end_local)
                    values ('00000000-0000-4000-8000-000000000601', TIME '22:00:00', TIME '22:00:00')
                    """)).isInstanceOf(SQLException.class);

                assertThatThrownBy(() -> statement.execute("""
                    insert into notification_preferences (user_id, training_eligible)
                    values ('00000000-0000-4000-8000-000000000601', true)
                    """)).isInstanceOf(SQLException.class);

                statement.execute("""
                    insert into notification_preferences (user_id) values
                        ('00000000-0000-4000-8000-000000000601'),
                        ('00000000-0000-4000-8000-000000000602')
                    """);

                // Verify default opt-out values
                try (var prefRs = statement.executeQuery("""
                    select push_enabled, study_plan_enabled, spaced_review_enabled,
                           exam_deadline_enabled, billing_trial_reminder_enabled, account_security_enabled
                    from notification_preferences
                    where user_id = '00000000-0000-4000-8000-000000000601'
                    """)) {
                    assertThat(prefRs.next()).isTrue();
                    assertThat(prefRs.getBoolean("push_enabled")).isFalse();
                    assertThat(prefRs.getBoolean("study_plan_enabled")).isFalse();
                    assertThat(prefRs.getBoolean("spaced_review_enabled")).isFalse();
                    assertThat(prefRs.getBoolean("exam_deadline_enabled")).isFalse();
                    assertThat(prefRs.getBoolean("billing_trial_reminder_enabled")).isFalse();
                    assertThat(prefRs.getBoolean("account_security_enabled")).isTrue();
                }

                // Device constraints & partial unique index on active token_sha256
                String tok1 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
                String hash1 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
                String hash2 = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

                assertThatThrownBy(() -> statement.execute("""
                    insert into notification_devices
                        (user_id, platform, environment, device_token, token_sha256, token_last4,
                         os_permission_status, app_version, training_eligible)
                    values ('00000000-0000-4000-8000-000000000601', 'IOS_APNS', 'SANDBOX',
                            '%s', '%s', 'cdef', 'AUTHORIZED', '1.0.0', true)
                    """.formatted(tok1, hash1))).isInstanceOf(SQLException.class);

                statement.execute("""
                    insert into notification_devices
                        (user_id, platform, environment, device_token, token_sha256, token_last4,
                         os_permission_status, app_version)
                    values ('00000000-0000-4000-8000-000000000601', 'IOS_APNS', 'SANDBOX',
                            '%s', '%s', 'cdef', 'AUTHORIZED', '1.0.0')
                    """.formatted(tok1, hash1));

                // Same active token_sha256 rejected for user 2
                assertThatThrownBy(() -> statement.execute("""
                    insert into notification_devices
                        (user_id, platform, environment, device_token, token_sha256, token_last4,
                         os_permission_status, app_version)
                    values ('00000000-0000-4000-8000-000000000602', 'IOS_APNS', 'SANDBOX',
                            '%s', '%s', 'cdef', 'AUTHORIZED', '1.0.0')
                    """.formatted(tok1, hash1))).isInstanceOf(SQLException.class);

                statement.execute("""
                    insert into notification_devices
                        (user_id, platform, environment, device_token, token_sha256, token_last4,
                         os_permission_status, app_version)
                    values ('00000000-0000-4000-8000-000000000602', 'IOS_APNS', 'SANDBOX',
                            '%s', '%s', 'cdef', 'AUTHORIZED', '1.0.0')
                    """.formatted(tok1, hash2));

                // Delivery & event constraints
                assertThatThrownBy(() -> statement.execute("""
                    insert into notification_deliveries
                        (id, user_id, idempotency_key, payload_hash, category, intent_type,
                         template_key, deep_link_path, status, scheduled_for, effective_send_at,
                         expires_at, correlation_id, source_module, training_eligible)
                    values ('00000000-0000-4000-8000-000000000701', '00000000-0000-4000-8000-000000000601',
                            'idem-bad-te', '%s', 'STUDY_PLAN', 'STUDY_PLAN_DAILY_MISSION',
                            'study.daily', 'verified-ai://study-plan/today', 'SCHEDULED',
                            '2026-11-22T10:00:00Z', '2026-11-22T10:00:00Z', '2026-11-22T11:00:00Z',
                            'corr-1', 'studyplan', true)
                    """.formatted(hash1))).isInstanceOf(SQLException.class);

                assertThatThrownBy(() -> statement.execute("""
                    insert into notification_deliveries
                        (id, user_id, idempotency_key, payload_hash, category, intent_type,
                         template_key, deep_link_path, status, scheduled_for, effective_send_at,
                         expires_at, correlation_id, source_module)
                    values ('00000000-0000-4000-8000-000000000702', '00000000-0000-4000-8000-000000000601',
                            'idem-bad-exp', '%s', 'STUDY_PLAN', 'STUDY_PLAN_DAILY_MISSION',
                            'study.daily', 'verified-ai://study-plan/today', 'SCHEDULED',
                            '2026-11-22T10:00:00Z', '2026-11-22T10:00:00Z', '2026-11-22T10:00:00Z',
                            'corr-1', 'studyplan')
                    """.formatted(hash1))).isInstanceOf(SQLException.class);

                statement.execute("""
                    insert into notification_deliveries
                        (id, user_id, idempotency_key, payload_hash, category, intent_type,
                         template_key, deep_link_path, status, scheduled_for, effective_send_at,
                         expires_at, correlation_id, source_module)
                    values
                        ('00000000-0000-4000-8000-000000000703', '00000000-0000-4000-8000-000000000601',
                         'idem-valid-1', '%s', 'BILLING_TRIAL_REMINDER', 'TRIAL_ENDING_48H_REMINDER',
                         'billing.trial.48h', 'verified-ai://subscription/manage', 'SCHEDULED',
                         '2026-11-22T10:00:00Z', '2026-11-22T10:00:00Z', '2026-11-22T12:00:00Z',
                         'corr-1', 'billing'),
                        ('00000000-0000-4000-8000-000000000704', '00000000-0000-4000-8000-000000000602',
                         'idem-valid-2', '%s', 'ACCOUNT_SECURITY', 'ACCOUNT_SECURITY_ALERT',
                         'account.security', 'verified-ai://account/security', 'SCHEDULED',
                         '2026-11-22T10:00:00Z', '2026-11-22T10:00:00Z', '2026-11-22T12:00:00Z',
                         'corr-2', 'identity')
                    """.formatted(hash1, hash2));

                statement.execute("""
                    insert into notification_delivery_events
                        (delivery_id, user_id, from_status, to_status, reason_code, correlation_id)
                    values
                        ('00000000-0000-4000-8000-000000000703', '00000000-0000-4000-8000-000000000601',
                         null, 'SCHEDULED', 'SCHEDULED', 'corr-1'),
                        ('00000000-0000-4000-8000-000000000704', '00000000-0000-4000-8000-000000000602',
                         null, 'SCHEDULED', 'SCHEDULED', 'corr-2')
                    """);

                // ON DELETE CASCADE purges all 4 tables for user 1 without affecting user 2
                statement.execute("delete from users where id = '00000000-0000-4000-8000-000000000601'");
                for (String table : new String[]{
                    "notification_preferences",
                    "notification_devices",
                    "notification_deliveries",
                    "notification_delivery_events"
                }) {
                    try (var rs = statement.executeQuery(
                        "select count(*), min(user_id::text) from " + table)) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getInt(1)).isEqualTo(1);
                        assertThat(rs.getString(2)).isEqualTo("00000000-0000-4000-8000-000000000602");
                    }
                }
            }
        }
    }
}
