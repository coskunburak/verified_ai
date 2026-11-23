package com.verifiedai.studyplan.lifecycle;

import com.verifiedai.notification.application.LifecycleCancellationResult;
import com.verifiedai.notification.application.LifecycleEvaluationResult;
import com.verifiedai.notification.application.LifecycleMessagingPort;
import com.verifiedai.notification.application.LifecycleSkipCommand;
import com.verifiedai.notification.application.LifecycleTriggerCommand;
import com.verifiedai.notification.application.NotificationPreferenceView;
import com.verifiedai.notification.application.NotificationSchedulingPort;
import com.verifiedai.sharedkernel.observability.CorrelationIds;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Owning-module trigger evaluator and obsolete-reminder canceller for {@code STUDY_PLAN} and {@code SPACED_REVIEW}.
 */
@Service
public class StudyPlanLifecycleMessagingService {

    private static final String SOURCE_MODULE = "studyplan";
    private static final String CATEGORY_STUDY_PLAN = "STUDY_PLAN";
    private static final String INTENT_DAILY_MISSION = "STUDY_PLAN_DAILY_MISSION";
    private static final String TEMPLATE_DAILY_MISSION = "notification.template.study_plan.daily_mission";
    private static final String DEEP_LINK_DAILY_MISSION = "verified-ai://study-plan/today";

    private static final String CATEGORY_SPACED_REVIEW = "SPACED_REVIEW";
    private static final String INTENT_SPACED_REVIEW_DUE = "SPACED_REVIEW_DUE";
    private static final String TEMPLATE_SPACED_REVIEW_DUE = "notification.template.spaced_review.due";
    private static final String DEEP_LINK_SPACED_REVIEW = "verified-ai://spaced-review";

    private final LifecycleMessagingPort lifecycleMessagingPort;
    private final NotificationSchedulingPort notificationSchedulingPort;
    private final JdbcClient jdbcClient;
    private final Clock clock;

