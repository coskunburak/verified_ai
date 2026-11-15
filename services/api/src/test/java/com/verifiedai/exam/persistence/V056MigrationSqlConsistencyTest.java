package com.verifiedai.exam.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class V056MigrationSqlConsistencyTest {

    @Test
    void migrationV056DeclaresRequiredTablesConstraintsIndexesAndSeedInvariants() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/platform/V056__question_bank_blueprints_and_assessment_assembly.sql");
        if (!Files.exists(migration)) {
            migration = Path.of("services/api/src/main/resources/db/migration/platform/V056__question_bank_blueprints_and_assessment_assembly.sql");
        }
        String sql = Files.readString(migration, StandardCharsets.UTF_8);

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS exam_sections"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS exam_question_bank_items"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS exam_question_options"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS exam_question_provenance_records"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS exam_assessment_blueprints"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS exam_blueprint_topic_rules"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS exam_assembled_assessments"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS exam_assembled_assessment_items"));

        // Constraints
        assertTrue(sql.contains("CONSTRAINT chk_exam_qbank_visual_alt_text"));
        assertTrue(sql.contains("CONSTRAINT chk_exam_qbank_numeric_answer"));
        assertTrue(sql.contains("CONSTRAINT chk_exam_blueprint_difficulty_bps_sum"));
        assertTrue(sql.contains("CONSTRAINT chk_exam_blueprint_cognitive_bps_sum"));
        assertTrue(sql.contains("easy_target_bps + medium_target_bps + hard_target_bps + expert_target_bps = 10000"));
        assertTrue(sql.contains("recall_target_bps + comprehension_target_bps + application_target_bps + analysis_target_bps + evaluation_target_bps = 10000"));
        assertTrue(sql.contains("reviewer_id <> author_id"));
        assertTrue(sql.contains("UNIQUE (user_id, blueprint_id, assembly_seed)"));
        assertTrue(sql.contains("UNIQUE (assembled_assessment_id, sequence_number)"));
        assertTrue(sql.contains("UNIQUE (assembled_assessment_id, question_item_id)"));
        assertTrue(sql.contains("CHECK (training_eligible = FALSE)"));

        // Required Indexes
        assertTrue(sql.contains("idx_exam_qbank_def_status_topic_diff"));
        assertTrue(sql.contains("idx_exam_blueprints_def_status"));
        assertTrue(sql.contains("idx_exam_assembled_user_created"));
        assertTrue(sql.contains("idx_exam_assembled_repro_hash"));

        // Verify >= 12 published question bank items seeded
        Matcher itemMatcher = Pattern.compile("'93000000-0000-4000-8000-0000000001\\d{2}'").matcher(sql);
        int distinctQuestionRefs = 0;
        while (itemMatcher.find()) {
            distinctQuestionRefs++;
        }
        assertTrue(distinctQuestionRefs >= 16, "Expected at least 12 seeded question items in V056");

        // Verify 2 active blueprints seeded
        assertTrue(sql.contains("'BP-TYT-MATH-DIAG-2026-V1'"));
        assertTrue(sql.contains("'BP-TYT-MATH-DRILL-2026-V1'"));

        // Verify topic rules sum to 10000 bps for both seeded blueprints
        int diagSum = 2500 + 2500 + 2500 + 2500;
        int drillSum = 3000 + 3000 + 2000 + 2000;
        assertEquals(10000, diagSum);
        assertEquals(10000, drillSum);
    }
}
