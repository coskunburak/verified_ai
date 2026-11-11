package com.verifiedai.mastery;

import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.solving.SolverSourceFixture;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.assertj.core.api.Assertions.*;

class MasteryMigrationIntegrationTest extends PostgresIntegrationTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Test void v037UpgradePreservesLegacyScoreWithoutFabricatingIndependentEvidence() throws Exception {
        String schema="mastery_upgrade_"+UUID.randomUUID().toString().replace("-","");
        try {
            Flyway.configure().dataSource(jdbc.getDataSource()).locations("classpath:db/migration/platform")
                .schemas(schema).defaultSchema(schema).target("37").load().migrate();
            UUID owner;
            try(var connection=jdbc.getDataSource().getConnection()) {
                connection.setSchema(schema);
                var isolated=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
                owner=new SolverSourceFixture(isolated).insertUser();
                isolated.update("""
                    insert into learner_skill_mastery(user_id,skill_id,mastery_version,score,half_life_days,evidence_count,confidence,last_assessed_at)
                    values(?,'attempt-skill-v1:SYNTHETIC','bkt-lite-v1',.91,8,12,.7,'2026-01-01T00:00:00Z')
                    """,owner);
                connection.setSchema("public");
            }
            var upgrade=Flyway.configure().dataSource(jdbc.getDataSource()).locations("classpath:db/migration/platform")
                .schemas(schema).defaultSchema(schema).target("38").load();
            assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(jdbc.queryForObject("select score from "+schema+".learner_skill_mastery where user_id=?",Double.class,owner)).isEqualTo(.91);
            assertThat(jdbc.queryForObject("select independent_evidence_count from "+schema+".learner_skill_mastery where user_id=?",Integer.class,owner)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from "+schema+".attempt_mastery_context",Integer.class)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from "+schema+".learner_skill_mastery_transitions",Integer.class)).isZero();
            assertThat(upgrade.migrate().migrationsExecuted).isZero();
            var temporal=Flyway.configure().dataSource(jdbc.getDataSource()).locations("classpath:db/migration/platform")
                .schemas(schema).defaultSchema(schema).target("39").load();
            assertThat(temporal.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from "+schema+".learner_mistake_retention_transitions",Integer.class)).isZero();
            assertThat(jdbc.queryForObject("select score from "+schema+".learner_skill_mastery where user_id=?",Double.class,owner)).isEqualTo(.91);
            assertThat(temporal.migrate().migrationsExecuted).isZero();
        } finally { jdbc.execute("drop schema if exists "+schema+" cascade"); }
    }
}
