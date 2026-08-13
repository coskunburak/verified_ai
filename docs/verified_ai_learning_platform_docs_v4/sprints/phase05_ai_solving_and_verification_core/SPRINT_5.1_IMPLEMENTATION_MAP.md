# Sprint 5.1 Production Implementation Map
## Provider-Neutral AI Gateway and Capability Model

**Project:** Verified AI Learning Platform  
**Phase:** Phase 5 - AI Solving and Verification Core  
**Sprint:** 5.1 - Provider-Neutral AI Gateway and Capability Model  
**Primary bounded context:** `ai`  
**Primary runtime:** Spring Boot API  
**Existing early gateway subset:** `VISION_PARSE`, `PROBLEM_NORMALIZE`, `PROBLEM_CLASSIFY`  
**New model training:** FORBIDDEN  
**New self-hosted inference:** FORBIDDEN  
**New public mobile AI provider access:** FORBIDDEN  
**Expected public API change:** NONE by default  
**Expected iOS product UI change:** NONE by default  
**Expected Flyway migration:** YES, if consolidated AI usage ledger is implemented in Sprint 5.1  

---

# 1. Executive Summary

Sprint 5.1 turns the Phase 4 early AI boundary into the durable AI platform contract used by the rest of Phase 5.

Phase 4 already proved three capability-specific paths:

```text
VISION_PARSE
  used by: problem recognition
  route: vision-route-v1
  prompt: vision-recognition/v001
  schema: recognition-evidence-v1

PROBLEM_NORMALIZE
  used by: problem parser
  route: problem-parser-route-v1
  prompt: problem-parser/v001
  schema: problem-parse-v1

PROBLEM_CLASSIFY
  used by: problem classification
  route: problem-classifier-route-v1
  prompt: problem-classifier/v001
  schema: problem-classification-v1
```

Those paths currently protect problem-domain code from provider SDKs, but they are still shaped around three hard-coded capabilities and three property classes. Sprint 5.1 must generalize the gateway without changing Phase 4 business meaning.

The target state is:

```text
Product module
    -> typed AI capability request
    -> AI application port
    -> capability registry
    -> route policy
    -> immutable route plan
    -> provider adapter
    -> syntactic/schema/semantic validation by caller-owned policy
    -> provenance + usage ledger + metrics
    -> caller-owned domain mapping
```

This sprint is not a solver sprint. It should make later solver, secondary solver, arbitration, explanation, mistake-classifier, tutor, and practice-generation capabilities possible without letting those modules import OpenAI, Gemini, Apple, or any provider SDK.

---

# 2. Source Documents Reviewed

Mandatory Phase 5 sources:

- `sprints/phase05_ai_solving_and_verification_core/PHASE_05_AI_SOLVING_AND_VERIFICATION_CORE.md`
- `sprints/phase05_ai_solving_and_verification_core/SPRINT_5.1_PROVIDER_NEUTRAL_AI_GATEWAY_AND_CAPABILITY_MODEL.md`
- `sprints/phase05_ai_solving_and_verification_core/SPRINT_5.2_PROMPT_REGISTRY_SCHEMA_GOVERNANCE_AND_PROMPT_RELEASE_WORKFLOW.md`
- `sprints/phase05_ai_solving_and_verification_core/SPRINT_5.3_MODEL_ROUTER_COST_BUDGETS_FALLBACK_AND_RELIABILITY_POLICY.md`
- `sprints/phase05_ai_solving_and_verification_core/SPRINT_5.4_PRIMARY_SOLVER_PIPELINE_AND_STRUCTURED_SOLUTION_CANDIDATE.md`
- `sprints/phase05_ai_solving_and_verification_core/SPRINT_5.5_CONDITIONAL_INDEPENDENT_SECONDARY_SOLVER_AND_BLIND_AGREEMENT_ANALYSIS.md`
- `sprints/phase05_ai_solving_and_verification_core/SPRINT_5.6_CANONICAL_SOLUTION_AND_STEP_DOMAIN_MODEL.md`
- `sprints/phase05_ai_solving_and_verification_core/SPRINT_5.7_VERIFICATION_PLANNER_AND_VERIFICATION_METHOD_SELECTION.md`
- `sprints/phase05_ai_solving_and_verification_core/SPRINT_5.11_ARBITRATION_UNCERTAINTY_RETRY_AND_HUMAN_HONEST_VERIFICATION_POLICY.md`
- `sprints/phase05_ai_solving_and_verification_core/SPRINT_5.12_GOLDEN_DATASET_AI_EVALUATION_COST_REGRESSION_AND_MODEL_RELEASE_GATE.md`

Canonical AI and governance sources:

- `domain/10_DOMAIN_INVARIANTS_AND_BUSINESS_RULES.md`
- `backend/20_BACKEND_MODULE_CONTRACTS.md`
- `ai/25_AI_ORCHESTRATION_AND_MODEL_ROUTING.md`
- `ai/26_PROMPT_SCHEMA_AND_VERSIONING.md`
- `ai/28_AI_EVALUATION_AND_GOLDEN_DATASET.md`
- `ai/29_AI_COST_LATENCY_AND_RELIABILITY.md`
- `ai/57_API_FIRST_HYBRID_AI_STRATEGY_AND_MODEL_EVOLUTION.md`
- `ai/60_AI_UNIT_ECONOMICS_AND_INFERENCE_COST_ENGINEERING.md`
- `quality/64_AI_MODEL_REPLACEMENT_DECISION_GATES.md`
- `data/62_TRAINING_DATA_LINEAGE_CONSENT_AND_DATASET_PIPELINE.md`
- `quality/56_COMPLETE_PRODUCTION_REPOSITORY_FILE_HIERARCHY.md`
- `quality/66_FILE_PLACEMENT_OWNERSHIP_AND_DEPENDENCY_MATRIX.md`
- `roadmap/68_PRODUCTION_V1_CAPABILITY_COVERAGE_MATRIX.md`
- `quality/69_REQUIREMENTS_TRACEABILITY_MATRIX.md`
- `operations/71_TECHNICAL_DEBT_AND_MAINTENANCE_REGISTER.md`
- `adr/ADR-003_PROVIDER_NEUTRAL_AI.md`
- `adr/ADR-005_API_FIRST_PROGRESSIVELY_PROPRIETARY_AI_STRATEGY.md`
- `adr/ADR-006_PRODUCTION_STUDENT_DATA_NOT_TRAINING_DATA_BY_DEFAULT.md`
- `adr/ADR-007_SELF_HOSTED_MODELS_ONLY_AFTER_TCO_AND_QUALITY_GATES.md`

