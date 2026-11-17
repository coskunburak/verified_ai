-- ============================================================================
-- V061: Sprint 9.7 Post-Assessment Error Attribution & Net-Gain ROI Simulator
-- ADR-005 ($0.00 deterministic), ADR-006 (training_eligible = FALSE)
-- ============================================================================

CREATE TABLE exam_session_item_attributions (
    attribution_id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES exam_sessions(id) ON DELETE CASCADE,
    item_evaluation_id UUID NOT NULL REFERENCES exam_session_item_evaluations(evaluation_id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    section_code VARCHAR(64) NOT NULL,
    topic_id VARCHAR(128) NOT NULL,
    subtopic_id VARCHAR(128),
    skill_id VARCHAR(128) NOT NULL,
    item_outcome VARCHAR(16) NOT NULL,
    attribution_category VARCHAR(32) NOT NULL,
    matched_mistake_code VARCHAR(64),
    evidence_source VARCHAR(32) NOT NULL DEFAULT 'MASTERY_THRESHOLD',
    time_spent_seconds INTEGER NOT NULL CHECK (time_spent_seconds >= 0),
    expected_solve_seconds INTEGER NOT NULL CHECK (expected_solve_seconds > 0),
    mastery_snapshot NUMERIC(5,4) NOT NULL CHECK (mastery_snapshot >= 0.0000 AND mastery_snapshot <= 1.0000),
    estimated_net_impact NUMERIC(6,3) NOT NULL CHECK (estimated_net_impact >= 0.000 AND estimated_net_impact <= 5.000),
    recommended_action_type VARCHAR(32) NOT NULL,
    recommended_target_ref VARCHAR(128) NOT NULL,
    rationale_tr TEXT NOT NULL,
    rationale_en TEXT NOT NULL,
    attribution_metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    llm_tokens_used INTEGER NOT NULL DEFAULT 0,
    training_eligible BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_exam_item_attribution_eval UNIQUE (item_evaluation_id),
    CONSTRAINT chk_exam_attr_category CHECK (
        attribution_category IN (
            'CONCEPTUAL_GAP',
            'RECURRING_MISTAKE_TRAP',
            'TIME_PRESSURE_SLIP',
            'STRATEGIC_OMISSION'
        )
    ),
    CONSTRAINT chk_exam_attr_outcome CHECK (
        item_outcome IN ('WRONG', 'OMITTED')
    ),
    CONSTRAINT chk_exam_attr_action_type CHECK (
        recommended_action_type IN (
            'MICRO_LESSON',
            'TWIN_DRILL',
            'TIMED_SPEED_DRILL',
            'TRIAGE_STRATEGY_REVIEW'
        )
    ),
    CONSTRAINT chk_exam_attr_evidence_source CHECK (
        evidence_source IN (
            'SCRATCHPAD_STEP_DIAGNOSIS',
            'DISTRACTOR_TRAP_MAP',
            'MISTAKE_DNA_PROFILE',
            'MASTERY_THRESHOLD',
            'TIMING_HEURISTIC',
            'OMISSION_DEFAULT'
        )
    ),
    CONSTRAINT chk_exam_attr_zero_llm_tokens CHECK (llm_tokens_used = 0),
    CONSTRAINT chk_exam_attr_not_training_eligible CHECK (training_eligible = FALSE)
);

CREATE INDEX idx_exam_attr_session_id
    ON exam_session_item_attributions (session_id, estimated_net_impact DESC);

CREATE INDEX idx_exam_attr_user_skill
    ON exam_session_item_attributions (user_id, skill_id, created_at DESC);

CREATE INDEX idx_exam_attr_user_category
    ON exam_session_item_attributions (user_id, attribution_category);

CREATE TABLE exam_study_reallocation_batches (
    batch_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    source_session_id UUID NOT NULL REFERENCES exam_sessions(id) ON DELETE CASCADE,
    exam_code VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    total_lost_net NUMERIC(6,2) NOT NULL CHECK (total_lost_net >= 0.00),
    category_breakdown_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    top_roi_plan_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    rebalanced_study_plan_id UUID,
    applied_to_study_plan BOOLEAN NOT NULL DEFAULT FALSE,
    applied_at TIMESTAMPTZ,
    algorithm_version VARCHAR(32) NOT NULL DEFAULT 'H03_ATTRIBUTION_V1',
    llm_tokens_used INTEGER NOT NULL DEFAULT 0,
    training_eligible BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_exam_realloc_session_idempotency UNIQUE (source_session_id, idempotency_key),
    CONSTRAINT chk_exam_realloc_zero_llm_tokens CHECK (llm_tokens_used = 0),
    CONSTRAINT chk_exam_realloc_not_training_eligible CHECK (training_eligible = FALSE)
);

CREATE INDEX idx_exam_realloc_user_created
    ON exam_study_reallocation_batches (user_id, created_at DESC);

CREATE INDEX idx_exam_realloc_source_session
    ON exam_study_reallocation_batches (source_session_id);
