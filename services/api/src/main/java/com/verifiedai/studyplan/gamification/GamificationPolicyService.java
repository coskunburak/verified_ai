package com.verifiedai.studyplan.gamification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verifiedai.studyplan.gamification.GamificationExceptions.GamificationActivityWindowInvalidException;
import com.verifiedai.studyplan.gamification.GamificationExceptions.GamificationFeatureDisabledException;
import com.verifiedai.studyplan.gamification.GamificationExceptions.GamificationTimezoneInvalidException;
import com.verifiedai.studyplan.gamification.GamificationModels.DailyStreakLedgerRecord;
import com.verifiedai.studyplan.gamification.GamificationModels.GamificationActivityCommand;
import com.verifiedai.studyplan.gamification.GamificationModels.GamificationSummaryView;
import com.verifiedai.studyplan.gamification.GamificationModels.LearnerAchievementRecord;
import com.verifiedai.studyplan.gamification.GamificationModels.LearnerStreakRecord;
import com.verifiedai.studyplan.gamification.GamificationModels.PedagogicalAchievementView;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure deterministic Sprint 8.8 policy engine for verified independent streaks,
 * shame-free assisted habit continuity, monthly rest freezes, and pedagogical milestones.
 */
@Service
public class GamificationPolicyService {

    public static final int MAX_MONTHLY_STREAK_FREEZES = 2;
    private static final int RECENT_LEDGER_LIMIT = 14;
    private static final long MAX_FUTURE_SKEW_SECONDS = 60L;
    private static final long MAX_PAST_WINDOW_DAYS = 365L;

    private final JdbcGamificationRepository repository;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    @SuppressWarnings("unused")
    private final ObjectMapper objectMapper;
    private final boolean gamificationEnabled;

    @Autowired
    public GamificationPolicyService(
            JdbcGamificationRepository repository,
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            ObjectProvider<Clock> clockProvider,
            ObjectProvider<ObjectMapper> objectMapperProvider,
            @Value("${verifiedai.studyplan.gamification-enabled:true}") boolean gamificationEnabled
    ) {
        this.repository = repository;
        this.meterRegistry = meterRegistryProvider.getIfAvailable(SimpleMeterRegistry::new);
        this.clock = clockProvider.getIfAvailable(Clock::systemUTC);
        this.objectMapper = objectMapperProvider.getIfAvailable(() -> new ObjectMapper().findAndRegisterModules());
        this.gamificationEnabled = gamificationEnabled;
    }

    public GamificationPolicyService(
            JdbcGamificationRepository repository,
            MeterRegistry meterRegistry,
            Clock clock,
            boolean gamificationEnabled
    ) {
        this.repository = repository;
        this.meterRegistry = meterRegistry != null ? meterRegistry : new SimpleMeterRegistry();
        this.clock = clock != null ? clock : Clock.systemUTC();
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
        this.gamificationEnabled = gamificationEnabled;
    }

    @Transactional
    public GamificationSummaryView getSummary(UUID userId, String rawTimezone) {
        requireEnabled();
        ZoneId zoneId = resolveZoneId(rawTimezone);
        Instant now = clock.instant();
        LocalDate evaluationLocalDate = LocalDate.ofInstant(now, zoneId);

        Optional<LearnerStreakRecord> existingOpt = repository.findStreakByUserIdForUpdate(userId);
        if (existingOpt.isEmpty()) {
            List<LearnerAchievementRecord> existingAchievements = repository.findAchievementsByUserId(userId);
            return buildSummaryView(
                    userId,
                    zoneId.getId(),
                    evaluationLocalDate,
                    new LearnerStreakRecord(
                            userId,
                            0,
                            0,
                            MAX_MONTHLY_STREAK_FREEZES,
                            0,
                            YearMonth.from(evaluationLocalDate).toString(),
                            zoneId.getId(),
                            null,
                            null,
                            null,
                            StreakStatus.FRESH_START_READY,
                            0,
                            0,
                            false,
                            now,
                            now
                    ),
                    false,
                    false,
                    repository.findRecentDailyLedger(userId, RECENT_LEDGER_LIMIT),
                    existingAchievements,
                    List.of(),
                    now
            );
        }

        GapEvaluationResult gapResult = evaluateRolloverAndGaps(
                userId,
                existingOpt.get(),
                evaluationLocalDate,
                zoneId.getId(),
                now
        );

        LearnerStreakRecord updatedStreak = gapResult.streak();
        List<DailyStreakLedgerRecord> recentLedger = repository.findRecentDailyLedger(userId, RECENT_LEDGER_LIMIT);
        StreakStatus resolvedStatus = resolveStreakStatusForDate(
                updatedStreak,
                recentLedger,
                evaluationLocalDate,
                gapResult.freezeApplied()
        );
        if (gapResult.mutated() || updatedStreak.streakStatus() != resolvedStatus || !zoneId.getId().equals(updatedStreak.learnerTimezone())) {
            updatedStreak = withStatusAndTimezone(updatedStreak, resolvedStatus, zoneId.getId(), now);
            repository.upsertStreak(updatedStreak);
        }

        return buildSummaryView(
                userId,
                zoneId.getId(),
                evaluationLocalDate,
                updatedStreak,
                gapResult.freezeApplied(),
                false,
                recentLedger,
                repository.findAchievementsByUserId(userId),
                List.of(),
                now
        );
    }

