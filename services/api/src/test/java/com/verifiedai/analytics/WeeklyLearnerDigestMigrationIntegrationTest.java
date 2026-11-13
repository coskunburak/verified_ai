package com.verifiedai.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.verifiedai.analytics.WeeklyReportDtos.WeeklyDigestDto;
import com.verifiedai.analytics.WeeklyReportDtos.WeeklySharePacketDto;
import com.verifiedai.analytics.WeeklyReportDtos.WeeklySharePacketRequest;
import com.verifiedai.integration.PostgresIntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class WeeklyLearnerDigestMigrationIntegrationTest extends PostgresIntegrationTestSupport {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-000000000891");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WeeklyLearnerDigestService digestService;

    @Autowired
    private WeeklyLearnerDigestRepository digestRepository;

    @Autowired
    private AnalyticsPrivacyLifecycleContributor privacyContributor;

    @BeforeEach
    void prepareLearner() {
        jdbcTemplate.update("DELETE FROM weekly_learner_digests WHERE user_id = ?", USER_ID);
        jdbcTemplate.update("DELETE FROM study_sessions WHERE user_id = ?", USER_ID);
        jdbcTemplate.update(
            """
            INSERT INTO users (id, status, created_at, updated_at)
            VALUES (?, 'ACTIVE', NOW(), NOW())
            ON CONFLICT (id) DO NOTHING
            """,
            USER_ID
        );
    }

    @Test
    void migrationV052EnforcesTrainingEligibleFalseAndWeekConstraints() {
        assertThatThrownBy(() -> jdbcTemplate.update(
            """
            INSERT INTO weekly_learner_digests (
                digest_id, user_id, week_iso, week_start_date, week_end_date, locale, status,
                solved_attempts_count, independent_twins_solved_count, study_minutes, active_days_count,
                digest_payload_json, explainable_recommendations_json, training_eligible, generated_at, updated_at
            ) VALUES (?, ?, '2026-W45', DATE '2026-11-02', DATE '2026-11-08', 'en', 'GENERATED',
                      0, 0, 0, 0, '{}'::jsonb, '[]'::jsonb, TRUE, NOW(), NOW())
            """,
            UUID.randomUUID(), USER_ID
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
            """
            INSERT INTO weekly_learner_digests (
                digest_id, user_id, week_iso, week_start_date, week_end_date, locale, status,
                solved_attempts_count, independent_twins_solved_count, study_minutes, active_days_count,
                digest_payload_json, explainable_recommendations_json, training_eligible, generated_at, updated_at
            ) VALUES (?, ?, '2026-13', DATE '2026-11-02', DATE '2026-11-08', 'en', 'GENERATED',
                      0, 0, 0, 0, '{}'::jsonb, '[]'::jsonb, FALSE, NOW(), NOW())
            """,
            UUID.randomUUID(), USER_ID
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void repositoryAndServiceRoundTripWithPostgresAndPrivacyLifecycleDelete() {
        jdbcTemplate.update(
            """
            INSERT INTO study_sessions (
                session_id, user_id, source_context, topic_code, locale, state,
                total_steps, current_step_index, completed_steps_count, last_sequence_number,
                active_duration_seconds, paused_duration_seconds, client_instance_id,
                step_blueprint_json, resume_snapshot_json, training_eligible,
                started_at, last_checkpoint_at, expires_at, created_at, updated_at
            ) VALUES (?, ?, 'DAILY_COACH', 'MATH.ARITHMETIC.FRACTIONS', 'en-US', 'COMPLETED',
                      3, 2, 3, 3, 900, 0, 'ios-test-01',
                      '[]'::jsonb, '{}'::jsonb, FALSE,
                      ?, ?, ?, ?, ?)
            """,
            UUID.randomUUID(), USER_ID,
            Timestamp.from(Instant.parse("2026-11-04T10:00:00Z")),
            Timestamp.from(Instant.parse("2026-11-04T10:15:00Z")),
            Timestamp.from(Instant.parse("2026-11-05T10:15:00Z")),
            Timestamp.from(Instant.parse("2026-11-04T10:00:00Z")),
            Timestamp.from(Instant.parse("2026-11-04T10:15:00Z"))
        );

        WeeklyDigestDto digest = digestService.getWeeklyDigest(USER_ID, "2026-W45", "en", true);
        assertThat(digest.trainingEligible()).isFalse();
        assertThat(digest.weekStartDate()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(digest.executiveSummary().studyMinutes()).isGreaterThanOrEqualTo(15);
        assertThat(digestRepository.findByUserAndWeek(USER_ID, "2026-W45")).isPresent();

        WeeklySharePacketDto packet = digestService.createSharePacket(
            USER_ID, "2026-W45", new WeeklySharePacketRequest("TUTOR", "Learner A.", "en")
        );
        assertThat(packet.privacyAttestation().policyId()).isEqualTo("G-PRIV-03");
        assertThat(packet.trainingEligible()).isFalse();

        Map<String, Object> exported = privacyContributor.exportUserData(USER_ID);
        assertThat(exported.get("weeklyLearnerDigestCount")).isEqualTo(1);

        privacyContributor.deleteUserData(USER_ID, Instant.now());
        assertThat(digestRepository.findByUserAndWeek(USER_ID, "2026-W45")).isEmpty();
    }
}
