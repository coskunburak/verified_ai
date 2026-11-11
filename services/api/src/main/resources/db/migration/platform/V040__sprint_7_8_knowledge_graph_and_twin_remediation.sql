-- ============================================================================
-- V040: Sprint 7.8 Prerequisite Knowledge Graph & Closed-Loop Twin Remediation
-- Invariants: BOLA composite keys, 60s lock_until, training_eligible = FALSE
-- ============================================================================

-- 1. Consecutive failures & direct practice counters on learner_skill_mastery
ALTER TABLE learner_skill_mastery
    ADD COLUMN IF NOT EXISTS tenant_id UUID NULL,
    ADD COLUMN IF NOT EXISTS curriculum_code VARCHAR(64) NULL,
    ADD COLUMN IF NOT EXISTS mastery_state VARCHAR(32) NOT NULL DEFAULT 'UNSEEN',
    ADD COLUMN IF NOT EXISTS consecutive_failures INT NOT NULL DEFAULT 0
        CONSTRAINT chk_lsm_consecutive_failures_non_negative CHECK (consecutive_failures >= 0),
    ADD COLUMN IF NOT EXISTS direct_practice_count INT NOT NULL DEFAULT 0
        CONSTRAINT chk_lsm_direct_practice_non_negative CHECK (direct_practice_count >= 0);

-- Migrate legacy V031 twin drills table if present
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'counterfactual_twin_drills' AND column_name = 'id'
    ) THEN
        ALTER TABLE counterfactual_twin_drills RENAME TO legacy_counterfactual_twin_drills;
    END IF;
END $$;

-- 2. Counterfactual Twin Drills Table (60s Solution Lock & Zero-Secret)
CREATE TABLE counterfactual_twin_drills (
    twin_id                  UUID PRIMARY KEY,
    tenant_id                UUID NOT NULL,
    user_id                  UUID NOT NULL,
    source_attempt_id        UUID NOT NULL,
    curriculum_code          VARCHAR(64) NOT NULL,
    target_skill_code        VARCHAR(128) NOT NULL,
    root_cause_skill_code    VARCHAR(128) NOT NULL,
    problem_statement_latex  TEXT NOT NULL,
    expected_canonical_expr  TEXT NOT NULL,
    solution_steps_json      JSONB NOT NULL,
    generator_seed           VARCHAR(64) NOT NULL,
    verifier_proof_hash      CHAR(64) NOT NULL,
    status                   VARCHAR(32) NOT NULL DEFAULT 'ISSUED',
    created_at               TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    lock_until               TIMESTAMPTZ NOT NULL,
    solution_revealed_at     TIMESTAMPTZ NULL,
    completed_at             TIMESTAMPTZ NULL,
    training_eligible        BOOLEAN NOT NULL DEFAULT FALSE,

    -- Exact 60-Second Solution Lock Invariant (DOC-84)
    CONSTRAINT chk_twin_exact_60s_lock
        CHECK (lock_until = created_at + INTERVAL '60 seconds'),

    -- Status Enum Invariant
    CONSTRAINT chk_twin_status
        CHECK (status IN ('ISSUED', 'SOLVED_UNASSISTED', 'SOLUTION_REVEALED_ASSISTED', 'FAILED_UNASSISTED', 'EXPIRED')),

    -- Zero-Secret / Privacy Invariant
    CONSTRAINT chk_twin_training_ineligible
        CHECK (training_eligible = FALSE),

    -- Revealed timestamp consistency
    CONSTRAINT chk_twin_reveal_consistency
        CHECK (
            (status = 'SOLUTION_REVEALED_ASSISTED' AND solution_revealed_at IS NOT NULL) OR
            (status <> 'SOLUTION_REVEALED_ASSISTED')
        ),

    -- Composite Unique Key for BOLA-safe child references
    CONSTRAINT uq_twin_tenant_user_id UNIQUE (tenant_id, user_id, twin_id)
);

CREATE INDEX idx_twin_drills_user_lookup
    ON counterfactual_twin_drills (tenant_id, user_id, source_attempt_id, created_at DESC);

