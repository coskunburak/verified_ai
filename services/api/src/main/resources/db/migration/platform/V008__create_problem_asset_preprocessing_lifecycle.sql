ALTER TABLE problem_assets
    ADD CONSTRAINT uq_problem_assets_id_user UNIQUE (id, user_id);

CREATE TABLE problem_asset_derivatives (
    id UUID PRIMARY KEY,
    source_asset_id UUID NOT NULL,
    problem_session_id UUID NOT NULL,
    user_id UUID NOT NULL,
    derivative_kind VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    selected_for_recognition BOOLEAN NOT NULL DEFAULT FALSE,
    object_key VARCHAR(512),
    content_type VARCHAR(128),
    size_bytes BIGINT,
    checksum_algorithm VARCHAR(16),
    checksum_value VARCHAR(64),
    width INTEGER,
    height INTEGER,
    source_width INTEGER NOT NULL,
    source_height INTEGER NOT NULL,
    crop_x NUMERIC(8, 6) NOT NULL,
    crop_y NUMERIC(8, 6) NOT NULL,
    crop_width NUMERIC(8, 6) NOT NULL,
    crop_height NUMERIC(8, 6) NOT NULL,
    processor_name VARCHAR(64) NOT NULL,
    processor_version VARCHAR(32) NOT NULL,
    configuration_version VARCHAR(64) NOT NULL,
    orientation_normalized BOOLEAN NOT NULL,
    perspective_applied BOOLEAN NOT NULL,
    contrast_normalized BOOLEAN NOT NULL,
    resized BOOLEAN NOT NULL,
    quality_outcome VARCHAR(32) NOT NULL,
    failure_code VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_problem_asset_derivatives_source_user FOREIGN KEY (source_asset_id, user_id)
        REFERENCES problem_assets(id, user_id) ON DELETE CASCADE,
    CONSTRAINT fk_problem_asset_derivatives_session_user FOREIGN KEY (problem_session_id, user_id)
        REFERENCES problem_sessions(id, user_id) ON DELETE CASCADE,
    CONSTRAINT ck_problem_asset_derivatives_kind CHECK (
        derivative_kind IN ('OCR_OPTIMIZED', 'THUMBNAIL')
    ),
    CONSTRAINT ck_problem_asset_derivatives_status CHECK (
        status IN ('READY', 'FAILED')
    ),
    CONSTRAINT ck_problem_asset_derivatives_ready_object CHECK (
        (status = 'READY'
            AND object_key IS NOT NULL
            AND content_type = 'image/jpeg'
            AND size_bytes IS NOT NULL
            AND size_bytes > 0
            AND checksum_algorithm = 'SHA-256'
            AND checksum_value ~ '^[a-f0-9]{64}$'
            AND width IS NOT NULL
            AND width > 0
            AND height IS NOT NULL
            AND height > 0
            AND completed_at IS NOT NULL
            AND failure_code IS NULL)
        OR (status = 'FAILED'
            AND failure_code IS NOT NULL
            AND completed_at IS NOT NULL)
    ),
    CONSTRAINT ck_problem_asset_derivatives_source_dimensions CHECK (
        source_width > 0 AND source_width <= 12000 AND source_height > 0 AND source_height <= 12000
    ),
    CONSTRAINT ck_problem_asset_derivatives_crop CHECK (
        crop_x >= 0 AND crop_y >= 0 AND crop_width > 0 AND crop_height > 0
        AND crop_x + crop_width <= 1 AND crop_y + crop_height <= 1
    ),
    CONSTRAINT ck_problem_asset_derivatives_quality_outcome CHECK (
        quality_outcome IN ('PASS', 'WARNING', 'FAILED')
    ),
    CONSTRAINT uq_problem_asset_derivatives_processing UNIQUE (
        source_asset_id,
        derivative_kind,
        processor_name,
        processor_version,
        configuration_version
    ),
    CONSTRAINT uq_problem_asset_derivatives_object_key UNIQUE (object_key)
);

CREATE UNIQUE INDEX uq_problem_asset_derivatives_selected_recognition
    ON problem_asset_derivatives(source_asset_id)
    WHERE selected_for_recognition = TRUE;

CREATE INDEX ix_problem_asset_derivatives_source_created
    ON problem_asset_derivatives(source_asset_id, created_at DESC);

CREATE INDEX ix_problem_asset_derivatives_user_created
    ON problem_asset_derivatives(user_id, created_at DESC);

CREATE INDEX ix_problem_asset_derivatives_status_updated
    ON problem_asset_derivatives(status, updated_at DESC);

CREATE INDEX ix_problem_asset_derivatives_quality
    ON problem_asset_derivatives(quality_outcome, created_at DESC);

CREATE TABLE problem_asset_quality_evidence (
    id UUID PRIMARY KEY,
    derivative_id UUID NOT NULL REFERENCES problem_asset_derivatives(id) ON DELETE CASCADE,
    source_asset_id UUID NOT NULL,
    user_id UUID NOT NULL,
    signal_type VARCHAR(32) NOT NULL,
    severity VARCHAR(32) NOT NULL,
    score NUMERIC(12, 6) NOT NULL,
    threshold NUMERIC(12, 6) NOT NULL,
    policy_version VARCHAR(64) NOT NULL,
    message_code VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_problem_asset_quality_source_user FOREIGN KEY (source_asset_id, user_id)
        REFERENCES problem_assets(id, user_id) ON DELETE CASCADE,
    CONSTRAINT ck_problem_asset_quality_signal CHECK (
        signal_type IN ('BLUR', 'GLARE', 'CROP_FRAMING', 'CONTRAST_READABILITY', 'RESOLUTION')
    ),
    CONSTRAINT ck_problem_asset_quality_severity CHECK (
        severity IN ('PASS', 'WARNING', 'BLOCKING')
    )
);

CREATE UNIQUE INDEX uq_problem_asset_quality_signal
    ON problem_asset_quality_evidence(derivative_id, signal_type);

CREATE INDEX ix_problem_asset_quality_source
    ON problem_asset_quality_evidence(source_asset_id, created_at DESC);
