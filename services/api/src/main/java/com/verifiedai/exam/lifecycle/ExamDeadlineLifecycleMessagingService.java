package com.verifiedai.exam.lifecycle;

import com.verifiedai.exam.events.CandidateGoalUpdatedEvent;
import com.verifiedai.notification.application.LifecycleEvaluationResult;
import com.verifiedai.notification.application.LifecycleMessagingPort;
import com.verifiedai.notification.application.LifecycleSkipCommand;
import com.verifiedai.notification.application.LifecycleTriggerCommand;
import com.verifiedai.notification.application.NotificationPreferenceView;
import com.verifiedai.notification.application.NotificationSchedulingPort;
import com.verifiedai.sharedkernel.observability.CorrelationIds;
import java.sql.Date;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Owning-module lifecycle evaluator for calm {@code EXAM_DEADLINE} milestone reminders ({30, 14, 7, 2} days)
 * and automatic cancellation of obsolete milestone deliveries when candidate goals change.
 */
@Service
public class ExamDeadlineLifecycleMessagingService {

    public static final Set<Integer> MILESTONE_DAYS = Set.of(30, 14, 7, 2);

    private static final String SOURCE_MODULE = "exam";
    private static final String CATEGORY_EXAM_DEADLINE = "EXAM_DEADLINE";
    private static final String INTENT_COUNTDOWN = "EXAM_DEADLINE_COUNTDOWN";
    private static final String TEMPLATE_COUNTDOWN = "notification.template.exam_deadline.countdown";
    private static final String DEEP_LINK_EXAM_READINESS = "verified-ai://exam-readiness";

    private final LifecycleMessagingPort lifecycleMessagingPort;
    private final NotificationSchedulingPort notificationSchedulingPort;
    private final JdbcClient jdbcClient;
    private final Clock clock;

    public ExamDeadlineLifecycleMessagingService(
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

    public LifecycleEvaluationResult evaluateExamCountdownForUser(
        UUID userId,
        String examCode,
        LocalDate targetDate,
        Instant now
    ) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(targetDate, "targetDate must not be null");
        if (examCode == null || examCode.isBlank()) {
            throw new IllegalArgumentException("examCode must not be blank");
        }

        Instant evalTime = now != null ? now : clock.instant();
        NotificationPreferenceView prefs = notificationSchedulingPort.getPreferences(userId);
        ZoneId userZone = safeZone(prefs.timezone());
        LocalDate localToday = evalTime.atZone(userZone).toLocalDate();
        long daysRemaining = ChronoUnit.DAYS.between(localToday, targetDate);

        String sanitizedExamCode = sanitizeToken(examCode.trim());
        String correlationId = CorrelationIds.current();

        if (daysRemaining < 0 || daysRemaining > Integer.MAX_VALUE || !MILESTONE_DAYS.contains((int) daysRemaining)) {
            String skipKey = countdownIdempotencyKey(userId, sanitizedExamCode, targetDate, Math.max(0L, daysRemaining));
            return lifecycleMessagingPort.recordSkip(new LifecycleSkipCommand(
                userId,
                SOURCE_MODULE,
                CATEGORY_EXAM_DEADLINE,
                INTENT_COUNTDOWN,
                skipKey,
                TEMPLATE_COUNTDOWN,
                "SKIPPED_NOT_ELIGIBLE",
                daysRemaining < 0 ? "TARGET_DATE_IN_PAST" : "NON_MILESTONE_DAY",
                correlationId
            ));
        }

        int milestoneDays = (int) daysRemaining;
        String idempotencyKey = countdownIdempotencyKey(userId, sanitizedExamCode, targetDate, milestoneDays);
        Instant dayStart = localToday.atStartOfDay(userZone).toInstant();
        Instant expiresAt = localToday.plusDays(1).atStartOfDay(userZone).toInstant();
        String locale = lifecycleMessagingPort.resolvePreferredLocale(userId);

        return lifecycleMessagingPort.evaluateAndSchedule(new LifecycleTriggerCommand(
            userId,
            SOURCE_MODULE,
            CATEGORY_EXAM_DEADLINE,
            INTENT_COUNTDOWN,
            idempotencyKey,
            locale,
            TEMPLATE_COUNTDOWN,
            Map.of(
                "examCode", examCode.trim(),
                "daysRemaining", Integer.toString(milestoneDays)
            ),
            DEEP_LINK_EXAM_READINESS,
            dayStart,
            expiresAt,
            correlationId
        ));
    }

    @EventListener
    public void onCandidateGoalUpdated(CandidateGoalUpdatedEvent event) {
        if (event == null) {
            return;
        }
        try {
            String sanitizedExamCode = sanitizeToken(event.examCode().trim());
            String retainPrefix = event.targetDate() == null
                ? ""
                : "exam:countdown:" + event.userId() + ":" + sanitizedExamCode + ":" + event.targetDate() + ":";
            lifecycleMessagingPort.cancelPendingByCategoryExceptPrefix(
                event.userId(),
                SOURCE_MODULE,
                CATEGORY_EXAM_DEADLINE,
                INTENT_COUNTDOWN,
                retainPrefix,
                "CANDIDATE_GOAL_UPDATED",
                CorrelationIds.current()
            );
            if (event.targetDate() != null) {
                evaluateExamCountdownForUser(event.userId(), event.examCode(), event.targetDate(), event.occurredAt());
            }
        } catch (RuntimeException ignored) {
        }
    }

    public void scanAndEvaluateBatch() {
        Instant startedAt = clock.instant();
        int batchSize = lifecycleMessagingPort.scanBatchSize();
        try {
            LocalDate minTargetDate = startedAt.atZone(ZoneOffset.UTC).toLocalDate().minusDays(1);
            List<CandidateTargetRow> rows = jdbcClient.sql("""
                    SELECT cp.user_id, cp.active_exam_code, cp.target_date
                    FROM candidate_profiles cp
                    JOIN users u ON u.id = cp.user_id
                    WHERE u.status = 'ACTIVE'
                      AND cp.target_date >= :minTargetDate
                    ORDER BY cp.updated_at DESC, cp.user_id ASC
                    LIMIT :limit
                    """)
                .param("minTargetDate", Date.valueOf(minTargetDate))
                .param("limit", batchSize)
                .query((rs, rowNum) -> new CandidateTargetRow(
                    rs.getObject("user_id", UUID.class),
                    rs.getString("active_exam_code"),
                    rs.getDate("target_date").toLocalDate()
                ))
                .list();

            for (CandidateTargetRow row : rows) {
                try {
                    evaluateExamCountdownForUser(row.userId(), row.examCode(), row.targetDate(), startedAt);
                } catch (RuntimeException ignored) {
                }
            }
        } finally {
            lifecycleMessagingPort.recordScanDuration(SOURCE_MODULE, Duration.between(startedAt, clock.instant()));
        }
    }

    public static String countdownIdempotencyKey(
        UUID userId,
        String examCode,
        LocalDate targetDate,
        long milestoneDays
    ) {
        return "exam:countdown:" + userId + ":" + sanitizeToken(examCode) + ":" + targetDate + ":" + milestoneDays + "d";
    }

    private static String sanitizeToken(String raw) {
        return raw.replaceAll("[^A-Za-z0-9:._-]", "_");
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

    private record CandidateTargetRow(UUID userId, String examCode, LocalDate targetDate) {
    }
}
