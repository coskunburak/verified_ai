package com.verifiedai.problem.domain.model;

public enum ProblemSessionStatus {
    CREATED,
    ASSET_UPLOADED,
    PARSING,
    PARSED,
    SOLVING,
    VERIFYING,
    COMPLETED,
    REVIEW_REQUIRED,
    FAILED,
    CANCELLED
}
