# Phase 14 — Active Cognitive Scaffolding, Verified Twin Mastery & Interactive Socratic Canvas

## Phase objective
This phase groups the production delivery units required for **Active Cognitive Scaffolding, CAS-Verified Isomorphic Twin Mastery, Misconception Root-Cause Diagnosis, and the Bidirectional Interactive Socratic Canvas (Audio-Visual Whiteboard)**. It operationalizes the architectural contracts in `learning/93_ACTIVE_SCAFFOLDING_TWIN_VERIFICATION_AND_INTERACTIVE_CANVAS_SPECIFICATION.md` and `learning/84_VERIFIED_TWIN_PRACTICE_AND_INDEPENDENT_LEARNING_ENGINE.md` to establish an unassailable pedagogical and technological moat against passive AI video solvers (such as Solvi / Solvely), high-latency human Q&A networks (Kunduz), and unverified LLM answer-copiers (Gauth, QANDA).

It must leave the repository in a coherent, testable, non-regressing state and may not corrupt any existing Phase 01–13 contracts, database migrations (`V001`–`V038`), or symbolic verification guarantees.

## Governance & Program Expansion Justification (`roadmap/67` & `operations/70`)
Per `roadmap/67_PRODUCT_PROGRAM_STRUCTURE.md` (Section: *Program Expansion Rule*) and `operations/70_RELEASE_TRAINS_AND_BACKLOG_PROMOTION.md` (Section: *Post-Launch Phase Expansion Policy*), Phase 14 is formally chartered under the following triggers and governance parameters:

| Governance Dimension | Specification |
|---|---|
| **Expansion Triggers** | 1. **Competitive Product Gap with Clear Business Case:** Solvi (Clarif Co / Solvely) captures high-intent Turkish YKS/LGS students via synthetic animated video solutions, but suffers from passive learning ("Illusion of Competence") and unverified LLM hallucinations. <br>2. **Technical Risk Requiring Isolated Delivery & Rollback:** Bidirectional Apple Pencil (`PencilKit`) ink verification, vector timeline synchronization, and Turkish mathematical Neural TTS require isolated feature-flagged delivery and independent FinOps guardrails. |
| **Program Ownership** | Bridges **Program 2 (Core Verified Solving)**, **Program 3 (Learning Intelligence)**, and **Program 5 (Post-Launch Evolution)**. Fast-track wedge slices (`Sprint 14.1`–`14.4`) may be promoted early as bridge units (`Sprint 6.5B`, `Sprint 7.8B`, `Sprint 8.6B`) once `Sprint 7.4` owner acceptance completes. |
| **Capability Coverage Impact** | Extends `CAP-SOLVE-002`, `CAP-VERIFY-001`, `CAP-LEARN-003`, and `CAP-ADAPT-001` in `roadmap/68_PRODUCTION_V1_CAPABILITY_COVERAGE_MATRIX.md`. |
| **Requirement Traceability Impact** | Enforces `REQ-TRUST-001`, `REQ-VERIFY-001`, `REQ-MATH-001`, `REQ-AI-001`, `REQ-ATTEMPT-001`, `REQ-MASTERY-001`, and `REQ-PRIV-001` in `quality/69_REQUIREMENTS_TRACEABILITY_MATRIX.md`. |
| **Source-of-Truth Documents** | `learning/93_ACTIVE_SCAFFOLDING_TWIN_VERIFICATION_AND_INTERACTIVE_CANVAS_SPECIFICATION.md`, `learning/83_VERIFIED_MISTAKE_DNA_AND_MISTAKE_PASSPORT_SPECIFICATION.md`, `learning/84_VERIFIED_TWIN_PRACTICE_AND_INDEPENDENT_LEARNING_ENGINE.md`, `product/82_LEARNING_OS_MARKET_COMPETITOR_AND_CUSTOMER_RESEARCH.md`, `roadmap/87_LEARNING_OS_STRATEGIC_PHASE_AND_SPRINT_EXPANSION_PROGRAM.md`. |
| **Affected Bounded Contexts** | `com.verifiedai.tutoring`, `com.verifiedai.attempt`, `com.verifiedai.mistake`, `com.verifiedai.mastery`, `com.verifiedai.studyplan`, `services/math-verifier`, `apps/ios/VerifiedAI/Features/Tutor`, `apps/ios/VerifiedAI/Features/ProblemSolving`. |
| **Entry Gates** | 1. Core `Sprint 7.1`–`7.4` attempt/mistake baseline accepted.<br>2. Full Phase 14 rollout gated by `adr/ADR-011_FOCUSED_V1_COMMERCIAL_LAUNCH_GATE_BEFORE_PHASE_08_13.md` (`Sprint 11.9A` cohort gate), while deterministic scaffolding (`14.1`–`14.4`) is eligible for fast-track wedge inclusion. |
| **Exit Gates** | 100% pass across `sprint_14_1` through `sprint_14_10` owner acceptance suites; `0.0%` false `VERIFIED` rate across all whiteboard, voice, and twin artifacts; per-session AI FinOps cost `<= $0.0080`; full KVKK/GDPR and Apple `5.1.2(i)` compliance. |

