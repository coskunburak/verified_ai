# Sprint 6.5 — execution report

Status: `LOCAL_ACCEPTANCE_PASSED` (owner run `20261102-113306-4u8mzd`).
Owner acceptance: `LOCAL_ACCEPTANCE_PASSED`. Agent-executed tests/builds/lint/migrations/evaluations: `NONE`.
Hosted CI: `NOT_VERIFIED`. Live AI and expert pedagogy: `NOT_EVALUATED`. Production release: `BLOCKED`.

## Inspected baseline

Requested and actual HEAD: `5bbc28b91057da6040b33019d5f75ad1537238dc`, branch `main`.
Initial worktree clean; there were no user modifications to overwrite. The sandbox denied creation of
`codex/sprint-6-5-socratic-tutor`; no escalation, reset, commit, push, merge or deployment followed.
Sprint 6.4's historical successful owner run is not evidence for these new changes.

## Implemented safe slice

- Server-owned SocraticPolicy with seven pedagogical phases and explicit paused/review/unavailable
  outcomes. Legal-transition validation, minimal conceptual support, reflection completion, bounded
  unproductive dialogue and twelve-turn budget. No model decides correctness, mastery or permissions.
- Existing six persistence operations remain unchanged in shape and learner message ordering.
  Additive strict POST/GET turns contract, tutor-turn-v1 schema, exact replay, owner/current-source
  checks, 5-second new-operation transaction timeout and shared rate limiting.
- V028 adds only tutor reply/state storage and a parent unique constraint. Learner append and local
  reply commit atomically. Database uniqueness protects one reply per parent. Rollback preserves history.
- iOS existing Tutor feature displays role, phase, uncertainty, pending acknowledgement and recovery.
  Protected pending markers distinguish legacy append from new turn requests. Account/cancellation
  guards remain. EN/TR copy, VoiceOver grouping and stable test identifiers are included.
- Export includes all visible reply/state records, excludes keys, counts combined content against the
  existing complete-export-or-failure bound, and deletion cascades. Training eligibility remains false.
- Synthetic policy evaluation and owner-only acceptance entry point, with immutable run directories,
  required-case reconciliation, explicit model/expert non-applicability and migration evidence fields.

## Important scope boundary

This is a deterministic conceptual-guidance implementation, explicitly identified as DETERMINISTIC_POLICY.
The approved TUTOR prompt/schema/provider route and trusted learner-step evidence boundary do not exist.
No speculative live route, model prompt, local AI fixture adapter, reference solution reader, parallel
verifier or external processing job was introduced. The existing gateway remains unchanged and disabled
for TUTOR. The owner runner includes its route, registry and gateway regression suites.

The production application ALWAYS supplies UNKNOWN step evidence. ADVANCE_ONE_STEP is domain-gated
and tested with synthetic trusted evidence but is not reachable in this release. The tutor cannot judge
mathematical correctness or adapt explanations to actual problem content. Review records a challenge
and directs the learner to existing source/evidence UX; it does not claim to run reverification.
COMPLETED describes reflection only. Expert teaching quality and post-tutor learning improvement are
NOT_EVALUATED. Free-form intent classification is deliberately conservative; explicit intent selection
is only a learner request, never authority. Persistent hint ladders/economics remain Sprint 6.6 work.

No inference: zero calls, provider retries, model context bytes, token charges or estimated inference cost.
Deterministic policy is disabled by default via verifiedai.tutoring.socratic-enabled; local test contexts
explicitly enable it. Disabling the flag still permits existing histories and committed retry responses.

## Authored evidence (not executed evidence)

- SocraticPolicyTest: legal/illegal transition matrix, unknown/trusted evidence separation, disclosure
  resistance, dispute, bounded dialogue, reflection and bilingual/injection classification.
- SocraticTurnIntegrationTest: fresh V028, actual isolated V027 → V028 migration preserving synthetic
  legacy records, separate roles, exact retry after restart, duplicate concurrency, operation-key
  conflicts, atomic rollback on reply-write failure, close race, stale source, capacity, disabled
  capability, owner isolation/composite foreign keys, export and deletion.
- TutorSessionControllerTest: real authenticated HTTP, strict authority/numeric/duplicate-field rejection,
  bounds, ordered pages, idempotency, auth/BOLA, export/deletion; previous assertions retained.
- SocraticEvaluationTest: twenty synthetic EN/TR algebra/calculus/security/uncertainty/reflection cases,
  real policy and versioned-schema execution; malformed/authority-bearing document rejection.
- Existing TutorTests/TutorUITests extended for two-speaker guidance, uncertainty, pending/lost ack,
  legacy marker recovery, unavailable policy, review state, wire contract, cancellation/logout and TR UI.
- Runner retains persistence/profile/solve/verification/privacy/Modulith regressions and adds gateway,
  prompt registry, route and schema gates. No assertion was removed or weakened.

