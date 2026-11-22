package com.verifiedai.notification.infrastructure;

import com.verifiedai.notification.application.NotificationDispatchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Isolated scheduling configuration enabled strictly when {@code verifiedai.notification.dispatch-worker-enabled=true}.
 * Keeps {@code VerifiedAiApplication} free of global {@code @EnableScheduling}.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
    prefix = "verifiedai.notification",
    name = "dispatch-worker-enabled",
    havingValue = "true"
)
class NotificationDispatchSchedulerConfig {

    private final NotificationDispatchService dispatchService;

    NotificationDispatchSchedulerConfig(NotificationDispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @Scheduled(fixedDelayString = "${verifiedai.notification.dispatch-poll-interval-ms:15000}")
    void runDispatchCycle() {
        dispatchService.recoverStaleLocks();
        dispatchService.dispatchDueBatch();
    }
}
