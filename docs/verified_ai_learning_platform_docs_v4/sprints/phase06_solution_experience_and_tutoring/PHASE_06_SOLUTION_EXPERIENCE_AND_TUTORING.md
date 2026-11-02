# Phase 6 — Solution Experience & Tutoring
## Phase objective
This phase groups the production delivery units required for **Solution Experience & Tutoring**. It must leave the repository in a coherent, testable state and may not transfer unresolved foundational risk into later phases without an explicit recorded exception.
## Phase-wide quality rules
- Preserve all domain invariants and accepted ADRs.
- Update canonical documentation when semantics or contracts change.
- Add automated tests at the cheapest reliable layer.
- Add observability before relying on a behavior in production.
- Never expose AI provider credentials or internal verifier endpoints to the mobile client.
- All user-visible failure states require a recovery path.
- All durable schema changes use reviewed Flyway migrations.
- All AI behavior changes require traceable prompt/model versions.
## Sprint sequence
- `SPRINT_6.1` — **Production Solution Results Experience** — Deliver final answer, steps, method, state restoration, error recovery, and readable mathematical rendering without overwhelming the learner (Local Acceptance Passed).
- `SPRINT_6.2` — **Verification Transparency and Evidence UX** — Expose what was and was not verified, why a result has its status, and when the system requires caution (Local Acceptance Passed).
- `SPRINT_6.2B` — **Live Cloud AI Provider Integration & Spending Guard Activation** — Wire managed cloud APIs (`Gemini 2.5 Flash` OCR/Normalize + `GPT-4.1-mini` Solve + `o4-mini` Escalation) behind existing ports; de-promote `vLLM` `Qwen3.5-4B` per `adr/ADR-008_MANAGED_CLOUD_API_ROUTING_AND_QWEN_4B_DE_PROMOTION.md` and `sprints/02_FOCUSED_COMMERCIAL_WEDGE_SPRINT_PROGRAM.md`.
- `SPRINT_6.2C` — **Canonical Solution Set Algebra, Extraneous Root Elimination & Domain Constraint Guard** — Implement `canonical_v2` (`FiniteSet`, `Interval`, `DomainConstraintGuard`) per `adr/ADR-009_CANONICAL_SOLUTION_SET_ALGEBRA_AND_DOMAIN_GUARD.md` and `sprints/02_FOCUSED_COMMERCIAL_WEDGE_SPRINT_PROGRAM.md`.
- `SPRINT_6.2D` — **Native iOS 2D Mathematical Rendering & Sub-Expression Diff Highlighting** — Integrate `SwiftMath` CoreGraphics 2D LaTeX rendering and token diffs per `adr/ADR-010_NATIVE_2D_MATH_RENDERING_AND_STEP_DIFF_HIGHLIGHTING.md` and `sprints/02_FOCUSED_COMMERCIAL_WEDGE_SPRINT_PROGRAM.md`.
- `SPRINT_6.3` — **Explanation Depth, Beginner-to-Deep Modes, and Personalization** — Support concise, standard, deep, and beginner explanations without changing mathematical truth.
- `SPRINT_6.4` — **Tutor Session Domain and Conversation Persistence** — Create persistent, resumable tutoring sessions linked to problem, skill, learner state, and safety policy.
- `SPRINT_6.5` — **Socratic Tutor Behavior and Pedagogical State Machine** — Make Tutor Mode guide the learner with questions instead of immediately revealing answers whenever the selected policy requires productive struggle.
- `SPRINT_6.6` — **Progressive Hint System and Hint Cost Semantics** — Introduce structured hint levels, track hint use as a mastery signal, and prevent accidental full-answer leakage.
- `SPRINT_6.7A` — **Analyze My Work: Handwritten Attempt Verification & First Wrong Step Pinpointing** — Fast-track handwritten attempt line-by-line verification, domain checks, and `CounterexampleEngine` per `sprints/02_FOCUSED_COMMERCIAL_WEDGE_SPRINT_PROGRAM.md`.
- `SPRINT_6.7B` — **Counterfactual Twin Problem Generator & Socratic Progressive Hint Ladder** — SymPy-verified twin question generator (`F04`) and 3-tier hint ladder (`F09`) per `sprints/02_FOCUSED_COMMERCIAL_WEDGE_SPRINT_PROGRAM.md`.
- `SPRINT_6.8` — **Alternative Solution Methods and Method Comparison** — Offer legitimate alternate methods and explain tradeoffs without fabricating unnecessary variants.
- `SPRINT_6.9` — **Interactive Mathematical Visualizations and Concept Explanations** — Add graph/geometry/number-line visual tools when they improve understanding and remain deterministic or clearly labeled.
- `SPRINT_6.10` — **Solution/Tutor Accessibility, Localization, Performance, and UX Polish** — Make the core learning experience production-ready across Dynamic Type, VoiceOver, localization, reduced motion, weak networks, and older supported devices.

