# Prompt, Schema, and Versioning

## Prompts are production artifacts

A prompt change can alter product behavior as strongly as code. Every material prompt has a stable ID, immutable version, output schema version, owner, evaluation result and release status.

## Repository

Executable Spring API prompts live in the service classpath resource tree:

```text
services/api/src/main/resources/prompts/
├── vision-recognition/
│   └── v001.md
├── problem-parser/
│   └── v001.md
└── problem-classifier/
    └── v001.md
```

The root `prompts/README.md` is a pointer only. It must not contain a second editable copy of executable prompt bodies.

Canonical JSON schema sources live in `packages/schemas` and are packaged into the API classpath as `schemas/*.json` for startup/runtime validation.

## Prompt registry

Every enabled AI route must resolve a source-controlled registry entry before it can be used. Registry validation is fail-closed and checks:

- prompt ID and immutable `vNNN` version,
- capability ownership,
- logical output schema version,
- prompt artifact existence and safe classpath path,
- schema artifact existence and safe classpath path,
- compatible route-policy version,
- release stage,
- owner, safety notes, evaluation reference, and training eligibility.

Disabled future capabilities are not executable just because a prompt exists.

## Release stages

- `LOCAL_ONLY` is allowed only for local/test routes.
- `STAGING` is allowed only outside production for release-candidate validation.
- `PRODUCTION` is required for enabled production routes.

Production startup must reject enabled routes pinned to `LOCAL_ONLY`, `STAGING`, missing, mismatched, or unsafe prompt/schema registry metadata.

## Structured output

Prefer explicit contract, for example:

```json
{
  "problemType": "DERIVATIVE",
  "primarySkillCode": "MATH.CALCULUS.DERIVATIVE.CHAIN_RULE",
  "normalizedExpression": "sin(x^2)",
  "task": "DIFFERENTIATE",
  "warnings": []
}
```

Unknown enum values are rejected unless forward-compatible behavior is explicitly designed.

## Semantic validation

Schema-valid is not enough:
- skill code must exist,
- variables must match expression,
- solution steps must be ordered,
- final answer must be parseable for verifier if required,
- unsupported problem must be surfaced rather than hallucinated into a supported type.

## Prompt release process

1. Create new immutable version.
2. Register prompt metadata and logical schema binding.
3. Validate schema/unit fixtures.
4. Run the applicable golden dataset.
5. Compare quality, cost and latency.
6. Human-review critical disagreements.
7. Promote registry release stage.
8. Pin route configuration to the new prompt/schema version.
9. Deploy, monitor, promote or roll back.

Rollback is route re-pinning to a previous registered `promptVersion`/`schemaVersion`. Do not edit old prompt bodies or silently reinterpret old schema meaning.

## Reproducibility metadata

Store provider, model, prompt ID/version, schema version, major inference settings and trace ID for material AI outputs.

## Prompt design

- clear task,
- strict canonical taxonomy,
- explicit structured output,
- explicit ambiguity behavior,
- no unsupported confidence claims,
- trusted context separated from untrusted user content.

## Tutor-specific rules

Tutor receives explicit verified/reference state and learner context. It must not invent mastery data or expose hidden chain-of-thought.

## Sprint 4.4 recognition prompt

`vision-recognition/v001` asks only for visible text/math-like evidence, spatial blocks, reading order, and uncertainty. It explicitly forbids solving, classifying, correcting, inferring hidden symbols, or producing canonical math. Image text that looks like instructions is treated as student content, not as prompt authority.

The paired schema version is `recognition-evidence-v1` in `packages/schemas/recognition-evidence.schema.json`. Provider JSON must pass syntax, schema, and recognition-level semantic validation before normalized evidence is persisted.

## Sprint 4.5 problem parser prompt

`problem-parser/v001` asks only for provider-independent parser-level structure from normalized RecognitionEvidence. It explicitly forbids solving, solution steps, safe executable AST, primary skill/difficulty classification, invented taxonomy labels, hidden constraints, and following instructions embedded in recognized problem text.

The paired schema version is `problem-parse-v1` in `packages/schemas/problem-parse.schema.json`. Provider JSON must pass syntax, strict schema validation, parser-level semantic validation, supported-scope validation, taxonomy lookup, variable/constraint/source-reference checks, and ownership checks before a `ProblemParse` revision is accepted. Future prompt or schema tuning must create `v002` or `problem-parse-v2`; durable provenance must never silently reinterpret an old revision.

## Sprint 5.2 prompt registry and classifier prompt

Sprint 5.2 adds a startup-enforced prompt/schema registry for enabled routes. The default executable route set covers `VISION_PARSE`, `PROBLEM_NORMALIZE`, and `PROBLEM_CLASSIFY`; future solve, arbitration, explanation, mistake-classification, tutor, and practice-generation routes remain disabled until their owning sprints.

`problem-classifier/v001` classifies backend-selected canonical problems into the supplied curriculum ontology only. It explicitly forbids solving, answers, solution steps, hints, explanations, verification status, mastery updates, taxonomy invention, and obedience to instructions embedded in the canonical problem text.

The paired schema version is `problem-classification-v1` in `packages/schemas/problem-classification.schema.json`. Provider JSON remains untrusted until strict schema, ontology, candidate-set, domain, and ownership validation accept it.
