package com.verifiedai.exam.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Category B structural consistency tests for Flyway migration
 * {@code V059__sprint_9_6_exam_readiness_projections.sql}.
 */
class V059MigrationSqlConsistencyTest {

    @Test
    void migrationDeclaresReadinessTablesCheckConstraintsAndIndexes() throws IOException {
        String sql = loadMigrationSql();

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS exam_readiness_projections"),
                "V059 must define exam_readiness_projections");
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS exam_readiness_topic_allocations"),
                "V059 must define exam_readiness_topic_allocations");

        assertTrue(sql.contains("REFERENCES users(id) ON DELETE CASCADE"));
        assertTrue(sql.contains("REFERENCES student_exam_targets(target_id) ON DELETE SET NULL"));
        assertTrue(sql.contains("REFERENCES exam_sessions(id) ON DELETE SET NULL"));
        assertTrue(sql.contains("REFERENCES exam_readiness_projections(projection_id) ON DELETE CASCADE ON UPDATE CASCADE"));

        assertTrue(sql.contains("credible_interval_level = 0.90"));
        assertTrue(sql.contains("training_eligible = FALSE"));
        assertTrue(sql.contains("llm_tokens_used = 0"));
        assertTrue(sql.contains("inference_cost_usd = 0.000000"));
        assertTrue(sql.contains("chk_exam_readiness_net_interval_order"));
        assertTrue(sql.contains("chk_exam_readiness_net_bounds"));
        assertTrue(sql.contains("chk_exam_readiness_preliminary_guard"));
        assertTrue(sql.contains("uq_exam_readiness_user_exam"));
        assertTrue(sql.contains("uq_exam_readiness_allocation_rank"));
        assertTrue(sql.contains("idx_exam_readiness_user_computed"));
        assertTrue(sql.contains("idx_exam_readiness_allocations_user"));
    }

    private static String loadMigrationSql() throws IOException {
        try (InputStream in = V059MigrationSqlConsistencyTest.class.getResourceAsStream(
                "/db/migration/platform/V059__sprint_9_6_exam_readiness_projections.sql")) {
            if (in != null) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        Path localPath = Path.of("src/main/resources/db/migration/platform/V059__sprint_9_6_exam_readiness_projections.sql");
        if (Files.exists(localPath)) {
            return Files.readString(localPath, StandardCharsets.UTF_8);
        }
        Path rootPath = Path.of("services/api/src/main/resources/db/migration/platform/V059__sprint_9_6_exam_readiness_projections.sql");
        return Files.readString(rootPath, StandardCharsets.UTF_8);
    }
}
