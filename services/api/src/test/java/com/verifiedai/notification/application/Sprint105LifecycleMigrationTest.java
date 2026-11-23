package com.verifiedai.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

final class Sprint105LifecycleMigrationTest {

    @Test
    void upgradeV068ToV069EnforcesConstraintsIndexesAndCascade() throws Exception {
        try (var db = new PostgreSQLContainer<>("postgres:16-alpine")) {
            db.start();
            Flyway.configure()
                .dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                .locations("classpath:db/migration/platform")
                .target("68")
                .load()
                .migrate();

            var upgrade = Flyway.configure()
                .dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                .locations("classpath:db/migration/platform")
                .target("69")
                .load();
            assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(upgrade.migrate().migrationsExecuted).isZero();

            try (var connection = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
                 var statement = connection.createStatement()) {
                try (var v = statement.executeQuery(
                    "select count(*) from flyway_schema_history where version in ('069', '69') and success = true")) {
                    assertThat(v.next()).isTrue();
                    assertThat(v.getInt(1)).isEqualTo(1);
                }

                statement.execute("""
                    insert into users (id, status, created_at, updated_at) values
                        ('00000000-0000-4000-8000-000000000691', 'ACTIVE', now(), now()),
                        ('00000000-0000-4000-8000-000000000692', 'ACTIVE', now(), now())
                    """);

                // Cooldown CHECK constraints: training_eligible=true, cooldown_until < last_triggered_at, invalid category
                assertThatThrownBy(() -> statement.execute("""
                    insert into lifecycle_messaging_cooldowns
                        (user_id, category, intent_type, last_idempotency_key, last_triggered_at, cooldown_until, training_eligible)
                    values ('00000000-0000-4000-8000-000000000691', 'STUDY_PLAN', 'STUDY_PLAN_DAILY_MISSION',
                            'studyplan:daily:user1:2026-11-22', '2026-11-22T10:00:00Z', '2026-11-23T06:00:00Z', true)
                    """)).isInstanceOf(SQLException.class);

                assertThatThrownBy(() -> statement.execute("""
                    insert into lifecycle_messaging_cooldowns
                        (user_id, category, intent_type, last_idempotency_key, last_triggered_at, cooldown_until)
                    values ('00000000-0000-4000-8000-000000000691', 'STUDY_PLAN', 'STUDY_PLAN_DAILY_MISSION',
                            'studyplan:daily:user1:2026-11-22', '2026-11-22T10:00:00Z', '2026-11-22T09:00:00Z')
                    """)).isInstanceOf(SQLException.class);

                assertThatThrownBy(() -> statement.execute("""
                    insert into lifecycle_messaging_cooldowns
                        (user_id, category, intent_type, last_idempotency_key, last_triggered_at, cooldown_until)
                    values ('00000000-0000-4000-8000-000000000691', 'PROMO_BLAST', 'STUDY_PLAN_DAILY_MISSION',
                            'studyplan:daily:user1:2026-11-22', '2026-11-22T10:00:00Z', '2026-11-23T06:00:00Z')
                    """)).isInstanceOf(SQLException.class);

                // Evaluation CHECK constraints: training_eligible=true, invalid decision, invalid source_module
                assertThatThrownBy(() -> statement.execute("""
                    insert into lifecycle_messaging_evaluations
                        (user_id, source_module, category, intent_type, idempotency_key, template_key,
                         decision, reason_code, correlation_id, training_eligible)
                    values ('00000000-0000-4000-8000-000000000691', 'studyplan', 'STUDY_PLAN',
                            'STUDY_PLAN_DAILY_MISSION', 'studyplan:daily:user1:2026-11-22',
                            'notification.template.study_plan.daily_mission', 'SCHEDULED', 'SCHEDULED', 'corr-1', true)
                    """)).isInstanceOf(SQLException.class);

                assertThatThrownBy(() -> statement.execute("""
                    insert into lifecycle_messaging_evaluations
                        (user_id, source_module, category, intent_type, idempotency_key, template_key,
                         decision, reason_code, correlation_id)
                    values ('00000000-0000-4000-8000-000000000691', 'studyplan', 'STUDY_PLAN',
                            'STUDY_PLAN_DAILY_MISSION', 'studyplan:daily:user1:2026-11-22',
                            'notification.template.study_plan.daily_mission', 'SPAM_BLAST', 'SPAM', 'corr-1')
                    """)).isInstanceOf(SQLException.class);

                assertThatThrownBy(() -> statement.execute("""
                    insert into lifecycle_messaging_evaluations
                        (user_id, source_module, category, intent_type, idempotency_key, template_key,
                         decision, reason_code, correlation_id)
                    values ('00000000-0000-4000-8000-000000000691', 'marketing', 'STUDY_PLAN',
                            'STUDY_PLAN_DAILY_MISSION', 'studyplan:daily:user1:2026-11-22',
                            'notification.template.study_plan.daily_mission', 'SCHEDULED', 'SCHEDULED', 'corr-1')
                    """)).isInstanceOf(SQLException.class);

                statement.execute("""
                    insert into lifecycle_messaging_cooldowns
                        (user_id, category, intent_type, last_idempotency_key, last_triggered_at, cooldown_until)
                    values
                        ('00000000-0000-4000-8000-000000000691', 'STUDY_PLAN', 'STUDY_PLAN_DAILY_MISSION',
                         'studyplan:daily:user1:2026-11-22', '2026-11-22T10:00:00Z', '2026-11-23T06:00:00Z'),
                        ('00000000-0000-4000-8000-000000000692', 'STUDY_PLAN', 'STUDY_PLAN_DAILY_MISSION',
                         'studyplan:daily:user2:2026-11-22', '2026-11-22T10:00:00Z', '2026-11-23T06:00:00Z')
                    """);

                statement.execute("""
                    insert into lifecycle_messaging_evaluations
                        (user_id, source_module, category, intent_type, idempotency_key, template_key,
                         decision, reason_code, correlation_id)
                    values
                        ('00000000-0000-4000-8000-000000000691', 'studyplan', 'STUDY_PLAN',
                         'STUDY_PLAN_DAILY_MISSION', 'studyplan:daily:user1:2026-11-22',
                         'notification.template.study_plan.daily_mission', 'SCHEDULED', 'SCHEDULED', 'corr-1'),
                        ('00000000-0000-4000-8000-000000000692', 'studyplan', 'STUDY_PLAN',
                         'STUDY_PLAN_DAILY_MISSION', 'studyplan:daily:user2:2026-11-22',
                         'notification.template.study_plan.daily_mission', 'SKIPPED_ALREADY_COMPLETED', 'DONE', 'corr-2')
                    """);

                // ON DELETE CASCADE deletes user 1 rows without affecting user 2
                statement.execute("delete from users where id = '00000000-0000-4000-8000-000000000691'");
                for (String table : new String[]{
                    "lifecycle_messaging_cooldowns",
                    "lifecycle_messaging_evaluations"
                }) {
                    try (var rs = statement.executeQuery(
                        "select count(*), min(user_id::text) from " + table)) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getInt(1)).isEqualTo(1);
                        assertThat(rs.getString(2)).isEqualTo("00000000-0000-4000-8000-000000000692");
                    }
                }
            }
        }
    }
}
