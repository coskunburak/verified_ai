# Sprint 5.2 Execution Report
## Prompt Registry, Schema Governance, and Prompt Release Workflow

Status: COMPLETE

Branch: `feature/sprint-5-2-prompt-registry-governance`

Starting HEAD: `8247c97cebc4c61b4019dfef25042687c9717cfc`

Implementation map: `docs/verified_ai_learning_platform_docs_v4/sprints/phase05_ai_solving_and_verification_core/SPRINT_5.2_IMPLEMENTATION_MAP.md`

## Scope Delivered

- Added source-controlled prompt registry metadata for enabled executable capabilities.
- Added source-controlled output schema registry metadata.
- Added strict immutable prompt version validation for `vNNN` identifiers.
- Added bounded classpath prompt and schema artifact resolvers with safe path checks.
- Integrated prompt/schema validation into AI route-planner startup for enabled routes.
- Added production fail-closed release-stage policy for enabled AI routes.
- Added `problem-classifier/v001` prompt artifact for `PROBLEM_CLASSIFY`.
- Renamed the classifier schema artifact to canonical `packages/schemas/problem-classification.schema.json`.
- Packaged `packages/schemas` into the API service classpath for schema resolution.
- Updated Docker build context so API image builds with schema resources available.
- Updated canonical prompt/schema documentation and repository placement guidance.

## Scope Excluded

- No Sprint 5.3 model-router budget/fallback policy work.
- No solver, secondary solver, arbitration, explanation, tutor, mistake-classifier, or practice-generation prompt activation.
- No new provider calls.
- No database migration.
- No public API or iOS DTO change.

## Safety And Privacy

- Prompt registry failures expose safe identifiers only.
- Prompt bodies, raw student content, provider payloads, object keys, signed URLs, and secrets are not logged by registry validation.
- Default executable prompt workflows remain `NOT_ELIGIBLE` for training.
- Schema-valid AI output remains untrusted until downstream semantic/domain validation accepts it.

## Validation Evidence

Repository and static gates:

```text
make doctor
git diff --check
docker compose config --quiet
docker compose build api
make docs-check
make contracts-check
make secret-scan
make lint
```

Result: PASS. The first API Docker image build exposed a final-stage copy path still pointing at `/workspace/target`; the Dockerfile now copies from `/workspace/services/api/target`, and the rerun built `verified-ai-local-api` successfully.

Deterministic AI evaluation:

```text
make eval-ai
```

Result: PASS. Dataset validation passed with 18 cases and 13 critical cases. Baseline decision was `PASS` with no regressions. The runner emitted existing near-duplicate fixture warnings only and wrote generated artifacts under `.generated/evaluations/latest`.

Focused API regression:

```text
mvn -Dtest=AiCapabilityRegistryTest,PromptVersionTest,PromptRegistryValidatorTest,ConfiguredAiRoutePlannerTest,ClasspathArtifactResolverTest,ConfiguredAiModelGatewayTest,AiProviderBoundaryTest,ModularityTest test
```

Result: PASS, 55 tests run, 0 failures, 0 errors, 0 skipped.

Full API regression:

```text
make test-api
```

Result: PASS, 264 tests run, 0 failures, 0 errors, 0 skipped. The first sandboxed attempt could not access Docker/Testcontainers; the approved escalated rerun completed successfully.

Math verifier regression:

```text
make test-verifier
```

Result: PASS, 20 tests passed. The earlier parallel run timed out one SymPy equivalence test while the full API suite was competing for CPU; the isolated rerun passed with only the existing Starlette/httpx deprecation warning.

iOS no-impact confirmation:

```text
make test-ios
```

Result: PASS, `TEST SUCCEEDED` on iPhone 16 Pro simulator. The first sandboxed attempt could not access CoreSimulator; the approved escalated rerun completed successfully.

## Rollout And Rollback

Rollout requires explicit registry promotion and route pinning to immutable `promptVersion` and `schemaVersion` values. Production enabled routes reject `LOCAL_ONLY` and `STAGING` prompt entries.

Rollback is route re-pinning to the previous registered prompt/schema version. Old prompt bodies and old schema meanings must not be edited.

## Exit Assessment

Sprint 5.2 exit criteria are satisfied:

- Enabled local/test routes validate against registered prompt/schema metadata.
- Classifier prompt artifact exists and resolves from the API classpath.
- Classification schema filename drift is resolved.
- Production enabled routes fail closed unless prompt registry entries are `PRODUCTION`.
- Local/test fixture routes remain usable.
- Future capabilities remain disabled.
- Prompt/schema governance adds zero provider calls.
- Documentation, manifest, coverage matrix, and traceability matrix are updated.
