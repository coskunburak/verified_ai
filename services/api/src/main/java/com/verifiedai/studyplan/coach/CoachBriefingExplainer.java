package com.verifiedai.studyplan.coach;

import java.util.Optional;

/**
 * Optional constrained explanation hook representing AiCapability.EXPLAIN (DOC-85 §3.1, ADR-011).
 * Implementations must remain read-only, capped at 1 sentence, and immediately yield to the
 * deterministic template builder when offline or disabled so briefing generation stays $0.00.
 */
@FunctionalInterface
public interface CoachBriefingExplainer {

    Optional<String> summarizeBriefing(
            DailyCoachMode mode,
            int budgetMinutes,
            boolean isColdStart,
            boolean isDownshifted,
            String localeTag
    );
}
