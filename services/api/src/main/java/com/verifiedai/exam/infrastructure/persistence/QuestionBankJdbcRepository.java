package com.verifiedai.exam.infrastructure.persistence;

import com.verifiedai.exam.domain.CognitiveTaxonomyLevel;
import com.verifiedai.exam.domain.ProvenanceVerificationStatus;
import com.verifiedai.exam.domain.QuestionBankItem;
import com.verifiedai.exam.domain.QuestionBankRepository;
import com.verifiedai.exam.domain.QuestionDifficultyBand;
import com.verifiedai.exam.domain.QuestionItemFormat;
import com.verifiedai.exam.domain.QuestionItemStatus;
import com.verifiedai.exam.domain.QuestionLicenseClassification;
import com.verifiedai.exam.domain.QuestionOption;
import com.verifiedai.exam.domain.QuestionProvenanceRecord;
import com.verifiedai.exam.domain.QuestionProvenanceSourceType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
public class QuestionBankJdbcRepository implements QuestionBankRepository {

    private static final TypeReference<Map<String, Object>> RUBRIC_TYPE = new TypeReference<>() {};

    private final JdbcClient jdbcClient;
    private final JsonMapper jsonMapper;

    public QuestionBankJdbcRepository(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient cannot be null");
        this.jsonMapper = JsonMapper.builder()
                .findAndAddModules()
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }

    @Override
    @Transactional
    public QuestionBankItem save(QuestionBankItem item) {
        String rubricJson = writeJson(item.structuredRubric() == null ? Map.of() : item.structuredRubric());

        jdbcClient.sql("""
                INSERT INTO exam_question_bank_items (
                    id, exam_definition_id, exam_version_number, exam_section_id,
                    question_code, topic_code, subtopic_code, item_format,
                    difficulty_band, difficulty_b_value, discrimination_a_value, guessing_c_value,
                    cognitive_level, stem_markdown, stem_latex, alt_text_description, has_visual_diagram,
                    numeric_correct_answer, numeric_tolerance, structured_rubric_json,
                    solution_explanation_markdown, estimated_time_seconds, max_points, negative_marking_weight,
                    status, content_sha256, exposure_count, training_eligible, created_at, updated_at
                ) VALUES (
                    :id, :examDefinitionId, :examVersionNumber, :examSectionId,
                    :questionCode, :topicCode, :subtopicCode, :itemFormat,
                    :difficultyBand, :difficultyBValue, :discriminationAValue, :guessingCValue,
                    :cognitiveLevel, :stemMarkdown, :stemLatex, :altTextDescription, :hasVisualDiagram,
                    :numericCorrectAnswer, :numericTolerance, CAST(:structuredRubricJson AS JSONB),
                    :solutionExplanationMarkdown, :estimatedTimeSeconds, :maxPoints, :negativeMarkingWeight,
                    :status, :contentSha256, :exposureCount, FALSE, :createdAt, :updatedAt
                )
                ON CONFLICT (id) DO UPDATE SET
                    status = EXCLUDED.status,
                    content_sha256 = EXCLUDED.content_sha256,
                    exposure_count = EXCLUDED.exposure_count,
                    updated_at = EXCLUDED.updated_at
                """)
                .param("id", item.id())
                .param("examDefinitionId", item.examDefinitionId())
                .param("examVersionNumber", item.examVersionNumber())
                .param("examSectionId", item.examSectionId())
                .param("questionCode", item.questionCode())
                .param("topicCode", item.topicCode())
                .param("subtopicCode", item.subtopicCode())
                .param("itemFormat", item.itemFormat().name())
                .param("difficultyBand", item.difficultyBand().name())
                .param("difficultyBValue", item.difficultyBValue())
                .param("discriminationAValue", item.discriminationAValue())
                .param("guessingCValue", item.guessingCValue())
                .param("cognitiveLevel", item.cognitiveLevel().name())
                .param("stemMarkdown", item.stemMarkdown())
                .param("stemLatex", item.stemLatex())
                .param("altTextDescription", item.altTextDescription())
                .param("hasVisualDiagram", item.hasVisualDiagram())
                .param("numericCorrectAnswer", item.numericCorrectAnswer())
                .param("numericTolerance", item.numericTolerance())
                .param("structuredRubricJson", rubricJson)
                .param("solutionExplanationMarkdown", item.solutionExplanationMarkdown())
                .param("estimatedTimeSeconds", item.estimatedTimeSeconds())
                .param("maxPoints", item.maxPoints())
                .param("negativeMarkingWeight", item.negativeMarkingWeight())
                .param("status", item.status().name())
                .param("contentSha256", item.contentSha256())
                .param("exposureCount", item.exposureCount())
                .param("createdAt", Timestamp.from(item.createdAt()))
                .param("updatedAt", Timestamp.from(item.updatedAt()))
                .update();

        jdbcClient.sql("DELETE FROM exam_question_options WHERE question_item_id = :itemId")
                .param("itemId", item.id())
                .update();

        for (QuestionOption opt : item.options()) {
            UUID optId = opt.id() == null ? UUID.randomUUID() : opt.id();
            jdbcClient.sql("""
                    INSERT INTO exam_question_options (
                        id, question_item_id, option_key, display_order,
                        content_markdown, content_latex, is_correct, distractor_rationale, training_eligible
                    ) VALUES (
                        :id, :questionItemId, :optionKey, :displayOrder,
                        :contentMarkdown, :contentLatex, :isCorrect, :distractorRationale, FALSE
                    )
                    """)
                    .param("id", optId)
                    .param("questionItemId", item.id())
                    .param("optionKey", opt.optionKey())
                    .param("displayOrder", opt.displayOrder())
                    .param("contentMarkdown", opt.contentMarkdown())
                    .param("contentLatex", opt.contentLatex())
                    .param("isCorrect", opt.isCorrect())
                    .param("distractorRationale", opt.distractorRationale())
                    .update();
        }

        if (item.provenance() != null) {
            QuestionProvenanceRecord p = item.provenance();
            UUID provId = p.id() == null ? UUID.randomUUID() : p.id();
            jdbcClient.sql("""
                    INSERT INTO exam_question_provenance_records (
                        id, question_item_id, source_type, license_classification,
                        copyright_holder, source_reference, author_id, reviewer_id,
                        verification_status, provenance_hash, verified_at, training_eligible, created_at
                    ) VALUES (
                        :id, :questionItemId, :sourceType, :licenseClassification,
                        :copyrightHolder, :sourceReference, :authorId, :reviewerId,
                        :verificationStatus, :provenanceHash, :verifiedAt, FALSE, :createdAt
                    )
                    ON CONFLICT (question_item_id) DO UPDATE SET
                        verification_status = EXCLUDED.verification_status,
                        provenance_hash = EXCLUDED.provenance_hash,
                        verified_at = EXCLUDED.verified_at
                    """)
                    .param("id", provId)
                    .param("questionItemId", item.id())
                    .param("sourceType", p.sourceType().name())
                    .param("licenseClassification", p.licenseClassification().name())
                    .param("copyrightHolder", p.copyrightHolder())
                    .param("sourceReference", p.sourceReference())
                    .param("authorId", p.authorId())
                    .param("reviewerId", p.reviewerId())
                    .param("verificationStatus", p.verificationStatus().name())
                    .param("provenanceHash", p.provenanceHash())
                    .param("verifiedAt", Timestamp.from(p.verifiedAt()))
                    .param("createdAt", Timestamp.from(p.createdAt()))
                    .update();
        }

        return findById(item.id()).orElse(item);
    }

