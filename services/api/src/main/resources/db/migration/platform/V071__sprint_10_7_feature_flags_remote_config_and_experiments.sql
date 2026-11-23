-- Sprint 10.7: Feature Flags, Remote Configuration, A/B Tests, and Kill Switches
-- Additive forward-only migration on top of V070 (zero changes to V001..V070).
-- Enforces INV-FF-001..007: lifecycle metadata, kill-switch supremacy, deterministic
-- user-stable assignments, immutable admin audit logs, CHECK (NOT training_eligible),
-- and ON DELETE CASCADE for user-scoped experiment assignments.

CREATE TABLE feature_flag_definitions (
    flag_key              VARCHAR(80)  PRIMARY KEY,
    category              VARCHAR(24)  NOT NULL,
    value_type            VARCHAR(16)  NOT NULL,
    client_safe           BOOLEAN      NOT NULL DEFAULT FALSE,
    enabled               BOOLEAN      NOT NULL DEFAULT FALSE,
    kill_switch_active    BOOLEAN      NOT NULL DEFAULT FALSE,
    rollout_basis_points  INTEGER      NOT NULL DEFAULT 0,
    bucketing_salt        VARCHAR(64)  NOT NULL DEFAULT 'v1',
    default_value_json    JSONB        NOT NULL,
    current_value_json    JSONB        NOT NULL,
    owner                 VARCHAR(64)  NOT NULL,
    purpose               VARCHAR(240) NOT NULL,
    planned_removal_at    TIMESTAMPTZ,
    version               BIGINT       NOT NULL DEFAULT 0,
    training_eligible     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_ff_def_key_format CHECK (flag_key ~ '^[a-z][a-z0-9_.-]{2,79}$'),
    CONSTRAINT ck_ff_def_category CHECK (
        category IN ('RELEASE_FLAG', 'EXPERIMENT_FLAG', 'KILL_SWITCH', 'REMOTE_CONFIG')
    ),
    CONSTRAINT ck_ff_def_value_type CHECK (
        value_type IN ('BOOLEAN', 'INTEGER', 'DECIMAL', 'STRING')
    ),
    CONSTRAINT ck_ff_def_rollout_bounds CHECK (rollout_basis_points BETWEEN 0 AND 10000),
    CONSTRAINT ck_ff_def_salt_non_blank CHECK (length(trim(bucketing_salt)) >= 2),
    CONSTRAINT ck_ff_def_owner_non_blank CHECK (length(trim(owner)) >= 2),
    CONSTRAINT ck_ff_def_purpose_non_blank CHECK (length(trim(purpose)) >= 5),
    CONSTRAINT ck_ff_def_value_size CHECK (
        octet_length(default_value_json::text) <= 512 AND octet_length(current_value_json::text) <= 512
    ),
    CONSTRAINT ck_ff_def_version_non_negative CHECK (version >= 0),
    CONSTRAINT ck_ff_def_training_ineligible CHECK (NOT training_eligible)
);

CREATE INDEX idx_feature_flag_definitions_client_safe
    ON feature_flag_definitions (client_safe, category, flag_key);

CREATE TABLE experiment_definitions (
    experiment_key        VARCHAR(64)  PRIMARY KEY,
    linked_flag_key       VARCHAR(80)  REFERENCES feature_flag_definitions (flag_key) ON DELETE SET NULL,
    status                VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    client_safe           BOOLEAN      NOT NULL DEFAULT FALSE,
    rollout_basis_points  INTEGER      NOT NULL DEFAULT 0,
    bucketing_salt        VARCHAR(64)  NOT NULL DEFAULT 'v1',
    control_variant       VARCHAR(48)  NOT NULL,
    variants_json         JSONB        NOT NULL,
    owner                 VARCHAR(64)  NOT NULL,
    purpose               VARCHAR(240) NOT NULL,
    planned_removal_at    TIMESTAMPTZ,
    experiment_version    INTEGER      NOT NULL DEFAULT 1,
    version               BIGINT       NOT NULL DEFAULT 0,
    training_eligible     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_exp_def_key_format CHECK (experiment_key ~ '^[A-Za-z][A-Za-z0-9_.-]{2,63}$'),
    CONSTRAINT ck_exp_def_status CHECK (
        status IN ('DRAFT', 'RUNNING', 'PAUSED', 'KILLED', 'CONCLUDED')
    ),
    CONSTRAINT ck_exp_def_rollout_bounds CHECK (rollout_basis_points BETWEEN 0 AND 10000),
    CONSTRAINT ck_exp_def_variants_array CHECK (
        jsonb_typeof(variants_json) = 'array' AND jsonb_array_length(variants_json) BETWEEN 2 AND 5
    ),
    CONSTRAINT ck_exp_def_control_non_blank CHECK (length(trim(control_variant)) >= 2),
    CONSTRAINT ck_exp_def_owner_non_blank CHECK (length(trim(owner)) >= 2),
    CONSTRAINT ck_exp_def_purpose_non_blank CHECK (length(trim(purpose)) >= 5),
    CONSTRAINT ck_exp_def_exp_version_positive CHECK (experiment_version >= 1),
    CONSTRAINT ck_exp_def_version_non_negative CHECK (version >= 0),
    CONSTRAINT ck_exp_def_training_ineligible CHECK (NOT training_eligible)
);

