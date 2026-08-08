ALTER TABLE entitlements
    ADD COLUMN original_transaction_id VARCHAR(128),
    ADD COLUMN environment VARCHAR(24),
    ADD COLUMN last_verified_at TIMESTAMPTZ;

CREATE INDEX ix_entitlements_original_transaction
    ON entitlements(environment, original_transaction_id)
    WHERE original_transaction_id IS NOT NULL;

CREATE TABLE commerce_account_tokens (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    app_account_token UUID NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE app_store_transactions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    transaction_id VARCHAR(128) NOT NULL,
    original_transaction_id VARCHAR(128) NOT NULL,
    product_id VARCHAR(255) NOT NULL,
    subscription_group_id VARCHAR(128),
    app_account_token UUID,
    environment VARCHAR(24) NOT NULL,
    purchase_date TIMESTAMPTZ,
    original_purchase_date TIMESTAMPTZ,
    expires_date TIMESTAMPTZ,
    revocation_date TIMESTAMPTZ,
    transaction_reason VARCHAR(64),
    ownership_type VARCHAR(64),
    signed_date TIMESTAMPTZ,
    payload_digest VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_app_store_transactions_environment_transaction UNIQUE (environment, transaction_id),
    CONSTRAINT ck_app_store_transactions_environment CHECK (
        environment IN ('XCODE', 'LOCAL_TESTING', 'SANDBOX', 'PRODUCTION')
    )
);

CREATE INDEX ix_app_store_transactions_user_purchase
    ON app_store_transactions(user_id, purchase_date DESC NULLS LAST);

CREATE INDEX ix_app_store_transactions_original
    ON app_store_transactions(environment, original_transaction_id);

CREATE TABLE app_store_subscriptions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    original_transaction_id VARCHAR(128) NOT NULL,
    current_transaction_id VARCHAR(128) NOT NULL,
    product_id VARCHAR(255) NOT NULL,
    subscription_group_id VARCHAR(128),
    environment VARCHAR(24) NOT NULL,
    status VARCHAR(32) NOT NULL,
    auto_renew_status BOOLEAN,
    renewal_product_id VARCHAR(255),
    expires_at TIMESTAMPTZ,
    grace_period_expires_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    last_transaction_signed_at TIMESTAMPTZ,
    last_renewal_signed_at TIMESTAMPTZ,
    last_reconciled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_app_store_subscriptions_environment_original UNIQUE (environment, original_transaction_id),
    CONSTRAINT ck_app_store_subscriptions_environment CHECK (
        environment IN ('XCODE', 'LOCAL_TESTING', 'SANDBOX', 'PRODUCTION')
    ),
    CONSTRAINT ck_app_store_subscriptions_status CHECK (
        status IN ('ACTIVE', 'GRACE_PERIOD', 'BILLING_RETRY', 'EXPIRED', 'REVOKED')
    )
);

CREATE INDEX ix_app_store_subscriptions_user_status
    ON app_store_subscriptions(user_id, status, expires_at DESC NULLS LAST);

CREATE TABLE app_store_notifications (
    id UUID PRIMARY KEY,
    notification_uuid VARCHAR(128) NOT NULL UNIQUE,
    notification_type VARCHAR(80) NOT NULL,
    subtype VARCHAR(80),
    environment VARCHAR(24) NOT NULL,
    app_store_status INTEGER,
    signed_date TIMESTAMPTZ,
    processing_status VARCHAR(32) NOT NULL,
    payload_digest VARCHAR(64) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    failure_code VARCHAR(80),
    failure_message VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_app_store_notifications_environment CHECK (
        environment IN ('XCODE', 'LOCAL_TESTING', 'SANDBOX', 'PRODUCTION')
    ),
    CONSTRAINT ck_app_store_notifications_processing_status CHECK (
        processing_status IN ('RECEIVED', 'PROCESSING', 'PROCESSED', 'FAILED_RETRYABLE', 'FAILED_TERMINAL')
    )
);

CREATE INDEX ix_app_store_notifications_status_received
    ON app_store_notifications(processing_status, received_at);

CREATE INDEX ix_app_store_notifications_type
    ON app_store_notifications(notification_type, subtype);

CREATE TABLE billing_events (
    id UUID PRIMARY KEY,
    external_event_id VARCHAR(180) NOT NULL UNIQUE,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    event_type VARCHAR(80) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    result VARCHAR(64) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);