    @Override
    public Optional<QuestionBankItem> findById(UUID id) {
        return jdbcClient.sql("""
                SELECT * FROM exam_question_bank_items WHERE id = :id
                """)
                .param("id", id)
                .query(this::mapQuestionItemRow)
                .optional();
    }

    @Override
    public Optional<QuestionBankItem> findByQuestionCode(String questionCode) {
        return jdbcClient.sql("""
                SELECT * FROM exam_question_bank_items WHERE question_code = :questionCode
                """)
                .param("questionCode", questionCode)
                .query(this::mapQuestionItemRow)
                .optional();
    }

    @Override
    public List<QuestionBankItem> findByExamDefinition(
            UUID examDefinitionId,
            Integer examVersionNumber,
            QuestionItemStatus status,
            String topicCode,
            QuestionDifficultyBand difficultyBand
    ) {
        StringBuilder sql = new StringBuilder("""
                SELECT * FROM exam_question_bank_items
                WHERE exam_definition_id = :examDefinitionId
                """);
        var params = new java.util.LinkedHashMap<String, Object>();
        params.put("examDefinitionId", examDefinitionId);

        if (examVersionNumber != null) {
            sql.append(" AND exam_version_number = :examVersionNumber");
            params.put("examVersionNumber", examVersionNumber);
        }
        if (status != null) {
            sql.append(" AND status = :status");
            params.put("status", status.name());
        }
        if (topicCode != null && !topicCode.isBlank()) {
            sql.append(" AND topic_code = :topicCode");
            params.put("topicCode", topicCode.trim());
        }
        if (difficultyBand != null) {
            sql.append(" AND difficulty_band = :difficultyBand");
            params.put("difficultyBand", difficultyBand.name());
        }
        sql.append(" ORDER BY question_code ASC");

        var spec = jdbcClient.sql(sql.toString());
        for (var entry : params.entrySet()) {
            spec = spec.param(entry.getKey(), entry.getValue());
        }
        return spec.query(this::mapQuestionItemRow).list();
    }

