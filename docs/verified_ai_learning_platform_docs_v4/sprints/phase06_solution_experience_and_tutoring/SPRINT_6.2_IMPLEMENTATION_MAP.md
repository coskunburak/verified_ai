# Sprint 6.2 — Implementation Map

## 1. Starting baseline and Sprint 6.1 prerequisite outcome

- **Repository & branch**: `https://github.com/coskunburak/verified_ai`, branch `codex/sprint-5-12-evaluation-release-gate`, `HEAD` `ef0dcc08acd441f7ee96cf9da3f14f13ec701eeb`.
- **Sprint 6.1 prerequisite status**: Complete in the local working tree and locally owner-accepted in `.generated/sprint-6-1-owner/latest/summary.json` (`20261031-152301-cKUveC`, `LOCAL_ACCEPTANCE_PASSED`, Gates A–D `PASS`). Sprint 6.2 builds directly on Sprint 6.1 without regressing any Sprint 6.1 contract or UI behavior.

## 2. Owning modules and existing components reused

- **Primary owner**: iOS `Features/ProblemSolving` (`Domain/Model`, `Data/Debug`, `Presentation/Solution`) and `Resources/Localizable.xcstrings`.
- **Backend / Verifier / Schema authority (reused unchanged)**: `services/api/verification` (`VerificationDecision`, `VerificationMethod`, `VerificationPolicy`, `VerificationRecoveryPolicy`, `VerificationArbitrationService`, `VerificationDecisionController`, `VerificationDecisionView`), `packages/schemas/verification-decision.schema.json`, and `packages/test-fixtures/verification/decision-v1/*.json`.

## 3. New and modified files

- `apps/ios/VerifiedAI/Features/ProblemSolving/Domain/Model/SolutionCandidate.swift` (`REPLACE`)
- `apps/ios/VerifiedAI/Features/ProblemSolving/Data/Debug/FixturePrimarySolveAPI.swift` (`REPLACE`)
- `apps/ios/VerifiedAI/Features/ProblemSolving/Presentation/Solution/PrimarySolveViewModel.swift` (`REPLACE`)
- `apps/ios/VerifiedAI/Features/ProblemSolving/Presentation/Solution/PrimarySolveView.swift` (`REPLACE`)
- `apps/ios/VerifiedAI/Resources/Localizable.xcstrings` (`EXACT PATCH`)
- `apps/ios/VerifiedAITests/ProblemSolving/PrimarySolveContractTests.swift` (`REPLACE`)
- `apps/ios/VerifiedAITests/ProblemSolving/PrimarySolveViewModelTests.swift` (`REPLACE`)
- `apps/ios/VerifiedAIUITests/PrimarySolveUITests.swift` (`REPLACE`)
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase06_solution_experience_and_tutoring/SPRINT_6.2_IMPLEMENTATION_MAP.md` (`CREATE`)
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase06_solution_experience_and_tutoring/SPRINT_6.2_EXECUTION_REPORT.md` (`CREATE`)
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase06_solution_experience_and_tutoring/PHASE_06_SOLUTION_EXPERIENCE_AND_TUTORING.md` (`EXACT PATCH`)
- `docs/verified_ai_learning_platform_docs_v4/00_MASTER_INDEX.md` (`EXACT PATCH`)
- `docs/verified_ai_learning_platform_docs_v4/DOCUMENTATION_MANIFEST.md` (`EXACT PATCH`)
- `scripts/quality/sprint_6_2_owner_acceptance.sh` (`CREATE`)

## 4. Server/client trust boundaries and verification semantics

- **Generation vs. verification vs. step correctness**: Candidate generation (`SolveState`), mathematical verification (`VerificationDecisionSnapshot`), and explanation step correctness remain strictly separate. `EXPLANATION_STEPS_UNCHECKED` is always required and disclosed.
- **Deterministic presentation mapper (`VerificationEvidencePresentation`)**: Converts a validated `VerificationDecisionSnapshot` (or absent/unavailable/invalid lookup state) into localized presentation keys, severity (`verified`, `caution`, `warning`, `neutral`), checked/unchecked/contradicted method lists, reason descriptions covering all 20 `Reason` codes, uncertainty descriptions covering all 7 `Uncertainty` codes, and recovery copy covering all 13 `Recovery` codes. It never computes mathematical truth or synthesizes an `UNVERIFIED` decision from a `null` response.
- **Scoped calculus honesty**: Preserves `OPEN_INTERVAL`, `ANTIDERIVATIVE_REPRESENTATIVE`, and `PUNCTURED_NEIGHBORHOOD` without implying endpoint coverage, full antiderivative families, or global proof.
- **Corroboration vs. proof**: Explicitly communicates that `SECONDARY_AGREEMENT` (independent solver agreement) and `NUMERIC_SAMPLING` are non-proof observations.

## 5. API, schema, migration, security, privacy, and AI cost impact

- **Backend/API/DB**: `NO_BACKEND_CONTRACT_CHANGE_REQUIRED` and `NO_DATABASE_MIGRATION_REQUIRED`. Uses read-only `GET /api/v1/problem-sessions/{sessionId}/solves/{jobId}/verification`; never auto-invokes `POST /solutions/{solutionId}/verification/{runKey}`.
- **Security & privacy**: Owner-scoped reads only; no raw problem expressions, secondary solver payloads, or internal UUIDs logged or exposed in the UI; student data remains `NOT_ELIGIBLE` for training by default.
- **AI call & cost impact**: `0` incremental AI inference calls and `0` verifier executions per presentation or `refreshVerification()` action.

## 6. Accessibility, localization, testing, rollout, and remaining risks

- **Accessibility & localization**: Full English and Turkish strings in `Localizable.xcstrings`, VoiceOver status/conflict announcements, Dynamic Type multi-line wrapping, reduced-motion compliance, and stable accessibility identifiers (`verification.summary`, `verification.evidence.toggle`, `verification.evidence.content`, `verification.reasons.section`, `verification.limitations.section`, `verification.contradictions.section`, `verification.refresh`).
- **Required tests**: `PrimarySolveContractTests`, `PrimarySolveViewModelTests`, `PrimarySolveUITests`, plus full non-regression via `bash scripts/quality/sprint_6_2_owner_acceptance.sh`.
- **Rollout, rollback & remaining risks**: Verification rollout flags (`app.verification.arbitration-enabled`, `app.solving.enabled`) remain disabled by default. Rollback is a pure iOS presentation revert with zero schema or database impact. Hosted CI, real-provider evaluation, protected holdout, and production release approval remain separate external gates.
