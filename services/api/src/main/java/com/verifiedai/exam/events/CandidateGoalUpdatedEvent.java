package com.verifiedai.exam.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain event published whenever a candidate's exam goal, study horizon,
 * accommodations, or confirmed syllabus topics are updated.
 */
public record CandidateGoalUpdatedEvent(
        UUID userId,
        UUID profileId,
        String examCode,
        String pinnedExamVersion,
        LocalDate targetDate,
        int dailyStudyMinutes,
        int weeklyStudyMinutes,
        BigDecimal targetScore,
        BigDecimal extraTimeMultiplier,
        int requiredDailyMinutes,
        String feasibilityStatus,
        String triggerType,
        Instant occurredAt
) {
    public CandidateGoalUpdatedEvent {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(examCode, "examCode must not be null");
        Objects.requireNonNull(triggerType, "triggerType must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
