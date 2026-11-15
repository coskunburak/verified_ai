package com.verifiedai.exam.domain;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;

/**
 * Pure deterministic server-authoritative clock and accommodation policy per ADR-005 & INV-EXAM-003.
 */
public final class ExamClockPolicy {

    public static final int DEFAULT_OFFLINE_GRACE_BUFFER_SECONDS = 120;
    public static final int MAX_OFFLINE_GRACE_BUFFER_SECONDS = 120;
    public static final int MIN_DURATION_SECONDS = 60;
    public static final int MAX_EFFECTIVE_DURATION_SECONDS = 32_400;
    public static final long CLOCK_SKEW_ANOMALY_THRESHOLD_MS = 5_000L;

    private static final Set<BigDecimal> ALLOWED_MULTIPLIERS = Set.of(
            new BigDecimal("1.00"),
            new BigDecimal("1.25"),
            new BigDecimal("1.50"),
            new BigDecimal("2.00")
    );

    private ExamClockPolicy() {
    }

    public static BigDecimal normalizeMultiplier(BigDecimal rawMultiplier) {
        if (rawMultiplier == null) {
            return new BigDecimal("1.00");
        }
        BigDecimal scaled = rawMultiplier.setScale(2, RoundingMode.UNNECESSARY);
        for (BigDecimal allowed : ALLOWED_MULTIPLIERS) {
            if (allowed.compareTo(scaled) == 0) {
                return allowed;
            }
        }
        throw new ApiProblemException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.CANDIDATE_ACCOMMODATION_INVALID,
                "extraTimeMultiplier must be one of 1.00, 1.25, 1.50, or 2.00",
                true,
                "Select a valid extra-time accommodation multiplier."
        );
    }

    public static int computeEffectiveDurationSeconds(int baseDurationSeconds, BigDecimal extraTimeMultiplier) {
        if (baseDurationSeconds < MIN_DURATION_SECONDS || baseDurationSeconds > 21_600) {
            throw new IllegalArgumentException("baseDurationSeconds must be between 60 and 21600");
        }
        BigDecimal normalized = normalizeMultiplier(extraTimeMultiplier);
        BigDecimal multiplied = BigDecimal.valueOf(baseDurationSeconds)
                .multiply(normalized)
                .setScale(0, RoundingMode.CEILING);
        int raw = multiplied.intValueExact();
        return Math.min(MAX_EFFECTIVE_DURATION_SECONDS, Math.max(MIN_DURATION_SECONDS, raw));
    }

    public static int validateOfflineGraceBufferSeconds(int offlineGraceBufferSeconds) {
        if (offlineGraceBufferSeconds < 0 || offlineGraceBufferSeconds > MAX_OFFLINE_GRACE_BUFFER_SECONDS) {
            throw new IllegalArgumentException("offlineGraceBufferSeconds must be between 0 and 120");
        }
        return offlineGraceBufferSeconds;
    }

    public static Instant computeServerDeadlineAt(Instant startedAt, int effectiveDurationSeconds) {
        Objects.requireNonNull(startedAt, "startedAt is required");
        return startedAt.plusSeconds(effectiveDurationSeconds);
    }

    public static Instant computeGraceDeadlineAt(Instant serverDeadlineAt, int offlineGraceBufferSeconds) {
        Objects.requireNonNull(serverDeadlineAt, "serverDeadlineAt is required");
        return serverDeadlineAt.plusSeconds(validateOfflineGraceBufferSeconds(offlineGraceBufferSeconds));
    }

    public static long computeRemainingSeconds(Instant serverNow, Instant serverDeadlineAt) {
        Objects.requireNonNull(serverNow, "serverNow is required");
        Objects.requireNonNull(serverDeadlineAt, "serverDeadlineAt is required");
        long seconds = Duration.between(serverNow, serverDeadlineAt).getSeconds();
        return Math.max(0L, seconds);
    }

    public static long computeGraceRemainingSeconds(Instant serverNow, Instant graceDeadlineAt) {
        Objects.requireNonNull(serverNow, "serverNow is required");
        Objects.requireNonNull(graceDeadlineAt, "graceDeadlineAt is required");
        long seconds = Duration.between(serverNow, graceDeadlineAt).getSeconds();
        return Math.max(0L, seconds);
    }

    public static long computeClientClockSkewMs(Instant serverNow, Instant clientReportedTimestamp) {
        Objects.requireNonNull(serverNow, "serverNow is required");
        if (clientReportedTimestamp == null) {
            return 0L;
        }
        return Duration.between(serverNow, clientReportedTimestamp).toMillis();
    }

    public static boolean isClockSkewAnomaly(long clientClockSkewMs) {
        return Math.abs(clientClockSkewMs) > CLOCK_SKEW_ANOMALY_THRESHOLD_MS;
    }

    public static boolean isPastServerDeadline(Instant serverNow, Instant serverDeadlineAt) {
        Objects.requireNonNull(serverNow, "serverNow is required");
        Objects.requireNonNull(serverDeadlineAt, "serverDeadlineAt is required");
        return serverNow.isAfter(serverDeadlineAt);
    }

    public static boolean isPastGraceDeadline(Instant serverNow, Instant graceDeadlineAt) {
        Objects.requireNonNull(serverNow, "serverNow is required");
        Objects.requireNonNull(graceDeadlineAt, "graceDeadlineAt is required");
        return serverNow.isAfter(graceDeadlineAt);
    }

    public static boolean isWithinOfflineGraceWindow(
            Instant serverNow,
            Instant serverDeadlineAt,
            Instant graceDeadlineAt,
            boolean offlineReplay
    ) {
        Objects.requireNonNull(serverNow, "serverNow is required");
        Objects.requireNonNull(serverDeadlineAt, "serverDeadlineAt is required");
        Objects.requireNonNull(graceDeadlineAt, "graceDeadlineAt is required");
        return offlineReplay && serverNow.isAfter(serverDeadlineAt) && !serverNow.isAfter(graceDeadlineAt);
    }
}
