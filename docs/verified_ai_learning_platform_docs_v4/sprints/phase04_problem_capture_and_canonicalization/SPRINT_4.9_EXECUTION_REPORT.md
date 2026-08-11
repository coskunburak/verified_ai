# Sprint 4.9 Execution Report

## Final status

COMPLETE - LOCAL VALIDATION GREEN

Sprint 4.9 implementation is present in backend, API contract, iOS, and governance docs. The top-level local validation gate passes when run with the required Docker/Testcontainers and CoreSimulator permissions.

## Implemented

- V015 owner-scoped keyset history index for `problem_sessions(user_id, updated_at DESC, id DESC)`.
- Closed derived `ProblemSessionStage` and `ProblemSessionNextAction` contracts.
- Backend lifecycle policy for coarse `ProblemSessionStatus` transitions without recognition/classification-specific persisted statuses.
- Recovery planner that derives stage, exact next action, retryability, active job, and terminal/unsupported states from durable PostgreSQL facts.
- Owner-scoped `GET /api/v1/problem-sessions` with `{items,nextCursor}` keyset pagination, default limit 20, and max limit 50.
- Owner-scoped `GET /api/v1/problem-sessions/{sessionId}` with selected parse, current canonical, current classification, active job, and recovery plan summaries.
- Fail-closed ambiguous lineage handling when accepted parse history exists but `current_parse_id` is absent.
- Selected parse authority preservation through `problem_sessions.current_parse_id`.
- Canonical summaries constrained to the selected parse; classification summaries constrained to the current canonical.
- Read-only reconnect semantics: history/detail do not reserve uploads, start jobs, canonicalize, classify, solve, verify, or invoke AI.
- Existing stage-specific commands remain the only retry path; no generic recover-all endpoint was added.
- Problem-session lifecycle/history/detail/recovery metrics with low-cardinality labels.
- OpenAPI contract additions for history/detail, summaries, stages, next actions, statuses, and active jobs.
- iOS `ProblemHistory` domain/API/cache/view-model/view feature with SwiftData stale-while-revalidate history.
- iOS resume/reconnect behavior that loads cached summaries offline, refreshes backend history/detail online, polls running work through detail refresh, and invokes exact-stage retry commands only when selected by the user.

## Validation

- [x] Focused backend tests: `ProblemSessionLifecyclePolicyTest`, `ProblemSessionCursorCodecTest`, `ProblemSessionRecoveryPlannerTest`.
- [x] Contract check: `python3 scripts/quality/check_contracts.py`.
- [x] Targeted iOS simulator tests: `VerifiedAITests/ProblemHistoryViewModelTests`.
- [x] Documentation check: `python3 scripts/quality/docs_check.py`.
- [x] Full backend API test suite: `JAVA21_HOME=/Users/burakcoskun/Library/Java/JavaVirtualMachines/ms-21.0.12/Contents/Home make test-api` passed with 213 tests when run with Docker/Testcontainers access.
- [x] Full iOS simulator test suite: `make test-ios` passed on iPhone 16 Pro simulator when run with CoreSimulator access.
- [x] Verifier test suite: `make test-verifier` passed with 20 tests.
- [x] Secret scan: `make secret-scan`.
- [x] Full local gate: `JAVA21_HOME=/Users/burakcoskun/Library/Java/JavaVirtualMachines/ms-21.0.12/Contents/Home make check`.

## Notes

- PostgreSQL remains the authority; SwiftData stores only account-scoped display/recovery cache.
- Sprint 4.9 introduces no new AI capability, prompt, model route, solver, verifier, tutor, attempt, mastery, or generic workflow engine.
- NotebookLM was not used for implementation; repository documentation and the attached Sprint 4.9 execution prompt were the source of truth.

## Scope exclusions

- Phase 5 solving and verification behavior remains out of scope.
- Automatic AI retry on reconnect remains out of scope.
- Arbitrary old parse restore remains out of scope.
