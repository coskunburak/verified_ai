# Sprint 5.2 Implementation Map
## Prompt Registry, Schema Governance, and Prompt Release Workflow

Status: CREATED BEFORE PRODUCTION CODE CHANGES

Documentation mode: LOCAL_CANONICAL_DOCS

## 1. Repository Baseline

Branch: `feature/sprint-5-2-prompt-registry-governance`

Starting HEAD: `8247c97cebc4c61b4019dfef25042687c9717cfc`

Working tree state before this map: clean.

`git diff --check`: clean.

Sprint 5.1 status:
- Provider-neutral AI gateway exists in the `ai` module.
- `AiCapabilityRegistry.defaults()` registers every current capability.
- `ConfiguredAiRoutePlanner` enables only Sprint 5.1 executable capabilities and blocks future capability enablement.
- `ConfiguredAiModelGateway` preserves provider-neutral execution, provenance normalization, usage ledger reservation/completion, bounded fallback, and privacy-safe logs.
- Usage ledger migration exists from Sprint 5.1; Sprint 5.2 does not add persistence.

Current AI capabilities:
- `VISION_PARSE`
- `PROBLEM_NORMALIZE`
- `PROBLEM_CLASSIFY`
- `SOLVE`
- `ARBITRATE`
- `EXPLAIN`
- `MISTAKE_CLASSIFY`
- `TUTOR`
- `PRACTICE_GENERATE`

Current enabled routes from default local configuration:
- `VISION_PARSE`: enabled, `LOCAL_FIXTURE`, `vision-route-v1`, `vision-recognition`, `v001`, `recognition-evidence-v1`, `LOCAL_ONLY`.
- `PROBLEM_NORMALIZE`: enabled, `LOCAL_FIXTURE`, `problem-parser-route-v1`, `problem-parser`, `v001`, `problem-parse-v1`, `LOCAL_ONLY`.
- `PROBLEM_CLASSIFY`: enabled, `LOCAL_FIXTURE`, `problem-classifier-route-v1`, `problem-classifier`, `v001`, `problem-classification-v1`, `LOCAL_ONLY`.

Current disabled/future routes:
- `SOLVE`, `ARBITRATE`, `EXPLAIN`, `MISTAKE_CLASSIFY`, `TUTOR`, and `PRACTICE_GENERATE` are disabled in `application.yml` and are blocked by `ConfiguredAiRoutePlanner` when enabled before their owning sprint.

Current prompt artifacts:
- `services/api/src/main/resources/prompts/vision-recognition/v001.md`
- `services/api/src/main/resources/prompts/problem-parser/v001.md`
- Missing before implementation: `services/api/src/main/resources/prompts/problem-classifier/v001.md`

Current schema artifacts:
- `packages/schemas/recognition-evidence.schema.json` -> logical `recognition-evidence-v1`
- `packages/schemas/problem-parse.schema.json` -> logical `problem-parse-v1`
- `packages/schemas/problem-classification.schema.json.json` -> logical `problem-classification-v1`

## 2. Owning Bounded Context / Module

Owning bounded context/module = ai

Application ownership:
- `services/api/src/main/java/com/verifiedai/ai/application`
- Owns prompt registry contract, immutable registry entries, release-stage policy, schema identity policy, and route validation rules.

Infrastructure ownership:
- `services/api/src/main/java/com/verifiedai/ai/infrastructure/configuration`
- Owns classpath artifact loading, schema resource loading, Spring configuration, and startup validation integration.

Resource ownership:
- Canonical runtime prompt artifacts are under `services/api/src/main/resources/prompts`.
- Root `prompts/README.md` remains a pointer, not a second editable prompt copy.

Schema package ownership:
- Canonical schema source remains `packages/schemas`.
- The API service packages validated schema resources into its classpath for deterministic startup/runtime validation.

Evaluation ownership:
- Deterministic ingestion evaluation remains under `evaluations/`.
- Sprint 5.2 adds no connected-provider evaluation requirement and no protected holdout materialization.

## 3. Domain Invariants Touched

Preserved invariants:
- AI output is untrusted.
- Structured AI output must pass schema validation and semantic validation.
- Prompt/model version is stored for material AI operations.
- High-impact AI changes require golden-dataset evaluation.
- User problem content cannot override system policy.
- Provider fallback preserves the internal semantic contract.
- Production student content is not training data by default.
- External provider/model details are provenance/configuration, not learner semantics.
- Prompt/model behavior is not a domain invariant.
- Future solver/arbitration/tutor capabilities remain disabled.
- Durable provenance remains version-exact and historically interpretable.

