-- ============================================================================
-- Flyway Migration V042: Mastery Calibration and Retention Snapshots
-- Sprint 7.10 — Learning Intelligence Evaluation & Phase 07 Exit Gate
-- Enforces: DOC-86 §5.2, DOC-91 §5 (G-PED-05), DOC-91 §6 (G-PRIV-01)
-- ============================================================================

CREATE TABLE IF NOT EXISTS learner_mastery_calibration_records (
    id                           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                      UUID NOT NULL,
    attempt_id                   UUID NOT NULL,
    canonical_skill_code         VARCHAR(100) NOT NULL,
    predicted_retention_prob     NUMERIC(5,4) NOT NULL
        CONSTRAINT chk_calibration_predicted_prob
        CHECK (predicted_retention_prob >= 0.0500 AND predicted_retention_prob <= 0.9900),
    observed_independent_outcome SMALLINT NOT NULL
        CONSTRAINT chk_calibration_observed_outcome
        CHECK (observed_independent_outcome IN (0, 1)),
    brier_residual               NUMERIC(6,5) NOT NULL
        CONSTRAINT chk_calibration_brier_residual
        CHECK (brier_residual >= 0.00000 AND brier_residual <= 1.00000),
    created_at                   TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_calibration_attempt_skill
        UNIQUE (attempt_id, canonical_skill_code)
);

CREATE INDEX IF NOT EXISTS idx_calibration_skill_created
    ON learner_mastery_calibration_records(canonical_skill_code, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_calibration_user_created
    ON learner_mastery_calibration_records(user_id, created_at DESC);

COMMENT ON TABLE learner_mastery_calibration_records IS
    'Deterministic psychometric calibration audit log pairing prior retention probability R_{u,s}(t) with observed unassisted outcome O_{u,a} (G-PED-05 / G-PRIV-01).';
