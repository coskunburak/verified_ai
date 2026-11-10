-- Forward only. No historical independence or canonical mapping is fabricated.
ALTER TABLE learner_skill_mastery DROP CONSTRAINT learner_skill_mastery_mastery_version_check;
ALTER TABLE learner_skill_mastery ADD CONSTRAINT ck_mastery_policy CHECK(mastery_version IN ('bkt-lite-v1','bkt-lite-v2'));
ALTER TABLE learner_skill_mastery
    ADD COLUMN independent_evidence_count INTEGER NOT NULL DEFAULT 0 CHECK(independent_evidence_count>=0),
    ADD COLUMN assisted_evidence_count INTEGER NOT NULL DEFAULT 0 CHECK(assisted_evidence_count>=0),
    ADD COLUMN independent_competence_score NUMERIC(9,8) NOT NULL DEFAULT .25 CHECK(independent_competence_score BETWEEN 0 AND 1),
    ADD COLUMN independent_pass_count INTEGER NOT NULL DEFAULT 0 CHECK(independent_pass_count>=0),
    ADD COLUMN capped_by_assistance BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN evidence_fingerprint CHAR(64),
    ADD COLUMN projected_at TIMESTAMPTZ,
    ADD COLUMN last_confidence_evidence_at TIMESTAMPTZ;
ALTER TABLE learner_skill_mastery ADD CONSTRAINT ck_mastery_v2_counts CHECK(mastery_version<>'bkt-lite-v2' OR
    (evidence_count=independent_evidence_count+assisted_evidence_count AND independent_pass_count<=independent_evidence_count
     AND evidence_fingerprint IS NOT NULL AND projected_at IS NOT NULL));

-- Server delivery, not internal canonical-solution reads. Legacy delivery history stays explicitly unknown.
CREATE TABLE solution_learning_disclosures (
    user_id UUID NOT NULL, problem_session_id UUID NOT NULL,
    first_delivered_at TIMESTAMPTZ, tracking_complete BOOLEAN NOT NULL,
    PRIMARY KEY(user_id,problem_session_id),
    FOREIGN KEY(problem_session_id,user_id) REFERENCES problem_sessions(id,user_id) ON DELETE CASCADE
);
INSERT INTO solution_learning_disclosures(user_id,problem_session_id,tracking_complete)
SELECT DISTINCT j.user_id,j.problem_session_id,FALSE FROM solve_jobs j JOIN solution_candidates c ON c.job_id=j.id;

CREATE TABLE attempt_mastery_context (
    evidence_id UUID PRIMARY KEY, user_id UUID NOT NULL, attempt_id UUID NOT NULL,
    hints_used_count INTEGER NOT NULL CHECK(hints_used_count>=0),
    solution_revealed BOOLEAN NOT NULL, provenance_complete BOOLEAN NOT NULL,
    captured_at TIMESTAMPTZ NOT NULL, schema_version TEXT NOT NULL DEFAULT 'mastery-context-v1' CHECK(schema_version='mastery-context-v1'),
    training_eligible BOOLEAN NOT NULL DEFAULT FALSE CHECK(NOT training_eligible),
    FOREIGN KEY(attempt_id,user_id) REFERENCES student_attempts(id,user_id) ON DELETE CASCADE
);
CREATE INDEX ix_mastery_context_owner ON attempt_mastery_context(user_id,attempt_id);
CREATE TRIGGER trg_mastery_context_immutable BEFORE UPDATE ON attempt_mastery_context FOR EACH ROW EXECUTE FUNCTION protect_learning_artifact();
CREATE FUNCTION capture_mastery_context() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE owner_id UUID; attempt UUID; evidence UUID; problem UUID; at_time TIMESTAMPTZ; twin BOOLEAN;
BEGIN
    twin := TG_TABLE_NAME='counterfactual_twin_drills';
    IF twin THEN
        IF NEW.completed_at IS NULL OR OLD.completed_at IS NOT NULL THEN RETURN NEW; END IF;
        owner_id:=NEW.user_id; attempt:=NEW.attempt_id; evidence:=NEW.id; at_time:=NEW.completed_at;
    ELSE
        owner_id:=NEW.user_id; attempt:=NEW.attempt_id; evidence:=NEW.attempt_id; at_time:=(NEW.artifact->>'createdAt')::timestamptz;
    END IF;
    SELECT problem_session_id INTO problem FROM student_attempts WHERE id=attempt AND user_id=owner_id;
    INSERT INTO attempt_mastery_context(evidence_id,user_id,attempt_id,hints_used_count,solution_revealed,provenance_complete,captured_at)
    SELECT evidence,owner_id,attempt,
      (SELECT count(*) FROM tutor_hints h JOIN tutor_sessions s ON s.id=h.tutor_session_id AND s.user_id=h.user_id
       WHERE s.user_id=owner_id AND s.problem_session_id=problem AND h.created_at<=at_time),
      -- Twin answers are withheld until completion by the existing immutable drill contract.
      NOT twin AND EXISTS(SELECT 1 FROM solution_learning_disclosures d WHERE d.user_id=owner_id AND d.problem_session_id=problem AND d.first_delivered_at<=at_time),
      (twin OR NOT EXISTS(SELECT 1 FROM solution_learning_disclosures d WHERE d.user_id=owner_id AND d.problem_session_id=problem
          AND NOT d.tracking_complete AND d.first_delivered_at IS NULL))
      -- Socratic guidance is not an exact hint-depth record. Refuse independence rather than infer it.
      AND NOT EXISTS(SELECT 1 FROM tutor_turns t JOIN tutor_sessions s ON s.id=t.tutor_session_id AND s.user_id=t.user_id
          WHERE s.user_id=owner_id AND s.problem_session_id=problem AND t.created_at<=at_time AND t.outcome='GUIDANCE'),at_time;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_capture_work_mastery AFTER INSERT ON attempt_structured_work FOR EACH ROW EXECUTE FUNCTION capture_mastery_context();
