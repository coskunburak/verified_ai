# Sprint 5.4 implementation map

Status: Stages A (67 tests), B (32 tests) and C (20 schema cases, 22 Java tests, local ingestion and immutability) and D (368 backend tests) PASS_OWNER_EXECUTED; E accepted from owner make check completion/iOS success. Real-model activation remains blocked. No assistant test/build/validation execution.
Baseline: `feature/sprint-5-2-prompt-registry-governance`, `eb8aa1f4e7748b176f981a1d777df6fd620ab2ab`.
Workspace clean on entry; no branch change, commit, push, fetch, reset, dependency install or paid call.

## Prerequisites and gaps

| Dependency | Evidence | Status |
| --- | --- | --- |
| 5.1 gateway/capabilities/ledger | Existing generic AiModelGateway, registry and PostgreSQL usage records | READY |
| 5.2 artifact governance | Digest-pinned prompt/schema registries; owner accepted local gates | READY_WITH_LIMITATIONS: hosted promotion evidence external |
| 5.3 routing/budgets/reliability | Owner A–E accepted: 310 API, 20 verifier, iOS success; local ingestion evaluation PASS | READY_WITH_LIMITATIONS: production health/adapter guarantees external |
| Exact approved mathematics model | Research 72–74 is provisional, no approved deployment | BLOCKED_MODEL_DECISION |
| Existing solving module | Previously package marker only; no candidate/job contract | Foundation locally accepted, owner A–E passed |

5.3 results are historical prerequisite evidence, not proof of this revision. Initial gaps were
SOLVE activation, an exported canonical source boundary, candidate schema/safety validation,
durable solve jobs, cancellation/recovery and derived-content privacy. No real adapter is invented.

## Ownership and source authority

`problem.application.solving` is a named Modulith boundary. It locks the active account and owned
session in a short transaction, resolves `current_parse_id`, requires SUPPORTED/non-review parse,
and loads its matching canonical v1 revision. No latest-parse heuristic or client expression is
accepted. Solving consumes an immutable snapshot record, never problem JPA entities. Source is
rechecked before dispatch, before persistence and at retrieval. Historical candidate artifacts
remain immutable; a changed source is projected STALE. Source statements must be a single
canonical expression/relation; multi-part and broader parser-only structures are unsupported.

`solving` owns command, job state, candidate validation/storage/API and privacy contribution.
`ai` owns provider-neutral SOLVE input/result, route eligibility, prompt/schema bindings, adapter
execution, accounting and fallback. `billing.application.CapabilityAccessPolicy` remains the access
authority. No new subscription debit ledger or direct provider integration exists in solving.

## Activation and model decision

Both `app.solving.enabled` and `app.solving.worker-enabled` default false. Generic SOLVE route also
remains disabled. Explicit activation currently permits only local/test, exact LOCAL_FIXTURE /
local-fixture-solver-v1, LOCAL_ONLY prompt, enabled durable ledger, zero cost and no fallback.
Real, staging and production SOLVE activation fails closed with BLOCKED_MODEL_DECISION.
The fixture implementation/configuration lives in test sources; the normal application has no
solver implementation in its fixture adapter. Enabling a route without an adapter fails startup.
Other future capabilities retain their activation prohibition. PRIMARY_SOLVER is the only SOLVE
route role; independent secondary solving remains outside scope. Server task, privacy and budget
constraints still intersect the approved eligibility catalog.

## Candidate contract and trust boundary

New `services/api/src/main/resources/prompts/solver/v001.md` and `solution-candidate.schema.json` are digest-bound LOCAL_ONLY
artifacts; previous artifact bytes are unchanged. No production prompt/model approval is fabricated.
`solution-candidate-v1` carries exact canonical/parse identity, version/origin, task/type, bounded
method, 1–24 ordered public steps, backward references, typed final answer, original assumption/
constraint/restriction IDs and explicit ambiguities/unresolved issues. References preserve canonical
source content rather than duplicating or permitting model-edited premises. Full model/prompt/route
provenance is server-owned and persisted separately; IDs/timestamps/attempt identity come from jobs.

SolutionCandidateValidator implements strict JSON syntax and the v1 shape/type/allowlist contract,
then source-relative semantics. It rejects extra fields, duplicate JSON keys, trailing data, wrong
source/task, omitted/invented restriction IDs, unsafe ASTs, new domains, bad step references and
oversized output. Independent Python JSON Schema validation and Java fixture tests guard the
published contract. Neither was run by the assistant.

Limits: 65,536 UTF-8 output bytes, 24 steps, 1,000 characters per explanation, 120 AST nodes per
claim, depth 32, function depth 8, 64 numeric digits, literal integer exponent magnitude <=12.
New division denominators must be nonzero numeric literals or the entire division must already
occur in the canonical source. New domain-sensitive functions and negative powers require an
existing source AST. This is intentionally conservative; unsupported transformations are rejected,
not coerced. Final equations/inequalities require a single variable on the left. General solution
sets, multiple branches, geometry, calculus and diagrams need later safe representations.

No expressions are executed. Decimal values remain exact strings. Wrong equations and contradictory
steps can be well-formed; they stay GENERATED_UNVERIFIED. Ambiguities yield REVIEW_REQUIRED.
The public notice always states that mathematical verification has not been performed. Prose is
untrusted plain text; clients must not execute/render it as HTML/tools or treat it as a verdict.
The validator blocks explicit verification/confidence/reasoning vocabulary as defense in depth,
not as a universal natural-language truth detector. Private chain-of-thought is neither requested
nor stored; only bounded public explanation artifacts are accepted.

## Durable lifecycle and concurrency

