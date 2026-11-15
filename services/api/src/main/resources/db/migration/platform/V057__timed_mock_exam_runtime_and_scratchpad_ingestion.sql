-- ============================================================================
-- V057: Sprint 9.4 Timed Mock Exam Runtime, Server-Authoritative Clock &
--       Photographed Deneme Scratchpad Ingestion (F28)
-- Phase 09 / CNT-LOS-10 / INV-EXAM-003 / ADR-005 / ADR-006 / ADR-011
-- Reconciled with V056 exam_assembled_assessments, exam_assessment_blueprints,
-- and exam_question_bank_items.
-- ============================================================================

CREATE TABLE IF NOT EXISTS exam_sessions (
    id                              UUID PRIMARY KEY,
    user_id                         UUID NOT NULL,
    assembly_id                     UUID NOT NULL REFERENCES exam_assembled_assessments(id) ON DELETE CASCADE,
    blueprint_id                    UUID NOT NULL REFERENCES exam_assessment_blueprints(id) ON DELETE RESTRICT,
    exam_code                       VARCHAR(64) NOT NULL,
    exam_version_number             INTEGER NOT NULL CHECK (exam_version_number >= 1),
    status                          VARCHAR(32) NOT NULL CHECK (status IN (
        'IN_PROGRESS',
        'OFFLINE_GRACE_BUFFER',
        'SUBMITTED',
        'EXPIRED_AUTO_SUBMITTED',
        'ABANDONED'
    )),
    navigation_policy               VARCHAR(32) NOT NULL CHECK (navigation_policy IN (
        'FREE_NAVIGATION',
        'SECTION_LOCKED'
    )),
    no_hint_policy_enforced         BOOLEAN NOT NULL DEFAULT TRUE CHECK (no_hint_policy_enforced = TRUE),
    accommodations_snapshot         JSONB NOT NULL,
    base_duration_seconds           INTEGER NOT NULL CHECK (base_duration_seconds BETWEEN 60 AND 21600),
    extra_time_multiplier           NUMERIC(4,2) NOT NULL CHECK (extra_time_multiplier IN (1.00, 1.25, 1.50, 2.00)),
    effective_duration_seconds      INTEGER NOT NULL CHECK (effective_duration_seconds BETWEEN 60 AND 32400),
    offline_grace_buffer_seconds    INTEGER NOT NULL DEFAULT 120 CHECK (offline_grace_buffer_seconds BETWEEN 0 AND 120),
    started_at                      TIMESTAMPTZ NOT NULL,
    server_deadline_at              TIMESTAMPTZ NOT NULL,
    grace_deadline_at               TIMESTAMPTZ NOT NULL,
    last_heartbeat_at               TIMESTAMPTZ NOT NULL,
    offline_detected_at             TIMESTAMPTZ,
    submitted_at                    TIMESTAMPTZ,
    submission_reason               VARCHAR(32) CHECK (submission_reason IS NULL OR submission_reason IN (
        'USER_INITIATED',
        'SERVER_DEADLINE_EXPIRED',
        'OFFLINE_GRACE_EXHAUSTED',
        'USER_ABANDONED'
    )),
    submit_idempotency_key          VARCHAR(128),
    total_item_count                INTEGER NOT NULL CHECK (total_item_count > 0),
    answered_item_count             INTEGER NOT NULL DEFAULT 0 CHECK (answered_item_count >= 0),
    flagged_item_count              INTEGER NOT NULL DEFAULT 0 CHECK (flagged_item_count >= 0),
    omitted_item_count              INTEGER NOT NULL DEFAULT 0 CHECK (omitted_item_count >= 0),
    scratchpad_attachment_count     INTEGER NOT NULL DEFAULT 0 CHECK (scratchpad_attachment_count >= 0),
    client_clock_skew_ms            BIGINT NOT NULL DEFAULT 0,
    f28_verification_batch_manifest JSONB,
    training_eligible               BOOLEAN NOT NULL DEFAULT FALSE CHECK (training_eligible = FALSE),
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_exam_sessions_deadlines CHECK (
        server_deadline_at > started_at AND grace_deadline_at >= server_deadline_at
    ),
    CONSTRAINT chk_exam_sessions_item_counts CHECK (
        answered_item_count <= total_item_count
        AND flagged_item_count <= total_item_count
        AND omitted_item_count <= total_item_count
    )
);