    @Transactional
    public GamificationSummaryView recordActivity(UUID userId, GamificationActivityCommand command) {
        requireEnabled();
        ZoneId zoneId = resolveZoneId(command.learnerTimezone());
        if (command.evidenceType() == null || command.evidenceType() == GamificationEvidenceType.UNSUPPORTED) {
            throw new GamificationExceptions.GamificationEvidenceUnsupportedException(
                    "Unsupported gamification evidence type."
            );
        }

        Instant now = clock.instant();
        Instant occurredAt = command.occurredAtUtc() != null ? command.occurredAtUtc() : now;
        validateActivityWindow(occurredAt, now);

        LocalDate activityLocalDate = LocalDate.ofInstant(occurredAt, zoneId);
        String currentYearMonth = YearMonth.from(activityLocalDate).toString();

        LearnerStreakRecord streak = repository.findStreakByUserIdForUpdate(userId)
                .orElseGet(() -> new LearnerStreakRecord(
                        userId,
                        0,
                        0,
                        MAX_MONTHLY_STREAK_FREEZES,
                        0,
                        currentYearMonth,
                        zoneId.getId(),
                        null,
                        null,
                        null,
                        StreakStatus.FRESH_START_READY,
                        0,
                        0,
                        false,
                        now,
                        now
                ));

        GapEvaluationResult gapResult = evaluateRolloverAndGaps(
                userId,
                streak,
                activityLocalDate,
                zoneId.getId(),
                now
        );
        streak = gapResult.streak();

        int currentStreakDays = streak.currentStreakDays();
        int longestStreakDays = streak.longestStreakDays();
        int totalIndependentDays = streak.totalIndependentDays();
        int totalAssistedContinuityDays = streak.totalAssistedContinuityDays();
        LocalDate lastVerifiedIndependentDate = streak.lastVerifiedIndependentDate();
        LocalDate lastActivityLocalDate = streak.lastActivityLocalDate();
        GamificationEvidenceType lastActivityEvidenceType = streak.lastActivityEvidenceType();
        StreakStatus nextStreakStatus = streak.streakStatus();
        boolean streakIncrementedOnEvaluation = false;

        Optional<DailyStreakLedgerRecord> existingLedgerOpt = repository.findDailyLedgerByUserAndDate(userId, activityLocalDate);

        if (command.evidenceType().countsForIndependentStreak()) {
            if (existingLedgerOpt.isEmpty()) {
                currentStreakDays += 1;
                longestStreakDays = Math.max(longestStreakDays, currentStreakDays);
                totalIndependentDays += 1;
                streakIncrementedOnEvaluation = true;

                repository.upsertDailyLedger(new DailyStreakLedgerRecord(
                        UUID.randomUUID(),
                        userId,
                        activityLocalDate,
                        zoneId.getId(),
                        DailyStreakStatus.VERIFIED_INDEPENDENT,
                        GamificationEvidenceType.INDEPENDENT.name(),
                        1,
                        0,
                        true,
                        false,
                        currentStreakDays,
                        command.evidenceReference(),
                        command.idempotencyKey(),
                        false,
                        now,
                        now
                ));
                meterRegistry.counter("gamification.streak.incremented.total").increment();
            } else {
                DailyStreakLedgerRecord existingLedger = existingLedgerOpt.get();
                if (existingLedger.dailyStatus() == DailyStreakStatus.ASSISTED_CONTINUITY) {
                    currentStreakDays += 1;
                    longestStreakDays = Math.max(longestStreakDays, currentStreakDays);
                    totalIndependentDays += 1;
                    streakIncrementedOnEvaluation = true;

                    repository.upsertDailyLedger(new DailyStreakLedgerRecord(
                            existingLedger.ledgerId(),
                            userId,
                            activityLocalDate,
                            zoneId.getId(),
                            DailyStreakStatus.VERIFIED_INDEPENDENT,
                            GamificationEvidenceType.INDEPENDENT.name(),
                            1,
                            existingLedger.assistedActivityCount(),
                            true,
                            false,
                            currentStreakDays,
                            command.evidenceReference(),
                            command.idempotencyKey(),
                            false,
                            existingLedger.createdAt(),
                            now
                    ));
                    meterRegistry.counter("gamification.streak.incremented.total").increment();
                } else if (existingLedger.streakIncremented() || existingLedger.dailyStatus() == DailyStreakStatus.VERIFIED_INDEPENDENT) {
                    boolean differentIdempotencyKey = !existingLedger.lastIdempotencyKey().equals(command.idempotencyKey());
                    if (differentIdempotencyKey) {
                        repository.upsertDailyLedger(new DailyStreakLedgerRecord(
                                existingLedger.ledgerId(),
                                userId,
                                activityLocalDate,
                                zoneId.getId(),
                                DailyStreakStatus.VERIFIED_INDEPENDENT,
                                GamificationEvidenceType.INDEPENDENT.name(),
                                existingLedger.independentActivityCount() + 1,
                                existingLedger.assistedActivityCount(),
                                existingLedger.streakIncremented(),
                                false,
                                currentStreakDays,
                                command.evidenceReference(),
                                command.idempotencyKey(),
                                false,
                                existingLedger.createdAt(),
                                now
                        ));
                    }
                } else if (existingLedger.dailyStatus() == DailyStreakStatus.FREEZE_APPLIED) {
                    currentStreakDays += 1;
                    longestStreakDays = Math.max(longestStreakDays, currentStreakDays);
                    totalIndependentDays += 1;
                    streakIncrementedOnEvaluation = true;

                    repository.upsertDailyLedger(new DailyStreakLedgerRecord(
                            existingLedger.ledgerId(),
                            userId,
                            activityLocalDate,
                            zoneId.getId(),
                            DailyStreakStatus.VERIFIED_INDEPENDENT,
                            GamificationEvidenceType.INDEPENDENT.name(),
                            1,
                            0,
                            true,
                            false,
                            currentStreakDays,
                            command.evidenceReference(),
                            command.idempotencyKey(),
                            false,
                            existingLedger.createdAt(),
                            now
                    ));
                    meterRegistry.counter("gamification.streak.incremented.total").increment();
                }
            }

            if (lastVerifiedIndependentDate == null || activityLocalDate.isAfter(lastVerifiedIndependentDate)) {
                lastVerifiedIndependentDate = activityLocalDate;
            }
            if (lastActivityLocalDate == null || !activityLocalDate.isBefore(lastActivityLocalDate)) {
                lastActivityLocalDate = activityLocalDate;
                lastActivityEvidenceType = GamificationEvidenceType.INDEPENDENT;
            }
            nextStreakStatus = StreakStatus.ACTIVE_VERIFIED_TODAY;
        } else {
            meterRegistry.counter("gamification.streak.assisted_continuity.total").increment();
            if (existingLedgerOpt.isEmpty()) {
                totalAssistedContinuityDays += 1;
                repository.upsertDailyLedger(new DailyStreakLedgerRecord(
                        UUID.randomUUID(),
                        userId,
                        activityLocalDate,
                        zoneId.getId(),
                        DailyStreakStatus.ASSISTED_CONTINUITY,
                        command.evidenceType().name(),
                        0,
                        1,
                        false,
                        false,
                        currentStreakDays,
                        command.evidenceReference(),
                        command.idempotencyKey(),
                        false,
                        now,
                        now
                ));
                if (lastActivityLocalDate == null || !activityLocalDate.isBefore(lastActivityLocalDate)) {
                    lastActivityLocalDate = activityLocalDate;
                    lastActivityEvidenceType = command.evidenceType();
                }
                nextStreakStatus = StreakStatus.CONTINUITY_PRESERVED_TODAY;
            } else {
                DailyStreakLedgerRecord existingLedger = existingLedgerOpt.get();
                boolean differentIdempotencyKey = !existingLedger.lastIdempotencyKey().equals(command.idempotencyKey());
                DailyStreakStatus preservedDailyStatus = existingLedger.dailyStatus() == DailyStreakStatus.VERIFIED_INDEPENDENT
                        ? DailyStreakStatus.VERIFIED_INDEPENDENT
                        : DailyStreakStatus.ASSISTED_CONTINUITY;
                String preservedHighestEvidence = existingLedger.dailyStatus() == DailyStreakStatus.VERIFIED_INDEPENDENT
                        ? GamificationEvidenceType.INDEPENDENT.name()
                        : existingLedger.highestEvidenceType();
                if (differentIdempotencyKey) {
                    repository.upsertDailyLedger(new DailyStreakLedgerRecord(
                            existingLedger.ledgerId(),
                            userId,
                            activityLocalDate,
                            zoneId.getId(),
                            preservedDailyStatus,
                            preservedHighestEvidence,
                            existingLedger.independentActivityCount(),
                            existingLedger.assistedActivityCount() + 1,
                            existingLedger.streakIncremented(),
                            false,
                            currentStreakDays,
                            existingLedger.evidenceReference(),
                            command.idempotencyKey(),
                            false,
                            existingLedger.createdAt(),
                            now
                    ));
                }
                if (lastActivityLocalDate == null || !activityLocalDate.isBefore(lastActivityLocalDate)) {
                    lastActivityLocalDate = activityLocalDate;
                    lastActivityEvidenceType = preservedDailyStatus == DailyStreakStatus.VERIFIED_INDEPENDENT
                            ? GamificationEvidenceType.INDEPENDENT
                            : command.evidenceType();
                }
                nextStreakStatus = preservedDailyStatus == DailyStreakStatus.VERIFIED_INDEPENDENT
                        ? StreakStatus.ACTIVE_VERIFIED_TODAY
                        : StreakStatus.CONTINUITY_PRESERVED_TODAY;
            }
        }

        LearnerStreakRecord persistedStreak = new LearnerStreakRecord(
                userId,
                currentStreakDays,
                longestStreakDays,
                streak.streakFreezesRemaining(),
                streak.freezesUsedThisMonth(),
                streak.freezeQuotaMonth(),
                zoneId.getId(),
                lastVerifiedIndependentDate,
                lastActivityLocalDate,
                lastActivityEvidenceType,
                nextStreakStatus,
                totalIndependentDays,
                totalAssistedContinuityDays,
                false,
                streak.createdAt(),
                now
        );
        repository.upsertStreak(persistedStreak);

        List<PedagogicalBadgeCode> newlyUnlocked = evaluateAndUnlockAchievements(
                userId,
                command,
                persistedStreak,
                activityLocalDate,
                now
        );

        List<DailyStreakLedgerRecord> recentLedger = repository.findRecentDailyLedger(userId, RECENT_LEDGER_LIMIT);
        List<LearnerAchievementRecord> allAchievements = repository.findAchievementsByUserId(userId);
        return buildSummaryView(
                userId,
                zoneId.getId(),
                activityLocalDate,
                persistedStreak,
                gapResult.freezeApplied(),
                streakIncrementedOnEvaluation,
                recentLedger,
                allAchievements,
                newlyUnlocked,
                now
        );
    }

