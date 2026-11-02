-- Additive: the six v1 persistence operations and V027 learner history remain unchanged.
-- Shared upgrades remain blocked until reserved V024-V026 ordering is reconciled.
ALTER TABLE tutor_messages ADD CONSTRAINT uq_tutor_message_parent
    UNIQUE (id,user_id,tutor_session_id,sequence_number);
CREATE TABLE tutor_turns (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    tutor_session_id UUID NOT NULL,
    learner_message_id UUID NOT NULL UNIQUE,
    learner_sequence_number BIGINT NOT NULL CHECK (learner_sequence_number BETWEEN 1 AND 100),
    reply_id UUID NOT NULL UNIQUE,
    role VARCHAR(16) NOT NULL CHECK (role='TUTOR'),
    schema_version VARCHAR(32) NOT NULL CHECK (schema_version='tutor-turn-v1'),
    policy_version VARCHAR(32) NOT NULL CHECK (policy_version='socratic-policy-v1'),
    previous_phase VARCHAR(32) NOT NULL CHECK (previous_phase IN ('CLARIFY_GOAL','ELICIT_CONCEPT','EVALUATE_LEARNER_STEP','OFFER_MINIMAL_SUPPORT','ADVANCE_ONE_STEP','SUMMARIZE_CONCEPT')),
    phase VARCHAR(32) NOT NULL CHECK (phase IN ('ELICIT_CONCEPT','EVALUATE_LEARNER_STEP','OFFER_MINIMAL_SUPPORT','ADVANCE_ONE_STEP','SUMMARIZE_CONCEPT','COMPLETED','PAUSED','NEEDS_REVIEW','UNAVAILABLE')),
    unproductive_count INTEGER NOT NULL CHECK (unproductive_count BETWEEN 0 AND 3),
    turn_count INTEGER NOT NULL CHECK (turn_count BETWEEN 1 AND 12),
    intent VARCHAR(32) NOT NULL CHECK (intent IN ('UNKNOWN','ATTEMPT_STEP','EXPLAIN_CONCEPT','SMALL_HINT','REVEAL_ANSWER','CONFUSED','CHALLENGE','OFF_TOPIC','SUMMARIZE')),
    action VARCHAR(32) NOT NULL CHECK (action IN ('ASK_GOAL','ASK_CONCEPT','ASK_STEP','CONCEPTUAL_NUDGE','ADVANCE','REFLECT','FINISH','PAUSE','REQUEST_REVIEW','CAPABILITY_UNAVAILABLE')),
    outcome VARCHAR(24) NOT NULL CHECK (outcome IN ('GUIDANCE','PAUSED','NEEDS_REVIEW','COMPLETED','UNAVAILABLE')),
    content TEXT NOT NULL CHECK (char_length(content) BETWEEN 1 AND 2000 AND octet_length(content)<=8000),
    language VARCHAR(8) NOT NULL CHECK (language IN ('en','tr')),
    correctness VARCHAR(24) NOT NULL CHECK (correctness='UNKNOWN'),
    reference_status VARCHAR(24) NOT NULL CHECK (reference_status='UNAVAILABLE'),
    uncertain BOOLEAN NOT NULL CHECK (uncertain=true),
    provenance VARCHAR(32) NOT NULL CHECK (provenance='DETERMINISTIC_POLICY'),
    created_at TIMESTAMPTZ NOT NULL,
    training_eligible BOOLEAN NOT NULL DEFAULT FALSE CHECK (training_eligible=false),
    FOREIGN KEY (learner_message_id,user_id,tutor_session_id,learner_sequence_number)
        REFERENCES tutor_messages(id,user_id,tutor_session_id,sequence_number) ON DELETE CASCADE,
    UNIQUE (tutor_session_id,turn_count),
    UNIQUE (tutor_session_id,learner_sequence_number)
);
CREATE INDEX ix_tutor_turns_owner_history ON tutor_turns(user_id,tutor_session_id,learner_sequence_number);
