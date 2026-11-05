# Sprint 7.1 implementation map

Baseline: clean `main`, `0349160164a45c6956dcb656a72ca7a34aa391e2`. Owner-only execution.
This audit precedes implementation. Source findings are not execution evidence.

## Reconciliation and initial gaps

| ID | Requirement | Initial classification | Inspected evidence / required repair |
|---|---|---|---|
| A1 | Independent identity, owner and source lineage | ALREADY_IMPLEMENTED | `StudentAttempt`, `SolveSourceAccess.lockOwner`, V030 composite foreign keys and owner-key uniqueness |
| A2 | Learner/reference and original/normalized separation | ALREADY_IMPLEMENTED | `StudentAttemptStep.content/normalizedStatement`, `StudentAttemptOrchestrator`, separate verifier result |
| A3 | Explicit final answer versus intermediate reasoning | MISSING | Submission v1/v2 only have `steps`; no final-answer domain field |
| A4 | Provenance, revision and immutable transitions | PARTIALLY_IMPLEMENTED | V031 update guards; step inserts/deletes after acceptance are not guarded |
| A5 | Uncertainty versus incorrectness | ALREADY_IMPLEMENTED | `AttemptAnalysisResult`, `AttemptAnalysisPolicyTest.uncertaintyNeverBecomesFirstError` |
| B1 | Answer-only capture | MISSING | One step cannot establish final-answer intent; introduce opt-in submission v3 |
| B2 | Manual multiple steps / reviewed handwriting | ALREADY_IMPLEMENTED | Orchestrator normalizer, capture-review attestation, iOS review sheet |
| B3 | Owned history and multiple meaningful attempts | ALREADY_IMPLEMENTED | Repository `history/get`, 30/account capacity, owner locks, immutable unique keys |
| B4 | Retry after timeout / concurrent replay | ALREADY_IMPLEMENTED | Prepare/execute/publish split, `timeoutAndCrashRecoverExactAttemptWithoutTransactionDuringVerification`, concurrency test |
| C1 | Immutable accepted content and source isolation | PARTIALLY_IMPLEMENTED | V030/V031 protect updates; add deferred shape and insert/delete guards; preserve source recheck |
| C2 | Durable constraints / migration compatibility | PARTIALLY_IMPLEMENTED | Active history V030–V032; new final answer requires additive V033, no historical rewrite |
| C3 | Export / account deletion | ALREADY_IMPLEMENTED | `StudentAttemptPrivacyLifecycleContributor`, cascades, active-account locking; extend new-field tests |
| D1 | Strict typed bounded contract | PARTIALLY_IMPLEMENTED | Controller parses untyped map; bind typed command with strict versioned decoding and UUID validation |
| D2 | Stable recoverable errors / abuse | ALREADY_IMPLEMENTED | RequestLimitFilter, owner limiter, stable ApiErrorCode; add adversarial endpoint coverage |
| D3 | iOS drafts, pending and interruption | PARTIALLY_IMPLEMENTED | Protected owner/problem pending store exists; drafts lack source binding/acknowledgement cleanup; late async response generation needs explicit fence |
| D4 | Account switching / history | ALREADY_IMPLEMENTED | ownsAccount, invalidate, logout clearAll; extend final-answer coverage |
| D5 | Accessibility / EN-TR | PARTIALLY_IMPLEMENTED | Existing controls localized; new answer/review/saved states need localization and tests |
| E1 | Submission cannot imply mastery or verified correctness | ALREADY_IMPLEMENTED | Existing assessment-gated learning repository; answer capture must bypass verifier and learning publication |
| E2 | AI/reference never independent evidence | ALREADY_IMPLEMENTED | No reference content copied to submitted text; training exclusion constrained |
| O1 | Privacy-safe telemetry | PARTIALLY_IMPLEMENTED | Outcome and request duration exist; add persistence failure/recovery and validation rejection metrics |
| O2 | Current-source owner acceptance evidence | MISSING | 6.9 runner/evidence reusable conventions; no 7.1 runner |
| R1 | Production / 50-student TestFlight authorization | BLOCKED_BY_EXTERNAL_GATE | ADR-011 and phase contract; no activation or beta evidence inferred |

## Planned changes before implementation

Reuse the existing attempt aggregate, orchestrator, repository, source ownership interfaces, learning boundary,
privacy contributor, APIClient, pending store and Analyze My Work screens. No new aggregate or service stack.

