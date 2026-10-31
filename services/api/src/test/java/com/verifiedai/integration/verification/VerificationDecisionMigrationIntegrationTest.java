package com.verifiedai.integration.verification;

import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.solving.SolverSourceFixture;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.assertj.core.api.Assertions.*;

class VerificationDecisionMigrationIntegrationTest extends PostgresIntegrationTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Test void additiveV023PreservesV022HistoricalRowsAndExistingTriggers() throws Exception {
        String schema="decision_upgrade_"+UUID.randomUUID().toString().replace("-","");
        try {
            Flyway.configure().dataSource(jdbc.getDataSource()).locations("classpath:db/migration/platform")
                .schemas(schema).defaultSchema(schema).target("22").load().migrate();
            UUID user,session;
            String before;
            try (var connection=jdbc.getDataSource().getConnection()) {
                connection.setSchema(schema);
                var scoped=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
                var fixture=new SolverSourceFixture(scoped); user=fixture.insertUser();
                session=fixture.insertSupportedParse(user,SolverSourceFixture.equationParseJson("x + 1 = 2"),1);
                before=scoped.queryForObject("select normalized_problem_jsonb::text from problem_parses where problem_session_id=?",String.class,session);
                connection.setSchema("public");
            }
            var upgrade=Flyway.configure().dataSource(jdbc.getDataSource()).locations("classpath:db/migration/platform")
                .schemas(schema).defaultSchema(schema).target("23").load();
            assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(jdbc.queryForObject("select normalized_problem_jsonb::text from "+schema+".problem_parses where problem_session_id=?",String.class,session)).isEqualTo(before);
            assertThat(jdbc.queryForList("select trigger_name from information_schema.triggers where trigger_schema=?",String.class,schema))
                .contains("solution_candidate_immutable","verification_run_immutable","canonical_solution_immutable","verification_decision_immutable","verification_decision_lineage");
            assertThat(upgrade.migrate().migrationsExecuted).isZero();
        } finally { jdbc.execute("drop schema if exists "+schema+" cascade"); }
    }
}
