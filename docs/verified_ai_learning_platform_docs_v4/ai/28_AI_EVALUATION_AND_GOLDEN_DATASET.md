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

Sprint 4.5 creates `evaluations/parser/golden/problem-parse-v1-seed.json` as a synthetic governance seed only. It covers supported parser-scope examples, unsupported examples, ambiguity, schema-invalid output, semantic-invalid output, and prompt-injection-as-content behavior. It is not a protected holdout, full accuracy benchmark, or training dataset.

Sprint 4.10 adds the Phase 4 ingestion evaluation foundation:

- `evaluations/golden-datasets/parsing/ingestion-v1/` contains synthetic deterministic development/regression/hard-tail cases with manifest, checksums, provenance, and `NOT_ELIGIBLE` training status;
- `packages/schemas/ingestion-evaluation-case.schema.json` and `packages/schemas/ingestion-evaluation-report.schema.json` define the engineering-only case/report contracts;
- `evaluations/runners/validate_ingestion_dataset.py`, metric modules, `run_ingestion_eval.py`, and `compare_release.py` produce privacy-minimized reports and release decisions;
- `evaluations/baselines/production-ingestion-v1.json` is the approved deterministic local fixture baseline, not real provider quality evidence;
- `evaluations/baselines/ingestion-release-gates-v1.yaml` fails closed on missing/incomparable evidence, critical false-authoritative acceptance, false-ready-for-solve, unsafe false accept, source fabrication, BOLA, and prompt-instruction execution.

Connected representative evaluation remains blocked until an approved non-fixture provider route and credentials exist. Protected holdout evaluation remains blocked until restricted payload storage and checksums exist. These blockers cannot be reclassified as PASS.

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

Sprint 4.10 adds `.github/workflows/ai-evaluation.yml` for deterministic PR evaluation on material ingestion/AI/schema/curriculum/evaluation changes. Connected and holdout jobs run only from trusted dispatch/scheduled contexts and use cost/timeout variables; they may return `BLOCKED` when required external evidence is unavailable.

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

## Sprint 5.12 engineering evaluation contract

Implementation state: OWNER_ACCEPTANCE_PASSED for local scope in owner run
20261031-121345-aTjFqW; production promotion remains BLOCKED. The separate solver-v1
corpus contains 28 project-authored synthetic cases with schema, byte checksums, stable
ordering, source/rights/training metadata, partition leakage checks and critical coverage.
Labels are SYNTHETIC_AUTHOR_JUDGMENT, not independently expert-approved gold. This is
not the proposed 240-case representative benchmark. Protected holdout and governed
production corrections are explicitly unavailable, never populated from development.

The solver evaluator scores authored fixture observations; it does not execute the
Java verification policy or infer provider quality. Full backend/verifier suites remain
required in owner acceptance. Schema validity, answer correctness, disposition, scope,
contradiction handling, stale lineage, uncertainty and source fidelity are separate.
Step/method review is UNAVAILABLE where no independent label exists. Exact bounded
rational answer normalization is supported; symbolic string comparison is an engineering
label comparison and never a mathematical proof. Only the existing verification policy
can assign canonical VERIFIED. Evaluation tooling only scores claims.

Reports expose numerators, denominators, missing observations, critical counts and
per-slice metrics. Timeouts/errors/missing outputs do not disappear from denominators.
Raw problem/answer payloads are excluded from reports. Both connected and holdout runner
modes return BLOCKED without inference. Local fixture success cannot authorize promotion.
See the Sprint 5.12 implementation map and execution report for owner-only acceptance.
