# Prompt, Schema, and Versioning

## Prompts are production artifacts

A prompt change can alter product behavior as strongly as code. Every material prompt has a stable ID, immutable version, output schema version, owner, evaluation result and release status.

## Repository

```text
src/main/resources/prompts/
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
