package com.verifiedai.studyplan.coldstart;

import com.verifiedai.studyplan.coldstart.ColdStartModels.BaselineTier;
import com.verifiedai.studyplan.coldstart.ColdStartModels.CalibrationMode;
import com.verifiedai.studyplan.coldstart.ColdStartModels.ColdStartItemOutcome;
import com.verifiedai.studyplan.coldstart.ColdStartModels.ColdStartRationale;
import com.verifiedai.studyplan.coldstart.ColdStartModels.ColdStartSkillPrior;
import com.verifiedai.studyplan.coldstart.ColdStartModels.ConfidenceTier;
import com.verifiedai.studyplan.coldstart.ColdStartModels.DiagnosticStatus;
import com.verifiedai.studyplan.coldstart.ColdStartModels.StoredColdStartRecord;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL JDBC repository for {@code cold_start_diagnostics} and learner baseline signal queries.
 * Uses Jackson 3 ({@code tools.jackson.*}) exclusively.
 */
@Repository
public class ColdStartDiagnosticJdbcRepository implements ColdStartDiagnosticRepository {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<ColdStartItemOutcome>> ITEM_OUTCOMES_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<ColdStartSkillPrior>> SKILL_PRIORS_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<ColdStartRationale>> RATIONALES_TYPE = new TypeReference<>() {};

    private final JdbcClient jdbcClient;
    private final JsonMapper jsonMapper;

    public ColdStartDiagnosticJdbcRepository(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient cannot be null");
        this.jsonMapper = JsonMapper.builder()
                .findAndAddModules()
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }

    @Override
    public Optional<StoredColdStartRecord> findByLearnerIdAndIdempotencyKey(UUID learnerId, String idempotencyKey) {
        return jdbcClient.sql("""
                SELECT diagnostic_id, learner_id, schema_version, idempotency_key, locale, status,
                       baseline_tier, correct_count, total_items, calibration_mode, confidence_tier,
                       goal_conflict_resolved, recommended_daily_budget_minutes,
                       inactive_decay_applied, explicit_preference_override,
                       declared_exam_target, declared_daily_budget_minutes, preferred_modality,
                       revalidation_prompt_key,
                       detected_trap_archetypes_json::text AS detected_trap_archetypes_text,
                       item_outcomes_json::text AS item_outcomes_text,
                       initialized_priors_json::text AS initialized_priors_text,
                       rationales_json::text AS rationales_text,
                       attempt_duration_seconds, llm_tokens_used, training_eligible,
                       created_at, updated_at
                FROM cold_start_diagnostics
                WHERE learner_id = :learnerId AND idempotency_key = :idempotencyKey
                LIMIT 1
                """)
                .param("learnerId", learnerId)
                .param("idempotencyKey", idempotencyKey)
                .query(this::mapRow)
                .optional();
    }

    @Override
    public Optional<StoredColdStartRecord> findLatestByLearnerId(UUID learnerId) {
        return jdbcClient.sql("""
                SELECT diagnostic_id, learner_id, schema_version, idempotency_key, locale, status,
                       baseline_tier, correct_count, total_items, calibration_mode, confidence_tier,
                       goal_conflict_resolved, recommended_daily_budget_minutes,
                       inactive_decay_applied, explicit_preference_override,
                       declared_exam_target, declared_daily_budget_minutes, preferred_modality,
                       revalidation_prompt_key,
                       detected_trap_archetypes_json::text AS detected_trap_archetypes_text,
                       item_outcomes_json::text AS item_outcomes_text,
                       initialized_priors_json::text AS initialized_priors_text,
                       rationales_json::text AS rationales_text,
                       attempt_duration_seconds, llm_tokens_used, training_eligible,
                       created_at, updated_at
                FROM cold_start_diagnostics
                WHERE learner_id = :learnerId
                ORDER BY created_at DESC
                LIMIT 1
                """)
                .param("learnerId", learnerId)
                .query(this::mapRow)
                .optional();
    }

