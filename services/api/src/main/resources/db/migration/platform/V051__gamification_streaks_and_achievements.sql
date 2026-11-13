-- ============================================================================
-- V051: Verified Independent Streaks, Shame-Free Habit Maintenance &
--       Pedagogical Achievements (Sprint 8.8 / DOC-90 §2.5)
-- ============================================================================

CREATE TABLE learner_streaks (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    current_streak_days INTEGER NOT NULL DEFAULT 0
        CHECK (current_streak_days >= 0),
    longest_streak_days INTEGER NOT NULL DEFAULT 0
        CHECK (longest_streak_days >= 0 AND longest_streak_days >= current_streak_days),
    streak_freezes_remaining INTEGER NOT NULL DEFAULT 2
        CHECK (streak_freezes_remaining BETWEEN 0 AND 2),
    freezes_used_this_month INTEGER NOT NULL DEFAULT 0
        CHECK (freezes_used_this_month BETWEEN 0 AND 2),
    freeze_quota_month VARCHAR(7) NOT NULL
        CHECK (freeze_quota_month ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    learner_timezone VARCHAR(64) NOT NULL DEFAULT 'UTC',
    last_verified_independent_date DATE,
    last_activity_local_date DATE,
    last_activity_evidence_type VARCHAR(32)
        CHECK (last_activity_evidence_type IS NULL OR last_activity_evidence_type IN (
            'INDEPENDENT',
            'HINT_ASSISTED',
            'SOLUTION_REVEALED_ASSISTED'
        )),
    streak_status VARCHAR(32) NOT NULL DEFAULT 'FRESH_START_READY'
        CHECK (streak_status IN (
            'ACTIVE_VERIFIED_TODAY',
            'CONTINUITY_PRESERVED_TODAY',
            'FREEZE_PROTECTED',
            'AT_RISK_TODAY',
            'FRESH_START_READY'
        )),
    total_independent_days INTEGER NOT NULL DEFAULT 0
        CHECK (total_independent_days >= 0),
    total_assisted_continuity_days INTEGER NOT NULL DEFAULT 0
        CHECK (total_assisted_continuity_days >= 0),
    training_eligible BOOLEAN NOT NULL DEFAULT FALSE
        CHECK (training_eligible = FALSE),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE learner_daily_streak_ledger (
    ledger_id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    activity_local_date DATE NOT NULL,
    learner_timezone VARCHAR(64) NOT NULL DEFAULT 'UTC',
    daily_status VARCHAR(32) NOT NULL
        CHECK (daily_status IN (
            'VERIFIED_INDEPENDENT',
            'ASSISTED_CONTINUITY',
            'FREEZE_APPLIED'
        )),
    highest_evidence_type VARCHAR(32) NOT NULL
        CHECK (highest_evidence_type IN (
            'INDEPENDENT',
            'HINT_ASSISTED',
            'SOLUTION_REVEALED_ASSISTED',
            'FREEZE_SYSTEM'
        )),
    independent_activity_count INTEGER NOT NULL DEFAULT 0
        CHECK (independent_activity_count >= 0),
    assisted_activity_count INTEGER NOT NULL DEFAULT 0
        CHECK (assisted_activity_count >= 0),
    streak_incremented BOOLEAN NOT NULL DEFAULT FALSE,
    freeze_consumed BOOLEAN NOT NULL DEFAULT FALSE,
    streak_days_snapshot INTEGER NOT NULL DEFAULT 0
        CHECK (streak_days_snapshot >= 0),
    evidence_reference VARCHAR(160) NOT NULL
        CHECK (length(btrim(evidence_reference)) > 0),
    last_idempotency_key VARCHAR(128) NOT NULL
        CHECK (length(btrim(last_idempotency_key)) > 0),
    training_eligible BOOLEAN NOT NULL DEFAULT FALSE
        CHECK (training_eligible = FALSE),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_learner_daily_streak_ledger_user_date UNIQUE (user_id, activity_local_date),
    CONSTRAINT chk_ledger_freeze_exclusivity CHECK (NOT (streak_incremented AND freeze_consumed))
);

CREATE INDEX idx_learner_daily_streak_ledger_user_date
    ON learner_daily_streak_ledger(user_id, activity_local_date DESC);

CREATE TABLE learner_achievements (
    achievement_id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    badge_code VARCHAR(64) NOT NULL
        CHECK (badge_code IN (
            'FIRST_INDEPENDENT_TWIN',
            'THREE_DAY_INDEPENDENT_STREAK',
            'SEVEN_DAY_INDEPENDENT_STREAK',
            'MISCONCEPTION_OVERCOME',
            'RETENTION_GUARDIAN',
            'CONCEPT_ARCHITECT'
        )),
    badge_category VARCHAR(32) NOT NULL
        CHECK (badge_category IN (
            'INDEPENDENT_TRANSFER',
            'HABIT_CONSISTENCY',
            'MISCONCEPTION_REPAIR',
            'SPACED_RETENTION',
            'CONCEPT_MASTERY'
        )),
    evidence_reference VARCHAR(160) NOT NULL
        CHECK (length(btrim(evidence_reference)) > 0),
    unlocked_local_date DATE NOT NULL,
    unlocked_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    training_eligible BOOLEAN NOT NULL DEFAULT FALSE
        CHECK (training_eligible = FALSE),
    CONSTRAINT uq_learner_achievements_user_badge UNIQUE (user_id, badge_code)
);

CREATE INDEX idx_learner_achievements_user_unlocked
    ON learner_achievements(user_id, unlocked_at DESC);
