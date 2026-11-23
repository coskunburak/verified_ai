package com.verifiedai.featureflags.domain;

/**
 * Canonical feature flag categories aligned with operations/49.
 */
public enum FlagCategory {
    RELEASE_FLAG,
    EXPERIMENT_FLAG,
    KILL_SWITCH,
    REMOTE_CONFIG
}
