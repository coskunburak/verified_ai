package com.verifiedai.studyplan.domain;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import org.springframework.http.HttpStatus;

public class RecommendationAccessDeniedException extends ApiProblemException {

    public RecommendationAccessDeniedException(String message) {
        super(
                HttpStatus.FORBIDDEN,
                ApiErrorCode.RESOURCE_FORBIDDEN,
                message != null ? message : "Access denied to recommendation",
                false,
                "AUTHENTICATE_AS_OWNER"
        );
    }
}
