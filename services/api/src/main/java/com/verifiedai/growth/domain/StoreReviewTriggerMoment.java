package com.verifiedai.growth.domain;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import org.springframework.http.HttpStatus;

public enum StoreReviewTriggerMoment {
    INDEPENDENT_TWIN_MASTERED,
    MASTERY_MILESTONE_UNLOCKED,
    WEEKLY_PROGRESS_WIN,
    MOCK_EXAM_COMPLETED;

    public static StoreReviewTriggerMoment parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiProblemException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.GROWTH_VALIDATION_FAILED,
                "triggerMoment is required",
                false,
                "FIX_GROWTH_REQUEST"
            );
        }
        try {
            return StoreReviewTriggerMoment.valueOf(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new ApiProblemException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.GROWTH_VALIDATION_FAILED,
                "Unsupported triggerMoment: " + raw,
                false,
                "FIX_GROWTH_REQUEST"
            );
        }
    }
}
