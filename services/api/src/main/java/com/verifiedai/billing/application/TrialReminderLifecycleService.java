package com.verifiedai.billing.application;

import com.verifiedai.billing.domain.model.AppStoreSubscriptionStatus;
import com.verifiedai.notification.application.LifecycleCancellationResult;
import com.verifiedai.notification.application.LifecycleEvaluationResult;
import com.verifiedai.notification.application.LifecycleMessagingPort;
import com.verifiedai.notification.application.LifecycleSkipCommand;
import com.verifiedai.notification.application.LifecycleTriggerCommand;
import com.verifiedai.sharedkernel.observability.CorrelationIds;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Owning-module lifecycle orchestrator for one-time 48-hour free-trial ending reminders
 * ({@code Apple Guideline 3.1.2} / Doc 88 §6) and instant cancellation on renewal/refund/early-cancel.
 */
@Service
public class TrialReminderLifecycleService {

    private static final String SOURCE_MODULE = "billing";
    private static final String CATEGORY_BILLING_TRIAL = "BILLING_TRIAL_REMINDER";
    private static final String INTENT_TRIAL_48H = "TRIAL_ENDING_48H_REMINDER";
    private static final String TEMPLATE_TRIAL_48H = "notification.template.billing.trial_ending_48h";
    private static final String DEEP_LINK_SUBSCRIPTION_MANAGE = "verified-ai://subscription/manage";

