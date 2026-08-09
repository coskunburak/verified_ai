# Runbooks and Failure Modes

## AI provider outage

1. Confirm provider-specific failures.
2. Enable/verify fallback route.
3. Disable nonessential secondary calls if capacity/cost requires.
4. Communicate degraded behavior if user impact is visible.
5. Monitor fallback latency/cost.

## Recognition pipeline failure

1. Use the API trace/correlation ID to inspect `recognition_jobs.status`, `attempt_count`, `last_error_code`, `next_attempt_at`, and `locked_until`.
2. Confirm the selected `OCR_OPTIMIZED` derivative is READY and belongs to the same user/session/source asset.
3. For schema-invalid or oversized output, inspect only safe metadata and reproduce with a synthetic/non-sensitive fixture; do not paste raw recognized student text into logs or tickets.
4. For timeout/429/5xx, verify provider health, route configuration, fallback settings, and recognition rate-limit counters.
5. For stuck RUNNING jobs, allow the stale-job recovery policy to requeue or terminally fail according to attempts.
6. Do not manually mark recognition as parsed, solved, or verified.

## Problem parser pipeline failure

1. Use the API trace/correlation ID to inspect `problem_parse_jobs.status`, `attempt_count`, `last_error_code`, `last_failure_class`, `next_attempt_at`, and `locked_until`.
2. Confirm the parser input is the exact accepted `recognition_evidence.id` and `revision` for the authenticated user's ProblemSession.
3. For schema-invalid or semantic-invalid parser output, reproduce with synthetic fixtures or redacted non-content metadata; do not paste raw expressions, recognized text, or raw parser JSON into logs or tickets.
4. For timeout/429/5xx, verify provider health, route configuration, fallback settings, parser rate-limit counters, and cost budget.
5. For unsupported outcomes, confirm the unsupported reason and client recovery state; do not coerce the problem into a supported equation schema.
6. Do not manually mark a parse as canonical, solved, classified, user-confirmed, or verified.

## Math verifier outage

Solver may continue, but affected answers become UNVERIFIED or verification is deferred. Never silently mark VERIFIED.

## PostgreSQL saturation

- inspect pool/query latency,
- identify expensive queries,
- reduce nonessential background load,
- optimize/index/scale,
- do not blindly increase connection count.

## Redis outage

Caches and throttling may degrade/reset, but canonical learning/billing state remains safe.

Sprint 3.8 rate limiting uses Redis only for counters. If Redis is unavailable, fail-closed policies protect sign-in/refresh/deletion confirmation; fail-open policies increment degraded-open metrics and preserve user-critical billing/logout/export progress.

## Account deletion failure

1. Confirm whether the account is `DELETION_REQUESTED`, `DELETION_IN_PROGRESS`, or `DELETED`.
2. Inspect `privacy_events`, session revocation rows, learning profile presence, entitlement status, billing events, `problem_sessions`, `problem_assets`, and corresponding object-storage keys.
3. Do not manually delete minimized billing/security audit rows unless legal/compliance approves.
4. Re-run deletion only through the backend service path or a reviewed corrective script that invokes equivalent lifecycle contributors.
5. Record any future module missing a lifecycle contributor as release-blocking for that module.

## Problem asset upload failure

1. Use the API trace/correlation ID to inspect reservation and completion responses.
2. Confirm `problem_assets.status`, `upload_expires_at`, expected `content_type`, `size_bytes`, and `checksum_sha256`.
3. Inspect object-storage availability and bucket policy without exposing object bytes.
4. For checksum, size, or content-type mismatches, confirm the backend deleted the mismatched object and left the asset non-AVAILABLE.
5. For expired reservations, ask the client to retry from the retained local accepted asset.
6. Watch `problem.asset.*` metrics and `security.rate_limit.*` counters for abuse or storage degradation.

## Billing notification backlog

Use authoritative transaction/server state sync, process events idempotently and monitor backlog age.

## Verification quality incident

If false VERIFIED is suspected:
1. disable affected policy/method via feature/config,
2. downgrade new affected results,
3. identify policy/model/problem-type scope,
4. run golden regression,
5. reverify affected historical cases if needed,
6. communicate material impact transparently.

## Cost spike

Inspect traffic, abusive accounts, route changes, retries and prompt/context growth. Emergency downgrade of noncritical route is allowed if trust invariants remain intact.

Recognition cost spikes are investigated through `ai.vision.recognition.*` metrics, route configuration, retry/fallback counts, and request volume. Do not reduce validation or fabricate confidence to lower cost.

Parser cost spikes are investigated through `ai.problem.parse.*` metrics, route configuration, retry/fallback counts, schema-invalid rate, semantic-invalid rate, unsupported rate, and request volume. Do not skip schema/semantic validation, fabricate certainty, or force unsupported problems into supported structures to lower cost.

<!-- HYBRID_AI_STRATEGY_V3:START -->
## Additional runbooks required

Maintain runbooks for:

- provider quota exhaustion;
- sudden inference cost spike;
- secondary-solver escalation storm;
- model quality regression;
- future self-hosted inference saturation/outage;
- emergency route rollback.
<!-- HYBRID_AI_STRATEGY_V3:END -->
