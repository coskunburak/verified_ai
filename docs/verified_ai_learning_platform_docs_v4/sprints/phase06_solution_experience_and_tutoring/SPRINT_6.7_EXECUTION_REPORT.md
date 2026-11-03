# Sprint 6.7 — execution report

Current product continuation: 2026-11-03. Baseline `main` / `4acc57ea8ee9b22536a8caa3f4f98ccc945a7855`.
Implementation remains uncommitted. No deployment, provider activation or database execution occurred.

## Current status ledger

| Workstream | Source integration | Remaining validation/external dependency |
|---|---|---|
| Canonical v2 / domain semantics | IMPLEMENTED within bounded typed real-set scope, new canonical-problem-v3 | Owner contracts/worker/source integration execution |
| Photo and OCR | IMPLEMENTED native image/crop/rotate/quality/review; existing gateway adapter | Real handwriting/provider activation NOT_EVALUATED; native device review pending |
| Mistake classification | IMPLEMENTED 17 evidence-gated bounded classifiers + generic fallback | Synthetic taxonomy execution and expert review pending |
| Semantic diff / native math | IMPLEMENTED AST evidence/path/range fallback; SwiftMath exact pin | Owner package resolution, license/release audit and device accessibility |
| Twin drills | IMPLEMENTED verified template gate, four options, persistence, answer/history/timer | Owner execution; no generative-provider dependency |
| Alternative methods | IMPLEMENTED verified quadratic methods and bounded expression factoring | General methods and exam-speed performance not claimed |
| Mastery | IMPLEMENTED evidence idempotency, assistance-aware BKT-lite/decay | Owner transaction/algorithm verification |
| Mistake Passport | IMPLEMENTED owner history, confidence/count/timestamps, privacy | Owner API/UI/privacy execution |
| Migration integration | IMPLEMENTED active forward V030–V032; no old migration edits | Fresh/upgrade owner evidence PENDING_OWNER; shared upgrade NOT_AUTHORIZED |
| iOS UX | IMPLEMENTED capture-review-editor-result-diff-drill-method-passport; protected drafts | Owner compile/unit/UI/device checks; EN/TR physical VoiceOver pending |
| Privacy/security | IMPLEMENTED ownership/rechecks, immutable evidence, export/delete, training=false, budgets | Owner integration + Linux release validation pending |

**Owner acceptance status: TARGETED_REPAIR_PASSED** (latest owner run `20261104-002119-IQLWzE`, `targetedRepair=PASS`, `unresolvedFailures=[]`).
All 5 test failures across Python verifier, Spring Modulith backend, and iOS UI test suites identified in initial product run `20261103-233019-8g1dlw` have now passed in the targeted repair chain.
Full current-source acceptance: **PENDING_OWNER** (requires owner invocation with `--full`).
Production release: **BLOCKED / NOT_AUTHORIZED**, independently of bounded source implementation.
Linux resource validation: **PLATFORM_SPECIFIC_VALIDATION_PENDING** (five Linux-only resource exhaustion tests on macOS remain pending).
Hosted CI: **NOT_VERIFIED**; live AI evaluation: **NOT_EVALUATED**.
No measured handwriting recall, latency, model accuracy, production readiness or universal math coverage is claimed.

Exact source inventory: `SPRINT_6.7_FILE_MANIFEST.md` (148 files: 103 created, 45 modified). API contracts, migration strategy, mathematical limits,
activation defaults, source ownership and authored test inventory: `SPRINT_6.7_IMPLEMENTATION_MAP.md`.
V1 files and historical owner artifacts retain their separate identity.

## Product owner run 20261103-233019-8g1dlw — targeted repairs pending

The owner executed the current product acceptance; the agent inspected its existing failing evidence only.
Run source fingerprint: `b9b0e9b928104e14832eaf960cdb9081b4f631456bf14e418cb9bb4ecffaa776`.
A passed. B executed 517 cases: 510 passed, 2 failed, 5 Linux-only skips; golden evaluation passed.
C executed 99 cases: 98 passed, one Modulith error. D recorded 201 passes and two attempt UI failures.
These are historical run results, not evidence that the following repairs passed.

| Failure | Source repair | Next owner selection |
|---|---|---|
| Freshman-dream classifier returned generic for (x+2)^2 → x^2+4 | Match the exact constant square as well as explicit power syntax, after independent disproof; reject unrelated constants | Only the failed ALG_FRESHMAN_DREAM_POWER parameter |
| Piecewise absolute value returned UNCERTAIN | Rewrite/fold piecewise residuals, prove zero on each exact branch and prove complete domain coverage; numerical agreement never proves equality | test_piecewise_and_set_membership_use_exact_domains |
| ModularityTest.verifiesSpringModulithBoundaries | Replace extraction field injection with immutable constructor dependencies/configuration; unavailable adapter fails closed | Only that method |
| Both AttemptAnalysisUITests methods | Honor advertised handwriting capability; show existing unavailable state for UNAVAILABLE contexts and native capture for supported contexts | Only those two methods, assertions retained |

