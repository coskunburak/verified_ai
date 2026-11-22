package com.verifiedai.notification.application;

import com.verifiedai.notification.domain.DevicePlatform;
import com.verifiedai.notification.domain.NotificationPayloadHasher;
import com.verifiedai.notification.domain.OsNotificationPermissionStatus;
import com.verifiedai.notification.domain.PushEnvironment;
import com.verifiedai.notification.infrastructure.NotificationDeviceJdbcRepository;
import com.verifiedai.notification.infrastructure.NotificationDeviceJdbcRepository.DeviceSummaryRow;
import com.verifiedai.notification.infrastructure.NotificationPreferenceJdbcRepository;
import com.verifiedai.notification.infrastructure.NotificationProperties;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service managing push notification device registrations and token takeover isolation.
 */
@Service
public class NotificationDeviceService {

    public static final String SCHEMA_VERSION = "notification-device-v1";
    private static final Set<String> SUPPORTED_LOCALES = Set.of("en", "tr");

    private final NotificationDeviceJdbcRepository deviceRepository;
    private final NotificationPreferenceJdbcRepository preferenceRepository;
    private final NotificationProperties properties;
    private final NotificationMetrics metrics;
    private final Clock clock;

    public NotificationDeviceService(
        NotificationDeviceJdbcRepository deviceRepository,
        NotificationPreferenceJdbcRepository preferenceRepository,
        NotificationProperties properties,
        NotificationMetrics metrics,
        Clock clock
    ) {
        this.deviceRepository = deviceRepository;
        this.preferenceRepository = preferenceRepository;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public DeviceRegistrationOutcome registerDevice(UUID userId, RegisterDeviceCommand command) {
        requireActiveAccount(userId);
        requireFeatureEnabled();
        if (command == null) {
            throw validationProblem("Device registration payload is required");
        }
        if (Boolean.TRUE.equals(command.trainingEligible())) {
            throw validationProblem("Notification device records are never training eligible");
        }

        DevicePlatform platform = parseEnum(DevicePlatform.class, command.platform(), "platform");
        PushEnvironment environment = parseEnum(PushEnvironment.class, command.environment(), "environment");
        OsNotificationPermissionStatus permissionStatus = parseEnum(
            OsNotificationPermissionStatus.class,
            command.osPermissionStatus(),
            "osPermissionStatus"
        );
        String deviceToken = validateDeviceToken(command.deviceToken());
        String appVersion = validateAppVersion(command.appVersion());
        String locale = validateLocale(command.locale());

        String tokenSha256 = NotificationPayloadHasher.sha256Hex(deviceToken);
        String tokenLast4 = deviceToken.substring(deviceToken.length() - 4);
        Instant now = clock.instant();

        // Always lock token before user to maintain a consistent global lock acquisition order.
        deviceRepository.lockToken(tokenSha256);
        deviceRepository.lockUser(userId);

        int takenOver = deviceRepository.revokeActiveTokenOwnedByOtherUsers(tokenSha256, userId, now);
        if (takenOver > 0) {
            metrics.recordDeviceLifecycle("takeover_revoked");
        }

        Optional<DeviceSummaryRow> existing = deviceRepository.findByUserAndTokenSha256(userId, tokenSha256);
        if (existing.isPresent()) {
            DeviceSummaryRow row = existing.get();
            if (!row.active() && deviceRepository.countActiveByUserId(userId) >= properties.maxActiveDevicesPerUser()) {
                throw deviceLimitExceeded();
            }
            boolean reactivated = !row.active();
            DeviceSummaryRow updated = deviceRepository.reactivateOrUpdate(
                row.deviceId(),
                userId,
                platform,
                environment,
                deviceToken,
                tokenLast4,
                permissionStatus,
                appVersion,
                locale,
                now
            );
            if (reactivated) {
                metrics.recordDeviceLifecycle("registered");
            }
            return new DeviceRegistrationOutcome(toView(updated), false);
        }

        if (deviceRepository.countActiveByUserId(userId) >= properties.maxActiveDevicesPerUser()) {
            throw deviceLimitExceeded();
        }

        DeviceSummaryRow inserted = deviceRepository.insert(
            UUID.randomUUID(),
            userId,
            platform,
            environment,
            deviceToken,
            tokenSha256,
            tokenLast4,
            permissionStatus,
            appVersion,
            locale,
            now
        );
        metrics.recordDeviceLifecycle("registered");
        return new DeviceRegistrationOutcome(toView(inserted), true);
    }

    @Transactional(readOnly = true)
    public List<RegisteredDeviceView> listActiveDevices(UUID userId) {
        requireActiveAccount(userId);
        requireFeatureEnabled();
        return deviceRepository.findActiveByUserId(userId).stream()
            .map(NotificationDeviceService::toView)
            .toList();
    }

    @Transactional
    public RegisteredDeviceView revokeDevice(UUID userId, UUID deviceId) {
        requireActiveAccount(userId);
        requireFeatureEnabled();
        if (deviceId == null) {
            throw new ApiProblemException(
                HttpStatus.NOT_FOUND,
                ApiErrorCode.NOTIFICATION_DEVICE_NOT_FOUND,
                "Notification device not found",
                false,
                "REFRESH"
            );
        }

        deviceRepository.lockUser(userId);
        DeviceSummaryRow row = deviceRepository.findByIdAndUserId(deviceId, userId)
            .orElseThrow(() -> new ApiProblemException(
                HttpStatus.NOT_FOUND,
                ApiErrorCode.NOTIFICATION_DEVICE_NOT_FOUND,
                "Notification device not found",
                false,
                "REFRESH"
            ));

        if (row.active()) {
            Instant now = clock.instant();
            deviceRepository.revokeByIdAndUserId(deviceId, userId, now);
            metrics.recordDeviceLifecycle("revoked");
            row = deviceRepository.findByIdAndUserId(deviceId, userId).orElse(row);
        }
        return toView(row);
    }

    private void requireActiveAccount(UUID userId) {
        if (!preferenceRepository.isAccountActive(userId)) {
            throw new ApiProblemException(
                HttpStatus.FORBIDDEN,
                ApiErrorCode.ACCOUNT_NOT_ACTIVE,
                "Account is not active",
                false,
                "SIGN_IN"
            );
        }
    }

    private void requireFeatureEnabled() {
        if (!properties.enabled()) {
            throw new ApiProblemException(
                HttpStatus.SERVICE_UNAVAILABLE,
                ApiErrorCode.NOTIFICATION_FEATURE_DISABLED,
                "Notification infrastructure is disabled",
                true,
                "RETRY_LATER"
            );
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> enumType, String raw, String field) {
        if (raw == null || raw.isBlank()) {
            throw validationProblem("Notification device " + field + " is required");
        }
        try {
            return Enum.valueOf(enumType, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw validationProblem("Unsupported notification device " + field + ": " + raw);
        }
    }

    private static String validateDeviceToken(String rawToken) {
        if (rawToken == null) {
            throw validationProblem("Notification deviceToken is required");
        }
        String trimmed = rawToken.trim();
        if (trimmed.length() < 32 || trimmed.length() > 512) {
            throw validationProblem("Notification deviceToken length must be between 32 and 512");
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if (Character.isWhitespace(ch) || Character.isISOControl(ch)) {
                throw validationProblem("Notification deviceToken must not contain whitespace or control characters");
            }
        }
        return trimmed;
    }

    private static String validateAppVersion(String rawVersion) {
        if (rawVersion == null || rawVersion.isBlank()) {
            throw validationProblem("Notification appVersion must not be blank");
        }
        String trimmed = rawVersion.trim();
        if (trimmed.length() > 32) {
            throw validationProblem("Notification appVersion exceeds 32 characters");
        }
        return trimmed;
    }

    private static String validateLocale(String rawLocale) {
        if (rawLocale == null || rawLocale.isBlank()) {
            return "en";
        }
        String normalized = rawLocale.trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_LOCALES.contains(normalized)) {
            throw validationProblem("Notification device locale must be en or tr");
        }
        return normalized;
    }

    private static RegisteredDeviceView toView(DeviceSummaryRow row) {
        return new RegisteredDeviceView(
            SCHEMA_VERSION,
            row.deviceId(),
            row.platform().name(),
            row.environment().name(),
            row.tokenLast4(),
            row.osPermissionStatus().name(),
            row.appVersion(),
            row.locale(),
            row.active(),
            row.version(),
            false,
            row.lastSeenAt(),
            row.revokedAt(),
            row.updatedAt()
        );
    }

    private static ApiProblemException validationProblem(String title) {
        return new ApiProblemException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ApiErrorCode.NOTIFICATION_VALIDATION_FAILED,
            title,
            true,
            "RETRY"
        );
    }

    private static ApiProblemException deviceLimitExceeded() {
        return new ApiProblemException(
            HttpStatus.CONFLICT,
            ApiErrorCode.NOTIFICATION_DEVICE_LIMIT_EXCEEDED,
            "Maximum active notification devices reached for this account",
            false,
            "REVOKE_UNUSED_DEVICE"
        );
    }

    public record DeviceRegistrationOutcome(RegisteredDeviceView device, boolean created) {
    }
}
