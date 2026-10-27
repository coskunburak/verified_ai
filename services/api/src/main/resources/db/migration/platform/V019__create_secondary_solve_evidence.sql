-- Additive, backwards compatible; existing primary API/candidates are untouched.
ALTER TABLE solve_jobs ADD CONSTRAINT uq_primary_secondary_lineage
    UNIQUE(id,user_id,problem_session_id,canonical_problem_id,problem_parse_id,problem_parse_revision);
CREATE TABLE secondary_solve_jobs (
    id UUID PRIMARY KEY,
    primary_job_id UUID NOT NULL REFERENCES solution_candidates(job_id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    problem_session_id UUID NOT NULL,
    canonical_problem_id UUID NOT NULL,
    problem_parse_id UUID NOT NULL,
    problem_parse_revision INTEGER NOT NULL CHECK(problem_parse_revision>0),
    canonical_revision INTEGER NOT NULL CHECK(canonical_revision>0),
    operation_id UUID NOT NULL UNIQUE,
    policy_version VARCHAR(96) NOT NULL,
    decision VARCHAR(32) NOT NULL CHECK(decision IN ('SKIP','ESCALATE','BLOCKED','INSUFFICIENT_EVIDENCE')),
    decision_reason VARCHAR(64) NOT NULL,
    state VARCHAR(40) NOT NULL CHECK(state IN ('NOT_REQUIRED','INSUFFICIENT_EVIDENCE','POLICY_BLOCKED',
        'QUEUED','RUNNING','GENERATED_UNVERIFIED','RETRYABLE_FAILURE','TERMINAL_FAILURE','OUTCOME_UNKNOWN','STALE','CANCELLED')),
    failure_code VARCHAR(64),
    attempt_number INTEGER NOT NULL DEFAULT 0 CHECK(attempt_number BETWEEN 0 AND 1),
    route_jsonb JSONB CHECK(route_jsonb IS NULL OR (jsonb_typeof(route_jsonb)='object'
        AND octet_length(route_jsonb::text)<=16384)),
    ledger_record_id UUID REFERENCES ai_usage_records(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL,
    deadline TIMESTAMPTZ NOT NULL CHECK(deadline>created_at),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    CONSTRAINT fk_secondary_primary_owner FOREIGN KEY
        (primary_job_id,user_id,problem_session_id,canonical_problem_id,problem_parse_id,problem_parse_revision)
        REFERENCES solve_jobs(id,user_id,problem_session_id,canonical_problem_id,problem_parse_id,problem_parse_revision) ON DELETE CASCADE,
    CONSTRAINT uq_secondary_primary_policy UNIQUE(primary_job_id,policy_version),
    CONSTRAINT ck_secondary_timing CHECK((state IN ('QUEUED','RUNNING') AND completed_at IS NULL)
        OR (state NOT IN ('QUEUED','RUNNING') AND completed_at IS NOT NULL)),
    CONSTRAINT ck_secondary_dispatch CHECK(state NOT IN ('QUEUED','RUNNING','GENERATED_UNVERIFIED')
        OR (decision='ESCALATE' AND route_jsonb IS NOT NULL)),
    CONSTRAINT ck_secondary_attempt CHECK(state NOT IN ('RUNNING','GENERATED_UNVERIFIED') OR attempt_number=1)
);
CREATE INDEX ix_secondary_due ON secondary_solve_jobs(state,created_at) WHERE state='QUEUED';
CREATE INDEX ix_secondary_recovery ON secondary_solve_jobs(deadline) WHERE state IN ('QUEUED','RUNNING');
CREATE INDEX ix_secondary_owner ON secondary_solve_jobs(user_id,problem_session_id,created_at DESC);
CREATE TABLE secondary_solution_evidence (
    job_id UUID PRIMARY KEY REFERENCES secondary_solve_jobs(id) ON DELETE CASCADE,
    schema_version VARCHAR(64) NOT NULL CHECK(schema_version='solution-candidate-v1'),
    artifact_jsonb JSONB NOT NULL CHECK(jsonb_typeof(artifact_jsonb)='object'
        AND artifact_jsonb ? 'schemaVersion' AND artifact_jsonb->>'schemaVersion'='solution-candidate-v1'
        AND NOT (artifact_jsonb ? 'verified') AND octet_length(artifact_jsonb::text)<=131072),
    provenance_jsonb JSONB NOT NULL CHECK(jsonb_typeof(provenance_jsonb)='object' AND octet_length(provenance_jsonb::text)<=16384),
    agreement_jsonb JSONB NOT NULL CHECK(jsonb_typeof(agreement_jsonb)='object'
        AND agreement_jsonb ?& ARRAY['schemaVersion','outcome','mathematicalVerificationPerformed']
        AND agreement_jsonb->>'schemaVersion'='blind-agreement-v1'
        AND agreement_jsonb->>'mathematicalVerificationPerformed'='false'
        AND agreement_jsonb->>'outcome' IN ('AGREEMENT','DISAGREEMENT','INDETERMINATE','INCOMPATIBLE_ASSUMPTIONS','STALE_LINEAGE')
        AND NOT (agreement_jsonb ? 'verified') AND octet_length(agreement_jsonb::text)<=8192),
    created_at TIMESTAMPTZ NOT NULL
);
CREATE TRIGGER secondary_evidence_immutable BEFORE UPDATE ON secondary_solution_evidence
    FOR EACH ROW EXECUTE FUNCTION prevent_solution_candidate_update();

-- Persist the original operation ceiling; old operations without a ceiling cannot fund paid secondary work.
ALTER TABLE ai_spend_accounts ADD COLUMN operation_limit_micros BIGINT
    CHECK(operation_limit_micros IS NULL OR operation_limit_micros>=0);

-- A policy rollout cannot silently re-dispatch an already attempted primary candidate.
CREATE UNIQUE INDEX uq_secondary_one_dispatch ON secondary_solve_jobs(primary_job_id) WHERE attempt_number=1;
CREATE FUNCTION protect_secondary_job_identity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF ROW(NEW.id,NEW.primary_job_id,NEW.user_id,NEW.problem_session_id,NEW.canonical_problem_id,
        NEW.problem_parse_id,NEW.problem_parse_revision,NEW.canonical_revision,NEW.operation_id,
        NEW.policy_version,NEW.decision,NEW.decision_reason,NEW.route_jsonb,NEW.created_at,NEW.deadline)
       IS DISTINCT FROM
       ROW(OLD.id,OLD.primary_job_id,OLD.user_id,OLD.problem_session_id,OLD.canonical_problem_id,
        OLD.problem_parse_id,OLD.problem_parse_revision,OLD.canonical_revision,OLD.operation_id,
        OLD.policy_version,OLD.decision,OLD.decision_reason,OLD.route_jsonb,OLD.created_at,OLD.deadline) THEN
        RAISE EXCEPTION 'Secondary source, policy and route are immutable';
    END IF;
    IF NEW.attempt_number IS DISTINCT FROM OLD.attempt_number AND NOT
        (OLD.state='QUEUED' AND NEW.state='RUNNING' AND OLD.attempt_number=0 AND NEW.attempt_number=1) THEN
        RAISE EXCEPTION 'Secondary attempt cannot be replayed';
    END IF;
    IF NEW.state IS DISTINCT FROM OLD.state AND NOT
        (OLD.state='QUEUED' AND NEW.state IN ('RUNNING','CANCELLED','TERMINAL_FAILURE') OR
         OLD.state='RUNNING' AND NEW.state IN ('GENERATED_UNVERIFIED','POLICY_BLOCKED','RETRYABLE_FAILURE',
            'TERMINAL_FAILURE','OUTCOME_UNKNOWN','STALE','CANCELLED')) THEN
        RAISE EXCEPTION 'Illegal secondary lifecycle transition';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER secondary_job_identity BEFORE UPDATE ON secondary_solve_jobs
    FOR EACH ROW EXECUTE FUNCTION protect_secondary_job_identity();
