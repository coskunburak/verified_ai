# Sprint 7.8 execution report

2026-11-11 · `IMPLEMENTED` · `STATICALLY_REVIEWED` · `LOCAL_ACCEPTANCE_PASSED` · `PRODUCTION_NOT_AUTHORIZED`

## Baseline and scope

Work started on a clean `main` at `5fd4519c4973eeccff21a2919e07d7e2ad201c23` (Sprint 7.7 completion commit). Sprint 7.8 delivers the Prerequisite Knowledge Graph Projection, Learning State APIs, and Closed-Loop Twin Remediation across the Spring Modulith backend, Python Math-Verifier, OpenAPI 3.1 & JSON Schema Draft 2020-12 contracts, and SwiftUI iOS application layers.

The sprint implements:
1. Conservative Prerequisite Propagation ($M^{\text{eff}}_{u, s_{\text{post}}} = \min(0.2500, \min M^{\text{eff}}_{u, s_{\text{pre}}})$ for cold-start successors).
2. Cold-start prerequisite score cap ($0.6500$, `PRACTICING`) on ancestor inference. Direct practice is strictly required to reach `MASTERED` ($\ge 0.8500$).
3. Prerequisite Bottleneck Detection ($M^{\text{eff}}_{u, s_{\text{pre}}} < 0.5000 \land \text{consecutiveFailures}_{u, s_{\text{post}}} \ge 2$) triggering `blockedByPrerequisiteBottleneck = true` and `AT_RISK_REMEDIATION_REQUIRED`.
4. Read-pure graph projection under `@Transactional(readOnly = true)` without lazy mutations or audit writes.
5. 60-Second Monotonic Solution Lock on generated counterfactual twin challenges (`lock_until = created_at + INTERVAL '60 seconds'`).
6. Closed-loop unassisted remediation granting $+0.1500$ credit and clearing at-risk status once reaching $\ge 0.5500$.
7. Strict zero-secret policy enforcing `training_eligible = FALSE` at database, schema, and API levels.

## Architectural and modulith boundaries

- **Module Decoupling**: `com.verifiedai.mastery`, `com.verifiedai.curriculum`, and `com.verifiedai.attempt` remain strictly decoupled through public API ports (`CurriculumGraphQueryPort`, `AttemptQueryPort`) and immutable DTO snapshots (`CurriculumDagSnapshot`, `VerifiedAttemptSnapshot`).
- **Circular Dependency Resolution**: `AttemptMasteryEvidenceSource` in `attempt` already implemented `MasteryEvidenceSource` from `mastery`. To preserve acyclic Modulith slices, `AttemptQueryPort` is declared in `com.verifiedai.mastery` (the caller's bounded context) and implemented by `AttemptQueryAdapter` in `attempt.application`.
- **BOLA & Multi-Tenancy**: Every entity, query, and constraint is composite-scoped by `(tenant_id, user_id)`. Identity is extracted exclusively from `@AuthenticationPrincipal`. Unauthorized attempts return uniform `404 Not Found` (`RESOURCE_NOT_FOUND`).

## Owner verification and delivery checklist

| Definition-of-done items | Source disposition | Execution |
|---|---|---|
| Baseline, authoritative scope, prior policy reuse | DONE | Merged at `5fd4519` |
| Mathematical behavior, propagation, bottleneck detection | DONE | LOCAL_ACCEPTANCE_PASSED |
| 60-second lock, unassisted remediation, Flyway V040 | DONE | LOCAL_ACCEPTANCE_PASSED |
| REST API endpoints, OpenAPI 3.1 & Draft 2020-12 schemas | DONE | LOCAL_ACCEPTANCE_PASSED |
| Python HMAC SymPy isomorphic twin synthesis & tests | DONE | LOCAL_ACCEPTANCE_PASSED |
| iOS Screen 5 ("Prove It Solo") & Screen 7 (Mastery Graph) | DONE | LOCAL_ACCEPTANCE_PASSED |
| Swift tests & 14 EN/TR Localizable.xcstrings string units | DONE | LOCAL_ACCEPTANCE_PASSED |
| Acceptance runner `scripts/quality/sprint_7_8_owner_acceptance.sh` | DONE | LOCAL_ACCEPTANCE_PASSED |
| Documentation, file manifest, master index sync | DONE | Static source review |

## Evidence summary

| Evidence | Owner result |
|---|---|
| Maven / Spring Modulith | 10 tests passed, zero failures, zero errors (`ModulithArchitectureTest`, `KnowledgeGraphProjectionServiceTest`, `CounterfactualTwinChallengeServiceTest`, `Sprint78KnowledgeGraphAndTwinIT`) |
| Python Math-Verifier | Deterministic HMAC-seeded SymPy twin synthesis test passed (`test_twin_synthesis_v78.py`) |
| Contracts & Schemas | `validate_json_schemas.py` passed with positive fixtures and 6 negative mutation invariants |
| iOS Client & Localization | 7 Swift source/test files created and 14 EN/TR localized strings verified in `Localizable.xcstrings` |
| Acceptance Gate Runner | `bash scripts/quality/sprint_7_8_owner_acceptance.sh --full` passed all 7 stages with exit code `0` |
