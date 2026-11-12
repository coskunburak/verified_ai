-- ============================================================================
-- Flyway Migration V046: Spaced Repetition Queue & 7-Day Trap Immunity Checks
-- Sprint 8.3 (DOC-86 §4.4, DOC-83 §5.1, G-PED-04)
-- Invariants: INV-SR-001..006
-- ============================================================================

CREATE TABLE IF NOT EXISTS spaced_review_queue (
    id                      UUID PRIMARY KEY,
    user_id                 UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    canonical_skill_code    VARCHAR(96) NOT NULL,
    target_mistake_code     VARCHAR(64),
    trigger_type            VARCHAR(32) NOT NULL
        CONSTRAINT chk_spaced_review_trigger_type
        CHECK (trigger_type IN ('RETENTION_DECAY', 'TRAP_IMMUNITY_7D')),
    estimated_retention     NUMERIC(5, 4) NOT NULL DEFAULT 0.7200
        CONSTRAINT chk_spaced_review_retention_bounds
        CHECK (estimated_retention >= 0.0500 AND estimated_retention <= 1.0000),
    half_life_days          NUMERIC(6, 2) NOT NULL DEFAULT 3.00
        CONSTRAINT chk_spaced_review_half_life_bounds
        CHECK (half_life_days >= 0.50 AND half_life_days <= 90.00),
    due_at                  TIMESTAMPTZ NOT NULL,
    assigned_twin_id        UUID,
    prompt_preview_latex    TEXT NOT NULL DEFAULT '',
    status                  VARCHAR(32) NOT NULL DEFAULT 'PENDING'
        CONSTRAINT chk_spaced_review_status
        CHECK (status IN ('PENDING', 'SERVED', 'COMPLETED', 'DISMISSED', 'EXPIRED')),
    completed_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_spaced_review_trap_code_required
        CHECK (
            (trigger_type = 'TRAP_IMMUNITY_7D' AND target_mistake_code IS NOT NULL)
            OR (trigger_type = 'RETENTION_DECAY')
        ),
    CONSTRAINT chk_spaced_review_completed_timestamp
        CHECK (
            (status = 'COMPLETED' AND completed_at IS NOT NULL)
            OR (status <> 'COMPLETED')
        )
);

-- BOLA & Priority Queue Lookup Index (INV-SR-004, INV-SR-006)
CREATE INDEX IF NOT EXISTS idx_spaced_review_queue_user_status_due
    ON spaced_review_queue (user_id, status, due_at ASC);

-- Unique Active Pending Review Index (prevents duplicate pending items per skill/trigger/mistake)
CREATE UNIQUE INDEX IF NOT EXISTS uq_spaced_review_active_pending
    ON spaced_review_queue (user_id, canonical_skill_code, trigger_type, COALESCE(target_mistake_code, 'NONE'))
    WHERE status IN ('PENDING', 'SERVED');

-- Fast Single-Query Privacy Purge Index
CREATE INDEX IF NOT EXISTS idx_spaced_review_queue_user_purge
    ON spaced_review_queue (user_id);
