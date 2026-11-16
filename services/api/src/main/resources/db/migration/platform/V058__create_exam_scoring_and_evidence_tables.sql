-- ============================================================================
-- V058: Sprint 9.5 Exam Scoring Engine, ÖSYM Net Calculation Policy &
--       Per-Item Deterministic Evidence Traceability
-- Phase 09 / INV-EXAM-004 ($0.00 LLM Cost) / ADR-006 (training_eligible=FALSE) / ADR-011
-- Reconciled with V054 (exam_definitions(id)), V056 (exam_assembled_assessments(id),
-- exam_question_bank_items(id)), and V057 (exam_sessions(id), exam_session_items(id)).
-- ============================================================================

-- 1. Canonical Exam Session Score Snapshots
CREATE TABLE IF NOT EXISTS exam_session_scores (
    score_id                    UUID PRIMARY KEY,
    session_id                  UUID NOT NULL UNIQUE REFERENCES exam_sessions(id) ON DELETE CASCADE,
    user_id                     UUID NOT NULL,
    exam_definition_id          UUID NOT NULL REFERENCES exam_definitions(id) ON DELETE RESTRICT,
    assembled_assessment_id     UUID NOT NULL REFERENCES exam_assembled_assessments(id) ON DELETE RESTRICT,
    exam_code                   VARCHAR(64) NOT NULL,
    scoring_formula_type        VARCHAR(40) NOT NULL CHECK (scoring_formula_type IN (
        'CORRECT_MINUS_QUARTER_WRONG',
        'RAW_CORRECT_ONLY',
        'WEIGHTED_SECTION_RUBRIC'
    )),
    wrong_penalty_ratio         NUMERIC(5,4) NOT NULL CHECK (wrong_penalty_ratio >= 0.0000 AND wrong_penalty_ratio <= 1.0000),
    total_questions             INTEGER NOT NULL CHECK (total_questions BETWEEN 1 AND 200),
    correct_count               INTEGER NOT NULL CHECK (correct_count >= 0),
    wrong_count                 INTEGER NOT NULL CHECK (wrong_count >= 0),
    blank_count                 INTEGER NOT NULL CHECK (blank_count >= 0),
    raw_correct_score           NUMERIC(8,2) NOT NULL,
    wrong_penalty_deduction     NUMERIC(8,2) NOT NULL CHECK (wrong_penalty_deduction >= 0.00),
    net_score                   NUMERIC(8,2) NOT NULL,
    min_possible_net_score      NUMERIC(8,2) NOT NULL,
    max_possible_net_score      NUMERIC(8,2) NOT NULL,
    normalized_percentage_score NUMERIC(6,2) NOT NULL CHECK (normalized_percentage_score >= 0.00 AND normalized_percentage_score <= 100.00),
    accuracy_percentage         NUMERIC(6,2) NOT NULL CHECK (accuracy_percentage >= 0.00 AND accuracy_percentage <= 100.00),
    total_time_spent_seconds    INTEGER NOT NULL CHECK (total_time_spent_seconds >= 0),
    average_seconds_per_item    NUMERIC(8,2) NOT NULL CHECK (average_seconds_per_item >= 0.00),
    topic_breakdown_json        JSONB NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(topic_breakdown_json) = 'array'),
    difficulty_breakdown_json   JSONB NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(difficulty_breakdown_json) = 'array'),
    score_version               INTEGER NOT NULL DEFAULT 1 CHECK (score_version >= 1),
    session_submitted_at        TIMESTAMPTZ NOT NULL,
    scored_at                   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_regraded_at            TIMESTAMPTZ NULL,
    llm_tokens_used             INTEGER NOT NULL DEFAULT 0 CHECK (llm_tokens_used = 0),
    inference_cost_usd          NUMERIC(10,6) NOT NULL DEFAULT 0.000000 CHECK (inference_cost_usd = 0.000000),
    training_eligible           BOOLEAN NOT NULL DEFAULT FALSE CHECK (training_eligible = FALSE),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_exam_session_scores_counts_sum CHECK (correct_count + wrong_count + blank_count = total_questions),
    CONSTRAINT chk_exam_session_scores_net_bounds CHECK (net_score >= min_possible_net_score AND net_score <= max_possible_net_score)
);

