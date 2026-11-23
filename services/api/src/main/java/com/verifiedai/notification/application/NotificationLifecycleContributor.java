package com.verifiedai.notification.application;

import com.verifiedai.notification.domain.NotificationPreferenceSnapshot;
import com.verifiedai.notification.domain.QuietHoursPolicy;
import com.verifiedai.notification.infrastructure.LifecycleMessagingJdbcRepository;
import com.verifiedai.notification.infrastructure.NotificationDeliveryJdbcRepository;
import com.verifiedai.notification.infrastructure.NotificationDeviceJdbcRepository;
import com.verifiedai.notification.infrastructure.NotificationPreferenceJdbcRepository;
import com.verifiedai.notification.infrastructure.NotificationProperties;
import com.verifiedai.sharedkernel.privacy.AccountDataLifecycleContributor;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * GDPR/KVKK lifecycle contributor exporting privacy-safe notification and lifecycle messaging data
 * (excluding raw device tokens) and explicitly erasing all V068 and V069 notification tables on account deletion.
 */
@Component
public class NotificationLifecycleContributor implements AccountDataLifecycleContributor {

    private static final int MAX_EXPORTED_DELIVERIES = 100;
    private static final int MAX_EXPORTED_LIFECYCLE_EVALUATIONS = 50;

    private final NotificationPreferenceJdbcRepository preferenceRepository;
    private final NotificationDeviceJdbcRepository deviceRepository;
    private final NotificationDeliveryJdbcRepository deliveryRepository;
    private final LifecycleMessagingJdbcRepository lifecycleRepository;
    private final NotificationProperties properties;
    private final Clock clock;

    public NotificationLifecycleContributor(
        NotificationPreferenceJdbcRepository preferenceRepository,
        NotificationDeviceJdbcRepository deviceRepository,
        NotificationDeliveryJdbcRepository deliveryRepository,
        LifecycleMessagingJdbcRepository lifecycleRepository,
        NotificationProperties properties,
        Clock clock
    ) {
        this.preferenceRepository = preferenceRepository;
        this.deviceRepository = deviceRepository;
        this.deliveryRepository = deliveryRepository;
        this.lifecycleRepository = lifecycleRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public String category() {
        return "notifications";
    }

    @Override
    public Map<String, Object> exportUserData(UUID userId) {
        Instant now = clock.instant();
        NotificationPreferenceSnapshot pref = preferenceRepository.findByUserId(userId)
            .orElseGet(() -> NotificationPreferenceSnapshot.defaultFor(userId, properties.defaultDailyCap(), now));

        Map<String, Object> prefExport = new LinkedHashMap<>();
        prefExport.put("pushEnabled", pref.pushEnabled());
        prefExport.put("studyPlanEnabled", pref.studyPlanEnabled());
        prefExport.put("spacedReviewEnabled", pref.spacedReviewEnabled());
        prefExport.put("examDeadlineEnabled", pref.examDeadlineEnabled());
        prefExport.put("billingTrialReminderEnabled", pref.billingTrialReminderEnabled());
        prefExport.put("accountSecurityEnabled", pref.accountSecurityEnabled());
        prefExport.put("quietHoursEnabled", pref.quietHoursEnabled());
        prefExport.put("quietHoursStartLocal", QuietHoursPolicy.formatLocalTime(pref.quietHoursStartLocal()));
        prefExport.put("quietHoursEndLocal", QuietHoursPolicy.formatLocalTime(pref.quietHoursEndLocal()));
        prefExport.put("timezone", pref.timezone());
        prefExport.put("dailyCap", pref.dailyCap());
        prefExport.put("version", pref.version());
        prefExport.put("trainingEligible", false);

        Map<String, Object> export = new LinkedHashMap<>();
        export.put("preferences", prefExport);
        export.put("devices", deviceRepository.findAllByUserIdForExport(userId));
        export.put("recentDeliveries", deliveryRepository.findRecentByUserIdForExport(userId, MAX_EXPORTED_DELIVERIES));
        export.put("lifecycleCooldowns", lifecycleRepository.findCooldownsByUserIdForExport(userId));
        export.put(
            "lifecycleEvaluations",
            lifecycleRepository.findRecentEvaluationsByUserIdForExport(userId, MAX_EXPORTED_LIFECYCLE_EVALUATIONS)
        );
        export.put(
            "retentionNote",
            "Raw push device tokens and token hashes are excluded from exports; all notification preferences, devices, deliveries, delivery events, lifecycle cooldowns, and lifecycle evaluation logs are permanently erased on account deletion."
        );
        return export;
    }

    @Override
    @Transactional
    public void deleteUserData(UUID userId, Instant now) {
        lifecycleRepository.deleteEvaluationsByUserId(userId);
        lifecycleRepository.deleteCooldownsByUserId(userId);
        deliveryRepository.deleteEventsByUserId(userId);
        deliveryRepository.deleteDeliveriesByUserId(userId);
        deviceRepository.deleteByUserId(userId);
        preferenceRepository.deleteByUserId(userId);
    }
}
