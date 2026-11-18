package com.verifiedai.exam.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Topic/subtopic/skill speed-pressure pacing breakdown over the selected longitudinal mock window.
 */
public record PacingAnalysisDto(
        String topicCode,
        String subtopicCode,
        String skillId,
        int observedQuestionCount,
        int excludedUnmeasuredCount,
        BigDecimal meanSecondsPerQuestion,
        BigDecimal expectedSecondsPerQuestion,
        BigDecimal ratio,
        PacingClassification classification
) {
    public enum PacingClassification {
        BOTTLENECK,
        ON_PACE,
        FAST,
        INSUFFICIENT_DATA
    }

    public PacingAnalysisDto {
        Objects.requireNonNull(topicCode, "topicCode cannot be null");
        if (topicCode.isBlank()) {
            throw new IllegalArgumentException("topicCode cannot be blank");
        }
        if (subtopicCode != null && subtopicCode.isBlank()) {
            subtopicCode = null;
        }
        if (skillId != null && skillId.isBlank()) {
            skillId = null;
        }
        if (observedQuestionCount < 0 || excludedUnmeasuredCount < 0) {
            throw new IllegalArgumentException("Question counts cannot be negative");
        }
        Objects.requireNonNull(classification, "classification cannot be null");
        if (classification == PacingClassification.INSUFFICIENT_DATA) {
            if (meanSecondsPerQuestion != null || expectedSecondsPerQuestion != null || ratio != null) {
                throw new IllegalArgumentException("INSUFFICIENT_DATA pacing entries must have null numeric metrics");
            }
        } else {
            if (observedQuestionCount < 2
                    || meanSecondsPerQuestion == null
                    || expectedSecondsPerQuestion == null
                    || ratio == null
                    || meanSecondsPerQuestion.signum() <= 0
                    || expectedSecondsPerQuestion.signum() <= 0
                    || ratio.signum() <= 0) {
                throw new IllegalArgumentException("Classified pacing entries require >= 2 timed questions and positive metrics");
            }
        }
    }
}