    public StudyPlanLifecycleMessagingService(
        LifecycleMessagingPort lifecycleMessagingPort,
        NotificationSchedulingPort notificationSchedulingPort,
        JdbcClient jdbcClient,
        Clock clock
    ) {
        this.lifecycleMessagingPort = Objects.requireNonNull(lifecycleMessagingPort, "lifecycleMessagingPort");
        this.notificationSchedulingPort = Objects.requireNonNull(notificationSchedulingPort, "notificationSchedulingPort");
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public LifecycleEvaluationResult evaluateDailyMissionReminder(UUID userId, Instant now) {
        Objects.requireNonNull(userId, "userId must not be null");
        Instant evalTime = now != null ? now : clock.instant();
        NotificationPreferenceView prefs = notificationSchedulingPort.getPreferences(userId);
        ZoneId userZone = safeZone(prefs.timezone());
        LocalDate localDate = evalTime.atZone(userZone).toLocalDate();
        String idempotencyKey = dailyMissionIdempotencyKey(userId, localDate);
        String correlationId = CorrelationIds.current();

        DailySessionState session = findDailySession(userId, localDate);
        if (session == null) {
            return lifecycleMessagingPort.recordSkip(new LifecycleSkipCommand(
                userId,
                SOURCE_MODULE,
                CATEGORY_STUDY_PLAN,
                INTENT_DAILY_MISSION,
                idempotencyKey,
                TEMPLATE_DAILY_MISSION,
                "SKIPPED_NOT_ELIGIBLE",
                "NO_DAILY_SESSION_TODAY",
                correlationId
            ));
        }

        if ("COMPLETED".equals(session.status())) {
            return lifecycleMessagingPort.recordSkip(new LifecycleSkipCommand(
                userId,
                SOURCE_MODULE,
                CATEGORY_STUDY_PLAN,
                INTENT_DAILY_MISSION,
                idempotencyKey,
                TEMPLATE_DAILY_MISSION,
                "SKIPPED_ALREADY_COMPLETED",
                "DAILY_MISSION_ALREADY_COMPLETED",
                correlationId
            ));
        }

        int remainingTasks = resolveRemainingTasks(session);
        if (remainingTasks <= 0) {
            return lifecycleMessagingPort.recordSkip(new LifecycleSkipCommand(
                userId,
                SOURCE_MODULE,
                CATEGORY_STUDY_PLAN,
                INTENT_DAILY_MISSION,
                idempotencyKey,
                TEMPLATE_DAILY_MISSION,
                "SKIPPED_ALREADY_COMPLETED",
                "NO_REMAINING_TASKS",
                correlationId
            ));
        }

        Instant dayStart = localDate.atStartOfDay(userZone).toInstant();
        Instant expiresAt = localDate.plusDays(1).atStartOfDay(userZone).toInstant();
        String locale = lifecycleMessagingPort.resolvePreferredLocale(userId);
        return lifecycleMessagingPort.evaluateAndSchedule(new LifecycleTriggerCommand(
            userId,
            SOURCE_MODULE,
            CATEGORY_STUDY_PLAN,
            INTENT_DAILY_MISSION,
            idempotencyKey,
            locale,
            TEMPLATE_DAILY_MISSION,
            Map.of(
                "budgetMinutes", Integer.toString(Math.max(1, session.budgetMinutes())),
                "remainingTasks", Integer.toString(remainingTasks)
            ),
            DEEP_LINK_DAILY_MISSION,
            dayStart,
            expiresAt,
            correlationId
        ));
    }

    public LifecycleCancellationResult cancelDailyMissionReminderIfCompleted(UUID userId, LocalDate localDate) {
        Objects.requireNonNull(userId, "userId must not be null");
        LocalDate effectiveDate = localDate != null ? localDate : LocalDate.now(clock);
        String correlationId = CorrelationIds.current();
        LifecycleCancellationResult primary = lifecycleMessagingPort.cancelPendingByIdempotencyKey(
            userId,
            SOURCE_MODULE,
            CATEGORY_STUDY_PLAN,
            INTENT_DAILY_MISSION,
            dailyMissionIdempotencyKey(userId, effectiveDate),
            "DAILY_MISSION_COMPLETED",
            correlationId
        );
        if (!primary.cancelled()) {
            ZoneId userZone = safeZone(notificationSchedulingPort.getPreferences(userId).timezone());
            LocalDate userLocalDate = clock.instant().atZone(userZone).toLocalDate();
            if (!userLocalDate.equals(effectiveDate)) {
                return lifecycleMessagingPort.cancelPendingByIdempotencyKey(
                    userId,
                    SOURCE_MODULE,
                    CATEGORY_STUDY_PLAN,
                    INTENT_DAILY_MISSION,
                    dailyMissionIdempotencyKey(userId, userLocalDate),
                    "DAILY_MISSION_COMPLETED",
                    correlationId
                );
            }
        }
        return primary;
    }

    public LifecycleEvaluationResult evaluateSpacedReviewReminder(UUID userId, Instant now) {
        Objects.requireNonNull(userId, "userId must not be null");
        Instant evalTime = now != null ? now : clock.instant();
        NotificationPreferenceView prefs = notificationSchedulingPort.getPreferences(userId);
        ZoneId userZone = safeZone(prefs.timezone());
        LocalDate localDate = evalTime.atZone(userZone).toLocalDate();
        Instant dayStart = localDate.atStartOfDay(userZone).toInstant();
        Instant dayEnd = localDate.plusDays(1).atStartOfDay(userZone).toInstant();
        String idempotencyKey = spacedReviewIdempotencyKey(userId, localDate);
        String correlationId = CorrelationIds.current();

        Integer completedToday = jdbcClient.sql("""
                SELECT COUNT(*)
                FROM spaced_review_queue
                WHERE user_id = :userId
                  AND status = 'COMPLETED'
                  AND completed_at >= :dayStart
                  AND completed_at < :dayEnd
                """)
            .param("userId", userId)
            .param("dayStart", Timestamp.from(dayStart))
            .param("dayEnd", Timestamp.from(dayEnd))
            .query(Integer.class)
            .single();
        if (completedToday != null && completedToday > 0) {
            return lifecycleMessagingPort.recordSkip(new LifecycleSkipCommand(
                userId,
                SOURCE_MODULE,
                CATEGORY_SPACED_REVIEW,
                INTENT_SPACED_REVIEW_DUE,
                idempotencyKey,
                TEMPLATE_SPACED_REVIEW_DUE,
                "SKIPPED_ALREADY_COMPLETED",
                "SPACED_REVIEW_COMPLETED_TODAY",
                correlationId
            ));
        }

        List<String> dueTriggerTypes = jdbcClient.sql("""
                SELECT trigger_type
                FROM spaced_review_queue
                WHERE user_id = :userId
                  AND status IN ('PENDING', 'SERVED')
                  AND due_at <= :now
                ORDER BY due_at ASC, id ASC
                """)
            .param("userId", userId)
            .param("now", Timestamp.from(evalTime))
            .query(String.class)
            .list();

        if (dueTriggerTypes.isEmpty()) {
            return lifecycleMessagingPort.recordSkip(new LifecycleSkipCommand(
                userId,
                SOURCE_MODULE,
                CATEGORY_SPACED_REVIEW,
                INTENT_SPACED_REVIEW_DUE,
                idempotencyKey,
                TEMPLATE_SPACED_REVIEW_DUE,
                "SKIPPED_NOT_ELIGIBLE",
                "NO_DUE_SPACED_ITEMS",
                correlationId
            ));
        }

        String triggerKind = dueTriggerTypes.contains("TRAP_IMMUNITY_7D")
            ? "TRAP_IMMUNITY_7D"
            : dueTriggerTypes.get(0);
        String locale = lifecycleMessagingPort.resolvePreferredLocale(userId);
        return lifecycleMessagingPort.evaluateAndSchedule(new LifecycleTriggerCommand(
            userId,
            SOURCE_MODULE,
            CATEGORY_SPACED_REVIEW,
            INTENT_SPACED_REVIEW_DUE,
            idempotencyKey,
            locale,
            TEMPLATE_SPACED_REVIEW_DUE,
            Map.of(
                "dueCount", Integer.toString(dueTriggerTypes.size()),
                "triggerKind", triggerKind
            ),
            DEEP_LINK_SPACED_REVIEW,
            dayStart,
            dayEnd,
            correlationId
        ));
    }

    public LifecycleCancellationResult cancelSpacedReviewReminderIfQueueCleared(UUID userId, Instant now) {
        return cancelSpacedReviewReminderIfQueueCleared(userId, now, null);
    }

    public LifecycleCancellationResult cancelSpacedReviewReminderIfQueueCleared(
        UUID userId,
        Instant now,
        UUID excludeCompletedItemId
    ) {
        Objects.requireNonNull(userId, "userId must not be null");
        Instant evalTime = now != null ? now : clock.instant();
        Integer remainingDue;
        if (excludeCompletedItemId == null) {
            remainingDue = jdbcClient.sql("""
                    SELECT COUNT(*)
                    FROM spaced_review_queue
                    WHERE user_id = :userId
                      AND status IN ('PENDING', 'SERVED')
                      AND due_at <= :now
                    """)
                .param("userId", userId)
                .param("now", Timestamp.from(evalTime))
                .query(Integer.class)
                .single();
        } else {
            remainingDue = jdbcClient.sql("""
                    SELECT COUNT(*)
                    FROM spaced_review_queue
                    WHERE user_id = :userId
                      AND status IN ('PENDING', 'SERVED')
                      AND due_at <= :now
                      AND id <> :excludeId
                    """)
                .param("userId", userId)
                .param("now", Timestamp.from(evalTime))
                .param("excludeId", excludeCompletedItemId)
                .query(Integer.class)
                .single();
        }

        ZoneId userZone = safeZone(notificationSchedulingPort.getPreferences(userId).timezone());
        LocalDate localDate = evalTime.atZone(userZone).toLocalDate();
        String idempotencyKey = spacedReviewIdempotencyKey(userId, localDate);
        if (remainingDue != null && remainingDue > 0) {
            return new LifecycleCancellationResult(
                userId,
                CATEGORY_SPACED_REVIEW,
                INTENT_SPACED_REVIEW_DUE,
                idempotencyKey,
                null,
                false,
                "DUE_ITEMS_REMAINING",
                evalTime,
                false
            );
        }

        return lifecycleMessagingPort.cancelPendingByIdempotencyKey(
            userId,
            SOURCE_MODULE,
            CATEGORY_SPACED_REVIEW,
            INTENT_SPACED_REVIEW_DUE,
            idempotencyKey,
            "SPACED_QUEUE_CLEARED",
            CorrelationIds.current()
        );
    }

    public void scanAndEvaluateBatch() {
        Instant startedAt = clock.instant();
        int batchSize = lifecycleMessagingPort.scanBatchSize();
        try {
            LocalDate minDate = startedAt.atZone(ZoneOffset.UTC).toLocalDate().minusDays(1);
            List<UUID> studyPlanUsers = jdbcClient.sql("""
                    SELECT DISTINCT s.user_id
                    FROM daily_coach_sessions s
                    JOIN users u ON u.id = s.user_id
                    WHERE u.status = 'ACTIVE'
                      AND s.status = 'IN_PROGRESS'
                      AND s.local_date >= :minDate
                    ORDER BY s.user_id ASC
                    LIMIT :limit
                    """)
                .param("minDate", minDate)
                .param("limit", batchSize)
                .query(UUID.class)
                .list();

            for (UUID userId : studyPlanUsers) {
                try {
                    evaluateDailyMissionReminder(userId, startedAt);
                } catch (RuntimeException ignored) {
                }
            }

            List<UUID> spacedUsers = jdbcClient.sql("""
                    SELECT DISTINCT q.user_id
                    FROM spaced_review_queue q
                    JOIN users u ON u.id = q.user_id
                    WHERE u.status = 'ACTIVE'
                      AND q.status IN ('PENDING', 'SERVED')
                      AND q.due_at <= :now
                    ORDER BY q.user_id ASC
                    LIMIT :limit
                    """)
                .param("now", Timestamp.from(startedAt))
                .param("limit", batchSize)
                .query(UUID.class)
                .list();

            for (UUID userId : spacedUsers) {
                try {
                    evaluateSpacedReviewReminder(userId, startedAt);
                } catch (RuntimeException ignored) {
                }
            }
        } finally {
            lifecycleMessagingPort.recordScanDuration(SOURCE_MODULE, Duration.between(startedAt, clock.instant()));
        }
    }

    private DailySessionState findDailySession(UUID userId, LocalDate localDate) {
        return jdbcClient.sql("""
                SELECT id, budget_minutes, status, completed_tasks_count, total_tasks_count
                FROM daily_coach_sessions
                WHERE user_id = :userId AND local_date = :localDate
                """)
            .param("userId", userId)
            .param("localDate", localDate)
            .query((rs, rowNum) -> new DailySessionState(
                rs.getObject("id", UUID.class),
                rs.getInt("budget_minutes"),
                rs.getString("status"),
                rs.getInt("completed_tasks_count"),
                rs.getInt("total_tasks_count")
            ))
            .optional()
            .orElse(null);
    }

    private int resolveRemainingTasks(DailySessionState session) {
        TaskCounts counts = jdbcClient.sql("""
                SELECT COUNT(*) FILTER (WHERE status = 'PENDING') AS pending_count,
                       COUNT(*) AS total_rows
                FROM daily_coach_tasks
                WHERE session_id = :sessionId
                """)
            .param("sessionId", session.sessionId())
            .query((rs, rowNum) -> new TaskCounts(rs.getInt("pending_count"), rs.getInt("total_rows")))
            .single();
        if (counts.totalRows() > 0) {
            return counts.pendingCount();
        }
        return Math.max(0, session.totalTasksCount() - session.completedTasksCount());
    }

    private static ZoneId safeZone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(timezone.trim());
        } catch (DateTimeException ignored) {
            return ZoneOffset.UTC;
        }
    }

    public static String dailyMissionIdempotencyKey(UUID userId, LocalDate localDate) {
        return "studyplan:daily:" + userId + ":" + localDate;
    }

    public static String spacedReviewIdempotencyKey(UUID userId, LocalDate localDate) {
        return "spaced:due:" + userId + ":" + localDate;
    }

    private record DailySessionState(
        UUID sessionId,
        int budgetMinutes,
        String status,
        int completedTasksCount,
        int totalTasksCount
    ) {
    }

    private record TaskCounts(int pendingCount, int totalRows) {
    }
}
