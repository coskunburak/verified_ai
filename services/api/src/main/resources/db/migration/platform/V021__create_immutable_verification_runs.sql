-- One atomically published immutable aggregate; method evidence is a bounded typed JSON array.
ALTER TABLE canonical_solutions ADD CONSTRAINT uq_verification_solution_lineage
    UNIQUE(id,user_id,problem_session_id,canonical_problem_id,problem_parse_id,
        problem_parse_revision,canonical_revision,representation_version,normalizer_version);
CREATE TABLE verification_runs (
    id UUID PRIMARY KEY,
    request_key UUID NOT NULL,
    user_id UUID NOT NULL,
    problem_session_id UUID NOT NULL,
    solution_id UUID NOT NULL,
    canonical_problem_id UUID NOT NULL,
    problem_parse_id UUID NOT NULL,
    problem_parse_revision INTEGER NOT NULL CHECK(problem_parse_revision>0),
    canonical_revision INTEGER NOT NULL CHECK(canonical_revision>0),
    representation_version VARCHAR(64) NOT NULL,
    normalizer_version VARCHAR(64) NOT NULL,
    plan_id UUID NOT NULL,
    policy_version VARCHAR(64) NOT NULL,
    capability_version VARCHAR(64) NOT NULL,
    correlation_id VARCHAR(64) NOT NULL CHECK(correlation_id ~ '^[A-Za-z0-9_-]{1,64}$'),
    run_jsonb JSONB NOT NULL CHECK(octet_length(run_jsonb::text)<=65536),
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE(user_id,problem_session_id,request_key),
    FOREIGN KEY(solution_id,user_id,problem_session_id,canonical_problem_id,problem_parse_id,
        problem_parse_revision,canonical_revision,representation_version,normalizer_version)
        REFERENCES canonical_solutions(id,user_id,problem_session_id,canonical_problem_id,problem_parse_id,
            problem_parse_revision,canonical_revision,representation_version,normalizer_version) ON DELETE CASCADE,
    CHECK((jsonb_typeof(run_jsonb)='object' AND run_jsonb->>'id'=id::text
        AND run_jsonb->>'requestKey'=request_key::text AND run_jsonb->>'planId'=plan_id::text
        AND run_jsonb->>'solutionId'=solution_id::text
        AND run_jsonb->>'policyVersion'=policy_version AND run_jsonb->>'capabilityVersion'=capability_version
        AND run_jsonb->>'correlationId'=correlation_id
        AND (run_jsonb->>'createdAt')::timestamptz=created_at
        AND run_jsonb->'source'->>'userId'=user_id::text
        AND run_jsonb->'source'->>'sessionId'=problem_session_id::text
        AND run_jsonb->'source'->>'canonicalProblemId'=canonical_problem_id::text
        AND run_jsonb->'source'->>'parseId'=problem_parse_id::text
        AND run_jsonb->'source'->>'parseRevision'=problem_parse_revision::text
        AND run_jsonb->'source'->>'canonicalRevision'=canonical_revision::text
        AND run_jsonb->'solutionVersion'->>'representation'=representation_version
        AND run_jsonb->'solutionVersion'->>'normalizer'=normalizer_version
        AND jsonb_typeof(run_jsonb->'evidence')='array'
        AND jsonb_array_length(run_jsonb->'evidence') BETWEEN 1 AND 5) IS TRUE)
);
CREATE INDEX ix_verification_runs_owner ON verification_runs(user_id,problem_session_id,solution_id,created_at);
CREATE TRIGGER verification_run_immutable BEFORE UPDATE ON verification_runs
    FOR EACH ROW EXECUTE FUNCTION prevent_solution_candidate_update();
CREATE FUNCTION protect_verification_run_delete() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS(SELECT 1 FROM canonical_solutions WHERE id=OLD.solution_id) THEN
        RAISE EXCEPTION 'Verification evidence can only be deleted with its owned source' USING ERRCODE='23514';
    END IF;
    RETURN OLD;
END;
$$;
CREATE TRIGGER verification_run_delete BEFORE DELETE ON verification_runs
    FOR EACH ROW EXECUTE FUNCTION protect_verification_run_delete();