## 4. iOS Impact

iOS IMPACT = NONE

No public API, DTO, route, or client prompt-selection behavior changes are planned.

## 5. Backend Commands / Queries / Policies / Adapters

Proposed components:
- `PromptRegistry`: immutable source-controlled registry of released prompt identities.
- `PromptRegistryEntry`: immutable metadata contract for `promptId + promptVersion`.
- `PromptReleaseStage`: `LOCAL_ONLY`, `STAGING`, `PRODUCTION`.
- `PromptTrainingEligibility`: default `NOT_ELIGIBLE`.
- `PromptVersion`: strict immutable version validator for values such as `v001`.
- `OutputSchemaRegistry`: logical schema version to artifact path registry.
- `PromptArtifactResolver`: bounded UTF-8 prompt artifact loader.
- `OutputSchemaArtifactResolver`: bounded schema artifact loader.
- `PromptRegistryValidator`: validates enabled route plans against prompt/schema registry metadata.
- `PromptRegistryValidationException` and stable failure codes.

Validation responsibilities:
- registry contract: unique `(promptId, promptVersion)` and complete required metadata.
- registry loader: static Java registry loaded at startup through Spring configuration.
- artifact validator: classpath prompt exists, path is safe, size is bounded, UTF-8 content is loadable.
- schema resolver: logical schema version resolves to a canonical schema artifact, path is safe, size is bounded.
- route-policy validation: route policy version must match registry compatibility.
- release-stage validation: production enabled route may use only `PRODUCTION` prompt registry entries.
- startup fail-closed behavior: validation runs during `ConfiguredAiRoutePlanner` construction for enabled routes.
- backward-compatibility adaptation: existing `application.yml` promptId/promptVersion/schemaVersion string shape is preserved.

## 6. DB Migration / Index Decision

DB MIGRATION = NOT REQUIRED

Prompt and schema registry artifacts are immutable source-controlled release artifacts, not learner DB state. Durable AI provenance already stores prompt/schema/route/provider/model identities.

## 7. API / Event / Schema Changes

PUBLIC API CHANGE = NONE

Internal API impact:
- New `ai.application` registry contracts and resolver ports.
- `ConfiguredAiRoutePlanner` gains enabled-route registry validation.

Event impact:
- NONE.

Schema artifact changes:
- Rename physical classifier schema from `problem-classification.schema.json.json` to `problem-classification.schema.json` if no stale references exist.
- Logical schema ID remains `problem-classification-v1`.

Resource path changes:
- Add `services/api/src/main/resources/prompts/problem-classifier/v001.md`.
- Add prompt repository README under `services/api/src/main/resources/prompts/`.

## 8. AI Capability / Route Impact

Enabled in local/test by current defaults:
- `VISION_PARSE`
- `PROBLEM_NORMALIZE`
- `PROBLEM_CLASSIFY`

Disabled / future-only:
- `SOLVE`
- `ARBITRATE`
- `EXPLAIN`
- `MISTAKE_CLASSIFY`
- `TUTOR`
- `PRACTICE_GENERATE`

Sprint 5.2 adds governance only. It does not add secondary solver routes, solver prompts, fallback solver invocation, tutor routes, or new inference calls.

## 9. Privacy / Security Impact

Prompt body logging:
- Forbidden. Registry exceptions and logs may include safe identifiers only.

Student content:
- Not stored in registry metadata and not logged by registry validation.

Signed URL:
- Not stored in registry metadata and not logged.

Secret:
- No provider credentials, API keys, JWTs, object-storage credentials, or signed URLs are added to prompts, registry metadata, tests, docs, or config.

Provider credential:
- Existing provider-secret server-side boundary remains unchanged.

Artifact exposure:
- Prompt/schema artifact identifiers are trusted source-controlled metadata. Path traversal and arbitrary URL/file loading are rejected.

Training eligibility:
- Registry defaults executable prompt workflows to `NOT_ELIGIBLE`.

Provider SDK boundary:
- No provider SDK dependency is added outside `ai.infrastructure`.

## 10. Telemetry / SLO / Cost Impact

new runtime inference calls = 0

invocation-rate change = 0

secondary solver calls = 0

new provider cost = 0 expected

Registry validation adds deterministic startup/resource-loading work only. Existing `AiGatewayMetrics` provider invocation metrics remain unchanged. New runtime metrics are not required for this sprint because registry validation is fail-fast configuration validation.

## 11. Test / Evaluation Evidence Plan

