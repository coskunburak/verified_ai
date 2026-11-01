# Sprint 6.4 — implementation map

Status: `LOCAL_ACCEPTANCE_PASSED` (owner run `20261101-200102-8qY6k7`).
Tests: 166 iOS passed, 117 backend passed. Hosted CI: `NOT_VERIFIED`. Production rollout: `BLOCKED`.

## Baseline and scheduling

Clean starting worktree, branch `main`, HEAD and local origin/main:
`14e18799c5780d07c9f1a53aeb960ba322bddd34`.
Creating `codex/sprint-6-4-tutor-persistence` failed because the sandbox denied Git ref writes.
Work remains uncommitted on main. No fetch, commit, push, merge or deployment.
Owner-directed local Track B exception does not waive ADR-011, Track A, hosted CI,
privacy/security review, provider approvals or TestFlight gates.

## Implementation dependency graph

- `problem/application/tutoring/TutorSourceAccess` publishes `tutor-source`: active owner and problem locks,
  selected supported/review-free canonical/parse identity, primary skill or NOT_AVAILABLE.
  It reuses the problem-owned `SolveSourceAccess` internally, without starting a solve.
- `profile/application/tutoring/TutorLearnerContext` publishes `tutor-context`: profile version,
  explanation preference and language only. No goal text, mistakes, fabricated mastery or full profile copy.
- `tutoring/domain/TutorSession`, `TutorMessage`, `TutorPolicy`: immutable snapshots, ACTIVE/CLOSED,
  bounded learner content, explicit source currentness and versioned safety/context contracts.
- `tutoring/infrastructure/persistence/TutorStore`: JDBC transactions consistent with the existing solving store.
  No unnecessary parallel JPA model. Reads/writes touch only tutoring tables.
- `tutoring/application/TutorSessionService`: authorization, durable retries, lifecycle, limits and Micrometer.
- `tutoring/api/TutorSessionController` and `TutorRequestLimitFilter`: strict body fields, six operations,
  actual request-byte bound before JSON parsing, existing authentication and Problem Details.
- `tutoring/application/TutorSessionLifecycleContributor`: complete bounded export and deletion.
  Identity deletes completed export snapshots during confirmed deletion, including copied transcripts.
- `Features/Tutor/{Domain,Data,Presentation}`: typed service, API, protected pending operation store,
  generation/account guarded model, SwiftUI screen. Entry via the existing problem/solution navigation.
  RootView supplies owner/auth-session checks; existing production solve-navigation gate is preserved.
- Existing Xcode project registers production, unit and UI test files in their real targets/shared scheme.
- OpenAPI, error codes, localizations, scoped canonical documentation and manifest change with the slice.

## Source and lifecycle invariants

Every session captures canonical id/revision, selected parse id/revision, primary skill/unavailable,
learner-context v1 and `learner-persistence-v1`. Content cannot assert identity, role, policy, mastery,
entitlements or verification. Training eligibility is false. Source changes never rewrite history.
ACTIVE/current allows new learner messages; CLOSED or stale is readable only. Close is idempotent,
never reopens, and is allowed on stale history. Explicit new-session creation binds the new source.
A durable retry may return an already committed message after close/source change; it inserts nothing.
No assistant/system write endpoint exists. Sprint 6.5 generation and 6.6 hint costs are deferred.

## Concurrency and limits

Lock order is active account row, owned problem row, then tutor writes; all operations are short local
transactions. The account lock serializes account capacity, deletion, exports and mutations. PostgreSQL
unique constraints protect sequence and retry identity; transaction rollback removes both message and
counter change. Session version increments on accepted append and first close.

Creation has empty payload and key scope `(owner, problem, key)`; retry returns that historical session.
Append scope is `(tutor session, key)` with exact content equality; conflicting content yields 409.
Keys are 8–128 ASCII letters/digits/underscore/hyphen. No hashes or keys enter ordinary responses/exports.
Limits: 2,000 Unicode code points, 8,000 UTF-8 content bytes, 16,384 request bytes, 100 messages/session,
30 retained sessions/account, 50 messages/page, 120 requests/account/minute with Redis fail-closed.
`verifiedai.tutoring.max-sessions` and `max-messages` can lower capacity, never exceed schema bounds.
No client timestamps determine ordering. No inference or verifier calls, entitlement debit or provider credentials.
Persistence is not a premium inference capability; expired subscriptions retain owned history.

