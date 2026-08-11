-- Sprint 4.8: User-Correctable Parse Revision Lifecycle
-- Extends problem_parses to support USER source revisions with immutable lineage,
-- correction metadata, and idempotent mutation safety.
-- Formalizes current_parse_id authority on problem_sessions.

-- ============================================================================
-- A2. Add new columns to problem_parses for USER correction lifecycle
-- ============================================================================

ALTER TABLE problem_parses
    ADD COLUMN parent_parse_id UUID NULL;

ALTER TABLE problem_parses
    ADD COLUMN correction_idempotency_key VARCHAR(128) NULL;

ALTER TABLE problem_parses
    ADD COLUMN correction_request_hash VARCHAR(64) NULL;

ALTER TABLE problem_parses
    ADD COLUMN correction_reason VARCHAR(32) NULL;

ALTER TABLE problem_parses
    ADD COLUMN corrected_fields_jsonb JSONB NULL;

ALTER TABLE problem_parses
    ADD COLUMN correction_schema_version VARCHAR(64) NULL;

-- ============================================================================
-- A3. Relax AI-only NOT NULL constraints for USER revision support
-- These columns must be nullable so USER revisions can leave them NULL
-- without fabricating AI provenance.
-- ============================================================================

ALTER TABLE problem_parses ALTER COLUMN parse_job_id DROP NOT NULL;
ALTER TABLE problem_parses ALTER COLUMN raw_output_jsonb DROP NOT NULL;
ALTER TABLE problem_parses ALTER COLUMN provider DROP NOT NULL;
ALTER TABLE problem_parses ALTER COLUMN model DROP NOT NULL;
ALTER TABLE problem_parses ALTER COLUMN route_policy_version DROP NOT NULL;
ALTER TABLE problem_parses ALTER COLUMN prompt_id DROP NOT NULL;
ALTER TABLE problem_parses ALTER COLUMN prompt_version DROP NOT NULL;
ALTER TABLE problem_parses ALTER COLUMN fallback_used DROP NOT NULL;
ALTER TABLE problem_parses ALTER COLUMN request_units DROP NOT NULL;
ALTER TABLE problem_parses ALTER COLUMN provider_latency_ms DROP NOT NULL;
ALTER TABLE problem_parses ALTER COLUMN total_latency_ms DROP NOT NULL;
ALTER TABLE problem_parses ALTER COLUMN estimated_cost_micros DROP NOT NULL;
ALTER TABLE problem_parses ALTER COLUMN currency DROP NOT NULL;
ALTER TABLE problem_parses ALTER COLUMN pricing_version DROP NOT NULL;

-- ============================================================================
-- A4. Source-aware CHECK constraints
-- Ensure AI revisions retain full provenance and USER revisions carry
-- correction metadata without fabricated AI fields.
-- ============================================================================

-- Drop existing JSON object check that requires raw_output_jsonb to be non-null
ALTER TABLE problem_parses DROP CONSTRAINT ck_problem_parses_json_objects;

-- Re-add normalized problem JSON validation (always required)
ALTER TABLE problem_parses
    ADD CONSTRAINT ck_problem_parses_normalized_json_object
        CHECK (jsonb_typeof(normalized_problem_jsonb) = 'object');

-- AI source constraint: requires full AI provenance, forbids correction metadata
ALTER TABLE problem_parses
    ADD CONSTRAINT ck_problem_parses_ai_source CHECK (
        source <> 'AI'
        OR (
            parse_job_id IS NOT NULL
            AND raw_output_jsonb IS NOT NULL
            AND provider IS NOT NULL
            AND model IS NOT NULL
            AND route_policy_version IS NOT NULL
            AND prompt_id IS NOT NULL
            AND prompt_version IS NOT NULL
            AND fallback_used IS NOT NULL
            AND request_units IS NOT NULL
            AND provider_latency_ms IS NOT NULL
            AND total_latency_ms IS NOT NULL
            AND estimated_cost_micros IS NOT NULL
            AND currency IS NOT NULL
            AND pricing_version IS NOT NULL
            AND correction_reason IS NULL
            AND parent_parse_id IS NULL
            AND correction_idempotency_key IS NULL
            AND correction_request_hash IS NULL
            AND corrected_fields_jsonb IS NULL
            AND correction_schema_version IS NULL
        )
    );