---

# 3. Current Repository Baseline

## 3.1 Existing AI package

Current backend package:

```text
services/api/src/main/java/com/verifiedai/ai/
├── application/
│   ├── AiCapability.java
│   ├── AiModelGateway.java
│   ├── AiRoutePlan.java
│   ├── AiProvenance.java
│   ├── AiUsage.java
│   ├── AiProviderException.java
│   ├── AiProviderFailureClass.java
│   ├── AiVisionParseRequest.java
│   ├── AiVisionParseResult.java
│   ├── AiProblemNormalizeRequest.java
│   ├── AiProblemNormalizeResult.java
│   ├── AiProblemClassifyRequest.java
│   └── AiProblemClassifyResult.java
└── infrastructure/
    ├── configuration/
    │   ├── AiInfrastructureConfiguration.java
    │   ├── AiVisionRecognitionProperties.java
    │   ├── AiProblemParserProperties.java
    │   └── AiProblemClassifierProperties.java
    └── provider/
        ├── ConfiguredAiModelGateway.java
        ├── VisionParseProviderAdapter.java
        ├── ProblemNormalizeProviderAdapter.java
        ├── ProblemClassifyProviderAdapter.java
        ├── LocalFixtureVisionParseProviderAdapter.java
        ├── LocalFixtureProblemNormalizeProviderAdapter.java
        ├── LocalFixtureProblemClassifyProviderAdapter.java
        ├── UnavailableVisionParseProviderAdapter.java
        ├── UnavailableProblemNormalizeProviderAdapter.java
        └── UnavailableProblemClassifyProviderAdapter.java
```

Current tests:

```text
services/api/src/test/java/com/verifiedai/ai/infrastructure/provider/ConfiguredAiModelGatewayTest.java
```

## 3.2 Current limitations to fix

- `AiCapability` contains only Phase 4 ingestion/classification capabilities.
- `AiModelGateway` exposes capability-specific methods instead of a future-proof capability execution contract.
- Route configuration is split across separate property classes rather than a common capability route registry.
- Fallback logic is repeated by capability inside `ConfiguredAiModelGateway`.
- Usage/cost/provenance is persisted in downstream Phase 4 domain tables, but there is no consolidated AI usage ledger.
- Production fixture-provider guards exist only for the three existing capability properties.
- There is no generic architecture test that prevents provider SDK imports outside `ai.infrastructure`.
- There is no route health/circuit breaker policy surface beyond basic fallback on retryable exceptions.
- There is no generic cost-budget enforcement surface shared by future solving/tutoring capabilities.

## 3.3 Current local validation baseline

Phase 4 reports record:

- `make check` passed with backend API, verifier, iOS simulator, docs, contracts, and secret scan.
- `make eval-ai` passed in `LOCAL_FIXTURE_REGRESSION`.
- Connected provider evaluation is blocked without an approved provider route.
- Protected holdout evaluation is blocked without restricted holdout payload.

Sprint 5.1 must preserve the local Phase 4 ingestion evaluation gate. A gateway refactor that makes `make eval-ai` fail is not acceptable unless the implementation intentionally updates the evaluation contract and baseline in the same reviewed change.

---

# 4. Sprint Mission Recast As Implementation Contract

Sprint 5.1 must deliver:

1. A stable AI capability vocabulary that can support Phase 5 and later product modules.
2. A provider-neutral gateway API that can execute typed capability requests without provider SDK leakage.
3. A route-policy model that separates product intent from provider/model configuration.
4. A common fallback/retry/budget/provenance execution path.
5. A consolidated AI usage/cost ledger or an explicit documented deferral with owner and target sprint.
6. Production safety guards for local fixture providers and unavailable routes.
7. Architecture tests proving domain/product modules do not depend on provider SDKs.
8. Privacy-safe metrics and structured logs for AI gateway execution.
9. Backward-compatible behavior for Phase 4 capabilities.
10. Documentation updates that move `CAP-AI-001` and `REQ-AI-001` according to evidence.

---

# 5. Explicit Non-Goals

Do not implement these in Sprint 5.1:

- Primary solution generation behavior. That belongs to Sprint 5.4.
- Secondary solver invocation policy. That belongs to Sprint 5.5.
- Canonical solution/step persistence. That belongs to Sprint 5.6.
- Verification planner or `VERIFIED` assignment. That belongs to Sprint 5.7 through Sprint 5.11.
- Prompt release workflow and prompt registry UI/process completion. That belongs to Sprint 5.2.
- Full cost router optimization by difficulty/entitlement/provider health. That belongs to Sprint 5.3.
- Tutor, mistake-classifier, study-planner, or practice-generation behavior.
- Model training, fine-tuning, embedding stores, vector DBs, or self-hosted inference.
- Public AI admin endpoints unless an explicit owner, authz model, and audit plan are added.
- iOS direct provider access or any mobile-stored AI provider secret.

---

# 6. Target Capability Vocabulary

## 6.1 Keep existing Phase 4 capabilities

```text
VISION_PARSE
PROBLEM_NORMALIZE
PROBLEM_CLASSIFY
```

These must remain stable because durable Phase 4 rows and evaluation reports already reference them.

## 6.2 Add Phase 5-ready capability IDs

Add capability IDs needed by future sprints, but keep them disabled/unavailable until their owning sprint adds behavior:

```text
SOLVE
ARBITRATE
EXPLAIN
MISTAKE_CLASSIFY
TUTOR
PRACTICE_GENERATE
```

Implementation note:

- Use `SOLVE` as the stable capability.
- Represent primary vs secondary solving through route context and policy fields until Sprint 5.5 finalizes the secondary-solver contract.
- If Sprint 5.5 later chooses a separate `SOLVE_SECONDARY` capability, it must be added by migration-safe capability registry change, not by provider-specific branching.

## 6.3 Capability metadata

Introduce a capability descriptor with at least:

```text
capability
owning_module
default_route_policy_version
default_prompt_id nullable
default_schema_version nullable
requires_schema_validation
allows_streaming
allows_cache
material_ai_output
requires_usage_ledger
default_privacy_class
default_cost_bucket
```

