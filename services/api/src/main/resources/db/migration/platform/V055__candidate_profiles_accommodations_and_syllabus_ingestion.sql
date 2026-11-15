CREATE TABLE IF NOT EXISTS candidate_profiles (
    profile_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    active_exam_definition_id UUID NOT NULL REFERENCES exam_definitions(id),
    active_exam_code VARCHAR(64) NOT NULL,
    pinned_exam_version VARCHAR(32) NOT NULL,
    active_target_id UUID NULL REFERENCES student_exam_targets(target_id) ON DELETE SET NULL,
    jurisdiction VARCHAR(16) NOT NULL CHECK (jurisdiction IN ('TR', 'US', 'UK', 'INTL')),
    target_date DATE NOT NULL,
    target_score NUMERIC(8,2) NOT NULL,
    daily_study_minutes INTEGER NOT NULL CHECK (daily_study_minutes BETWEEN 15 AND 960),
    weekly_study_minutes INTEGER NOT NULL CHECK (weekly_study_minutes BETWEEN 15 AND 6720),
    extra_time_multiplier NUMERIC(3,2) NOT NULL DEFAULT 1.00 CHECK (extra_time_multiplier IN (1.00, 1.25, 1.50, 2.00)),
    high_contrast_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    screen_reader_optimized BOOLEAN NOT NULL DEFAULT FALSE,
    reduced_motion_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    baseline_mastery NUMERIC(5,4) NOT NULL DEFAULT 0.2500 CHECK (baseline_mastery BETWEEN 0.0000 AND 1.0000),
    required_daily_minutes INTEGER NOT NULL CHECK (required_daily_minutes >= 0),
    feasibility_status VARCHAR(24) NOT NULL CHECK (feasibility_status IN ('OPTIMAL', 'STRETCH', 'AT_RISK', 'UNREALISTIC')),
    last_idempotency_key VARCHAR(128) NULL,
    schema_version VARCHAR(32) NOT NULL DEFAULT 'candidate-profile-v1',
    training_eligible BOOLEAN NOT NULL DEFAULT FALSE CHECK (training_eligible = FALSE),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_candidate_profiles_user UNIQUE (user_id),
    CONSTRAINT uq_candidate_profiles_user_idempotency UNIQUE (user_id, last_idempotency_key),
    CONSTRAINT fk_candidate_profiles_exam_code_ver
        FOREIGN KEY (active_exam_code, pinned_exam_version)
        REFERENCES exam_definitions (exam_code, exam_version),
    CONSTRAINT ck_candidate_profiles_weekly_daily_consistent CHECK (weekly_study_minutes >= daily_study_minutes)
);

CREATE INDEX IF NOT EXISTS idx_candidate_profiles_exam_code
    ON candidate_profiles (active_exam_code, pinned_exam_version);

CREATE TABLE IF NOT EXISTS candidate_profile_horizon_history (
    history_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id UUID NOT NULL REFERENCES candidate_profiles(profile_id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    exam_definition_id UUID NOT NULL REFERENCES exam_definitions(id),
    exam_code VARCHAR(64) NOT NULL,
    pinned_exam_version VARCHAR(32) NOT NULL,
    previous_target_date DATE NULL,
    new_target_date DATE NOT NULL,
    previous_daily_minutes INTEGER NULL,
    new_daily_minutes INTEGER NOT NULL CHECK (new_daily_minutes BETWEEN 15 AND 960),
    previous_weekly_minutes INTEGER NULL,
    new_weekly_minutes INTEGER NOT NULL CHECK (new_weekly_minutes BETWEEN 15 AND 6720),
    previous_target_score NUMERIC(8,2) NULL,
    new_target_score NUMERIC(8,2) NOT NULL,
    extra_time_multiplier NUMERIC(3,2) NOT NULL CHECK (extra_time_multiplier IN (1.00, 1.25, 1.50, 2.00)),
    required_daily_minutes INTEGER NOT NULL CHECK (required_daily_minutes >= 0),
    feasibility_status VARCHAR(24) NOT NULL CHECK (feasibility_status IN ('OPTIMAL', 'STRETCH', 'AT_RISK', 'UNREALISTIC')),
    change_reason VARCHAR(64) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_candidate_history_exam_code_ver
        FOREIGN KEY (exam_code, pinned_exam_version)
        REFERENCES exam_definitions (exam_code, exam_version)
);

CREATE INDEX IF NOT EXISTS idx_candidate_horizon_history_user_time
    ON candidate_profile_horizon_history (user_id, changed_at DESC);

CREATE TABLE IF NOT EXISTS candidate_syllabus_uploads (
    upload_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    exam_definition_id UUID NOT NULL REFERENCES exam_definitions(id),
    exam_code VARCHAR(64) NOT NULL,
    exam_version VARCHAR(32) NOT NULL,
    image_hash CHAR(64) NOT NULL CHECK (char_length(image_hash) = 64),
    mime_type VARCHAR(64) NOT NULL CHECK (mime_type IN ('image/jpeg', 'image/png', 'image/webp', 'image/heic', 'application/pdf')),
    byte_size INTEGER NOT NULL CHECK (byte_size > 0 AND byte_size <= 10485760),
    parsed_topics_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    status VARCHAR(32) NOT NULL CHECK (status IN ('PARSED_AUTO_CONFIRMED', 'NEEDS_DISAMBIGUATION', 'USER_CONFIRMED', 'REJECTED')),
    deduplicated_from_cache BOOLEAN NOT NULL DEFAULT FALSE,
    llm_tokens_used INTEGER NOT NULL DEFAULT 0 CHECK (llm_tokens_used >= 0),
    last_idempotency_key VARCHAR(128) NULL,
    training_eligible BOOLEAN NOT NULL DEFAULT FALSE CHECK (training_eligible = FALSE),
    schema_version VARCHAR(32) NOT NULL DEFAULT 'syllabus-ingestion-v1',
    expires_at TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_syllabus_uploads_exam_code_ver
        FOREIGN KEY (exam_code, exam_version)
        REFERENCES exam_definitions (exam_code, exam_version),
    CONSTRAINT uq_syllabus_uploads_user_hash UNIQUE (user_id, image_hash),
    CONSTRAINT uq_syllabus_uploads_user_idempotency UNIQUE (user_id, last_idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_syllabus_uploads_user_created
    ON candidate_syllabus_uploads (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_syllabus_uploads_expires
    ON candidate_syllabus_uploads (expires_at);
