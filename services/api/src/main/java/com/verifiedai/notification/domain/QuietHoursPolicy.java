package com.verifiedai.notification.domain;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

/**
 * Timezone and DST-aware quiet hours policy evaluator and validator (INV-NOTIF-002).
 */
public final class QuietHoursPolicy {

    private static final int MAX_TIMEZONE_LENGTH = 64;
    private static final Pattern HH_MM_PATTERN = Pattern.compile("^([01][0-9]|2[0-3]):[0-5][0-9]$");
    private static final DateTimeFormatter HH_MM_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private QuietHoursPolicy() {
    }

    public static ZoneId parseAndValidateZone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            throw invalidTimezone("Timezone must not be blank");
        }
        String trimmed = timezone.trim();
        if (trimmed.length() > MAX_TIMEZONE_LENGTH) {
            throw invalidTimezone("Timezone exceeds maximum length");
        }
        try {
            return ZoneId.of(trimmed);
        } catch (RuntimeException exception) {
            throw invalidTimezone("Unsupported IANA timezone: " + trimmed);
        }
    }

    public static LocalTime parseLocalTime(String value) {
        if (value == null || value.isBlank()) {
            throw invalidQuietHours("Quiet hours time must not be blank");
        }
        String trimmed = value.trim();
        if (!HH_MM_PATTERN.matcher(trimmed).matches()) {
            throw invalidQuietHours("Quiet hours time must use HH:mm 24-hour format");
        }
        return LocalTime.parse(trimmed, HH_MM_FORMATTER);
    }

    public static String formatLocalTime(LocalTime time) {
        Objects.requireNonNull(time, "time must not be null");
        return time.format(HH_MM_FORMATTER);
    }

    public static void validateWindow(LocalTime quietStart, LocalTime quietEnd) {
        if (quietStart == null || quietEnd == null) {
            throw invalidQuietHours("Quiet hours start and end must both be specified");
        }
        if (quietStart.equals(quietEnd)) {
            throw invalidQuietHours("Quiet hours start and end must not be equal");
        }
    }

    public static QuietHoursEvaluation evaluate(
        Instant scheduledFor,
        Instant expiresAt,
        boolean quietHoursEnabled,
        LocalTime quietStart,
        LocalTime quietEnd,
        ZoneId zoneId
    ) {
        Objects.requireNonNull(scheduledFor, "scheduledFor must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        Objects.requireNonNull(zoneId, "zoneId must not be null");
        if (!expiresAt.isAfter(scheduledFor)) {
            throw new ApiProblemException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.NOTIFICATION_VALIDATION_FAILED,
                "Notification expiresAt must be strictly after scheduledFor",
                true,
                "RETRY"
            );
        }
        validateWindow(quietStart, quietEnd);

        if (!quietHoursEnabled) {
            return new QuietHoursEvaluation(false, false, scheduledFor);
        }

        ZonedDateTime scheduledZdt = scheduledFor.atZone(zoneId);
        LocalTime localTime = scheduledZdt.toLocalTime();
        LocalDate localDate = scheduledZdt.toLocalDate();

        boolean overnight = quietStart.isAfter(quietEnd);
        boolean insideWindow = overnight
            ? (!localTime.isBefore(quietStart) || localTime.isBefore(quietEnd))
            : (!localTime.isBefore(quietStart) && localTime.isBefore(quietEnd));

        if (!insideWindow) {
            return new QuietHoursEvaluation(false, false, scheduledFor);
        }

        LocalDate endDate = (overnight && !localTime.isBefore(quietStart))
            ? localDate.plusDays(1)
            : localDate;

        // Use withLaterOffsetAtOverlap() so fall-back DST repeats never release early inside quiet hours,
        // and JVM/IANA gap resolution moves spring-forward non-existent local times forward.
        ZonedDateTime endZdt = ZonedDateTime.of(endDate, quietEnd, zoneId).withLaterOffsetAtOverlap();
        Instant deferredUntil = endZdt.toInstant();
        if (deferredUntil.isBefore(scheduledFor)) {
            deferredUntil = scheduledFor;
        }

        if (!deferredUntil.isBefore(expiresAt)) {
            return new QuietHoursEvaluation(false, true, scheduledFor);
        }
        return new QuietHoursEvaluation(true, false, deferredUntil);
    }

    public static LocalDayBounds localDayBounds(Instant reference, ZoneId zoneId) {
        Objects.requireNonNull(reference, "reference must not be null");
        Objects.requireNonNull(zoneId, "zoneId must not be null");
        LocalDate localDate = reference.atZone(zoneId).toLocalDate();
        Instant startInclusive = localDate.atStartOfDay(zoneId).toInstant();
        Instant endExclusive = localDate.plusDays(1).atStartOfDay(zoneId).toInstant();
        return new LocalDayBounds(startInclusive, endExclusive);
    }

    private static ApiProblemException invalidTimezone(String title) {
        return new ApiProblemException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ApiErrorCode.NOTIFICATION_TIMEZONE_INVALID,
            title,
            true,
            "RETRY"
        );
    }

    private static ApiProblemException invalidQuietHours(String title) {
        return new ApiProblemException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ApiErrorCode.NOTIFICATION_QUIET_HOURS_INVALID,
            title,
            true,
            "RETRY"
        );
    }

    public record QuietHoursEvaluation(boolean deferred, boolean expired, Instant effectiveSendAt) {
    }

    public record LocalDayBounds(Instant startInclusive, Instant endExclusive) {
    }
}
