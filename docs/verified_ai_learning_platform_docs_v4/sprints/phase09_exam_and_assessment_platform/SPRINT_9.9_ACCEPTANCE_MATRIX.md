# Sprint 9.9 acceptance matrix

Release state: `COMPLETED — OWNER ACCEPTANCE PASSED` (`2026-11-18`). Feature flag `verifiedai.exam.analytics-enabled` remains `false` by default; production activation, commit, and push require separate owner authorization.

| Gate | Test / Script | Evidence | Status | Remaining Action |
| --- | --- | --- | --- | --- |
| 9.9 contracts | `validate_sprint_9_9_contracts.py` | Owner PASS (`PASS: Sprint 9.9 exam-analytics-progress schema, fixtures, mutations, and OpenAPI mirrors verified.`) | PASS — OWNER VERIFIED | None |
| Global schema/API/localization | `validate_json_schemas.py`, `check_contracts.py` | Owner PASS (`465` EN/TR localizations, Sprint 8.1..9.9 schemas/OpenAPI verified; `contract check passed`) | PASS — OWNER VERIFIED | None |
| Domain, service, controller & Modulith | `ExamAnalyticsPolicyTest`, `ExamAnalyticsServiceTest`, `ExamAnalyticsControllerTest`, `ModulithArchitectureTest` | Owner PASS: 7 + 5 + 2 + 1 (+1 privacy) = 16/16 (`BUILD SUCCESS`) | PASS — OWNER VERIFIED | None |
| Privacy lifecycle | `ExamAnalyticsPrivacyLifecycleIntegrationTest` | Owner PASS: 1/1 (`examAnalyticsSnapshots` export, child-first delete, TTL purge) | PASS — OWNER VERIFIED | None |
| PostgreSQL V063 / Testcontainers | `ExamAnalyticsSnapshotIntegrationTest` | Owner PASS: 2/2 (`Flyway` applied through `v063`, CHECK/FK/cascade/20-limit/regrade) | PASS — OWNER VERIFIED | None |
| HTTP integration | `ExamAnalyticsHttpIntegrationTest` | Owner PASS: 1/1 (`TEST-com.verifiedai.exam.integration.ExamAnalyticsHttpIntegrationTest.xml` after `401` `Cache-Control` `containsString("no-store")` update) | PASS — OWNER VERIFIED | None |
| iOS static | `sprint_9_9_swift_check.sh` | Owner PASS (`SPRINT_9_9_STATIC_CHECKS_PASSED_HOST_TESTS_STILL_REQUIRED`) | PASS — OWNER VERIFIED | None |
| iOS runtime XCTest | `ExamProgressHistoryViewModelTests` | Owner PASS: 5/5 on `iPhone 16 Pro` (`iOS 18.4` Simulator, `Test-VerifiedAI-2026.11.18_17-58-22-+0300.xcresult`) | PASS — OWNER VERIFIED | None |
| Owner acceptance entrypoint | `sprint_9_9_owner_acceptance.sh --VerifyHost` | Owner PASS (`SPRINT_9_9_IOS_AND_CONTRACT_GATES_PASSED_REVIEW_RECORDED_BACKEND_EVIDENCE_BEFORE_RELEASE`) | PASS — OWNER VERIFIED | None |
| Documentation | `docs_check.py` | Re-verified PASS (`437 Markdown files`) after final closure edits | PASS | None |

## Acceptance scope and limits

- **Rollout state:** `verifiedai.exam.analytics-enabled` defaults to `false`; production activation requires separate owner authorization.
- **AI economics & privacy:** `training_eligible = FALSE`, `llm_tokens_used = 0`, `inference_cost_usd = 0`, and 24-hour TTL purge are enforced in V063, API DTOs, iOS validation/SQLite cache, and `ExamPrivacyLifecycleContributor`.
- **Statistical & trap semantics:** `INV-EXAM-005` computes moving average (`MA3`) and topic pacing over at most the latest 20 scored sessions per owner and canonical exam (`TR_YKS_TYT_MATH` / `TR_YKS_AYT_MATH`); trap correlation uses `SESSION_MATCHED_TRAP_INCIDENCE` (`associationIsCausal = false`) and reports `conqueredTrapsEvidenceStatus = UNAVAILABLE` (`conqueredTrapsCount = 0`) because historical trap-state transition series do not yet exist.
