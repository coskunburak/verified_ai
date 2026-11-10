# Sprint 7.6 Execution Report — Evidence-Stratified Mastery Model (`bkt-lite-v2`) and Anti-Crutch Update Policy

- **Sprint:** `Sprint 7.6`
- **Status:** `SOURCE_COMPLETE_PENDING_OWNER_TEST` (`PENDING_OWNER_TEST` / `NOT_EXECUTED_BY_AGENT`)
- **Branch / Baseline HEAD:** `main` at `48bf89804267e468a28209fcbf5dd0750d9c913e`
- **Active Forward Migration:** `V038__evidence_stratified_mastery_and_prerequisite_edges.sql` (source authored; not applied by agent)
- **Production Authorization:** `NOT_AUTHORIZED`

---

## 1. Continuation & Workspace Recovery Summary

This report records the continuation and completion of Sprint 7.6 from the uncommitted local working tree left by the prior implementation session on `main` (`48bf89804267e468a28209fcbf5dd0750d9c913e`). All prior work was preserved without reset, checkout, or history rewrite:

1. **Recovered Source & Schema Artifacts:**
   - `services/api/src/main/resources/db/migration/platform/V038__evidence_stratified_mastery_and_prerequisite_edges.sql`
   - `services/api/src/main/java/com/verifiedai/mastery/` (`LearningEvidenceType.java`, `EvidenceStratifiedMasteryPolicy.java`, `MasteryApplicationService.java`, `MasteryEvidenceSource.java`, `MasteryEvidenceChanged.java`, `LegacyMasteryPolicy.java`, `LegacyMasteryWriter.java`, `api/MasteryController.java`, `infrastructure/MasteryRepository.java`, `infrastructure/MasteryPrivacyLifecycleContributor.java`)
   - `services/api/src/main/java/com/verifiedai/attempt/` and `problem/application/solving/` (`AttemptMasteryEvidenceSource.java`, `AttemptMistakeDnaEvidenceSource.java`, `AttemptWorkService.java`, `AttemptLearningService.java`, `LearnerMasteryPolicy.java`, `PrimarySolveService.java`, `SolveStore.java`)
   - `packages/contracts/openapi/public-api.yaml`, `packages/schemas/mastery-skills-v1.schema.json`, `packages/schemas/fixtures/mastery-skills-v1.json`, `scripts/quality/check_mastery_contracts.py`
   - `apps/ios/VerifiedAI/Features/ProblemSolving/` (`AttemptAnalysisAPI.swift`, `FixtureAttemptAnalysisAPI.swift`, `AttemptAnalysisModels.swift`, `AnalyzeMyWorkCaptureView.swift`, `AnalyzeMyWorkViewModel.swift`) and `apps/ios/VerifiedAI/Resources/Localizable.xcstrings`

---

## 2. Static Source Review Findings & Repairs Completed

Through static source inspection (without executing any compiler, test runner, or validator), four defects/gaps were identified and repaired:

1. **Repository Constructor Compatibility (`StudentAttemptRepository` / `AttemptMistakeRepository` / `AttemptLearningRepository`):**
   - *Finding:* `StudentAttemptRepository.java:74` invoked `new AttemptMistakeRepository(jdbc).export(owner)`, which would fail to compile because the 1-argument `AttemptMistakeRepository(JdbcTemplate)` constructor had been removed when adding `ApplicationEventPublisher`.
   - *Repair:* Restored 1-argument `(JdbcTemplate)` convenience constructors and annotated the multi-argument Spring constructors with `@Autowired` in `AttemptMistakeRepository`, `AttemptLearningRepository`, and `StudentAttemptRepository`.
2. **Demonstrated Independent Competence ($I_{u,s}$) Eligible Total Denominator (`INV-LRN-COMP-001`):**
   - *Finding:* `EvidenceStratifiedMasteryPolicy.java` accumulated `total += weight` only when `e.type() == INDEPENDENT`, leaving $I_{u,s}$ unchanged under repeated `HINT_ASSISTED` or `SOLUTION_REVEALED_ASSISTED` completions instead of reducing $I_{u,s}$ toward `0` as required by `learning/86_LEARNING_OUTCOMES_RETRIEVAL_PRACTICE_AND_MASTERY_EVALUATION.md` §4.2 (`INV-LRN-COMP-001`).
   - *Repair:* Updated `EvidenceStratifiedMasteryPolicy.java` so `total += weight` accumulates across all valid supported attempts (`ordered`, i.e., `INDEPENDENT`, `HINT_ASSISTED`, `SOLUTION_REVEALED_ASSISTED`), while `successes += weight` accumulates only on `INDEPENDENT` correct completions. Updated `EvidenceStratifiedMasteryPolicyTest.competenceUsesEligibleTotalDenominatorAnd28DayHalfLife` accordingly.
