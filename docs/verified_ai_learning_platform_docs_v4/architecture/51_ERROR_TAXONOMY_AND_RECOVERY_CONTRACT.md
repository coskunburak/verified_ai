# Error Taxonomy and Recovery Contract

## Purpose

Errors should be semantically stable across backend and iOS. User recovery depends on error category, not raw HTTP/library exception text.

## Categories

### AUTHENTICATION
- AUTH_TOKEN_EXPIRED
- AUTH_REFRESH_REVOKED
- APPLE_IDENTITY_INVALID

### AUTHORIZATION
- RESOURCE_FORBIDDEN
- ENTITLEMENT_REQUIRED
- QUOTA_EXCEEDED

### INPUT
- ASSET_TOO_LARGE
- UNSUPPORTED_FILE_TYPE
- IMAGE_UNREADABLE
- PROBLEM_PARSE_FAILED
- PROBLEM_UNSUPPORTED
- PROBLEM_CLASS_NOT_SUPPORTED
- OPTIMISTIC_CONFLICT
- IDEMPOTENCY_KEY_REUSED

### SOLVING
- AI_TEMPORARILY_UNAVAILABLE
- SOLVER_TEMPORARILY_UNAVAILABLE
- SOLVER_SCHEMA_INVALID
- SOLVE_JOB_FAILED
- SOLUTION_REQUIRES_RETRY

### VERIFICATION
- VERIFICATION_UNAVAILABLE
- VERIFICATION_CONFLICT
- VERIFICATION_UNSUPPORTED

### BILLING
- PURCHASE_VERIFICATION_FAILED
- ENTITLEMENT_SYNC_FAILED

### SYSTEM
- RATE_LIMITED
- ADVANCED_USAGE_LIMIT_REACHED
- TEMPORARY_UNAVAILABLE
- INTERNAL_ERROR

## Recovery semantics

Every API error may include:
- recoverable boolean,
- retryAfter,
- userAction code,
- support traceId.

Possible user actions:
- RETAKE_IMAGE
- EDIT_PARSE
- RETRY
- SIGN_IN
- UPGRADE
- CONTACT_SUPPORT
- NONE

## iOS mapping

The client maps stable error code to localized copy and action. It never displays provider error payload directly.

## Retry safety

Only errors designated transient are automatically retried. Quota/auth/input errors are not.

<!-- HYBRID_AI_STRATEGY_V3:START -->
## AI/cost-aware recovery errors

Recommended product-safe error families include:

- `AI_TEMPORARILY_UNAVAILABLE` — approved inference routes unavailable within retry budget;
- `SOLUTION_REQUIRES_RETRY` — transient solve failure with safe retry;
- `VERIFICATION_UNAVAILABLE` — solution may be presented only under explicit unverified semantics;
- `ADVANCED_USAGE_LIMIT_REACHED` — entitlement/fair-use limit, not provider-token terminology;
- `PROBLEM_CLASS_NOT_SUPPORTED` — current verification/solver support boundary;
- `MODEL_ROUTE_ROLLED_BACK` is internal operational metadata, not normally a consumer error.

Never expose provider secrets, account quota details, internal model IDs unnecessarily, or cost values in user-facing error payloads.
<!-- HYBRID_AI_STRATEGY_V3:END -->
