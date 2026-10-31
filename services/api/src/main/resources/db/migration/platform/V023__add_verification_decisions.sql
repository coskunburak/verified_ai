-- Additive policy history: released evidence and canonical schemas remain unchanged.
ALTER TABLE verification_runs ADD CONSTRAINT uq_decision_run_owner
    UNIQUE(id,user_id,problem_session_id,solution_id);
CREATE TABLE verification_decisions (
    id UUID PRIMARY KEY,
    request_key UUID NOT NULL,
    user_id UUID NOT NULL,
    problem_session_id UUID NOT NULL,
    solution_id UUID NOT NULL,
    run_id UUID NOT NULL,
    policy_version VARCHAR(64) NOT NULL CHECK(policy_version='verification-policy-v1'),
    decision_jsonb JSONB NOT NULL CHECK(octet_length(decision_jsonb::text)<=16384),
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE(user_id,problem_session_id,request_key),
    FOREIGN KEY(run_id,user_id,problem_session_id,solution_id)
        REFERENCES verification_runs(id,user_id,problem_session_id,solution_id) ON DELETE CASCADE,
    CHECK((jsonb_typeof(decision_jsonb)='object'
        AND decision_jsonb->>'schemaVersion'='verification-decision-v1'
        AND decision_jsonb->>'policyVersion'=policy_version
        AND decision_jsonb->>'id'=id::text AND decision_jsonb->>'requestKey'=request_key::text
        AND decision_jsonb->>'runId'=run_id::text AND decision_jsonb->>'solutionId'=solution_id::text
        AND decision_jsonb->'source'->>'userId'=user_id::text
        AND decision_jsonb->'source'->>'sessionId'=problem_session_id::text
        AND (decision_jsonb->>'createdAt')::timestamptz=created_at
        AND decision_jsonb->>'status' IN ('VERIFIED','PARTIALLY_VERIFIED','UNVERIFIED')) IS TRUE)
);
CREATE INDEX ix_verification_decisions_current ON verification_decisions(user_id,problem_session_id,solution_id,created_at DESC,id DESC);
CREATE FUNCTION validate_verification_decision() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE original JSONB; refs JSONB;
BEGIN
    SELECT run_jsonb INTO original FROM verification_runs WHERE id=NEW.run_id AND user_id=NEW.user_id
        AND problem_session_id=NEW.problem_session_id AND solution_id=NEW.solution_id;
    SELECT jsonb_agg(e->'id') INTO refs FROM jsonb_array_elements(original->'evidence') e;
    IF NOT ((NEW.decision_jsonb->'source'=original->'source'
        AND NEW.decision_jsonb->'solutionVersion'=original->'solutionVersion'
        AND NEW.decision_jsonb->>'planId'=original->>'planId'
        AND NEW.decision_jsonb->>'capabilityVersion'=original->>'capabilityVersion'
        AND NEW.decision_jsonb->>'runRequestKey'=original->>'requestKey'
        AND NEW.decision_jsonb->>'correlationId'=original->>'correlationId'
        AND (NEW.decision_jsonb->'evidenceIds') @> refs
        AND refs @> (NEW.decision_jsonb->'evidenceIds')
        AND jsonb_array_length(NEW.decision_jsonb->'evidenceIds')=jsonb_array_length(refs)) IS TRUE) THEN
        RAISE EXCEPTION 'Decision evidence lineage mismatch' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER verification_decision_lineage BEFORE INSERT ON verification_decisions
    FOR EACH ROW EXECUTE FUNCTION validate_verification_decision();
CREATE TRIGGER verification_decision_immutable BEFORE UPDATE ON verification_decisions
    FOR EACH ROW EXECUTE FUNCTION prevent_solution_candidate_update();
CREATE FUNCTION protect_verification_decision_delete() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS(SELECT 1 FROM verification_runs WHERE id=OLD.run_id) THEN
        RAISE EXCEPTION 'Decision can only be deleted with its owned evidence' USING ERRCODE='23514';
    END IF;
    RETURN OLD;
END;
$$;
CREATE TRIGGER verification_decision_delete BEFORE DELETE ON verification_decisions
    FOR EACH ROW EXECUTE FUNCTION protect_verification_decision_delete();