- Backend: `attempt/domain/{StudentAttempt,AttemptAnalysisPolicy,StudentAttemptStep}.java`,
  `attempt/api/{StudentAttemptController,AttemptSubmissionRequest}.java`,
  `attempt/application/StudentAttemptOrchestrator.java`, `attempt/infrastructure/persistence/StudentAttemptRepository.java`.
- Persistence: additive `V033__attempt_final_answer_and_evidence_guards.sql`; historical V030–V032 unchanged.
- Contracts: new submission-v3 and student-attempt-v2 schemas, explicit representation negotiation on existing GETs,
  strict shared schema registration, public OpenAPI and shared synthetic fixtures.
- iOS: existing attempt models/API/pending store/view model/editor/results/capture screens, fixture adapter and EN/TR catalog.
- Tests: existing attempt domain/controller/PostgreSQL suites, iOS unit/UI suites, contract checker and owner-evidence tests.
- Operations: `scripts/quality/sprint_7_1_owner_acceptance.sh`, `sprint_7_1_evidence.py`, sprint records and canonical docs.

## Semantic decisions

Submission v3 explicitly stores a final answer and zero to eleven intermediate lines (twelve total pieces).
It is capture-only: SUBMITTED means durably saved, with no assessment, normalization or mastery claim.
Legacy v1/v2 submissions retain their existing deterministic analysis behavior and exact hash algorithm.
Student-attempt-v2 uses an explicit finalAnswer field; existing v1 stored records are never rewritten.
GET representationVersion=2 returns native versioned records. Default GET retains the v1 display contract:
new final answers are rendered as a last unassessed text line, never passed to the verifier or learning system.
This compatibility projection is not stored evidence and cannot infer final-answer intent for historical steps.

## Migration, privacy and rollout

V033 adds a nullable bounded final_answer column and permits student-attempt-v2, strengthens immutable child
shape at transaction commit, and retains deletion cascades. New fields participate in existing export/deletion.
Training eligibility remains false. New submissions use the same default-off and production-authorization gates.
Rollback disables submissions and retains upgraded reader/storage for owned history; do not drop V033 or deploy
an older reader over new rows. Full binary rollback requires an owner-managed compatibility release.
No paid inference, model change, Phase 7.2 expansion or production authorization is introduced.

## Acceptance

All authored source and tests require fresh owner execution. Historical 6.7–6.9 evidence is context only.
The functional completion matrix and execution report record the final source inventory and limitations.

## Final implementation review

The planned slice is authored. Status: IMPLEMENTED_PENDING_OWNER_ACCEPTANCE. See the functional completion matrix
for source coverage and the execution report for owner gates. Strict transport validation occurs in the bounded
filter before binding the typed record, preserving the repository's Jackson-2 contracts / Jackson-3 MVC separation.
V033 also rejects malformed UTF-16 lengths for new evidence; historical step constraint validation is explicitly deferred.
Accepted telemetry fires after commit. Unexpected verifier failures return owned, source-rechecked durable evidence.
The protected draft store records source identity and only confirmed acknowledgement clears original retry material.

## Changed and created files

All paths below are repository-relative. No generated acceptance evidence is included.

### Modified

