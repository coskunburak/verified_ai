package com.verifiedai.featureflags.domain;

/**
 * Legal lifecycle states for controlled Learning OS A/B experiments.
 */
public enum ExperimentStatus {
    DRAFT,
    RUNNING,
    PAUSED,
    KILLED,
    CONCLUDED
}
