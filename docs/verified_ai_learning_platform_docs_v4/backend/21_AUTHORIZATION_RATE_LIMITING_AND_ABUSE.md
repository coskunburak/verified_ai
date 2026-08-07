# Authorization, Rate Limiting, and Abuse Protection

## Authorization

Every resource access verifies ownership/role. Never trust client-supplied userId or UUID obscurity.

Authenticated principal determines the current user.

## Roles

Initial:
- USER
- ADMIN_SUPPORT
- ADMIN_ENGINEERING

Apply least privilege. Support access to raw student content is restricted and auditable.

## Premium authorization

Costly operations use server capability guards such as `PremiumCapability.VERIFIED_SOLVE`. Guard evaluates entitlement + quota.

## Rate limits

Dimensions:
- user,
- IP,
- endpoint,
- cost class,
- risk/device signal.

Protect auth, upload, solve, tutor, document import and billing sync.

## Abuse scenarios

### Automated solving/scraping
Quotas, anomaly detection, throttling and optional attestation signals.

### Prompt injection in problem text
Treat all content as untrusted data. Do not concatenate into system instructions. Tool calls remain allowlisted.

### Oversized/malicious upload
MIME/type/size/dimension/page limits; document scanning/isolation policy.

### Entitlement replay
Server verifies and idempotently records external transaction/events.

### Refresh-token theft
Rotation, family tracking, reuse detection and revocation.

## Security logging

Record suspicious auth, refresh reuse, unusual solve volume, privileged admin access and billing anomalies without secrets or raw tokens.

<!-- HYBRID_AI_STRATEGY_V3:START -->
## AI cost-abuse controls

Expensive AI endpoints require layered limits by authenticated account, IP/device risk where appropriate, entitlement, and operation. Rate limits protect infrastructure; product quotas protect economics. They are related but not interchangeable.

Detect repeated retries, image flooding, automated solving abuse, account sharing patterns, and route-escalation abuse. A malicious user must not be able to force premium/secondary routes by crafting untrusted client flags.
<!-- HYBRID_AI_STRATEGY_V3:END -->
