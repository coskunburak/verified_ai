# Sprint 5.3 implementation map

Status: VERIFIED_PASS — owner local acceptance A–E; production rollout has documented limitations. No test/build/evaluation execution by assistant.

## Baseline and prerequisite

Branch `feature/sprint-5-2-prompt-registry-governance`, HEAD
`65fcbea601b8587df5093cafeb464decea99267b`. Existing uncommitted Sprint 5.2 work is
preserved. Prerequisite READY_WITH_DOCUMENTED_LIMITATIONS: prompt/schema hashes,
release approvals, semantic binding and rollback contracts exist; owner A–D local
acceptance is recorded in Sprint 5.2 report. Hosted CI/branch protection remain external.

## Consulted sources

Sprint 5.3 specification, Phase 05 scope, ai/25, 26, 29, 57, 60, domain/10,
quality/41, 42, 56, Master Index and ADR-003/005/006/007.

## Design and ownership

AI application owns deterministic eligibility and bounded recovery. Configuration
owns approved exact provider/model identities and quality gates. Caller metadata
cannot approve models or upgrade entitlement. Billing remains entitlement authority.
Unknown server entitlement falls back to FREE; unknown remote health blocks routing.
Problem-owned difficulty/task/confidence can restrict eligibility, never grant a tier.
Future capabilities remain disabled; no SOLVE implementation or real provider activation.

Existing consolidated ledger remains usage/provenance authority. Durable PostgreSQL
budget commitments are separate control state, linked to the same operation, not a
second usage ledger. Reserve ceilings before dispatch with deterministic lock ordering;
all instances share the same accounts. Unknown billing retains the full ceiling.
UTC windows are pinned at reservation; no automatic timeout refund. Default paid
allowances are zero pending business approval. Provider-enforceable upper bounds are
required before paid adapters can execute; estimates alone cannot guarantee bills.

## Workstream gaps at baseline

Router ignored context; gateway checked only final-attempt cost and initial deadline;
retry flag alone allowed fallback; usage reserve created only a STARTED ledger row.
No aggregate spending authority or fresh provider-health evidence existed.

## Rollout and remaining gates

Owner local tests, migration/concurrency coverage and local ingestion evaluation passed.
Model/price approvals, staging, shadow/canary and hosted protections still precede promotion.
Rollback re-pins policy/configuration; never drops committed spend or historical ledger.
Research candidates are not runtime approvals. Production student data remains excluded
from training by default; cost per verified solution is unavailable before verification.

## Implemented routing contract

`AiRouteSelector` intersects approved capability/exact model, explicit task/difficulty
sets, server billing tier, privacy/retention, latency allowance and fresh CLOSED health.
Sort: attempt ceiling, provider, exact model. Equal inputs/evidence give equal order.
Missing difficulty/task is UNKNOWN, never HARD. Confidence below 0.8 or parser review
or non-LOW recognition risk returns REVIEW_REQUIRED. NaN confidence is invalid.
The 0.8 threshold is conservative selection policy v1, not calibrated accuracy.
Unknown canonical types and non-default route roles are rejected. Feature flags,
claimed entitlement and caller health strings cannot approve or upgrade routes.
Existing Phase 4 convenience calls still use UNKNOWN context; they do not manufacture
classification or difficulty before it exists. No user-facing input contract changed.

`ConfiguredAiRoutePlanner` retains legacy route templates and validates generic
capability overrides with the existing prompt registry. Exact default fixture models
have local-only eligibility; remote targets need explicit catalog entries matching
route quality version, privacy and retention. Prompt release checks run before model
eligibility. Future capabilities cannot be enabled. Selection uses the existing route
ID/policy version; release configuration must be retained to reconstruct the original
template. Ledger records selected target and eligible fallback chain. This is not a
new model-management platform. UNKNOWN measured quality/latency never becomes approval.

Billing owns FREE/PRO/PRO_PLUS via its application interface. AI reads `currentAiTier`;
client metadata is ignored. Fixtures need no paid entitlement. Unknown/inactive/expired
access cannot grant premium selection. Per-attempt tier re-evaluation can reduce
allowance after selection. Existing identity/problem authorization remains upstream;
AI routing is an internal port, not a new client endpoint.

## Budget persistence and reconciliation

V017 adds three AI-owned tables: `ai_spend_accounts` (atomic committed totals),
`ai_spend_attempts` (operation/attempt, ceiling, optional usage estimate, price/policy,
status and timestamps) and `ai_spend_allocations` (original window links). Attempt FK
references the existing unique usage operation. No student content or API secret.
Usage ledger reservation commits in REQUIRES_NEW before budget reservation/dispatch.
Budget reservations also commit independently; rollback of a caller transaction must
not erase potentially billable work. A duplicate ledger operation cannot dispatch again.

Each attempt atomically increments six accounts in sorted key order: operation,
problem session, user UTC day, user UTC month, provider UTC month and global UTC month.
All limits plus configured per-attempt and tier request caps must admit the ceiling.
Row locks and unique constraints are the cross-instance authority. Checked subtraction
avoids overflow. Missing user/session for paid work blocks; failed reservation rolls
back every account. Every retry/fallback belongs to the same operation and accounts.
Clock rollover chooses new UTC windows; late reports do not move previous commitments.

