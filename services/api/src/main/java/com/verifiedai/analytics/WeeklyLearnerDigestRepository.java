package com.verifiedai.analytics;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL JDBC read-projection and persistence repository for Sprint 8.9 weekly learner digests.
 * Reads verified aggregates across V001-V051 tables while persisting exclusively non-training
 * digest projections into {@code weekly_learner_digests}.
 */
@Repository
public class WeeklyLearnerDigestRepository {

    private static final TypeReference<List<WeeklyReportDtos.ExplainableRecommendationItemDto>> RECOMMENDATIONS_TYPE =
        new TypeReference<>() {};

    private final NamedParameterJdbcTemplate jdbc;
    private final JsonMapper objectMapper;

    @Autowired
    public WeeklyLearnerDigestRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc cannot be null");
        this.objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();
    }

    protected WeeklyLearnerDigestRepository() {
        this.jdbc = null;
        this.objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();
    }

    public Optional<WeeklyLearnerDigest> findByUserAndWeek(UUID userId, String weekIso) {
        String sql = """
            SELECT digest_id, user_id, week_iso, week_start_date, week_end_date, locale, status,
                   solved_attempts_count, independent_twins_solved_count, study_minutes, active_days_count,
                   digest_payload_json::text AS payload_json,
                   explainable_recommendations_json::text AS recommendations_json,
                   training_eligible, generated_at, updated_at
            FROM weekly_learner_digests
            WHERE user_id = :userId AND week_iso = :weekIso
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("userId", userId, Types.OTHER)
            .addValue("weekIso", weekIso, Types.VARCHAR);
        List<WeeklyLearnerDigest> rows = jdbc.query(sql, params, this::mapDigestRow);
        return rows.stream().findFirst();
    }

    public List<WeeklyLearnerDigest> findAllByUserId(UUID userId) {
        String sql = """
            SELECT digest_id, user_id, week_iso, week_start_date, week_end_date, locale, status,
                   solved_attempts_count, independent_twins_solved_count, study_minutes, active_days_count,
                   digest_payload_json::text AS payload_json,
                   explainable_recommendations_json::text AS recommendations_json,
                   training_eligible, generated_at, updated_at
            FROM weekly_learner_digests
            WHERE user_id = :userId
            ORDER BY week_start_date DESC, generated_at DESC
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("userId", userId, Types.OTHER);
        return jdbc.query(sql, params, this::mapDigestRow);
    }

    public void upsertDigest(WeeklyLearnerDigest digest) {
        String payloadJson = writeJson(digest.payload());
        String recommendationsJson = writeJson(digest.recommendations());
        String sql = """
            INSERT INTO weekly_learner_digests (
                digest_id, user_id, week_iso, week_start_date, week_end_date, locale, status,
                solved_attempts_count, independent_twins_solved_count, study_minutes, active_days_count,
                digest_payload_json, explainable_recommendations_json, training_eligible,
                generated_at, updated_at
            ) VALUES (
                :digestId, :userId, :weekIso, :weekStartDate, :weekEndDate, :locale, :status,
                :solvedAttemptsCount, :independentTwinsSolvedCount, :studyMinutes, :activeDaysCount,
                CAST(:payloadJson AS jsonb), CAST(:recommendationsJson AS jsonb), FALSE,
                :generatedAt, :updatedAt
            )
            ON CONFLICT (user_id, week_iso) DO UPDATE SET
                locale = EXCLUDED.locale,
                status = EXCLUDED.status,
                solved_attempts_count = EXCLUDED.solved_attempts_count,
                independent_twins_solved_count = EXCLUDED.independent_twins_solved_count,
                study_minutes = EXCLUDED.study_minutes,
                active_days_count = EXCLUDED.active_days_count,
                digest_payload_json = EXCLUDED.digest_payload_json,
                explainable_recommendations_json = EXCLUDED.explainable_recommendations_json,
                training_eligible = FALSE,
                updated_at = EXCLUDED.updated_at
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("digestId", digest.digestId(), Types.OTHER)
            .addValue("userId", digest.userId(), Types.OTHER)
            .addValue("weekIso", digest.weekIso(), Types.VARCHAR)
            .addValue("weekStartDate", Date.valueOf(digest.weekStartDate()), Types.DATE)
            .addValue("weekEndDate", Date.valueOf(digest.weekEndDate()), Types.DATE)
            .addValue("locale", digest.locale(), Types.VARCHAR)
            .addValue("status", digest.status().name(), Types.VARCHAR)
            .addValue("solvedAttemptsCount", digest.solvedAttemptsCount(), Types.INTEGER)
            .addValue("independentTwinsSolvedCount", digest.independentTwinsSolvedCount(), Types.INTEGER)
            .addValue("studyMinutes", digest.studyMinutes(), Types.INTEGER)
            .addValue("activeDaysCount", digest.activeDaysCount(), Types.INTEGER)
            .addValue("payloadJson", payloadJson, Types.VARCHAR)
            .addValue("recommendationsJson", recommendationsJson, Types.VARCHAR)
            .addValue("generatedAt", Timestamp.from(digest.generatedAt()), Types.TIMESTAMP)
            .addValue("updatedAt", Timestamp.from(digest.updatedAt()), Types.TIMESTAMP);
        jdbc.update(sql, params);
    }

    public int deleteByUserId(UUID userId) {
        String sql = "DELETE FROM weekly_learner_digests WHERE user_id = :userId";
        return jdbc.update(sql, new MapSqlParameterSource().addValue("userId", userId, Types.OTHER));
    }

    public LearnerContextProjection readLearnerContext(UUID userId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("userId", userId, Types.OTHER);
        List<Map<String, Object>> profileRows = jdbc.queryForList(
            "SELECT timezone, preferred_language FROM learning_profiles WHERE user_id = :userId LIMIT 1",
            params
        );
        String profileTimezone = profileRows.isEmpty() ? null : (String) profileRows.getFirst().get("timezone");
        String preferredLanguage = profileRows.isEmpty() ? null : (String) profileRows.getFirst().get("preferred_language");

        List<Map<String, Object>> streakRows = jdbc.queryForList(
            "SELECT current_streak_days, learner_timezone FROM learner_streaks WHERE user_id = :userId LIMIT 1",
            params
        );
        int currentStreakDays = streakRows.isEmpty() ? 0 : ((Number) streakRows.getFirst().get("current_streak_days")).intValue();
        String streakTimezone = streakRows.isEmpty() ? null : (String) streakRows.getFirst().get("learner_timezone");

        String effectiveTimezone = firstNonBlank(streakTimezone, profileTimezone, "UTC");
        String defaultLocale = firstNonBlank(preferredLanguage, "en");
        return new LearnerContextProjection(effectiveTimezone, defaultLocale, currentStreakDays);
    }

    public WeeklyEvidenceProjection readWeeklyEvidence(
        UUID userId,
        LocalDate weekStartDate,
        LocalDate weekEndDate,
        Instant weekStartTs,
        Instant weekEndTs,
        Instant prevWeekStartTs
    ) {
        MapSqlParameterSource rangeParams = new MapSqlParameterSource()
            .addValue("userId", userId, Types.OTHER)
            .addValue("startDate", Date.valueOf(weekStartDate), Types.DATE)
            .addValue("endDate", Date.valueOf(weekEndDate), Types.DATE)
            .addValue("startTs", Timestamp.from(weekStartTs), Types.TIMESTAMP)
            .addValue("endTs", Timestamp.from(weekEndTs), Types.TIMESTAMP)
            .addValue("prevStartTs", Timestamp.from(prevWeekStartTs), Types.TIMESTAMP);

        List<LedgerDayRow> ledgerRows = jdbc.query(
            """
            SELECT activity_local_date, daily_status, independent_activity_count,
                   assisted_activity_count, evidence_reference
            FROM learner_daily_streak_ledger
            WHERE user_id = :userId
              AND activity_local_date BETWEEN :startDate AND :endDate
            ORDER BY activity_local_date ASC
            """,
            rangeParams,
            (rs, rowNum) -> new LedgerDayRow(
                rs.getDate("activity_local_date").toLocalDate(),
                rs.getString("daily_status"),
                rs.getInt("independent_activity_count"),
                rs.getInt("assisted_activity_count"),
                rs.getString("evidence_reference")
            )
        );

        List<AttemptEventRow> attemptRows = jdbc.query(
            """
            SELECT id, created_at
            FROM student_attempts
            WHERE user_id = :userId
              AND status IN ('SUBMITTED', 'ANALYZED')
              AND created_at >= :startTs
              AND created_at < :endTs
            ORDER BY created_at ASC
            """,
            rangeParams,
            (rs, rowNum) -> new AttemptEventRow(
                rs.getObject("id", UUID.class),
                rs.getTimestamp("created_at").toInstant()
            )
        );

        List<TwinCompletionRow> twinRows = jdbc.query(
            """
            SELECT twin_id, target_skill_code, status, completed_at
            FROM counterfactual_twin_drills
            WHERE user_id = :userId
              AND completed_at IS NOT NULL
              AND completed_at >= :startTs
              AND completed_at < :endTs
            ORDER BY completed_at ASC
            """,
            rangeParams,
            (rs, rowNum) -> new TwinCompletionRow(
                rs.getObject("twin_id", UUID.class),
                rs.getString("target_skill_code"),
                rs.getString("status"),
                rs.getTimestamp("completed_at").toInstant()
            )
        );

        List<StudySessionRow> sessionRows = jdbc.query(
            """
            SELECT session_id, topic_code, state, active_duration_seconds,
                   COALESCE(completed_at, updated_at, started_at) AS activity_ts
            FROM study_sessions
            WHERE user_id = :userId
              AND COALESCE(completed_at, updated_at, started_at) >= :startTs
              AND COALESCE(completed_at, updated_at, started_at) < :endTs
            ORDER BY activity_ts ASC
            """,
            rangeParams,
            (rs, rowNum) -> new StudySessionRow(
                rs.getObject("session_id", UUID.class),
                rs.getString("topic_code"),
                rs.getString("state"),
                rs.getInt("active_duration_seconds"),
                rs.getTimestamp("activity_ts").toInstant()
            )
        );

        Integer completedPlanItems = jdbc.queryForObject(
            """
            SELECT COUNT(*)
            FROM daily_coach_tasks t
            JOIN daily_coach_sessions s ON t.session_id = s.id
            WHERE s.user_id = :userId
              AND t.status = 'COMPLETED'
              AND s.local_date BETWEEN :startDate AND :endDate
            """,
            rangeParams,
            Integer.class
        );

        List<SkillMasteryRow> masteryRows = jdbc.query(
            """
            SELECT skill_id, score, half_life_days, independent_evidence_count,
                   assisted_evidence_count, independent_competence_score
            FROM learner_skill_mastery
            WHERE user_id = :userId
            ORDER BY skill_id ASC
            """,
            rangeParams,
            (rs, rowNum) -> new SkillMasteryRow(
                rs.getString("skill_id"),
                rs.getDouble("score"),
                rs.getDouble("half_life_days"),
                rs.getInt("independent_evidence_count"),
                rs.getInt("assisted_evidence_count"),
                rs.getDouble("independent_competence_score")
            )
        );

        List<MasteryTransitionRow> transitionRows = jdbc.query(
            """
            SELECT canonical_skill_code, prior_score, posterior_score, evidence_type, event_time
            FROM learner_skill_mastery_transitions
            WHERE user_id = :userId
              AND event_time >= :startTs
              AND event_time < :endTs
            ORDER BY event_time ASC
            """,
            rangeParams,
            (rs, rowNum) -> new MasteryTransitionRow(
                rs.getString("canonical_skill_code"),
                rs.getDouble("prior_score"),
                rs.getDouble("posterior_score"),
                rs.getString("evidence_type"),
                rs.getTimestamp("event_time").toInstant()
            )
        );

        List<MistakeDnaProfileRow> mistakeProfiles = jdbc.query(
            """
            SELECT mistake_code, skill_id, smoothed_propensity, recent_error_count, recurrence_state
            FROM learner_mistake_dna_profiles
            WHERE user_id = :userId
            ORDER BY smoothed_propensity DESC, mistake_code ASC
            """,
            rangeParams,
            (rs, rowNum) -> new MistakeDnaProfileRow(
                rs.getString("mistake_code"),
                rs.getString("skill_id"),
                rs.getDouble("smoothed_propensity"),
                rs.getInt("recent_error_count"),
                rs.getString("recurrence_state")
            )
        );

        List<MistakeWindowCountRow> mistakeWindowCounts = jdbc.query(
            """
            SELECT mistake_code, skill_id,
                   COUNT(*) FILTER (WHERE assessed_at >= :startTs AND assessed_at < :endTs AND NOT correct) AS this_week_errors,
                   COUNT(*) FILTER (WHERE assessed_at >= :prevStartTs AND assessed_at < :startTs AND NOT correct) AS prev_week_errors
            FROM learner_mistake_events
            WHERE user_id = :userId
              AND assessed_at >= :prevStartTs
              AND assessed_at < :endTs
            GROUP BY mistake_code, skill_id
            """,
            rangeParams,
            (rs, rowNum) -> new MistakeWindowCountRow(
                rs.getString("mistake_code"),
                rs.getString("skill_id"),
                rs.getInt("this_week_errors"),
                rs.getInt("prev_week_errors")
            )
        );

        Set<String> completedMicroLessonMistakeCodes = Set.copyOf(jdbc.queryForList(
            """
            SELECT DISTINCT mistake_code
            FROM learner_micro_lesson_completions
            WHERE user_id = :userId AND quick_check_passed = TRUE
            """,
            rangeParams,
            String.class
        ));

        List<SpacedReviewRow> spacedReviews = jdbc.query(
            """
            SELECT canonical_skill_code, estimated_retention, status, due_at
            FROM spaced_review_queue
            WHERE user_id = :userId
            """,
            rangeParams,
            (rs, rowNum) -> new SpacedReviewRow(
                rs.getString("canonical_skill_code"),
                rs.getDouble("estimated_retention"),
                rs.getString("status"),
                rs.getTimestamp("due_at").toInstant()
            )
        );

        Map<String, Integer> prerequisiteDependentCounts = new LinkedHashMap<>();
        jdbc.query(
            """
            SELECT prerequisite_skill_code, COUNT(DISTINCT dependent_skill_code) AS dep_count
            FROM mastery_prerequisite_edges
            GROUP BY prerequisite_skill_code
            """,
            (rs) -> {
                prerequisiteDependentCounts.put(rs.getString("prerequisite_skill_code"), rs.getInt("dep_count"));
            }
        );

        return new WeeklyEvidenceProjection(
            ledgerRows,
            attemptRows,
            twinRows,
            sessionRows,
            completedPlanItems != null ? completedPlanItems : 0,
            masteryRows,
            transitionRows,
            mistakeProfiles,
            mistakeWindowCounts,
            completedMicroLessonMistakeCodes,
            spacedReviews,
            prerequisiteDependentCounts
        );
    }

    private WeeklyLearnerDigest mapDigestRow(ResultSet rs, int rowNum) throws SQLException {
        WeeklyReportDtos.WeeklyDigestDto payload = readJson(
            rs.getString("payload_json"),
            WeeklyReportDtos.WeeklyDigestDto.class
        );
        List<WeeklyReportDtos.ExplainableRecommendationItemDto> recommendations = readJson(
            rs.getString("recommendations_json"),
            RECOMMENDATIONS_TYPE
        );
        return new WeeklyLearnerDigest(
            rs.getObject("digest_id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getString("week_iso"),
            rs.getDate("week_start_date").toLocalDate(),
            rs.getDate("week_end_date").toLocalDate(),
            rs.getString("locale"),
            WeeklyDigestStatus.valueOf(rs.getString("status")),
            rs.getInt("solved_attempts_count"),
            rs.getInt("independent_twins_solved_count"),
            rs.getInt("study_minutes"),
            rs.getInt("active_days_count"),
            payload,
            recommendations,
            rs.getBoolean("training_eligible"),
            rs.getTimestamp("generated_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
        );
    }

    String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to serialize weekly digest JSON", ex);
        }
    }

    <T> T readJson(String rawJson, Class<T> type) {
        try {
            return objectMapper.readValue(rawJson, type);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to deserialize weekly digest payload JSON", ex);
        }
    }

    <T> T readJson(String rawJson, TypeReference<T> type) {
        try {
            return objectMapper.readValue(rawJson, type);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to deserialize weekly recommendations JSON", ex);
        }
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return "UTC";
    }

    public record LearnerContextProjection(
        String timezone,
        String preferredLocale,
        int currentStreakDays
    ) {
    }

    public record LedgerDayRow(
        LocalDate activityLocalDate,
        String dailyStatus,
        int independentActivityCount,
        int assistedActivityCount,
        String evidenceReference
    ) {
    }

    public record AttemptEventRow(
        UUID attemptId,
        Instant createdAt
    ) {
    }

    public record TwinCompletionRow(
        UUID twinId,
        String targetSkillCode,
        String status,
        Instant completedAt
    ) {
    }

    public record StudySessionRow(
        UUID sessionId,
        String topicCode,
        String state,
        int activeDurationSeconds,
        Instant activityTs
    ) {
    }

    public record SkillMasteryRow(
        String skillId,
        double score,
        double halfLifeDays,
        int independentEvidenceCount,
        int assistedEvidenceCount,
        double independentCompetenceScore
    ) {
    }

    public record MasteryTransitionRow(
        String skillId,
        double priorScore,
        double posteriorScore,
        String evidenceType,
        Instant eventTime
    ) {
    }

    public record MistakeDnaProfileRow(
        String mistakeCode,
        String skillId,
        double smoothedPropensity,
        int recentErrorCount,
        String recurrenceState
    ) {
    }

    public record MistakeWindowCountRow(
        String mistakeCode,
        String skillId,
        int thisWeekErrors,
        int prevWeekErrors
    ) {
    }

    public record SpacedReviewRow(
        String skillId,
        double estimatedRetention,
        String status,
        Instant dueAt
    ) {
    }

    public record WeeklyEvidenceProjection(
        List<LedgerDayRow> ledgerDays,
        List<AttemptEventRow> attempts,
        List<TwinCompletionRow> twinCompletions,
        List<StudySessionRow> studySessions,
        int completedPlanItemsCount,
        List<SkillMasteryRow> skillMasteries,
        List<MasteryTransitionRow> masteryTransitions,
        List<MistakeDnaProfileRow> mistakeProfiles,
        List<MistakeWindowCountRow> mistakeWindowCounts,
        Set<String> completedMicroLessonMistakeCodes,
        List<SpacedReviewRow> spacedReviews,
        Map<String, Integer> prerequisiteDependentCounts
    ) {
        public static WeeklyEvidenceProjection empty() {
            return new WeeklyEvidenceProjection(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                0,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Set.of(),
                List.of(),
                Map.of()
            );
        }
    }
}
