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
GitHub Actions validation of these changes remains pending. No build, test or validator was
executed by the assistant.
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

### Owner acceptance commands

Run from the repository root with Xcode selected and the iPhone 16 Pro simulator installed.
Timestamped result bundles avoid collisions with previous owner runs. First run the affected
UI flow and its related ViewModel/fixture regressions:

```sh
cd /Users/burakcoskun/Desktop/ai_verification_tutor
mkdir -p .generated/ios-ci
xcodebuild test \
  -project apps/ios/VerifiedAI.xcodeproj \
  -scheme VerifiedAI \
  -destination 'platform=iOS Simulator,name=iPhone 16 Pro' \
  -derivedDataPath .generated/DerivedData \
  -resultBundlePath ".generated/ios-ci/PrimarySolve-focused-$(date +%Y%m%d-%H%M%S).xcresult" \
  -only-testing:VerifiedAIUITests/PrimarySolveUITests/testHistoryQueuedRunningUnverifiedStepsAndReopen \
  -only-testing:VerifiedAITests/PrimarySolveViewModelTests
```

After focused acceptance, run the full iOS regression:

```sh
cd /Users/burakcoskun/Desktop/ai_verification_tutor
mkdir -p .generated/ios-ci
xcodebuild test \
  -project apps/ios/VerifiedAI.xcodeproj \
  -scheme VerifiedAI \
  -destination 'platform=iOS Simulator,name=iPhone 16 Pro' \
  -derivedDataPath .generated/DerivedData \
  -resultBundlePath ".generated/ios-ci/VerifiedAI-full-$(date +%Y%m%d-%H%M%S).xcresult"
```

On failure, share the first assertion/compile error and the corresponding xcresult (including
PrimarySolve checkpoint/hierarchy/screenshot attachments). On GitHub Actions the artifact is
named ios-test-results-RUN_ID-RUN_ATTEMPT. Local owner acceptance is complete; CI acceptance
requires a subsequent GitHub Actions run of these changes.

