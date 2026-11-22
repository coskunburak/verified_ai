package com.verifiedai.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.notification.domain.NotificationCategory;
import com.verifiedai.notification.domain.NotificationChannel;
import com.verifiedai.notification.domain.NotificationDeliveryStatus;
import com.verifiedai.notification.domain.NotificationIntentType;
import com.verifiedai.notification.domain.NotificationPolicyEvaluator;
import com.verifiedai.notification.infrastructure.NotificationDeliveryJdbcRepository;
import com.verifiedai.notification.infrastructure.NotificationDeviceJdbcRepository;
import com.verifiedai.notification.infrastructure.NotificationPreferenceJdbcRepository;
import com.verifiedai.notification.infrastructure.NotificationProperties;
import com.verifiedai.notification.infrastructure.PushDeliveryGateway;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

@TestPropertySource(properties = {
    "verifiedai.notification.enabled=true",
    "verifiedai.notification.dispatch-worker-enabled=false",
    "verifiedai.notification.apns-live-delivery-enabled=false",
    "spring.datasource.hikari.maximum-pool-size=25",
    "spring.datasource.hikari.connection-timeout=10000"
})
final class NotificationSchedulingConcurrencyIT extends PostgresIntegrationTestSupport {

    @Autowired
    NotificationSchedulingPort schedulingPort;

    @Autowired
    NotificationDeviceService deviceService;

    @Autowired
    NotificationDispatchService dispatchService;

    @Autowired
    NotificationDeliveryJdbcRepository deliveryRepository;

    @Autowired
    NotificationPreferenceJdbcRepository preferenceRepository;

    @Autowired
    NotificationDeviceJdbcRepository deviceRepository;

