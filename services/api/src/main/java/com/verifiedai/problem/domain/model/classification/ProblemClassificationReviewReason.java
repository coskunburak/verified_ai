package com.verifiedai.problem.domain.model.classification;

public enum ProblemClassificationReviewReason {

    /**
     * Multiple canonical skills remain materially plausible.
     */
    AMBIGUOUS_PRIMARY_SKILL,

    /**
     * Classification evidence is insufficient for automatic promotion.
     */
    LOW_CLASSIFICATION_CONFIDENCE,

    /**
     * CanonicalProblem itself carries material upstream review/risk evidence.
     */
    UPSTREAM_RISK,

    /**
     * The curriculum ontology does not currently contain a sufficiently
     * precise mapping for this otherwise valid problem.
     */
    ONTOLOGY_COVERAGE_GAP,

    /**
     * Canonical semantic information is insufficient to classify safely.
     */
    INSUFFICIENT_SEMANTIC_EVIDENCE
}
