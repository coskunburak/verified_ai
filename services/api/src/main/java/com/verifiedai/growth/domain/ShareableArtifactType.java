package com.verifiedai.growth.domain;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import org.springframework.http.HttpStatus;

public enum ShareableArtifactType {
    WEEKLY_PROGRESS_SUMMARY,
    MASTERY_MILESTONE,
    COUNTERFACTUAL_CHALLENGE_CARD,
    EXAM_READINESS_MILESTONE;

    public static ShareableArtifactType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiProblemException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.GROWTH_VALIDATION_FAILED,
                "artifactType is required",
                false,
                "FIX_GROWTH_REQUEST"
            );
        }
        try {
            return ShareableArtifactType.valueOf(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new ApiProblemException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.GROWTH_VALIDATION_FAILED,
                "Unsupported artifactType: " + raw,
                false,
                "FIX_GROWTH_REQUEST"
            );
        }
    }
}
