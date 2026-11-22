package com.verifiedai.billing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

class Sprint102EntitlementMigrationTest {

    @Test
    void freshV001ToV066EnforcesConstraintsUniquenessAndCascade() throws Exception {
        try (var db = new PostgreSQLContainer<>("postgres:16-alpine")) {
            db.start();
            var flyway = Flyway.configure()
                .dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                .locations("classpath:db/migration/platform")
                .load();
            assertThat(flyway.migrate().migrationsExecuted).isGreaterThanOrEqualTo(63);
            assertThat(flyway.migrate().migrationsExecuted).isZero();

            try (var connection = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
                 var statement = connection.createStatement()) {
                try (var v = statement.executeQuery(
                    "select count(*) from flyway_schema_history where version in ('066', '66') and success = true")) {
                    assertThat(v.next()).isTrue();
                    assertThat(v.getInt(1)).isEqualTo(1);
                }
                statement.execute("""
                    insert into users (id, status, created_at, updated_at) values
                        ('00000000-0000-4000-8000-000000000201', 'ACTIVE', now(), now()),
                        ('00000000-0000-4000-8000-000000000202', 'ACTIVE', now(), now())
                    """);

                assertThatThrownBy(() -> statement.execute("""
                    insert into entitlements (id, user_id, tier, source, status, effective_at, package_code, created_at, updated_at)
                    values (gen_random_uuid(), '00000000-0000-4000-8000-000000000201', 'PRO_PLUS',
                        'APP_STORE_SUBSCRIPTION', 'ACTIVE', now(), 'TUTOR_COPILOT_PACK', now(), now())
                    """)).isInstanceOf(SQLException.class);

                assertThatThrownBy(() -> statement.execute("""
                    insert into entitlements (id, user_id, tier, source, status, effective_at, grace_period_expires_at, package_code, created_at, updated_at)
                    values (gen_random_uuid(), '00000000-0000-4000-8000-000000000201', 'PRO',
                        'APP_STORE_SUBSCRIPTION', 'GRACE_PERIOD', '2026-11-20T12:00:00Z', '2026-11-20T12:00:00Z', 'PRO', now(), now())
                    """)).isInstanceOf(SQLException.class);

                String validHash1 = "a".repeat(64);
                String validHash2 = "b".repeat(64);
                assertThatThrownBy(() -> statement.execute("""
                    insert into billing_entitlement_transition_audit
                        (user_id, event_key_hash, from_package, to_package, from_status, to_status, reason, occurred_at, training_eligible)
                    values ('00000000-0000-4000-8000-000000000201', '%s', 'FREE', 'PRO', 'ACTIVE', 'ACTIVE', 'PURCHASE', now(), true)
                    """.formatted(validHash1))).isInstanceOf(SQLException.class);

                assertThatThrownBy(() -> statement.execute("""
                    insert into billing_entitlement_transition_audit
                        (user_id, event_key_hash, from_package, to_package, from_status, to_status, reason, occurred_at)
                    values ('00000000-0000-4000-8000-000000000201', 'not-64-hex', 'FREE', 'PRO', 'ACTIVE', 'ACTIVE', 'PURCHASE', now())
                    """)).isInstanceOf(SQLException.class);

                assertThatThrownBy(() -> statement.execute("""
                    insert into billing_entitlement_transition_audit
                        (user_id, event_key_hash, from_package, to_package, from_status, to_status, reason, occurred_at)
                    values ('00000000-0000-4000-8000-000000000201', '%s', 'FREE', 'TUTOR_COPILOT_PACK', 'ACTIVE', 'ACTIVE', 'PURCHASE', now())
                    """.formatted(validHash1))).isInstanceOf(SQLException.class);

                assertThatThrownBy(() -> statement.execute("""
                    insert into billing_entitlement_transition_audit
                        (user_id, event_key_hash, from_package, to_package, from_status, to_status, reason, occurred_at)
                    values ('00000000-0000-4000-8000-000000000201', '%s', 'FREE', 'PRO', 'ACTIVE', 'ACTIVE', 'UNKNOWN_CAUSE', now())
                    """.formatted(validHash1))).isInstanceOf(SQLException.class);

                statement.execute("""
                    insert into billing_entitlement_transition_audit
                        (user_id, event_key_hash, from_package, to_package, from_status, to_status, reason, occurred_at)
                    values ('00000000-0000-4000-8000-000000000201', '%s', 'FREE', 'PRO', 'ACTIVE', 'ACTIVE', 'PURCHASE', now())
                    """.formatted(validHash1));

                assertThatThrownBy(() -> statement.execute("""
                    insert into billing_entitlement_transition_audit
                        (user_id, event_key_hash, from_package, to_package, from_status, to_status, reason, occurred_at)
                    values ('00000000-0000-4000-8000-000000000202', '%s', 'FREE', 'PRO', 'ACTIVE', 'ACTIVE', 'PURCHASE', now())
                    """.formatted(validHash1))).isInstanceOf(SQLException.class);

                statement.execute("""
                    insert into billing_entitlement_transition_audit
                        (user_id, event_key_hash, from_package, to_package, from_status, to_status, reason, occurred_at)
                    values ('00000000-0000-4000-8000-000000000202', '%s', 'FREE', 'COACH_PRO', 'ACTIVE', 'ACTIVE', 'PURCHASE', now())
                    """.formatted(validHash2));

                statement.execute("delete from users where id = '00000000-0000-4000-8000-000000000201'");
                try (var rs = statement.executeQuery("select count(*), min(user_id::text) from billing_entitlement_transition_audit")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                    assertThat(rs.getString(2)).isEqualTo("00000000-0000-4000-8000-000000000202");
                }
            }
        }
    }