Minimum descriptors:

| Capability | Owner | Material output | Default status in Sprint 5.1 |
|---|---|---:|---|
| `VISION_PARSE` | `problem` consumes, `ai` routes | Yes | Enabled in local/test as existing fixture route |
| `PROBLEM_NORMALIZE` | `problem` consumes, `ai` routes | Yes | Enabled in local/test as existing fixture route |
| `PROBLEM_CLASSIFY` | `problem` consumes, `ai` routes | Yes | Enabled in local/test as existing fixture route |
| `SOLVE` | future `solving` consumes, `ai` routes | Yes | Defined but disabled/unavailable |
| `ARBITRATE` | future `verification` consumes, `ai` routes | Yes | Defined but disabled/unavailable |
| `EXPLAIN` | future solution/tutoring consumes, `ai` routes | Yes | Defined but disabled/unavailable |
| `MISTAKE_CLASSIFY` | future `mistake` consumes, `ai` routes | Yes | Defined but disabled/unavailable |
| `TUTOR` | future `tutoring` consumes, `ai` routes | Yes | Defined but disabled/unavailable |
| `PRACTICE_GENERATE` | future practice/study planning consumes, `ai` routes | Yes | Defined but disabled/unavailable |

---

# 7. Target Gateway Contract

## 7.1 Application boundary

Replace the hard-coded shape of `AiModelGateway` with a general capability execution boundary while preserving typed compatibility methods for Phase 4 callers.

Recommended application types:

```text
services/api/src/main/java/com/verifiedai/ai/application/
├── AiCapability.java
├── AiCapabilityDescriptor.java
├── AiCapabilityRegistry.java
├── AiExecutionCommand.java
├── AiExecutionResult.java
├── AiExecutionStatus.java
├── AiExecutionContext.java
├── AiRouteContext.java
├── AiRoutePlan.java
├── AiRoutePlanner.java
├── AiRoutePolicy.java
├── AiRouteFailure.java
├── AiProviderFailureClass.java
├── AiProviderException.java
├── AiProvenance.java
├── AiUsage.java
├── AiUsageRecorder.java
├── AiUsageRecord.java
├── AiGatewayMetrics.java
└── AiModelGateway.java
```

`AiModelGateway` should support both:

```java
AiExecutionResult execute(AiExecutionCommand command);
AiRoutePlan routePlan(AiRouteContext context);
```

and compatibility wrappers:

```java
AiVisionParseResult executeVisionParse(AiVisionParseRequest request);
AiProblemNormalizeResult executeProblemNormalize(AiProblemNormalizeRequest request);
AiProblemClassifyResult executeProblemClassify(AiProblemClassifyRequest request);
```

The wrappers should construct `AiExecutionCommand`, call the generic execution path, and map the generic result back to existing typed result records. That prevents duplicate retry/fallback/usage code while avoiding a Phase 4 breaking change.

## 7.2 Command shape

`AiExecutionCommand` should contain:

```text
capability
operation_id
user_id nullable
problem_session_id nullable
correlation_id
trace_id nullable
idempotency_key nullable
input_payload
input_payload_content_type
input_payload_schema_version nullable
prompt_id nullable
prompt_version nullable
output_schema_version nullable
route_context
deadline
max_cost_micros
privacy_constraints
metadata
```

Rules:

- `input_payload` may contain student problem content only when the capability requires it.
- Logs and metrics must never emit `input_payload`.
- `operation_id` is a gateway-level idempotency/audit key, not a provider request ID.
- Product modules own semantic validation of returned AI JSON after schema validation.

## 7.3 Result shape

`AiExecutionResult` should contain:

```text
operation_id
capability
status
raw_output
raw_output_content_type
output_schema_version
provenance
usage
provider_latency_ms
gateway_latency_ms
attempt_count
fallback_used
failure_class nullable
retryable
ledger_record_id nullable
```

Allowed statuses:

```text
SUCCEEDED
FAILED_RETRYABLE
FAILED_TERMINAL
DISABLED
BLOCKED_BUDGET
BLOCKED_PROVIDER_UNAVAILABLE
BLOCKED_POLICY
```

Do not return a successful-looking result on provider failure. A recoverable failure is still a failure.

---

# 8. Route Policy And Planner

## 8.1 Route context

`AiRouteContext` should include the policy inputs listed in `ai/25_AI_ORCHESTRATION_AND_MODEL_ROUTING.md`:

```text
capability
canonical_problem_type nullable
task_type nullable
difficulty nullable
classification_confidence nullable
parser_review_required nullable
recognition_quality_risk nullable
verification_method_available nullable
entitlement_tier nullable
locale nullable
latency_budget
max_cost_micros
provider_health_snapshot nullable
feature_flag_context nullable
prior_route_failure nullable
privacy_region nullable
privacy_retention_constraint nullable
route_role nullable
```

Sprint 5.1 does not need to implement advanced scoring over every field. It must define the typed context so Sprint 5.3 can implement the policy without changing product-module contracts.

## 8.2 Route plan

Extend `AiRoutePlan` to represent:

```text
capability
route_policy_version
route_id
primary_provider
primary_model
fallback_chain
prompt_id
prompt_version
schema_version
timeout
max_attempts
max_response_bytes
max_cost_micros
pricing_version
cache_policy
streaming_policy
provider_retention_policy
privacy_region
circuit_breaker_key
quality_gate_version nullable
release_stage
```

`RoutePlan` must be immutable once selected.

## 8.3 Route policy source

Sprint 5.1 should move toward a common config shape:

```yaml
app:
  ai:
    capabilities:
      VISION_PARSE:
        enabled: true
        route-policy-version: vision-route-v1
        routes:
          - route-id: vision-local-fixture
            provider: LOCAL_FIXTURE
            model: local-fixture-vision-v1
            prompt-id: vision-recognition
            prompt-version: v001
            schema-version: recognition-evidence-v1
            timeout: PT20S
            max-attempts: 2
            max-response-bytes: 65536
            max-cost-micros: 20000
            pricing-version: vision-recognition-local-v1
```

Backward compatibility:

