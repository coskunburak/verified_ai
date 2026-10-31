# Phase 13 — Proprietary ML Evolution and Model Independence

## Status

**Conditional post-launch phase.** This phase is not a prerequisite for Production V1/V1.5/V2 and must not begin merely because proprietary AI is attractive technically.

## Entry gates

Phase 13 may begin only when:

- production product-market/usage evidence exists;
- AI usage ledger is reliable;
- material recurring tasks/costs are measurable;
- protected golden/holdout datasets exist;
- training eligibility/data-lineage governance is implemented;
- privacy/security review approves the intended data use;
- deterministic/external baselines are known;
- model registry/rollback path can be implemented without destabilizing core product.

## Objective

Move selected bounded tasks from external inference to proprietary models **only where** quality, learner value, reliability, latency or total cost demonstrably improves. Preserve provider-neutral contracts and external fallback.

## Strategy order

1. cost/opportunity map;
2. governed datasets;
3. bounded classifiers;
4. calibrated predictors/rankers;
5. fine-tuning infrastructure;
6. specialized solver experiment;
7. self-hosting TCO/capacity proof;
8. shadow/canary hybrid rollout.

## Non-goals

- foundation-model pretraining;
- eliminating external APIs at all costs;
- training on production student data by default;
- replacing deterministic verification with a model;
- accepting quality regression for branding purposes.

## Exit gate

Phase 13 is successful if at least one proprietary route is either:

- promoted with clear quality/economic benefit and safe fallback; or
- rejected with high-confidence evidence that external/deterministic routes remain superior.

A scientifically justified “do not self-host” outcome is a valid success.

<!-- PHASE_PRODUCTION_V3:START -->
# Phase Production Contract v3

## Phase objective alignment

**Proprietary ML evolution**

- Introduce proprietary models only after data, evaluation, economic and operational readiness gates.
- Start with bounded classifiers/predictors before specialized solving.
- Roll out through offline -> shadow -> canary -> production with immediate external fallback.

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
# Phase Production Execution Contract v6 (Quantitative 6-Gate Trigger & Open-Weight Distillation Architecture)

## 1. Hard Quantitative Entry Gates (`ADR-005`, `ADR-007`, `ADR-008`, `ai/77_PRODUCTION_MATH_MODEL_BENCHMARK_AND_BUILD_VS_BUY_STRATEGY.md`)
No sprint in Phase 13 may provision production GPUs or train production weights until **all 6 gates** are simultaneously satisfied:
1. **Commercial Scale Gate:** `>= 25,000` active paid subscribers OR `>= $4,000/month` recurring cloud API spend on a single narrow capability (`PROBLEM_NORMALIZE`, `MISTAKE_CLASSIFY`, or `SOLVE`).
2. **Traffic Utilization Gate:** Sustained request volume sufficient to maintain `>= 35%` 24-hour average GPU utilization (eliminating the idle evening-only student traffic penalty).
3. **Consent-Clean Verified Corpus Gate:** `>= 50,000` real production trajectories where `training_eligible = true` (`REQ-PRIV-002`), PII is stripped, and `services/math-verifier` has deterministically verified the output (`VERIFIED`).
4. **Quality Parity Gate:** Candidate fine-tuned open-weight model (`Qwen3-8B`, `Qwen3-30B-A3B`, or `Qwen2.5-VL-7B`) matches or beats `GPT-4.1-mini` / `Gemini 2.5 Flash` on the 500-case `verified-ai-eval-v2` suite with **`0.0%` false `VERIFIED` rate** and **`>= 99.0%` JSON schema compliance**.
5. **TCO Superiority Gate:** All-in self-hosted/serverless GPU cost (including idle hours, cold-start fallbacks, and observability) reduces per-call cost by **`>= 35%`** compared to managed API pricing at the time of evaluation.
6. **Zero-Downtime Shadow & Rollback Gate:** Candidate model runs in shadow mode (`SPRINT_13.9`) for 14 days with instant feature-flag rollback to `OpenAiSolveProviderAdapter` / `GeminiVisionParseProviderAdapter`.

## 2. Prioritized Distillation Order When Phase 13 Unlocks
1. **Task 1 — `MISTAKE_CLASSIFY` & Step-Diff Classifier (`Qwen3-8B` LoRA):** Narrow 18-class classification task with highest margin reduction and lowest hallucination surface.
2. **Task 2 — Handwritten Math Line Segmenter & LaTeX Transcriber (`Qwen2.5-VL-7B` LoRA):** Trained on consent-clean handwritten Turkish/English notebook lines paired with verified `SymPy` parses.
3. **Task 3 — Verifier-Guided GRPO / RLVR Algebra & Calculus Solver (`Qwen3-30B-A3B` MoE):** Uses `services/math-verifier` (`SymPy` step equivalence + `DomainConstraintGuard`) as a deterministic binary reward function (`R = 1.0` iff `VERIFIED` and `0` extraneous roots).
<!-- PHASE_PRODUCTION_V6:END -->
