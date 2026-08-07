# Documentation V3 Changelog — Hybrid AI, Cost Engineering, and Proprietary-Model Readiness

## Summary

V3 updates the entire knowledge base to reflect the accepted strategy:

- external provider-neutral foundation-model APIs for initial production;
- platform-owned deterministic verification and learning intelligence;
- cheap-by-default routing and conditional secondary solving;
- full AI unit-cost telemetry from day one;
- production student data not training data by default;
- deterministic/simple baselines before learned models;
- proprietary small models only after explicit quality/data/economics gates;
- self-hosted model serving only after realistic TCO/capacity proof;
- external fallback retained for hard-tail and rollback scenarios.

## New canonical AI/Data/Operations documents

- AI strategy and model evolution (57)
- dataset governance/training eligibility (58)
- fine-tuning/self-hosting readiness (59)
- AI unit economics (60)
- model registry/release/rollback (61)
- training-data lineage pipeline (62)
- AI FinOps/capacity operations (63)
- model replacement decision gates (64)

## New ADRs

- ADR-005 API-first progressively proprietary AI
- ADR-006 production student data not training data by default
- ADR-007 self-hosted models only after TCO/quality gates

## Sprint program changes

- all existing sprint files received a Production Execution Specification v3;
- Sprint 5.5 was renamed/clarified as a **conditional** independent secondary-solver capability;
- a sprint-wide production execution standard was added;
- conditional Phase 13 with 12 proprietary-ML/model-independence sprints was added;
- every sprint now explicitly covers AI cost impact, training-data guardrails, failure behavior, rollout/rollback, evidence, and documentation synchronization.

## Repository hierarchy changes

The canonical file hierarchy now includes optional future `ml/`, `services/model-inference/`, dataset schema and model-serving infrastructure paths. These must not be materialized before the relevant Phase 13 gates.

## Important precedence

If an older paragraph conflicts with ADR-005/006/007 or canonical documents 57–64, the newer strategy documents take precedence unless a later accepted ADR explicitly reverses the decision.
