-- Derived evaluation and recovery state; no mutation/backfill of learner evidence.
CREATE TABLE attempt_evaluations (
    id UUID PRIMARY KEY,
    attempt_id UUID NOT NULL UNIQUE,
    user_id UUID NOT NULL,
    problem_session_id UUID NOT NULL,
    work_revision INTEGER NOT NULL CHECK(work_revision BETWEEN 1 AND 30),
    source JSONB NOT NULL,
    create_key VARCHAR(128) NOT NULL CHECK(create_key ~ '^[A-Za-z0-9_-]{8,128}$'),
    real_domain_confirmed BOOLEAN NOT NULL,
    policy_version TEXT NOT NULL CHECK(policy_version='structured-evaluation-v1'),
    execution_id UUID NOT NULL,
    lease_until TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    artifact JSONB,
    training_eligible BOOLEAN NOT NULL DEFAULT FALSE CHECK(NOT training_eligible),
    UNIQUE(user_id,problem_session_id,create_key),
    FOREIGN KEY(attempt_id,user_id) REFERENCES attempt_structured_work(attempt_id,user_id) ON DELETE CASCADE,
    -- Deletion has one path through structured work. A second direct session cascade
    -- could reach the immutable-child guard before the structured parent is removed.
    FOREIGN KEY(problem_session_id,user_id) REFERENCES problem_sessions(id,user_id) DEFERRABLE INITIALLY DEFERRED,
    CHECK(jsonb_typeof(source)='object' AND octet_length(source::text)<=1024),
    CHECK(artifact IS NULL OR (jsonb_typeof(artifact)='object' AND octet_length(artifact::text)<=60000
        AND artifact->>'schemaVersion' IS NOT DISTINCT FROM 'attempt-evaluation-v1'
        AND artifact->>'id' IS NOT DISTINCT FROM id::text
        AND artifact->>'attemptId' IS NOT DISTINCT FROM attempt_id::text
        AND artifact->>'ownerId' IS NOT DISTINCT FROM user_id::text
        AND artifact->>'problemSessionId' IS NOT DISTINCT FROM problem_session_id::text
        AND artifact->>'workRevision' IS NOT DISTINCT FROM work_revision::text
        AND artifact->'source' IS NOT DISTINCT FROM source
        AND artifact->>'executionId' IS NOT DISTINCT FROM execution_id::text
        AND artifact->>'realDomainConfirmed' IS NOT DISTINCT FROM real_domain_confirmed::text
        AND artifact->>'policyVersion' IS NOT DISTINCT FROM policy_version
        AND artifact->>'trainingEligible' IS NOT DISTINCT FROM 'false'))
);
CREATE INDEX ix_attempt_evaluation_owner ON attempt_evaluations(user_id,problem_session_id,created_at);
CREATE FUNCTION guard_attempt_evaluation() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE work JSONB;
BEGIN
    IF TG_OP='DELETE' THEN
        IF EXISTS(SELECT 1 FROM attempt_structured_work WHERE attempt_id=OLD.attempt_id) THEN
            RAISE EXCEPTION 'Evaluation belongs to accepted work' USING ERRCODE='23514';
        END IF;
        RETURN OLD;
    END IF;
    IF TG_OP='UPDATE' AND (OLD.artifact IS NOT NULL OR
        (NEW.id,NEW.attempt_id,NEW.user_id,NEW.problem_session_id,NEW.work_revision,NEW.source,NEW.create_key,NEW.real_domain_confirmed,NEW.policy_version,NEW.created_at)
        IS DISTINCT FROM
        (OLD.id,OLD.attempt_id,OLD.user_id,OLD.problem_session_id,OLD.work_revision,OLD.source,OLD.create_key,OLD.real_domain_confirmed,OLD.policy_version,OLD.created_at)) THEN
        RAISE EXCEPTION 'Immutable evaluation evidence' USING ERRCODE='23514';
    END IF;
    SELECT artifact INTO work FROM attempt_structured_work WHERE attempt_id=NEW.attempt_id AND user_id=NEW.user_id;
    IF work IS NULL OR work->>'problemSessionId' IS DISTINCT FROM NEW.problem_session_id::text
        OR work->'source' IS DISTINCT FROM NEW.source OR work->>'revision' IS DISTINCT FROM NEW.work_revision::text THEN
        RAISE EXCEPTION 'Invalid evaluation lineage' USING ERRCODE='23514';
    END IF;
    IF NEW.artifact IS NOT NULL AND (NEW.artifact->'stepIds' IS DISTINCT FROM
        (SELECT jsonb_agg(value->'id' ORDER BY n) FROM jsonb_array_elements(work->'steps') WITH ORDINALITY item(value,n))
        OR NEW.artifact->>'finalStepId' IS DISTINCT FROM
        (SELECT value->>'id' FROM jsonb_array_elements(work->'steps') WHERE value->>'role'='FINAL_ANSWER')) THEN
        RAISE EXCEPTION 'Invalid evaluation step identity' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_attempt_evaluation BEFORE INSERT OR UPDATE OR DELETE ON attempt_evaluations
    FOR EACH ROW EXECUTE FUNCTION guard_attempt_evaluation();
