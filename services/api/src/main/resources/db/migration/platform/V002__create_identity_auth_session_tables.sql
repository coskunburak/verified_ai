CREATE TABLE users (
    id UUID PRIMARY KEY,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    deletion_requested_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'DISABLED', 'DELETION_REQUESTED', 'DELETED'))
);

CREATE TABLE user_identities (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    provider VARCHAR(32) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_user_identities_provider CHECK (provider IN ('APPLE')),
    CONSTRAINT uq_user_identities_provider_subject UNIQUE (provider, provider_subject)
);

CREATE INDEX ix_user_identities_user_id ON user_identities(user_id);

CREATE TABLE sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    revocation_reason VARCHAR(80),
    CONSTRAINT ck_sessions_status CHECK (status IN ('ACTIVE', 'REVOKED', 'EXPIRED'))
);

CREATE INDEX ix_sessions_user_id_created_at ON sessions(user_id, created_at DESC);
CREATE INDEX ix_sessions_active_expires_at ON sessions(expires_at) WHERE revoked_at IS NULL;

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    session_id UUID NOT NULL REFERENCES sessions(id),
    family_id UUID NOT NULL,
    token_hash VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    replaced_by_id UUID,
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_replaced_by FOREIGN KEY (replaced_by_id) REFERENCES refresh_tokens(id)
);

CREATE INDEX ix_refresh_tokens_session_id_created_at ON refresh_tokens(session_id, created_at DESC);
CREATE INDEX ix_refresh_tokens_family_id ON refresh_tokens(family_id);

CREATE TABLE auth_security_events (
    id UUID PRIMARY KEY,
    event_type VARCHAR(80) NOT NULL,
    user_id UUID REFERENCES users(id),
    session_id UUID,
    reason VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_auth_security_events_created_at ON auth_security_events(created_at DESC);
