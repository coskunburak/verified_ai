# Sprint 14.3 — SymPy Parametric Isomorphic Twin Engine for YKS/AYT/LGS Families

## Sprint mission

Build the deterministic, **`$0.00`-LLM-cost SymPy Parametric Isomorphic Twin Engine** (`services/math-verifier/app/verifiers/yks_isomorphic_twin_generator.py`) covering 12 high-frequency Turkish YKS (`TYT`/`AYT`), LGS, and Calculus problem families so every solved or scaffolded problem immediately yields a mathematically verified twin question with guaranteed clean roots and boundary conditions.

## Why this sprint exists

Watching or stepping through a solution does not prove learning. Competitors (Solvi, Kunduz, Photomath) either terminate the session after showing the solution or recommend loosely related static database questions that do not test the exact algebraic mechanism the student just learned. This sprint builds a formal parametric generator that mutates coefficients, exponents, and roots while using SymPy to prove that the new question has a unique, clean solution and preserves all domain invariants (e.g., $P(0) \neq 0$, non-zero denominators, exact root counts).

## Preconditions and dependencies

- `SPRINT_14.2` completed.
- Existing `V031` (`counterfactual_twin_drills`) and `learning/84_VERIFIED_TWIN_PRACTICE_AND_INDEPENDENT_LEARNING_ENGINE.md` baseline reviewed.

## In scope

- Deterministic SymPy parametric generators for 12 core exam families (derived directly from our 24-question AYT Production Benchmark + AYT 2022 Polynomial benchmark):
  1. `FAM_01_POLY_FUNCTIONAL_DEGREE` — Functional polynomial equations $(x - r_2)P(kx) = k^n(x - r_1)P(x)$ with $P(0) \neq 0$.
  2. `FAM_02_ABS_VALUE_ROOT_COUNT` — Nested absolute value equations $||x - a| - b| = k$ with exact $m$-element solution sets.
  3. `FAM_03_PROPORTION_IDENTITY` — Multi-ratio algebraic identities $\frac{A}{B} = \frac{C}{D} = \frac{E}{F} = k$ invariant across free variable $c$.
  4. `FAM_04_COMPOSITE_FUNCTION_CHAIN` — Coupled compositions $(f \circ g)(x) = \alpha g(x) + \beta$ and $(g \circ h)(x) = \gamma h(x) + \delta$.
  5. `FAM_05_CAUCHY_LINEAR_FUNCTIONAL` — Additive/multiplicative functional equations $f(x+y)=f(x)+f(y)$ with rational scaling.
  6. `FAM_06_VIETA_ROOT_POWER_REDUCTION` — Quadratic root degree-reduction $x_1^2 + \alpha x_2 + \beta x_1$ via $x^2 - sx + p = 0$.
  7. `FAM_07_COMPLEX_CONJUGATE_SYSTEM` — Complex real/imaginary system $\operatorname{Re}(z_1) = \operatorname{Im}(\overline{z_2})$.
  8. `FAM_08_PARABOLA_INSCRIBED_GEOMETRY` — Parabola $y = a(x-r_1)(x-r_2)$ with inscribed axis-aligned rectangle perimeter/area.
  9. `FAM_09_TRIG_HALF_ANGLE_AND_BOUND` — Trigonometric identities $\frac{1+\sin 2\alpha}{\cos 2\alpha}$ and $A\sin x + B\cos x = C$ root-count bounds.
  10. `FAM_10_LOG_BASE_CHANGE_AND_ORDER` — Logarithmic base-change $\log_{a b}(a^m b^n)$ in terms of $k$ and integer-interval ordering.
  11. `FAM_11_GEOMETRIC_SEQ_FACTORING` — Positive-term geometric progression ratio factoring $\frac{a_{n+3}+a_n}{a_{n+1}+a_n} = r^2 - r + 1$.
  12. `FAM_12_LIMIT_INDETERMINATE_AND_TANGENT_CUBIC` — $0/0$ rational limit parameter recovery and cubic $x$-axis tangency $f(x_0)=f'(x_0)=0$.
- SymPy sanity gate (`verify_twin_candidate`) asserting unique correct option + 4 distinct plausible distractor options (`A`–`E`).

## Explicitly out of scope

- Unverified free-form LLM question generation without a SymPy proof check.
- Timed user submission and BKT mastery update (covered in `SPRINT_14.4`).

## Deliverables

1. **Semantic deliverable** — 12 family AST templates and invariant verification predicates.
2. **Implementation deliverable** — `yks_isomorphic_twin_generator.py` in `services/math-verifier` and `IsomorphicTwinGeneratorService.java` in `com.verifiedai.attempt`.
3. **Contract deliverable** — `/internal/v1/twins/generate-isomorphic` Python verifier contract and `IsomorphicTwinPayload` schema.
4. **Quality deliverable** — Property-based test suite generating 50 random seeds per family (`12 x 50 = 600` twins) asserting `100.0%` SymPy solvability and distinct `A`–`E` choices.
5. **Operational deliverable** — Generation latency and family coverage telemetry.
6. **Documentation deliverable** — Canonical alignment with `learning/84` and `learning/93`.

---

<!-- LEARNING_OS_PRODUCTION_SPEC_V1:START -->
# Learning OS Production Implementation Specification v1 (`2026-11-11`)

