package com.verifiedai.exam.domain;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public record BlueprintTopicRule(
        UUID id,
        UUID blueprintId,
        String topicCode,
        int targetWeightBps,
        int minQuestionCount,
        int maxQuestionCount,
        List<QuestionItemFormat> requiredItemFormats,
        int ruleOrder,
        boolean trainingEligible
) {
    public BlueprintTopicRule {
        if (topicCode == null || topicCode.trim().isEmpty()) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.EXAM_BLUEPRINT_DISTRIBUTION_INVALID,
                    "Blueprint topic rule topicCode is required",
                    true,
                    "Provide a valid topicCode for each blueprint topic rule."
            );
        }
        topicCode = topicCode.trim();
        if (targetWeightBps < 100 || targetWeightBps > 10000) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.EXAM_BLUEPRINT_DISTRIBUTION_INVALID,
                    "Blueprint topic rule targetWeightBps must be between 100 and 10000",
                    true,
                    "Specify targetWeightBps between 100 and 10000 basis points."
            );
        }
        if (minQuestionCount < 0 || maxQuestionCount < minQuestionCount || maxQuestionCount < 1) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.EXAM_BLUEPRINT_DISTRIBUTION_INVALID,
                    "Invalid minQuestionCount / maxQuestionCount bounds on topic rule " + topicCode,
                    true,
                    "Ensure 0 <= minQuestionCount <= maxQuestionCount and maxQuestionCount >= 1."
            );
        }
        if (requiredItemFormats == null || requiredItemFormats.isEmpty()) {
            requiredItemFormats = List.of(QuestionItemFormat.SINGLE_CHOICE_MCQ);
        } else {
            requiredItemFormats = List.copyOf(requiredItemFormats);
        }
        if (ruleOrder < 1) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.EXAM_BLUEPRINT_DISTRIBUTION_INVALID,
                    "Blueprint topic ruleOrder must be >= 1",
                    true,
                    "Provide ruleOrder >= 1."
            );
        }
        if (trainingEligible) {
            throw new IllegalArgumentException("trainingEligible must be false per ADR-006");
        }
    }
}
