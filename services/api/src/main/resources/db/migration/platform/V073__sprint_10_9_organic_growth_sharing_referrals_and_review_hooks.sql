-- Sprint 10.9: Organic Growth Loops, Sharing, Referral, and Store Optimization Hooks
-- Additive forward-only migration on top of V072 (zero changes to V001..V072).
-- Enforces INV-GRW-001..007: PII-free revocable share artifacts, anti-abuse referral
-- codes/attributions, non-spammy store review prompt ledger, CHECK (NOT training_eligible),
-- and ON DELETE CASCADE.

CREATE TABLE growth_referral_codes (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    referral_code     VARCHAR(16) NOT NULL,
    active            BOOLEAN     NOT NULL DEFAULT TRUE,
    version           BIGINT      NOT NULL DEFAULT 0,
    training_eligible BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_growth_referral_codes_user UNIQUE (user_id),
    CONSTRAINT uq_growth_referral_codes_code UNIQUE (referral_code),
    CONSTRAINT ck_grw_ref_code_format CHECK (referral_code ~ '^[A-Z2-9]{6,16}$'),
    CONSTRAINT ck_grw_ref_code_version_non_negative CHECK (version >= 0),
    CONSTRAINT ck_grw_ref_code_training_ineligible CHECK (NOT training_eligible)
);

