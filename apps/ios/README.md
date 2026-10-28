# VerifiedAI iOS

SwiftUI application shell for the Verified AI Learning Platform.

The iOS client owns presentation, navigation, local cache/projection, and secure token storage. It does not call AI providers, PostgreSQL, Redis, object storage credentials, or the internal math verifier directly.

## Local Validation

```sh
xcodebuild test -project apps/ios/VerifiedAI.xcodeproj -scheme VerifiedAI -destination 'platform=iOS Simulator,name=iPhone 16 Pro'
```

## Recurring primary solve UI failure — 2026-10-27

Owner local validation passed on 2026-10-28: focused acceptance was confirmed by the owner;
the supplied full-regression console log records 114 passed tests and TEST SUCCEEDED.
The subsequent PR #30 GitHub Actions run failed during application launch; see the
artifact-backed investigation below. No build, test or validator was executed by the assistant.
The investigation began on a clean feature branch at 1e31f3dd8f2d34fa9f1ee4d1012322dcc75ccb10.
GitHub's comparison with main commit daa5ddf32b0092b70681af3df64e2a8204348ab2 reported
no file differences; the exact main test/workflow contents were also read and matched locally.
No branch switch, commit or push was performed.

Evidence:

- [Run 40494005459](https://github.com/coskunburak/verified_ai/actions/runs/40494005459),
  main daa5ddf32b0092b70681af3df64e2a8204348ab2: only
  PrimarySolveUITests.testHistoryQueuedRunningUnverifiedStepsAndReopen failed in the reported tests
  (189.763 seconds). The remaining reported iOS tests passed.
- [Run 40485735850](https://github.com/coskunburak/verified_ai/actions/runs/40485735850),
  main aa9b9a5387d56f4fa00be6ddae5bb88667e8cc06: the same test failed (61.704 seconds),
  as did the separate capture-review UI test. No capture changes are included here.
- Both downloaded failure logs report the test name and exit code 65, without an assertion
  message or source line. Check annotations add no XCTest assertion details. Both runs have
  zero uploaded artifacts; their runner-local xcresult paths cannot be recovered from artifacts.
  The precise assertion in those historical failures therefore remains unknown.

The confirmed harness defect is a transient-state observation race: the normal poll loop waits
2 seconds before RUNNING and 3 seconds before the terminal response. XCTest's start/tap/idle and
accessibility queries can outlive either visible phase. A longer wait cannot recover a phase
that has already disappeared. The old fixture's instance-local read counter also replayed RUNNING
after recreation instead of deriving the next transition from persisted state.

The affected UI test now opts into DEBUG-only controlled fixture progression. After asserting
the real QUEUED UI, it acknowledges that synthetic server phase through a fixture-only toolbar
control; the pending/next actual polling GET then returns RUNNING. The same handshake releases
the terminal response after RUNNING is asserted. Controls do not assign ViewModel snapshots,
change production polling, skip phases, disable tests or increase the original 2/4/8-second
state assertion timeouts. Both solution steps and the existing answer/fixture/reopen/no-new-start
assertions remain covered. Normal fixture runs still progress automatically.

Each UI test uses a UUID-scoped UserDefaults suite and recovery directory, preserved only for
that test's deliberate relaunch. Fixture transitions derive from persisted job state, and
cancelled controlled reads unblock without advancing the job. New ViewModel/fixture tests cover
phase holding, same-job progression, wrong-phase acknowledgement, cancellation, running-job
relaunch without another start, and automatic progression without terminal-state rewind.

Navigation/start/reopen waits now identify their checkpoints. Failed assertions retain the
synthetic app's accessibility hierarchy, launch arguments and screenshot in XCTest attachments;
teardown terminates the launched app. The workflow preserves the xcodebuild exit status with
pipefail and always uploads its explicit xcresult plus raw console log for 14 days. If the next
run fails elsewhere (navigation, readiness, rendering or reopening), these records establish the
exact assertion and screen state instead of attributing it to timing without evidence.

Scope: ios-ci workflow, AppDependencies fixture wiring, FixturePrimarySolveAPI, DEBUG-only
PrimarySolveViewModel/View controls, PrimarySolveUITests, PrimarySolveViewModelTests and this README.
No backend, Sprint 5.6 domain, real-service polling policy or model routing change is included.

Owner iteration, 2026-10-28: the first focused command stopped during Swift 6 test compilation;
no selected test executed. Six diagnostics shared one cause: the newly added synchronous
XCTest tearDownWithError override is nonisolated, so it cannot access the test's MainActor UI
state or capture/terminate the app. The first async correction exposed a further Swift 6
diagnostic at super.tearDown(): the override inherited MainActor isolation and sent its
non-Sendable XCTestCase superclass reference to a nonisolated async method. The second owner
run also stopped before executing tests. Making the async override nonisolated then exposed
the same boundary in the opposite direction: capturing task-isolated self in MainActor.run
was rejected in the third run, again before any test executed.

The async teardown override has now been removed. The MainActor-isolated launch helper
registers one synchronous addTeardownBlock on its first launch; the closure inherits that
context and reads the latest application reference so deliberate relaunches are cleaned up
as well. Failure attachments and app termination remain in the block. XCTest's normal
superclass lifecycle remains intact, with no async transfer of the test case. Apple's
[addTeardownBlock contract](https://developer.apple.com/documentation/xctest/xctestcase/addteardownblock%3A?language=objc)
specifies serial, main-thread execution after the test and before the teardown methods.
No unsafe Sendable conformance, isolation bypass, assertion or timeout relaxation was added.
Evidence bundles under .generated/ios-ci:
PrimarySolve-focused-20261028-021121.xcresult,
PrimarySolve-focused-20261028-021328.xcresult and
PrimarySolve-focused-20261028-021957.xcresult. These compilation failures are not evidence
of the original UI assertion; subsequent owner acceptance is recorded below.

Owner acceptance, 2026-10-28: the owner confirmed both the focused and full commands passed.
The supplied log covers the full command without only-testing filters and ends in
TEST SUCCEEDED. It reports 114 passed test cases (104 unit/contract tests and 10 UI tests),
with no reported failed or skipped cases. All 11 PrimarySolveViewModelTests passed, including
the three new fixture regressions. The recurring
PrimarySolveUITests.testHistoryQueuedRunningUnverifiedStepsAndReopen passed in 29.243 seconds;
the relaunch UI test and the separately failing historical capture-review UI test also passed.
The result bundle is .generated/ios-ci/VerifiedAI-full-20261028-022452.xcresult.
Focused success is owner-reported; no separate successful focused console log was supplied.
This establishes local acceptance, not a new main-branch CI result or recovery of the
historical failing assertion. No further source changes were made after this acceptance.

### PR #30 launch failure — artifact investigation, 2026-10-28

Baseline rechecked against GitHub: PR #30 remains OPEN at
`c53a389db0b1813f87c0568d0b739da440cbb13b`; main remains
`daa5ddf32b0092b70681af3df64e2a8204348ab2`. The local feature branch matches the PR
head, started clean, and is 14 commits behind / 1 ahead of origin/main.
No Git publication or branch changes were performed.

[Run 40603823381](https://github.com/coskunburak/verified_ai/actions/runs/40603823381)
failed its iOS job. Artifact `ios-test-results-40603823381-1` (ID `12780158452`)
was downloaded to `.generated/ci-pr30`. Existing results were inspected with
`xcresulttool`; no tests were executed. The failed test's activities, hierarchy,
screenshot, runner logs and scheduling logs establish:

- `testHistoryQueuedRunningUnverifiedStepsAndReopen()` failed in `app.launch()`
  with `Application 'com.verifiedai.learning' does not have a process ID`.
  The result issue records `PrimarySolveUITests.swift` line 33; runner console
  diagnostics report line 34. The launch call is line 33 at the PR head.
- The last checkpoint is `launch GENERATED_UNVERIFIED, reset=true`. No history
  navigation, phase acknowledgement, polling assertion, or reopen assertion ran.
- Runner timestamps below are UTC. Launch began at 23:48:28.602. Xcode reported
  PID 8100 at 23:49:32.825 and confirmed launch completion at 23:49:36.820.
  XCTest still tracked `com.verifiedai.learning (pid:0)`, then timed out waiting
  for `isProcessIDValid` at 23:50:36.883 after 60.061 seconds. Xcode independently
  reported PID 8100 alive at 23:50:27.055.
- Teardown reported `appState=1` and could not capture the app hierarchy, but its
  screen attachment shows the fixture dashboard. This is consistent with failed
  automation process tracking, not evidence that the app failed to render.
- Scheduling logs show parallel execution: PrimarySolve UI tests on Clone 1
  overlapped unit tests on Clone 2. Bootstrapping each worker took almost five
  minutes. The total failed-test duration was 142.255 seconds.

The concrete failure is XCTest launch/PID tracking before the solve flow. The
artifact does not prove the underlying Apple framework defect or that resource
contention alone caused it. It provides no basis for changing polling, fixture
phase controls, navigation, or accessibility identifiers.

Bounded mitigation: `.github/workflows/ios-ci.yml` passes
`-parallel-testing-enabled NO` to remove concurrent test runners and cloned
simulator test execution from this CI gate. All tests, assertions, timeouts,
`pipefail`, and failure-artifact upload remain intact. Production and test Swift
sources are unchanged. The tradeoff is potentially longer serial execution.
Rollback is removal of this flag if subsequent evidence warrants a different fix.

Status: IMPLEMENTED / LOCAL_TESTED / PENDING_CI. The owner subsequently authorized
agent execution of all tests, superseding the original owner-only execution rule.
The focused owner run and full agent run below validate the serial configuration locally.
CI remains FAILED on the current PR head. Sprint 5.7 is BLOCKED until the corrected
commit passes required CI and the owner merges PR #30. If launch fails again,
inspect the new result bundle and runner diagnostics before making further changes.

### Serial configuration acceptance — 2026-10-28

- Owner focused run: `PrimarySolve-focused-20261028-031118.xcresult`;
  1 UI test and 11 ViewModel/fixture tests passed, zero failures, `TEST SUCCEEDED`.
  The queued/running/reopen UI flow passed in 27.168 seconds.
- Agent full iOS run: `VerifiedAI-agent-full-20261028-031314.xcresult`;
  114 passed (104 unit/contract + 10 UI), zero failures and zero skipped tests,
  `TEST SUCCEEDED`, exit code 0. Counts were independently read from the result
  bundle summary. The queued/running/reopen flow passed in 26.792 seconds.
  Console: `.generated/ios-ci/agent-full-xcodebuild.log`.
- Local runtime: iPhone 16 Pro, iOS Simulator 18.4, Xcode 16.3. The failed hosted
  run used iOS Simulator 26.2; local success does not establish hosted acceptance.
- The exact full-suite command is recorded below. No Swift source changes were
  necessary. No commit, push, merge, or paid model invocation was performed.

The owner's expanded test authorization also covered the existing repository
regression and quality gates. Agent results on the same working tree:

| Gate / command | Actual result |
| --- | --- |
| API: JDK 21, `mvn -Dverifiedai.test.minio-image=verified-ai/minio-test:2024-07-16-3535197 test` from `services/api` | BUILD SUCCESS; 483 discovered, 481 passed, 2 skipped, zero failures/errors |
| `make test-verifier` | 20 passed; one existing Starlette/httpx deprecation warning |
| `make docs-check contracts-check` | PASS; 290 Markdown files checked, contracts passed |
| `make lint` | PASS; Python compile/Ruff and Java compile |
| `make secret-scan` | PASS |
| `python3 scripts/quality/check_prompt_immutability.py --base-ref origin/main` | PASS; existing prompt/schema versions unchanged |
| `python3 -m unittest discover -s scripts/quality -p test_prompt_immutability.py` | 5 passed |
| `python3 -m unittest discover -s scripts/security -p test_secret_scan.py` | 7 passed |
| `python3 -m unittest evaluations/runners/test_ingestion_evaluation.py` | 12 passed |
| `python3 -m unittest discover -s evaluations/runners -p test_qwen_report.py` | 5 passed |
| `AI_EVAL_MODE=local AI_EVAL_RUN_ID=pr30-agent make eval-ai` | PASS; 18 synthetic cases, no release regressions; existing near-duplicate fixture warnings |

Backend tests used the existing Docker service and pinned MinIO test image;
no dependencies were installed. Canonical persistence, secondary-solving and
architecture regressions ran as part of the full Maven suite. The two skipped
cases are `QwenRealModelEvaluationTest.durableSmoke` and `canonicalBenchmark`:
`QWEN_OWNER_RUN` is absent, so real-model acceptance remains gated. No skipped
case is counted as passed. Backend console: `.generated/ci-pr30/agent-backend-test.log`.
Local evaluation reports: `.generated/evaluations/pr30-agent/`.

The first Qwen report unit-test invocation used the verifier virtual environment
and failed because it lacks `jsonschema`. Re-running with the existing system
`python3`, which supplies that dependency, passed all five tests without source
changes or installation. This was a test-environment correction, not model execution.

Publication handoff: only `.github/workflows/ios-ci.yml` and this README are changed.
Proposed commit: `fix(ios): serialize CI tests after XCTest launch PID failure`.
PR #30 can retain its existing title. Publication requires owner authorization;
hosted acceptance must reference the new commit. Sprint 5.7 remains unimplemented
pending that acceptance and the owner's merge, as required by the execution gate.

### Owner acceptance commands

Run from the repository root with Xcode selected and the iPhone 16 Pro simulator installed.
The focused and full commands below have passed; retain them for reproduction,
not a request to repeat accepted tests. Timestamped result bundles avoid collisions.
Use the same
serial test setting as CI. First run the affected UI flow (gate A) and related
ViewModel/fixture regressions (gate B) in one focused invocation. Stop at the first
failure and share its evidence; do not proceed to the full suite until focused acceptance.

```sh
cd /Users/burakcoskun/Desktop/ai_verification_tutor
mkdir -p .generated/ios-ci
xcodebuild test \
  -project apps/ios/VerifiedAI.xcodeproj \
  -scheme VerifiedAI \
  -destination 'platform=iOS Simulator,name=iPhone 16 Pro' \
  -parallel-testing-enabled NO \
  -derivedDataPath .generated/DerivedData \
  -resultBundlePath ".generated/ios-ci/PrimarySolve-focused-$(date +%Y%m%d-%H%M%S).xcresult" \
  -only-testing:VerifiedAIUITests/PrimarySolveUITests/testHistoryQueuedRunningUnverifiedStepsAndReopen \
  -only-testing:VerifiedAITests/PrimarySolveViewModelTests
```

After focused acceptance, run the full iOS regression (gate C):

```sh
cd /Users/burakcoskun/Desktop/ai_verification_tutor
mkdir -p .generated/ios-ci
xcodebuild test \
  -project apps/ios/VerifiedAI.xcodeproj \
  -scheme VerifiedAI \
  -destination 'platform=iOS Simulator,name=iPhone 16 Pro' \
  -parallel-testing-enabled NO \
  -derivedDataPath .generated/DerivedData \
  -resultBundlePath ".generated/ios-ci/VerifiedAI-full-$(date +%Y%m%d-%H%M%S).xcresult"
```

On failure, share the first assertion/compile error and the corresponding xcresult (including
PrimarySolve checkpoint/hierarchy/screenshot attachments). On GitHub Actions the artifact is
named ios-test-results-RUN_ID-RUN_ATTEMPT. Local acceptance of this mitigation is
complete; hosted CI acceptance remains pending.

Gate D, only after local acceptance and owner-authorized publication to PR #30:

```sh
gh pr checks 30 --repo coskunburak/verified_ai --watch
gh pr view 30 --repo coskunburak/verified_ai \
  --json headRefOid,state,mergedAt,statusCheckRollup
```

These commands inspect checks; they do not publish changes or trigger a workflow.
Require a successful iOS run on the corrected published commit, together with the
other required checks. A rerun of the old commit does not validate this change.
After owner-local PASS, provide a commit/PR handoff and await explicit publication
authorization. The owner performs the merge after CI acceptance.

