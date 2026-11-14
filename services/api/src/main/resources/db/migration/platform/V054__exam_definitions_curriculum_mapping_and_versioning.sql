-- ============================================================================
-- V054: Sprint 9.1 Exam Definitions, Curriculum Mapping, and Versioning
-- Phase 09 / ADR-011 | ADR-006 training_eligible=FALSE, llm_tokens_used=0
-- Canonical Active Curriculum Taxonomy Alignment: StaticCurriculumTaxonomyCatalog
-- ============================================================================

CREATE TABLE IF NOT EXISTS exam_definitions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    exam_code               VARCHAR(64) NOT NULL,
    exam_version            VARCHAR(32) NOT NULL,
    jurisdiction            VARCHAR(16) NOT NULL,
    exam_family             VARCHAR(32) NOT NULL,
    title_en                VARCHAR(160) NOT NULL,
    title_tr                VARCHAR(160) NOT NULL,
    status                  VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    total_questions         INTEGER NOT NULL,
    duration_minutes        INTEGER NOT NULL,
    scoring_formula_type    VARCHAR(48) NOT NULL,
    wrong_penalty_factor    NUMERIC(6,4) NOT NULL DEFAULT 0.0000,
    min_score               NUMERIC(8,2) NOT NULL,
    max_score               NUMERIC(8,2) NOT NULL,
    navigation_policy       VARCHAR(32) NOT NULL DEFAULT 'FREE_JUMP',
    blueprint_hash          CHAR(64) NOT NULL,
    effective_from          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deprecated_at           TIMESTAMPTZ NULL,
    superseded_by_version   VARCHAR(32) NULL,
    sections_json           JSONB NOT NULL DEFAULT '[]'::jsonb,
    scoring_parameters_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    training_eligible       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_exam_definitions_code_version UNIQUE (exam_code, exam_version),
    CONSTRAINT chk_exam_definitions_jurisdiction CHECK (jurisdiction IN ('TR', 'INTL')),
    CONSTRAINT chk_exam_definitions_family CHECK (exam_family IN ('YKS', 'LGS', 'SAT', 'AP', 'IB')),
    CONSTRAINT chk_exam_definitions_status CHECK (status IN ('DRAFT', 'ACTIVE', 'DEPRECATED', 'ARCHIVED')),
    CONSTRAINT chk_exam_definitions_total_questions CHECK (total_questions > 0),
    CONSTRAINT chk_exam_definitions_duration CHECK (duration_minutes > 0),
    CONSTRAINT chk_exam_definitions_scoring_formula CHECK (
        scoring_formula_type IN ('CORRECT_MINUS_QUARTER_WRONG', 'RAW_CORRECT_ONLY', 'WEIGHTED_SECTION_RUBRIC')
    ),
    CONSTRAINT chk_exam_definitions_wrong_penalty CHECK (wrong_penalty_factor >= 0 AND wrong_penalty_factor <= 1),
    CONSTRAINT chk_exam_definitions_score_bounds CHECK (max_score > min_score),
    CONSTRAINT chk_exam_definitions_navigation CHECK (navigation_policy IN ('FREE_JUMP', 'SECTION_LOCKED', 'LINEAR_ONLY')),
    CONSTRAINT chk_exam_definitions_hash_len CHECK (char_length(blueprint_hash) = 64),
    CONSTRAINT chk_exam_definitions_training_false CHECK (training_eligible = FALSE)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_exam_definitions_single_active
    ON exam_definitions (exam_code)
    WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_exam_definitions_status_jurisdiction
    ON exam_definitions (status, jurisdiction, exam_family, exam_code);

CREATE TABLE IF NOT EXISTS exam_topic_weights (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    exam_definition_id       UUID NOT NULL REFERENCES exam_definitions(id) ON DELETE CASCADE,
    section_code             VARCHAR(48) NOT NULL,
    skill_id                 VARCHAR(128) NOT NULL,
    domain_code              VARCHAR(64) NOT NULL,
    question_count_weight    NUMERIC(7,4) NOT NULL,
    min_questions            INTEGER NOT NULL,
    max_questions            INTEGER NOT NULL,
    cognitive_level_mix_json JSONB NOT NULL DEFAULT '{"recall":0.25,"application":0.5,"analysis":0.25}'::jsonb,
    sort_order               INTEGER NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_exam_topic_weights_def_section_skill UNIQUE (exam_definition_id, section_code, skill_id),
    CONSTRAINT chk_exam_topic_weights_weight_non_negative CHECK (question_count_weight >= 0),
    CONSTRAINT chk_exam_topic_weights_min_non_negative CHECK (min_questions >= 0),
    CONSTRAINT chk_exam_topic_weights_max_ge_min CHECK (max_questions >= min_questions),
    CONSTRAINT chk_exam_topic_weights_sort_non_negative CHECK (sort_order >= 0)
);

CREATE INDEX IF NOT EXISTS idx_exam_topic_weights_def_sort
    ON exam_topic_weights (exam_definition_id, sort_order, section_code, skill_id);

CREATE INDEX IF NOT EXISTS idx_exam_topic_weights_skill
    ON exam_topic_weights (skill_id);

