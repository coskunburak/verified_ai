package com.verifiedai.exam.domain;

import com.verifiedai.exam.domain.CandidateProfileModels.AccessibilityAccommodations;
import com.verifiedai.exam.domain.CandidateProfileModels.AdjustedExamSection;
import com.verifiedai.exam.domain.CandidateProfileModels.FeasibilityStatus;
import com.verifiedai.exam.domain.CandidateProfileModels.PacingAssessment;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;

/**
 * Pure deterministic domain engine ($0.00 LLM spend) for study pacing,
 * exam feasibility classification, and accessibility extra-time multipliers.
 */
public final class CandidatePaceCalculator {

    public static final int MIN_DAILY_STUDY_MINUTES = 15;
    public static final int MAX_DAILY_STUDY_MINUTES = 960;
    public static final int MIN_WEEKLY_STUDY_MINUTES = 15;
    public static final int MAX_WEEKLY_STUDY_MINUTES = 6720;
    public static final BigDecimal DEFAULT_BASELINE_MASTERY = new BigDecimal("0.2500");

    private static final double MINUTES_PER_QUESTION_WEIGHT = 90.0d;
    private static final BigDecimal OPTIMAL_THRESHOLD = new BigDecimal("0.85");
    private static final BigDecimal STRETCH_THRESHOLD = new BigDecimal("1.15");
    private static final BigDecimal AT_RISK_THRESHOLD = new BigDecimal("1.50");

    private static final Set<BigDecimal> ALLOWED_MULTIPLIERS = Set.of(
            new BigDecimal("1.00"),
            new BigDecimal("1.25"),
            new BigDecimal("1.50"),
            new BigDecimal("2.00")
    );

    private CandidatePaceCalculator() {
    }