## Phase exit gate
The phase is complete only when every sprint-specific acceptance criterion is satisfied, phase-level integration tests pass, documentation is current, no unresolved P0/P1 defects remain in the phase scope, and the next phase can consume the delivered contracts without relying on undocumented assumptions.

## Required review at phase close
- Architecture drift review.
- Security/privacy delta review.
- AI cost/quality delta review when applicable.
- Database migration and data-retention review.
- iOS accessibility/localization review for user-visible work.
- Observability and supportability review.
- Backlog reprioritization using evidence collected during the phase.

<!-- PHASE_PRODUCTION_V3:START -->
# Phase Production Contract v3

## Phase objective alignment

**Solution and tutoring experience**

- Present verification and uncertainty honestly while maintaining premium UX.
- Bound conversational context/cost and avoid expensive hidden calls.
- Keep tutor behavior provider-independent and testable against pedagogical policy.

## Hybrid-AI rule

This phase must preserve the accepted API-first/provider-neutral architecture. Proprietary training/self-hosting is forbidden before Phase 13 entry gates, except for isolated evaluation research that never affects production and uses eligible data. All AI-affecting work must measure quality, latency and cost together.

## Phase exit evidence

The phase exit review must include:

- completed sprint acceptance evidence;
- unresolved P0/P1 risks;
- architecture/documentation drift check;
- security/privacy review for changed trust boundaries;
- AI quality/cost trend if AI routes were touched;
- migration/rollback readiness;
- next-phase dependency statement.
<!-- PHASE_PRODUCTION_V3:END -->

## Sprint 6.1 handoff

- `sprints/phase06_solution_experience_and_tutoring/SPRINT_6.1_IMPLEMENTATION_MAP.md` — iOS solution results hierarchy, typed screen phase, candidate schema-version guard, and lifecycle ownership.
- `sprints/phase06_solution_experience_and_tutoring/SPRINT_6.1_EXECUTION_REPORT.md` — `OWNER_ACCEPTANCE_PASSED` for local scope in run `20261031-152301-cKUveC` (Gates A–D `PASS`, 129 iOS tests and 9 backend/evaluation suites `PASS`, exit 0).

## Sprint 6.2 handoff

- `sprints/phase06_solution_experience_and_tutoring/SPRINT_6.2_IMPLEMENTATION_MAP.md` — deterministic `VerificationEvidencePresentation`, progressive-disclosure evidence UX, reason/uncertainty/recovery catalog coverage, and read-only verification refresh.
- `sprints/phase06_solution_experience_and_tutoring/SPRINT_6.2_EXECUTION_REPORT.md` — `OWNER_ACCEPTANCE_PASSED` for local scope in run `20261031-184154-aBdvps` (Gates A–D `PASS`, 134 iOS tests and 9 backend/evaluation suites `PASS`, exit 0).

<!-- PHASE_PRODUCTION_V6:START -->
# Phase Production Execution Contract v6 (Commercial Wedge & Deep Pedagogical Differentiators)

## 1. Strategic Re-Ordering & Fast-Track Commercial Wedge (`ADR-008`..`ADR-011`)
Per `sprints/02_FOCUSED_COMMERCIAL_WEDGE_SPRINT_PROGRAM.md`, Phase 6 is split into **Track A (Immediate 30-Day Commercial Wedge)** and **Track B (Post-Beta Expansion)**:
- **Track A (Mandatory Before TestFlight Beta):** `SPRINT_6.2B` (Managed Cloud AI Routing: `Gemini 2.5 Flash` + `GPT-4.1-mini` + conditional `o4-mini` behind `PostgresAiSpendGuard`), `SPRINT_6.2C` (`CanonicalMathDocument v2`, Solution Set Algebra `FiniteSet`/`Interval`, `DomainConstraintGuard` in `V024`), `SPRINT_6.2D` (iOS Native 2D `SwiftMath` rendering & `DomainTrapRadarBannerView`), `SPRINT_6.7A` (`Analyze My Work` line-by-line attempt verification & `CounterexampleEngine` in `V025`), and `SPRINT_6.7B` (`FirstWrongStepDiffView`, 3-Stage Socratic Hints, and 60-Second Counterfactual Twin Drill `F04`).
- **Track B (Executed After 50-Student TestFlight Gate Passes):** `SPRINT_6.3`, `SPRINT_6.4`, `SPRINT_6.5`, `SPRINT_6.8`, `SPRINT_6.9` (Interactive Function/Derivative Plotter `F24` & Turkish YKS Story-to-Algebra Visual Bridge `F08`), and `SPRINT_6.10`.

