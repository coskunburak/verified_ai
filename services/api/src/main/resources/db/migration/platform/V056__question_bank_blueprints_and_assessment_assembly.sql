-- ============================================================================
-- V056: Sprint 9.3 Question Bank, Blueprint, and Assessment Assembly Rules
-- Phase 09 / ADR-011 | ADR-006 training_eligible=FALSE, $0.00 LLM Assembly
-- Reconciled with V054 exam_definitions and V055 candidate_profiles(profile_id)
-- ============================================================================

CREATE TABLE IF NOT EXISTS exam_sections (
    id                 UUID PRIMARY KEY,
    exam_definition_id UUID NOT NULL REFERENCES exam_definitions(id) ON DELETE CASCADE,
    section_code       VARCHAR(64) NOT NULL,
    title_en           VARCHAR(160) NOT NULL,
    title_tr           VARCHAR(160) NOT NULL,
    question_count     INTEGER NOT NULL CHECK (question_count > 0),
    duration_minutes   INTEGER NOT NULL CHECK (duration_minutes > 0),
    section_order      INTEGER NOT NULL DEFAULT 1 CHECK (section_order >= 1),
    training_eligible  BOOLEAN NOT NULL DEFAULT FALSE CHECK (training_eligible = FALSE),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (exam_definition_id, section_code)
);

INSERT INTO exam_sections (
    id, exam_definition_id, section_code, title_en, title_tr,
    question_count, duration_minutes, section_order, training_eligible
) VALUES
    ('92000000-0000-4000-8000-000000000101', '91000000-0000-4000-8000-000000000101', 'TYT_MATH', 'TYT Mathematics', 'TYT Matematik', 40, 65, 1, FALSE),
    ('92000000-0000-4000-8000-000000000102', '91000000-0000-4000-8000-000000000102', 'TYT_MATH', 'TYT Mathematics (Archive)', 'TYT Matematik (Arşiv)', 40, 65, 1, FALSE),
    ('92000000-0000-4000-8000-000000000103', '91000000-0000-4000-8000-000000000103', 'AYT_MATH', 'AYT Mathematics', 'AYT Matematik', 40, 90, 1, FALSE),
    ('92000000-0000-4000-8000-000000000104', '91000000-0000-4000-8000-000000000104', 'SAT_MOD_1', 'Math Module 1', 'Matematik Modül 1', 22, 35, 1, FALSE),
    ('92000000-0000-4000-8000-000000000105', '91000000-0000-4000-8000-000000000104', 'SAT_MOD_2', 'Math Module 2', 'Matematik Modül 2', 22, 35, 2, FALSE),
    ('92000000-0000-4000-8000-000000000106', '91000000-0000-4000-8000-000000000105', 'CALC_PART_A', 'Part A (No Calculator)', 'Bölüm A (Hesap Makinesiz)', 30, 60, 1, FALSE),
    ('92000000-0000-4000-8000-000000000107', '91000000-0000-4000-8000-000000000105', 'CALC_PART_B', 'Part B (Graphing Calculator)', 'Bölüm B (Grafik Hesap Makineli)', 15, 45, 2, FALSE),
    ('92000000-0000-4000-8000-000000000108', '91000000-0000-4000-8000-000000000106', 'IB_PAPER_1', 'Paper 1 (Non-Calculator)', 'Bölüm 1 (Hesap Makinesiz)', 15, 60, 1, FALSE),
    ('92000000-0000-4000-8000-000000000109', '91000000-0000-4000-8000-000000000106', 'IB_PAPER_2', 'Paper 2 (Technology Allowed)', 'Bölüm 2 (Teknoloji İzinli)', 15, 60, 2, FALSE)
ON CONFLICT (exam_definition_id, section_code) DO NOTHING;

-- 1. Question Bank Items
CREATE TABLE IF NOT EXISTS exam_question_bank_items (
    id UUID PRIMARY KEY,
    exam_definition_id UUID NOT NULL REFERENCES exam_definitions(id) ON DELETE RESTRICT,
    exam_version_number INTEGER NOT NULL CHECK (exam_version_number >= 1),
    exam_section_id UUID REFERENCES exam_sections(id) ON DELETE SET NULL,
    question_code VARCHAR(64) NOT NULL UNIQUE,
    topic_code VARCHAR(128) NOT NULL,
    subtopic_code VARCHAR(128),
    item_format VARCHAR(32) NOT NULL CHECK (item_format IN (
        'SINGLE_CHOICE_MCQ',
        'MULTI_SELECT_MCQ',
        'NUMERIC_GRID_IN',
        'SHORT_STRUCTURED_RESPONSE'
    )),
    difficulty_band VARCHAR(16) NOT NULL CHECK (difficulty_band IN (
        'EASY', 'MEDIUM', 'HARD', 'EXPERT'
    )),
    difficulty_b_value NUMERIC(5,2) NOT NULL CHECK (difficulty_b_value BETWEEN -3.00 AND 3.00),
    discrimination_a_value NUMERIC(4,2) NOT NULL DEFAULT 1.00 CHECK (discrimination_a_value BETWEEN 0.20 AND 2.50),
    guessing_c_value NUMERIC(4,2) NOT NULL DEFAULT 0.20 CHECK (guessing_c_value BETWEEN 0.00 AND 0.35),
    cognitive_level VARCHAR(24) NOT NULL CHECK (cognitive_level IN (
        'RECALL', 'COMPREHENSION', 'APPLICATION', 'ANALYSIS', 'EVALUATION'
    )),
    stem_markdown TEXT NOT NULL CHECK (char_length(btrim(stem_markdown)) >= 10),
    stem_latex TEXT,
    alt_text_description TEXT,
    has_visual_diagram BOOLEAN NOT NULL DEFAULT FALSE,
    numeric_correct_answer NUMERIC(18,6),
    numeric_tolerance NUMERIC(18,6) CHECK (numeric_tolerance IS NULL OR numeric_tolerance >= 0),
    structured_rubric_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    solution_explanation_markdown TEXT NOT NULL CHECK (char_length(btrim(solution_explanation_markdown)) >= 10),
    estimated_time_seconds INTEGER NOT NULL CHECK (estimated_time_seconds BETWEEN 15 AND 1800),
    max_points NUMERIC(6,2) NOT NULL DEFAULT 1.00 CHECK (max_points > 0),
    negative_marking_weight NUMERIC(5,4) NOT NULL DEFAULT 0.2500 CHECK (negative_marking_weight BETWEEN 0.0000 AND 1.0000),
    status VARCHAR(24) NOT NULL CHECK (status IN (
        'DRAFT', 'UNDER_REVIEW', 'PUBLISHED', 'RETIRED'
    )),
    content_sha256 CHAR(64) NOT NULL CHECK (content_sha256 ~ '^[0-9a-f]{64}$'),
    exposure_count BIGINT NOT NULL DEFAULT 0 CHECK (exposure_count >= 0),
    training_eligible BOOLEAN NOT NULL DEFAULT FALSE CHECK (training_eligible = FALSE),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_exam_qbank_visual_alt_text CHECK (
        NOT has_visual_diagram OR (alt_text_description IS NOT NULL AND char_length(btrim(alt_text_description)) >= 10)
    ),
    CONSTRAINT chk_exam_qbank_numeric_answer CHECK (
        item_format <> 'NUMERIC_GRID_IN' OR (numeric_correct_answer IS NOT NULL AND numeric_tolerance IS NOT NULL)
    )
);

-- 2. Question Options (for SINGLE_CHOICE_MCQ and MULTI_SELECT_MCQ)
CREATE TABLE IF NOT EXISTS exam_question_options (
    id UUID PRIMARY KEY,
    question_item_id UUID NOT NULL REFERENCES exam_question_bank_items(id) ON DELETE CASCADE,
    option_key VARCHAR(8) NOT NULL CHECK (option_key IN ('A', 'B', 'C', 'D', 'E')),
    display_order INTEGER NOT NULL CHECK (display_order BETWEEN 1 AND 5),
    content_markdown TEXT NOT NULL CHECK (char_length(btrim(content_markdown)) >= 1),
    content_latex TEXT,
    is_correct BOOLEAN NOT NULL DEFAULT FALSE,
    distractor_rationale TEXT,
    training_eligible BOOLEAN NOT NULL DEFAULT FALSE CHECK (training_eligible = FALSE),
    UNIQUE (question_item_id, option_key),
    UNIQUE (question_item_id, display_order)
);

