# Sprint 6.6 — execution report

## Current delivery status — 2026-11-02

The bounded deterministic implementation, defect repair and owner-requested targeted evidence
reconciliation are complete. Evidence status: `TARGETED_RECONCILIATION_PASSED`.
This is the current status; historical failed attempts below are retained as history.

| Dimension | Current recorded result |
|---|---|
| Implementation scope | Complete for the bounded deterministic hint slice described below |
| Latest owner reconciliation | `20261102-214452-cv4eye9f`, inputs/E/D PASS, exit 0 |
| iOS | 185 cases passed in prior full run |
| Backend | 217 prior passing cases + 1 successful focused repair; 218 cases covered across runs |
| Unresolved cases in reconciled evidence | None |
| Synthetic policy evidence | 20 Socratic + 18 progressive-hint cases reconciled |
| V029 local migration evidence | Fresh and isolated V028-forward cases passed in prior run; reconciled |
| Full current-source local acceptance | `PENDING_FULL_CURRENT_SOURCE_RUN` |
| Hosted CI | `NOT_VERIFIED` |
| Production release | `NOT_AUTHORIZED`; rollout disabled |
| Agent-executed tests/builds/linters/migrations/evaluations | `NONE` |
| Commit/push/merge/deployment | None performed by the agent |

## Baseline and ownership

Branch `main`, baseline HEAD `c9f1b5837ed1584dded8afeafe91fe07c1acf118`; initial worktree clean.
Sprint 6.5 policy, persistence, contracts and owner acceptance existed locally. Its accepted run
`20261102-113306-4u8mzd` is prerequisite evidence, not Sprint 6.6 acceptance.
No unrelated changes were discarded. TutorSessionService, TutorStore and existing iOS Tutor files
were extended; no parallel pedagogical state machine or new Swift target registration was introduced.
See `sprints/phase06_solution_experience_and_tutoring/SPRINT_6.6_FILE_MANIFEST.md` for exact paths.

## Implemented behavior and explicit scope

H1 conceptual cue → H2 method/rule → H3 first actionable step is enforced server-side.
Applicable sources are canonical-v1 single-variable `a*x+b=c`, integer a=2..99 and b/c=1..999,
without additional constraints, assumptions or restrictions. UNKNOWN domain remains UNKNOWN.
Source shape selects a reviewed family, not a computed solution or proof. Unsupported sources return
unavailable. EN/TR exact publication templates contain no computed intermediate/final answer, source
prose or interpolated learner instructions. JSON Schema alone is not treated as semantic validation.

Hints share existing Socratic progress and interaction/capacity limits. Each accepted hint moves to
OFFER_MINIMAL_SUPPORT, preserves unproductiveCount and records its original policy/schema/cost versions.
It does not judge correctness, grant ADVANCE_ONE_STEP or count as unaided success. Closed/stale/terminal
sessions reject new hints. Same-key/same-level replay returns the original committed artifact, including
after close/stale/disable; changed payload or cross-operation key reuse conflicts. Timestamp precision
matches PostgreSQL microseconds so the first response and durable replay agree.

Explicit reveal has its own confirmation and operation. No tutor-authorized verified-reference reader
exists in this slice: it returns TUTOR_REVEAL_UNAVAILABLE, with no disclosure, usage or debit.
Successful answer reveal, broader mathematical coverage and live AI generation are not implemented
capabilities. Completing this bounded slice does not claim the full production feature is complete.

Assistance is versioned ASSISTANCE_ONLY evidence (CONCEPTUAL/METHOD/FIRST_STEP). No mastery score or
confidence writes/penalties occur. Opening Tutor or using accessibility creates no hint evidence.
Inference calls/tokens/cost/retries are zero; no fabricated provider measurement or new TUTOR route.
Commercial classification is NO_DEBIT; no subscription charge or new quota was invented.

## Persistence, API, privacy and iOS

V029 adds tutor_hints with composite owner/session FK, unique level/sequence/key identities and
schema/policy/assistance/training checks. Account → problem locks serialize hints, turns, close, source
edits and privacy. Publication/storage is one local transaction; no provider call holds a lock.
V027/V028 and prior released schemas remain unchanged. Historical source identity stays in the parent
session and is never rebound. Export includes all bounded hints and counts their bytes toward the
existing fail-closed 8 MB ceiling. Source/session/account deletion cascades; trainingEligible=false.

GET/POST hints and POST reveal extend the existing Tutor hierarchy. Strict types, exact fields,
duplicate rejection, 16 KB body bound, owner checks, 120/account/minute limit and correlation remain.
Hint/reveal semantic validation runs explicitly after transport Map decoding, retaining typed command
records and contractual HTTP 422 through the existing domain-error handler. Shared handler and legacy
endpoint meanings are unchanged. New SQL operations have a five-second timeout.

