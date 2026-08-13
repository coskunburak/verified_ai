# Phase 4 — Problem Capture & Canonicalization
## Phase objective
This phase groups the production delivery units required for **Problem Capture & Canonicalization**. It must leave the repository in a coherent, testable state and may not transfer unresolved foundational risk into later phases without an explicit recorded exception.
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
- `SPRINT_4.1` — **Premium Camera Capture and Import Experience** — Build the primary camera/gallery/PDF entry experience with permissions, framing, quality guidance, cropping, and accessibility.
- `SPRINT_4.2` — **Presigned Asset Upload and Object Storage Lifecycle** — Upload original images directly to object storage through backend-authorized reservations with checksums, size limits, metadata, and retention policy.
- `SPRINT_4.3` — **Image Preprocessing and Capture Quality Pipeline** — Normalize perspective, orientation, contrast, compression, and quality signals without altering mathematical meaning.
- `SPRINT_4.4` — **Vision/OCR Ingestion and Raw Recognition Evidence** — Extract textual and visual math evidence while preserving uncertainty and source coordinates for later user correction.
- `SPRINT_4.5` — **Structured Problem Parser and Versioned Output Schema** — Transform recognition output into schema-validated subject/topic/task/expression/variables/constraints representation.
- `SPRINT_4.6` — **Canonical Mathematical Representation and Safe Parsing** — Create provider-independent normalized mathematical representations that deterministic verifiers can safely consume.
- `SPRINT_4.7` — **Problem Classification: Subject, Topic, Skill, Type, and Difficulty** — Map problems to stable curriculum skills and machine-usable problem types with confidence and review states.
- `SPRINT_4.8` — **User-Correctable Parse Review and Revision History** — Let users repair OCR/parser mistakes before solving while retaining immutable revision history and provenance.
- `SPRINT_4.9` — **Problem Session, History, Retry, and Recovery Experience** — Persist problem lifecycle state so uploads, parsing, corrections, retries, and later history survive app termination and transient failures.
- `SPRINT_4.10` — **Ingestion Golden Dataset, Accuracy Gates, and Production Hardening** — Create measurable parser/OCR quality baselines, regression tests, difficult-input fixtures, and release gates.

## Phase exit gate
The phase is complete only when every sprint-specific acceptance criterion is satisfied, phase-level integration tests pass, documentation is current, no unresolved P0/P1 defects remain in the phase scope, and the next phase can consume the delivered contracts without relying on undocumented assumptions.

## Sprint 4.10 closeout status

Sprint 4.10 adds the local deterministic ingestion evaluation foundation for OCR recognition, parser normalization, canonicalization handoff, and classification checks. The committed corpus is synthetic, explicitly non-training-eligible, and covered by schema validation, coverage checks, local fixture evaluation, baseline comparison, and CI wiring through `make eval-ai`.

This does not complete production-provider accuracy validation. Connected representative evaluation is blocked by the absence of an approved provider route in this workspace, and protected holdout evaluation is blocked because the restricted payload is not available in the repository. Phase 4 therefore has local implementation evidence, but production exit remains limited by `TD-CAPTURE-001`, `TD-AI-001`, `TD-AI-002`, `TD-AI-003`, `TD-AI-004`, and `TD-AI-005`.

Phase 5 solving, verification verdicts, tutoring, attempts, mastery, and broader AI evaluation were not started by Sprint 4.10.

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

**Problem ingestion and canonicalization**

- Create stable canonical representations that allow providers/models to be replaced later.
- Record correction/evidence signals without treating them as automatic training labels.
- Use economical parsing/classification routes subject to evaluation gates.

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