    @Override
    @Transactional
    public void incrementExposureCounts(List<UUID> questionItemIds) {
        if (questionItemIds == null || questionItemIds.isEmpty()) {
            return;
        }
        for (UUID itemId : questionItemIds) {
            jdbcClient.sql("""
                    UPDATE exam_question_bank_items
                    SET exposure_count = exposure_count + 1, updated_at = NOW()
                    WHERE id = :id
                    """)
                    .param("id", itemId)
                    .update();
        }
    }

    private QuestionBankItem mapQuestionItemRow(ResultSet rs, int rowNum) throws SQLException {
        UUID itemId = rs.getObject("id", UUID.class);
        List<QuestionOption> options = loadOptions(itemId);
        QuestionProvenanceRecord provenance = loadProvenance(itemId).orElse(null);
        String rubricRaw = rs.getString("structured_rubric_json");
        Map<String, Object> rubric = readJson(rubricRaw, RUBRIC_TYPE, Map.of());

        return new QuestionBankItem(
                itemId,
                rs.getObject("exam_definition_id", UUID.class),
                rs.getInt("exam_version_number"),
                rs.getObject("exam_section_id", UUID.class),
                rs.getString("question_code"),
                rs.getString("topic_code"),
                rs.getString("subtopic_code"),
                QuestionItemFormat.valueOf(rs.getString("item_format")),
                QuestionDifficultyBand.valueOf(rs.getString("difficulty_band")),
                rs.getBigDecimal("difficulty_b_value"),
                rs.getBigDecimal("discrimination_a_value"),
                rs.getBigDecimal("guessing_c_value"),
                CognitiveTaxonomyLevel.valueOf(rs.getString("cognitive_level")),
                rs.getString("stem_markdown"),
                rs.getString("stem_latex"),
                rs.getString("alt_text_description"),
                rs.getBoolean("has_visual_diagram"),
                rs.getBigDecimal("numeric_correct_answer"),
                rs.getBigDecimal("numeric_tolerance"),
                rubric,
                rs.getString("solution_explanation_markdown"),
                rs.getInt("estimated_time_seconds"),
                rs.getBigDecimal("max_points"),
                rs.getBigDecimal("negative_marking_weight"),
                QuestionItemStatus.valueOf(rs.getString("status")),
                rs.getString("content_sha256") == null ? null : rs.getString("content_sha256").trim(),
                rs.getLong("exposure_count"),
                rs.getBoolean("training_eligible"),
                options,
                provenance,
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }

    private List<QuestionOption> loadOptions(UUID itemId) {
        return jdbcClient.sql("""
                SELECT id, question_item_id, option_key, display_order,
                       content_markdown, content_latex, is_correct, distractor_rationale, training_eligible
                FROM exam_question_options
                WHERE question_item_id = :itemId
                ORDER BY display_order ASC
                """)
                .param("itemId", itemId)
                .query((rs, rowNum) -> new QuestionOption(
                        rs.getObject("id", UUID.class),
                        rs.getObject("question_item_id", UUID.class),
                        rs.getString("option_key"),
                        rs.getInt("display_order"),
                        rs.getString("content_markdown"),
                        rs.getString("content_latex"),
                        rs.getBoolean("is_correct"),
                        rs.getString("distractor_rationale"),
                        rs.getBoolean("training_eligible")
                ))
                .list();
    }

    private Optional<QuestionProvenanceRecord> loadProvenance(UUID itemId) {
        return jdbcClient.sql("""
                SELECT id, question_item_id, source_type, license_classification,
                       copyright_holder, source_reference, author_id, reviewer_id,
                       verification_status, provenance_hash, verified_at, training_eligible, created_at
                FROM exam_question_provenance_records
                WHERE question_item_id = :itemId
                """)
                .param("itemId", itemId)
                .query((rs, rowNum) -> new QuestionProvenanceRecord(
                        rs.getObject("id", UUID.class),
                        rs.getObject("question_item_id", UUID.class),
                        QuestionProvenanceSourceType.valueOf(rs.getString("source_type")),
                        QuestionLicenseClassification.valueOf(rs.getString("license_classification")),
                        rs.getString("copyright_holder"),
                        rs.getString("source_reference"),
                        rs.getString("author_id"),
                        rs.getString("reviewer_id"),
                        ProvenanceVerificationStatus.valueOf(rs.getString("verification_status")),
                        rs.getString("provenance_hash") == null ? null : rs.getString("provenance_hash").trim(),
                        rs.getTimestamp("verified_at").toInstant(),
                        rs.getBoolean("training_eligible"),
                        rs.getTimestamp("created_at").toInstant()
                ))
                .optional();
    }

    private String writeJson(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to serialize JSON in QuestionBankJdbcRepository", ex);
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
