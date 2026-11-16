package com.verifiedai.exam.domain;

import java.util.Locale;

public enum QuestionFormatType {
    SINGLE_CHOICE_MCQ,
    MULTI_SELECT_MCQ,
    NUMERIC_GRID_IN;

    public static QuestionFormatType fromQuestionItemFormat(QuestionItemFormat format) {
        if (format == null) {
            return SINGLE_CHOICE_MCQ;
        }
        return switch (format) {
            case SINGLE_CHOICE_MCQ -> SINGLE_CHOICE_MCQ;
            case MULTI_SELECT_MCQ -> MULTI_SELECT_MCQ;
            case NUMERIC_GRID_IN, SHORT_STRUCTURED_RESPONSE -> NUMERIC_GRID_IN;
        };
    }

    public static QuestionFormatType fromItemFormat(String raw) {
        if (raw == null || raw.isBlank()) {
            return SINGLE_CHOICE_MCQ;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "SINGLE_CHOICE_MCQ", "SINGLE_CHOICE_4_OPTION", "SINGLE_CHOICE_5_OPTION" -> SINGLE_CHOICE_MCQ;
            case "MULTI_SELECT_MCQ" -> MULTI_SELECT_MCQ;
            case "NUMERIC_GRID_IN", "SHORT_STRUCTURED_RESPONSE" -> NUMERIC_GRID_IN;
            default -> throw new IllegalArgumentException("Unsupported question formatType: " + raw);
        };
    }
}
