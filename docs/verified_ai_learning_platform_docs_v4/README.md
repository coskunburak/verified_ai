# Verified AI Learning Platform — Documentation Repository

This folder contains the canonical semantic and technical specification for a production-grade AI mathematics learning platform.

Start with **`00_MASTER_INDEX.md`**.

## Core stack

- iOS: Swift + SwiftUI
- Backend: Java + Spring Boot modular monolith
- Database: PostgreSQL
- Cache: Redis
- Binary storage: S3-compatible object storage
- Math verification: internal Python + FastAPI + SymPy service
- AI: provider-neutral adapters

## Architectural thesis

The product is not an LLM wrapper.

Its durable value is:

**Problem Intelligence + Verification + Mistake Intelligence + Mastery + Adaptive Learning + Structured Student History**

## AI agents

NotebookLM/Codex should read `integrations/44_AI_AGENT_CONTEXT_AND_READING_ORDER.md` before making architecture assumptions.

## Production implementation program

- Read `quality/56_COMPLETE_PRODUCTION_REPOSITORY_FILE_HIERARCHY.md` before creating new folders or relocating production code.
- Read `sprints/00_SPRINT_MASTER_PLAN.md` for the complete Phase 1 → Phase 12 execution sequence.
- Each phase folder contains a phase exit gate and individual `SPRINT_<phase>.<sequence>_*.md` production delivery documents.

<!-- HYBRID_AI_STRATEGY_V3:START -->
## Documentation v3 update — API-first hybrid AI strategy

This knowledge base now formalizes a staged AI strategy:

- launch with provider-neutral external APIs;
- own deterministic verification and learner intelligence;
- measure per-stage AI quality/latency/cost from day one;
- invoke independent secondary solving only when risk policy requires it;
- treat production student data as non-training data by default;
- replace bounded high-volume tasks with proprietary models only after formal gates;
- consider specialized/self-hosted math inference only after quality and total-cost proof.

The sprint program now contains **Phase 13 — Proprietary ML Evolution and Model Independence**, which is conditional and may never execute if APIs remain economically/technically superior.
<!-- HYBRID_AI_STRATEGY_V3:END -->

## V4 exhaustive hierarchy release

The complete repository topology is defined in `quality/56_COMPLETE_PRODUCTION_REPOSITORY_FILE_HIERARCHY.md`. The complete Markdown knowledge-base tree is indexed by `quality/65_CANONICAL_DOCUMENTATION_TREE_AND_SOURCE_OF_TRUTH_MAP.md`, and ambiguous file placement is resolved by `quality/66_FILE_PLACEMENT_OWNERSHIP_AND_DEPENDENCY_MATRIX.md`. The packaged V4 archive contains every Markdown document, including all Phase and Sprint execution contracts.

<!-- LEARNING_OS_STRATEGY_V7:START -->
## V7 Learning OS strategic expansion

The knowledge base now includes the **Verified AI Learning OS** strategic expansion (Documents `81`–`92` and upgraded Phase 7–12 production sprint specifications):

- `product/81_LEARNING_OS_PRODUCT_VISION_AND_POSITIONING.md` & `product/82_LEARNING_OS_MARKET_COMPETITOR_AND_CUSTOMER_RESEARCH.md` — Learning OS product positioning, 10-step Verified Learning Value Loop, and 16-competitor market intelligence.
- `learning/83_VERIFIED_MISTAKE_DNA_AND_MISTAKE_PASSPORT_SPECIFICATION.md`, `learning/84_VERIFIED_TWIN_PRACTICE_AND_INDEPENDENT_LEARNING_ENGINE.md`, `learning/85_PERSONAL_AI_MATHEMATICS_COACH_AND_DAILY_LEARNING_EXPERIENCE.md`, & `learning/86_LEARNING_OUTCOMES_RETRIEVAL_PRACTICE_AND_MASTERY_EVALUATION.md` — Verified Mistake DNA, 3-variant Twin Practice Engine, Daily 15-Minute AI Coach, and delayed retrieval mastery evaluation.
- `roadmap/87_LEARNING_OS_STRATEGIC_PHASE_AND_SPRINT_EXPANSION_PROGRAM.md` & `roadmap/88_LEARNING_OS_MONETIZATION_GROWTH_AND_VALIDATION_PLAN.md` — Reconciled Phase 7–12 sprint sequence (`V030`–`V036` active baseline + `PLANNED V037+`), solo-founder execution gates, and unit-economics-guarded monetization.
- `security/89_STUDENT_COACH_PRIVACY_MINOR_SAFETY_AND_TEACHER_ACCESS.md`, `architecture/90_LEARNING_OS_DOMAIN_CONTRACTS_AND_PLATFORM_INTEGRATION.md`, `quality/91_LEARNING_OS_PRODUCT_LEARNING_AND_RELEASE_ACCEPTANCE_GATES.md`, & `operations/92_LEARNING_OS_ASSUMPTIONS_EXPERIMENTS_AND_EVIDENCE_LEDGER.md` — Minor safety, domain contracts, release acceptance gates, and epistemic evidence ledger.
<!-- LEARNING_OS_STRATEGY_V7:END -->
