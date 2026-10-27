package com.verifiedai.integration.solving;

import com.verifiedai.integration.PostgresIntegrationTestSupport;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

class CanonicalMigrationIntegrationTest extends PostgresIntegrationTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Test void additiveV020UpgradesAnActualV019Schema() {
        String schema="canonical_upgrade_"+UUID.randomUUID().toString().replace("-","");
        try {
            var before=Flyway.configure().dataSource(jdbc.getDataSource())
                .locations("classpath:db/migration/platform").schemas(schema).defaultSchema(schema).target("19").load();
            before.migrate();
            assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_schema=? and table_name='solution_candidates'",Integer.class,schema)).isEqualTo(1);
            var upgrade=Flyway.configure().dataSource(jdbc.getDataSource())
                .locations("classpath:db/migration/platform").schemas(schema).defaultSchema(schema).target("20").load();
            assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(jdbc.queryForList("select table_name from information_schema.tables where table_schema=?",String.class,schema))
                .contains("canonical_solutions","canonical_solution_steps","canonical_solution_dependencies","canonical_materialization_failures","solution_candidates","secondary_solution_evidence");
            assertThat(jdbc.queryForList("select indexname from pg_indexes where schemaname=?",String.class,schema))
                .contains("uq_canonical_origin_version","ix_canonical_selected_source","ix_canonical_dependency_target");
            assertThat(upgrade.migrate().migrationsExecuted).isZero();
        } finally { jdbc.execute("drop schema if exists "+schema+" cascade"); } // Only this test's isolated schema.
    }
}
