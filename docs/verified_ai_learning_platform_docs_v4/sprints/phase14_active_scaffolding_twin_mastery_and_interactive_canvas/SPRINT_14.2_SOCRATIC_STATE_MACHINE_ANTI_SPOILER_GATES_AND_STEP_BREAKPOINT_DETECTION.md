# Sprint 14.2 — Socratic State Machine, Anti-Spoiler Gates, and Step-Breakpoint Detection

## Sprint mission

Implement the end-to-end **Step-Breakpoint Detection Engine**, **Anti-Spoiler / Anti-Cheating Guardrail**, REST API endpoints (`/api/v1/tutoring/scaffolding-sessions`), and native iOS **Interactive Step-Check Card UX** so learners actively solve micro-checkpoints at each mathematical step before advancing.

## Why this sprint exists

In standard LLM chats (as demonstrated in our AYT 2022 Polynomial benchmark), a learner can simply type *"Uğraştırma, cevabı direkt ver"* and the LLM surrenders the complete answer. Furthermore, static solvers cannot generate mathematically verified multiple-choice or symbolic micro-checkpoints per step. This sprint connects `services/math-verifier` with `com.verifiedai.tutoring` to generate SymPy-verified micro-checkpoints (with deterministic distractors reflecting real student algebraic errors) and refuses spoiler bypasses.

## Preconditions and dependencies

- `SPRINT_14.1` completed (`SocraticScaffoldingSession` aggregate and `V039` schema).
- Existing `services/math-verifier/app/attempt_verification_v2.py` and `com.verifiedai.solving` verified solution steps available.

## In scope

- Python verifier endpoint `POST /internal/v1/scaffolding/generate-step-checkpoints` in `services/math-verifier/app/verifiers/scaffolding_breakpoint.py`.
- Spring Boot orchestration service `ScaffoldingSessionOrchestrator.java` and `ScaffoldingController.java`.
- Prompt-injection and spoiler-request classifier (`SpoilerBypassGuard.java`) that intercepts *"direkt cevabı ver"* / *"skip steps"* inputs and redirects the learner to the active micro-checkpoint (or requires explicit forfeiture of mastery points).
- iOS `SocraticScaffoldingView.swift` and `SocraticScaffoldingViewModel.swift` with inline interactive micro-choice and symbolic input validation.

## Explicitly out of scope

- Audio-visual voiceover synthesis (covered in `SPRINT_14.7`).
- Live PencilKit canvas drawing (covered in `SPRINT_14.8`).

## Deliverables

1. **Semantic deliverable** — Deterministic mapping from each canonical solution step $S_i$ to a 4-tier scaffolding packet (`tier1ConceptPrompt`, `tier2StrategyEquation`, `tier3CriticalMove`, `tier4FullStep`, plus 3 SymPy-generated distractor choices).
2. **Implementation deliverable** — `scaffolding_breakpoint.py`, `ScaffoldingSessionOrchestrator.java`, and iOS `SocraticScaffoldingView.swift`.
3. **Contract deliverable** — OpenAPI and JSON schema contracts for `/api/v1/tutoring/scaffolding-sessions/**`.
4. **Quality deliverable** — End-to-end integration tests verifying that wrong micro-checkpoint selections trigger targeted correction (e.g., catching $2^n=4 \implies n=3$ and explaining $2^3=8 \neq 4$).
5. **Operational deliverable** — Latency histograms and tier-completion funnel metrics.
6. **Documentation deliverable** — API and pedagogical state documentation kept in sync.

---

<!-- LEARNING_OS_PRODUCTION_SPEC_V1:START -->
# Learning OS Production Implementation Specification v1 (`2026-11-11`)

