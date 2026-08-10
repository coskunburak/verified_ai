CREATE TABLE problem_classification_jobs (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    problem_session_id UUID NOT NULL,
    canonical_problem_id UUID NOT NULL,
    canonical_problem_revision INTEGER NOT NULL,

    ontology_version VARCHAR(64) NOT NULL,
    projection_version VARCHAR(64) NOT NULL,

    status VARCHAR(32) NOT NULL,
    capability VARCHAR(64) NOT NULL,

    prompt_id VARCHAR(128) NOT NULL,
    prompt_version VARCHAR(32) NOT NULL,
    schema_version VARCHAR(64) NOT NULL,
    route_policy_version VARCHAR(64) NOT NULL,

    request_fingerprint CHAR(64) NOT NULL,

    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL,
    next_attempt_at TIMESTAMPTZ NOT NULL,

    last_error_code VARCHAR(64),
    last_failure_class VARCHAR(64),

    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,

    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_classification_jobs_session_user
        FOREIGN KEY (problem_session_id, user_id)
        REFERENCES problem_sessions(id, user_id)
        ON DELETE CASCADE,

    CONSTRAINT fk_classification_jobs_canonical
        FOREIGN KEY (canonical_problem_id)
        REFERENCES canonical_problems(id)
        ON DELETE CASCADE,

    CONSTRAINT ck_classification_jobs_canonical_revision
        CHECK (canonical_problem_revision > 0),

    CONSTRAINT ck_classification_jobs_status
        CHECK (
            status IN (
                'QUEUED',
                'RUNNING',
                'SUCCEEDED',
                'FAILED_RETRYABLE',
                'FAILED_TERMINAL'
            )
        ),

    CONSTRAINT ck_classification_jobs_capability
        CHECK (capability = 'PROBLEM_CLASSIFY'),

    CONSTRAINT ck_classification_jobs_attempts
        CHECK (
            attempt_count >= 0
            AND max_attempts > 0
            AND attempt_count <= max_attempts
        ),

    CONSTRAINT ck_classification_jobs_fingerprint
        CHECK (request_fingerprint ~ '^[0-9a-f]{64}$'),

    CONSTRAINT uq_classification_jobs_fingerprint
        UNIQUE (request_fingerprint)
);

CREATE INDEX ix_classification_jobs_due
    ON problem_classification_jobs(
        status,
        next_attempt_at,
        created_at
    );

CREATE INDEX ix_classification_jobs_session_created
    ON problem_classification_jobs(
        problem_session_id,
        user_id,
        created_at DESC
    );

CREATE INDEX ix_classification_jobs_canonical
    ON problem_classification_jobs(
        canonical_problem_id,
        created_at DESC
    );


