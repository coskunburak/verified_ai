package com.verifiedai.notification.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Public privacy-safe device projection containing {@code tokenLast4} and never raw {@code deviceToken}.
 */
public record RegisteredDeviceView(
    String schemaVersion,
    UUID deviceId,
    String platform,
    String environment,
    String tokenLast4,
    String osPermissionStatus,
    String appVersion,
    String locale,
    boolean active,
    long version,
    boolean trainingEligible,
    Instant lastSeenAt,
    Instant revokedAt,
    Instant updatedAt
) {
}
