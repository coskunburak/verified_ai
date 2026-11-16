package com.verifiedai.exam.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.verifiedai.exam.application.ExamAssessmentAssemblyService;
import com.verifiedai.exam.application.ExamAssessmentAssemblyService.AssembleCommand;
import com.verifiedai.exam.application.ExamScoringEngineService;
import com.verifiedai.exam.application.ExamScoringEngineService.RegradeResult;
import com.verifiedai.exam.application.ExamScoringEngineService.ScoreSummaryResult;
import com.verifiedai.exam.application.ExamSessionRuntimeService;
import com.verifiedai.exam.application.ExamSessionRuntimeService.AutosaveItemCommand;
import com.verifiedai.exam.application.ExamSessionRuntimeService.StartSessionCommand;
import com.verifiedai.exam.application.ExamSessionRuntimeService.SubmitSessionCommand;
import com.verifiedai.exam.domain.AssembledAssessment;
import com.verifiedai.exam.domain.AssembledAssessmentRepository;
import com.verifiedai.exam.domain.ExamScoreRegradeAudit;
import com.verifiedai.exam.domain.ExamSession;
import com.verifiedai.exam.domain.ExamSessionScoreSnapshot;
import com.verifiedai.exam.domain.ExamSessionStatus;
import com.verifiedai.exam.domain.QuestionBankItem;
import com.verifiedai.exam.domain.QuestionBankRepository;
import com.verifiedai.exam.domain.QuestionOption;
import com.verifiedai.exam.domain.ScoreRegradeReasonCode;
import com.verifiedai.exam.infrastructure.persistence.ExamScoringRepository;
import com.verifiedai.exam.infrastructure.persistence.ExamSessionRepository;
import com.verifiedai.integration.PostgresIntegrationTestSupport;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ExamScoringMigrationAndRepositoryTest extends PostgresIntegrationTestSupport {

    private static final UUID EXAM_DEF_TYT_MATH = UUID.fromString("91000000-0000-4000-8000-000000000101");
    private static final UUID BP_TYT_MATH_DIAG = UUID.fromString("94000000-0000-4000-8000-000000000101");

    @Autowired
    private ExamAssessmentAssemblyService assemblyService;

    @Autowired
    private ExamSessionRuntimeService runtimeService;

    @Autowired
    private ExamScoringEngineService scoringService;

    @Autowired
    private ExamScoringRepository scoringRepository;

    @Autowired
    private ExamSessionRepository sessionRepository;

    @Autowired
    private QuestionBankRepository questionBankRepository;

    @Autowired
    private AssembledAssessmentRepository assembledAssessmentRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void persistsIdempotentDeterministicScoresRegradeAuditAndChildFirstDeletionInPostgres() {
        UUID userId = UUID.randomUUID();
        jdbcClient.sql("""
                INSERT INTO users (id, status, created_at, updated_at)
                VALUES (:id, 'ACTIVE', NOW(), NOW())
                ON CONFLICT (id) DO NOTHING
                """)
                .param("id", userId)
                .update();

        AssembledAssessment assembled = assemblyService.assembleAssessment(new AssembleCommand(
                EXAM_DEF_TYT_MATH,
                userId,
                userId,
                null,
                BP_TYT_MATH_DIAG,
                "seed-v058-pg-" + userId,
                null,
                null,
                null,
                null
        )).assessment();

        ExamSession session = runtimeService.startOrResumeSession(userId, new StartSessionCommand(
                userId, assembled.id(), 120, Instant.now(), false
        )).session();

        // Answer item 1 cleanly
        QuestionBankItem q1 = questionBankRepository.findById(session.items().getFirst().questionItemId()).orElseThrow();
        String chosenKey = q1.options().stream()
                .filter(opt -> !opt.isCorrect())
                .map(QuestionOption::optionKey)
                .findFirst()
                .orElse("A");
        String trueCorrectKey = q1.options().stream()
                .filter(QuestionOption::isCorrect)
                .map(QuestionOption::optionKey)
                .findFirst()
                .orElse("B");

        runtimeService.autosaveItem(
                userId,
                session.id(),
                1,
                "v058-auto-1",
                new AutosaveItemCommand(
                        "SELECT_OPTION", chosenKey, null, false, List.of(),
                        15, 0, 1L, false, Instant.now(), false
                )
        );

        runtimeService.submitSession(
                userId,
                session.id(),
                "v058-submit-1",
                new SubmitSessionCommand("USER_INITIATED", Instant.now(), false)
        );

        // Compute or get score summary idempotently
        ScoreSummaryResult first = scoringService.computeOrGetScoreSummary(userId, session.id());
        ScoreSummaryResult replay = scoringService.computeOrGetScoreSummary(userId, session.id());
        assertEquals(first.snapshot().scoreId(), replay.snapshot().scoreId());
        assertEquals(1, replay.snapshot().scoreVersion());
        assertFalse(replay.snapshot().trainingEligible());
        assertEquals(0, replay.snapshot().llmTokensUsed());

        ExamSessionScoreSnapshot loaded = scoringRepository.findScoreSnapshotBySessionId(session.id()).orElseThrow();
        assertEquals(session.items().size(), loaded.itemEvaluations().size());
        assertEquals(1, loaded.wrongCount());

        // Trigger regrade after updating option key on Q1 to chosenKey via JDBC so outcome flips WRONG -> CORRECT
        jdbcClient.sql("""
                UPDATE exam_question_options
                SET is_correct = CASE WHEN option_key = :chosen THEN TRUE ELSE FALSE END
                WHERE question_item_id = :qid
                """)
                .param("chosen", chosenKey)
                .param("qid", q1.id())
                .update();

        try {
            RegradeResult regraded = scoringService.regradeSession(
                    userId,
                    session.id(),
                    ScoreRegradeReasonCode.ANSWER_KEY_CORRECTION,
                    "Verified key adjustment",
                    "pg-integration-test"
            );
            assertEquals(2, regraded.snapshot().scoreVersion());
            assertTrue(regraded.snapshot().netScore().compareTo(first.snapshot().netScore()) > 0);
            List<ExamScoreRegradeAudit> audits = scoringRepository.findRegradeAuditsBySessionId(session.id());
            assertEquals(1, audits.size());
            assertEquals(2, audits.getFirst().newScoreVersion());
        } finally {
            // Restore original option key on seeded Q1
            jdbcClient.sql("""
                    UPDATE exam_question_options
                    SET is_correct = CASE WHEN option_key = :orig THEN TRUE ELSE FALSE END
                    WHERE question_item_id = :qid
                    """)
                    .param("orig", trueCorrectKey)
                    .param("qid", q1.id())
                    .update();
        }

        // Strict child-first cleanup (regrade audit + item evaluations + score row, then session & assembly)
        int deletedScoring = scoringRepository.deleteByUserId(userId);
        assertTrue(deletedScoring >= 1);
        assertTrue(scoringRepository.findScoreSnapshotBySessionId(session.id()).isEmpty());
        sessionRepository.deleteByUserId(userId);
        assembledAssessmentRepository.deleteByUserId(userId);
    }
}
