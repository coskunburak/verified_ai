package com.verifiedai.studyplan.gamification;

import java.util.Locale;

/**
 * Evidence classification for Sprint 8.8 streak and achievement evaluation.
 *
 * <p>Sprint 8.8 defines this local enum inside {@code studyplan} to respect Spring Modulith
 * module boundaries without coupling to {@code mastery}.
 */
public enum GamificationEvidenceType {
    INDEPENDENT,
    HINT_ASSISTED,
    SOLUTION_REVEALED_ASSISTED,
    UNSUPPORTED;

    public boolean countsForIndependentStreak() {
        return this == INDEPENDENT;
    }

    public boolean countsForAssistedContinuity() {
        return this == HINT_ASSISTED || this == SOLUTION_REVEALED_ASSISTED;
    }

    public static GamificationEvidenceType fromRequest(String rawEvidenceType) {
        if (rawEvidenceType == null || rawEvidenceType.isBlank()) {
            throw new GamificationExceptions.GamificationEvidenceUnsupportedException(
                    "Evidence type is required for gamification activity evaluation."
            );
        }
        String normalized = rawEvidenceType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "INDEPENDENT" -> INDEPENDENT;
            case "HINT_ASSISTED" -> HINT_ASSISTED;
            case "SOLUTION_REVEALED_ASSISTED" -> SOLUTION_REVEALED_ASSISTED;
            default -> throw new GamificationExceptions.GamificationEvidenceUnsupportedException(
                    "Unsupported gamification evidence type: " + rawEvidenceType
            );
        };
    }

    public static GamificationEvidenceType fromNullableStored(String rawEvidenceType) {
        if (rawEvidenceType == null || rawEvidenceType.isBlank()) {
            return null;
        }
        return GamificationEvidenceType.valueOf(rawEvidenceType.trim().toUpperCase(Locale.ROOT));
    }
}
