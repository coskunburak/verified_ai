package com.verifiedai.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.verifiedai.billing.application.TrialReminderLifecycleService;
import com.verifiedai.billing.domain.model.AppStoreSubscriptionStatus;
import com.verifiedai.exam.events.CandidateGoalUpdatedEvent;
import com.verifiedai.exam.lifecycle.ExamDeadlineLifecycleMessagingService;
import com.verifiedai.identity.application.IdentitySecurityNotificationService;
import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.notification.domain.LifecycleTemplateRegistry;
import com.verifiedai.notification.infrastructure.LifecycleMessagingJdbcRepository;
import com.verifiedai.notification.infrastructure.NotificationDeliveryJdbcRepository;
import com.verifiedai.notification.infrastructure.NotificationDeviceJdbcRepository;
import com.verifiedai.notification.infrastructure.NotificationPreferenceJdbcRepository;
import com.verifiedai.notification.infrastructure.NotificationProperties;
import com.verifiedai.sharedkernel.privacy.AccountDataLifecycleContributor;
import com.verifiedai.studyplan.coach.DailyCoachSessionService;
import com.verifiedai.studyplan.coach.dto.DailyCoachSummaryResponse;
import com.verifiedai.studyplan.coach.dto.DailyCoachTaskCompleteRequest;
import com.verifiedai.studyplan.coach.dto.DailyCoachTaskDto;
import com.verifiedai.studyplan.lifecycle.StudyPlanLifecycleMessagingService;
import com.verifiedai.studyplan.spaced.SpacedQueueStatus;
import com.verifiedai.studyplan.spaced.SpacedReviewQueueEntity;
import com.verifiedai.studyplan.spaced.SpacedReviewQueueRepository;
import com.verifiedai.studyplan.spaced.SpacedReviewService;
import com.verifiedai.studyplan.spaced.SpacedTriggerType;
import com.verifiedai.studyplan.spaced.dto.SpacedItemCompleteRequest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

@TestPropertySource(properties = {
    "verifiedai.notification.enabled=true",
    "verifiedai.notification.lifecycle-campaigns-enabled=true",
    "verifiedai.notification.lifecycle-scheduler-enabled=false",
    "verifiedai.notification.dispatch-worker-enabled=false",
    "verifiedai.notification.apns-live-delivery-enabled=false"
})
final class LifecycleMessagingOrchestrationIT extends PostgresIntegrationTestSupport {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().findAndAddModules().build();

    @Autowired
    LifecycleMessagingPort lifecycleMessagingPort;

    @Autowired
    NotificationSchedulingPort schedulingPort;

    @Autowired
    NotificationPreferenceService preferenceService;

    @Autowired
    NotificationDeviceService deviceService;

    @Autowired
    NotificationDeliveryJdbcRepository deliveryRepository;

    @Autowired
    NotificationPreferenceJdbcRepository preferenceRepository;

    @Autowired
    NotificationDeviceJdbcRepository deviceRepository;

    @Autowired
    LifecycleMessagingJdbcRepository lifecycleRepository;

    @Autowired
    StudyPlanLifecycleMessagingService studyPlanLifecycleService;

    @Autowired
    DailyCoachSessionService dailyCoachSessionService;

    @Autowired
    SpacedReviewService spacedReviewService;

    @Autowired
    SpacedReviewQueueRepository spacedReviewQueueRepository;

    @Autowired
    ExamDeadlineLifecycleMessagingService examDeadlineLifecycleService;

    @Autowired
    ApplicationEventPublisher eventPublisher;

    @Autowired
    TrialReminderLifecycleService trialReminderLifecycleService;

    @Autowired
    IdentitySecurityNotificationService identitySecurityNotificationService;

    @Autowired
    List<AccountDataLifecycleContributor> lifecycleContributors;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    Clock clock;

