package com.verifiedai.exam.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.verifiedai.exam.application.ExamAssessmentAssemblyService;
import com.verifiedai.exam.application.ExamAssessmentAssemblyService.AssembleCommand;
import com.verifiedai.exam.application.ExamAssessmentAssemblyService.AssembleResult;
import com.verifiedai.exam.application.ExamRepository;
import com.verifiedai.exam.domain.AppliedAccommodationSnapshot;
import com.verifiedai.exam.domain.AssembledAssessment;
import com.verifiedai.exam.domain.AssembledAssessmentItem;
import com.verifiedai.exam.domain.AssessmentBlueprint;
import com.verifiedai.exam.domain.AssessmentBlueprintRepository;
import com.verifiedai.exam.domain.AssessmentBlueprintStatus;
import com.verifiedai.exam.domain.DeterministicAssessmentAssemblyEngine;
import com.verifiedai.exam.domain.DeterministicAssessmentAssemblyEngine.AssemblyInput;
import com.verifiedai.exam.domain.ExamBlueprintValidator;
import com.verifiedai.exam.domain.ExamModels.ExamDefinition;
import com.verifiedai.exam.domain.ExamModels.ExamStatus;
import com.verifiedai.exam.domain.QuestionBankItem;
import com.verifiedai.exam.domain.QuestionBankRepository;
import com.verifiedai.exam.domain.QuestionItemStatus;
import com.verifiedai.integration.PostgresIntegrationTestSupport;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * Full PostgreSQL + Flyway V001..V060 integration suite for Sprint 9.6B:
 * verifies 2026.2 curriculum activation, >= 120 Turkish TYT/AYT items,
 * deterministic assembly across all 5 V2 blueprints (including 40Q full TYT/AYT mocks),
 * and backward compatibility of V056 V1 blueprints.
 */
@SpringBootTest
@ActiveProfiles("test")
class Sprint96BYksQuestionBankIntegrationTest extends PostgresIntegrationTestSupport {

    private static final UUID DEF_TYT_2026_2 = UUID.fromString("91000000-0000-4000-8000-000000000201");
    private static final UUID DEF_AYT_2026_2 = UUID.fromString("91000000-0000-4000-8000-000000000203");
    private static final UUID DEF_TYT_2026_1 = UUID.fromString("91000000-0000-4000-8000-000000000101");

    private static final UUID BP_TYT_WORD_PROBLEMS_V2 = UUID.fromString("94000000-0000-0000-0000-000000000201");
    private static final UUID BP_TYT_FULL_SIM_V2 = UUID.fromString("94000000-0000-0000-0000-000000000202");
    private static final UUID BP_AYT_DIAG_V2 = UUID.fromString("94000000-0000-0000-0000-000000000203");
    private static final UUID BP_AYT_LTI_DRILL_V2 = UUID.fromString("94000000-0000-0000-0000-000000000204");
    private static final UUID BP_AYT_FULL_SIM_V2 = UUID.fromString("94000000-0000-0000-0000-000000000205");
    private static final UUID BP_TYT_DIAG_V1 = UUID.fromString("94000000-0000-4000-8000-000000000101");

    @Autowired private ExamRepository examRepository;
    @Autowired private ExamBlueprintValidator blueprintValidator;
    @Autowired private QuestionBankRepository questionBankRepository;
    @Autowired private AssessmentBlueprintRepository blueprintRepository;
    @Autowired private ExamAssessmentAssemblyService assemblyService;
    @Autowired private DeterministicAssessmentAssemblyEngine assemblyEngine;
    @Autowired private JdbcClient jdbcClient;

