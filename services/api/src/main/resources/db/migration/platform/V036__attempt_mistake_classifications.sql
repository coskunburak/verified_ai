-- Append-only derived evidence; no backfill and no mastery side effects.
ALTER TABLE attempt_evaluations ADD CONSTRAINT uq_evaluation_classification_parent UNIQUE(id,user_id,attempt_id);
CREATE TABLE attempt_mistake_classifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    problem_session_id UUID NOT NULL,
    attempt_id UUID NOT NULL,
    evaluation_id UUID NOT NULL,
    policy_version VARCHAR(64) NOT NULL CHECK(policy_version='evidence-classifier-v1'),
    create_key VARCHAR(128) NOT NULL CHECK(create_key ~ '^[A-Za-z0-9_-]{8,128}$'),
    created_at TIMESTAMPTZ NOT NULL,
    artifact JSONB NOT NULL,
    training_eligible BOOLEAN NOT NULL DEFAULT FALSE CHECK(NOT training_eligible),
    UNIQUE(evaluation_id,policy_version),
    UNIQUE(user_id,problem_session_id,create_key),
    FOREIGN KEY(evaluation_id,user_id,attempt_id) REFERENCES attempt_evaluations(id,user_id,attempt_id) ON DELETE CASCADE,
    CHECK(jsonb_typeof(artifact)='object' AND octet_length(artifact::text)<=8192
        AND artifact->>'schemaVersion' IS NOT DISTINCT FROM 'attempt-mistake-classification-v1'
        AND artifact->>'id' IS NOT DISTINCT FROM id::text
        AND artifact->>'ownerId' IS NOT DISTINCT FROM user_id::text
        AND artifact->>'problemSessionId' IS NOT DISTINCT FROM problem_session_id::text
        AND artifact->>'attemptId' IS NOT DISTINCT FROM attempt_id::text
        AND artifact->>'evaluationId' IS NOT DISTINCT FROM evaluation_id::text
        AND artifact->>'policyVersion' IS NOT DISTINCT FROM policy_version
        AND artifact->>'taxonomyVersion' IS NOT DISTINCT FROM 'attempt-taxonomy-v2'
        AND artifact->>'categoryVersion' IS NOT DISTINCT FROM 'mistake-category-v1'
        AND artifact->>'verifierVersion' IS NOT DISTINCT FROM 'bounded-attempt-v2'
        AND artifact->>'trainingEligible' IS NOT DISTINCT FROM 'false'
        AND jsonb_typeof(artifact->'decision') IS NOT DISTINCT FROM 'object'
        AND artifact ?& ARRAY['schemaVersion','id','ownerId','problemSessionId','attemptId','evaluationId','executionId',
            'source','workRevision','taxonomyVersion','categoryVersion','policyVersion','verifierVersion','firstWrongStepId',
            'firstWrongStepIndex','supportingEvidenceId','decision','createdAt','trainingEligible']
        AND artifact - ARRAY['schemaVersion','id','ownerId','problemSessionId','attemptId','evaluationId','executionId',
            'source','workRevision','taxonomyVersion','categoryVersion','policyVersion','verifierVersion','firstWrongStepId',
            'firstWrongStepIndex','supportingEvidenceId','decision','createdAt','trainingEligible'] = '{}'::jsonb
        AND (artifact->'decision') ?& ARRAY['status','code','category','authority','evidenceStrength','reviewStatus','skillId','explanationKey']
        AND (artifact->'decision') - ARRAY['status','code','category','authority','evidenceStrength','reviewStatus','skillId','explanationKey'] = '{}'::jsonb
        AND artifact#>>'{decision,authority}' IS NOT DISTINCT FROM 'DETERMINISTIC_EVIDENCE'
        AND artifact->>'createdAt' IS NOT NULL AND (artifact->>'createdAt')::timestamptz=created_at)
);
CREATE INDEX ix_attempt_classification_history ON attempt_mistake_classifications(user_id,attempt_id,created_at DESC);
CREATE FUNCTION guard_attempt_mistake_classification() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE evidence JSONB; step JSONB; step_index INTEGER;
BEGIN
    IF TG_OP='DELETE' THEN
        IF EXISTS(SELECT 1 FROM attempt_evaluations WHERE id=OLD.evaluation_id) THEN
            RAISE EXCEPTION 'Classification belongs to accepted evaluation' USING ERRCODE='23514';
        END IF;
        RETURN OLD;
    END IF;
    IF TG_OP='UPDATE' THEN
        RAISE EXCEPTION 'Immutable classification evidence' USING ERRCODE='23514';
    END IF;
    SELECT artifact INTO evidence FROM attempt_evaluations
        WHERE id=NEW.evaluation_id AND user_id=NEW.user_id AND attempt_id=NEW.attempt_id;
    IF evidence IS NULL OR evidence->>'problemSessionId' IS DISTINCT FROM NEW.problem_session_id::text
        OR evidence->'source' IS DISTINCT FROM NEW.artifact->'source'
        OR evidence->'workRevision' IS DISTINCT FROM NEW.artifact->'workRevision'
        OR evidence->'executionId' IS DISTINCT FROM NEW.artifact->'executionId' THEN
        RAISE EXCEPTION 'Invalid classification lineage' USING ERRCODE='23514';
    END IF;
    IF NEW.artifact#>>'{decision,evidenceStrength}'='STRUCTURAL_AND_DISPROOF' THEN
        IF NEW.artifact#>>'{decision,status}' IS DISTINCT FROM 'SIGNATURE_SUPPORTED'
            OR NEW.artifact#>>'{decision,reviewStatus}' IS DISTINCT FROM 'AUTO_DETECTED'
            OR NEW.artifact#>>'{decision,category}' IS NULL
            OR NEW.artifact#>>'{decision,category}' IS DISTINCT FROM
                ('{"ALG_SIGN_FLIP_DISTRIBUTE":"SIGN_ERROR","ALG_FRESHMAN_DREAM_POWER":"ALGEBRA_ERROR","ALG_FRACTION_ADD_DENOM":"ALGEBRA_ERROR","ALG_ROOT_LOST_BY_DIVISION":"ALGEBRA_ERROR","DOM_EXTRANEOUS_ROOT_DENOM":"CONCEPT_ERROR","DOM_EVEN_ROOT_NEGATIVE":"CONCEPT_ERROR","DOM_LOG_NON_POSITIVE":"CONCEPT_ERROR","INEQ_NEGATIVE_MULT_NO_FLIP":"SIGN_ERROR","INEQ_CROSS_MULT_VARIABLE":"ALGEBRA_ERROR","ABS_SINGLE_BRANCH_ONLY":"ALGEBRA_ERROR","FUNC_COMPOSITION_ORDER":"CONCEPT_ERROR","TRIG_PERIOD_OMITTED":"CONCEPT_ERROR","CALC_LIMIT_INDETERMINATE_SUB":"CONCEPT_ERROR","CALC_CHAIN_RULE_OMITTED":"FORMULA_ERROR","CALC_PRODUCT_QUOTIENT_NAIVE":"FORMULA_ERROR","CALC_INTEGRAL_BOUNDS_SWAPPED":"SIGN_ERROR","WORD_VARIABLE_MISASSIGNMENT":"INTERPRETATION_ERROR"}'::jsonb ->> (NEW.artifact#>>'{decision,code}'))
            OR (NEW.artifact#>>'{decision,skillId}' IS NOT NULL AND NEW.artifact#>>'{decision,skillId}' IS DISTINCT FROM
                ('{"ALG_SIGN_FLIP_DISTRIBUTE":"MATH.ALGEBRA.SIMPLIFY_EXPRESSIONS","ALG_FRACTION_ADD_DENOM":"MATH.ARITHMETIC.FRACTIONS","INEQ_NEGATIVE_MULT_NO_FLIP":"MATH.EQUATIONS.INEQUALITIES_BASIC","FUNC_COMPOSITION_ORDER":"MATH.FUNCTIONS.COMPOSITION","CALC_CHAIN_RULE_OMITTED":"MATH.CALCULUS.DIFFERENTIATION.CHAIN_RULE"}'::jsonb ->> (NEW.artifact#>>'{decision,code}')))
            OR NEW.artifact#>>'{decision,explanationKey}' IS DISTINCT FROM ('attempt.mistake.' || (NEW.artifact#>>'{decision,code}')) THEN
            RAISE EXCEPTION 'Invalid structural classification' USING ERRCODE='23514';
        END IF;
    ELSIF NEW.artifact#>>'{decision,evidenceStrength}'='EXACT_DISPROOF' THEN
        IF NEW.artifact#>>'{decision,status}' IS DISTINCT FROM 'INVALID_TRANSFORMATION'
            OR NEW.artifact#>>'{decision,code}' IS NULL
            OR NEW.artifact#>>'{decision,code}' NOT IN ('INVALID_TRANSFORMATION','ALG_TRANSFORMATION_INVALID')
            OR NEW.artifact#>>'{decision,category}' IS NOT NULL OR NEW.artifact#>>'{decision,skillId}' IS NOT NULL
            OR NEW.artifact#>>'{decision,reviewStatus}' IS DISTINCT FROM 'REQUIRED'
            OR NEW.artifact#>>'{decision,explanationKey}' IS DISTINCT FROM 'attempt.classification.INVALID_TRANSFORMATION' THEN
            RAISE EXCEPTION 'Invalid generic classification' USING ERRCODE='23514';
        END IF;
    ELSE
        IF NEW.artifact#>>'{decision,status}' IS NULL OR NEW.artifact#>>'{decision,status}' NOT IN
                ('UNCERTAIN','UNSUPPORTED','SOURCE_REVIEW_REQUIRED','INCOMPLETE_WORK','NO_ERROR_ESTABLISHED')
            OR NEW.artifact#>>'{decision,reviewStatus}' IS DISTINCT FROM 'REQUIRED'
            OR NEW.artifact#>>'{decision,skillId}' IS NOT NULL
            OR NEW.artifact#>>'{decision,explanationKey}' IS DISTINCT FROM ('attempt.classification.' || (NEW.artifact#>>'{decision,status}')) THEN
            RAISE EXCEPTION 'Invalid review classification' USING ERRCODE='23514';
        END IF;
    END IF;
    step_index=(NEW.artifact->>'firstWrongStepIndex')::integer;
    IF NEW.artifact#>>'{decision,evidenceStrength}' IN ('STRUCTURAL_AND_DISPROOF','EXACT_DISPROOF') THEN
        step=evidence#>ARRAY['analysis','steps',(step_index-1)::text];
        IF step_index IS NULL OR step_index NOT BETWEEN 1 AND 12
            OR evidence#>'{analysis,firstWrongStepIndex}' IS DISTINCT FROM NEW.artifact->'firstWrongStepIndex'
            OR evidence->'stepIds'->(step_index-1) IS DISTINCT FROM NEW.artifact->'firstWrongStepId'
            OR step->>'verdict' IS DISTINCT FROM 'FIRST_WRONG_STEP'
            OR step#>'{diagnosis,evidenceId}' IS DISTINCT FROM NEW.artifact->'supportingEvidenceId'
            OR (step->'mistakeCode' IS DISTINCT FROM NEW.artifact#>'{decision,code}' AND NOT (
                NEW.artifact#>>'{decision,status}'='INVALID_TRANSFORMATION'
                AND NEW.artifact#>>'{decision,code}'='INVALID_TRANSFORMATION'
                AND step#>>'{diagnosis,classifierPolicyVersion}' IS NULL))
            OR (NEW.artifact#>>'{decision,status}'='SIGNATURE_SUPPORTED'
                AND step#>>'{diagnosis,classifierPolicyVersion}' IS DISTINCT FROM 'structural-signatures-v1') THEN
            RAISE EXCEPTION 'Missing causal disproof' USING ERRCODE='23514';
        END IF;
    ELSE
        IF evidence#>>'{analysis,firstWrongStepIndex}' IS NOT NULL
            OR NEW.artifact#>>'{decision,evidenceStrength}' IS DISTINCT FROM 'NONE'
            OR step_index IS NOT NULL OR NEW.artifact->>'firstWrongStepId' IS NOT NULL
            OR NEW.artifact->>'supportingEvidenceId' IS NOT NULL OR NEW.artifact#>>'{decision,code}' IS NOT NULL
            OR NEW.artifact#>>'{decision,category}' IS NOT NULL THEN
            RAISE EXCEPTION 'Unproven classification' USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_attempt_mistake_classification BEFORE INSERT OR UPDATE OR DELETE ON attempt_mistake_classifications
    FOR EACH ROW EXECUTE FUNCTION guard_attempt_mistake_classification();