## 1. Sprint Metadata & Status
- **Sprint ID:** `SPRINT_14.2` (Fast-track bridge alias: `SPRINT_6.5B-Part2`)
- **Canonical Title:** Socratic State Machine, Anti-Spoiler Gates, and Step-Breakpoint Detection
- **Status:** `PLANNED` (`GATED_ON_SPRINT_14_1`)
- **Primary Spec Reference:** `learning/93_ACTIVE_SCAFFOLDING_TWIN_VERIFICATION_AND_INTERACTIVE_CANVAS_SPECIFICATION.md`

## 2. Strategic Learning OS Objective & User Value
Delivers the interactive step-by-step Socratic tutor experience where the platform asks a targeted micro-question at the exact algebraic breakpoint (e.g., *"$(x-8)P(2x)$ çarpımının en yüksek dereceli terimi hangisi olur?"*), validates the learner's response symbolically via SymPy, and diagnoses specific wrong selections (such as $n=3$ for $2^n=4$) before moving to the next step.

## 3. Entry Gate & Preconditions
- `SPRINT_14.1` domain & `V039` migration accepted.

## 4. Domain Invariants & Mathematical Rules
1. **SymPy-Verified Micro-Checkpoints (`INV-SCAF-004`):** Every micro-checkpoint question generated for step $S_i$ has its correct expression $E_{\text{correct}}$ and its 3 misconception distractors $(E_{d1}, E_{d2}, E_{d3})$ verified by SymPy so that no distractor is accidentally algebraically equivalent to $E_{\text{correct}}$ ($\forall j, \text{SymPy.simplify}(E_{\text{correct}} - E_{dj}) \neq 0$).
2. **Anti-Spoiler Guardrail (`INV-SCAF-005`):** Any user message matching answer-demand intents (`SPOILER_DEMAND_DETECTED`) in `ACTIVE_SCAFFOLDING` mode is intercepted deterministically at `$0.00` LLM cost and answered with a pedagogical nudge unless the learner taps the explicit `"Cevabı Göster (Ustalık Puanı Kazandırmaz)"` confirmation button.
3. **Targeted Misstep Correction (`INV-SCAF-006`):** When a learner submits an incorrect response $R_{\text{student}}$ to a micro-checkpoint, the verifier computes $\text{SymPy}$ witness values or matches the distractor's `misconceptionCode` (e.g., `EXP_POWER_CONFUSED_WITH_MULTIPLICATION` when $2^3=8$ is confused with $2^2=4$) to deliver an immediate, mathematically exact correction.

## 5. Owning Spring Modulith Backend Modules & Classes
- `services/api/src/main/java/com/verifiedai/tutoring/application/ScaffoldingSessionOrchestrator.java`
- `services/api/src/main/java/com/verifiedai/tutoring/application/SpoilerBypassGuard.java`
- `services/api/src/main/java/com/verifiedai/tutoring/web/ScaffoldingController.java`
- `services/api/src/main/java/com/verifiedai/tutoring/infrastructure/verifier/HttpMathVerifierScaffoldingClient.java`

## 6. Python Math Verifier Impact (`services/math-verifier/`)
- `services/math-verifier/app/verifiers/scaffolding_breakpoint.py`:
  - Given a verified step transition $L_{i-1} \to L_i$, synthesizes:
    - `correct_expr_latex`
    - `distractors`: generated via deterministic perturbation operators (missing power distribution $(2x)^n \to 2x^n$, sign flip, exponent off-by-one $2^n=4 \to n=3$, root-factor confusion $P(4)=0 \to \text{root}=2$), each checked for non-equivalence against `correct_expr_latex`.
    - `evaluate_learner_checkpoint_input(expected_expr, learner_expr)`: returns `EXACT_MATCH`, `EQUIVALENT_VALID`, or `MISMATCH_WITH_WITNESS` (providing counterexample evaluation).

## 7. Database Migration & Persistence Contract
- Uses `V039__create_socratic_scaffolding_sessions.sql` authored in `SPRINT_14.1`.

