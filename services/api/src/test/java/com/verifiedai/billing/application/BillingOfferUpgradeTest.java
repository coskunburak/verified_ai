package com.verifiedai.billing.application;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.assertThat;

class BillingOfferUpgradeTest {
    @Test void upgradePreservesLegacyCoachAndExistingReceipt() throws Exception {
        try (var db = new PostgreSQLContainer<>("postgres:16-alpine")) {
            db.start();
            Flyway.configure().dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                .locations("classpath:db/migration/platform").target("64").load().migrate();
            try (var connection = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
                 var statement = connection.createStatement()) {
                statement.execute("""
                    insert into users (id,status,created_at,updated_at)
                    values ('00000000-0000-4000-8000-000000000101','ACTIVE',now(),now())
                    """);
                statement.execute("""
                    insert into entitlements(id,user_id,tier,source,status,effective_at,created_at,updated_at)
                    select gen_random_uuid(),id,'PRO_PLUS','APP_STORE_SUBSCRIPTION','ACTIVE',now(),now(),now() from users
                    where id='00000000-0000-4000-8000-000000000101'
                    """);
                statement.execute("""
                    insert into app_store_transactions(id,user_id,transaction_id,original_transaction_id,product_id,
                        environment,payload_digest,created_at,updated_at)
                    values(gen_random_uuid(),'00000000-0000-4000-8000-000000000101','local_dev_receipt',
                        'local_dev_original','local_dev_legacy','XCODE','local_dev_digest',now(),now())
                    """);
                var upgrade = Flyway.configure().dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                    .locations("classpath:db/migration/platform").load();
                assertThat(upgrade.migrate().migrationsExecuted).isPositive();
                assertThat(upgrade.migrate().migrationsExecuted).isZero();
                try (var rows = statement.executeQuery("""
                    select e.tier,t.product_id,t.offer_family from entitlements e join app_store_transactions t using(user_id)
                    where t.transaction_id='local_dev_receipt'
                    """)) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo("PRO_PLUS");
                    assertThat(rows.getString(2)).isEqualTo("local_dev_legacy");
                    assertThat(rows.getString(3)).isNull();
                }
            }
        }
    }
}