- Existing `app.ai.vision-recognition.*`, `app.ai.problem-parser.*`, and `app.ai.problem-classifier.*` environment variables must continue to work during Sprint 5.1.
- The common registry may internally translate the existing properties into capability route policies.
- A later cleanup can remove duplicated old property classes only after docs and operations agree.

---

# 9. Provider Adapter Contract

## 9.1 Generic adapter boundary

Introduce a generic provider adapter interface:

```text
AiProviderAdapter
  providerId()
  supports(AiCapability)
  execute(AiProviderRequest, AiRoutePlan)
```

Keep provider-specific implementation under:

```text
services/api/src/main/java/com/verifiedai/ai/infrastructure/provider/
```

Recommended package split:

```text
provider/
├── common/
├── local/
├── unavailable/
└── external/
```

Do not place provider SDK classes in `problem`, `solving`, `verification`, `tutoring`, `mistake`, `mastery`, `studyplan`, `ios`, or shared domain packages.

## 9.2 Existing adapters

Adapt existing adapters:

- `LocalFixtureVisionParseProviderAdapter`
- `LocalFixtureProblemNormalizeProviderAdapter`
- `LocalFixtureProblemClassifyProviderAdapter`
- `UnavailableVisionParseProviderAdapter`
- `UnavailableProblemNormalizeProviderAdapter`
- `UnavailableProblemClassifyProviderAdapter`

Target:

- Either implement `AiProviderAdapter` directly.
- Or use thin compatibility adapters that delegate to the existing typed interfaces during Sprint 5.1.

Do not delete working Phase 4 adapters until the new generic path is tested by the Phase 4 integration suites and `make eval-ai`.

## 9.3 Real external providers

Sprint 5.1 may define real external provider adapter interfaces and configuration slots, but it must not claim external validation without credentials and approved route configuration.

If an external provider adapter is added:

- keep credentials in environment/secrets only;
- configure provider disabled by default;
- add contract tests with mocked HTTP responses;
- add production startup validation for required credentials;
- record provider request/response IDs when available;
- never log request payload, prompt content, provider raw output, API keys, signed URLs, object keys, or student content.

If no real provider route is available, record:

```text
BLOCKED_EXTERNAL_PROVIDER_CREDENTIALS
```

or the more specific existing blocker:

```text
BLOCKED_NO_APPROVED_PROVIDER_ROUTE
```

This blocker does not prevent Sprint 5.1 local architecture completion unless the sprint acceptance criteria explicitly require live provider execution.

---

# 10. Usage Ledger

## 10.1 Why Sprint 5.1 should add the ledger

`ai/25_AI_ORCHESTRATION_AND_MODEL_ROUTING.md` requires every material AI call to record capability, route, provider/model, prompt/schema, units, latency, cost, status, fallback/escalation, and trace identifiers.

Phase 4 stores some of this in downstream problem tables. Sprint 5.1 should introduce a consolidated AI-owned usage ledger so later solver/tutor/mistake routes do not duplicate accounting in every module.

## 10.2 Migration

Expected migration:

```text
services/api/src/main/resources/db/migration/platform/V016__create_ai_usage_ledger.sql
```

Preferred table:

```text
ai_usage_records
```

Columns:

```text
id uuid primary key
operation_id uuid not null unique
user_id uuid nullable
problem_session_id uuid nullable
capability text not null
route_policy_version text not null
route_id text nullable
provider text not null
model text nullable
prompt_id text nullable
prompt_version text nullable
schema_version text nullable
provider_request_id text nullable
provider_response_id text nullable
status text not null
failure_class text nullable
retryable boolean not null default false
attempt_count integer not null
fallback_used boolean not null default false
fallback_chain text[] nullable
escalation_reason text nullable
cache_status text nullable
input_token_count integer nullable
output_token_count integer nullable
image_unit_count integer nullable
input_unit_count integer nullable
output_unit_count integer nullable
estimated_cost_micros bigint not null default 0
currency text not null default 'USD'
pricing_version text not null
provider_latency_ms integer nullable
gateway_latency_ms integer not null
correlation_id text nullable
trace_id text nullable
created_at timestamptz not null
```

Constraints:

- `capability` must be one of registered `AiCapability` values.
- `status` must be closed enum text.
- `estimated_cost_micros >= 0`.
- `attempt_count >= 0`.
- no raw prompt, raw provider response, raw student text, object key, signed URL, or binary payload column.

Indexes:

```text
idx_ai_usage_records_created_at
idx_ai_usage_records_capability_created_at
idx_ai_usage_records_provider_created_at
idx_ai_usage_records_user_created_at where user_id is not null
idx_ai_usage_records_problem_session where problem_session_id is not null
idx_ai_usage_records_status_created_at
```

## 10.3 Ledger write semantics

Record one ledger row for each gateway operation, not every low-level retry, unless a later operations review requires retry-level detail.

Rules:

- Write on success and failure.
- Do not let ledger write failure transform an otherwise safe provider failure into a false success.
- If the DB is unavailable before provider execution, fail closed unless a capability-specific policy explicitly permits no-ledger execution.
- Include fallback result in one row with `fallback_used=true` and fallback chain metadata.
- Keep downstream Phase 4 provenance fields; ledger supplements them and does not replace domain-owned provenance in recognition/parse/classification records.

## 10.4 Privacy lifecycle

Add an AI lifecycle contributor or documented retention policy:

```text
services/api/src/main/java/com/verifiedai/ai/application/AiUsageLifecycleContributor.java
```

Policy:

- Export may include high-level usage metadata needed for transparency, but never raw provider payloads.
- Account deletion should delete or anonymize user-linked AI usage rows according to privacy docs.
- Retain aggregate cost metrics through observability systems without user identifiers.

---

# 11. Security And Privacy Requirements

## 11.1 Secrets

Provider secrets:

- never in iOS;
- never committed to repository;
- never logged;
- loaded through environment/secret manager only;
- validated at startup only for enabled real providers.

## 11.2 User content minimization

Gateway code must not default to passing entire session history. The caller must provide the minimal input needed for the capability.

For Phase 4 compatibility:

- `VISION_PARSE` receives selected derivative bytes only.
- `PROBLEM_NORMALIZE` receives normalized RecognitionEvidence and quality context only.
- `PROBLEM_CLASSIFY` receives canonical projection and bounded candidate taxonomy only.

For future Phase 5:

