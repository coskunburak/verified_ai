CREATE TABLE learning_profiles (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    education_level VARCHAR(32),
    preferred_language VARCHAR(16),
    explanation_depth VARCHAR(16),
    daily_study_minutes INTEGER,
    timezone VARCHAR(64),
    goal_context VARCHAR(160),
    onboarding_status VARCHAR(24) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_learning_profiles_user_id UNIQUE (user_id),
    CONSTRAINT ck_learning_profiles_education_level CHECK (
        education_level IS NULL OR education_level IN ('MIDDLE_SCHOOL', 'HIGH_SCHOOL', 'UNIVERSITY', 'OTHER')
    ),
    CONSTRAINT ck_learning_profiles_preferred_language CHECK (
        preferred_language IS NULL OR preferred_language IN ('en', 'tr')
    ),
    CONSTRAINT ck_learning_profiles_explanation_depth CHECK (
        explanation_depth IS NULL OR explanation_depth IN ('BEGINNER', 'QUICK', 'STANDARD', 'DEEP')
    ),
    CONSTRAINT ck_learning_profiles_daily_study_minutes CHECK (
        daily_study_minutes IS NULL OR daily_study_minutes BETWEEN 5 AND 240
    ),
    CONSTRAINT ck_learning_profiles_timezone CHECK (
        timezone IS NULL OR char_length(timezone) BETWEEN 1 AND 64
    ),
    CONSTRAINT ck_learning_profiles_goal_context CHECK (
        goal_context IS NULL OR char_length(goal_context) <= 160
    ),
    CONSTRAINT ck_learning_profiles_onboarding_status CHECK (
        onboarding_status IN ('IN_PROGRESS', 'COMPLETED')
    )
);

CREATE INDEX ix_learning_profiles_user_status ON learning_profiles(user_id, onboarding_status);
