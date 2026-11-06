-- Additive derived artifact: original attempts, hashes and V030–V033 checksums are unchanged.
CREATE TABLE attempt_structured_work (
    attempt_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    revision INTEGER NOT NULL CHECK (revision BETWEEN 1 AND 30),
    supersedes_attempt_id UUID UNIQUE,
    artifact JSONB NOT NULL,
    training_eligible BOOLEAN NOT NULL DEFAULT FALSE CHECK (training_eligible=false),
    UNIQUE (attempt_id,user_id),
    FOREIGN KEY (attempt_id,user_id) REFERENCES student_attempts(id,user_id) ON DELETE CASCADE,
    FOREIGN KEY (supersedes_attempt_id,user_id) REFERENCES attempt_structured_work(attempt_id,user_id) ON DELETE CASCADE,
    CHECK ((revision=1)=(supersedes_attempt_id IS NULL)),
    CHECK (jsonb_typeof(artifact)='object' AND octet_length(artifact::text)<=65536),
    CHECK (artifact->>'schemaVersion' IS NOT DISTINCT FROM 'attempt-structured-work-v1'
        AND artifact->>'normalizationVersion' IS NOT DISTINCT FROM 'structured-canonical-v1'
        AND artifact->>'trainingEligible' IS NOT DISTINCT FROM 'false'
        AND artifact->>'attemptId' IS NOT DISTINCT FROM attempt_id::text
        AND artifact->>'ownerId' IS NOT DISTINCT FROM user_id::text
        AND artifact->>'revision' IS NOT DISTINCT FROM revision::text
        AND artifact->>'supersedesAttemptId' IS NOT DISTINCT FROM supersedes_attempt_id::text)
);
CREATE INDEX ix_attempt_work_owner ON attempt_structured_work(user_id,attempt_id);

CREATE FUNCTION guard_attempt_structured_work() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE parent student_attempts%ROWTYPE; parent_xmin xid; prior student_attempts%ROWTYPE; prior_revision integer; prior_artifact jsonb; step_text jsonb; final_text text;
BEGIN
    IF TG_OP='UPDATE' THEN
        RAISE EXCEPTION 'Structured work is immutable' USING ERRCODE='23514';
    END IF;
    IF TG_OP='DELETE' THEN
        IF EXISTS (SELECT 1 FROM student_attempts WHERE id=OLD.attempt_id) THEN
            RAISE EXCEPTION 'Structured work belongs to accepted evidence' USING ERRCODE='23514';
        END IF;
        RETURN OLD;
    END IF;
    SELECT a.* INTO parent FROM student_attempts a WHERE a.id=NEW.attempt_id AND a.user_id=NEW.user_id;
    SELECT xmin INTO parent_xmin FROM student_attempts WHERE id=NEW.attempt_id;
    IF parent.id IS NULL OR parent.status<>'SUBMITTED' OR parent.verifier_request_jsonb IS NOT NULL
        OR parent_xmin::text<>(pg_current_xact_id()::text::numeric % 4294967296)::text
        OR NEW.artifact->>'problemSessionId' IS DISTINCT FROM parent.problem_session_id::text
        OR NEW.artifact->'source' IS DISTINCT FROM jsonb_build_object('canonicalId',parent.canonical_id,
            'canonicalRevision',parent.canonical_revision,'parseId',parent.parse_id,'parseRevision',parent.parse_revision) THEN
        RAISE EXCEPTION 'Invalid structured work lineage' USING ERRCODE='23514';
    END IF;
    IF jsonb_typeof(NEW.artifact->'steps') IS DISTINCT FROM 'array'
        OR jsonb_array_length(NEW.artifact->'steps') NOT BETWEEN 1 AND 12
        OR jsonb_typeof(NEW.artifact->'observations') IS DISTINCT FROM 'array'
        OR jsonb_array_length(NEW.artifact->'observations')>12 THEN
        RAISE EXCEPTION 'Invalid structured evidence shape' USING ERRCODE='23514';
    END IF;
    IF EXISTS (SELECT 1 FROM jsonb_array_elements(NEW.artifact->'steps') WITH ORDINALITY AS item(value,n)
        WHERE value->>'index' IS DISTINCT FROM n::text) THEN
        RAISE EXCEPTION 'Invalid structured step order' USING ERRCODE='23514';
    END IF;
    SELECT coalesce(jsonb_agg(content ORDER BY step_index),'[]'::jsonb) INTO step_text
        FROM student_attempt_steps WHERE attempt_id=NEW.attempt_id;
    SELECT value->>'reviewedText' INTO final_text FROM jsonb_array_elements(NEW.artifact->'steps')
        WHERE value->>'role'='FINAL_ANSWER' LIMIT 1;
    IF step_text IS DISTINCT FROM (SELECT coalesce(jsonb_agg(value->'reviewedText' ORDER BY n),'[]'::jsonb)
        FROM jsonb_array_elements(NEW.artifact->'steps') WITH ORDINALITY AS item(value,n) WHERE value->>'role'<>'FINAL_ANSWER')
        OR final_text IS DISTINCT FROM parent.final_answer
        OR (SELECT count(*) FROM jsonb_array_elements(NEW.artifact->'steps') WHERE value->>'role'='FINAL_ANSWER')>1 THEN
        RAISE EXCEPTION 'Structured work must represent accepted text exactly' USING ERRCODE='23514';
    END IF;
    IF NEW.supersedes_attempt_id IS NOT NULL THEN
        SELECT a.* INTO prior FROM student_attempts a WHERE a.id=NEW.supersedes_attempt_id AND a.user_id=NEW.user_id;
        SELECT revision,artifact INTO prior_revision,prior_artifact FROM attempt_structured_work WHERE attempt_id=NEW.supersedes_attempt_id;
        IF (NEW.artifact->'engine',NEW.artifact->'captureId',NEW.artifact->'assetSessionId',NEW.artifact->'recognitionJobId',NEW.artifact->'observations')
            IS DISTINCT FROM (prior_artifact->'engine',prior_artifact->'captureId',prior_artifact->'assetSessionId',prior_artifact->'recognitionJobId',prior_artifact->'observations') THEN
            RAISE EXCEPTION 'Original recognition cannot change across a review revision' USING ERRCODE='23514';
        END IF;
        IF prior.id IS NULL OR prior_revision IS NULL OR NEW.revision<>prior_revision+1
            OR (prior.problem_session_id,prior.canonical_id,prior.canonical_revision,prior.parse_id,prior.parse_revision)
               IS DISTINCT FROM (parent.problem_session_id,parent.canonical_id,parent.canonical_revision,parent.parse_id,parent.parse_revision) THEN
            RAISE EXCEPTION 'Invalid structured revision' USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_attempt_work_immutable BEFORE INSERT OR UPDATE OR DELETE ON attempt_structured_work
    FOR EACH ROW EXECUTE FUNCTION guard_attempt_structured_work();