CREATE TABLE IF NOT EXISTS student_exam_targets (
    target_id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    exam_definition_id    UUID NOT NULL REFERENCES exam_definitions(id),
    exam_code             VARCHAR(64) NOT NULL,
    exam_version          VARCHAR(32) NOT NULL,
    blueprint_hash        CHAR(64) NOT NULL,
    target_score          NUMERIC(8,2) NOT NULL,
    target_date           DATE NOT NULL,
    priority_rank         INTEGER NOT NULL DEFAULT 1,
    target_status         VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    is_primary            BOOLEAN NOT NULL DEFAULT TRUE,
    idempotency_key       VARCHAR(128) NOT NULL,
    snapshot_payload_json JSONB NOT NULL,
    superseded_at         TIMESTAMPTZ NULL,
    llm_tokens_used       INTEGER NOT NULL DEFAULT 0,
    training_eligible     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_student_exam_targets_user_idem UNIQUE (user_id, idempotency_key),
    CONSTRAINT chk_student_exam_targets_priority CHECK (priority_rank BETWEEN 1 AND 5),
    CONSTRAINT chk_student_exam_targets_status CHECK (target_status IN ('ACTIVE', 'SUPERSEDED', 'ACHIEVED', 'ARCHIVED')),
    CONSTRAINT chk_student_exam_targets_hash_len CHECK (char_length(blueprint_hash) = 64),
    CONSTRAINT chk_student_exam_targets_zero_tokens CHECK (llm_tokens_used = 0),
    CONSTRAINT chk_student_exam_targets_training_false CHECK (training_eligible = FALSE)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_student_exam_targets_single_active_primary
    ON student_exam_targets (user_id)
    WHERE target_status = 'ACTIVE' AND is_primary = TRUE;

CREATE INDEX IF NOT EXISTS idx_student_exam_targets_user_status
    ON student_exam_targets (user_id, target_status, created_at DESC);

-- ============================================================================
-- Canonical Seed Exam Definitions (Version 2026.1 ACTIVE + 2025.2 DEPRECATED)
-- ============================================================================

INSERT INTO exam_definitions (
    id, exam_code, exam_version, jurisdiction, exam_family,
    title_en, title_tr, status, total_questions, duration_minutes,
    scoring_formula_type, wrong_penalty_factor, min_score, max_score, navigation_policy,
    blueprint_hash, effective_from, deprecated_at, superseded_by_version,
    sections_json, scoring_parameters_json, training_eligible
) VALUES
(
    '91000000-0000-4000-8000-000000000101',
    'TR_YKS_TYT_MATH',
    '2026.1',
    'TR',
    'YKS',
    'YKS Basic Proficiency Test (TYT) Mathematics',
    'YKS Temel Yeterlilik Testi (TYT) Matematik',
    'ACTIVE',
    40,
    65,
    'CORRECT_MINUS_QUARTER_WRONG',
    0.2500,
    -10.00,
    40.00,
    'FREE_JUMP',
    'c1cb8c26245fdee1b53c164a94e35a6cecbde8bae6ece948e60f577df53e1391',
    TIMESTAMPTZ '2026-01-01T00:00:00Z',
    NULL,
    NULL,
    '[{"sectionCode":"TYT_MATH","titleEn":"TYT Mathematics","titleTr":"TYT Matematik","questionCount":40,"durationMinutes":65,"order":1}]'::jsonb,
    '{"scaleMin":null,"scaleMax":null,"moduleCount":1,"rubricScaleMin":null,"rubricScaleMax":null,"sectionWeights":[]}'::jsonb,
    FALSE
),
(
    '91000000-0000-4000-8000-000000000102',
    'TR_YKS_TYT_MATH',
    '2025.2',
    'TR',
    'YKS',
    'YKS Basic Proficiency Test (TYT) Mathematics (2025 Archive)',
    'YKS Temel Yeterlilik Testi (TYT) Matematik (2025 Arşiv)',
    'DEPRECATED',
    40,
    65,
    'CORRECT_MINUS_QUARTER_WRONG',
    0.2500,
    -10.00,
    40.00,
    'FREE_JUMP',
    '9b015acfc42ee2f12fdfe158741debcb8336e4cc0574fb60085a843cc326db48',
    TIMESTAMPTZ '2025-07-01T00:00:00Z',
    TIMESTAMPTZ '2026-01-01T00:00:00Z',
    '2026.1',
    '[{"sectionCode":"TYT_MATH","titleEn":"TYT Mathematics","titleTr":"TYT Matematik","questionCount":40,"durationMinutes":65,"order":1}]'::jsonb,
    '{"scaleMin":null,"scaleMax":null,"moduleCount":1,"rubricScaleMin":null,"rubricScaleMax":null,"sectionWeights":[]}'::jsonb,
    FALSE
),
(
    '91000000-0000-4000-8000-000000000103',
    'TR_YKS_AYT_MATH',
    '2026.1',
    'TR',
    'YKS',
    'YKS Field Qualification Test (AYT) Mathematics',
    'YKS Alan Yeterlilik Testi (AYT) Matematik',
    'ACTIVE',
    40,
    90,
    'CORRECT_MINUS_QUARTER_WRONG',
    0.2500,
    -10.00,
    40.00,
    'FREE_JUMP',
    '3cd5cb6fc93ee2533f544d2f24ce04922674c06a4e81073a410dcd94e4e9ceb8',
    TIMESTAMPTZ '2026-01-01T00:00:00Z',
    NULL,
    NULL,
    '[{"sectionCode":"AYT_MATH","titleEn":"AYT Mathematics","titleTr":"AYT Matematik","questionCount":40,"durationMinutes":90,"order":1}]'::jsonb,
    '{"scaleMin":null,"scaleMax":null,"moduleCount":1,"rubricScaleMin":null,"rubricScaleMax":null,"sectionWeights":[]}'::jsonb,
    FALSE
),
(
    '91000000-0000-4000-8000-000000000104',
    'INTL_SAT_MATH',
    '2026.1',
    'INTL',
    'SAT',
    'Digital SAT Mathematics (Adaptive Modules 1 & 2)',
    'Dijital SAT Matematik (Uyarlanabilir Modül 1 ve 2)',
    'ACTIVE',
    44,
    70,
    'RAW_CORRECT_ONLY',
    0.0000,
    200.00,
    800.00,
    'SECTION_LOCKED',
    '89063bfc449a4000bb05154fceff8c9ebbb81446ae2cafc41c46adfeeaff29dd',
    TIMESTAMPTZ '2026-01-01T00:00:00Z',
    NULL,
    NULL,
    '[{"sectionCode":"SAT_MOD_1","titleEn":"Math Module 1","titleTr":"Matematik Modül 1","questionCount":22,"durationMinutes":35,"order":1},{"sectionCode":"SAT_MOD_2","titleEn":"Math Module 2","titleTr":"Matematik Modül 2","questionCount":22,"durationMinutes":35,"order":2}]'::jsonb,
    '{"scaleMin":200.0,"scaleMax":800.0,"moduleCount":2,"rubricScaleMin":null,"rubricScaleMax":null,"sectionWeights":[]}'::jsonb,
    FALSE
),
(
    '91000000-0000-4000-8000-000000000105',
    'INTL_AP_CALC_BC',
    '2026.1',
    'INTL',
    'AP',
    'AP Calculus BC (Analytical & Calculus MCQ Blueprint)',
    'AP Calculus BC (Analiz ve Kalkülüs Çoktan Seçmeli Planı)',
    'ACTIVE',
    45,
    105,
    'WEIGHTED_SECTION_RUBRIC',
    0.0000,
    1.00,
    5.00,
    'SECTION_LOCKED',
    '67cbd60ba75b1d17ede2833ea86f598ef027859d17f421ec5e7a02a03d66c183',
    TIMESTAMPTZ '2026-01-01T00:00:00Z',
    NULL,
    NULL,
    '[{"sectionCode":"CALC_PART_A","titleEn":"Part A (No Calculator)","titleTr":"Bölüm A (Hesap Makinesiz)","questionCount":30,"durationMinutes":60,"order":1},{"sectionCode":"CALC_PART_B","titleEn":"Part B (Graphing Calculator)","titleTr":"Bölüm B (Grafik Hesap Makineli)","questionCount":15,"durationMinutes":45,"order":2}]'::jsonb,
    '{"scaleMin":null,"scaleMax":null,"moduleCount":2,"rubricScaleMin":1,"rubricScaleMax":5,"sectionWeights":[{"sectionCode":"CALC_PART_A","weight":0.6667},{"sectionCode":"CALC_PART_B","weight":0.3333}]}'::jsonb,
    FALSE
),
(
    '91000000-0000-4000-8000-000000000106',
    'INTL_IB_MATH_AA_HL',
    '2026.1',
    'INTL',
    'IB',
    'IB Mathematics: Analysis and Approaches HL',
    'IB Matematik: Analiz ve Yaklaşımlar HL',
    'ACTIVE',
    30,
    120,
    'WEIGHTED_SECTION_RUBRIC',
    0.0000,
    1.00,
    7.00,
    'SECTION_LOCKED',
    '264ec677ece16a7c7439e8a6ac8e44d1d264b45f3f39b2a0d6374e526ab7f71b',
    TIMESTAMPTZ '2026-01-01T00:00:00Z',
    NULL,
    NULL,
    '[{"sectionCode":"IB_PAPER_1","titleEn":"Paper 1 (Non-Calculator)","titleTr":"Bölüm 1 (Hesap Makinesiz)","questionCount":15,"durationMinutes":60,"order":1},{"sectionCode":"IB_PAPER_2","titleEn":"Paper 2 (Technology Allowed)","titleTr":"Bölüm 2 (Teknoloji İzinli)","questionCount":15,"durationMinutes":60,"order":2}]'::jsonb,
    '{"scaleMin":null,"scaleMax":null,"moduleCount":2,"rubricScaleMin":1,"rubricScaleMax":7,"sectionWeights":[{"sectionCode":"IB_PAPER_1","weight":0.5},{"sectionCode":"IB_PAPER_2","weight":0.5}]}'::jsonb,
    FALSE
)
ON CONFLICT (exam_code, exam_version) DO NOTHING;

-- ============================================================================
-- Seed Topic Weights (Strict Weight Conservation & Active Taxonomy Alignment)
-- ============================================================================

INSERT INTO exam_topic_weights (
    exam_definition_id, section_code, skill_id, domain_code,
    question_count_weight, min_questions, max_questions, cognitive_level_mix_json, sort_order
) VALUES
-- TR_YKS_TYT_MATH @ 2026.1 (sum = 40.0000)
('91000000-0000-4000-8000-000000000101', 'TYT_MATH', 'MATH.ARITHMETIC.INTEGER_OPERATIONS', 'MATH.ARITHMETIC', 5.0000, 4, 7, '{"recall":0.30,"application":0.50,"analysis":0.20}'::jsonb, 1),
('91000000-0000-4000-8000-000000000101', 'TYT_MATH', 'MATH.ARITHMETIC.FRACTIONS', 'MATH.ARITHMETIC', 4.0000, 3, 5, '{"recall":0.25,"application":0.55,"analysis":0.20}'::jsonb, 2),
('91000000-0000-4000-8000-000000000101', 'TYT_MATH', 'MATH.ARITHMETIC.PERCENTAGES', 'MATH.ARITHMETIC', 5.0000, 4, 7, '{"recall":0.20,"application":0.55,"analysis":0.25}'::jsonb, 3),
('91000000-0000-4000-8000-000000000101', 'TYT_MATH', 'MATH.ARITHMETIC.ORDER_OF_OPERATIONS', 'MATH.ARITHMETIC', 3.0000, 2, 4, '{"recall":0.35,"application":0.50,"analysis":0.15}'::jsonb, 4),
('91000000-0000-4000-8000-000000000101', 'TYT_MATH', 'MATH.ALGEBRA.SIMPLIFY_EXPRESSIONS', 'MATH.ALGEBRA', 3.0000, 2, 4, '{"recall":0.25,"application":0.55,"analysis":0.20}'::jsonb, 5),
('91000000-0000-4000-8000-000000000101', 'TYT_MATH', 'MATH.ALGEBRA.FACTORING', 'MATH.ALGEBRA', 3.0000, 2, 4, '{"recall":0.20,"application":0.55,"analysis":0.25}'::jsonb, 6),
('91000000-0000-4000-8000-000000000101', 'TYT_MATH', 'MATH.ALGEBRA.EXPONENT_RULES', 'MATH.ALGEBRA', 3.0000, 2, 4, '{"recall":0.25,"application":0.50,"analysis":0.25}'::jsonb, 7),
('91000000-0000-4000-8000-000000000101', 'TYT_MATH', 'MATH.ALGEBRA.RADICALS', 'MATH.ALGEBRA', 3.0000, 2, 4, '{"recall":0.20,"application":0.55,"analysis":0.25}'::jsonb, 8),
('91000000-0000-4000-8000-000000000101', 'TYT_MATH', 'MATH.EQUATIONS.LINEAR_ONE_VARIABLE', 'MATH.EQUATIONS', 3.0000, 2, 4, '{"recall":0.20,"application":0.55,"analysis":0.25}'::jsonb, 9),
('91000000-0000-4000-8000-000000000101', 'TYT_MATH', 'MATH.EQUATIONS.LINEAR_SYSTEMS', 'MATH.EQUATIONS', 2.0000, 1, 3, '{"recall":0.20,"application":0.50,"analysis":0.30}'::jsonb, 10),
('91000000-0000-4000-8000-000000000101', 'TYT_MATH', 'MATH.EQUATIONS.INEQUALITIES_BASIC', 'MATH.EQUATIONS', 2.0000, 1, 3, '{"recall":0.20,"application":0.50,"analysis":0.30}'::jsonb, 11),
('91000000-0000-4000-8000-000000000101', 'TYT_MATH', 'MATH.FUNCTIONS.NOTATION', 'MATH.FUNCTIONS', 2.0000, 1, 3, '{"recall":0.25,"application":0.50,"analysis":0.25}'::jsonb, 12),
('91000000-0000-4000-8000-000000000101', 'TYT_MATH', 'MATH.FUNCTIONS.GRAPH_FEATURES', 'MATH.FUNCTIONS', 2.0000, 1, 3, '{"recall":0.15,"application":0.50,"analysis":0.35}'::jsonb, 13),

-- TR_YKS_TYT_MATH @ 2025.2 (DEPRECATED historical version, sum = 40.0000)
('91000000-0000-4000-8000-000000000102', 'TYT_MATH', 'MATH.ARITHMETIC.INTEGER_OPERATIONS', 'MATH.ARITHMETIC', 6.0000, 4, 7, '{"recall":0.30,"application":0.50,"analysis":0.20}'::jsonb, 1),
('91000000-0000-4000-8000-000000000102', 'TYT_MATH', 'MATH.ARITHMETIC.FRACTIONS', 'MATH.ARITHMETIC', 4.0000, 3, 5, '{"recall":0.25,"application":0.55,"analysis":0.20}'::jsonb, 2),
('91000000-0000-4000-8000-000000000102', 'TYT_MATH', 'MATH.ARITHMETIC.PERCENTAGES', 'MATH.ARITHMETIC', 4.0000, 3, 6, '{"recall":0.20,"application":0.55,"analysis":0.25}'::jsonb, 3),
('91000000-0000-4000-8000-000000000102', 'TYT_MATH', 'MATH.ARITHMETIC.ORDER_OF_OPERATIONS', 'MATH.ARITHMETIC', 3.0000, 2, 4, '{"recall":0.35,"application":0.50,"analysis":0.15}'::jsonb, 4),
('91000000-0000-4000-8000-000000000102', 'TYT_MATH', 'MATH.ALGEBRA.SIMPLIFY_EXPRESSIONS', 'MATH.ALGEBRA', 3.0000, 2, 4, '{"recall":0.25,"application":0.55,"analysis":0.20}'::jsonb, 5),
('91000000-0000-4000-8000-000000000102', 'TYT_MATH', 'MATH.ALGEBRA.FACTORING', 'MATH.ALGEBRA', 3.0000, 2, 4, '{"recall":0.20,"application":0.55,"analysis":0.25}'::jsonb, 6),
('91000000-0000-4000-8000-000000000102', 'TYT_MATH', 'MATH.ALGEBRA.EXPONENT_RULES', 'MATH.ALGEBRA', 3.0000, 2, 4, '{"recall":0.25,"application":0.50,"analysis":0.25}'::jsonb, 7),
('91000000-0000-4000-8000-000000000102', 'TYT_MATH', 'MATH.ALGEBRA.RADICALS', 'MATH.ALGEBRA', 3.0000, 2, 4, '{"recall":0.20,"application":0.55,"analysis":0.25}'::jsonb, 8),
('91000000-0000-4000-8000-000000000102', 'TYT_MATH', 'MATH.EQUATIONS.LINEAR_ONE_VARIABLE', 'MATH.EQUATIONS', 3.0000, 2, 4, '{"recall":0.20,"application":0.55,"analysis":0.25}'::jsonb, 9),
('91000000-0000-4000-8000-000000000102', 'TYT_MATH', 'MATH.EQUATIONS.LINEAR_SYSTEMS', 'MATH.EQUATIONS', 2.0000, 1, 3, '{"recall":0.20,"application":0.50,"analysis":0.30}'::jsonb, 10),
('91000000-0000-4000-8000-000000000102', 'TYT_MATH', 'MATH.EQUATIONS.INEQUALITIES_BASIC', 'MATH.EQUATIONS', 2.0000, 1, 3, '{"recall":0.20,"application":0.50,"analysis":0.30}'::jsonb, 11),
('91000000-0000-4000-8000-000000000102', 'TYT_MATH', 'MATH.FUNCTIONS.NOTATION', 'MATH.FUNCTIONS', 2.0000, 1, 3, '{"recall":0.25,"application":0.50,"analysis":0.25}'::jsonb, 12),
('91000000-0000-4000-8000-000000000102', 'TYT_MATH', 'MATH.FUNCTIONS.GRAPH_FEATURES', 'MATH.FUNCTIONS', 2.0000, 1, 3, '{"recall":0.15,"application":0.50,"analysis":0.35}'::jsonb, 13),

-- TR_YKS_AYT_MATH @ 2026.1 (sum = 40.0000)
('91000000-0000-4000-8000-000000000103', 'AYT_MATH', 'MATH.ALGEBRA.FACTORING', 'MATH.ALGEBRA', 2.0000, 1, 3, '{"recall":0.15,"application":0.55,"analysis":0.30}'::jsonb, 1),
('91000000-0000-4000-8000-000000000103', 'AYT_MATH', 'MATH.EQUATIONS.QUADRATIC_SOLVING', 'MATH.EQUATIONS', 4.0000, 3, 5, '{"recall":0.15,"application":0.55,"analysis":0.30}'::jsonb, 2),
('91000000-0000-4000-8000-000000000103', 'AYT_MATH', 'MATH.EQUATIONS.INEQUALITIES_BASIC', 'MATH.EQUATIONS', 2.0000, 1, 3, '{"recall":0.15,"application":0.50,"analysis":0.35}'::jsonb, 3),
('91000000-0000-4000-8000-000000000103', 'AYT_MATH', 'MATH.FUNCTIONS.COMPOSITION', 'MATH.FUNCTIONS', 3.0000, 2, 4, '{"recall":0.15,"application":0.50,"analysis":0.35}'::jsonb, 4),
('91000000-0000-4000-8000-000000000103', 'AYT_MATH', 'MATH.FUNCTIONS.INVERSE_BASIC', 'MATH.FUNCTIONS', 3.0000, 2, 4, '{"recall":0.15,"application":0.50,"analysis":0.35}'::jsonb, 5),
('91000000-0000-4000-8000-000000000103', 'AYT_MATH', 'MATH.FUNCTIONS.GRAPH_FEATURES', 'MATH.FUNCTIONS', 2.0000, 1, 3, '{"recall":0.10,"application":0.45,"analysis":0.45}'::jsonb, 6),
('91000000-0000-4000-8000-000000000103', 'AYT_MATH', 'MATH.CALCULUS.LIMITS.DIRECT_SUBSTITUTION', 'MATH.CALCULUS.LIMITS', 2.0000, 1, 3, '{"recall":0.25,"application":0.55,"analysis":0.20}'::jsonb, 7),
('91000000-0000-4000-8000-000000000103', 'AYT_MATH', 'MATH.CALCULUS.LIMITS.FACTOR_AND_CANCEL', 'MATH.CALCULUS.LIMITS', 2.0000, 1, 3, '{"recall":0.15,"application":0.55,"analysis":0.30}'::jsonb, 8),
('91000000-0000-4000-8000-000000000103', 'AYT_MATH', 'MATH.CALCULUS.LIMITS.ONE_SIDED_BASIC', 'MATH.CALCULUS.LIMITS', 2.0000, 1, 3, '{"recall":0.15,"application":0.50,"analysis":0.35}'::jsonb, 9),
('91000000-0000-4000-8000-000000000103', 'AYT_MATH', 'MATH.CALCULUS.DIFFERENTIATION.POWER_RULE', 'MATH.CALCULUS.DIFFERENTIATION', 2.0000, 1, 3, '{"recall":0.25,"application":0.55,"analysis":0.20}'::jsonb, 10),
('91000000-0000-4000-8000-000000000103', 'AYT_MATH', 'MATH.CALCULUS.DIFFERENTIATION.PRODUCT_RULE', 'MATH.CALCULUS.DIFFERENTIATION', 2.0000, 1, 3, '{"recall":0.20,"application":0.55,"analysis":0.25}'::jsonb, 11),
('91000000-0000-4000-8000-000000000103', 'AYT_MATH', 'MATH.CALCULUS.DIFFERENTIATION.QUOTIENT_RULE', 'MATH.CALCULUS.DIFFERENTIATION', 2.0000, 1, 3, '{"recall":0.20,"application":0.50,"analysis":0.30}'::jsonb, 12),
('91000000-0000-4000-8000-000000000103', 'AYT_MATH', 'MATH.CALCULUS.DIFFERENTIATION.CHAIN_RULE', 'MATH.CALCULUS.DIFFERENTIATION', 3.0000, 2, 4, '{"recall":0.15,"application":0.50,"analysis":0.35}'::jsonb, 13),
('91000000-0000-4000-8000-000000000103', 'AYT_MATH', 'MATH.CALCULUS.DIFFERENTIATION.TRIG_BASIC', 'MATH.CALCULUS.DIFFERENTIATION', 2.0000, 1, 3, '{"recall":0.20,"application":0.50,"analysis":0.30}'::jsonb, 14),
('91000000-0000-4000-8000-000000000103', 'AYT_MATH', 'MATH.CALCULUS.INTEGRATION.POWER_RULE', 'MATH.CALCULUS.INTEGRATION', 2.0000, 1, 3, '{"recall":0.25,"application":0.55,"analysis":0.20}'::jsonb, 15),
('91000000-0000-4000-8000-000000000103', 'AYT_MATH', 'MATH.CALCULUS.INTEGRATION.BASIC_SUBSTITUTION', 'MATH.CALCULUS.INTEGRATION', 3.0000, 2, 4, '{"recall":0.15,"application":0.50,"analysis":0.35}'::jsonb, 16),
('91000000-0000-4000-8000-000000000103', 'AYT_MATH', 'MATH.CALCULUS.INTEGRATION.CONSTANT_OF_INTEGRATION', 'MATH.CALCULUS.INTEGRATION', 2.0000, 1, 3, '{"recall":0.20,"application":0.50,"analysis":0.30}'::jsonb, 17),

-- INTL_SAT_MATH @ 2026.1 (sum = 44.0000)
('91000000-0000-4000-8000-000000000104', 'SAT_MOD_1', 'MATH.ARITHMETIC.PERCENTAGES', 'MATH.ARITHMETIC', 4.0000, 3, 5, '{"recall":0.25,"application":0.55,"analysis":0.20}'::jsonb, 1),
('91000000-0000-4000-8000-000000000104', 'SAT_MOD_1', 'MATH.ALGEBRA.SIMPLIFY_EXPRESSIONS', 'MATH.ALGEBRA', 4.0000, 3, 5, '{"recall":0.25,"application":0.55,"analysis":0.20}'::jsonb, 2),
('91000000-0000-4000-8000-000000000104', 'SAT_MOD_1', 'MATH.ALGEBRA.EXPONENT_RULES', 'MATH.ALGEBRA', 3.0000, 2, 4, '{"recall":0.25,"application":0.55,"analysis":0.20}'::jsonb, 3),
('91000000-0000-4000-8000-000000000104', 'SAT_MOD_1', 'MATH.EQUATIONS.LINEAR_ONE_VARIABLE', 'MATH.EQUATIONS', 4.0000, 3, 5, '{"recall":0.20,"application":0.60,"analysis":0.20}'::jsonb, 4),
('91000000-0000-4000-8000-000000000104', 'SAT_MOD_1', 'MATH.EQUATIONS.LINEAR_SYSTEMS', 'MATH.EQUATIONS', 4.0000, 3, 5, '{"recall":0.20,"application":0.55,"analysis":0.25}'::jsonb, 5),
('91000000-0000-4000-8000-000000000104', 'SAT_MOD_1', 'MATH.FUNCTIONS.NOTATION', 'MATH.FUNCTIONS', 3.0000, 2, 4, '{"recall":0.25,"application":0.50,"analysis":0.25}'::jsonb, 6),
('91000000-0000-4000-8000-000000000104', 'SAT_MOD_2', 'MATH.ALGEBRA.FACTORING', 'MATH.ALGEBRA', 4.0000, 3, 5, '{"recall":0.15,"application":0.55,"analysis":0.30}'::jsonb, 7),
('91000000-0000-4000-8000-000000000104', 'SAT_MOD_2', 'MATH.ALGEBRA.RADICALS', 'MATH.ALGEBRA', 3.0000, 2, 4, '{"recall":0.20,"application":0.50,"analysis":0.30}'::jsonb, 8),
('91000000-0000-4000-8000-000000000104', 'SAT_MOD_2', 'MATH.EQUATIONS.QUADRATIC_SOLVING', 'MATH.EQUATIONS', 5.0000, 4, 6, '{"recall":0.15,"application":0.50,"analysis":0.35}'::jsonb, 9),
('91000000-0000-4000-8000-000000000104', 'SAT_MOD_2', 'MATH.EQUATIONS.INEQUALITIES_BASIC', 'MATH.EQUATIONS', 4.0000, 3, 5, '{"recall":0.15,"application":0.50,"analysis":0.35}'::jsonb, 10),
('91000000-0000-4000-8000-000000000104', 'SAT_MOD_2', 'MATH.FUNCTIONS.GRAPH_FEATURES', 'MATH.FUNCTIONS', 4.0000, 3, 5, '{"recall":0.15,"application":0.45,"analysis":0.40}'::jsonb, 11),
('91000000-0000-4000-8000-000000000104', 'SAT_MOD_2', 'MATH.FUNCTIONS.COMPOSITION', 'MATH.FUNCTIONS', 2.0000, 1, 3, '{"recall":0.15,"application":0.45,"analysis":0.40}'::jsonb, 12),

-- INTL_AP_CALC_BC @ 2026.1 (sum = 45.0000)
('91000000-0000-4000-8000-000000000105', 'CALC_PART_A', 'MATH.CALCULUS.LIMITS.DIRECT_SUBSTITUTION', 'MATH.CALCULUS.LIMITS', 3.0000, 2, 4, '{"recall":0.25,"application":0.55,"analysis":0.20}'::jsonb, 1),
('91000000-0000-4000-8000-000000000105', 'CALC_PART_A', 'MATH.CALCULUS.LIMITS.FACTOR_AND_CANCEL', 'MATH.CALCULUS.LIMITS', 3.0000, 2, 4, '{"recall":0.15,"application":0.55,"analysis":0.30}'::jsonb, 2),
('91000000-0000-4000-8000-000000000105', 'CALC_PART_A', 'MATH.CALCULUS.LIMITS.ONE_SIDED_BASIC', 'MATH.CALCULUS.LIMITS', 2.0000, 1, 3, '{"recall":0.15,"application":0.50,"analysis":0.35}'::jsonb, 3),
('91000000-0000-4000-8000-000000000105', 'CALC_PART_A', 'MATH.CALCULUS.DIFFERENTIATION.POWER_RULE', 'MATH.CALCULUS.DIFFERENTIATION', 3.0000, 2, 4, '{"recall":0.25,"application":0.55,"analysis":0.20}'::jsonb, 4),
('91000000-0000-4000-8000-000000000105', 'CALC_PART_A', 'MATH.CALCULUS.DIFFERENTIATION.PRODUCT_RULE', 'MATH.CALCULUS.DIFFERENTIATION', 3.0000, 2, 4, '{"recall":0.20,"application":0.55,"analysis":0.25}'::jsonb, 5),
('91000000-0000-4000-8000-000000000105', 'CALC_PART_A', 'MATH.CALCULUS.DIFFERENTIATION.QUOTIENT_RULE', 'MATH.CALCULUS.DIFFERENTIATION', 3.0000, 2, 4, '{"recall":0.20,"application":0.50,"analysis":0.30}'::jsonb, 6),
('91000000-0000-4000-8000-000000000105', 'CALC_PART_A', 'MATH.CALCULUS.DIFFERENTIATION.CHAIN_RULE', 'MATH.CALCULUS.DIFFERENTIATION', 5.0000, 4, 6, '{"recall":0.15,"application":0.50,"analysis":0.35}'::jsonb, 7),
('91000000-0000-4000-8000-000000000105', 'CALC_PART_A', 'MATH.CALCULUS.DIFFERENTIATION.TRIG_BASIC', 'MATH.CALCULUS.DIFFERENTIATION', 3.0000, 2, 4, '{"recall":0.20,"application":0.50,"analysis":0.30}'::jsonb, 8),
('91000000-0000-4000-8000-000000000105', 'CALC_PART_A', 'MATH.CALCULUS.INTEGRATION.POWER_RULE', 'MATH.CALCULUS.INTEGRATION', 2.0000, 1, 3, '{"recall":0.25,"application":0.55,"analysis":0.20}'::jsonb, 9),
('91000000-0000-4000-8000-000000000105', 'CALC_PART_A', 'MATH.CALCULUS.INTEGRATION.BASIC_SUBSTITUTION', 'MATH.CALCULUS.INTEGRATION', 3.0000, 2, 4, '{"recall":0.15,"application":0.50,"analysis":0.35}'::jsonb, 10),
('91000000-0000-4000-8000-000000000105', 'CALC_PART_B', 'MATH.FUNCTIONS.GRAPH_FEATURES', 'MATH.FUNCTIONS', 3.0000, 2, 4, '{"recall":0.10,"application":0.45,"analysis":0.45}'::jsonb, 11),
('91000000-0000-4000-8000-000000000105', 'CALC_PART_B', 'MATH.CALCULUS.DIFFERENTIATION.CHAIN_RULE', 'MATH.CALCULUS.DIFFERENTIATION', 4.0000, 3, 5, '{"recall":0.15,"application":0.45,"analysis":0.40}'::jsonb, 12),
('91000000-0000-4000-8000-000000000105', 'CALC_PART_B', 'MATH.CALCULUS.INTEGRATION.BASIC_SUBSTITUTION', 'MATH.CALCULUS.INTEGRATION', 5.0000, 4, 6, '{"recall":0.10,"application":0.50,"analysis":0.40}'::jsonb, 13),
('91000000-0000-4000-8000-000000000105', 'CALC_PART_B', 'MATH.CALCULUS.INTEGRATION.CONSTANT_OF_INTEGRATION', 'MATH.CALCULUS.INTEGRATION', 3.0000, 2, 4, '{"recall":0.15,"application":0.50,"analysis":0.35}'::jsonb, 14),

-- INTL_IB_MATH_AA_HL @ 2026.1 (sum = 30.0000)
('91000000-0000-4000-8000-000000000106', 'IB_PAPER_1', 'MATH.ALGEBRA.FACTORING', 'MATH.ALGEBRA', 2.0000, 1, 3, '{"recall":0.15,"application":0.50,"analysis":0.35}'::jsonb, 1),
('91000000-0000-4000-8000-000000000106', 'IB_PAPER_1', 'MATH.EQUATIONS.QUADRATIC_SOLVING', 'MATH.EQUATIONS', 3.0000, 2, 4, '{"recall":0.15,"application":0.50,"analysis":0.35}'::jsonb, 2),
('91000000-0000-4000-8000-000000000106', 'IB_PAPER_1', 'MATH.FUNCTIONS.INVERSE_BASIC', 'MATH.FUNCTIONS', 3.0000, 2, 4, '{"recall":0.15,"application":0.45,"analysis":0.40}'::jsonb, 3),
('91000000-0000-4000-8000-000000000106', 'IB_PAPER_1', 'MATH.CALCULUS.LIMITS.FACTOR_AND_CANCEL', 'MATH.CALCULUS.LIMITS', 2.0000, 1, 3, '{"recall":0.15,"application":0.45,"analysis":0.40}'::jsonb, 4),
('91000000-0000-4000-8000-000000000106', 'IB_PAPER_1', 'MATH.CALCULUS.DIFFERENTIATION.CHAIN_RULE', 'MATH.CALCULUS.DIFFERENTIATION', 3.0000, 2, 4, '{"recall":0.15,"application":0.45,"analysis":0.40}'::jsonb, 5),
('91000000-0000-4000-8000-000000000106', 'IB_PAPER_1', 'MATH.CALCULUS.INTEGRATION.CONSTANT_OF_INTEGRATION', 'MATH.CALCULUS.INTEGRATION', 2.0000, 1, 3, '{"recall":0.15,"application":0.45,"analysis":0.40}'::jsonb, 6),
('91000000-0000-4000-8000-000000000106', 'IB_PAPER_2', 'MATH.FUNCTIONS.COMPOSITION', 'MATH.FUNCTIONS', 3.0000, 2, 4, '{"recall":0.10,"application":0.45,"analysis":0.45}'::jsonb, 7),
('91000000-0000-4000-8000-000000000106', 'IB_PAPER_2', 'MATH.CALCULUS.DIFFERENTIATION.PRODUCT_RULE', 'MATH.CALCULUS.DIFFERENTIATION', 3.0000, 2, 4, '{"recall":0.15,"application":0.45,"analysis":0.40}'::jsonb, 8),
('91000000-0000-4000-8000-000000000106', 'IB_PAPER_2', 'MATH.CALCULUS.DIFFERENTIATION.QUOTIENT_RULE', 'MATH.CALCULUS.DIFFERENTIATION', 3.0000, 2, 4, '{"recall":0.15,"application":0.45,"analysis":0.40}'::jsonb, 9),
('91000000-0000-4000-8000-000000000106', 'IB_PAPER_2', 'MATH.CALCULUS.DIFFERENTIATION.TRIG_BASIC', 'MATH.CALCULUS.DIFFERENTIATION', 3.0000, 2, 4, '{"recall":0.15,"application":0.45,"analysis":0.40}'::jsonb, 10),
('91000000-0000-4000-8000-000000000106', 'IB_PAPER_2', 'MATH.CALCULUS.INTEGRATION.BASIC_SUBSTITUTION', 'MATH.CALCULUS.INTEGRATION', 3.0000, 2, 4, '{"recall":0.10,"application":0.45,"analysis":0.45}'::jsonb, 11)
ON CONFLICT (exam_definition_id, section_code, skill_id) DO NOTHING;
