CREATE TABLE canonical_problems (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    problem_session_id UUID NOT NULL,
    problem_parse_id UUID NOT NULL,
    problem_parse_revision INTEGER NOT NULL,
    canonical_revision INTEGER NOT NULL,
    schema_version VARCHAR(64) NOT NULL,
    verifier_schema_version VARCHAR(64) NOT NULL,
    problem_type VARCHAR(64) NOT NULL,
    task_type VARCHAR(64) NOT NULL,
    canonical_problem_jsonb JSONB NOT NULL,
    verifier_input_jsonb JSONB NOT NULL,
    display_jsonb JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_canonical_problems_session_user FOREIGN KEY (problem_session_id, user_id)
        REFERENCES problem_sessions(id, user_id) ON DELETE CASCADE,
    CONSTRAINT fk_canonical_problems_parse FOREIGN KEY (problem_parse_id)
        REFERENCES problem_parses(id) ON DELETE CASCADE,
    CONSTRAINT ck_canonical_problems_revision CHECK (
        problem_parse_revision > 0
        AND canonical_revision > 0
    ),
    CONSTRAINT ck_canonical_problems_schema_versions CHECK (
        schema_version = 'canonical-problem-v1'
        AND verifier_schema_version = 'verifier-input-v1'
    ),
    CONSTRAINT ck_canonical_problems_problem_type CHECK (
        problem_type IN (
            'ARITHMETIC_EXPRESSION',
            'ALGEBRAIC_EXPRESSION',
            'EQUATION',
            'INEQUALITY'
        )
    ),
    CONSTRAINT ck_canonical_problems_task_type CHECK (
        task_type IN (
            'EVALUATE',
            'SIMPLIFY',
            'SOLVE_EQUATION',
            'SOLVE_INEQUALITY'
        )
    ),
    CONSTRAINT ck_canonical_problems_json_objects CHECK (
        jsonb_typeof(canonical_problem_jsonb) = 'object'
        AND jsonb_typeof(verifier_input_jsonb) = 'object'
        AND jsonb_typeof(display_jsonb) = 'object'
    ),
    CONSTRAINT uq_canonical_problems_parse_schema UNIQUE (
        problem_parse_id,
        problem_parse_revision,
        schema_version
    ),
    CONSTRAINT uq_canonical_problems_session_revision UNIQUE (
        problem_session_id,
        canonical_revision
    )
);

CREATE INDEX ix_canonical_problems_user_created
    ON canonical_problems(user_id, created_at DESC);

CREATE INDEX ix_canonical_problems_session_revision
    ON canonical_problems(problem_session_id, canonical_revision DESC);

CREATE INDEX ix_canonical_problems_parse
    ON canonical_problems(problem_parse_id, problem_parse_revision);