    private GapEvaluationResult evaluateRolloverAndGaps(
            UUID userId,
            LearnerStreakRecord streak,
            LocalDate evaluationLocalDate,
            String timezoneId,
            Instant now
    ) {
        int freezesRemaining = streak.streakFreezesRemaining();
        int freezesUsed = streak.freezesUsedThisMonth();
        String quotaMonth = streak.freezeQuotaMonth();
        int currentStreakDays = streak.currentStreakDays();
        LocalDate lastActivityLocalDate = streak.lastActivityLocalDate();
        StreakStatus status = streak.streakStatus();
        boolean mutated = false;
        boolean freezeApplied = false;

        YearMonth evaluationYm = YearMonth.from(evaluationLocalDate);
        String evaluationMonthStr = evaluationYm.toString();
        if (quotaMonth == null || quotaMonth.isBlank()) {
            quotaMonth = evaluationMonthStr;
            freezesRemaining = MAX_MONTHLY_STREAK_FREEZES;
            freezesUsed = 0;
            mutated = true;
        } else {
            YearMonth storedYm = YearMonth.parse(quotaMonth);
            if (evaluationYm.isAfter(storedYm)) {
                quotaMonth = evaluationMonthStr;
                freezesRemaining = MAX_MONTHLY_STREAK_FREEZES;
                freezesUsed = 0;
                mutated = true;
            }
        }

        if (lastActivityLocalDate != null && evaluationLocalDate.isAfter(lastActivityLocalDate.plusDays(1))) {
            int missedDays = (int) ChronoUnit.DAYS.between(lastActivityLocalDate, evaluationLocalDate) - 1;
            if (currentStreakDays > 0 && missedDays > 0) {
                if (missedDays <= freezesRemaining) {
                    LocalDate coverEnd = evaluationLocalDate.minusDays(1);
                    for (LocalDate missedDate = lastActivityLocalDate.plusDays(1);
                         !missedDate.isAfter(coverEnd);
                         missedDate = missedDate.plusDays(1)) {
                        if (repository.findDailyLedgerByUserAndDate(userId, missedDate).isEmpty()) {
                            freezesRemaining -= 1;
                            freezesUsed += 1;
                            repository.upsertDailyLedger(new DailyStreakLedgerRecord(
                                    UUID.randomUUID(),
                                    userId,
                                    missedDate,
                                    timezoneId,
                                    DailyStreakStatus.FREEZE_APPLIED,
                                    "FREEZE_SYSTEM",
                                    0,
                                    0,
                                    false,
                                    true,
                                    currentStreakDays,
                                    "system:auto_freeze:" + missedDate,
                                    "freeze:" + userId + ":" + missedDate,
                                    false,
                                    now,
                                    now
                            ));
                            meterRegistry.counter("gamification.streak.freeze.applied.total").increment();
                            freezeApplied = true;
                        }
                    }
                    lastActivityLocalDate = coverEnd;
                    status = StreakStatus.FREEZE_PROTECTED;
                    mutated = true;
                } else {
                    currentStreakDays = 0;
                    status = StreakStatus.FRESH_START_READY;
                    mutated = true;
                }
            }
        }

        LearnerStreakRecord updated = new LearnerStreakRecord(
                streak.userId(),
                currentStreakDays,
                streak.longestStreakDays(),
                freezesRemaining,
                freezesUsed,
                quotaMonth,
                timezoneId,
                streak.lastVerifiedIndependentDate(),
                lastActivityLocalDate,
                streak.lastActivityEvidenceType(),
                status,
                streak.totalIndependentDays(),
                streak.totalAssistedContinuityDays(),
                false,
                streak.createdAt(),
                now
        );
        return new GapEvaluationResult(updated, mutated, freezeApplied);
    }

