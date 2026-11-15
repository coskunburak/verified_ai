package com.verifiedai.exam.domain;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public record QuestionOption(
        UUID id,
        UUID questionItemId,
        String optionKey,
        int displayOrder,
        String contentMarkdown,
        String contentLatex,
        boolean isCorrect,
        String distractorRationale,
        boolean trainingEligible
) {
    private static final Set<String> VALID_KEYS = Set.of("A", "B", "C", "D", "E");

    public QuestionOption {
        if (optionKey == null || !VALID_KEYS.contains(optionKey.trim())) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.EXAM_QUESTION_INVALID_OPTIONS,
                    "Question option key must be one of A, B, C, D, or E",
                    true,
                    "Provide valid option keys in A..E."
            );
        }
        optionKey = optionKey.trim();
        if (displayOrder < 1 || displayOrder > 5) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.EXAM_QUESTION_INVALID_OPTIONS,
                    "Question option displayOrder must be between 1 and 5",
                    true,
                    "Provide option displayOrder values between 1 and 5."
            );
        }
        if (contentMarkdown == null || contentMarkdown.trim().isEmpty()) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.EXAM_QUESTION_INVALID_OPTIONS,
                    "Question option contentMarkdown must not be blank",
                    true,
                    "Provide non-empty markdown content for each option."
            );
        }
        contentMarkdown = contentMarkdown.trim();
        if (trainingEligible) {
            throw new IllegalArgumentException("trainingEligible must be false per ADR-006");
        }
    }
}
