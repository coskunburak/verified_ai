-- Sprint 10.5: Lifecycle Messaging and Learning-Relevant Re-Engagement
-- Additive forward-only migration on top of V068 (zero changes to V001..V068).
-- Enforces INV-LIFE-001..006: per-category anti-spam cooldowns, trigger evaluation audit,
-- CHECK (NOT training_eligible), and ON DELETE CASCADE.

CREATE TABLE lifecycle_messaging_cooldowns (
    user_id               UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    category              VARCHAR(32)  NOT NULL,
    intent_type           VARCHAR(64)  NOT NULL,
    last_idempotency_key  VARCHAR(128) NOT NULL,
    last_delivery_id      UUID         REFERENCES notification_deliveries (id) ON DELETE SET NULL,
    last_triggered_at     TIMESTAMPTZ  NOT NULL,
    cooldown_until        TIMESTAMPTZ  NOT NULL,
    version               BIGINT       NOT NULL DEFAULT 0,
    training_eligible     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, category, intent_type),
    CONSTRAINT ck_lm_cool_category CHECK (
        category IN ('STUDY_PLAN', 'SPACED_REVIEW', 'EXAM_DEADLINE', 'BILLING_TRIAL_REMINDER', 'ACCOUNT_SECURITY')
    ),
    CONSTRAINT ck_lm_cool_intent CHECK (
        intent_type IN (
            'STUDY_PLAN_DAILY_MISSION',
            'SPACED_REVIEW_DUE',
            'EXAM_DEADLINE_COUNTDOWN',
            'TRIAL_ENDING_48H_REMINDER',
            'ACCOUNT_SECURITY_ALERT'
        )
    ),
    CONSTRAINT ck_lm_cool_idem_format CHECK (last_idempotency_key ~ '^[A-Za-z0-9:._-]{8,128}$'),
    CONSTRAINT ck_lm_cool_window CHECK (cooldown_until >= last_triggered_at),
    CONSTRAINT ck_lm_cool_version_non_negative CHECK (version >= 0),
    CONSTRAINT ck_lm_cool_training_ineligible CHECK (NOT training_eligible)
);

CREATE INDEX idx_lifecycle_messaging_cooldowns_until
    ON lifecycle_messaging_cooldowns (user_id, cooldown_until DESC);

CREATE TABLE lifecycle_messaging_evaluations (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    source_module     VARCHAR(32)  NOT NULL,
    category          VARCHAR(32)  NOT NULL,
    intent_type       VARCHAR(64)  NOT NULL,
    idempotency_key   VARCHAR(128) NOT NULL,
    template_key      VARCHAR(128) NOT NULL,
    decision          VARCHAR(32)  NOT NULL,
    reason_code       VARCHAR(64)  NOT NULL,
    delivery_id       UUID         REFERENCES notification_deliveries (id) ON DELETE SET NULL,
    correlation_id    VARCHAR(64)  NOT NULL,
    training_eligible BOOLEAN      NOT NULL DEFAULT FALSE,
    evaluated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_lm_eval_source_module CHECK (
        source_module IN ('notification', 'billing', 'studyplan', 'exam', 'identity')
    ),
    CONSTRAINT ck_lm_eval_category CHECK (
        category IN ('STUDY_PLAN', 'SPACED_REVIEW', 'EXAM_DEADLINE', 'BILLING_TRIAL_REMINDER', 'ACCOUNT_SECURITY')
    ),
    CONSTRAINT ck_lm_eval_intent CHECK (
        intent_type IN (
            'STUDY_PLAN_DAILY_MISSION',
            'SPACED_REVIEW_DUE',
            'EXAM_DEADLINE_COUNTDOWN',
            'TRIAL_ENDING_48H_REMINDER',
            'ACCOUNT_SECURITY_ALERT'
        )
    ),
    CONSTRAINT ck_lm_eval_idem_format CHECK (idempotency_key ~ '^[A-Za-z0-9:._-]{8,128}$'),
    CONSTRAINT ck_lm_eval_template_non_blank CHECK (length(trim(template_key)) > 0),
    CONSTRAINT ck_lm_eval_decision CHECK (
        decision IN (
            'SCHEDULED',
            'DEFERRED_QUIET_HOURS',
            'SUPPRESSED',
            'REPLAYED',
            'SKIPPED_ALREADY_COMPLETED',
            'SKIPPED_COOLDOWN_ACTIVE',
            'SKIPPED_OPTED_OUT',
            'SKIPPED_NOT_ELIGIBLE',
            'SKIPPED_FEATURE_DISABLED',
            'CANCELLED_OBSOLETE'
        )
    ),
    CONSTRAINT ck_lm_eval_reason_non_blank CHECK (length(trim(reason_code)) > 0),
    CONSTRAINT ck_lm_eval_training_ineligible CHECK (NOT training_eligible)
);

CREATE INDEX idx_lifecycle_messaging_evaluations_user_time
    ON lifecycle_messaging_evaluations (user_id, evaluated_at DESC, id DESC);

CREATE INDEX idx_lifecycle_messaging_evaluations_user_idem
    ON lifecycle_messaging_evaluations (user_id, idempotency_key, evaluated_at DESC);
