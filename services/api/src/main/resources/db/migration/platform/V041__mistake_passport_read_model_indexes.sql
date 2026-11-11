-- services/api/src/main/resources/db/migration/platform/V041__mistake_passport_read_model_indexes.sql
-- Sprint 7.9: Read-model projection tables and covering indexes for Mistake Passport (<50ms p95 target) and Dual-Ring Mastery Explorer

-- 1. Ensure table mistake_dna_profiles exists for read-model projection
CREATE TABLE IF NOT EXISTS mistake_dna_profiles (
    id UUID PRIMARY KEY,
    learner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    mistake_code VARCHAR(64) NOT NULL,
    mistake_family VARCHAR(64) NOT NULL,
    skill_node_id VARCHAR(96) NOT NULL,
    lifecycle_status VARCHAR(32) NOT NULL CHECK (lifecycle_status IN ('RECURRING_PATTERN', 'IN_REMEDIATION', 'RESOLVED_PENDING_RETENTION', 'STABLE_RESOLVED')),
    occurrence_count INT NOT NULL DEFAULT 1 CHECK (occurrence_count >= 1),
    consecutive_solo_passes INT NOT NULL DEFAULT 0 CHECK (consecutive_solo_passes >= 0),
    last_detected_at TIMESTAMPTZ NOT NULL,
    training_eligible BOOLEAN NOT NULL DEFAULT FALSE CHECK (training_eligible = FALSE)
);

-- 2. Ensure table learner_skill_mastery_states exists for 5-metric snapshot projection
CREATE TABLE IF NOT EXISTS learner_skill_mastery_states (
    learner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    skill_node_id VARCHAR(96) NOT NULL,
    observed_outcome_score NUMERIC(9,4) NOT NULL DEFAULT 0.0000 CHECK (observed_outcome_score BETWEEN 0.0000 AND 1.0000),
    independent_competence_score NUMERIC(9,4) NOT NULL DEFAULT 0.0000 CHECK (independent_competence_score BETWEEN 0.0000 AND 1.0000),
    posterior_mastery_probability NUMERIC(9,4) NOT NULL DEFAULT 0.0000 CHECK (posterior_mastery_probability BETWEEN 0.0000 AND 1.0000),
    retention_probability NUMERIC(9,4) NOT NULL DEFAULT 0.0000 CHECK (retention_probability BETWEEN 0.0000 AND 1.0000),
    scaffold_dependency_index NUMERIC(9,4) NOT NULL DEFAULT 0.0000 CHECK (scaffold_dependency_index BETWEEN 0.0000 AND 1.0000),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (learner_id, skill_node_id)
);

-- 3. Ensure table mistake_retention_transitions exists for retention unlock tracking
CREATE TABLE IF NOT EXISTS mistake_retention_transitions (
    id UUID PRIMARY KEY,
    mistake_dna_id UUID NOT NULL REFERENCES mistake_dna_profiles(id) ON DELETE CASCADE,
    from_status VARCHAR(32) NULL,
    to_status VARCHAR(32) NOT NULL,
    retention_unlock_eligible_at TIMESTAMPTZ NULL,
    conquered_at TIMESTAMPTZ NULL,
    transitioned_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

-- 4. Extend counterfactual_twin_drills with columns supporting fast active drill joins
ALTER TABLE counterfactual_twin_drills
    ADD COLUMN IF NOT EXISTS id UUID,
    ADD COLUMN IF NOT EXISTS learner_id UUID,
    ADD COLUMN IF NOT EXISTS mistake_dna_id UUID,
    ADD COLUMN IF NOT EXISTS drill_status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS latest_counterexample_payload TEXT;

-- Populate default values on legacy/existing rows if applicable
UPDATE counterfactual_twin_drills
SET id = twin_id,
    learner_id = user_id,
    drill_status = status
WHERE id IS NULL;

-- 5. Read-Model Covering & Partial Indexes (<50ms p95 SLA)

-- 5.1. Covering index on mistake_dna_profiles for fast learner + lifecycle filtering and sorting
CREATE INDEX IF NOT EXISTS idx_mistake_dna_profiles_passport_covering
    ON mistake_dna_profiles (learner_id, lifecycle_status, last_detected_at DESC)
    INCLUDE (id, mistake_code, mistake_family, skill_node_id, occurrence_count, consecutive_solo_passes);

-- 5.2. Partial index isolating conceptual traps from ARITH_BASIC_COMPUTATION for instant header metric aggregation
CREATE INDEX IF NOT EXISTS idx_mistake_dna_conceptual_traps_only
    ON mistake_dna_profiles (learner_id, lifecycle_status)
    WHERE mistake_code <> 'ARITH_BASIC_COMPUTATION';

-- 5.3. Latest retention transition lookup index on mistake_retention_transitions
CREATE INDEX IF NOT EXISTS idx_mistake_retention_transitions_latest_lookup
    ON mistake_retention_transitions (mistake_dna_id, transitioned_at DESC)
    INCLUDE (to_status, retention_unlock_eligible_at, conquered_at);

-- 5.4. Active counterfactual twin drill lookup index on counterfactual_twin_drills
CREATE INDEX IF NOT EXISTS idx_counterfactual_twin_drills_active_passport
    ON counterfactual_twin_drills (learner_id, mistake_dna_id, created_at DESC)
    INCLUDE (id, drill_status, latest_counterexample_payload);

-- 5.5. Dual-Ring Mastery 5-metrics covering index on learner_skill_mastery_states
CREATE INDEX IF NOT EXISTS idx_learner_skill_mastery_five_metrics_covering
    ON learner_skill_mastery_states (learner_id, skill_node_id)
    INCLUDE (
        observed_outcome_score,
        independent_competence_score,
        posterior_mastery_probability,
        retention_probability,
        scaffold_dependency_index
    );
