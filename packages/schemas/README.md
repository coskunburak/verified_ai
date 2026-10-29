# Schemas

Versioned JSON schemas live here.

- `domain/problem-details.schema.json` defines the platform error contract.
- `recognition-evidence.schema.json` defines Sprint 4.4 raw recognition evidence. It is recognition-level evidence only, not a structured `ProblemParse` or canonical mathematical representation.
- `problem-parse.schema.json` defines Sprint 4.5 `problem-parse-v1` parser output. It is parser-level structure only, not a safe verifier AST, solution, primary skill classification, or verified answer.
- `canonical-problem.schema.json` defines Sprint 4.6 `canonical-problem-v1`, the durable backend-owned Layer 4 math representation.
- `verifier-input.schema.json` defines Sprint 4.6 `verifier-input-v1`, the internal typed AST payload accepted by the Python math verifier.
- `problem-classification.schema.json` defines `problem-classification-v1`, the classifier output contract for canonical ontology skill/difficulty projection. It is classification metadata only, not a solution, verification verdict, explanation, or mastery update.
- `ingestion-evaluation-case.schema.json` defines Sprint 4.10 `ingestion-evaluation-case-v1`, the engineering-only golden ingestion dataset case contract.
- `ingestion-evaluation-report.schema.json` defines Sprint 4.10 `ingestion-evaluation-report-v1`, the generated report contract used by release comparators and baselines.

Logical schema versions are stable API/AI contracts. The API `OutputSchemaRegistry` maps logical versions such as `problem-classification-v1` to these source files, and the API build packages them into the service classpath under `schemas/`.

Schema changes that alter meaning require a new logical version, for example `problem-classification-v2`. Do not rewrite old schema semantics or restore stale double-extension filenames.

The API registry pins SHA-256, `$id`, logical version and existing semantic validator ownership
for the three executable AI output schemas. Startup checks these bindings, while existing
problem-module validators still validate responses. Backend CI rejects in-place modifications
to these V1 schema files. Add a new version/path and explicit validator compatibility for upgrades;
retain old schemas for stored-output interpretation. Schema validity is not mathematical verification.

## Primary solver candidate (Sprint 5.4)

`solution-candidate.schema.json` defines `solution-candidate-v1`: bounded unverified public steps, typed
final answer, exact source identity and canonical assumption/restriction references. It never replaces
canonical-problem-v1 or verifier-input-v1 and cannot authorize VERIFIED. Java shape/safety validation is
owned by solving; independent owner-only schema fixtures live in `evaluations/fixtures/solver`.

Sprint 5.8 adds verification-execution.schema.json and verification-execution-result.schema.json.
They define scoped method execution and evidence only; existing canonical/verifier-input versions are unchanged.
Shared exact mathematical cases live under packages/test-fixtures/verification/execution-v1/.
