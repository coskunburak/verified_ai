package com.verifiedai.studyplan.domain;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import org.springframework.http.HttpStatus;

public class RecommendationStateConflictException extends ApiProblemException {

    public RecommendationStateConflictException(String message) {
        super(
                HttpStatus.CONFLICT,
                ApiErrorCode.OPTIMISTIC_CONFLICT,
                message != null ? message : "Recommendation state conflict",
                false,
                "REFRESH_RECOMMENDATIONS"
        );
    }
}