Settlement records REPORTED_ESTIMATE or UNKNOWN, retaining full ceiling. Crash leaves
RESERVED. Neither timeout nor low estimated usage authorizes a refund. Automated invoice
reconciliation/refund is NOT implemented; an audited provider invoice reconciliation
workflow is a prerequisite to releasing unused commitments. This deliberately favors
spend containment over utilization. The same accounts persist across policy/price changes;
changing a pricing version cannot reset monthly allowance or rewrite history.

The enforced invariant is admitted commitment <= configured limits across instances.
Real invoice containment additionally requires a provider-enforceable charge ceiling,
including failed calls and hidden reasoning units. No current paid adapter supplies
that guarantee; paid execution stays blocked. Adapter `enforcesCostCeiling` is a trusted
implementation contract, not a client flag. Generic fixture is provably zero cost.

Account deletion anonymizes personal account keys/user IDs with FK cascade while
retaining aggregate commitments; existing ledger anonymization removes user/session
links. No automatic account pruning: unsettled amounts must not be forgotten. Before
paid rollout approve retention and audited reconciliation. Rollback disables routes
and restores previous config; never drop budget tables or counters to reset spend.

## Deadline, recovery and telemetry

One monotonic allowance covers route/ledger overhead and all attempts, intersected
with absolute execution deadline and context/route budgets. Adapter requests contain
remaining timeout and per-attempt ceiling. No background executor is introduced:
provider adapters MUST enforce their own transport deadline; the gateway also refuses
late success and prevents another attempt. No measured p95/SLA is claimed.

Only TIMEOUT, RATE_LIMITED and PROVIDER_UNAVAILABLE can recover when adapter also marks
retryable; max attempts is 1–8, further bounded by eligible target count. Auth/schema/
configuration/privacy/budget failures are terminal. Re-check health before each attempt.
Health port uses source, timestamp and max-age (default 30s, max 5m); future, stale,
OPEN/HALF_OPEN/UNKNOWN block. Default remote source is UNKNOWN. No synthetic healthy
observations, automatic paid probes or shared in-memory global breaker. A production
telemetry-backed circuit controller remains external integration work.

Existing metrics remain; bounded attempt outcome and committed-microcost metrics added.
No user IDs or content labels. Aggregate gateway/ledger usage estimates conservatively
include ceilings for every reserved attempt; final output retains final provider usage.
Attempt rows distinguish missing usage from confirmed zero. Provider-specific estimated
cost is not an invoice. Alert on unknown commitments, bound violations, ledger failures,
blocked budget spikes, stale health and fallback growth. Freeze promotion/disable route
on privacy/quality violations; rollback never relaxes prompt, quality or cost constraints.

## Test matrix and evidence boundary

| Area | Written coverage | Status |
| --- | --- | --- |
| Selection | economy/strong, UNKNOWN difficulty, untrusted tier/flags/health, review, privacy, unsupported task, future capability, monotonic restrictions | VERIFIED_PASS — owner Group A (42 tests combined) |
| Configuration | default zero paid caps, invalid limits/health age, duplicate identities, bounded attempts, existing prompt/stage validation | VERIFIED_PASS — owner Group A (42 tests combined) |
| Gateway | existing 15 cases plus auth no-retry, cumulative fallback spend, deadline exhaustion/remaining timeout, reserve failure | VERIFIED_PASS — owner Group A (42 tests combined) |
| Persistence | concurrency across service instances, duplicate attempt, rollback, unknown usage, rollover/late report, price preservation, user/session limits, anonymization | VERIFIED_PASS — owner Group B (16 tests combined) |
| Compatibility | ledger, entitlement, Modulith, Phase 4 ingestion and local evaluation | Modulith VERIFIED_PASS in owner Group A; ledger and entitlement VERIFIED_PASS in owner Group B; Phase 4 ingestion and local evaluation VERIFIED_PASS in owner Group C |

No test, build, compile, lint, validation, evaluation, Docker or paid API run was executed
by assistant. Owner Group A passed on 2026-10-26: 42 tests, zero failures/errors/skips,
BUILD SUCCESS; gateway coverage includes 21 tests. Owner Group B passed at 11:32:37+03:00 on 2026-10-26: 16 tests,
zero failures/errors/skips, BUILD SUCCESS; entitlement coverage includes 5 tests.
Owner Group C passed on 2026-10-26: 69 Java tests, zero failures/errors/skips,
BUILD SUCCESS; local ingestion evaluation PASS (18 cases, 13 critical, no regressions).
Near-duplicate fixture text warnings remain documented in the execution report.
Owner Group D passed on 2026-10-26: prompt/schema immutability, secret-scanner
regression 7/7, documentation (278 Markdown files), contracts and secret scan.
Owner Group E passed on 2026-10-26: make check, API 310 tests (zero failures/errors/skips),
verifier 20/20 and iOS TEST SUCCEEDED at 11:40:02+03:00. Local acceptance A–E is complete.
Closure-only evidence documentation edits were not rerun; no runtime code changed afterward.
Research docs 72/73/74 are source-linked proposals and calculated scenarios, not
observed quality/cost. Owner protocol and exact commands are in the execution report.

## Sprint 5.4 handoff

SOLVE remains disabled. Before enabling: implement typed solver request/result and
semantic validation; register immutable prompt/schema and approved quality evidence;
approve exact deployment/privacy/license/pricing; implement bounded provider adapter
and trusted health producer; approve business caps; prove reservation/reconciliation;
feed only accepted canonical problems and server-owned context. Secondary solver
independence belongs to 5.5. Neither routing nor a schema-valid answer sets VERIFIED.
