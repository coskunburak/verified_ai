CREATE TABLE platform_foundation_marker (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    marker_key VARCHAR(120) NOT NULL UNIQUE,
    marker_value VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO platform_foundation_marker (marker_key, marker_value)
VALUES ('phase', 'phase-2-platform-foundation');

