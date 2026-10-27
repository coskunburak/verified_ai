# Sprint 5.5 — Implementation map

Baseline: clean `feature/sprint-5-2-prompt-registry-governance`, HEAD
`62b2f7f3e31954de612773631d4d9d8e3d0468a8`; supplied main checkpoint differs.
Owner alone executes builds, tests, validation and inference. After A–F acceptance, the owner
authorized commit/push on the existing feature branch; main merge/deployment remain outside scope.

Current acceptance: **SPRINT_5.5_BACKEND_FIXTURE_ACCEPTANCE_PASS**. Owner Gates A–F passed
for backend, contracts, architecture, repository quality and local fixtures. Full API: 434 total,
432 passed, 2 gated real-model skips, zero failures/errors. Detailed dated evidence is in
[the execution report](SPRINT_5.5_EXECUTION_REPORT.md). Gate G remains blocked.

| Concern | Implementation boundary |
| --- | --- |
| Ownership/integration | Existing `solving` module; durable polling of completed primary candidates, no volatile callback or primary API change. |
| Conditional policy | Versioned deterministic rules: unsupported/stale/missing evidence, primary ambiguity/issues, bounded stable QC hash; no invented difficulty/confidence/verification coverage. |
| Blind boundary | Dedicated builder accepts only `SolveSource`; canonical fields allowlisted, source validated, primary artifacts never accepted by execution request builder. |
| Gateway | Reuse SOLVE + SECONDARY_SOLVER with dedicated prompt/route; existing provider, ledger, budget, deadline and health machinery. No fallback/arbitration. |
| Independence | Separate route/model identity; only explicitly enabled test fixture approved here. Synthetic separation is not model-level independence. All real secondary models remain blocked. |
| Candidate contract | Reuse `solution-candidate-v1` and source-relative validator; immutable `secondary-solver/v001` registry artifact and offline fixture. |
| Lifecycle | V019 (next unused): secondary jobs, unique primary/policy identity, source ownership FK, conditional claim/finish, immutable candidates/evidence. One dispatch maximum; restart closes expired running as OUTCOME_UNKNOWN, never replays. |
| Agreement | Versioned deterministic exact numeric/rational comparison, structural agreement, assumptions/restrictions, step metadata only. Unknown symbolic equivalence remains indeterminate. No correctness verdict. |
| Privacy/security | Owner/source checked at decision, dispatch and completion; entitlement at decision/dispatch; account deletion cascades and export includes internal evidence. No raw logging/client reasoning projection. |
| Economics | Default disabled/zero; separate operation/route attribution; shared original operation/session/user/provider/global ledger limits retained. Real secondary budget unavailable until explicit approval implementation. |
| Tests | Policy, blind boundary, comparison, model eligibility, prompt integrity; PostgreSQL lifecycle, concurrency, recovery, ownership/deletion and fixture gateway/ledger integration. Owner-passed under A–F. |
| Rollout | Owner focused gates, then regression; worker/fixture flags off by default. Disable dispatch; preserve evidence/migration, reconcile unknown charges. No destructive down migration. |
| Consumers | 5.6 planner reads owned lineage-bound evidence; 5.7+ proves mathematics; 5.11 arbitration remains separate; iOS keeps GENERATED_UNVERIFIED. |

Backend/fixture acceptance is owner-confirmed; real-model quality, statistical independence and
release approval are not established by fixtures. Production secondary dispatch and budgets remain
disabled/zero; iOS/verifier results retain their prior historical status without new run evidence.