    private List<PedagogicalBadgeCode> evaluateAndUnlockAchievements(
            UUID userId,
            GamificationActivityCommand command,
            LearnerStreakRecord streak,
            LocalDate unlockedLocalDate,
            Instant now
    ) {
        List<PedagogicalBadgeCode> candidates = new ArrayList<>();
        if (command.evidenceType() == GamificationEvidenceType.INDEPENDENT || streak.totalIndependentDays() >= 1) {
            candidates.add(PedagogicalBadgeCode.FIRST_INDEPENDENT_TWIN);
        }
        if (streak.longestStreakDays() >= 3) {
            candidates.add(PedagogicalBadgeCode.THREE_DAY_INDEPENDENT_STREAK);
        }
        if (streak.longestStreakDays() >= 7) {
            candidates.add(PedagogicalBadgeCode.SEVEN_DAY_INDEPENDENT_STREAK);
        }
        if (command.evidenceType() == GamificationEvidenceType.INDEPENDENT && command.misconceptionRepaired()) {
            candidates.add(PedagogicalBadgeCode.MISCONCEPTION_OVERCOME);
        }
        if (command.evidenceType() == GamificationEvidenceType.INDEPENDENT && command.spacedReviewCompleted()) {
            candidates.add(PedagogicalBadgeCode.RETENTION_GUARDIAN);
        }
        if (command.verifiedMasteredConceptCount() >= 5) {
            candidates.add(PedagogicalBadgeCode.CONCEPT_ARCHITECT);
        }

        List<PedagogicalBadgeCode> newlyUnlocked = new ArrayList<>();
        for (PedagogicalBadgeCode badge : candidates) {
            UUID deterministicAchievementId = UUID.nameUUIDFromBytes(
                    ("achievement:" + userId + ":" + badge.name()).getBytes(StandardCharsets.UTF_8)
            );
            boolean inserted = repository.insertAchievementIfAbsent(new LearnerAchievementRecord(
                    deterministicAchievementId,
                    userId,
                    badge,
                    badge.badgeCategory(),
                    command.evidenceReference(),
                    unlockedLocalDate,
                    now,
                    false
            ));
            if (inserted) {
                newlyUnlocked.add(badge);
                meterRegistry.counter("gamification.achievement.unlocked.total", "badge_code", badge.name()).increment();
            }
        }
        return List.copyOf(newlyUnlocked);
    }

