-- ============================================================================
-- Flyway Migration V047: Daily Coach Sessions & Deterministic Study Plan Tasks
-- Sprint 8.4 (DOC-85 Screen 1 & Screen 8, DOC-90, H-13, H-14, ADR-011)
-- Invariants: INV-COACH-001..006
-- ============================================================================

CREATE TABLE IF NOT EXISTS daily_coach_sessions (
    id                    UUID        PRIMARY KEY,
    user_id               UUID        NOT NULL,
    local_date            DATE        NOT NULL,
    budget_minutes        INT         NOT NULL DEFAULT 15
        CONSTRAINT chk_daily_coach_budget_minutes
        CHECK (budget_minutes IN (5, 10, 15, 25)),
    status                VARCHAR(32) NOT NULL DEFAULT 'IN_PROGRESS'
        CONSTRAINT chk_daily_coach_session_status
        CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    is_downshifted        BOOLEAN     NOT NULL DEFAULT FALSE,
    is_cold_start         BOOLEAN     NOT NULL DEFAULT FALSE,
    completed_tasks_count INT         NOT NULL DEFAULT 0
        CONSTRAINT chk_daily_coach_completed_count
        CHECK (completed_tasks_count >= 0),
    total_tasks_count     INT         NOT NULL DEFAULT 3
        CONSTRAINT chk_daily_coach_total_count
        CHECK (total_tasks_count BETWEEN 1 AND 5),
    briefing_text         TEXT        NOT NULL,
    preferred_mode        VARCHAR(32) NOT NULL DEFAULT 'SOCRATIC_COACH_MODE'
        CONSTRAINT chk_daily_coach_preferred_mode
        CHECK (preferred_mode IN ('SOLVER_MODE', 'SOCRATIC_COACH_MODE')),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_daily_coach_sessions_user_date UNIQUE (user_id, local_date)
);

CREATE TABLE IF NOT EXISTS daily_coach_tasks (
    id                      UUID         PRIMARY KEY,
    session_id              UUID         NOT NULL REFERENCES daily_coach_sessions(id) ON DELETE CASCADE,
    task_order              INT          NOT NULL
        CONSTRAINT chk_daily_coach_task_order
        CHECK (task_order BETWEEN 1 AND 5),
    task_type               VARCHAR(32)  NOT NULL
        CONSTRAINT chk_daily_coach_task_type
        CHECK (task_type IN ('TRAP_REPAIR', 'SPACED_RETENTION', 'EXAM_SYNTHESIS')),
    target_skill_code       VARCHAR(128) NOT NULL,
    target_mistake_code     VARCHAR(128),
    assigned_twin_id        VARCHAR(128) NOT NULL,
    estimated_minutes       INT          NOT NULL DEFAULT 5
        CONSTRAINT chk_daily_coach_estimated_minutes
        CHECK (estimated_minutes BETWEEN 1 AND 15),
    why_this_task_rationale TEXT         NOT NULL,
    prompt_preview_latex    TEXT         NOT NULL,
    status                  VARCHAR(32)  NOT NULL DEFAULT 'PENDING'
        CONSTRAINT chk_daily_coach_task_status
        CHECK (status IN ('PENDING', 'COMPLETED', 'SKIPPED')),
    completed_at            TIMESTAMPTZ,
    CONSTRAINT uq_daily_coach_tasks_session_order UNIQUE (session_id, task_order)
);

-- BOLA & Daily Idempotency Index (INV-COACH-004, INV-COACH-005)
CREATE INDEX IF NOT EXISTS idx_daily_coach_session_user_date
    ON daily_coach_sessions (user_id, local_date);

-- Fast Task Lookup by Session
CREATE INDEX IF NOT EXISTS idx_daily_coach_tasks_session
    ON daily_coach_tasks (session_id);