- `apps/ios/VerifiedAI.xcodeproj/project.pbxproj`
- `apps/ios/VerifiedAI/Features/AttemptAnalysis/Data/AttemptAnalysisAPI.swift`
- `apps/ios/VerifiedAI/Features/AttemptAnalysis/Data/AttemptAnalysisPendingStore.swift`
- `apps/ios/VerifiedAI/Features/AttemptAnalysis/Data/FixtureAttemptAnalysisAPI.swift`
- `apps/ios/VerifiedAI/Features/AttemptAnalysis/Domain/AttemptAnalysisModels.swift`
- `apps/ios/VerifiedAI/Features/AttemptAnalysis/Presentation/AnalyzeMyWorkCaptureView.swift`
- `apps/ios/VerifiedAI/Features/AttemptAnalysis/Presentation/AnalyzeMyWorkViewModel.swift`
- `apps/ios/VerifiedAI/Features/AttemptAnalysis/Presentation/AttemptAnalysisResultsView.swift`
- `apps/ios/VerifiedAI/Resources/Localizable.xcstrings`
- `apps/ios/VerifiedAITests/AttemptAnalysis/AttemptAnalysisTests.swift`
- `apps/ios/VerifiedAIUITests/AttemptAnalysisUITests.swift`
- `docs/verified_ai_learning_platform_docs_v4/00_MASTER_INDEX.md`
- `docs/verified_ai_learning_platform_docs_v4/DOCUMENTATION_MANIFEST.md`
- `docs/verified_ai_learning_platform_docs_v4/architecture/14_API_DESIGN_AND_CONTRACTS.md`
- `docs/verified_ai_learning_platform_docs_v4/backend/20_BACKEND_MODULE_CONTRACTS.md`
- `docs/verified_ai_learning_platform_docs_v4/data/22_POSTGRESQL_DATA_MODEL.md`
- `docs/verified_ai_learning_platform_docs_v4/data/23_DATA_LIFECYCLE_RETENTION_AND_AUDIT.md`
- `docs/verified_ai_learning_platform_docs_v4/domain/07_DOMAIN_MODEL_AND_AGGREGATES.md`
- `docs/verified_ai_learning_platform_docs_v4/domain/09_DOMAIN_EVENTS_AND_STATE_MACHINES.md`
- `docs/verified_ai_learning_platform_docs_v4/domain/10_DOMAIN_INVARIANTS_AND_BUSINESS_RULES.md`
- `docs/verified_ai_learning_platform_docs_v4/ios/17_IOS_NETWORKING_PERSISTENCE_AND_OFFLINE.md`
- `docs/verified_ai_learning_platform_docs_v4/quality/42_DEFINITION_OF_DONE_AND_ACCEPTANCE.md`
- `docs/verified_ai_learning_platform_docs_v4/quality/69_REQUIREMENTS_TRACEABILITY_MATRIX.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase07_attempt_mistake_and_mastery_intelligence/SPRINT_7.1_ATTEMPT_DOMAIN_AND_LEARNER_ANSWER_SUBMISSION.md`
- `packages/contracts/openapi/public-api.yaml`
- `scripts/quality/check_attempt_contracts.py`
- `services/api/src/main/java/com/verifiedai/attempt/api/AttemptRequestLimitFilter.java`
- `services/api/src/main/java/com/verifiedai/attempt/api/StudentAttemptController.java`
- `services/api/src/main/java/com/verifiedai/attempt/application/StudentAttemptOrchestrator.java`
- `services/api/src/main/java/com/verifiedai/attempt/domain/AttemptAnalysisPolicy.java`
- `services/api/src/main/java/com/verifiedai/attempt/domain/AttemptCaptureReview.java`
- `services/api/src/main/java/com/verifiedai/attempt/domain/StudentAttempt.java`
- `services/api/src/main/java/com/verifiedai/attempt/domain/StudentAttemptStep.java`
- `services/api/src/main/java/com/verifiedai/attempt/infrastructure/persistence/StudentAttemptRepository.java`
- `services/api/src/main/java/com/verifiedai/sharedkernel/contracts/VersionedJsonContract.java`
- `services/api/src/test/java/com/verifiedai/attempt/domain/AttemptAnalysisPolicyTest.java`
- `services/api/src/test/java/com/verifiedai/attempt/integration/StudentAttemptIntegrationTest.java`
- `services/math-verifier/pyproject.toml`

### Created

- `docs/verified_ai_learning_platform_docs_v4/sprints/phase07_attempt_mistake_and_mastery_intelligence/SPRINT_7.1_EXECUTION_REPORT.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase07_attempt_mistake_and_mastery_intelligence/SPRINT_7.1_FUNCTIONAL_COMPLETION_MATRIX.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase07_attempt_mistake_and_mastery_intelligence/SPRINT_7.1_IMPLEMENTATION_MAP.md`
- `packages/schemas/attempt-submission-v3.schema.json`
- `packages/schemas/fixtures/attempt-submission-v3-work.json`
- `packages/schemas/fixtures/attempt-submission-v3.json`
- `packages/schemas/fixtures/student-attempt-v2.json`
- `packages/schemas/student-attempt-v2.schema.json`
- `scripts/quality/sprint_7_1_evidence.py`
- `scripts/quality/sprint_7_1_owner_acceptance.sh`
- `services/api/src/main/java/com/verifiedai/attempt/api/AttemptSubmissionRequest.java`
- `services/api/src/main/resources/db/migration/platform/V033__attempt_final_answer_and_evidence_guards.sql`
- `services/api/src/test/java/com/verifiedai/attempt/api/AttemptSubmissionRequestTest.java`
- `services/api/src/test/java/com/verifiedai/attempt/api/StudentAttemptSubmissionControllerTest.java`
- `services/math-verifier/tests/test_sprint_7_1_evidence.py`