CREATE INDEX idx_twin_drills_skill_lookup
    ON counterfactual_twin_drills (tenant_id, user_id, target_skill_code, status);

-- 3. Counterfactual Twin Submissions (Append-Only Audit & Evidence Log)
CREATE TABLE counterfactual_twin_submissions (
    submission_id            UUID PRIMARY KEY,
    tenant_id                UUID NOT NULL,
    user_id                  UUID NOT NULL,
    twin_id                  UUID NOT NULL,
    action_type              VARCHAR(32) NOT NULL,
    submitted_answer_latex   TEXT NULL,
    is_mathematically_equal  BOOLEAN NOT NULL DEFAULT FALSE,
    evidence_classification  VARCHAR(32) NOT NULL,
    remediation_credit       NUMERIC(5,4) NOT NULL DEFAULT 0.0000,
    submitted_at             TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    training_eligible        BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_twin_sub_parent
        FOREIGN KEY (tenant_id, user_id, twin_id)
        REFERENCES counterfactual_twin_drills (tenant_id, user_id, twin_id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_twin_sub_action
        CHECK (action_type IN ('SUBMIT_ANSWER', 'REVEAL_SOLUTION')),

    CONSTRAINT chk_twin_sub_evidence
        CHECK (evidence_classification IN (
            'VERIFIED_UNASSISTED_SUCCESS',
            'UNASSISTED_INCORRECT',
            'SOLUTION_REVEALED_ASSISTED'
        )),

    -- Assisted reveal MUST yield 0.0000 remediation credit
    CONSTRAINT chk_twin_sub_credit_bounds
        CHECK (
            (evidence_classification = 'SOLUTION_REVEALED_ASSISTED' AND remediation_credit = 0.0000) OR
            (evidence_classification = 'UNASSISTED_INCORRECT' AND remediation_credit = 0.0000) OR
            (evidence_classification = 'VERIFIED_UNASSISTED_SUCCESS' AND remediation_credit > 0.0000 AND remediation_credit <= 1.0000)
        ),

    CONSTRAINT chk_twin_sub_training_ineligible
        CHECK (training_eligible = FALSE)
);

CREATE INDEX idx_twin_sub_lookup
    ON counterfactual_twin_submissions (tenant_id, user_id, twin_id, submitted_at DESC);

-- 4. Immutability Trigger for Append-Only Twin Submissions & Core Drill Fields
CREATE OR REPLACE FUNCTION trg_enforce_twin_immutability()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_TABLE_NAME = 'counterfactual_twin_submissions' THEN
        RAISE EXCEPTION 'TERMINAL_INVARIANT_VIOLATION: counterfactual_twin_submissions is append-only.';
    END IF;

    IF TG_TABLE_NAME = 'counterfactual_twin_drills' THEN
        IF OLD.created_at <> NEW.created_at OR
           OLD.lock_until <> NEW.lock_until OR
           OLD.user_id <> NEW.user_id OR
           OLD.tenant_id <> NEW.tenant_id OR
           OLD.expected_canonical_expr <> NEW.expected_canonical_expr OR
           OLD.training_eligible <> NEW.training_eligible THEN
            RAISE EXCEPTION 'TERMINAL_INVARIANT_VIOLATION: Immutable columns on counterfactual_twin_drills cannot be modified.';
        END IF;

        IF OLD.status IN ('SOLVED_UNASSISTED', 'SOLUTION_REVEALED_ASSISTED', 'EXPIRED') AND OLD.status <> NEW.status THEN
            RAISE EXCEPTION 'TERMINAL_INVARIANT_VIOLATION: Cannot transition out of terminal twin status %', OLD.status;
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_twin_submissions_immutable
    BEFORE UPDATE OR DELETE ON counterfactual_twin_submissions
    FOR EACH ROW EXECUTE FUNCTION trg_enforce_twin_immutability();

CREATE TRIGGER trg_twin_drills_immutable_fields
    BEFORE UPDATE ON counterfactual_twin_drills
    FOR EACH ROW EXECUTE FUNCTION trg_enforce_twin_immutability();
