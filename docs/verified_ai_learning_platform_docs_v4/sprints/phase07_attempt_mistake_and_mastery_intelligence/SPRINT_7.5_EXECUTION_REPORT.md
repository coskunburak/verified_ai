# Sprint 7.5 Execution Report

Status: **SOURCE_COMPLETE_PENDING_OWNER_TEST**. Production is **NOT_AUTHORIZED**.

Test status: **NOT RUN — MANUAL OWNER EXECUTION REQUIRED**.

## Baseline and entry gate

Work continued on the existing local working tree on `main`, based on `b79151a571d15dbc3c5de8d4a83e4e748ee5b3fc`.
Sprint 7.4 implementation is committed at `e39bafe88ea369f98c96a65497a94462dc79ef33`.
Existing Sprint 7.5 source was preserved and completed; no commit, push, reset or historical evidence rewrite was performed.

The locally inspected Sprint 7.4 owner summaries establish a failed FULL run (`20261107-114912-CFdjVI`),
failed targeted runs (`124129-DmnC82`, `133646-ubZC15`) and a targeted repair pass (`134659-rvmP80`).
They do **not** establish a passing FULL owner acceptance run. This entry gate remains open.
The Sprint 7.5 runner reads original full baseline evidence; it does not invoke the prior acceptance runner.
Independent current-source gates continue even when the historical entry gate is BLOCKED.

## Verified source claims and remaining acceptance

The following is a static source assessment, not a runtime verification result.

| Criterion | Source implementation | Owner verification |
| --- | --- | --- |
| Mistake DNA ledger and projections | Existing immutable V036 classifications are the ledger; V037 adds derived versioned profiles and append-only exclusions | Pending PostgreSQL, contract and domain suites |
| Causal evidence authority | Validated V035/V036 work, evaluation, execution and first-error identity; confirmed prefix; deterministic structural disproof only | Pending lineage and downstream-error regressions |
| Eligible exposure provenance | Exact canonical source's accepted primary skill as of work time; active compatible ontology; mapped causal error or fully verified success | Pending missing, unrelated and unknown-ontology tests |
| Deterministic propensity | UTC 21-day half-life; prior mean 0.10, strength 4; stable ordering; scale 8 HALF_EVEN | Pending pure math and fixed-clock replay tests |
| Recurrence | At least two causal errors within 21 days **AND** propensity >= 0.22 | Pending boundary and rollback tests |
| Dispute rollback | Owner lock, hash-bound idempotency, append-only operation; remove error and exposure in one transaction | Pending races, uniqueness, outer rollback and latency tests |
| OCR correction | New immutable work revision preserves raw observations; explicit evaluation and classification required | Pending concurrent correction and stale-evaluation tests |
| iOS dispute/editor/retry | EN/TR controls, account/generation fences, protected durable pending correction, original OCR retained | Pending Swift fixtures, view-model and accessibility suites |
| Privacy and paid access | Lifecycle export/deletion, retention invalidation, no training eligibility; server entitlement limits FREE history | Pending database, HTTP and privacy suites |
| Fail-closed acceptance | Fresh A–G evidence, source fingerprints, required named cases, full-run scopes, historical entry gate | Pending synthetic evidence-writer tests and owner FULL run |

All listed source work is authored. Runtime correctness, integration success and acceptance remain unverified.

## Source review and completion

- Preserved V035/V036 migration files, evidence DTOs, deterministic classifier and verifier algorithms. V037 adds constraints and derived-state lifecycle behavior without rewriting accepted artifacts.
- Completed synthetic database upgrade, append-only, concurrency, stale publication, source skill, privacy, contract and iOS retry tests.
- Tightened source-skill ontology compatibility and OCR acknowledgement identity checks. The disabled diagnosis endpoint now returns DISABLED consistently; replay retains accepted operations without reactivation.
- Added cache invalidation when parent classifications are deleted and a local retry recovery action that only clears the pending client request.
- Reviewed transaction ownership, source access, schema/DTO/API alignment, deletion cascades and source-bound acceptance reporting by reading source. No compiler or validator was executed.
- Acceptance reporting rejects absent entry evidence, missing full backend/iOS scopes, missing named cases, skipped mandatory tests, changed source and incomplete gate execution. Targeted repair cannot become full acceptance.

## Contracts, data and operations

V037 defines `learner_mistake_dna_profiles` and `learner_mistake_dispute_events`; it is **NOT APPLIED**.
Six versioned JSON schemas and synthetic fixtures cover DNA views, effective state, dispute requests/results and OCR correction requests/results.
OpenAPI includes owner-only DNA/effective-state/dispute routes and the existing attempt route's OCR correction action.

