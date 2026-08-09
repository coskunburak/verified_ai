# Security Threat Model

## Assets

- identity/session,
- learning history,
- raw images/documents,
- entitlement/billing state,
- AI provider credentials,
- database,
- admin privilege,
- model/prompt routing configuration.

## Trust boundaries

1. iOS device ↔ public API.
2. API ↔ PostgreSQL/Redis/object storage.
3. API ↔ internal math verifier.
4. API ↔ external AI providers.
5. API ↔ Apple services.
6. Admin user ↔ privileged functions.

## Phase 1 Baseline Threat Matrix

| Threat | Primary boundary | Minimum control | Phase 2+ validation |
|---|---|---|---|
| Stolen access token | iOS -> API | short-lived token, refresh rotation, revocation | auth/security integration tests |
| Refresh-token reuse | iOS -> API | token family tracking and reuse detection | replay tests |
| Object ownership bypass | iOS -> API/object storage | principal-derived ownership and scoped presigned URLs | IDOR/object-reference tests |
| Uploaded malicious file | iOS -> API/object storage | MIME/size/dimension/page limits and scanning/isolation policy | upload abuse tests |
| Oversized upload or image flood | iOS -> API/object storage | quota, rate limit, max size, backpressure | rate-limit/load tests |
| Prompt injection in problem text | API -> AI provider | untrusted-content separation, schema validation, tool allowlist | adversarial prompt fixtures |
| Prompt injection in captured image/OCR text | API -> AI provider | treat visual text as data, strict recognition prompt, schema/semantic validation, no model-driven tool or URL execution | Sprint 4.4 prompt-injection recognition fixtures |
| AI provider secret leakage | API -> AI provider | server-side secret manager, redacted logs, no client exposure | secret scan and config tests |
| Object storage URL abuse | iOS -> object storage | short TTL, narrow key scope, completion verification | presigned URL contract tests |
| Math verifier exposure | API -> verifier | private network/internal auth, no public/iOS ingress | network/config tests |
| Admin privilege escalation | Admin -> API | least privilege, strong auth, audit, redaction | authorization tests and audit review |
| Subscription spoofing | iOS/Apple -> API | server-side Apple validation, idempotent event handling | billing replay tests |
| Rate-limit bypass | iOS -> API | layered limits by user/IP/device/cost class | abuse tests |
| PII in logs/analytics | API/iOS -> observability | redaction, event schema minimization | log/analytics fixture review |
| Unintended training-data capture | product data -> future datasets | default not training eligible, lineage gate | dataset eligibility tests in Phase 13 |
| Account deletion failure | user data stores | deletion orchestration and retention classes | deletion workflow tests |

## Threats and mitigations

### Credential theft
TLS, Keychain, short access token, refresh rotation/revocation.

Sprint 3.8 strengthens this with backend session-state checks on protected API requests. Logout, refresh-token family revocation, and account deletion invalidate continued use of an otherwise unexpired bearer token.

### IDOR
Principal-derived ownership checks on every resource.

### Prompt injection
User content is data, not instructions; strict prompt separation; no arbitrary tool execution.

Sprint 4.4 applies this to image text during recognition. A captured problem can visibly contain strings such as "ignore previous instructions" or "return secrets"; recognition stores those strings only as visible evidence and never executes them, changes schema because of them, fetches URLs from them, or treats them as trusted prompt instructions.

### Symbolic-expression abuse
No Python eval, parser allowlist, expression complexity limits, verifier timeout.

### Malicious uploads
Size/MIME/dimension/page limits, isolated storage and scanning policy.

Sprint 4.2 validates JPEG/PDF upload metadata before issuing a presigned URL and again after direct object upload. The backend owns the key, bucket policy assumption, expected byte size, expected content type, SHA-256 checksum, reservation TTL, and user/session relationship. Wrong-owner completion attempts are denied by principal-derived lookup rather than UUID opacity. Mismatched objects are deleted and never marked AVAILABLE.

Sprint 4.4 bounds recognition response bytes, block count, text length, confidence ranges, reading order, and normalized coordinates. Provider output is model data only and cannot choose storage keys, mutate entitlement, execute SQL/code, invoke tools, or call URLs.

### Billing spoof
Server-side App Store validation and idempotent external event handling.

Sprint 3.8 adds request bounds and rate limits on purchase-evidence submission and App Store notification ingestion. Billing evidence remains backend-interpreted; account-deleted users cannot request Apple billing configuration or submit purchase evidence.

### Admin abuse
Least privilege, audited access, content redaction and strong admin authentication.

## Review cadence

Threat-model new input types, PDF import, teacher/parent sharing, new admin operations and any model-tool capability before release.

## Secrets

Secret manager/environment only. Production secrets never enter git or iOS bundle.

## Phase 3 hardening closure

| Threat | Implemented control | Remaining release evidence |
|---|---|---|
| Auth brute force / refresh abuse | Redis rate limits on Apple sign-in and refresh; refresh reuse revokes active family/session | production threshold tuning |
| Revoked token replay | protected API session-state filter checks session/user status | broader Phase 4+ endpoint coverage as endpoints are added |
| Oversized malicious JSON | header/body bounds filter with stable `REQUEST_TOO_LARGE` errors | upload-specific MIME/image bounds added in Sprint 4.2; later parser/OCR payloads still need coverage |
| Deleted account reattachment | deleted account tombstone blocks same Apple identity from creating a fresh account silently | external Apple sandbox validation remains `TD-AUTH-001` |
| Privacy lifecycle failure | transactional deletion/export contributors for identity/profile/billing/problem assets | future parse/problem/AI/attempt/mastery/tutor contributors before those stores ship |

<!-- HYBRID_AI_STRATEGY_V3:START -->
## Model-training and serving threats

Future ML introduces additional threats:

- unauthorized training-data extraction;
- evaluation-set leakage;
- poisoned labels/examples;
- malicious model artifacts;
- model-serving endpoint abuse;
- supply-chain risk in model weights/frameworks.

Training/model-serving environments require separate credentials and artifact integrity checks.
<!-- HYBRID_AI_STRATEGY_V3:END -->