-- 3. Question Provenance & SME Verification Records
CREATE TABLE IF NOT EXISTS exam_question_provenance_records (
    id UUID PRIMARY KEY,
    question_item_id UUID NOT NULL UNIQUE REFERENCES exam_question_bank_items(id) ON DELETE CASCADE,
    source_type VARCHAR(32) NOT NULL CHECK (source_type IN (
        'ORIGINAL_AUTHORED',
        'PUBLIC_DOMAIN_PAST_PAPER',
        'LICENSED_PUBLISHER_BANK',
        'SME_CURATED_SYNTHETIC'
    )),
    license_classification VARCHAR(32) NOT NULL CHECK (license_classification IN (
        'PROPRIETARY_INTERNAL',
        'CC_BY_SA_4_0',
        'PUBLIC_DOMAIN',
        'COMMERCIAL_LICENSED'
    )),
    copyright_holder VARCHAR(160) NOT NULL,
    source_reference VARCHAR(255) NOT NULL,
    author_id VARCHAR(128) NOT NULL,
    reviewer_id VARCHAR(128) NOT NULL CHECK (reviewer_id <> author_id),
    verification_status VARCHAR(24) NOT NULL CHECK (verification_status IN (
        'PENDING', 'VERIFIED', 'REJECTED', 'REVOKED'
    )),
    provenance_hash CHAR(64) NOT NULL CHECK (provenance_hash ~ '^[0-9a-f]{64}$'),
    verified_at TIMESTAMPTZ NOT NULL,
    training_eligible BOOLEAN NOT NULL DEFAULT FALSE CHECK (training_eligible = FALSE),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 4. Assessment Blueprints
CREATE TABLE IF NOT EXISTS exam_assessment_blueprints (
    id UUID PRIMARY KEY,
    exam_definition_id UUID NOT NULL REFERENCES exam_definitions(id) ON DELETE RESTRICT,
    exam_version_number INTEGER NOT NULL CHECK (exam_version_number >= 1),
    exam_section_id UUID REFERENCES exam_sections(id) ON DELETE SET NULL,
    blueprint_code VARCHAR(64) NOT NULL UNIQUE,
    title VARCHAR(160) NOT NULL,
    description TEXT,
    blueprint_mode VARCHAR(32) NOT NULL CHECK (blueprint_mode IN (
        'DIAGNOSTIC_BASELINE',
        'SECTION_DRILL',
        'FULL_SIMULATION',
        'TOPIC_MASTERY_CHECK'
    )),
    total_question_count INTEGER NOT NULL CHECK (total_question_count BETWEEN 5 AND 200),
    base_duration_seconds INTEGER NOT NULL CHECK (base_duration_seconds BETWEEN 300 AND 21600),
    easy_target_bps INTEGER NOT NULL CHECK (easy_target_bps BETWEEN 0 AND 10000),
    medium_target_bps INTEGER NOT NULL CHECK (medium_target_bps BETWEEN 0 AND 10000),
    hard_target_bps INTEGER NOT NULL CHECK (hard_target_bps BETWEEN 0 AND 10000),
    expert_target_bps INTEGER NOT NULL CHECK (expert_target_bps BETWEEN 0 AND 10000),
    difficulty_tolerance_bps INTEGER NOT NULL DEFAULT 500 CHECK (difficulty_tolerance_bps BETWEEN 0 AND 2000),
    recall_target_bps INTEGER NOT NULL CHECK (recall_target_bps BETWEEN 0 AND 10000),
    comprehension_target_bps INTEGER NOT NULL CHECK (comprehension_target_bps BETWEEN 0 AND 10000),
    application_target_bps INTEGER NOT NULL CHECK (application_target_bps BETWEEN 0 AND 10000),
    analysis_target_bps INTEGER NOT NULL CHECK (analysis_target_bps BETWEEN 0 AND 10000),
    evaluation_target_bps INTEGER NOT NULL CHECK (evaluation_target_bps BETWEEN 0 AND 10000),
    allow_recently_seen_cooldown_days INTEGER NOT NULL DEFAULT 14 CHECK (allow_recently_seen_cooldown_days BETWEEN 0 AND 365),
    shuffle_questions BOOLEAN NOT NULL DEFAULT TRUE,
    shuffle_options BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(24) NOT NULL CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    blueprint_hash CHAR(64) NOT NULL CHECK (blueprint_hash ~ '^[0-9a-f]{64}$'),
    training_eligible BOOLEAN NOT NULL DEFAULT FALSE CHECK (training_eligible = FALSE),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_exam_blueprint_difficulty_bps_sum CHECK (
        easy_target_bps + medium_target_bps + hard_target_bps + expert_target_bps = 10000
    ),
    CONSTRAINT chk_exam_blueprint_cognitive_bps_sum CHECK (
        recall_target_bps + comprehension_target_bps + application_target_bps + analysis_target_bps + evaluation_target_bps = 10000
    )
);

-- 5. Blueprint Topic Distribution Rules
CREATE TABLE IF NOT EXISTS exam_blueprint_topic_rules (
    id UUID PRIMARY KEY,
    blueprint_id UUID NOT NULL REFERENCES exam_assessment_blueprints(id) ON DELETE CASCADE,
    topic_code VARCHAR(128) NOT NULL,
    target_weight_bps INTEGER NOT NULL CHECK (target_weight_bps BETWEEN 100 AND 10000),
    min_question_count INTEGER NOT NULL CHECK (min_question_count >= 0),
    max_question_count INTEGER NOT NULL CHECK (max_question_count >= min_question_count AND max_question_count >= 1),
    required_item_formats_json JSONB NOT NULL DEFAULT '["SINGLE_CHOICE_MCQ"]'::jsonb,
    rule_order INTEGER NOT NULL CHECK (rule_order >= 1),
    training_eligible BOOLEAN NOT NULL DEFAULT FALSE CHECK (training_eligible = FALSE),
    UNIQUE (blueprint_id, topic_code),
    UNIQUE (blueprint_id, rule_order)
);

-- 6. Assembled Assessments (Immutable Instances Ready for Delivery in Sprint 9.4)
-- Reconciled FK: candidate_profiles(profile_id) from V055 (canonical alias for exam_candidate_profiles(id))
CREATE TABLE IF NOT EXISTS exam_assembled_assessments (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    candidate_profile_id UUID REFERENCES candidate_profiles(profile_id) ON DELETE SET NULL,
    blueprint_id UUID NOT NULL REFERENCES exam_assessment_blueprints(id) ON DELETE RESTRICT,
    exam_definition_id UUID NOT NULL REFERENCES exam_definitions(id) ON DELETE RESTRICT,
    exam_version_number INTEGER NOT NULL CHECK (exam_version_number >= 1),
    assembly_seed VARCHAR(128) NOT NULL CHECK (char_length(btrim(assembly_seed)) >= 4),
    reproducibility_hash CHAR(64) NOT NULL CHECK (reproducibility_hash ~ '^[0-9a-f]{64}$'),
    total_question_count INTEGER NOT NULL CHECK (total_question_count BETWEEN 5 AND 200),
    base_duration_seconds INTEGER NOT NULL CHECK (base_duration_seconds BETWEEN 300 AND 21600),
    time_multiplier NUMERIC(4,2) NOT NULL DEFAULT 1.00 CHECK (time_multiplier BETWEEN 1.00 AND 2.00),
    effective_duration_seconds INTEGER NOT NULL CHECK (effective_duration_seconds >= base_duration_seconds),
    accommodations_snapshot_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    distribution_summary_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(24) NOT NULL CHECK (status IN ('ASSEMBLED', 'LAUNCHED', 'COMPLETED', 'EXPIRED')),
    assembled_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL CHECK (expires_at > assembled_at),
    training_eligible BOOLEAN NOT NULL DEFAULT FALSE CHECK (training_eligible = FALSE),
    UNIQUE (user_id, blueprint_id, assembly_seed)
);

-- 7. Assembled Assessment Items (Ordered Question Sequence)
CREATE TABLE IF NOT EXISTS exam_assembled_assessment_items (
    id UUID PRIMARY KEY,
    assembled_assessment_id UUID NOT NULL REFERENCES exam_assembled_assessments(id) ON DELETE CASCADE,
    question_item_id UUID NOT NULL REFERENCES exam_question_bank_items(id) ON DELETE RESTRICT,
    sequence_number INTEGER NOT NULL CHECK (sequence_number >= 1),
    topic_code VARCHAR(128) NOT NULL,
    difficulty_band VARCHAR(16) NOT NULL,
    cognitive_level VARCHAR(24) NOT NULL,
    option_order_json JSONB NOT NULL DEFAULT '["A","B","C","D","E"]'::jsonb,
    allocated_points NUMERIC(6,2) NOT NULL CHECK (allocated_points > 0),
    training_eligible BOOLEAN NOT NULL DEFAULT FALSE CHECK (training_eligible = FALSE),
    UNIQUE (assembled_assessment_id, sequence_number),
    UNIQUE (assembled_assessment_id, question_item_id)
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_exam_qbank_def_status_topic_diff
    ON exam_question_bank_items (exam_definition_id, status, topic_code, difficulty_band);
CREATE INDEX IF NOT EXISTS idx_exam_blueprints_def_status
    ON exam_assessment_blueprints (exam_definition_id, status);
CREATE INDEX IF NOT EXISTS idx_exam_assembled_user_created
    ON exam_assembled_assessments (user_id, assembled_at DESC);
CREATE INDEX IF NOT EXISTS idx_exam_assembled_repro_hash
    ON exam_assembled_assessments (reproducibility_hash);

-- ============================================================================
-- Deterministic Seed Data: 16 Published, SME-Verified Question Bank Items
-- Exam: TR_YKS_TYT_MATH (91000000-0000-4000-8000-000000000101), Version 1
-- Topics:
--   1. MATH.ARITHMETIC.INTEGER_OPERATIONS (4 items: EASY, MEDIUM, MEDIUM, HARD)
--   2. MATH.ARITHMETIC.FRACTIONS          (4 items: EASY, MEDIUM, MEDIUM, HARD)
--   3. MATH.ARITHMETIC.PERCENTAGES        (4 items: EASY, MEDIUM, HARD, EXPERT)
--   4. MATH.ALGEBRA.FACTORING             (4 items: EASY, MEDIUM, HARD, EXPERT)
-- ============================================================================

INSERT INTO exam_question_bank_items (
    id, exam_definition_id, exam_version_number, exam_section_id,
    question_code, topic_code, subtopic_code, item_format,
    difficulty_band, difficulty_b_value, discrimination_a_value, guessing_c_value,
    cognitive_level, stem_markdown, stem_latex, alt_text_description, has_visual_diagram,
    numeric_correct_answer, numeric_tolerance, structured_rubric_json,
    solution_explanation_markdown, estimated_time_seconds, max_points, negative_marking_weight,
    status, content_sha256, exposure_count, training_eligible
) VALUES
-- Topic 1: MATH.ARITHMETIC.INTEGER_OPERATIONS
(
    '93000000-0000-4000-8000-000000000101',
    '91000000-0000-4000-8000-000000000101', 1, '92000000-0000-4000-8000-000000000101',
    'TYT-MATH-2026-INT-001', 'MATH.ARITHMETIC.INTEGER_OPERATIONS', 'SIGNED_INTEGERS', 'SINGLE_CHOICE_MCQ',
    'EASY', -1.20, 1.05, 0.20, 'RECALL',
    'Evaluate the integer expression $(-18) + 27 - (-9)$ and choose the resulting value.',
    '(-18) + 27 - (-9)', NULL, FALSE, NULL, NULL, '{}'::jsonb,
    'Adding $27$ to $-18$ gives $9$, and subtracting $-9$ adds $9$, yielding $9 + 9 = 18$.',
    60, 1.00, 0.2500, 'PUBLISHED',
    '1010101010101010101010101010101010101010101010101010101010101001', 0, FALSE
),
(
    '93000000-0000-4000-8000-000000000102',
    '91000000-0000-4000-8000-000000000101', 1, '92000000-0000-4000-8000-000000000101',
    'TYT-MATH-2026-INT-002', 'MATH.ARITHMETIC.INTEGER_OPERATIONS', 'DIVISIBILITY_PARITY', 'SINGLE_CHOICE_MCQ',
    'MEDIUM', -0.15, 1.15, 0.20, 'COMPREHENSION',
    'If $a$ and $b$ are consecutive odd integers with $a < b$ and $a + b = 48$, what is the product $a \cdot b$?',
    'a < b,\; a + b = 48', NULL, FALSE, NULL, NULL, '{}'::jsonb,
    'Two consecutive odd integers summing to $48$ are $23$ and $25$; their product is $23 \times 25 = 575$.',
    75, 1.00, 0.2500, 'PUBLISHED',
    '1010101010101010101010101010101010101010101010101010101010101002', 0, FALSE
),
(
    '93000000-0000-4000-8000-000000000103',
    '91000000-0000-4000-8000-000000000101', 1, '92000000-0000-4000-8000-000000000101',
    'TYT-MATH-2026-INT-003', 'MATH.ARITHMETIC.INTEGER_OPERATIONS', 'REMAINDER_DIVISION', 'SINGLE_CHOICE_MCQ',
    'MEDIUM', 0.25, 1.20, 0.20, 'APPLICATION',
    'When positive integer $N$ is divided by $14$, the quotient is $5$ and the remainder is $r > 9$. What is the sum of all possible values of $N$?',
    'N = 14 \cdot 5 + r,\; 9 < r < 14', NULL, FALSE, NULL, NULL, '{}'::jsonb,
    'Since $9 < r < 14$, $r \in \{10, 11, 12, 13\}$, giving $N \in \{80, 81, 82, 83\}$ with sum $326$.',
    90, 1.00, 0.2500, 'PUBLISHED',
    '1010101010101010101010101010101010101010101010101010101010101003', 0, FALSE
),
(
    '93000000-0000-4000-8000-000000000104',
    '91000000-0000-4000-8000-000000000101', 1, '92000000-0000-4000-8000-000000000101',
    'TYT-MATH-2026-INT-004', 'MATH.ARITHMETIC.INTEGER_OPERATIONS', 'PRIME_FACTORIZATION', 'SINGLE_CHOICE_MCQ',
    'HARD', 1.15, 1.35, 0.20, 'ANALYSIS',
    'Let $k$ be the smallest positive integer such that $180k$ is a perfect cube. What is the sum of the digits of $k$?',
    '180k = m^3', NULL, FALSE, NULL, NULL, '{}'::jsonb,
    'Prime factorization $180 = 2^2 \cdot 3^2 \cdot 5^1$ requires $k = 2^1 \cdot 3^1 \cdot 5^2 = 150$, whose digit sum is $1+5+0=6$.',
    110, 1.00, 0.2500, 'PUBLISHED',
    '1010101010101010101010101010101010101010101010101010101010101004', 0, FALSE
),
-- Topic 2: MATH.ARITHMETIC.FRACTIONS
(
    '93000000-0000-4000-8000-000000000105',
    '91000000-0000-4000-8000-000000000101', 1, '92000000-0000-4000-8000-000000000101',
    'TYT-MATH-2026-FRC-001', 'MATH.ARITHMETIC.FRACTIONS', 'RATIONAL_ADDITION', 'SINGLE_CHOICE_MCQ',
    'EASY', -1.05, 1.00, 0.20, 'RECALL',
    'Compute the simplified value of $\frac{3}{4} + \frac{5}{6} - \frac{7}{12}$.',
    '\frac{3}{4} + \frac{5}{6} - \frac{7}{12}', NULL, FALSE, NULL, NULL, '{}'::jsonb,
    'Using common denominator $12$: $\frac{9 + 10 - 7}{12} = \frac{12}{12} = 1$.',
    60, 1.00, 0.2500, 'PUBLISHED',
    '1010101010101010101010101010101010101010101010101010101010101005', 0, FALSE
),
(
    '93000000-0000-4000-8000-000000000106',
    '91000000-0000-4000-8000-000000000101', 1, '92000000-0000-4000-8000-000000000101',
    'TYT-MATH-2026-FRC-002', 'MATH.ARITHMETIC.FRACTIONS', 'COMPOUND_FRACTIONS', 'SINGLE_CHOICE_MCQ',
    'MEDIUM', -0.10, 1.15, 0.20, 'COMPREHENSION',
    'Simplify the compound fraction $\frac{1 - \frac{1}{3}}{1 + \frac{1}{3}}$ to its lowest terms.',
    '\frac{1 - 1/3}{1 + 1/3}', NULL, FALSE, NULL, NULL, '{}'::jsonb,
    'The numerator is $\frac{2}{3}$ and the denominator is $\frac{4}{3}$; dividing gives $\frac{2}{4} = \frac{1}{2}$.',
    75, 1.00, 0.2500, 'PUBLISHED',
    '1010101010101010101010101010101010101010101010101010101010101006', 0, FALSE
),
(
    '93000000-0000-4000-8000-000000000107',
    '91000000-0000-4000-8000-000000000101', 1, '92000000-0000-4000-8000-000000000101',
    'TYT-MATH-2026-FRC-003', 'MATH.ARITHMETIC.FRACTIONS', 'PROPORTION_WORD_PROBLEMS', 'SINGLE_CHOICE_MCQ',
    'MEDIUM', 0.30, 1.25, 0.20, 'APPLICATION',
    'A water tank is $\frac{2}{5}$ full. After adding $36$ liters of water, the tank becomes $\frac{7}{10}$ full. What is the total capacity of the tank in liters?',
    '\frac{7}{10}C - \frac{2}{5}C = 36', NULL, FALSE, NULL, NULL, '{}'::jsonb,
    'Since $\frac{7}{10} - \frac{4}{10} = \frac{3}{10}$ of the tank equals $36$ liters, total capacity $C = 36 \times \frac{10}{3} = 120$ liters.',
    90, 1.00, 0.2500, 'PUBLISHED',
    '1010101010101010101010101010101010101010101010101010101010101007', 0, FALSE
),
(
    '93000000-0000-4000-8000-000000000108',
    '91000000-0000-4000-8000-000000000101', 1, '92000000-0000-4000-8000-000000000101',
    'TYT-MATH-2026-FRC-004', 'MATH.ARITHMETIC.FRACTIONS', 'TELESCOPING_PRODUCTS', 'SINGLE_CHOICE_MCQ',
    'HARD', 1.25, 1.40, 0.20, 'ANALYSIS',
    'Evaluate the finite product $\left(1 + \frac{1}{2}\right)\left(1 + \frac{1}{3}\right)\left(1 + \frac{1}{4}\right)\cdots\left(1 + \frac{1}{19}\right)$.',
    '\prod_{n=2}^{19} \left(1 + \frac{1}{n}\right)', NULL, FALSE, NULL, NULL, '{}'::jsonb,
    'Rewriting each factor as $\frac{n+1}{n}$ telescopes to $\frac{3}{2}\cdot\frac{4}{3}\cdots\frac{20}{19} = \frac{20}{2} = 10$.',
    105, 1.00, 0.2500, 'PUBLISHED',
    '1010101010101010101010101010101010101010101010101010101010101008', 0, FALSE
),
-- Topic 3: MATH.ARITHMETIC.PERCENTAGES
(
    '93000000-0000-4000-8000-000000000109',
    '91000000-0000-4000-8000-000000000101', 1, '92000000-0000-4000-8000-000000000101',
    'TYT-MATH-2026-PCT-001', 'MATH.ARITHMETIC.PERCENTAGES', 'DIRECT_PERCENTAGE', 'SINGLE_CHOICE_MCQ',
    'EASY', -0.95, 1.05, 0.20, 'RECALL',
    'What is $35\%$ of $240$?',
    '0.35 \times 240', NULL, FALSE, NULL, NULL, '{}'::jsonb,
    'Computing $0.35 \times 240 = \frac{7}{20} \times 240 = 7 \times 12 = 84$.',
    55, 1.00, 0.2500, 'PUBLISHED',
    '1010101010101010101010101010101010101010101010101010101010101009', 0, FALSE
),
(
    '93000000-0000-4000-8000-000000000110',
    '91000000-0000-4000-8000-000000000101', 1, '92000000-0000-4000-8000-000000000101',
    'TYT-MATH-2026-PCT-002', 'MATH.ARITHMETIC.PERCENTAGES', 'SUCCESSIVE_CHANGES', 'SINGLE_CHOICE_MCQ',
    'MEDIUM', 0.15, 1.20, 0.20, 'APPLICATION',
    'An item priced at $500$ TL is first increased by $20\%$ and then discounted by $15\%$. What is the final sale price in TL?',
    '500 \times 1.20 \times 0.85', NULL, FALSE, NULL, NULL, '{}'::jsonb,
    'After a $20\%$ increase the price is $600$ TL; a $15\%$ discount reduces it by $90$ TL to $510$ TL.',
    85, 1.00, 0.2500, 'PUBLISHED',
    '1010101010101010101010101010101010101010101010101010101010101010', 0, FALSE
),
(
    '93000000-0000-4000-8000-000000000111',
    '91000000-0000-4000-8000-000000000101', 1, '92000000-0000-4000-8000-000000000101',
    'TYT-MATH-2026-PCT-003', 'MATH.ARITHMETIC.PERCENTAGES', 'MIXTURE_PERCENTAGES', 'SINGLE_CHOICE_MCQ',
    'HARD', 1.05, 1.30, 0.20, 'ANALYSIS',
    'According to the diagram, $300$ grams of a $20\%$ salt solution is mixed with $200$ grams of a $45\%$ salt solution. What is the salt percentage of the resulting mixture?',
    '\frac{300(0.20) + 200(0.45)}{500}',
    'Bar chart comparing two containers: Container A with 300g at 20 percent concentration and Container B with 200g at 45 percent concentration.',
    TRUE, NULL, NULL, '{}'::jsonb,
    'Total salt is $60 + 90 = 150$ grams in $500$ grams of solution, which corresponds to $\frac{150}{500} = 30\%$.',
    110, 1.00, 0.2500, 'PUBLISHED',
    '1010101010101010101010101010101010101010101010101010101010101011', 0, FALSE
),
(
    '93000000-0000-4000-8000-000000000112',
    '91000000-0000-4000-8000-000000000101', 1, '92000000-0000-4000-8000-000000000101',
    'TYT-MATH-2026-PCT-004', 'MATH.ARITHMETIC.PERCENTAGES', 'INFLATION_PURCHASING_POWER', 'SINGLE_CHOICE_MCQ',
    'EXPERT', 1.85, 1.50, 0.20, 'EVALUATION',
    'Annual inflation is $60\%$ while a nominal scholarship stipend increases by $36\%$. By what percentage does the real purchasing power of the stipend decrease?',
    '\left(1 - \frac{1.36}{1.60}\right) \times 100', NULL, FALSE, NULL, NULL, '{}'::jsonb,
    'Real value ratio is $\frac{1.36}{1.60} = 0.85$, representing a $15\%$ decrease in purchasing power.',
    135, 1.00, 0.2500, 'PUBLISHED',
    '1010101010101010101010101010101010101010101010101010101010101012', 0, FALSE
),
-- Topic 4: MATH.ALGEBRA.FACTORING
(
    '93000000-0000-4000-8000-000000000113',
    '91000000-0000-4000-8000-000000000101', 1, '92000000-0000-4000-8000-000000000101',
    'TYT-MATH-2026-FAC-001', 'MATH.ALGEBRA.FACTORING', 'DIFFERENCE_OF_SQUARES', 'SINGLE_CHOICE_MCQ',
    'EASY', -1.10, 1.05, 0.20, 'RECALL',
    'Evaluate $103^2 - 97^2$ using the difference of squares identity.',
    '103^2 - 97^2 = (103 - 97)(103 + 97)', NULL, FALSE, NULL, NULL, '{}'::jsonb,
    'By $a^2 - b^2 = (a-b)(a+b)$, we obtain $(103 - 97)(103 + 97) = 6 \times 200 = 1200$.',
    60, 1.00, 0.2500, 'PUBLISHED',
    '1010101010101010101010101010101010101010101010101010101010101013', 0, FALSE
),
(
    '93000000-0000-4000-8000-000000000114',
    '91000000-0000-4000-8000-000000000101', 1, '92000000-0000-4000-8000-000000000101',
    'TYT-MATH-2026-FAC-002', 'MATH.ALGEBRA.FACTORING', 'QUADRATIC_SIMPLIFICATION', 'SINGLE_CHOICE_MCQ',
    'MEDIUM', 0.20, 1.20, 0.20, 'APPLICATION',
    'For $x \notin \{2, 5\}$, simplify the rational expression $\frac{x^2 - 7x + 10}{x^2 - 4} \cdot \frac{x + 2}{x - 5}$.',
    '\frac{(x-2)(x-5)}{(x-2)(x+2)} \cdot \frac{x+2}{x-5}', NULL, FALSE, NULL, NULL, '{}'::jsonb,
    'Factoring numerator and denominator cancels $(x-2)$, $(x-5)$, and $(x+2)$, leaving $1$.',
    85, 1.00, 0.2500, 'PUBLISHED',
    '1010101010101010101010101010101010101010101010101010101010101014', 0, FALSE
),
(
    '93000000-0000-4000-8000-000000000115',
    '91000000-0000-4000-8000-000000000101', 1, '92000000-0000-4000-8000-000000000101',
    'TYT-MATH-2026-FAC-003', 'MATH.ALGEBRA.FACTORING', 'SYMMETRIC_IDENTITIES', 'SINGLE_CHOICE_MCQ',
    'HARD', 1.20, 1.35, 0.20, 'ANALYSIS',
    'Given $x - \frac{1}{x} = 4$ for $x > 0$, what is the value of $x^2 + \frac{1}{x^2}$?',
    '\left(x - \frac{1}{x}\right)^2 = x^2 - 2 + \frac{1}{x^2}', NULL, FALSE, NULL, NULL, '{}'::jsonb,
    'Squaring both sides gives $x^2 - 2 + \frac{1}{x^2} = 16$, so $x^2 + \frac{1}{x^2} = 18$.',
    105, 1.00, 0.2500, 'PUBLISHED',
    '1010101010101010101010101010101010101010101010101010101010101015', 0, FALSE
),
(
    '93000000-0000-4000-8000-000000000116',
    '91000000-0000-4000-8000-000000000101', 1, '92000000-0000-4000-8000-000000000101',
    'TYT-MATH-2026-FAC-004', 'MATH.ALGEBRA.FACTORING', 'SUM_OF_CUBES', 'SINGLE_CHOICE_MCQ',
    'EXPERT', 1.95, 1.55, 0.20, 'EVALUATION',
    'If $a + b = 5$ and $a \cdot b = 3$, compute the exact value of $a^3 + b^3$.',
    'a^3 + b^3 = (a+b)^3 - 3ab(a+b)', NULL, FALSE, NULL, NULL, '{}'::jsonb,
    'Using $a^3 + b^3 = (a+b)^3 - 3ab(a+b)$, we substitute $5^3 - 3(3)(5) = 125 - 45 = 80$.',
    130, 1.00, 0.2500, 'PUBLISHED',
    '1010101010101010101010101010101010101010101010101010101010101016', 0, FALSE
)
ON CONFLICT (question_code) DO NOTHING;

-- Seed Options (5 options A..E per MCQ item, exactly 1 correct)
INSERT INTO exam_question_options (
    id, question_item_id, option_key, display_order, content_markdown, content_latex, is_correct, distractor_rationale, training_eligible
) VALUES
-- Item 101 (Correct: C = 18)
('93100000-0000-4000-8000-000000000101', '93000000-0000-4000-8000-000000000101', 'A', 1, '$0$', '0', FALSE, 'Subtracted 9 instead of adding 9.', FALSE),
('93100000-0000-4000-8000-000000000102', '93000000-0000-4000-8000-000000000101', 'B', 2, '$9$', '9', FALSE, 'Omitted the final -(-9) term.', FALSE),
('93100000-0000-4000-8000-000000000103', '93000000-0000-4000-8000-000000000101', 'C', 3, '$18$', '18', TRUE, 'Correct evaluation of (-18) + 27 + 9.', FALSE),
('93100000-0000-4000-8000-000000000104', '93000000-0000-4000-8000-000000000101', 'D', 4, '$36$', '36', FALSE, 'Treated -18 as +18.', FALSE),
('93100000-0000-4000-8000-000000000105', '93000000-0000-4000-8000-000000000101', 'E', 5, '$-18$', '-18', FALSE, 'Sign reversal error.', FALSE),
-- Item 102 (Correct: B = 575)
('93100000-0000-4000-8000-000000000201', '93000000-0000-4000-8000-000000000102', 'A', 1, '$572$', '572', FALSE, 'Used consecutive even integers 22 and 26.', FALSE),
('93100000-0000-4000-8000-000000000202', '93000000-0000-4000-8000-000000000102', 'B', 2, '$575$', '575', TRUE, 'Correct product 23 * 25 = 575.', FALSE),
('93100000-0000-4000-8000-000000000203', '93000000-0000-4000-8000-000000000102', 'C', 3, '$576$', '576', FALSE, 'Computed 24 * 24 ignoring odd constraint.', FALSE),
('93100000-0000-4000-8000-000000000204', '93000000-0000-4000-8000-000000000102', 'D', 4, '$525$', '525', FALSE, 'Used 21 * 25.', FALSE),
('93100000-0000-4000-8000-000000000205', '93000000-0000-4000-8000-000000000102', 'E', 5, '$625$', '625', FALSE, 'Squared 25.', FALSE),
-- Item 103 (Correct: D = 326)
('93100000-0000-4000-8000-000000000301', '93000000-0000-4000-8000-000000000103', 'A', 1, '$246$', '246', FALSE, 'Omitted r=10.', FALSE),
('93100000-0000-4000-8000-000000000302', '93000000-0000-4000-8000-000000000103', 'B', 2, '$315$', '315', FALSE, 'Arithmetic sum error.', FALSE),
('93100000-0000-4000-8000-000000000303', '93000000-0000-4000-8000-000000000103', 'C', 3, '$405$', '405', FALSE, 'Included r=9.', FALSE),
('93100000-0000-4000-8000-000000000304', '93000000-0000-4000-8000-000000000103', 'D', 4, '$326$', '326', TRUE, 'Correct sum 80 + 81 + 82 + 83 = 326.', FALSE),
('93100000-0000-4000-8000-000000000305', '93000000-0000-4000-8000-000000000103', 'E', 5, '$410$', '410', FALSE, 'Included r=14.', FALSE),
-- Item 104 (Correct: B = 6)
('93100000-0000-4000-8000-000000000401', '93000000-0000-4000-8000-000000000104', 'A', 1, '$3$', '3', FALSE, 'Used k=30 (perfect square instead of cube).', FALSE),
('93100000-0000-4000-8000-000000000402', '93000000-0000-4000-8000-000000000104', 'B', 2, '$6$', '6', TRUE, 'For k=150, digit sum is 1+5+0 = 6.', FALSE),
('93100000-0000-4000-8000-000000000403', '93000000-0000-4000-8000-000000000104', 'C', 3, '$9$', '9', FALSE, 'Used k=90.', FALSE),
('93100000-0000-4000-8000-000000000404', '93000000-0000-4000-8000-000000000104', 'D', 4, '$12$', '12', FALSE, 'Added prime factors 2+3+5+2.', FALSE),
('93100000-0000-4000-8000-000000000405', '93000000-0000-4000-8000-000000000104', 'E', 5, '$15$', '15', FALSE, 'Used 15 directly.', FALSE),
-- Item 105 (Correct: A = 1)
('93100000-0000-4000-8000-000000000501', '93000000-0000-4000-8000-000000000105', 'A', 1, '$1$', '1', TRUE, 'Correct sum 12/12 = 1.', FALSE),
('93100000-0000-4000-8000-000000000502', '93000000-0000-4000-8000-000000000105', 'B', 2, '$\frac{5}{6}$', '\frac{5}{6}', FALSE, 'Numerator calculation slip.', FALSE),
('93100000-0000-4000-8000-000000000503', '93000000-0000-4000-8000-000000000105', 'C', 3, '$\frac{11}{12}$', '\frac{11}{12}', FALSE, 'Converted 3/4 to 8/12.', FALSE),
('93100000-0000-4000-8000-000000000504', '93000000-0000-4000-8000-000000000105', 'D', 4, '$\frac{13}{12}$', '\frac{13}{12}', FALSE, 'Rounding error.', FALSE),
('93100000-0000-4000-8000-000000000505', '93000000-0000-4000-8000-000000000105', 'E', 5, '$\frac{13}{6}$', '\frac{13}{6}', FALSE, 'Added 7/12 instead of subtracting.', FALSE),
-- Item 106 (Correct: C = 1/2)
('93100000-0000-4000-8000-000000000601', '93000000-0000-4000-8000-000000000106', 'A', 1, '$\frac{1}{4}$', '\frac{1}{4}', FALSE, 'Multiplied numerator by 1/2.', FALSE),
('93100000-0000-4000-8000-000000000602', '93000000-0000-4000-8000-000000000106', 'B', 2, '$\frac{1}{3}$', '\frac{1}{3}', FALSE, 'Cancelled 1s directly.', FALSE),
('93100000-0000-4000-8000-000000000603', '93000000-0000-4000-8000-000000000106', 'C', 3, '$\frac{1}{2}$', '\frac{1}{2}', TRUE, 'Correct ratio (2/3)/(4/3) = 1/2.', FALSE),
('93100000-0000-4000-8000-000000000604', '93000000-0000-4000-8000-000000000106', 'D', 4, '$\frac{2}{3}$', '\frac{2}{3}', FALSE, 'Forgot to divide by 4/3.', FALSE),
('93100000-0000-4000-8000-000000000605', '93000000-0000-4000-8000-000000000106', 'E', 5, '$2$', '2', FALSE, 'Inverted numerator and denominator.', FALSE),
-- Item 107 (Correct: D = 120)
('93100000-0000-4000-8000-000000000701', '93000000-0000-4000-8000-000000000107', 'A', 1, '$80$', '80', FALSE, 'Used 9/20 instead of 3/10.', FALSE),
('93100000-0000-4000-8000-000000000702', '93000000-0000-4000-8000-000000000107', 'B', 2, '$90$', '90', FALSE, 'Used 2/5 = 36.', FALSE),
('93100000-0000-4000-8000-000000000703', '93000000-0000-4000-8000-000000000107', 'C', 3, '$108$', '108', FALSE, 'Multiplied 36 by 3.', FALSE),
('93100000-0000-4000-8000-000000000704', '93000000-0000-4000-8000-000000000107', 'D', 4, '$120$', '120', TRUE, '3/10 of capacity is 36L, so capacity is 120L.', FALSE),
('93100000-0000-4000-8000-000000000705', '93000000-0000-4000-8000-000000000107', 'E', 5, '$150$', '150', FALSE, 'Used 1/4 instead of 3/10.', FALSE),
-- Item 108 (Correct: B = 10)
('93100000-0000-4000-8000-000000000801', '93000000-0000-4000-8000-000000000108', 'A', 1, '$\frac{19}{2}$', '\frac{19}{2}', FALSE, 'Stopped at 19 in numerator.', FALSE),
('93100000-0000-4000-8000-000000000802', '93000000-0000-4000-8000-000000000108', 'B', 2, '$10$', '10', TRUE, 'Telescopes to 20/2 = 10.', FALSE),
('93100000-0000-4000-8000-000000000803', '93000000-0000-4000-8000-000000000108', 'C', 3, '$\frac{21}{2}$', '\frac{21}{2}', FALSE, 'Off-by-one in last term.', FALSE),
('93100000-0000-4000-8000-000000000804', '93000000-0000-4000-8000-000000000108', 'D', 4, '$19$', '19', FALSE, 'Forgot denominator 2.', FALSE),
('93100000-0000-4000-8000-000000000805', '93000000-0000-4000-8000-000000000108', 'E', 5, '$20$', '20', FALSE, 'Started at n=1 instead of n=2.', FALSE),
-- Item 109 (Correct: C = 84)
('93100000-0000-4000-8000-000000000901', '93000000-0000-4000-8000-000000000109', 'A', 1, '$72$', '72', FALSE, 'Computed 30% of 240.', FALSE),
('93100000-0000-4000-8000-000000000902', '93000000-0000-4000-8000-000000000109', 'B', 2, '$78$', '78', FALSE, 'Multiplication slip.', FALSE),
('93100000-0000-4000-8000-000000000903', '93000000-0000-4000-8000-000000000109', 'C', 3, '$84$', '84', TRUE, '0.35 * 240 = 84.', FALSE),
('93100000-0000-4000-8000-000000000904', '93000000-0000-4000-8000-000000000109', 'D', 4, '$90$', '90', FALSE, 'Computed 37.5% of 240.', FALSE),
('93100000-0000-4000-8000-000000000905', '93000000-0000-4000-8000-000000000109', 'E', 5, '$96$', '96', FALSE, 'Computed 40% of 240.', FALSE),
-- Item 110 (Correct: B = 510)
('93100000-0000-4000-8000-000000001001', '93000000-0000-4000-8000-000000000110', 'A', 1, '$500$', '500', FALSE, 'Assumed changes cancel.', FALSE),
('93100000-0000-4000-8000-000000001002', '93000000-0000-4000-8000-000000000110', 'B', 2, '$510$', '510', TRUE, '500 * 1.20 * 0.85 = 510.', FALSE),
('93100000-0000-4000-8000-000000001003', '93000000-0000-4000-8000-000000000110', 'C', 3, '$525$', '525', FALSE, 'Added percentages 20% - 15% = 5%.', FALSE),
('93100000-0000-4000-8000-000000001004', '93000000-0000-4000-8000-000000000110', 'D', 4, '$540$', '540', FALSE, 'Used 10% discount instead of 15%.', FALSE),
('93100000-0000-4000-8000-000000001005', '93000000-0000-4000-8000-000000000110', 'E', 5, '$490$', '490', FALSE, 'Reversed increase and decrease rates.', FALSE),
-- Item 111 (Correct: C = 30)
('93100000-0000-4000-8000-000000001101', '93000000-0000-4000-8000-000000000111', 'A', 1, '$25\%$', '25\%', FALSE, 'Underweighted second solution.', FALSE),
('93100000-0000-4000-8000-000000001102', '93000000-0000-4000-8000-000000000111', 'B', 2, '$28\%$', '28\%', FALSE, 'Swapped weights.', FALSE),
('93100000-0000-4000-8000-000000001103', '93000000-0000-4000-8000-000000000111', 'C', 3, '$30\%$', '30\%', TRUE, '(60 + 90) / 500 = 30%.', FALSE),
('93100000-0000-4000-8000-000000001104', '93000000-0000-4000-8000-000000000111', 'D', 4, '$32.5\%$', '32.5\%', FALSE, 'Took unweighted mean of 20% and 45%.', FALSE),
('93100000-0000-4000-8000-000000001105', '93000000-0000-4000-8000-000000000111', 'E', 5, '$35\%$', '35\%', FALSE, 'Overweighted 45% solution.', FALSE),
-- Item 112 (Correct: B = 15)
('93100000-0000-4000-8000-000000001201', '93000000-0000-4000-8000-000000000112', 'A', 1, '$12\%$', '12\%', FALSE, 'Divided 24 by 200.', FALSE),
('93100000-0000-4000-8000-000000001202', '93000000-0000-4000-8000-000000000112', 'B', 2, '$15\%$', '15\%', TRUE, '1 - 1.36/1.60 = 0.15 = 15%.', FALSE),
('93100000-0000-4000-8000-000000001203', '93000000-0000-4000-8000-000000000112', 'C', 3, '$18\%$', '18\%', FALSE, 'Approximation error.', FALSE),
('93100000-0000-4000-8000-000000001204', '93000000-0000-4000-8000-000000000112', 'D', 4, '$20\%$', '20\%', FALSE, 'Ratio slip.', FALSE),
('93100000-0000-4000-8000-000000001205', '93000000-0000-4000-8000-000000000112', 'E', 5, '$24\%$', '24\%', FALSE, 'Subtracted 60% - 36% directly.', FALSE),
-- Item 113 (Correct: D = 1200)
('93100000-0000-4000-8000-000000001301', '93000000-0000-4000-8000-000000000113', 'A', 1, '$36$', '36', FALSE, 'Computed (103 - 97)^2.', FALSE),
('93100000-0000-4000-8000-000000001302', '93000000-0000-4000-8000-000000000113', 'B', 2, '$600$', '600', FALSE, 'Used 100 instead of 200.', FALSE),
('93100000-0000-4000-8000-000000001303', '93000000-0000-4000-8000-000000000113', 'C', 3, '$1000$', '1000', FALSE, 'Used difference 5 instead of 6.', FALSE),
('93100000-0000-4000-8000-000000001304', '93000000-0000-4000-8000-000000000113', 'D', 4, '$1200$', '1200', TRUE, '(103 - 97)(103 + 97) = 6 * 200 = 1200.', FALSE),
('93100000-0000-4000-8000-000000001305', '93000000-0000-4000-8000-000000000113', 'E', 5, '$1400$', '1400', FALSE, 'Arithmetic error.', FALSE),
-- Item 114 (Correct: A = 1)
('93100000-0000-4000-8000-000000001401', '93000000-0000-4000-8000-000000000114', 'A', 1, '$1$', '1', TRUE, 'All linear factors cancel to 1.', FALSE),
('93100000-0000-4000-8000-000000001402', '93000000-0000-4000-8000-000000000114', 'B', 2, '$-1$', '-1', FALSE, 'Sign reversal when factoring.', FALSE),
('93100000-0000-4000-8000-000000001403', '93000000-0000-4000-8000-000000000114', 'C', 3, '$\frac{x-5}{x+2}$', '\frac{x-5}{x+2}', FALSE, 'Did not multiply by second fraction.', FALSE),
('93100000-0000-4000-8000-000000001404', '93000000-0000-4000-8000-000000000114', 'D', 4, '$\frac{x-2}{x+2}$', '\frac{x-2}{x+2}', FALSE, 'Partial cancellation.', FALSE),
('93100000-0000-4000-8000-000000001405', '93000000-0000-4000-8000-000000000114', 'E', 5, '$x - 2$', 'x - 2', FALSE, 'Failed to factor x^2 - 4.', FALSE),
-- Item 115 (Correct: D = 18)
('93100000-0000-4000-8000-000000001501', '93000000-0000-4000-8000-000000000115', 'A', 1, '$12$', '12', FALSE, 'Subtracted 4 instead of adding 2.', FALSE),
('93100000-0000-4000-8000-000000001502', '93000000-0000-4000-8000-000000000115', 'B', 2, '$14$', '14', FALSE, 'Subtracted 2 from 16 instead of adding.', FALSE),
('93100000-0000-4000-8000-000000001503', '93000000-0000-4000-8000-000000000115', 'C', 3, '$16$', '16', FALSE, 'Omitted middle cross term -2.', FALSE),
('93100000-0000-4000-8000-000000001504', '93000000-0000-4000-8000-000000000115', 'D', 4, '$18$', '18', TRUE, '4^2 + 2 = 18.', FALSE),
('93100000-0000-4000-8000-000000001505', '93000000-0000-4000-8000-000000000115', 'E', 5, '$20$', '20', FALSE, 'Added 4 instead of 2.', FALSE),
-- Item 116 (Correct: C = 80)
('93100000-0000-4000-8000-000000001601', '93000000-0000-4000-8000-000000000116', 'A', 1, '$65$', '65', FALSE, 'Subtracted 60 from 125.', FALSE),
('93100000-0000-4000-8000-000000001602', '93000000-0000-4000-8000-000000000116', 'B', 2, '$75$', '75', FALSE, 'Subtracted 50 from 125.', FALSE),
('93100000-0000-4000-8000-000000001603', '93000000-0000-4000-8000-000000000116', 'C', 3, '$80$', '80', TRUE, '5^3 - 3(3)(5) = 125 - 45 = 80.', FALSE),
('93100000-0000-4000-8000-000000001604', '93000000-0000-4000-8000-000000000116', 'D', 4, '$110$', '110', FALSE, 'Subtracted 3*5 instead of 3*3*5.', FALSE),
('93100000-0000-4000-8000-000000001605', '93000000-0000-4000-8000-000000000116', 'E', 5, '$125$', '125', FALSE, 'Computed (a+b)^3 directly.', FALSE)
ON CONFLICT (question_item_id, option_key) DO NOTHING;

-- Seed Provenance Records (All 16 items SME-VERIFIED with reviewer_id <> author_id)
INSERT INTO exam_question_provenance_records (
    id, question_item_id, source_type, license_classification,
    copyright_holder, source_reference, author_id, reviewer_id,
    verification_status, provenance_hash, verified_at, training_eligible
) VALUES
('93200000-0000-4000-8000-000000000101', '93000000-0000-4000-8000-000000000101', 'ORIGINAL_AUTHORED', 'PROPRIETARY_INTERNAL', 'VerifiedAI Assessment Guild', 'TYT-2026-BANK-SPEC-INT-001', 'sme.author.ayse.kaya', 'sme.reviewer.mehmet.demir', 'VERIFIED', '2020202020202020202020202020202020202020202020202020202020202001', TIMESTAMPTZ '2026-01-15T10:00:00Z', FALSE),
('93200000-0000-4000-8000-000000000102', '93000000-0000-4000-8000-000000000102', 'ORIGINAL_AUTHORED', 'PROPRIETARY_INTERNAL', 'VerifiedAI Assessment Guild', 'TYT-2026-BANK-SPEC-INT-002', 'sme.author.ayse.kaya', 'sme.reviewer.mehmet.demir', 'VERIFIED', '2020202020202020202020202020202020202020202020202020202020202002', TIMESTAMPTZ '2026-01-15T10:05:00Z', FALSE),
('93200000-0000-4000-8000-000000000103', '93000000-0000-4000-8000-000000000103', 'ORIGINAL_AUTHORED', 'PROPRIETARY_INTERNAL', 'VerifiedAI Assessment Guild', 'TYT-2026-BANK-SPEC-INT-003', 'sme.author.ayse.kaya', 'sme.reviewer.mehmet.demir', 'VERIFIED', '2020202020202020202020202020202020202020202020202020202020202003', TIMESTAMPTZ '2026-01-15T10:10:00Z', FALSE),
('93200000-0000-4000-8000-000000000104', '93000000-0000-4000-8000-000000000104', 'ORIGINAL_AUTHORED', 'PROPRIETARY_INTERNAL', 'VerifiedAI Assessment Guild', 'TYT-2026-BANK-SPEC-INT-004', 'sme.author.ayse.kaya', 'sme.reviewer.mehmet.demir', 'VERIFIED', '2020202020202020202020202020202020202020202020202020202020202004', TIMESTAMPTZ '2026-01-15T10:15:00Z', FALSE),
('93200000-0000-4000-8000-000000000105', '93000000-0000-4000-8000-000000000105', 'ORIGINAL_AUTHORED', 'PROPRIETARY_INTERNAL', 'VerifiedAI Assessment Guild', 'TYT-2026-BANK-SPEC-FRC-001', 'sme.author.ayse.kaya', 'sme.reviewer.mehmet.demir', 'VERIFIED', '2020202020202020202020202020202020202020202020202020202020202005', TIMESTAMPTZ '2026-01-15T10:20:00Z', FALSE),
('93200000-0000-4000-8000-000000000106', '93000000-0000-4000-8000-000000000106', 'ORIGINAL_AUTHORED', 'PROPRIETARY_INTERNAL', 'VerifiedAI Assessment Guild', 'TYT-2026-BANK-SPEC-FRC-002', 'sme.author.ayse.kaya', 'sme.reviewer.mehmet.demir', 'VERIFIED', '2020202020202020202020202020202020202020202020202020202020202006', TIMESTAMPTZ '2026-01-15T10:25:00Z', FALSE),
('93200000-0000-4000-8000-000000000107', '93000000-0000-4000-8000-000000000107', 'ORIGINAL_AUTHORED', 'PROPRIETARY_INTERNAL', 'VerifiedAI Assessment Guild', 'TYT-2026-BANK-SPEC-FRC-003', 'sme.author.ayse.kaya', 'sme.reviewer.mehmet.demir', 'VERIFIED', '2020202020202020202020202020202020202020202020202020202020202007', TIMESTAMPTZ '2026-01-15T10:30:00Z', FALSE),
('93200000-0000-4000-8000-000000000108', '93000000-0000-4000-8000-000000000108', 'ORIGINAL_AUTHORED', 'PROPRIETARY_INTERNAL', 'VerifiedAI Assessment Guild', 'TYT-2026-BANK-SPEC-FRC-004', 'sme.author.ayse.kaya', 'sme.reviewer.mehmet.demir', 'VERIFIED', '2020202020202020202020202020202020202020202020202020202020202008', TIMESTAMPTZ '2026-01-15T10:35:00Z', FALSE),
('93200000-0000-4000-8000-000000000109', '93000000-0000-4000-8000-000000000109', 'SME_CURATED_SYNTHETIC', 'CC_BY_SA_4_0', 'VerifiedAI Assessment Guild', 'TYT-2026-BANK-SPEC-PCT-001', 'sme.author.elena.rostova', 'sme.reviewer.mehmet.demir', 'VERIFIED', '2020202020202020202020202020202020202020202020202020202020202009', TIMESTAMPTZ '2026-01-15T10:40:00Z', FALSE),
('93200000-0000-4000-8000-000000000110', '93000000-0000-4000-8000-000000000110', 'SME_CURATED_SYNTHETIC', 'CC_BY_SA_4_0', 'VerifiedAI Assessment Guild', 'TYT-2026-BANK-SPEC-PCT-002', 'sme.author.elena.rostova', 'sme.reviewer.mehmet.demir', 'VERIFIED', '2020202020202020202020202020202020202020202020202020202020202010', TIMESTAMPTZ '2026-01-15T10:45:00Z', FALSE),
('93200000-0000-4000-8000-000000000111', '93000000-0000-4000-8000-000000000111', 'SME_CURATED_SYNTHETIC', 'CC_BY_SA_4_0', 'VerifiedAI Assessment Guild', 'TYT-2026-BANK-SPEC-PCT-003', 'sme.author.elena.rostova', 'sme.reviewer.mehmet.demir', 'VERIFIED', '2020202020202020202020202020202020202020202020202020202020202011', TIMESTAMPTZ '2026-01-15T10:50:00Z', FALSE),
('93200000-0000-4000-8000-000000000112', '93000000-0000-4000-8000-000000000112', 'SME_CURATED_SYNTHETIC', 'CC_BY_SA_4_0', 'VerifiedAI Assessment Guild', 'TYT-2026-BANK-SPEC-PCT-004', 'sme.author.elena.rostova', 'sme.reviewer.mehmet.demir', 'VERIFIED', '2020202020202020202020202020202020202020202020202020202020202012', TIMESTAMPTZ '2026-01-15T10:55:00Z', FALSE),
('93200000-0000-4000-8000-000000000113', '93000000-0000-4000-8000-000000000113', 'ORIGINAL_AUTHORED', 'PROPRIETARY_INTERNAL', 'VerifiedAI Assessment Guild', 'TYT-2026-BANK-SPEC-FAC-001', 'sme.author.elena.rostova', 'sme.reviewer.ayse.kaya', 'VERIFIED', '2020202020202020202020202020202020202020202020202020202020202013', TIMESTAMPTZ '2026-01-15T11:00:00Z', FALSE),
('93200000-0000-4000-8000-000000000114', '93000000-0000-4000-8000-000000000114', 'ORIGINAL_AUTHORED', 'PROPRIETARY_INTERNAL', 'VerifiedAI Assessment Guild', 'TYT-2026-BANK-SPEC-FAC-002', 'sme.author.elena.rostova', 'sme.reviewer.ayse.kaya', 'VERIFIED', '2020202020202020202020202020202020202020202020202020202020202014', TIMESTAMPTZ '2026-01-15T11:05:00Z', FALSE),
('93200000-0000-4000-8000-000000000115', '93000000-0000-4000-8000-000000000115', 'ORIGINAL_AUTHORED', 'PROPRIETARY_INTERNAL', 'VerifiedAI Assessment Guild', 'TYT-2026-BANK-SPEC-FAC-003', 'sme.author.elena.rostova', 'sme.reviewer.ayse.kaya', 'VERIFIED', '2020202020202020202020202020202020202020202020202020202020202015', TIMESTAMPTZ '2026-01-15T11:10:00Z', FALSE),
('93200000-0000-4000-8000-000000000116', '93000000-0000-4000-8000-000000000116', 'ORIGINAL_AUTHORED', 'PROPRIETARY_INTERNAL', 'VerifiedAI Assessment Guild', 'TYT-2026-BANK-SPEC-FAC-004', 'sme.author.elena.rostova', 'sme.reviewer.ayse.kaya', 'VERIFIED', '2020202020202020202020202020202020202020202020202020202020202016', TIMESTAMPTZ '2026-01-15T11:15:00Z', FALSE)
ON CONFLICT (question_item_id) DO NOTHING;

-- Seed Active Assessment Blueprints (DIAGNOSTIC_BASELINE & SECTION_DRILL)
INSERT INTO exam_assessment_blueprints (
    id, exam_definition_id, exam_version_number, exam_section_id,
    blueprint_code, title, description, blueprint_mode,
    total_question_count, base_duration_seconds,
    easy_target_bps, medium_target_bps, hard_target_bps, expert_target_bps, difficulty_tolerance_bps,
    recall_target_bps, comprehension_target_bps, application_target_bps, analysis_target_bps, evaluation_target_bps,
    allow_recently_seen_cooldown_days, shuffle_questions, shuffle_options,
    status, blueprint_hash, training_eligible
) VALUES
(
    '94000000-0000-4000-8000-000000000101',
    '91000000-0000-4000-8000-000000000101', 1, '92000000-0000-4000-8000-000000000101',
    'BP-TYT-MATH-DIAG-2026-V1',
    'TYT Mathematics Diagnostic Baseline (8 Items)',
    'Deterministic 8-question diagnostic baseline across core arithmetic and algebra foundations.',
    'DIAGNOSTIC_BASELINE',
    8, 900,
    2500, 5000, 2500, 0, 500,
    2500, 2500, 2500, 2500, 0,
    14, TRUE, TRUE,
    'ACTIVE',
    '3030303030303030303030303030303030303030303030303030303030303001',
    FALSE
),
(
    '94000000-0000-4000-8000-000000000102',
    '91000000-0000-4000-8000-000000000101', 1, '92000000-0000-4000-8000-000000000101',
    'BP-TYT-MATH-DRILL-2026-V1',
    'TYT Mathematics Section Drill (10 Items)',
    'Deterministic 10-question timed section drill covering integer operations, fractions, percentages, and factoring.',
    'SECTION_DRILL',
    10, 1200,
    2000, 4000, 3000, 1000, 500,
    2000, 2000, 3000, 2000, 1000,
    14, TRUE, TRUE,
    'ACTIVE',
    '3030303030303030303030303030303030303030303030303030303030303002',
    FALSE
)
ON CONFLICT (blueprint_code) DO NOTHING;

-- Seed Topic Rules for Blueprint 1 (sum = 10000 bps) & Blueprint 2 (sum = 10000 bps)
INSERT INTO exam_blueprint_topic_rules (
    id, blueprint_id, topic_code, target_weight_bps, min_question_count, max_question_count,
    required_item_formats_json, rule_order, training_eligible
) VALUES
-- Blueprint 1: BP-TYT-MATH-DIAG-2026-V1 (8 questions, 4 topics @ 2500 bps = 2 each)
('94100000-0000-4000-8000-000000000101', '94000000-0000-4000-8000-000000000101', 'MATH.ARITHMETIC.INTEGER_OPERATIONS', 2500, 1, 3, '["SINGLE_CHOICE_MCQ"]'::jsonb, 1, FALSE),
('94100000-0000-4000-8000-000000000102', '94000000-0000-4000-8000-000000000101', 'MATH.ARITHMETIC.FRACTIONS', 2500, 1, 3, '["SINGLE_CHOICE_MCQ"]'::jsonb, 2, FALSE),
('94100000-0000-4000-8000-000000000103', '94000000-0000-4000-8000-000000000101', 'MATH.ARITHMETIC.PERCENTAGES', 2500, 1, 3, '["SINGLE_CHOICE_MCQ"]'::jsonb, 3, FALSE),
('94100000-0000-4000-8000-000000000104', '94000000-0000-4000-8000-000000000101', 'MATH.ALGEBRA.FACTORING', 2500, 1, 3, '["SINGLE_CHOICE_MCQ"]'::jsonb, 4, FALSE),
-- Blueprint 2: BP-TYT-MATH-DRILL-2026-V1 (10 questions, 3000+3000+2000+2000 = 10000 bps -> 3+3+2+2 = 10)
('94100000-0000-4000-8000-000000000201', '94000000-0000-4000-8000-000000000102', 'MATH.ARITHMETIC.INTEGER_OPERATIONS', 3000, 2, 4, '["SINGLE_CHOICE_MCQ"]'::jsonb, 1, FALSE),
('94100000-0000-4000-8000-000000000202', '94000000-0000-4000-8000-000000000102', 'MATH.ARITHMETIC.FRACTIONS', 3000, 2, 4, '["SINGLE_CHOICE_MCQ"]'::jsonb, 2, FALSE),
('94100000-0000-4000-8000-000000000203', '94000000-0000-4000-8000-000000000102', 'MATH.ARITHMETIC.PERCENTAGES', 2000, 1, 3, '["SINGLE_CHOICE_MCQ"]'::jsonb, 3, FALSE),
('94100000-0000-4000-8000-000000000204', '94000000-0000-4000-8000-000000000102', 'MATH.ALGEBRA.FACTORING', 2000, 1, 3, '["SINGLE_CHOICE_MCQ"]'::jsonb, 4, FALSE)
ON CONFLICT (blueprint_id, topic_code) DO NOTHING;