## 1. Sprint Metadata & Status
- **Sprint ID:** `SPRINT_14.3` (Fast-track bridge alias: `SPRINT_7.8B-Part1`)
- **Canonical Title:** SymPy Parametric Isomorphic Twin Engine for YKS/AYT/LGS Families
- **Status:** `PLANNED` (`GATED_ON_SPRINT_14_2`)
- **Primary Spec Reference:** `learning/93_ACTIVE_SCAFFOLDING_TWIN_VERIFICATION_AND_INTERACTIVE_CANVAS_SPECIFICATION.md`, `learning/84_VERIFIED_TWIN_PRACTICE_AND_INDEPENDENT_LEARNING_ENGINE.md`

## 2. Strategic Learning OS Objective & User Value
Guarantees that whenever a learner finishes a problem (e.g., the AYT 2022 Polynomial question $(x-8)P(2x) = 4(x-2)P(x)$), the platform instantaneously synthesizes a fresh, mathematically proven twin (e.g., $(x-16)P(2x) = 8(x-2)P(x)$ where $\deg(P)=3$ and $P(0) \neq 0$) with 5 valid multiple-choice options (`A`–`E`) and full step-by-step verification metadata.

## 3. Entry Gate & Preconditions
- `SPRINT_14.2` completed; `services/math-verifier` SymPy environment active.

## 4. Domain Invariants & Mathematical Rules
1. **Deterministic SymPy Proof Before Emission (`INV-TWIN-101`):** No generated twin question may be persisted or shown to a learner unless `SymPy` independently solves the generated equation from scratch and confirms:
   - The solution set is non-empty and matches `expectedAnswerLatex`.
   - All stated domain conditions (e.g., $P(0) \neq 0$, $r > 0$, $\pi/2 < \alpha < \pi$) hold strictly.
   - All 5 multiple-choice options (`A`–`E`) are pairwise distinct ($\forall i \neq j, \text{Option}_i \neq \text{Option}_j$).
2. **Non-Identical Parameter Perturbation (`INV-TWIN-102`):** The generated twin's parameter vector $\vec{\theta}_{\text{twin}}$ must differ from the parent problem's $\vec{\theta}_{\text{parent}}$ and produce a different numerical/symbolic final answer while preserving the identical solving algorithm.
3. **Sub-150ms Execution Budget (`INV-TWIN-103`):** Parametric generation + SymPy proof for any of the 12 families must complete within `150ms` P95 at `$0.00` LLM token cost.

## 5. Owning Spring Modulith Backend Modules & Classes
- `services/api/src/main/java/com/verifiedai/attempt/application/IsomorphicTwinGeneratorService.java`
- `services/api/src/main/java/com/verifiedai/attempt/domain/TwinProblemFamily.java`
- `services/api/src/main/java/com/verifiedai/attempt/infrastructure/verifier/HttpMathVerifierTwinClient.java`

## 6. Python Math Verifier Impact (`services/math-verifier/`)
- `services/math-verifier/app/verifiers/yks_isomorphic_twin_generator.py` *(new)*:
  - Implements deterministic family classifiers and parametric generators for `FAM_01` through `FAM_12`.
  - Computes `sympy_verification_hash` (SHA-256 of canonical AST + verified solution set) for auditability.

## 7. Database Migration & Persistence Contract
- Extends `counterfactual_twin_drills` (`V031`) via additive columns in `PLANNED V040` (detailed in `SPRINT_14.4`).

## 8. REST API Contracts
- Internal verifier route: `POST /internal/v1/twins/generate-isomorphic`
- Public trigger route wired in `SPRINT_14.4`: `POST /api/v1/problem-sessions/{sessionId}/generate-twin`

## 9. iOS (`apps/ios/VerifiedAI/`) Scope
- Models `TwinProblemFamily.swift` and `VerifiedTwinChallengeDTO.swift` in `apps/ios/VerifiedAI/Features/ProblemSolving/Domain/`.

## 10. AI Capability & Unit Economics Guardrail
- `100%` symbolic Python/SymPy execution for all 12 supported families (`$0.00` AI cost).

## 11. Privacy, Safety & Compliance
- Generated twin problems contain only synthetic mathematical parameters and zero student PII.

## 12. Telemetry & Observability
- `verifiedai.twin.generator.invocations{family,status}`
- `verifiedai.twin.generator.latency_ms{family}`

## 13. Feature Flags & Rollback Plan
- Feature flag: `verifiedai.attempt.yks-isomorphic-twin-engine-enabled: false`.

## 14. Automated Test Matrix (Categories A–F)
- **Gate A (600-Case SymPy Property Suite):** `test_yks_isomorphic_twin_generator.py` runs 50 randomized generations across each of the 12 families (`600` total generated questions) and asserts `100%` SymPy verification pass, zero duplicate multiple-choice options, and `<150ms` execution per twin.
- **Gate B (Backend Contract Test):** `IsomorphicTwinGeneratorServiceTest` verifies serialization and hash integrity.

## 15. Owner-Only Acceptance Command
- `bash scripts/quality/sprint_14_3_owner_acceptance.sh --full`

## 16. Explicit Non-Goals
- Does not generate 3D solid geometry diagrams.

## 17. Exit Gate & Downstream Consumers
- Unlocks `SPRINT_14.4` (Timed Independent Twin Challenge & Epistemic Mastery Verification).

## 18. Canonical Cross-References
- `learning/84_VERIFIED_TWIN_PRACTICE_AND_INDEPENDENT_LEARNING_ENGINE.md`
- `learning/93_ACTIVE_SCAFFOLDING_TWIN_VERIFICATION_AND_INTERACTIVE_CANVAS_SPECIFICATION.md`
<!-- LEARNING_OS_PRODUCTION_SPEC_V1:END -->
