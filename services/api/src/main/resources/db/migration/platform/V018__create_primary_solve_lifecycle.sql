-- Immutable source binding; no mutation of earlier migrations/artifacts.
ALTER TABLE canonical_problems ADD CONSTRAINT uq_canonical_solve_lineage
    UNIQUE (id, user_id, problem_session_id, problem_parse_id, problem_parse_revision);
CREATE TABLE solve_jobs (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    problem_session_id UUID NOT NULL,
    canonical_problem_id UUID NOT NULL,
    problem_parse_id UUID NOT NULL,
    problem_parse_revision INTEGER NOT NULL CHECK (problem_parse_revision > 0),
    idempotency_key VARCHAR(128) NOT NULL,
    prompt_id VARCHAR(64) NOT NULL DEFAULT 'solver' CHECK (prompt_id='solver'),
    prompt_version VARCHAR(64) NOT NULL DEFAULT 'v001' CHECK (prompt_version='v001'),
    schema_version VARCHAR(64) NOT NULL DEFAULT 'solution-candidate-v1' CHECK (schema_version='solution-candidate-v1'),
    route_policy_version VARCHAR(64) NOT NULL DEFAULT 'solve-route-v1' CHECK (route_policy_version='solve-route-v1'),
    attempt_number INTEGER NOT NULL CHECK (attempt_number BETWEEN 1 AND 3),
    retry_of UUID NULL REFERENCES solve_jobs(id) ON DELETE SET NULL,
    operation_id UUID NOT NULL UNIQUE,
    state VARCHAR(40) NOT NULL CHECK (state IN ('QUEUED','RUNNING','GENERATED_UNVERIFIED',
        'REVIEW_REQUIRED','RETRYABLE_FAILURE','TERMINAL_FAILURE','POLICY_BLOCKED','UNSUPPORTED',
        'STALE','CANCELLED','OUTCOME_UNKNOWN')),
    failure_code VARCHAR(64),
    ledger_record_id UUID REFERENCES ai_usage_records(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    deadline TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_solve_source FOREIGN KEY
        (canonical_problem_id,user_id,problem_session_id,problem_parse_id,problem_parse_revision)
        REFERENCES canonical_problems(id,user_id,problem_session_id,problem_parse_id,problem_parse_revision) ON DELETE CASCADE,
    CONSTRAINT uq_solve_idempotency UNIQUE(user_id,idempotency_key),
    CONSTRAINT uq_solve_source_attempt UNIQUE(canonical_problem_id,attempt_number),
    CONSTRAINT ck_solve_timing CHECK (deadline>created_at AND
        (state IN ('QUEUED','RUNNING') AND completed_at IS NULL OR
         state NOT IN ('QUEUED','RUNNING') AND completed_at IS NOT NULL))
);
CREATE UNIQUE INDEX uq_solve_active_session ON solve_jobs(problem_session_id) WHERE state IN ('QUEUED','RUNNING');
CREATE INDEX ix_solve_user_session ON solve_jobs(user_id,problem_session_id,created_at DESC);
CREATE INDEX ix_solve_recovery ON solve_jobs(state,deadline) WHERE state IN ('QUEUED','RUNNING');
CREATE TABLE solution_candidates (
    job_id UUID PRIMARY KEY REFERENCES solve_jobs(id) ON DELETE CASCADE,
    schema_version VARCHAR(64) NOT NULL CHECK (schema_version='solution-candidate-v1'),
    artifact_jsonb JSONB NOT NULL CHECK (jsonb_typeof(artifact_jsonb)='object'
        AND artifact_jsonb->>'schemaVersion'='solution-candidate-v1'
        AND NOT (artifact_jsonb ? 'verified')),
    provenance_jsonb JSONB NOT NULL CHECK(jsonb_typeof(provenance_jsonb)='object'),
    created_at TIMESTAMPTZ NOT NULL
);
CREATE FUNCTION prevent_solution_candidate_update() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Solution candidates are immutable; create a new job revision';
END;
$$;
CREATE TRIGGER solution_candidate_immutable BEFORE UPDATE ON solution_candidates
    FOR EACH ROW EXECUTE FUNCTION prevent_solution_candidate_update();
