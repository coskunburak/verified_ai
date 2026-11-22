package com.verifiedai.billing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

final class Sprint103QuotaMigrationTest {

    @Test
    void upgradeV066ToV067EnforcesConstraintsGeneratedUsdAndCascade() throws Exception {
        try (var db = new PostgreSQLContainer<>("postgres:16-alpine")) {
            db.start();
            Flyway.configure()
                .dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                .locations("classpath:db/migration/platform")
                .target("66")
                .load()
                .migrate();

            var upgrade = Flyway.configure()
                .dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                .locations("classpath:db/migration/platform")
                .target("67")
                .load();
            assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(upgrade.migrate().migrationsExecuted).isZero();

            try (var connection = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
                 var statement = connection.createStatement()) {
                try (var v = statement.executeQuery(
                    "select count(*) from flyway_schema_history where version in ('067', '67') and success = true")) {
                    assertThat(v.next()).isTrue();
                    assertThat(v.getInt(1)).isEqualTo(1);
                }

                statement.execute("""
                    insert into users (id, status, created_at, updated_at) values
                        ('00000000-0000-4000-8000-000000000401', 'ACTIVE', now(), now()),
                        ('00000000-0000-4000-8000-000000000402', 'ACTIVE', now(), now())
                    """);

                // Negative counters & costs rejected
                assertThatThrownBy(() -> statement.execute("""
                    insert into entitlement_quota_ledgers
                        (user_id, period_key, tier_code, ai_verifications_used, ai_verifications_limit,
                         hybrid_twins_limit, ai_cost_cap_micros, resets_at)
                    values ('00000000-0000-4000-8000-000000000401', '2026-11-22', 'FREE', -1, 3, 1, 80000, now())
                    """)).isInstanceOf(SQLException.class);

                assertThatThrownBy(() -> statement.execute("""
                    insert into entitlement_quota_ledgers
                        (user_id, period_key, tier_code, ai_verifications_limit, hybrid_twins_limit,
                         accumulated_ai_cost_micros, ai_cost_cap_micros, resets_at)
                    values ('00000000-0000-4000-8000-000000000401', '2026-11-22', 'FREE', 3, 1, -5, 80000, now())
                    """)).isInstanceOf(SQLException.class);

                // Invalid tier_code & training_eligible = true rejected
                assertThatThrownBy(() -> statement.execute("""
                    insert into entitlement_quota_ledgers
                        (user_id, period_key, tier_code, ai_verifications_limit, hybrid_twins_limit, ai_cost_cap_micros, resets_at)
                    values ('00000000-0000-4000-8000-000000000401', '2026-11-22', 'TUTOR_COPILOT_PACK', 3, 1, 80000, now())
                    """)).isInstanceOf(SQLException.class);

                assertThatThrownBy(() -> statement.execute("""
                    insert into entitlement_quota_ledgers
                        (user_id, period_key, tier_code, ai_verifications_limit, hybrid_twins_limit,
                         ai_cost_cap_micros, resets_at, training_eligible)
                    values ('00000000-0000-4000-8000-000000000401', '2026-11-22', 'FREE', 3, 1, 80000, now(), true)
                    """)).isInstanceOf(SQLException.class);

                // Valid inserts + generated USD verification
                statement.execute("""
                    insert into entitlement_quota_ledgers
                        (id, user_id, period_key, tier_code, ai_verifications_used, ai_verifications_limit,
                         hybrid_twins_used, hybrid_twins_limit, accumulated_ai_cost_micros, ai_cost_cap_micros, resets_at)
                    values
                        ('00000000-0000-4000-8000-000000000501', '00000000-0000-4000-8000-000000000401',
                         '2026-11', 'PRO', 10, 150, 2, 150, 2500000, 2500000, '2026-12-01T00:00:00Z'),
                        ('00000000-0000-4000-8000-000000000502', '00000000-0000-4000-8000-000000000402',
                         '2026-11', 'COACH_PRO', 1, 400, 0, 400, 12500, 4200000, '2026-12-01T00:00:00Z')
                    """);

                try (var usdRs = statement.executeQuery("""
                    select accumulated_ai_cost_usd
                    from entitlement_quota_ledgers
                    where id = '00000000-0000-4000-8000-000000000501'
                    """)) {
                    assertThat(usdRs.next()).isTrue();
                    assertThat(usdRs.getBigDecimal(1)).isEqualByComparingTo(new BigDecimal("2.500000"));
                }

                // Unique (user_id, period_key)
                assertThatThrownBy(() -> statement.execute("""
                    insert into entitlement_quota_ledgers
                        (user_id, period_key, tier_code, ai_verifications_limit, hybrid_twins_limit, ai_cost_cap_micros, resets_at)
                    values ('00000000-0000-4000-8000-000000000401', '2026-11', 'PRO', 150, 150, 2500000, '2026-12-01T00:00:00Z')
                    """)).isInstanceOf(SQLException.class);

                // Consumption checks: training_eligible=true, bad quota_type, bad status, zero units rejected
                assertThatThrownBy(() -> statement.execute("""
                    insert into entitlement_quota_consumptions
                        (ledger_id, user_id, idempotency_key, quota_type, units_consumed, cost_micros, status, training_eligible)
                    values ('00000000-0000-4000-8000-000000000501', '00000000-0000-4000-8000-000000000401',
                            'idem-bad-1', 'AI_VERIFICATION', 1, 100, 'RESERVED', true)
                    """)).isInstanceOf(SQLException.class);

                assertThatThrownBy(() -> statement.execute("""
                    insert into entitlement_quota_consumptions
                        (ledger_id, user_id, idempotency_key, quota_type, units_consumed, cost_micros, status)
                    values ('00000000-0000-4000-8000-000000000501', '00000000-0000-4000-8000-000000000401',
                            'idem-bad-2', 'DETERMINISTIC_TWIN', 1, 0, 'RESERVED')
                    """)).isInstanceOf(SQLException.class);

                assertThatThrownBy(() -> statement.execute("""
                    insert into entitlement_quota_consumptions
                        (ledger_id, user_id, idempotency_key, quota_type, units_consumed, cost_micros, status)
                    values ('00000000-0000-4000-8000-000000000501', '00000000-0000-4000-8000-000000000401',
                            'idem-bad-3', 'AI_VERIFICATION', 0, 100, 'RESERVED')
                    """)).isInstanceOf(SQLException.class);

                statement.execute("""
                    insert into entitlement_quota_consumptions
                        (ledger_id, user_id, idempotency_key, quota_type, units_consumed, cost_micros, status)
                    values
                        ('00000000-0000-4000-8000-000000000501', '00000000-0000-4000-8000-000000000401',
                         'idem-ok-1', 'AI_VERIFICATION', 1, 5000, 'COMMITTED'),
                        ('00000000-0000-4000-8000-000000000502', '00000000-0000-4000-8000-000000000402',
                         'idem-ok-2', 'HYBRID_TWIN', 1, 12500, 'COMMITTED')
                    """);

                // Unique (user_id, idempotency_key)
                assertThatThrownBy(() -> statement.execute("""
                    insert into entitlement_quota_consumptions
                        (ledger_id, user_id, idempotency_key, quota_type, units_consumed, cost_micros, status)
                    values ('00000000-0000-4000-8000-000000000501', '00000000-0000-4000-8000-000000000401',
                            'idem-ok-1', 'AI_VERIFICATION', 1, 5000, 'RESERVED')
                    """)).isInstanceOf(SQLException.class);

                // ON DELETE CASCADE deletes owner rows without affecting user 2
                statement.execute("delete from users where id = '00000000-0000-4000-8000-000000000401'");
                try (var rs = statement.executeQuery("select count(*), min(user_id::text) from entitlement_quota_ledgers")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                    assertThat(rs.getString(2)).isEqualTo("00000000-0000-4000-8000-000000000402");
                }
                try (var rs = statement.executeQuery("select count(*), min(user_id::text) from entitlement_quota_consumptions")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                    assertThat(rs.getString(2)).isEqualTo("00000000-0000-4000-8000-000000000402");
                }
            }
        }
    }
}
