-- V040 retained attempt evidence in legacy_counterfactual_twin_drills.
-- Keep the existing trigger bindings and evidence checks on that relation.

CREATE OR REPLACE FUNCTION protect_learning_artifact() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_TABLE_NAME='legacy_counterfactual_twin_drills' THEN
        IF (NEW.id,NEW.user_id,NEW.attempt_id,NEW.artifact,NEW.correct_option,NEW.created_at)
           IS DISTINCT FROM (OLD.id,OLD.user_id,OLD.attempt_id,OLD.artifact,OLD.correct_option,OLD.created_at)
           OR OLD.completed_at IS NOT NULL OR NEW.completed_at IS NULL THEN
            RAISE EXCEPTION 'Twin artifact and completion are immutable' USING ERRCODE='23514';
        END IF;
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'Learning evidence is immutable' USING ERRCODE='23514';
END $$;

CREATE OR REPLACE FUNCTION capture_mastery_context() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE owner_id UUID; attempt UUID; evidence UUID; problem UUID; at_time TIMESTAMPTZ; twin BOOLEAN;
BEGIN
    twin := TG_TABLE_NAME='legacy_counterfactual_twin_drills';
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

CREATE OR REPLACE FUNCTION guard_mistake_retention_sources() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    source_time TIMESTAMPTZ;
    expected_drills INTEGER;
    matching_drills INTEGER;
BEGIN
    SELECT (w.artifact->>'createdAt')::timestamptz INTO source_time
    FROM attempt_mistake_classifications c
    JOIN attempt_structured_work w ON w.attempt_id=c.attempt_id AND w.user_id=c.user_id
    WHERE c.id=NEW.classification_id AND c.user_id=NEW.user_id AND c.attempt_id=NEW.causal_attempt
      AND c.artifact#>>'{decision,status}'='SIGNATURE_SUPPORTED'
      AND c.artifact#>>'{decision,code}'=NEW.mistake_code AND c.artifact#>>'{decision,skillId}'=NEW.skill_id;
    IF source_time IS NULL OR source_time > NEW.event_at
        OR (NEW.to_state='RELAPSED' AND source_time <> NEW.event_at) THEN
        RAISE EXCEPTION 'Invalid retention error lineage' USING ERRCODE='23514';
    END IF;
    expected_drills := CASE WHEN NEW.check_drill IS NULL THEN 2 ELSE 3 END;
    SELECT count(*) INTO matching_drills
    FROM legacy_counterfactual_twin_drills d
    JOIN attempt_mastery_context c ON c.evidence_id=d.id AND c.user_id=d.user_id AND c.attempt_id=d.attempt_id
    WHERE d.user_id=NEW.user_id AND d.artifact->>'mistakeCode'=NEW.mistake_code
      AND d.assistance='INDEPENDENT' AND d.selected_option=d.correct_option
      AND c.provenance_complete AND NOT c.solution_revealed AND c.hints_used_count=0 AND c.captured_at=d.completed_at
      AND ((d.id=NEW.first_drill AND d.completed_at<=NEW.pending_at)
        OR (d.id=NEW.pending_drill AND d.completed_at=NEW.pending_at)
        OR (d.id=NEW.check_drill AND d.completed_at=NEW.event_at));
    IF matching_drills <> expected_drills THEN
        RAISE EXCEPTION 'Invalid retention drill lineage' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
