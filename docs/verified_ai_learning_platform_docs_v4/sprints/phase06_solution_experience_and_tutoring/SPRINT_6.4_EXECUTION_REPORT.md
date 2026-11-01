# Sprint 6.4 — execution report

Implementation status: `LOCAL_ACCEPTANCE_PASSED`.
Owner acceptance: run `20261101-200102-8qY6k7` (exit code 0; all gates PASS).
Hosted CI: `NOT_VERIFIED`. Production rollout: `BLOCKED`. Definition of Done: pending hosted verification.

## Baseline

Branch main, clean starting worktree, HEAD/local origin/main
`14e18799c5780d07c9f1a53aeb960ba322bddd34`. No baseline advancement or network fetch.
Sandbox denied requested branch creation; no escalation or bypass. No commit/push/merge/deploy.
Owner explicitly directs local Sprint 6.4 ahead of Track B scheduling; ADR-011 and all commercial,
security/privacy, CI, provider and TestFlight gates remain authoritative.

## Implemented scope

Backend tutoring domain and JDBC persistence; six authenticated owner-scoped API operations; exact
canonical/selected-parse lineage; minimal profile context; no fabricated mastery; ACTIVE/CLOSED and
derived stale state; database-backed retry identity and ordered messages; configurable bounded capacity;
actual chunked/request body size bound; enum-only privacy-safe telemetry; full bounded account export;
account/source cascade and erasure of completed account export snapshots.

iOS Tutor feature is attached to existing problem/solution navigation, using AppDependencies and APIClient.
It displays persisted learner text, loading/empty/saved/error/offline/closed/stale states and honest lack
of generation. Protected atomic pending operations preserve the same key/payload across relaunch after
uncertain commit; request generation, active owner and authentication session guard publication.
EN/TR catalog entries, VoiceOver grouping, Dynamic Type layout, 44-point composer action and test IDs
are included. No animations or UserDefaults transcript authority were added.

OpenAPI is additive. The problem and profile modules expose small named interfaces. Historical migration
and mathematical fixtures are untouched by implementation. See the implementation map for exact ownership,
API semantics, resource limits, export recovery and rollback instructions.

## Authored evidence, not executed evidence

- TutorPolicyTest: lifecycle, currentness, capacity, Unicode/control validation, keys and pagination.
- TutorPersistenceIntegrationTest: fresh Flyway, composite FKs, durable restart replay, concurrent creation
  and appends, conflicts, ordering/pages, source changes, close, deletion/ownership, export and limiter.
- TutorPrivacyCapacityTest: oversized export fails before transcript loading, with no truncation.
- TutorSessionControllerTest: auth, cross-owner reads/writes, strict role/field rejection, body bounds,
  retry/status/schema behavior, account export/deletion and excessive requests.
- TutorTests: typed wire/headers/body, assistant/schema rejection, durable pending marker, uncertain
  commit/model reinstantiation, duplicate send guard, offline recovery, paginated stale-source history, account invalidation and close.
- TutorUITests: synthetic start, ordered messages, refresh/resume, lost-ack retry, close and TR accessibility.
- Owner runner includes ModularityTest and existing profile/privacy/solve/verification/solution UI regressions.

There are no agent-produced passing counts, integration results, simulator evidence or performance claims.
Synthetic UI fixtures are not PostgreSQL evidence. They neither call providers nor invent tutor replies.
Manual VoiceOver, large Dynamic Type, physical-device, background termination and pedagogical copy review
remain owner work. Automated results may require repair once compilation/runtime evidence is available.

## Migration and rollout blockers

V027 is used because V024, V025 **and V026** are already reserved. Shared upgrades are not ready.
Only fresh disposable PostgreSQL is supported in this local scheduling exception. Reconcile the reserved
migrations in order and test real upgrade sequencing before shared rollout; no out-of-order workaround.
Rollback disables the application entry/routes while preserving additive tables and user data.
Tutor-generation behavior, Socratic policy and progressive hints remain Sprint 6.5/6.6 work.
Incremental AI calls, verifier calls, paid providers and required credentials are all zero by design.
Training eligibility remains false. No release authorization is implied by local implementation.

## Owner handoff

Entry point: `scripts/quality/sprint_6_4_owner_acceptance.sh` (never executed by agent).
Evidence: `.generated/sprint-6-4-owner/latest/summary.json`; timestamped directories retain gate logs,
backend XML reports, migration copy and VerifiedAI.xcresult. Missing prerequisites fail BLOCKED;
missing mandatory test evidence, failures or skips fail closed. Reruns never overwrite previous runs.
Return summary.json and the relevant failing logs for the owner-operated repair loop.

