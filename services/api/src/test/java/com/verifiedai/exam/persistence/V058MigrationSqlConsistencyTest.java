package com.verifiedai.exam.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class V058MigrationSqlConsistencyTest {

    @Test
    void migrationV058DeclaresRequiredTablesConstraintsAndIndexes() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/platform/V058__create_exam_scoring_and_evidence_tables.sql");
        if (!Files.exists(migration)) {
            migration = Path.of("services/api/src/main/resources/db/migration/platform/V058__create_exam_scoring_and_evidence_tables.sql");
        }
        String sql = Files.readString(migration, StandardCharsets.UTF_8);

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS exam_session_scores"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS exam_session_item_evaluations"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS exam_score_regrade_audits"));

        assertTrue(sql.contains("CHECK (training_eligible = FALSE)"));
        assertTrue(sql.contains("CHECK (llm_tokens_used = 0)"));
        assertTrue(sql.contains("CHECK (inference_cost_usd = 0.000000)"));

        assertTrue(sql.contains("idx_exam_session_scores_user_scored_at"));
        assertTrue(sql.contains("idx_exam_session_scores_exam_def"));
        assertTrue(sql.contains("idx_exam_item_evals_session_pos"));
        assertTrue(sql.contains("idx_exam_item_evals_user_topic"));
        assertTrue(sql.contains("idx_exam_regrade_audits_session"));
    }
}