Unit tests:
- Prompt registry valid entries resolve for `VISION_PARSE`, `PROBLEM_NORMALIZE`, and `PROBLEM_CLASSIFY`.
- Invalid prompt versions reject `latest`, `1`, `v1`, and `prod`.
- Duplicate conflicting prompt entries fail.
- Capability mismatch fails.
- Route-policy incompatibility fails.
- Missing prompt entry fails closed.
- Missing prompt artifact fails closed.
- Missing schema artifact fails closed.
- Unsafe prompt/schema paths fail closed.
- Registry validation messages do not expose prompt bodies or student/secret content.
- Default executable capability descriptors have registry coverage.

Configuration tests:
- Local/test allow `LOCAL_ONLY`.
- Production rejects `LOCAL_ONLY`.
- Production rejects `STAGING`.
- Production accepts `PRODUCTION`.
- Disabled future capabilities remain non-executable.
- Registry validation adds zero provider invocations.

Gateway regression tests:
- Existing `ConfiguredAiModelGatewayTest`.
- Existing `ConfiguredAiRoutePlannerTest`.
- Existing `AiCapabilityRegistryTest`.

Architecture tests:
- Existing `AiProviderBoundaryTest`.
- Existing `ModularityTest`.

Context tests:
- Existing `ApplicationContextTest`.

Evaluation gates:
- Run `make eval-ai` if prompt/schema/provenance changes require deterministic ingestion verification.

Regression tests:
- `make test-api` if feasible.
- `make docs-check`, `make contracts-check`, `make secret-scan`, and `git diff --check`.

## 12. Rollout / Feature Flag Strategy

Prompt promotion:
- `LOCAL_ONLY` -> local tests
- `STAGING` -> staging validation/evaluation evidence
- `PRODUCTION` -> explicit source-controlled registry promotion plus explicit route config pinned to immutable `promptVersion` and `schemaVersion`

Routes opt into a new prompt version by changing route configuration to the new immutable `promptVersion`/`schemaVersion` after the registry entry exists.

Feature flag:
- No new runtime feature flag is needed. Immutable route configuration plus fail-closed registry validation is sufficient for this sprint.

## 13. Rollback / Recovery Plan

Route config rollback:
- Revert production route config to previous registered `promptVersion`/`schemaVersion`.

PromptVersion rollback:
- Do not edit old prompt bodies. Re-pin the route to the previous immutable registered version.

SchemaVersion rollback:
- Do not rewrite old schema meaning. Re-pin the route to the previous logical schema version.

Registry fail-closed behavior:
- Invalid production registry/config prevents startup or earliest route validation.

Startup recovery:
- Fix registry/config or revert route pin, redeploy, and keep validation enabled.

## 14. Documentation Conflict Record

DOCUMENTATION_CONFLICT

SOURCE_A:
- `docs/verified_ai_learning_platform_docs_v4/quality/56_COMPLETE_PRODUCTION_REPOSITORY_FILE_HIERARCHY.md`
- `docs/verified_ai_learning_platform_docs_v4/quality/66_FILE_PLACEMENT_OWNERSHIP_AND_DEPENDENCY_MATRIX.md`

SOURCE_B:
- `docs/verified_ai_learning_platform_docs_v4/ai/26_PROMPT_SCHEMA_AND_VERSIONING.md`
- `prompts/README.md`
- current Spring Boot runtime resources under `services/api/src/main/resources/prompts`

CANONICAL_OWNER:
- `ai` module runtime prompt packaging for executable API prompts.

PRECEDENCE_RULE:
- `quality/56` and `quality/66` govern file placement when starting from ambiguity, but current repository runtime architecture and `ai/26` identify the existing classpath prompt repository. Sprint 5.2 must eliminate ambiguity rather than maintain duplicate editable prompt copies.

DECISION:
- Use `services/api/src/main/resources/prompts` as the single canonical executable prompt source for current Spring API prompts.
- Keep root `prompts/README.md` as a pointer only.
- Update `ai/26`, prompt README, `quality/56`, and `quality/66` so docs and runtime packaging agree.

## 15. Exit Decision Criteria

Sprint 5.2 can exit only when:
- All enabled routes validate against registered prompt/schema metadata.
- Classifier prompt artifact exists.
- Classification schema filename drift is resolved safely.
- Production enabled routes fail closed on missing, mismatched, unsafe, or non-production prompt/schema governance.
- Local/test fixture routes remain usable.
- Future capabilities remain disabled.
- Prompt/schema governance adds zero provider calls.
- Documentation and evidence are updated truthfully.
