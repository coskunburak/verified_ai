package com.verifiedai.exam.domain;

import java.util.Locale;

public enum CognitiveLevel {
    RECALL,
    APPLICATION,
    ANALYSIS,
    SYNTHESIS;

    public static CognitiveLevel fromCognitiveTaxonomyLevel(CognitiveTaxonomyLevel level) {
        if (level == null) {
            return APPLICATION;
        }
        return switch (level) {
            case RECALL -> RECALL;
            case COMPREHENSION, APPLICATION -> APPLICATION;
            case ANALYSIS -> ANALYSIS;
            case EVALUATION -> SYNTHESIS;
        };
    }

    public static CognitiveLevel fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return APPLICATION;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "RECALL" -> RECALL;
            case "COMPREHENSION", "APPLICATION" -> APPLICATION;
            case "ANALYSIS" -> ANALYSIS;
            case "EVALUATION", "SYNTHESIS" -> SYNTHESIS;
            default -> throw new IllegalArgumentException("Unsupported cognitiveLevel: " + raw);
        };
    }
}