    private GamificationSummaryView buildSummaryView(
            UUID userId,
            String learnerTimezone,
            LocalDate serverLocalDate,
            LearnerStreakRecord streak,
            boolean freezeAppliedOnEvaluation,
            boolean streakIncrementedOnEvaluation,
            List<DailyStreakLedgerRecord> recentLedger,
            List<LearnerAchievementRecord> achievementRecords,
            List<PedagogicalBadgeCode> newlyUnlocked,
            Instant evaluatedAtUtc
    ) {
        Optional<DailyStreakLedgerRecord> todayLedger = recentLedger.stream()
                .filter(entry -> serverLocalDate.equals(entry.activityLocalDate()))
                .findFirst();

        boolean verifiedToday = todayLedger
                .map(entry -> entry.dailyStatus() == DailyStreakStatus.VERIFIED_INDEPENDENT)
                .orElse(false);
        boolean continuityPreservedToday = todayLedger
                .map(entry -> entry.dailyStatus() == DailyStreakStatus.VERIFIED_INDEPENDENT
                        || entry.dailyStatus() == DailyStreakStatus.ASSISTED_CONTINUITY)
                .orElse(false);

        StreakStatus effectiveStatus = resolveStreakStatusForDate(
                streak,
                recentLedger,
                serverLocalDate,
                freezeAppliedOnEvaluation
        );

        Map<PedagogicalBadgeCode, LearnerAchievementRecord> unlockedByCode = new EnumMap<>(PedagogicalBadgeCode.class);
        for (LearnerAchievementRecord record : achievementRecords) {
            unlockedByCode.put(record.badgeCode(), record);
        }

        List<PedagogicalAchievementView> fullCatalogue = new ArrayList<>(PedagogicalBadgeCode.values().length);
        for (PedagogicalBadgeCode badge : PedagogicalBadgeCode.values()) {
            LearnerAchievementRecord unlockedRecord = unlockedByCode.get(badge);
            if (unlockedRecord != null) {
                fullCatalogue.add(new PedagogicalAchievementView(
                        badge,
                        badge.badgeCategory(),
                        badge.titleKey(),
                        badge.descriptionKey(),
                        true,
                        unlockedRecord.achievementId(),
                        unlockedRecord.evidenceReference(),
                        unlockedRecord.unlockedLocalDate(),
                        unlockedRecord.unlockedAt()
                ));
            } else {
                fullCatalogue.add(new PedagogicalAchievementView(
                        badge,
                        badge.badgeCategory(),
                        badge.titleKey(),
                        badge.descriptionKey(),
                        false,
                        null,
                        null,
                        null,
                        null
                ));
            }
        }

        return new GamificationSummaryView(
                userId,
                learnerTimezone,
                serverLocalDate,
                streak.currentStreakDays(),
                streak.longestStreakDays(),
                streak.streakFreezesRemaining(),
                MAX_MONTHLY_STREAK_FREEZES,
                streak.freezesUsedThisMonth(),
                streak.freezeQuotaMonth(),
                effectiveStatus,
                verifiedToday,
                continuityPreservedToday,
                freezeAppliedOnEvaluation,
                streakIncrementedOnEvaluation,
                streak.lastVerifiedIndependentDate(),
                streak.lastActivityLocalDate(),
                streak.lastActivityEvidenceType(),
                streak.totalIndependentDays(),
                streak.totalAssistedContinuityDays(),
                effectiveStatus.supportiveHeadlineKey(),
                effectiveStatus.supportiveBodyKey(),
                recentLedger,
                List.copyOf(fullCatalogue),
                newlyUnlocked,
                false,
                evaluatedAtUtc
        );
    }

