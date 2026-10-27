-- Canonical representation is immutable accepted syntax, never a correctness verdict.
ALTER TABLE canonical_problems ADD CONSTRAINT uq_canonical_projection_revision
    UNIQUE(id,user_id,problem_session_id,problem_parse_id,problem_parse_revision,canonical_revision);
ALTER TABLE secondary_solve_jobs ADD CONSTRAINT uq_secondary_projection_lineage
    UNIQUE(id,primary_job_id,user_id,problem_session_id,canonical_problem_id,problem_parse_id,problem_parse_revision,canonical_revision);

CREATE TABLE canonical_solutions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    problem_session_id UUID NOT NULL,
    canonical_problem_id UUID NOT NULL,
    problem_parse_id UUID NOT NULL,
    problem_parse_revision INTEGER NOT NULL CHECK(problem_parse_revision>0),
    canonical_revision INTEGER NOT NULL CHECK(canonical_revision>0),
    source_role VARCHAR(16) NOT NULL CHECK(source_role IN ('PRIMARY','SECONDARY')),
    source_job_id UUID NOT NULL,
    primary_job_id UUID NOT NULL REFERENCES solution_candidates(job_id) ON DELETE CASCADE,
    secondary_job_id UUID REFERENCES secondary_solution_evidence(job_id) ON DELETE CASCADE,
    representation_version VARCHAR(64) NOT NULL CHECK(length(representation_version)>0),
    normalizer_version VARCHAR(64) NOT NULL CHECK(length(normalizer_version)>0),
    operation_id UUID NOT NULL,
    -- References remain in original evidence; ledger retention may remove its own row independently.
    ledger_record_id UUID,
    content_jsonb JSONB NOT NULL CHECK(jsonb_typeof(content_jsonb)='object'
        AND content_jsonb ?& ARRAY['generationOrigin','problemType','taskType','method','finalAnswer',
            'sourceAssumptionIds','sourceConstraintIds','derivedRestrictionIds','ambiguities','unresolvedIssues','provenance']
        AND jsonb_typeof(content_jsonb->'finalAnswer')='object'
        AND coalesce(content_jsonb->'finalAnswer'->>'kind' IN ('EXPRESSION','RELATION'),false)
        AND NOT (content_jsonb ? 'verified') AND octet_length(content_jsonb::text)<=131072),
    expected_step_count INTEGER NOT NULL CHECK(expected_step_count BETWEEN 1 AND 24),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_canonical_origin_version UNIQUE(source_role,source_job_id,representation_version,normalizer_version),
    CONSTRAINT ck_canonical_role CHECK(
        source_role='PRIMARY' AND source_job_id=primary_job_id AND secondary_job_id IS NULL OR
        source_role='SECONDARY' AND source_job_id=secondary_job_id AND secondary_job_id IS NOT NULL),
    CONSTRAINT fk_canonical_projection_source FOREIGN KEY
        (canonical_problem_id,user_id,problem_session_id,problem_parse_id,problem_parse_revision,canonical_revision)
        REFERENCES canonical_problems(id,user_id,problem_session_id,problem_parse_id,problem_parse_revision,canonical_revision) ON DELETE CASCADE,
    CONSTRAINT fk_canonical_primary_owner FOREIGN KEY
        (primary_job_id,user_id,problem_session_id,canonical_problem_id,problem_parse_id,problem_parse_revision)
        REFERENCES solve_jobs(id,user_id,problem_session_id,canonical_problem_id,problem_parse_id,problem_parse_revision) ON DELETE CASCADE,
    CONSTRAINT fk_canonical_secondary_owner FOREIGN KEY
        (secondary_job_id,primary_job_id,user_id,problem_session_id,canonical_problem_id,problem_parse_id,problem_parse_revision,canonical_revision)
        REFERENCES secondary_solve_jobs(id,primary_job_id,user_id,problem_session_id,canonical_problem_id,problem_parse_id,problem_parse_revision,canonical_revision) ON DELETE CASCADE
);
CREATE INDEX ix_canonical_selected_source ON canonical_solutions
    (user_id,problem_session_id,canonical_problem_id,canonical_revision,representation_version,normalizer_version);
