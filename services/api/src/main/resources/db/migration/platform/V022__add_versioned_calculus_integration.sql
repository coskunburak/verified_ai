-- Additive contracts only. No existing row is rewritten; production rollout stays disabled.
ALTER TABLE canonical_problems DROP CONSTRAINT ck_canonical_problems_schema_versions;
ALTER TABLE canonical_problems DROP CONSTRAINT ck_canonical_problems_problem_type;
ALTER TABLE canonical_problems DROP CONSTRAINT ck_canonical_problems_task_type;
ALTER TABLE canonical_problems ADD CONSTRAINT ck_canonical_problem_versioned_kind CHECK ((
    (schema_version='canonical-problem-v1' AND verifier_schema_version='verifier-input-v1'
        AND problem_type IN ('ARITHMETIC_EXPRESSION','ALGEBRAIC_EXPRESSION','EQUATION','INEQUALITY')
        AND task_type IN ('EVALUATE','SIMPLIFY','SOLVE_EQUATION','SOLVE_INEQUALITY'))
    OR (schema_version='canonical-problem-v2' AND verifier_schema_version='canonical-problem-v2'
        AND (problem_type,task_type) IN (('DERIVATIVE','DIFFERENTIATE'),('INTEGRAL','INTEGRATE'),('LIMIT','FIND_LIMIT'))
        AND canonical_problem_jsonb->>'schemaVersion'='canonical-problem-v2')
) IS TRUE);
ALTER TABLE solution_candidates DROP CONSTRAINT solution_candidates_schema_version_check;
ALTER TABLE solution_candidates DROP CONSTRAINT solution_candidates_artifact_jsonb_check;
ALTER TABLE solution_candidates ADD CONSTRAINT ck_candidate_versioned_contract CHECK (
    (schema_version IN ('solution-candidate-v1','solution-candidate-v2')
    AND jsonb_typeof(artifact_jsonb)='object' AND artifact_jsonb->>'schemaVersion'=schema_version
    AND NOT (artifact_jsonb ? 'verified')) IS TRUE
);
ALTER TABLE secondary_solution_evidence DROP CONSTRAINT secondary_solution_evidence_schema_version_check;
ALTER TABLE secondary_solution_evidence DROP CONSTRAINT secondary_solution_evidence_artifact_jsonb_check;
ALTER TABLE secondary_solution_evidence ADD CONSTRAINT ck_secondary_candidate_versioned_contract CHECK (
    (schema_version IN ('solution-candidate-v1','solution-candidate-v2')
    AND jsonb_typeof(artifact_jsonb)='object' AND artifact_jsonb->>'schemaVersion'=schema_version
    AND octet_length(artifact_jsonb::text)<=CASE WHEN schema_version='solution-candidate-v1' THEN 131072 ELSE 65536 END
    AND NOT (artifact_jsonb ? 'verified')) IS TRUE
);
ALTER TABLE solve_jobs DROP CONSTRAINT solve_jobs_prompt_id_check;
ALTER TABLE solve_jobs DROP CONSTRAINT solve_jobs_prompt_version_check;
ALTER TABLE solve_jobs DROP CONSTRAINT solve_jobs_schema_version_check;
ALTER TABLE solve_jobs DROP CONSTRAINT solve_jobs_route_policy_version_check;
ALTER TABLE solve_jobs ADD CONSTRAINT ck_solve_versioned_route CHECK (
    (schema_version='solution-candidate-v1' AND prompt_id='solver' AND prompt_version='v001' AND route_policy_version='solve-route-v1')
    OR (schema_version='solution-candidate-v2' AND prompt_id='owner-calculus-fixture' AND prompt_version='v001'
        AND route_policy_version='calculus-fixture-v1')
);
