# Sprint 6.6 — implementation map

Evidence status: TARGETED_RECONCILIATION_PASSED (owner run 20261102-214452-cv4eye9f).
Full current-source acceptance: PENDING_FULL_CURRENT_SOURCE_RUN; execution remains owner-only.
Baseline: clean main, c9f1b5837ed1584dded8afeafe91fe07c1acf118. Sprint 6.5 exists and its
owner run 20261102-113306-4u8mzd is recorded as locally accepted. No pre-existing edits.

## Ownership and contracts

Extend TutorSessionService, TutorStore, SocraticPolicy and the existing iOS Tutor feature.
New HintPolicy/TutorHint model the ladder, not another pedagogical state machine.
Problem's existing tutor-source interface exposes only a reviewed structural hint family;
no raw math, reference answer, learner text or prompt reaches hint publication.
Support is deliberately bounded to canonical v1 single-variable positive integer
`a*x+b=c` equations with a>1 and b>0. Other forms return unavailable, never fabricated guidance.
H1 -> H2 -> H3 requires the immediately preceding durable level. Each accepted hint moves
existing Socratic progress to OFFER_MINIMAL_SUPPORT and consumes one of its twelve interactions.
It does not increment unproductiveCount, judge correctness or grant ADVANCE_ONE_STEP.
Explicit reveal is a distinct confirmed command. The repository has no tutor-authorized verified
answer read contract: reveal fails closed with TUTOR_REVEAL_UNAVAILABLE. H3 never reveals.

## Persistence and API

V029 adds tutor_hints with owner/session/source-parent FK, unique session/level and key,
policy/schema/cost versions, typed assistance, immutable text and pedagogical snapshots.
Account -> problem lock serializes hints, ordinary turns, close, source edits and deletion.
A committed retry returns the identical artifact even after close/staleness/disable; changed
level or cross-operation key conflicts. No provider job or network call is held under locks.
GET/POST /hints and POST /reveal extend the existing hierarchy; existing DTOs are unchanged.
GET returns history and server-authoritative next level/availability, including stopped state.

## Cost, privacy and rollout

CONCEPTUAL/METHOD/FIRST_STEP are assistance evidence, never independent success; future mastery
policy eligibility is ASSISTANCE_ONLY, with no score writes. Opening Tutor/accessibility is free
of pedagogical cost. No billing/quota contract exists for this deterministic feature: NO_DEBIT.
Inference calls/tokens/cost/retries are zero; no fabricated provider measurements or new gateway route.
Export includes all hints and accounts for bytes; session/source/account cascades erase them.
trainingEligible=false. Metrics contain only bounded operation/outcome/level codes.
Flags socratic-enabled and hints-enabled default false; rollback disables new work, retains history.
V024–V026 remain reserved, V027/V028 immutable. Shared DB upgrades/production remain blocked.

## Acceptance matrix

Domain: transitions, versions, bilingual publication allowlist, assistance, reveal denial.
PostgreSQL: V029 fresh/forward migration, replay/restart/race, atomic failure, terminal/stale,
source scope, unique/check/FK constraints, complete export/deletion and cross-operation keys.
HTTP: auth/BOLA, exact body, duplicate fields, level tampering, size/rate/correlation and history.
iOS: Codable, server next action, protected pending recovery, duplicate taps, offline/account
cancellation, confirmed reveal denial, EN/TR accessible UI and regression suites.
Versioned synthetic fixture evaluation executes the real policy; no live-model quality claims.
Owner runner retains 6.5 gates and adds hint suites/evaluation/migration reconciliation.
No agent tests/builds/linters/migrations/evaluations, Git commits or remote mutations.

## Completed owner validation and publication handoff

The bounded slice, repair and targeted E/D reconciliation are complete. Evidence status is
TARGETED_RECONCILIATION_PASSED in owner run 20261102-214452-cv4eye9f (inputs/E/D PASS, exit 0).
Prior run 20261102-211111-8b8qag passed 185 iOS and 217 backend cases; the single HTTP 400/422
failure was repaired and passed its focused owner retest at 21:39:51 Europe/Istanbul.
Cross-run coverage is 218 backend cases with none unresolved. Synthetic policy evidence reconciles
20 Socratic and 18 hint cases. Fresh V029 and isolated V028-forward evidence passed in the prior run.

Full current-source acceptance remains PENDING_FULL_CURRENT_SOURCE_RUN because the original run
has no source-content snapshot and the owner selected targeted reruns. This is not a single-tree
LOCAL_ACCEPTANCE_PASSED result. Hosted CI NOT_VERIFIED; shared upgrades BLOCKED; production
NOT_AUTHORIZED. Details and the historical run ledger are in the execution report.

Full runner: scripts/quality/sprint_6_6_owner_acceptance.sh.
Targeted E/D runner: scripts/quality/sprint_6_6_reconcile_evidence.sh and its Python implementation.
Evidence root: .generated/sprint-6-6-reconciliation/20261102-214452-cv4eye9f/summary.json.
Sources/fixtures/runners remain unchanged after reconciliation. Reporting and manifest preparation
are complete for owner Git review; agent tests/builds/linters/migrations/evaluations and Git publication: NONE.
