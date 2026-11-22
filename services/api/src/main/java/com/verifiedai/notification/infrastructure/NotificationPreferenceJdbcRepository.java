package com.verifiedai.notification.infrastructure;

import com.verifiedai.notification.domain.NotificationPreferenceSnapshot;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationPreferenceJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public NotificationPreferenceJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void lockUser(UUID userId) {
        jdbcTemplate.query(
            "select pg_advisory_xact_lock(hashtextextended(?, 1041))",
            preparedStatement -> preparedStatement.setString(1, userId.toString()),
            resultSet -> {
            }
        );
    }

    public boolean isAccountActive(UUID userId) {
        String status = jdbcTemplate.query(
            "select status from users where id = ?",
            preparedStatement -> preparedStatement.setObject(1, userId),
            resultSet -> resultSet.next() ? resultSet.getString("status") : null
        );
        return "ACTIVE".equals(status);
    }

    public Optional<NotificationPreferenceSnapshot> findByUserId(UUID userId) {
        return jdbcTemplate.query(
            """
            select user_id, push_enabled, study_plan_enabled, spaced_review_enabled,
                   exam_deadline_enabled, billing_trial_reminder_enabled, account_security_enabled,
                   quiet_hours_enabled, quiet_hours_start_local, quiet_hours_end_local,
                   timezone, daily_cap, version, created_at, updated_at
            from notification_preferences
            where user_id = ?
            """,
            preparedStatement -> preparedStatement.setObject(1, userId),
            resultSet -> {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new NotificationPreferenceSnapshot(
                    resultSet.getObject("user_id", UUID.class),
                    resultSet.getBoolean("push_enabled"),
                    resultSet.getBoolean("study_plan_enabled"),
                    resultSet.getBoolean("spaced_review_enabled"),
                    resultSet.getBoolean("exam_deadline_enabled"),
                    resultSet.getBoolean("billing_trial_reminder_enabled"),
                    resultSet.getBoolean("account_security_enabled"),
                    resultSet.getBoolean("quiet_hours_enabled"),
                    resultSet.getTime("quiet_hours_start_local").toLocalTime(),
                    resultSet.getTime("quiet_hours_end_local").toLocalTime(),
                    resultSet.getString("timezone"),
                    resultSet.getInt("daily_cap"),
                    resultSet.getLong("version"),
                    resultSet.getTimestamp("created_at").toInstant(),
                    resultSet.getTimestamp("updated_at").toInstant()
                ));
            }
        );
    }

    public void insertInitial(NotificationPreferenceSnapshot snapshot) {
        jdbcTemplate.update(
            """
            insert into notification_preferences (
                user_id, push_enabled, study_plan_enabled, spaced_review_enabled,
                exam_deadline_enabled, billing_trial_reminder_enabled, account_security_enabled,
                quiet_hours_enabled, quiet_hours_start_local, quiet_hours_end_local,
                timezone, daily_cap, version, training_eligible, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, true, ?, ?, ?, ?, ?, ?, false, ?, ?)
            """,
            snapshot.userId(),
            snapshot.pushEnabled(),
            snapshot.studyPlanEnabled(),
            snapshot.spacedReviewEnabled(),
            snapshot.examDeadlineEnabled(),
            snapshot.billingTrialReminderEnabled(),
            snapshot.quietHoursEnabled(),
            Time.valueOf(snapshot.quietHoursStartLocal()),
            Time.valueOf(snapshot.quietHoursEndLocal()),
            snapshot.timezone(),
            snapshot.dailyCap(),
            snapshot.version(),
            Timestamp.from(snapshot.createdAt()),
            Timestamp.from(snapshot.updatedAt())
        );
    }

    public int updateOptimistic(NotificationPreferenceSnapshot snapshot, long expectedVersion) {
        return jdbcTemplate.update(
            """
            update notification_preferences
               set push_enabled = ?,
                   study_plan_enabled = ?,
                   spaced_review_enabled = ?,
                   exam_deadline_enabled = ?,
                   billing_trial_reminder_enabled = ?,
                   quiet_hours_enabled = ?,
                   quiet_hours_start_local = ?,
                   quiet_hours_end_local = ?,
                   timezone = ?,
                   daily_cap = ?,
                   version = version + 1,
                   updated_at = ?
             where user_id = ?
               and version = ?
            """,
            snapshot.pushEnabled(),
            snapshot.studyPlanEnabled(),
            snapshot.spacedReviewEnabled(),
            snapshot.examDeadlineEnabled(),
            snapshot.billingTrialReminderEnabled(),
            snapshot.quietHoursEnabled(),
            Time.valueOf(snapshot.quietHoursStartLocal()),
            Time.valueOf(snapshot.quietHoursEndLocal()),
            snapshot.timezone(),
            snapshot.dailyCap(),
            Timestamp.from(snapshot.updatedAt()),
            snapshot.userId(),
            expectedVersion
        );
    }

    public int deleteByUserId(UUID userId) {
        return jdbcTemplate.update("delete from notification_preferences where user_id = ?", userId);
    }
}
