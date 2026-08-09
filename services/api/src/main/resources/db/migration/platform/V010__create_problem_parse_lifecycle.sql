ALTER TABLE recognition_evidence
    ADD CONSTRAINT uq_recognition_evidence_id_user_session_revision UNIQUE (
        id,
        user_id,
        problem_session_id,
        revision
    );

CREATE TABLE problem_parse_jobs (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    problem_session_id UUID NOT NULL,
    recognition_evidence_id UUID NOT NULL,
    recognition_evidence_revision INTEGER NOT NULL,
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
    CONSTRAINT fk_problem_parse_jobs_session_user FOREIGN KEY (problem_session_id, user_id)
        REFERENCES problem_sessions(id, user_id) ON DELETE CASCADE,
    CONSTRAINT fk_problem_parse_jobs_evidence_user_session_revision FOREIGN KEY (
        recognition_evidence_id,
        user_id,
        problem_session_id,
        recognition_evidence_revision
    )
        REFERENCES recognition_evidence(id, user_id, problem_session_id, revision) ON DELETE CASCADE,
    CONSTRAINT ck_problem_parse_jobs_status CHECK (
        status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED_RETRYABLE', 'FAILED_TERMINAL', 'UNSUPPORTED')
    ),
    CONSTRAINT ck_problem_parse_jobs_capability CHECK (capability = 'PROBLEM_NORMALIZE'),
    CONSTRAINT ck_problem_parse_jobs_attempts CHECK (
        attempt_count >= 0 AND max_attempts > 0 AND attempt_count <= max_attempts
    ),
    CONSTRAINT ck_problem_parse_jobs_terminal_completion CHECK (
        (status IN ('SUCCEEDED', 'UNSUPPORTED', 'FAILED_TERMINAL') AND completed_at IS NOT NULL)
        OR (status NOT IN ('SUCCEEDED', 'UNSUPPORTED', 'FAILED_TERMINAL'))
    ),
    CONSTRAINT uq_problem_parse_jobs_logical_input UNIQUE (
        user_id,
        problem_session_id,
        recognition_evidence_id,
        recognition_evidence_revision,
        capability,
        prompt_id,
        prompt_version,
        schema_version,
        route_policy_version
    )
);

CREATE INDEX ix_problem_parse_jobs_user_created
    ON problem_parse_jobs(user_id, created_at DESC);

CREATE INDEX ix_problem_parse_jobs_session_created
    ON problem_parse_jobs(problem_session_id, created_at DESC);

CREATE INDEX ix_problem_parse_jobs_due
    ON problem_parse_jobs(status, next_attempt_at)
    WHERE status IN ('QUEUED', 'FAILED_RETRYABLE');

CREATE INDEX ix_problem_parse_jobs_running_updated
    ON problem_parse_jobs(status, updated_at)
    WHERE status = 'RUNNING';

CREATE TABLE problem_parses (
    id UUID PRIMARY KEY,
    parse_job_id UUID NOT NULL,
    user_id UUID NOT NULL,
    problem_session_id UUID NOT NULL,
    recognition_evidence_id UUID NOT NULL,
    recognition_evidence_revision INTEGER NOT NULL,
    revision INTEGER NOT NULL,
    source VARCHAR(16) NOT NULL,
    support_status VARCHAR(32) NOT NULL,
    unsupported_reason VARCHAR(64),
    review_required BOOLEAN NOT NULL DEFAULT FALSE,
    schema_version VARCHAR(64) NOT NULL,
    raw_output_jsonb JSONB NOT NULL,
    normalized_problem_jsonb JSONB NOT NULL,
    provider VARCHAR(64) NOT NULL,
    model VARCHAR(128) NOT NULL,
    route_policy_version VARCHAR(64) NOT NULL,
    prompt_id VARCHAR(64) NOT NULL,
    prompt_version VARCHAR(32) NOT NULL,
    provider_request_id VARCHAR(128),
    provider_response_id VARCHAR(128),
    fallback_used BOOLEAN NOT NULL DEFAULT FALSE,
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
    CONSTRAINT fk_problem_parses_job FOREIGN KEY (parse_job_id)
        REFERENCES problem_parse_jobs(id) ON DELETE CASCADE,
    CONSTRAINT fk_problem_parses_session_user FOREIGN KEY (problem_session_id, user_id)
        REFERENCES problem_sessions(id, user_id) ON DELETE CASCADE,
    CONSTRAINT fk_problem_parses_evidence_user_session_revision FOREIGN KEY (
        recognition_evidence_id,
        user_id,
        problem_session_id,
        recognition_evidence_revision
    )
        REFERENCES recognition_evidence(id, user_id, problem_session_id, revision) ON DELETE CASCADE,
    CONSTRAINT ck_problem_parses_revision CHECK (revision > 0),
    CONSTRAINT ck_problem_parses_source CHECK (source IN ('AI', 'USER')),
    CONSTRAINT ck_problem_parses_support_status CHECK (
        support_status IN ('SUPPORTED', 'REVIEW_REQUIRED', 'UNSUPPORTED')
    ),
    CONSTRAINT ck_problem_parses_unsupported_reason CHECK (
        (support_status = 'UNSUPPORTED' AND unsupported_reason IS NOT NULL)
        OR (support_status <> 'UNSUPPORTED' AND unsupported_reason IS NULL)
    ),
    CONSTRAINT ck_problem_parses_json_objects CHECK (
        jsonb_typeof(raw_output_jsonb) = 'object'
        AND jsonb_typeof(normalized_problem_jsonb) = 'object'
    ),
    CONSTRAINT ck_problem_parses_usage CHECK (
        (input_tokens IS NULL OR input_tokens >= 0)
        AND (output_tokens IS NULL OR output_tokens >= 0)
        AND (image_units IS NULL OR image_units >= 0)
        AND request_units >= 0
    ),
    CONSTRAINT ck_problem_parses_latency_cost CHECK (
        provider_latency_ms >= 0
        AND total_latency_ms >= 0
        AND estimated_cost_micros >= 0
    ),
    CONSTRAINT uq_problem_parses_job UNIQUE (parse_job_id),
    CONSTRAINT uq_problem_parses_session_revision UNIQUE (problem_session_id, revision)
);

CREATE INDEX ix_problem_parses_user_created
    ON problem_parses(user_id, created_at DESC);

CREATE INDEX ix_problem_parses_session_revision
    ON problem_parses(problem_session_id, revision DESC);

CREATE INDEX ix_problem_parses_evidence
    ON problem_parses(recognition_evidence_id, recognition_evidence_revision);