- `SOLVE` receives canonical problem and minimal context.
- `ARBITRATE` receives structured solver evidence, not hidden provider chain-of-thought.
- `EXPLAIN` receives verified/reference state when available, not private chain-of-thought.

## 11.3 Prompt injection

Prompt injection is capability-specific but the gateway must preserve trusted/untrusted separation:

- route policy and prompt identity are trusted configuration;
- user problem content is untrusted payload;
- provider output is untrusted until caller validation accepts it.

Add tests that prove image/problem text cannot override route policy, provider selection, usage ledger behavior, or validation status.

## 11.4 Object authorization

Sprint 5.1 should not introduce new object reads outside existing product modules. If future provider adapters need object bytes, the owning module must fetch and authorize the object first, then pass minimal bytes through the capability command.

The `ai` module must not become a bypass around `problem` ownership checks.

---

# 12. Observability And Cost Metrics

## 12.1 Metrics

Add low-cardinality Micrometer metrics:

```text
ai.gateway.request.total
ai.gateway.result.total
ai.gateway.latency.ms
ai.gateway.provider.latency.ms
ai.gateway.estimated_cost_micros
ai.gateway.retry.total
ai.gateway.fallback.total
ai.gateway.blocked.total
ai.gateway.schema_invalid.total
ai.gateway.budget_exceeded.total
ai.gateway.ledger.write.total
```

Allowed tags:

```text
capability
route_policy_version
provider
status
failure_class
fallback_used
environment
```

Avoid tags:

```text
user_id
problem_session_id
raw model deployment id if high cardinality
prompt text
student content
object key
provider request id
```

## 12.2 Logs

Structured logs may include:

```text
operation_id
capability
route_policy_version
provider
status
failure_class
retryable
fallback_used
estimated_cost_micros
latency_ms
correlation_id
trace_id
```

Structured logs must not include raw prompt, raw response, recognized text, parser text, solution text, uploaded image bytes, file names, object keys, signed URLs, tokens, secrets, or user IDs where a correlation ID is enough.

## 12.3 Cost evidence

Sprint 5.1 must record:

- cost impact of the gateway refactor itself;
- whether it adds any net new inference calls;
- whether any capability is newly enabled in local/staging/production;
- expected invocation rate for enabled capabilities;
- ledger and metric evidence for local fixture zero-cost routes;
- blocker status for connected real-provider cost measurement when credentials are absent.

---

# 13. Backend Workstreams

## 13.1 Workstream A - capability model

Implement:

- `AiCapability` expanded vocabulary.
- `AiCapabilityDescriptor`.
- `AiCapabilityRegistry`.
- validation that every configured route references a known capability.
- validation that every enabled material capability has route policy, provider, timeout, max attempts, response size, and max cost.

Tests:

- all required descriptors exist;
- unknown capability config fails startup;
- disabled future capabilities do not execute;
- existing Phase 4 capabilities still route.

## 13.2 Workstream B - generic route policy

Implement:

- generic `AiRoutePolicyProperties`;
- adapter from old Phase 4 properties to new route registry;
- immutable `AiRoutePlan` construction;
- closed route release stage enum such as `LOCAL_ONLY`, `STAGING`, `PRODUCTION`, `DISABLED`.

Tests:

- existing env-backed Phase 4 config still maps correctly;
- local fixture provider is rejected in production when enabled;
- missing provider for enabled route fails closed;
- disabled capability returns `DISABLED`/configuration exception without provider call.

## 13.3 Workstream C - gateway execution engine

Implement one shared execution path:

```text
validate command
select route plan
check budget and disabled state
execute primary provider
retry bounded transient failures
fallback to approved route if configured
measure latency/cost
record usage
emit metrics
return result or typed failure
```

Tests:

- retryable primary failure uses fallback once.
- terminal failure does not use fallback.
- budget-exceeded route blocks without provider call.
- max response bytes violation fails without accepted result.
- gateway latency is recorded on success and failure.
- fallback provenance is marked exactly once.
- provider exception never leaks secrets or raw content.

## 13.4 Workstream D - provider adapter unification

Implement:

- generic `AiProviderAdapter`;
- local fixture generic adapter or wrappers;
- unavailable generic adapter;
- compatibility shims for existing typed provider interfaces if needed.

Tests:

- adapter declares supported capabilities.
- unsupported capability/provider pair fails closed.
- provider IDs are normalized consistently.
- duplicate provider IDs fail startup.

## 13.5 Workstream E - usage ledger

Implement:

- Flyway migration V016.
- JPA entity/repository under `ai.infrastructure.persistence`.
- application writer `AiUsageRecorder`.
- no-op or failing-safe fallback for tests where ledger is intentionally disabled.
- privacy lifecycle contributor if user-linked rows are retained in PostgreSQL.

Tests:

- migration applies with Testcontainers.
- successful gateway call writes one row.
- terminal provider failure writes one row.
- blocked budget writes one row or a documented blocked metric record.
- ledger row contains no raw prompt/payload/response.
- account deletion/export handles ledger records according to privacy policy.

## 13.6 Workstream F - Phase 4 compatibility

Update existing problem-module callers only as needed:

- `ProblemRecognitionApplicationService`
- `ProblemParseApplicationService`
- `ProblemClassificationApplicationService`

Rules:

- No change to public Phase 4 API shape.
- No change to durable recognition/parse/classification semantics.
- Keep existing provider/model/prompt/schema provenance on domain rows.
- Add ledger recording without making ledger the domain source of truth.
- `make eval-ai` must still pass.

## 13.7 Workstream G - architecture guardrails

Add or extend ArchUnit/modularity tests:

- no provider SDK imports outside `com.verifiedai.ai.infrastructure`.
- `problem`, `solving`, `verification`, `tutoring`, `mistake`, `mastery`, `studyplan`, and `exam` may depend on `ai.application` only, not `ai.infrastructure`.
- `ai` must not depend on `problem.infrastructure.persistence` or domain-owned JPA entities.
- route policy is configuration/application-owned, not embedded in prompts or UI.
- no catch-all `utils`, `helpers`, or generic technical dumping-ground packages.

---

# 14. Data Contract

## 14.1 Existing tables not to reinterpret

Do not reinterpret:

- `recognition_jobs`
- `recognition_evidence`
- `problem_parse_jobs`
- `problem_parses`
- `canonical_problems`
- `problem_classification_jobs`
- `problem_classifications`
- `problem_sessions.current_parse_id`

