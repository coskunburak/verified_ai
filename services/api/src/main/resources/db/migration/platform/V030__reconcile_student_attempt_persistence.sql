-- Forward reconciliation of reserved V025 after V029. Historical checksums remain unchanged.
-- A database with manually applied planned SQL must be reconciled before deployment; no silent IF NOT EXISTS adoption.
ALTER TABLE canonical_problems ADD CONSTRAINT uq_canonical_attempt_lineage
    UNIQUE (id,user_id,problem_session_id,canonical_revision,problem_parse_id,problem_parse_revision);
CREATE TABLE student_attempts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    problem_session_id UUID NOT NULL,
    canonical_id UUID NOT NULL,
    canonical_revision INTEGER NOT NULL CHECK (canonical_revision>0),
    parse_id UUID NOT NULL,
    parse_revision INTEGER NOT NULL CHECK (parse_revision>0),
    source_type VARCHAR(16) NOT NULL CHECK (source_type='MANUAL'),
    schema_version VARCHAR(32) NOT NULL CHECK (schema_version='student-attempt-v1'),
    normalization_version VARCHAR(32) NOT NULL CHECK (normalization_version IN ('manual-canonical-v1','manual-canonical-v2')),
    status VARCHAR(24) NOT NULL CHECK (status IN ('SUBMITTED','ANALYZED','STALE_SOURCE')),
    create_key VARCHAR(128) NOT NULL CHECK (create_key ~ '^[A-Za-z0-9_-]{8,128}$'),
    request_hash CHAR(64) NOT NULL CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    analysis_jsonb JSONB,
    verifier_request_jsonb JSONB,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    training_eligible BOOLEAN NOT NULL DEFAULT FALSE CHECK (training_eligible=false),
    UNIQUE (id,user_id),
    UNIQUE (user_id,problem_session_id,create_key),
    FOREIGN KEY (problem_session_id,user_id) REFERENCES problem_sessions(id,user_id) ON DELETE CASCADE,
    FOREIGN KEY (canonical_id,user_id,problem_session_id,canonical_revision,parse_id,parse_revision)
      REFERENCES canonical_problems(id,user_id,problem_session_id,canonical_revision,problem_parse_id,problem_parse_revision) ON DELETE CASCADE,
    CHECK (analysis_jsonb IS NULL OR (jsonb_typeof(analysis_jsonb)='object' AND octet_length(analysis_jsonb::text)<=20000)),
    CHECK (verifier_request_jsonb IS NULL OR (jsonb_typeof(verifier_request_jsonb)='object' AND octet_length(verifier_request_jsonb::text)<=65536)),
    CHECK ((status='ANALYZED')=(analysis_jsonb IS NOT NULL))
);
CREATE INDEX ix_attempt_owner_history ON student_attempts(user_id,problem_session_id,created_at,id);
CREATE TABLE student_attempt_steps (
    id UUID PRIMARY KEY,
    attempt_id UUID NOT NULL,
    user_id UUID NOT NULL,
    step_index INTEGER NOT NULL CHECK (step_index BETWEEN 1 AND 12),
    content TEXT NOT NULL CHECK (char_length(content) BETWEEN 1 AND 256 AND octet_length(content)<=1024),
    normalized_statement JSONB,
    training_eligible BOOLEAN NOT NULL DEFAULT FALSE CHECK (training_eligible=false),
    FOREIGN KEY (attempt_id,user_id) REFERENCES student_attempts(id,user_id) ON DELETE CASCADE,
    UNIQUE (attempt_id,step_index),
    CHECK (normalized_statement IS NULL OR (jsonb_typeof(normalized_statement)='object' AND octet_length(normalized_statement::text)<=12000))
);
CREATE INDEX ix_attempt_steps_owner ON student_attempt_steps(user_id,attempt_id,step_index);
