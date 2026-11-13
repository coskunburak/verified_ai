# Sprint 8.5 Execution Report — Missed-Day Recovery, Study Plan Rebalancing & Anti-Backlog Defense

- **Date:** 2026-11-13
- **Status:** Completed
- **Owner Acceptance Script:** `scripts/quality/sprint_8_5_owner_acceptance.sh --full`
- **Architecture & Domain:** `studyplan.rebalance` (Modular Monolith + Native iOS SwiftUI/SQLite + OpenAPI 3.1 + Flyway PostgreSQL)

## 1. Delivered Capabilities & Invariants

- **`INV-REBAL-001` (Anti-Backlog Wall):** Enforced a strict cap of at most `3` active tasks per day (`<= 1` trap repair + `<= 2` spaced reviews). Prior-day `IN_PROGRESS` sessions are closed as `EXPIRED`, open pending tasks are expired/pruned, and overflow spaced reviews are horizon-shifted within `[1, 14]` days without streak loss.
- **`INV-REBAL-002` (Cognitive-Load Bounded Daily Budget):** Implemented `MissedDayRecoveryPolicy.rebalanceBudget(...)` computing `B_d' = min(25, snapToAllowedBudget(ceil(B_d * T_exam / max(1, T_exam - k))))` snapped to `{5, 10, 15, 20, 25}`. Low-ROI items (`deltaNet < 0.20`) are pruned first, followed by lowest retention urgency.
- **`INV-REBAL-003` (5-Minute Mini Mode Downshift):** Added explicit Mini Mode downshift (`preferMiniMode = true` or `MINI_MODE_DOWNSHIFT`), constraining the plan to `5` minutes, `1` active task, `miniModeActivated = true`, and `PruneReasonCode.MINI_MODE_SINGLE_FOCUS`.
- **`INV-REBAL-004` (Idempotency):** Enforced idempotent rebalancing keyed by `(user_id, recovery_date, trigger_type)` / `idempotency_key` in PostgreSQL (`uq_rebalance_events_user_idempotency`), returning `idempotent_replay = true` on duplicate submissions.
- **`INV-REBAL-005` (BOLA Isolation & Privacy Lifecycle Purge):** Enforced user-scoped `STUDY_PLAN_RECOVERY_NOT_FOUND` (404) isolation across backend APIs and SQLite caches, alongside `RebalancePrivacyPurgeContributor` (`@Order(27)`) deleting `study_plan_rebalance_events` rows inside `< 500 ms`.
- **`INV-REBAL-006` (Tone, Rendering & Cost Guardrails):** Verified zero DOC-83 §7 stigmatizing lexicon terms across Java, Swift, SQL, JSON schemas, OpenAPI contracts, and EN/TR `Localizable.xcstrings`, zero WebKit/KaTeX/MathJax usage on iOS, and `$0.00` incremental LLM cost (100% deterministic policy).

## 2. Verification Summary

- **Schemas & OpenAPI Mirror:** `python3 scripts/quality/validate_json_schemas.py` verified `recovery-status-v1.schema.json`, `rebalance-response-v1.schema.json`, `daily-coach-v1.schema.json`, byte-identical OpenAPI 3.1 contracts (`packages/contracts/openapi/public-api.yaml` and `packages/openapi/verified-ai-core-api.yaml`), and `86` EN/TR localization keys.
- **Database Migration (`V048`):** Audited `study_plan_rebalance_events` DDL, 9 CHECK constraints, 3 indexes, `chk_coach_session_budget_minutes` (`{5, 10, 15, 20, 25}`), and `chk_daily_coach_session_status` (`EXPIRED`).
- **Java Backend Suite:** Ran 39 unit/controller/integration tests (`MissedDayRecoveryPolicyTest`, `StudyPlanRebalancingServiceTest`, `RebalanceControllerTest`, `DailyCoachSessionServiceTest`, `DailyCoachControllerTest`, `SpacedReviewServiceTest`, `ModularityTests`) with 39 passed.
- **iOS Native Layer:** Verified `swiftc -parse` across all modified/new Swift files and standalone iOS simulator `swiftc -typecheck` for `RebalanceSQLiteCache.swift`, `StudyPlanRebalanceViewModel.swift`, and `PlanRebalanceNotificationSheet.swift`, plus `DailyCoachViewModel.swift` integration.
- **Docs & Security Hygiene:** Verified `python3 scripts/quality/docs_check.py` and `python3 scripts/security/test_secret_scan.py`.