`MISTAKE_DNA_ENABLED` defaults false and requires the attempt capability plus existing production authorization outside local/test.
Rollback disables new DNA actions and projection delivery while retaining immutable evidence, privacy access and accepted-operation replay.
No migration reversal, paid inference, secret access or new training consent is introduced.
After-commit metrics use bounded policy/state tags. The rollback timer has a 250 ms histogram boundary.
The owner latency test measures complete dispute calls at the existing 30-attempt capacity; production/staging p95 remains unverified.

## Documentation reconciliation

Canonical Sprint 7.5, learning 83, architecture 90, quality 91, roadmap 87, security 89, data 22/23,
the sprint master plan, master index and documentation manifest were updated together.
The docs now distinguish deterministic evidence from numeric confidence, require the AND recurrence rule,
use the actual V037 tables/API contracts and identify accepted source-specific skill provenance.
They reserve mastery for Sprint 7.6, Passport for Sprint 7.7 and remediation/retention closure for Sprint 7.8.
The six-state vocabulary does not authorize premature resolved, remediation or relapse transitions.

## Owner acceptance handoff

The single complete local command is `bash scripts/quality/sprint_7_5_owner_acceptance.sh --full`.
It writes a fresh directory under `.generated/sprint-7-5-owner`, including gate logs, source fingerprints,
backend/verifier/iOS/security summaries, failure inventory and a combined summary.
Missing prerequisites or historical Sprint 7.4 evidence prevent acceptance and return a nonzero result.

Prerequisites: Java 21, Maven, Docker/Testcontainers, Python verifier development dependencies,
full Xcode with an available iOS 17+ iPhone simulator, and original passing FULL Sprint 7.4 evidence matching its committed baseline.
The runner does not install dependencies or use production services. Test fixtures are synthetic.

No tests, linting, builds, type checks, migrations, acceptance runners, documentation validators or schema validators
were executed during this continuation. Hosted CI, physical-device accessibility, TestFlight/student gates,
production authorization and staging latency are also unverified. Sprint 7.5 is **not accepted**.

## Known scope limits

- Existing owner capacity remains 30 attempts. Corrections consume a new revision and may hit that capacity; failure cannot mutate prior evidence.
- Only the five existing authoritative skill mappings are eligible. Missing provenance is explicit exclusion, not inferred skill or denominator.
- Rates describe the conservative eligible-evidence cohort, not every submitted attempt. Delayed evidence keeps its original work time.
- Cached export profiles are dated snapshots; enabled reads rebuild. Disabling does not claim a new current projection.
- No V031 mastery mutation/reversal is needed because this V034–V036 path creates none. No secondary errors, severity weights, Passport screen or remediation closure was added.

## Exact changed files

Paths below are repository-relative and cover the complete local Sprint 7.5 change set at handoff, including this report and manifest.

### Backend application and database

- `services/api/src/main/java/com/verifiedai/attempt/api/AttemptRequestLimitFilter.java`
- `services/api/src/main/java/com/verifiedai/attempt/api/AttemptWorkController.java`
- `services/api/src/main/java/com/verifiedai/attempt/application/AttemptMistakeDnaEvidenceSource.java`
- `services/api/src/main/java/com/verifiedai/attempt/application/AttemptMistakeService.java`
- `services/api/src/main/java/com/verifiedai/attempt/application/AttemptStepOcrCorrectionService.java`
- `services/api/src/main/java/com/verifiedai/attempt/application/AttemptWorkService.java`
- `services/api/src/main/java/com/verifiedai/mistake/EmpiricalBayesMistakeRateCalculator.java`
- `services/api/src/main/java/com/verifiedai/mistake/LearnerMistakeDnaProfile.java`
- `services/api/src/main/java/com/verifiedai/mistake/LearnerMistakeDnaService.java`
- `services/api/src/main/java/com/verifiedai/mistake/MistakeDnaEvidenceSource.java`
- `services/api/src/main/java/com/verifiedai/mistake/MistakeRecurrenceState.java`
- `services/api/src/main/java/com/verifiedai/mistake/api/MistakeDnaController.java`
- `services/api/src/main/java/com/verifiedai/mistake/api/MistakeDnaRequestLimitFilter.java`
- `services/api/src/main/java/com/verifiedai/mistake/infrastructure/MistakeDnaPrivacyLifecycleContributor.java`
- `services/api/src/main/java/com/verifiedai/mistake/infrastructure/MistakeDnaRepository.java`
- `services/api/src/main/java/com/verifiedai/problem/application/solving/SolveSourceAccess.java`
- `services/api/src/main/java/com/verifiedai/sharedkernel/contracts/VersionedJsonContract.java`
- `services/api/src/main/resources/application.yml`
- `services/api/src/main/resources/db/migration/platform/V037__learner_mistake_dna_profiles_and_disputes.sql`

### iOS application

