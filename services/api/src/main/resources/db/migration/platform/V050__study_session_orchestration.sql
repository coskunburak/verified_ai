-- ============================================================================
-- V050: Sprint 8.7 Study Session Orchestration & Interruption Recovery Semantics
-- Invariants: INV-SESS-001..005, ADR-006 training_eligible=FALSE
-- ============================================================================

CREATE TABLE IF NOT EXISTS study_sessions (
    session_id               UUID PRIMARY KEY,
    user_id                  UUID NOT NULL,
    coach_session_id         UUID NULL REFERENCES daily_coach_sessions(id) ON DELETE SET NULL,
    source_context           VARCHAR(32) NOT NULL,
    topic_code               VARCHAR(128) NOT NULL,
    locale                   VARCHAR(10) NOT NULL DEFAULT 'tr-TR',
    state                    VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    total_steps              INTEGER NOT NULL,
    current_step_index       INTEGER NOT NULL DEFAULT 0,
    completed_steps_count    INTEGER NOT NULL DEFAULT 0,
    last_sequence_number     BIGINT NOT NULL DEFAULT 0,
    active_duration_seconds  INTEGER NOT NULL DEFAULT 0,
    paused_duration_seconds  INTEGER NOT NULL DEFAULT 0,
    client_instance_id       VARCHAR(128) NOT NULL,
    interruption_reason      VARCHAR(64) NULL,
    step_blueprint_json      JSONB NOT NULL DEFAULT '[]'::jsonb,
    resume_snapshot_json     JSONB NOT NULL DEFAULT '{}'::jsonb,
    complete_idempotency_key VARCHAR(128) NULL,
    training_eligible        BOOLEAN NOT NULL DEFAULT FALSE,
    started_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_checkpoint_at       TIMESTAMPTZ NULL,
    paused_at                TIMESTAMPTZ NULL,
    resumed_at               TIMESTAMPTZ NULL,
    completed_at             TIMESTAMPTZ NULL,
    expires_at               TIMESTAMPTZ NOT NULL,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_study_sessions_source_context CHECK (
        source_context IN ('DAILY_COACH', 'SPACED_REVIEW', 'ADAPTIVE_PRACTICE', 'MISTAKE_REPAIR', 'FREE_STUDY')
    ),
    CONSTRAINT chk_study_sessions_locale CHECK (
        locale IN ('tr-TR', 'en-US')
    ),
    CONSTRAINT chk_study_sessions_state CHECK (
        state IN ('INITIALIZED', 'ACTIVE', 'PAUSED', 'COMPLETED', 'ABANDONED')
    ),
    CONSTRAINT chk_study_sessions_total_steps CHECK (
        total_steps BETWEEN 1 AND 50
    ),
    CONSTRAINT chk_study_sessions_step_bounds CHECK (
        current_step_index >= 0
        AND current_step_index <= total_steps
        AND completed_steps_count >= 0
        AND completed_steps_count <= total_steps
    ),
    CONSTRAINT chk_study_sessions_sequence_non_negative CHECK (
        last_sequence_number >= 0
    ),
    CONSTRAINT chk_study_sessions_active_duration CHECK (
        active_duration_seconds BETWEEN 0 AND 14400
    ),
    CONSTRAINT chk_study_sessions_paused_duration CHECK (
        paused_duration_seconds >= 0
    ),
    CONSTRAINT chk_study_sessions_interruption_reason CHECK (
        interruption_reason IS NULL OR interruption_reason IN (
            'USER_PAUSED',
            'APP_BACKGROUNDED',
            'NETWORK_LOST',
            'PHONE_CALL',
            'OS_TERMINATED',
            'EXPIRED_INACTIVITY',
            'SUPERSEDED_BY_NEW_SESSION'
        )
    ),
    CONSTRAINT chk_study_sessions_training_false CHECK (
        training_eligible = FALSE
    )
);

CREATE TABLE IF NOT EXISTS study_session_checkpoints (
    checkpoint_id                    UUID PRIMARY KEY,
    session_id                       UUID NOT NULL REFERENCES study_sessions(session_id) ON DELETE CASCADE,
    user_id                          UUID NOT NULL,
    sequence_number                  BIGINT NOT NULL,
    step_index                       INTEGER NOT NULL,
    step_type                        VARCHAR(32) NOT NULL,
    step_status                      VARCHAR(24) NOT NULL,
    reported_active_duration_seconds INTEGER NOT NULL,
    accepted_active_duration_seconds INTEGER NOT NULL,
    duration_clamped                 BOOLEAN NOT NULL DEFAULT FALSE,
    offline_queued                   BOOLEAN NOT NULL DEFAULT FALSE,
    client_recorded_at               TIMESTAMPTZ NOT NULL,
    server_received_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    idempotency_key                  VARCHAR(128) NOT NULL,
    checkpoint_payload_json          JSONB NOT NULL DEFAULT '{}'::jsonb,
    training_eligible                BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT uq_study_session_checkpoints_idem UNIQUE (session_id, idempotency_key),
    CONSTRAINT uq_study_session_checkpoints_user_idem UNIQUE (user_id, idempotency_key),
    CONSTRAINT uq_study_session_checkpoints_seq UNIQUE (session_id, sequence_number),
    CONSTRAINT chk_study_session_checkpoints_seq_positive CHECK (
        sequence_number >= 1
    ),
    CONSTRAINT chk_study_session_checkpoints_step_index CHECK (
        step_index BETWEEN 0 AND 50
    ),
    CONSTRAINT chk_study_session_checkpoints_step_type CHECK (
        step_type IN ('CONCEPT_PRIMER', 'MICRO_LESSON', 'SOLVE_ITEM', 'VERIFY_STEP', 'SPACED_RECALL', 'REFLECTION')
    ),
    CONSTRAINT chk_study_session_checkpoints_step_status CHECK (
        step_status IN ('IN_PROGRESS', 'COMPLETED', 'SKIPPED')
    ),
    CONSTRAINT chk_study_session_checkpoints_reported_duration CHECK (
        reported_active_duration_seconds BETWEEN 0 AND 14400
    ),
    CONSTRAINT chk_study_session_checkpoints_accepted_duration CHECK (
        accepted_active_duration_seconds BETWEEN 0 AND 14400
    ),
    CONSTRAINT chk_study_session_checkpoints_idem_len CHECK (
        char_length(idempotency_key) >= 16
    ),
    CONSTRAINT chk_study_session_checkpoints_training_false CHECK (
        training_eligible = FALSE
    )
);

CREATE INDEX IF NOT EXISTS idx_study_sessions_user_state_updated
    ON study_sessions (user_id, state, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_study_sessions_user_created
    ON study_sessions (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_study_sessions_active_expiry
    ON study_sessions (expires_at)
    WHERE state IN ('INITIALIZED', 'ACTIVE', 'PAUSED');

CREATE INDEX IF NOT EXISTS idx_study_session_checkpoints_session_seq
    ON study_session_checkpoints (session_id, sequence_number DESC);

CREATE INDEX IF NOT EXISTS idx_study_session_checkpoints_user_received
    ON study_session_checkpoints (user_id, server_received_at DESC);
