-- ============================================================================
-- Flyway Migration V048: Missed-Day Recovery & Study Plan Rebalancing
-- Sprint 8.5 (DOC-85 Screens 1 & 8, DOC-86, H-13, INV-REBAL-001..006)
-- Enforces non-punitive anti-backlog recovery and cognitive-load bounded daily budgets.
-- ============================================================================

CREATE TABLE IF NOT EXISTS study_plan_rebalance_events (
    id                        UUID         PRIMARY KEY,
    user_id                   UUID         NOT NULL,
    study_plan_id             UUID         NULL,
    recovery_date             DATE         NOT NULL,
    trigger_type              VARCHAR(32)  NOT NULL,
    missed_days_count         INT          NOT NULL,
    days_until_exam           INT          NOT NULL,
    previous_daily_budget     INT          NOT NULL,
    rebalanced_daily_budget   INT          NOT NULL,
    pruned_tasks_count        INT          NOT NULL DEFAULT 0,
    rescheduled_reviews_count INT          NOT NULL DEFAULT 0,
    active_today_tasks_count  INT          NOT NULL DEFAULT 1,
    mini_mode_activated       BOOLEAN      NOT NULL DEFAULT FALSE,
    prune_reason_code         VARCHAR(48)  NOT NULL DEFAULT 'NONE',
    pruned_concept_ids_json   JSONB        NOT NULL DEFAULT '[]'::jsonb,
    idempotency_key           VARCHAR(128) NOT NULL,
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_rebalance_trigger_type CHECK (
        trigger_type IN ('MISSED_DAYS', 'PACE_CHANGE', 'EXAM_DATE_SHIFT', 'MANUAL_RESET', 'MINI_MODE_DOWNSHIFT')
    ),
    CONSTRAINT chk_rebalance_missed_days_non_negative CHECK (
        missed_days_count >= 0 AND missed_days_count <= 365
    ),
    CONSTRAINT chk_rebalance_days_until_exam CHECK (
        days_until_exam >= 1 AND days_until_exam <= 730
    ),
    CONSTRAINT chk_rebalance_prev_budget CHECK (
        previous_daily_budget BETWEEN 5 AND 25
    ),
    CONSTRAINT chk_rebalance_new_budget CHECK (
        rebalanced_daily_budget BETWEEN 5 AND 25
    ),
    CONSTRAINT chk_rebalance_pruned_non_negative CHECK (
        pruned_tasks_count >= 0
    ),
    CONSTRAINT chk_rebalance_rescheduled_non_negative CHECK (
        rescheduled_reviews_count >= 0
    ),
    CONSTRAINT chk_rebalance_active_today_cap CHECK (
        active_today_tasks_count BETWEEN 1 AND 3
    ),
    CONSTRAINT chk_rebalance_prune_reason CHECK (
        prune_reason_code IN (
            'NONE',
            'MISSED_DAY_WINDOW_RESET',
            'BUDGET_CEILING_LOW_ROI_PRUNED',
            'DAILY_MISSION_CAP_ENFORCED',
            'MINI_MODE_SINGLE_FOCUS'
        )
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_rebalance_events_user_idempotency
    ON study_plan_rebalance_events (user_id, idempotency_key);

CREATE INDEX IF NOT EXISTS idx_rebalance_events_user_created
    ON study_plan_rebalance_events (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_rebalance_events_user_recovery_date
    ON study_plan_rebalance_events (user_id, recovery_date DESC);

-- Harmonize daily_coach_sessions budget options {5, 10, 15, 20, 25} and prior-day expiration status
ALTER TABLE daily_coach_sessions
    DROP CONSTRAINT IF EXISTS chk_daily_coach_budget_minutes;

ALTER TABLE daily_coach_sessions
    DROP CONSTRAINT IF EXISTS chk_coach_session_budget_minutes;

ALTER TABLE daily_coach_sessions
    ADD CONSTRAINT chk_coach_session_budget_minutes
    CHECK (budget_minutes IN (5, 10, 15, 20, 25));

ALTER TABLE daily_coach_sessions
    DROP CONSTRAINT IF EXISTS chk_daily_coach_session_status;

ALTER TABLE daily_coach_sessions
    ADD CONSTRAINT chk_daily_coach_session_status
    CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'EXPIRED'));
