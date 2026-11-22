package com.verifiedai.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.verifiedai.notification.domain.QuietHoursPolicy.QuietHoursEvaluation;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

final class QuietHoursPolicyTest {

    private static final LocalTime DEFAULT_START = LocalTime.of(22, 0);
    private static final LocalTime DEFAULT_END = LocalTime.of(8, 0);

    @Test
    void overnightQuietHoursRespectInclusiveStartAndExclusiveEnd() {
        ZoneId istanbul = QuietHoursPolicy.parseAndValidateZone("Europe/Istanbul");

        Instant beforeWindow = ZonedDateTime.of(2026, 11, 22, 21, 59, 59, 0, istanbul).toInstant();
        QuietHoursEvaluation beforeEval = QuietHoursPolicy.evaluate(
            beforeWindow, beforeWindow.plusSeconds(43_200), true, DEFAULT_START, DEFAULT_END, istanbul
        );
        assertThat(beforeEval.deferred()).isFalse();
        assertThat(beforeEval.expired()).isFalse();
        assertThat(beforeEval.effectiveSendAt()).isEqualTo(beforeWindow);

        Instant atStart = ZonedDateTime.of(2026, 11, 22, 22, 0, 0, 0, istanbul).toInstant();
        Instant expectedMorning = ZonedDateTime.of(2026, 11, 23, 8, 0, 0, 0, istanbul).toInstant();
        QuietHoursEvaluation startEval = QuietHoursPolicy.evaluate(
            atStart, atStart.plusSeconds(86_400), true, DEFAULT_START, DEFAULT_END, istanbul
        );
        assertThat(startEval.deferred()).isTrue();
        assertThat(startEval.expired()).isFalse();
        assertThat(startEval.effectiveSendAt()).isEqualTo(expectedMorning);

        Instant earlyMorning = ZonedDateTime.of(2026, 11, 23, 7, 59, 59, 0, istanbul).toInstant();
        QuietHoursEvaluation morningEval = QuietHoursPolicy.evaluate(
            earlyMorning, earlyMorning.plusSeconds(3_600), true, DEFAULT_START, DEFAULT_END, istanbul
        );
        assertThat(morningEval.deferred()).isTrue();
        assertThat(morningEval.effectiveSendAt()).isEqualTo(expectedMorning);

        Instant atEnd = ZonedDateTime.of(2026, 11, 23, 8, 0, 0, 0, istanbul).toInstant();
        QuietHoursEvaluation endEval = QuietHoursPolicy.evaluate(
            atEnd, atEnd.plusSeconds(3_600), true, DEFAULT_START, DEFAULT_END, istanbul
        );
        assertThat(endEval.deferred()).isFalse();
        assertThat(endEval.effectiveSendAt()).isEqualTo(atEnd);
    }

    @Test
    void sameDayQuietHoursWindowDefersWithinAfternoonBounds() {
        ZoneId istanbul = QuietHoursPolicy.parseAndValidateZone("Europe/Istanbul");
        LocalTime start = LocalTime.of(13, 0);
        LocalTime end = LocalTime.of(15, 0);

        Instant inside = ZonedDateTime.of(2026, 11, 22, 13, 30, 0, 0, istanbul).toInstant();
        Instant expected = ZonedDateTime.of(2026, 11, 22, 15, 0, 0, 0, istanbul).toInstant();
        QuietHoursEvaluation eval = QuietHoursPolicy.evaluate(
            inside, inside.plusSeconds(14_400), true, start, end, istanbul
        );
        assertThat(eval.deferred()).isTrue();
        assertThat(eval.effectiveSendAt()).isEqualTo(expected);

        Instant outside = ZonedDateTime.of(2026, 11, 22, 15, 0, 0, 0, istanbul).toInstant();
        QuietHoursEvaluation outEval = QuietHoursPolicy.evaluate(
            outside, outside.plusSeconds(3_600), true, start, end, istanbul
        );
        assertThat(outEval.deferred()).isFalse();
    }

