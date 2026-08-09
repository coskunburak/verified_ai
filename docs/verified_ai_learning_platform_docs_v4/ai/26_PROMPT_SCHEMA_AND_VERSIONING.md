# Prompt, Schema, and Versioning

## Prompts are production artifacts

A prompt change can alter product behavior as strongly as code. Every material prompt has a stable ID, immutable version, output schema version, owner, evaluation result and release status.

## Repository

```text
src/main/resources/prompts/
├── vision-recognition/
│   └── v001.md
├── problem-parser/
│   ├── v001.md
│   ├── v002.md
│   └── schema-v002.json
├── solver/
├── arbiter/
├── explainer/
├── mistake-classifier/
├── tutor/
└── practice-generator/
```

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
2. Validate schema/unit fixtures.
3. Run golden dataset.
4. Compare quality, cost and latency.
5. Human-review critical disagreements.
6. Stage.
7. Roll out via flag.
8. Monitor.
9. Promote or roll back.

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
