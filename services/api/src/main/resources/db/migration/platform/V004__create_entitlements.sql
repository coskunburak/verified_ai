CREATE TABLE entitlements (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tier VARCHAR(24) NOT NULL,
    source VARCHAR(48) NOT NULL,
    status VARCHAR(32) NOT NULL,
    effective_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_entitlements_user_id UNIQUE (user_id),
    CONSTRAINT ck_entitlements_tier CHECK (
        tier IN ('FREE', 'PRO', 'PRO_PLUS')
    ),
    CONSTRAINT ck_entitlements_source CHECK (
        source IN ('DEFAULT_FREE', 'APP_STORE_SUBSCRIPTION', 'PROMOTIONAL', 'ADMIN_SUPPORT')
    ),
    CONSTRAINT ck_entitlements_status CHECK (
        status IN ('ACTIVE', 'GRACE_PERIOD', 'BILLING_RETRY', 'EXPIRED', 'REVOKED')
    ),
    CONSTRAINT ck_entitlements_expiry_order CHECK (
        expires_at IS NULL OR expires_at > effective_at
    )
);

CREATE INDEX ix_entitlements_user_status ON entitlements(user_id, status);
CREATE INDEX ix_entitlements_tier_status ON entitlements(tier, status);
