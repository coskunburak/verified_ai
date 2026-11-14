package com.verifiedai.exam.infrastructure.persistence;

import com.verifiedai.exam.application.ExamRepository;
import com.verifiedai.exam.domain.ExamModels.CognitiveLevelMix;
import com.verifiedai.exam.domain.ExamModels.ExamDefinition;
import com.verifiedai.exam.domain.ExamModels.ExamFamily;
import com.verifiedai.exam.domain.ExamModels.ExamSectionSpec;
import com.verifiedai.exam.domain.ExamModels.ExamStatus;
import com.verifiedai.exam.domain.ExamModels.ExamTopicWeight;
import com.verifiedai.exam.domain.ExamModels.Jurisdiction;
import com.verifiedai.exam.domain.ExamModels.NavigationPolicy;
import com.verifiedai.exam.domain.ExamModels.ScoringFormulaType;
import com.verifiedai.exam.domain.ExamModels.ScoringParameters;
import com.verifiedai.exam.domain.ExamModels.StudentExamTargetRecord;
import com.verifiedai.exam.domain.ExamModels.TargetStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL JDBC repository for {@code exam_definitions}, {@code exam_topic_weights},
 * and {@code student_exam_targets}. Uses Jackson 3 ({@code tools.jackson.*}) exclusively.
 */
@Repository
public class ExamJdbcRepository implements ExamRepository {

    private static final TypeReference<List<ExamSectionSpec>> SECTIONS_TYPE = new TypeReference<>() {};
    private static final TypeReference<ScoringParameters> SCORING_PARAMS_TYPE = new TypeReference<>() {};
    private static final TypeReference<CognitiveLevelMix> COGNITIVE_MIX_TYPE = new TypeReference<>() {};

    private final JdbcClient jdbcClient;
    private final JsonMapper jsonMapper;

    public ExamJdbcRepository(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient cannot be null");
        this.jsonMapper = JsonMapper.builder()
                .findAndAddModules()
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }

