# Error Taxonomy and Recovery Contract

## Purpose

Errors should be semantically stable across backend and iOS. User recovery depends on error category, not raw HTTP/library exception text.

## Categories

### AUTHENTICATION
- AUTH_TOKEN_EXPIRED
- AUTH_REFRESH_REVOKED
- APPLE_IDENTITY_INVALID
- ACCOUNT_DELETION_REQUESTED
- ACCOUNT_DELETED
- ACCOUNT_NOT_ACTIVE
- METHOD_NOT_ALLOWED

### AUTHORIZATION
- RESOURCE_FORBIDDEN
- ENTITLEMENT_REQUIRED
- QUOTA_EXCEEDED

### INPUT
- ASSET_TOO_LARGE
- UNSUPPORTED_FILE_TYPE
- UPLOAD_CONTENT_TYPE_UNSUPPORTED
- UPLOAD_TOO_LARGE
- UPLOAD_RESERVATION_EXPIRED
- UPLOAD_OBJECT_NOT_FOUND
- UPLOAD_CHECKSUM_MISMATCH
- UPLOAD_SIZE_MISMATCH
- UPLOAD_INVALID_STATE
- UPLOAD_STORAGE_UNAVAILABLE
- IMAGE_UNREADABLE
- RECOGNITION_INPUT_UNAVAILABLE
- RECOGNITION_PROVIDER_UNAVAILABLE
- RECOGNITION_TIMEOUT
- RECOGNITION_RATE_LIMITED
- RECOGNITION_SCHEMA_INVALID
- RECOGNITION_OUTPUT_TOO_LARGE
- RECOGNITION_UNSUPPORTED
- RECOGNITION_FAILED
- PROBLEM_PARSE_FAILED
- PROBLEM_UNSUPPORTED
- PROBLEM_CLASS_NOT_SUPPORTED
- PROFILE_VALIDATION_FAILED
- OPTIMISTIC_CONFLICT
- IDEMPOTENCY_KEY_REUSED
- DATA_EXPORT_NOT_FOUND
- DATA_EXPORT_EXPIRED
- DELETION_CONFIRMATION_INVALID

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
- RATE_LIMIT_EXCEEDED
- REQUEST_TOO_LARGE
- ADVANCED_USAGE_LIMIT_REACHED
- TEMPORARY_UNAVAILABLE
- INTERNAL_ERROR

### Sprint 4.2 upload recovery

Upload reservation and completion failures are stable client contracts:

- `UPLOAD_CONTENT_TYPE_UNSUPPORTED` and `UPLOAD_TOO_LARGE` are non-transient input failures; the client should pick another file or regenerate a compliant local asset.
- `UPLOAD_RESERVATION_EXPIRED`, `UPLOAD_OBJECT_NOT_FOUND`, `UPLOAD_CHECKSUM_MISMATCH`, and `UPLOAD_SIZE_MISMATCH` are recoverable by retrying from the local accepted asset. The backend does not mark the asset AVAILABLE.
- `UPLOAD_INVALID_STATE` means the reservation is not in a state that accepts completion.
- `UPLOAD_STORAGE_UNAVAILABLE` is transient infrastructure failure; client retry is bounded and must use idempotency.
- `RESOURCE_FORBIDDEN` is used for wrong-owner or unknown upload IDs without confirming resource existence.

### Sprint 4.4 recognition recovery

Recognition failures are pre-parse failures. `RECOGNITION_INPUT_UNAVAILABLE` means no selected READY recognition derivative exists; the client should return to preprocessing/capture recovery. `RECOGNITION_TIMEOUT`, `RECOGNITION_RATE_LIMITED`, and `RECOGNITION_PROVIDER_UNAVAILABLE` are retryable until job attempts are exhausted. `RECOGNITION_SCHEMA_INVALID` rejects malformed provider output and may retry within the bounded job policy. `RECOGNITION_OUTPUT_TOO_LARGE` and `RECOGNITION_UNSUPPORTED` are terminal for the current input/configuration. None of these codes imply `PROBLEM_UNSUPPORTED` or a verified solution.

### Sprint 4.5 problem parse recovery

Problem parse failures are post-recognition, pre-canonicalization failures. `PROBLEM_PARSE_FAILED` covers provider execution that produced no usable parser result, parser output that remains schema-invalid after bounded attempts, or semantic-invalid output that cannot be accepted. Unsupported problems are not infrastructure failures: the normal client contract is `jobStatus=UNSUPPORTED` / `supportStatus=UNSUPPORTED` with a stable unsupported reason; `PROBLEM_UNSUPPORTED` is the equivalent recovery category if an error surface is needed. Neither state implies a safe verifier representation, solution, classification, or answer.

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
