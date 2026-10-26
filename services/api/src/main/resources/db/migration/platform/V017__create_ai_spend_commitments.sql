-- Control state; ai_usage_records remains the consolidated usage/provenance ledger.
CREATE TABLE ai_spend_accounts (
    account_key VARCHAR(256) PRIMARY KEY,
    user_id UUID NULL,
    committed_micros BIGINT NOT NULL DEFAULT 0 CHECK (committed_micros >= 0)
);
CREATE INDEX idx_ai_spend_accounts_user ON ai_spend_accounts(user_id);

CREATE TABLE ai_spend_attempts (
    operation_id UUID NOT NULL REFERENCES ai_usage_records(operation_id),
    attempt_number INTEGER NOT NULL CHECK (attempt_number BETWEEN 1 AND 8),
    provider VARCHAR(128) NOT NULL,
    model VARCHAR(256) NOT NULL,
    pricing_version VARCHAR(128) NOT NULL,
    budget_policy_version VARCHAR(128) NOT NULL,
    reserved_micros BIGINT NOT NULL CHECK (reserved_micros >= 0),
    reported_micros BIGINT NULL CHECK (reported_micros >= 0),
    input_tokens INTEGER NULL CHECK (input_tokens >= 0),
    output_tokens INTEGER NULL CHECK (output_tokens >= 0),
    image_units INTEGER NULL CHECK (image_units >= 0),
    request_units INTEGER NULL CHECK (request_units >= 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD' CHECK (currency = 'USD'),
    state VARCHAR(32) NOT NULL CHECK (state IN ('RESERVED', 'REPORTED_ESTIMATE', 'UNKNOWN')),
    failure_class VARCHAR(64) NULL,
    created_at TIMESTAMPTZ NOT NULL,
    reported_at TIMESTAMPTZ NULL,
    PRIMARY KEY(operation_id, attempt_number)
);
-- Keys refer to original UTC windows, including after late reports/month rollover.
CREATE TABLE ai_spend_allocations (
    operation_id UUID NOT NULL,
    attempt_number INTEGER NOT NULL,
    account_key VARCHAR(256) NOT NULL REFERENCES ai_spend_accounts(account_key) ON UPDATE CASCADE,
    PRIMARY KEY(operation_id, attempt_number, account_key),
    FOREIGN KEY(operation_id, attempt_number) REFERENCES ai_spend_attempts(operation_id, attempt_number)
);
CREATE INDEX idx_ai_spend_allocations_account ON ai_spend_allocations(account_key);
