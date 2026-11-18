package com.verifiedai.exam.domain;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/** A date alone never establishes an exact exam start. No official schedule is inferred. */
public final class ExamStartInstantResolver {
    public enum Precision { EXACT, DATE_ONLY_ESTIMATE, UNAVAILABLE }
    public enum Window { ACTIVE_48H_WINDOW, EARLY_PREVIEW_OUTSIDE_48H, DATE_ONLY_ESTIMATE, NO_TARGET_DATE, EXAM_PASSED }
    public record Resolution(LocalDate targetExamDate, Instant targetExamAt,
                             Precision timePrecision, Window windowStatus, Double hoursRemaining) {}
    private final Clock clock;
    private final ZoneId candidateZone;

    public ExamStartInstantResolver(Clock clock, ZoneId candidateZone) {
        this.clock = Objects.requireNonNull(clock);
        this.candidateZone = Objects.requireNonNull(candidateZone);
    }

    public Resolution resolveDate(LocalDate targetDate) {
        if (targetDate == null) {
            return new Resolution(null, null, Precision.UNAVAILABLE, Window.NO_TARGET_DATE, null);
        }
        Window status = targetDate.isBefore(LocalDate.now(clock.withZone(candidateZone)))
                ? Window.EXAM_PASSED : Window.DATE_ONLY_ESTIMATE;
        return new Resolution(targetDate, null, Precision.DATE_ONLY_ESTIMATE, status, null);
    }

    /** Only call with a start obtained from a trusted schedule source, never a fabricated midnight. */
    public Resolution resolveTrustedStart(Instant start) {
        Objects.requireNonNull(start);
        Duration remaining = Duration.between(clock.instant(), start);
        Window status = remaining.isNegative() || remaining.isZero() ? Window.EXAM_PASSED
                : remaining.compareTo(Duration.ofHours(48)) <= 0 ? Window.ACTIVE_48H_WINDOW
                : Window.EARLY_PREVIEW_OUTSIDE_48H;
        double hours = Math.max(0, remaining.getSeconds() / 3600.0 + remaining.getNano() / 3_600_000_000_000.0);
        return new Resolution(start.atZone(candidateZone).toLocalDate(), start, Precision.EXACT, status, hours);
    }
}
