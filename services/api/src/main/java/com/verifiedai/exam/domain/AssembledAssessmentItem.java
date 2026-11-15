package com.verifiedai.exam.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record AssembledAssessmentItem(
        UUID id,
        UUID assembledAssessmentId,
        UUID questionItemId,
        String questionCode,
        int sequenceNumber,
        String topicCode,
        String subtopicCode,
        QuestionItemFormat itemFormat,
        QuestionDifficultyBand difficultyBand,
        CognitiveTaxonomyLevel cognitiveLevel,
        String stemMarkdown,
        String stemLatex,
        String altTextDescription,
        boolean hasVisualDiagram,
        List<String> optionOrder,
        List<QuestionOption> orderedOptions,
        BigDecimal allocatedPoints,
        int estimatedTimeSeconds,
        boolean trainingEligible
) {
    public AssembledAssessmentItem {
        if (sequenceNumber < 1) {
            throw new IllegalArgumentException("sequenceNumber must be >= 1");
        }
        optionOrder = optionOrder == null ? List.of() : List.copyOf(optionOrder);
        orderedOptions = orderedOptions == null ? List.of() : List.copyOf(orderedOptions);
        allocatedPoints = allocatedPoints == null ? new BigDecimal("1.00") : allocatedPoints;
        if (allocatedPoints.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("allocatedPoints must be > 0");
        }
        if (trainingEligible) {
            throw new IllegalArgumentException("trainingEligible must be false per ADR-006");
        }
    }
}
