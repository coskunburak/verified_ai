# Proprietary Dataset Governance and Training Eligibility

## 1. Purpose

The platform may eventually train task-specific models, but production student interactions must never silently become training material. This document defines which data may be used, how eligibility is represented, how leakage is prevented, and how datasets become reproducible engineering artifacts.

## 2. Core invariant

> A record is ineligible for model training unless its training eligibility is explicitly established and auditable.

Absence of a restriction does not mean consent.

## 3. Data classes

### 3.1 Public/licensed benchmark data

Examples whose license explicitly permits intended use. Store license metadata, origin URL/source identifier, license version, acquisition date, and permitted purposes.

### 3.2 Internally authored synthetic data

Generated or authored test examples used for evaluation/training. Synthetic origin must be labeled; model-generated examples must never contaminate evaluation sets without review.

### 3.3 User-provided content

Images, PDFs, typed questions, student attempts, tutor messages, and corrections. Default state: **not training eligible**.

### 3.4 Derived product telemetry

Skill IDs, latency, verification status, aggregate performance signals. Some derived data may be usable for analytics/modeling if privacy policy, minimization, and eligibility rules permit it. Raw content and derived metadata have separate eligibility states.

## 4. Eligibility states

Recommended canonical states:

- `NOT_EVALUATED`
- `NOT_ELIGIBLE`
- `ELIGIBLE_PRODUCT_QUALITY_ONLY`
- `ELIGIBLE_INTERNAL_MODEL_TRAINING`
- `ELIGIBLE_PUBLIC_BENCHMARK` (only where explicitly permitted)
- `REVOKED`

Eligibility must include reason/source, policy version, timestamp, and actor/process provenance.

## 5. Dataset lifecycle

```text
Raw Source
  -> Eligibility Evaluation
  -> De-identification / Minimization
  -> Quality Validation
  -> Labeling / Verification
  -> Dataset Candidate
  -> Leakage/Dedup Scan
  -> Dataset Version Freeze
  -> Train / Validation / Test Split
  -> Model Experiment
  -> Model Release Evidence
```

## 6. Separation of evaluation and training

Golden evaluation sets must be protected from training leakage.

Rules:

- dataset IDs used for release gates are immutable per version;
- training pipelines cannot read protected test partitions;
- near-duplicate detection runs across train/test boundaries;
- model-generated synthetic variants of test questions cannot be reintroduced into training;
- access to test labels is limited to evaluation systems and authorized maintainers.

## 7. Data minimization

Before a training example is materialized:

- remove user/account identifiers;
- remove file/object-storage identifiers;
- remove timestamps unless analytically required;
- remove unrelated free text;
- strip metadata not required by the task;
- retain only the minimum mathematical/learning representation necessary.

## 8. Label provenance

Every label should identify its origin:

- deterministic verifier,
- human annotation,
- expert review,
- user correction,
- model suggestion later accepted/rejected,
- rule-derived label.

Model-generated labels without independent validation must not be treated as ground truth.

## 9. Quality tiers

Suggested tiers:

- Bronze: weakly labeled/synthetic; exploratory use only.
- Silver: schema-valid, deduplicated, partially verified.
- Gold: deterministic or expert-verified, leakage-reviewed, production-grade evaluation/training.

A model release document must state which tiers were used and why.

## 10. Dataset versioning

Each dataset release contains:

- immutable dataset version,
- source snapshot IDs,
- transformation code version,
- eligibility policy version,
- label policy version,
- split seed/strategy,
- row counts by skill/difficulty/language,
- known limitations,
- checksum/manifest.

## 11. Revocation

If source eligibility is revoked:

- future dataset versions exclude the data;
- dataset lineage identifies affected versions;
- legal/product policy determines whether retraining or model retirement is required;
- revocation action is auditable.

## 12. Child/student privacy sensitivity

The application deals with educational data and may serve minors. Treat training-data expansion as a privacy-sensitive product decision requiring explicit review, not as a normal analytics optimization.

## 13. Prohibited practices

- scraping private user history into a training corpus;
- storing screenshots indefinitely “for future AI”; 
- training on account-linked content without a documented eligibility basis;
- merging analytics warehouses and model training stores without purpose limitation;
- using user corrections as truth without validation;
- using evaluation data in fine-tuning.

## 14. Model-card dependency

Any proprietary model must reference the exact dataset versions used, and dataset manifests must reference the experiments/models that consumed them.

## 15. Required operational controls

- access-controlled dataset registry;
- reproducible export job;
- audit trail;
- data-quality reports;
- dedup/leakage report;
- retention and deletion integration;
- encrypted storage;
- environment separation.

## 16. Exit gate for first proprietary model

No proprietary training begins until:

- canonical eligibility states exist in data model;
- privacy policy and internal governance are aligned;
- dataset pipeline is reproducible;
- golden test set is protected;
- model release/evaluation process exists;
- rollback path exists;
- business/economic reason for the model is documented.
