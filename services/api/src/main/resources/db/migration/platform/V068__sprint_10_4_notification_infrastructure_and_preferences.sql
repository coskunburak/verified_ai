-- Sprint 10.4: Notification Infrastructure and Preference Center
-- Additive forward-only migration on top of V067 (zero changes to V001..V067).
-- Enforces INV-NOTIF-001..006: default opt-out, non-degenerate quiet hours,
-- active device token isolation, idempotent delivery ledger, SKIP LOCKED dispatch index,
-- CHECK (NOT training_eligible), and ON DELETE CASCADE.

CREATE TABLE notification_preferences (
    user_id                        UUID        PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    push_enabled                   BOOLEAN     NOT NULL DEFAULT FALSE,
    study_plan_enabled             BOOLEAN     NOT NULL DEFAULT FALSE,
    spaced_review_enabled          BOOLEAN     NOT NULL DEFAULT FALSE,
    exam_deadline_enabled          BOOLEAN     NOT NULL DEFAULT FALSE,
    billing_trial_reminder_enabled BOOLEAN     NOT NULL DEFAULT FALSE,
    account_security_enabled       BOOLEAN     NOT NULL DEFAULT TRUE,
    quiet_hours_enabled            BOOLEAN     NOT NULL DEFAULT TRUE,
    quiet_hours_start_local        TIME        NOT NULL DEFAULT TIME '22:00:00',
    quiet_hours_end_local          TIME        NOT NULL DEFAULT TIME '08:00:00',
    timezone                       VARCHAR(64) NOT NULL DEFAULT 'UTC',
    daily_cap                      SMALLINT    NOT NULL DEFAULT 2,
    version                        BIGINT      NOT NULL DEFAULT 0,
    training_eligible              BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at                     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_notif_pref_security_mandatory CHECK (account_security_enabled = TRUE),
    CONSTRAINT ck_notif_pref_quiet_window_non_empty CHECK (quiet_hours_start_local <> quiet_hours_end_local),
    CONSTRAINT ck_notif_pref_timezone_non_blank CHECK (length(trim(timezone)) > 0),
    CONSTRAINT ck_notif_pref_daily_cap_bounds CHECK (daily_cap BETWEEN 0 AND 5),
    CONSTRAINT ck_notif_pref_version_non_negative CHECK (version >= 0),
    CONSTRAINT ck_notif_pref_training_ineligible CHECK (NOT training_eligible)
);

