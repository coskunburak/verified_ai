package com.verifiedai.exam.persistence;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class V061MigrationSqlConsistencyTest {
    @Test void migrationPreservesPrivacyAndReferencesExistingEvaluationPrimaryKey() throws Exception {
        try (var in=getClass().getResourceAsStream("/db/migration/platform/V061__sprint_9_7_post_assessment_attribution.sql")) {
            assertNotNull(in);
            String sql=new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(sql.contains("REFERENCES exam_session_item_evaluations(evaluation_id) ON DELETE CASCADE"));
            assertTrue(sql.contains("UNIQUE (item_evaluation_id)"));
            assertTrue(sql.contains("UNIQUE (source_session_id, idempotency_key)"));
            assertEquals(2,sql.split("CHECK \\(training_eligible = FALSE\\)",-1).length-1);
            assertEquals(2,sql.split("CHECK \\(llm_tokens_used = 0\\)",-1).length-1);
            for (String index : new String[]{"idx_exam_attr_session_id","idx_exam_attr_user_skill","idx_exam_attr_user_category","idx_exam_realloc_user_created","idx_exam_realloc_source_session"})
                assertTrue(sql.contains("CREATE INDEX " + index));
            for (String category : new String[]{"CONCEPTUAL_GAP","RECURRING_MISTAKE_TRAP","TIME_PRESSURE_SLIP","STRATEGIC_OMISSION"})
                assertTrue(sql.contains("'"+category+"'"));
            assertFalse(sql.contains("DEFAULT 'DETERMINISTIC_RULE'"),"Default must satisfy the evidence constraint");
        }
    }
}