CREATE INDEX IF NOT EXISTS idx_exam_sessions_user_status
    ON exam_sessions(user_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_exam_sessions_user_assembly
    ON exam_sessions(user_id, assembly_id, status);

CREATE UNIQUE INDEX IF NOT EXISTS idx_exam_sessions_active_user
    ON exam_sessions(user_id, assembly_id)
    WHERE status IN ('IN_PROGRESS', 'OFFLINE_GRACE_BUFFER');

CREATE TABLE IF NOT EXISTS exam_session_items (
    id                      UUID PRIMARY KEY,
    session_id              UUID NOT NULL REFERENCES exam_sessions(id) ON DELETE CASCADE,
    item_position           INTEGER NOT NULL CHECK (item_position >= 1),
    question_item_id        UUID NOT NULL REFERENCES exam_question_bank_items(id) ON DELETE RESTRICT,
    question_code           VARCHAR(64) NOT NULL,
    section_code            VARCHAR(64) NOT NULL,
    section_order           INTEGER NOT NULL CHECK (section_order >= 1),
    topic_code              VARCHAR(128) NOT NULL,
    item_format             VARCHAR(32) NOT NULL CHECK (item_format IN (
        'SINGLE_CHOICE_MCQ',
        'MULTI_SELECT_MCQ',
        'NUMERIC_GRID_IN',
        'SHORT_STRUCTURED_RESPONSE',
        'SINGLE_CHOICE_4_OPTION',
        'SINGLE_CHOICE_5_OPTION'
    )),
    difficulty_band         VARCHAR(16) NOT NULL CHECK (difficulty_band IN (
        'EASY', 'MEDIUM', 'HARD', 'EXPERT'
    )),
    stem_markdown           TEXT NOT NULL,
    options_snapshot        JSONB NOT NULL,
    figure_alt_text         TEXT,
    max_points              NUMERIC(6,2) NOT NULL DEFAULT 1.00 CHECK (max_points > 0),
    negative_marking_weight NUMERIC(5,4) NOT NULL DEFAULT 0.2500 CHECK (negative_marking_weight BETWEEN 0.0000 AND 1.0000),
    response_state          VARCHAR(24) NOT NULL DEFAULT 'UNVISITED' CHECK (response_state IN (
        'UNVISITED',
        'VIEWED_OMITTED',
        'ANSWERED',
        'FLAGGED_ANSWERED',
        'FLAGGED_OMITTED'
    )),
    selected_option_key     VARCHAR(8) CHECK (selected_option_key IS NULL OR selected_option_key IN ('A', 'B', 'C', 'D', 'E')),
    numeric_response_value  NUMERIC(18,6),
    flagged_for_review      BOOLEAN NOT NULL DEFAULT FALSE,
    eliminated_option_keys  JSONB NOT NULL DEFAULT '[]'::jsonb,
    time_spent_seconds      INTEGER NOT NULL DEFAULT 0 CHECK (time_spent_seconds >= 0 AND time_spent_seconds <= 32400),
    autosave_version        INTEGER NOT NULL DEFAULT 0 CHECK (autosave_version >= 0),
    client_autosave_seq     BIGINT NOT NULL DEFAULT 0 CHECK (client_autosave_seq >= 0),
    last_autosaved_at       TIMESTAMPTZ,
    scratchpad_required     BOOLEAN NOT NULL DEFAULT FALSE,
    training_eligible       BOOLEAN NOT NULL DEFAULT FALSE CHECK (training_eligible = FALSE),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (session_id, item_position),
    UNIQUE (session_id, question_item_id)
);

CREATE INDEX IF NOT EXISTS idx_exam_session_items_session_pos
    ON exam_session_items(session_id, item_position);

CREATE INDEX IF NOT EXISTS idx_exam_session_items_session_section
    ON exam_session_items(session_id, section_order, item_position);

CREATE TABLE IF NOT EXISTS exam_session_scratchpad_assets (
    id                      UUID PRIMARY KEY,
    session_id              UUID NOT NULL REFERENCES exam_sessions(id) ON DELETE CASCADE,
    session_item_id         UUID NOT NULL REFERENCES exam_session_items(id) ON DELETE CASCADE,
    user_id                 UUID NOT NULL,
    item_position           INTEGER NOT NULL CHECK (item_position >= 1),
    page_number             INTEGER NOT NULL DEFAULT 1 CHECK (page_number BETWEEN 1 AND 4),
    storage_key             VARCHAR(512) NOT NULL,
    mime_type               VARCHAR(64) NOT NULL CHECK (mime_type IN (
        'image/jpeg',
        'image/png',
        'image/heic'
    )),
    byte_size               BIGINT NOT NULL CHECK (byte_size > 0 AND byte_size <= 15728640),
    sha256_checksum         CHAR(64) NOT NULL CHECK (sha256_checksum ~ '^[0-9a-f]{64}$'),
    width_px                INTEGER CHECK (width_px IS NULL OR (width_px BETWEEN 200 AND 12000)),
    height_px               INTEGER CHECK (height_px IS NULL OR (height_px BETWEEN 200 AND 12000)),
    capture_source          VARCHAR(32) NOT NULL CHECK (capture_source IN (
        'DIGITAL_MOCK_SCRATCHPAD',
        'PAPER_DENEME_BOOKLET'
    )),
    booklet_publisher       VARCHAR(120),
    f28_verification_status VARCHAR(32) NOT NULL DEFAULT 'QUEUED_FOR_POST_SUBMIT_BATCH' CHECK (f28_verification_status IN (
        'QUEUED_FOR_POST_SUBMIT_BATCH',
        'NOT_APPLICABLE'
    )),
    training_eligible       BOOLEAN NOT NULL DEFAULT FALSE CHECK (training_eligible = FALSE),
    captured_at             TIMESTAMPTZ NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (session_item_id, page_number)
);

CREATE INDEX IF NOT EXISTS idx_exam_scratchpad_session_item
    ON exam_session_scratchpad_assets(session_id, item_position, page_number);

CREATE INDEX IF NOT EXISTS idx_exam_scratchpad_user
    ON exam_session_scratchpad_assets(user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS exam_session_idempotency_records (
    id                   UUID PRIMARY KEY,
    session_id           UUID NOT NULL REFERENCES exam_sessions(id) ON DELETE CASCADE,
    user_id              UUID NOT NULL,
    operation_type       VARCHAR(32) NOT NULL CHECK (operation_type IN (
        'ITEM_AUTOSAVE',
        'SCRATCHPAD_ATTACH',
        'SESSION_SUBMIT'
    )),
    idempotency_key      VARCHAR(128) NOT NULL,
    request_payload_hash CHAR(64) NOT NULL CHECK (request_payload_hash ~ '^[0-9a-f]{64}$'),
    response_status_code INTEGER NOT NULL,
    response_snapshot    JSONB NOT NULL,
    training_eligible    BOOLEAN NOT NULL DEFAULT FALSE CHECK (training_eligible = FALSE),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (session_id, operation_type, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_exam_session_idemp_lookup
    ON exam_session_idempotency_records(session_id, operation_type, idempotency_key);
