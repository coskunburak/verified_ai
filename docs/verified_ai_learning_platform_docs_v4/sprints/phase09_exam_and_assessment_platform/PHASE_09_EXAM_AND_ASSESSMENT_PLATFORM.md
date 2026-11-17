# Phase 9 — Exam & Assessment Platform
## Phase objective
This phase groups the production delivery units required for **Exam & Assessment Platform**. It must leave the repository in a coherent, testable state and may not transfer unresolved foundational risk into later phases without an explicit recorded exception.
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
- `SPRINT_9.1` — **Exam Definitions, Curriculum Mapping, and Versioning** — Create stable exam models, syllabus mappings, skill weights, versions, and effective dates without hardcoding UI assumptions.
- `SPRINT_9.2` — **Learner Exam Goal Setup and Candidate Profile** — Capture exam date, target score, accommodations, available study time, and curriculum version as planning inputs.
- `SPRINT_9.3` — **Question Bank, Blueprint, and Assessment Assembly Rules** — Build reproducible assessment blueprints with topic/difficulty distribution and source provenance.
- `SPRINT_9.4` — **Timed Mock Exam Runtime** — Implement interruption-resistant timed assessments with navigation, autosave, accessibility, and clear no-hint policy.
- `SPRINT_9.5` — **Scoring Engine and Evidence Traceability** — Compute deterministic score components and retain evidence so reported scores are explainable and reproducible.
- `SPRINT_9.6` — **Exam Readiness Model and Confidence Semantics** — Estimate readiness from mastery, assessment evidence, syllabus coverage, recency, and uncertainty without claiming unsupported precision.
- `SPRINT_9.6B` — **TYT/AYT Curriculum Expansion, Turkish New-Gen Question Bank & SymPy Benchmarks (Option A — Wave 1)** — `IMPLEMENTED & VERIFIED` (`V060`; see `SPRINT_9.6B_EXECUTION_REPORT.md` & `SPRINT_9.6B_FILE_MANIFEST.md`): Expand curriculum taxonomy (`2026.2`), populate >= 60 Turkish TYT & >= 60 hard Turkish AYT items with distractor trap links (`V060`), and establish `yks-tr-*` SymPy-verified golden benchmarks (`Bridge B1+B2+B3`, `SPRINT_9.6B_TYT_AYT_CURRICULUM_EXPANSION_QUESTION_BANK_AND_SYMPY_BENCHMARKS.md`).
- `SPRINT_9.7` — **Weak-Area Prioritization After Assessments** — **COMPLETED (V061; owner host acceptance passed, 2026-11-17)** — Convert mock-exam outcomes into high-value skill recommendations and targeted review.
- `SPRINT_9.8` — **Final Review and Pre-Exam Study Mode** — Assemble high-yield review from weak skills, old mistakes, spaced repetition needs, and remaining time.
- `SPRINT_9.9` — **Exam Analytics, Reports, and Progress Comparison** — Show score movement, coverage, timing, section weakness, and confidence across assessment history.
- `SPRINT_9.10` — **Assessment Integrity, Accessibility, Failure Recovery, and Reliability** — Harden exam mode against accidental data loss, timer inconsistencies, unfair accessibility barriers, and invalid scoring states.

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

**Exam and assessment platform**

- Keep scoring/evidence server-authoritative and deterministic where possible.
- Calibrate readiness/confidence from evidence rather than LLM self-assessment.
- Prevent model changes from silently changing exam semantics.

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

<!-- PHASE_PRODUCTION_V6:START -->
# Phase Production Execution Contract v6 (ÖSYM YKS TYT/AYT & University Calculus 101 Exam Engine)

