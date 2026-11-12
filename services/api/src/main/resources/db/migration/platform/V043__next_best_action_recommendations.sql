-- ============================================================================
-- Flyway Migration V043: Next-Best-Action (NBA) Recommendations
-- Sprint 8.1 — Adaptive Learning, Study Planning & Personal AI Mathematics Coach
-- Enforces: INV-COACH-001, INV-COACH-002, INV-COACH-003, INV-COACH-004
-- ============================================================================

CREATE TABLE IF NOT EXISTS learner_recommendations (
    id                    UUID PRIMARY KEY,
    user_id               UUID NOT NULL,
    recommendation_type   VARCHAR(32) NOT NULL
        CONSTRAINT chk_recommendation_type
        CHECK (recommendation_type IN ('MISTAKE_REPAIR', 'SPACED_RETENTION', 'PREREQUISITE_REPAIR', 'HIGH_ROI_TOPIC')),
    target_skill_code     VARCHAR(128) NOT NULL,
    target_mistake_code   VARCHAR(64) NULL,
    priority_score        NUMERIC(8,4) NOT NULL
        CONSTRAINT chk_priority_score_range
        CHECK (priority_score >= 0.0000 AND priority_score <= 100.0000),
    rationale_code        VARCHAR(64) NOT NULL
        CONSTRAINT chk_recommendation_rationale_code
        CHECK (rationale_code IN ('ACTIVE_RECURRING_TRAP', 'RETENTION_HALF_LIFE_DUE', 'PREREQUISITE_REPAIR', 'HIGH_EXAM_NET_ROI')),
    rationale_params_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    status                VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'
        CONSTRAINT chk_recommendation_status
        CHECK (status IN ('ACTIVE', 'ACCEPTED', 'DISMISSED', 'EXPIRED')),
    expires_at            TIMESTAMPTZ NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_recommendations_user_status_priority
    ON learner_recommendations (user_id, status, priority_score DESC, target_skill_code ASC);

CREATE INDEX IF NOT EXISTS idx_recommendations_expires_at
    ON learner_recommendations (expires_at);

COMMENT ON TABLE learner_recommendations IS
    'Deterministic Next-Best-Action recommendation queue prioritizing active traps, memory retention decay, prerequisite bottlenecks, and exam ROI (INV-COACH-001).';