These are problem-domain facts. AI usage ledger rows may reference them but cannot become their authority.

## 14.2 New tables

Add `ai_usage_records` if usage ledger is in scope. Do not add:

- solver runs;
- solutions;
- verification runs;
- arbitration records;
- tutor sessions;
- training datasets;
- model experiments;
- vector indexes.

Those belong to later sprints or Phase 13.

## 14.3 Retention

Classify `ai_usage_records` as operational AI metadata:

- no raw student content;
- user-linked when needed for abuse/cost/account transparency;
- deletion/export handled by lifecycle contributor or documented retention exception;
- aggregate metrics can outlive user rows only without user identifiers.

---

# 15. API And iOS Contract

## 15.1 Public API

No new public mobile API is required by default.

Existing Phase 4 endpoints must continue to work:

- recognition start/status;
- parse start/status/review/correction/history;
- canonicalization;
- classification;
- problem-session history/detail/retry.

## 15.2 Internal API

No internal HTTP API is required unless provider configuration is exposed to admin tooling. If any internal endpoint is added:

- require admin/internal auth;
- hide secrets;
- return route metadata only;
- add audit records;
- document it in API contracts and runbooks.

## 15.3 iOS

Expected iOS change: none.

If backend response statuses or error codes change, update:

- iOS DTOs;
- view-model retry states;
- localized error copy;
- APIClient mapping tests;
- critical UI/recovery tests.

No iOS code may contain provider IDs, provider keys, model names used as business rules, prompt versions, or route policy decisions.

---

# 16. AI Evaluation Requirements

Sprint 5.1 should not add solving-quality claims. It must still run evaluation to prove no ingestion regression:

```text
make eval-ai
```

Required outcomes:

- local fixture ingestion evaluation PASS;
- zero false authoritative accept;
- zero false ready-for-solve;
- zero unsafe false accept;
- zero invented source reference count;
- connected mode remains BLOCKED unless an approved provider route is available;
- protected holdout remains BLOCKED unless restricted payload exists.

If route behavior changes for any existing Phase 4 capability, update:

- `evaluations/golden-datasets/parsing/ingestion-v1/manifest.yaml`;
- baseline comparison evidence;
- route provenance expectations;
- Sprint 5.1 execution report.

Do not promote a new route on local fixture evidence alone.

---

# 17. Testing Matrix

## 17.1 Backend unit tests

Add tests for:

- capability registry validation;
- route policy parsing;
- route plan immutability;
- fallback chain behavior;
- disabled capability behavior;
- production fixture-provider rejection;
- provider duplicate ID rejection;
- budget block before provider call;
- usage cost aggregation;
- metric tag sanitization.

## 17.2 Backend integration tests

Add Testcontainers coverage for:

- V016 migration;
- `ai_usage_records` constraints;
- ledger insert on success;
- ledger insert on terminal failure;
- account deletion/export interaction if lifecycle contributor is implemented.

## 17.3 Architecture tests

Add or extend:

```text
services/api/src/test/java/com/verifiedai/architecture/ModularityTest.java
```

Required assertions:

- provider SDK imports only under `ai.infrastructure`.
- product modules depend only on `ai.application`.
- no domain module imports `ai.infrastructure`.
- no AI route policy in prompt resources or iOS code.

## 17.4 Contract/schema tests

If adding schema files:

```text
packages/schemas/ai-usage-record.schema.json
```

then update:

- `packages/schemas/README.md`;
- `scripts/quality/check_contracts.py`.

## 17.5 Evaluation tests

Run:

```text
python3 -m unittest evaluations/runners/test_ingestion_evaluation.py
python3 evaluations/runners/validate_ingestion_dataset.py --json
make eval-ai
```

## 17.6 Full gates

Final gate:

```text
make check
```

If sandbox blocks Docker/Testcontainers or CoreSimulator, rerun with the required local permissions and record the reason in the execution report.

---

# 18. Rollout Strategy

## 18.1 Local/test

- Existing Phase 4 capabilities remain enabled through local fixture providers.
- Future Phase 5 capabilities are registered but disabled/unavailable.
- Usage ledger enabled in tests unless explicitly isolated.

## 18.2 Staging

- Real providers remain disabled until credentials, retention terms, region constraints, and approved route policy exist.
- A staging route may be enabled only with max-cost, timeout, retry, and response-size limits.
- Connected evaluation must run before any route is considered production candidate.

## 18.3 Production

- Production startup rejects enabled `LOCAL_FIXTURE`.
- Missing credentials for enabled real provider fail startup or fail closed by route status.
- Release stage must be explicit.
- No default production route may silently fall back to fixture or unavailable provider.

## 18.4 Feature flags

Recommended flags/config switches:

```text
AI_GATEWAY_GENERIC_EXECUTION_ENABLED
AI_USAGE_LEDGER_ENABLED
AI_ROUTE_<CAPABILITY>_<ROUTE_ID>_ENABLED
```

Flags may protect rollout, but they may not hide invariant failures. If generic gateway is disabled, old wrappers must still satisfy Phase 4 behavior until the new path is fully promoted.

---

# 19. Rollback And Recovery

Rollback options:

1. Disable the generic execution path and keep typed Phase 4 wrappers.
2. Disable newly defined future capabilities.
3. Disable ledger writes only if a documented no-ledger policy is acceptable for local/test; production material AI calls should fail closed or use a durable fallback record.
4. Revert route configuration to the last approved route policy version.
5. Roll back V016 only if no production rows exist or after applying a forward migration that stops reads/writes safely.

Operational runbook additions:

- provider outage;
- route misconfiguration;
- cost spike;
- schema-invalid spike;
- fallback spike;
- ledger write failure;
- accidental fixture-provider enablement attempt.

---

# 20. Documentation Updates Required During Implementation

Update at least:

