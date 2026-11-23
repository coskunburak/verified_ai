package com.verifiedai.studyplan.lifecycle;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Isolated scheduling configuration enabled strictly when {@code verifiedai.notification.lifecycle-scheduler-enabled=true}.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
    prefix = "verifiedai.notification",
    name = "lifecycle-scheduler-enabled",
    havingValue = "true"
)
class StudyPlanLifecycleSchedulerConfig {

    private final StudyPlanLifecycleMessagingService studyPlanLifecycleMessagingService;

    StudyPlanLifecycleSchedulerConfig(StudyPlanLifecycleMessagingService studyPlanLifecycleMessagingService) {
        this.studyPlanLifecycleMessagingService = studyPlanLifecycleMessagingService;
    }

    @Scheduled(fixedDelayString = "${verifiedai.notification.lifecycle-scan-interval-ms:300000}")
    void runStudyPlanLifecycleScan() {
        studyPlanLifecycleMessagingService.scanAndEvaluateBatch();
    }
}
