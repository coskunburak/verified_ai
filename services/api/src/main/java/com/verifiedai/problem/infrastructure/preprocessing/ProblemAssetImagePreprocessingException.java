package com.verifiedai.problem.infrastructure.preprocessing;

import com.verifiedai.sharedkernel.error.ApiErrorCode;

public final class ProblemAssetImagePreprocessingException extends RuntimeException {
    private final ApiErrorCode apiErrorCode;
    private final String failureCode;

    public ProblemAssetImagePreprocessingException(ApiErrorCode apiErrorCode, String failureCode, String message) {
        super(message);
        this.apiErrorCode = apiErrorCode;
        this.failureCode = failureCode;
    }

    public ApiErrorCode apiErrorCode() {
        return apiErrorCode;
    }

    public String failureCode() {
        return failureCode;
    }
}
