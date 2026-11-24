package com.verifiedai.growth.domain;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import org.springframework.http.HttpStatus;

public enum StoreReviewOutcome {
    PROMPT_SHOWN,
    PROMPT_DISMISSED,
    PROMPT_DEFERRED,
    PROMPT_DECLINED_PERMANENTLY;

    public static StoreReviewOutcome parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiProblemException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.GROWTH_VALIDATION_FAILED,
                "outcome is required",
                false,
                "FIX_GROWTH_REQUEST"
            );
        }
        try {
            return StoreReviewOutcome.valueOf(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new ApiProblemException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.GROWTH_VALIDATION_FAILED,
                "Unsupported outcome: " + raw,
                false,
                "FIX_GROWTH_REQUEST"
            );
        }
    }
}