-- USER source constraint: requires parent lineage and idempotency, forbids AI provenance
ALTER TABLE problem_parses
    ADD CONSTRAINT ck_problem_parses_user_source CHECK (
        source <> 'USER'
        OR (
            parent_parse_id IS NOT NULL
            AND correction_idempotency_key IS NOT NULL
            AND parse_job_id IS NULL
            AND raw_output_jsonb IS NULL
            AND provider IS NULL
            AND model IS NULL
            AND route_policy_version IS NULL
            AND prompt_id IS NULL
            AND prompt_version IS NULL
            AND provider_request_id IS NULL
            AND provider_response_id IS NULL
            AND fallback_used IS NULL
            AND input_tokens IS NULL
            AND output_tokens IS NULL
            AND image_units IS NULL
            AND request_units IS NULL
            AND provider_latency_ms IS NULL
            AND total_latency_ms IS NULL
            AND estimated_cost_micros IS NULL
            AND currency IS NULL
            AND pricing_version IS NULL
            AND raw_output_retention_until IS NULL
            AND correction_request_hash IS NOT NULL
            AND correction_schema_version IS NOT NULL
        )
    );

-- Raw output JSON validation (only when present)
ALTER TABLE problem_parses
    ADD CONSTRAINT ck_problem_parses_raw_output_json_object
        CHECK (raw_output_jsonb IS NULL OR jsonb_typeof(raw_output_jsonb) = 'object');

-- ============================================================================
-- A5. Parent parse ownership integrity
-- A child revision cannot reference a parse in another session or another user.
-- ============================================================================

-- Composite unique key enabling the parent FK to enforce same-user, same-session
ALTER TABLE problem_parses
    ADD CONSTRAINT uq_problem_parses_id_user_session
        UNIQUE (id, user_id, problem_session_id);

-- Parent must belong to same user and same session
ALTER TABLE problem_parses
    ADD CONSTRAINT fk_problem_parses_parent
        FOREIGN KEY (parent_parse_id, user_id, problem_session_id)
        REFERENCES problem_parses(id, user_id, problem_session_id)
        ON DELETE CASCADE;

-- ============================================================================
-- A6. Corrected fields JSON validation
-- ============================================================================

ALTER TABLE problem_parses
    ADD CONSTRAINT ck_problem_parses_corrected_fields_json
        CHECK (corrected_fields_jsonb IS NULL OR jsonb_typeof(corrected_fields_jsonb) = 'object');

-- ============================================================================
-- A7. Correction idempotency uniqueness
-- Same user + session + key must not produce duplicate revisions.
-- Partial unique index excludes AI parses (which have NULL keys).
-- ============================================================================

CREATE UNIQUE INDEX uq_problem_parses_correction_idempotency
    ON problem_parses(user_id, problem_session_id, correction_idempotency_key)
    WHERE correction_idempotency_key IS NOT NULL;

-- ============================================================================
-- A8. Correction reason validation
-- ============================================================================

ALTER TABLE problem_parses
    ADD CONSTRAINT ck_problem_parses_correction_reason CHECK (
        correction_reason IS NULL
        OR correction_reason IN (
            'OCR_TEXT_ERROR',
            'MATH_EXPRESSION_ERROR',
            'VARIABLE_ERROR',
            'CONSTRAINT_ERROR',
            'ASSUMPTION_ERROR',
            'TASK_TYPE_ERROR',
            'PROBLEM_TYPE_ERROR',
            'OTHER'
        )
    );

ALTER TABLE problem_parses
    ADD CONSTRAINT ck_problem_parses_correction_request_hash CHECK (
        correction_request_hash IS NULL
        OR correction_request_hash ~ '^[a-f0-9]{64}$'
    );

-- ============================================================================
-- A9. Formalize current_parse_id FK on problem_sessions
-- The selected parse must reference a valid parse row for the same owner/session.
-- ============================================================================

ALTER TABLE problem_sessions
    ADD CONSTRAINT fk_problem_sessions_current_parse
        FOREIGN KEY (current_parse_id, user_id, id)
        REFERENCES problem_parses(id, user_id, problem_session_id)
        ON DELETE SET NULL (current_parse_id);

-- ============================================================================
-- A10. Backfill existing sessions with current_parse_id
-- For each session that has accepted parses but no current_parse_id,
-- set it to the latest revision (highest revision number).
-- Only selects SUPPORTED or REVIEW_REQUIRED parses (not UNSUPPORTED).
-- ============================================================================

UPDATE problem_sessions s
SET current_parse_id = sub.latest_parse_id,
    updated_at = NOW()
FROM (
    SELECT DISTINCT ON (p.problem_session_id)
        p.problem_session_id,
        p.id AS latest_parse_id
    FROM problem_parses p
    WHERE p.support_status IN ('SUPPORTED', 'REVIEW_REQUIRED')
    ORDER BY p.problem_session_id, p.revision DESC
) sub
WHERE s.id = sub.problem_session_id
  AND s.current_parse_id IS NULL;

-- ============================================================================
-- A11. Index for parent lineage queries
-- ============================================================================

CREATE INDEX ix_problem_parses_parent
    ON problem_parses(parent_parse_id)
    WHERE parent_parse_id IS NOT NULL;