## 2. Signature Differentiators Owned by Phase 6
- **`F01` Handwritten First-Wrong-Step Pinpointing (`Analyze My Work`):** Evaluates student notebook photos line by line (`L_0 -> L_1 -> ... -> L_k`) using `services/math-verifier/app/attempt_verification.py`.
- **`F02` Concrete Counterexample Generator (`CounterexampleEngine`):** Proves *why* a student's step transition is invalid by substituting a safe integer `x \in D` where `val(L_{i-1}, x) != val(L_i, x)`.
- **`F03` Non-Standard Valid Path Recognition:** Validates any mathematically sound student method (completing the square, substitution, Vieta's formulas) via solution-set equivalence (`SolutionSet(L_i) == SolutionSet(L_{i-1})`) rather than brittle template matching.
- **`F04` Counterfactual Twin Problem Generator:** Generates a `SymPy`-verified 60-second twin question isolating the exact mistake code (`primaryMistakeCode`) detected in `Analyze My Work`.
- **`F08` YKS "Yeni Nesil" Story-to-Algebra Visual Bridge (`SPRINT_6.8`/`6.9`):** Maps each highlighted Turkish phrase in a contextual word problem directly to its algebraic variable/constraint before solving.
- **`F09` & `F12` Domain Trap Radar & Calculus Pedantic Guard (`SPRINT_6.2C`):** Automatically flags denominator zeros, negative even radicals, non-positive logarithm arguments, missing `+ C` constants, and `\sqrt{x^2} = |x|` traps.
- **`F15` Exam-Speed Shortcut vs. Rigorous Method Toggle (`SPRINT_6.7B`/`6.8`):** Offers both the full guaranteed step-by-step derivation and the 30-second ÖSYM exam-speed shortcut.

## 3. Canonical Path & Privacy Invariants
- All iOS deliverables must reside under `apps/ios/VerifiedAI/Features/...` (reconciling legacy `apps/ios/VerifiedLearning/...` paths).
- All `student_attempts` and `student_attempt_steps` rows (`V025`) enforce `training_eligible = false` by default and are exported/erased via `StudentAttemptPrivacyLifecycleContributor`.
<!-- PHASE_PRODUCTION_V6:END -->

## Sprint 6.3 local development handoff — owner scheduling exception

- `SPRINT_6.3_IMPLEMENTATION_MAP.md` — existing profile ownership, deterministic four-mode projection, invariants and V6 scheduling exception.
- `SPRINT_6.3_EXECUTION_REPORT.md` — `LOCAL_ACCEPTANCE_PASSED` in owner run `20261101-172742-BCuatU`: 154 iOS and 85 backend tests passed, zero failures/skips; Gates A–D passed.
- This local Track B implementation does not complete Track A prerequisites, Phase 6 exit criteria, hosted CI or production gates.

## Sprint 6.4 owner-directed local scheduling exception

Tutor session/message persistence is implemented locally; `LOCAL_ACCEPTANCE_PASSED` in owner run
`20261101-200102-8qY6k7`: 166 iOS and 117 backend tests passed, zero failures/skips; Gates A–D passed.
This Track B exception does not authorize release or waive Track A/ADR-011.
V027 is additive; V024–V026 remain reserved by the commercial-wedge program, including Sprint 7.0A.
Only fresh disposable databases are permitted until ordered shared upgrades are reconciled.
See `sprints/phase06_solution_experience_and_tutoring/SPRINT_6.4_EXECUTION_REPORT.md`.

## Sprint 6.5 owner-directed local implementation

Status `LOCAL_ACCEPTANCE_PASSED` in owner run `20261102-113306-4u8mzd`: 176 iOS tests,
189 backend tests and 20 synthetic policy cases passed, with zero failures/skips. Bounded deterministic
Socratic guidance, server-owned progress, additive turn persistence/API and iOS recovery are locally accepted.
See `sprints/phase06_solution_experience_and_tutoring/SPRINT_6.5_IMPLEMENTATION_MAP.md` and
`sprints/phase06_solution_experience_and_tutoring/SPRINT_6.5_EXECUTION_REPORT.md`.
Live tutor generation/trusted step assessment/expert teaching quality remain unavailable. TUTOR routing
and deterministic rollout default disabled. This local scope does not waive ADR-011 or shared migration,
hosted CI, staging and production gates. Sprint 6.6 hint ladders/economics remain separate.
