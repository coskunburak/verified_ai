package com.verifiedai.exam.infrastructure.persistence;

import com.verifiedai.exam.application.CandidateProfileRepository;
import com.verifiedai.exam.domain.CandidateProfileModels.AccessibilityAccommodations;
import com.verifiedai.exam.domain.CandidateProfileModels.CandidateHorizonHistoryEntry;
import com.verifiedai.exam.domain.CandidateProfileModels.CandidateProfileRecord;
import com.verifiedai.exam.domain.CandidateProfileModels.FeasibilityStatus;
import com.verifiedai.exam.domain.CandidateProfileModels.ParsedSyllabusTopicItem;
import com.verifiedai.exam.domain.CandidateProfileModels.SyllabusUploadRecord;
import com.verifiedai.exam.domain.CandidateProfileModels.SyllabusUploadStatus;
import com.verifiedai.exam.domain.ExamModels.Jurisdiction;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class CandidateProfileJdbcRepository implements CandidateProfileRepository {

    private static final TypeReference<List<ParsedSyllabusTopicItem>> PARSED_TOPICS_TYPE = new TypeReference<>() {};

    private final JdbcClient jdbcClient;
    private final JsonMapper jsonMapper;

    public CandidateProfileJdbcRepository(JdbcClient jdbcClient, JsonMapper jsonMapper) {
        this.jdbcClient = jdbcClient;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public Optional<CandidateProfileRecord> findProfileByUserId(UUID userId) {
        return jdbcClient.sql("""
                        SELECT profile_id, user_id, active_exam_definition_id, active_exam_code, pinned_exam_version,
                               active_target_id, jurisdiction, target_date, target_score,
                               daily_study_minutes, weekly_study_minutes, extra_time_multiplier,
                               high_contrast_enabled, screen_reader_optimized, reduced_motion_enabled,
                               baseline_mastery, required_daily_minutes, feasibility_status,
                               last_idempotency_key, schema_version, training_eligible, created_at, updated_at
                        FROM candidate_profiles
                        WHERE user_id = :userId
                        """)
                .param("userId", userId)
                .query((rs, rowNum) -> mapProfile(rs))
                .optional();
    }

    @Override
    public Optional<CandidateProfileRecord> findProfileByUserAndIdempotencyKey(UUID userId, String idempotencyKey) {
        return jdbcClient.sql("""
                        SELECT profile_id, user_id, active_exam_definition_id, active_exam_code, pinned_exam_version,
                               active_target_id, jurisdiction, target_date, target_score,
                               daily_study_minutes, weekly_study_minutes, extra_time_multiplier,
                               high_contrast_enabled, screen_reader_optimized, reduced_motion_enabled,
                               baseline_mastery, required_daily_minutes, feasibility_status,
                               last_idempotency_key, schema_version, training_eligible, created_at, updated_at
                        FROM candidate_profiles
                        WHERE user_id = :userId AND last_idempotency_key = :idempotencyKey
                        """)
                .param("userId", userId)
                .param("idempotencyKey", idempotencyKey)
                .query((rs, rowNum) -> mapProfile(rs))
                .optional();
    }

    @Override
    public CandidateProfileRecord saveProfileAndRecordHistory(
            CandidateProfileRecord p,
            CandidateHorizonHistoryEntry h,
            boolean insertNewProfile
    ) {
        if (insertNewProfile) {
            jdbcClient.sql("""
                            INSERT INTO candidate_profiles (
                                profile_id, user_id, active_exam_definition_id, active_exam_code, pinned_exam_version,
                                active_target_id, jurisdiction, target_date, target_score,
                                daily_study_minutes, weekly_study_minutes, extra_time_multiplier,
                                high_contrast_enabled, screen_reader_optimized, reduced_motion_enabled,
                                baseline_mastery, required_daily_minutes, feasibility_status,
                                last_idempotency_key, schema_version, training_eligible, created_at, updated_at
                            ) VALUES (
                                :profileId, :userId, :activeExamDefinitionId, :activeExamCode, :pinnedExamVersion,
                                :activeTargetId, :jurisdiction, :targetDate, :targetScore,
                                :dailyStudyMinutes, :weeklyStudyMinutes, :extraTimeMultiplier,
                                :highContrastEnabled, :screenReaderOptimized, :reducedMotionEnabled,
                                :baselineMastery, :requiredDailyMinutes, :feasibilityStatus,
                                :lastIdempotencyKey, :schemaVersion, FALSE, :createdAt, :updatedAt
                            )
                            """)
                    .param("profileId", p.profileId())
                    .param("userId", p.userId())
                    .param("activeExamDefinitionId", p.activeExamDefinitionId())
                    .param("activeExamCode", p.activeExamCode())
                    .param("pinnedExamVersion", p.pinnedExamVersion())
                    .param("activeTargetId", p.activeTargetId())
                    .param("jurisdiction", p.jurisdiction().name())
                    .param("targetDate", Date.valueOf(p.targetDate()))
                    .param("targetScore", p.targetScore())
                    .param("dailyStudyMinutes", p.dailyStudyMinutes())
                    .param("weeklyStudyMinutes", p.weeklyStudyMinutes())
                    .param("extraTimeMultiplier", p.accommodations().extraTimeMultiplier())
                    .param("highContrastEnabled", p.accommodations().highContrastEnabled())
                    .param("screenReaderOptimized", p.accommodations().screenReaderOptimized())
                    .param("reducedMotionEnabled", p.accommodations().reducedMotionEnabled())
                    .param("baselineMastery", p.baselineMastery())
                    .param("requiredDailyMinutes", p.requiredDailyMinutes())
                    .param("feasibilityStatus", p.feasibilityStatus().name())
                    .param("lastIdempotencyKey", p.lastIdempotencyKey())
                    .param("schemaVersion", p.schemaVersion())
                    .param("createdAt", Timestamp.from(p.createdAt()))
                    .param("updatedAt", Timestamp.from(p.updatedAt()))
                    .update();
        } else {
            jdbcClient.sql("""
                            UPDATE candidate_profiles
                            SET active_exam_definition_id = :activeExamDefinitionId,
                                active_exam_code = :activeExamCode,
                                pinned_exam_version = :pinnedExamVersion,
                                active_target_id = :activeTargetId,
                                jurisdiction = :jurisdiction,
                                target_date = :targetDate,
                                target_score = :targetScore,
                                daily_study_minutes = :dailyStudyMinutes,
                                weekly_study_minutes = :weeklyStudyMinutes,
                                extra_time_multiplier = :extraTimeMultiplier,
                                high_contrast_enabled = :highContrastEnabled,
                                screen_reader_optimized = :screenReaderOptimized,
                                reduced_motion_enabled = :reducedMotionEnabled,
                                baseline_mastery = :baselineMastery,
                                required_daily_minutes = :requiredDailyMinutes,
                                feasibility_status = :feasibilityStatus,
                                last_idempotency_key = :lastIdempotencyKey,
                                updated_at = :updatedAt
                            WHERE profile_id = :profileId AND user_id = :userId
                            """)
                    .param("activeExamDefinitionId", p.activeExamDefinitionId())
                    .param("activeExamCode", p.activeExamCode())
                    .param("pinnedExamVersion", p.pinnedExamVersion())
                    .param("activeTargetId", p.activeTargetId())
                    .param("jurisdiction", p.jurisdiction().name())
                    .param("targetDate", Date.valueOf(p.targetDate()))
                    .param("targetScore", p.targetScore())
                    .param("dailyStudyMinutes", p.dailyStudyMinutes())
                    .param("weeklyStudyMinutes", p.weeklyStudyMinutes())
                    .param("extraTimeMultiplier", p.accommodations().extraTimeMultiplier())
                    .param("highContrastEnabled", p.accommodations().highContrastEnabled())
                    .param("screenReaderOptimized", p.accommodations().screenReaderOptimized())
                    .param("reducedMotionEnabled", p.accommodations().reducedMotionEnabled())
                    .param("baselineMastery", p.baselineMastery())
                    .param("requiredDailyMinutes", p.requiredDailyMinutes())
                    .param("feasibilityStatus", p.feasibilityStatus().name())
                    .param("lastIdempotencyKey", p.lastIdempotencyKey())
                    .param("updatedAt", Timestamp.from(p.updatedAt()))
                    .param("profileId", p.profileId())
                    .param("userId", p.userId())
                    .update();
        }

        jdbcClient.sql("""
                        INSERT INTO candidate_profile_horizon_history (
                            history_id, profile_id, user_id, exam_definition_id, exam_code, pinned_exam_version,
                            previous_target_date, new_target_date, previous_daily_minutes, new_daily_minutes,
                            previous_weekly_minutes, new_weekly_minutes, previous_target_score, new_target_score,
                            extra_time_multiplier, required_daily_minutes, feasibility_status, change_reason, changed_at
                        ) VALUES (
                            :historyId, :profileId, :userId, :examDefinitionId, :examCode, :pinnedExamVersion,
                            :previousTargetDate, :newTargetDate, :previousDailyMinutes, :newDailyMinutes,
                            :previousWeeklyMinutes, :newWeeklyMinutes, :previousTargetScore, :newTargetScore,
                            :extraTimeMultiplier, :requiredDailyMinutes, :feasibilityStatus, :changeReason, :changedAt
                        )
                        """)
                .param("historyId", h.historyId())
                .param("profileId", h.profileId())
                .param("userId", p.userId())
                .param("examDefinitionId", p.activeExamDefinitionId())
                .param("examCode", h.examCode())
                .param("pinnedExamVersion", h.pinnedExamVersion())
                .param("previousTargetDate", h.previousTargetDate() == null ? null : Date.valueOf(h.previousTargetDate()))
                .param("newTargetDate", Date.valueOf(h.newTargetDate()))
                .param("previousDailyMinutes", h.previousDailyMinutes())
                .param("newDailyMinutes", h.newDailyMinutes())
                .param("previousWeeklyMinutes", h.previousWeeklyMinutes())
                .param("newWeeklyMinutes", h.newWeeklyMinutes())
                .param("previousTargetScore", h.previousTargetScore())
                .param("newTargetScore", h.newTargetScore())
                .param("extraTimeMultiplier", h.extraTimeMultiplier())
                .param("requiredDailyMinutes", h.requiredDailyMinutes())
                .param("feasibilityStatus", h.feasibilityStatus().name())
                .param("changeReason", h.changeReason())
                .param("changedAt", Timestamp.from(h.changedAt()))
                .update();

        return p;
    }

    @Override
    public List<CandidateHorizonHistoryEntry> findHorizonHistoryByUserId(UUID userId, int limit) {
        return jdbcClient.sql("""
                        SELECT history_id, profile_id, exam_code, pinned_exam_version,
                               previous_target_date, new_target_date,
                               previous_daily_minutes, new_daily_minutes,
                               previous_weekly_minutes, new_weekly_minutes,
                               previous_target_score, new_target_score,
                               extra_time_multiplier, required_daily_minutes,
                               feasibility_status, change_reason, changed_at
                        FROM candidate_profile_horizon_history
                        WHERE user_id = :userId
                        ORDER BY changed_at DESC
                        LIMIT :limit
                        """)
                .param("userId", userId)
                .param("limit", limit)
                .query((rs, rowNum) -> {
                    Date prevDate = rs.getDate("previous_target_date");
                    Integer prevDaily = rs.getObject("previous_daily_minutes", Integer.class);
                    Integer prevWeekly = rs.getObject("previous_weekly_minutes", Integer.class);
                    return new CandidateHorizonHistoryEntry(
                            UUID.fromString(rs.getString("history_id")),
                            UUID.fromString(rs.getString("profile_id")),
                            rs.getString("exam_code"),
                            rs.getString("pinned_exam_version"),
                            prevDate == null ? null : prevDate.toLocalDate(),
                            rs.getDate("new_target_date").toLocalDate(),
                            prevDaily,
                            rs.getInt("new_daily_minutes"),
                            prevWeekly,
                            rs.getInt("new_weekly_minutes"),
                            rs.getBigDecimal("previous_target_score"),
                            rs.getBigDecimal("new_target_score"),
                            rs.getBigDecimal("extra_time_multiplier"),
                            rs.getInt("required_daily_minutes"),
                            FeasibilityStatus.valueOf(rs.getString("feasibility_status")),
                            rs.getString("change_reason"),
                            rs.getTimestamp("changed_at").toInstant()
                    );
                })
                .list();
    }

    @Override
    public Optional<SyllabusUploadRecord> findSyllabusUploadById(UUID uploadId) {
        return jdbcClient.sql("""
                        SELECT upload_id, user_id, exam_definition_id, exam_code, exam_version,
                               image_hash, mime_type, byte_size, parsed_topics_json, status,
                               deduplicated_from_cache, llm_tokens_used, last_idempotency_key,
                               schema_version, training_eligible, expires_at, confirmed_at, created_at, updated_at
                        FROM candidate_syllabus_uploads
                        WHERE upload_id = :uploadId
                        """)
                .param("uploadId", uploadId)
                .query((rs, rowNum) -> mapUpload(rs))
                .optional();
    }

    @Override
    public Optional<SyllabusUploadRecord> findSyllabusUploadByUserAndHash(UUID userId, String imageHash) {
        return jdbcClient.sql("""
                        SELECT upload_id, user_id, exam_definition_id, exam_code, exam_version,
                               image_hash, mime_type, byte_size, parsed_topics_json, status,
                               deduplicated_from_cache, llm_tokens_used, last_idempotency_key,
                               schema_version, training_eligible, expires_at, confirmed_at, created_at, updated_at
                        FROM candidate_syllabus_uploads
                        WHERE user_id = :userId AND image_hash = :imageHash
                        """)
                .param("userId", userId)
                .param("imageHash", imageHash)
                .query((rs, rowNum) -> mapUpload(rs))
                .optional();
    }

    @Override
    public Optional<SyllabusUploadRecord> findSyllabusUploadByUserAndIdempotencyKey(UUID userId, String idempotencyKey) {
        return jdbcClient.sql("""
                        SELECT upload_id, user_id, exam_definition_id, exam_code, exam_version,
                               image_hash, mime_type, byte_size, parsed_topics_json, status,
                               deduplicated_from_cache, llm_tokens_used, last_idempotency_key,
                               schema_version, training_eligible, expires_at, confirmed_at, created_at, updated_at
                        FROM candidate_syllabus_uploads
                        WHERE user_id = :userId AND last_idempotency_key = :idempotencyKey
                        """)
                .param("userId", userId)
                .param("idempotencyKey", idempotencyKey)
                .query((rs, rowNum) -> mapUpload(rs))
                .optional();
    }

    @Override
    public int countNonCachedSyllabusUploadsSince(UUID userId, Instant cutoff) {
        Integer count = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM candidate_syllabus_uploads
                        WHERE user_id = :userId
                          AND deduplicated_from_cache = FALSE
                          AND created_at >= :cutoff
                        """)
                .param("userId", userId)
                .param("cutoff", Timestamp.from(cutoff))
                .query(Integer.class)
                .single();
        return count == null ? 0 : count;
    }

    @Override
    public SyllabusUploadRecord insertSyllabusUpload(SyllabusUploadRecord r) {
        String topicsJson = jsonMapper.writeValueAsString(r.parsedTopics());
        jdbcClient.sql("""
                        INSERT INTO candidate_syllabus_uploads (
                            upload_id, user_id, exam_definition_id, exam_code, exam_version,
                            image_hash, mime_type, byte_size, parsed_topics_json, status,
                            deduplicated_from_cache, llm_tokens_used, last_idempotency_key,
                            schema_version, training_eligible, expires_at, confirmed_at, created_at, updated_at
                        ) VALUES (
                            :uploadId, :userId, :examDefinitionId, :examCode, :examVersion,
                            :imageHash, :mimeType, :byteSize, CAST(:parsedTopicsJson AS jsonb), :status,
                            :deduplicatedFromCache, :llmTokensUsed, :lastIdempotencyKey,
                            :schemaVersion, FALSE, :expiresAt, :confirmedAt, :createdAt, :updatedAt
                        )
                        """)
                .param("uploadId", r.uploadId())
                .param("userId", r.userId())
                .param("examDefinitionId", r.examDefinitionId())
                .param("examCode", r.examCode())
                .param("examVersion", r.examVersion())
                .param("imageHash", r.imageHash())
                .param("mimeType", r.mimeType())
                .param("byteSize", r.byteSize())
                .param("parsedTopicsJson", topicsJson)
                .param("status", r.status().name())
                .param("deduplicatedFromCache", r.deduplicatedFromCache())
                .param("llmTokensUsed", r.llmTokensUsed())
                .param("lastIdempotencyKey", r.lastIdempotencyKey())
                .param("schemaVersion", r.schemaVersion())
                .param("expiresAt", Timestamp.from(r.expiresAt()))
                .param("confirmedAt", r.confirmedAt() == null ? null : Timestamp.from(r.confirmedAt()))
                .param("createdAt", Timestamp.from(r.createdAt()))
                .param("updatedAt", Timestamp.from(r.updatedAt()))
                .update();
        return r;
    }

    @Override
    public SyllabusUploadRecord confirmSyllabusUpload(
            UUID uploadId,
            UUID userId,
            List<ParsedSyllabusTopicItem> updatedTopics,
            Instant confirmedAt,
            Instant updatedAt
    ) {
        String topicsJson = jsonMapper.writeValueAsString(updatedTopics);
        jdbcClient.sql("""
                        UPDATE candidate_syllabus_uploads
                        SET parsed_topics_json = CAST(:parsedTopicsJson AS jsonb),
                            status = 'USER_CONFIRMED',
                            confirmed_at = :confirmedAt,
                            updated_at = :updatedAt
                        WHERE upload_id = :uploadId AND user_id = :userId
                        """)
                .param("parsedTopicsJson", topicsJson)
                .param("confirmedAt", Timestamp.from(confirmedAt))
                .param("updatedAt", Timestamp.from(updatedAt))
                .param("uploadId", uploadId)
                .param("userId", userId)
                .update();
        return findSyllabusUploadById(uploadId).orElseThrow();
    }

    @Override
    public List<SyllabusUploadRecord> findSyllabusUploadsByUserId(UUID userId, int limit) {
        return jdbcClient.sql("""
                        SELECT upload_id, user_id, exam_definition_id, exam_code, exam_version,
                               image_hash, mime_type, byte_size, parsed_topics_json, status,
                               deduplicated_from_cache, llm_tokens_used, last_idempotency_key,
                               schema_version, training_eligible, expires_at, confirmed_at, created_at, updated_at
                        FROM candidate_syllabus_uploads
                        WHERE user_id = :userId
                        ORDER BY created_at DESC
                        LIMIT :limit
                        """)
                .param("userId", userId)
                .param("limit", limit)
                .query((rs, rowNum) -> mapUpload(rs))
                .list();
    }

    @Override
    public int deleteAllCandidateDataForUser(UUID userId) {
        int uploads = jdbcClient.sql("DELETE FROM candidate_syllabus_uploads WHERE user_id = :userId")
                .param("userId", userId)
                .update();
        int history = jdbcClient.sql("DELETE FROM candidate_profile_horizon_history WHERE user_id = :userId")
                .param("userId", userId)
                .update();
        int profiles = jdbcClient.sql("DELETE FROM candidate_profiles WHERE user_id = :userId")
                .param("userId", userId)
                .update();
        return uploads + history + profiles;
    }

    @Override
    public int purgeExpiredSyllabusUploads(Instant cutoff) {
        return jdbcClient.sql("DELETE FROM candidate_syllabus_uploads WHERE expires_at < :cutoff")
                .param("cutoff", Timestamp.from(cutoff))
                .update();
    }

    private CandidateProfileRecord mapProfile(java.sql.ResultSet rs) throws java.sql.SQLException {
        String targetIdRaw = rs.getString("active_target_id");
        return new CandidateProfileRecord(
                UUID.fromString(rs.getString("profile_id")),
                UUID.fromString(rs.getString("user_id")),
                UUID.fromString(rs.getString("active_exam_definition_id")),
                rs.getString("active_exam_code"),
                rs.getString("pinned_exam_version"),
                targetIdRaw == null ? null : UUID.fromString(targetIdRaw),
                Jurisdiction.valueOf(rs.getString("jurisdiction")),
                rs.getDate("target_date").toLocalDate(),
                rs.getBigDecimal("target_score"),
                rs.getInt("daily_study_minutes"),
                rs.getInt("weekly_study_minutes"),
                new AccessibilityAccommodations(
                        rs.getBigDecimal("extra_time_multiplier"),
                        rs.getBoolean("high_contrast_enabled"),
                        rs.getBoolean("screen_reader_optimized"),
                        rs.getBoolean("reduced_motion_enabled")
                ),
                rs.getBigDecimal("baseline_mastery"),
                rs.getInt("required_daily_minutes"),
                FeasibilityStatus.valueOf(rs.getString("feasibility_status")),
                rs.getString("last_idempotency_key"),
                rs.getString("schema_version"),
                rs.getBoolean("training_eligible"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }

    private SyllabusUploadRecord mapUpload(java.sql.ResultSet rs) throws java.sql.SQLException {
        String topicsJson = rs.getString("parsed_topics_json");
        List<ParsedSyllabusTopicItem> topics = topicsJson == null || topicsJson.isBlank()
                ? List.of()
                : jsonMapper.readValue(topicsJson, PARSED_TOPICS_TYPE);
        Timestamp confirmedTs = rs.getTimestamp("confirmed_at");
        return new SyllabusUploadRecord(
                UUID.fromString(rs.getString("upload_id")),
                UUID.fromString(rs.getString("user_id")),
                UUID.fromString(rs.getString("exam_definition_id")),
                rs.getString("exam_code"),
                rs.getString("exam_version"),
                rs.getString("image_hash"),
                rs.getString("mime_type"),
                rs.getInt("byte_size"),
                topics,
                SyllabusUploadStatus.valueOf(rs.getString("status")),
                rs.getBoolean("deduplicated_from_cache"),
                rs.getInt("llm_tokens_used"),
                rs.getString("last_idempotency_key"),
                rs.getString("schema_version"),
                rs.getBoolean("training_eligible"),
                rs.getTimestamp("expires_at").toInstant(),
                confirmedTs == null ? null : confirmedTs.toInstant(),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }
}
