package com.verifiedai.studyplan.privacy;

import com.verifiedai.sharedkernel.privacy.AccountDataLifecycleContributor;
import com.verifiedai.studyplan.domain.LearnerRecommendationEntity;
import com.verifiedai.studyplan.domain.LearnerRecommendationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Component
public class StudyPlanPrivacyLifecycleContributor implements AccountDataLifecycleContributor {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final LearnerRecommendationRepository repository;
    private final JdbcClient jdbcClient;
    private final JsonMapper jsonMapper;

    public StudyPlanPrivacyLifecycleContributor(LearnerRecommendationRepository repository) {
        this(repository, null);
    }

    @Autowired
    public StudyPlanPrivacyLifecycleContributor(
            LearnerRecommendationRepository repository,
            JdbcClient jdbcClient
    ) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.jdbcClient = jdbcClient;
        this.jsonMapper = JsonMapper.builder().findAndAddModules().build();
    }

    @Override
    public String category() {
        return "study_plan";
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> exportUserData(UUID userId) {
        Objects.requireNonNull(userId, "userId cannot be null");
        List<LearnerRecommendationEntity> records = repository.findAllByUserIdOrderByCreatedAtDesc(userId);

        List<Map<String, Object>> exportRecords = records.stream()
                .map(r -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", r.getId().toString());
                    map.put("recommendationType", r.getRecommendationType().name());
                    map.put("targetSkillCode", r.getTargetSkillCode());
                    map.put("targetMistakeCode", r.getTargetMistakeCode());
                    map.put("priorityScore", r.getPriorityScore().doubleValue());
                    map.put("rationaleCode", r.getRationaleCode().name());
                    map.put("status", r.getStatus().name());
                    map.put("expiresAt", r.getExpiresAt().toString());
                    map.put("createdAt", r.getCreatedAt().toString());
                    return map;
                })
                .toList();

        List<Map<String, Object>> coldStartDiagnostics = exportColdStartDiagnostics(userId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("category", category());
        payload.put("recordCount", exportRecords.size() + coldStartDiagnostics.size());
        payload.put("recommendations", exportRecords);
        payload.put("coldStartDiagnosticCount", coldStartDiagnostics.size());
        payload.put("coldStartDiagnostics", coldStartDiagnostics);
        return Collections.unmodifiableMap(payload);
    }

    @Override
    @Transactional
    public void deleteUserData(UUID userId, Instant now) {
        Objects.requireNonNull(userId, "userId cannot be null");
        repository.deleteAllByUserId(userId);
        if (jdbcClient != null) {
            jdbcClient.sql("DELETE FROM cold_start_diagnostics WHERE learner_id = :userId")
                    .param("userId", userId)
                    .update();
        }
    }

    private List<Map<String, Object>> exportColdStartDiagnostics(UUID userId) {
        if (jdbcClient == null) {
            return List.of();
        }
        return jdbcClient.sql("""
                SELECT diagnostic_id, schema_version, locale, status, baseline_tier,
                       correct_count, total_items, calibration_mode, confidence_tier,
                       goal_conflict_resolved, recommended_daily_budget_minutes,
                       inactive_decay_applied, explicit_preference_override,
                       declared_exam_target, declared_daily_budget_minutes, preferred_modality,
                       detected_trap_archetypes_json::text AS detected_trap_archetypes_text,
                       initialized_priors_json::text AS initialized_priors_text,
                       rationales_json::text AS rationales_text,
                       attempt_duration_seconds, llm_tokens_used, training_eligible,
                       created_at, updated_at
                FROM cold_start_diagnostics
                WHERE learner_id = :userId
                ORDER BY created_at DESC
                """)
                .param("userId", userId)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("diagnosticId", rs.getObject("diagnostic_id", UUID.class).toString());
                    row.put("schemaVersion", rs.getString("schema_version"));
                    row.put("locale", rs.getString("locale"));
                    row.put("status", rs.getString("status"));
                    row.put("baselineTier", rs.getString("baseline_tier"));
                    row.put("correctCount", rs.getInt("correct_count"));
                    row.put("totalItems", rs.getInt("total_items"));
                    row.put("calibrationMode", rs.getString("calibration_mode"));
                    row.put("confidenceTier", rs.getString("confidence_tier"));
                    row.put("goalConflictResolved", rs.getBoolean("goal_conflict_resolved"));
                    row.put("recommendedDailyBudgetMinutes", rs.getInt("recommended_daily_budget_minutes"));
                    row.put("inactiveDecayApplied", rs.getBoolean("inactive_decay_applied"));
                    row.put("explicitPreferenceOverride", rs.getBoolean("explicit_preference_override"));
                    row.put("declaredExamTarget", rs.getString("declared_exam_target"));
                    row.put("declaredDailyBudgetMinutes", rs.getObject("declared_daily_budget_minutes", Integer.class));
                    row.put("preferredModality", rs.getString("preferred_modality"));
                    row.put("detectedTrapArchetypes", readJsonList(rs.getString("detected_trap_archetypes_text")));
                    row.put("initializedPriors", readJsonMap(rs.getString("initialized_priors_text")));
                    row.put("rationales", readJsonList(rs.getString("rationales_text")));
                    row.put("attemptDurationSeconds", rs.getInt("attempt_duration_seconds"));
                    row.put("llmTokensUsed", rs.getInt("llm_tokens_used"));
                    row.put("trainingEligible", rs.getBoolean("training_eligible"));
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    Timestamp updatedAt = rs.getTimestamp("updated_at");
                    row.put("createdAt", createdAt != null ? createdAt.toInstant().toString() : null);
                    row.put("updatedAt", updatedAt != null ? updatedAt.toInstant().toString() : null);
                    return row;
                })
                .list();
    }

    private List<String> readJsonList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return jsonMapper.readValue(json, STRING_LIST_TYPE);
        } catch (JacksonException ex) {
            return List.of();
        }
    }

    private Map<String, Object> readJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return jsonMapper.readValue(json, MAP_TYPE);
        } catch (JacksonException ex) {
            return Map.of();
        }
    }
}
