# API-First Hybrid AI Strategy and Model Evolution

## 1. Purpose

This document is the canonical strategy for how the platform uses external foundation models, deterministic mathematics, proprietary learning intelligence, and—only when justified by evidence—internally trained models.

It exists to prevent two equally damaging architecture mistakes:

1. treating a third-party LLM as the product and allowing provider behavior to become business logic; and
2. prematurely training or self-hosting models before the product has enough proprietary data, evaluation coverage, scale, or economic justification.

The default production strategy is **API-first, provider-neutral, verification-first, measurement-first, and progressively proprietary**.

---

## 2. Strategic statement

The platform does **not** train a GPT/Gemini-class foundation model from scratch.

Production V1 uses external multimodal/reasoning models behind internal capability ports. The platform's differentiated intelligence is built in-house through:

- canonical problem representation,
- deterministic verification,
- solver orchestration,
- uncertainty policy,
- mistake intelligence,
- mastery modeling,
- adaptive learning,
- exam/readiness logic,
- evaluation datasets,
- and cost-aware routing.

Later, high-volume and well-labeled tasks may be migrated to proprietary small models if they pass explicit quality and economics gates.

---

## 3. Four intelligence layers

### 3.1 Foundation Intelligence

Externally supplied general-purpose models provide capabilities such as:

- visual/math parsing support,
- natural-language reasoning,
- primary solving,
- optional independent solving,
- explanation generation,
- tutoring dialogue,
- controlled question generation.

External foundation models are replaceable infrastructure. They are not domain authorities.

### 3.2 Deterministic Intelligence

Owned by the platform and implemented as code/math engines:

- symbolic equivalence,
- equation solving checks,
- derivative/integral checks,
- numerical sampling,
- matrix verification,
- unit/constraint validation where supported,
- state machines,
- entitlement logic,
- scoring logic,
- deterministic mastery policies in early versions.

Deterministic intelligence should replace probabilistic calls whenever it can provide equal or higher product quality at lower cost and lower uncertainty.

### 3.3 Learning Intelligence

Owned by the platform:

- mistake taxonomy,
- attempt interpretation,
- skill evidence extraction,
- mastery state,
- knowledge graph,
- next-best-action selection,
- spaced repetition,
- study planning,
- exam readiness,
- personalized explanation preferences.

Learning intelligence is a durable product asset even if every foundation-model provider changes.

### 3.4 Proprietary ML

Potential future models trained or calibrated using eligible, governed data:

- skill classifier,
- mistake classifier,
- difficulty predictor,
- mastery predictor,
- recommendation/ranking model,
- specialized math solver candidate,
- retrieval/reranking models.

Proprietary ML is an optimization and differentiation stage, not a prerequisite for launch.

---

## 4. Production V1 model-use policy

### 4.1 Cheap-by-default routing

The router selects the least expensive model/capability combination that meets an approved quality threshold.

Examples:

- extraction/classification -> economy tier;
- ordinary solving -> standard tier;
- hard or ambiguous problems -> stronger reasoning tier;
- failed verification/disagreement -> conditional escalation;
- deterministic verification -> internal verifier, not an LLM judge where possible.

### 4.2 Secondary solver is conditional

A second independent solver is **not mandatory for every problem**.

It is invoked according to a risk policy using signals such as:

- unsupported or partially supported verification class,
- hard difficulty,
- parser uncertainty,
- primary-solver schema/semantic anomalies,
- deterministic verifier disagreement,
- high-stakes assessment context,
- premium-quality policy,
- sampled quality audit traffic.

This policy is central to controlling cost without weakening trust.

### 4.3 No self-reported confidence

An LLM's statement that it is “95% confident” is not a valid verification signal.

Confidence/status derives from measurable evidence:

- parser quality,
- solver agreement,
- deterministic checks,
- numerical checks,
- support coverage,
- historical calibration.

### 4.4 No direct provider dependency in product modules

Product modules request typed capabilities through internal ports. Provider/model names are runtime configuration and provenance, never product semantics.

---

## 5. Model evolution sequence

### Stage A — External APIs + deterministic verification

Goal: prove product value, quality, retention, and willingness to pay.

Requirements:

- provider-neutral gateway,
- usage ledger,
- per-stage cost telemetry,
- golden datasets,
- prompt/schema versioning,
- verification evidence,
- failure/fallback policies.

