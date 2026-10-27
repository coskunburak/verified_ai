# Sprint 5.6 — Implementation map

Baseline: clean `feature/sprint-5-2-prompt-registry-governance`, HEAD
`90ee3f492aa0c17d1c2d4533837a484f62ccd42c`. Owner executes every check.

Acceptance: **SPRINT_5.6_BACKEND_FIXTURE_ACCEPTANCE_PASS**, owner evidence dated 2026-10-27.
Gates A–F passed; full API 483 total, 481 passed, 2 gated real-Qwen skips, zero failures/errors.
Contracts, documentation, secret scan, lint and local ingestion evaluation passed. Real-model
acceptance and production rollout remain separate; the canonical worker remains disabled.

| Concern | Implementation |
| --- | --- |
| Ownership | `solving` owns immutable canonical solutions; `problem.application.solving` supplies owned current/historical source snapshots. No problem repository bypass. |
| Domain | Sealed typed expression/statement records, exact number lexemes, ordered immutable steps, source/role/version lineage. No algebraic normalization or correctness verdict. |
| Graph | Candidate IDs retained; deterministic solution-scoped step UUIDs; unique contiguous orders and only earlier dependencies. No invented rules/concepts. |
| Mapping | Revalidate persisted primary/secondary artifacts with existing candidate validator and original provider provenance. Separate solution per role/job/version. |
| Persistence | Additive V020; relational lineage/header/steps/dependencies, bounded typed JSON math. Unique exact origin/representation/normalizer; immutable updates; cascaded deletion. |
| Transactions | Durable polling of V018/V019 accepted candidate tables, like Sprint 5.5. Each bounded item uses its own short transaction; original candidate already committed. Account/session locks serialize materializers and source selection. Unique keys additionally fence races. No transaction wraps inference. |
| Recovery | Bounded scheduled/manual recovery, no startup bulk scan. DB rollback leaves candidate eligible for next poll. Terminal unsupported historical artifacts receive durable typed failures per version; a future governed version can retry without overwriting history. |
| History | Explicit owned historical lookup; current query requires exact selected source and revision. Historical normalization versions remain separately readable. Deleted source fails closed through authority/FKs. |
| Provenance | Reference role/job/parent primary/operation/ledger and original immutable provenance, rather than copying provider payload per step. Agreement remains separate evidence. |
| Consumer | Narrow named internal canonical application interface, typed domain contract. No public endpoint, OpenAPI or iOS change; unverified presentation retained. |
| Privacy | Account export includes header, claims, ordered steps, dependencies and projection failures; deletion cascades from accepted jobs. No training eligibility granted. |
| Diagnostics | Bounded outcome counters and latency; no user/content labels. Original operation ID retained for correlation. |
| Economics | No AI gateway dependency, new inference, provider cost or routing/entitlement changes. |
| Gates | A domain/AST/graph; B mapping/codec; C PostgreSQL; D fixture E2E including degraded recovery; E touched regressions/architecture; F full owner quality. No agent execution. |
| Rollback | Disable `app.solving.canonical.worker-enabled`; retain additive schema and immutable history. No down migration or inference replay. |

Implementation details, exact owner commands and dated acceptance evidence are recorded in the execution report.