- `ai/25_AI_ORCHESTRATION_AND_MODEL_ROUTING.md`
- `ai/26_PROMPT_SCHEMA_AND_VERSIONING.md` if prompt/schema registry references change
- `ai/29_AI_COST_LATENCY_AND_RELIABILITY.md`
- `backend/20_BACKEND_MODULE_CONTRACTS.md`
- `data/22_POSTGRESQL_DATA_MODEL.md` if V016 ledger is added
- `security/35_SECURITY_THREAT_MODEL.md`
- `security/36_PRIVACY_AND_STUDENT_DATA_PROTECTION.md`
- `operations/37_OBSERVABILITY_SLOS_AND_INCIDENTS.md`
- `operations/39_RUNBOOKS_AND_FAILURE_MODES.md`
- `quality/40_TEST_STRATEGY.md`
- `quality/42_DEFINITION_OF_DONE_AND_ACCEPTANCE.md`
- `roadmap/68_PRODUCTION_V1_CAPABILITY_COVERAGE_MATRIX.md`
- `quality/69_REQUIREMENTS_TRACEABILITY_MATRIX.md`
- `operations/71_TECHNICAL_DEBT_AND_MAINTENANCE_REGISTER.md`
- `DOCUMENTATION_MANIFEST.md`

Expected governance changes:

- `CAP-AI-001` can move to `Complete` only after generic provider-neutral gateway, architecture guardrails, route registry, fallback policy, production fixture guard, and usage ledger evidence pass.
- `REQ-AI-001` can move from `Foundation` to `Satisfied` only after product modules have no provider SDK dependency and architecture tests enforce it.
- `TD-AI-001`, `TD-AI-003`, and connected evaluation blockers remain open unless real approved provider routes are externally validated.
- Add a new debt item if Sprint 5.1 defines but cannot externally validate solver provider credentials, such as `TD-AI-006`.

---

# 21. Implementation Checklist

## 21.1 Preflight

- [ ] Confirm branch and dirty worktree state.
- [ ] Read highest-precedence docs.
- [ ] Record current `make eval-ai` result.
- [ ] Record current `make check` or component test status.
- [ ] Confirm no Phase 5 solving code is already partially implemented.

## 21.2 Capability model

- [ ] Expand `AiCapability`.
- [ ] Add capability descriptors.
- [ ] Add capability registry.
- [ ] Add validation for unknown/duplicate capabilities.
- [ ] Add disabled future capability definitions.

## 21.3 Route policy

- [ ] Add generic route property model.
- [ ] Preserve Phase 4 property compatibility.
- [ ] Build immutable route plans from route context.
- [ ] Enforce enabled/disabled state.
- [ ] Enforce production fixture-provider rejection.
- [ ] Enforce provider registration and duplicate checks.

## 21.4 Gateway execution

- [ ] Add generic `execute(AiExecutionCommand)`.
- [ ] Move fallback/retry handling to shared path.
- [ ] Add cost budget blocking before provider call.
- [ ] Add response size guard.
- [ ] Preserve typed wrapper methods.
- [ ] Preserve existing Phase 4 result semantics.

## 21.5 Provider adapters

- [ ] Add generic provider adapter interface.
- [ ] Adapt local fixture providers.
- [ ] Adapt unavailable providers.
- [ ] Keep real providers disabled unless credentials/config exist.
- [ ] Add mocked external-provider contract tests if external adapter is added.

## 21.6 Usage ledger

- [ ] Add V016 migration.
- [ ] Add entity/repository.
- [ ] Add recorder.
- [ ] Write records on success/failure/blocked states.
- [ ] Add lifecycle contributor or documented retention exception.
- [ ] Add privacy-safe export/delete behavior.

## 21.7 Observability

- [ ] Add gateway request/result metrics.
- [ ] Add latency histograms.
- [ ] Add estimated cost metrics.
- [ ] Add fallback/retry/blocked counters.
- [ ] Add structured logs without raw content.

## 21.8 Tests and gates

- [ ] Add unit tests.
- [ ] Add integration tests.
- [ ] Add architecture tests.
- [ ] Run `python3 scripts/quality/check_contracts.py`.
- [ ] Run `python3 scripts/quality/docs_check.py`.
- [ ] Run `scripts/security/secret_scan.sh`.
- [ ] Run `make eval-ai`.
- [ ] Run `make check`.

## 21.9 Execution report

- [ ] Create the Sprint 5.1 execution report as a new Markdown artifact after implementation.
- [ ] Include implementation summary.
- [ ] Include migration/API/schema diff.
- [ ] Include tests and evaluation evidence.
- [ ] Include blockers honestly.
- [ ] Include capability/requirement/debt status changes.
- [ ] State clearly that solving and verification were not implemented.

---

# 22. File-Level Plan

## 22.1 New or changed backend application files

Likely changed:

```text
services/api/src/main/java/com/verifiedai/ai/application/AiCapability.java
services/api/src/main/java/com/verifiedai/ai/application/AiModelGateway.java
services/api/src/main/java/com/verifiedai/ai/application/AiRoutePlan.java
services/api/src/main/java/com/verifiedai/ai/application/AiUsage.java
```

Likely new:

```text
services/api/src/main/java/com/verifiedai/ai/application/AiCapabilityDescriptor.java
services/api/src/main/java/com/verifiedai/ai/application/AiCapabilityRegistry.java
services/api/src/main/java/com/verifiedai/ai/application/AiExecutionCommand.java
services/api/src/main/java/com/verifiedai/ai/application/AiExecutionContext.java
services/api/src/main/java/com/verifiedai/ai/application/AiExecutionResult.java
services/api/src/main/java/com/verifiedai/ai/application/AiExecutionStatus.java
services/api/src/main/java/com/verifiedai/ai/application/AiRouteContext.java
services/api/src/main/java/com/verifiedai/ai/application/AiRoutePlanner.java
services/api/src/main/java/com/verifiedai/ai/application/AiRoutePolicy.java
services/api/src/main/java/com/verifiedai/ai/application/AiUsageRecorder.java
services/api/src/main/java/com/verifiedai/ai/application/AiUsageRecord.java
```

## 22.2 New or changed backend infrastructure files

Likely changed:

```text
services/api/src/main/java/com/verifiedai/ai/infrastructure/provider/ConfiguredAiModelGateway.java
services/api/src/main/java/com/verifiedai/ai/infrastructure/configuration/AiInfrastructureConfiguration.java
services/api/src/main/resources/application.yml
services/api/src/main/resources/application-prod.yml
services/api/src/main/resources/application-test.yml
```

Likely new:

