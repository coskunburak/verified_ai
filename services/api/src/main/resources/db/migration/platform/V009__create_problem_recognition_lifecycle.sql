ALTER TABLE problem_asset_derivatives
    ADD CONSTRAINT uq_problem_asset_derivatives_id_user UNIQUE (id, user_id);

CREATE TABLE recognition_jobs (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    problem_session_id UUID NOT NULL,
    source_asset_id UUID NOT NULL,
    input_derivative_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    capability VARCHAR(32) NOT NULL,
    prompt_id VARCHAR(64) NOT NULL,
    prompt_version VARCHAR(32) NOT NULL,
    schema_version VARCHAR(64) NOT NULL,
    route_policy_version VARCHAR(64) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    last_error_code VARCHAR(64),
    last_failure_class VARCHAR(64),
    review_required BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_recognition_jobs_session_user FOREIGN KEY (problem_session_id, user_id)
        REFERENCES problem_sessions(id, user_id) ON DELETE CASCADE,
    CONSTRAINT fk_recognition_jobs_source_user FOREIGN KEY (source_asset_id, user_id)
        REFERENCES problem_assets(id, user_id) ON DELETE CASCADE,
    CONSTRAINT fk_recognition_jobs_input_derivative_user FOREIGN KEY (input_derivative_id, user_id)
        REFERENCES problem_asset_derivatives(id, user_id) ON DELETE CASCADE,
    CONSTRAINT ck_recognition_jobs_status CHECK (
        status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED_RETRYABLE', 'FAILED_TERMINAL')
    ),
    CONSTRAINT ck_recognition_jobs_capability CHECK (capability = 'VISION_PARSE'),
    CONSTRAINT ck_recognition_jobs_attempts CHECK (
        attempt_count >= 0 AND max_attempts > 0 AND attempt_count <= max_attempts
    ),
    CONSTRAINT ck_recognition_jobs_terminal_completion CHECK (
        (status IN ('SUCCEEDED', 'FAILED_TERMINAL') AND completed_at IS NOT NULL)
        OR (status NOT IN ('SUCCEEDED', 'FAILED_TERMINAL'))
    ),
    CONSTRAINT uq_recognition_jobs_logical_input UNIQUE (
        user_id,
        problem_session_id,
        input_derivative_id,
        capability,
        prompt_id,
        prompt_version,
        schema_version
    )
);

CREATE INDEX ix_recognition_jobs_user_created
    ON recognition_jobs(user_id, created_at DESC);

CREATE INDEX ix_recognition_jobs_session_created
    ON recognition_jobs(problem_session_id, created_at DESC);

CREATE INDEX ix_recognition_jobs_due
    ON recognition_jobs(status, next_attempt_at)
    WHERE status IN ('QUEUED', 'FAILED_RETRYABLE');

CREATE INDEX ix_recognition_jobs_running_updated
    ON recognition_jobs(status, updated_at)
    WHERE status = 'RUNNING';

CREATE TABLE recognition_evidence (
    id UUID PRIMARY KEY,
    recognition_job_id UUID NOT NULL,
    user_id UUID NOT NULL,
    problem_session_id UUID NOT NULL,
    source_asset_id UUID NOT NULL,
    input_derivative_id UUID NOT NULL,
    revision INTEGER NOT NULL,
    capability VARCHAR(32) NOT NULL,
    schema_version VARCHAR(64) NOT NULL,
    raw_output_jsonb JSONB NOT NULL,
    normalized_evidence_jsonb JSONB NOT NULL,
    upstream_quality_evidence_jsonb JSONB NOT NULL,
    provider VARCHAR(64) NOT NULL,
    model VARCHAR(128) NOT NULL,
    route_policy_version VARCHAR(64) NOT NULL,
    prompt_id VARCHAR(64) NOT NULL,
    prompt_version VARCHAR(32) NOT NULL,
    provider_request_id VARCHAR(128),
    provider_response_id VARCHAR(128),
    input_tokens INTEGER,
    output_tokens INTEGER,
    image_units INTEGER,
    request_units INTEGER NOT NULL,
    provider_latency_ms BIGINT NOT NULL,
    total_latency_ms BIGINT NOT NULL,
    estimated_cost_micros BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    pricing_version VARCHAR(64) NOT NULL,
    raw_output_retention_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_recognition_evidence_job FOREIGN KEY (recognition_job_id)
        REFERENCES recognition_jobs(id) ON DELETE CASCADE,
    CONSTRAINT fk_recognition_evidence_session_user FOREIGN KEY (problem_session_id, user_id)
        REFERENCES problem_sessions(id, user_id) ON DELETE CASCADE,
    CONSTRAINT fk_recognition_evidence_source_user FOREIGN KEY (source_asset_id, user_id)
        REFERENCES problem_assets(id, user_id) ON DELETE CASCADE,
    CONSTRAINT fk_recognition_evidence_input_derivative_user FOREIGN KEY (input_derivative_id, user_id)
        REFERENCES problem_asset_derivatives(id, user_id) ON DELETE CASCADE,
    CONSTRAINT ck_recognition_evidence_revision CHECK (revision > 0),
    CONSTRAINT ck_recognition_evidence_capability CHECK (capability = 'VISION_PARSE'),
    CONSTRAINT ck_recognition_evidence_json_objects CHECK (
        jsonb_typeof(raw_output_jsonb) = 'object'
        AND jsonb_typeof(normalized_evidence_jsonb) = 'object'
        AND jsonb_typeof(upstream_quality_evidence_jsonb) = 'object'
    ),
    CONSTRAINT ck_recognition_evidence_usage CHECK (
        (input_tokens IS NULL OR input_tokens >= 0)
        AND (output_tokens IS NULL OR output_tokens >= 0)
        AND (image_units IS NULL OR image_units >= 0)
        AND request_units >= 0
    ),
    CONSTRAINT ck_recognition_evidence_latency_cost CHECK (
        provider_latency_ms >= 0
        AND total_latency_ms >= 0
        AND estimated_cost_micros >= 0
    ),
    CONSTRAINT uq_recognition_evidence_job UNIQUE (recognition_job_id),
    CONSTRAINT uq_recognition_evidence_revision UNIQUE (
        problem_session_id,
        input_derivative_id,
        schema_version,
        prompt_version,
        revision
    )
);

CREATE INDEX ix_recognition_evidence_user_created
    ON recognition_evidence(user_id, created_at DESC);

CREATE INDEX ix_recognition_evidence_session_created
    ON recognition_evidence(problem_session_id, created_at DESC);
