package com.verifiedai.exam.domain;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import org.junit.jupiter.api.Test;

class ExamStartInstantResolverTest {
    private final Instant now = Instant.parse("2026-11-18T12:00:00Z");
    private final ExamStartInstantResolver resolver = new ExamStartInstantResolver(
            Clock.fixed(now, ZoneOffset.UTC), ZoneId.of("Europe/Istanbul"));

    @Test void exactBoundaries() {
        assertEquals(ExamStartInstantResolver.Window.EXAM_PASSED, resolver.resolveTrustedStart(now).windowStatus());
        assertEquals(ExamStartInstantResolver.Window.ACTIVE_48H_WINDOW, resolver.resolveTrustedStart(now.plusSeconds(172800)).windowStatus());
        assertEquals(ExamStartInstantResolver.Window.EARLY_PREVIEW_OUTSIDE_48H, resolver.resolveTrustedStart(now.plusSeconds(172800).plusNanos(1)).windowStatus());
        assertEquals(0.0, resolver.resolveTrustedStart(now.minusSeconds(1)).hoursRemaining());
    }

    @Test void missingAndDateOnlyNeverInventCountdown() {
        assertEquals(ExamStartInstantResolver.Window.NO_TARGET_DATE, resolver.resolveDate(null).windowStatus());
        var date = resolver.resolveDate(LocalDate.of(2026, 11, 20));
        assertNull(date.targetExamAt());
        assertNull(date.hoursRemaining());
        assertEquals(ExamStartInstantResolver.Precision.DATE_ONLY_ESTIMATE, date.timePrecision());
        assertEquals(ExamStartInstantResolver.Window.EXAM_PASSED, resolver.resolveDate(LocalDate.of(2026, 11, 17)).windowStatus());
    }

    @Test void localCalendarAndLeapDayRemainDateOnly() {
        var local = new ExamStartInstantResolver(Clock.fixed(Instant.parse("2028-02-28T22:00:00Z"), ZoneOffset.UTC), ZoneId.of("Europe/Istanbul"));
        assertEquals(ExamStartInstantResolver.Window.EXAM_PASSED, local.resolveDate(LocalDate.of(2028, 2, 28)).windowStatus());
        assertNull(local.resolveDate(LocalDate.of(2028, 2, 29)).hoursRemaining());
        var dst = new ExamStartInstantResolver(Clock.fixed(Instant.parse("2026-03-29T00:30:00Z"), ZoneOffset.UTC), ZoneId.of("Europe/Berlin"));
        assertNull(dst.resolveDate(LocalDate.of(2026, 3, 29)).hoursRemaining());
    }
}
