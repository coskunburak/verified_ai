package com.verifiedai.studyplan.gamification;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Immutable domain models for Sprint 8.8 streak evaluation and pedagogical achievements.
 */
public final class GamificationModels {

    private GamificationModels() {
    }

    public record LearnerStreakRecord(
            UUID userId,
            int currentStreakDays,
            int longestStreakDays,
            int streakFreezesRemaining,
            int freezesUsedThisMonth,
            String freezeQuotaMonth,
            String learnerTimezone,
            LocalDate lastVerifiedIndependentDate,
            LocalDate lastActivityLocalDate,
            GamificationEvidenceType lastActivityEvidenceType,
            StreakStatus streakStatus,
            int totalIndependentDays,
            int totalAssistedContinuityDays,
            boolean trainingEligible,
            Instant createdAt,
            Instant updatedAt
    ) {
        public LearnerStreakRecord {
            if (trainingEligible) {
                throw new IllegalArgumentException("LearnerStreakRecord must enforce trainingEligible=false");
            }
        }
    }

    public record DailyStreakLedgerRecord(
            UUID ledgerId,
            UUID userId,
            LocalDate activityLocalDate,
            String learnerTimezone,
            DailyStreakStatus dailyStatus,
            String highestEvidenceType,
            int independentActivityCount,
            int assistedActivityCount,
            boolean streakIncremented,
            boolean freezeConsumed,
            int streakDaysSnapshot,
            String evidenceReference,
            String lastIdempotencyKey,
            boolean trainingEligible,
            Instant createdAt,
            Instant updatedAt
    ) {
        public DailyStreakLedgerRecord {
            if (trainingEligible) {
                throw new IllegalArgumentException("DailyStreakLedgerRecord must enforce trainingEligible=false");
            }
            if (streakIncremented && freezeConsumed) {
                throw new IllegalArgumentException("DailyStreakLedgerRecord cannot both increment streak and consume freeze");
            }
        }
    }

    public record LearnerAchievementRecord(
            UUID achievementId,
            UUID userId,
            PedagogicalBadgeCode badgeCode,
            PedagogicalBadgeCategory badgeCategory,
            String evidenceReference,
            LocalDate unlockedLocalDate,
            Instant unlockedAt,
            boolean trainingEligible
    ) {
        public LearnerAchievementRecord {
            if (trainingEligible) {
                throw new IllegalArgumentException("LearnerAchievementRecord must enforce trainingEligible=false");
            }
        }
    }

    public record GamificationActivityCommand(
            String idempotencyKey,
            GamificationEvidenceType evidenceType,
            String evidenceReference,
            String learnerTimezone,
            Instant occurredAtUtc,
            String conceptId,
            boolean misconceptionRepaired,
            boolean spacedReviewCompleted,
            int verifiedMasteredConceptCount
    ) {
    }

    public record PedagogicalAchievementView(
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
    }

    public record GamificationSummaryView(
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
            GamificationEvidenceType lastActivityEvidenceType,
            int totalIndependentDays,
            int totalAssistedContinuityDays,
            String supportiveHeadlineKey,
            String supportiveBodyKey,
            List<DailyStreakLedgerRecord> recentDailyLedger,
            List<PedagogicalAchievementView> achievements,
            List<PedagogicalBadgeCode> newlyUnlockedBadgeCodes,
            boolean trainingEligible,
            Instant evaluatedAtUtc
    ) {
        public GamificationSummaryView {
            if (trainingEligible) {
                throw new IllegalArgumentException("GamificationSummaryView must enforce trainingEligible=false");
            }
        }
    }
}
