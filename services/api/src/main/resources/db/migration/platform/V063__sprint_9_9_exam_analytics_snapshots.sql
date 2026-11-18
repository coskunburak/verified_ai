-- ============================================================================
-- V063: Sprint 9.9 Longitudinal Exam Progress Analytics, Topic-Level Pacing &
--       Trap Remediation Correlation Snapshots (INV-EXAM-005)
-- Phase 09 / ADR-005 ($0.00 deterministic) / ADR-006 (training_eligible=FALSE) / ADR-011
-- ============================================================================

CREATE TABLE exam_analytics_snapshots (
    snapshot_id             UUID PRIMARY KEY,
    user_id                 UUID NOT NULL,
    exam_code               VARCHAR(64) NOT NULL CHECK (exam_code IN ('TR_YKS_TYT_MATH', 'TR_YKS_AYT_MATH')),
    mock_count              INTEGER NOT NULL CHECK (mock_count BETWEEN 0 AND 20),
    net_trend_json          JSONB NOT NULL CHECK (jsonb_typeof(net_trend_json) = 'array'),
    pacing_stats_json       JSONB NOT NULL CHECK (jsonb_typeof(pacing_stats_json) = 'array'),
    trap_correlation_json   JSONB NOT NULL CHECK (jsonb_typeof(trap_correlation_json) = 'object'),
    conquered_traps_count   INTEGER NOT NULL CHECK (conquered_traps_count >= 0),
    as_of_date              DATE NOT NULL,
    source_fingerprint      CHAR(64) NOT NULL CHECK (source_fingerprint ~ '^[0-9a-f]{64}$'),
    algorithm_version       VARCHAR(32) NOT NULL CHECK (length(btrim(algorithm_version)) > 0),
    source_last_score_id    UUID NULL REFERENCES exam_session_scores(score_id) ON DELETE CASCADE,
    schema_version          VARCHAR(48) NOT NULL DEFAULT 'exam-analytics-progress-v1'
                            CHECK (schema_version = 'exam-analytics-progress-v1'),
    generated_at            TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    expires_at              TIMESTAMPTZ NOT NULL,
    training_eligible       BOOLEAN NOT NULL DEFAULT FALSE CHECK (training_eligible = FALSE),
    llm_tokens_used         INTEGER NOT NULL DEFAULT 0 CHECK (llm_tokens_used = 0),
    inference_cost_usd      NUMERIC(10,6) NOT NULL DEFAULT 0.000000 CHECK (inference_cost_usd = 0.000000),
    CONSTRAINT chk_exam_analytics_snapshots_timestamps
        CHECK (expires_at > updated_at AND updated_at >= generated_at),
    CONSTRAINT chk_exam_analytics_snapshots_ttl_bound
        CHECK (expires_at <= generated_at + INTERVAL '24 hours'),
    CONSTRAINT chk_exam_analytics_snapshots_last_score_consistency
        CHECK ((mock_count = 0 AND source_last_score_id IS NULL)
            OR (mock_count > 0 AND source_last_score_id IS NOT NULL)),
    CONSTRAINT uq_exam_analytics_snapshots_owner_source
        UNIQUE (user_id, exam_code, source_fingerprint, algorithm_version)
);

CREATE INDEX idx_exam_analytics_snapshots_user_exam_gen
    ON exam_analytics_snapshots (user_id, exam_code, generated_at DESC);

CREATE INDEX idx_exam_analytics_snapshots_expires_at
    ON exam_analytics_snapshots (expires_at);

COMMENT ON TABLE exam_analytics_snapshots IS
    'STUDENT_CONFIDENTIAL; Sprint 9.9 longitudinal exam progress snapshots; 24h TTL and account deletion purge; never training data (training_eligible=FALSE, llm_tokens_used=0).';