CREATE INDEX ix_canonical_primary ON canonical_solutions(primary_job_id);
CREATE INDEX ix_canonical_secondary ON canonical_solutions(secondary_job_id) WHERE secondary_job_id IS NOT NULL;

CREATE TABLE canonical_solution_steps (
    solution_id UUID NOT NULL REFERENCES canonical_solutions(id) ON DELETE CASCADE,
    id UUID NOT NULL,
    candidate_step_id VARCHAR(32) NOT NULL CHECK(length(candidate_step_id)>0),
    ordinal INTEGER NOT NULL CHECK(ordinal BETWEEN 1 AND 24),
    category VARCHAR(32) NOT NULL CHECK(category IN ('RESTATE','TRANSFORM','CONCLUDE')),
    claim_jsonb JSONB NOT NULL CHECK(jsonb_typeof(claim_jsonb)='object'
        AND coalesce(claim_jsonb->>'kind' IN ('EXPRESSION','RELATION'),false)
        AND NOT (claim_jsonb ? 'verified') AND octet_length(claim_jsonb::text)<=32768),
    explanation VARCHAR(1000) NOT NULL CHECK(length(explanation)>0),
    expected_dependency_count INTEGER NOT NULL CHECK(expected_dependency_count BETWEEN 0 AND 23),
    PRIMARY KEY(solution_id,id),
    UNIQUE(solution_id,candidate_step_id),
    UNIQUE(solution_id,ordinal),
    UNIQUE(solution_id,id,ordinal)
);
CREATE TABLE canonical_solution_dependencies (
    solution_id UUID NOT NULL,
    step_id UUID NOT NULL,
    step_ordinal INTEGER NOT NULL,
    dependency_id UUID NOT NULL,
    dependency_ordinal INTEGER NOT NULL,
    reference_order INTEGER NOT NULL CHECK(reference_order BETWEEN 1 AND 23),
    PRIMARY KEY(solution_id,step_id,dependency_id),
    UNIQUE(solution_id,step_id,reference_order),
    CHECK(dependency_ordinal<step_ordinal),
    FOREIGN KEY(solution_id,step_id,step_ordinal) REFERENCES canonical_solution_steps(solution_id,id,ordinal) ON DELETE CASCADE,
    FOREIGN KEY(solution_id,dependency_id,dependency_ordinal) REFERENCES canonical_solution_steps(solution_id,id,ordinal) ON DELETE CASCADE
);
CREATE INDEX ix_canonical_dependency_target ON canonical_solution_dependencies(solution_id,dependency_id);

CREATE TABLE canonical_materialization_failures (
    source_role VARCHAR(16) NOT NULL CHECK(source_role IN ('PRIMARY','SECONDARY')),
    source_job_id UUID NOT NULL,
    user_id UUID NOT NULL,
    problem_session_id UUID NOT NULL,
    primary_job_id UUID NOT NULL REFERENCES solution_candidates(job_id) ON DELETE CASCADE,
    secondary_job_id UUID REFERENCES secondary_solution_evidence(job_id) ON DELETE CASCADE,
    representation_version VARCHAR(64) NOT NULL,
    normalizer_version VARCHAR(64) NOT NULL,
    failure_code VARCHAR(40) NOT NULL CHECK(failure_code IN ('UNSUPPORTED_SCHEMA','INVALID_CANDIDATE',
        'SOURCE_MISMATCH','SOURCE_UNAVAILABLE','UNSUPPORTED_REPRESENTATION')),
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY(source_role,source_job_id,representation_version,normalizer_version),
    CHECK(source_role='PRIMARY' AND source_job_id=primary_job_id AND secondary_job_id IS NULL OR
        source_role='SECONDARY' AND source_job_id=secondary_job_id AND secondary_job_id IS NOT NULL)
);
CREATE INDEX ix_canonical_failure_owner ON canonical_materialization_failures(user_id,problem_session_id);
-- Failure ownership must be just as strong as success ownership; a forged failure cannot suppress another user's work.
ALTER TABLE solve_jobs ADD CONSTRAINT uq_primary_projection_owner UNIQUE(id,user_id,problem_session_id);
ALTER TABLE secondary_solve_jobs ADD CONSTRAINT uq_secondary_projection_owner UNIQUE(id,primary_job_id,user_id,problem_session_id);
ALTER TABLE canonical_materialization_failures ADD FOREIGN KEY(primary_job_id,user_id,problem_session_id)
    REFERENCES solve_jobs(id,user_id,problem_session_id) ON DELETE CASCADE;
