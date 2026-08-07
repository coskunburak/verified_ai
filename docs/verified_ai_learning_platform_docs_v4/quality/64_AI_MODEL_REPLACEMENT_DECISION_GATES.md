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