Provider timeout/model output assessment is not applicable to the no-dispatch slice; live evaluation is
NOT_EVALUATED. External pending jobs are not applicable: local transactions commit both records or none.
Network acknowledgement uncertainty is covered by durable same-key retry. Synthetic results do not
establish production SQL migration readiness, accessibility quality, pedagogy or live AI safety.

## Remaining release gates and recovery

Owner must execute the single sprint runner and supply latest/summary.json plus only failing gate logs.
No acceptance counts or success status are claimed before that evidence exists.
Shared migration rollout remains blocked by V024–V026 reservations and V027/V028 ordering reconciliation.
Hosted CI, staging, operational load/latency, manual VoiceOver/Dynamic Type, physical device/background
termination, expert pedagogy, approved live provider, trusted step evidence and ADR-011 commercial gates
remain outstanding. Owner-only tests establish local synthetic correctness, not these release approvals.
Rollback disables the deterministic policy flag; retain all tables/history. Never use destructive down
migrations or out-of-order Flyway to bypass the reserved migration sequence.

## Owner acceptance attempt and keyboard recovery repair — 2026-11-02

Owner run `20261102-102001-2MMjT6`: prerequisites and Gate A passed; Gate B failed.
iOS executed 176 tests: 173 passed, 3 failed, 0 skipped, no missing required cases.
All 16 Tutor unit tests passed. The failing UI cases were
`testSocraticReplyHasSpeakerAndUncertaintyAndResumes`, `testStartOrderedMessagesResumeAndClose`,
and `testUnavailableTutorKeepsDraftAndOffersRecoveryWithoutLoadingForever`.
All three failed the existing enabled-and-hittable assertion for tutor.retry, after message/reply or
unavailable-state assertions succeeded. Logs show five application-wide swipes did not expose it.
Backend (C), synthetic evaluation (E), and reconciliation (D) did not run; local acceptance was FAIL.

Source inspection identified retained composer keyboard focus after successful/definitively rejected
sends. Those paths re-enable the composer immediately, unlike the passing uncertain-send scenario,
which keeps its pending composer disabled. The keyboard reduces the transcript viewport, and the test's
application-wide swipes can land outside the conversation's scroll surface.

Repair: TutorView now owns composer FocusState and dismisses it when Send is tapped, without changing
draft, intent, pending markers or networking. Conversation scrolling also supports interactive keyboard
dismissal. Its stable tutor.conversation identifier lets UI tests scroll the actual transcript instead
of the full application. Existing hittability/content/order/recovery assertions remain intact; every
send now additionally asserts keyboard disappearance within a bounded wait. No longer waits, forced
coordinate taps, skipped cases or weakened assertions were introduced.

The agent read the owner's existing logs; no tests, build, lint, migrations or evaluations were run.
This repair is awaiting a new owner run through the same acceptance entry point. Current owner
acceptance: PENDING_OWNER; backend/migration/evaluation evidence remains unavailable, hosted CI
NOT_VERIFIED, live AI/expert pedagogy NOT_EVALUATED and production BLOCKED.

## Second owner run and persistent closure notice — 2026-11-02

Owner run `20261102-103815-H0qYkd`: prerequisites and Gate A passed. Gate B executed
176 iOS tests: 175 passed, 1 failed, 0 skipped, no missing required cases. The previous keyboard,
Socratic refresh and unavailable-recovery cases passed, including keyboard dismissal assertions.
Only TutorUITests.testStartOrderedMessagesResumeAndClose failed, at the existing tutor.closed
existence assertion after a successful hittable tap on tutor.close. All Tutor unit tests passed.
Backend C, evaluation E and reconciliation D did not run. Overall local acceptance remains FAIL.

The view placed the closed notice above the transcript, while the Close control was below it. The
failure is consistent with the upper notice being outside the current scroll viewport after two
learner/tutor exchanges. Repair moves the single closed notice into the bottom safe-area composer
section. It is shown only when the returned session status is CLOSED; it does not optimistically
claim closure on a tap. Transcript position, ownership, pending operations and service behavior stay
unchanged. The UI test retains its existence and disabled-send assertions and additionally requires
that the confirmed closed notice be hittable without scrolling through the transcript.

The agent inspected existing owner logs and source only; tests/builds/lint/migrations/evaluations
executed by the agent: NONE. Repair acceptance: PENDING_OWNER. Hosted CI NOT_VERIFIED,
live AI/expert pedagogy NOT_EVALUATED, shared migration and production release BLOCKED.

## Third owner run — confirmed transcript/composer overlap — 2026-11-02

Owner run `20261102-105358-vlFQOb`: prerequisites and A passed; B again executed 176 iOS tests,
175 passed, 1 failed, 0 skipped. The same close UI case failed at tutor.closed existence. C/E/D
were NOT_RUN. The previous assumption that only the closed notice was offscreen was incomplete;
moving that notice alone did not resolve the failure.

