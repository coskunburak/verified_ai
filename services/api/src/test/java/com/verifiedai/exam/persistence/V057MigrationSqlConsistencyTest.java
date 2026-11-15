package com.verifiedai.exam.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class V057MigrationSqlConsistencyTest {

    @Test
    void migrationV057DeclaresTablesReconciledForeignKeysConstraintsAndIndexes() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/platform/V057__timed_mock_exam_runtime_and_scratchpad_ingestion.sql");
        if (!Files.exists(migration)) {
            migration = Path.of("services/api/src/main/resources/db/migration/platform/V057__timed_mock_exam_runtime_and_scratchpad_ingestion.sql");
        }
        String sql = Files.readString(migration, StandardCharsets.UTF_8);

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS exam_sessions"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS exam_session_items"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS exam_session_scratchpad_assets"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS exam_session_idempotency_records"));

        // Reconciled V056 foreign key table names
        assertTrue(sql.contains("REFERENCES exam_assembled_assessments(id)"));
        assertTrue(sql.contains("REFERENCES exam_assessment_blueprints(id)"));
        assertTrue(sql.contains("REFERENCES exam_question_bank_items(id)"));
        assertTrue(sql.contains("REFERENCES exam_sessions(id)"));
        assertTrue(sql.contains("REFERENCES exam_session_items(id)"));

        // Invariants & constraints
        assertTrue(sql.contains("CHECK (training_eligible = FALSE)"));
        assertTrue(sql.contains("CHECK (no_hint_policy_enforced = TRUE)"));
        assertTrue(sql.contains("CONSTRAINT chk_exam_sessions_deadlines"));
        assertTrue(sql.contains("CONSTRAINT chk_exam_sessions_item_counts"));
        assertTrue(sql.contains("UNIQUE (session_id, item_position)"));
        assertTrue(sql.contains("UNIQUE (session_id, question_item_id)"));
        assertTrue(sql.contains("UNIQUE (session_item_id, page_number)"));
        assertTrue(sql.contains("UNIQUE (session_id, operation_type, idempotency_key)"));

        // Required indexes
        assertTrue(sql.contains("idx_exam_sessions_user_status"));
        assertTrue(sql.contains("idx_exam_sessions_user_assembly"));
        assertTrue(sql.contains("idx_exam_sessions_active_user"));
        assertTrue(sql.contains("idx_exam_session_items_session_pos"));
        assertTrue(sql.contains("idx_exam_session_items_session_section"));
        assertTrue(sql.contains("idx_exam_scratchpad_session_item"));
        assertTrue(sql.contains("idx_exam_scratchpad_user"));
        assertTrue(sql.contains("idx_exam_session_idemp_lookup"));
    }
}