ALTER TABLE canonical_materialization_failures ADD FOREIGN KEY(secondary_job_id,primary_job_id,user_id,problem_session_id)
    REFERENCES secondary_solve_jobs(id,primary_job_id,user_id,problem_session_id) ON DELETE CASCADE;

CREATE TRIGGER canonical_solution_immutable BEFORE UPDATE ON canonical_solutions
    FOR EACH ROW EXECUTE FUNCTION prevent_solution_candidate_update();
CREATE TRIGGER canonical_step_immutable BEFORE UPDATE ON canonical_solution_steps
    FOR EACH ROW EXECUTE FUNCTION prevent_solution_candidate_update();
CREATE TRIGGER canonical_dependency_immutable BEFORE UPDATE ON canonical_solution_dependencies
    FOR EACH ROW EXECUTE FUNCTION prevent_solution_candidate_update();
CREATE TRIGGER canonical_failure_immutable BEFORE UPDATE ON canonical_materialization_failures
    FOR EACH ROW EXECUTE FUNCTION prevent_solution_candidate_update();

-- Reject delete/reinsert rewrites. Deletion is allowed only as a whole owned-source cascade;
-- PostgreSQL has removed the referenced parent before invoking its cascading child deletes.
CREATE FUNCTION protect_canonical_projection_delete() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE parent_present BOOLEAN;
BEGIN
    IF TG_TABLE_NAME IN ('canonical_solution_steps','canonical_solution_dependencies') THEN
        SELECT EXISTS(SELECT 1 FROM canonical_solutions WHERE id=OLD.solution_id) INTO parent_present;
    ELSIF OLD.source_role='PRIMARY' THEN
        SELECT EXISTS(SELECT 1 FROM solution_candidates c JOIN solve_jobs j ON j.id=c.job_id
            JOIN canonical_problems p ON p.id=j.canonical_problem_id WHERE j.id=OLD.source_job_id) INTO parent_present;
    ELSE
        SELECT EXISTS(SELECT 1 FROM secondary_solution_evidence e JOIN secondary_solve_jobs j ON j.id=e.job_id
            JOIN canonical_problems p ON p.id=j.canonical_problem_id
            JOIN solution_candidates c ON c.job_id=j.primary_job_id
            JOIN solve_jobs primary_job ON primary_job.id=c.job_id
            WHERE j.id=OLD.source_job_id) INTO parent_present;
    END IF;
    IF parent_present THEN
        RAISE EXCEPTION 'Canonical content can only be deleted with its owned source' USING ERRCODE='23514';
    END IF;
    RETURN OLD;
END;
$$;
CREATE TRIGGER canonical_solution_delete BEFORE DELETE ON canonical_solutions
    FOR EACH ROW EXECUTE FUNCTION protect_canonical_projection_delete();
CREATE TRIGGER canonical_step_delete BEFORE DELETE ON canonical_solution_steps
    FOR EACH ROW EXECUTE FUNCTION protect_canonical_projection_delete();
CREATE TRIGGER canonical_dependency_delete BEFORE DELETE ON canonical_solution_dependencies
    FOR EACH ROW EXECUTE FUNCTION protect_canonical_projection_delete();
