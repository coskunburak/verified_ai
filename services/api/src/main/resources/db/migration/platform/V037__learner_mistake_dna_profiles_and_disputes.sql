-- Reproducible projections over V035/V036; no copies or changes to V031/V035/V036 evidence.
ALTER TABLE attempt_mistake_classifications ADD CONSTRAINT uq_dna_classification_owner UNIQUE(id,user_id,attempt_id);
CREATE TABLE learner_mistake_dispute_events (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    attempt_id UUID NOT NULL,
    classification_id UUID,
    replacement_attempt_id UUID,
    step_index INTEGER CHECK(step_index BETWEEN 1 AND 12),
    dispute_type VARCHAR(32) NOT NULL CHECK(dispute_type IN ('DISPUTED_BY_LEARNER','SUPERSEDED_BY_OCR_CORRECTION')),
    policy_version VARCHAR(32) NOT NULL DEFAULT 'mistake-dna-v1' CHECK(policy_version='mistake-dna-v1'),
    create_key VARCHAR(128) NOT NULL CHECK(create_key ~ '^[A-Za-z0-9_-]{8,128}$'),
    request_hash CHAR(64) NOT NULL CHECK(request_hash ~ '^[0-9a-f]{64}$'),
    created_at TIMESTAMPTZ NOT NULL,
    training_eligible BOOLEAN NOT NULL DEFAULT FALSE CHECK(NOT training_eligible),
    UNIQUE(user_id,create_key),
    UNIQUE(attempt_id,dispute_type),
    FOREIGN KEY(attempt_id,user_id) REFERENCES attempt_structured_work(attempt_id,user_id) ON DELETE CASCADE,
    FOREIGN KEY(classification_id,user_id,attempt_id) REFERENCES attempt_mistake_classifications(id,user_id,attempt_id)
        DEFERRABLE INITIALLY DEFERRED,
    FOREIGN KEY(replacement_attempt_id,user_id) REFERENCES attempt_structured_work(attempt_id,user_id)
        DEFERRABLE INITIALLY DEFERRED,
    CHECK((dispute_type='DISPUTED_BY_LEARNER' AND classification_id IS NOT NULL AND replacement_attempt_id IS NULL AND step_index IS NULL)
       OR (dispute_type='SUPERSEDED_BY_OCR_CORRECTION' AND replacement_attempt_id IS NOT NULL AND step_index IS NOT NULL))
);
CREATE INDEX ix_dna_dispute_owner ON learner_mistake_dispute_events(user_id,attempt_id);
CREATE FUNCTION guard_mistake_dispute() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='UPDATE' THEN RAISE EXCEPTION 'Disputes are append only' USING ERRCODE='23514'; END IF;
    IF TG_OP='DELETE' THEN
        IF EXISTS(SELECT 1 FROM attempt_structured_work WHERE attempt_id=OLD.attempt_id) THEN
            RAISE EXCEPTION 'Dispute belongs to work evidence' USING ERRCODE='23514';
        END IF;
        RETURN OLD;
    END IF;
    IF NEW.replacement_attempt_id IS NOT NULL AND NOT EXISTS(
        SELECT 1 FROM attempt_structured_work w JOIN attempt_structured_work prior ON prior.attempt_id=w.supersedes_attempt_id
        WHERE w.attempt_id=NEW.replacement_attempt_id AND w.user_id=NEW.user_id AND w.supersedes_attempt_id=NEW.attempt_id
          AND prior.artifact->>'engine' IN ('NATIVE_VISION','GATEWAY_RECOGNITION')
          AND NEW.step_index<=jsonb_array_length(prior.artifact->'steps')) THEN
        RAISE EXCEPTION 'Invalid OCR replacement lineage' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_mistake_dispute BEFORE INSERT OR UPDATE OR DELETE ON learner_mistake_dispute_events
    FOR EACH ROW EXECUTE FUNCTION guard_mistake_dispute();
CREATE TABLE learner_mistake_dna_profiles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    mistake_code VARCHAR(64) NOT NULL,
    skill_id VARCHAR(96) NOT NULL,
    taxonomy_version VARCHAR(32) NOT NULL CHECK(taxonomy_version='attempt-taxonomy-v2'),
    category_version VARCHAR(32) NOT NULL CHECK(category_version='mistake-category-v1'),
    policy_version VARCHAR(32) NOT NULL CHECK(policy_version='mistake-dna-v1'),
    decayed_error_count NUMERIC(20,8) NOT NULL CHECK(decayed_error_count>=0 AND decayed_error_count<>'NaN'::numeric),
    decayed_exposure_count NUMERIC(20,8) NOT NULL CHECK(decayed_exposure_count>=decayed_error_count AND decayed_exposure_count<>'NaN'::numeric),
    smoothed_propensity NUMERIC(9,8) NOT NULL CHECK(smoothed_propensity BETWEEN 0 AND 1),
    recent_error_count INTEGER NOT NULL CHECK(recent_error_count>=0),
    recurrence_state VARCHAR(32) NOT NULL CHECK(recurrence_state IN ('OBSERVED_ONCE','RECURRING_PATTERN',
        'IN_REMEDIATION','RESOLVED_PENDING_RETENTION','STABLE_RESOLVED','RELAPSED')),
    last_occurred_at TIMESTAMPTZ NOT NULL,
    as_of TIMESTAMPTZ NOT NULL,
    training_eligible BOOLEAN NOT NULL DEFAULT FALSE CHECK(NOT training_eligible),
    PRIMARY KEY(user_id,mistake_code,skill_id,policy_version)
);
CREATE INDEX ix_dna_profile_owner_rank ON learner_mistake_dna_profiles(user_id,smoothed_propensity DESC,mistake_code);

-- Retention/session/account cascades must never leave a cached profile derived from deleted evidence.
-- Invalidate the whole bounded owner cache; the next enabled read rebuilds from remaining evidence.
CREATE FUNCTION invalidate_deleted_mistake_dna() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    DELETE FROM learner_mistake_dna_profiles WHERE user_id=OLD.user_id;
    RETURN OLD;
END $$;
CREATE TRIGGER trg_deleted_classification_dna AFTER DELETE ON attempt_mistake_classifications
    FOR EACH ROW EXECUTE FUNCTION invalidate_deleted_mistake_dna();
