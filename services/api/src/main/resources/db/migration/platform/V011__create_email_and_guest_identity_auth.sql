ALTER TABLE user_identities DROP CONSTRAINT ck_user_identities_provider;

ALTER TABLE user_identities
    ADD CONSTRAINT ck_user_identities_provider CHECK (provider IN ('APPLE', 'EMAIL', 'GUEST'));

CREATE TABLE user_password_credentials (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    email_normalized VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    password_algorithm VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    last_used_at TIMESTAMPTZ,
    CONSTRAINT uq_user_password_credentials_user UNIQUE (user_id),
    CONSTRAINT uq_user_password_credentials_email UNIQUE (email_normalized),
    CONSTRAINT ck_user_password_credentials_email CHECK (
        email_normalized = lower(email_normalized)
        AND length(email_normalized) BETWEEN 3 AND 320
        AND position('@' in email_normalized) > 1
    ),
    CONSTRAINT ck_user_password_credentials_algorithm CHECK (password_algorithm IN ('BCRYPT')),
    CONSTRAINT ck_user_password_credentials_hash CHECK (length(password_hash) BETWEEN 40 AND 255)
);

CREATE INDEX ix_user_password_credentials_email ON user_password_credentials(email_normalized);