## Owner acceptance attempt and repair — 2026-11-01

Owner run `20261101-185412-LM6LM5`: prerequisites, Gate A and cleanup PASS; Gate B FAIL.
iOS executed 164 cases: 161 passed, 3 failed, 0 skipped, no missing required cases.
All three failures were TutorUITests. Backend/Gate C and reconciliation/Gate D did not run.
These are owner-provided results for the pre-repair source, not acceptance of the current changes.

Root cause: the DEBUG UI authentication fixture populated SignInViewModel state without writing
AuthenticationSessionStore. Tutor's ownership closure required the absent stored credentials, invalidated
the model and disabled start/composer controls. The remaining UI assertions then failed in sequence.
Repair binds the ownership check to the actual service instance and current presented owner/session.
Only FixtureTutorAPI in DEBUG can use the matching synthetic presentation without stored credentials.
Live TutorAPI still requires matching persisted credentials and rechecks presentation after awaiting them.
Account invalidation now exposes a localized authorization error rather than only disabled controls.
Added regression tests cover fixture owner/session/logout rejection and live credential requirements.
UI tests wait for enabled/hittable controls and stop on the first failure to preserve useful diagnostics.

Repair tests/builds/acceptance: NOT_RUN_BY_AGENT. Await a fresh owner run using the same entry point.
Overall acceptance remains FAIL pending rerun; hosted CI NOT_VERIFIED and production BLOCKED.

## Second owner run and accessibility-query repair

Owner run `20261101-190742-R3OL8v`: prerequisites, Gate A and cleanup PASS; Gate B FAIL.
iOS executed 166 cases: 164 passed, 2 failed, 0 skipped and no missing required cases.
The two added authorization regressions and Turkish Tutor UI smoke test passed. Backend/Gates C–D
remain NOT_RUN. This evidence accepts neither the current repair nor the overall sprint.

Both remaining failures occurred at the `tutor.message.1` lookup restricted to XCUIElementTypeOther,
after sending (and retrying in the lost-ack case). The SwiftUI row combines textual accessibility children;
its platform element type is not guaranteed to be Other. UI queries now use the stable message identifier
across element types. Assertions additionally require the exact synthetic text, learner sender and saved
confirmation, preserve the ordering/no-duplicate checks, and reject a remaining error state. Future failures
retain a screenshot and full accessibility hierarchy in the owner-generated Xcode result bundle.

No tests/builds/acceptance were executed by the agent. Reading the existing xcresult activities was denied
by local TestReport cache permissions; no escalation was attempted. Diagnosis used the existing test log
and view/test source. Current repair awaits the same single-command owner rerun.

## Third owner run and backend compilation repair

Owner run `20261101-192256-qUzacI`: prerequisites, Gate A and Gate B PASS (166 iOS cases passed, 0 failed, 0 skipped).
Gate C FAIL: Maven build failed with compilation error on `TutorPersistenceIntegrationTest.java`:
ambiguous overload resolution for `assertThat(tx.execute(...))` matching both `assertThat(IntPredicate)`
and `assertThat(Predicate<T>)`.
Repair: Bound the executed callback to an explicit local variable `var replayed` before asserting equality with `m`,
resolving the type ambiguity cleanly without raw unchecked casts.

## Fourth owner run and export timestamp serialization repair

Owner run `20261101-194311-ZYtr3b`: Gate A and Gate B PASS.
Gate C executed 117 cases across 17 suites: 116 passed, 1 failed (`TutorSessionControllerTest.accountExportAndConfirmedDeletionReachTutorData`).
Root cause: `TutorSessionLifecycleContributor` exported raw `TutorSession` and `TutorMessage` domain objects containing
`java.time.Instant` fields without `jackson-datatype-jsr310` registered in the identity module's Jackson 2 mapper.
Repair: Added `sessionMap(TutorSession)` and `messageMap(TutorMessage)` mappers in `TutorSessionLifecycleContributor`,
formatting timestamps to ISO-8601 strings (`Instant.toString()`), exactly matching existing contributors.

## Successful owner acceptance — run 20261101-200102-8qY6k7

- All gates: prerequisites, A, B, C, D, cleanup: `PASS`
- iOS: 166 executed, 166 passed, 0 failed, 0 skipped, 89 required cases verified
- Backend: 117 executed, 117 passed, 0 failed, 0 skipped, 17 suites, 25 required cases verified
- Fresh database migration V027 verified on PostgreSQL
- Evidence reconciled and verified: `LOCAL_ACCEPTANCE_PASSED` (exit code 0)
- Limitations: Hosted CI NOT_VERIFIED, production release BLOCKED, V024–V026 reserved, tutor generation deferred to Sprint 6.5/6.6.
