-- Derived, append-only history. Effective state is replayed from surviving, eligible sources.
CREATE TABLE learner_mistake_retention_transitions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    mistake_code VARCHAR(64) NOT NULL,
    skill_id VARCHAR(96) NOT NULL,
    policy_version TEXT NOT NULL DEFAULT 'trap-retention-v1' CHECK (policy_version = 'trap-retention-v1'),
    from_state TEXT NOT NULL,
    to_state TEXT NOT NULL,
    first_drill UUID NOT NULL,
    pending_drill UUID NOT NULL,
    check_drill UUID,
    classification_id UUID NOT NULL,
    causal_attempt UUID NOT NULL,
    pending_at TIMESTAMPTZ NOT NULL,
    event_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    training_eligible BOOLEAN NOT NULL DEFAULT FALSE CHECK (NOT training_eligible),
    FOREIGN KEY (classification_id,user_id,causal_attempt)
        REFERENCES attempt_mistake_classifications(id,user_id,attempt_id) ON DELETE CASCADE,
    FOREIGN KEY (first_drill,user_id) REFERENCES counterfactual_twin_drills(id,user_id) ON DELETE CASCADE,
    FOREIGN KEY (pending_drill,user_id) REFERENCES counterfactual_twin_drills(id,user_id) ON DELETE CASCADE,
    FOREIGN KEY (check_drill,user_id) REFERENCES counterfactual_twin_drills(id,user_id) ON DELETE CASCADE,
    CHECK (first_drill <> pending_drill AND event_at >= pending_at AND processed_at >= event_at),
    CHECK (
        (from_state = 'IN_REMEDIATION' AND to_state = 'RESOLVED_PENDING_RETENTION'
            AND check_drill IS NULL AND event_at = pending_at)
        OR (from_state = 'RESOLVED_PENDING_RETENTION' AND to_state = 'STABLE_RESOLVED'
            AND check_drill IS NOT NULL AND check_drill NOT IN (first_drill,pending_drill)
            AND event_at - pending_at >= INTERVAL '168 hours')
        OR (from_state IN ('RESOLVED_PENDING_RETENTION','STABLE_RESOLVED') AND to_state = 'RELAPSED' AND check_drill IS NULL)
    ),
    UNIQUE NULLS NOT DISTINCT (user_id,mistake_code,skill_id,policy_version,from_state,to_state,
        first_drill,pending_drill,check_drill,classification_id)
);
CREATE INDEX ix_mistake_retention_history ON learner_mistake_retention_transitions(user_id,event_at,id);
CREATE TRIGGER trg_mistake_retention_immutable BEFORE UPDATE ON learner_mistake_retention_transitions
    FOR EACH ROW EXECUTE FUNCTION protect_learning_artifact();

-- Bind immutable audit times and hazard identity to actual owner evidence.
CREATE FUNCTION guard_mistake_retention_sources() RETURNS trigger LANGUAGE plpgsql AS $$
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
    FROM counterfactual_twin_drills d
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
CREATE TRIGGER trg_mistake_retention_sources BEFORE INSERT ON learner_mistake_retention_transitions
    FOR EACH ROW EXECUTE FUNCTION guard_mistake_retention_sources();

-- Suppress stale derived exports when a source is removed before the next accepted write.
CREATE TRIGGER trg_deleted_twin_dna AFTER DELETE ON counterfactual_twin_drills
    FOR EACH ROW EXECUTE FUNCTION invalidate_deleted_mistake_dna();
CREATE TRIGGER trg_deleted_mastery_context_dna AFTER DELETE ON attempt_mastery_context
    FOR EACH ROW EXECUTE FUNCTION invalidate_deleted_mistake_dna();
