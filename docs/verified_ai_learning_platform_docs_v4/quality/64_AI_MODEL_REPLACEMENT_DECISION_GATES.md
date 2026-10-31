# AI Model Replacement Decision Gates

## 1. Purpose

This checklist is mandatory before replacing an approved external route with a cheaper external model, a deterministic implementation, or a proprietary model.

## 2. Gate A — Product need

- Is the task important enough to optimize?
- Is there measured cost, latency, reliability, privacy, or differentiation value?
- Is the task contract stable?

## 3. Gate B — Data/evaluation readiness

- Golden dataset exists and is representative.
- Hard-tail cases are represented.
- Protected test set is uncontaminated.
- Metrics are defined before experiment.
- Training data, if any, is eligible and lineage-tracked.

## 4. Gate C — Quality

Candidate meets approved thresholds for:

- accuracy/task metric;
- verifier pass/compatibility;
- calibration where relevant;
- correction/failure distribution;
- critical skill/language slices;
- robustness.

No aggregate average may hide severe regressions in a critical slice.

## 5. Gate D — Economics

Compare realistic total cost, including:

- training;
- serving;
- idle capacity;
- engineering;
- monitoring;
- incident burden;
- fallback traffic.

Require a documented savings or strategic benefit margin rather than break-even optimism.

## 6. Gate E — Operations

- Observability exists.
- Capacity is tested.
- Security is reviewed.
- Rollback is immediate.
- External fallback remains available when required.

## 7. Gate F — Online evidence

- Shadow evaluation passed.
- Canary metrics passed.
- No unacceptable learner-impact regression.
- Cost improvement appears in real traffic.

## 8. Final decision artifact

Every replacement produces a short decision record containing baseline, candidate, evaluation, economics, rollout, owner, and rollback plan.

## Sprint 5.12 fail-closed implementation

The solver comparator returns PASS, FAIL or BLOCKED with explicit evidence scope.
Critical false verification, scope widening, ignored contradiction, unsupported coercion,
schema bypass, privacy/authorization violation, prompt injection and fabricated source
references fail deterministic gates. Missing or tampered evidence, incompatible datasets,
missing slices/observations, unapproved thresholds and absent reviewed baselines block.
A hard failure is not hidden by simultaneous missing external evidence.

A local fixture PASS is limited to authored engineering regression. Connected provider,
protected holdout, independent labels, representativeness, approved quantitative policy,
observed economics, approved baseline and stage approval remain required externally.
The proposed schema validity 99% and accuracy margin 2 percentage points from document
73 remain proposals. No production quality, latency or monetary threshold is invented.

The baseline tool verifies report integrity, scope, aggregate consistency, source/code
and policy bindings, approval metadata, SHA-pinned rationale and create-only destination.
Approvals derive authenticity from human review/branch protection, not from self-asserted
JSON. Production remains BLOCKED regardless of fixture baseline approval. Java registry
validation additionally binds reviewed external evidence to exact execution artifacts,
while existing solver production restrictions remain active. Owner acceptance and hosted
CI are separate evidence; neither has been run by the implementing agent.
