# Data Lifecycle, Retention, and Audit

## Classification

Highly sensitive:
- auth tokens,
- external identity material,
- signed billing data.

Personal learning data:
- problem images,
- attempts,
- mistakes,
- study history,
- mastery.

Operational metadata:
- latency,
- model/version,
- non-content traces.

## Retention principle

Keep only as long as product value, user expectation, security or legal need requires. Raw assets should generally have shorter retention than derived learning state.

## Suggested retention classes

### TEMPORARY_RAW
Original uploaded image/PDF. Example configurable default: 30 days unless explicitly saved.

### USER_LIBRARY
User-saved assets retained until explicit deletion/account lifecycle.

### DERIVED_LEARNING
Structured attempts, mistakes and mastery retained while account active subject to privacy rules.

### SECURITY_AUDIT
Minimal security evidence retained according to compliance policy.

## Account deletion

1. Lock account.
2. Revoke sessions.
3. Stop personalization jobs.
4. Delete/anonymize PII.
5. Delete object storage assets.
6. Delete/anonymize learning records according to product/legal policy.
7. Retain only minimized legal/billing/security evidence if required.

## User-controlled deletion

Allow deletion of originals and history items where semantics permit. Clearly explain whether derived learning evidence remains after deleting only an image.

## Audit

Audit privileged access, entitlement mutation, account deletion and security-sensitive configuration. Do not use audit log as a general debug dump.

## Backups

Backup retention is part of deletion semantics. Deleted data may remain in encrypted backup until the documented backup expiry window; this must be accounted for transparently.

<!-- HYBRID_AI_STRATEGY_V3:START -->
## Training-data retention clarification

Normal production retention and deletion rules remain authoritative even if data might be useful for future ML. Training eligibility does not imply indefinite retention.

Any future dataset export requires lineage and a revocation/deletion response policy. Analytics stores must not silently become training stores.
<!-- HYBRID_AI_STRATEGY_V3:END -->