### Stage B — Cost and quality observability

Before replacing any model call, measure:

- calls/month,
- cost/month,
- cost/successful output,
- p50/p95 latency,
- quality pass rate,
- human/user correction rate,
- downstream verification success,
- revenue/entitlement context.

### Stage C — Replace simple repeated tasks

Candidate tasks:

- problem/skill classification,
- mistake classification,
- difficulty estimation,
- simple routing features.

Possible technologies:

- deterministic rules,
- embeddings + nearest-neighbor,
- gradient boosting,
- small classifiers,
- compact fine-tuned models.

### Stage D — Proprietary learning models

Only after enough longitudinal learner evidence exists:

- mastery prediction,
- recommendation ranking,
- learning-gain prediction,
- adaptive sequencing.

These models must be compared against simple deterministic baselines. More complex is not automatically better.

### Stage E — Specialized math model candidate

A self-hosted/fine-tuned solver is considered only if:

- external solving is a material recurring COGS line,
- a sufficiently large verified dataset exists,
- the task distribution is stable,
- quality is objectively measurable,
- self-hosted total cost is lower at realistic utilization,
- operational burden is acceptable,
- fallback to external models remains available.

---

## 6. Non-goals

The project will not:

- train a frontier foundation model from scratch;
- collect student data merely because it might be useful later;
- use private student content for training by default;
- deploy a fine-tuned model without golden-dataset and shadow evaluation;
- self-host a model only to claim “our own AI”;
- accept lower answer quality purely to reduce inference cost;
- make a proprietary model a single point of failure.

---

## 7. Economic principle

The primary engineering unit is:

> **Cost per successful, policy-compliant learning outcome**, not cost per raw API call.

At minimum track:

- cost per parsed problem,
- cost per solved problem,
- cost per verified solution,
- cost per tutor session,
- cost per paid learner/month,
- AI COGS as a percentage of net revenue,
- incremental cost of secondary solver/escalation.

Model replacement decisions require both quality and total-cost evidence.

---

## 8. Quality principle

A cheaper route may be promoted only when it meets the capability's quality gate.

A proprietary model may be promoted only when it matches or exceeds the approved baseline on:

- task accuracy,
- calibration where relevant,
- verification compatibility,
- latency/SLO,
- safety/robustness,
- cost,
- operational reliability.

Quality regressions cannot be hidden behind aggregate average cost savings.

---

## 9. Data principle

Production usage is not automatically training data.

Every example must have an explicit eligibility state, lineage, retention policy, and legal/product basis before it may enter a training or fine-tuning dataset.

See:

- `ai/58_PROPRIETARY_DATASET_GOVERNANCE_AND_TRAINING_ELIGIBILITY.md`
- `data/62_TRAINING_DATA_LINEAGE_CONSENT_AND_DATASET_PIPELINE.md`

---

## 10. Architectural consequence

The system must support multiple inference backends through the same capability contracts:

```text
Feature/Application Module
        |
        v
    AiCapabilityPort
        |
        v
     ModelRouter
   /      |       \
External  Proprietary  Deterministic/Rule
 API      Model         Path
   \      |       /
        v
   Schema Validation
        |
        v
 Verification / Domain Policy
```

The product must continue functioning when one provider is removed or a proprietary model is rolled back.

---

## 11. Release philosophy

Every new model route follows:

1. offline evaluation;
2. cost benchmark;
3. shadow traffic where feasible;
4. sampled internal review;
5. canary rollout;
6. metric comparison;
7. progressive promotion;
8. instant rollback capability.

Model changes are production releases, not prompt experiments.

---

## 12. Source-of-truth relationship

This document governs the strategic sequence. Detailed implementation rules live in:

- `ai/25_AI_ORCHESTRATION_AND_MODEL_ROUTING.md`
- `ai/28_AI_EVALUATION_AND_GOLDEN_DATASET.md`
- `ai/29_AI_COST_LATENCY_AND_RELIABILITY.md`
- `ai/59_SMALL_MODEL_FINE_TUNING_AND_SELF_HOSTING_READINESS.md`
- `ai/60_AI_UNIT_ECONOMICS_AND_INFERENCE_COST_ENGINEERING.md`
- `quality/64_AI_MODEL_REPLACEMENT_DECISION_GATES.md`

If implementation code implies a different model strategy, the code is wrong unless an ADR explicitly changes this policy.
