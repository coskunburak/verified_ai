-- V024/V025/V026 belong to the commercial wedge. Fresh disposable DBs only until ordering is reconciled.
ALTER TABLE canonical_problems ADD CONSTRAINT uq_canonical_tutor_lineage
    UNIQUE (id, user_id, problem_session_id, canonical_revision, problem_parse_id, problem_parse_revision);
CREATE TABLE tutor_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    problem_session_id UUID NOT NULL,
    canonical_id UUID NOT NULL,
    canonical_revision INTEGER NOT NULL,
    parse_id UUID NOT NULL,
    parse_revision INTEGER NOT NULL,
    primary_skill_id VARCHAR(128) NOT NULL,
    context_schema_version VARCHAR(32) NOT NULL CHECK (context_schema_version='tutor-context-v1'),
    profile_version BIGINT,
    explanation_depth VARCHAR(16) CHECK (explanation_depth IN ('QUICK','STANDARD','DEEP','BEGINNER')),
    language VARCHAR(8) CHECK (language IN ('en','tr')),
    mastery VARCHAR(24) NOT NULL CHECK (mastery='NOT_AVAILABLE'),
    safety_policy_version VARCHAR(32) NOT NULL CHECK (safety_policy_version='learner-persistence-v1'),
    schema_version VARCHAR(32) NOT NULL CHECK (schema_version='tutor-session-v1'),
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE','CLOSED')),
    create_key VARCHAR(128) NOT NULL CHECK (create_key ~ '^[A-Za-z0-9_-]{8,128}$'),
    message_count BIGINT NOT NULL DEFAULT 0 CHECK (message_count BETWEEN 0 AND 100),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version>=0),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    training_eligible BOOLEAN NOT NULL DEFAULT FALSE CHECK (training_eligible=false),
    UNIQUE (id,user_id),
    UNIQUE (user_id,problem_session_id,create_key),
    FOREIGN KEY (problem_session_id,user_id) REFERENCES problem_sessions(id,user_id) ON DELETE CASCADE,
    FOREIGN KEY (canonical_id,user_id,problem_session_id,canonical_revision,parse_id,parse_revision)
        REFERENCES canonical_problems(id,user_id,problem_session_id,canonical_revision,problem_parse_id,problem_parse_revision)
        ON DELETE CASCADE
);
CREATE INDEX ix_tutor_owner_history ON tutor_sessions(user_id,problem_session_id,created_at,id);
CREATE TABLE tutor_messages (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    tutor_session_id UUID NOT NULL,
    role VARCHAR(16) NOT NULL CHECK (role='LEARNER'),
    schema_version VARCHAR(32) NOT NULL CHECK (schema_version='tutor-message-v1'),
    sequence_number BIGINT NOT NULL CHECK (sequence_number BETWEEN 1 AND 100),
    content TEXT NOT NULL CHECK (char_length(content) BETWEEN 1 AND 2000 AND octet_length(content)<=8000),
    idempotency_key VARCHAR(128) NOT NULL CHECK (idempotency_key ~ '^[A-Za-z0-9_-]{8,128}$'),
    created_at TIMESTAMPTZ NOT NULL,
    FOREIGN KEY (tutor_session_id,user_id) REFERENCES tutor_sessions(id,user_id) ON DELETE CASCADE,
    UNIQUE (tutor_session_id,sequence_number),
    UNIQUE (tutor_session_id,idempotency_key)
);
CREATE INDEX ix_tutor_messages_owner ON tutor_messages(user_id,tutor_session_id,sequence_number);