iOS renders server-selected nextLevel/availability, H1–H3 history, exhaustion, unavailable/stale/closed
states, separate reveal confirmation, offline/error/retry and protected pending recovery with stable
identity. Drafts survive hint/reveal operations; account/cancellation guards prevent stale publication.
EN/TR strings, VoiceOver focus/announcements and 44-point controls are included. Synthetic transport
remains DEBUG-only and explicitly labelled; it is not backend or paid-model evidence.

## Owner execution ledger

| Owner execution | Observed result | Final disposition |
|---|---|---|
| `20261102-184805-H4fyyt` full runner | A/B passed; iOS 185 passed; backend 129 passed + 89 initializer errors; E/D NOT_RUN | Historical FAIL; Ryuk connection reset prevented PostgreSQL bootstrap. Did not recur later. |
| `20261102-211111-8b8qag` full runner | A/B passed; iOS 185 passed; backend 217 passed + 1 assertion failure; no skips; E/D NOT_RUN | Historical FAIL; invalid hint request returned 400 instead of 422. |
| Focused retest, 2026-11-02 21:39:51 Europe/Istanbul | Repaired HTTP test: 1 passed, zero failures/errors/skips; BUILD SUCCESS, 15.479 seconds | Repair verified by owner execution and matching Surefire XML. |
| `20261102-214452-cv4eye9f` E/D-only reconciliation | inputs/E/D PASS, exit 0; no unresolved cases; 20+18 synthetic cases reconciled | `TARGETED_RECONCILIATION_PASSED`; targeted cross-run scope only. |

The runner now classifies a proven shared Ryuk initializer failure as BLOCKED while preserving error
counts/Maven exit. It never disables Ryuk or skips required suites. The HTTP repair moves semantic
validation outside Jackson creators to prevent wrapping it into a generic 400. Stronger tests retain
422 assertions and cover wrong types, forged/duplicate fields, safe errors and no mutation on rejection.

## Evidence and reproducibility

Primary evidence: `.generated/sprint-6-6-reconciliation/20261102-214452-cv4eye9f/summary.json`.
Adjacent artifacts: E.json, E.log, D.log and evidence/ copies of compact input reports and focused XML.
Latest reconciliation pointer: `.generated/sprint-6-6-reconciliation/latest/summary.json`.
Prior full-run artifacts remain under `.generated/sprint-6-6-owner/20261102-211111-8b8qag/`, including
summary.json, ios-summary.json, backend-summary.json, VerifiedAI.xcresult, maven-reports/,
synthetic-evaluation.json, hint-evaluation.json and migration/V029__create_progressive_tutor_hints.sql.
Original FAIL reports are preserved, not relabelled as passing.

The owner requested targeted reruns to avoid repeating long suites. E/D validates mandatory case
coverage, dataset digests, case outcomes, migration bytes and newer focused repair evidence.
The reconciliation report records repair-source and input digests. Its scope is
PRIOR_RUN_PLUS_OWNER_FOCUSED_REPAIR, singleCurrentSourceRun=false. The original full run has no
source-content snapshot, so this report cannot establish LOCAL_ACCEPTANCE_PASSED on one current tree.
No repeated long suite is requested solely for the now-verified HTTP repair.

Full runner: scripts/quality/sprint_6_6_owner_acceptance.sh.
E/D-only runner: scripts/quality/sprint_6_6_reconcile_evidence.sh (Python implementation beside it).
Both are owner-operated; the agent has only authored/reviewed source and read existing evidence.

## Publication preparation and remaining release gates

Documentation is reconciled for owner review before Git publication. Runtime, tests, fixtures, schemas,
migrations and runners have not changed after the successful owner reconciliation; subsequent edits
are documentation only. File manifest includes all created/modified files; documentation inventory
metadata is maintained. No Git index, branch, commit or remote mutation is part of this handoff.
Read-only publication preparation: git diff --check is clean; the repaired controller and HTTP test
source digests still match the successful owner reconciliation. No tests/builds/linters/documentation
validators were rerun for this Markdown-only preparation; prior execution evidence retains its scope.

Both tutoring flags default false; rollback disables new work and retains history/replay/tables.
Shared upgrades remain BLOCKED by reserved V024–V026 ordering; never enable out-of-order or rewrite
Flyway history. Disposable fresh/synthetic upgrade evidence does not authorize shared-database rollout.
Approved verified-answer reader, broader applicability, live/expert teaching quality, physical-device
VoiceOver/Dynamic Type/background review, hosted CI, staging/load evidence, ADR-011/TestFlight gates
and production approval remain outstanding. Source publication and production authorization are distinct.
