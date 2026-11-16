package com.verifiedai.exam.infrastructure.persistence;

import com.verifiedai.exam.domain.CognitiveLevel;
import com.verifiedai.exam.domain.DifficultyBand;
import com.verifiedai.exam.domain.DifficultyScoreBreakdown;
import com.verifiedai.exam.domain.ExamModels.ScoringFormulaType;
import com.verifiedai.exam.domain.ExamScoreRegradeAudit;
import com.verifiedai.exam.domain.ExamSessionItemEvaluation;
import com.verifiedai.exam.domain.ExamSessionScoreSnapshot;
import com.verifiedai.exam.domain.F28ScratchpadTraceStatus;
import com.verifiedai.exam.domain.ItemEvaluationOutcome;
import com.verifiedai.exam.domain.QuestionFormatType;
import com.verifiedai.exam.domain.ScoreRegradeReasonCode;
import com.verifiedai.exam.domain.TopicScoreBreakdown;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class JdbcExamScoringRepository implements ExamScoringRepository {

    private static final TypeReference<List<TopicScoreBreakdown>> TOPIC_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<DifficultyScoreBreakdown>> DIFFICULTY_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<UUID>> UUID_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcClient jdbcClient;
    private final JsonMapper jsonMapper;

    public JdbcExamScoringRepository(JdbcClient jdbcClient) {
        this(jdbcClient, null);
    }

    @Autowired
    public JdbcExamScoringRepository(
            JdbcClient jdbcClient,
            ObjectProvider<JsonMapper> jsonMapperProvider
    ) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient cannot be null");
        this.jsonMapper = jsonMapperProvider != null
                ? jsonMapperProvider.getIfAvailable(JsonMapper::new)
                : new JsonMapper();
    }

    @Override
    @Transactional
    public ExamSessionScoreSnapshot saveInitialScoreSnapshot(ExamSessionScoreSnapshot snapshot) {
        jdbcClient.sql("""
                INSERT INTO exam_session_scores (
                    score_id, session_id, user_id, exam_definition_id, assembled_assessment_id,
                    exam_code, scoring_formula_type, wrong_penalty_ratio,
                    total_questions, correct_count, wrong_count, blank_count,
                    raw_correct_score, wrong_penalty_deduction, net_score,
                    min_possible_net_score, max_possible_net_score,
                    normalized_percentage_score, accuracy_percentage,
                    total_time_spent_seconds, average_seconds_per_item,
                    topic_breakdown_json, difficulty_breakdown_json,
                    score_version, session_submitted_at, scored_at, last_regraded_at,
                    llm_tokens_used, inference_cost_usd, training_eligible,
                    created_at, updated_at
                ) VALUES (
                    :scoreId, :sessionId, :userId, :examDefinitionId, :assembledAssessmentId,
                    :examCode, :scoringFormulaType, :wrongPenaltyRatio,
                    :totalQuestions, :correctCount, :wrongCount, :blankCount,
                    :rawCorrectScore, :wrongPenaltyDeduction, :netScore,
                    :minPossibleNetScore, :maxPossibleNetScore,
                    :normalizedPercentageScore, :accuracyPercentage,
                    :totalTimeSpentSeconds, :averageSecondsPerItem,
                    CAST(:topicBreakdownJson AS jsonb), CAST(:difficultyBreakdownJson AS jsonb),
                    :scoreVersion, :sessionSubmittedAt, :scoredAt, :lastRegradedAt,
                    0, 0.000000, FALSE,
                    :createdAt, :updatedAt
                )
                """)
                .param("scoreId", snapshot.scoreId())
                .param("sessionId", snapshot.sessionId())
                .param("userId", snapshot.userId())
                .param("examDefinitionId", snapshot.examDefinitionId())
                .param("assembledAssessmentId", snapshot.assembledAssessmentId())
                .param("examCode", snapshot.examCode())
                .param("scoringFormulaType", snapshot.scoringFormulaType().name())
                .param("wrongPenaltyRatio", snapshot.wrongPenaltyRatio())
                .param("totalQuestions", snapshot.totalQuestions())
                .param("correctCount", snapshot.correctCount())
                .param("wrongCount", snapshot.wrongCount())
                .param("blankCount", snapshot.blankCount())
                .param("rawCorrectScore", snapshot.rawCorrectScore())
                .param("wrongPenaltyDeduction", snapshot.wrongPenaltyDeduction())
                .param("netScore", snapshot.netScore())
                .param("minPossibleNetScore", snapshot.minPossibleNetScore())
                .param("maxPossibleNetScore", snapshot.maxPossibleNetScore())
                .param("normalizedPercentageScore", snapshot.normalizedPercentageScore())
                .param("accuracyPercentage", snapshot.accuracyPercentage())
                .param("totalTimeSpentSeconds", snapshot.totalTimeSpentSeconds())
                .param("averageSecondsPerItem", snapshot.averageSecondsPerItem())
                .param("topicBreakdownJson", writeJson(snapshot.topicBreakdowns()))
                .param("difficultyBreakdownJson", writeJson(snapshot.difficultyBreakdowns()))
                .param("scoreVersion", snapshot.scoreVersion())
                .param("sessionSubmittedAt", Timestamp.from(snapshot.sessionSubmittedAt()))
                .param("scoredAt", Timestamp.from(snapshot.scoredAt()))
                .param("lastRegradedAt", toTimestamp(snapshot.lastRegradedAt()), Types.TIMESTAMP)
                .param("createdAt", Timestamp.from(snapshot.scoredAt()))
                .param("updatedAt", Timestamp.from(snapshot.scoredAt()))
                .update();

        insertItemEvaluations(snapshot.itemEvaluations());
        return findScoreSnapshotBySessionId(snapshot.sessionId()).orElse(snapshot);
    }

    @Override
    @Transactional
    public ExamSessionScoreSnapshot updateRegradedScoreSnapshot(
            ExamSessionScoreSnapshot updatedSnapshot,
            ExamScoreRegradeAudit audit
    ) {
        jdbcClient.sql("""
                UPDATE exam_session_scores SET
                    scoring_formula_type = :scoringFormulaType,
                    wrong_penalty_ratio = :wrongPenaltyRatio,
                    total_questions = :totalQuestions,
                    correct_count = :correctCount,
                    wrong_count = :wrongCount,
                    blank_count = :blankCount,
                    raw_correct_score = :rawCorrectScore,
                    wrong_penalty_deduction = :wrongPenaltyDeduction,
                    net_score = :netScore,
                    min_possible_net_score = :minPossibleNetScore,
                    max_possible_net_score = :maxPossibleNetScore,
                    normalized_percentage_score = :normalizedPercentageScore,
                    accuracy_percentage = :accuracyPercentage,
                    total_time_spent_seconds = :totalTimeSpentSeconds,
                    average_seconds_per_item = :averageSecondsPerItem,
                    topic_breakdown_json = CAST(:topicBreakdownJson AS jsonb),
                    difficulty_breakdown_json = CAST(:difficultyBreakdownJson AS jsonb),
                    score_version = :scoreVersion,
                    last_regraded_at = :lastRegradedAt,
                    updated_at = :updatedAt
                WHERE session_id = :sessionId
                """)
                .param("scoringFormulaType", updatedSnapshot.scoringFormulaType().name())
                .param("wrongPenaltyRatio", updatedSnapshot.wrongPenaltyRatio())
                .param("totalQuestions", updatedSnapshot.totalQuestions())
                .param("correctCount", updatedSnapshot.correctCount())
                .param("wrongCount", updatedSnapshot.wrongCount())
                .param("blankCount", updatedSnapshot.blankCount())
                .param("rawCorrectScore", updatedSnapshot.rawCorrectScore())
                .param("wrongPenaltyDeduction", updatedSnapshot.wrongPenaltyDeduction())
                .param("netScore", updatedSnapshot.netScore())
                .param("minPossibleNetScore", updatedSnapshot.minPossibleNetScore())
                .param("maxPossibleNetScore", updatedSnapshot.maxPossibleNetScore())
                .param("normalizedPercentageScore", updatedSnapshot.normalizedPercentageScore())
                .param("accuracyPercentage", updatedSnapshot.accuracyPercentage())
                .param("totalTimeSpentSeconds", updatedSnapshot.totalTimeSpentSeconds())
                .param("averageSecondsPerItem", updatedSnapshot.averageSecondsPerItem())
                .param("topicBreakdownJson", writeJson(updatedSnapshot.topicBreakdowns()))
                .param("difficultyBreakdownJson", writeJson(updatedSnapshot.difficultyBreakdowns()))
                .param("scoreVersion", updatedSnapshot.scoreVersion())
                .param("lastRegradedAt", toTimestamp(updatedSnapshot.lastRegradedAt()), Types.TIMESTAMP)
                .param("updatedAt", Timestamp.from(audit.regradedAt()))
                .param("sessionId", updatedSnapshot.sessionId())
                .update();

        jdbcClient.sql("DELETE FROM exam_session_item_evaluations WHERE session_id = :sessionId")
                .param("sessionId", updatedSnapshot.sessionId())
                .update();

        insertItemEvaluations(updatedSnapshot.itemEvaluations());

        jdbcClient.sql("""
                INSERT INTO exam_score_regrade_audits (
                    regrade_audit_id, score_id, session_id, user_id,
                    previous_score_version, new_score_version,
                    previous_net_score, new_net_score,
                    previous_correct_count, new_correct_count,
                    previous_wrong_count, new_wrong_count,
                    previous_blank_count, new_blank_count,
                    reason_code, reason_detail, triggered_by,
                    delta_summary_json, training_eligible, regraded_at
                ) VALUES (
                    :regradeAuditId, :scoreId, :sessionId, :userId,
                    :previousScoreVersion, :newScoreVersion,
                    :previousNetScore, :newNetScore,
                    :previousCorrectCount, :newCorrectCount,
                    :previousWrongCount, :newWrongCount,
                    :previousBlankCount, :newBlankCount,
                    :reasonCode, :reasonDetail, :triggeredBy,
                    CAST(:deltaSummaryJson AS jsonb), FALSE, :regradedAt
                )
                """)
                .param("regradeAuditId", audit.regradeAuditId())
                .param("scoreId", audit.scoreId())
                .param("sessionId", audit.sessionId())
                .param("userId", audit.userId())
                .param("previousScoreVersion", audit.previousScoreVersion())
                .param("newScoreVersion", audit.newScoreVersion())
                .param("previousNetScore", audit.previousNetScore())
                .param("newNetScore", audit.newNetScore())
                .param("previousCorrectCount", audit.previousCorrectCount())
                .param("newCorrectCount", audit.newCorrectCount())
                .param("previousWrongCount", audit.previousWrongCount())
                .param("newWrongCount", audit.newWrongCount())
                .param("previousBlankCount", audit.previousBlankCount())
                .param("newBlankCount", audit.newBlankCount())
                .param("reasonCode", audit.reasonCode().name())
                .param("reasonDetail", audit.reasonDetail())
                .param("triggeredBy", audit.triggeredBy())
                .param("deltaSummaryJson", writeJson(audit.deltaSummary()))
                .param("regradedAt", Timestamp.from(audit.regradedAt()))
                .update();

        return findScoreSnapshotBySessionId(updatedSnapshot.sessionId()).orElse(updatedSnapshot);
    }

    @Override
    public Optional<ExamSessionScoreSnapshot> findScoreSnapshotBySessionId(UUID sessionId) {
        return jdbcClient.sql("""
                SELECT score_id, session_id, user_id, exam_definition_id, assembled_assessment_id,
                       exam_code, scoring_formula_type, wrong_penalty_ratio,
                       total_questions, correct_count, wrong_count, blank_count,
                       raw_correct_score, wrong_penalty_deduction, net_score,
                       min_possible_net_score, max_possible_net_score,
                       normalized_percentage_score, accuracy_percentage,
                       total_time_spent_seconds, average_seconds_per_item,
                       topic_breakdown_json::text AS topic_breakdown_text,
                       difficulty_breakdown_json::text AS difficulty_breakdown_text,
                       score_version, session_submitted_at, scored_at, last_regraded_at,
                       llm_tokens_used, inference_cost_usd, training_eligible
                FROM exam_session_scores
                WHERE session_id = :sessionId
                LIMIT 1
                """)
                .param("sessionId", sessionId)
                .query(this::mapScoreHeader)
                .optional()
                .map(header -> attachEvaluations(header, findItemEvaluationsBySessionId(header.sessionId())));
    }

    @Override
    public List<ExamSessionScoreSnapshot> findLatestScoresByUserId(UUID userId, int limit) {
        int clampedLimit = Math.max(1, Math.min(limit, 200));
        List<ExamSessionScoreSnapshot> headers = jdbcClient.sql("""
                SELECT score_id, session_id, user_id, exam_definition_id, assembled_assessment_id,
                       exam_code, scoring_formula_type, wrong_penalty_ratio,
                       total_questions, correct_count, wrong_count, blank_count,
                       raw_correct_score, wrong_penalty_deduction, net_score,
                       min_possible_net_score, max_possible_net_score,
                       normalized_percentage_score, accuracy_percentage,
                       total_time_spent_seconds, average_seconds_per_item,
                       topic_breakdown_json::text AS topic_breakdown_text,
                       difficulty_breakdown_json::text AS difficulty_breakdown_text,
                       score_version, session_submitted_at, scored_at, last_regraded_at,
                       llm_tokens_used, inference_cost_usd, training_eligible
                FROM exam_session_scores
                WHERE user_id = :userId
                ORDER BY scored_at DESC
                LIMIT :limit
                """)
                .param("userId", userId)
                .param("limit", clampedLimit)
                .query(this::mapScoreHeader)
                .list();

        return headers.stream()
                .map(header -> attachEvaluations(header, findItemEvaluationsBySessionId(header.sessionId())))
                .toList();
    }

    @Override
    public List<ExamSessionItemEvaluation> findItemEvaluationsBySessionId(UUID sessionId) {
        return jdbcClient.sql("""
                SELECT evaluation_id, score_id, session_id, user_id, session_item_id, question_item_id,
                       position_index, question_code, topic_code, topic_title,
                       difficulty_band, cognitive_level, format_type, evaluation_outcome,
                       selected_option_key, numeric_response_value, correct_option_key,
                       expected_numeric_answer, numeric_tolerance,
                       raw_points_awarded, penalty_points_deducted, net_points_contribution,
                       is_flagged_for_review, time_spent_seconds,
                       selected_option_distractor_rationale, solution_explanation_markdown, evidence_source_ref,
                       f28_scratchpad_status, scratchpad_asset_count,
                       scratchpad_asset_ids_json::text AS scratchpad_asset_ids_text,
                       question_version, llm_tokens_used, training_eligible, evaluated_at
                FROM exam_session_item_evaluations
                WHERE session_id = :sessionId
                ORDER BY position_index ASC
                """)
                .param("sessionId", sessionId)
                .query(this::mapItemEvaluation)
                .list();
    }

    @Override
    public List<ExamScoreRegradeAudit> findRegradeAuditsBySessionId(UUID sessionId) {
        return jdbcClient.sql("""
                SELECT regrade_audit_id, score_id, session_id, user_id,
                       previous_score_version, new_score_version,
                       previous_net_score, new_net_score,
                       previous_correct_count, new_correct_count,
                       previous_wrong_count, new_wrong_count,
                       previous_blank_count, new_blank_count,
                       reason_code, reason_detail, triggered_by,
                       delta_summary_json::text AS delta_summary_text,
                       training_eligible, regraded_at
                FROM exam_score_regrade_audits
                WHERE session_id = :sessionId
                ORDER BY regraded_at DESC, new_score_version DESC
                """)
                .param("sessionId", sessionId)
                .query(this::mapRegradeAudit)
                .list();
    }

    @Override
    public List<ExamScoreRegradeAudit> findRegradeAuditsByUserId(UUID userId) {
        return jdbcClient.sql("""
                SELECT regrade_audit_id, score_id, session_id, user_id,
                       previous_score_version, new_score_version,
                       previous_net_score, new_net_score,
                       previous_correct_count, new_correct_count,
                       previous_wrong_count, new_wrong_count,
                       previous_blank_count, new_blank_count,
                       reason_code, reason_detail, triggered_by,
                       delta_summary_json::text AS delta_summary_text,
                       training_eligible, regraded_at
                FROM exam_score_regrade_audits
                WHERE user_id = :userId
                ORDER BY regraded_at DESC, new_score_version DESC
                """)
                .param("userId", userId)
                .query(this::mapRegradeAudit)
                .list();
    }

    @Override
    @Transactional
    public int deleteByUserId(UUID userId) {
        int deletedAudits = jdbcClient.sql("DELETE FROM exam_score_regrade_audits WHERE user_id = :userId")
                .param("userId", userId)
                .update();
        int deletedEvaluations = jdbcClient.sql("DELETE FROM exam_session_item_evaluations WHERE user_id = :userId")
                .param("userId", userId)
                .update();
        int deletedScores = jdbcClient.sql("DELETE FROM exam_session_scores WHERE user_id = :userId")
                .param("userId", userId)
                .update();
        return deletedAudits + deletedEvaluations + deletedScores;
    }

    private void insertItemEvaluations(List<ExamSessionItemEvaluation> evaluations) {
        for (ExamSessionItemEvaluation eval : evaluations) {
            jdbcClient.sql("""
                    INSERT INTO exam_session_item_evaluations (
                        evaluation_id, score_id, session_id, user_id, session_item_id, question_item_id,
                        position_index, question_code, topic_code, topic_title,
                        difficulty_band, cognitive_level, format_type, evaluation_outcome,
                        selected_option_key, numeric_response_value, correct_option_key,
                        expected_numeric_answer, numeric_tolerance,
                        raw_points_awarded, penalty_points_deducted, net_points_contribution,
                        is_flagged_for_review, time_spent_seconds,
                        selected_option_distractor_rationale, solution_explanation_markdown, evidence_source_ref,
                        f28_scratchpad_status, scratchpad_asset_count, scratchpad_asset_ids_json,
                        question_version, llm_tokens_used, training_eligible, evaluated_at
                    ) VALUES (
                        :evaluationId, :scoreId, :sessionId, :userId, :sessionItemId, :questionItemId,
                        :positionIndex, :questionCode, :topicCode, :topicTitle,
                        :difficultyBand, :cognitiveLevel, :formatType, :evaluationOutcome,
                        :selectedOptionKey, :numericResponseValue, :correctOptionKey,
                        :expectedNumericAnswer, :numericTolerance,
                        :rawPointsAwarded, :penaltyPointsDeducted, :netPointsContribution,
                        :flaggedForReview, :timeSpentSeconds,
                        :distractorRationale, :solutionExplanation, :evidenceSourceRef,
                        :f28Status, :scratchpadAssetCount, CAST(:scratchpadAssetIdsJson AS jsonb),
                        :questionVersion, 0, FALSE, :evaluatedAt
                    )
                    """)
                    .param("evaluationId", eval.evaluationId())
                    .param("scoreId", eval.scoreId())
                    .param("sessionId", eval.sessionId())
                    .param("userId", eval.userId())
                    .param("sessionItemId", eval.sessionItemId())
                    .param("questionItemId", eval.questionItemId())
                    .param("positionIndex", eval.positionIndex())
                    .param("questionCode", eval.questionCode())
                    .param("topicCode", eval.topicCode())
                    .param("topicTitle", eval.topicTitle())
                    .param("difficultyBand", eval.difficultyBand().name())
                    .param("cognitiveLevel", eval.cognitiveLevel().name())
                    .param("formatType", eval.formatType().name())
                    .param("evaluationOutcome", eval.evaluationOutcome().name())
                    .param("selectedOptionKey", eval.selectedOptionKey(), Types.VARCHAR)
                    .param("numericResponseValue", eval.numericResponseValue(), Types.NUMERIC)
                    .param("correctOptionKey", eval.correctOptionKey(), Types.VARCHAR)
                    .param("expectedNumericAnswer", eval.expectedNumericAnswer(), Types.NUMERIC)
                    .param("numericTolerance", eval.numericTolerance(), Types.NUMERIC)
                    .param("rawPointsAwarded", eval.rawPointsAwarded())
                    .param("penaltyPointsDeducted", eval.penaltyPointsDeducted())
                    .param("netPointsContribution", eval.netPointsContribution())
                    .param("flaggedForReview", eval.flaggedForReview())
                    .param("timeSpentSeconds", eval.timeSpentSeconds())
                    .param("distractorRationale", eval.selectedOptionDistractorRationale(), Types.VARCHAR)
                    .param("solutionExplanation", eval.solutionExplanationMarkdown())
                    .param("evidenceSourceRef", eval.evidenceSourceRef())
                    .param("f28Status", eval.f28ScratchpadStatus().name())
                    .param("scratchpadAssetCount", eval.scratchpadAssetCount())
                    .param("scratchpadAssetIdsJson", writeJson(eval.scratchpadAssetIds()))
                    .param("questionVersion", eval.questionVersion())
                    .param("evaluatedAt", Timestamp.from(eval.evaluatedAt()))
                    .update();
        }
    }

    private ExamSessionScoreSnapshot mapScoreHeader(ResultSet rs, int rowNum) throws SQLException {
        Timestamp lastRegradedTs = rs.getTimestamp("last_regraded_at");
        return new ExamSessionScoreSnapshot(
                rs.getObject("score_id", UUID.class),
                rs.getObject("session_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("exam_definition_id", UUID.class),
                rs.getObject("assembled_assessment_id", UUID.class),
                rs.getString("exam_code"),
                ScoringFormulaType.valueOf(rs.getString("scoring_formula_type")),
                rs.getBigDecimal("wrong_penalty_ratio"),
                rs.getInt("total_questions"),
                rs.getInt("correct_count"),
                rs.getInt("wrong_count"),
                rs.getInt("blank_count"),
                rs.getBigDecimal("raw_correct_score"),
                rs.getBigDecimal("wrong_penalty_deduction"),
                rs.getBigDecimal("net_score"),
                rs.getBigDecimal("min_possible_net_score"),
                rs.getBigDecimal("max_possible_net_score"),
                rs.getBigDecimal("normalized_percentage_score"),
                rs.getBigDecimal("accuracy_percentage"),
                rs.getInt("total_time_spent_seconds"),
                rs.getBigDecimal("average_seconds_per_item"),
                readJson(rs.getString("topic_breakdown_text"), TOPIC_LIST_TYPE, List.of()),
                readJson(rs.getString("difficulty_breakdown_text"), DIFFICULTY_LIST_TYPE, List.of()),
                List.of(),
                rs.getInt("score_version"),
                rs.getTimestamp("session_submitted_at").toInstant(),
                rs.getTimestamp("scored_at").toInstant(),
                lastRegradedTs == null ? null : lastRegradedTs.toInstant(),
                rs.getInt("llm_tokens_used"),
                rs.getBigDecimal("inference_cost_usd"),
                rs.getBoolean("training_eligible")
        );
    }

    private ExamSessionItemEvaluation mapItemEvaluation(ResultSet rs, int rowNum) throws SQLException {
        return new ExamSessionItemEvaluation(
                rs.getObject("evaluation_id", UUID.class),
                rs.getObject("score_id", UUID.class),
                rs.getObject("session_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("session_item_id", UUID.class),
                rs.getObject("question_item_id", UUID.class),
                rs.getInt("position_index"),
                rs.getString("question_code"),
                rs.getString("topic_code"),
                rs.getString("topic_title"),
                DifficultyBand.valueOf(rs.getString("difficulty_band")),
                CognitiveLevel.valueOf(rs.getString("cognitive_level")),
                QuestionFormatType.valueOf(rs.getString("format_type")),
                ItemEvaluationOutcome.valueOf(rs.getString("evaluation_outcome")),
                rs.getString("selected_option_key"),
                rs.getBigDecimal("numeric_response_value"),
                rs.getString("correct_option_key"),
                rs.getBigDecimal("expected_numeric_answer"),
                rs.getBigDecimal("numeric_tolerance"),
                rs.getBigDecimal("raw_points_awarded"),
                rs.getBigDecimal("penalty_points_deducted"),
                rs.getBigDecimal("net_points_contribution"),
                rs.getBoolean("is_flagged_for_review"),
                rs.getInt("time_spent_seconds"),
                rs.getString("selected_option_distractor_rationale"),
                rs.getString("solution_explanation_markdown"),
                rs.getString("evidence_source_ref"),
                F28ScratchpadTraceStatus.valueOf(rs.getString("f28_scratchpad_status")),
                rs.getInt("scratchpad_asset_count"),
                readJson(rs.getString("scratchpad_asset_ids_text"), UUID_LIST_TYPE, List.of()),
                rs.getInt("question_version"),
                rs.getInt("llm_tokens_used"),
                rs.getBoolean("training_eligible"),
                rs.getTimestamp("evaluated_at").toInstant()
        );
    }

    private ExamScoreRegradeAudit mapRegradeAudit(ResultSet rs, int rowNum) throws SQLException {
        return new ExamScoreRegradeAudit(
                rs.getObject("regrade_audit_id", UUID.class),
                rs.getObject("score_id", UUID.class),
                rs.getObject("session_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getInt("previous_score_version"),
                rs.getInt("new_score_version"),
                rs.getBigDecimal("previous_net_score"),
                rs.getBigDecimal("new_net_score"),
                rs.getInt("previous_correct_count"),
                rs.getInt("new_correct_count"),
                rs.getInt("previous_wrong_count"),
                rs.getInt("new_wrong_count"),
                rs.getInt("previous_blank_count"),
                rs.getInt("new_blank_count"),
                ScoreRegradeReasonCode.valueOf(rs.getString("reason_code")),
                rs.getString("reason_detail"),
                rs.getString("triggered_by"),
                readJson(rs.getString("delta_summary_text"), MAP_TYPE, Map.of()),
                rs.getBoolean("training_eligible"),
                rs.getTimestamp("regraded_at").toInstant()
        );
    }

    private static ExamSessionScoreSnapshot attachEvaluations(
            ExamSessionScoreSnapshot header,
            List<ExamSessionItemEvaluation> evaluations
    ) {
        return new ExamSessionScoreSnapshot(
                header.scoreId(),
                header.sessionId(),
                header.userId(),
                header.examDefinitionId(),
                header.assembledAssessmentId(),
                header.examCode(),
                header.scoringFormulaType(),
                header.wrongPenaltyRatio(),
                header.totalQuestions(),
                header.correctCount(),
                header.wrongCount(),
                header.blankCount(),
                header.rawCorrectScore(),
                header.wrongPenaltyDeduction(),
                header.netScore(),
                header.minPossibleNetScore(),
                header.maxPossibleNetScore(),
                header.normalizedPercentageScore(),
                header.accuracyPercentage(),
                header.totalTimeSpentSeconds(),
                header.averageSecondsPerItem(),
                header.topicBreakdowns(),
                header.difficultyBreakdowns(),
                evaluations,
                header.scoreVersion(),
                header.sessionSubmittedAt(),
                header.scoredAt(),
                header.lastRegradedAt(),
                header.llmTokensUsed(),
                header.inferenceCostUsd(),
                header.trainingEligible()
        );
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private String writeJson(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to serialize JSON in JdbcExamScoringRepository", ex);
        }
    }

    private <T> T readJson(String raw, TypeReference<T> typeRef, T defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return jsonMapper.readValue(raw, typeRef);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to deserialize JSON in JdbcExamScoringRepository", ex);
        }
    }
}
