package com.verifiedai.tutoring.infrastructure.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verifiedai.tutoring.domain.AttemptDiagnosisOwnership;
import com.verifiedai.tutoring.domain.MicroLessonCard;
import com.verifiedai.tutoring.domain.MicroLessonCompletionRecord;
import com.verifiedai.tutoring.domain.MicroLessonCoreRule;
import com.verifiedai.tutoring.domain.MicroLessonCounterexample;
import com.verifiedai.tutoring.domain.MicroLessonQuickCheck;
import com.verifiedai.tutoring.domain.MicroLessonRepository;
import com.verifiedai.tutoring.domain.MicroLessonTriggerSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcMicroLessonRepository implements MicroLessonRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcMicroLessonRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    @Override
    public Optional<MicroLessonCard> findVerifiedCard(String mistakeCode, String locale) {
        String sql = """
                SELECT card_id, mistake_code, locale, schema_version, title, focus_badge_label,
                       estimated_duration_seconds, card_payload, verification_status,
                       verification_hash, verifier_version
                FROM micro_lesson_catalog
                WHERE mistake_code = ? AND locale = ? AND verification_status = 'VERIFIED'
                LIMIT 1
                """;
        List<MicroLessonCard> rows = jdbc.query(
                sql,
                (rs, rowNum) -> {
                    try {
                        Map<String, Object> payload = objectMapper.readValue(rs.getString("card_payload"), MAP_TYPE);
                        MicroLessonCoreRule coreRule = objectMapper.convertValue(
                                payload.get("coreRule"), MicroLessonCoreRule.class
                        );
                        MicroLessonCounterexample counterexample = objectMapper.convertValue(
                                payload.get("brokenVsValidCounterexample"), MicroLessonCounterexample.class
                        );
                        MicroLessonQuickCheck quickCheck = objectMapper.convertValue(
                                payload.get("interactiveQuickCheck"), MicroLessonQuickCheck.class
                        );
                        return new MicroLessonCard(
                                rs.getString("schema_version"),
                                UUID.fromString(rs.getString("card_id")),
                                rs.getString("mistake_code"),
                                rs.getString("locale"),
                                rs.getString("title"),
                                rs.getString("focus_badge_label"),
                                rs.getInt("estimated_duration_seconds"),
                                null,
                                null,
                                coreRule,
                                counterexample,
                                quickCheck,
                                rs.getString("verification_status"),
                                rs.getString("verifier_version"),
                                rs.getString("verification_hash")
                        );
                    } catch (Exception ex) {
                        throw new IllegalStateException("Failed to deserialize micro_lesson_catalog payload", ex);
                    }
                },
                mistakeCode,
                locale
        );
        return rows.stream().findFirst();
    }

    @Override
    public boolean hasActiveMistakeDiagnosis(UUID userId, String mistakeCode) {
        Integer dnaCount = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM learner_mistake_dna_profiles
                WHERE user_id = ? AND mistake_code = ? AND recurrence_state <> 'STABLE_RESOLVED'
                """,
                Integer.class,
                userId,
                mistakeCode
        );
        if (dnaCount != null && dnaCount > 0) {
            return true;
        }
        Integer classificationCount = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM attempt_mistake_classifications
                WHERE user_id = ? AND mistake_code = ?
                """,
                Integer.class,
                userId,
                mistakeCode
        );
        return classificationCount != null && classificationCount > 0;
    }

    @Override
    public AttemptDiagnosisOwnership checkAttemptDiagnosisOwnership(UUID userId, UUID sourceAttemptId, String mistakeCode) {
        Integer ownedAttemptCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM student_attempts WHERE id = ? AND user_id = ?",
                Integer.class,
                sourceAttemptId,
                userId
        );
        if (ownedAttemptCount == null || ownedAttemptCount == 0) {
            return AttemptDiagnosisOwnership.NOT_FOUND_OR_FOREIGN;
        }

        Integer matchedClassifications = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM attempt_mistake_classifications
                WHERE user_id = ? AND attempt_id = ? AND mistake_code = ?
                """,
                Integer.class,
                userId,
                sourceAttemptId,
                mistakeCode
        );
        if (matchedClassifications != null && matchedClassifications > 0) {
            return AttemptDiagnosisOwnership.OWNED_DIAGNOSIS_ELIGIBLE;
        }

        Integer twinFailures = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM counterfactual_twin_drills
                WHERE user_id = ? AND source_attempt_id = ? AND status = 'FAILED_UNASSISTED'
                """,
                Integer.class,
                userId,
                sourceAttemptId
        );
        if (twinFailures != null && twinFailures >= 2) {
            return AttemptDiagnosisOwnership.OWNED_DIAGNOSIS_ELIGIBLE;
        }
        return AttemptDiagnosisOwnership.OWNED_NO_DIAGNOSIS;
    }

    @Override
    public boolean hasEligibleContextualTrigger(
            UUID userId,
            String mistakeCode,
            MicroLessonTriggerSource triggerSource,
            UUID sourceAttemptId
    ) {
        return switch (triggerSource) {
            case TWIN_SECOND_FAILURE -> {
                Integer failedTwins = sourceAttemptId != null
                        ? jdbc.queryForObject(
                                """
                                SELECT COUNT(*)
                                FROM counterfactual_twin_drills
                                WHERE user_id = ? AND source_attempt_id = ? AND status = 'FAILED_UNASSISTED'
                                """,
                                Integer.class,
                                userId,
                                sourceAttemptId
                        )
                        : jdbc.queryForObject(
                                """
                                SELECT COUNT(*)
                                FROM counterfactual_twin_drills
                                WHERE user_id = ? AND status = 'FAILED_UNASSISTED'
                                """,
                                Integer.class,
                                userId
                        );
                yield failedTwins != null && failedTwins >= 2;
            }
            case SOCRATIC_RUNG_2 -> {
                Integer rung2Hints = jdbc.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM tutor_hints
                        WHERE user_id = ? AND (sequence_number >= 2 OR unproductive_count >= 2)
                        """,
                        Integer.class,
                        userId
                );
                yield rung2Hints != null && rung2Hints > 0;
            }
            case COACH_REPAIR_DRAWER -> hasActiveMistakeDiagnosis(userId, mistakeCode);
        };
    }

    @Override
    public Optional<MicroLessonCompletionRecord> findCompletionByIdempotencyKey(UUID userId, String idempotencyKey) {
        String sql = """
                SELECT completion_id, user_id, card_id, mistake_code, locale, source_attempt_id,
                       trigger_source, selected_option_id, quick_check_passed, dwell_time_ms,
                       idempotency_key, training_eligible, completed_at
                FROM learner_micro_lesson_completions
                WHERE user_id = ? AND idempotency_key = ?
                LIMIT 1
                """;
        List<MicroLessonCompletionRecord> rows = jdbc.query(
                sql,
                (rs, rowNum) -> {
                    String rawAttemptId = rs.getString("source_attempt_id");
                    Timestamp ts = rs.getTimestamp("completed_at");
                    return new MicroLessonCompletionRecord(
                            UUID.fromString(rs.getString("completion_id")),
                            UUID.fromString(rs.getString("user_id")),
                            UUID.fromString(rs.getString("card_id")),
                            rs.getString("mistake_code"),
                            rs.getString("locale"),
                            rawAttemptId != null ? UUID.fromString(rawAttemptId) : null,
                            MicroLessonTriggerSource.valueOf(rs.getString("trigger_source")),
                            rs.getString("selected_option_id"),
                            rs.getBoolean("quick_check_passed"),
                            rs.getInt("dwell_time_ms"),
                            rs.getString("idempotency_key"),
                            rs.getBoolean("training_eligible"),
                            ts != null ? ts.toInstant() : Instant.now()
                    );
                },
                userId,
                idempotencyKey
        );
        return rows.stream().findFirst();
    }

    @Override
    public void saveCompletion(MicroLessonCompletionRecord record) {
        jdbc.update(
                """
                INSERT INTO learner_micro_lesson_completions (
                    completion_id, user_id, card_id, mistake_code, locale, source_attempt_id,
                    trigger_source, selected_option_id, quick_check_passed, dwell_time_ms,
                    idempotency_key, training_eligible, completed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                record.completionId(),
                record.userId(),
                record.cardId(),
                record.mistakeCode(),
                record.locale(),
                record.sourceAttemptId(),
                record.triggerSource().name(),
                record.selectedOptionId(),
                record.quickCheckPassed(),
                record.dwellTimeMs(),
                record.idempotencyKey(),
                false,
                Timestamp.from(record.completedAt())
        );
    }

    @Override
    public List<MicroLessonCompletionRecord> findCompletionsByUserId(UUID userId) {
        String sql = """
                SELECT completion_id, user_id, card_id, mistake_code, locale, source_attempt_id,
                       trigger_source, selected_option_id, quick_check_passed, dwell_time_ms,
                       idempotency_key, training_eligible, completed_at
                FROM learner_micro_lesson_completions
                WHERE user_id = ?
                ORDER BY completed_at ASC
                """;
        return jdbc.query(
                sql,
                (rs, rowNum) -> {
                    String rawAttemptId = rs.getString("source_attempt_id");
                    Timestamp ts = rs.getTimestamp("completed_at");
                    return new MicroLessonCompletionRecord(
                            UUID.fromString(rs.getString("completion_id")),
                            UUID.fromString(rs.getString("user_id")),
                            UUID.fromString(rs.getString("card_id")),
                            rs.getString("mistake_code"),
                            rs.getString("locale"),
                            rawAttemptId != null ? UUID.fromString(rawAttemptId) : null,
                            MicroLessonTriggerSource.valueOf(rs.getString("trigger_source")),
                            rs.getString("selected_option_id"),
                            rs.getBoolean("quick_check_passed"),
                            rs.getInt("dwell_time_ms"),
                            rs.getString("idempotency_key"),
                            rs.getBoolean("training_eligible"),
                            ts != null ? ts.toInstant() : Instant.now()
                    );
                },
                userId
        );
    }

    @Override
    public int deleteCompletionsByUserId(UUID userId) {
        return jdbc.update(
                "DELETE FROM learner_micro_lesson_completions WHERE user_id = ?",
                userId
        );
    }
}
