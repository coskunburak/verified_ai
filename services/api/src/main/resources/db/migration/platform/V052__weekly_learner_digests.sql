-- Sprint 8.9: Weekly Learning Report, Explainable Recommendations & Privacy-Safe Share Packet Read-Model Projection
-- Enforces strict ADR-006 non-training boundary (training_eligible = FALSE), BOLA user scoping, and ISO-8601 week determinism.

CREATE TABLE weekly_learner_digests (
    digest_id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    week_iso VARCHAR(8) NOT NULL,
    week_start_date DATE NOT NULL,
    week_end_date DATE NOT NULL,
    locale VARCHAR(8) NOT NULL DEFAULT 'en',
    status VARCHAR(24) NOT NULL,
    solved_attempts_count INTEGER NOT NULL DEFAULT 0,
    independent_twins_solved_count INTEGER NOT NULL DEFAULT 0,
    study_minutes INTEGER NOT NULL DEFAULT 0,
    active_days_count INTEGER NOT NULL DEFAULT 0,
    digest_payload_json JSONB NOT NULL,
    explainable_recommendations_json JSONB NOT NULL,
    training_eligible BOOLEAN NOT NULL DEFAULT FALSE,
    generated_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_weekly_learner_digests_user_week UNIQUE (user_id, week_iso),
    CONSTRAINT chk_weekly_learner_digests_week_iso CHECK (week_iso ~ '^[0-9]{4}-W(0[1-9]|[1-4][0-9]|5[0-3])$'),
    CONSTRAINT chk_weekly_learner_digests_date_order CHECK (week_end_date >= week_start_date AND week_end_date = week_start_date + 6),
    CONSTRAINT chk_weekly_learner_digests_locale CHECK (locale IN ('en', 'tr')),
    CONSTRAINT chk_weekly_learner_digests_status CHECK (status IN ('GENERATED', 'EMPTY_WEEK', 'DEGRADED')),
    CONSTRAINT chk_weekly_learner_digests_counts CHECK (
        solved_attempts_count >= 0
        AND independent_twins_solved_count >= 0
        AND study_minutes >= 0
        AND active_days_count BETWEEN 0 AND 7
    ),
    CONSTRAINT chk_weekly_learner_digests_json_types CHECK (
        jsonb_typeof(digest_payload_json) = 'object'
        AND octet_length(digest_payload_json::text) <= 65536
        AND jsonb_typeof(explainable_recommendations_json) = 'array'
        AND octet_length(explainable_recommendations_json::text) <= 32768
    ),
    CONSTRAINT chk_weekly_learner_digests_training_false CHECK (training_eligible = FALSE)
);

CREATE INDEX idx_weekly_learner_digests_user_generated
    ON weekly_learner_digests(user_id, generated_at DESC);

CREATE INDEX idx_weekly_learner_digests_week_iso
    ON weekly_learner_digests(week_iso);

COMMENT ON TABLE weekly_learner_digests IS
    'Privacy-safe weekly learning digest projection per learner and ISO week (Sprint 8.9). Never contains raw TutorTurns, uncropped media, or training-eligible payloads.';
