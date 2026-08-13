# Sprint 4.10 Execution Report

## Final Status

LOCAL FIXTURE EVALUATION FOUNDATION COMPLETE - PRODUCTION PROVIDER AND HOLDOUT EVIDENCE BLOCKED

Sprint 4.10 establishes a reproducible ingestion golden-dataset evaluation gate for Phase 4 OCR/parser/canonicalization/classification behavior. It does not claim real provider accuracy, protected holdout performance, solver quality, verification correctness, tutoring quality, or Phase 5 readiness.

## Implemented

- Synthetic `ingestion-v1` golden dataset with 18 non-training-eligible cases and 13 critical cases.
- Coverage slices for clean arithmetic, equations, inequality, ambiguous sign review, user correction selection, unsupported input, retryable recognition/parser/classification states, stale lineage, cross-user BOLA rejection, prompt-injection content, Turkish decimal/percent notation, invalid geometry, source-fabrication rejection, and unsafe canonical input.
- Dataset manifest with asset checksums, route/prompt/schema provenance, protected-holdout blocked status, privacy constraints, and license notes.
- `ingestion-evaluation-case-v1` and `ingestion-evaluation-report-v1` schemas.
- Standard-library Python tooling for dataset validation, recognition metrics, parser metrics, end-to-end ingestion metrics, report generation, release comparison, and regression tests.
- Approved deterministic local fixture baseline at `evaluations/baselines/production-ingestion-v1.json`.
- Hard release gates for false authoritative accept, false ready-for-solve, unsafe false accept, invented source references, cross-user acceptance, stale lineage, and prompt-instruction execution.
- `make eval-ai` and `scripts/evaluation/run-golden-suite.sh` as the local deterministic evaluation entrypoint.
- `scripts/evaluation/update-approved-baseline.sh` with explicit `--approve` and local-fixture-only promotion checks.
- GitHub Actions workflow `.github/workflows/ai-evaluation.yml` for deterministic PR/push gating and blocked connected scheduled/workflow-dispatch evaluation.
- Canonical documentation updates for AI evaluation, cost/latency, security, privacy, observability, CI, runbooks, test strategy, Definition of Done, requirements, capability coverage, and technical debt.

## Latest Local Evaluation Evidence

- Command: `make eval-ai`
- Decision: `PASS`
- Execution mode: `LOCAL_FIXTURE_REGRESSION`
- Dataset checksum: `769befe32df0f8646aec4c5fa49c777bb4b39b990e92fd70af18811f6270a50f`
- Latest report checksum: `0c4c9dcca1531906fdc815df0beb90f26b29b6704fd3d1326da33d8af5618da7`
- Cases: 18 total, 13 critical
- Recognition normalized exact match rate: `0.9444444444444444`
- Recognition character error rate: `0.05555555555555555`
- Parser semantic valid rate: `0.9444444444444444`
- Classification primary skill accuracy: `1.0`
- Pipeline p50 latency: `28.5 ms`
- Pipeline p95 latency: `38.3 ms`
- Pipeline average cost: `0.0 micros`

Critical trust counters:

- `false_authoritative_accept_count`: 0
- `false_ready_for_solve_count`: 0
- `unsafe_input_false_accept_count`: 0
- `invented_source_reference_count`: 0
- `cross_user_access_accepted_count`: 0
- `prompt_instruction_executed_count`: 0

## Blocked Evidence

- Connected representative provider evaluation: `BLOCKED_NO_APPROVED_PROVIDER_ROUTE`.
- Protected holdout evaluation: `BLOCKED_PROTECTED_HOLDOUT`.
- Real-device capture validation remains `TD-CAPTURE-001`.

These blockers are recorded as capability/requirement limitations and technical debt. They must not be treated as PASS in release notes or dashboards.

## Validation

- [x] `python3 -m unittest evaluations/runners/test_ingestion_evaluation.py`
- [x] `python3 evaluations/runners/validate_ingestion_dataset.py --json`
- [x] `make eval-ai`
- [x] Release comparator PASS against `evaluations/baselines/production-ingestion-v1.json`
- [x] Intentional regression proof: comparator returns FAIL when a candidate report regresses a gated metric.
- [x] Connected-mode proof returns BLOCKED without writing a baseline when no approved provider route exists.
- [x] Holdout-mode proof returns BLOCKED when the protected payload is absent.
- [x] Aggregate local gate: `make check` passed with doctor, lint, docs, contracts, secrets, backend API tests, verifier tests, and iOS simulator tests.

Aggregate gate details:

- Backend API: 213 tests, 0 failures, 0 errors.
- Math verifier: 20 tests passed, with the existing Starlette/httpx deprecation warning tracked as `TD-DEP-001`.
- iOS simulator: `xcodebuild test` succeeded on iPhone 16 Pro simulator.
- Docs: `documentation check passed: 270 Markdown files`.
- Contracts: `contract check passed`.
- Secret scan: `secret scan passed`.

## Scope Boundaries

- No Phase 5 solving, answer verification, tutoring, attempts, mistake intelligence, mastery, or adaptive planner behavior was implemented.
- No production student content was committed.
- No fixture is eligible for training.
- No provider credentials, raw student content, signed URLs, object keys, prompt payloads, or raw provider outputs are written to evaluation artifacts.
- No Flyway migration was added because Sprint 4.10 introduces repository evaluation assets and CI gates, not durable product tables.

## Capability And Requirement Status

- `CAP-PROBLEM-006`: Partial. Local deterministic ingestion gate exists; connected provider and holdout evidence are blocked.
- `CAP-EVAL-001`: Partial. Ingestion subset exists; broader AI release gate remains Sprint 5.12 scope.
- `REQ-INGEST-EVAL-001`: Foundation. Local deterministic gate exists; production-provider and protected-holdout claims are blocked.
- `REQ-EVAL-001`: Foundation. Sprint 4.10 contributes the ingestion subset only.

## Handoff

Future ingestion route changes must run `make eval-ai` before local promotion. Production promotion additionally needs an approved provider route, protected holdout access, privacy/data-lineage approval for any restricted assets, and explicit baseline promotion evidence.
