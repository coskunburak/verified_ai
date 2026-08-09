# AI Evaluation and Golden Dataset

## 1. Purpose

No prompt/model/router/proprietary-model change is production-ready until measured against a protected, representative evaluation system.

## 2. Evaluation layers

### Parser evaluation
- OCR/recognition correctness;
- Sprint 4.5 `ProblemParse` schema validity;
- parser-level semantic validity;
- unsupported/review-required handling;
- source evidence and uncertainty preservation;
- future canonical expression accuracy after Sprint 4.6;
- skill/topic classification after Sprint 4.7.

Sprint 4.5 creates `evaluations/parser/golden/problem-parse-v1-seed.json` as a synthetic governance seed only. It covers supported parser-scope examples, unsupported examples, ambiguity, schema-invalid output, semantic-invalid output, and prompt-injection-as-content behavior. It is not a protected holdout, full accuracy benchmark, or training dataset. Sprint 4.10 owns representative ingestion accuracy calibration.

### Solver evaluation
- final answer accuracy;
- step validity where measurable;
- method appropriateness;
- unsupported/hallucinated claims.

### Verification evaluation
- true-positive verified rate;
- false verification rate (critical metric);
- false-negative/unverified rate;
- coverage by problem class.

### Tutor/learning evaluation
- pedagogical policy compliance;
- answer leakage in tutor mode;
- hint progression;
- mistake classification quality;
- recommendation quality.

## 3. Dataset partitions

Maintain:

- development examples;
- regression/golden set;
- protected holdout test set;
- hard-tail/adversarial set;
- production-correction set (eligible/governed only).

Protected holdout must never enter prompts, fine-tuning, or synthetic training generation.

## 4. Required slices

Report by:

- skill/topic;
- difficulty;
- input type (typed/image);
- language/locale;
- supported verification class;
- common notation variants;
- hard-tail category.

## 5. Release metrics

A change report includes:

- accuracy delta;
- false-verification delta;
- schema-valid rate;
- correction rate estimate;
- p50/p95 latency;
- expected cost per successful/verified solve;
- route escalation rate;
- regression list.

## 6. Cost-quality frontier

Evaluate multiple routes where appropriate and maintain a quality-vs-cost frontier. The router may use cheaper routes only within approved quality boundaries.

## 7. Secondary solver evaluation

Measure whether conditional secondary solving improves quality enough to justify cost by problem class. Tune escalation policy from evidence.

## 8. Proprietary model evaluation

Future proprietary candidates must be evaluated against the current approved route using the exact protected test version and slice-level reports.

## 9. Dataset governance

See `ai/58_PROPRIETARY_DATASET_GOVERNANCE_AND_TRAINING_ELIGIBILITY.md`. Evaluation sets require lineage, licensing/eligibility, deduplication, and leakage protection.

## 10. CI/release gates

Small deterministic unit datasets run in CI. Larger model evaluations may run in dedicated workflows, but production promotion cannot bypass recorded results.

## 11. Human review

Maintain sampled human/expert review for failure modes that automatic metrics do not reliably capture, particularly tutoring and novel hard-tail mathematics.

## 12. Reproducibility

Store:

- dataset version;
- route/model/prompt/schema versions;
- evaluation code commit;
- runtime parameters;
- result artifact/checksum;
- timestamp/owner.