    public static AccessibilityAccommodations normalizeAndValidateAccommodations(
            AccessibilityAccommodations requested
    ) {
        if (requested == null) {
            return AccessibilityAccommodations.standardDefaults();
        }
        BigDecimal rawMultiplier = requested.extraTimeMultiplier() == null
                ? new BigDecimal("1.00")
                : requested.extraTimeMultiplier();
        BigDecimal scaled = rawMultiplier.setScale(2, RoundingMode.HALF_UP);
        // Reject silent rounding (e.g. 1.26 rounding to 1.26 isn't in set; 1.250 is exact; check compareTo == 0)
        if (rawMultiplier.stripTrailingZeros().scale() > 2
                || ALLOWED_MULTIPLIERS.stream().noneMatch(allowed -> allowed.compareTo(scaled) == 0)) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.CANDIDATE_ACCOMMODATION_INVALID,
                    "Extra time multiplier must be one of 1.00, 1.25, 1.50, or 2.00",
                    true,
                    "Select a permitted extra-time accommodation multiplier (1.00x, 1.25x, 1.50x, or 2.00x)."
            );
        }
        return new AccessibilityAccommodations(
                scaled,
                requested.highContrastEnabled(),
                requested.screenReaderOptimized(),
                requested.reducedMotionEnabled()
        );
    }

    public static void validateTargetDate(LocalDate targetDate, LocalDate today) {
        if (targetDate == null || !targetDate.isAfter(today)) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.CANDIDATE_EXAM_DATE_IN_PAST,
                    "Exam target date must be in the future",
                    true,
                    "Choose a target exam date strictly after today."
            );
        }
    }

    public static void validateStudyBudget(Integer dailyStudyMinutes, Integer weeklyStudyMinutes) {
        if (dailyStudyMinutes == null || weeklyStudyMinutes == null) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.CANDIDATE_STUDY_BUDGET_INVALID,
                    "Daily and weekly study minutes are required",
                    true,
                    "Provide daily study minutes between 15 and 960 and weekly study minutes between 15 and 6720."
            );
        }
        if (dailyStudyMinutes < MIN_DAILY_STUDY_MINUTES || dailyStudyMinutes > MAX_DAILY_STUDY_MINUTES) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.CANDIDATE_STUDY_BUDGET_INVALID,
                    "Daily study budget must be between 15 and 960 minutes",
                    true,
                    "Set daily study minutes between 15 and 960."
            );
        }
        if (weeklyStudyMinutes < MIN_WEEKLY_STUDY_MINUTES || weeklyStudyMinutes > MAX_WEEKLY_STUDY_MINUTES) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.CANDIDATE_STUDY_BUDGET_INVALID,
                    "Weekly study budget must be between 15 and 6720 minutes",
                    true,
                    "Set weekly study minutes between 15 and 6720."
            );
        }
        if (weeklyStudyMinutes < dailyStudyMinutes) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.CANDIDATE_STUDY_BUDGET_INVALID,
                    "Weekly study minutes cannot be less than daily study minutes",
                    true,
                    "Increase weekly study minutes to at least match daily study minutes."
            );
        }
    }

    public static BigDecimal normalizeAndValidateBaselineMastery(BigDecimal requested, BigDecimal fallback) {
        BigDecimal effective = requested != null ? requested : (fallback != null ? fallback : DEFAULT_BASELINE_MASTERY);
        if (effective.compareTo(BigDecimal.ZERO) < 0 || effective.compareTo(BigDecimal.ONE) > 0) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.CANDIDATE_STUDY_BUDGET_INVALID,
                    "Baseline mastery must be between 0.0 and 1.0",
                    true,
                    "Provide a valid baseline mastery fraction between 0.0 and 1.0."
            );
        }
        return effective.setScale(4, RoundingMode.HALF_UP);
    }

    public static List<AdjustedExamSection> calculateAdjustedSections(
            ExamModels.ExamDefinition exam,
            BigDecimal extraTimeMultiplier
    ) {
        if (exam == null || exam.sections() == null || exam.sections().isEmpty()) {
            return List.of();
        }
        double multiplier = extraTimeMultiplier.doubleValue();
        List<AdjustedExamSection> adjusted = new java.util.ArrayList<>(exam.sections().size());
        for (var section : exam.sections()) {
            adjusted.add(new AdjustedExamSection(
                    section.sectionCode(),
                    section.titleEn(),
                    section.titleTr(),
                    section.questionCount(),
                    section.durationMinutes(),
                    (int) Math.ceil(section.durationMinutes() * multiplier),
                    section.order()
            ));
        }
        return List.copyOf(adjusted);
    }

    public static PacingAssessment assessPacing(
            ExamModels.ExamDefinition exam,
            LocalDate today,
            LocalDate targetDate,
            int dailyStudyMinutes,
            int weeklyStudyMinutes,
            BigDecimal baselineMastery
    ) {
        validateTargetDate(targetDate, today);
        validateStudyBudget(dailyStudyMinutes, weeklyStudyMinutes);
        BigDecimal normalizedMastery = normalizeAndValidateBaselineMastery(baselineMastery, DEFAULT_BASELINE_MASTERY);

        int daysUntilExam = (int) Math.max(1L, ChronoUnit.DAYS.between(today, targetDate));
        double totalWeight = (exam.topicWeights() == null || exam.topicWeights().isEmpty())
                ? Math.max(1, exam.totalQuestions())
                : exam.topicWeights().stream()
                        .mapToDouble(w -> w.questionCountWeight().doubleValue())
                        .sum();
        double remainingUnits = totalWeight * Math.max(0.0d, 1.0d - normalizedMastery.doubleValue());
        double totalRemainingMinutes = remainingUnits * MINUTES_PER_QUESTION_WEIGHT;

        int requiredDailyMinutes = (int) Math.ceil(totalRemainingMinutes / daysUntilExam);
        double effectiveDailyExact = Math.min((double) dailyStudyMinutes, weeklyStudyMinutes / 7.0d);
        int effectiveDailyCapacity = Math.max(1, (int) Math.round(effectiveDailyExact));

        BigDecimal ratio = effectiveDailyExact <= 0.0d
                ? new BigDecimal("99.99")
                : BigDecimal.valueOf(requiredDailyMinutes / effectiveDailyExact).setScale(4, RoundingMode.HALF_UP);

        FeasibilityStatus feasibility;
        if (ratio.compareTo(OPTIMAL_THRESHOLD) <= 0) {
            feasibility = FeasibilityStatus.OPTIMAL;
        } else if (ratio.compareTo(STRETCH_THRESHOLD) <= 0) {
            feasibility = FeasibilityStatus.STRETCH;
        } else if (ratio.compareTo(AT_RISK_THRESHOLD) <= 0) {
            feasibility = FeasibilityStatus.AT_RISK;
        } else {
            feasibility = FeasibilityStatus.UNREALISTIC;
        }

        return new PacingAssessment(
                daysUntilExam,
                effectiveDailyCapacity,
                requiredDailyMinutes,
                BigDecimal.valueOf(totalRemainingMinutes).setScale(2, RoundingMode.HALF_UP),
                ratio,
                feasibility
        );
    }
}
