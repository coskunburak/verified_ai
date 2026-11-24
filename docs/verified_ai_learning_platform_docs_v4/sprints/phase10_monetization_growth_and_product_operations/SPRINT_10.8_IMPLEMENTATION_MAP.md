# Sprint 10.8 Implementation Map — Admin and Support Console Foundations

## 1. `PRODUCTION_EXECUTION_V3` 12-Item Decomposition

1. **Owning Bounded Context / Module:**
   - Primary owning Spring Modulith module: `com.verifiedai.admin` (`@ApplicationModule(displayName = "Admin")`, `com.verifiedai.admin.application` exposed via `@NamedInterface("application")`).
   - Allowed outbound dependencies (`INV-ADM-007`): `com.verifiedai.billing.application..`, `com.verifiedai.featureflags.application..`, and `com.verifiedai.sharedkernel..`.
   - Zero peer modules may depend on `com.verifiedai.admin..`.

2. **Domain States / Invariants Touched (`INV-ADM-001`..`INV-ADM-007`):**
   - `WrongAnswerReportStatus` & `WrongAnswerReportStateMachine`: `OPEN -> {IN_REVIEW, RESOLVED_CONFIRMED_DEFECT, RESOLVED_NOT_A_DEFECT, RESOLVED_DUPLICATE, DISMISSED}`, `IN_REVIEW -> {RESOLVED_CONFIRMED_DEFECT, RESOLVED_NOT_A_DEFECT, RESOLVED_DUPLICATE, DISMISSED}`, terminal states `{}` immutable (`409 ADMIN_INVALID_TRANSITION`). Resolution requires non-null `WrongAnswerResolutionCode` and `resolutionNote` (`5..400` chars).
   - `INV-ADM-001`: All `/v1/admin/support/**`, `/api/v1/admin/support/**`, `/v1/admin/ai-ops/**`, `/api/v1/admin/ai-ops/**` routes require `ROLE_ADMIN` (`403 ADMIN_SUPPORT_FORBIDDEN`). Raw content grant issuance and inspection additionally require `ADMIN_RAW_CONTENT_READ` or `ROLE_ADMIN_ENGINEERING` (`403 ADMIN_RAW_CONTENT_FORBIDDEN`).
   - `INV-ADM-002`: Standard `User Lookup` and `Problem Trace` responses always enforce `rawContentRedacted = true` and strip `object_key`, `raw_output_jsonb`, `normalized_problem_jsonb`, raw OCR/problem text, raw verification expressions, raw Apple JWS payloads, and session token hashes. Raw content access requires explicit time-bounded (`5..60` min) `admin_raw_content_access_grants` row tied to `(actor_user_id, problem_session_id)` and audited on creation (`RAW_CONTENT_GRANT_CREATED`) and every read (`RAW_CONTENT_INSPECTED`).
   - `INV-ADM-003`: Zero direct SQL `INSERT`/`UPDATE`/`DELETE` mutations on foreign-owned tables (`users`, `entitlements`, `entitlement_quota_ledgers`, `app_store_subscriptions`, `feature_flag_definitions`, `problem_sessions`, `verification_runs`). All remediations execute via owning-module `@NamedInterface("application")` services.
   - `INV-ADM-004`: Every mutation requires `Idempotency-Key` (`^[A-Za-z0-9:._-]{8,128}$`), non-blank `reason` (`5..240` chars, or `10..240` for elevated grants), advisory lock (`pg_advisory_xact_lock`), SHA-256 `payload_hash` replay/conflict enforcement (`409 ADMIN_IDEMPOTENCY_CONFLICT`), optimistic locking (`expectedVersion` -> `409 OPTIMISTIC_CONFLICT`), and immutable `admin_support_audit_logs`.
   - `INV-ADM-005`: Learner `POST /api/v1/support/wrong-answer-reports` derives `userId` from `AuthenticatedUser.from(jwt)`, enforces active account (`403 ACCOUNT_NOT_ACTIVE`), and verifies `problem_sessions(id, user_id)` ownership (`404 PROBLEM_SESSION_NOT_FOUND`). Server snapshots `verification_status`, `policy_version`, `model_version`, and `problem_type`.
   - `INV-ADM-006`: All `V072` tables enforce `training_eligible BOOLEAN NOT NULL DEFAULT FALSE CHECK (NOT training_eligible)`. `AdminSupportPrivacyLifecycleContributor` (`@Order(33)`, `category() = "admin_support"`) exports PII-free `wrongAnswerReports` and deletes user-scoped `admin_raw_content_access_grants` and `wrong_answer_reports` alongside `ON DELETE CASCADE`, while preserving non-PII operational `admin_support_audit_logs`.
   - `INV-ADM-007`: Acyclic Modulith boundaries verified in `ModulithArchitectureTest`.

