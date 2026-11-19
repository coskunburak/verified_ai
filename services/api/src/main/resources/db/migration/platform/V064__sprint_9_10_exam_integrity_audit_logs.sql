-- ============================================================================
-- V064: Sprint 9.10 Exam Assessment Integrity Audit Logs & Anti-Replay Nonces
--       (INV-EXAM-006 / Phase 09 Exit Gate)
-- ADR-006 (training_eligible=FALSE) / ADR-011 (feature-flagged)
-- ============================================================================

CREATE TABLE exam_integrity_audit_logs (
    id                      UUID PRIMARY KEY,
    session_id              UUID NOT NULL REFERENCES exam_sessions(id) ON DELETE CASCADE,
    user_id                 UUID NOT NULL,
    event_type              VARCHAR(48) NOT NULL CHECK (event_type IN (
                                'VERIFIED_SUBMIT',
                                'SIGNATURE_REJECTED',
                                'NONCE_REPLAY_REJECTED',
                                'CLOCK_SKEW_REJECTED',
                                'LEGACY_BYPASS_DISABLED'
                            )),
    signature_verified      BOOLEAN NOT NULL,
    client_clock_skew_ms    BIGINT NULL,
    payload_sha256          CHAR(64) NOT NULL CHECK (payload_sha256 ~ '^[0-9a-f]{64}$'),
    nonce_hash              CHAR(64) NULL CHECK (nonce_hash IS NULL OR nonce_hash ~ '^[0-9a-f]{64}$'),
    integrity_key_id        VARCHAR(64) NULL,
    idempotency_key_hash    CHAR(64) NULL CHECK (idempotency_key_hash IS NULL OR idempotency_key_hash ~ '^[0-9a-f]{64}$'),
    protocol_version        VARCHAR(32) NOT NULL DEFAULT 'exam-submit-hmac-v1'
                            CHECK (protocol_version = 'exam-submit-hmac-v1'),
    reason_code             VARCHAR(64) NOT NULL CHECK (length(btrim(reason_code)) > 0),
    training_eligible       BOOLEAN NOT NULL DEFAULT FALSE CHECK (training_eligible = FALSE),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_exam_integrity_audit_event_verified
        CHECK ((event_type = 'VERIFIED_SUBMIT' AND signature_verified = TRUE)
            OR (event_type <> 'VERIFIED_SUBMIT' AND signature_verified = FALSE))
);

CREATE INDEX idx_exam_integrity_audit_logs_user_created
    ON exam_integrity_audit_logs (user_id, created_at DESC);

CREATE INDEX idx_exam_integrity_audit_logs_session_created
    ON exam_integrity_audit_logs (session_id, created_at DESC);

CREATE INDEX idx_exam_integrity_audit_logs_created_at
    ON exam_integrity_audit_logs (created_at);

CREATE TABLE exam_integrity_used_nonces (
    session_id              UUID NOT NULL REFERENCES exam_sessions(id) ON DELETE CASCADE,
    user_id                 UUID NOT NULL,
    nonce_hash              CHAR(64) NOT NULL CHECK (nonce_hash ~ '^[0-9a-f]{64}$'),
    idempotency_key_hash    CHAR(64) NOT NULL CHECK (idempotency_key_hash ~ '^[0-9a-f]{64}$'),
    payload_sha256          CHAR(64) NOT NULL CHECK (payload_sha256 ~ '^[0-9a-f]{64}$'),
    integrity_key_id        VARCHAR(64) NOT NULL CHECK (length(btrim(integrity_key_id)) > 0),
    training_eligible       BOOLEAN NOT NULL DEFAULT FALSE CHECK (training_eligible = FALSE),
    consumed_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at              TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (session_id, nonce_hash),
    CONSTRAINT chk_exam_integrity_used_nonces_expiry
        CHECK (expires_at >= consumed_at)
);

CREATE INDEX idx_exam_integrity_used_nonces_user_id
    ON exam_integrity_used_nonces (user_id);

CREATE INDEX idx_exam_integrity_used_nonces_expires_at
    ON exam_integrity_used_nonces (expires_at);

COMMENT ON TABLE exam_integrity_audit_logs IS
    'STUDENT_CONFIDENTIAL; Sprint 9.10 cryptographic exam submission integrity audit logs; 30d TTL and child-first account deletion purge; never training data (training_eligible=FALSE).';

COMMENT ON TABLE exam_integrity_used_nonces IS
    'STUDENT_CONFIDENTIAL; Sprint 9.10 single-use SHA-256 hashed submission nonces per session; expired post-grace and purged on account deletion (training_eligible=FALSE).';
