package com.verifiedai.exam.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.verifiedai.exam.application.ExamAnalyticsService;
import com.verifiedai.exam.application.ExamReadinessSignalReader;
import com.verifiedai.exam.config.ExamProperties;
import com.verifiedai.exam.domain.ExamProgressReport;
import com.verifiedai.exam.domain.ExamProgressReport.EvidenceStatus;
import com.verifiedai.exam.infrastructure.persistence.ExamAnalyticsSnapshotRepository;
import com.verifiedai.integration.PostgresIntegrationTestSupport;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Category E Testcontainers PostgreSQL gate for V063 exam_analytics_snapshots.
 */
class ExamAnalyticsSnapshotIntegrationTest extends PostgresIntegrationTestSupport {

    private static final UUID EXAM_DEF_TYT = UUID.fromString("91000000-0000-4000-8000-000000000101");
    private static final UUID EXAM_DEF_AYT = UUID.fromString("91000000-0000-4000-8000-000000000103");
    private static final UUID BP_TYT = UUID.fromString("94000000-0000-4000-8000-000000000101");
    private static final UUID Q_BANK_ITEM_1 = UUID.fromString("93000000-0000-4000-8000-000000000101");
    private static final UUID Q_BANK_ITEM_2 = UUID.fromString("93000000-0000-4000-8000-000000000102");

    @Autowired ExamAnalyticsSnapshotRepository repository;
    @Autowired ExamAnalyticsService service;
    @Autowired ExamProperties examProperties;
    @Autowired JdbcClient jdbc;
    @Autowired TransactionTemplate txTemplate;

    private UUID owner;
    private UUID stranger;
    private boolean prevFlag;

    @BeforeEach
    void setup() {
        prevFlag = examProperties.isAnalyticsEnabled();
        examProperties.setAnalyticsEnabled(true);
        owner = UUID.randomUUID();
        stranger = UUID.randomUUID();
        seedUser(owner);
        seedUser(stranger);
    }

    @AfterEach
    void cleanup() {
        examProperties.setAnalyticsEnabled(prevFlag);
        cleanupUser(owner);
        cleanupUser(stranger);
    }