3. **iOS Screens / View-Models / Use Cases Touched:**
   - None (`iOS impact = none`). Sprint 10.8 delivers backend-only internal admin/support console foundations and the additive server-side learner wrong-answer report contract (`POST /api/v1/support/wrong-answer-reports`) without modifying any iOS views or Xcode project files.

4. **Backend Commands / Queries / Policies / Adapters Touched:**
   - **Domain:** `AdminAuditAction`, `AdminAuditTargetType`, `WrongAnswerReportReason`, `WrongAnswerReportStatus`, `WrongAnswerResolutionCode`, `WrongAnswerReportStateMachine`, `AdminRedactionPolicy`, `AdminLookupType`.
   - **Application:** `AdminSupportQueryService`, `AdminRawContentAccessService`, `WrongAnswerReportService`, `AdminRemediationService`, `AdminSupportPrivacyLifecycleContributor`, `AdminSupportMetrics`, plus immutable view records.
   - **Infrastructure:** `AdminSupportProperties`, `AdminSupportConfiguration`, `AdminSupportReadJdbcRepository` (read-only `SELECT` projections), `AdminSupportWriteJdbcRepository` (strictly `V072` tables + advisory locks).
   - **API:** `AdminSupportConsoleController`, `AdminAiOperationsController`, `LearnerWrongAnswerReportController`, and immutable request/response DTO records.
   - **Shared / Security:** `ApiErrorCode` (11 Sprint 10.8 codes appended), `SecurityConfiguration` (`accessDeniedHandler` extended for `/admin/support/**` and `/admin/ai-ops/**`).

5. **Database Migrations / Indexes Touched:**
   - `V001`–`V071` remain byte-for-byte immutable.
   - Forward migration `V072__sprint_10_8_admin_and_support_console_foundations.sql` adds:
     - `admin_support_audit_logs` + `uq_admin_support_audit_idempotency_key`, `idx_admin_support_audit_occurred`, `idx_admin_support_audit_target`.
     - `admin_raw_content_access_grants` + `uq_adm_raw_grant_idempotency_key`, `idx_admin_raw_content_grants_actor_session`, `idx_admin_raw_content_grants_target_user`.
     - `wrong_answer_reports` + `uq_war_user_idempotency_key`, `idx_wrong_answer_reports_queue`, `idx_wrong_answer_reports_user_time`, `idx_wrong_answer_reports_session`.

6. **API / Event / Schema Changes:**
   - 13 endpoint pairs (26 paths across `/v1/...` and `/api/v1/...`) with `Cache-Control: no-store, private`, `bearerAuth`, and `ProblemDetails` error responses.
   - 10 Draft 2020-12 JSON Schemas + 10 positive fixtures under `packages/schemas/admin/`.
   - Byte-for-byte identical dual OpenAPI mirrors: `packages/contracts/openapi/public-api.yaml` and `packages/openapi/verified-ai-core-api.yaml`.

7. **AI Capability / Route Implications:**
   - `$0.00` incremental AI/LLM spend (`llmTokensUsed = 0`, `inferenceCostUsd = 0.00`). Zero AI provider calls or route changes.

8. **Privacy / Security Implications:**
   - Default redaction (`rawContentRedacted = true`), elevated short-lived audited raw-content grants (`5..60` min), BOLA ownership enforcement on learner wrong-answer reports, PII/secret pattern rejection/redaction in `AdminRedactionPolicy`, `ROLE_ADMIN` + `ADMIN_RAW_CONTENT_READ`/`ROLE_ADMIN_ENGINEERING` RBAC, `CHECK (NOT training_eligible)`, and GDPR export/deletion via `AdminSupportPrivacyLifecycleContributor`.

9. **Telemetry / SLO / Cost Implications:**
   - Low-cardinality Micrometer counters via `AdminSupportMetrics`:
     - `verifiedai.admin.query.total{kind}`
     - `verifiedai.admin.remediation.total{action,outcome}`
     - `verifiedai.admin.raw_content.total{action}`
     - `verifiedai.admin.wrong_answer.total{status}`
   - Zero user IDs, session IDs, object keys, or student content in metric tags or logs.

10. **Test / Evaluation Evidence Required (Suites A–E):**
    - Suite A: `WrongAnswerReportStateMachineTest`, `AdminRedactionAndAccessPolicyTest`.
    - Suite B: `Sprint108AdminMigrationTest` (Testcontainers PostgreSQL 16, `.target("71") -> .target("72")`).
    - Suite C: `AdminSupportRemediationAndGdprIT` (Testcontainers PostgreSQL 16).
    - Suite D: `AdminSupportConsoleSecurityIT` (`@SpringBootTest` + MockMvc + Testcontainers).
    - Suite E: `ModulithArchitectureTest`, `ModularityTest`, `Sprint107FeatureFlagMigrationTest`, `Sprint106AnalyticsMigrationTest`, plus `validate_sprint_10_8_contracts.py` and `docs_check.py`.