    private final LifecycleMessagingPort lifecycleMessagingPort;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public TrialReminderLifecycleService(
        LifecycleMessagingPort lifecycleMessagingPort,
        JdbcTemplate jdbcTemplate,
        Clock clock
    ) {
        this.lifecycleMessagingPort = Objects.requireNonNull(lifecycleMessagingPort, "lifecycleMessagingPort");
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public LifecycleEvaluationResult onSubscriptionStateSynced(
        UUID userId,
        String originalTransactionId,
        String planCode,
        boolean freeTrial,
        AppStoreSubscriptionStatus status,
        Instant expiresAt,
        Boolean autoRenewStatus,
        Instant now
    ) {
        Objects.requireNonNull(userId, "userId must not be null");
        if (originalTransactionId == null || originalTransactionId.isBlank()) {
            throw new IllegalArgumentException("originalTransactionId must not be blank");
        }
        Instant evalTime = now != null ? now : clock.instant();
        String sanitizedTxId = originalTransactionId.trim().replaceAll("[^A-Za-z0-9:._-]", "_");
        String idempotencyKey = trialReminderIdempotencyKey(userId, sanitizedTxId);
        String correlationId = CorrelationIds.current();

        boolean eligibleActiveTrial = freeTrial
            && status == AppStoreSubscriptionStatus.ACTIVE
            && expiresAt != null
            && expiresAt.isAfter(evalTime)
            && !Boolean.FALSE.equals(autoRenewStatus);

        if (eligibleActiveTrial) {
            int leadHours = lifecycleMessagingPort.trialReminderLeadHours();
            Instant reminderAt = expiresAt.minus(Duration.ofHours(leadHours));
            Instant scheduledFor = reminderAt.isBefore(evalTime) ? evalTime : reminderAt;
            long hoursRemaining = Math.max(1L, Duration.between(scheduledFor, expiresAt).toHours());
            String sanitizedPlanCode = (planCode == null || planCode.isBlank()) ? "PRO" : planCode.trim();
            if (sanitizedPlanCode.length() > 64) {
                sanitizedPlanCode = sanitizedPlanCode.substring(0, 64);
            }
            String locale = lifecycleMessagingPort.resolvePreferredLocale(userId);
            return lifecycleMessagingPort.evaluateAndSchedule(new LifecycleTriggerCommand(
                userId,
                SOURCE_MODULE,
                CATEGORY_BILLING_TRIAL,
                INTENT_TRIAL_48H,
                idempotencyKey,
                locale,
                TEMPLATE_TRIAL_48H,
                Map.of(
                    "hoursRemaining", Long.toString(hoursRemaining),
                    "planCode", sanitizedPlanCode
                ),
                DEEP_LINK_SUBSCRIPTION_MANAGE,
                scheduledFor,
                expiresAt,
                correlationId
            ));
        }

        LifecycleCancellationResult cancellation = lifecycleMessagingPort.cancelPendingByIdempotencyKey(
            userId,
            SOURCE_MODULE,
            CATEGORY_BILLING_TRIAL,
            INTENT_TRIAL_48H,
            idempotencyKey,
            "TRIAL_STATE_CHANGED",
            correlationId
        );
        if (cancellation.cancelled()) {
            return new LifecycleEvaluationResult(
                null,
                userId,
                CATEGORY_BILLING_TRIAL,
                INTENT_TRIAL_48H,
                idempotencyKey,
                "CANCELLED_OBSOLETE",
                cancellation.reasonCode(),
                cancellation.deliveryId(),
                null,
                cancellation.processedAt(),
                false
            );
        }

        String skipReason = resolveIneligibleReason(freeTrial, status, expiresAt, autoRenewStatus, evalTime);
        return lifecycleMessagingPort.recordSkip(new LifecycleSkipCommand(
            userId,
            SOURCE_MODULE,
            CATEGORY_BILLING_TRIAL,
            INTENT_TRIAL_48H,
            idempotencyKey,
            TEMPLATE_TRIAL_48H,
            "SKIPPED_NOT_ELIGIBLE",
            skipReason,
            correlationId
        ));
    }

    public void scanUpcomingTrialsBatch() {
        Instant startedAt = clock.instant();
        int batchSize = lifecycleMessagingPort.scanBatchSize();
        try {
            List<TrialSubscriptionRow> staleCancelCandidates = jdbcTemplate.query(
                """
                select s.user_id, s.original_transaction_id, s.product_id, s.status, s.auto_renew_status, s.expires_at
                from app_store_subscriptions s
                join users u on u.id = s.user_id
                where u.status = 'ACTIVE'
                  and (s.status in ('EXPIRED', 'REVOKED') or s.auto_renew_status = false)
                  and exists (
                      select 1
                      from notification_deliveries d
                      where d.user_id = s.user_id
                        and d.idempotency_key = 'billing:trial48h:' || s.user_id::text || ':' || s.original_transaction_id
                        and d.status in ('SCHEDULED', 'DEFERRED_QUIET_HOURS', 'RETRY_SCHEDULED')
                  )
                order by s.updated_at desc
                limit ?
                """,
                (rs, rowNum) -> new TrialSubscriptionRow(
                    rs.getObject("user_id", UUID.class),
                    rs.getString("original_transaction_id"),
                    rs.getString("product_id"),
                    AppStoreSubscriptionStatus.valueOf(rs.getString("status")),
                    rs.getObject("auto_renew_status", Boolean.class),
                    rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toInstant()
                ),
                batchSize
            );
            for (TrialSubscriptionRow candidate : staleCancelCandidates) {
                try {
                    onSubscriptionStateSynced(
                        candidate.userId(),
                        candidate.originalTransactionId(),
                        candidate.productId(),
                        false,
                        candidate.status(),
                        candidate.expiresAt(),
                        candidate.autoRenewStatus(),
                        startedAt
                    );
                } catch (RuntimeException ignored) {
                }
            }

            List<TrialSubscriptionRow> upcomingTrials = jdbcTemplate.query(
                """
                select s.user_id, s.original_transaction_id, s.product_id, s.status, s.auto_renew_status, s.expires_at
                from app_store_subscriptions s
                join app_store_transactions t
                  on t.environment = s.environment
                 and t.transaction_id = s.current_transaction_id
                join users u on u.id = s.user_id
                where u.status = 'ACTIVE'
                  and s.status = 'ACTIVE'
                  and (s.auto_renew_status is null or s.auto_renew_status = true)
                  and t.transaction_reason = 'PURCHASE'
                  and t.revocation_date is null
                  and not t.access_revoked
                  and s.expires_at > ?
                  and t.purchase_date is not null
                  and (t.expires_date - t.purchase_date) <= interval '14 days'
                  and not exists (
                      select 1
                      from notification_deliveries d
                      where d.user_id = s.user_id
                        and d.idempotency_key = 'billing:trial48h:' || s.user_id::text || ':' || s.original_transaction_id
                  )
                order by s.expires_at asc
                limit ?
                """,
                (rs, rowNum) -> new TrialSubscriptionRow(
                    rs.getObject("user_id", UUID.class),
                    rs.getString("original_transaction_id"),
                    rs.getString("product_id"),
                    AppStoreSubscriptionStatus.valueOf(rs.getString("status")),
                    rs.getObject("auto_renew_status", Boolean.class),
                    rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toInstant()
                ),
                Timestamp.from(startedAt),
                batchSize
            );
            for (TrialSubscriptionRow trial : upcomingTrials) {
                try {
                    onSubscriptionStateSynced(
                        trial.userId(),
                        trial.originalTransactionId(),
                        trial.productId(),
                        true,
                        trial.status(),
                        trial.expiresAt(),
                        trial.autoRenewStatus(),
                        startedAt
                    );
                } catch (RuntimeException ignored) {
                }
            }
        } finally {
            lifecycleMessagingPort.recordScanDuration(SOURCE_MODULE, Duration.between(startedAt, clock.instant()));
        }
    }

    public static String trialReminderIdempotencyKey(UUID userId, String originalTransactionId) {
        String sanitizedTx = originalTransactionId.trim().replaceAll("[^A-Za-z0-9:._-]", "_");
        return "billing:trial48h:" + userId + ":" + sanitizedTx;
    }

    private static String resolveIneligibleReason(
        boolean freeTrial,
        AppStoreSubscriptionStatus status,
        Instant expiresAt,
        Boolean autoRenewStatus,
        Instant now
    ) {
        if (Boolean.FALSE.equals(autoRenewStatus)) {
            return "AUTO_RENEW_DISABLED";
        }
        if (status != AppStoreSubscriptionStatus.ACTIVE) {
            return "SUBSCRIPTION_NOT_ACTIVE";
        }
        if (expiresAt == null || !expiresAt.isAfter(now)) {
            return "TRIAL_EXPIRED";
        }
        if (!freeTrial) {
            return "NOT_FREE_TRIAL";
        }
        return "NOT_ELIGIBLE";
    }

    private record TrialSubscriptionRow(
        UUID userId,
        String originalTransactionId,
        String productId,
        AppStoreSubscriptionStatus status,
        Boolean autoRenewStatus,
        Instant expiresAt
    ) {
    }
}