```text
services/api/src/main/java/com/verifiedai/ai/infrastructure/configuration/AiCapabilityRouteProperties.java
services/api/src/main/java/com/verifiedai/ai/infrastructure/configuration/AiRouteRegistryProperties.java
services/api/src/main/java/com/verifiedai/ai/infrastructure/provider/AiProviderAdapter.java
services/api/src/main/java/com/verifiedai/ai/infrastructure/provider/AiProviderRequest.java
services/api/src/main/java/com/verifiedai/ai/infrastructure/provider/AiProviderResponse.java
services/api/src/main/java/com/verifiedai/ai/infrastructure/provider/GenericLocalFixtureProviderAdapter.java
services/api/src/main/java/com/verifiedai/ai/infrastructure/provider/GenericUnavailableProviderAdapter.java
services/api/src/main/java/com/verifiedai/ai/infrastructure/persistence/AiUsageRecordJpaEntity.java
services/api/src/main/java/com/verifiedai/ai/infrastructure/persistence/AiUsageRecordJpaRepository.java
services/api/src/main/resources/db/migration/platform/V016__create_ai_usage_ledger.sql
```

## 22.3 Tests

Likely changed:

```text
services/api/src/test/java/com/verifiedai/ai/infrastructure/provider/ConfiguredAiModelGatewayTest.java
services/api/src/test/java/com/verifiedai/architecture/ModularityTest.java
```

Likely new:

```text
services/api/src/test/java/com/verifiedai/ai/application/AiCapabilityRegistryTest.java
services/api/src/test/java/com/verifiedai/ai/application/AiRoutePlannerTest.java
services/api/src/test/java/com/verifiedai/ai/application/AiUsageRecorderTest.java
services/api/src/test/java/com/verifiedai/ai/infrastructure/provider/GenericAiProviderAdapterTest.java
services/api/src/test/java/com/verifiedai/ai/infrastructure/persistence/AiUsageLedgerIntegrationTest.java
services/api/src/test/java/com/verifiedai/architecture/AiProviderBoundaryTest.java
```

## 22.4 Schemas and docs

Potentially new:

```text
packages/schemas/ai-usage-record.schema.json
```

Changed:

```text
packages/schemas/README.md
scripts/quality/check_contracts.py
docs/verified_ai_learning_platform_docs_v4/DOCUMENTATION_MANIFEST.md
```

---

# 23. Acceptance Criteria

Sprint 5.1 can be accepted only if:

- [ ] Existing Phase 4 AI capabilities still work through provider-neutral contracts.
- [ ] Future Phase 5 AI capability IDs are defined but disabled/unavailable until their owning sprint.
- [ ] No product/domain module imports provider SDK classes.
- [ ] No provider secret can be configured in iOS.
- [ ] Production startup rejects enabled local fixture providers.
- [ ] Gateway result semantics distinguish success, retryable failure, terminal failure, disabled, provider unavailable, policy blocked, and budget blocked.
- [ ] Fallback is policy-driven and provenance marks fallback exactly.
- [ ] Cost/latency/usage is recorded in a consolidated ledger or an explicit documented deferral exists.
- [ ] Ledger and metrics contain no raw student content or provider secrets.
- [ ] Existing recognition/parser/classification provenance remains intact.
- [ ] `make eval-ai` remains PASS for local ingestion.
- [ ] Connected/provider evaluation is not claimed unless actually run.
- [ ] Docs, contracts, secret scan, backend tests, verifier tests, iOS tests, and architecture checks pass.
- [ ] Execution report states clearly that solving and verification are still out of scope.

---

# 24. Handoff To Later Phase 5 Sprints

## Sprint 5.2

Can rely on:

- capability descriptors;
- prompt/schema fields in route policy;
- immutable route plan metadata.

Must still implement:

- full prompt registry governance;
- prompt release workflow;
- prompt examples and schema promotion process.

## Sprint 5.3

Can rely on:

- typed route context;
- route planner abstraction;
- usage/cost ledger;
- fallback and disabled-route semantics.

Must still implement:

- difficulty/entitlement/provider-health optimization;
- cost-budget hierarchy;
- canary/rollout route policy.

## Sprint 5.4

Can rely on:

- `SOLVE` capability being registered;
- generic gateway execution;
- provider-neutral provenance and usage recording.

Must still implement:

- solver job/domain model;
- solution candidate schema;
- durable solution candidate persistence;
- solver-specific validation.

## Sprint 5.5

Can rely on:

- route role or future secondary capability extension point;
- usage ledger with escalation/fallback fields.

Must still implement:

- secondary solver invocation policy;
- blind independence enforcement;
- agreement/disagreement evidence.

## Sprint 5.7 through 5.11

Can rely on:

- gateway output not being treated as verified truth;
- route provenance and usage records;
- explicit blocked/failure states.

Must still implement:

- verification planner;
- deterministic verification methods;
- arbitration;
- `VERIFIED`/`PARTIALLY_VERIFIED`/`UNVERIFIED` policy.

---

# 25. Known Limitations After Sprint 5.1

Expected honest limitations:

- Real provider credentials may remain unavailable.
- Connected provider evaluation may remain blocked.
- Protected holdout evaluation may remain blocked.
- No solver output exists until Sprint 5.4.
- No verification status exists until Sprint 5.7 through Sprint 5.11.
- No global AI release gate completion exists until Sprint 5.12.
- No proprietary model or self-hosted route exists before Phase 13 gates.

These limitations should be recorded as technical debt or future sprint scope, not hidden behind successful local fixture tests.

---

# 26. Final Evidence Package

Sprint 5.1 execution report must include:

- branch and commit or diff scope;
- files changed;
- migration summary;
- gateway/capability architecture summary;
- route policy examples;
- usage ledger sample with no sensitive content;
- architecture test evidence;
- backend test summary;
- verifier test summary;
- iOS no-change or test summary;
- `make eval-ai` result;
- docs/contracts/secret scan result;
- full `make check` result;
- capability/requirement/debt status updates;
- blockers and non-goals.

Minimum final commands:

```text
python3 scripts/quality/check_contracts.py
python3 scripts/quality/docs_check.py
scripts/security/secret_scan.sh
python3 -m unittest evaluations/runners/test_ingestion_evaluation.py
python3 evaluations/runners/validate_ingestion_dataset.py --json
make eval-ai
make check
```

If `make check` requires Docker/Testcontainers or CoreSimulator permissions, the execution report must state that explicitly.
