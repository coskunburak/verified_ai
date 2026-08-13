package com.verifiedai.problem.domain.model.session;

public enum ProblemSessionStage {
    AWAITING_UPLOAD,
    PREPROCESSING,
    RECOGNITION,
    PARSING,
    PARSE_REVIEW,
    CANONICALIZATION,
    CLASSIFICATION,
    READY_FOR_SOLVE,
    TERMINAL
}
