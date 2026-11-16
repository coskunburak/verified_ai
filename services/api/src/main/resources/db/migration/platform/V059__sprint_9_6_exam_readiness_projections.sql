-- ============================================================================
-- V059: Sprint 9.6 Exam Readiness Model, 90% Credible-Interval Net Projection
--       & Active-Trap Penalty Semantics (CNT-LOS-11)
-- Phase 09 / INV-EXAM-001 (Sparse-Evidence PRELIMINARY Guard)
--         / INV-EXAM-004 ($0.00 LLM Cost)
--         / ADR-006 (training_eligible=FALSE)
--         / ADR-011 (Feature-Flagged Exam Platform)
-- Reconciled with V002 users(id), V054 student_exam_targets(target_id),
-- and V057 exam_sessions(id).
-- ============================================================================

-- 1. Canonical Exam Readiness Projections (One Current Snapshot per User & Exam)
CREATE TABLE IF NOT EXISTS exam_readiness_projections (
    projection_id                        UUID PRIMARY KEY,
    user_id                              UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    exam_code                            VARCHAR(64) NOT NULL,
    target_id                            UUID NULL REFERENCES student_exam_targets(target_id) ON DELETE SET NULL,
    latest_scored_session_id             UUID NULL REFERENCES exam_sessions(id) ON DELETE SET NULL,
    total_question_count                 INTEGER NOT NULL CHECK (total_question_count > 0),
    wrong_answer_penalty_ratio           NUMERIC(6,4) NOT NULL CHECK (wrong_answer_penalty_ratio >= 0.0000 AND wrong_answer_penalty_ratio <= 1.0000),
    estimated_net_low                    NUMERIC(8,2) NOT NULL,
    estimated_net_mid                    NUMERIC(8,2) NOT NULL,
    estimated_net_high                   NUMERIC(8,2) NOT NULL,
    credible_interval_level              NUMERIC(4,2) NOT NULL DEFAULT 0.90 CHECK (credible_interval_level = 0.90),
    target_net_score                     NUMERIC(8,2) NULL,
    net_gap_to_target                    NUMERIC(8,2) NULL,
    net_lost_to_traps                    NUMERIC(8,2) NOT NULL CHECK (net_lost_to_traps >= 0.00),
    pacing_factor                        NUMERIC(6,4) NOT NULL CHECK (pacing_factor > 0.0000 AND pacing_factor <= 1.0000),
    mean_retention_probability           NUMERIC(6,4) NOT NULL CHECK (mean_retention_probability >= 0.0000 AND mean_retention_probability <= 1.0000),
    epistemic_confidence                 NUMERIC(6,4) NOT NULL CHECK (epistemic_confidence >= 0.0000 AND epistemic_confidence <= 1.0000),
    confidence_tier                      VARCHAR(32) NOT NULL CHECK (confidence_tier IN ('PRELIMINARY', 'CALIBRATING', 'ESTABLISHED', 'HIGH_CONFIDENCE')),
    calibration_required                 BOOLEAN NOT NULL,
    recommended_diagnostic_question_count INTEGER NOT NULL CHECK (recommended_diagnostic_question_count >= 0),
    evaluated_skill_count                INTEGER NOT NULL CHECK (evaluated_skill_count >= 0),
    total_evidence_count                 INTEGER NOT NULL CHECK (total_evidence_count >= 0),
    completed_mock_count                 INTEGER NOT NULL CHECK (completed_mock_count >= 0),
    active_trap_breakdown_json           JSONB NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(active_trap_breakdown_json) = 'array'),
    section_readiness_json               JSONB NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(section_readiness_json) = 'array'),
    top_roi_allocations_json             JSONB NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(top_roi_allocations_json) = 'array'),
    methodology_version                  VARCHAR(32) NOT NULL DEFAULT 'EXAM_READINESS_V1',
    training_eligible                    BOOLEAN NOT NULL DEFAULT FALSE CHECK (training_eligible = FALSE),
    llm_tokens_used                      INTEGER NOT NULL DEFAULT 0 CHECK (llm_tokens_used = 0),
    inference_cost_usd                   NUMERIC(10,6) NOT NULL DEFAULT 0.000000 CHECK (inference_cost_usd = 0.000000),
    computed_at                          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_exam_readiness_net_interval_order CHECK (
        estimated_net_low <= estimated_net_mid AND estimated_net_mid <= estimated_net_high
    ),
    CONSTRAINT chk_exam_readiness_net_bounds CHECK (
        estimated_net_low >= -(total_question_count * wrong_answer_penalty_ratio)
        AND estimated_net_high <= total_question_count
    ),
    CONSTRAINT chk_exam_readiness_preliminary_guard CHECK (
        (epistemic_confidence < 0.2500
            AND confidence_tier = 'PRELIMINARY'
            AND calibration_required = TRUE
            AND recommended_diagnostic_question_count >= 10)
        OR (epistemic_confidence >= 0.2500)
    ),
    CONSTRAINT uq_exam_readiness_user_exam UNIQUE (user_id, exam_code)
);

CREATE INDEX IF NOT EXISTS idx_exam_readiness_user_computed
    ON exam_readiness_projections (user_id, computed_at DESC);

-- 2. Auditable High-ROI Topic Allocations Linked to Projection
CREATE TABLE IF NOT EXISTS exam_readiness_topic_allocations (
    allocation_id               UUID PRIMARY KEY,
    projection_id               UUID NOT NULL REFERENCES exam_readiness_projections(projection_id) ON DELETE CASCADE ON UPDATE CASCADE,
    user_id                     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    exam_code                   VARCHAR(64) NOT NULL,
    section_code                VARCHAR(64) NOT NULL,
    skill_code                  VARCHAR(128) NOT NULL,
    topic_title_key             VARCHAR(128) NOT NULL,
    blueprint_question_weight   NUMERIC(6,2) NOT NULL CHECK (blueprint_question_weight >= 0.00),
    current_mastery             NUMERIC(6,4) NOT NULL CHECK (current_mastery >= 0.0000 AND current_mastery <= 1.0000),
    retention_probability       NUMERIC(6,4) NOT NULL CHECK (retention_probability >= 0.0000 AND retention_probability <= 1.0000),
    expected_net_gain_per_hour  NUMERIC(6,3) NOT NULL CHECK (expected_net_gain_per_hour >= 0.000),
    recommended_study_minutes   INTEGER NOT NULL CHECK (recommended_study_minutes > 0),
    priority_rank               INTEGER NOT NULL CHECK (priority_rank >= 1),
    rationale_code              VARCHAR(64) NOT NULL CHECK (rationale_code IN (
        'HIGH_BLUEPRINT_WEIGHT_LOW_MASTERY',
        'RETENTION_DECAY_RECOVERY',
        'ACTIVE_TRAP_MITIGATION',
        'CALIBRATION_EVIDENCE_GAP'
    )),
    training_eligible           BOOLEAN NOT NULL DEFAULT FALSE CHECK (training_eligible = FALSE),
    CONSTRAINT uq_exam_readiness_allocation_rank UNIQUE (projection_id, priority_rank)
);

CREATE INDEX IF NOT EXISTS idx_exam_readiness_allocations_user
    ON exam_readiness_topic_allocations (user_id, exam_code);
