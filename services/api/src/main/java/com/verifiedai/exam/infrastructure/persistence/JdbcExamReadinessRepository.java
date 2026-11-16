package com.verifiedai.exam.infrastructure.persistence;

import com.verifiedai.exam.application.ExamReadinessSignalReader;
import com.verifiedai.exam.domain.ActiveTrapImpactItem;
import com.verifiedai.exam.domain.ExamReadinessCalculator.ActiveTrapSignal;
import com.verifiedai.exam.domain.ExamReadinessCalculator.SkillMasterySignal;
import com.verifiedai.exam.domain.ExamReadinessConfidenceTier;
import com.verifiedai.exam.domain.ExamReadinessProjection;
import com.verifiedai.exam.domain.ExamReadinessRationaleCode;
import com.verifiedai.exam.domain.ExamReadinessRepository;
import com.verifiedai.exam.domain.ExamReadinessTopicAllocation;
import com.verifiedai.exam.domain.SectionReadinessBreakdown;
import com.verifiedai.mistake.MistakeTaxonomy;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.LinkedHashMap;
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

/**
 * PostgreSQL JDBC repository for V059 {@code exam_readiness_projections} and
 * {@code exam_readiness_topic_allocations}, plus upstream mastery/mistake signal reads.
 * Uses Jackson 3 ({@code tools.jackson.*}) exclusively.
 */
@Repository
public class JdbcExamReadinessRepository implements ExamReadinessRepository, ExamReadinessSignalReader {

    private static final TypeReference<List<ActiveTrapImpactItem>> TRAP_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<SectionReadinessBreakdown>> SECTION_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<ExamReadinessTopicAllocation>> ALLOCATION_LIST_TYPE = new TypeReference<>() {
    };

    private final JdbcClient jdbcClient;
    private final JsonMapper jsonMapper;

    public JdbcExamReadinessRepository(JdbcClient jdbcClient) {
        this(jdbcClient, null);
    }