3. **Counterfactual Twin Drill Assistance Provenance Hardening (`AttemptMasteryEvidenceSource.java`):**
   - *Finding:* Twin drill reconstruction read `attempt_mastery_context` via `type(owner, id, at)` without cross-checking `counterfactual_twin_drills.assistance`.
   - *Repair:* Enforced that if `counterfactual_twin_drills.assistance` is `HINT_ASSISTED` while context is `INDEPENDENT`, the evidence type downgrades to `HINT_ASSISTED`, and if `assistance` is not in `{"INDEPENDENT", "HINT_ASSISTED"}`, it degrades to `UNSUPPORTED`.
4. **Acceptance Runner Isolation & Evidence Standard (`sprint_7_6_owner_acceptance.sh` & `sprint_7_6_evidence.py`):**
   - *Finding:* The draft `sprint_7_6_evidence.py` invoked `bash scripts/quality/sprint_7_5_owner_acceptance.sh --full` as a child process, which would overwrite `.generated/sprint-7-5-owner/latest` on dirty Sprint 7.6 source and duplicate test runs.
   - *Repair:* Replaced both `scripts/quality/sprint_7_6_owner_acceptance.sh` and `scripts/quality/sprint_7_6_evidence.py` with a standalone Gates A–G + cleanup runner writing exclusively to `.generated/sprint-7-6-owner`, inspecting `.generated/sprint-7-5-owner` read-only for the historical entry gate (`baseline = 48bf89804267e468a28209fcbf5dd0750d9c913e`, `priorAcceptanceRunnerInvoked = False`), and authored `services/math-verifier/tests/test_sprint_7_6_evidence.py`.

---

## 3. Authored Tests & Validation Status (`NOT_EXECUTED_BY_AGENT`)

All tests, builds, migrations, and contract checks remain **unexecuted** (`PENDING_OWNER_TEST`):

- **Backend Unit & Policy:** `EvidenceStratifiedMasteryPolicyTest` (12 cases), `LearnerMasteryPolicyTest`, `ModularityTest` — `NOT_EXECUTED`
- **Backend PostgreSQL/Testcontainers Integration & API:** `MasteryMigrationIntegrationTest` (1 case), `MasteryControllerTest` (1 case), `EvidenceStratifiedMasteryIntegrationTest` (17 cases), plus Sprint 7.1–7.5 regressions — `NOT_EXECUTED`
- **Contracts & Schemas:** `scripts/quality/check_mastery_contracts.py` + prior contract validators — `NOT_EXECUTED`
- **iOS Unit & UI:** `MasteryPresentationTests` (4 cases in `AttemptAnalysisTests.swift`), `AttemptAnalysisUITests` (`testMasteryEnglishEvidenceAccessibility`, `testMasteryTurkishEvidenceAccessibility` + 15 prior cases) — `NOT_EXECUTED`
- **Python Verifier & Acceptance Evidence Suite:** `services/math-verifier/tests/test_sprint_7_6_evidence.py` (25 synthetic evidence-writer tests) — `NOT_EXECUTED`

---

## 4. Remaining Blockers & Owner Acceptance Command

1. **Historical Sprint 7.4 / Sprint 7.5 Full Acceptance Entry Gate:** `.generated/sprint-7-5-owner` currently contains a `BLOCKED` FULL run (`20261109-185846-gj0YdT`) and a `TARGETED_REPAIR_PASSED` run (`20261109-200633-1KasxL`). Until a passing FULL Sprint 7.5 run is recorded on baseline `48bf89804267e468a28209fcbf5dd0750d9c913e`, Gate A's historical entry check in `sprint_7_6_owner_acceptance.sh --full` will report `BLOCKED` while still executing Gates B–G on current source.
2. **Owner Acceptance Execution:** Run `bash scripts/quality/sprint_7_6_owner_acceptance.sh --full`.
3. **External Gates:** Hosted CI, physical-device VoiceOver/Dynamic Type verification, and the 50-student TestFlight Beta gate (`ADR-011`) remain external.
