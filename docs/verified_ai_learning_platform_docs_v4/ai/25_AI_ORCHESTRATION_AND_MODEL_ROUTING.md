# AI Orchestration and Model Routing

## 1. Role of the AI subsystem

The AI subsystem supplies probabilistic capabilities to product modules without becoming the authority for verification, billing, mastery, scoring, or privacy decisions.

Production strategy: **API-first, provider-neutral, cheap-by-default, conditional escalation, progressively proprietary only after evidence.**

Read first:

- `ai/57_API_FIRST_HYBRID_AI_STRATEGY_AND_MODEL_EVOLUTION.md`
- `adr/ADR-003_PROVIDER_NEUTRAL_AI.md`
- `adr/ADR-005_API_FIRST_PROGRESSIVELY_PROPRIETARY_AI_STRATEGY.md`

## 2. Capability contract

Product modules request stable capabilities such as:

- `VISION_PARSE`
- `PROBLEM_NORMALIZE`
- `PROBLEM_CLASSIFY`
- `SOLVE`
- `ARBITRATE`
- `EXPLAIN`
- `MISTAKE_CLASSIFY`
- `TUTOR`
- `PRACTICE_GENERATE`

A capability request includes typed policy context, not arbitrary provider parameters.

Sprint 4.4 implementation note: `VISION_PARSE` is the first provider-neutral capability subset pulled forward before Sprint 5.1. It exists so the problem module can request raw recognition evidence without importing provider SDKs.

Sprint 4.5 implementation note: `PROBLEM_NORMALIZE` extends the same early subset for parser-level normalization of accepted RecognitionEvidence into `ProblemParse` revisions. It reuses `AiModelGateway`, provider adapters, route provenance, usage/cost/latency accounting, bounded retries, and production fixture-provider guards. This early subset still does not complete the full model router, solver routing, tutor routing, route optimizer, secondary-solver policy, or any future solve/tutor capabilities.

## 3. Primary port

`AiModelGateway` or equivalent capability port is implemented by adapters. Product/domain code must not import vendor SDK classes.

Adapters may include:

- external provider A;
- external provider B;
- future proprietary inference service;
- deterministic/rule path for bounded tasks.

## 4. ModelRouter input

The router considers:

- capability;
- canonical problem class;
- difficulty/support coverage;
- parser confidence/evidence;
- verification method availability;
- entitlement tier;
- latency budget;
- per-operation cost budget;
- provider health/quota;
- feature flag/experiment;
- prior route failure;
- locale/language capability;
- privacy/data constraints.

## 5. Route plan output

The router returns an immutable `RoutePlan` containing:

- route policy version;
- provider/adapter;
- model deployment/version metadata;
- prompt/schema version;
- timeout;
- retry policy;
- fallback chain;
- max cost budget;
- secondary-solver policy;
- verification requirements;
- provenance tags.

Persist enough provenance to reconstruct the decision later.

## 6. Economy / Standard / Strong tiers

### Economy

Use for bounded extraction/classification when evaluation proves sufficient quality.

### Standard

Use for ordinary solving, explanations, and tutoring.

### Strong reasoning

Use for hard-tail cases, unsupported deterministic verification, disagreement, or explicitly premium/high-quality policy.

Model names remain configuration.

## 7. Conditional secondary solver policy

A secondary solver is **not universally invoked**.

Possible escalation triggers:

- deterministic verifier unavailable;
- primary solution fails verifier;
- hard problem class;
- high parser uncertainty;
- primary semantic anomaly;
- assessment policy requires corroboration;
- sampled QA traffic;
- historical risk calibration.

Log an `escalation_reason` whenever invoked.

## 8. Independence requirement

When a secondary solver is used:

- it receives the canonical problem, not hidden primary reasoning;
- preferably vary provider/model/prompt strategy for error independence;
- do not leak the primary final answer unless an arbitration stage explicitly requires comparison.

## 9. Deterministic-first opportunities

Before routing to AI, check whether the task can be completed safely using:

- canonical parser/rules;
- SymPy/math verifier;
- curriculum lookup;
- cached immutable artifact;
- lightweight local classifier after future rollout.

## 10. Fallback

Fallback is controlled by policy and overall deadline. Avoid retry storms.

Typical sequence:

1. same route retry only for transient errors;
2. alternative approved provider/model;
3. stronger route if quality failure requires it and budget permits;
4. degrade optional feature;
5. explicit recoverable failure/unverified state.

## 11. Circuit breaking and health

Track per provider/model/capability:

- success;
- 429/5xx;
- schema invalid;
- p50/p95 latency;
- evaluation quality;
- estimated cost;
- quota headroom.

## 12. Structured output boundary

Provider response pipeline:

```text
Provider bytes/text
 -> syntactic parse
 -> JSON/schema validation
 -> semantic validation
 -> canonical normalization
 -> domain mapping
```

Schema-valid AI output remains untrusted until relevant domain/verification policy accepts it.

## 13. Context minimization

Send only necessary context:

- current canonical problem;
- minimal skill/curriculum definitions;
- bounded mastery summary for tutor;
- selected mistakes if needed.

Never default to entire history or raw unrelated PII.

## 14. Usage ledger

Every material call records:

- capability;
- route policy;
- provider/model/deployment;
- prompt/schema versions;
- token/image units;
- cache status;
- latency;
- estimated/actual provider cost;
- result status;
- escalation/fallback reason;
- trace ID.

Sprint 4.4 stores recognition-stage usage/cost/latency and provider/model/prompt/schema provenance on `recognition_evidence`. The future consolidated `ai_usage` ledger can ingest or reference that evidence without changing problem-domain meaning.

## 15. Cost-aware routing rule

The router may choose cheaper approved routes but may not downgrade truthfulness. A cost budget cannot convert a failed verification into a verified result.

## 16. Future proprietary models

Proprietary models implement the same capability contracts. They are added only after `quality/64_AI_MODEL_REPLACEMENT_DECISION_GATES.md` is satisfied.

A proprietary model is not special-cased in feature code.

## 17. Ownership boundaries

- AI module: which capability route to invoke and how.
- Verification: whether solution evidence supports verification status.
- Learning modules: what attempts mean for mastery/recommendations.
- Billing: what user may access.
- Operations: whether route is healthy/cost-compliant.

## 18. Required tests

- routing policy unit tests;
- provider adapter contract tests;
- schema-invalid behavior;
- fallback/circuit-breaker tests;
- cost budget tests;
- conditional secondary-solver tests;
- provenance persistence;
- feature-flag rollback tests;
- golden-dataset evaluation for material route changes.