CREATE INDEX idx_experiment_definitions_status_client
    ON experiment_definitions (status, client_safe);

CREATE TABLE experiment_assignments (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    experiment_key      VARCHAR(64) NOT NULL REFERENCES experiment_definitions (experiment_key) ON DELETE CASCADE,
    experiment_version  INTEGER     NOT NULL,
    variant_key         VARCHAR(48) NOT NULL,
    bucket_value        INTEGER     NOT NULL,
    assignment_reason   VARCHAR(32) NOT NULL,
    assigned_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    training_eligible   BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_exp_assignments_user_exp_ver UNIQUE (user_id, experiment_key, experiment_version),
    CONSTRAINT ck_exp_assign_exp_ver_positive CHECK (experiment_version >= 1),
    CONSTRAINT ck_exp_assign_variant_non_blank CHECK (length(trim(variant_key)) >= 2),
    CONSTRAINT ck_exp_assign_bucket_bounds CHECK (bucket_value BETWEEN 0 AND 9999),
    CONSTRAINT ck_exp_assign_reason CHECK (
        assignment_reason IN ('BUCKET_MATCH', 'FALLBACK_CONTROL', 'KILL_SWITCH_ACTIVE')
    ),
    CONSTRAINT ck_exp_assign_training_ineligible CHECK (NOT training_eligible)
);

CREATE INDEX idx_experiment_assignments_user_time
    ON experiment_assignments (user_id, assigned_at DESC);

CREATE INDEX idx_experiment_assignments_exp_variant
    ON experiment_assignments (experiment_key, experiment_version, variant_key);