    @Test
    void v060TurkishPoolAndAllFiveV2BlueprintsAssembleDeterministicallyOnPostgres() {
        // 1. Verify 2026.1 -> DEPRECATED and 2026.2 -> ACTIVE with 40.0000 weight conservation
        ExamDefinition tytV1 = examRepository.findByCodeAndVersion("TR_YKS_TYT_MATH", "2026.1").orElseThrow();
        ExamDefinition aytV1 = examRepository.findByCodeAndVersion("TR_YKS_AYT_MATH", "2026.1").orElseThrow();
        assertEquals(ExamStatus.DEPRECATED, tytV1.status());
        assertEquals("2026.2", tytV1.supersededByVersion());
        assertEquals(ExamStatus.DEPRECATED, aytV1.status());
        assertEquals("2026.2", aytV1.supersededByVersion());

        ExamDefinition tytV2 = examRepository.findByCodeAndVersion("TR_YKS_TYT_MATH", "2026.2").orElseThrow();
        ExamDefinition aytV2 = examRepository.findByCodeAndVersion("TR_YKS_AYT_MATH", "2026.2").orElseThrow();
        assertEquals(ExamStatus.ACTIVE, tytV2.status());
        assertEquals(ExamStatus.ACTIVE, aytV2.status());
        blueprintValidator.validateBlueprint(tytV2);
        blueprintValidator.validateBlueprint(aytV2);

        // 2. Verify >= 60 TYT & >= 60 AYT published Turkish questions with 5 options & ADR-006
        List<QuestionBankItem> tytItems = questionBankRepository.findByExamDefinition(DEF_TYT_2026_2, 2, QuestionItemStatus.PUBLISHED, null, null);
        List<QuestionBankItem> aytItems = questionBankRepository.findByExamDefinition(DEF_AYT_2026_2, 2, QuestionItemStatus.PUBLISHED, null, null);
        assertTrue(tytItems.size() >= 60, "Expected >= 60 TYT items, got " + tytItems.size());
        assertTrue(aytItems.size() >= 60, "Expected >= 60 AYT items, got " + aytItems.size());

        Integer missingMistakeCodes = jdbcClient.sql("""
                SELECT COUNT(*) FROM exam_question_options o
                JOIN exam_question_bank_items i ON i.id = o.question_item_id
                WHERE i.exam_definition_id IN (:tytId, :aytId)
                  AND o.is_correct = FALSE
                  AND (o.distractor_mistake_code IS NULL OR btrim(o.distractor_mistake_code) = '')
                """)
                .param("tytId", DEF_TYT_2026_2)
                .param("aytId", DEF_AYT_2026_2)
                .query(Integer.class)
                .single();
        assertEquals(0, missingMistakeCodes);

        // 3. Verify all 5 V2 blueprints exist and are ACTIVE
        List<AssessmentBlueprint> tytBps = blueprintRepository.findByExamDefinition(DEF_TYT_2026_2, 2, AssessmentBlueprintStatus.ACTIVE, null);
        List<AssessmentBlueprint> aytBps = blueprintRepository.findByExamDefinition(DEF_AYT_2026_2, 2, AssessmentBlueprintStatus.ACTIVE, null);
        assertEquals(2, tytBps.size());
        assertEquals(3, aytBps.size());

        // 4. Deterministic Assembly across all 5 V2 blueprints + V056 V1 regression
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        for (UUID uid : List.of(userA, userB)) {
            jdbcClient.sql("INSERT INTO users (id, status, created_at, updated_at) VALUES (:id, 'ACTIVE', NOW(), NOW()) ON CONFLICT (id) DO NOTHING")
                    .param("id", uid)
                    .update();
        }

        Map<UUID, Integer> expectedCounts = new LinkedHashMap<>();
        expectedCounts.put(BP_TYT_WORD_PROBLEMS_V2, 12);
        expectedCounts.put(BP_TYT_FULL_SIM_V2, 40);
        expectedCounts.put(BP_AYT_DIAG_V2, 16);
        expectedCounts.put(BP_AYT_LTI_DRILL_V2, 12);
        expectedCounts.put(BP_AYT_FULL_SIM_V2, 40);

        Instant deterministicNow = Instant.parse("2026-11-16T12:00:00Z");

        for (Map.Entry<UUID, Integer> entry : expectedCounts.entrySet()) {
            UUID bpId = entry.getKey();
            int expectedQ = entry.getValue();
            boolean isTyt = bpId.equals(BP_TYT_WORD_PROBLEMS_V2) || bpId.equals(BP_TYT_FULL_SIM_V2);
            UUID defId = isTyt ? DEF_TYT_2026_2 : DEF_AYT_2026_2;
            List<QuestionBankItem> pool = isTyt ? tytItems : aytItems;
            AssessmentBlueprint blueprint = blueprintRepository.findById(bpId).orElseThrow();

            AssembleResult runA1 = assemblyService.assembleAssessment(new AssembleCommand(
                    defId, userA, userA, null, bpId, "sprint-9-6b-pg-seed-01",
                    null, null, null, null
            ));
            assertTrue(runA1.created());
            AssembledAssessment a1 = runA1.assessment();
            assertEquals(expectedQ, a1.items().size(), "Blueprint " + bpId + " question count mismatch");
            assertFalse(a1.trainingEligible());
            assertNotNull(a1.reproducibilityHash());
            assertEquals(64, a1.reproducibilityHash().length());

            // Idempotent replay returns same ID, hash, and exact item sequence
            AssembleResult runA2 = assemblyService.assembleAssessment(new AssembleCommand(
                    defId, userA, userA, null, bpId, "sprint-9-6b-pg-seed-01",
                    null, null, null, null
            ));
            assertFalse(runA2.created());
            assertEquals(a1.id(), runA2.assessment().id());
            assertEquals(a1.reproducibilityHash(), runA2.assessment().reproducibilityHash());
            assertEquals(
                    a1.items().stream().map(AssembledAssessmentItem::questionItemId).toList(),
                    runA2.assessment().items().stream().map(AssembledAssessmentItem::questionItemId).toList(),
                    "Idempotent replay sequence mismatch for " + bpId
            );

            // Engine-level same-user deterministic reproducibility on Postgres-loaded blueprint & pool
            AssembledAssessment detRun1 = assemblyEngine.assemble(new AssemblyInput(
                    UUID.fromString("95000000-0000-4000-8000-000000000001"),
                    userA, null, blueprint, pool, Set.of(), "sprint-9-6b-pg-engine-seed",
                    AppliedAccommodationSnapshot.standardDefaults(), deterministicNow
            ));
            AssembledAssessment detRun2 = assemblyEngine.assemble(new AssemblyInput(
                    UUID.fromString("95000000-0000-4000-8000-000000000001"),
                    userA, null, blueprint, pool, Set.of(), "sprint-9-6b-pg-engine-seed",
                    AppliedAccommodationSnapshot.standardDefaults(), deterministicNow
            ));
            assertEquals(detRun1.reproducibilityHash(), detRun2.reproducibilityHash());
            assertEquals(
                    detRun1.items().stream().map(AssembledAssessmentItem::questionItemId).toList(),
                    detRun2.items().stream().map(AssembledAssessmentItem::questionItemId).toList(),
                    "Same-user engine deterministic sequence mismatch for " + bpId
            );

            // Second user with identical seed builds an isolated valid assessment (userId participates in tie-break hash)
            AssembleResult runB = assemblyService.assembleAssessment(new AssembleCommand(
                    defId, userB, userB, null, bpId, "sprint-9-6b-pg-seed-01",
                    null, null, null, null
            ));
            assertTrue(runB.created());
            assertEquals(expectedQ, runB.assessment().items().size(), "Blueprint " + bpId + " userB count mismatch");
            assertFalse(runB.assessment().trainingEligible());
            assertNotNull(runB.assessment().reproducibilityHash());
            assertEquals(64, runB.assessment().reproducibilityHash().length());
        }

        // 5. Backward compatibility regression: V056 BP-TYT-MATH-DIAG-2026-V1 still assembles 8 items
        AssembleResult v1Run = assemblyService.assembleAssessment(new AssembleCommand(
                DEF_TYT_2026_1, userA, userA, null, BP_TYT_DIAG_V1, "legacy-v056-seed",
                null, null, null, null
        ));
        assertTrue(v1Run.created());
        assertEquals(8, v1Run.assessment().items().size());
    }
}
