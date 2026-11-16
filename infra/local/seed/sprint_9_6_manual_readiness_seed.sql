-- ============================================================================
-- Sprint 9.6 Local Manual Testing Seed (Synthesized Non-PII Test Telemetry)
-- Pass -v manual_user_id='<UUID>' to psql.
-- Seeds a sample active TYT target, BKT-lite mastery signals, and active
-- Mistake DNA trap profiles so Screen 9 renders ESTABLISHED readiness,
-- active trap net losses, and prioritized ROI topic allocations against
-- the live Spring Boot backend.
-- ============================================================================

INSERT INTO users (id, status, created_at, updated_at)
VALUES (:'manual_user_id'::uuid, 'ACTIVE', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO student_exam_targets (
    user_id,
    exam_definition_id,
    exam_code,
    exam_version,
    blueprint_hash,
    target_score,
    target_date,
    priority_rank,
    target_status,
    is_primary,
    idempotency_key,
    snapshot_payload_json,
    llm_tokens_used,
    training_eligible,
    created_at,
    updated_at
)
SELECT
    :'manual_user_id'::uuid,
    ed.id,
    ed.exam_code,
    ed.exam_version,
    ed.blueprint_hash,
    33.50,
    (CURRENT_DATE + INTERVAL '180 days')::date,
    1,
    'ACTIVE',
    TRUE,
    'manual-sprint96-tyt-target',
    '{}'::jsonb,
    0,
    FALSE,
    NOW(),
    NOW()
FROM exam_definitions ed
WHERE ed.id = '91000000-0000-4000-8000-000000000101'::uuid
  AND NOT EXISTS (
      SELECT 1
      FROM student_exam_targets t
      WHERE t.user_id = :'manual_user_id'::uuid
        AND t.target_status = 'ACTIVE'
  )
ON CONFLICT (user_id, idempotency_key) DO NOTHING;

INSERT INTO learner_skill_mastery (
    user_id, skill_id, mastery_version, score, half_life_days,
    evidence_count, confidence, last_assessed_at, training_eligible
)
VALUES
    (:'manual_user_id'::uuid, 'MATH.ARITHMETIC.INTEGER_OPERATIONS', 'bkt-lite-v1', 0.84, 24.0, 18, 0.72, NOW() - INTERVAL '2 days', FALSE),
    (:'manual_user_id'::uuid, 'MATH.ARITHMETIC.FRACTIONS',          'bkt-lite-v1', 0.71, 16.0, 14, 0.64, NOW() - INTERVAL '4 days', FALSE),
    (:'manual_user_id'::uuid, 'MATH.ARITHMETIC.PERCENTAGES',        'bkt-lite-v1', 0.66, 14.0, 12, 0.58, NOW() - INTERVAL '6 days', FALSE),
    (:'manual_user_id'::uuid, 'MATH.ARITHMETIC.ORDER_OF_OPERATIONS','bkt-lite-v1', 0.88, 28.0, 20, 0.78, NOW() - INTERVAL '1 day',  FALSE),
    (:'manual_user_id'::uuid, 'MATH.ALGEBRA.SIMPLIFY_EXPRESSIONS',  'bkt-lite-v1', 0.62, 12.0, 11, 0.55, NOW() - INTERVAL '5 days', FALSE),
    (:'manual_user_id'::uuid, 'MATH.ALGEBRA.FACTORING',             'bkt-lite-v1', 0.59, 11.0,  9, 0.51, NOW() - INTERVAL '7 days', FALSE),
    (:'manual_user_id'::uuid, 'MATH.ALGEBRA.EXPONENT_RULES',        'bkt-lite-v1', 0.79, 20.0, 15, 0.68, NOW() - INTERVAL '3 days', FALSE),
    (:'manual_user_id'::uuid, 'MATH.ALGEBRA.RADICALS',              'bkt-lite-v1', 0.68, 15.0, 10, 0.56, NOW() - INTERVAL '4 days', FALSE),
    (:'manual_user_id'::uuid, 'MATH.EQUATIONS.LINEAR_ONE_VARIABLE', 'bkt-lite-v1', 0.86, 26.0, 16, 0.74, NOW() - INTERVAL '2 days', FALSE),
    (:'manual_user_id'::uuid, 'MATH.EQUATIONS.LINEAR_SYSTEMS',      'bkt-lite-v1', 0.72, 16.0, 11, 0.60, NOW() - INTERVAL '5 days', FALSE),
    (:'manual_user_id'::uuid, 'MATH.EQUATIONS.INEQUALITIES_BASIC',  'bkt-lite-v1', 0.54, 10.0,  9, 0.49, NOW() - INTERVAL '8 days', FALSE),
    (:'manual_user_id'::uuid, 'MATH.FUNCTIONS.NOTATION',            'bkt-lite-v1', 0.64, 13.0,  8, 0.52, NOW() - INTERVAL '6 days', FALSE),
    (:'manual_user_id'::uuid, 'MATH.FUNCTIONS.GRAPH_FEATURES',      'bkt-lite-v1', 0.57, 11.0,  7, 0.46, NOW() - INTERVAL '9 days', FALSE),
    (:'manual_user_id'::uuid, 'MATH.EQUATIONS.QUADRATIC_SOLVING',   'bkt-lite-v1', 0.61, 14.0, 10, 0.54, NOW() - INTERVAL '4 days', FALSE),
    (:'manual_user_id'::uuid, 'MATH.FUNCTIONS.COMPOSITION',         'bkt-lite-v1', 0.52, 10.0,  8, 0.48, NOW() - INTERVAL '7 days', FALSE),
    (:'manual_user_id'::uuid, 'MATH.CALCULUS.DIFFERENTIATION.CHAIN_RULE', 'bkt-lite-v1', 0.49, 9.0, 7, 0.45, NOW() - INTERVAL '10 days', FALSE)
ON CONFLICT (user_id, skill_id) DO UPDATE SET
    score = EXCLUDED.score,
    half_life_days = EXCLUDED.half_life_days,
    evidence_count = EXCLUDED.evidence_count,
    confidence = EXCLUDED.confidence,
    last_assessed_at = EXCLUDED.last_assessed_at;

INSERT INTO learner_mistake_dna_profiles (
    user_id, mistake_code, skill_id, taxonomy_version, category_version,
    policy_version, decayed_error_count, decayed_exposure_count,
    smoothed_propensity, recent_error_count, recurrence_state,
    last_occurred_at, as_of, training_eligible
)
VALUES
    (:'manual_user_id'::uuid, 'ALG_SIGN_FLIP_DISTRIBUTE',   'MATH.ALGEBRA.SIMPLIFY_EXPRESSIONS',        'attempt-taxonomy-v2', 'mistake-category-v1', 'mistake-dna-v1', 4.50, 11.50, 0.39000000, 4, 'RECURRING_PATTERN', NOW() - INTERVAL '2 days', NOW(), FALSE),
    (:'manual_user_id'::uuid, 'ALG_FRACTION_ADD_DENOM',     'MATH.ARITHMETIC.FRACTIONS',                'attempt-taxonomy-v2', 'mistake-category-v1', 'mistake-dna-v1', 3.20, 10.00, 0.32000000, 3, 'IN_REMEDIATION',    NOW() - INTERVAL '3 days', NOW(), FALSE),
    (:'manual_user_id'::uuid, 'INEQ_NEGATIVE_MULT_NO_FLIP', 'MATH.EQUATIONS.INEQUALITIES_BASIC',        'attempt-taxonomy-v2', 'mistake-category-v1', 'mistake-dna-v1', 3.80,  9.50, 0.40000000, 3, 'RECURRING_PATTERN', NOW() - INTERVAL '4 days', NOW(), FALSE),
    (:'manual_user_id'::uuid, 'FUNC_COMPOSITION_ORDER',     'MATH.FUNCTIONS.COMPOSITION',               'attempt-taxonomy-v2', 'mistake-category-v1', 'mistake-dna-v1', 2.90,  8.50, 0.34000000, 3, 'RECURRING_PATTERN', NOW() - INTERVAL '5 days', NOW(), FALSE),
    (:'manual_user_id'::uuid, 'CALC_CHAIN_RULE_OMITTED',    'MATH.CALCULUS.DIFFERENTIATION.CHAIN_RULE', 'attempt-taxonomy-v2', 'mistake-category-v1', 'mistake-dna-v1', 3.60,  8.00, 0.45000000, 4, 'RECURRING_PATTERN', NOW() - INTERVAL '6 days', NOW(), FALSE)
ON CONFLICT (user_id, mistake_code, skill_id, policy_version) DO UPDATE SET
    decayed_error_count = EXCLUDED.decayed_error_count,
    decayed_exposure_count = EXCLUDED.decayed_exposure_count,
    smoothed_propensity = EXCLUDED.smoothed_propensity,
    recent_error_count = EXCLUDED.recent_error_count,
    recurrence_state = EXCLUDED.recurrence_state,
    last_occurred_at = EXCLUDED.last_occurred_at,
    as_of = EXCLUDED.as_of;