The agent inspected the existing xcresult through the legacy read/export API (no test execution).
The captured Tutor accessibility hierarchy gives close button bounds y=676.0..726.3, intent picker
y=663.3..697.6, composer text field y=709.7..731.7 and conversation viewport y=57.0..844.0.
Thus the transcript controls overlapped the composer surface. The close-button tap passed XCTest's
hittability check but no closed notice appeared; the capture still showed the active conversation.
This concrete overlap evidence supersedes the earlier scroll-position-only hypothesis.

Repair replaces the safeAreaInset composer with a sibling composer below ScrollView inside a VStack.
The layout now allocates separate transcript/composer areas, preserving keyboard focus handling and
the acknowledged-only closed notice. No coordinate taps or test weakening. The existing close test
adds a viewport/composer non-overlap assertion and verifies that refresh preserves confirmed closure.

Source/log inspection only; agent tests/builds/lint/migrations/evaluations: NONE. Awaiting owner rerun:
PENDING_OWNER. Hosted CI NOT_VERIFIED; live AI/expert pedagogy NOT_EVALUATED; production BLOCKED.

## Fourth owner run — iOS accepted, Jackson boundary repairs — 2026-11-02

Owner run `20261102-111544-xV17KD`: prerequisites/A/B passed; all 176 iOS tests passed,
0 skipped, 99 required cases present. This is owner evidence for the corrected Tutor layout.
Backend C executed 189 tests: 185 passed, 4 failed (2 assertion failures, 2 serialization errors),
0 skipped and no missing mandatory cases. E/D did not run; overall local acceptance remains FAIL.

Two HTTP tests failed before domain authorization/validation: the Boot 4 Jackson 3 converter attempted
to construct the Jackson 2 JsonNode parameter of TurnRequest's delegating creator and returned 500.
Repair uses a transport-neutral Map<String,Object> creator, with explicit string-type, allowlisted-field
and intent-enum validation. It preserves the typed TurnRequest, numeric/unknown-field rejection and
bounded duplicate/trailing-token filter; no global mapper or error handling was relaxed.

Both evaluation tests failed because their independent Jackson 2 mapper could not serialize Instant.
Repair registers an explicit ISO-string Instant serializer, following existing verification repository
conventions. It adds no dependency and does not disable unsupported-type errors. Evaluation now also
asserts the exact ISO timestamp; real HTTP tests assert textual parseable timestamps and cover additional
boolean/null/object/array/missing content and null/unknown intent rejection. Existing assertions remain.

The agent inspected owner evidence and authored repairs only. Agent tests/builds/lint/migrations/
evaluations: NONE. Repairs await owner acceptance (PENDING_OWNER); E/D reconciliation is outstanding.
Hosted CI NOT_VERIFIED, live AI/expert pedagogy NOT_EVALUATED, shared deployment/production BLOCKED.


## Successful owner acceptance — 2026-11-02, run 20261102-113306-4u8mzd

Owner evidence: .generated/sprint-6-5-owner/20261102-113306-4u8mzd/summary.json.
Branch main; HEAD 5bbc28b91057da6040b33019d5f75ad1537238dc, with the uncommitted Sprint 6.5 implementation.
All gates (prerequisites, A, B, C, E, D, cleanup) PASS; exitCode 0; LOCAL_ACCEPTANCE_PASSED.

- iOS: 176 executed, 176 passed, zero failed/skipped; all 99 required cases present.
- Backend: 189 executed, 189 passed, zero failed/skipped; all 50 required cases and mandatory suites present.
- Synthetic evaluation: 20 cases passed; legal transitions/schema validity 1.0 within this finite dataset;
  zero fixture answer-leakage, false-correctness or unverified-certainty violations. Dataset SHA-256:
  37d08b0e82764d2bca7e214715a37c20fea9b76b2ad31e98a7e2c94d78a5b468.
- Fresh V028 migration and isolated synthetic V027 compatibility accepted by owner-run PostgreSQL tests.
- Security/privacy: LOCAL_SYNTHETIC_CHECKS_PASSED. No inference calls or estimated inference charges.

This supersedes the historical PENDING_OWNER/failed-run states above for the tested local slice.
The agent ran no tests/builds/lint/migrations/evaluations and made no commit/push/deployment.
After acceptance, only documentation and manifest status were updated; runtime/test sources stayed unchanged.
Hosted CI remains NOT_VERIFIED. TUTOR provider and production rollout remain DISABLED. Live model and
expert pedagogical quality remain NOT_EVALUATED. Shared database upgrade remains BLOCKED pending
V024–V028 ordering reconciliation; production release remains BLOCKED and ADR-011 gates still apply.
No claim of mathematical assessment, general pedagogical effectiveness or live AI validation is made.
