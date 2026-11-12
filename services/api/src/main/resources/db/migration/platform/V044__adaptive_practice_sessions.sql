-- ============================================================================
-- Migration: V044__adaptive_practice_sessions.sql
-- Sprint: 8.2 — Adaptive Practice Question Selection & Generation
-- Invariants: INV-PRAC-001..006, DOC-84, DOC-85, DOC-83 §7
-- ============================================================================

CREATE TABLE IF NOT EXISTS practice_sessions (
    id                          UUID             NOT NULL,
    user_id                     UUID             NOT NULL,
    source_recommendation_id    UUID             NULL,
    session_mode                VARCHAR(32)     NOT NULL,
    status                      VARCHAR(24)     NOT NULL,
    target_skill_id             VARCHAR(128)    NOT NULL,
    active_trap_family          VARCHAR(64)     NULL,
    learner_mastery_snapshot    NUMERIC(5, 4)   NOT NULL,
    total_items                  INT             NOT NULL,
    completed_items             INT             NOT NULL DEFAULT 0,
    verified_correct_items      INT             NOT NULL DEFAULT 0,
    trap_closure_achieved       BOOLEAN         NOT NULL DEFAULT FALSE,
    started_at                  TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at                TIMESTAMPTZ     NULL,
    abandoned_at                TIMESTAMPTZ     NULL,
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_practice_sessions PRIMARY KEY (id),
    CONSTRAINT fk_practice_sessions_recommendation
        FOREIGN KEY (source_recommendation_id)
        REFERENCES learner_recommendations (id)
        ON DELETE SET NULL,
    CONSTRAINT chk_practice_sessions_mode
        CHECK (session_mode IN ('DAILY_1_2_1', 'TRAP_VERIFICATION_DRILL', 'SPACED_REVIEW', 'EXAM_ROI_SYNTHESIS')),
    CONSTRAINT chk_practice_sessions_status
        CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'ABANDONED')),
    CONSTRAINT chk_practice_sessions_mastery_range
        CHECK (learner_mastery_snapshot >= 0.0000 AND learner_mastery_snapshot <= 1.0000),
    CONSTRAINT chk_practice_sessions_counts
        CHECK (total_items BETWEEN 1 AND 10 AND completed_items >= 0 AND completed_items <= total_items
               AND verified_correct_items >= 0 AND verified_correct_items <= completed_items)
);

CREATE INDEX IF NOT EXISTS idx_practice_sessions_user_status_created
    ON practice_sessions (user_id, status, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_practice_sessions_recommendation
    ON practice_sessions (source_recommendation_id);

CREATE TABLE IF NOT EXISTS practice_session_items (
    id                              UUID            NOT NULL,
    session_id                      UUID            NOT NULL,
    user_id                         UUID            NOT NULL,
    ordinal_index                   INT             NOT NULL,
    slot_role                       VARCHAR(32)     NOT NULL,
    question_source                 VARCHAR(32)     NOT NULL,
    source_question_id              VARCHAR(128)    NOT NULL,
    skill_id                        VARCHAR(128)    NOT NULL,
    trap_family                     VARCHAR(64)     NULL,
    target_difficulty               NUMERIC(5, 4)   NOT NULL,
    item_difficulty                 NUMERIC(5, 4)   NOT NULL,
    zpd_lower_bound                 NUMERIC(5, 4)   NOT NULL,
    zpd_upper_bound                 NUMERIC(5, 4)   NOT NULL,
    prompt_latex                    TEXT            NOT NULL,
    prompt_instruction_tr           VARCHAR(512)    NOT NULL,
    prompt_instruction_en           VARCHAR(512)    NOT NULL,
    canonical_solution_expr         VARCHAR(512)    NOT NULL,
    trap_distractor_expr            VARCHAR(512)    NULL,
    sympy_verification_hash         CHAR(64)        NOT NULL,
    is_twin_verification            BOOLEAN         NOT NULL DEFAULT FALSE,
    independent_lock_seconds        INT             NOT NULL DEFAULT 60,
    unlocked_at                     TIMESTAMPTZ     NULL,
    hints_unlocked_count            INT             NOT NULL DEFAULT 0,
    attempt_count                   INT             NOT NULL DEFAULT 0,
    item_status                     VARCHAR(32)     NOT NULL DEFAULT 'PENDING',
    submitted_answer_latex          VARCHAR(512)    NULL,
    detected_trap_triggered          BOOLEAN         NOT NULL DEFAULT FALSE,
    verification_duration_ms        BIGINT          NULL,
    submitted_at                    TIMESTAMPTZ     NULL,
    created_at                      TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_practice_session_items PRIMARY KEY (id),
    CONSTRAINT fk_practice_session_items_session
        FOREIGN KEY (session_id)
        REFERENCES practice_sessions (id)
        ON DELETE CASCADE,
    CONSTRAINT uq_practice_session_items_ordinal
        UNIQUE (session_id, ordinal_index),
    CONSTRAINT chk_practice_items_slot_role
        CHECK (slot_role IN ('TRAP_CLOSURE', 'SPACED_DRILL', 'EXAM_SYNTHESIS')),
    CONSTRAINT chk_practice_items_source
        CHECK (question_source IN ('CURATED_BANK', 'DETERMINISTIC_TWIN_TIER1', 'SYNTHESIZED_TWIN_TIER2')),
    CONSTRAINT chk_practice_items_status
        CHECK (item_status IN ('PENDING', 'SUBMITTED_CORRECT', 'SUBMITTED_INCORRECT', 'SKIPPED')),
    CONSTRAINT chk_practice_items_difficulty
        CHECK (target_difficulty >= 0.0000 AND target_difficulty <= 1.0000
               AND item_difficulty >= 0.0000 AND item_difficulty <= 1.0000
               AND zpd_lower_bound >= 0.0000 AND zpd_upper_bound <= 1.0000
               AND zpd_lower_bound <= zpd_upper_bound),
    CONSTRAINT chk_practice_items_lock_seconds
        CHECK (independent_lock_seconds BETWEEN 0 AND 300)
);

CREATE INDEX IF NOT EXISTS idx_practice_session_items_user_session
    ON practice_session_items (user_id, session_id, ordinal_index ASC);

CREATE INDEX IF NOT EXISTS idx_practice_session_items_user_skill_created
    ON practice_session_items (user_id, skill_id, created_at DESC);
