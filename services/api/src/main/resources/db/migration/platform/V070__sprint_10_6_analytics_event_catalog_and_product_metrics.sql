-- Sprint 10.6: Analytics Event Catalog and Product Metrics Implementation
-- Additive forward-only migration on top of V069 (zero changes to V001..V069).
-- Enforces INV-ANL-001..007: explicit relational dimensions, (user_id, client_event_id) idempotency,
-- low-cardinality bounds, CHECK (NOT training_eligible), and ON DELETE CASCADE.

CREATE TABLE analytics_events (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    client_event_id   VARCHAR(128) NOT NULL,
    payload_hash      CHAR(64)     NOT NULL,
    event_name        VARCHAR(64)  NOT NULL,
    event_domain      VARCHAR(32)  NOT NULL,
    catalog_version   VARCHAR(16)  NOT NULL DEFAULT '1.0.0',
    emitted_by        VARCHAR(16)  NOT NULL,
    source_module     VARCHAR(32)  NOT NULL,
    entitlement_tier  VARCHAR(24)  NOT NULL DEFAULT 'FREE',
    locale            VARCHAR(16)  NOT NULL DEFAULT 'en',
    app_version       VARCHAR(32),
    session_ref_hash  CHAR(64),
    latency_ms        INTEGER,
    ai_cost_micros    BIGINT       NOT NULL DEFAULT 0,
    properties_json   JSONB        NOT NULL DEFAULT '{}'::jsonb,
    correlation_id    VARCHAR(64)  NOT NULL,
    occurred_at       TIMESTAMPTZ  NOT NULL,
    received_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    training_eligible BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_analytics_events_user_client_id UNIQUE (user_id, client_event_id),
    CONSTRAINT ck_anl_evt_client_id_format CHECK (client_event_id ~ '^[A-Za-z0-9:._-]{8,128}$'),
    CONSTRAINT ck_anl_evt_payload_hash_hex CHECK (payload_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_anl_evt_session_hash_hex CHECK (session_ref_hash IS NULL OR session_ref_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_anl_evt_domain CHECK (
        event_domain IN (
            'ACQUISITION_ACTIVATION',
            'SOLVING',
            'TUTOR',
            'LEARNING_MASTERY',
            'EXAM',
            'BILLING_MONETIZATION',
            'LIFECYCLE_RETENTION'
        )
    ),
    CONSTRAINT ck_anl_evt_emitted_by CHECK (emitted_by IN ('CLIENT', 'SERVER')),
    CONSTRAINT ck_anl_evt_source_module CHECK (
        source_module IN (
            'ios_client', 'analytics', 'identity', 'profile', 'problem', 'solving',
            'verification', 'tutoring', 'attempt', 'mistake', 'mastery',
            'studyplan', 'exam', 'billing', 'notification'
        )
    ),
    CONSTRAINT ck_anl_evt_tier CHECK (
        entitlement_tier IN ('FREE', 'PRO', 'COACH_PRO', 'YKS_SEASON_PASS', 'UNKNOWN')
    ),
    CONSTRAINT ck_anl_evt_locale CHECK (locale IN ('en', 'tr')),
    CONSTRAINT ck_anl_evt_latency_bounds CHECK (latency_ms IS NULL OR latency_ms BETWEEN 0 AND 600000),
    CONSTRAINT ck_anl_evt_ai_cost_non_negative CHECK (ai_cost_micros >= 0),
    CONSTRAINT ck_anl_evt_properties_obj CHECK (
        jsonb_typeof(properties_json) = 'object' AND octet_length(properties_json::text) <= 2048
    ),
    CONSTRAINT ck_anl_evt_training_ineligible CHECK (NOT training_eligible)
);

CREATE INDEX idx_analytics_events_user_occurred
    ON analytics_events (user_id, occurred_at DESC, id DESC);

CREATE INDEX idx_analytics_events_name_occurred
    ON analytics_events (event_name, occurred_at DESC);

CREATE INDEX idx_analytics_events_domain_tier_occurred
    ON analytics_events (event_domain, entitlement_tier, occurred_at DESC);

CREATE INDEX idx_analytics_events_latency_samples
    ON analytics_events (event_name, occurred_at DESC)
    WHERE latency_ms IS NOT NULL;

CREATE TABLE analytics_daily_metric_rollups (
    metric_date          DATE        NOT NULL,
    entitlement_tier     VARCHAR(24) NOT NULL,
    event_domain         VARCHAR(32) NOT NULL,
    event_name           VARCHAR(64) NOT NULL,
    event_count          BIGINT      NOT NULL DEFAULT 0,
    distinct_user_count  BIGINT      NOT NULL DEFAULT 0,
    total_ai_cost_micros BIGINT      NOT NULL DEFAULT 0,
    p50_latency_ms       INTEGER,
    p95_latency_ms       INTEGER,
    computed_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    training_eligible    BOOLEAN     NOT NULL DEFAULT FALSE,
    PRIMARY KEY (metric_date, entitlement_tier, event_domain, event_name),
    CONSTRAINT ck_anl_rollup_tier CHECK (
        entitlement_tier IN ('ALL', 'FREE', 'PRO', 'COACH_PRO', 'YKS_SEASON_PASS', 'UNKNOWN')
    ),
    CONSTRAINT ck_anl_rollup_domain CHECK (
        event_domain IN (
            'ACQUISITION_ACTIVATION',
            'SOLVING',
            'TUTOR',
            'LEARNING_MASTERY',
            'EXAM',
            'BILLING_MONETIZATION',
            'LIFECYCLE_RETENTION'
        )
    ),
    CONSTRAINT ck_anl_rollup_counts_non_negative CHECK (
        event_count >= 0 AND distinct_user_count >= 0 AND total_ai_cost_micros >= 0
    ),
    CONSTRAINT ck_anl_rollup_latency_bounds CHECK (
        (p50_latency_ms IS NULL OR p50_latency_ms >= 0)
        AND (p95_latency_ms IS NULL OR p95_latency_ms >= 0)
        AND (p50_latency_ms IS NULL OR p95_latency_ms IS NULL OR p95_latency_ms >= p50_latency_ms)
    ),
    CONSTRAINT ck_anl_rollup_training_ineligible CHECK (NOT training_eligible)
);

CREATE INDEX idx_analytics_daily_rollups_date_tier
    ON analytics_daily_metric_rollups (metric_date DESC, entitlement_tier);
