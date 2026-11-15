package com.verifiedai.exam.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.verifiedai.exam.application.CandidateProfileService;
import com.verifiedai.exam.application.ExamAssessmentAssemblyService;
import com.verifiedai.exam.application.ExamAssessmentAssemblyService.AssembleCommand;
import com.verifiedai.exam.application.ExamAssessmentAssemblyService.AssembleResult;
import com.verifiedai.exam.domain.AssembledAssessment;
import com.verifiedai.exam.domain.AssembledAssessmentRepository;
import com.verifiedai.exam.domain.AssessmentBlueprint;
import com.verifiedai.exam.domain.AssessmentBlueprintRepository;
import com.verifiedai.exam.domain.AssessmentBlueprintStatus;
import com.verifiedai.exam.domain.CandidateProfileModels.AccessibilityAccommodations;
import com.verifiedai.exam.domain.CandidateProfileModels.CandidateProfileUpsertRequest;
import com.verifiedai.exam.domain.QuestionBankItem;
import com.verifiedai.exam.domain.QuestionBankRepository;
import com.verifiedai.exam.domain.QuestionItemStatus;
import com.verifiedai.exam.infrastructure.privacy.ExamPrivacyLifecycleContributor;
import com.verifiedai.integration.PostgresIntegrationTestSupport;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ExamMigrationV056IntegrationTest extends PostgresIntegrationTestSupport {

    private static final UUID EXAM_DEF_TYT_MATH = UUID.fromString("91000000-0000-4000-8000-000000000101");
    private static final UUID BP_TYT_MATH_DIAG = UUID.fromString("94000000-0000-4000-8000-000000000101");

    @Autowired
    private ExamAssessmentAssemblyService assemblyService;

    @Autowired
    private CandidateProfileService candidateProfileService;

    @Autowired
    private QuestionBankRepository questionBankRepository;

    @Autowired
    private AssessmentBlueprintRepository blueprintRepository;

    @Autowired
    private AssembledAssessmentRepository assembledAssessmentRepository;

    @Autowired
    private ExamPrivacyLifecycleContributor privacyContributor;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void v056SeededPoolDeterministicAssemblyConstraintsAndPrivacyIsolation() {
        // 1. Verify V056 seeded question bank items, options, provenance records, and active blueprints
        List<QuestionBankItem> publishedItems = questionBankRepository.findByExamDefinition(
                EXAM_DEF_TYT_MATH,
                1,
                QuestionItemStatus.PUBLISHED,
                null,
                null
        );
        assertTrue(publishedItems.size() >= 12);
        for (QuestionBankItem item : publishedItems) {
            assertFalse(item.trainingEligible());
            assertTrue(item.provenance().isSmeVerified());
            assertFalse(item.provenance().trainingEligible());
            assertEquals(5, item.options().size());
        }

        List<AssessmentBlueprint> blueprints = blueprintRepository.findByExamDefinition(
                EXAM_DEF_TYT_MATH,
                1,
                AssessmentBlueprintStatus.ACTIVE,
                null
        );
        assertTrue(blueprints.size() >= 2);
        for (AssessmentBlueprint bp : blueprints) {
            assertFalse(bp.trainingEligible());
            assertEquals(10000, bp.easyTargetBps() + bp.mediumTargetBps() + bp.hardTargetBps() + bp.expertTargetBps());
        }

        // 2. Create two users and configure candidate 1 with 1.50x accommodation multiplier via V055 service
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        for (UUID uid : List.of(userA, userB)) {
            jdbcClient.sql("""
                    INSERT INTO users (id, status, created_at, updated_at)
                    VALUES (:id, 'ACTIVE', NOW(), NOW())
                    ON CONFLICT (id) DO NOTHING
                    """)
                    .param("id", uid)
                    .update();
        }

        candidateProfileService.upsertProfile(
                userA,
                new CandidateProfileUpsertRequest(
                        "candidate-profile-request-v1",
                        "TR_YKS_TYT_MATH",
                        "2026.1",
                        new BigDecimal("35.00"),
                        LocalDate.now().plusDays(90),
                        60,
                        420,
                        new AccessibilityAccommodations(new BigDecimal("1.50"), true, true, true),
                        new BigDecimal("0.2500"),
                        "V056_PG_TEST",
                        "v056-profile-key-a"
                ),
                null
        );

        // 3. Assemble assessment for userA and verify idempotent replay + accommodation application
        AssembleResult firstRun = assemblyService.assembleAssessment(new AssembleCommand(
                EXAM_DEF_TYT_MATH,
                userA,
                userA,
                null,
                BP_TYT_MATH_DIAG,
                "seed-v056-pg-alpha",
                null,
                null,
                null,
                null
        ));
        assertTrue(firstRun.created());
        AssembledAssessment assembledA = firstRun.assessment();
        assertEquals(8, assembledA.items().size());
        assertEquals(900, assembledA.baseDurationSeconds());
        assertEquals(1350, assembledA.effectiveDurationSeconds());
        assertEquals(new BigDecimal("1.50"), assembledA.accommodationsSnapshot().timeMultiplier());
        assertFalse(assembledA.trainingEligible());

        AssembleResult replayRun = assemblyService.assembleAssessment(new AssembleCommand(
                EXAM_DEF_TYT_MATH,
                userA,
                userA,
                null,
                BP_TYT_MATH_DIAG,
                "seed-v056-pg-alpha",
                null,
                null,
                null,
                null
        ));
        assertFalse(replayRun.created());
        assertEquals(assembledA.id(), replayRun.assessment().id());
        assertEquals(assembledA.reproducibilityHash(), replayRun.assessment().reproducibilityHash());

        // Assemble for userB with the same seed and 1.50x override -> identical duration scaling & valid 64-char hash
        AssembleResult userBRun = assemblyService.assembleAssessment(new AssembleCommand(
                EXAM_DEF_TYT_MATH,
                userB,
                userB,
                null,
                BP_TYT_MATH_DIAG,
                "seed-v056-pg-alpha",
                new BigDecimal("1.50"),
                true,
                true,
                true
        ));
        assertTrue(userBRun.created());
        assertEquals(8, userBRun.assessment().items().size());
        assertEquals(1350, userBRun.assessment().effectiveDurationSeconds());
        assertNotNull(userBRun.assessment().reproducibilityHash());

        // 4. Verify DB CHECK constraints reject training_eligible = TRUE across all Sprint 9.3 tables
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbcClient.sql("UPDATE exam_question_bank_items SET training_eligible = TRUE WHERE question_code = 'TYT-MATH-2026-INT-001'")
                        .update()
        );
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbcClient.sql("UPDATE exam_question_provenance_records SET training_eligible = TRUE WHERE question_item_id = '93000000-0000-4000-8000-000000000101'")
                        .update()
        );
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbcClient.sql("UPDATE exam_assessment_blueprints SET training_eligible = TRUE WHERE id = :id")
                        .param("id", BP_TYT_MATH_DIAG)
                        .update()
        );
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbcClient.sql("UPDATE exam_assembled_assessments SET training_eligible = TRUE WHERE id = :id")
                        .param("id", assembledA.id())
                        .update()
        );
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbcClient.sql("UPDATE exam_assembled_assessment_items SET training_eligible = TRUE WHERE assembled_assessment_id = :id")
                        .param("id", assembledA.id())
                        .update()
        );

        // 5. Verify DB CHECK constraint rejects blueprint difficulty bps not summing to 10000
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbcClient.sql("UPDATE exam_assessment_blueprints SET easy_target_bps = 9999 WHERE id = :id")
                        .param("id", BP_TYT_MATH_DIAG)
                        .update()
        );

        // 6. Verify GDPR export and user-isolated deletion via ExamPrivacyLifecycleContributor
        Map<String, Object> exportedA = privacyContributor.exportUserData(userA);
        assertEquals(1, exportedA.get("assembledAssessmentCount"));
        assertFalse((Boolean) exportedA.get("trainingEligible"));

        privacyContributor.deleteUserData(userA, Instant.now());
        assertTrue(assembledAssessmentRepository.findByUserId(userA, 50).isEmpty());
        // UserB's assembled assessment and global question bank / blueprints remain intact
        assertEquals(1, assembledAssessmentRepository.findByUserId(userB, 50).size());
        assertTrue(questionBankRepository.findByExamDefinition(
                EXAM_DEF_TYT_MATH,
                1,
                QuestionItemStatus.PUBLISHED,
                null,
                null
        ).size() >= 12);
    }
}