11. **Rollout / Feature-Flag Strategy:**
    - Three fail-closed flags in `application.yml` defaulting to `false`:
      - `verifiedai.admin.console-enabled` (`VERIFIEDAI_ADMIN_CONSOLE_ENABLED:false`)
      - `verifiedai.admin.remediation-enabled` (`VERIFIEDAI_ADMIN_REMEDIATION_ENABLED:false`)
      - `verifiedai.admin.raw-content-access-enabled` (`VERIFIEDAI_ADMIN_RAW_CONTENT_ACCESS_ENABLED:false`)

12. **Rollback / Recovery Plan:**
    - Set all three `VERIFIEDAI_ADMIN_*` environment variables to `false` to fail-close all admin/support endpoints with `503` without dropping `V072` tables or altering `V001`–`V071`.

---

## 2. AI Economics / Proprietary-Model Guardrail (7 Explicit Answers)

1. **Does this add or multiply an inference call?**
   - No. Sprint 10.8 adds `0` AI/LLM inference calls (`llmTokensUsed = 0`, `inferenceCostUsd = 0.00`).
2. **What is the expected invocation rate per learner action?**
   - `0` inference invocations per learner action (`POST /api/v1/support/wrong-answer-reports`) and `0` per admin lookup, trace, remediation, or raw-content inspection.
3. **Can deterministic code/cache remove the call?**
   - Yes; the entire Sprint 10.8 subsystem is 100% deterministic Java + PostgreSQL `JdbcClient` queries, SHA-256 hashing, advisory locks, and owning-module service calls.
4. **Can an economy-tier approved route satisfy the task?**
   - Not applicable; no LLM route is invoked (`$0.00` spend).
5. **Is a secondary solver actually required, and what triggers it?**
   - No; Sprint 10.8 never invokes primary or secondary solvers.
6. **What telemetry will reveal cost regression?**
   - `AdminAiOperationsController` (`GET /api/v1/admin/ai-ops/overview`) surfaces aggregated `ai_usage_records` spend, fallback/failure rates, and kill-switch flag states, while `AdminSupportMetrics` tracks admin operations without inference cost.
7. **Does this create data that might later be useful for training? If yes, it remains not training eligible by default and requires separate governed eligibility.**
   - Wrong-answer reports and audit logs are strictly operational/support records and are non-training-eligible by default (`training_eligible BOOLEAN NOT NULL DEFAULT FALSE CHECK (NOT training_eligible)` across all three `V072` tables and `"const": false` in all schemas).

---

## 3. Safe Remediation vs. Forbidden Direct-Edit Matrix

| Remediation Workflow | Owning Module & Authorized Public SPI | Forbidden Direct-Edit Pattern (Blocked by `INV-ADM-003` & `INV-ADM-007`) | Audit & Idempotency Guarantee |
|---|---|---|---|
| **Billing Reconcile** (`POST /admin/support/remediations/billing-reconcile`) | `com.verifiedai.billing.application.AppleBillingApplicationService.reconcileFromTransaction(userId, transactionId)` | Direct `INSERT`/`UPDATE` on `entitlements`, `app_store_subscriptions`, or `app_store_transactions` | `Idempotency-Key` + SHA-256 `payload_hash` + `BILLING_RECONCILE_EXECUTED` row in `admin_support_audit_logs` |
| **Entitlement & Quota Sync** (`POST /admin/support/remediations/quota-sync`) | `EntitlementApplicationService.resolveAccess(userId)` + `QuotaLedgerService.getOrInitializeSummary(userId, access)` | Direct `UPDATE` on `entitlement_quota_ledgers` or `entitlements` | `Idempotency-Key` + SHA-256 `payload_hash` + `QUOTA_SYNC_EXECUTED` row in `admin_support_audit_logs` |
| **Emergency Kill-Switch Linkage** (`POST /admin/support/remediations/kill-switch`) | `com.verifiedai.featureflags.application.FeatureFlagGovernanceService.toggleKillSwitch(flagKey, command, actorUserId)` | Direct `UPDATE` on `feature_flag_definitions` or `experiment_definitions` | Atomic linked audit: `feature_flag_audit_logs` (written by `FeatureFlagGovernanceService`) + `KILL_SWITCH_LINKED_EXECUTED` in `admin_support_audit_logs` |
| **Wrong-Answer Report Review** (`PATCH /admin/support/wrong-answer-reports/{reportId}`) | `com.verifiedai.admin.application.WrongAnswerReportService` + `WrongAnswerReportStateMachine` | Direct `UPDATE` on `verification_runs`, `problem_parses`, or `problem_sessions` | Optimistic lock (`expectedVersion`) + `Idempotency-Key` + `WRONG_ANSWER_REPORT_REVIEWED` in `admin_support_audit_logs` |
