-- Sprint 10.8: Admin and Support Console Foundations
-- Additive forward-only migration on top of V071 (zero changes to V001..V071).
-- Enforces INV-ADM-001..007: immutable admin/support audit ledger, time-bounded audited
-- raw-content elevation grants, wrong-answer report queue with optimistic locking,
-- CHECK (NOT training_eligible), and ON DELETE CASCADE for user-scoped records.

CREATE TABLE admin_support_audit_logs (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    action            VARCHAR(40)  NOT NULL,
    target_type       VARCHAR(24)  NOT NULL,
    target_id         VARCHAR(128) NOT NULL,
    actor_user_id     UUID         NOT NULL,
    actor_role        VARCHAR(48)  NOT NULL,
    idempotency_key   VARCHAR(128),
    payload_hash      CHAR(64),
    reason            VARCHAR(240) NOT NULL,
    before_state_json JSONB        NOT NULL DEFAULT '{}'::jsonb,
    after_state_json  JSONB        NOT NULL DEFAULT '{}'::jsonb,
    correlation_id    VARCHAR(64)  NOT NULL,
    training_eligible BOOLEAN      NOT NULL DEFAULT FALSE,
    occurred_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_adm_audit_action CHECK (
        action IN (
            'USER_LOOKUP_READ',
            'PROBLEM_TRACE_READ',
            'BILLING_SUPPORT_READ',
            'AI_OPS_OVERVIEW_READ',
            'RAW_CONTENT_GRANT_CREATED',
            'RAW_CONTENT_INSPECTED',
            'WRONG_ANSWER_REPORT_REVIEWED',
            'BILLING_RECONCILE_EXECUTED',
            'QUOTA_SYNC_EXECUTED',
            'KILL_SWITCH_LINKED_EXECUTED'
        )
    ),
    CONSTRAINT ck_adm_audit_target_type CHECK (
        target_type IN ('USER', 'PROBLEM_SESSION', 'WRONG_ANSWER_REPORT', 'BILLING', 'FEATURE_FLAG', 'AI_OPS')
    ),
    CONSTRAINT ck_adm_audit_target_id_non_blank CHECK (length(trim(target_id)) BETWEEN 1 AND 128),
    CONSTRAINT ck_adm_audit_actor_role_non_blank CHECK (length(trim(actor_role)) BETWEEN 2 AND 48),
    CONSTRAINT ck_adm_audit_idem_pair CHECK (
        (idempotency_key IS NULL AND payload_hash IS NULL)
        OR (
            idempotency_key ~ '^[A-Za-z0-9:._-]{8,128}$'
            AND payload_hash ~ '^[0-9a-f]{64}$'
        )
    ),
    CONSTRAINT ck_adm_audit_reason_len CHECK (length(trim(reason)) BETWEEN 5 AND 240),
    CONSTRAINT ck_adm_audit_json_objects CHECK (
        jsonb_typeof(before_state_json) = 'object'
        AND jsonb_typeof(after_state_json) = 'object'
        AND octet_length(before_state_json::text) <= 4096
        AND octet_length(after_state_json::text) <= 4096
    ),
    CONSTRAINT ck_adm_audit_training_ineligible CHECK (NOT training_eligible)
);

