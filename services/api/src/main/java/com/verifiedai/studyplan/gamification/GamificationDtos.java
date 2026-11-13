package com.verifiedai.studyplan.gamification;

import com.verifiedai.studyplan.gamification.GamificationModels.DailyStreakLedgerRecord;
import com.verifiedai.studyplan.gamification.GamificationModels.GamificationSummaryView;
import com.verifiedai.studyplan.gamification.GamificationModels.PedagogicalAchievementView;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * OpenAPI 3.1 / JSON Schema aligned request and response DTOs for Sprint 8.8.
 */
public final class GamificationDtos {

    private GamificationDtos() {
    }

    public record GamificationActivityRecordRequestDto(
            @NotBlank @Size(min = 8, max = 128) String idempotencyKey,
            @NotBlank @Size(min = 3, max = 32) String evidenceType,
            @NotBlank @Size(min = 3, max = 160) String evidenceReference,
            @NotBlank @Size(min = 3, max = 64) String learnerTimezone,
            Instant occurredAtUtc,
            @Size(min = 2, max = 128) String conceptId,
            Boolean misconceptionRepaired,
            Boolean spacedReviewCompleted,
            @Min(0) @Max(10000) Integer verifiedMasteredConceptCount
    ) {
    }

    public record DailyStreakLedgerEntryDto(
            LocalDate activityLocalDate,
            DailyStreakStatus dailyStatus,
            String highestEvidenceType,
            int independentActivityCount,
            int assistedActivityCount,
            boolean streakIncremented,
            boolean freezeConsumed,
            int streakDaysSnapshot,
            String evidenceReference
    ) {
        public static DailyStreakLedgerEntryDto fromDomain(DailyStreakLedgerRecord record) {
            return new DailyStreakLedgerEntryDto(
                    record.activityLocalDate(),
                    record.dailyStatus(),
                    record.highestEvidenceType(),
                    record.independentActivityCount(),
                    record.assistedActivityCount(),
                    record.streakIncremented(),
                    record.freezeConsumed(),
                    record.streakDaysSnapshot(),
                    record.evidenceReference()
            );
        }
    }

    public record PedagogicalAchievementItemDto(
            PedagogicalBadgeCode badgeCode,
            PedagogicalBadgeCategory badgeCategory,
            String titleKey,
            String descriptionKey,
            boolean unlocked,
            UUID achievementId,
            String evidenceReference,
            LocalDate unlockedLocalDate,
            Instant unlockedAtUtc
    ) {
        public static PedagogicalAchievementItemDto fromDomain(PedagogicalAchievementView view) {
            return new PedagogicalAchievementItemDto(
                    view.badgeCode(),
                    view.badgeCategory(),
                    view.titleKey(),
                    view.descriptionKey(),
                    view.unlocked(),
                    view.achievementId(),
                    view.evidenceReference(),
                    view.unlockedLocalDate(),
                    view.unlockedAtUtc()
            );
        }
    }

    public record GamificationSummaryResponseDto(
            UUID userId,
            String learnerTimezone,
            LocalDate serverLocalDate,
            int currentStreakDays,
            int longestStreakDays,
            int streakFreezesRemaining,
            int maxMonthlyStreakFreezes,
            int freezesUsedThisMonth,
            String freezeQuotaMonth,
            StreakStatus streakStatus,
            boolean verifiedToday,
            boolean continuityPreservedToday,
            boolean freezeAppliedOnEvaluation,
            boolean streakIncrementedOnEvaluation,
            LocalDate lastVerifiedIndependentDate,
            LocalDate lastActivityLocalDate,
            String lastActivityEvidenceType,
            int totalIndependentDays,
            int totalAssistedContinuityDays,
            String supportiveHeadlineKey,
            String supportiveBodyKey,
            List<DailyStreakLedgerEntryDto> recentDailyLedger,
            List<PedagogicalAchievementItemDto> achievements,
            List<PedagogicalBadgeCode> newlyUnlockedBadgeCodes,
            boolean trainingEligible,
            Instant evaluatedAtUtc
    ) {
        public static GamificationSummaryResponseDto fromDomain(GamificationSummaryView view) {
            return new GamificationSummaryResponseDto(
                    view.userId(),
                    view.learnerTimezone(),
                    view.serverLocalDate(),
                    view.currentStreakDays(),
                    view.longestStreakDays(),
                    view.streakFreezesRemaining(),
                    view.maxMonthlyStreakFreezes(),
                    view.freezesUsedThisMonth(),
                    view.freezeQuotaMonth(),
                    view.streakStatus(),
                    view.verifiedToday(),
                    view.continuityPreservedToday(),
                    view.freezeAppliedOnEvaluation(),
                    view.streakIncrementedOnEvaluation(),
                    view.lastVerifiedIndependentDate(),
                    view.lastActivityLocalDate(),
                    view.lastActivityEvidenceType() != null ? view.lastActivityEvidenceType().name() : null,
                    view.totalIndependentDays(),
                    view.totalAssistedContinuityDays(),
                    view.supportiveHeadlineKey(),
                    view.supportiveBodyKey(),
                    view.recentDailyLedger().stream()
                            .map(DailyStreakLedgerEntryDto::fromDomain)
                            .toList(),
                    view.achievements().stream()
                            .map(PedagogicalAchievementItemDto::fromDomain)
                            .toList(),
                    List.copyOf(view.newlyUnlockedBadgeCodes()),
                    false,
                    view.evaluatedAtUtc()
            );
        }
    }
}
