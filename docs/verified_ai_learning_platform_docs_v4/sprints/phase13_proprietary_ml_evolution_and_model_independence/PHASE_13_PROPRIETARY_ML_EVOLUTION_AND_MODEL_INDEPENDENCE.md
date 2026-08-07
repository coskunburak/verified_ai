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
