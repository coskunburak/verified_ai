package com.verifiedai.problem.application.parse;

class ProblemParseValidationException extends RuntimeException {
    private final ProblemParseValidationFailure failure;

    ProblemParseValidationException(ProblemParseValidationFailure failure, String message) {
        super(message);
        this.failure = failure;
    }

    ProblemParseValidationFailure failure() {
        return failure;
    }
}
