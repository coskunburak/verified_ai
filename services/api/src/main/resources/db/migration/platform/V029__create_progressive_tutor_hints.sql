-- Additive local development migration. V024-V026 reservation/shared upgrade block remains.
CREATE TABLE tutor_hints (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    tutor_session_id UUID NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL CHECK (idempotency_key ~ '^[A-Za-z0-9_-]{8,128}$'),
    schema_version VARCHAR(32) NOT NULL CHECK (schema_version='tutor-hint-v1'),
    policy_version VARCHAR(32) NOT NULL CHECK (policy_version='progressive-hints-v1'),
    cost_policy_version VARCHAR(32) NOT NULL CHECK (cost_policy_version='hint-assistance-v1'),
    sequence_number INTEGER NOT NULL CHECK (sequence_number BETWEEN 1 AND 3),
    level VARCHAR(2) NOT NULL CHECK (level IN ('H1','H2','H3')),
    assistance VARCHAR(16) NOT NULL,
    CHECK ((level='H1' AND sequence_number=1 AND assistance='CONCEPTUAL') OR
           (level='H2' AND sequence_number=2 AND assistance='METHOD') OR
           (level='H3' AND sequence_number=3 AND assistance='FIRST_STEP')),
    content TEXT NOT NULL CHECK (char_length(content) BETWEEN 1 AND 2000 AND octet_length(content)<=8000),
    language VARCHAR(8) NOT NULL CHECK (language IN ('en','tr')),
    previous_phase VARCHAR(32) NOT NULL CHECK (previous_phase IN ('CLARIFY_GOAL','ELICIT_CONCEPT','EVALUATE_LEARNER_STEP','OFFER_MINIMAL_SUPPORT','ADVANCE_ONE_STEP','SUMMARIZE_CONCEPT')),
    phase VARCHAR(32) NOT NULL CHECK (phase='OFFER_MINIMAL_SUPPORT'),
    unproductive_count INTEGER NOT NULL CHECK (unproductive_count BETWEEN 0 AND 2),
    turn_count INTEGER NOT NULL CHECK (turn_count BETWEEN 1 AND 11),
    mastery_evidence VARCHAR(24) NOT NULL CHECK (mastery_evidence='ASSISTANCE_ONLY'),
    inference VARCHAR(32) NOT NULL CHECK (inference='DETERMINISTIC_NO_INFERENCE'),
    commercial VARCHAR(16) NOT NULL CHECK (commercial='NO_DEBIT'),
    reference_status VARCHAR(16) NOT NULL CHECK (reference_status='UNAVAILABLE'),
    uncertain BOOLEAN NOT NULL CHECK (uncertain=true),
    training_eligible BOOLEAN NOT NULL DEFAULT FALSE CHECK (training_eligible=false),
    created_at TIMESTAMPTZ NOT NULL,
    FOREIGN KEY (tutor_session_id,user_id) REFERENCES tutor_sessions(id,user_id) ON DELETE CASCADE,
    UNIQUE (tutor_session_id,level),
    UNIQUE (tutor_session_id,sequence_number),
    UNIQUE (tutor_session_id,turn_count),
    UNIQUE (tutor_session_id,idempotency_key)
);
CREATE INDEX ix_tutor_hints_owner_history ON tutor_hints(user_id,tutor_session_id,sequence_number);
