# Admin, Support, and Internal Tools

## Why admin tooling is production-critical

An AI product cannot be debugged from generic server logs alone. Support must trace a user's reported problem through parse, solve, verification and entitlement state safely.

## Admin capabilities

### User lookup
Search by safe identifiers. Show account/entitlement status and limited profile metadata.

### Problem trace
Display:
- problem session state,
- asset metadata,
- parse revisions,
- solver runs,
- verification run/signals,
- trace ID,
- latency/cost.

Raw content access requires elevated permission and audit.

### Wrong-answer reports
Queue with:
- report reason,
- verification status,
- policy/model versions,
- problem type,
- reviewer resolution.

### Billing support
View authoritative entitlement and external transaction references. Support cannot manually flip DB booleans; corrections go through billing commands.

### AI operations
- provider health,
- route config,
- prompt/model active versions,
- cost dashboards,
- schema failure samples.

### Feature flags
Controlled access to release/kill switches.

## Admin security

- dedicated strong authentication,
- least privilege,
- audited actions,
- session timeout,
- raw student content hidden by default,
- production mutation heavily restricted.

## Support workflow

User supplies support reference/trace ID. Support can find logical flow without needing screenshots of internal systems.

<!-- HYBRID_AI_STRATEGY_V3:START -->
## AI route and economics support tooling

Authorized internal tooling should expose, by trace/problem session:

- route policy version;
- provider/model provenance;
- primary/secondary invocation and escalation reason;
- verification signals/status;
- latency and cost units;
- fallback/retry history;
- prompt/schema versions.

Default support views should avoid raw student content. Content access, when genuinely required for a reported issue, must be separately authorized/audited.

Future ModelOps views may show model-release status, evaluation report and rollback controls but must not grant unrestricted dataset access to general support roles.
<!-- HYBRID_AI_STRATEGY_V3:END -->

## Sprint 10.8 implemented admin and support foundation

- **Module & Boundaries (`com.verifiedai.admin`):** Constructor-injected Spring Modulith module (`@ApplicationModule(displayName = "Admin")`) depending exclusively on `com.verifiedai.billing.application..`, `com.verifiedai.featureflags.application..`, and `com.verifiedai.sharedkernel..`. Cross-module operational reads execute via strictly read-only `JdbcClient` projections in `AdminSupportReadJdbcRepository`.
- **RBAC & Fail-Closed Flags:** All `/v1/admin/support/**`, `/api/v1/admin/support/**`, `/v1/admin/ai-ops/**`, and `/api/v1/admin/ai-ops/**` endpoints require `ROLE_ADMIN` (`403 ADMIN_SUPPORT_FORBIDDEN`) and emit `Cache-Control: no-store, private`. All three `verifiedai.admin.*` flags default to `false` (`503`).
- **Default Redaction & Elevated Raw-Content Grants:** Standard `GET /admin/support/users/lookup` and `GET /admin/support/problems/trace` responses enforce `rawContentRedacted = true` and strip `object_key`, `raw_output_jsonb`, `normalized_problem_jsonb`, raw Apple JWS payloads, and session token hashes. Raw artifact inspection requires `ADMIN_RAW_CONTENT_READ` or `ROLE_ADMIN_ENGINEERING`, an explicit `POST .../raw-content-access` grant (`5..60` minute TTL, ticket reference, reason, `Idempotency-Key`), and audited `GET .../raw-content?grantId=...` reads (`409 ADMIN_RAW_CONTENT_GRANT_EXPIRED` after expiry).
- **Wrong-Answer Reports & Safe Remediations:** Learners submit BOLA-verified reports via `POST /api/v1/support/wrong-answer-reports`; admins review/resolve via `GET` / `PATCH /admin/support/wrong-answer-reports/{reportId}` with optimistic locking and `WrongAnswerReportStateMachine`. Billing reconciliation, quota sync, and emergency kill switches execute exclusively through owning-module application services (`AppleBillingApplicationService`, `EntitlementApplicationService`, `QuotaLedgerService`, `FeatureFlagGovernanceService`) with `Idempotency-Key` protection and immutable `admin_support_audit_logs` records (`$0.00` incremental LLM spend).