    @Test
    void enforcesMixedExamLimitTwentyAndPrivacyAndCascadeConstraintsInPostgres() {
        Instant base = Instant.parse("2026-09-01T09:00:00Z");

        // Seed 25 TYT mocks and 5 AYT mocks for owner, plus 3 TYT mocks for stranger
        for (int i = 0; i < 25; i++) {
            seedScoredSession(owner, EXAM_DEF_TYT, "TR_YKS_TYT_MATH", new BigDecimal(10 + i), 1,
                    base.plus(i, ChronoUnit.DAYS), 85, 195);
        }
        for (int i = 0; i < 5; i++) {
            seedScoredSession(owner, EXAM_DEF_AYT, "TR_YKS_AYT_MATH", new BigDecimal(15 + i), 1,
                    base.plus(i, ChronoUnit.DAYS), 90, 120);
        }
        for (int i = 0; i < 3; i++) {
            seedScoredSession(stranger, EXAM_DEF_TYT, "TR_YKS_TYT_MATH", new BigDecimal(38), 1,
                    base.plus(50 + i, ChronoUnit.DAYS), 50, 50);
        }

        ExamProgressReport tyt = service.getProgressReport(owner, "TR_YKS_TYT_MATH");
        assertEquals(20, tyt.mockCount());
        assertEquals(EvidenceStatus.SUFFICIENT_EVIDENCE, tyt.evidenceStatus());
        // Window is indices 5..24 => net 15.00 to 34.00 => delta 19.00
        assertEquals(new BigDecimal("15.00"), tyt.scoreTrajectory().get(0).netScore());
        assertEquals(new BigDecimal("34.00"), tyt.scoreTrajectory().get(19).netScore());
        assertEquals(new BigDecimal("19.00"), tyt.deltaNet());
        assertFalse(tyt.trainingEligible());
        assertEquals(0, tyt.llmTokensUsed());
        assertEquals(0, tyt.inferenceCostUsd().signum());
        assertFalse(tyt.pacingByTopic().isEmpty());

        // DB privacy and TTL constraints
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.sql(
                "UPDATE exam_analytics_snapshots SET training_eligible = TRUE WHERE snapshot_id = :id")
                .param("id", tyt.snapshotId()).update());
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.sql(
                "UPDATE exam_analytics_snapshots SET llm_tokens_used = 1 WHERE snapshot_id = :id")
                .param("id", tyt.snapshotId()).update());
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.sql(
                "UPDATE exam_analytics_snapshots SET mock_count = 21 WHERE snapshot_id = :id")
                .param("id", tyt.snapshotId()).update());
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.sql(
                "UPDATE exam_analytics_snapshots SET expires_at = generated_at + INTERVAL '25 hours' WHERE snapshot_id = :id")
                .param("id", tyt.snapshotId()).update());

        // Second call hits cached snapshot idempotently
        ExamProgressReport tytHit = service.getProgressReport(owner, "YKS_TYT");
        assertEquals(tyt.snapshotId(), tytHit.snapshotId());

        // Regrading latest score invalidates fingerprint and updates snapshot cleanly
        UUID lastSessionId = tyt.scoreTrajectory().get(19).sessionId();
        jdbc.sql("""
                UPDATE exam_session_scores
                SET net_score = 36.25, score_version = 2, updated_at = NOW()
                WHERE user_id = :userId AND session_id = :sessionId
                """)
                .param("userId", owner)
                .param("sessionId", lastSessionId)
                .update();

        ExamProgressReport tytRegraded = service.getProgressReport(owner, "TR_YKS_TYT_MATH");
        assertNotEquals(tyt.snapshotId(), tytRegraded.snapshotId());
        assertEquals(new BigDecimal("36.25"), tytRegraded.scoreTrajectory().get(19).netScore());
        assertEquals(2, tytRegraded.scoreTrajectory().get(19).scoreVersion());

        // Deleting upstream last score cascades to snapshot row cleanly
        jdbc.sql("DELETE FROM exam_session_scores WHERE user_id = :userId AND session_id = :sessionId")
                .param("userId", owner)
                .param("sessionId", lastSessionId)
                .update();
        Long remaining = jdbc.sql("SELECT COUNT(*) FROM exam_analytics_snapshots WHERE snapshot_id = :id")
                .param("id", tytRegraded.snapshotId())
                .query(Long.class)
                .single();
        assertEquals(0L, remaining);

        // Stranger stays isolated
        ExamProgressReport strangerReport = service.getProgressReport(stranger, "TR_YKS_TYT_MATH");
        assertEquals(3, strangerReport.mockCount());
        assertEquals(new BigDecimal("38.00"), strangerReport.scoreTrajectory().get(0).netScore());
    }

    @Test
    void expiredSnapshotUpsertsAtomicallyOnSameFingerprint() {
        Instant base = Instant.parse("2026-10-01T09:00:00Z");
        seedScoredSession(owner, EXAM_DEF_TYT, "TR_YKS_TYT_MATH", new BigDecimal("20.00"), 1, base, 60, 120);

        ExamProgressReport first = service.getProgressReport(owner, "TR_YKS_TYT_MATH");
        jdbc.sql("UPDATE exam_analytics_snapshots SET expires_at = generated_at + INTERVAL '1 second' WHERE snapshot_id = :id")
                .param("id", first.snapshotId())
                .update();

        // Force TTL expiry lookup miss with deterministic upsert on same (user, exam, fingerprint, algorithm)
        ExamProgressReport refreshed = txTemplate.execute(status -> service.getProgressReport(owner, "TR_YKS_TYT_MATH"));
        assertNotNull(refreshed);
        assertEquals(1, repository.exportByUserId(owner).size());
    }

    private void seedUser(UUID userId) {
        jdbc.sql("""
                INSERT INTO users (id, status, created_at, updated_at)
                VALUES (:id, 'ACTIVE', NOW(), NOW())
                ON CONFLICT (id) DO NOTHING
                """)
                .param("id", userId)
                .update();
    }

    private void cleanupUser(UUID userId) {
        jdbc.sql("DELETE FROM exam_analytics_snapshots WHERE user_id = :id").param("id", userId).update();
        jdbc.sql("DELETE FROM exam_session_item_attributions WHERE user_id = :id").param("id", userId).update();
        jdbc.sql("DELETE FROM exam_session_scores WHERE user_id = :id").param("id", userId).update();
        jdbc.sql("DELETE FROM exam_sessions WHERE user_id = :id").param("id", userId).update();
        jdbc.sql("DELETE FROM exam_assembled_assessments WHERE user_id = :id").param("id", userId).update();
    }

    private void seedScoredSession(
            UUID userId,
            UUID examDefId,
            String examCode,
            BigDecimal netScore,
            int scoreVersion,
            Instant submittedAt,
            int timeItem1,
            int timeItem2
    ) {
        UUID assemblyId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID scoreId = UUID.randomUUID();
        UUID sessionItem1 = UUID.randomUUID();
        UUID sessionItem2 = UUID.randomUUID();
        UUID eval1 = UUID.randomUUID();
        UUID eval2 = UUID.randomUUID();
        String seed = "seed-" + sessionId;
        String hex64 = sessionId.toString().replace("-", "").repeat(2);

        jdbc.sql("""
                INSERT INTO exam_assembled_assessments (
                    id, user_id, blueprint_id, exam_definition_id, exam_version_number,
                    assembly_seed, reproducibility_hash, total_question_count,
                    base_duration_seconds, time_multiplier, effective_duration_seconds,
                    status, assembled_at, expires_at, training_eligible
                ) VALUES (
                    :id, :userId, :bpId, :defId, 1,
                    :seed, :hash, 5,
                    900, 1.00, 900,
                    'COMPLETED', :ts, :exp, FALSE
                )
                """)
                .param("id", assemblyId)
                .param("userId", userId)
                .param("bpId", BP_TYT)
                .param("defId", examDefId)
                .param("seed", seed)
                .param("hash", hex64)
                .param("ts", Timestamp.from(submittedAt.minusSeconds(900)))
                .param("exp", Timestamp.from(submittedAt.plusSeconds(3600)))
                .update();

        jdbc.sql("""
                INSERT INTO exam_sessions (
                    id, user_id, assembly_id, blueprint_id, exam_code, exam_version_number,
                    status, navigation_policy, no_hint_policy_enforced, accommodations_snapshot,
                    base_duration_seconds, extra_time_multiplier, effective_duration_seconds,
                    offline_grace_buffer_seconds, started_at, server_deadline_at, grace_deadline_at,
                    last_heartbeat_at, submitted_at, submission_reason, total_item_count,
                    answered_item_count, flagged_item_count, omitted_item_count,
                    scratchpad_attachment_count, client_clock_skew_ms, training_eligible
                ) VALUES (
                    :id, :userId, :assemblyId, :bpId, :examCode, 1,
                    'SUBMITTED', 'FREE_NAVIGATION', TRUE, '{}'::jsonb,
                    900, 1.00, 900,
                    120, :started, :deadline, :grace,
                    :submitted, :submitted, 'USER_INITIATED', 2,
                    2, 0, 0, 0, 0, FALSE
                )
                """)
                .param("id", sessionId)
                .param("userId", userId)
                .param("assemblyId", assemblyId)
                .param("bpId", BP_TYT)
                .param("examCode", examCode)
                .param("started", Timestamp.from(submittedAt.minusSeconds(600)))
                .param("deadline", Timestamp.from(submittedAt.plusSeconds(300)))
                .param("grace", Timestamp.from(submittedAt.plusSeconds(420)))
                .param("submitted", Timestamp.from(submittedAt))
                .update();

        insertSessionItem(sessionItem1, sessionId, 1, Q_BANK_ITEM_1, "TYT-MATH-2026-INT-001", timeItem1);
        insertSessionItem(sessionItem2, sessionId, 2, Q_BANK_ITEM_2, "TYT-MATH-2026-INT-002", timeItem2);

        jdbc.sql("""
                INSERT INTO exam_session_scores (
                    score_id, session_id, user_id, exam_definition_id, assembled_assessment_id,
                    exam_code, scoring_formula_type, wrong_penalty_ratio,
                    total_questions, correct_count, wrong_count, blank_count,
                    raw_correct_score, wrong_penalty_deduction, net_score,
                    min_possible_net_score, max_possible_net_score,
                    normalized_percentage_score, accuracy_percentage,
                    total_time_spent_seconds, average_seconds_per_item,
                    score_version, session_submitted_at, scored_at, llm_tokens_used,
                    inference_cost_usd, training_eligible
                ) VALUES (
                    :scoreId, :sessionId, :userId, :defId, :assemblyId,
                    :examCode, 'CORRECT_MINUS_QUARTER_WRONG', 0.2500,
                    40, 20, 4, 16,
                    20.00, 1.00, :netScore,
                    -10.00, 40.00,
                    50.00, 83.33,
                    :totalTime, 100.00,
                    :scoreVersion, :submittedAt, :submittedAt, 0,
                    0.000000, FALSE
                )
                """)
                .param("scoreId", scoreId)
                .param("sessionId", sessionId)
                .param("userId", userId)
                .param("defId", examDefId)
                .param("assemblyId", assemblyId)
                .param("examCode", examCode)
                .param("netScore", netScore)
                .param("totalTime", timeItem1 + timeItem2)
                .param("scoreVersion", scoreVersion)
                .param("submittedAt", Timestamp.from(submittedAt))
                .update();

        insertEvaluation(eval1, scoreId, sessionId, userId, sessionItem1, Q_BANK_ITEM_1, 1, "TYT-MATH-2026-INT-001", timeItem1);
        insertEvaluation(eval2, scoreId, sessionId, userId, sessionItem2, Q_BANK_ITEM_2, 2, "TYT-MATH-2026-INT-002", timeItem2);
    }

    private void insertSessionItem(UUID itemId, UUID sessionId, int pos, UUID bankId, String qCode, int timeSpent) {
        jdbc.sql("""
                INSERT INTO exam_session_items (
                    id, session_id, item_position, question_item_id, question_code,
                    section_code, section_order, topic_code, item_format, difficulty_band,
                    stem_markdown, options_snapshot, response_state, selected_option_key,
                    time_spent_seconds, training_eligible
                ) VALUES (
                    :id, :sessionId, :pos, :bankId, :qCode,
                    'TYT_MATH', 1, 'MATH.ARITHMETIC.INTEGER_OPERATIONS', 'SINGLE_CHOICE_MCQ', 'MEDIUM',
                    'Synthetic test stem for analytics', '[]'::jsonb, 'ANSWERED', 'A',
                    :timeSpent, FALSE
                )
                """)
                .param("id", itemId)
                .param("sessionId", sessionId)
                .param("pos", pos)
                .param("bankId", bankId)
                .param("qCode", qCode)
                .param("timeSpent", timeSpent)
                .update();
    }

    private void insertEvaluation(UUID evalId, UUID scoreId, UUID sessionId, UUID userId, UUID sessionItemId, UUID bankId, int pos, String qCode, int timeSpent) {
        jdbc.sql("""
                INSERT INTO exam_session_item_evaluations (
                    evaluation_id, score_id, session_id, user_id, session_item_id,
                    question_item_id, position_index, question_code, topic_code, topic_title,
                    difficulty_band, cognitive_level, format_type, evaluation_outcome,
                    raw_points_awarded, penalty_points_deducted, net_points_contribution,
                    time_spent_seconds, solution_explanation_markdown, evidence_source_ref,
                    f28_scratchpad_status, llm_tokens_used, training_eligible
                ) VALUES (
                    :evalId, :scoreId, :sessionId, :userId, :sessionItemId,
                    :bankId, :pos, :qCode, 'MATH.ARITHMETIC.INTEGER_OPERATIONS', 'Integers',
                    'MODERATE', 'APPLICATION', 'SINGLE_CHOICE_MCQ', 'CORRECT',
                    1.00, 0.00, 1.00,
                    :timeSpent, 'Deterministic solution explanation', 'SRC-REF',
                    'NONE', 0, FALSE
                )
                """)
                .param("evalId", evalId)
                .param("scoreId", scoreId)
                .param("sessionId", sessionId)
                .param("userId", userId)
                .param("sessionItemId", sessionItemId)
                .param("bankId", bankId)
                .param("pos", pos)
                .param("qCode", qCode)
                .param("timeSpent", timeSpent)
                .update();
    }
}
