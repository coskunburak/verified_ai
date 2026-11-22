-- V067__sprint_10_3_entitlement_quota_ledgers.sql
-- Sprint 10.3: Usage Quotas, AI Budgets, Credits, and Fair-Use Enforcement

CREATE TABLE IF NOT EXISTS entitlement_quota_ledgers (
    id                           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    period_key                   VARCHAR(16) NOT NULL, -- 'YYYY-MM-DD' (FREE daily) or 'YYYY-MM' (monthly)
    tier_code                    VARCHAR(32) NOT NULL CHECK (tier_code IN ('FREE', 'PRO', 'COACH_PRO', 'YKS_SEASON_PASS')),
    ai_verifications_used        INTEGER NOT NULL DEFAULT 0 CHECK (ai_verifications_used >= 0),
    ai_verifications_limit       INTEGER NOT NULL CHECK (ai_verifications_limit >= 0),
    hybrid_twins_used            INTEGER NOT NULL DEFAULT 0 CHECK (hybrid_twins_used >= 0),
    hybrid_twins_limit           INTEGER NOT NULL CHECK (hybrid_twins_limit >= 0),
    accumulated_ai_cost_micros   BIGINT NOT NULL DEFAULT 0 CHECK (accumulated_ai_cost_micros >= 0),
    accumulated_ai_cost_usd      NUMERIC(12, 6) GENERATED ALWAYS AS (accumulated_ai_cost_micros / 1000000.0) STORED,
    ai_cost_cap_micros           BIGINT NOT NULL CHECK (ai_cost_cap_micros >= 0),
    cost_circuit_breaker_tripped BOOLEAN NOT NULL DEFAULT FALSE,
    resets_at                    TIMESTAMPTZ NOT NULL,
    training_eligible            BOOLEAN NOT NULL DEFAULT FALSE CHECK (NOT training_eligible),
    created_at                   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version                      BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_entitlement_quota_ledgers_user_period UNIQUE (user_id, period_key)
);

CREATE INDEX IF NOT EXISTS idx_quota_ledgers_user_updated
    ON entitlement_quota_ledgers (user_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_quota_ledgers_breaker_tripped
    ON entitlement_quota_ledgers (cost_circuit_breaker_tripped)
    WHERE cost_circuit_breaker_tripped = TRUE;

-- Idempotent consumption and reservation tracking table (prevents double consumption)
CREATE TABLE IF NOT EXISTS entitlement_quota_consumptions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ledger_id           UUID NOT NULL REFERENCES entitlement_quota_ledgers(id) ON DELETE CASCADE,
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    idempotency_key     VARCHAR(128) NOT NULL,
    quota_type          VARCHAR(32) NOT NULL CHECK (quota_type IN ('AI_VERIFICATION', 'HYBRID_TWIN', 'HANDWRITING_DIAGNOSTIC')),
    units_consumed      INTEGER NOT NULL DEFAULT 1 CHECK (units_consumed > 0),
    cost_micros         BIGINT NOT NULL DEFAULT 0 CHECK (cost_micros >= 0),
    status              VARCHAR(24) NOT NULL CHECK (status IN ('RESERVED', 'COMMITTED', 'RELEASED')),
    training_eligible   BOOLEAN NOT NULL DEFAULT FALSE CHECK (NOT training_eligible),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_quota_consumptions_user_idem UNIQUE (user_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_quota_consumptions_ledger
    ON entitlement_quota_consumptions (ledger_id, created_at DESC);
