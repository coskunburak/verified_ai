-- Forward-only learner evidence. Original attempts and verifier runs remain immutable.
ALTER TABLE student_attempts ADD COLUMN capture_review JSONB CHECK (capture_review IS NULL OR (jsonb_typeof(capture_review)='object' AND octet_length(capture_review::text)<=16000));
CREATE TABLE counterfactual_twin_drills (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    attempt_id UUID NOT NULL,
    artifact JSONB NOT NULL CHECK (jsonb_typeof(artifact)='object' AND octet_length(artifact::text)<=20000),
    correct_option INTEGER NOT NULL CHECK (correct_option BETWEEN 0 AND 3),
    selected_option INTEGER CHECK (selected_option BETWEEN 0 AND 3),
    answer_key VARCHAR(128) CHECK (answer_key ~ '^[A-Za-z0-9_-]{8,128}$'),
    assistance VARCHAR(24) CHECK (assistance IN ('INDEPENDENT','HINT_ASSISTED')),
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    training_eligible BOOLEAN NOT NULL DEFAULT FALSE CHECK (training_eligible=false),
    UNIQUE (attempt_id), UNIQUE (id,user_id),
    FOREIGN KEY (attempt_id,user_id) REFERENCES student_attempts(id,user_id) ON DELETE CASCADE,
    CHECK ((completed_at IS NULL)=(selected_option IS NULL)),
    CHECK ((completed_at IS NULL)=(answer_key IS NULL)),
    CHECK ((completed_at IS NULL)=(assistance IS NULL))
);
CREATE INDEX ix_twin_owner_history ON counterfactual_twin_drills(user_id,created_at,id);
CREATE TABLE attempt_verified_methods (
    attempt_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    evidence JSONB NOT NULL CHECK (jsonb_typeof(evidence)='object' AND octet_length(evidence::text)<=20000),
    created_at TIMESTAMPTZ NOT NULL,
    training_eligible BOOLEAN NOT NULL DEFAULT FALSE CHECK (training_eligible=false),
    FOREIGN KEY (attempt_id,user_id) REFERENCES student_attempts(id,user_id) ON DELETE CASCADE
);
CREATE INDEX ix_methods_owner ON attempt_verified_methods(user_id);
CREATE TABLE learner_mistake_events (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    attempt_id UUID NOT NULL,
    drill_id UUID,
    evidence_source VARCHAR(24) NOT NULL CHECK (evidence_source IN ('ATTEMPT_DISPROOF','TWIN_COMPLETION')),
    evidence_key VARCHAR(128) NOT NULL,
    skill_id VARCHAR(96) NOT NULL,
    mistake_code VARCHAR(64) NOT NULL,
    correct BOOLEAN NOT NULL,
    assistance VARCHAR(24) NOT NULL CHECK (assistance IN ('UNKNOWN','INDEPENDENT','HINT_ASSISTED')),
    verifier_version VARCHAR(40) NOT NULL,
    assessed_at TIMESTAMPTZ NOT NULL,
    training_eligible BOOLEAN NOT NULL DEFAULT FALSE CHECK (training_eligible=false),
    UNIQUE(user_id,evidence_key),
    FOREIGN KEY (attempt_id,user_id) REFERENCES student_attempts(id,user_id) ON DELETE CASCADE,
    FOREIGN KEY (drill_id,user_id) REFERENCES counterfactual_twin_drills(id,user_id) ON DELETE CASCADE,
    CHECK ((evidence_source='TWIN_COMPLETION')=(drill_id IS NOT NULL)),
    CHECK (evidence_source!='ATTEMPT_DISPROOF' OR correct=false)
);
CREATE INDEX ix_mistake_owner_skill ON learner_mistake_events(user_id,skill_id,assessed_at,id);
CREATE TABLE learner_skill_mastery (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    skill_id VARCHAR(96) NOT NULL,
    mastery_version VARCHAR(32) NOT NULL CHECK (mastery_version='bkt-lite-v1'),
    score DOUBLE PRECISION NOT NULL CHECK (score BETWEEN 0.05 AND 0.99),
    half_life_days DOUBLE PRECISION NOT NULL CHECK (half_life_days BETWEEN 1 AND 90),
    evidence_count INTEGER NOT NULL CHECK (evidence_count>0),
    confidence DOUBLE PRECISION NOT NULL CHECK (confidence BETWEEN 0 AND 1),
    last_assessed_at TIMESTAMPTZ NOT NULL,
    training_eligible BOOLEAN NOT NULL DEFAULT FALSE CHECK (training_eligible=false),
    PRIMARY KEY(user_id,skill_id)
);

CREATE FUNCTION protect_attempt_evidence() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (NEW.id,NEW.user_id,NEW.problem_session_id,NEW.canonical_id,NEW.canonical_revision,NEW.parse_id,NEW.parse_revision,
        NEW.source_type,NEW.schema_version,NEW.normalization_version,NEW.create_key,NEW.request_hash,NEW.verifier_request_jsonb,NEW.created_at,NEW.capture_review)
       IS DISTINCT FROM
       (OLD.id,OLD.user_id,OLD.problem_session_id,OLD.canonical_id,OLD.canonical_revision,OLD.parse_id,OLD.parse_revision,
        OLD.source_type,OLD.schema_version,OLD.normalization_version,OLD.create_key,OLD.request_hash,OLD.verifier_request_jsonb,OLD.created_at,OLD.capture_review)
       OR OLD.status<>'SUBMITTED' OR NEW.status NOT IN ('ANALYZED','STALE_SOURCE') THEN
        RAISE EXCEPTION 'Attempt evidence is immutable' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_attempt_evidence_immutable BEFORE UPDATE ON student_attempts FOR EACH ROW EXECUTE FUNCTION protect_attempt_evidence();
CREATE FUNCTION protect_learning_artifact() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_TABLE_NAME='counterfactual_twin_drills' THEN
        IF (NEW.id,NEW.user_id,NEW.attempt_id,NEW.artifact,NEW.correct_option,NEW.created_at)
           IS DISTINCT FROM (OLD.id,OLD.user_id,OLD.attempt_id,OLD.artifact,OLD.correct_option,OLD.created_at)
           OR OLD.completed_at IS NOT NULL OR NEW.completed_at IS NULL THEN
            RAISE EXCEPTION 'Twin artifact and completion are immutable' USING ERRCODE='23514';
        END IF;
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'Learning evidence is immutable' USING ERRCODE='23514';
END $$;
CREATE TRIGGER trg_twin_immutable BEFORE UPDATE ON counterfactual_twin_drills FOR EACH ROW EXECUTE FUNCTION protect_learning_artifact();
CREATE TRIGGER trg_method_immutable BEFORE UPDATE ON attempt_verified_methods FOR EACH ROW EXECUTE FUNCTION protect_learning_artifact();
CREATE TRIGGER trg_mistake_immutable BEFORE UPDATE ON learner_mistake_events FOR EACH ROW EXECUTE FUNCTION protect_learning_artifact();
CREATE TRIGGER trg_attempt_steps_immutable BEFORE UPDATE ON student_attempt_steps FOR EACH ROW EXECUTE FUNCTION protect_learning_artifact();
