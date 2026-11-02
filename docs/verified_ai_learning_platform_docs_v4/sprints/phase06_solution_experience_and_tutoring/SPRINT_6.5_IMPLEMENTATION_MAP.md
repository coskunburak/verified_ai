# Sprint 6.5 — implementation map

Status: `LOCAL_ACCEPTANCE_PASSED` (owner run `20261102-113306-4u8mzd`).
176 iOS tests, 189 backend tests and 20 synthetic policy cases passed; zero failures/skips.
Hosted CI `NOT_VERIFIED`; live AI/expert pedagogy `NOT_EVALUATED`; production `BLOCKED`.

## Baseline and ownership

Inspected clean `main`, HEAD `5bbc28b91057da6040b33019d5f75ad1537238dc` matches the requested baseline.
Sandbox denied creation of `codex/sprint-6-5-socratic-tutor`; work stays uncommitted on main.
No pre-existing modifications, secrets, network inference or owner checks were accessed/executed.
The owner-directed Track B exception does not waive ADR-011 commercial release gates.

Dependency map: TutorSessionController → TutorSessionService → SocraticPolicy / TutorStore;
existing problem `tutor-source` and profile `tutor-context` boundaries remain authoritative.
iOS TutorAPI → TutorViewModel → existing TutorView; TutorPendingStore retains operation kind.
No new cross-module database reads or verification authority.

## Policy and evidence

`socratic-policy-v1` owns typed phase, learner intent, action, outcome and uncertainty.
CLARIFY_GOAL → ELICIT_CONCEPT → EVALUATE_LEARNER_STEP → OFFER_MINIMAL_SUPPORT;
support can return to evaluation. ADVANCE_ONE_STEP requires trusted step evidence, never text plausibility.
No trusted step-evidence boundary is currently available, so the application always supplies UNKNOWN.
SUMMARIZE_CONCEPT → COMPLETED is a learner reflection exercise, never mathematical completion/mastery.
Dispute → NEEDS_REVIEW; three unproductive interactions → PAUSED; twelve turns maximum.
Terminal phases require a new explicitly created session; closed/stale sessions reject new turns.
A minimal conceptual nudge has no hint levels, prices or mastery effects (Sprint 6.6 remains separate).

## API and persistence compatibility

Existing six endpoints and their DTOs remain unchanged. `/messages` remains LEARNER-only, including
its consecutive sequence and `messageCount`. New POST/GET `/{id}/turns` use `tutor-turn-v1`.
POST accepts exactly `content` and optional non-authoritative `intent`; defaults UNKNOWN.
GET pages by exclusive learner sequence, bounded to 50. Each turn records its parent learner message,
TUTOR reply identity/content, before/after state, action, outcome, versions, language and safe provenance.
V028 adds `tutor_turns` with composite owner/session/message foreign key and unique parent/reply IDs.
V027 and all previous migrations stay byte-identical; no learner history is rewritten.
`messageCount` still counts legacy learner records. Capacity additionally counts tutor replies:
new turns need two free slots within 100 combined records; legacy append needs one. Turn limit is 12.

## Transactions, recovery and observability

Account → problem lock serializes close, source edits, privacy and all tutor mutations.
Learner append, reply and phase are committed atomically in one bounded local transaction.
New turn operations have a five-second transaction timeout; iOS turn requests use fifteen seconds.
No external inference or external processing state exists; a crash rolls back all or commits all.
Client pending means acknowledgement is unknown, not a durable provider job. Retry uses the same key,
content and intent; mismatches or reuse across legacy append/turn operations fail explicitly.
Committed retries remain readable after closure, staleness and feature disable; they generate nothing.
At most one committed reply per learner message is enforced by PostgreSQL.
Metrics use bounded outcome/phase/action tags only; existing request latency and correlation apply.

## AI, costs and rollout