    @Override
    public List<ExamDefinition> findCatalog(
            Jurisdiction jurisdiction,
            ExamFamily examFamily,
            boolean includeDeprecated
    ) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, exam_code, exam_version, jurisdiction, exam_family,
                       title_en, title_tr, status, total_questions, duration_minutes,
                       scoring_formula_type, wrong_penalty_factor, min_score, max_score, navigation_policy,
                       blueprint_hash, effective_from, deprecated_at, superseded_by_version,
                       sections_json::text AS sections_text,
                       scoring_parameters_json::text AS scoring_parameters_text,
                       training_eligible, created_at, updated_at
                FROM exam_definitions
                WHERE 1 = 1
                """);

        Map<String, Object> params = new LinkedHashMap<>();
        if (includeDeprecated) {
            sql.append(" AND status IN ('ACTIVE', 'DEPRECATED')");
        } else {
            sql.append(" AND status = 'ACTIVE'");
        }
        if (jurisdiction != null) {
            sql.append(" AND jurisdiction = :jurisdiction");
            params.put("jurisdiction", jurisdiction.name());
        }
        if (examFamily != null) {
            sql.append(" AND exam_family = :examFamily");
            params.put("examFamily", examFamily.name());
        }
        sql.append(" ORDER BY jurisdiction ASC, exam_family ASC, exam_code ASC, effective_from DESC");

        JdbcClient.StatementSpec spec = jdbcClient.sql(sql.toString());
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            spec = spec.param(entry.getKey(), entry.getValue());
        }

        List<ExamDefinition> definitions = spec.query(this::mapExamDefinitionWithoutWeights).list();
        if (definitions.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<ExamTopicWeight>> weightsByDef = loadWeightsForDefinitionIds(
                definitions.stream().map(ExamDefinition::id).toList()
        );
        return definitions.stream()
                .map(def -> attachWeights(def, weightsByDef.getOrDefault(def.id(), List.of())))
                .toList();
    }

    @Override
    public Optional<ExamDefinition> findLatestByCode(String examCode, boolean includeDeprecated) {
        String sql = """
                SELECT id, exam_code, exam_version, jurisdiction, exam_family,
                       title_en, title_tr, status, total_questions, duration_minutes,
                       scoring_formula_type, wrong_penalty_factor, min_score, max_score, navigation_policy,
                       blueprint_hash, effective_from, deprecated_at, superseded_by_version,
                       sections_json::text AS sections_text,
                       scoring_parameters_json::text AS scoring_parameters_text,
                       training_eligible, created_at, updated_at
                FROM exam_definitions
                WHERE exam_code = :examCode
                  AND (%s)
                ORDER BY CASE WHEN status = 'ACTIVE' THEN 0 ELSE 1 END ASC, effective_from DESC
                LIMIT 1
                """.formatted(includeDeprecated ? "status IN ('ACTIVE', 'DEPRECATED')" : "status = 'ACTIVE'");

        return jdbcClient.sql(sql)
                .param("examCode", examCode)
                .query(this::mapExamDefinitionWithoutWeights)
                .optional()
                .map(def -> attachWeights(def, loadWeightsForSingleDefinition(def.id())));
    }

    @Override
    public Optional<ExamDefinition> findByCodeAndVersion(String examCode, String examVersion) {
        return jdbcClient.sql("""
                SELECT id, exam_code, exam_version, jurisdiction, exam_family,
                       title_en, title_tr, status, total_questions, duration_minutes,
                       scoring_formula_type, wrong_penalty_factor, min_score, max_score, navigation_policy,
                       blueprint_hash, effective_from, deprecated_at, superseded_by_version,
                       sections_json::text AS sections_text,
                       scoring_parameters_json::text AS scoring_parameters_text,
                       training_eligible, created_at, updated_at
                FROM exam_definitions
                WHERE exam_code = :examCode AND exam_version = :examVersion
                LIMIT 1
                """)
                .param("examCode", examCode)
                .param("examVersion", examVersion)
                .query(this::mapExamDefinitionWithoutWeights)
                .optional()
                .map(def -> attachWeights(def, loadWeightsForSingleDefinition(def.id())));
    }

    @Override
    public Optional<StudentExamTargetRecord> findTargetByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey) {
        return jdbcClient.sql("""
                SELECT target_id, user_id, exam_definition_id, exam_code, exam_version,
                       blueprint_hash, target_score, target_date, priority_rank,
                       target_status, is_primary, idempotency_key,
                       snapshot_payload_json::text AS snapshot_payload_text,
                       superseded_at, llm_tokens_used, training_eligible, created_at, updated_at
                FROM student_exam_targets
                WHERE user_id = :userId AND idempotency_key = :idempotencyKey
                LIMIT 1
                """)
                .param("userId", userId)
                .param("idempotencyKey", idempotencyKey)
                .query(this::mapStudentTarget)
                .optional();
    }

    @Override
    public Optional<StudentExamTargetRecord> findTargetById(UUID targetId) {
        return jdbcClient.sql("""
                SELECT target_id, user_id, exam_definition_id, exam_code, exam_version,
                       blueprint_hash, target_score, target_date, priority_rank,
                       target_status, is_primary, idempotency_key,
                       snapshot_payload_json::text AS snapshot_payload_text,
                       superseded_at, llm_tokens_used, training_eligible, created_at, updated_at
                FROM student_exam_targets
                WHERE target_id = :targetId
                LIMIT 1
                """)
                .param("targetId", targetId)
                .query(this::mapStudentTarget)
                .optional();
    }

    @Override
    public List<StudentExamTargetRecord> findTargetsByUserId(UUID userId) {
        return jdbcClient.sql("""
                SELECT target_id, user_id, exam_definition_id, exam_code, exam_version,
                       blueprint_hash, target_score, target_date, priority_rank,
                       target_status, is_primary, idempotency_key,
                       snapshot_payload_json::text AS snapshot_payload_text,
                       superseded_at, llm_tokens_used, training_eligible, created_at, updated_at
                FROM student_exam_targets
                WHERE user_id = :userId
                ORDER BY CASE WHEN target_status = 'ACTIVE' THEN 0 ELSE 1 END ASC,
                         priority_rank ASC,
                         created_at DESC
                """)
                .param("userId", userId)
                .query(this::mapStudentTarget)
                .list();
    }

    @Override
    public int supersedeActivePrimaryTargets(UUID userId, Instant supersededAt) {
        return jdbcClient.sql("""
                UPDATE student_exam_targets
                SET target_status = 'SUPERSEDED',
                    superseded_at = :supersededAt,
                    updated_at = :supersededAt
                WHERE user_id = :userId
                  AND target_status = 'ACTIVE'
                  AND is_primary = TRUE
                """)
                .param("userId", userId)
                .param("supersededAt", Timestamp.from(supersededAt))
                .update();
    }

    @Override
    public StudentExamTargetRecord insertTarget(StudentExamTargetRecord record) {
        jdbcClient.sql("""
                INSERT INTO student_exam_targets (
                    target_id, user_id, exam_definition_id, exam_code, exam_version,
                    blueprint_hash, target_score, target_date, priority_rank,
                    target_status, is_primary, idempotency_key,
                    snapshot_payload_json, superseded_at,
                    llm_tokens_used, training_eligible, created_at, updated_at
                ) VALUES (
                    :targetId, :userId, :examDefinitionId, :examCode, :examVersion,
                    :blueprintHash, :targetScore, :targetDate, :priorityRank,
                    :targetStatus, :isPrimary, :idempotencyKey,
                    CAST(:snapshotPayloadJson AS jsonb), :supersededAt,
                    0, FALSE, :createdAt, :updatedAt
                )
                """)
                .param("targetId", record.targetId())
                .param("userId", record.userId())
                .param("examDefinitionId", record.examDefinitionId())
                .param("examCode", record.examCode())
                .param("examVersion", record.examVersion())
                .param("blueprintHash", record.blueprintHash())
                .param("targetScore", record.targetScore())
                .param("targetDate", Date.valueOf(record.targetDate()))
                .param("priorityRank", record.priorityRank())
                .param("targetStatus", record.targetStatus().name())
                .param("isPrimary", record.isPrimary())
                .param("idempotencyKey", record.idempotencyKey())
                .param("snapshotPayloadJson", record.snapshotPayloadJson())
                .param("supersededAt", record.supersededAt() != null ? Timestamp.from(record.supersededAt()) : null)
                .param("createdAt", Timestamp.from(record.createdAt()))
                .param("updatedAt", Timestamp.from(record.updatedAt()))
                .update();
        return record;
    }

    @Override
    public int deleteTargetsByUserId(UUID userId) {
        return jdbcClient.sql("DELETE FROM student_exam_targets WHERE user_id = :userId")
                .param("userId", userId)
                .update();
    }

    private List<ExamTopicWeight> loadWeightsForSingleDefinition(UUID examDefinitionId) {
        return jdbcClient.sql("""
                SELECT id, exam_definition_id, section_code, skill_id, domain_code,
                       question_count_weight, min_questions, max_questions,
                       cognitive_level_mix_json::text AS cognitive_level_mix_text,
                       sort_order
                FROM exam_topic_weights
                WHERE exam_definition_id = :examDefinitionId
                ORDER BY sort_order ASC, section_code ASC, skill_id ASC
                """)
                .param("examDefinitionId", examDefinitionId)
                .query(this::mapTopicWeight)
                .list();
    }

    private Map<UUID, List<ExamTopicWeight>> loadWeightsForDefinitionIds(List<UUID> definitionIds) {
        if (definitionIds.isEmpty()) {
            return Map.of();
        }
        List<ExamTopicWeight> allWeights = jdbcClient.sql("""
                SELECT id, exam_definition_id, section_code, skill_id, domain_code,
                       question_count_weight, min_questions, max_questions,
                       cognitive_level_mix_json::text AS cognitive_level_mix_text,
                       sort_order
                FROM exam_topic_weights
                WHERE exam_definition_id IN (:ids)
                ORDER BY sort_order ASC, section_code ASC, skill_id ASC
                """)
                .param("ids", definitionIds)
                .query(this::mapTopicWeight)
                .list();

        Map<UUID, List<ExamTopicWeight>> grouped = new LinkedHashMap<>();
        for (ExamTopicWeight weight : allWeights) {
            grouped.computeIfAbsent(weight.examDefinitionId(), ignored -> new ArrayList<>()).add(weight);
        }
        return grouped;
    }

    private ExamDefinition mapExamDefinitionWithoutWeights(ResultSet rs, int rowNum) throws SQLException {
        Timestamp deprecatedTs = rs.getTimestamp("deprecated_at");
        return new ExamDefinition(
                rs.getObject("id", UUID.class),
                rs.getString("exam_code"),
                rs.getString("exam_version"),
                Jurisdiction.valueOf(rs.getString("jurisdiction")),
                ExamFamily.valueOf(rs.getString("exam_family")),
                rs.getString("title_en"),
                rs.getString("title_tr"),
                ExamStatus.valueOf(rs.getString("status")),
                rs.getInt("total_questions"),
                rs.getInt("duration_minutes"),
                ScoringFormulaType.valueOf(rs.getString("scoring_formula_type")),
                rs.getBigDecimal("wrong_penalty_factor"),
                rs.getBigDecimal("min_score"),
                rs.getBigDecimal("max_score"),
                NavigationPolicy.valueOf(rs.getString("navigation_policy")),
                rs.getString("blueprint_hash"),
                rs.getTimestamp("effective_from").toInstant(),
                deprecatedTs == null ? null : deprecatedTs.toInstant(),
                rs.getString("superseded_by_version"),
                readJson(rs.getString("sections_text"), SECTIONS_TYPE),
                readJson(rs.getString("scoring_parameters_text"), SCORING_PARAMS_TYPE),
                List.of(),
                rs.getBoolean("training_eligible"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }

    private ExamTopicWeight mapTopicWeight(ResultSet rs, int rowNum) throws SQLException {
        return new ExamTopicWeight(
                rs.getObject("id", UUID.class),
                rs.getObject("exam_definition_id", UUID.class),
                rs.getString("section_code"),
                rs.getString("skill_id"),
                rs.getString("domain_code"),
                rs.getBigDecimal("question_count_weight"),
                rs.getInt("min_questions"),
                rs.getInt("max_questions"),
                readJson(rs.getString("cognitive_level_mix_text"), COGNITIVE_MIX_TYPE),
                rs.getInt("sort_order")
        );
    }

    private StudentExamTargetRecord mapStudentTarget(ResultSet rs, int rowNum) throws SQLException {
        Timestamp supersededTs = rs.getTimestamp("superseded_at");
        return new StudentExamTargetRecord(
                rs.getObject("target_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("exam_definition_id", UUID.class),
                rs.getString("exam_code"),
                rs.getString("exam_version"),
                rs.getString("blueprint_hash"),
                rs.getBigDecimal("target_score"),
                rs.getDate("target_date").toLocalDate(),
                rs.getInt("priority_rank"),
                TargetStatus.valueOf(rs.getString("target_status")),
                rs.getBoolean("is_primary"),
                rs.getString("idempotency_key"),
                rs.getString("snapshot_payload_text"),
                supersededTs == null ? null : supersededTs.toInstant(),
                rs.getInt("llm_tokens_used"),
                rs.getBoolean("training_eligible"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }

    private static ExamDefinition attachWeights(ExamDefinition def, List<ExamTopicWeight> weights) {
        return new ExamDefinition(
                def.id(),
                def.examCode(),
                def.examVersion(),
                def.jurisdiction(),
                def.examFamily(),
                def.titleEn(),
                def.titleTr(),
                def.status(),
                def.totalQuestions(),
                def.durationMinutes(),
                def.scoringFormulaType(),
                def.wrongPenaltyFactor(),
                def.minScore(),
                def.maxScore(),
                def.navigationPolicy(),
                def.blueprintHash(),
                def.effectiveFrom(),
                def.deprecatedAt(),
                def.supersededByVersion(),
                def.sections(),
                def.scoringParameters(),
                weights,
                def.trainingEligible(),
                def.createdAt(),
                def.updatedAt()
        );
    }

    private <T> T readJson(String rawJson, TypeReference<T> typeReference) {
        try {
            return jsonMapper.readValue(rawJson, typeReference);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to deserialize exam JSON payload", ex);
        }
    }
}