## API

Base: `/api/v1/problem-sessions/{sessionId}/tutor-sessions`.
POST creates with Idempotency-Key; GET lists with offset/limit, ordered createdAt/id.
GET `/{tutorSessionId}` resumes a historical session.
GET `/{tutorSessionId}/messages` uses exclusive `after` sequence and limit; empty page is end.
POST `/{tutorSessionId}/messages` accepts exactly `{content}` and Idempotency-Key.
POST `/{tutorSessionId}/close` is intrinsically idempotent; accepts no fields and needs no retry key.
Success is HTTP 200; stable errors use existing Problem Details/correlation handling.
Missing/foreign objects are indistinguishable. All operations require active authenticated ownership.
See public-api.yaml for exact DTOs, validation bounds and status codes.

## Migration and rollback

Actual migrations end at V023. The commercial-wedge program reserves V024 (6.2C), V025 (6.7A),
**and V026 (7.0A)**. No V027 reservation was found in the scoped roadmap/data/sprint references.
V027 adds tutor_sessions/tutor_messages and a composite canonical lineage unique constraint.
Composite FKs enforce owner/problem/canonical/parse lineage, plus message/session ownership.
Source/account deletion cascades, without altering historical V001–V023 migrations.

Only fresh disposable PostgreSQL/Testcontainers databases may apply V027 now. Before any shared DB
upgrade, owners must implement/reconcile V024–V026, verify their compatibility with V027, test the ordered
upgrade from that database's exact current version and review backups/rollback. A disposable database
that already applied V027 must be recreated to test the full sequence. Do not enable out-of-order,
edit Flyway history, renumber applied migrations, or claim shared upgrade readiness.
Rollback application routes/navigation first; retain the additive schema/history. Roll back application
binaries without dropping personal learning data. Schema removal requires a separate reviewed backup,
export/retention plan and authorization. No automatic destructive down migration is provided.

## Privacy and operations

Transcripts are DERIVED_LEARNING, retained with owned history until source/account deletion; existing
backup expiry remains applicable. Export includes all sessions and messages, no retry metadata.
Synchronous tutor export caps content at 8 MB before loading it. Above this, the entire export fails with
TUTOR_EXPORT_CAPACITY_EXCEEDED; never truncates. Recovery is owner-assisted, authenticated paginated
export of all owned sessions/messages or a separately reviewed streaming export implementation; never
ask the learner to delete history to obtain an export. This capacity limitation requires release review.
The derived `sourceCurrent=false` in export denotes historical export, not a current-source authorization.

Pending iOS data is one bounded owner/problem operation (key, target id and original content), not a
transcript cache. Atomic complete-file protection, backup exclusion, bounded count and logout erasure
apply. It remains until acknowledgement or definitive rejection. Uncertain transport errors preserve it.
Reopen reads server truth; background/disappearance cancels UI publication. Account and auth-session
checks prevent prior responses from updating current presentation. No UserDefaults transcript store.

Metrics: `tutor.lifecycle` bounded outcomes; `tutor.outcome` operation/error enum;
`tutor.request` operation latency. Storage failures become generic retryable 503 without SQL content.
No transcript, identifiers, keys, math or learner profile values in telemetry tags. Existing correlation
infrastructure applies. Production alerts/load thresholds need owner operational review.

## Owner evidence plan

Author domain, PostgreSQL/concurrency/privacy/API tests, iOS contract/recovery/model tests and synthetic
UI flows. Run ModularityTest and existing profile, solving, verification, privacy and solution regressions.
The single owner runner checks prerequisites, disposable PostgreSQL via Testcontainers, fresh Xcode
results, mandatory suites/cases, skips/failures and immutable historical assets. It creates independent
runs under `.generated/sprint-6-4-owner/` and updates the `latest` symlink.
Agent does not execute tests/builds/lint/migrations/evaluations or the acceptance runner.