    private static StreakStatus resolveStreakStatusForDate(
            LearnerStreakRecord streak,
            List<DailyStreakLedgerRecord> recentLedger,
            LocalDate evaluationDate,
            boolean freezeAppliedOnEvaluation
    ) {
        Optional<DailyStreakLedgerRecord> todayLedger = recentLedger.stream()
                .filter(entry -> evaluationDate.equals(entry.activityLocalDate()))
                .findFirst();
        if (todayLedger.isPresent()) {
            DailyStreakStatus todayStatus = todayLedger.get().dailyStatus();
            if (todayStatus == DailyStreakStatus.VERIFIED_INDEPENDENT) {
                return StreakStatus.ACTIVE_VERIFIED_TODAY;
            }
            if (todayStatus == DailyStreakStatus.ASSISTED_CONTINUITY) {
                return StreakStatus.CONTINUITY_PRESERVED_TODAY;
            }
        }
        if (streak.currentStreakDays() > 0) {
            if (freezeAppliedOnEvaluation) {
                return StreakStatus.FREEZE_PROTECTED;
            }
            LocalDate yesterday = evaluationDate.minusDays(1);
            boolean yesterdayFrozen = recentLedger.stream()
                    .anyMatch(entry -> yesterday.equals(entry.activityLocalDate())
                            && entry.dailyStatus() == DailyStreakStatus.FREEZE_APPLIED);
            if (yesterdayFrozen) {
                return StreakStatus.FREEZE_PROTECTED;
            }
            return StreakStatus.AT_RISK_TODAY;
        }
        return StreakStatus.FRESH_START_READY;
    }