CREATE TABLE notification_devices (
    id                   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    platform             VARCHAR(16) NOT NULL,
    environment          VARCHAR(16) NOT NULL,
    device_token         TEXT        NOT NULL,
    token_sha256         CHAR(64)    NOT NULL,
    token_last4          VARCHAR(4)  NOT NULL,
    os_permission_status VARCHAR(24) NOT NULL,
    app_version          VARCHAR(32) NOT NULL,
    locale               VARCHAR(16) NOT NULL DEFAULT 'en',
    active               BOOLEAN     NOT NULL DEFAULT TRUE,
    version              BIGINT      NOT NULL DEFAULT 0,
    training_eligible    BOOLEAN     NOT NULL DEFAULT FALSE,
    last_seen_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at           TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_notification_devices_user_token_hash UNIQUE (user_id, token_sha256),
    CONSTRAINT ck_notif_dev_platform CHECK (platform IN ('IOS_APNS')),
    CONSTRAINT ck_notif_dev_environment CHECK (environment IN ('SANDBOX', 'PRODUCTION')),
    CONSTRAINT ck_notif_dev_token_len CHECK (length(trim(device_token)) BETWEEN 32 AND 512),
    CONSTRAINT ck_notif_dev_token_sha256_hex CHECK (token_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_notif_dev_token_last4 CHECK (length(token_last4) = 4),
    CONSTRAINT ck_notif_dev_os_permission CHECK (
        os_permission_status IN ('NOT_DETERMINED', 'DENIED', 'AUTHORIZED', 'PROVISIONAL', 'EPHEMERAL')
    ),
    CONSTRAINT ck_notif_dev_app_version_non_blank CHECK (length(trim(app_version)) > 0),
    CONSTRAINT ck_notif_dev_locale CHECK (locale IN ('en', 'tr')),
    CONSTRAINT ck_notif_dev_active_revoked CHECK (
        (active = TRUE AND revoked_at IS NULL) OR (active = FALSE AND revoked_at IS NOT NULL)
    ),
    CONSTRAINT ck_notif_dev_version_non_negative CHECK (version >= 0),
    CONSTRAINT ck_notif_dev_training_ineligible CHECK (NOT training_eligible)
);

CREATE UNIQUE INDEX uq_notification_devices_active_token_sha256
    ON notification_devices (token_sha256)
    WHERE active = TRUE;

CREATE INDEX idx_notification_devices_active_user
    ON notification_devices (user_id, updated_at DESC)
    WHERE active = TRUE;

CREATE TABLE notification_deliveries (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    idempotency_key      VARCHAR(128) NOT NULL,
    payload_hash         CHAR(64)     NOT NULL,
    category             VARCHAR(32)  NOT NULL,
    intent_type          VARCHAR(64)  NOT NULL,
    channel              VARCHAR(16)  NOT NULL DEFAULT 'PUSH',
    locale               VARCHAR(16)  NOT NULL DEFAULT 'en',
    template_key         VARCHAR(128) NOT NULL,
    template_params_json JSONB        NOT NULL DEFAULT '{}'::jsonb,
    deep_link_path       VARCHAR(128) NOT NULL,
    status               VARCHAR(24)  NOT NULL,
    suppression_reason   VARCHAR(64),
    failure_code         VARCHAR(64),
    scheduled_for        TIMESTAMPTZ  NOT NULL,
    effective_send_at    TIMESTAMPTZ  NOT NULL,
    expires_at           TIMESTAMPTZ  NOT NULL,
    attempt_count        SMALLINT     NOT NULL DEFAULT 0,
    max_attempts         SMALLINT     NOT NULL DEFAULT 3,
    locked_until         TIMESTAMPTZ,
    sent_at              TIMESTAMPTZ,
    provider_message_id  VARCHAR(128),
    correlation_id       VARCHAR(64)  NOT NULL,
    source_module        VARCHAR(32)  NOT NULL,
    version              BIGINT       NOT NULL DEFAULT 0,
    training_eligible    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_notification_deliveries_user_idem UNIQUE (user_id, idempotency_key),
    CONSTRAINT ck_notif_deliv_idem_format CHECK (idempotency_key ~ '^[A-Za-z0-9:._-]{8,128}$'),
    CONSTRAINT ck_notif_deliv_payload_hash_hex CHECK (payload_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_notif_deliv_category CHECK (
        category IN ('STUDY_PLAN', 'SPACED_REVIEW', 'EXAM_DEADLINE', 'BILLING_TRIAL_REMINDER', 'ACCOUNT_SECURITY')
    ),
    CONSTRAINT ck_notif_deliv_intent_type CHECK (
        intent_type IN (
            'STUDY_PLAN_DAILY_MISSION',
            'SPACED_REVIEW_DUE',
            'EXAM_DEADLINE_COUNTDOWN',
            'TRIAL_ENDING_48H_REMINDER',
            'ACCOUNT_SECURITY_ALERT'
        )
    ),
    CONSTRAINT ck_notif_deliv_channel CHECK (channel IN ('PUSH')),
    CONSTRAINT ck_notif_deliv_locale CHECK (locale IN ('en', 'tr')),
    CONSTRAINT ck_notif_deliv_template_key_non_blank CHECK (length(trim(template_key)) > 0),
    CONSTRAINT ck_notif_deliv_template_params_obj CHECK (
        jsonb_typeof(template_params_json) = 'object' AND octet_length(template_params_json::text) <= 2048
    ),
    CONSTRAINT ck_notif_deliv_deep_link_format CHECK (deep_link_path ~ '^verified-ai://[a-z0-9/_-]{1,100}$'),
    CONSTRAINT ck_notif_deliv_status CHECK (
        status IN (
            'SCHEDULED',
            'DEFERRED_QUIET_HOURS',
            'DISPATCHING',
            'SENT',
            'RETRY_SCHEDULED',
            'SUPPRESSED',
            'FAILED_PERMANENT',
            'CANCELLED'
        )
    ),
    CONSTRAINT ck_notif_deliv_suppression_reason CHECK (
        suppression_reason IS NULL OR suppression_reason IN (
            'USER_OPTED_OUT',
            'PUSH_GLOBALLY_DISABLED',
            'DAILY_CAP_REACHED',
            'NO_ACTIVE_DEVICE',
            'FEATURE_DISABLED',
            'ACCOUNT_NOT_ACTIVE',
            'INTENT_EXPIRED'
        )
    ),
    CONSTRAINT ck_notif_deliv_time_bounds CHECK (
        expires_at > scheduled_for AND effective_send_at >= scheduled_for
    ),
    CONSTRAINT ck_notif_deliv_attempts CHECK (
        attempt_count BETWEEN 0 AND 5 AND max_attempts BETWEEN 1 AND 5 AND attempt_count <= max_attempts
    ),
    CONSTRAINT ck_notif_deliv_status_consistency CHECK (
        (status <> 'SUPPRESSED' OR suppression_reason IS NOT NULL)
        AND (status <> 'SENT' OR sent_at IS NOT NULL)
    ),
    CONSTRAINT ck_notif_deliv_source_module CHECK (
        source_module IN ('notification', 'billing', 'studyplan', 'exam', 'identity')
    ),
    CONSTRAINT ck_notif_deliv_version_non_negative CHECK (version >= 0),
    CONSTRAINT ck_notif_deliv_training_ineligible CHECK (NOT training_eligible)
);

CREATE INDEX idx_notification_deliveries_due_dispatch
    ON notification_deliveries (effective_send_at ASC, id ASC)
    WHERE status IN ('SCHEDULED', 'DEFERRED_QUIET_HOURS', 'RETRY_SCHEDULED');

CREATE INDEX idx_notification_deliveries_user_history
    ON notification_deliveries (user_id, created_at DESC, id DESC);

CREATE INDEX idx_notification_deliveries_user_daily_cap
    ON notification_deliveries (user_id, sent_at DESC)
    WHERE status = 'SENT';

CREATE INDEX idx_notification_deliveries_stale_locks
    ON notification_deliveries (locked_until ASC)
    WHERE status = 'DISPATCHING';

CREATE TABLE notification_delivery_events (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    delivery_id       UUID        NOT NULL REFERENCES notification_deliveries (id) ON DELETE CASCADE,
    user_id           UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    from_status       VARCHAR(24),
    to_status         VARCHAR(24) NOT NULL,
    reason_code       VARCHAR(64) NOT NULL,
    correlation_id    VARCHAR(64) NOT NULL,
    training_eligible BOOLEAN     NOT NULL DEFAULT FALSE,
    occurred_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_notif_evt_from_status CHECK (
        from_status IS NULL OR from_status IN (
            'SCHEDULED', 'DEFERRED_QUIET_HOURS', 'DISPATCHING', 'SENT',
            'RETRY_SCHEDULED', 'SUPPRESSED', 'FAILED_PERMANENT', 'CANCELLED'
        )
    ),
    CONSTRAINT ck_notif_evt_to_status CHECK (
        to_status IN (
            'SCHEDULED', 'DEFERRED_QUIET_HOURS', 'DISPATCHING', 'SENT',
            'RETRY_SCHEDULED', 'SUPPRESSED', 'FAILED_PERMANENT', 'CANCELLED'
        )
    ),
    CONSTRAINT ck_notif_evt_reason_non_blank CHECK (length(trim(reason_code)) > 0),
    CONSTRAINT ck_notif_evt_training_ineligible CHECK (NOT training_eligible)
);

CREATE INDEX idx_notification_delivery_events_delivery
    ON notification_delivery_events (delivery_id, occurred_at ASC);

CREATE INDEX idx_notification_delivery_events_user
    ON notification_delivery_events (user_id, occurred_at DESC);
