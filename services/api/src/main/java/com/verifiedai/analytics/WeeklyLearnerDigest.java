package com.verifiedai.analytics;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain aggregate representing a persisted weekly learner digest row.
 * Enforces ADR-006 non-training invariant ({@code trainingEligible == false})
 * and strict Monday-through-Sunday ISO week bounds.
 */
public record WeeklyLearnerDigest(
    UUID digestId,
    UUID userId,
    String weekIso,
    LocalDate weekStartDate,
    LocalDate weekEndDate,
    String locale,
    WeeklyDigestStatus status,
    int solvedAttemptsCount,
    int independentTwinsSolvedCount,
    int studyMinutes,
    int activeDaysCount,
    WeeklyReportDtos.WeeklyDigestDto payload,
    List<WeeklyReportDtos.ExplainableRecommendationItemDto> recommendations,
    boolean trainingEligible,
    Instant generatedAt,
    Instant updatedAt
) {
    public WeeklyLearnerDigest {
        Objects.requireNonNull(digestId, "digestId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(weekIso, "weekIso must not be null");
        Objects.requireNonNull(weekStartDate, "weekStartDate must not be null");
        Objects.requireNonNull(weekEndDate, "weekEndDate must not be null");
        if (!weekEndDate.equals(weekStartDate.plusDays(6))) {
            throw new IllegalArgumentException("weekEndDate must be weekStartDate + 6 days");
        }
        Objects.requireNonNull(locale, "locale must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (solvedAttemptsCount < 0 || independentTwinsSolvedCount < 0 || studyMinutes < 0) {
            throw new IllegalArgumentException("Digest metrics must be non-negative");
        }
        if (activeDaysCount < 0 || activeDaysCount > 7) {
            throw new IllegalArgumentException("activeDaysCount must be between 0 and 7");
        }
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(recommendations, "recommendations must not be null");
        recommendations = List.copyOf(recommendations);
        if (trainingEligible) {
            throw new IllegalArgumentException("trainingEligible must always be false (ADR-006)");
        }
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }
}
