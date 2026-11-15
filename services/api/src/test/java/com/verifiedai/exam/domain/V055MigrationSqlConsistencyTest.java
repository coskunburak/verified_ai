package com.verifiedai.exam.domain;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class V055MigrationSqlConsistencyTest {

    @Test
    void migrationV055DeclaresRequiredTablesConstraintsAndReconciledForeignKeys() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/platform/V055__candidate_profiles_accommodations_and_syllabus_ingestion.sql");
        if (!Files.exists(migration)) {
            migration = Path.of("services/api/src/main/resources/db/migration/platform/V055__candidate_profiles_accommodations_and_syllabus_ingestion.sql");
        }
        String sql = Files.readString(migration, StandardCharsets.UTF_8);

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS candidate_profiles"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS candidate_profile_horizon_history"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS candidate_syllabus_uploads"));
        assertTrue(sql.contains("REFERENCES users(id) ON DELETE CASCADE"));
        assertTrue(sql.contains("REFERENCES exam_definitions(id)"));
        assertTrue(sql.contains("REFERENCES exam_definitions (exam_code, exam_version)"));
        assertTrue(sql.contains("REFERENCES student_exam_targets(target_id)"));
        assertTrue(sql.contains("daily_study_minutes BETWEEN 15 AND 960"));
        assertTrue(sql.contains("weekly_study_minutes BETWEEN 15 AND 6720"));
        assertTrue(sql.contains("extra_time_multiplier IN (1.00, 1.25, 1.50, 2.00)"));
        assertTrue(sql.contains("CHECK (training_eligible = FALSE)"));
        assertTrue(sql.contains("byte_size > 0 AND byte_size <= 10485760"));
        assertTrue(sql.contains("('OPTIMAL', 'STRETCH', 'AT_RISK', 'UNREALISTIC')"));
        assertTrue(sql.contains("('PARSED_AUTO_CONFIRMED', 'NEEDS_DISAMBIGUATION', 'USER_CONFIRMED', 'REJECTED')"));
    }
}
