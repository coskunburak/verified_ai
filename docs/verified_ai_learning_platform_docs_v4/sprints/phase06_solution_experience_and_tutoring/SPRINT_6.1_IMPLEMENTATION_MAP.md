# Sprint 6.1 — Implementation Map

## 1. Owning bounded context and module

- **Primary owner**: iOS `Features/ProblemSolving` (`Domain/Model`, `Domain/Port`, `Data/Remote`, `Data/Local`, `Data/Debug`, `Presentation/Components`, `Presentation/Solution`).
- **Integration touchpoints**: iOS `App/RootView.swift` (injecting privacy-safe `AppLogger`), `Features/ProblemHistory/Presentation/History/ProblemHistoryView.swift` (binding `PrimarySolveView` structural identity to `problemSessionId`), and `Resources/Localizable.xcstrings` (English and Turkish strings).
- **Backend / Verifier / Schema authority**: `services/api/solving`, `services/api/verification`, `services/math-verifier`, and `packages/schemas` remain unchanged and authoritative.

## 2. Domain states and invariants touched

- `SolveState`: `QUEUED`, `RUNNING`, `GENERATED_UNVERIFIED`, `REVIEW_REQUIRED`, `RETRYABLE_FAILURE`, `TERMINAL_FAILURE`, `POLICY_BLOCKED`, `UNSUPPORTED`, `STALE`, `CANCELLED`, `OUTCOME_UNKNOWN`.
- `SolveScreenPhase`: explicit typed UI state separating `checkingExisting`, `readyToStart`, `recoveryPending(SolveRecoveryMarker)`, `job(SolveState)`, and `degraded(String)`.
- `VerificationDecisionSnapshot`: `VERIFIED`, `PARTIALLY_VERIFIED`, `UNVERIFIED`, with explicit proof scopes (`NONE`, `CHECKED_METHOD_CLAIMS`, `FINAL_ANSWER`, `OPEN_INTERVAL`, `ANTIDERIVATIVE_REPRESENTATIVE`, `PUNCTURED_NEIGHBORHOOD`).
- **Verification honesty**: client never infers `VERIFIED` from generated content, step structure, solver agreement, or numeric sampling; non-verified states never render green verification iconography.
- **Lineage & version safety**: `solution-candidate-v2` and unknown candidate schema versions fail closed with `SolveContractError.unsupportedCandidateSchema` (`solve.error.unsupported`) instead of appearing as transport failures or verified answers. Polling and refresh invalidate stale verification decisions whenever `jobId`, `canonicalProblemId`, `problemParseId`, `problemParseRevision`, or `state` changes.

## 3. iOS screens, view-models, and components touched

- `apps/ios/VerifiedAI/Features/ProblemSolving/Domain/Model/SolutionCandidate.swift`
- `apps/ios/VerifiedAI/Features/ProblemSolving/Presentation/Components/MathematicalExpressionView.swift`
- `apps/ios/VerifiedAI/Features/ProblemSolving/Presentation/Solution/PrimarySolveViewModel.swift`
- `apps/ios/VerifiedAI/Features/ProblemSolving/Presentation/Solution/PrimarySolveView.swift`
- `apps/ios/VerifiedAI/Features/ProblemSolving/Data/Debug/FixturePrimarySolveAPI.swift`
- `apps/ios/VerifiedAI/App/RootView.swift`
- `apps/ios/VerifiedAI/Features/ProblemHistory/Presentation/History/ProblemHistoryView.swift`
- `apps/ios/VerifiedAI/Resources/Localizable.xcstrings`

## 4. Backend, database, API, and AI route implications

- **Backend commands/queries/policies**: Unchanged. Reuses `/api/v1/problem-sessions/{sessionId}/solves` (`/current`, `POST`, `/{jobId}`, `/{jobId}/retry`, `/{jobId}/cancel`, `/{jobId}/verification`).
- **Database migrations**: None. Durable state remains in existing PostgreSQL tables and bounded client-side `SolveRecoveryStore`.
- **Shared schemas**: Unchanged (`solution-candidate-v1` and `verification-decision-v1` preserved byte-for-byte).
- **AI capability / cost impact**: Zero new or multiplied inference calls (`0` additional calls per learner action). Production solver rollout remains disabled; paid models remain skipped by policy.
- **Training data**: Student content and solve interactions remain `NOT_ELIGIBLE` for training by default.

## 5. Privacy, security, and observability

- `SolveRecoveryStore` stores only bounded request/job identifiers with `completeFileProtectionUntilFirstUserAuthentication` and backup exclusion; cleared on sign-out, account switch, and account deletion.
- `PrimarySolveViewModel` emits privacy-safe `OSLog` diagnostics via `AppLogger` (`solve.reconciled`, `solve.submitted`, `solve.cancelled`, `solve.poll.paused`, `solve.verification.loaded`, `solve.verification.unavailable`, `solve.error`) containing only state/scope enums and error keys—never raw problem text or expressions.

## 6. Test, rollout, and rollback plan

- **Unit & contract tests**: `PrimarySolveContractTests`, `PrimarySolveViewModelTests`, `MathPresentationTests`, `SolveRecoveryStoreTests`.
- **UI acceptance tests**: `PrimarySolveUITests` (deterministic controlled progression, verification statuses/scopes, uncertainty, retry, relaunch recovery, malformed/unsupported rejection, Turkish accessibility XXXL layout).
- **Owner acceptance entry point**: `bash scripts/quality/sprint_6_1_owner_acceptance.sh`.
- **Rollout & rollback**: Controlled by existing `solveEnabled` / `app.solving.enabled=false` gates; reverting the iOS presentation slice requires no schema or database rollback.
