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

### IDOR
Principal-derived ownership checks on every resource.

### Prompt injection
User content is data, not instructions; strict prompt separation; no arbitrary tool execution.

### Symbolic-expression abuse
No Python eval, parser allowlist, expression complexity limits, verifier timeout.

### Malicious uploads
Size/MIME/dimension/page limits, isolated storage and scanning policy.

### Billing spoof
Server-side App Store validation and idempotent external event handling.

### Admin abuse
Least privilege, audited access, content redaction and strong admin authentication.

## Review cadence

Threat-model new input types, PDF import, teacher/parent sharing, new admin operations and any model-tool capability before release.

## Secrets

Secret manager/environment only. Production secrets never enter git or iOS bundle.

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
