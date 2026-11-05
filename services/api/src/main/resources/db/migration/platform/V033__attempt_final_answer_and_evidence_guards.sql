-- Additive only: historical rows, hashes, schemas and published assessments are unchanged.
CREATE FUNCTION attempt_text_utf16_units(value TEXT) RETURNS INTEGER LANGUAGE sql IMMUTABLE STRICT AS $$
    SELECT coalesce(sum(CASE WHEN ascii(c)>65535 THEN 2 ELSE 1 END),0)::integer
    FROM regexp_split_to_table(value,'') AS c WHERE c<>''
$$;
ALTER TABLE student_attempts ADD COLUMN final_answer TEXT;
ALTER TABLE student_attempts DROP CONSTRAINT student_attempts_schema_version_check;
ALTER TABLE student_attempts ADD CONSTRAINT student_attempts_schema_version_check
    CHECK (schema_version IN ('student-attempt-v1','student-attempt-v2'));
ALTER TABLE student_attempts ADD CONSTRAINT ck_attempt_final_answer CHECK (
    (schema_version='student-attempt-v1' AND final_answer IS NULL) OR
    (schema_version='student-attempt-v2' AND final_answer IS NOT NULL
        AND attempt_text_utf16_units(final_answer) BETWEEN 1 AND 256 AND octet_length(final_answer)<=1024
        AND final_answer ~ '[^[:space:]]' AND analysis_jsonb IS NULL
        AND verifier_request_jsonb IS NULL AND capture_review IS NULL
        AND status IN ('SUBMITTED','STALE_SOURCE')));
ALTER TABLE student_attempt_steps ADD CONSTRAINT ck_attempt_step_utf16
    CHECK (attempt_text_utf16_units(content) BETWEEN 1 AND 256 AND content ~ '[^[:space:]]') NOT VALID;
-- Enforced for new evidence; no new validation/rewrite of historical student work during upgrade.

CREATE OR REPLACE FUNCTION protect_attempt_evidence() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (NEW.id,NEW.user_id,NEW.problem_session_id,NEW.canonical_id,NEW.canonical_revision,NEW.parse_id,NEW.parse_revision,
        NEW.source_type,NEW.schema_version,NEW.normalization_version,NEW.create_key,NEW.request_hash,NEW.verifier_request_jsonb,
        NEW.created_at,NEW.capture_review,NEW.final_answer)
       IS DISTINCT FROM
       (OLD.id,OLD.user_id,OLD.problem_session_id,OLD.canonical_id,OLD.canonical_revision,OLD.parse_id,OLD.parse_revision,
        OLD.source_type,OLD.schema_version,OLD.normalization_version,OLD.create_key,OLD.request_hash,OLD.verifier_request_jsonb,
        OLD.created_at,OLD.capture_review,OLD.final_answer)
       OR OLD.status<>'SUBMITTED' OR NEW.status NOT IN ('ANALYZED','STALE_SOURCE')
       OR NEW.updated_at<OLD.updated_at THEN
        RAISE EXCEPTION 'Attempt evidence is immutable' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;

-- A child can only be created in the parent's original INSERT transaction. Published or
-- interrupted submissions cannot acquire extra lines later. Row-locking does not change xmin.
CREATE FUNCTION guard_attempt_step_membership() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE parent student_attempts%ROWTYPE; parent_xmin xid;
BEGIN
    IF TG_OP='DELETE' THEN
        IF EXISTS (SELECT 1 FROM student_attempts WHERE id=OLD.attempt_id) THEN
            RAISE EXCEPTION 'Accepted learner steps cannot be removed' USING ERRCODE='23514';
        END IF;
        RETURN OLD; -- parent/account/source cascade; privacy deletion remains possible
    END IF;
    SELECT a.* INTO parent FROM student_attempts a WHERE id=NEW.attempt_id;
    SELECT xmin INTO parent_xmin FROM student_attempts WHERE id=NEW.attempt_id;
    IF parent.id IS NULL OR parent.status<>'SUBMITTED'
        OR parent_xmin::text<>(pg_current_xact_id()::text::numeric % 4294967296)::text
        OR (parent.final_answer IS NOT NULL AND NEW.normalized_statement IS NOT NULL) THEN
        RAISE EXCEPTION 'Attempt steps must be inserted with original evidence' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_attempt_steps_membership BEFORE INSERT OR DELETE ON student_attempt_steps
    FOR EACH ROW EXECUTE FUNCTION guard_attempt_step_membership();

CREATE FUNCTION check_attempt_shape() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE target UUID; answer TEXT; n INTEGER; last_index INTEGER;
BEGIN
    IF TG_TABLE_NAME='student_attempts' THEN target=NEW.id; ELSE target=NEW.attempt_id; END IF;
    SELECT final_answer INTO answer FROM student_attempts WHERE id=target;
    IF NOT FOUND THEN RETURN NULL; END IF;
    SELECT count(*),coalesce(max(step_index),0) INTO n,last_index FROM student_attempt_steps WHERE attempt_id=target;
    IF n<>last_index OR (answer IS NULL AND n NOT BETWEEN 1 AND 12)
        OR (answer IS NOT NULL AND n NOT BETWEEN 0 AND 11) THEN
        RAISE EXCEPTION 'Invalid learner evidence shape' USING ERRCODE='23514';
    END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER trg_attempt_shape AFTER INSERT ON student_attempts
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_attempt_shape();
CREATE CONSTRAINT TRIGGER trg_attempt_step_shape AFTER INSERT ON student_attempt_steps
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_attempt_shape();
