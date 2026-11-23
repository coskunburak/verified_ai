package com.verifiedai.analytics.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

final class Sprint106AnalyticsMigrationTest {

    private static final String HASH_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Test
    void upgradeV069ToV070EnforcesConstraintsIndexesAndCascade() throws Exception {
        try (var db = new PostgreSQLContainer<>("postgres:16-alpine")) {
            db.start();
            Flyway.configure()
                .dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                .locations("classpath:db/migration/platform")
                .target("69")
                .load()
                .migrate();

            var upgrade = Flyway.configure()
                .dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                .locations("classpath:db/migration/platform")
                .target("70")
                .load();
            assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(upgrade.migrate().migrationsExecuted).isZero();

            try (var connection = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
                 var statement = connection.createStatement()) {
                try (var v = statement.executeQuery(
                    "select count(*) from flyway_schema_history where version in ('070', '70') and success = true")) {
                    assertThat(v.next()).isTrue();
                    assertThat(v.getInt(1)).isEqualTo(1);
                }

                statement.execute("""
                    insert into users (id, status, created_at, updated_at) values
                        ('00000000-0000-4000-8000-000000000701', 'ACTIVE', now(), now()),
                        ('00000000-0000-4000-8000-000000000702', 'ACTIVE', now(), now())
                    """);

                // CHECK (NOT training_eligible) on analytics_events
                assertThatThrownBy(() -> statement.execute("""
                    insert into analytics_events (
                        user_id, client_event_id, payload_hash, event_name, event_domain,
                        emitted_by, source_module, correlation_id, occurred_at, training_eligible
                    ) values (
                        '00000000-0000-4000-8000-000000000701', 'evt-bad-train-01', '%s',
                        'onboarding_started', 'ACQUISITION_ACTIVATION', 'CLIENT', 'ios_client',
                        'corr-1', now(), true
                    )
                    """.formatted(HASH_A))).isInstanceOf(SQLException.class);

                // Invalid domain, emitted_by, tier, latency bounds
                assertThatThrownBy(() -> statement.execute("""
                    insert into analytics_events (
                        user_id, client_event_id, payload_hash, event_name, event_domain,
                        emitted_by, source_module, correlation_id, occurred_at
                    ) values (
                        '00000000-0000-4000-8000-000000000701', 'evt-bad-domain-1', '%s',
                        'onboarding_started', 'ADS_TRACKING', 'CLIENT', 'ios_client',
                        'corr-1', now()
                    )
                    """.formatted(HASH_A))).isInstanceOf(SQLException.class);

                assertThatThrownBy(() -> statement.execute("""
                    insert into analytics_events (
                        user_id, client_event_id, payload_hash, event_name, event_domain,
                        emitted_by, source_module, entitlement_tier, correlation_id, occurred_at
                    ) values (
                        '00000000-0000-4000-8000-000000000701', 'evt-bad-tier-01', '%s',
                        'onboarding_started', 'ACQUISITION_ACTIVATION', 'CLIENT', 'ios_client',
                        'ENTERPRISE_VIP', 'corr-1', now()
                    )
                    """.formatted(HASH_A))).isInstanceOf(SQLException.class);

                assertThatThrownBy(() -> statement.execute("""
                    insert into analytics_events (
                        user_id, client_event_id, payload_hash, event_name, event_domain,
                        emitted_by, source_module, latency_ms, correlation_id, occurred_at
                    ) values (
                        '00000000-0000-4000-8000-000000000701', 'evt-bad-lat-01', '%s',
                        'onboarding_started', 'ACQUISITION_ACTIVATION', 'CLIENT', 'ios_client',
                        600001, 'corr-1', now()
                    )
                    """.formatted(HASH_A))).isInstanceOf(SQLException.class);

                // Rollup CHECK constraints: training_eligible=true and p95 < p50
                assertThatThrownBy(() -> statement.execute("""
                    insert into analytics_daily_metric_rollups (
                        metric_date, entitlement_tier, event_domain, event_name, training_eligible
                    ) values ('2026-11-23', 'ALL', 'SOLVING', 'solve_completed', true)
                    """)).isInstanceOf(SQLException.class);

                assertThatThrownBy(() -> statement.execute("""
                    insert into analytics_daily_metric_rollups (
                        metric_date, entitlement_tier, event_domain, event_name,
                        event_count, distinct_user_count, p50_latency_ms, p95_latency_ms
                    ) values ('2026-11-23', 'ALL', 'SOLVING', 'solve_completed', 10, 5, 500, 300)
                    """)).isInstanceOf(SQLException.class);

                // Valid rows for user 1 & user 2 + UNIQUE (user_id, client_event_id)
                statement.execute("""
                    insert into analytics_events (
                        user_id, client_event_id, payload_hash, event_name, event_domain,
                        emitted_by, source_module, correlation_id, occurred_at
                    ) values
                        ('00000000-0000-4000-8000-000000000701', 'shared-evt-id-01', '%s',
                         'onboarding_started', 'ACQUISITION_ACTIVATION', 'CLIENT', 'ios_client', 'corr-1', now()),
                        ('00000000-0000-4000-8000-000000000702', 'shared-evt-id-01', '%s',
                         'onboarding_started', 'ACQUISITION_ACTIVATION', 'CLIENT', 'ios_client', 'corr-2', now())
                    """.formatted(HASH_A, HASH_A));

                assertThatThrownBy(() -> statement.execute("""
                    insert into analytics_events (
                        user_id, client_event_id, payload_hash, event_name, event_domain,
                        emitted_by, source_module, correlation_id, occurred_at
                    ) values (
                        '00000000-0000-4000-8000-000000000701', 'shared-evt-id-01', '%s',
                        'onboarding_started', 'ACQUISITION_ACTIVATION', 'CLIENT', 'ios_client', 'corr-dup', now()
                    )
                    """.formatted(HASH_A))).isInstanceOf(SQLException.class);

                statement.execute("""
                    insert into analytics_daily_metric_rollups (
                        metric_date, entitlement_tier, event_domain, event_name,
                        event_count, distinct_user_count, total_ai_cost_micros, p50_latency_ms, p95_latency_ms
                    ) values ('2026-11-23', 'ALL', 'ACQUISITION_ACTIVATION', 'onboarding_started', 2, 2, 0, 120, 200)
                    """);

                // ON DELETE CASCADE deletes user 1 rows without touching user 2 or anonymized rollups
                statement.execute("delete from users where id = '00000000-0000-4000-8000-000000000701'");
                try (var rs = statement.executeQuery(
                    "select count(*), min(user_id::text) from analytics_events")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                    assertThat(rs.getString(2)).isEqualTo("00000000-0000-4000-8000-000000000702");
                }
                try (var rollupRs = statement.executeQuery(
                    "select count(*) from analytics_daily_metric_rollups")) {
                    assertThat(rollupRs.next()).isTrue();
                    assertThat(rollupRs.getInt(1)).isEqualTo(1);
                }
            }
        }
    }
}