The two selected Python tests now also cover correct expansion, unrelated constant errors, a wrong boundary value
and partial domain coverage. These added assertions and all repairs are NOT_EXECUTED by the agent.
The existing runner selects **2 Python, 1 backend, 2 iOS** cases for this unresolved product run. A, golden evaluation,
previously passing cases and Linux-only skipped cases are not selected. No owner artifact was rewritten.
`sourceUnchanged=false` came solely from Xcode creating
`apps/ios/VerifiedAI.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved` during first resolution.
The owner-generated lockfile is retained: SwiftMath 1.7.3, revision `fa8244ed032f4a1ade4cb0571bf87d2f1a9fd2d7`.
Fingerprint checks are not weakened or bypassed. New targeted repair status is PENDING_OWNER; production remains
NOT_AUTHORIZED and platform-specific release validation remains pending.

## Targeted owner run 20261104-000339-IzxY36 — one Turkish UI failure remains

The owner reran exactly two Python, one backend and two iOS cases. B and C passed; the English
manual-wrong-step UI test passed. Only testTurkishLostAcknowledgementRecovery failed. A, golden evaluation,
previously passing tests and Linux-only cases were not rerun. sourceUnchanged=true; cleanup passed.
Source fingerprint: `05bd58162f8f6b30571a7b934888abeb0c2180503a298b3649e262fc134320d4`.

The Turkish test stopped before submitting: the editor contained x=5x=5 after typing x=5. Read-only log/source
inspection traced this to persistent attempt drafts using the shared real-account store despite each UI test's
unique solve-fixture-scope. The previous English test's draft was correctly retained, then restored into the next
synthetic scenario. AppDependencies now injects a separate protected AttemptFixtures/<validated-fixture-scope>
store in DEBUG fixture mode. The existing --solve-reset flag clears only that scoped synthetic store. Normal
account draft/retry persistence is unchanged; reopening the same fixture without reset still retains its draft.
The UI helper now requires an initially empty editor, preserving the original exact-input, lost-acknowledgement,
localized result and counterexample assertions instead of silently clearing leaked state.

Repairs were executed and verified by the owner in targeted run `20261104-002119-IQLWzE`. B/C and the English UI pass remain historical evidence. No earlier owner artifact was rewritten;
production remains NOT_AUTHORIZED and platform-specific release validation remains pending.

## Final targeted owner run 20261104-002119-IQLWzE — all identified product failures resolved

The owner executed the targeted repair run for the sole remaining failing test case.
Run directory: `.generated/sprint-6-7-owner/20261104-002119-IQLWzE`.
Source fingerprint: `6ba82c84f9c06ca3c328f75895f0f4ec28e54b233dd62b73523f4a5b62d58f1a`.
Working tree source: `sourceUnchanged=true`.

Selection:
- Verifier (pytest): 0 cases selected (previously passing 510 tests + 2 targeted repairs retained).
- Backend (Maven): 0 cases selected (previously passing 98 tests + 1 targeted repair retained).
- iOS (xcodebuild): 1 test case selected: `VerifiedAIUITests.AttemptAnalysisUITests/testTurkishLostAcknowledgementRecovery`.

Execution and evidence results:
- Gate Prerequisites: `PASS` (forward migration source V030–V032 validated).
- Gate A: `NOT_RERUN` (docs and contracts previously verified).
- Gate B: `NOT_RERUN` (previously passed in `20261104-000339-IzxY36`).
- Gate C: `NOT_RERUN` (previously passed in `20261104-000339-IzxY36`).
- Gate D: `PASS`. Exactly one test executed and passed:
  - `VerifiedAIUITests.AttemptAnalysisUITests/testTurkishLostAcknowledgementRecovery` (passed).
  - Failed cases: 0. Missing: 0. Unexpected: 0. Skipped: false. Result bundle: `VerifiedAI.xcresult`.
- Gate Cleanup: `PASS` (simulator shutdown and deleted).
- Targeted Repair Status: `targetedRepair: "PASS"`.
- Unresolved Failures: `unresolvedFailures: []` (zero remaining failures).
- Next targeted selection: 0 verifier, 0 backend, 0 iOS tests.

All 5 test failures originally observed in product run `20261103-233019-8g1dlw` are now verifiably resolved across the owner's targeted repair chain without weakening assertions or leaking synthetic state.

## Current owner acceptance policy