CREATE TABLE feature_flag_audit_logs (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    target_type       VARCHAR(16)  NOT NULL,
    target_key        VARCHAR(80)  NOT NULL,
    action            VARCHAR(32)  NOT NULL,
    actor_user_id     UUID         NOT NULL,
    actor_role        VARCHAR(32)  NOT NULL,
    idempotency_key   VARCHAR(128) NOT NULL,
    payload_hash      CHAR(64)     NOT NULL,
    reason            VARCHAR(240) NOT NULL,
    before_state_json JSONB        NOT NULL,
    after_state_json  JSONB        NOT NULL,
    correlation_id    VARCHAR(64)  NOT NULL,
    training_eligible BOOLEAN      NOT NULL DEFAULT FALSE,
    occurred_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ff_audit_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_ff_audit_target_type CHECK (target_type IN ('FLAG', 'EXPERIMENT')),
    CONSTRAINT ck_ff_audit_action CHECK (
        action IN (
            'CREATED', 'UPDATED', 'ROLLOUT_CHANGED',
            'KILL_SWITCH_ENABLED', 'KILL_SWITCH_DISABLED',
            'EXPERIMENT_STATE_CHANGED'
        )
    ),
    CONSTRAINT ck_ff_audit_idem_format CHECK (idempotency_key ~ '^[A-Za-z0-9:._-]{8,128}$'),
    CONSTRAINT ck_ff_audit_payload_hash_hex CHECK (payload_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_ff_audit_reason_len CHECK (length(trim(reason)) BETWEEN 5 AND 240),
    CONSTRAINT ck_ff_audit_json_objects CHECK (
        jsonb_typeof(before_state_json) = 'object' AND jsonb_typeof(after_state_json) = 'object'
    ),
    CONSTRAINT ck_ff_audit_training_ineligible CHECK (NOT training_eligible)
);

CREATE INDEX idx_feature_flag_audit_logs_target_time
    ON feature_flag_audit_logs (target_type, target_key, occurred_at DESC);

CREATE INDEX idx_feature_flag_audit_logs_occurred
    ON feature_flag_audit_logs (occurred_at DESC, id DESC);

-- Canonical seed definitions aligned with operations/49 & operations/92 (all risky rollouts default safely off)
INSERT INTO feature_flag_definitions (
    flag_key, category, value_type, client_safe, enabled, kill_switch_active,
    rollout_basis_points, bucketing_salt, default_value_json, current_value_json,
    owner, purpose, planned_removal_at
) VALUES
    ('secondary_solver_enabled', 'KILL_SWITCH', 'BOOLEAN', FALSE, FALSE, FALSE, 0, 'v1', 'false'::jsonb, 'false'::jsonb,
     'platform-ai', 'Emergency kill switch and progressive gate for secondary AI solver escalation', TIMESTAMPTZ '2027-06-01T00:00:00Z'),
    ('advanced_verification_enabled', 'RELEASE_FLAG', 'BOOLEAN', FALSE, FALSE, FALSE, 0, 'v1', 'false'::jsonb, 'false'::jsonb,
     'platform-verifier', 'Progressive rollout flag for advanced deterministic verification methods', TIMESTAMPTZ '2027-06-01T00:00:00Z'),
    ('tutor_v2_enabled', 'RELEASE_FLAG', 'BOOLEAN', FALSE, FALSE, FALSE, 0, 'v1', 'false'::jsonb, 'false'::jsonb,
     'platform-learning', 'Progressive rollout flag for Socratic Tutor v2 flow', TIMESTAMPTZ '2027-06-01T00:00:00Z'),
    ('adaptive_plan_v2_enabled', 'RELEASE_FLAG', 'BOOLEAN', FALSE, FALSE, FALSE, 0, 'v1', 'false'::jsonb, 'false'::jsonb,
     'platform-learning', 'Progressive rollout flag for adaptive study plan v2 engine', TIMESTAMPTZ '2027-06-01T00:00:00Z'),
    ('pdf_upload_enabled', 'KILL_SWITCH', 'BOOLEAN', TRUE, TRUE, FALSE, 10000, 'v1', 'true'::jsonb, 'true'::jsonb,
     'platform-security', 'Operational kill switch to disable PDF uploads immediately during security incidents', NULL),
    ('verification_method_false_positive_kill_switch', 'KILL_SWITCH', 'BOOLEAN', FALSE, FALSE, FALSE, 0, 'v1', 'false'::jsonb, 'false'::jsonb,
     'platform-verifier', 'Emergency kill switch to disable verification methods linked to false positives', NULL),
    ('strong_model_pct', 'REMOTE_CONFIG', 'INTEGER', FALSE, TRUE, FALSE, 10000, 'v1', '0'::jsonb, '0'::jsonb,
     'platform-ai', 'Server-authoritative strong-model routing percentage ceiling (0-100)', NULL),
    ('proprietary_canary_pct', 'REMOTE_CONFIG', 'INTEGER', FALSE, FALSE, FALSE, 0, 'v1', '0'::jsonb, '0'::jsonb,
     'platform-ai', 'Proprietary model shadow/canary percentage (locked at 0 until Phase 13 gates)', NULL),
    ('daily_solve_quota_override', 'REMOTE_CONFIG', 'INTEGER', FALSE, FALSE, FALSE, 0, 'v1', '3'::jsonb, '3'::jsonb,
     'platform-billing', 'Server-authoritative remote configuration for free daily solve quota baseline', NULL),
    ('client_platform_health_screen', 'RELEASE_FLAG', 'BOOLEAN', TRUE, FALSE, FALSE, 0, 'v1', 'false'::jsonb, 'false'::jsonb,
     'platform-ios', 'Client-safe presentation flag matching FeatureFlag.platformHealthScreen', TIMESTAMPTZ '2027-06-01T00:00:00Z'),
    ('client_mistake_moment_paywall_ui', 'EXPERIMENT_FLAG', 'BOOLEAN', TRUE, FALSE, FALSE, 0, 'v1', 'false'::jsonb, 'false'::jsonb,
     'platform-growth', 'Client-safe presentation flag for contextual paywall card framing', TIMESTAMPTZ '2027-06-01T00:00:00Z')
ON CONFLICT (flag_key) DO NOTHING;

INSERT INTO experiment_definitions (
    experiment_key, linked_flag_key, status, client_safe, rollout_basis_points,
    bucketing_salt, control_variant, variants_json, owner, purpose, planned_removal_at
) VALUES
    ('EXP-LOS-01', NULL, 'DRAFT', TRUE, 0, 'v1', 'control',
     '[{"key":"control","weightBasisPoints":5000},{"key":"treatment","weightBasisPoints":5000}]'::jsonb,
     'platform-learning', 'Evaluate concrete Counterexample Proof Card layout lift on Verified Twin start rate', TIMESTAMPTZ '2027-06-01T00:00:00Z'),
    ('EXP-LOS-02', NULL, 'DRAFT', FALSE, 0, 'v1', 'control_60s',
     '[{"key":"control_60s","weightBasisPoints":5000},{"key":"micro_choice_unlock","weightBasisPoints":5000}]'::jsonb,
     'platform-learning', 'Evaluate 60s solution lock vs first-step micro-choice unlock on twin transfer rate', TIMESTAMPTZ '2027-06-01T00:00:00Z'),
    ('EXP-LOS-08', 'client_mistake_moment_paywall_ui', 'DRAFT', TRUE, 0, 'v1', 'control_quota_wall',
     '[{"key":"control_quota_wall","weightBasisPoints":5000},{"key":"treatment_mastery_shield","weightBasisPoints":5000}]'::jsonb,
     'platform-growth', 'Evaluate contextual upgrade prompt after first Independent Mastery Shield unlock', TIMESTAMPTZ '2027-06-01T00:00:00Z'),
    ('EXP-LOS-09', NULL, 'DRAFT', TRUE, 0, 'v1', 'control_two_plan',
     '[{"key":"control_two_plan","weightBasisPoints":5000},{"key":"treatment_three_plan","weightBasisPoints":5000}]'::jsonb,
     'platform-growth', 'Evaluate 3-plan YKS Season Pass paywall layout vs 2-plan Monthly/Annual layout', TIMESTAMPTZ '2027-06-01T00:00:00Z')
ON CONFLICT (experiment_key) DO NOTHING;
