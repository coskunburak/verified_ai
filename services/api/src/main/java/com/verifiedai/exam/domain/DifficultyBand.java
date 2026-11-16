package com.verifiedai.exam.domain;

import java.util.Locale;

public enum DifficultyBand {
    EASY,
    MODERATE,
    HARD,
    ELITE;

    public static DifficultyBand fromQuestionDifficultyBand(QuestionDifficultyBand band) {
        if (band == null) {
            return MODERATE;
        }
        return switch (band) {
            case EASY -> EASY;
            case MEDIUM -> MODERATE;
            case HARD -> HARD;
            case EXPERT -> ELITE;
        };
    }

    public static DifficultyBand fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return MODERATE;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "EASY" -> EASY;
            case "MEDIUM", "MODERATE" -> MODERATE;
            case "HARD" -> HARD;
            case "EXPERT", "ELITE" -> ELITE;
            default -> throw new IllegalArgumentException("Unsupported difficultyBand: " + raw);
        };
    }
}