The sole owner entry point is `scripts/quality/sprint_6_7_owner_acceptance.sh`; the agent did not execute it.
This new product revision changes canonical parsing/source selection, isolated-worker routing, active Flyway,
privacy and the shared iOS renderer. Those concrete regression dependencies justify current-source integration
coverage on the first owner run, recorded in selection-reason.json. Follow-up repair runs select only failures.
Python failed selectors, failed validators, golden failures and gates that fail before running any case are
recoverable without repeating previously passing test cases. Missing partial results remain blocked, never
silently interpreted as success. All evidence remains per-run with source fingerprints; no historical files rewritten.
Known Linux resource tests skipped on macOS are separately PLATFORM_SPECIFIC_VALIDATION_PENDING; unknown skips
remain blocking. Local acceptance cannot authorize shared migration, live providers or production release.

## Final source review corrections

Native OCR review now retains a bounded memory-only image for line comparison; encoded drafts and submissions
share a consistent size ceiling. Uncertain results cannot carry a diagnosis, and natural-domain/set envelopes
are bounded across Python and Java. Quadratic factoring explicitly presents root factors; all method transitions
include independent checks. Canonical v3 routing inspects mathematical expression fields only and leaves syntax
already supported by legacy canonicalization on its existing path. Twin completion refreshes mastery; confidence
is displayed. Private exports omit answer retry keys. Additional synthetic sources cover private-answer exposure,
first-answer replay, owner isolation, immutable artifacts and learning deletion; none were executed.
The runner distinguishes runtime changes from documentation-only edits and does not broaden a failed-case retry
because an inherited gate was NOT_RERUN. Static source review is not a substitute for owner execution.

## Historical evidence — manual v1 repair chain

The following record is preserved from the previous delivery. Its statements about absent features, staged SQL,
runner behavior and then-current source refer only to that historical revision, not the current product continuation.

## Owner run and failure repair

Evidence directory: .generated/sprint-6-7-owner/20261103-095718-VWsajt.
Source fingerprint: c4536fdce1e1889096ea718e8dfee0dfab9a2930c58bf418e3622b655e16b188.
These observed counts apply only to that run's source, not to the subsequent repairs:

- A: PASS.
- B: 382 Python tests passed, five Linux-only resource tests skipped on macOS; gate BLOCKED.
  Golden evaluation: 30/30 passed, nine published witnesses independently sound, zero false accusations.
- C: Maven log records 89 tests, 87 passed and two failed. The original evidence collector reported zero
  because Surefire did not honor the unbound reports-directory property. The POM now binds it explicitly.
  Stale reports in target/surefire-reports are never used for selecting retries.
- D: 198 iOS tests passed (174 unit and 24 UI), two attempt UI tests failed.

Backend failures: StudentAttemptControllerTest.authenticationAndBolaAreEnforced and
disabledSubmissionReturnsProblemDetails. The production rate limiter correctly fails closed without Redis;
the HTTP test had no synthetic limiter, so context returned 429 and the dependent submit fixture became
invalid (422). The test now injects a deterministic limiter and checks context status before using its source.
Production rate limiting is unchanged.

iOS failures: AttemptAnalysisUITests.testManualWrongStepAndCounterexample and
testTurkishLostAcknowledgementRecovery. Both timed out trying to reach submit after typing while the keyboard
was open. Added a localized keyboard Done button and interactive scroll dismissal; tests explicitly dismiss
the keyboard before submitting. The second run confirmed both tests can now dismiss the keyboard and submit.

The first targeted invocation selected exactly these two Java methods and two XCTest methods. It did not run
A, B, golden evaluation, passed Java/iOS cases, or full-suite gate E. If only some repairs pass, subsequent
invocations exclude those passed methods. New evidence records FAILED_TESTS_ONLY and fullCurrentSourceAcceptance=false.

## Second owner run: backend repaired, result accessibility repair pending

Evidence directory: .generated/sprint-6-7-owner/20261103-102051-pjIcnT.
Source fingerprint: c81e1ed4b20cae7c6138211b72e38ed616e473422ae73cc9e40bc066be3315e8.
C passed exactly the two selected HTTP test methods, with no skipped, missing or unexpected methods.
D ran only the two selected UI methods; both reached submission/retry but failed finding the first-wrong-step
accessibility identifier. No previously passing tests were repeated.

Read-only inspection of both recorded xcresult accessibility trees established that the wrong-step text,
submitted x=5 and exact counterexample were rendered. The container's attempt.results identifier had
propagated onto every result child, replacing the verdict and proof identifiers. Removed that container
identifier and placed outcome/domain identifiers on their individual Text elements. Verdict/proof identifiers
remain on their own elements; result assertions are retained.

The same recorded trees exposed literal attempt.outcome.*, attempt.domain.* and attempt.witness.* keys.
Dynamic localization keys now use String concatenation before LocalizedStringKey construction, avoiding
literal interpolation templates. Both existing failing UI tests now check the localized outcome, domain,
verdict and witness explanation in English/Turkish, and scroll the attempt screen only until proof is visible.