    @Test
    void cleanUpgradeV065ToV066BackfillsPackageGraceAndRevokedFlag() throws Exception {
        try (var db = new PostgreSQLContainer<>("postgres:16-alpine")) {
            db.start();
            Flyway.configure()
                .dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                .locations("classpath:db/migration/platform")
                .target("65")
                .load()
                .migrate();

            try (var connection = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
                 var statement = connection.createStatement()) {
                statement.execute("""
                    insert into users (id, status, created_at, updated_at) values
                        ('00000000-0000-4000-8000-000000000301', 'ACTIVE', now(), now()),
                        ('00000000-0000-4000-8000-000000000302', 'ACTIVE', now(), now()),
                        ('00000000-0000-4000-8000-000000000303', 'ACTIVE', now(), now()),
                        ('00000000-0000-4000-8000-000000000304', 'ACTIVE', now(), now()),
                        ('00000000-0000-4000-8000-000000000305', 'ACTIVE', now(), now())
                    """);
                statement.execute("""
                    insert into entitlements (id, user_id, tier, source, status, effective_at, expires_at, original_transaction_id, environment, created_at, updated_at) values
                        (gen_random_uuid(), '00000000-0000-4000-8000-000000000301', 'FREE', 'DEFAULT_FREE', 'ACTIVE', '2026-11-01T00:00:00Z', null, null, null, now(), now()),
                        (gen_random_uuid(), '00000000-0000-4000-8000-000000000302', 'PRO', 'APP_STORE_SUBSCRIPTION', 'GRACE_PERIOD', '2026-11-01T00:00:00Z', '2026-11-15T00:00:00Z', 'orig-pro-302', 'XCODE', now(), now()),
                        (gen_random_uuid(), '00000000-0000-4000-8000-000000000303', 'PRO_PLUS', 'PROMOTIONAL', 'ACTIVE', '2026-11-01T00:00:00Z', '2026-12-01T00:00:00Z', null, null, now(), now()),
                        (gen_random_uuid(), '00000000-0000-4000-8000-000000000304', 'PRO_PLUS', 'APP_STORE_SUBSCRIPTION', 'ACTIVE', '2026-11-01T00:00:00Z', '2027-05-01T00:00:00Z', 'orig-season-304', 'XCODE', now(), now()),
                        (gen_random_uuid(), '00000000-0000-4000-8000-000000000305', 'FREE', 'DEFAULT_FREE', 'ACTIVE', '2026-11-01T00:00:00Z', null, null, null, now(), now())
                    """);
                statement.execute("""
                    insert into app_store_transactions
                        (id, user_id, transaction_id, original_transaction_id, product_id, environment, purchase_date, expires_date, offer_family, payload_digest, created_at, updated_at)
                    values
                        (gen_random_uuid(), '00000000-0000-4000-8000-000000000302', 'tx-pro-302', 'orig-pro-302', 'local_dev_pro_monthly', 'XCODE', '2026-11-01T00:00:00Z', '2026-11-15T00:00:00Z', null, 'd302', now(), now()),
                        (gen_random_uuid(), '00000000-0000-4000-8000-000000000304', 'tx-season-304', 'orig-season-304', 'local_dev_season_6m', 'XCODE', '2026-11-01T00:00:00Z', '2027-05-01T00:00:00Z', 'YKS_SEASON_PASS', 'd304', now(), now()),
                        (gen_random_uuid(), '00000000-0000-4000-8000-000000000305', 'tx-revoked-305', 'orig-revoked-305', 'local_dev_pro_monthly', 'XCODE', '2026-11-01T00:00:00Z', '2026-12-01T00:00:00Z', null, 'd305', now(), now())
                    """);
                statement.execute("""
                    insert into app_store_subscriptions
                        (id, user_id, original_transaction_id, current_transaction_id, product_id, environment, status, expires_at, grace_period_expires_at, created_at, updated_at)
                    values
                        (gen_random_uuid(), '00000000-0000-4000-8000-000000000302', 'orig-pro-302', 'tx-pro-302', 'local_dev_pro_monthly', 'XCODE', 'GRACE_PERIOD', '2026-11-15T00:00:00Z', '2026-11-18T00:00:00Z', now(), now()),
                        (gen_random_uuid(), '00000000-0000-4000-8000-000000000304', 'orig-season-304', 'tx-season-304', 'local_dev_season_6m', 'XCODE', 'ACTIVE', '2027-05-01T00:00:00Z', null, now(), now()),
                        (gen_random_uuid(), '00000000-0000-4000-8000-000000000305', 'orig-revoked-305', 'tx-revoked-305', 'local_dev_pro_monthly', 'XCODE', 'REVOKED', '2026-12-01T00:00:00Z', null, now(), now())
                    """);

                var upgrade = Flyway.configure()
                    .dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                    .locations("classpath:db/migration/platform")
                    .target("66")
                    .load();
                assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
                assertThat(upgrade.migrate().migrationsExecuted).isZero();

                try (var rs = statement.executeQuery("""
                    select user_id::text, tier, package_code, grace_period_expires_at
                    from entitlements order by user_id
                    """)) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("package_code")).isEqualTo("FREE");

                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("package_code")).isEqualTo("PRO");
                    assertThat(rs.getTimestamp("grace_period_expires_at")).isNotNull();

                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("tier")).isEqualTo("PRO_PLUS");
                    assertThat(rs.getString("package_code")).isEqualTo("COACH_PRO");

                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("tier")).isEqualTo("PRO_PLUS");
                    assertThat(rs.getString("package_code")).isEqualTo("YKS_SEASON_PASS");
                }

                try (var tx = statement.executeQuery("""
                    select transaction_id, access_revoked from app_store_transactions order by transaction_id
                    """)) {
                    while (tx.next()) {
                        boolean expectedRevoked = "tx-revoked-305".equals(tx.getString("transaction_id"));
                        assertThat(tx.getBoolean("access_revoked")).isEqualTo(expectedRevoked);
                    }
                }
            }
        }
    }
}