CREATE TABLE growth_referral_attributions (
    id                      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    referral_code_id        UUID         NOT NULL REFERENCES growth_referral_codes (id) ON DELETE CASCADE,
    referrer_user_id        UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    referred_user_id        UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    referral_code_snapshot  VARCHAR(16)  NOT NULL,
    idempotency_key         VARCHAR(128) NOT NULL,
    payload_hash            CHAR(64)     NOT NULL,
    device_fingerprint_hash CHAR(64),
    attribution_source      VARCHAR(32)  NOT NULL,
    status                  VARCHAR(24)  NOT NULL DEFAULT 'ATTRIBUTED',
    abuse_reason_code       VARCHAR(64),
    reward_type             VARCHAR(40),
    reward_units            INTEGER      NOT NULL DEFAULT 0,
    correlation_id          VARCHAR(64)  NOT NULL,
    version                 BIGINT       NOT NULL DEFAULT 0,
    training_eligible       BOOLEAN      NOT NULL DEFAULT FALSE,
    attributed_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    qualified_at            TIMESTAMPTZ,
    reward_granted_at       TIMESTAMPTZ,
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_grw_ref_attr_referred_user UNIQUE (referred_user_id),
    CONSTRAINT uq_grw_ref_attr_user_idem UNIQUE (referred_user_id, idempotency_key),
    CONSTRAINT ck_grw_ref_attr_no_self CHECK (referrer_user_id <> referred_user_id),
    CONSTRAINT ck_grw_ref_attr_code_format CHECK (referral_code_snapshot ~ '^[A-Z2-9]{6,16}$'),
    CONSTRAINT ck_grw_ref_attr_idem_format CHECK (idempotency_key ~ '^[A-Za-z0-9:._-]{8,128}$'),
    CONSTRAINT ck_grw_ref_attr_payload_hash_hex CHECK (payload_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_grw_ref_attr_device_hash_hex CHECK (
        device_fingerprint_hash IS NULL OR device_fingerprint_hash ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_grw_ref_attr_source CHECK (
        attribution_source IN ('DEEP_LINK', 'SHARE_CARD', 'MANUAL_CODE', 'WEEKLY_PDF_QR')
    ),
    CONSTRAINT ck_grw_ref_attr_status CHECK (
        status IN ('ATTRIBUTED', 'QUALIFIED', 'REWARD_GRANTED', 'REJECTED_ABUSE', 'EXPIRED')
    ),
    CONSTRAINT ck_grw_ref_attr_reward_units CHECK (reward_units BETWEEN 0 AND 20),
    CONSTRAINT ck_grw_ref_attr_state_consistency CHECK (
        (status <> 'QUALIFIED' OR qualified_at IS NOT NULL)
        AND (status <> 'REWARD_GRANTED' OR (qualified_at IS NOT NULL AND reward_granted_at IS NOT NULL AND reward_units > 0))
        AND (status <> 'REJECTED_ABUSE' OR abuse_reason_code IS NOT NULL)
    ),
    CONSTRAINT ck_grw_ref_attr_version_non_negative CHECK (version >= 0),
    CONSTRAINT ck_grw_ref_attr_training_ineligible CHECK (NOT training_eligible)
);

CREATE UNIQUE INDEX uq_grw_ref_attr_referrer_device
    ON growth_referral_attributions (referrer_user_id, device_fingerprint_hash)
    WHERE device_fingerprint_hash IS NOT NULL;

CREATE INDEX idx_grw_ref_attr_referrer_time
    ON growth_referral_attributions (referrer_user_id, attributed_at DESC);

CREATE INDEX idx_grw_ref_attr_device_hash
    ON growth_referral_attributions (device_fingerprint_hash)
    WHERE device_fingerprint_hash IS NOT NULL;

CREATE TABLE growth_shareable_artifacts (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    share_token           VARCHAR(64)  NOT NULL,
    idempotency_key       VARCHAR(128) NOT NULL,
    payload_hash          CHAR(64)     NOT NULL,
    artifact_type         VARCHAR(36)  NOT NULL,
    locale                VARCHAR(16)  NOT NULL DEFAULT 'en',
    display_alias         VARCHAR(32),
    referral_code         VARCHAR(16),
    summary_payload_json  JSONB        NOT NULL,
    deep_link_url         VARCHAR(160) NOT NULL,
    status                VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    raw_content_redacted  BOOLEAN      NOT NULL DEFAULT TRUE,
    open_count            BIGINT       NOT NULL DEFAULT 0,
    correlation_id        VARCHAR(64)  NOT NULL,
    version               BIGINT       NOT NULL DEFAULT 0,
    training_eligible     BOOLEAN      NOT NULL DEFAULT FALSE,
    expires_at            TIMESTAMPTZ  NOT NULL,
    revoked_at            TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_grw_share_token UNIQUE (share_token),
    CONSTRAINT uq_grw_share_user_idem UNIQUE (user_id, idempotency_key),
    CONSTRAINT ck_grw_share_token_format CHECK (share_token ~ '^[A-Za-z0-9_-]{16,48}$'),
    CONSTRAINT ck_grw_share_idem_format CHECK (idempotency_key ~ '^[A-Za-z0-9:._-]{8,128}$'),
    CONSTRAINT ck_grw_share_payload_hash_hex CHECK (payload_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_grw_share_type CHECK (
        artifact_type IN (
            'WEEKLY_PROGRESS_SUMMARY',
            'MASTERY_MILESTONE',
            'COUNTERFACTUAL_CHALLENGE_CARD',
            'EXAM_READINESS_MILESTONE'
        )
    ),
    CONSTRAINT ck_grw_share_locale CHECK (locale IN ('en', 'tr')),
    CONSTRAINT ck_grw_share_referral_format CHECK (
        referral_code IS NULL OR referral_code ~ '^[A-Z2-9]{6,16}$'
    ),
    CONSTRAINT ck_grw_share_summary_json CHECK (
        jsonb_typeof(summary_payload_json) = 'object'
        AND octet_length(summary_payload_json::text) <= 2048
    ),
    CONSTRAINT ck_grw_share_deep_link CHECK (deep_link_url ~ '^verified-ai://share/[A-Za-z0-9_-]{16,48}$'),
    CONSTRAINT ck_grw_share_status CHECK (status IN ('ACTIVE', 'REVOKED', 'EXPIRED')),
    CONSTRAINT ck_grw_share_redacted_mandatory CHECK (raw_content_redacted = TRUE),
    CONSTRAINT ck_grw_share_open_count_non_negative CHECK (open_count >= 0),
    CONSTRAINT ck_grw_share_expiry_bounds CHECK (
        expires_at > created_at AND expires_at <= created_at + INTERVAL '90 days'
    ),
    CONSTRAINT ck_grw_share_revoked_consistency CHECK (
        (status = 'REVOKED' AND revoked_at IS NOT NULL) OR (status <> 'REVOKED' AND revoked_at IS NULL)
    ),
    CONSTRAINT ck_grw_share_version_non_negative CHECK (version >= 0),
    CONSTRAINT ck_grw_share_training_ineligible CHECK (NOT training_eligible)
);

CREATE INDEX idx_grw_share_user_created
    ON growth_shareable_artifacts (user_id, created_at DESC, id DESC);

CREATE TABLE growth_store_review_events (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    idempotency_key   VARCHAR(128) NOT NULL,
    payload_hash      CHAR(64)     NOT NULL,
    trigger_moment    VARCHAR(40)  NOT NULL,
    outcome           VARCHAR(32)  NOT NULL,
    app_version       VARCHAR(32)  NOT NULL,
    locale            VARCHAR(16)  NOT NULL DEFAULT 'en',
    correlation_id    VARCHAR(64)  NOT NULL,
    training_eligible BOOLEAN      NOT NULL DEFAULT FALSE,
    occurred_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_grw_review_user_idem UNIQUE (user_id, idempotency_key),
    CONSTRAINT ck_grw_review_idem_format CHECK (idempotency_key ~ '^[A-Za-z0-9:._-]{8,128}$'),
    CONSTRAINT ck_grw_review_payload_hash_hex CHECK (payload_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_grw_review_trigger CHECK (
        trigger_moment IN (
            'INDEPENDENT_TWIN_MASTERED',
            'MASTERY_MILESTONE_UNLOCKED',
            'WEEKLY_PROGRESS_WIN',
            'MOCK_EXAM_COMPLETED'
        )
    ),
    CONSTRAINT ck_grw_review_outcome CHECK (
        outcome IN ('PROMPT_SHOWN', 'PROMPT_DISMISSED', 'PROMPT_DEFERRED', 'PROMPT_DECLINED_PERMANENTLY')
    ),
    CONSTRAINT ck_grw_review_app_version_non_blank CHECK (length(trim(app_version)) BETWEEN 1 AND 32),
    CONSTRAINT ck_grw_review_locale CHECK (locale IN ('en', 'tr')),
    CONSTRAINT ck_grw_review_training_ineligible CHECK (NOT training_eligible)
);

CREATE INDEX idx_grw_review_user_occurred
    ON growth_store_review_events (user_id, occurred_at DESC);
