package com.verifiedai.problem.application;

final class ProblemClassificationOutputException
    extends RuntimeException {

    ProblemClassificationOutputException(
        String message
    ) {
        super(message);
    }

    ProblemClassificationOutputException(
        String message,
        Throwable cause
    ) {
        super(message, cause);
    }
}
