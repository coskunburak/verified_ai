package com.verifiedai.problem.domain.model;

/**
 * Durable semantic outcome of problem classification.
 *
 * Provider/network/job failures are intentionally not represented here.
 * They belong to the classification execution lifecycle.
 */
public enum ProblemClassificationStatus {
    CLASSIFIED,
    REVIEW_REQUIRED,
    UNKNOWN,
    UNSUPPORTED
}
