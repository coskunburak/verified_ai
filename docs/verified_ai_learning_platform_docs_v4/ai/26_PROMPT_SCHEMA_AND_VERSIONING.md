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
- prompt artifact existence, canonical identity-derived classpath path, and SHA-256,
- schema artifact existence, safe classpath path, SHA-256, JSON syntax, `$id` and logical version,
- capability and version support of the existing semantic validator owner,
- compatible route-policy version,
- release stage,
- owner, safety notes, evaluation reference, training eligibility, and digest-bound approval.

Disabled future capabilities are not executable just because a prompt exists.

## Release stages

| Artifact state | Executable eligibility |
| --- | --- |
| `DRAFT`, `VALIDATED`, `APPROVED` | Preparation/evidence states only; never implicitly selected. |
| `LOCAL_ONLY` | Local/test, with fixture-only primary and fallback targets. |
| `STAGING` | Local/test/staging with digest-bound approval and evaluation references. |
| `PRODUCTION` | Explicit production eligibility with digest-bound approval and evaluation references. |
| `DEPRECATED`, `ROLLED_BACK` | Retained for history; cannot execute. |

Environment must be `local`, `test`, `staging`, `prod`, or `production`; unknown values fail closed.
Staging routes require route stage `STAGING` or `PRODUCTION`; production routes require
`PRODUCTION`. A route stage cannot promote an artifact. Staging and production profiles
keep current fixture capabilities disabled by default. Future capabilities stay disabled.

`PromptReleaseApproval` binds the exact prompt SHA-256, schema SHA-256, evaluation baseline,
evaluation report, repository approval reference, reviewer identifier and approval timestamp.
Missing or mismatched evidence blocks executable staging/production artifacts. References
must be repository paths, never credential-bearing URLs or blocked-evaluation placeholders.
This is a source-controlled review attestation, not a runtime evaluator or signature service:
reviewers must inspect the referenced evidence at the deployed Git revision. No current
artifact has a production approval. Production still rejects fixture providers and requires
the usage ledger, including the `prod` environment alias.

## Integrity and review gate

The Java registries are the version-specific metadata manifest; no second editable manifest
or database is introduced. Startup hashes bounded classpath bytes before routes can execute.
The three existing prompts and schemas retain their original bytes and schema identities.
Pinned regression hashes detect accidental changes. Backend CI additionally compares all
prompt versions already present in the trusted PR base and the three governed V1 schemas
against the proposed worktree using `scripts/quality/check_prompt_immutability.py`.
Changing the prompt and its registered/test hashes together cannot satisfy that base comparison.
New versions need new paths; add new schema versions to that gate's governed schema set.

CODEOWNERS review and a required Backend CI status must protect registry, artifact, test,
evidence and workflow changes on the hosting service. Those remote protection settings
are not verified or changed by this sprint. Direct pushes/admin overrides are outside the
local gate's authority and must be restricted operationally. A new immutable artifact must
remain in Git and packaged registries while historical outputs depend on it.

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
7. Record reviewed evidence for the exact prompt/schema hashes, then explicitly promote registry release stage.
8. Pin route configuration to the new prompt/schema version.
9. Deploy, monitor, promote or roll back.

Rollback is route re-pinning to a previous still-approved `PRODUCTION` prompt/schema and its compatible route-policy version. Keep that known-good entry eligible; mark the failed candidate `ROLLED_BACK` only after removing active route pins. If no eligible version exists, disable the capability. Do not edit old prompt bodies or silently reinterpret old schema meaning.

## Reproducibility metadata

Store provider, model, prompt ID/version, schema version, major inference settings and trace ID for material AI outputs. Existing gateway provenance and usage ledger remain authoritative. Resolve the exact historical ID/version to its immutable digest; never substitute the current default. Retain the deployed Git revision and configuration with release records. This preserves durable provenance without a database migration.

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

## Resolution and validation ownership

Capability descriptors provide defaults, not automatic upgrades. Explicit route metadata selects
an exact registry identity; the registry checks its capability, schema and compatible policy.
`OutputSemanticContract` declares supported V1 schema versions and existing validator ownership:
`RecognitionEvidenceNormalizer`, `ProblemParseNormalizer`, and `ProblemClassificationValidator`
in the `problem` module. It does not call across module internals or duplicate those validators.
Schema artifact integrity/identity checks do not validate student responses and do not confer
mathematical correctness. Existing response syntax, structure and semantic validation stay in
their owners; mathematical verification remains a separate responsibility.

The existing local provider adapters generate deterministic fixtures and do not consume prompt
bodies for inference. The classifier prompt is a local artifact, not a production-quality claim.
Unknown/missing versions, digest mismatches, invalid references and incompatible schemas fail at
startup before usage reservation or provider invocation. There is no latest/default fallback,
paid retry, successful student answer or VERIFIED outcome for these failures. Unsupported
providers remain rejected by the existing gateway startup validation.

## Sprint 5.4 solver artifacts

`solver/v001` and `solution-candidate-v1` are new digest-pinned LOCAL_ONLY artifacts with no production
approval. The semantic owner is `solving.application.SolutionCandidateValidator`. The contract binds exact
canonical/parse IDs, safe AST claims and preserved source restriction IDs; it forbids authoritative
verification fields and hidden reasoning. Old prompt/schema bytes are unchanged. Owner tests/evaluation
are pending; any live promotion needs real model and governed release evidence, not fixture success.
