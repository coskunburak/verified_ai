package com.verifiedai.problem.application;

public final class ProblemClassificationContract {
    public static final String SCHEMA_VERSION = "problem-classification-v1";
    public static final String PROJECTION_VERSION = "problem-classification-projection-v1";

    public static final String DIFFICULTY_POLICY_VERSION =
        "classification-difficulty-v1";

    public static final String CONFIDENCE_POLICY_VERSION =
        "classification-confidence-v1";

    public static final int MAX_SECONDARY_SKILLS = 5;

    private ProblemClassificationContract() {
    }
}