    @Override
    public Map<String, Integer> findSkillEvidenceCounts(UUID learnerId, Collection<String> skillCodes) {
        if (skillCodes == null || skillCodes.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        try {
            jdbcClient.sql("""
                    SELECT skill_id, COALESCE(MAX(evidence_count), 0) AS ev_count
                    FROM learner_skill_mastery
                    WHERE user_id = :learnerId
                      AND skill_id IN (:skillCodes)
                    GROUP BY skill_id
                    """)
                    .param("learnerId", learnerId)
                    .param("skillCodes", skillCodes)
                    .query(rs -> {
                        while (rs.next()) {
                            result.put(rs.getString("skill_id"), rs.getInt("ev_count"));
                        }
                    });
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
        return result;
    }

    @Override
    public Optional<Instant> findLastVerifiedActivityAt(UUID learnerId) {
        try {
            Timestamp latest = jdbcClient.sql("""
                    SELECT GREATEST(
                        COALESCE((SELECT MAX(last_assessed_at) FROM learner_skill_mastery WHERE user_id = :learnerId), '1970-01-01'::timestamptz),
                        COALESCE((SELECT MAX(created_at) FROM cold_start_diagnostics WHERE learner_id = :learnerId), '1970-01-01'::timestamptz)
                    ) AS last_at
                    """)
                    .param("learnerId", learnerId)
                    .query((rs, rowNum) -> rs.getTimestamp("last_at"))
                    .optional()
                    .orElse(null);
            if (latest == null || latest.toInstant().getEpochSecond() <= 0) {
                return Optional.empty();
            }
            return Optional.of(latest.toInstant());
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    @Override
    public void insertDiagnostic(StoredColdStartRecord record) {
        jdbcClient.sql("""
                INSERT INTO cold_start_diagnostics (
                    diagnostic_id, learner_id, schema_version, idempotency_key, locale, status,
                    baseline_tier, correct_count, total_items, calibration_mode, confidence_tier,
                    goal_conflict_resolved, recommended_daily_budget_minutes,
                    inactive_decay_applied, explicit_preference_override,
                    declared_exam_target, declared_daily_budget_minutes, preferred_modality,
                    revalidation_prompt_key,
                    detected_trap_archetypes_json, item_outcomes_json, initialized_priors_json, rationales_json,
                    attempt_duration_seconds, llm_tokens_used, training_eligible, created_at, updated_at
                ) VALUES (
                    :diagnosticId, :learnerId, :schemaVersion, :idempotencyKey, :locale, :status,
                    :baselineTier, :correctCount, :totalItems, :calibrationMode, :confidenceTier,
                    :goalConflictResolved, :recommendedDailyBudgetMinutes,
                    :inactiveDecayApplied, :explicitPreferenceOverride,
                    :declaredExamTarget, :declaredDailyBudgetMinutes, :preferredModality,
                    :revalidationPromptKey,
                    CAST(:detectedTrapArchetypesJson AS jsonb),
                    CAST(:itemOutcomesJson AS jsonb),
                    CAST(:initializedPriorsJson AS jsonb),
                    CAST(:rationalesJson AS jsonb),
                    :attemptDurationSeconds, 0, FALSE, :createdAt, :updatedAt
                )
                """)
                .param("diagnosticId", record.diagnosticId())
                .param("learnerId", record.learnerId())
                .param("schemaVersion", record.schemaVersion())
                .param("idempotencyKey", record.idempotencyKey())
                .param("locale", record.locale())
                .param("status", record.status().name())
                .param("baselineTier", record.baselineTier().name())
                .param("correctCount", record.correctCount())
                .param("totalItems", record.totalItems())
                .param("calibrationMode", record.calibrationMode().name())
                .param("confidenceTier", record.confidenceTier().name())
                .param("goalConflictResolved", record.goalConflictResolved())
                .param("recommendedDailyBudgetMinutes", record.recommendedDailyBudgetMinutes())
                .param("inactiveDecayApplied", record.inactiveDecayApplied())
                .param("explicitPreferenceOverride", record.explicitPreferenceOverride())
                .param("declaredExamTarget", record.declaredExamTarget())
                .param("declaredDailyBudgetMinutes", record.declaredDailyBudgetMinutes())
                .param("preferredModality", record.preferredModality())
                .param("revalidationPromptKey", record.revalidationPromptKey())
                .param("detectedTrapArchetypesJson", writeJson(record.detectedTrapArchetypes()))
                .param("itemOutcomesJson", writeJson(record.itemOutcomes()))
                .param("initializedPriorsJson", writeJson(record.initializedPriors()))
                .param("rationalesJson", writeJson(record.rationales()))
                .param("attemptDurationSeconds", record.attemptDurationSeconds())
                .param("createdAt", Timestamp.from(record.createdAt()))
                .param("updatedAt", Timestamp.from(record.updatedAt()))
                .update();
    }

    private StoredColdStartRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new StoredColdStartRecord(
                rs.getObject("diagnostic_id", UUID.class),
                rs.getObject("learner_id", UUID.class),
                rs.getString("schema_version"),
                rs.getString("idempotency_key"),
                rs.getString("locale"),
                DiagnosticStatus.valueOf(rs.getString("status")),
                BaselineTier.valueOf(rs.getString("baseline_tier")),
                rs.getInt("correct_count"),
                rs.getInt("total_items"),
                CalibrationMode.valueOf(rs.getString("calibration_mode")),
                ConfidenceTier.valueOf(rs.getString("confidence_tier")),
                rs.getBoolean("goal_conflict_resolved"),
                rs.getInt("recommended_daily_budget_minutes"),
                rs.getBoolean("inactive_decay_applied"),
                rs.getBoolean("explicit_preference_override"),
                rs.getString("declared_exam_target"),
                rs.getObject("declared_daily_budget_minutes", Integer.class),
                rs.getString("preferred_modality"),
                rs.getString("revalidation_prompt_key"),
                readJson(rs.getString("detected_trap_archetypes_text"), STRING_LIST_TYPE, List.of()),
                readJson(rs.getString("item_outcomes_text"), ITEM_OUTCOMES_TYPE, List.of()),
                readJson(rs.getString("initialized_priors_text"), SKILL_PRIORS_TYPE, List.of()),
                readJson(rs.getString("rationales_text"), RATIONALES_TYPE, List.of()),
                rs.getInt("attempt_duration_seconds"),
                rs.getInt("llm_tokens_used"),
                rs.getBoolean("training_eligible"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }

    private String writeJson(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to serialize cold-start JSON payload", ex);
        }
    }

    private <T> T readJson(String raw, TypeReference<T> typeRef, T fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return jsonMapper.readValue(raw, typeRef);
        } catch (JacksonException ex) {
            return fallback;
        }
    }
}
