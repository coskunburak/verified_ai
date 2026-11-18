-- H-15 private deterministic snapshots. Earlier migrations remain immutable.
CREATE TABLE pre_exam_study_sessions (
    session_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    exam_code VARCHAR(64) NOT NULL CHECK (exam_code IN ('TR_YKS_TYT_MATH', 'TR_YKS_AYT_MATH')),
    target_exam_date DATE,
    target_exam_at TIMESTAMPTZ,
    time_precision VARCHAR(32) NOT NULL CHECK (time_precision IN ('EXACT', 'DATE_ONLY_ESTIMATE', 'UNAVAILABLE')),
    window_status VARCHAR(32) NOT NULL CHECK (window_status IN ('ACTIVE_48H_WINDOW', 'EARLY_PREVIEW_OUTSIDE_48H', 'DATE_ONLY_ESTIMATE', 'NO_TARGET_DATE', 'EXAM_PASSED')),
    evidence_status VARCHAR(32) NOT NULL CHECK (evidence_status IN ('SUFFICIENT_EVIDENCE', 'INSUFFICIENT_EVIDENCE')),
    hours_remaining NUMERIC(12,4) CHECK (hours_remaining >= 0),
    panic_inhibition_active BOOLEAN NOT NULL DEFAULT TRUE CHECK (panic_inhibition_active),
    mastery_floor NUMERIC(5,4) NOT NULL DEFAULT 0.5000 CHECK (mastery_floor = 0.5000),
    excluded_low_mastery_topic_count INTEGER NOT NULL CHECK (excluded_low_mastery_topic_count >= 0),
    active_trap_count INTEGER NOT NULL CHECK (active_trap_count BETWEEN 0 AND 10),
    formula_count INTEGER NOT NULL CHECK (formula_count BETWEEN 0 AND 12),
    source_fingerprint VARCHAR(64) NOT NULL CHECK (source_fingerprint ~ '^[0-9a-f]{64}$'),
    content_fingerprint VARCHAR(64) NOT NULL CHECK (content_fingerprint ~ '^[0-9a-f]{64}$'),
    algorithm_version VARCHAR(32) NOT NULL,
    catalog_version VARCHAR(32) NOT NULL,
    schema_version VARCHAR(32) NOT NULL DEFAULT 'pre-exam-review-v1' CHECK (schema_version = 'pre-exam-review-v1'),
    review_payload JSONB NOT NULL CHECK (jsonb_typeof(review_payload) = 'object'),
    cheat_sheet_payload JSONB NOT NULL CHECK (jsonb_typeof(cheat_sheet_payload) = 'object'),
    payload_etag VARCHAR(64) NOT NULL CHECK (payload_etag ~ '^[0-9a-f]{64}$'),
    sheet_etag VARCHAR(64) NOT NULL CHECK (sheet_etag ~ '^[0-9a-f]{64}$'),
    llm_tokens_used INTEGER NOT NULL DEFAULT 0 CHECK (llm_tokens_used = 0),
    training_eligible BOOLEAN NOT NULL DEFAULT FALSE CHECK (training_eligible = FALSE),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,
    CHECK (expires_at > updated_at AND updated_at >= created_at),
    CHECK (expires_at <= created_at + INTERVAL '24 hours'),
    CHECK ((time_precision = 'EXACT' AND target_exam_at IS NOT NULL AND hours_remaining IS NOT NULL)
        OR (time_precision <> 'EXACT' AND target_exam_at IS NULL AND hours_remaining IS NULL)),
    CHECK (window_status NOT IN ('ACTIVE_48H_WINDOW', 'EARLY_PREVIEW_OUTSIDE_48H') OR time_precision = 'EXACT'),
    CHECK (window_status <> 'ACTIVE_48H_WINDOW' OR (hours_remaining > 0 AND hours_remaining <= 48)),
    CHECK (window_status <> 'EARLY_PREVIEW_OUTSIDE_48H' OR hours_remaining > 48),
    CHECK (time_precision <> 'DATE_ONLY_ESTIMATE' OR target_exam_date IS NOT NULL),
    CHECK (window_status <> 'NO_TARGET_DATE' OR (target_exam_date IS NULL AND time_precision = 'UNAVAILABLE')),
    UNIQUE (user_id, exam_code, payload_etag),
    UNIQUE (session_id, user_id)
);
CREATE INDEX idx_pre_exam_study_sessions_user_exam ON pre_exam_study_sessions (user_id, exam_code, updated_at DESC);
CREATE INDEX idx_pre_exam_study_sessions_expiry ON pre_exam_study_sessions (expires_at);
CREATE TABLE pre_exam_export_events (
    session_id UUID NOT NULL,
    user_id UUID NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL CHECK (length(trim(idempotency_key)) > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, session_id, idempotency_key),
    FOREIGN KEY (session_id, user_id) REFERENCES pre_exam_study_sessions (session_id, user_id) ON DELETE CASCADE
);
COMMENT ON TABLE pre_exam_study_sessions IS 'STUDENT_CONFIDENTIAL; H-15; account deletion and 24h TTL; never training data';
COMMENT ON TABLE pre_exam_export_events IS 'Owner-scoped export deduplication; no PDF content; cascades with snapshot';