    @Autowired
    NotificationPolicyEvaluator policyEvaluator;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.execute("""
            truncate table
                notification_delivery_events,
                notification_deliveries,
                notification_devices,
                notification_preferences,
                users
            cascade
            """);
    }

    @Test
    void twentyParallelThreadsScheduleIdempotentlyAndConflictOnPayloadMismatch() throws Exception {
        UUID userId = insertActiveUser();
        registerAuthorizedDevice(userId, "1111111111111111111111111111111111111111111111111111111111111111");
        Instant scheduledFor = Instant.now().minusSeconds(5);
        Instant expiresAt = Instant.now().plusSeconds(3_600);
        String idempotencyKey = "parallel-idem-key-1";

        ScheduleNotificationCommand command = new ScheduleNotificationCommand(
            userId,
            idempotencyKey,
            NotificationCategory.ACCOUNT_SECURITY,
            NotificationIntentType.ACCOUNT_SECURITY_ALERT,
            NotificationChannel.PUSH,
            "tr",
            "account.security.login",
            Map.of("deviceCity", "Istanbul"),
            "verified-ai://account/security",
            scheduledFor,
            expiresAt,
            "identity",
            "corr-parallel-1"
        );

        int threads = 20;
        var ready = new CountDownLatch(threads);
        var go = new CountDownLatch(1);
        List<Future<ScheduledNotificationResult>> futures = new ArrayList<>();
        try (var pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await(5, TimeUnit.SECONDS);
                    return schedulingPort.schedule(command);
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            List<ScheduledNotificationResult> results = new ArrayList<>();
            for (Future<ScheduledNotificationResult> future : futures) {
                results.add(future.get(15, TimeUnit.SECONDS));
            }
            Set<UUID> deliveryIds = results.stream()
                .map(ScheduledNotificationResult::deliveryId)
                .collect(Collectors.toSet());
            assertThat(deliveryIds).hasSize(1);
            assertThat(results.stream().filter(r -> !r.replayed()).count()).isEqualTo(1);
            assertThat(results.stream().filter(ScheduledNotificationResult::replayed).count()).isEqualTo(19);
        }

        Integer rowCount = jdbcTemplate.queryForObject(
            "select count(*) from notification_deliveries where user_id = ?",
            Integer.class,
            userId
        );
        assertThat(rowCount).isEqualTo(1);

        ScheduleNotificationCommand mutated = new ScheduleNotificationCommand(
            userId,
            idempotencyKey,
            NotificationCategory.ACCOUNT_SECURITY,
            NotificationIntentType.ACCOUNT_SECURITY_ALERT,
            NotificationChannel.PUSH,
            "en",
            "account.security.login",
            Map.of("deviceCity", "Ankara"),
            "verified-ai://account/security",
            scheduledFor,
            expiresAt,
            "identity",
            "corr-parallel-2"
        );
        assertThatThrownBy(() -> schedulingPort.schedule(mutated))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> assertThat(((ApiProblemException) ex).code())
                .isEqualTo(ApiErrorCode.NOTIFICATION_IDEMPOTENCY_CONFLICT));
    }

    @Test
    void fourParallelWorkersDispatchTenDueDeliveriesExactlyOnceViaSkipLocked() throws Exception {
        Instant now = Instant.now();
        List<UUID> deliveryIds = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            UUID userId = insertActiveUser();
            registerAuthorizedDevice(
                userId,
                String.format("%064x", i + 100)
            );
            ScheduledNotificationResult scheduled = schedulingPort.schedule(new ScheduleNotificationCommand(
                userId,
                "due-dispatch-key-" + i,
                NotificationCategory.ACCOUNT_SECURITY,
                NotificationIntentType.ACCOUNT_SECURITY_ALERT,
                NotificationChannel.PUSH,
                "en",
                "account.security.alert",
                Map.of("seq", Integer.toString(i)),
                "verified-ai://account/security",
                now.minusSeconds(60),
                now.plusSeconds(3_600),
                "identity",
                "corr-worker-" + i
            ));
            assertThat(scheduled.status()).isEqualTo(NotificationDeliveryStatus.SCHEDULED);
            deliveryIds.add(scheduled.deliveryId());
        }

        int workers = 4;
        var ready = new CountDownLatch(workers);
        var go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        try (var pool = Executors.newFixedThreadPool(workers)) {
            for (int w = 0; w < workers; w++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await(5, TimeUnit.SECONDS);
                    int totalClaimed = 0;
                    while (true) {
                        int claimed = dispatchService.dispatchDueBatch(3);
                        if (claimed == 0) {
                            break;
                        }
                        totalClaimed += claimed;
                    }
                    return totalClaimed;
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            int total = 0;
            for (Future<Integer> future : futures) {
                total += future.get(20, TimeUnit.SECONDS);
            }
            assertThat(total).isEqualTo(10);
        }

        Integer sentCount = jdbcTemplate.queryForObject(
            "select count(*) from notification_deliveries where status = 'SENT' and attempt_count = 1",
            Integer.class
        );
        Integer sentEvents = jdbcTemplate.queryForObject(
            "select count(*) from notification_delivery_events where to_status = 'SENT'",
            Integer.class
        );
        assertThat(sentCount).isEqualTo(10);
        assertThat(sentEvents).isEqualTo(10);

        for (UUID deliveryId : deliveryIds) {
            String providerId = jdbcTemplate.queryForObject(
                "select provider_message_id from notification_deliveries where id = ?",
                String.class,
                deliveryId
            );
            assertThat(providerId).isEqualTo("noop-" + deliveryId);
        }
    }

    @Test
    void recoversStaleLocksAndAppliesRetryBackoffUntilPermanentFailure() {
        UUID userId = insertActiveUser();
        registerAuthorizedDevice(userId, "2222222222222222222222222222222222222222222222222222222222222222");
        Instant baseNow = Instant.now();

        ScheduledNotificationResult staleScheduled = schedulingPort.schedule(new ScheduleNotificationCommand(
            userId,
            "stale-lock-key-1",
            NotificationCategory.ACCOUNT_SECURITY,
            NotificationIntentType.ACCOUNT_SECURITY_ALERT,
            NotificationChannel.PUSH,
            "en",
            "account.security.alert",
            Map.of(),
            "verified-ai://account/security",
            baseNow.minusSeconds(120),
            baseNow.plusSeconds(7_200),
            "identity",
            "corr-stale-1"
        ));

        jdbcTemplate.update(
            "update notification_deliveries set status = 'DISPATCHING', locked_until = ? where id = ?",
            Timestamp.from(baseNow.minusSeconds(30)),
            staleScheduled.deliveryId()
        );

        int recovered = dispatchService.recoverStaleLocks();
        assertThat(recovered).isEqualTo(1);
        assertThat(dispatchService.dispatchDueBatch(10)).isEqualTo(1);
        String staleFinalStatus = jdbcTemplate.queryForObject(
            "select status from notification_deliveries where id = ?",
            String.class,
            staleScheduled.deliveryId()
        );
        assertThat(staleFinalStatus).isEqualTo("SENT");

        // Retry exponential backoff & permanent failure
        AtomicReference<Instant> clockRef = new AtomicReference<>(baseNow);
        Clock mutableClock = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return clockRef.get();
            }
        };
        PushDeliveryGateway alwaysTransientFailure = req -> PushDeliveryGateway.PushDispatchOutcome.retryableFailure("APNS_503");
        NotificationDispatchService retryDispatchService = new NotificationDispatchService(
            deliveryRepository,
            preferenceRepository,
            deviceRepository,
            policyEvaluator,
            alwaysTransientFailure,
            NotificationProperties.enabledDefaults(),
            new NotificationMetrics(new SimpleMeterRegistry()),
            mutableClock,
            transactionManager
        );

        ScheduledNotificationResult retryTarget = schedulingPort.schedule(new ScheduleNotificationCommand(
            userId,
            "retry-backoff-key-1",
            NotificationCategory.ACCOUNT_SECURITY,
            NotificationIntentType.ACCOUNT_SECURITY_ALERT,
            NotificationChannel.PUSH,
            "tr",
            "account.security.alert",
            Map.of(),
            "verified-ai://account/security",
            baseNow.minusSeconds(10),
            baseNow.plusSeconds(7_200),
            "identity",
            "corr-retry-1"
        ));

        // Attempt 1 -> RETRY_SCHEDULED (+60s)
        assertThat(retryDispatchService.dispatchDueBatch(10)).isEqualTo(1);
        assertThat(statusOf(retryTarget.deliveryId())).isEqualTo("RETRY_SCHEDULED");
        assertThat(attemptCountOf(retryTarget.deliveryId())).isEqualTo(1);

        // Attempt 2 -> RETRY_SCHEDULED (+120s)
        clockRef.set(baseNow.plusSeconds(61));
        assertThat(retryDispatchService.dispatchDueBatch(10)).isEqualTo(1);
        assertThat(statusOf(retryTarget.deliveryId())).isEqualTo("RETRY_SCHEDULED");
        assertThat(attemptCountOf(retryTarget.deliveryId())).isEqualTo(2);

        // Attempt 3 (max_attempts=3) -> FAILED_PERMANENT
        clockRef.set(baseNow.plusSeconds(185));
        assertThat(retryDispatchService.dispatchDueBatch(10)).isEqualTo(1);
        assertThat(statusOf(retryTarget.deliveryId())).isEqualTo("FAILED_PERMANENT");
        assertThat(attemptCountOf(retryTarget.deliveryId())).isEqualTo(3);
    }

    private UUID insertActiveUser() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into users (id, status, created_at, updated_at) values (?, 'ACTIVE', now(), now())",
            userId
        );
        return userId;
    }

    private void registerAuthorizedDevice(UUID userId, String token) {
        deviceService.registerDevice(userId, new RegisterDeviceCommand(
            "IOS_APNS", "SANDBOX", token, "AUTHORIZED", "1.0.0", "en", false
        ));
    }

    private String statusOf(UUID deliveryId) {
        return jdbcTemplate.queryForObject(
            "select status from notification_deliveries where id = ?",
            String.class,
            deliveryId
        );
    }

    private int attemptCountOf(UUID deliveryId) {
        Integer count = jdbcTemplate.queryForObject(
            "select attempt_count from notification_deliveries where id = ?",
            Integer.class,
            deliveryId
        );
        return count == null ? -1 : count;
    }
}