- `apps/ios/VerifiedAI.xcodeproj/project.pbxproj`
- `apps/ios/VerifiedAI/Features/AttemptAnalysis/Data/AttemptAnalysisAPI.swift`
- `apps/ios/VerifiedAI/Features/AttemptAnalysis/Data/AttemptAnalysisPendingStore.swift`
- `apps/ios/VerifiedAI/Features/AttemptAnalysis/Data/FixtureAttemptAnalysisAPI.swift`
- `apps/ios/VerifiedAI/Features/AttemptAnalysis/Domain/AttemptAnalysisModels.swift`
- `apps/ios/VerifiedAI/Features/AttemptAnalysis/Presentation/AnalyzeMyWorkCaptureView.swift`
- `apps/ios/VerifiedAI/Features/AttemptAnalysis/Presentation/AnalyzeMyWorkViewModel.swift`
- `apps/ios/VerifiedAI/Resources/Localizable.xcstrings`

### Shared contracts and fixtures

- `packages/contracts/README.md`
- `packages/contracts/openapi/public-api.yaml`
- `packages/schemas/README.md`
- `packages/schemas/attempt-ocr-correction-request-v1.schema.json`
- `packages/schemas/attempt-ocr-correction-result-v1.schema.json`
- `packages/schemas/fixtures/attempt-ocr-correction-request-v1.json`
- `packages/schemas/fixtures/attempt-ocr-correction-result-v1.json`
- `packages/schemas/fixtures/mistake-dispute-request-v1.json`
- `packages/schemas/fixtures/mistake-dispute-result-v1.json`
- `packages/schemas/fixtures/mistake-dna-view-v1.json`
- `packages/schemas/fixtures/mistake-effective-state-v1.json`
- `packages/schemas/mistake-dispute-request-v1.schema.json`
- `packages/schemas/mistake-dispute-result-v1.schema.json`
- `packages/schemas/mistake-dna-view-v1.schema.json`
- `packages/schemas/mistake-effective-state-v1.schema.json`

### Tests and owner acceptance tooling

- `apps/ios/VerifiedAITests/AttemptAnalysis/AttemptAnalysisTests.swift`
- `apps/ios/VerifiedAIUITests/AttemptAnalysisUITests.swift`
- `scripts/quality/check_mistake_dna_contracts.py`
- `scripts/quality/sprint_7_5_evidence.py`
- `scripts/quality/sprint_7_5_owner_acceptance.sh`
- `services/api/src/test/java/com/verifiedai/attempt/api/MistakeDnaControllerTest.java`
- `services/api/src/test/java/com/verifiedai/attempt/integration/AttemptEvaluationIntegrationTest.java`
- `services/api/src/test/java/com/verifiedai/attempt/integration/AttemptMistakeIntegrationTest.java`
- `services/api/src/test/java/com/verifiedai/attempt/integration/AttemptWorkIntegrationTest.java`
- `services/api/src/test/java/com/verifiedai/attempt/integration/MistakeDisputeRollbackIntegrationTest.java`
- `services/api/src/test/java/com/verifiedai/mistake/MistakeDnaServiceTest.java`
- `services/math-verifier/tests/test_sprint_7_5_evidence.py`

### Canonical documentation

- `docs/verified_ai_learning_platform_docs_v4/00_MASTER_INDEX.md`
- `docs/verified_ai_learning_platform_docs_v4/DOCUMENTATION_MANIFEST.md`
- `docs/verified_ai_learning_platform_docs_v4/architecture/90_LEARNING_OS_DOMAIN_CONTRACTS_AND_PLATFORM_INTEGRATION.md`
- `docs/verified_ai_learning_platform_docs_v4/data/22_POSTGRESQL_DATA_MODEL.md`
- `docs/verified_ai_learning_platform_docs_v4/data/23_DATA_LIFECYCLE_RETENTION_AND_AUDIT.md`
- `docs/verified_ai_learning_platform_docs_v4/learning/83_VERIFIED_MISTAKE_DNA_AND_MISTAKE_PASSPORT_SPECIFICATION.md`
- `docs/verified_ai_learning_platform_docs_v4/quality/91_LEARNING_OS_PRODUCT_LEARNING_AND_RELEASE_ACCEPTANCE_GATES.md`
- `docs/verified_ai_learning_platform_docs_v4/roadmap/87_LEARNING_OS_STRATEGIC_PHASE_AND_SPRINT_EXPANSION_PROGRAM.md`
- `docs/verified_ai_learning_platform_docs_v4/security/89_STUDENT_COACH_PRIVACY_MINOR_SAFETY_AND_TEACHER_ACCESS.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/00_SPRINT_MASTER_PLAN.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase07_attempt_mistake_and_mastery_intelligence/SPRINT_7.5_AUTOMATIC_MISTAKE_BOOK_AND_REVIEW_WORKFLOWS.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase07_attempt_mistake_and_mastery_intelligence/SPRINT_7.5_EXECUTION_REPORT.md`