CREATE TRIGGER trg_capture_twin_mastery AFTER UPDATE ON counterfactual_twin_drills FOR EACH ROW EXECUTE FUNCTION capture_mastery_context();

CREATE TABLE learner_skill_mastery_transitions (
    id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    canonical_skill_code VARCHAR(96) NOT NULL, attempt_id UUID NOT NULL, evidence_id UUID NOT NULL,
    evaluation_id UUID, execution_id UUID, skill_evidence_id UUID NOT NULL,
    evidence_type VARCHAR(32) NOT NULL CHECK(evidence_type IN ('INDEPENDENT','HINT_ASSISTED','SOLUTION_REVEALED_ASSISTED')),
    prior_score DOUBLE PRECISION NOT NULL CHECK(prior_score BETWEEN .05 AND .99),
    posterior_score DOUBLE PRECISION NOT NULL CHECK(posterior_score BETWEEN .05 AND .99),
    prior_half_life_days DOUBLE PRECISION NOT NULL CHECK(prior_half_life_days BETWEEN 1 AND 90),
    posterior_half_life_days DOUBLE PRECISION NOT NULL CHECK(posterior_half_life_days BETWEEN 1 AND 90),
    policy_version VARCHAR(32) NOT NULL CHECK(policy_version='bkt-lite-v2'),
    evidence_fingerprint CHAR(64) NOT NULL, provenance VARCHAR(96) NOT NULL,
    event_time TIMESTAMPTZ NOT NULL, created_at TIMESTAMPTZ NOT NULL,
    training_eligible BOOLEAN NOT NULL DEFAULT FALSE CHECK(NOT training_eligible),
    UNIQUE(user_id,canonical_skill_code,policy_version,evidence_fingerprint,evidence_id),
    FOREIGN KEY(attempt_id,user_id) REFERENCES student_attempts(id,user_id) ON DELETE CASCADE,
    FOREIGN KEY(evaluation_id,user_id,attempt_id) REFERENCES attempt_evaluations(id,user_id,attempt_id) ON DELETE CASCADE
);
CREATE INDEX ix_mastery_audit_owner ON learner_skill_mastery_transitions(user_id,event_time,id);
CREATE TRIGGER trg_mastery_audit_immutable BEFORE UPDATE ON learner_skill_mastery_transitions FOR EACH ROW EXECUTE FUNCTION protect_learning_artifact();

-- Immediate invalidation even while v2 is disabled. Reads rebuild from surviving accepted evidence.
CREATE FUNCTION invalidate_mastery_projection() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        DELETE FROM learner_skill_mastery WHERE user_id=OLD.user_id AND mastery_version='bkt-lite-v2';
        RETURN OLD;
    END IF;
    DELETE FROM learner_skill_mastery WHERE user_id=NEW.user_id AND mastery_version='bkt-lite-v2';
    RETURN NEW;
END $$;
CREATE TRIGGER trg_mastery_dispute AFTER INSERT ON learner_mistake_dispute_events FOR EACH ROW EXECUTE FUNCTION invalidate_mastery_projection();
CREATE TRIGGER trg_mastery_revision AFTER INSERT ON attempt_structured_work FOR EACH ROW WHEN(NEW.supersedes_attempt_id IS NOT NULL) EXECUTE FUNCTION invalidate_mastery_projection();
CREATE TRIGGER trg_mastery_classification_delete AFTER DELETE ON attempt_mistake_classifications FOR EACH ROW EXECUTE FUNCTION invalidate_mastery_projection();
CREATE TRIGGER trg_mastery_attempt_delete AFTER DELETE ON student_attempts FOR EACH ROW EXECUTE FUNCTION invalidate_mastery_projection();
CREATE TRIGGER trg_mastery_source_revision AFTER INSERT ON canonical_problems FOR EACH ROW EXECUTE FUNCTION invalidate_mastery_projection();

-- Ontology is a versioned resource catalog, not a SQL skill table. No inferred edges or propagation.
CREATE TABLE mastery_prerequisite_edges (
    ontology_version VARCHAR(64) NOT NULL, prerequisite_skill_code VARCHAR(96) NOT NULL,
    dependent_skill_code VARCHAR(96) NOT NULL,
    PRIMARY KEY(ontology_version,prerequisite_skill_code,dependent_skill_code),
    CHECK(prerequisite_skill_code<>dependent_skill_code)
);
