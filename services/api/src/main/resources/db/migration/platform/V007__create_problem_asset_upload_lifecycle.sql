CREATE TABLE problem_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    status VARCHAR(32) NOT NULL,
    input_mode VARCHAR(32) NOT NULL,
    current_parse_id UUID,
    problem_id UUID,
    solve_job_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_problem_sessions_status CHECK (
        status IN ('CREATED', 'ASSET_UPLOADED', 'PARSING', 'PARSED', 'SOLVING', 'VERIFYING', 'COMPLETED', 'REVIEW_REQUIRED', 'FAILED', 'CANCELLED')
    ),
    CONSTRAINT ck_problem_sessions_input_mode CHECK (
        input_mode IN ('CAMERA', 'PHOTO_LIBRARY', 'FILE', 'PDF')
    ),
    CONSTRAINT uq_problem_sessions_id_user UNIQUE (id, user_id)
);

CREATE INDEX ix_problem_sessions_user_created
    ON problem_sessions(user_id, created_at DESC);

CREATE INDEX ix_problem_sessions_status_updated
    ON problem_sessions(status, updated_at DESC);

CREATE TABLE problem_assets (
    id UUID PRIMARY KEY,
    problem_session_id UUID NOT NULL,
    user_id UUID NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    asset_kind VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    object_key VARCHAR(512) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    size_bytes BIGINT NOT NULL,
    checksum_algorithm VARCHAR(16) NOT NULL,
    checksum_value VARCHAR(64) NOT NULL,
    crop_x NUMERIC(8, 6) NOT NULL,
    crop_y NUMERIC(8, 6) NOT NULL,
    crop_width NUMERIC(8, 6) NOT NULL,
    crop_height NUMERIC(8, 6) NOT NULL,
    image_width INTEGER,
    image_height INTEGER,
    page_count INTEGER,
    retention_class VARCHAR(32) NOT NULL,
    upload_expires_at TIMESTAMPTZ NOT NULL,
    available_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    reservation_idempotency_key VARCHAR(128) NOT NULL,
    reservation_request_hash VARCHAR(64) NOT NULL,
    CONSTRAINT fk_problem_assets_session_user FOREIGN KEY (problem_session_id, user_id)
        REFERENCES problem_sessions(id, user_id) ON DELETE CASCADE,
    CONSTRAINT ck_problem_assets_source_type CHECK (
        source_type IN ('CAMERA', 'PHOTO_LIBRARY', 'FILE', 'PDF')
    ),
    CONSTRAINT ck_problem_assets_kind CHECK (
        asset_kind IN ('IMAGE', 'PDF')
    ),
    CONSTRAINT ck_problem_assets_status CHECK (
        status IN ('PENDING', 'AVAILABLE', 'EXPIRED', 'DELETED')
    ),
    CONSTRAINT ck_problem_assets_content_type CHECK (
        content_type IN ('image/jpeg', 'application/pdf')
    ),
    CONSTRAINT ck_problem_assets_size CHECK (size_bytes > 0),
    CONSTRAINT ck_problem_assets_checksum_algorithm CHECK (checksum_algorithm = 'SHA-256'),
    CONSTRAINT ck_problem_assets_checksum_value CHECK (checksum_value ~ '^[a-f0-9]{64}$'),
    CONSTRAINT ck_problem_assets_crop CHECK (
        crop_x >= 0 AND crop_y >= 0 AND crop_width > 0 AND crop_height > 0
        AND crop_x + crop_width <= 1 AND crop_y + crop_height <= 1
    ),
    CONSTRAINT ck_problem_assets_image_dimensions CHECK (
        (asset_kind = 'IMAGE' AND image_width IS NOT NULL AND image_height IS NOT NULL AND page_count IS NULL)
        OR (asset_kind = 'PDF' AND image_width IS NULL AND image_height IS NULL)
    ),
    CONSTRAINT ck_problem_assets_image_bounds CHECK (
        image_width IS NULL OR (image_width > 0 AND image_width <= 12000)
    ),
    CONSTRAINT ck_problem_assets_height_bounds CHECK (
        image_height IS NULL OR (image_height > 0 AND image_height <= 12000)
    ),
    CONSTRAINT ck_problem_assets_page_count CHECK (
        page_count IS NULL OR (page_count > 0 AND page_count <= 500)
    ),
    CONSTRAINT ck_problem_assets_retention CHECK (
        retention_class IN ('TEMPORARY_RAW', 'USER_LIBRARY')
    ),
    CONSTRAINT uq_problem_assets_object_key UNIQUE (object_key),
    CONSTRAINT uq_problem_assets_user_idempotency UNIQUE (user_id, reservation_idempotency_key)
);

CREATE INDEX ix_problem_assets_session_created
    ON problem_assets(problem_session_id, created_at DESC);

CREATE INDEX ix_problem_assets_user_created
    ON problem_assets(user_id, created_at DESC);

CREATE INDEX ix_problem_assets_user_status
    ON problem_assets(user_id, status);

CREATE INDEX ix_problem_assets_pending_expiry
    ON problem_assets(status, upload_expires_at)
    WHERE status = 'PENDING';