## 1. Entry Gate & Target Exam Blueprints (`ADR-011`)
- Unlocks after the 30-Day Commercial Wedge gate (`Sprint 11.9A`).
- Focuses sharply on our two highest-WTP exam verticals (`product/76_COMPETITIVE_INTELLIGENCE_AND_MARKET_POSITIONING_MATRIX.md`):
  1. **Turkish ÖSYM YKS (`TYT` Temel Matematik 40-question blueprint & `AYT` Matematik 40-question blueprint):**
     - `TYT` focus: Number sense, ratio/proportion, non-routine story-to-algebra translation (`F08`), speed under time pressure (`1.5 min/question`).
     - `AYT` focus: Polynomials, quadratics, inequalities, logarithms, sequences, limits & continuity, derivatives, integrals, and trigonometry with high domain-trap density (`F09`).
  2. **University Calculus 101 / AP Calculus AB-BC Midterm & Final Blueprint:**
     - Limits (`0/0` indeterminate forms, L'Hôpital), derivative rules (chain/product/quotient, implicit differentiation, optimization), and integration (`u`-substitution, integration by parts, definite areas) with pedantic notation checks (`F12`).

## 2. Signature Differentiator: Post-Mock "Deneme Sınavı Hata & Tuzak Karnesi" (`SPRINT_9.4` / `F28`)
- Instead of making students take a 135-minute mock test inside a phone screen, Phase 9 introduces **"Fotoğraflı Deneme Yanlış/Boş Analiz Kampı"**:
  - A student photographs the 5–10 questions they got wrong or left blank in their physical paper YKS deneme exam (e.g., Özdebir, 3D, Bilgi Sarmal, Apotemi) alongside their scratchpad work on the booklet.
  - Verified AI batch-processes those 5–10 attempts, separates **Knowledge Gaps (Bilgi Eksikliği)** from **Algebraic/Sign Mistakes (İşlem Hatası)** and **ÖSYM Domain Traps (Tanım Kümesi Tuzağı)**, calculates how many **Net (`+4.25 Net`)** they lost solely to avoidable mistakes, and generates a tailored 15-minute **Deneme Telafi Antrenmanı**.
<!-- PHASE_PRODUCTION_V6:END -->

<!-- PHASE_PRODUCTION_V7_LEARNING_OS:START -->
# Phase Production Execution Contract v7 — YKS Readiness Credible Intervals & Net-Gain Simulator (`2026-11-07`)

## 1. Entry Gate & Package Ownership (`ADR-011`)
- Phase 9 production rollout remains **gated behind `ADR-011`** (`verifiedai.exam.*` feature flags default to `false`), while `Sprint 9.1` foundation (`V054`) achieved `LOCAL_ACCEPTANCE_PASSED` (`2026-11-15`; `SPRINT_9.1_EXECUTION_REPORT.md` & `SPRINT_9.1_FILE_MANIFEST.md`), `Sprint 9.2` (`V055` candidate profiles, accommodations, horizon history, and Snap-to-Plan syllabus ingestion) achieved `LOCAL_ACCEPTANCE_PASSED` (`2026-11-15`; `SPRINT_9.2_EXECUTION_REPORT.md` & `SPRINT_9.2_FILE_MANIFEST.md`), `Sprint 9.3` (`V056` question bank, SME provenance verification, blueprint distribution rules, and deterministic seed-driven assessment assembly) achieved `LOCAL_ACCEPTANCE_PASSED` (`2026-11-15`; `SPRINT_9.3_EXECUTION_REPORT.md` & `SPRINT_9.3_FILE_MANIFEST.md`), `Sprint 9.4` (`V057` timed mock exam runtime, server-authoritative clock + 120s grace buffer, no-hint guard, and `F28` scratchpad ingestion) achieved `LOCAL_ACCEPTANCE_PASSED` (`2026-11-15`; `SPRINT_9.4_EXECUTION_REPORT.md` & `SPRINT_9.4_FILE_MANIFEST.md`), `Sprint 9.5` (`V058` deterministic scoring engine, ÖSYM $\text{Net} = C - 0.25W$ policy, evidence traceability, and regrade audit trail) achieved `LOCAL_ACCEPTANCE_PASSED` (`2026-11-16`; `SPRINT_9.5_EXECUTION_REPORT.md` & `SPRINT_9.5_FILE_MANIFEST.md`), and `Sprint 9.6` (`V059` exam readiness model, 90% credible-interval net projection, active-trap penalty attribution, `INV-EXAM-001` sparse-evidence `PRELIMINARY` guard, and Screen 9 Net-Gain Simulator) achieved `LOCAL_ACCEPTANCE_PASSED` (`2026-11-16`; `SPRINT_9.6_EXECUTION_REPORT.md` & `SPRINT_9.6_FILE_MANIFEST.md`).
- All backend exam domain models, targets, candidate profiles, syllabus ingestion, question bank items, blueprints, deterministic assembly, session timers, scratchpad ingestion, deterministic score snapshots, readiness projections, and `2026.2` Turkish TYT/AYT curriculum/question-bank extensions belong to `com.verifiedai.exam` (`services/api/src/main/java/com/verifiedai/exam/`), consuming `com.verifiedai.curriculum`, `com.verifiedai.mastery` (`bkt-lite-v1`/`v2`), and `com.verifiedai.mistake` (`V036`/`V037`) while emitting `CandidateGoalUpdatedEvent`, `QuestionBankItemPublishedEvent`, `AssessmentBlueprintActivatedEvent`, `AssessmentAssembledEvent`, `ExamSessionStartedEvent`, `ExamItemAnsweredEvent`, `ExamScratchpadAttachedEvent`, `ExamSessionSubmittedEvent`, `ExamScoreComputedEvent`, and `ExamReadinessComputedEvent` (`exam::events`) to downstream listeners, with Phase 9 persistence reconciled at **`V054`–`V060` (implemented, including `Sprint 9.6B` `V060` `LOCAL_ACCEPTANCE_PASSED`) and Option A remaining waves `V061 COMPLETED; OWNER HOST ACCEPTANCE PASSED` (`SPRINT_9.7` + `Bridge B4`) -> `V062` (`SPRINT_9.8`) -> `V063` (`SPRINT_9.9`) -> `V064` (`SPRINT_9.10`)** (superseding historical `V046–V047` placeholders; see `sprints/phase09_exam_and_assessment_platform/PHASE_09_TYT_AYT_BRIDGE_AND_REMAINING_SPRINTS_PLAN.md`).

## 2. Learning OS Canonical Specifications Governed by Phase 9
- `learning/86_LEARNING_OUTCOMES_RETRIEVAL_PRACTICE_AND_MASTERY_EVALUATION.md` (Quantity 5: Expected Net Score $\widehat{\text{Net}}_{u,E}$ with mandatory 90% credible interval $[\text{Net}_{\text{low}}, \text{Net}_{\text{high}}]$ and active-trap penalty $\sum \beta_m \theta_{u,m}$)
- `product/81_LEARNING_OS_PRODUCT_VISION_AND_POSITIONING.md` (Supporting Innovation `H-03`: YKS Net-Gain Simulator & High-ROI Topic Allocator $\Delta\text{Net} / \text{hr}$)
- `architecture/90_LEARNING_OS_DOMAIN_CONTRACTS_AND_PLATFORM_INTEGRATION.md` (`CNT-LOS-10` & `CNT-LOS-11`)
<!-- PHASE_PRODUCTION_V7_LEARNING_OS:END -->