The following owner invocation selected zero backend tests and only those two remaining UI tests. C's recorded
pass remains historical evidence; A/B/C and other passed tests are not rerun. Codex ran no tests/builds or
validators; xcresult inspection read already-recorded artifacts only. Latest repairs await owner execution.

## Third owner run: Turkish recovery passed, English keyboard focus repair pending

Evidence directory: .generated/sprint-6-7-owner/20261103-102821-2oI0xP.
Source fingerprint: 74ece18208a71549a9dc4b3f575a2caee747fb889a1e6055e4b9a9e6153641c6.
Only the two remaining UI methods ran. Turkish lost-acknowledgement recovery passed, including localized
outcome/domain/verdict/witness assertions and the proof accessibility identifier. English manual input failed
earlier at typeText: XCTest reported that neither the editor nor its descendants had keyboard focus.
No English result assertion executed in this run; the Turkish pass does not substitute for English evidence.

The UI input helper now waits for an enabled/hittable editor, taps it and waits for the software keyboard.
If the first tap does not acquire focus, it performs one bounded tap inside the editor's text inset and
requires the keyboard before typing. It also asserts the exact entered text. No unconditional sleeps,
whole-test retries, prefilled model state or relaxed result assertions were added. Product code is unchanged.
This test synchronization repair was not executed by Codex. The following owner invocation selected only
testManualWrongStepAndCounterexample; the passed Turkish method and all previously passed tests remained excluded.

## Fourth owner run: all identified test failures resolved

Evidence directory: .generated/sprint-6-7-owner/20261103-103500-nSsCvT.
Source fingerprint: 164d01bf1c134c1734f97954f18ab9df36b547c12aedfbae727d89049a7b82b7.
Exactly one test ran: AttemptAnalysisUITests.testManualWrongStepAndCounterexample, PASS.
No failed, skipped, missing or unexpected tests were reported; sourceUnchanged=true and cleanup=PASS.
The summary records targetedRepair=PASS and unresolvedFailures=[]. Both original backend failures and
both original UI failures have now passed in the explicitly linked targeted runs. Their different source
fingerprints and run scopes remain preserved; the passes are not merged into a fabricated full-suite run.

No additional retry is requested. A normal failed-test-only invocation would select zero tests.
overall=BLOCKED and exitCode=2 remain intentional: the five Linux-only tests skipped on macOS, missing
migration/domain prerequisites and full-current-source coverage were not resolved by these targeted runs.
Production remains BLOCKED; hosted CI is NOT_VERIFIED and live AI is NOT_EVALUATED. This final handoff
updates documentation only and does not change product/test/runner source or any generated owner evidence.

## Static review performed

Read-only review covered source/module imports, routes and auth, staged migration FKs and unchanged legacy
migrations, typed wire shape and response binding, first-error indices, domain refusal, process supervision,
private retry state, Xcode source registration, finite metric labels and default-off configuration. It found
and corrected the unsupported-source transport path, verifier metric allowlist integration, partial-schema
privacy behavior and history reload ordering. This review is not executable validation or acceptance.

Continuation review also closed an exact-witness boundary: Java rejects mathematically equal rational
evaluations even when their strings differ (for example 1/2 and 2/4), using bounded BigInteger cross-products.
Python and iOS reject contradictory uncertainty/stop reasons; iOS also checks witness kind/claim/truth shape.
The iOS client displays backend evidence and does not independently establish the mathematical theorem.
Context/history loading errors preserve the pending submission identity. Local retry reads allocate at most
16,385 bytes before rejecting oversized files; saved lines obey both UTF-16 and UTF-8 API bounds.
Regression tests cover these cases, including near-equal exact fractions, context-conflict recovery and
malformed evidence. The owner runner now requires 13 attempt unit tests and 2 attempt UI tests.
The first owner run passed all 13 attempt unit tests; both attempt UI tests failed as detailed above.
Tests executed by Codex during continuation and repair: NONE.

## Authored evaluation artifact

Dataset: evaluations/golden-datasets/attempts/manual-polynomial-v1.json.
Size: 30 synthetic cases. Algorithm: bounded-attempt-v1.
SHA-256: 24a30b62ba0dc94d795f8626c87f5ef8e45df7f2a74e3ca8d791bc1b895105e5.
The owner's recorded golden run passed all 30 cases: first-error recall 1.0, false-accusation rate 0.0,
11 uncertain cases, nine sound published witnesses and seven safely refused domain-protection cases.
These are bounded synthetic observations for the recorded fingerprint, not handwriting or production claims.
Specific taxonomy accuracy and handwritten performance: NOT_EVALUATED.
