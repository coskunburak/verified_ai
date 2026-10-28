# Sprint 5.7 — Implementation map

Baseline: `codex/sprint-5-7-verification-planner`, main
`8e5f9bef9ab1cfc5ca77b0f87140d78da363609e`, clean working tree.
PR #30 merged with owner authorization after iOS run 40661340535 and all PR checks
passed on `a64ceb61635aba2fb9404f8b184845912ad7c43a`.
The owner again executes tests for this sprint; no test result is assumed.

| Concern | Planned implementation |
| --- | --- |
| Owner | Existing `verification` module; named planning application and model interfaces. |
| Files | `domain/model/{VerificationPlan,VerificationMethod,PlanningSource}.java`; `domain/policy/{VerificationPlanner,VerificationCapabilities}.java`; `application/{VerificationPlanning,VerificationPlanningService,VerificationSourceReader}.java`. |
| Authority | `CanonicalSolutions` owner-scoped reads and `SolveSourceAccess`; reuse the named canonical math codec/model. No unrelated repository access. |
| Transactions | Finish canonical reads before opening the short source-lock transaction. Recheck owner, selected source and every lineage field under that lock. Never nest a canonical REQUIRES_NEW read inside a held owner lock. |
| Secondary | Require a current primary canonical parent with matching job, source, owner, session and revisions. Missing parent is a blocked prerequisite. |
| Input | Decode bounded canonical JSON to immutable typed AST/variables/restrictions; reject unsupported versions, ambiguity, undeclared symbols and malformed shape. No text evaluation or inferred assumptions. |
| Policy | `verification-planner-v1`: ownership/source/lineage, representation, review, structure/domain prerequisites, method requirements, capabilities, immutable decision. |
| Math | Arithmetic evaluation; algebraic equivalence; equation substitution AND completeness; domain check where needed. Inequalities remain explicitly unsupported. Unknown/noninteger powers and unsupported representations fail closed. |
| Domain restrictions | Match typed nonzero/nonnegative/positive restrictions against the actual denominator or function argument. Presence of an unrelated restriction is insufficient. All source references and candidate step domains are retained/checked. |
| Capabilities | Existing equivalence endpoint accepts strings; typed validate-input only validates input. Symbolic equivalence is blocked by the missing approved canonical execution adapter. Other catalog methods are planned, not executable. No method is currently advertised as executable for canonical plans. |
| Output | Stable identity from exact source/solution lineage and policy/capability versions; ordered method IDs/versions, purposes, required inputs, availability, bounded reasons. No evidence, confidence or verification verdict. |
| Persistence | None: deterministic ephemeral plans reference immutable existing records. No migration, new student store or fabricated VerificationRun. Reauthorize/recheck source before future execution. |
| Observability | Bounded requested/completed/rejected/unavailable counters (including unsupported state/reasons), selected method count and latency. No user IDs or math in labels/logs. Zero AI calls. |
| Tests | Pure selection/adversarial math, decoder boundaries, service order/lineage/metrics, PostgreSQL ownership/source changes, capability contracts and architecture. Existing canonical and secondary regressions retained. |
| Delivery | No public endpoint, iOS change, worker, provider or verifier network call. Rollout by internal consumer adoption only; rollback by stopping calls. |
| Sprint 5.8 | Implement approved typed execution adapters, exact arithmetic/equation checks and evidence contracts separately; version capability changes. Domain/completeness requirements cannot be discarded to claim success. |

Source-of-truth review: sprint 5.7 mission and gates, sprint 5.6 map/report/contracts,
master index, domain invariants, engineering standards/DoD/file hierarchy, verification
engine, strategy/economics sections and ADR-003–007. No model, prompt, schema or training
eligibility changes are planned. Required acceptance commands and actual results belong
in the execution report; implementation is not sprint acceptance.
