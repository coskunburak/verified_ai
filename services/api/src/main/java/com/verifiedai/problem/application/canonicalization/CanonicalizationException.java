package com.verifiedai.problem.application.canonicalization;

final class CanonicalizationException extends RuntimeException {
    private final CanonicalizationFailure failure;

    CanonicalizationException(CanonicalizationFailure failure, String message) {
        super(message);
        this.failure = failure;
    }

    CanonicalizationFailure failure() {
        return failure;
    }
}

enum CanonicalizationFailure {
    UNSUPPORTED_PARSE,
    UNSAFE_IDENTIFIER,
    UNSUPPORTED_EXPRESSION,
    COMPLEXITY_LIMIT,
    INVALID_CONSTRAINT
}
