# Documentation V6 Changelog — Deep Research, Competitive Strategy, Managed API Reset & Focused V1 Launch

## Purpose

V6 records the 2026-10-31 comprehensive production architecture audit, competitive tear-down across 13 mathematics learning products, mathematical AI model and TCO evaluation, symbolic verification engine V2 specification, pedagogical mistake intelligence (`Analyze My Work`), unit economics model, and the 30-Day Focused MVP Launch Contract.

## Added

### Accepted ADRs
- `adr/ADR-008_MANAGED_CLOUD_API_ROUTING_AND_QWEN_4B_DE_PROMOTION.md`
- `adr/ADR-009_CANONICAL_SOLUTION_SET_ALGEBRA_AND_DOMAIN_GUARD.md`
- `adr/ADR-010_NATIVE_2D_MATH_RENDERING_AND_STEP_DIFF_HIGHLIGHTING.md`
- `adr/ADR-011_FOCUSED_V1_COMMERCIAL_LAUNCH_GATE_BEFORE_PHASE_08_13.md`

### Master Strategic & Production Engineering Specifications
- `architecture/75_PRODUCTION_ARCHITECTURE_AUDIT_AND_EVIDENCE_LEDGER.md`
- `product/76_COMPETITIVE_INTELLIGENCE_AND_MARKET_POSITIONING_MATRIX.md`
- `ai/77_PRODUCTION_MATH_MODEL_BENCHMARK_AND_BUILD_VS_BUY_STRATEGY.md`
- `ai/78_SYMBOLIC_VERIFICATION_ENGINE_V2_AND_DOMAIN_TRAP_RADAR.md`
- `learning/79_PEDAGOGICAL_DIAGNOSIS_HANDWRITING_UX_AND_RETENTION_ENGINE.md`
- `roadmap/80_UNIT_ECONOMICS_30_DAY_LAUNCH_CONTRACT_AND_EXECUTION_ROADMAP.md`

## Updated

- `00_MASTER_INDEX.md`
- `ai/72_OPEN_WEIGHT_MATH_MODEL_RESEARCH.md`
- `ai/73_MATH_MODEL_EVALUATION_PLAN.md`
- `ai/74_MODEL_ROUTING_COST_AND_TCO_DECISION.md`
- `roadmap/43_PRODUCT_AND_TECHNICAL_ROADMAP.md`
- `quality/65_CANONICAL_DOCUMENTATION_TREE_AND_SOURCE_OF_TRUTH_MAP.md`
- `DOCUMENTATION_MANIFEST.md`

## Key Strategic & Technical Decisions

1. **Managed Cloud API Routing for V1 (`ADR-008`):** `Qwen/Qwen3.5-4B` (`vLLM`) is suspended as a V1 primary solver candidate and relegated to offline/local CI testing. Production V1 uses `Gemini 2.5 Flash` (`VISION_PARSE` / `PROBLEM_NORMALIZE`), `GPT-4.1-mini` (`PRIMARY_SOLVE` / `TUTOR_EXPLAIN`), and verifier-triggered `OpenAI o4-mini` (`SECONDARY_SOLVE` escalation).
2. **Symbolic Verification V2 & Domain Trap Radar (`ADR-009`):** `CanonicalMathDocument v2` and `services/math-verifier` expand beyond single-equality roots to support `FiniteSet`, `Interval`, `Union`, `DomainConstraintGuard` (`extraneous_roots` detection), and deterministic `CounterexampleEngine`.
3. **Native 2D Math Typesetting (`ADR-010`):** `MathematicalExpressionView.swift` upgrades from plain monospaced text to native 2D mathematical rendering (`SwiftMath`) with step-diff highlighting and preserved VoiceOver spoken math.
4. **Focused 30-Day Launch Gate Before Phases 8–13 (`ADR-011`):** Phases 8–13 are frozen until the core commercial wedge (`Live Cloud Solving` + `Analyze My Work / First Wrong Step Diagnosis` + `Domain Restriction Trap Radar`) is validated with at least 50 target students on TestFlight.
