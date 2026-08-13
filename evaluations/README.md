# Evaluations

Golden datasets, baselines, release-gate policies, and generated report entrypoints live here.

Sprint 4.5 added the first synthetic parser seed at `evaluations/parser/golden/problem-parse-v1-seed.json`. Sprint 4.10 preserves those semantics in `evaluations/golden-datasets/parsing/ingestion-v1/` and adds deterministic Phase 4 ingestion release tooling:

- dataset manifest and source/license/training-eligibility governance;
- coverage rubric for production-supported and hard-tail slices;
- deterministic recognition/parser/canonicalization/classification/E2E metrics;
- approved deterministic local fixture baseline;
- release comparator and `make eval-ai` gate.

Generated candidate reports are written under `.generated/evaluations/` and are not committed by default. The approved deterministic baseline in `evaluations/baselines/production-ingestion-v1.json` is source-controlled.

Current limitations are explicit:

- local fixture regression is not real provider OCR/model accuracy;
- connected representative evaluation is `BLOCKED_NO_APPROVED_PROVIDER_ROUTE` in this workspace;
- protected holdout payload is not committed and is `BLOCKED_PROTECTED_HOLDOUT` until restricted storage is available;
- all committed Sprint 4.10 examples are `NOT_ELIGIBLE` for training.
