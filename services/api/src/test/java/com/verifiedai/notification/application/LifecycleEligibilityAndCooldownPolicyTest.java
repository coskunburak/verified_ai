package com.verifiedai.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.verifiedai.billing.application.TrialReminderLifecycleService;
import com.verifiedai.billing.domain.model.AppStoreSubscriptionStatus;
import com.verifiedai.exam.lifecycle.ExamDeadlineLifecycleMessagingService;
import com.verifiedai.identity.application.IdentitySecurityNotificationService;
import com.verifiedai.notification.domain.NotificationCategory;
import com.verifiedai.notification.infrastructure.NotificationProperties;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

final class LifecycleEligibilityAndCooldownPolicyTest {

    private static final Instant NOW = Instant.parse("2026-11-22T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void notificationPropertiesValidateSprint105BoundsAndFailClosedDefaults() {
        NotificationProperties defaults = NotificationProperties.defaults();
        assertThat(defaults.enabled()).isFalse();
        assertThat(defaults.lifecycleCampaignsEnabled()).isFalse();
        assertThat(defaults.lifecycleSchedulerEnabled()).isFalse();
        assertThat(defaults.apnsLiveDeliveryEnabled()).isFalse();
        assertThat(defaults.studyPlanCooldownHours()).isEqualTo(20);
        assertThat(defaults.spacedReviewCooldownHours()).isEqualTo(24);
        assertThat(defaults.examDeadlineCooldownHours()).isEqualTo(24);
        assertThat(defaults.trialReminderLeadHours()).isEqualTo(48);

        assertThatThrownBy(() -> new NotificationProperties(
            true, false, false, 50, 15_000L, 60, 2, 5, 3, 60, 10,
            true, false, 501, 300_000L, 20, 24, 24, 48
        )).isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> new NotificationProperties(
            true, false, false, 50, 15_000L, 60, 2, 5, 3, 60, 10,
            true, false, 100, 9_999L, 20, 24, 24, 48
        )).isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> new NotificationProperties(
            true, false, false, 50, 15_000L, 60, 2, 5, 3, 60, 10,
            true, false, 100, 300_000L, 0, 24, 24, 48
        )).isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> new NotificationProperties(
            true, false, false, 50, 15_000L, 60, 2, 5, 3, 60, 10,
            true, false, 100, 300_000L, 20, 24, 24, 6
        )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void examDeadlineTriggersOnlyOnCalmMilestones30_14_7_2Days() {
        RecordingLifecyclePort port = new RecordingLifecyclePort();
        StubNotificationSchedulingPort schedulingPort = new StubNotificationSchedulingPort("UTC");
        ExamDeadlineLifecycleMessagingService service = new ExamDeadlineLifecycleMessagingService(
            port,
            schedulingPort,
            mock(JdbcClient.class),
            FIXED_CLOCK
        );
        UUID userId = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 11, 22);

        for (int milestone : List.of(30, 14, 7, 2)) {
            service.evaluateExamCountdownForUser(userId, "TR_YKS_TYT_MATH", today.plusDays(milestone), NOW);
        }
        assertThat(port.triggers).hasSize(4);
        assertThat(port.triggers.get(2).templateParams())
            .containsEntry("examCode", "TR_YKS_TYT_MATH")
            .containsEntry("daysRemaining", "7");

        for (int nonMilestone : List.of(-1, 0, 1, 3, 15, 31, 60)) {
            service.evaluateExamCountdownForUser(userId, "TR_YKS_TYT_MATH", today.plusDays(nonMilestone), NOW);
        }
        assertThat(port.triggers).hasSize(4);
        assertThat(port.skips).hasSize(7);
        assertThat(port.skips).allSatisfy(skip -> assertThat(skip.decision()).isEqualTo("SKIPPED_NOT_ELIGIBLE"));
    }

    @Test
    void trialReminderSchedulesAt48hBeforeExpiryClampsShortTrialsAndCancelsOnNonActiveOrOptOutRenew() {
        RecordingLifecyclePort port = new RecordingLifecyclePort();
        TrialReminderLifecycleService service = new TrialReminderLifecycleService(
            port,
            mock(JdbcTemplate.class),
            FIXED_CLOCK
        );
        UUID userId = UUID.randomUUID();

        // 7-day trial -> scheduledFor = expiresAt - 48h, hoursRemaining = 48
        Instant sevenDayExpiry = NOW.plus(Duration.ofDays(7));
        service.onSubscriptionStateSynced(
            userId,
            "orig-tx-1",
            "coach_pro_annual",
            true,
            AppStoreSubscriptionStatus.ACTIVE,
            sevenDayExpiry,
            true,
            NOW
        );
        assertThat(port.triggers).hasSize(1);
        assertThat(port.triggers.get(0).scheduledFor()).isEqualTo(sevenDayExpiry.minus(Duration.ofHours(48)));
        assertThat(port.triggers.get(0).expiresAt()).isEqualTo(sevenDayExpiry);
        assertThat(port.triggers.get(0).templateParams()).containsEntry("hoursRemaining", "48");

        // <48h trial -> clamped to now, hoursRemaining = 24
        Instant shortExpiry = NOW.plus(Duration.ofHours(24));
        service.onSubscriptionStateSynced(
            userId,
            "orig-tx-2",
            "pro_annual",
            true,
            AppStoreSubscriptionStatus.ACTIVE,
            shortExpiry,
            null,
            NOW
        );
        assertThat(port.triggers).hasSize(2);
        assertThat(port.triggers.get(1).scheduledFor()).isEqualTo(NOW);
        assertThat(port.triggers.get(1).templateParams()).containsEntry("hoursRemaining", "24");

        // Early cancel (autoRenewStatus=false), paid renewal (freeTrial=false), revoked/expired -> cancel obsolete
        service.onSubscriptionStateSynced(
            userId, "orig-tx-1", "coach_pro_annual", true, AppStoreSubscriptionStatus.ACTIVE, sevenDayExpiry, false, NOW
        );
        service.onSubscriptionStateSynced(
            userId, "orig-tx-1", "coach_pro_annual", false, AppStoreSubscriptionStatus.ACTIVE, sevenDayExpiry, true, NOW
        );
        service.onSubscriptionStateSynced(
            userId, "orig-tx-2", "pro_annual", true, AppStoreSubscriptionStatus.REVOKED, shortExpiry, false, NOW
        );
        assertThat(port.cancellations).hasSize(3);
    }

    @Test
    void identitySecurityNotificationEnforcesStrictAllowlistAndExemptsCooldowns() {
        assertThat(NotificationCategory.ACCOUNT_SECURITY.isSecurityMandatory()).isTrue();
        assertThat(NotificationCategory.ACCOUNT_SECURITY.isSubjectToQuietHours()).isFalse();
        assertThat(NotificationCategory.ACCOUNT_SECURITY.isSubjectToDailyCap()).isFalse();
        assertThat(NotificationCategory.STUDY_PLAN.isSecurityMandatory()).isFalse();

        RecordingLifecyclePort port = new RecordingLifecyclePort();
        IdentitySecurityNotificationService service = new IdentitySecurityNotificationService(port, FIXED_CLOCK);
        UUID userId = UUID.randomUUID();

        service.notifySecurityEvent(userId, "REFRESH_TOKEN_REUSE", "evt-1", NOW);
        assertThat(port.triggers).hasSize(1);
        assertThat(port.triggers.get(0).category()).isEqualTo("ACCOUNT_SECURITY");
        assertThat(port.triggers.get(0).scheduledFor()).isEqualTo(NOW);
        assertThat(port.triggers.get(0).expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));

        assertThatThrownBy(() -> service.notifySecurityEvent(userId, "MARKETING_BLAST", "evt-2", NOW))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> assertThat(((ApiProblemException) ex).code())
                .isEqualTo(ApiErrorCode.NOTIFICATION_VALIDATION_FAILED));
    }

    private static final class RecordingLifecyclePort implements LifecycleMessagingPort {
        final List<LifecycleTriggerCommand> triggers = new ArrayList<>();
        final List<LifecycleSkipCommand> skips = new ArrayList<>();
        final List<String> cancellations = new ArrayList<>();

        @Override
        public LifecycleEvaluationResult evaluateAndSchedule(LifecycleTriggerCommand command) {
            triggers.add(command);
            return new LifecycleEvaluationResult(
                UUID.randomUUID(),
                command.userId(),
                command.category(),
                command.intentType(),
                command.idempotencyKey(),
                "SCHEDULED",
                "SCHEDULED",
                UUID.randomUUID(),
                NOW,
                NOW,
                false
            );
        }

        @Override
        public LifecycleEvaluationResult recordSkip(LifecycleSkipCommand command) {
            skips.add(command);
            return new LifecycleEvaluationResult(
                UUID.randomUUID(),
                command.userId(),
                command.category(),
                command.intentType(),
                command.idempotencyKey(),
                command.decision(),
                command.reasonCode(),
                null,
                null,
                NOW,
                false
            );
        }

        @Override
        public LifecycleCancellationResult cancelPendingByIdempotencyKey(
            UUID userId,
            String sourceModule,
            String category,
            String intentType,
            String idempotencyKey,
            String reasonCode,
            String correlationId
        ) {
            cancellations.add(idempotencyKey + ":" + reasonCode);
            return new LifecycleCancellationResult(
                userId,
                category,
                intentType,
                idempotencyKey,
                UUID.randomUUID(),
                true,
                reasonCode,
                NOW,
                false
            );
        }

        @Override
        public LifecycleSummaryView getLifecycleSummary(UUID userId, int limit) {
            return new LifecycleSummaryView("lifecycle-summary-v1", userId, true, List.of(), List.of(), 0, false, NOW);
        }
    }

    private record StubNotificationSchedulingPort(String timezone) implements NotificationSchedulingPort {
        @Override
        public ScheduledNotificationResult schedule(ScheduleNotificationCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CancelNotificationResult cancel(UUID userId, UUID deliveryId, String reasonCode, String correlationId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public NotificationPreferenceView getPreferences(UUID userId) {
            return new NotificationPreferenceView(
                "notification-preferences-v1", userId, true, true, true, true, true, true,
                true, "22:00", "08:00", timezone, 2, 0L, false, NOW
            );
        }
    }
}
