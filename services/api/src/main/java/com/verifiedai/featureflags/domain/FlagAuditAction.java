package com.verifiedai.featureflags.domain;

/**
 * Immutable audit actions recorded in feature_flag_audit_logs.
 */
public enum FlagAuditAction {
    CREATED,
    UPDATED,
    ROLLOUT_CHANGED,
    KILL_SWITCH_ENABLED,
    KILL_SWITCH_DISABLED,
    EXPERIMENT_STATE_CHANGED
}