## 8. REST API Contracts
- `POST /api/v1/tutoring/scaffolding-sessions` — Creates or resumes a scaffolding session for `problemSessionId` / `attemptId`; returns `Tier 1` prompt and Step 1 micro-checkpoint.
- `POST /api/v1/tutoring/scaffolding-sessions/{sessionId}/respond` — Submits learner micro-checkpoint selection or algebraic expression; returns symbolic verification result, targeted misconception correction if wrong, or advances to the next step/tier.
- `POST /api/v1/tutoring/scaffolding-sessions/{sessionId}/escalate-tier` — Advances from `TIER_1` $\to$ `TIER_2` $\to$ `TIER_3` $\to$ `TIER_4` with explicit mastery-cap acknowledgment.

## 9. iOS (`apps/ios/VerifiedAI/`) Scope
- `apps/ios/VerifiedAI/Features/Tutor/Presentation/SocraticScaffoldingView.swift`
- `apps/ios/VerifiedAI/Features/Tutor/Presentation/SocraticScaffoldingViewModel.swift`
- `apps/ios/VerifiedAI/Features/Tutor/Presentation/StepMicroCheckpointCardView.swift` (renders 2D LaTeX options via `SwiftMath` per `ADR-010`, highlights wrong selections with constructive algebraic witness feedback, and displays current Tier badge).

## 10. AI Capability & Unit Economics Guardrail
- Deterministic SymPy distractor & equivalence checking runs at **`$0.00` AI cost** in `<90ms`.
- Optional `AiCapability.TUTOR` natural-language phrasing is cached by `(canonicalProblemHash, stepIndex, tier)` in PostgreSQL/Redis so popular exam questions (e.g., ÖSYM AYT/TYT questions) incur `$0.00` marginal LLM cost after first generation.

## 11. Privacy, Safety & Compliance
- Object-level authorization enforced on `sessionId` (`user_id = currentUserId`).
- Rate-limited at `30 requests / minute / user` to prevent automated scraping.

## 12. Telemetry & Observability
- `verifiedai.scaffolding.checkpoint.evaluated{outcome=CORRECT|WRONG_DISTRACTOR|EQUIVALENT_ALT}`
- `verifiedai.scaffolding.spoiler_guard.intercepted`
- `verifiedai.scaffolding.step.latency_ms`

## 13. Feature Flags & Rollback Plan
- Controlled by `verifiedai.tutoring.active-scaffolding-enabled`. Disabling cleanly falls back to standard `Sprint 6.5`/`6.6` tutor view without data loss.

## 14. Automated Test Matrix (Categories A–F)
- **Gate A (Python Verifier):** `test_scaffolding_breakpoint.py` — verifies non-equivalent distractor generation and witness counterexamples on 25 YKS AYT/TYT problems (including the AYT 2022 polynomial degree problem).
- **Gate B (Backend API):** `ScaffoldingControllerIntegrationTest` — verifies tier progression, spoiler guard interception, and idempotency.
- **Gate C (iOS):** `SocraticScaffoldingViewModelTests` — verifies state transitions, error recovery, and VoiceOver labels.

## 15. Owner-Only Acceptance Command
- `bash scripts/quality/sprint_14_2_owner_acceptance.sh --full`

## 16. Explicit Non-Goals
- Does not generate post-solution Twin Problems (handled in `SPRINT_14.3`–`14.4`).

## 17. Exit Gate & Downstream Consumers
- Unlocks `SPRINT_14.3` (Isomorphic Twin Generator) and `SPRINT_14.6` (Whiteboard Vector Stream).

## 18. Canonical Cross-References
- `learning/93_ACTIVE_SCAFFOLDING_TWIN_VERIFICATION_AND_INTERACTIVE_CANVAS_SPECIFICATION.md`
- `adr/ADR-010_NATIVE_2D_MATH_RENDERING_AND_STEP_DIFF_HIGHLIGHTING.md`
<!-- LEARNING_OS_PRODUCTION_SPEC_V1:END -->
