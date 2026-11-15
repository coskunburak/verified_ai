package com.verifiedai.exam.infrastructure.persistence;

import com.verifiedai.exam.domain.AppliedAccommodationSnapshot;
import com.verifiedai.exam.domain.AssembledAssessment;
import com.verifiedai.exam.domain.AssembledAssessmentItem;
import com.verifiedai.exam.domain.AssembledAssessmentRepository;
import com.verifiedai.exam.domain.AssembledAssessmentStatus;
import com.verifiedai.exam.domain.AssemblyDistributionSummary;
import com.verifiedai.exam.domain.AssessmentBlueprintMode;
import com.verifiedai.exam.domain.CognitiveTaxonomyLevel;
import com.verifiedai.exam.domain.QuestionBankItem;
import com.verifiedai.exam.domain.QuestionBankRepository;
import com.verifiedai.exam.domain.QuestionDifficultyBand;
import com.verifiedai.exam.domain.QuestionItemFormat;
import com.verifiedai.exam.domain.QuestionOption;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class AssembledAssessmentJdbcRepository implements AssembledAssessmentRepository {

    private static final TypeReference<AppliedAccommodationSnapshot> ACCOMMODATIONS_TYPE = new TypeReference<>() {};
    private static final TypeReference<AssemblyDistributionSummary> DISTRIBUTION_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<String>> OPTION_ORDER_TYPE = new TypeReference<>() {};

    private final JdbcClient jdbcClient;
    private final QuestionBankRepository questionBankRepository;
    private final JsonMapper jsonMapper;

    public AssembledAssessmentJdbcRepository(
            JdbcClient jdbcClient,
            QuestionBankRepository questionBankRepository
    ) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient cannot be null");
        this.questionBankRepository = Objects.requireNonNull(questionBankRepository, "questionBankRepository cannot be null");
        this.jsonMapper = JsonMapper.builder()
                .findAndAddModules()
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }

    @Override
    @Transactional
    public AssembledAssessment save(AssembledAssessment assessment) {
        Optional<AssembledAssessment> existing = findByUserIdAndBlueprintIdAndSeed(
                assessment.userId(),
                assessment.blueprintId(),
                assessment.assemblySeed()
        );
        if (existing.isPresent()) {
            return existing.get();
        }

        jdbcClient.sql("""
                INSERT INTO exam_assembled_assessments (
                    id, user_id, candidate_profile_id, blueprint_id,
                    exam_definition_id, exam_version_number,
                    assembly_seed, reproducibility_hash,
                    total_question_count, base_duration_seconds,
                    time_multiplier, effective_duration_seconds,
                    accommodations_snapshot_json, distribution_summary_json,
                    status, assembled_at, expires_at, training_eligible
                ) VALUES (
                    :id, :userId, :candidateProfileId, :blueprintId,
                    :examDefinitionId, :examVersionNumber,
                    :assemblySeed, :reproducibilityHash,
                    :totalQuestionCount, :baseDurationSeconds,
                    :timeMultiplier, :effectiveDurationSeconds,
                    CAST(:accommodationsSnapshotJson AS JSONB), CAST(:distributionSummaryJson AS JSONB),
                    :status, :assembledAt, :expiresAt, FALSE
                )
                """)
                .param("id", assessment.id())
                .param("userId", assessment.userId())
                .param("candidateProfileId", assessment.candidateProfileId())
                .param("blueprintId", assessment.blueprintId())
                .param("examDefinitionId", assessment.examDefinitionId())
                .param("examVersionNumber", assessment.examVersionNumber())
                .param("assemblySeed", assessment.assemblySeed())
                .param("reproducibilityHash", assessment.reproducibilityHash())
                .param("totalQuestionCount", assessment.totalQuestionCount())
                .param("baseDurationSeconds", assessment.baseDurationSeconds())
                .param("timeMultiplier", assessment.timeMultiplier())
                .param("effectiveDurationSeconds", assessment.effectiveDurationSeconds())
                .param("accommodationsSnapshotJson", writeJson(assessment.accommodationsSnapshot()))
                .param("distributionSummaryJson", writeJson(assessment.distributionSummary()))
                .param("status", assessment.status().name())
                .param("assembledAt", Timestamp.from(assessment.assembledAt()))
                .param("expiresAt", Timestamp.from(assessment.expiresAt()))
                .update();

        for (AssembledAssessmentItem item : assessment.items()) {
            jdbcClient.sql("""
                    INSERT INTO exam_assembled_assessment_items (
                        id, assembled_assessment_id, question_item_id, sequence_number,
                        topic_code, difficulty_band, cognitive_level,
                        option_order_json, allocated_points, training_eligible
                    ) VALUES (
                        :id, :assembledAssessmentId, :questionItemId, :sequenceNumber,
                        :topicCode, :difficultyBand, :cognitiveLevel,
                        CAST(:optionOrderJson AS JSONB), :allocatedPoints, FALSE
                    )
                    """)
                    .param("id", item.id())
                    .param("assembledAssessmentId", assessment.id())
                    .param("questionItemId", item.questionItemId())
                    .param("sequenceNumber", item.sequenceNumber())
                    .param("topicCode", item.topicCode())
                    .param("difficultyBand", item.difficultyBand().name())
                    .param("cognitiveLevel", item.cognitiveLevel().name())
                    .param("optionOrderJson", writeJson(item.optionOrder()))
                    .param("allocatedPoints", item.allocatedPoints())
                    .update();
        }

        return findById(assessment.id()).orElse(assessment);
    }

    @Override
    public Optional<AssembledAssessment> findById(UUID id) {
        return jdbcClient.sql("""
                SELECT a.*, b.blueprint_code, b.title AS blueprint_title, b.blueprint_mode
                FROM exam_assembled_assessments a
                JOIN exam_assessment_blueprints b ON b.id = a.blueprint_id
                WHERE a.id = :id
                """)
                .param("id", id)
                .query(this::mapAssembledRow)
                .optional();
    }

    @Override
    public Optional<AssembledAssessment> findByUserIdAndBlueprintIdAndSeed(
            UUID userId,
            UUID blueprintId,
            String assemblySeed
    ) {
        return jdbcClient.sql("""
                SELECT a.*, b.blueprint_code, b.title AS blueprint_title, b.blueprint_mode
                FROM exam_assembled_assessments a
                JOIN exam_assessment_blueprints b ON b.id = a.blueprint_id
                WHERE a.user_id = :userId
                  AND a.blueprint_id = :blueprintId
                  AND a.assembly_seed = :assemblySeed
                """)
                .param("userId", userId)
                .param("blueprintId", blueprintId)
                .param("assemblySeed", assemblySeed.trim())
                .query(this::mapAssembledRow)
                .optional();
    }

    @Override
    public List<AssembledAssessment> findByUserId(UUID userId, int limit) {
        return jdbcClient.sql("""
                SELECT a.*, b.blueprint_code, b.title AS blueprint_title, b.blueprint_mode
                FROM exam_assembled_assessments a
                JOIN exam_assessment_blueprints b ON b.id = a.blueprint_id
                WHERE a.user_id = :userId
                ORDER BY a.assembled_at DESC
                LIMIT :limit
                """)
                .param("userId", userId)
                .param("limit", Math.max(1, limit))
                .query(this::mapAssembledRow)
                .list();
    }

    @Override
    public Set<UUID> findRecentlySeenQuestionIds(UUID userId, UUID examDefinitionId, Instant since) {
        List<UUID> ids = jdbcClient.sql("""
                SELECT DISTINCT ai.question_item_id
                FROM exam_assembled_assessment_items ai
                JOIN exam_assembled_assessments a ON a.id = ai.assembled_assessment_id
                WHERE a.user_id = :userId
                  AND a.exam_definition_id = :examDefinitionId
                  AND a.assembled_at >= :since
                """)
                .param("userId", userId)
                .param("examDefinitionId", examDefinitionId)
                .param("since", Timestamp.from(since))
                .query(UUID.class)
                .list();
        return new HashSet<>(ids);
    }

    @Override
    @Transactional
    public int deleteByUserId(UUID userId) {
        jdbcClient.sql("""
                DELETE FROM exam_assembled_assessment_items
                WHERE assembled_assessment_id IN (
                    SELECT id FROM exam_assembled_assessments WHERE user_id = :userId
                )
                """)
                .param("userId", userId)
                .update();
        return jdbcClient.sql("""
                DELETE FROM exam_assembled_assessments WHERE user_id = :userId
                """)
                .param("userId", userId)
                .update();
    }

    private AssembledAssessment mapAssembledRow(ResultSet rs, int rowNum) throws SQLException {
        UUID assessmentId = rs.getObject("id", UUID.class);
        AppliedAccommodationSnapshot acc = readJson(
                rs.getString("accommodations_snapshot_json"),
                ACCOMMODATIONS_TYPE,
                AppliedAccommodationSnapshot.standardDefaults()
        );
        AssemblyDistributionSummary dist = readJson(
                rs.getString("distribution_summary_json"),
                DISTRIBUTION_TYPE,
                new AssemblyDistributionSummary(Map.of(), Map.of(), Map.of(), Map.of(), 0)
        );
        List<AssembledAssessmentItem> items = loadItems(assessmentId);

        return new AssembledAssessment(
                assessmentId,
                rs.getObject("user_id", UUID.class),
                rs.getObject("candidate_profile_id", UUID.class),
                rs.getObject("blueprint_id", UUID.class),
                rs.getString("blueprint_code"),
                rs.getString("blueprint_title"),
                AssessmentBlueprintMode.valueOf(rs.getString("blueprint_mode")),
                rs.getObject("exam_definition_id", UUID.class),
                rs.getInt("exam_version_number"),
                rs.getString("assembly_seed"),
                rs.getString("reproducibility_hash") == null ? null : rs.getString("reproducibility_hash").trim(),
                rs.getInt("total_question_count"),
                rs.getInt("base_duration_seconds"),
                rs.getBigDecimal("time_multiplier"),
                rs.getInt("effective_duration_seconds"),
                acc,
                dist,
                AssembledAssessmentStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("assembled_at").toInstant(),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getBoolean("training_eligible"),
                items
        );
    }

    private List<AssembledAssessmentItem> loadItems(UUID assessmentId) {
        return jdbcClient.sql("""
                SELECT id, assembled_assessment_id, question_item_id, sequence_number,
                       topic_code, difficulty_band, cognitive_level,
                       option_order_json, allocated_points, training_eligible
                FROM exam_assembled_assessment_items
                WHERE assembled_assessment_id = :assessmentId
                ORDER BY sequence_number ASC
                """)
                .param("assessmentId", assessmentId)
                .query((rs, rowNum) -> {
                    UUID qItemId = rs.getObject("question_item_id", UUID.class);
                    Optional<QuestionBankItem> qItemOpt = questionBankRepository.findById(qItemId);
                    List<String> optionOrder = readJson(
                            rs.getString("option_order_json"),
                            OPTION_ORDER_TYPE,
                            List.of()
                    );
                    List<QuestionOption> orderedOptions = new ArrayList<>();
                    if (qItemOpt.isPresent()) {
                        Map<String, QuestionOption> byKey = new HashMap<>();
                        for (QuestionOption opt : qItemOpt.get().options()) {
                            byKey.put(opt.optionKey(), opt);
                        }
                        for (String key : optionOrder) {
                            QuestionOption matched = byKey.get(key);
                            if (matched != null) {
                                orderedOptions.add(matched);
                            }
                        }
                    }
                    return new AssembledAssessmentItem(
                            rs.getObject("id", UUID.class),
                            rs.getObject("assembled_assessment_id", UUID.class),
                            qItemId,
                            qItemOpt.map(QuestionBankItem::questionCode).orElse("UNKNOWN"),
                            rs.getInt("sequence_number"),
                            rs.getString("topic_code"),
                            qItemOpt.map(QuestionBankItem::subtopicCode).orElse(null),
                            qItemOpt.map(QuestionBankItem::itemFormat).orElse(QuestionItemFormat.SINGLE_CHOICE_MCQ),
                            QuestionDifficultyBand.valueOf(rs.getString("difficulty_band")),
                            CognitiveTaxonomyLevel.valueOf(rs.getString("cognitive_level")),
                            qItemOpt.map(QuestionBankItem::stemMarkdown).orElse("Question stem"),
                            qItemOpt.map(QuestionBankItem::stemLatex).orElse(null),
                            qItemOpt.map(QuestionBankItem::altTextDescription).orElse(null),
                            qItemOpt.map(QuestionBankItem::hasVisualDiagram).orElse(false),
                            optionOrder,
                            orderedOptions,
                            rs.getBigDecimal("allocated_points"),
                            qItemOpt.map(QuestionBankItem::estimatedTimeSeconds).orElse(60),
                            rs.getBoolean("training_eligible")
                    );
                })
                .list();
    }

    private String writeJson(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to serialize JSON in AssembledAssessmentJdbcRepository", ex);
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