    @Test
    void americaNewYorkSpringForwardAndFallBackDstAreDeterministic() {
        ZoneId newYork = QuietHoursPolicy.parseAndValidateZone("America/New_York");

        // Spring-forward: 2026-03-08 02:00 jumps to 03:00 EDT; 02:30 does not exist -> resolves to 03:30-04:00
        Instant springScheduled = ZonedDateTime.of(2026, 3, 8, 1, 30, 0, 0, newYork).toInstant();
        QuietHoursEvaluation springEval = QuietHoursPolicy.evaluate(
            springScheduled,
            springScheduled.plusSeconds(86_400),
            true,
            LocalTime.of(1, 0),
            LocalTime.of(2, 30),
            newYork
        );
        assertThat(springEval.deferred()).isTrue();
        assertThat(springEval.effectiveSendAt()).isAfterOrEqualTo(springScheduled);
        assertThat(springEval.effectiveSendAt())
            .isEqualTo(ZonedDateTime.of(2026, 3, 8, 3, 30, 0, 0, newYork).toInstant());

        // Fall-back: 2026-11-01 repeats 01:00..01:59; withLaterOffsetAtOverlap() guarantees 01:30 EST (-05:00)
        // so a notification scheduled during the second 01:15 pass is not released early at 01:30 EDT.
        Instant secondPass = ZonedDateTime.of(2026, 11, 1, 1, 15, 0, 0, newYork)
            .withLaterOffsetAtOverlap()
            .toInstant();
        Instant expectedEstEnd = ZonedDateTime.of(2026, 11, 1, 1, 30, 0, 0, newYork)
            .withLaterOffsetAtOverlap()
            .toInstant();
        QuietHoursEvaluation fallEval = QuietHoursPolicy.evaluate(
            secondPass,
            secondPass.plusSeconds(86_400),
            true,
            LocalTime.of(22, 0),
            LocalTime.of(1, 30),
            newYork
        );
        assertThat(fallEval.deferred()).isTrue();
        assertThat(fallEval.effectiveSendAt()).isEqualTo(expectedEstEnd);
        assertThat(fallEval.effectiveSendAt()).isAfter(secondPass);
    }

    @Test
    void europeBerlinSpringForwardAndFallBackDstAreDeterministic() {
        ZoneId berlin = QuietHoursPolicy.parseAndValidateZone("Europe/Berlin");

        // Spring-forward: 2026-03-29 02:00 jumps to 03:00 CEST; 02:30 resolves to 03:30+02:00
        Instant springScheduled = ZonedDateTime.of(2026, 3, 29, 1, 45, 0, 0, berlin).toInstant();
        QuietHoursEvaluation springEval = QuietHoursPolicy.evaluate(
            springScheduled,
            springScheduled.plusSeconds(86_400),
            true,
            LocalTime.of(1, 0),
            LocalTime.of(2, 30),
            berlin
        );
        assertThat(springEval.deferred()).isTrue();
        assertThat(springEval.effectiveSendAt())
            .isEqualTo(ZonedDateTime.of(2026, 3, 29, 3, 30, 0, 0, berlin).toInstant());

        // Fall-back: 2026-10-25 repeats 02:00..02:59; 02:30 end picks later +01:00 offset
        Instant fallScheduled = ZonedDateTime.of(2026, 10, 25, 2, 15, 0, 0, berlin)
            .withLaterOffsetAtOverlap()
            .toInstant();
        Instant expectedEnd = ZonedDateTime.of(2026, 10, 25, 2, 30, 0, 0, berlin)
            .withLaterOffsetAtOverlap()
            .toInstant();
        QuietHoursEvaluation fallEval = QuietHoursPolicy.evaluate(
            fallScheduled,
            fallScheduled.plusSeconds(86_400),
            true,
            LocalTime.of(22, 0),
            LocalTime.of(2, 30),
            berlin
        );
        assertThat(fallEval.deferred()).isTrue();
        assertThat(fallEval.effectiveSendAt()).isEqualTo(expectedEnd);
        assertThat(fallEval.effectiveSendAt()).isAfter(fallScheduled);
    }

    @Test
    void marksIntentExpiredWhenDeferredSendReachesOrExceedsExpiresAt() {
        ZoneId utc = QuietHoursPolicy.parseAndValidateZone("UTC");
        Instant scheduled = Instant.parse("2026-11-22T23:00:00Z");
        Instant expiresAt = Instant.parse("2026-11-23T08:00:00Z");

        QuietHoursEvaluation eval = QuietHoursPolicy.evaluate(
            scheduled, expiresAt, true, DEFAULT_START, DEFAULT_END, utc
        );
        assertThat(eval.expired()).isTrue();
        assertThat(eval.deferred()).isFalse();
        assertThat(eval.effectiveSendAt()).isEqualTo(scheduled);
    }

    @Test
    void rejectsDegenerateQuietWindowAndInvalidTimezone() {
        assertThatThrownBy(() -> QuietHoursPolicy.validateWindow(LocalTime.of(22, 0), LocalTime.of(22, 0)))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> assertThat(((ApiProblemException) ex).code())
                .isEqualTo(ApiErrorCode.NOTIFICATION_QUIET_HOURS_INVALID));

        assertThatThrownBy(() -> QuietHoursPolicy.parseAndValidateZone("Mars/Olympus_Mons"))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> assertThat(((ApiProblemException) ex).code())
                .isEqualTo(ApiErrorCode.NOTIFICATION_TIMEZONE_INVALID));
    }
}
