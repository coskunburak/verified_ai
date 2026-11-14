-- ============================================================================
-- Migration: V053__cold_start_diagnostics_and_quality_gates.sql
-- Sprint: 8.10 — Personalization Calibration, Cold-Start Baseline &
--               Recommendation Quality Gates (Phase 08 Exit Gate)
-- Invariants: ADR-006 training_eligible=FALSE, $0.00 LLM cost (llm_tokens_used=0),
--             G-PRIV-01..03, G-PED-01..05, CNT-LOS-06..09
-- ============================================================================

CREATE TABLE IF NOT EXISTS cold_start_diagnostics (
    diagnostic_id                   UUID            PRIMARY KEY,
    learner_id                      UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    schema_version                  VARCHAR(32)     NOT NULL DEFAULT 'cold-start-v1'
        CONSTRAINT ck_cold_start_diagnostics_schema_version CHECK (schema_version = 'cold-start-v1'),
    idempotency_key                 VARCHAR(128)    NOT NULL
        CONSTRAINT ck_cold_start_diagnostics_idempotency_non_blank CHECK (length(btrim(idempotency_key)) > 0),
    locale                          VARCHAR(16)     NOT NULL DEFAULT 'en-US'
        CONSTRAINT ck_cold_start_diagnostics_locale CHECK (locale IN ('tr-TR', 'en-US')),
    status                          VARCHAR(32)     NOT NULL
        CONSTRAINT ck_cold_start_diagnostics_status CHECK (status IN ('COMPLETED', 'REVALIDATION_REQUIRED')),
    baseline_tier                   VARCHAR(32)     NOT NULL
        CONSTRAINT ck_cold_start_diagnostics_tier CHECK (baseline_tier IN ('FOUNDATION_SOLID', 'CALIBRATED_MIXED', 'FOUNDATION_REPAIR')),
    correct_count                   INTEGER         NOT NULL
        CONSTRAINT ck_cold_start_diagnostics_correct_count CHECK (correct_count BETWEEN 0 AND 3),
    total_items                     INTEGER         NOT NULL DEFAULT 3
        CONSTRAINT ck_cold_start_diagnostics_total_items CHECK (total_items = 3),
    calibration_mode                VARCHAR(32)     NOT NULL
        CONSTRAINT ck_cold_start_diagnostics_mode CHECK (calibration_mode IN ('EXPLORATION', 'ACTIVE_CALIBRATED', 'REVALIDATION_REQUIRED')),
    confidence_tier                 VARCHAR(32)     NOT NULL
        CONSTRAINT ck_cold_start_diagnostics_confidence CHECK (confidence_tier IN ('PRELIMINARY', 'CALIBRATED')),
    goal_conflict_resolved          BOOLEAN         NOT NULL DEFAULT FALSE,
    recommended_daily_budget_minutes INTEGER        NOT NULL
        CONSTRAINT ck_cold_start_diagnostics_budget CHECK (recommended_daily_budget_minutes BETWEEN 5 AND 240),
    inactive_decay_applied          BOOLEAN         NOT NULL DEFAULT FALSE,
    explicit_preference_override    BOOLEAN         NOT NULL DEFAULT FALSE,
    declared_exam_target            VARCHAR(32)     NULL
        CONSTRAINT ck_cold_start_diagnostics_exam_target CHECK (declared_exam_target IS NULL OR declared_exam_target IN ('TYT', 'AYT', 'LGS', 'SAT', 'GENERAL')),
    declared_daily_budget_minutes   INTEGER         NULL
        CONSTRAINT ck_cold_start_diagnostics_declared_budget CHECK (declared_daily_budget_minutes IS NULL OR declared_daily_budget_minutes BETWEEN 5 AND 240),
    preferred_modality              VARCHAR(32)     NULL
        CONSTRAINT ck_cold_start_diagnostics_modality CHECK (preferred_modality IS NULL OR preferred_modality IN ('PRACTICE_FIRST', 'SOCRATIC_COACH', 'MIXED', 'VIDEO_HINTS_OFF')),
    detected_trap_archetypes_json   JSONB           NOT NULL DEFAULT '[]'::jsonb,
    item_outcomes_json              JSONB           NOT NULL DEFAULT '[]'::jsonb,
    initialized_priors_json         JSONB           NOT NULL DEFAULT '{}'::jsonb,
    rationales_json                 JSONB           NOT NULL DEFAULT '[]'::jsonb,
    revalidation_prompt_key         VARCHAR(128)    NULL,
    attempt_duration_seconds        INTEGER         NOT NULL DEFAULT 0
        CONSTRAINT ck_cold_start_diagnostics_duration CHECK (attempt_duration_seconds BETWEEN 0 AND 1800),
    llm_tokens_used                 INTEGER         NOT NULL DEFAULT 0
        CONSTRAINT ck_cold_start_diagnostics_zero_llm CHECK (llm_tokens_used = 0),
    training_eligible               BOOLEAN         NOT NULL DEFAULT FALSE
        CONSTRAINT ck_cold_start_diagnostics_training_false CHECK (training_eligible = FALSE),
    created_at                      TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                      TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_cold_start_diagnostics_learner_idempotency UNIQUE (learner_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_cold_start_diagnostics_learner_created
    ON cold_start_diagnostics(learner_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_cold_start_diagnostics_status_created
    ON cold_start_diagnostics(learner_id, status, created_at DESC);

CREATE TABLE IF NOT EXISTS planner_quality_gate_snapshots (
    snapshot_id                     UUID            PRIMARY KEY,
    schema_version                  VARCHAR(32)     NOT NULL DEFAULT 'study-plan-quality-v1'
        CONSTRAINT ck_quality_gate_schema_version CHECK (schema_version = 'study-plan-quality-v1'),
    overall_status                  VARCHAR(16)     NOT NULL
        CONSTRAINT ck_quality_gate_overall_status CHECK (overall_status IN ('PASS', 'WARN', 'FAIL')),
    p0_regression_count             INTEGER         NOT NULL DEFAULT 0
        CONSTRAINT ck_quality_gate_p0_non_negative CHECK (p0_regression_count >= 0),
    p1_regression_count             INTEGER         NOT NULL DEFAULT 0
        CONSTRAINT ck_quality_gate_p1_non_negative CHECK (p1_regression_count >= 0),
    cnt_los_06_status               VARCHAR(16)     NOT NULL
        CONSTRAINT ck_quality_gate_cnt06 CHECK (cnt_los_06_status IN ('PASS', 'WARN', 'FAIL')),
    cnt_los_07_status               VARCHAR(16)     NOT NULL
        CONSTRAINT ck_quality_gate_cnt07 CHECK (cnt_los_07_status IN ('PASS', 'WARN', 'FAIL')),
    cnt_los_08_status               VARCHAR(16)     NOT NULL
        CONSTRAINT ck_quality_gate_cnt08 CHECK (cnt_los_08_status IN ('PASS', 'WARN', 'FAIL')),
    cnt_los_09_status               VARCHAR(16)     NOT NULL
        CONSTRAINT ck_quality_gate_cnt09 CHECK (cnt_los_09_status IN ('PASS', 'WARN', 'FAIL')),
    metrics_json                    JSONB           NOT NULL DEFAULT '{}'::jsonb,
    contract_checks_json            JSONB           NOT NULL DEFAULT '[]'::jsonb,
    evaluated_by_actor_id           UUID            NULL,
    llm_tokens_used                 INTEGER         NOT NULL DEFAULT 0
        CONSTRAINT ck_quality_gate_zero_llm CHECK (llm_tokens_used = 0),
    training_eligible               BOOLEAN         NOT NULL DEFAULT FALSE
        CONSTRAINT ck_quality_gate_training_false CHECK (training_eligible = FALSE),
    evaluated_at                    TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at                      TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_planner_quality_gate_snapshots_evaluated
    ON planner_quality_gate_snapshots(evaluated_at DESC);
