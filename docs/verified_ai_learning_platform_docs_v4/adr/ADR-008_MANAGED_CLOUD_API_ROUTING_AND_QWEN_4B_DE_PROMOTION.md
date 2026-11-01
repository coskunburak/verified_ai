# ADR-008 — Managed Cloud API Routing and Qwen3.5-4B De-Promotion

- **Status:** Accepted (2026-10-31)
- **Decision:** Suspend `Qwen/Qwen3.5-4B` (`vLLM`) as a Production V1 primary solver candidate and relegate `VllmSolveProviderAdapter` to offline/local CI testing only. Adopt a managed cloud API routing stack (`Gemini 2.5 Flash` for multimodal OCR/normalization, `GPT-4.1-mini` for default structured solving and tutoring, and `o4-mini` for verifier-triggered escalation solving) behind the existing provider-neutral ports.

## Context (Bğlam ve Kanıtlar)

Sprint 5.4-RM selected `Qwen/Qwen3.5-4B@851bf6e806efd8d0a36b00ddf55e13ccb7b8cd0a` as an isolated research candidate served via `vLLM 0.17.1` (`VllmSolveProviderAdapter`). Deep technical and economic audit (`ai/77_PRODUCTION_MATH_MODEL_BENCHMARK_AND_BUILD_VS_BUY_STRATEGY.md` and `roadmap/80_UNIT_ECONOMICS_30_DAY_LAUNCH_CONTRACT_AND_EXECUTION_ROADMAP.md`) established three blocking realities:

1. **Mathematical Reasoning & Schema Fragility:** A 4B-parameter dense model exhibits high syntax/schema drop rates on nested `CanonicalMathDocument` (`canonical_v1` / `canonical_v2`) JSON contracts and systematically fails on multi-step Turkish YKS (TYT/AYT) contextual word problems and implicit domain restrictions.
2. **Negative TCO at Early Scale:** Running a dedicated 24/7 GPU (`L4` / `A10G` / `H100`) for evening-peaked student traffic (19:00–23:30 local time, `<18%` daily average utilization) costs `$650–$3,565/month` fixed before operational overhead, whereas managed API routing for 1,000 paid learners (`50,000` solves/month) costs `~$290.50/month` total (`~$0.00581/solve`).
3. **Contradiction with ADR-005 and ADR-007:** `adr/ADR-005_API_FIRST_PROGRESSIVELY_PROPRIETARY_AI_STRATEGY.md` and `adr/ADR-007_SELF_HOSTED_MODELS_ONLY_AFTER_TCO_AND_QUALITY_GATES.md` explicitly forbid self-hosted production inference before live API baselines, quality gates, and TCO thresholds (`>25,000` paid learners or `>$4,000/month` single-task API spend) are reached.

## Decision Details (Üretim Yönlendirme Mimarisi)

1. **R1 — Multimodal OCR & Layout Parsing (`VISION_PARSE` / `PROBLEM_NORMALIZE`):**
   - Primary: `Gemini 2.5 Flash` (`$0.30/1M` input, `$2.50/1M` output).
   - Fallback: `Qwen2.5-VL-72B-Instruct` or `GPT-4.1-mini` via managed API.
2. **R2 — Primary Structured Solving & Step Generation (`PRIMARY_SOLVE`):**
   - Primary: `GPT-4.1-mini` (`$0.40/1M` input, `$1.60/1M` output) with strict JSON Schema enforcement (`response_format`).
   - Secondary/Fallback: `Gemini 2.5 Flash` or hosted `Qwen3-30B-A3B` / `DeepSeek-V3`.
3. **R3 — Verifier-Triggered Escalation & Blind Secondary Solving (`SECONDARY_SOLVE`):**
   - Triggered **only** when `R2` fails deterministic verification in `services/math-verifier`, returns `NEEDS_REVIEW`, or problem difficulty is classified as `HARD_TAIL` / `AYT_MULTI_STEP`.
   - Primary: `OpenAI o4-mini` (`$1.10/1M` input, `$4.40/1M` output) with bounded `max_completion_tokens` (`<= 2048`).
   - Secondary: `DeepSeek-R1-0528` (managed zero-retention endpoint).
4. **R4 — Socratic Hints & Mistake Explanation (`TUTOR_EXPLAIN`):**
   - Primary: `GPT-4.1-mini` conditioned strictly on deterministic verification evidence and step-equivalence diffs produced by `math-verifier`.

## Consequences

- `OpenAiSolveProviderAdapter` and `GeminiVisionProviderAdapter` become the mandatory Sprint 6.2B/6.3 priority so the platform can execute live end-to-end verified solves.
- `VllmSolveProviderAdapter` and `infra/local/qwen-model-release.json` remain intact for offline deterministic contract tests and future Phase 13 benchmarking, but are frozen for V1 launch.
- All cloud API calls remain governed by `PostgresAiSpendGuard` and `VerificationPolicy`.
