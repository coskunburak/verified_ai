ALTER TABLE entitlements
    ADD COLUMN grace_period_expires_at timestamptz CHECK (grace_period_expires_at IS NULL OR grace_period_expires_at > effective_at),
    ADD COLUMN package_code varchar(24) CHECK (package_code IS NULL OR package_code IN ('FREE','PRO','COACH_PRO','YKS_SEASON_PASS'));

UPDATE entitlements
SET package_code = CASE tier
    WHEN 'FREE' THEN 'FREE'
    WHEN 'PRO' THEN 'PRO'
    WHEN 'PRO_PLUS' THEN 'COACH_PRO'
    ELSE NULL
END
WHERE package_code IS NULL;

UPDATE entitlements e
SET package_code = 'YKS_SEASON_PASS'
FROM app_store_subscriptions s
JOIN app_store_transactions t
  ON t.user_id = s.user_id
 AND t.environment = s.environment
 AND t.transaction_id = s.current_transaction_id
WHERE e.user_id = s.user_id
  AND e.source = 'APP_STORE_SUBSCRIPTION'
  AND e.tier = 'PRO_PLUS'
  AND e.original_transaction_id = s.original_transaction_id
  AND e.environment = s.environment
  AND t.offer_family = 'YKS_SEASON_PASS';

UPDATE entitlements e
SET grace_period_expires_at = s.grace_period_expires_at
FROM app_store_subscriptions s
WHERE e.user_id = s.user_id
  AND e.source = 'APP_STORE_SUBSCRIPTION'
  AND e.original_transaction_id = s.original_transaction_id
  AND e.environment = s.environment
  AND s.grace_period_expires_at IS NOT NULL
  AND s.grace_period_expires_at > e.effective_at
  AND e.grace_period_expires_at IS NULL;

CREATE TABLE billing_entitlement_transition_audit (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_key_hash char(64) NOT NULL UNIQUE CHECK (event_key_hash ~ '^[0-9a-f]{64}$'),
    from_package varchar(24) NOT NULL CHECK (from_package IN ('FREE','PRO','COACH_PRO','YKS_SEASON_PASS')),
    to_package varchar(24) NOT NULL CHECK (to_package IN ('FREE','PRO','COACH_PRO','YKS_SEASON_PASS')),
    from_status varchar(24) NOT NULL CHECK (from_status IN ('ACTIVE','GRACE_PERIOD','BILLING_RETRY','EXPIRED','REVOKED')),
    to_status varchar(24) NOT NULL CHECK (to_status IN ('ACTIVE','GRACE_PERIOD','BILLING_RETRY','EXPIRED','REVOKED')),
    reason varchar(16) NOT NULL CHECK (reason IN ('PURCHASE','RENEWAL','GRACE','RETRY','EXPIRY','REFUND','REVOKE','ADMIN','RECONCILE')),
    occurred_at timestamptz NOT NULL,
    recorded_at timestamptz NOT NULL DEFAULT current_timestamp,
    training_eligible boolean NOT NULL DEFAULT false CHECK (NOT training_eligible)
);
CREATE INDEX billing_transition_owner_time ON billing_entitlement_transition_audit(user_id, occurred_at DESC);

-- A refund remains terminal for its verified transaction even if a later renewal changes the subscription head.
ALTER TABLE app_store_transactions ADD COLUMN access_revoked boolean NOT NULL DEFAULT false;

ALTER TABLE app_store_subscriptions ADD COLUMN state_signed_at timestamptz;
UPDATE app_store_transactions t SET access_revoked=true
FROM app_store_subscriptions s WHERE s.status='REVOKED' AND s.user_id=t.user_id
    AND s.environment=t.environment AND s.current_transaction_id=t.transaction_id;
