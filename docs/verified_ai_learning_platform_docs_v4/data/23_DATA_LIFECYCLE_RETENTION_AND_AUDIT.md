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

Sprint 4.1 introduces a stricter pre-upload iOS temporary class for local capture/import review. `CapturedAsset` files live under the app temporary directory, default to a 24-hour cleanup window, and are deleted on cancel/retake/replace when no longer referenced. They are not PostgreSQL rows, object-storage objects, backend `ProblemAsset` records, or `ProblemSession` history.

Sprint 4.2 introduces durable `ProblemAsset` raw source objects with configurable pending-upload retention. The default reservation TTL is 15 minutes; abandoned pending uploads are eligible for cleanup after expiry and are swept by the backend cleanup job. Account export includes problem asset metadata and declares `rawBinaryIncluded=false`; confirmed account deletion deletes matching private object-storage keys and cascades the owning problem rows.

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

Sprint 3.7 implementation:

| Data area | Export behavior | Deletion behavior | Retention exception |
|---|---|---|---|
| Identity/account | user id, status, created timestamp, deletion timestamps | account moves through deletion-requested/in-progress/deleted states and all sessions/refresh tokens are revoked | deleted account tombstone remains to prevent silent Apple identity reattachment |
| Learning profile | profile settings, onboarding status, timestamps, version | `learning_profiles` row is deleted | none in current Phase 3 schema |
| Sessions | recent session IDs, status, timestamps, revocation reason | all active sessions revoked | minimized security history may remain |
| Billing | entitlement summary, subscriptions, transaction identifiers/status/environment/product metadata | app account token deleted; entitlement revoked/free; billing event records retained | legal/refund/fraud audit retains minimized transaction references, never raw payment credentials |
| Problem assets | asset/session metadata, status, source/kind, content type, size, checksum, crop/dimension/page metadata, timestamps; raw binaries excluded | delete private object-storage keys, then delete `problem_sessions`/`problem_assets` through the lifecycle contributor | pending upload expiry and backup expiry apply; raw binaries are not export payloads |
| Future AI/attempt/mastery/tutor data | lifecycle-contributor contract reserved for future stores | contributor must delete/anonymize owned rows/assets before phase exit | follow retention class and backup expiry |

The implementation stores export JSON in `data_exports` with seven-day expiry and records privacy events in `privacy_events`. Export content excludes secrets, token hashes, raw Apple JWS/payment material, internal fraud signals, and unrestricted operational telemetry.

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
