package com.verifiedai.studyplan.gamification;

import com.verifiedai.studyplan.gamification.GamificationModels.DailyStreakLedgerRecord;
import com.verifiedai.studyplan.gamification.GamificationModels.LearnerAchievementRecord;
import com.verifiedai.studyplan.gamification.GamificationModels.LearnerStreakRecord;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL JDBC repository for Sprint 8.8 tables ({@code learner_streaks},
 * {@code learner_daily_streak_ledger}, and {@code learner_achievements}).
 *
 * <p>Uses explicit {@link Types#TIMESTAMP}, {@link Types#DATE}, and {@link Types#OTHER}
 * parameter bindings for deterministic PostgreSQL persistence.
 */
@Repository
public class JdbcGamificationRepository {

    private static final String SELECT_STREAK_SQL = """
            SELECT user_id, current_streak_days, longest_streak_days,
                   streak_freezes_remaining, freezes_used_this_month, freeze_quota_month,
                   learner_timezone, last_verified_independent_date, last_activity_local_date,
                   last_activity_evidence_type, streak_status,
                   total_independent_days, total_assisted_continuity_days,
                   training_eligible, created_at, updated_at
            FROM learner_streaks
            WHERE user_id = :userId
            """;

    private static final String SELECT_STREAK_FOR_UPDATE_SQL = SELECT_STREAK_SQL + " FOR UPDATE";

    private static final RowMapper<LearnerStreakRecord> STREAK_ROW_MAPPER = (rs, rowNum) -> new LearnerStreakRecord(
            rs.getObject("user_id", UUID.class),
            rs.getInt("current_streak_days"),
            rs.getInt("longest_streak_days"),
            rs.getInt("streak_freezes_remaining"),
            rs.getInt("freezes_used_this_month"),
            rs.getString("freeze_quota_month"),
            rs.getString("learner_timezone"),
            toLocalDate(rs.getDate("last_verified_independent_date")),
            toLocalDate(rs.getDate("last_activity_local_date")),
            GamificationEvidenceType.fromNullableStored(rs.getString("last_activity_evidence_type")),
            StreakStatus.valueOf(rs.getString("streak_status")),
            rs.getInt("total_independent_days"),
            rs.getInt("total_assisted_continuity_days"),
            rs.getBoolean("training_eligible"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
    );

    private static final RowMapper<DailyStreakLedgerRecord> LEDGER_ROW_MAPPER = (rs, rowNum) -> new DailyStreakLedgerRecord(
            rs.getObject("ledger_id", UUID.class),
            rs.getObject("user_id", UUID.class),
            toLocalDate(rs.getDate("activity_local_date")),
            rs.getString("learner_timezone"),
            DailyStreakStatus.valueOf(rs.getString("daily_status")),
            rs.getString("highest_evidence_type"),
            rs.getInt("independent_activity_count"),
            rs.getInt("assisted_activity_count"),
            rs.getBoolean("streak_incremented"),
            rs.getBoolean("freeze_consumed"),
            rs.getInt("streak_days_snapshot"),
            rs.getString("evidence_reference"),
            rs.getString("last_idempotency_key"),
            rs.getBoolean("training_eligible"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
    );

    private static final RowMapper<LearnerAchievementRecord> ACHIEVEMENT_ROW_MAPPER = (rs, rowNum) -> new LearnerAchievementRecord(
            rs.getObject("achievement_id", UUID.class),
            rs.getObject("user_id", UUID.class),
            PedagogicalBadgeCode.valueOf(rs.getString("badge_code")),
            PedagogicalBadgeCategory.valueOf(rs.getString("badge_category")),
            rs.getString("evidence_reference"),
            toLocalDate(rs.getDate("unlocked_local_date")),
            rs.getTimestamp("unlocked_at").toInstant(),
            rs.getBoolean("training_eligible")
    );

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcGamificationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<LearnerStreakRecord> findStreakByUserIdForUpdate(UUID userId) {
        List<LearnerStreakRecord> rows = jdbc.query(
                SELECT_STREAK_FOR_UPDATE_SQL,
                new MapSqlParameterSource().addValue("userId", userId, Types.OTHER),
                STREAK_ROW_MAPPER
        );
        return rows.stream().findFirst();
    }

    public Optional<LearnerStreakRecord> findStreakByUserId(UUID userId) {
        List<LearnerStreakRecord> rows = jdbc.query(
                SELECT_STREAK_SQL,
                new MapSqlParameterSource().addValue("userId", userId, Types.OTHER),
                STREAK_ROW_MAPPER
        );
        return rows.stream().findFirst();
    }

    public void upsertStreak(LearnerStreakRecord record) {
        String sql = """
                INSERT INTO learner_streaks (
                    user_id, current_streak_days, longest_streak_days,
                    streak_freezes_remaining, freezes_used_this_month, freeze_quota_month,
                    learner_timezone, last_verified_independent_date, last_activity_local_date,
                    last_activity_evidence_type, streak_status,
                    total_independent_days, total_assisted_continuity_days,
                    training_eligible, created_at, updated_at
                ) VALUES (
                    :userId, :currentStreakDays, :longestStreakDays,
                    :streakFreezesRemaining, :freezesUsedThisMonth, :freezeQuotaMonth,
                    :learnerTimezone, :lastVerifiedIndependentDate, :lastActivityLocalDate,
                    :lastActivityEvidenceType, :streakStatus,
                    :totalIndependentDays, :totalAssistedContinuityDays,
                    FALSE, :createdAt, :updatedAt
                )
                ON CONFLICT (user_id) DO UPDATE SET
                    current_streak_days = EXCLUDED.current_streak_days,
                    longest_streak_days = EXCLUDED.longest_streak_days,
                    streak_freezes_remaining = EXCLUDED.streak_freezes_remaining,
                    freezes_used_this_month = EXCLUDED.freezes_used_this_month,
                    freeze_quota_month = EXCLUDED.freeze_quota_month,
                    learner_timezone = EXCLUDED.learner_timezone,
                    last_verified_independent_date = EXCLUDED.last_verified_independent_date,
                    last_activity_local_date = EXCLUDED.last_activity_local_date,
                    last_activity_evidence_type = EXCLUDED.last_activity_evidence_type,
                    streak_status = EXCLUDED.streak_status,
                    total_independent_days = EXCLUDED.total_independent_days,
                    total_assisted_continuity_days = EXCLUDED.total_assisted_continuity_days,
                    training_eligible = FALSE,
                    updated_at = EXCLUDED.updated_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", record.userId(), Types.OTHER)
                .addValue("currentStreakDays", record.currentStreakDays(), Types.INTEGER)
                .addValue("longestStreakDays", record.longestStreakDays(), Types.INTEGER)
                .addValue("streakFreezesRemaining", record.streakFreezesRemaining(), Types.INTEGER)
                .addValue("freezesUsedThisMonth", record.freezesUsedThisMonth(), Types.INTEGER)
                .addValue("freezeQuotaMonth", record.freezeQuotaMonth(), Types.VARCHAR)
                .addValue("learnerTimezone", record.learnerTimezone(), Types.VARCHAR)
                .addValue("lastVerifiedIndependentDate", toSqlDate(record.lastVerifiedIndependentDate()), Types.DATE)
                .addValue("lastActivityLocalDate", toSqlDate(record.lastActivityLocalDate()), Types.DATE)
                .addValue("lastActivityEvidenceType",
                        record.lastActivityEvidenceType() != null ? record.lastActivityEvidenceType().name() : null,
                        Types.VARCHAR)
                .addValue("streakStatus", record.streakStatus().name(), Types.VARCHAR)
                .addValue("totalIndependentDays", record.totalIndependentDays(), Types.INTEGER)
                .addValue("totalAssistedContinuityDays", record.totalAssistedContinuityDays(), Types.INTEGER)
                .addValue("createdAt", Timestamp.from(record.createdAt()), Types.TIMESTAMP)
                .addValue("updatedAt", Timestamp.from(record.updatedAt()), Types.TIMESTAMP);
        jdbc.update(sql, params);
    }

    public Optional<DailyStreakLedgerRecord> findDailyLedgerByUserAndDate(UUID userId, LocalDate date) {
        String sql = """
                SELECT ledger_id, user_id, activity_local_date, learner_timezone,
                       daily_status, highest_evidence_type,
                       independent_activity_count, assisted_activity_count,
                       streak_incremented, freeze_consumed, streak_days_snapshot,
                       evidence_reference, last_idempotency_key,
                       training_eligible, created_at, updated_at
                FROM learner_daily_streak_ledger
                WHERE user_id = :userId AND activity_local_date = :activityLocalDate
                """;
        List<DailyStreakLedgerRecord> rows = jdbc.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("userId", userId, Types.OTHER)
                        .addValue("activityLocalDate", toSqlDate(date), Types.DATE),
                LEDGER_ROW_MAPPER
        );
        return rows.stream().findFirst();
    }

    public List<DailyStreakLedgerRecord> findRecentDailyLedger(UUID userId, int limit) {
        int clampedLimit = Math.max(1, Math.min(limit, 31));
        String sql = """
                SELECT ledger_id, user_id, activity_local_date, learner_timezone,
                       daily_status, highest_evidence_type,
                       independent_activity_count, assisted_activity_count,
                       streak_incremented, freeze_consumed, streak_days_snapshot,
                       evidence_reference, last_idempotency_key,
                       training_eligible, created_at, updated_at
                FROM learner_daily_streak_ledger
                WHERE user_id = :userId
                ORDER BY activity_local_date DESC
                LIMIT :limit
                """;
        return jdbc.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("userId", userId, Types.OTHER)
                        .addValue("limit", clampedLimit, Types.INTEGER),
                LEDGER_ROW_MAPPER
        );
    }

    public void upsertDailyLedger(DailyStreakLedgerRecord entry) {
        String sql = """
                INSERT INTO learner_daily_streak_ledger (
                    ledger_id, user_id, activity_local_date, learner_timezone,
                    daily_status, highest_evidence_type,
                    independent_activity_count, assisted_activity_count,
                    streak_incremented, freeze_consumed, streak_days_snapshot,
                    evidence_reference, last_idempotency_key,
                    training_eligible, created_at, updated_at
                ) VALUES (
                    :ledgerId, :userId, :activityLocalDate, :learnerTimezone,
                    :dailyStatus, :highestEvidenceType,
                    :independentActivityCount, :assistedActivityCount,
                    :streakIncremented, :freezeConsumed, :streakDaysSnapshot,
                    :evidenceReference, :lastIdempotencyKey,
                    FALSE, :createdAt, :updatedAt
                )
                ON CONFLICT (user_id, activity_local_date) DO UPDATE SET
                    learner_timezone = EXCLUDED.learner_timezone,
                    daily_status = EXCLUDED.daily_status,
                    highest_evidence_type = EXCLUDED.highest_evidence_type,
                    independent_activity_count = EXCLUDED.independent_activity_count,
                    assisted_activity_count = EXCLUDED.assisted_activity_count,
                    streak_incremented = EXCLUDED.streak_incremented,
                    freeze_consumed = EXCLUDED.freeze_consumed,
                    streak_days_snapshot = EXCLUDED.streak_days_snapshot,
                    evidence_reference = EXCLUDED.evidence_reference,
                    last_idempotency_key = EXCLUDED.last_idempotency_key,
                    training_eligible = FALSE,
                    updated_at = EXCLUDED.updated_at
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ledgerId", entry.ledgerId(), Types.OTHER)
                .addValue("userId", entry.userId(), Types.OTHER)
                .addValue("activityLocalDate", toSqlDate(entry.activityLocalDate()), Types.DATE)
                .addValue("learnerTimezone", entry.learnerTimezone(), Types.VARCHAR)
                .addValue("dailyStatus", entry.dailyStatus().name(), Types.VARCHAR)
                .addValue("highestEvidenceType", entry.highestEvidenceType(), Types.VARCHAR)
                .addValue("independentActivityCount", entry.independentActivityCount(), Types.INTEGER)
                .addValue("assistedActivityCount", entry.assistedActivityCount(), Types.INTEGER)
                .addValue("streakIncremented", entry.streakIncremented(), Types.BOOLEAN)
                .addValue("freezeConsumed", entry.freezeConsumed(), Types.BOOLEAN)
                .addValue("streakDaysSnapshot", entry.streakDaysSnapshot(), Types.INTEGER)
                .addValue("evidenceReference", entry.evidenceReference(), Types.VARCHAR)
                .addValue("lastIdempotencyKey", entry.lastIdempotencyKey(), Types.VARCHAR)
                .addValue("createdAt", Timestamp.from(entry.createdAt()), Types.TIMESTAMP)
                .addValue("updatedAt", Timestamp.from(entry.updatedAt()), Types.TIMESTAMP);
        jdbc.update(sql, params);
    }

    public List<LearnerAchievementRecord> findAchievementsByUserId(UUID userId) {
        String sql = """
                SELECT achievement_id, user_id, badge_code, badge_category,
                       evidence_reference, unlocked_local_date, unlocked_at, training_eligible
                FROM learner_achievements
                WHERE user_id = :userId
                ORDER BY unlocked_at DESC
                """;
        return jdbc.query(
                sql,
                new MapSqlParameterSource().addValue("userId", userId, Types.OTHER),
                ACHIEVEMENT_ROW_MAPPER
        );
    }

    public boolean insertAchievementIfAbsent(LearnerAchievementRecord achievement) {
        String sql = """
                INSERT INTO learner_achievements (
                    achievement_id, user_id, badge_code, badge_category,
                    evidence_reference, unlocked_local_date, unlocked_at, training_eligible
                ) VALUES (
                    :achievementId, :userId, :badgeCode, :badgeCategory,
                    :evidenceReference, :unlockedLocalDate, :unlockedAt, FALSE
                )
                ON CONFLICT (user_id, badge_code) DO NOTHING
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("achievementId", achievement.achievementId(), Types.OTHER)
                .addValue("userId", achievement.userId(), Types.OTHER)
                .addValue("badgeCode", achievement.badgeCode().name(), Types.VARCHAR)
                .addValue("badgeCategory", achievement.badgeCategory().name(), Types.VARCHAR)
                .addValue("evidenceReference", achievement.evidenceReference(), Types.VARCHAR)
                .addValue("unlockedLocalDate", toSqlDate(achievement.unlockedLocalDate()), Types.DATE)
                .addValue("unlockedAt", Timestamp.from(achievement.unlockedAt()), Types.TIMESTAMP);
        return jdbc.update(sql, params) > 0;
    }

    public long countStreakByUserId(UUID userId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM learner_streaks WHERE user_id = :userId",
                new MapSqlParameterSource().addValue("userId", userId, Types.OTHER),
                Long.class
        );
        return count != null ? count : 0L;
    }

    public long countLedgerByUserId(UUID userId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM learner_daily_streak_ledger WHERE user_id = :userId",
                new MapSqlParameterSource().addValue("userId", userId, Types.OTHER),
                Long.class
        );
        return count != null ? count : 0L;
    }

    public long countAchievementsByUserId(UUID userId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM learner_achievements WHERE user_id = :userId",
                new MapSqlParameterSource().addValue("userId", userId, Types.OTHER),
                Long.class
        );
        return count != null ? count : 0L;
    }

    public void deleteAllByUserId(UUID userId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("userId", userId, Types.OTHER);
        jdbc.update("DELETE FROM learner_daily_streak_ledger WHERE user_id = :userId", params);
        jdbc.update("DELETE FROM learner_achievements WHERE user_id = :userId", params);
        jdbc.update("DELETE FROM learner_streaks WHERE user_id = :userId", params);
    }

    private static LocalDate toLocalDate(Date sqlDate) {
        return sqlDate != null ? sqlDate.toLocalDate() : null;
    }

    private static Date toSqlDate(LocalDate localDate) {
        return localDate != null ? Date.valueOf(localDate) : null;
    }
}
