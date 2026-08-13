package com.verifiedai.problem.domain.model.classification;

/**
 * Server-policy-derived classification confidence band.
 *
 * This is never copied directly from an LLM/provider self-reported
 * confidence field.
 */
public enum ClassificationConfidence {
    HIGH,
    MEDIUM,
    LOW,
    UNKNOWN
}