CREATE UNIQUE INDEX uq_admin_support_audit_idempotency_key
    ON admin_support_audit_logs (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX idx_admin_support_audit_occurred
    ON admin_support_audit_logs (occurred_at DESC, id DESC);

CREATE INDEX idx_admin_support_audit_target
    ON admin_support_audit_logs (target_type, target_id, occurred_at DESC);

CREATE TABLE admin_raw_content_access_grants (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id      UUID         NOT NULL,
    actor_role         VARCHAR(48)  NOT NULL,
    target_user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    problem_session_id UUID         NOT NULL,
    ticket_reference   VARCHAR(64)  NOT NULL,
    reason             VARCHAR(240) NOT NULL,
    idempotency_key    VARCHAR(128) NOT NULL,
    payload_hash       CHAR(64)     NOT NULL,
    audit_log_id       UUID         NOT NULL REFERENCES admin_support_audit_logs (id) ON DELETE RESTRICT,
    granted_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at         TIMESTAMPTZ  NOT NULL,
    revoked_at         TIMESTAMPTZ,
    correlation_id     VARCHAR(64)  NOT NULL,
    training_eligible  BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_adm_raw_grant_session_user FOREIGN KEY (problem_session_id, target_user_id)
        REFERENCES problem_sessions (id, user_id) ON DELETE CASCADE,
    CONSTRAINT uq_adm_raw_grant_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_adm_raw_grant_ticket_format CHECK (ticket_reference ~ '^[A-Za-z0-9._-]{3,64}$'),
    CONSTRAINT ck_adm_raw_grant_reason_len CHECK (length(trim(reason)) BETWEEN 10 AND 240),
    CONSTRAINT ck_adm_raw_grant_idem_format CHECK (idempotency_key ~ '^[A-Za-z0-9:._-]{8,128}$'),
    CONSTRAINT ck_adm_raw_grant_payload_hash_hex CHECK (payload_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_adm_raw_grant_ttl_bounds CHECK (
        expires_at > granted_at AND expires_at <= granted_at + INTERVAL '60 minutes'
    ),
    CONSTRAINT ck_adm_raw_grant_training_ineligible CHECK (NOT training_eligible)
);

CREATE INDEX idx_admin_raw_content_grants_actor_session
    ON admin_raw_content_access_grants (actor_user_id, problem_session_id, expires_at DESC);

CREATE INDEX idx_admin_raw_content_grants_target_user
    ON admin_raw_content_access_grants (target_user_id, granted_at DESC);

CREATE TABLE wrong_answer_reports (
    id                           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                      UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    problem_session_id           UUID         NOT NULL,
    idempotency_key              VARCHAR(128) NOT NULL,
    payload_hash                 CHAR(64)     NOT NULL,
    report_reason                VARCHAR(40)  NOT NULL,
    user_note                    VARCHAR(400),
    verification_status_snapshot VARCHAR(32)  NOT NULL DEFAULT 'UNKNOWN',
    policy_version_snapshot      VARCHAR(64),
    model_version_snapshot       VARCHAR(128),
    problem_type_snapshot        VARCHAR(64),
    status                       VARCHAR(32)  NOT NULL DEFAULT 'OPEN',
    reviewer_user_id             UUID,
    resolution_code              VARCHAR(40),
    resolution_note              VARCHAR(400),
    linked_remediation_audit_id  UUID         REFERENCES admin_support_audit_logs (id) ON DELETE SET NULL,
    correlation_id               VARCHAR(64)  NOT NULL,
    version                      BIGINT       NOT NULL DEFAULT 0,
    training_eligible            BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at                   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    resolved_at                  TIMESTAMPTZ,
    CONSTRAINT fk_war_session_user FOREIGN KEY (problem_session_id, user_id)
        REFERENCES problem_sessions (id, user_id) ON DELETE CASCADE,
    CONSTRAINT uq_war_user_idempotency_key UNIQUE (user_id, idempotency_key),
    CONSTRAINT ck_war_idem_format CHECK (idempotency_key ~ '^[A-Za-z0-9:._-]{8,128}$'),
    CONSTRAINT ck_war_payload_hash_hex CHECK (payload_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_war_reason CHECK (
        report_reason IN (
            'WRONG_FINAL_ANSWER',
            'WRONG_STEP_EXPLANATION',
            'PARSE_MISMATCH',
            'FALSE_VERIFICATION',
            'MISTAKE_DIAGNOSIS_DISPUTE',
            'OTHER'
        )
    ),
    CONSTRAINT ck_war_status CHECK (
        status IN (
            'OPEN',
            'IN_REVIEW',
            'RESOLVED_CONFIRMED_DEFECT',
            'RESOLVED_NOT_A_DEFECT',
            'RESOLVED_DUPLICATE',
            'DISMISSED'
        )
    ),
    CONSTRAINT ck_war_resolution_code CHECK (
        resolution_code IS NULL OR resolution_code IN (
            'CONFIRMED_PARSER_DEFECT',
            'CONFIRMED_SOLVER_DEFECT',
            'CONFIRMED_VERIFIER_DEFECT',
            'CONFIRMED_MISTAKE_DEFECT',
            'VERIFIED_CORRECT_NO_DEFECT',
            'DUPLICATE_REPORT',
            'INSUFFICIENT_EVIDENCE',
            'USER_INPUT_AMBIGUOUS'
        )
    ),
    CONSTRAINT ck_war_resolution_consistency CHECK (
        (status IN ('OPEN', 'IN_REVIEW') AND resolved_at IS NULL)
        OR (
            status IN ('RESOLVED_CONFIRMED_DEFECT', 'RESOLVED_NOT_A_DEFECT', 'RESOLVED_DUPLICATE', 'DISMISSED')
            AND reviewer_user_id IS NOT NULL
            AND resolution_code IS NOT NULL
            AND resolution_note IS NOT NULL
            AND length(trim(resolution_note)) >= 5
            AND resolved_at IS NOT NULL
        )
    ),
    CONSTRAINT ck_war_version_non_negative CHECK (version >= 0),
    CONSTRAINT ck_war_training_ineligible CHECK (NOT training_eligible)
);

CREATE INDEX idx_wrong_answer_reports_queue
    ON wrong_answer_reports (status, created_at DESC, id DESC);

CREATE INDEX idx_wrong_answer_reports_user_time
    ON wrong_answer_reports (user_id, created_at DESC);

CREATE INDEX idx_wrong_answer_reports_session
    ON wrong_answer_reports (problem_session_id, created_at DESC);
