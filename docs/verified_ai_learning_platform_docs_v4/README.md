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
