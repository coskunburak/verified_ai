package com.verifiedai.problem.application.classification;

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