CREATE TABLE problem_classifications (
    id UUID PRIMARY KEY,

    classification_job_id UUID NOT NULL,

    user_id UUID NOT NULL,
    problem_session_id UUID NOT NULL,
    canonical_problem_id UUID NOT NULL,

    revision INTEGER NOT NULL,

    source VARCHAR(16) NOT NULL,

    status VARCHAR(32) NOT NULL,
    review_reason VARCHAR(64),

    ontology_version VARCHAR(64) NOT NULL,
    classification_schema_version VARCHAR(64) NOT NULL,
    projection_version VARCHAR(64) NOT NULL,

    subject_id VARCHAR(128),
    topic_id VARCHAR(128),
    primary_skill_id VARCHAR(128),

    difficulty VARCHAR(32),
    difficulty_policy_version VARCHAR(64) NOT NULL,

    confidence_band VARCHAR(32) NOT NULL,
    confidence_policy_version VARCHAR(64) NOT NULL,
    confidence_calibration VARCHAR(32) NOT NULL,

    capability VARCHAR(64) NOT NULL,

    provider VARCHAR(64),
    model VARCHAR(128),

    prompt_id VARCHAR(128) NOT NULL,
    prompt_version VARCHAR(32) NOT NULL,
    route_policy_version VARCHAR(64) NOT NULL,

    fallback_used BOOLEAN NOT NULL DEFAULT FALSE,

    provider_latency_ms BIGINT,
    estimated_cost_micros BIGINT,

    request_fingerprint CHAR(64) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_classifications_job
        FOREIGN KEY (classification_job_id)
        REFERENCES problem_classification_jobs(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_classifications_session_user
        FOREIGN KEY (problem_session_id, user_id)
        REFERENCES problem_sessions(id, user_id)
        ON DELETE CASCADE,

    CONSTRAINT fk_classifications_canonical
        FOREIGN KEY (canonical_problem_id)
        REFERENCES canonical_problems(id)
        ON DELETE CASCADE,

    CONSTRAINT ck_classifications_revision
        CHECK (revision > 0),

    CONSTRAINT ck_classifications_source
        CHECK (source IN ('AI', 'SYSTEM', 'USER')),

    CONSTRAINT ck_classifications_status
        CHECK (
            status IN (
                'CLASSIFIED',
                'REVIEW_REQUIRED',
                'UNKNOWN',
                'UNSUPPORTED'
            )
        ),

    CONSTRAINT ck_classifications_review_reason
        CHECK (
            review_reason IS NULL
            OR review_reason IN (
                'AMBIGUOUS_PRIMARY_SKILL',
                'LOW_CLASSIFICATION_CONFIDENCE',
                'UPSTREAM_RISK',
                'ONTOLOGY_COVERAGE_GAP',
                'INSUFFICIENT_SEMANTIC_EVIDENCE'
            )
        ),

    CONSTRAINT ck_classifications_difficulty
        CHECK (
            difficulty IS NULL
            OR difficulty IN ('EASY', 'MEDIUM', 'HARD')
        ),

    CONSTRAINT ck_classifications_confidence
        CHECK (
            confidence_band IN (
                'HIGH',
                'MEDIUM',
                'LOW',
                'UNKNOWN'
            )
            AND confidence_calibration IN (
                'UNCALIBRATED',
                'CALIBRATED'
            )
        ),

    CONSTRAINT ck_classifications_capability
        CHECK (capability = 'PROBLEM_CLASSIFY'),

    CONSTRAINT ck_classifications_provider_latency
        CHECK (
            provider_latency_ms IS NULL
            OR provider_latency_ms >= 0
        ),

    CONSTRAINT ck_classifications_cost
        CHECK (
            estimated_cost_micros IS NULL
            OR estimated_cost_micros >= 0
        ),

    CONSTRAINT ck_classifications_fingerprint
        CHECK (request_fingerprint ~ '^[0-9a-f]{64}$'),

    CONSTRAINT ck_classifications_semantics
        CHECK (
            (
                status = 'CLASSIFIED'
                AND review_reason IS NULL
                AND subject_id IS NOT NULL
                AND topic_id IS NOT NULL
                AND primary_skill_id IS NOT NULL
                AND difficulty IS NOT NULL
            )
            OR
            (
                status = 'REVIEW_REQUIRED'
                AND review_reason IS NOT NULL
                AND subject_id IS NULL
                AND topic_id IS NULL
                AND primary_skill_id IS NULL
                AND difficulty IS NULL
            )
            OR
            (
                status IN ('UNKNOWN', 'UNSUPPORTED')
                AND subject_id IS NULL
                AND topic_id IS NULL
                AND primary_skill_id IS NULL
                AND difficulty IS NULL
            )
        ),

    CONSTRAINT uq_classifications_job
        UNIQUE (classification_job_id),

    CONSTRAINT uq_classifications_canonical_revision
        UNIQUE (canonical_problem_id, revision),

    CONSTRAINT uq_classifications_request_fingerprint
        UNIQUE (request_fingerprint)
);

CREATE INDEX ix_classifications_user_created
    ON problem_classifications(
        user_id,
        created_at DESC
    );

CREATE INDEX ix_classifications_session_created
    ON problem_classifications(
        problem_session_id,
        user_id,
        created_at DESC
    );

CREATE INDEX ix_classifications_canonical_revision
    ON problem_classifications(
        canonical_problem_id,
        revision DESC
    );


CREATE TABLE problem_classification_secondary_skills (
    classification_id UUID NOT NULL,
    ordinal SMALLINT NOT NULL,
    skill_id VARCHAR(128) NOT NULL,

    PRIMARY KEY (
        classification_id,
        skill_id
    ),

    CONSTRAINT fk_classification_secondary_classification
        FOREIGN KEY (classification_id)
        REFERENCES problem_classifications(id)
        ON DELETE CASCADE,

    CONSTRAINT ck_classification_secondary_ordinal
        CHECK (ordinal BETWEEN 0 AND 4),

    CONSTRAINT uq_classification_secondary_ordinal
        UNIQUE (
            classification_id,
            ordinal
        )
);

CREATE INDEX ix_classification_secondary_skill
    ON problem_classification_secondary_skills(skill_id);
