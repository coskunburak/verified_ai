# ADR-011 — Focused V1 Commercial Launch Gate Before Phase 8–13 Expansion

- **Status:** Accepted (2026-10-31)
- **Decision:** Freeze implementation of Phases 8, 9, 10 (non-essential growth/admin slices), 12, and 13 until a Focused V1 Commercial Wedge (`Verified Cloud Solving` + `Analyze My Work / First Wrong Step Diagnosis` + `Domain Restriction Trap Radar` + `Mistake Passport`) is shipped to at least 50 real target students via TestFlight and evaluated against strict 30-day Go/No-Go product-market fit criteria.

## Context (Bağlam ve Kurucu Odak Analizi)

By commit `e6a928db2d70efb0b9f2556e478a243205c54955`, the repository had completed Phases 1 through 6.2 with 23 Flyway migrations, 650+ Java tests, 338 Python verifier tests, and 134 iOS tests, plus 309 Markdown governance documents—yet had **zero live cloud AI adapter calls** and **zero real student beta sessions**. Attempting to complete all 13 phases (120+ sprints) as a solo founder before first customer contact creates fatal schedule and burnout risk ("architectural procrastination").

## Decision Details (Odaklanmış V1 Çekirdek Kapsamı ve 30 Günlük Kapı)

1. **Mandatory Pre-Beta Scope (Focused V1 Commercial Wedge — 30-Day Sprint):**
   - **Week 1:** Managed cloud AI adapters (`Gemini 2.5 Flash` OCR + `GPT-4.1-mini` / `o4-mini` Solver) wired into `VerificationPolicy` and `PostgresAiSpendGuard`.
   - **Week 2:** `CanonicalMathDocument v2` (`FiniteSet`, `Interval`, `DomainConstraintGuard`) + iOS native 2D math rendering (`ADR-009`, `ADR-010`).
   - **Week 3:** `Analyze My Work` (First Wrong Step handwritten attempt diagnosis via line-by-line SymPy equivalence + counterexamples) and `Domain Restriction Trap Radar`.
   - **Week 4:** Closed TestFlight Beta with `>= 50` real target learners (Turkish YKS TYT/AYT & 1st-year Calculus students) and live telemetry on `verified-ai-eval-v2`.
2. **Explicitly Deferred Until Post-Beta Validation:**
   - Phase 8 full study-planner rebalancing & gamification loops (keep only lightweight Mistake Passport & Counterfactual Twin drill generation).
   - Phase 9 full timed mock-exam runtime.
   - Phase 12 Android/Web/B2B teacher-parent portals.
   - Phase 13 proprietary model fine-tuning and self-hosted GPU serving.
3. **30-Day Go / No-Go Gate (`roadmap/80_UNIT_ECONOMICS_30_DAY_LAUNCH_CONTRACT_AND_EXECUTION_ROADMAP.md`):**
   - **GO (Scale to App Store Commercial V1):** `>= 30%` Week-2 retention among beta learners, `>= 85%` `VERIFIED` yield on supported algebra/calculus problems, `0` false `VERIFIED` labels, and clear qualitative preference for `Analyze My Work` over unverified ChatGPT/Photomath.
   - **PIVOT / STOP:** If real students do not retain on verified solving + first-wrong-step diagnosis, halt Phase 8–13 feature construction immediately and pivot based on empirical telemetry.

## Consequences

- Aligns `roadmap/43_PRODUCT_AND_TECHNICAL_ROADMAP.md`, `roadmap/67_PRODUCT_PROGRAM_STRUCTURE.md`, and `sprints/00_SPRINT_MASTER_PLAN.md` around rapid empirical validation without compromising any safety, verification, privacy, or billing invariant.
