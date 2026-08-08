ALTER TABLE users
    DROP CONSTRAINT ck_users_status,
    ADD CONSTRAINT ck_users_status CHECK (
        status IN ('ACTIVE', 'DISABLED', 'DELETION_REQUESTED', 'DELETION_IN_PROGRESS', 'DELETED')
    );

CREATE TABLE data_exports (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    status VARCHAR(32) NOT NULL,
    schema_version VARCHAR(24) NOT NULL,
    content_json JSONB NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    downloaded_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL,
    failure_reason VARCHAR(255),
    CONSTRAINT ck_data_exports_status CHECK (
        status IN ('READY', 'EXPIRED', 'FAILED')
    )
);

CREATE INDEX ix_data_exports_user_requested
    ON data_exports(user_id, requested_at DESC);

CREATE INDEX ix_data_exports_expiry
    ON data_exports(status, expires_at);

CREATE TABLE privacy_events (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    event_type VARCHAR(80) NOT NULL,
    reason VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_privacy_events_user_created
    ON privacy_events(user_id, created_at DESC);

CREATE INDEX ix_privacy_events_type_created
    ON privacy_events(event_type, created_at DESC);