    private static LearnerStreakRecord withStatusAndTimezone(
            LearnerStreakRecord source,
            StreakStatus status,
            String timezoneId,
            Instant now
    ) {
        return new LearnerStreakRecord(
                source.userId(),
                source.currentStreakDays(),
                source.longestStreakDays(),
                source.streakFreezesRemaining(),
                source.freezesUsedThisMonth(),
                source.freezeQuotaMonth(),
                timezoneId,
                source.lastVerifiedIndependentDate(),
                source.lastActivityLocalDate(),
                source.lastActivityEvidenceType(),
                status,
                source.totalIndependentDays(),
                source.totalAssistedContinuityDays(),
                false,
                source.createdAt(),
                now
        );
    }

    private void requireEnabled() {
        if (!gamificationEnabled) {
            throw new GamificationFeatureDisabledException(
                    "Gamification streak and achievement service is disabled by configuration."
            );
        }
    }

    private static ZoneId resolveZoneId(String rawTimezone) {
        if (rawTimezone == null || rawTimezone.isBlank() || rawTimezone.trim().length() > 64) {
            throw new GamificationTimezoneInvalidException("Learner timezone must be a valid non-empty IANA zone identifier.");
        }
        try {
            return ZoneId.of(rawTimezone.trim());
        } catch (DateTimeException ex) {
            throw new GamificationTimezoneInvalidException("Invalid learner timezone: " + rawTimezone);
        }
    }

    private static void validateActivityWindow(Instant occurredAt, Instant now) {
        if (occurredAt.isAfter(now.plusSeconds(MAX_FUTURE_SKEW_SECONDS))) {
            throw new GamificationActivityWindowInvalidException(
                    "Activity timestamp cannot exceed server clock skew window."
            );
        }
        if (occurredAt.isBefore(now.minus(MAX_PAST_WINDOW_DAYS, ChronoUnit.DAYS))) {
            throw new GamificationActivityWindowInvalidException(
                    "Activity timestamp falls outside the supported evaluation window."
            );
        }
    }

    private record GapEvaluationResult(
            LearnerStreakRecord streak,
            boolean mutated,
            boolean freezeApplied
    ) {
    }
}
