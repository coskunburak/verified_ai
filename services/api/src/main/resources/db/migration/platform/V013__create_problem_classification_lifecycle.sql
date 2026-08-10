CREATE TABLE problem_classifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    problem_session_id UUID NOT NULL,
    canonical_problem_id UUID NOT NULL,
    revision INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    ontology_version VARCHAR(64) NOT NULL,
    schema_version VARCHAR(64) NOT NULL,
    subject_id VARCHAR(128),
    topic_id VARCHAR(128),
    primary_skill_id VARCHAR(128),
    secondary_skill_ids JSONB,
    difficulty VARCHAR(32),
    confidence VARCHAR(32),
    provider VARCHAR(64),
    model VARCHAR(128),
    prompt_id VARCHAR(128),
    prompt_version VARCHAR(32),
    route_policy_version VARCHAR(64),
    classification_schema_version VARCHAR(64),
    fallback_used BOOLEAN NOT NULL DEFAULT FALSE,
    provider_latency_ms INTEGER,
    estimated_cost_micros BIGINT,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_classifications_session_user FOREIGN KEY (problem_session_id, user_id)
        REFERENCES problem_sessions(id, user_id) ON DELETE CASCADE,
    CONSTRAINT fk_classifications_canonical FOREIGN KEY (canonical_problem_id)
        REFERENCES canonical_problems(id) ON DELETE CASCADE,
    CONSTRAINT ck_classifications_revision CHECK (revision > 0),
    CONSTRAINT ck_classifications_status CHECK (
        status IN ('CLASSIFIED','REVIEW_REQUIRED','AMBIGUOUS','UNKNOWN','UNSUPPORTED','FAILED')
    ),
    CONSTRAINT ck_classifications_difficulty CHECK (
        difficulty IS NULL OR difficulty IN ('EASY','MEDIUM','HARD')
    ),
    CONSTRAINT ck_classifications_confidence CHECK (
        confidence IS NULL OR confidence IN ('HIGH','MEDIUM','LOW')
    ),
    CONSTRAINT ck_classifications_primary_skill CHECK (
        (status = 'CLASSIFIED' AND primary_skill_id IS NOT NULL)
        OR (status != 'CLASSIFIED')
    ),
    CONSTRAINT ck_classifications_secondary_json CHECK (
        secondary_skill_ids IS NULL OR jsonb_typeof(secondary_skill_ids) = 'array'
    ),
    CONSTRAINT uq_classifications_canonical_ontology_schema UNIQUE (
        canonical_problem_id, ontology_version, schema_version, revision
    ),
    CONSTRAINT uq_classifications_session_revision UNIQUE (
        problem_session_id, revision
    )
);

CREATE INDEX ix_classifications_user_created
    ON problem_classifications(user_id, created_at DESC);

CREATE INDEX ix_classifications_session_revision
    ON problem_classifications(problem_session_id, revision DESC);

CREATE INDEX ix_classifications_canonical
    ON problem_classifications(canonical_problem_id);
