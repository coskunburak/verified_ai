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

## Sprint 5.12 solver engineering evaluation

`make eval-solver` is additive; `make eval-ai` retains Phase 4 ingestion semantics.
The 28-case corpus in `golden-datasets/solving/solver-v1/` is synthetic engineering
material, not the proposed 240-case independent benchmark. Every label explicitly
records author judgment without expert approval. Every case is NOT_ELIGIBLE for training.
The local observations in `fixtures/solver/evaluation-observations-v1.json` are authored
scoring inputs, not captured backend execution or provider outputs. Backend/verifier
behavior is tested independently by full owner acceptance.

`runners/solver_evaluation.py` supports `--mode validate|local|connected|holdout` and
`--output`. External modes return BLOCKED and exit 2 without calling a provider.
Local mode scores bounded immutable fixture observations, emits per-slice metrics with
denominators and missing-data states, and leaves productionPromotion BLOCKED. Reports
contain no raw problem, answer or provider payload. Case/report schemas live under
packages/schemas. Failures use controlled diagnostic codes.

`runners/solver_release.py compare` accepts repository-relative `--candidate`, optional
`--baseline`, and `--scope LOCAL_FIXTURE_REGRESSION|PRODUCTION`. Missing approved baseline
blocks comparison. Critical safety failures take precedence over missing evidence.
Local zero-regression checks are engineering invariants, not production thresholds.
Proposed 99% schema validity and 2-point noninferiority remain unapproved proposals.

Local baseline promotion is a separate explicit human-reviewed operation, never invoked
by CI or acceptance. The `promote` subcommand requires `--approve`, `--report`, `--review`,
`--review-sha256`, and a new `--destination` beneath evaluations/baselines/solver-approved-.
Existing baselines cannot be overwritten. Review JSON under docs uses
solver-baseline-review-v1, decision APPROVED, evidenceScope LOCAL_FIXTURE_REGRESSION,
approvedBy, timezone-aware approvedAt, binding, rationaleReference and rationaleSha256.
The binding includes reportSha256, datasetSha256, provenanceSha256, policySha256 and
evaluationCodeSha256; the rationale must be an existing SHA-pinned Markdown document.
No approved baseline or review is supplied by this sprint. Review authenticity relies
on human Git review and protected branches; hashes alone are not signatures. Rollback
loads and revalidates the prior exact approved artifact rather than bypassing checks.
This local baseline mechanism never creates a Java production authorization.

Run the single owner acceptance entry point documented in the Sprint 5.12 execution
report. Generated artifacts belong in .generated; local reports must remain private
until reviewed. CI uploads only named synthetic reports and retains them for 14 days.
