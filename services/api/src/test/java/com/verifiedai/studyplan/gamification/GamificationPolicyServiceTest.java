package com.verifiedai.studyplan.gamification;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.studyplan.gamification.GamificationExceptions.GamificationActivityWindowInvalidException;
import com.verifiedai.studyplan.gamification.GamificationExceptions.GamificationFeatureDisabledException;
import com.verifiedai.studyplan.gamification.GamificationExceptions.GamificationTimezoneInvalidException;
import com.verifiedai.studyplan.gamification.GamificationModels.DailyStreakLedgerRecord;
import com.verifiedai.studyplan.gamification.GamificationModels.GamificationActivityCommand;
import com.verifiedai.studyplan.gamification.GamificationModels.GamificationSummaryView;
import com.verifiedai.studyplan.gamification.GamificationModels.LearnerAchievementRecord;
import com.verifiedai.studyplan.gamification.GamificationModels.LearnerStreakRecord;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class GamificationPolicyServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-000000000880");

    private InMemoryGamificationRepository repository;
    private SimpleMeterRegistry meterRegistry;
    private MutableClock clock;
    private GamificationPolicyService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryGamificationRepository();
        meterRegistry = new SimpleMeterRegistry();
        clock = new MutableClock(Instant.parse("2026-11-10T12:00:00Z"));
        service = new GamificationPolicyService(repository, meterRegistry, clock, true);
    }

    @Test
    void independentActivityIncrementsStreakOncePerLocalDay() {
        GamificationSummaryView summary = service.recordActivity(USER_ID, independentCmd("k1", "twin:1"));

        assertEquals(1, summary.currentStreakDays());
        assertEquals(1, summary.longestStreakDays());
        assertEquals(StreakStatus.ACTIVE_VERIFIED_TODAY, summary.streakStatus());
        assertTrue(summary.verifiedToday());
        assertTrue(summary.continuityPreservedToday());
        assertTrue(summary.streakIncrementedOnEvaluation());
        assertEquals(1, summary.totalIndependentDays());
        assertEquals(0, summary.totalAssistedContinuityDays());
        assertFalse(summary.trainingEligible());
    }

    @Test
    void multipleSameDayIndependentActivitiesAreIdempotentForStreakCount() {
        GamificationSummaryView first = service.recordActivity(USER_ID, independentCmd("idem-day1-a", "twin:1"));
        clock.advanceSeconds(600);
        GamificationSummaryView second = service.recordActivity(USER_ID, independentCmd("idem-day1-b", "twin:2"));

        assertEquals(1, first.currentStreakDays());
        assertEquals(1, second.currentStreakDays());
        assertEquals(1, second.totalIndependentDays());
        assertFalse(second.streakIncrementedOnEvaluation());
        assertEquals(2, second.recentDailyLedger().getFirst().independentActivityCount());
        assertEquals(1.0, meterRegistry.counter("gamification.streak.incremented.total").count());
    }

    @Test
    void hintAssistedAndSolutionRevealedPreserveContinuityWithoutIncrementingStreak() {
        GamificationSummaryView hintResult = service.recordActivity(
                USER_ID,
                command("idem-hint-1", GamificationEvidenceType.HINT_ASSISTED, "attempt:hint:1")
        );
        clock.advanceDays(1);
        GamificationSummaryView revealedResult = service.recordActivity(
                USER_ID,
                command("idem-reveal-1", GamificationEvidenceType.SOLUTION_REVEALED_ASSISTED, "attempt:rev:1")
        );

        assertEquals(0, hintResult.currentStreakDays());
        assertEquals(StreakStatus.CONTINUITY_PRESERVED_TODAY, hintResult.streakStatus());
        assertFalse(hintResult.verifiedToday());
        assertTrue(hintResult.continuityPreservedToday());

        assertEquals(0, revealedResult.currentStreakDays());
        assertEquals(0, revealedResult.totalIndependentDays());
        assertEquals(2, revealedResult.totalAssistedContinuityDays());
        assertEquals(DailyStreakStatus.ASSISTED_CONTINUITY, revealedResult.recentDailyLedger().getFirst().dailyStatus());
        assertEquals(2.0, meterRegistry.counter("gamification.streak.assisted_continuity.total").count());
    }

    @Test
    void assistedEarlierThenIndependentSameDayUpgradesToVerifiedAndIncrementsOnce() {
        GamificationSummaryView assisted = service.recordActivity(
                USER_ID,
                command("idem-assisted-morning", GamificationEvidenceType.HINT_ASSISTED, "attempt:hint:am")
        );
        assertEquals(0, assisted.currentStreakDays());
        assertEquals(StreakStatus.CONTINUITY_PRESERVED_TODAY, assisted.streakStatus());

        clock.advanceSeconds(1800);
        GamificationSummaryView upgraded = service.recordActivity(
                USER_ID,
                independentCmd("idem-indep-evening", "twin:pm")
        );
        assertEquals(1, upgraded.currentStreakDays());
        assertEquals(1, upgraded.longestStreakDays());
        assertTrue(upgraded.verifiedToday());
        assertTrue(upgraded.streakIncrementedOnEvaluation());
        assertEquals(StreakStatus.ACTIVE_VERIFIED_TODAY, upgraded.streakStatus());
        assertEquals(1, upgraded.recentDailyLedger().size());
        assertEquals(DailyStreakStatus.VERIFIED_INDEPENDENT, upgraded.recentDailyLedger().getFirst().dailyStatus());
    }

    @Test
    void missedSingleDayAutomaticallyConsumesOneFreezeAndPreservesStreak() {
        service.recordActivity(USER_ID, independentCmd("day1-key", "twin:day1"));
        // Skip Nov 11, evaluate summary on Nov 12
        clock.advanceDays(2);

        GamificationSummaryView summary = service.getSummary(USER_ID, "UTC");
        assertEquals(1, summary.currentStreakDays());
        assertEquals(1, summary.streakFreezesRemaining());
        assertEquals(1, summary.freezesUsedThisMonth());
        assertTrue(summary.freezeAppliedOnEvaluation());
        assertEquals(StreakStatus.FREEZE_PROTECTED, summary.streakStatus());
        assertEquals(1.0, meterRegistry.counter("gamification.streak.freeze.applied.total").count());

        // Independent completion on Nov 12 advances preserved streak to 2
        GamificationSummaryView day3 = service.recordActivity(USER_ID, independentCmd("day3-key", "twin:day3"));
        assertEquals(2, day3.currentStreakDays());
        assertEquals(1, day3.streakFreezesRemaining());
    }

    @Test
    void missedTwoDaysConsumesBothFreezesAndThirdMissedDayResetsCurrentStreakWhileKeepingLongest() {
        service.recordActivity(USER_ID, independentCmd("d1", "twin:1"));
        clock.advanceDays(1);
        service.recordActivity(USER_ID, independentCmd("d2", "twin:2"));
        clock.advanceDays(1);
        GamificationSummaryView day3 = service.recordActivity(USER_ID, independentCmd("d3", "twin:3"));
        assertEquals(3, day3.currentStreakDays());
        assertEquals(3, day3.longestStreakDays());

        // Miss 2 days (skip 2 consecutive days -> advance 3 days) -> consumes both freezes
        clock.advanceDays(3);
        GamificationSummaryView afterTwoMissed = service.getSummary(USER_ID, "UTC");
        assertEquals(3, afterTwoMissed.currentStreakDays());
        assertEquals(0, afterTwoMissed.streakFreezesRemaining());
        assertEquals(2, afterTwoMissed.freezesUsedThisMonth());
        assertEquals(StreakStatus.FREEZE_PROTECTED, afterTwoMissed.streakStatus());

        // Miss another full day (advance 2 days from last covered day) with 0 freezes remaining -> shame-free reset
        clock.advanceDays(2);
        GamificationSummaryView resetSummary = service.getSummary(USER_ID, "UTC");
        assertEquals(0, resetSummary.currentStreakDays());
        assertEquals(3, resetSummary.longestStreakDays());
        assertEquals(StreakStatus.FRESH_START_READY, resetSummary.streakStatus());
    }

    @Test
    void newCalendarMonthReplenishesMonthlyFreezeQuotaToTwo() {
        clock.setInstant(Instant.parse("2026-10-20T12:00:00Z"));
        service.recordActivity(USER_ID, independentCmd("oct-20", "twin:oct20"));
        clock.setInstant(Instant.parse("2026-10-23T12:00:00Z")); // misses Oct 21 & 22 -> uses both freezes
        GamificationSummaryView oct23 = service.recordActivity(USER_ID, independentCmd("oct-23", "twin:oct23"));
        assertEquals(0, oct23.streakFreezesRemaining());
        assertEquals(2, oct23.freezesUsedThisMonth());
        assertEquals("2026-10", oct23.freezeQuotaMonth());

        // Keep daily activity through Oct 31, then check Nov 01 rollover
        for (int day = 24; day <= 31; day++) {
            clock.setInstant(Instant.parse(String.format("2026-10-%02dT12:00:00Z", day)));
            service.recordActivity(USER_ID, independentCmd("oct-" + day, "twin:oct" + day));
        }

        clock.setInstant(Instant.parse("2026-11-01T12:00:00Z"));
        GamificationSummaryView nov01 = service.getSummary(USER_ID, "UTC");
        assertEquals("2026-11", nov01.freezeQuotaMonth());
        assertEquals(2, nov01.streakFreezesRemaining());
        assertEquals(0, nov01.freezesUsedThisMonth());
        assertEquals(10, nov01.currentStreakDays());
    }

    @Test
    void pedagogicalMilestonesUnlockDeterministicallyAndIdempotentlyWithEvidenceReference() {
        for (int i = 0; i < 6; i++) {
            clock.setInstant(Instant.parse(String.format("2026-11-%02dT12:00:00Z", 10 + i)));
            service.recordActivity(USER_ID, independentCmd("streak-" + i, "twin:s" + i));
        }
        clock.setInstant(Instant.parse("2026-11-16T12:00:00Z"));
        GamificationSummaryView day7 = service.recordActivity(
                USER_ID,
                new GamificationActivityCommand(
                        "streak-7-all",
                        GamificationEvidenceType.INDEPENDENT,
                        "twin:final-milestone",
                        "Europe/Istanbul",
                        clock.instant(),
                        "algebra.quadratics",
                        true,
                        true,
                        5
                )
        );

        assertEquals(6, day7.achievements().size());
        assertTrue(day7.achievements().stream().allMatch(a -> a.unlocked() && "twin:s0".equals(a.evidenceReference()) || a.evidenceReference() != null));
        assertTrue(day7.newlyUnlockedBadgeCodes().contains(PedagogicalBadgeCode.SEVEN_DAY_INDEPENDENT_STREAK));
        assertTrue(day7.newlyUnlockedBadgeCodes().contains(PedagogicalBadgeCode.MISCONCEPTION_OVERCOME));
        assertTrue(day7.newlyUnlockedBadgeCodes().contains(PedagogicalBadgeCode.RETENTION_GUARDIAN));
        assertTrue(day7.newlyUnlockedBadgeCodes().contains(PedagogicalBadgeCode.CONCEPT_ARCHITECT));

        // Second submission on same day does not duplicate or re-emit unlocked badges
        GamificationSummaryView replay = service.recordActivity(
                USER_ID,
                new GamificationActivityCommand(
                        "streak-7-replay",
                        GamificationEvidenceType.INDEPENDENT,
                        "twin:replay",
                        "Europe/Istanbul",
                        clock.instant(),
                        "algebra.quadratics",
                        true,
                        true,
                        5
                )
        );
        assertTrue(replay.newlyUnlockedBadgeCodes().isEmpty());
    }

    @Test
    void invalidTimezoneAndFutureActivityWindowAreRejectedDeterministically() {
        GamificationTimezoneInvalidException tzEx = assertThrows(
                GamificationTimezoneInvalidException.class,
                () -> service.getSummary(USER_ID, "Mars/Olympus_Mons")
        );
        assertEquals(ApiErrorCode.GAMIFICATION_TIMEZONE_INVALID, tzEx.code());

        GamificationActivityCommand futureCmd = new GamificationActivityCommand(
                "future-idem-1234",
                GamificationEvidenceType.INDEPENDENT,
                "twin:future",
                "UTC",
                clock.instant().plusSeconds(3600),
                null,
                false,
                false,
                0
        );
        GamificationActivityWindowInvalidException windowEx = assertThrows(
                GamificationActivityWindowInvalidException.class,
                () -> service.recordActivity(USER_ID, futureCmd)
        );
        assertEquals(ApiErrorCode.GAMIFICATION_ACTIVITY_WINDOW_INVALID, windowEx.code());
    }

    @Test
    void featureFlagDisabledThrowsServiceUnavailable() {
        GamificationPolicyService disabledService = new GamificationPolicyService(repository, meterRegistry, clock, false);
        GamificationFeatureDisabledException summaryEx = assertThrows(
                GamificationFeatureDisabledException.class,
                () -> disabledService.getSummary(USER_ID, "UTC")
        );
        assertEquals(ApiErrorCode.GAMIFICATION_FEATURE_DISABLED, summaryEx.code());

        GamificationFeatureDisabledException recordEx = assertThrows(
                GamificationFeatureDisabledException.class,
                () -> disabledService.recordActivity(USER_ID, independentCmd("disabled-k", "twin:disabled"))
        );
        assertEquals(ApiErrorCode.GAMIFICATION_FEATURE_DISABLED, recordEx.code());
    }

    private GamificationActivityCommand independentCmd(String idempotencyKey, String evidenceRef) {
        return command(idempotencyKey, GamificationEvidenceType.INDEPENDENT, evidenceRef);
    }

    private GamificationActivityCommand command(
            String idempotencyKey,
            GamificationEvidenceType evidenceType,
            String evidenceRef
    ) {
        return new GamificationActivityCommand(
                idempotencyKey,
                evidenceType,
                evidenceRef,
                "UTC",
                clock.instant(),
                "algebra.linear",
                false,
                false,
                0
        );
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        MutableClock(Instant initial) {
            this.current = initial;
        }

        void advanceSeconds(long seconds) {
            this.current = this.current.plusSeconds(seconds);
        }

        void advanceDays(long days) {
            this.current = this.current.plusSeconds(days * 86_400L);
        }

        void setInstant(Instant instant) {
            this.current = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }

    private static final class InMemoryGamificationRepository extends JdbcGamificationRepository {
        private final Map<UUID, LearnerStreakRecord> streaks = new HashMap<>();
        private final Map<String, DailyStreakLedgerRecord> ledgers = new HashMap<>();
        private final Map<String, LearnerAchievementRecord> achievements = new HashMap<>();

        InMemoryGamificationRepository() {
            super(mock(NamedParameterJdbcTemplate.class));
        }

        @Override
        public Optional<LearnerStreakRecord> findStreakByUserIdForUpdate(UUID userId) {
            return Optional.ofNullable(streaks.get(userId));
        }

        @Override
        public Optional<LearnerStreakRecord> findStreakByUserId(UUID userId) {
            return Optional.ofNullable(streaks.get(userId));
        }

        @Override
        public void upsertStreak(LearnerStreakRecord record) {
            streaks.put(record.userId(), record);
        }

        @Override
        public Optional<DailyStreakLedgerRecord> findDailyLedgerByUserAndDate(UUID userId, LocalDate date) {
            return Optional.ofNullable(ledgers.get(userId + ":" + date));
        }

        @Override
        public List<DailyStreakLedgerRecord> findRecentDailyLedger(UUID userId, int limit) {
            return ledgers.values().stream()
                    .filter(entry -> entry.userId().equals(userId))
                    .sorted(Comparator.comparing(DailyStreakLedgerRecord::activityLocalDate).reversed())
                    .limit(limit)
                    .toList();
        }

        @Override
        public void upsertDailyLedger(DailyStreakLedgerRecord entry) {
            ledgers.put(entry.userId() + ":" + entry.activityLocalDate(), entry);
        }

        @Override
        public List<LearnerAchievementRecord> findAchievementsByUserId(UUID userId) {
            List<LearnerAchievementRecord> list = new ArrayList<>();
            for (LearnerAchievementRecord rec : achievements.values()) {
                if (rec.userId().equals(userId)) {
                    list.add(rec);
                }
            }
            return list;
        }

        @Override
        public boolean insertAchievementIfAbsent(LearnerAchievementRecord achievement) {
            String key = achievement.userId() + ":" + achievement.badgeCode().name();
            return achievements.putIfAbsent(key, achievement) == null;
        }

        @Override
        public long countStreakByUserId(UUID userId) {
            return streaks.containsKey(userId) ? 1L : 0L;
        }

        @Override
        public long countLedgerByUserId(UUID userId) {
            return ledgers.values().stream().filter(e -> e.userId().equals(userId)).count();
        }

        @Override
        public long countAchievementsByUserId(UUID userId) {
            return findAchievementsByUserId(userId).size();
        }

        @Override
        public void deleteAllByUserId(UUID userId) {
            streaks.remove(userId);
            ledgers.entrySet().removeIf(e -> e.getValue().userId().equals(userId));
            achievements.entrySet().removeIf(e -> e.getValue().userId().equals(userId));
        }
    }
}
