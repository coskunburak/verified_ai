package com.verifiedai.problem.domain.model;

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