## Phase-wide quality rules
- Preserve all domain invariants (`domain/10_DOMAIN_INVARIANTS_AND_BUSINESS_RULES.md`) and accepted ADRs (`ADR-001` through `ADR-011`).
- Never emit an unverified mathematical step onto the Socratic scaffolding ladder, twin challenge, or audio-visual whiteboard as `VERIFIED`.
- All durable schema additions (`PLANNED V039+`) must be strictly additive and backwards-compatible with `V001`–`V038`.
- Never expose AI provider credentials, TTS API keys, or internal Python verifier endpoints to the iOS client.
- Passively watching an animated whiteboard or revealing `TIER_4_FULL_SOLUTION` grants **`0.0` mastery increase** (`INV-MASTERY-ANTI-CRUTCH`); mastery advances only on verified student step completion or independent Twin Challenge success.

## Sprint sequence
- `SPRINT_14.1` — **Competitive Pedagogy Contract and 4-Tier Cognitive Scaffolding Domain** — `SPRINT_14.1_COMPETITIVE_PEDAGOGY_CONTRACT_AND_4_TIER_SCAFFOLDING_DOMAIN.md` — Define the `SocraticScaffoldingSession` aggregate, 4-tier progression state machine (`TIER_1_CONCEPT` -> `TIER_4_FULL_SOLUTION`), `V039` persistence schema, and server-authoritative anti-spoiler policies.
- `SPRINT_14.2` — **Socratic State Machine, Anti-Spoiler Gates, and Step-Breakpoint Detection** — `SPRINT_14.2_SOCRATIC_STATE_MACHINE_ANTI_SPOILER_GATES_AND_STEP_BREAKPOINT_DETECTION.md` — Implement SymPy-backed step-breakpoint pinpointing, `/api/v1/tutoring/sessions/{sessionId}/scaffold` APIs, prompt-injection/spoiler refusal guards, and the iOS progressive scaffolding card UX.
- `SPRINT_14.3` — **SymPy Parametric Isomorphic Twin Engine for YKS/AYT/LGS Families** — `SPRINT_14.3_SYMPY_PARAMETRIC_ISOMORPHIC_TWIN_ENGINE_FOR_YKS_AYT_FAMILIES.md` — Build deterministic `$0.00`-cost parametric twin generators for 12 high-frequency YKS (`TYT`/`AYT`), LGS, and Calculus problem families with 100% SymPy root, degree, and boundary-condition validation.
- `SPRINT_14.4` — **Timed Independent Twin Challenge and Epistemic Mastery Verification** — `SPRINT_14.4_TIMED_INDEPENDENT_TWIN_CHALLENGE_AND_EPISTEMIC_MASTERY_VERIFICATION.md` — Wire the 60–120 second locked "İkiz Soru Meydan Okuması" flow, separate `INDEPENDENT_TWIN_VERIFIED` from `SCAFFOLD_ASSISTED` evidence, and connect twin mastery to `MistakePassport` closure.
- `SPRINT_14.5` — **Misconception Root-Cause Diagnosis and Prerequisite Repair Bridge** — `SPRINT_14.5_MISCONCEPTION_ROOT_CAUSE_DIAGNOSIS_AND_PREREQUISITE_REPAIR_BRIDGE.md` — Diagnose whether an error stems from the target grade-level topic or a foundational prerequisite slip (e.g., Grade 10 Polynomials vs. Grade 9 Exponents) mapped to MEB Maarif / ÖSYM Kazanım trees.
- `SPRINT_14.6` — **Declarative Whiteboard Vector Stream and Timeline Synchronization** — `SPRINT_14.6_DECLARATIVE_WHITEBOARD_VECTOR_STREAM_AND_TIMELINE_SYNCHRONIZATION.md` — Replace static `.mp4` video rendering with `WhiteboardTimelineDocument v1` (keyframed vector strokes, 2D LaTeX token highlights, coordinate geometry paths) at `<5%` of video bandwidth.
- `SPRINT_14.7` — **Turkish Mathematics Neural TTS and Synchronized Voice Pedagogy** — `SPRINT_14.7_TURKISH_MATHEMATICS_NEURAL_TTS_AND_SYNCHRONIZED_VOICE_PEDAGOGY.md` — Implement deterministic LaTeX-to-Turkish mathematical prosody normalization, word-level keyframe synchronization, content-addressed audio caching, and deaf/hard-of-hearing caption parity.
- `SPRINT_14.8` — **Bidirectional PencilKit Canvas and Live Ink Step Verification** — `SPRINT_14.8_BIDIRECTIONAL_PENCILKIT_CANVAS_AND_LIVE_INK_STEP_VERIFICATION.md` — Deliver the interactive iOS `PencilKit` whiteboard allowing learners to pause playback, write steps on-canvas, and receive real-time symbolic verification and voice/visual Socratic nudges.
- `SPRINT_14.9` — **Solvi Parity Benchmark, Retention Experiments, and FinOps Guardrails** — `SPRINT_14.9_SOLVI_PARITY_BENCHMARK_RETENTION_EXPERIMENTS_AND_FINOPS_GUARDRAILS.md` — Execute the 100-question YKS/AYT competitive benchmark (`verified-ai-solvi-disruption-eval-v1`), configure `EXP-009`/`EXP-010` cohort experiments, and enforce strict unit-cost ceilings.
- `SPRINT_14.10` — **Phase 14 Production Hardening, Accessibility, and Release Exit Gate** — `SPRINT_14.10_PHASE_14_PRODUCTION_HARDENING_ACCESSIBILITY_AND_RELEASE_EXIT_GATE.md` — Complete concurrency/chaos testing, VoiceOver/Dynamic Type/Reduced Motion verification, KVKK/GDPR privacy cascade checks, and the full owner acceptance gate.

## Phase exit gate
The phase is complete only when every sprint-specific acceptance criterion (`SPRINT_14.1`–`14.10`) is satisfied, phase-level integration tests pass, `scripts/quality/docs_check.py` passes with zero errors, no unresolved P0/P1 defects remain in the phase scope, and the owner records formal acceptance via `bash scripts/quality/sprint_14_10_owner_acceptance.sh --full`.

## Required review at phase close
- Architecture & bounded-context drift review (`ModularityTests`).
- Security, KVKK/GDPR, and Apple `5.1.2(i)` privacy delta review for voice and ink streams.
- AI FinOps cost/latency delta review (`PostgresAiSpendGuard` + TTS cache hit ratios).
- Database migration (`V039+`) forward/rollback and account-deletion cascade review.
- iOS accessibility (VoiceOver, Dynamic Type, Reduced Motion, Captions) and localization (`tr`, `en`) review.
- Observability, trace correlation, and runbook readiness review.