    @Autowired
    PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.execute("""
            truncate table
                lifecycle_messaging_evaluations,
                lifecycle_messaging_cooldowns,
                notification_delivery_events,
                notification_deliveries,
                notification_devices,
                notification_preferences,
                daily_coach_tasks,
                daily_coach_sessions,
                spaced_review_queue,
                auth_security_events,
                refresh_tokens,
                sessions,
                user_identities,
                users
            cascade
            """);
    }

    @Test
    void disabledFeatureFlagFailsClosedWithZeroDeliveryWrites() {
        UUID userId = insertActiveUser();
        Instant now = clock.instant();
        NotificationProperties disabledProps = NotificationProperties.enabledDefaults();
        LifecycleMessagingService disabledService = new LifecycleMessagingService(
            schedulingPort,
            deliveryRepository,
            preferenceRepository,
            deviceRepository,
            lifecycleRepository,
            disabledProps,
            new NotificationMetrics(new SimpleMeterRegistry()),
            clock,
            transactionManager
        );

        LifecycleEvaluationResult result = disabledService.evaluateAndSchedule(new LifecycleTriggerCommand(
            userId,
            "studyplan",
            "STUDY_PLAN",
            "STUDY_PLAN_DAILY_MISSION",
            "studyplan:daily:" + userId + ":2026-11-22",
            "en",
            LifecycleTemplateRegistry.TEMPLATE_STUDY_PLAN_DAILY_MISSION,
            Map.of("budgetMinutes", "15", "remainingTasks", "3"),
            "verified-ai://study-plan/today",
            now,
            now.plusSeconds(3_600),
            "corr-disabled"
        ));

        assertThat(result.decision()).isEqualTo("SKIPPED_FEATURE_DISABLED");
        assertThat(result.deliveryId()).isNull();
        assertThat(jdbcTemplate.queryForObject("select count(*) from notification_deliveries", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from lifecycle_messaging_evaluations", Integer.class))
            .isZero();
    }

    @Test
    void studyPlanSchedulesReplaysRespectsCooldownAndCancelsWhenMissionCompletes() {
        UUID userId = insertActiveUser();
        enableAllCategoriesAndDevice(userId, "1111111111111111111111111111111111111111111111111111111111110001");

        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock);
        Instant midday = today.atTime(12, 0).toInstant(ZoneOffset.UTC);
        DailyCoachSummaryResponse summary = dailyCoachSessionService.getOrCreateTodaySummary(userId, today);

        LifecycleEvaluationResult scheduled = studyPlanLifecycleService.evaluateDailyMissionReminder(userId, midday);
        assertThat(scheduled.decision()).isEqualTo("SCHEDULED");
        assertThat(scheduled.deliveryId()).isNotNull();

        LifecycleEvaluationResult replayed = studyPlanLifecycleService.evaluateDailyMissionReminder(
            userId,
            midday.plusSeconds(30)
        );
        assertThat(replayed.decision()).isEqualTo("REPLAYED");
        assertThat(replayed.deliveryId()).isEqualTo(scheduled.deliveryId());

        // Different idempotencyKey within 20h cooldown is skipped deterministically
        LifecycleEvaluationResult cooldownBlocked = lifecycleMessagingPort.evaluateAndSchedule(
            new LifecycleTriggerCommand(
                userId,
                "studyplan",
                "STUDY_PLAN",
                "STUDY_PLAN_DAILY_MISSION",
                "studyplan:daily:" + userId + ":" + today.plusDays(1),
                "en",
                LifecycleTemplateRegistry.TEMPLATE_STUDY_PLAN_DAILY_MISSION,
                Map.of("budgetMinutes", "15", "remainingTasks", "3"),
                "verified-ai://study-plan/today",
                now.plusSeconds(60),
                now.plusSeconds(7_200),
                "corr-cooldown"
            )
        );
        assertThat(cooldownBlocked.decision()).isEqualTo("SKIPPED_COOLDOWN_ACTIVE");

        // Complete all tasks -> pending delivery cancelled atomically and marked CANCELLED_OBSOLETE
        for (DailyCoachTaskDto task : summary.tasks()) {
            dailyCoachSessionService.completeTask(
                userId,
                summary.sessionId(),
                new DailyCoachTaskCompleteRequest(task.taskId(), true, 120)
            );
        }

        assertThat(deliveryStatus(scheduled.deliveryId())).isEqualTo("CANCELLED");
        Integer cancelAuditCount = jdbcTemplate.queryForObject(
            "select count(*) from lifecycle_messaging_evaluations where user_id = ? and decision = 'CANCELLED_OBSOLETE'",
            Integer.class,
            userId
        );
        assertThat(cancelAuditCount).isEqualTo(1);

        // Re-evaluating after completion skips with SKIPPED_ALREADY_COMPLETED
        LifecycleEvaluationResult afterComplete = studyPlanLifecycleService.evaluateDailyMissionReminder(
            userId,
            midday.plusSeconds(120)
        );
        assertThat(afterComplete.decision()).isEqualTo("SKIPPED_ALREADY_COMPLETED");
    }

    @Test
    void spacedReviewSchedulesAndCancelsWhenQueueIsCleared() {
        UUID userId = insertActiveUser();
        enableAllCategoriesAndDevice(userId, "2222222222222222222222222222222222222222222222222222222222220002");

        Instant midday = LocalDate.now(clock).atTime(12, 0).toInstant(ZoneOffset.UTC);
        UUID queueItemId = UUID.randomUUID();
        spacedReviewQueueRepository.save(new SpacedReviewQueueEntity(
            queueItemId,
            userId,
            "MATH.ALG.RATIONAL_EQ",
            "ALG_DOMAIN_EXCLUSION_MISSED",
            SpacedTriggerType.TRAP_IMMUNITY_7D,
            new BigDecimal("0.7100"),
            new BigDecimal("7.00"),
            midday.minusSeconds(300),
            UUID.randomUUID(),
            "\\frac{x^2-4}{x-2}",
            SpacedQueueStatus.PENDING
        ));

        LifecycleEvaluationResult scheduled = studyPlanLifecycleService.evaluateSpacedReviewReminder(userId, midday);
        assertThat(scheduled.decision()).isEqualTo("SCHEDULED");
        assertThat(scheduled.deliveryId()).isNotNull();

        spacedReviewService.completeQueueItem(
            userId,
            queueItemId,
            new SpacedItemCompleteRequest(true, true, 12_000L, null),
            midday.plusSeconds(60)
        );

        assertThat(deliveryStatus(scheduled.deliveryId())).isEqualTo("CANCELLED");
        LifecycleEvaluationResult afterClear = studyPlanLifecycleService.evaluateSpacedReviewReminder(
            userId,
            midday.plusSeconds(90)
        );
        assertThat(afterClear.decision()).isEqualTo("SKIPPED_ALREADY_COMPLETED");
    }

    @Test
    void examDeadlineCancelsPreviousMilestoneWhenCandidateGoalUpdated() {
        UUID userId = insertActiveUser();
        enableAllCategoriesAndDevice(userId, "3333333333333333333333333333333333333333333333333333333333330003");

        LocalDate today = LocalDate.now(clock);
        Instant midday = today.atTime(12, 0).toInstant(ZoneOffset.UTC);
        LocalDate sevenDaysOut = today.plusDays(7);

        LifecycleEvaluationResult sevenDayScheduled = examDeadlineLifecycleService.evaluateExamCountdownForUser(
            userId,
            "TR_YKS_TYT_MATH",
            sevenDaysOut,
            midday
        );
        assertThat(sevenDayScheduled.decision()).isEqualTo("SCHEDULED");
        assertThat(deliveryStatus(sevenDayScheduled.deliveryId())).isEqualTo("SCHEDULED");

        // Candidate shifts goal date to 45 days away -> old 7d milestone cancelled, 45d skipped
        eventPublisher.publishEvent(new CandidateGoalUpdatedEvent(
            userId,
            UUID.randomUUID(),
            "TR_YKS_TYT_MATH",
            "2026-v1",
            today.plusDays(45),
            20,
            140,
            new BigDecimal("35.0"),
            new BigDecimal("1.00"),
            20,
            "ON_TRACK",
            "EXAM_DATE_SHIFT",
            midday.plusSeconds(60)
        ));

        assertThat(deliveryStatus(sevenDayScheduled.deliveryId())).isEqualTo("CANCELLED");
        Integer cancelledCount = jdbcTemplate.queryForObject(
            "select count(*) from lifecycle_messaging_evaluations where user_id = ? and category = 'EXAM_DEADLINE' and decision = 'CANCELLED_OBSOLETE'",
            Integer.class,
            userId
        );
        assertThat(cancelledCount).isEqualTo(1);
    }

    @Test
    void billingTrialReminderSchedulesOnceReplaysAndCancelsOnAutoRenewOffRefundOrRenewal() {
        UUID userId = insertActiveUser();
        enableAllCategoriesAndDevice(userId, "4444444444444444444444444444444444444444444444444444444444440004");

        Instant now = clock.instant();
        Instant trialExpiry = now.plus(Duration.ofDays(7));

        LifecycleEvaluationResult first = trialReminderLifecycleService.onSubscriptionStateSynced(
            userId, "tx-trial-1", "coach_pro_annual", true, AppStoreSubscriptionStatus.ACTIVE, trialExpiry, true, now
        );
        assertThat(first.decision()).isEqualTo("SCHEDULED");
        assertThat(first.deliveryId()).isNotNull();

        LifecycleEvaluationResult replay = trialReminderLifecycleService.onSubscriptionStateSynced(
            userId, "tx-trial-1", "coach_pro_annual", true, AppStoreSubscriptionStatus.ACTIVE, trialExpiry, true, now
        );
        assertThat(replay.decision()).isEqualTo("REPLAYED");
        assertThat(replay.deliveryId()).isEqualTo(first.deliveryId());

        // User turns off auto-renew -> pending 48h reminder cancelled
        LifecycleEvaluationResult cancelledEarly = trialReminderLifecycleService.onSubscriptionStateSynced(
            userId, "tx-trial-1", "coach_pro_annual", true, AppStoreSubscriptionStatus.ACTIVE, trialExpiry, false,
            now.plusSeconds(60)
        );
        assertThat(cancelledEarly.decision()).isEqualTo("CANCELLED_OBSOLETE");
        assertThat(deliveryStatus(first.deliveryId())).isEqualTo("CANCELLED");

        // Second trial -> cancelled by DID_RENEW (freeTrial=false)
        LifecycleEvaluationResult second = trialReminderLifecycleService.onSubscriptionStateSynced(
            userId, "tx-trial-2", "pro_annual", true, AppStoreSubscriptionStatus.ACTIVE, trialExpiry, true, now
        );
        assertThat(second.decision()).isEqualTo("SCHEDULED");
        trialReminderLifecycleService.onSubscriptionStateSynced(
            userId, "tx-trial-2", "pro_annual", false, AppStoreSubscriptionStatus.ACTIVE, trialExpiry.plus(Duration.ofDays(30)),
            true, now.plusSeconds(120)
        );
        assertThat(deliveryStatus(second.deliveryId())).isEqualTo("CANCELLED");

        // Third trial -> cancelled by REFUND/REVOKED
        LifecycleEvaluationResult third = trialReminderLifecycleService.onSubscriptionStateSynced(
            userId, "tx-trial-3", "pro_annual", true, AppStoreSubscriptionStatus.ACTIVE, trialExpiry, true, now
        );
        assertThat(third.decision()).isEqualTo("SCHEDULED");
        trialReminderLifecycleService.onSubscriptionStateSynced(
            userId, "tx-trial-3", "pro_annual", true, AppStoreSubscriptionStatus.REVOKED, trialExpiry, false,
            now.plusSeconds(180)
        );
        assertThat(deliveryStatus(third.deliveryId())).isEqualTo("CANCELLED");
    }

    @Test
    void accountSecurityBypassesOptOutQuietHoursAndDailyCapAndGdprCoversV069Tables() throws Exception {
        UUID userA = insertActiveUser();
        UUID userB = insertActiveUser();
        String rawTokenA = "5555555555555555555555555555555555555555555555555555555555554321";
        String rawTokenB = "6666666666666666666666666666666666666666666666666666666666669876";

        deviceService.registerDevice(userA, new RegisterDeviceCommand(
            "IOS_APNS", "SANDBOX", rawTokenA, "AUTHORIZED", "1.0.0", "tr", false
        ));
        deviceService.registerDevice(userB, new RegisterDeviceCommand(
            "IOS_APNS", "SANDBOX", rawTokenB, "AUTHORIZED", "1.0.0", "en", false
        ));

        Instant now = clock.instant();
        LocalTime nowUtc = now.atZone(ZoneOffset.UTC).toLocalTime();
        String quietStart = nowUtc.minusHours(1).format(DateTimeFormatter.ofPattern("HH:mm"));
        String quietEnd = nowUtc.plusHours(2).format(DateTimeFormatter.ofPattern("HH:mm"));

        // Push disabled, quiet hours covering current instant, dailyCap=0
        preferenceService.updatePreferences(userA, new UpdateNotificationPreferencesCommand(
            false, false, false, false, false, true,
            true, quietStart, quietEnd, "UTC", 0, 0L, false
        ));

        LifecycleEvaluationResult securityRes = identitySecurityNotificationService.notifySecurityEvent(
            userA, "REFRESH_TOKEN_REUSE", "sec-evt-1", now
        );
        assertThat(securityRes.decision()).isEqualTo("SCHEDULED");
        assertThat(deliveryStatus(securityRes.deliveryId())).isEqualTo("SCHEDULED");

        // User B gets a lifecycle cooldown & evaluation to confirm isolation on GDPR delete
        enableAllCategoriesAndDevice(userB, rawTokenB);
        trialReminderLifecycleService.onSubscriptionStateSynced(
            userB, "tx-user-b", "pro_annual", true, AppStoreSubscriptionStatus.ACTIVE,
            now.plus(Duration.ofDays(7)), true, now
        );

        AccountDataLifecycleContributor contributor = lifecycleContributors.stream()
            .filter(c -> "notifications".equals(c.category()))
            .findFirst()
            .orElseThrow();

        Map<String, Object> exportA = contributor.exportUserData(userA);
        String exportJson = OBJECT_MAPPER.writeValueAsString(exportA);
        assertThat(exportJson)
            .contains("\"lifecycleCooldowns\"")
            .contains("\"lifecycleEvaluations\"")
            .contains("REFRESH_TOKEN_REUSE")
            .doesNotContain(rawTokenA);

        contributor.deleteUserData(userA, Instant.now());

        for (String table : new String[]{
            "lifecycle_messaging_evaluations",
            "lifecycle_messaging_cooldowns",
            "notification_delivery_events",
            "notification_deliveries",
            "notification_devices",
            "notification_preferences"
        }) {
            Integer remainingA = jdbcTemplate.queryForObject(
                "select count(*) from " + table + " where user_id = ?",
                Integer.class,
                userA
            );
            assertThat(remainingA).as("remaining rows for userA in " + table).isZero();
        }
        Integer remainingEvalB = jdbcTemplate.queryForObject(
            "select count(*) from lifecycle_messaging_evaluations where user_id = ?",
            Integer.class,
            userB
        );
        assertThat(remainingEvalB).isGreaterThan(0);
    }

    private UUID insertActiveUser() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into users (id, status, created_at, updated_at) values (?, 'ACTIVE', now(), now())",
            userId
        );
        return userId;
    }

    private void enableAllCategoriesAndDevice(UUID userId, String token) {
        preferenceService.updatePreferences(userId, new UpdateNotificationPreferencesCommand(
            true, true, true, true, true, true,
            false, "22:00", "08:00", "UTC", 5, 0L, false
        ));
        deviceService.registerDevice(userId, new RegisterDeviceCommand(
            "IOS_APNS", "SANDBOX", token, "AUTHORIZED", "1.0.0", "en", false
        ));
    }

    private String deliveryStatus(UUID deliveryId) {
        return jdbcTemplate.queryForObject(
            "select status from notification_deliveries where id = ?",
            String.class,
            deliveryId
        );
    }
}
