package com.verifiedai.studyplan.privacy;

import com.verifiedai.sharedkernel.privacy.AccountDataLifecycleContributor;
import com.verifiedai.studyplan.gamification.GamificationModels.DailyStreakLedgerRecord;
import com.verifiedai.studyplan.gamification.GamificationModels.LearnerAchievementRecord;
import com.verifiedai.studyplan.gamification.GamificationModels.LearnerStreakRecord;
import com.verifiedai.studyplan.gamification.JdbcGamificationRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * GDPR / KVKK export and cascade purge contributor for Sprint 8.8 gamification tables.
 */
@Component
@Order(30)
public class GamificationPrivacyContributor implements AccountDataLifecycleContributor {

    public static final String CONTRIBUTOR_KEY = "studyplan.gamification";

    private final JdbcGamificationRepository repository;

    public GamificationPrivacyContributor(JdbcGamificationRepository repository) {
        this.repository = repository;
    }

    @Override
    public String category() {
        return CONTRIBUTOR_KEY;
    }

    public String contributorKey() {
        return CONTRIBUTOR_KEY;
    }

    @Override
    public Map<String, Object> exportUserData(UUID userId) {
        Optional<LearnerStreakRecord> streakOpt = repository.findStreakByUserId(userId);
        List<DailyStreakLedgerRecord> ledger = repository.findRecentDailyLedger(userId, 31);
        List<LearnerAchievementRecord> achievements = repository.findAchievementsByUserId(userId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contributor", CONTRIBUTOR_KEY);
        payload.put("trainingEligible", false);
        payload.put("ledgerCount", repository.countLedgerByUserId(userId));
        payload.put("achievementCount", repository.countAchievementsByUserId(userId));
        payload.put("unlockedBadgeCodes", achievements.stream().map(a -> a.badgeCode().name()).toList());

        if (streakOpt.isPresent()) {
            LearnerStreakRecord streak = streakOpt.get();
            Map<String, Object> streakSummary = new LinkedHashMap<>();
            streakSummary.put("currentStreakDays", streak.currentStreakDays());
            streakSummary.put("longestStreakDays", streak.longestStreakDays());
            streakSummary.put("streakFreezesRemaining", streak.streakFreezesRemaining());
            streakSummary.put("freezesUsedThisMonth", streak.freezesUsedThisMonth());
            streakSummary.put("freezeQuotaMonth", streak.freezeQuotaMonth());
            streakSummary.put("learnerTimezone", streak.learnerTimezone());
            streakSummary.put("streakStatus", streak.streakStatus().name());
            streakSummary.put("totalIndependentDays", streak.totalIndependentDays());
            streakSummary.put("totalAssistedContinuityDays", streak.totalAssistedContinuityDays());
            payload.put("streakSummary", streakSummary);
        } else {
            payload.put("streakSummary", null);
        }

        payload.put("recentDailyLedger", ledger.stream().map(entry -> Map.of(
                "activityLocalDate", entry.activityLocalDate().toString(),
                "dailyStatus", entry.dailyStatus().name(),
                "highestEvidenceType", entry.highestEvidenceType(),
                "streakIncremented", entry.streakIncremented(),
                "freezeConsumed", entry.freezeConsumed()
        )).toList());
        return payload;
    }

    @Override
    public void deleteUserData(UUID userId, Instant now) {
        purgeUserData(userId);
    }

    public void purgeUserData(UUID userId) {
        repository.deleteAllByUserId(userId);
    }
}
