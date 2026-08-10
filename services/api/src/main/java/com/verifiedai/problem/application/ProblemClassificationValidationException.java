package com.verifiedai.problem.application;

public final class ProblemClassificationValidationException
    extends RuntimeException {

    private final ProblemClassificationValidationFailure failure;

    public ProblemClassificationValidationException(
        ProblemClassificationValidationFailure failure,
        String message
    ) {
        super(message);
        this.failure = failure;
    }

    public ProblemClassificationValidationFailure failure() {
        return failure;
    }
}