This slice uses reviewed deterministic question templates, explicitly labelled DETERMINISTIC_POLICY.
It does not generate AI text, consume source solution text, interpolate learner content, or dispatch TUTOR.
Existing provider-neutral gateway, TUTOR disabled descriptor/route, prompt registry, spend guard and
fixture adapter remain unchanged. No speculative prompt/provider route is advertised as usable.
Model schema/timeout quality is NOT_EVALUATED; gateway-disabled regression protects this boundary.
Calls/turn = 0, model context = 0 bytes, provider retries = 0, inference cost/turn and /session = 0.
`verifiedai.tutoring.socratic-enabled` defaults false; enable only in owner local acceptance fixtures.
Rollback disables this flag, keeps legacy persistence and all history readable; never drop tables.
Live activation requires a separate approved prompt/schema/gateway route, semantic validation,
trusted evidence read boundary, expert pedagogy evaluation and external-dispatch recovery design.

## Privacy, acceptance and blockers

Turn content/state are DERIVED_LEARNING, trainingEligible=false. Source/account deletion cascades.
Complete export includes learner-visible turns and accurate phase without keys; combined content bytes
remain subject to the existing fail-closed 8 MB export bound. No hidden reasoning or raw AI context.
Owner runner: scripts/quality/sprint_6_5_owner_acceptance.sh, timestamped immutable run evidence under
.generated/sprint-6-5-owner/, latest/summary.json. Backend domain, PostgreSQL, HTTP/security,
Modulith, iOS unit/UI, schema and synthetic pedagogical gates are required; no agent execution.
Synthetic structural quality does not establish teaching effectiveness or real model quality.
Shared DB rollout is BLOCKED pending V024–V026/V027/V028 ordering, actual upgrade evidence and review.
Hosted CI NOT_VERIFIED; expert labels, live AI, staging, physical-device/VoiceOver review and production
approval remain unavailable. Production release BLOCKED.

## Owner-run UI repair

After owner run 20261102-102001-2MMjT6, TutorView explicitly dismisses composer focus on Send and
supports interactive keyboard dismissal while scrolling. TutorUITests assert keyboard disappearance
and target the identified conversation ScrollView when exposing controls. Draft/pending/networking
semantics and existing assertions stay intact. Repair awaits owner execution; see the execution report.

After the second owner run (20261102-103815-H0qYkd), confirmed session closure is presented in the
bottom safe-area composer section so it remains visible independently of transcript scroll position.
The close UI regression additionally checks notice visibility; server-authoritative close semantics
and pending-operation protection are unchanged. This repair awaits the next owner acceptance run.

The third owner-run hierarchy confirmed transcript controls overlapping the composer. TutorView now
uses sibling ScrollView/composer regions inside VStack instead of safeAreaInset; the closed notice
remains in the composer. The close UI regression checks viewport separation and closure after refresh.
This supersedes the earlier scroll-position-only diagnosis; repair remains pending owner acceptance.

Owner run 20261102-111544-xV17KD accepted all 176 iOS tests. Backend repairs keep Jackson generations
explicit: the typed turn request creator accepts only JDK Map/String values at the Boot 4 HTTP boundary;
internal bounded JSON/schema validation remains Jackson 2. Synthetic evaluation registers an explicit
ISO-string Instant serializer. HTTP type rejection and timestamp assertions are strengthened; no global
serialization/security configuration or dependencies change. Backend/evaluation acceptance is pending.


## Accepted local evidence

Owner run 20261102-113306-4u8mzd completed prerequisites/A/B/C/E/D/cleanup with exit 0.
All mandatory suites/cases are present (99 required iOS cases, 50 required backend cases).
Fresh V028 and isolated synthetic V027 → V028 compatibility passed. This accepts the deterministic
local slice; shared database upgrades, live model/expert quality and production approval remain blocked.
Previous repair notes above describe historical pending states. Runtime source was unchanged after
this successful run; only acceptance documentation/manifest were updated. Agent checks remain NONE.