CREATE TRIGGER canonical_failure_delete BEFORE DELETE ON canonical_materialization_failures
    FOR EACH ROW EXECUTE FUNCTION protect_canonical_projection_delete();

-- Deferred completeness guards permit atomic construction and whole-account deletion,
-- while rejecting partial graphs and post-commit append/delete mutations.
CREATE FUNCTION enforce_canonical_graph_complete() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE sid UUID; expected INTEGER; actual INTEGER; first_order INTEGER; last_order INTEGER;
BEGIN
    IF TG_TABLE_NAME='canonical_solutions' THEN sid:=NEW.id;
    ELSIF TG_OP='DELETE' THEN sid:=OLD.solution_id;
    ELSE sid:=NEW.solution_id; END IF;
    SELECT expected_step_count INTO expected FROM canonical_solutions WHERE id=sid;
    IF NOT FOUND THEN RETURN NULL; END IF;
    SELECT count(*),min(ordinal),max(ordinal) INTO actual,first_order,last_order FROM canonical_solution_steps WHERE solution_id=sid;
    IF actual<>expected OR first_order<>1 OR last_order<>expected THEN
        RAISE EXCEPTION 'Incomplete canonical step graph' USING ERRCODE='23514';
    END IF;
    IF EXISTS(SELECT 1 FROM canonical_solution_steps s WHERE s.solution_id=sid AND
        (s.expected_dependency_count<>(SELECT count(*) FROM canonical_solution_dependencies d WHERE d.solution_id=sid AND d.step_id=s.id)
         OR s.expected_dependency_count<>coalesce((SELECT max(reference_order) FROM canonical_solution_dependencies d WHERE d.solution_id=sid AND d.step_id=s.id),0))) THEN
        RAISE EXCEPTION 'Incomplete canonical dependencies' USING ERRCODE='23514';
    END IF;
    RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER canonical_graph_header AFTER INSERT ON canonical_solutions
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION enforce_canonical_graph_complete();
CREATE CONSTRAINT TRIGGER canonical_graph_steps AFTER INSERT OR DELETE ON canonical_solution_steps
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION enforce_canonical_graph_complete();
CREATE CONSTRAINT TRIGGER canonical_graph_dependencies AFTER INSERT OR DELETE ON canonical_solution_dependencies
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION enforce_canonical_graph_complete();

CREATE FUNCTION protect_primary_projection_identity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF ROW(NEW.id,NEW.user_id,NEW.problem_session_id,NEW.canonical_problem_id,NEW.problem_parse_id,
        NEW.problem_parse_revision,NEW.operation_id,NEW.attempt_number,NEW.idempotency_key,NEW.retry_of,
        NEW.prompt_id,NEW.prompt_version,NEW.schema_version,NEW.route_policy_version,NEW.created_at,NEW.deadline)
       IS DISTINCT FROM
       ROW(OLD.id,OLD.user_id,OLD.problem_session_id,OLD.canonical_problem_id,OLD.problem_parse_id,
        OLD.problem_parse_revision,OLD.operation_id,OLD.attempt_number,OLD.idempotency_key,OLD.retry_of,
        OLD.prompt_id,OLD.prompt_version,OLD.schema_version,OLD.route_policy_version,OLD.created_at,OLD.deadline) THEN
        -- retry_of may be nulled by account/source deletion's existing ON DELETE SET NULL.
        IF NEW.retry_of IS NOT NULL OR OLD.retry_of IS NULL OR
           (to_jsonb(NEW)-'retry_of') IS DISTINCT FROM (to_jsonb(OLD)-'retry_of') THEN
            RAISE EXCEPTION 'Primary projection source identity is immutable' USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER primary_projection_identity BEFORE UPDATE ON solve_jobs
    FOR EACH ROW EXECUTE FUNCTION protect_primary_projection_identity();