V018 adds `solve_jobs` and `solution_candidates`, composite source ownership FK, idempotency key,
unique canonical attempt, one active job per session, queue/deadline indexes and immutable-candidate
update trigger. Queued jobs pin solver/v001, schema and route-policy version; actual model/target
is selected at dispatch and recorded by the existing ledger. Operation UUID is stable for the job.
Candidate revision is identified by job ID/attempt and canonical lineage; artifact version is v1.

Transitions: QUEUED -> RUNNING -> GENERATED_UNVERIFIED / REVIEW_REQUIRED / RETRYABLE_FAILURE /
TERMINAL_FAILURE / POLICY_BLOCKED / UNSUPPORTED / STALE / OUTCOME_UNKNOWN. QUEUED/RUNNING may become
CANCELLED. Completion uses a conditional RUNNING+deadline update in the same transaction as candidate
insertion. Account then session locking serializes same-owner requests and source transitions;
conditional claims prevent multiple workers dispatching one job. No transaction spans inference.
The service suspends any caller transaction and opens only explicit short transaction blocks.
Expected source-availability exceptions do not mark those blocks rollback-only, so blocked/stale
states can commit; unexpected database failures still roll back normally.

Same idempotency key and source returns the original job. Reusing it for another source/parent
conflicts. Another key cannot create another initial run for the same source. Explicit retry requires
the latest RETRYABLE_FAILURE, creates a new immutable job/operation and is capped at 3 total attempts
per canonical source. No automatic inference retry occurs in the worker; any gateway fallback remains
within one operation and its budget. Current local route has one target and one configured attempt.

Queue deadline is 60 seconds from submission; provider allowance is at most 30 seconds and never
exceeds remaining job deadline. Recovery closes expired queued jobs terminally and expired running
jobs as OUTCOME_UNKNOWN. Timeout, unknown provider outcome and ledger failure are not automatically
retryable. A provider success followed by database failure leaves RUNNING until recovery; its ledger
record and spend commitments survive and the job is not replayed. No exactly-once remote billing
claim is made. Cancel/deletion/source changes fence later persistence, not remote charge cancellation.
After process restart, enabled worker recovery closes expired jobs before selecting more queued work.
Disabling the worker pauses processing/recovery; re-enable under the same reviewed configuration to
resume. Failed/unknown operations require inspection, never blind replay.

## API

Authenticated `/api/v1/problem-sessions/{sessionId}/solves` POST requires bounded Idempotency-Key;
GET `/{jobId}` reads owned state/content; POST `/{jobId}/retry` creates only an eligible new attempt;
POST `/{jobId}/cancel` fences late writes. POST returns 202, read/cancel 200. OpenAPI describes all four.
No request body accepts model, expression, tier, canonical ID or verification status. The service
resolves these facts. Cross-owner access returns 404; inactive account 403; state/source/activation
conflicts 409 with stable codes. Standard Problem Details supplies correlation/trace information.
Existing problem history and iOS contracts remain unchanged; no new tutoring UI is introduced.

## Cost, metrics and privacy

Gateway remains the single AI execution/usage boundary. SOLVE request cap is zero until an approved
model rollout changes reviewed configuration/code. Existing per-request/session/user/provider/global
commitments, deadlines and provider guarantees remain mandatory for future paid execution. Usage
survives rejected, cancelled or stale candidates via operation linkage. Unknown usage is not invented
as confirmed zero cost. Local fixture zero cost is explicit and carries no real model evidence.

Metrics: outcomes/latency by bounded state, rejection kind SCHEMA/SEMANTIC/PROVIDER, estimated cost by
state and cost-unavailable counter. Gateway supplies attempts, fallback, usage and token telemetry;
per-session economics is queried from the existing ledger, never a high-cardinality metric label.
No cost-per-verified-solution measurement exists. Worker failures increment a counter without raw
student/provider content in logs. Growing QUEUED/RUNNING/OUTCOME_UNKNOWN or worker failures require
operator investigation before retries; inspect job ID and linked operation/ledger, not raw prompts.

Candidates are Sensitive DERIVED_LEARNING / account-linked personal data, retained as account problem
history while needed and deleted with the account/source lifecycle. Rejected/raw provider payloads
are not persisted. Export includes normalized candidates, source lineage, state and operation ID;
no provider response internals or idempotency key. Solving lifecycle contributor deletes jobs/candidates;
source deletion also cascades. Existing AI contributor separately anonymizes retained operational spend.
No content is training eligible by default. No credentials, external URLs or production data fixtures added.

## Tests and rollback

Prepared tests cover validator safety/trust, real artifact route binding, test adapter contract,
source ownership, idempotency/concurrency, late source changes, cancellation, recovery, manual retry
cap, persistence failure after provider success, export/deletion and authenticated API behavior.
Additional regressions cover missing/review-required source during dispatch/completion and suspending
an existing caller transaction across inference.
Synthetic contract fixtures cover correct, incorrect, ambiguous and unsupported cases. See execution
report for exact commands, owner A–E evidence and blocked real-model activation. No test/build/lint/validator was run by the assistant.

Rollback disables SOLVE/service/worker and preserves V018 tables, history, operation IDs and ledger.
Do not drop migration data, edit old artifacts or replay uncertain paid requests. Re-enabling any
real model requires new approval/health/transport bounds/privacy/license/evaluation evidence.

## Future handoff

5.5 consumes the same canonical snapshot without primary hidden reasoning; fallback is not independent
corroboration. 5.6 may evolve candidate v1 into richer Solution/Step semantics through additive versions.
5.7–5.10 validate typed claims independently. Only 5.11 VerificationPolicy may assign VERIFIED. 5.12
extends real-model evaluation, protected holdouts and measured cost/latency release gates.
