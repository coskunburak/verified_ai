package com.verifiedai.studyplan.domain;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class RecommendationNotFoundException extends ApiProblemException {

    public RecommendationNotFoundException(UUID recommendationId) {
        super(
                HttpStatus.NOT_FOUND,
                ApiErrorCode.RESOURCE_NOT_FOUND,
                "Recommendation not found: " + recommendationId,
                false,
                "CHECK_RECOMMENDATION_ID"
        );
    }
}
