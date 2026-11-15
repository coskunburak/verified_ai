package com.verifiedai.exam.infrastructure.persistence;

import com.verifiedai.exam.domain.AssessmentBlueprint;
import com.verifiedai.exam.domain.AssessmentBlueprintMode;
import com.verifiedai.exam.domain.AssessmentBlueprintRepository;
import com.verifiedai.exam.domain.AssessmentBlueprintStatus;
import com.verifiedai.exam.domain.BlueprintTopicRule;
import com.verifiedai.exam.domain.QuestionItemFormat;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class AssessmentBlueprintJdbcRepository implements AssessmentBlueprintRepository {

    private static final TypeReference<List<String>> FORMAT_LIST_TYPE = new TypeReference<>() {};

    private final JdbcClient jdbcClient;
    private final JsonMapper jsonMapper;

    public AssessmentBlueprintJdbcRepository(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient cannot be null");
        this.jsonMapper = JsonMapper.builder()
                .findAndAddModules()
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }

    @Override
    @Transactional
    public AssessmentBlueprint save(AssessmentBlueprint blueprint) {
        jdbcClient.sql("""
                INSERT INTO exam_assessment_blueprints (
                    id, exam_definition_id, exam_version_number, exam_section_id,
                    blueprint_code, title, description, blueprint_mode,
                    total_question_count, base_duration_seconds,
                    easy_target_bps, medium_target_bps, hard_target_bps, expert_target_bps, difficulty_tolerance_bps,
                    recall_target_bps, comprehension_target_bps, application_target_bps, analysis_target_bps, evaluation_target_bps,
                    allow_recently_seen_cooldown_days, shuffle_questions, shuffle_options,
                    status, blueprint_hash, training_eligible, created_at, updated_at
                ) VALUES (
                    :id, :examDefinitionId, :examVersionNumber, :examSectionId,
                    :blueprintCode, :title, :description, :blueprintMode,
                    :totalQuestionCount, :baseDurationSeconds,
                    :easyTargetBps, :mediumTargetBps, :hardTargetBps, :expertTargetBps, :difficultyToleranceBps,
                    :recallTargetBps, :comprehensionTargetBps, :applicationTargetBps, :analysisTargetBps, :evaluationTargetBps,
                    :allowRecentlySeenCooldownDays, :shuffleQuestions, :shuffleOptions,
                    :status, :blueprintHash, FALSE, :createdAt, :updatedAt
                )
                ON CONFLICT (id) DO UPDATE SET
                    status = EXCLUDED.status,
                    blueprint_hash = EXCLUDED.blueprint_hash,
                    updated_at = EXCLUDED.updated_at
                """)
                .param("id", blueprint.id())
                .param("examDefinitionId", blueprint.examDefinitionId())
                .param("examVersionNumber", blueprint.examVersionNumber())
                .param("examSectionId", blueprint.examSectionId())
                .param("blueprintCode", blueprint.blueprintCode())
                .param("title", blueprint.title())
                .param("description", blueprint.description())
                .param("blueprintMode", blueprint.blueprintMode().name())
                .param("totalQuestionCount", blueprint.totalQuestionCount())
                .param("baseDurationSeconds", blueprint.baseDurationSeconds())
                .param("easyTargetBps", blueprint.easyTargetBps())
                .param("mediumTargetBps", blueprint.mediumTargetBps())
                .param("hardTargetBps", blueprint.hardTargetBps())
                .param("expertTargetBps", blueprint.expertTargetBps())
                .param("difficultyToleranceBps", blueprint.difficultyToleranceBps())
                .param("recallTargetBps", blueprint.recallTargetBps())
                .param("comprehensionTargetBps", blueprint.comprehensionTargetBps())
                .param("applicationTargetBps", blueprint.applicationTargetBps())
                .param("analysisTargetBps", blueprint.analysisTargetBps())
                .param("evaluationTargetBps", blueprint.evaluationTargetBps())
                .param("allowRecentlySeenCooldownDays", blueprint.allowRecentlySeenCooldownDays())
                .param("shuffleQuestions", blueprint.shuffleQuestions())
                .param("shuffleOptions", blueprint.shuffleOptions())
                .param("status", blueprint.status().name())
                .param("blueprintHash", blueprint.blueprintHash())
                .param("createdAt", Timestamp.from(blueprint.createdAt()))
                .param("updatedAt", Timestamp.from(blueprint.updatedAt()))
                .update();

        jdbcClient.sql("DELETE FROM exam_blueprint_topic_rules WHERE blueprint_id = :blueprintId")
                .param("blueprintId", blueprint.id())
                .update();

        for (BlueprintTopicRule rule : blueprint.topicRules()) {
            UUID ruleId = rule.id() == null ? UUID.randomUUID() : rule.id();
            List<String> formatNames = rule.requiredItemFormats().stream().map(Enum::name).toList();
            jdbcClient.sql("""
                    INSERT INTO exam_blueprint_topic_rules (
                        id, blueprint_id, topic_code, target_weight_bps,
                        min_question_count, max_question_count, required_item_formats_json,
                        rule_order, training_eligible
                    ) VALUES (
                        :id, :blueprintId, :topicCode, :targetWeightBps,
                        :minQuestionCount, :maxQuestionCount, CAST(:requiredItemFormatsJson AS JSONB),
                        :ruleOrder, FALSE
                    )
                    """)
                    .param("id", ruleId)
                    .param("blueprintId", blueprint.id())
                    .param("topicCode", rule.topicCode())
                    .param("targetWeightBps", rule.targetWeightBps())
                    .param("minQuestionCount", rule.minQuestionCount())
                    .param("maxQuestionCount", rule.maxQuestionCount())
                    .param("requiredItemFormatsJson", writeJson(formatNames))
                    .param("ruleOrder", rule.ruleOrder())
                    .update();
        }

        return findById(blueprint.id()).orElse(blueprint);
    }

    @Override
    public Optional<AssessmentBlueprint> findById(UUID id) {
        return jdbcClient.sql("SELECT * FROM exam_assessment_blueprints WHERE id = :id")
                .param("id", id)
                .query(this::mapBlueprintRow)
                .optional();
    }

    @Override
    public Optional<AssessmentBlueprint> findByBlueprintCode(String blueprintCode) {
        return jdbcClient.sql("SELECT * FROM exam_assessment_blueprints WHERE blueprint_code = :blueprintCode")
                .param("blueprintCode", blueprintCode)
                .query(this::mapBlueprintRow)
                .optional();
    }

    @Override
    public List<AssessmentBlueprint> findByExamDefinition(
            UUID examDefinitionId,
            Integer examVersionNumber,
            AssessmentBlueprintStatus status,
            AssessmentBlueprintMode blueprintMode
    ) {
        StringBuilder sql = new StringBuilder("""
                SELECT * FROM exam_assessment_blueprints
                WHERE exam_definition_id = :examDefinitionId
                """);
        var params = new LinkedHashMap<String, Object>();
        params.put("examDefinitionId", examDefinitionId);

        if (examVersionNumber != null) {
            sql.append(" AND exam_version_number = :examVersionNumber");
            params.put("examVersionNumber", examVersionNumber);
        }
        if (status != null) {
            sql.append(" AND status = :status");
            params.put("status", status.name());
        }
        if (blueprintMode != null) {
            sql.append(" AND blueprint_mode = :blueprintMode");
            params.put("blueprintMode", blueprintMode.name());
        }
        sql.append(" ORDER BY blueprint_code ASC");

        var spec = jdbcClient.sql(sql.toString());
        for (var entry : params.entrySet()) {
            spec = spec.param(entry.getKey(), entry.getValue());
        }
        return spec.query(this::mapBlueprintRow).list();
    }

    private AssessmentBlueprint mapBlueprintRow(ResultSet rs, int rowNum) throws SQLException {
        UUID blueprintId = rs.getObject("id", UUID.class);
        List<BlueprintTopicRule> rules = loadTopicRules(blueprintId);
        return new AssessmentBlueprint(
                blueprintId,
                rs.getObject("exam_definition_id", UUID.class),
                rs.getInt("exam_version_number"),
                rs.getObject("exam_section_id", UUID.class),
                rs.getString("blueprint_code"),
                rs.getString("title"),
                rs.getString("description"),
                AssessmentBlueprintMode.valueOf(rs.getString("blueprint_mode")),
                rs.getInt("total_question_count"),
                rs.getInt("base_duration_seconds"),
                rs.getInt("easy_target_bps"),
                rs.getInt("medium_target_bps"),
                rs.getInt("hard_target_bps"),
                rs.getInt("expert_target_bps"),
                rs.getInt("difficulty_tolerance_bps"),
                rs.getInt("recall_target_bps"),
                rs.getInt("comprehension_target_bps"),
                rs.getInt("application_target_bps"),
                rs.getInt("analysis_target_bps"),
                rs.getInt("evaluation_target_bps"),
                rs.getInt("allow_recently_seen_cooldown_days"),
                rs.getBoolean("shuffle_questions"),
                rs.getBoolean("shuffle_options"),
                AssessmentBlueprintStatus.valueOf(rs.getString("status")),
                rs.getString("blueprint_hash") == null ? null : rs.getString("blueprint_hash").trim(),
                rs.getBoolean("training_eligible"),
                rules,
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }

    private List<BlueprintTopicRule> loadTopicRules(UUID blueprintId) {
        return jdbcClient.sql("""
                SELECT id, blueprint_id, topic_code, target_weight_bps,
                       min_question_count, max_question_count, required_item_formats_json,
                       rule_order, training_eligible
                FROM exam_blueprint_topic_rules
                WHERE blueprint_id = :blueprintId
                ORDER BY rule_order ASC
                """)
                .param("blueprintId", blueprintId)
                .query((rs, rowNum) -> {
                    List<String> formatStrs = readJson(
                            rs.getString("required_item_formats_json"),
                            FORMAT_LIST_TYPE,
                            List.of("SINGLE_CHOICE_MCQ")
                    );
                    List<QuestionItemFormat> formats = formatStrs.stream()
                            .map(QuestionItemFormat::valueOf)
                            .toList();
                    return new BlueprintTopicRule(
                            rs.getObject("id", UUID.class),
                            rs.getObject("blueprint_id", UUID.class),
                            rs.getString("topic_code"),
                            rs.getInt("target_weight_bps"),
                            rs.getInt("min_question_count"),
                            rs.getInt("max_question_count"),
                            formats,
                            rs.getInt("rule_order"),
                            rs.getBoolean("training_eligible")
                    );
                })
                .list();
    }

    private String writeJson(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to serialize JSON in AssessmentBlueprintJdbcRepository", ex);
        }
    }

    private <T> T readJson(String raw, TypeReference<T> typeRef, T defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return jsonMapper.readValue(raw, typeRef);
        } catch (JacksonException ex) {
            return defaultValue;
        }
    }
}
