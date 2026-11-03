-- Additive canonical envelope for real-set syntax. No historic canonical or verification row is rewritten.
ALTER TABLE canonical_problems DROP CONSTRAINT ck_canonical_problem_versioned_kind;
ALTER TABLE canonical_problems ADD CONSTRAINT ck_canonical_problem_versioned_kind CHECK ((
    (schema_version='canonical-problem-v1' AND verifier_schema_version='verifier-input-v1'
        AND problem_type IN ('ARITHMETIC_EXPRESSION','ALGEBRAIC_EXPRESSION','EQUATION','INEQUALITY')
        AND task_type IN ('EVALUATE','SIMPLIFY','SOLVE_EQUATION','SOLVE_INEQUALITY'))
    OR (schema_version='canonical-problem-v2' AND verifier_schema_version='canonical-problem-v2'
        AND (problem_type,task_type) IN (('DERIVATIVE','DIFFERENTIATE'),('INTEGRAL','INTEGRATE'),('LIMIT','FIND_LIMIT'))
        AND canonical_problem_jsonb->>'schemaVersion'='canonical-problem-v2')
    OR (schema_version='canonical-problem-v3' AND verifier_schema_version='canonical-problem-v3'
        AND problem_type IN ('ARITHMETIC_EXPRESSION','ALGEBRAIC_EXPRESSION','EQUATION','INEQUALITY')
        AND task_type IN ('EVALUATE','SIMPLIFY','SOLVE_EQUATION','SOLVE_INEQUALITY')
        AND canonical_problem_jsonb->>'schemaVersion'='canonical-problem-v3')
) IS TRUE);