    @Autowired
    public JdbcExamReadinessRepository(
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
    public ExamReadinessProjection saveUpsert(ExamReadinessProjection projection) {
        Objects.requireNonNull(projection, "projection cannot be null");

        jdbcClient.sql("""
                DELETE FROM exam_readiness_topic_allocations
                WHERE user_id = :userId AND exam_code = :examCode
                """)
                .param("userId", projection.userId())
                .param("examCode", projection.examCode())
                .update();

        jdbcClient.sql("""
                INSERT INTO exam_readiness_projections (
                    projection_id, user_id, exam_code, target_id, latest_scored_session_id,
                    total_question_count, wrong_answer_penalty_ratio,
                    estimated_net_low, estimated_net_mid, estimated_net_high, credible_interval_level,
                    target_net_score, net_gap_to_target, net_lost_to_traps,
                    pacing_factor, mean_retention_probability, epistemic_confidence,
                    confidence_tier, calibration_required, recommended_diagnostic_question_count,
                    evaluated_skill_count, total_evidence_count, completed_mock_count,
                    active_trap_breakdown_json, section_readiness_json, top_roi_allocations_json,
                    methodology_version, training_eligible, llm_tokens_used, inference_cost_usd,
                    computed_at
                ) VALUES (
                    :projectionId, :userId, :examCode, :targetId, :latestScoredSessionId,
                    :totalQuestionCount, :wrongAnswerPenaltyRatio,
                    :estimatedNetLow, :estimatedNetMid, :estimatedNetHigh, :credibleIntervalLevel,
                    :targetNetScore, :netGapToTarget, :netLostToTraps,
                    :pacingFactor, :meanRetentionProbability, :epistemicConfidence,
                    :confidenceTier, :calibrationRequired, :recommendedDiagnosticQuestionCount,
                    :evaluatedSkillCount, :totalEvidenceCount, :completedMockCount,
                    CAST(:activeTrapBreakdownJson AS jsonb),
                    CAST(:sectionReadinessJson AS jsonb),
                    CAST(:topRoiAllocationsJson AS jsonb),
                    :methodologyVersion, FALSE, 0, 0.000000,
                    :computedAt
                )
                ON CONFLICT (user_id, exam_code) DO UPDATE SET
                    projection_id = EXCLUDED.projection_id,
                    target_id = EXCLUDED.target_id,
                    latest_scored_session_id = EXCLUDED.latest_scored_session_id,
                    total_question_count = EXCLUDED.total_question_count,
                    wrong_answer_penalty_ratio = EXCLUDED.wrong_answer_penalty_ratio,
                    estimated_net_low = EXCLUDED.estimated_net_low,
                    estimated_net_mid = EXCLUDED.estimated_net_mid,
                    estimated_net_high = EXCLUDED.estimated_net_high,
                    credible_interval_level = EXCLUDED.credible_interval_level,
                    target_net_score = EXCLUDED.target_net_score,
                    net_gap_to_target = EXCLUDED.net_gap_to_target,
                    net_lost_to_traps = EXCLUDED.net_lost_to_traps,
                    pacing_factor = EXCLUDED.pacing_factor,
                    mean_retention_probability = EXCLUDED.mean_retention_probability,
                    epistemic_confidence = EXCLUDED.epistemic_confidence,
                    confidence_tier = EXCLUDED.confidence_tier,
                    calibration_required = EXCLUDED.calibration_required,
                    recommended_diagnostic_question_count = EXCLUDED.recommended_diagnostic_question_count,
                    evaluated_skill_count = EXCLUDED.evaluated_skill_count,
                    total_evidence_count = EXCLUDED.total_evidence_count,
                    completed_mock_count = EXCLUDED.completed_mock_count,
                    active_trap_breakdown_json = EXCLUDED.active_trap_breakdown_json,
                    section_readiness_json = EXCLUDED.section_readiness_json,
                    top_roi_allocations_json = EXCLUDED.top_roi_allocations_json,
                    methodology_version = EXCLUDED.methodology_version,
                    training_eligible = FALSE,
                    llm_tokens_used = 0,
                    inference_cost_usd = 0.000000,
                    computed_at = EXCLUDED.computed_at
                """)
                .param("projectionId", projection.projectionId())
                .param("userId", projection.userId())
                .param("examCode", projection.examCode())
                .param("targetId", projection.targetId(), Types.OTHER)
                .param("latestScoredSessionId", projection.latestScoredSessionId(), Types.OTHER)
                .param("totalQuestionCount", projection.totalQuestionCount())
                .param("wrongAnswerPenaltyRatio", projection.wrongAnswerPenaltyRatio())
                .param("estimatedNetLow", projection.estimatedNetLow())
                .param("estimatedNetMid", projection.estimatedNetMid())
                .param("estimatedNetHigh", projection.estimatedNetHigh())
                .param("credibleIntervalLevel", projection.credibleIntervalLevel())
                .param("targetNetScore", projection.targetNetScore(), Types.NUMERIC)
                .param("netGapToTarget", projection.netGapToTarget(), Types.NUMERIC)
                .param("netLostToTraps", projection.netLostToTraps())
                .param("pacingFactor", projection.pacingFactor())
                .param("meanRetentionProbability", projection.meanRetentionProbability())
                .param("epistemicConfidence", projection.epistemicConfidence())
                .param("confidenceTier", projection.confidenceTier().name())
                .param("calibrationRequired", projection.calibrationRequired())
                .param("recommendedDiagnosticQuestionCount", projection.recommendedDiagnosticQuestionCount())
                .param("evaluatedSkillCount", projection.evaluatedSkillCount())
                .param("totalEvidenceCount", projection.totalEvidenceCount())
                .param("completedMockCount", projection.completedMockCount())
                .param("activeTrapBreakdownJson", writeJson(projection.activeTrapBreakdown()))
                .param("sectionReadinessJson", writeJson(projection.sectionReadiness()))
                .param("topRoiAllocationsJson", writeJson(projection.topRoiAllocations()))
                .param("methodologyVersion", projection.methodologyVersion())
                .param("computedAt", Timestamp.from(projection.computedAt()))
                .update();

        for (ExamReadinessTopicAllocation allocation : projection.topRoiAllocations()) {
            jdbcClient.sql("""
                    INSERT INTO exam_readiness_topic_allocations (
                        allocation_id, projection_id, user_id, exam_code, section_code,
                        skill_code, topic_title_key, blueprint_question_weight,
                        current_mastery, retention_probability, expected_net_gain_per_hour,
                        recommended_study_minutes, priority_rank, rationale_code, training_eligible
                    ) VALUES (
                        :allocationId, :projectionId, :userId, :examCode, :sectionCode,
                        :skillCode, :topicTitleKey, :blueprintQuestionWeight,
                        :currentMastery, :retentionProbability, :expectedNetGainPerHour,
                        :recommendedStudyMinutes, :priorityRank, :rationaleCode, FALSE
                    )
                    """)
                    .param("allocationId", allocation.allocationId())
                    .param("projectionId", projection.projectionId())
                    .param("userId", projection.userId())
                    .param("examCode", projection.examCode())
                    .param("sectionCode", allocation.sectionCode())
                    .param("skillCode", allocation.skillCode())
                    .param("topicTitleKey", allocation.topicTitleKey())
                    .param("blueprintQuestionWeight", allocation.blueprintQuestionWeight())
                    .param("currentMastery", allocation.currentMastery())
                    .param("retentionProbability", allocation.retentionProbability())
                    .param("expectedNetGainPerHour", allocation.expectedNetGainPerHour())
                    .param("recommendedStudyMinutes", allocation.recommendedStudyMinutes())
                    .param("priorityRank", allocation.priorityRank())
                    .param("rationaleCode", allocation.rationaleCode().name())
                    .update();
        }

        return findByUserIdAndExamCode(projection.userId(), projection.examCode()).orElse(projection);
    }

    @Override
    public Optional<ExamReadinessProjection> findByUserIdAndExamCode(UUID userId, String examCode) {
        return jdbcClient.sql("""
                SELECT projection_id, user_id, exam_code, target_id, latest_scored_session_id,
                       total_question_count, wrong_answer_penalty_ratio,
                       estimated_net_low, estimated_net_mid, estimated_net_high, credible_interval_level,
                       target_net_score, net_gap_to_target, net_lost_to_traps,
                       pacing_factor, mean_retention_probability, epistemic_confidence,
                       confidence_tier, calibration_required, recommended_diagnostic_question_count,
                       evaluated_skill_count, total_evidence_count, completed_mock_count,
                       active_trap_breakdown_json::text AS active_trap_breakdown_text,
                       section_readiness_json::text AS section_readiness_text,
                       top_roi_allocations_json::text AS top_roi_allocations_text,
                       methodology_version, training_eligible, llm_tokens_used, inference_cost_usd,
                       computed_at
                FROM exam_readiness_projections
                WHERE user_id = :userId AND exam_code = :examCode
                LIMIT 1
                """)
                .param("userId", userId)
                .param("examCode", examCode)
                .query(this::mapProjection)
                .optional();
    }

    @Override
    public List<ExamReadinessProjection> findAllByUserId(UUID userId) {
        return jdbcClient.sql("""
                SELECT projection_id, user_id, exam_code, target_id, latest_scored_session_id,
                       total_question_count, wrong_answer_penalty_ratio,
                       estimated_net_low, estimated_net_mid, estimated_net_high, credible_interval_level,
                       target_net_score, net_gap_to_target, net_lost_to_traps,
                       pacing_factor, mean_retention_probability, epistemic_confidence,
                       confidence_tier, calibration_required, recommended_diagnostic_question_count,
                       evaluated_skill_count, total_evidence_count, completed_mock_count,
                       active_trap_breakdown_json::text AS active_trap_breakdown_text,
                       section_readiness_json::text AS section_readiness_text,
                       top_roi_allocations_json::text AS top_roi_allocations_text,
                       methodology_version, training_eligible, llm_tokens_used, inference_cost_usd,
                       computed_at
                FROM exam_readiness_projections
                WHERE user_id = :userId
                ORDER BY computed_at DESC, exam_code ASC
                """)
                .param("userId", userId)
                .query(this::mapProjection)
                .list();
    }

    @Override
    public List<ExamReadinessTopicAllocation> findTopicAllocationsByUserId(UUID userId) {
        return jdbcClient.sql("""
                SELECT allocation_id, section_code, skill_code, topic_title_key,
                       blueprint_question_weight, current_mastery, retention_probability,
                       expected_net_gain_per_hour, recommended_study_minutes,
                       priority_rank, rationale_code, training_eligible
                FROM exam_readiness_topic_allocations
                WHERE user_id = :userId
                ORDER BY exam_code ASC, priority_rank ASC
                """)
                .param("userId", userId)
                .query(this::mapTopicAllocation)
                .list();
    }

    @Override
    @Transactional
    public int deleteByUserId(UUID userId) {
        int deletedAllocations = jdbcClient.sql(
                "DELETE FROM exam_readiness_topic_allocations WHERE user_id = :userId")
                .param("userId", userId)
                .update();
        int deletedProjections = jdbcClient.sql(
                "DELETE FROM exam_readiness_projections WHERE user_id = :userId")
                .param("userId", userId)
                .update();
        return deletedAllocations + deletedProjections;
    }

    @Override
    public Map<String, SkillMasterySignal> loadSkillMasterySignals(UUID userId) {
        List<SkillMasterySignal> rows = jdbcClient.sql("""
                SELECT skill_id, score, half_life_days, evidence_count, confidence, last_assessed_at
                FROM learner_skill_mastery
                WHERE user_id = :userId
                """)
                .param("userId", userId)
                .query((rs, rowNum) -> {
                    Timestamp ts = rs.getTimestamp("last_assessed_at");
                    return new SkillMasterySignal(
                            rs.getString("skill_id"),
                            rs.getDouble("score"),
                            rs.getDouble("half_life_days"),
                            rs.getDouble("confidence"),
                            rs.getInt("evidence_count"),
                            ts != null ? ts.toInstant() : Instant.now()
                    );
                })
                .list();

        Map<String, SkillMasterySignal> map = new LinkedHashMap<>();
        for (SkillMasterySignal signal : rows) {
            map.put(signal.skillCode(), signal);
        }
        return map;
    }

    @Override
    public List<ActiveTrapSignal> loadActiveTrapSignals(UUID userId) {
        List<ActiveTrapSignal> dnaRows = jdbcClient.sql("""
                SELECT mistake_code, skill_id, smoothed_propensity, recent_error_count
                FROM learner_mistake_dna_profiles
                WHERE user_id = :userId
                  AND recurrence_state IN ('OBSERVED_ONCE', 'RECURRING_PATTERN', 'IN_REMEDIATION', 'RELAPSED')
                ORDER BY smoothed_propensity DESC, mistake_code ASC
                """)
                .param("userId", userId)
                .query((rs, rowNum) -> {
                    String mistakeCode = rs.getString("mistake_code");
                    String family = MistakeTaxonomy.CATEGORIES
                            .getOrDefault(mistakeCode, MistakeTaxonomy.Category.OTHER_REVIEWED)
                            .name();
                    return new ActiveTrapSignal(
                            mistakeCode,
                            family,
                            rs.getString("skill_id"),
                            rs.getBigDecimal("smoothed_propensity").doubleValue(),
                            rs.getInt("recent_error_count")
                    );
                })
                .list();
        if (!dnaRows.isEmpty()) {
            return dnaRows;
        }

        return jdbcClient.sql("""
                SELECT mistake_code, mistake_family, skill_node_id, occurrence_count
                FROM mistake_dna_profiles
                WHERE learner_id = :userId
                  AND lifecycle_status IN ('RECURRING_PATTERN', 'IN_REMEDIATION')
                ORDER BY occurrence_count DESC, mistake_code ASC
                """)
                .param("userId", userId)
                .query((rs, rowNum) -> {
                    int occ = Math.max(1, rs.getInt("occurrence_count"));
                    double rate = Math.min(0.90, (occ + 1.0) / (occ + 4.0));
                    return new ActiveTrapSignal(
                            rs.getString("mistake_code"),
                            rs.getString("mistake_family"),
                            rs.getString("skill_node_id"),
                            rate,
                            occ
                    );
                })
                .list();
    }

    private ExamReadinessProjection mapProjection(ResultSet rs, int rowNum) throws SQLException {
        return new ExamReadinessProjection(
                rs.getObject("projection_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("exam_code"),
                rs.getObject("target_id", UUID.class),
                rs.getObject("latest_scored_session_id", UUID.class),
                rs.getInt("total_question_count"),
                rs.getBigDecimal("wrong_answer_penalty_ratio"),
                rs.getBigDecimal("estimated_net_low"),
                rs.getBigDecimal("estimated_net_mid"),
                rs.getBigDecimal("estimated_net_high"),
                rs.getBigDecimal("credible_interval_level"),
                rs.getBigDecimal("target_net_score"),
                rs.getBigDecimal("net_gap_to_target"),
                rs.getBigDecimal("net_lost_to_traps"),
                rs.getBigDecimal("pacing_factor"),
                rs.getBigDecimal("mean_retention_probability"),
                rs.getBigDecimal("epistemic_confidence"),
                ExamReadinessConfidenceTier.valueOf(rs.getString("confidence_tier")),
                rs.getBoolean("calibration_required"),
                rs.getInt("recommended_diagnostic_question_count"),
                rs.getInt("evaluated_skill_count"),
                rs.getInt("total_evidence_count"),
                rs.getInt("completed_mock_count"),
                readJson(rs.getString("active_trap_breakdown_text"), TRAP_LIST_TYPE, List.of()),
                readJson(rs.getString("section_readiness_text"), SECTION_LIST_TYPE, List.of()),
                readJson(rs.getString("top_roi_allocations_text"), ALLOCATION_LIST_TYPE, List.of()),
                rs.getString("methodology_version"),
                rs.getBoolean("training_eligible"),
                rs.getInt("llm_tokens_used"),
                rs.getBigDecimal("inference_cost_usd"),
                rs.getTimestamp("computed_at").toInstant()
        );
    }

    private ExamReadinessTopicAllocation mapTopicAllocation(ResultSet rs, int rowNum) throws SQLException {
        return new ExamReadinessTopicAllocation(
                rs.getObject("allocation_id", UUID.class),
                rs.getString("section_code"),
                rs.getString("skill_code"),
                rs.getString("topic_title_key"),
                rs.getBigDecimal("blueprint_question_weight"),
                rs.getBigDecimal("current_mastery"),
                rs.getBigDecimal("retention_probability"),
                rs.getBigDecimal("expected_net_gain_per_hour"),
                rs.getInt("recommended_study_minutes"),
                rs.getInt("priority_rank"),
                ExamReadinessRationaleCode.valueOf(rs.getString("rationale_code")),
                rs.getBoolean("training_eligible")
        );
    }

    private String writeJson(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to serialize JSON in JdbcExamReadinessRepository", ex);
        }
    }

    private <T> T readJson(String raw, TypeReference<T> typeRef, T defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return jsonMapper.readValue(raw, typeRef);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to deserialize JSON in JdbcExamReadinessRepository", ex);
        }
    }
}