-- 2. Per-Item Deterministic Evaluation & Evidence Traceability
CREATE TABLE IF NOT EXISTS exam_session_item_evaluations (
    evaluation_id                        UUID PRIMARY KEY,
    score_id                             UUID NOT NULL REFERENCES exam_session_scores(score_id) ON DELETE CASCADE,
    session_id                           UUID NOT NULL REFERENCES exam_sessions(id) ON DELETE CASCADE,
    user_id                              UUID NOT NULL,
    session_item_id                      UUID NOT NULL REFERENCES exam_session_items(id) ON DELETE CASCADE,
    question_item_id                     UUID NOT NULL REFERENCES exam_question_bank_items(id) ON DELETE RESTRICT,
    position_index                       INTEGER NOT NULL CHECK (position_index >= 1 AND position_index <= 200),
    question_code                        VARCHAR(64) NOT NULL,
    topic_code                           VARCHAR(128) NOT NULL,
    topic_title                          VARCHAR(160) NOT NULL,
    difficulty_band                      VARCHAR(24) NOT NULL CHECK (difficulty_band IN ('EASY', 'MODERATE', 'HARD', 'ELITE')),
    cognitive_level                      VARCHAR(24) NOT NULL CHECK (cognitive_level IN ('RECALL', 'APPLICATION', 'ANALYSIS', 'SYNTHESIS')),
    format_type                          VARCHAR(32) NOT NULL CHECK (format_type IN ('SINGLE_CHOICE_MCQ', 'MULTI_SELECT_MCQ', 'NUMERIC_GRID_IN')),
    evaluation_outcome                   VARCHAR(16) NOT NULL CHECK (evaluation_outcome IN ('CORRECT', 'WRONG', 'BLANK')),
    selected_option_key                  VARCHAR(64) NULL,
    numeric_response_value               NUMERIC(12,4) NULL,
    correct_option_key                   VARCHAR(64) NULL,
    expected_numeric_answer              NUMERIC(12,4) NULL,
    numeric_tolerance                    NUMERIC(12,6) NULL,
    raw_points_awarded                   NUMERIC(6,2) NOT NULL,
    penalty_points_deducted              NUMERIC(6,2) NOT NULL CHECK (penalty_points_deducted >= 0.00),
    net_points_contribution              NUMERIC(6,2) NOT NULL,
    is_flagged_for_review                BOOLEAN NOT NULL DEFAULT FALSE,
    time_spent_seconds                   INTEGER NOT NULL DEFAULT 0 CHECK (time_spent_seconds >= 0),
    selected_option_distractor_rationale TEXT NULL,
    solution_explanation_markdown        TEXT NOT NULL,
    evidence_source_ref                  VARCHAR(160) NOT NULL,
    f28_scratchpad_status                VARCHAR(32) NOT NULL CHECK (f28_scratchpad_status IN ('NONE', 'ATTACHED_METADATA_ONLY')),
    scratchpad_asset_count               INTEGER NOT NULL DEFAULT 0 CHECK (scratchpad_asset_count >= 0),
    scratchpad_asset_ids_json            JSONB NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(scratchpad_asset_ids_json) = 'array'),
    question_version                     INTEGER NOT NULL DEFAULT 1 CHECK (question_version >= 1),
    llm_tokens_used                      INTEGER NOT NULL DEFAULT 0 CHECK (llm_tokens_used = 0),
    training_eligible                    BOOLEAN NOT NULL DEFAULT FALSE CHECK (training_eligible = FALSE),
    evaluated_at                         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_exam_item_eval_session_position UNIQUE (session_id, position_index),
    CONSTRAINT uq_exam_item_eval_session_item UNIQUE (session_id, session_item_id)
);

-- 3. Immutable Audit Log for Explicit Regrades
CREATE TABLE IF NOT EXISTS exam_score_regrade_audits (
    regrade_audit_id       UUID PRIMARY KEY,
    score_id               UUID NOT NULL REFERENCES exam_session_scores(score_id) ON DELETE CASCADE,
    session_id             UUID NOT NULL REFERENCES exam_sessions(id) ON DELETE CASCADE,
    user_id                UUID NOT NULL,
    previous_score_version INTEGER NOT NULL CHECK (previous_score_version >= 1),
    new_score_version      INTEGER NOT NULL CHECK (new_score_version = previous_score_version + 1),
    previous_net_score     NUMERIC(8,2) NOT NULL,
    new_net_score          NUMERIC(8,2) NOT NULL,
    previous_correct_count INTEGER NOT NULL CHECK (previous_correct_count >= 0),
    new_correct_count      INTEGER NOT NULL CHECK (new_correct_count >= 0),
    previous_wrong_count   INTEGER NOT NULL CHECK (previous_wrong_count >= 0),
    new_wrong_count        INTEGER NOT NULL CHECK (new_wrong_count >= 0),
    previous_blank_count   INTEGER NOT NULL CHECK (previous_blank_count >= 0),
    new_blank_count        INTEGER NOT NULL CHECK (new_blank_count >= 0),
    reason_code            VARCHAR(48) NOT NULL CHECK (reason_code IN (
        'ANSWER_KEY_CORRECTION',
        'PENALTY_POLICY_SYNC',
        'ADMIN_MANUAL_REGRADE'
    )),
    reason_detail          VARCHAR(500) NOT NULL,
    triggered_by           VARCHAR(120) NOT NULL,
    delta_summary_json     JSONB NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(delta_summary_json) = 'object'),
    training_eligible      BOOLEAN NOT NULL DEFAULT FALSE CHECK (training_eligible = FALSE),
    regraded_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_exam_score_regrade_version UNIQUE (score_id, new_score_version)
);

-- 4. Required Indexes
CREATE INDEX IF NOT EXISTS idx_exam_session_scores_user_scored_at
    ON exam_session_scores(user_id, scored_at DESC);

CREATE INDEX IF NOT EXISTS idx_exam_session_scores_exam_def
    ON exam_session_scores(exam_definition_id, scored_at DESC);

CREATE INDEX IF NOT EXISTS idx_exam_item_evals_session_pos
    ON exam_session_item_evaluations(session_id, position_index ASC);

CREATE INDEX IF NOT EXISTS idx_exam_item_evals_user_topic
    ON exam_session_item_evaluations(user_id, topic_code, evaluation_outcome);

CREATE INDEX IF NOT EXISTS idx_exam_regrade_audits_session
    ON exam_score_regrade_audits(session_id, regraded_at DESC);
