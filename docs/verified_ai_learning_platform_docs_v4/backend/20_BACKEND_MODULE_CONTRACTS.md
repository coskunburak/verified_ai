# Backend Module Contracts

## identity
Owns user account, external identity bindings, refresh token/session lifecycle.

Commands: authenticateWithApple, refreshSession, logout, requestDeletion.

## profile
Owns learning profile and preferences. Cannot mutate identity, entitlement, or mastery. Current API scope is authenticated `/api/v1/me/learning-profile` read/upsert with one profile per user and optimistic update conflicts.

## curriculum
Owns canonical skill graph and curricula. Primarily read/query at runtime; changes use controlled import/admin process.

## problem
Owns problem sessions, asset metadata, parse versions, selected parse and solve-request lifecycle.

Sprint 4.2 scope within `problem`:
- owns `ProblemSession` minimal lifecycle `CREATED -> ASSET_UPLOADED`;
- owns `ProblemAsset` identity, user ownership, object key, upload status, metadata, checksum expectation, and retention class;
- exposes thin upload controllers under `/api/v1/uploads`;
- depends on object storage through `ProblemAssetStorage`, not directly from domain/application semantics;
- depends on billing only through the public `CapabilityAccessPolicy` application interface and never on StoreKit/App Store internals.

Sprint 4.4 scope within `problem`:
- owns `RecognitionJob` and `RecognitionEvidence` persistence because evidence belongs to a user-owned `ProblemSession`;
- consumes only the selected READY `OCR_OPTIMIZED` derivative from Sprint 4.3;
- calls the provider-neutral `AiModelGateway` application interface for `VISION_PARSE`;
- validates untrusted provider JSON, coordinates, confidence, reading order, size limits, and uncertainty before normalized evidence is stored;
- must not import OpenAI, Gemini, Apple Vision, or other provider SDK classes;
- does not create `ProblemParse`, canonical math, classification, solving, verification, or mastery evidence.

Sprint 4.5 scope within `problem`:
- owns `ProblemParseJob` and `ProblemParse` persistence because parser revisions belong to a user-owned `ProblemSession`;
- consumes only exact accepted `RecognitionEvidence` id/revision, not object storage or arbitrary latest provider output;
- calls the provider-neutral `AiModelGateway` application interface for `PROBLEM_NORMALIZE`;
- validates untrusted parser JSON, strict `problem-parse-v1` schema, subject/topic taxonomy, task/problem support, variables, constraints, source block references, uncertainty, and quality-risk propagation before normalized parse data is stored;
- persists raw parser output separately from normalized parser output and excludes raw parser output from normal product API/export payloads;
- must not import OpenAI, Gemini, Apple Vision, or other provider SDK classes;
- does not create canonical safe math, primary skill/difficulty classification, solve jobs, verification evidence, mastery evidence, or Sprint 4.8 user-correction behavior.

Sprint 4.8 scope within `problem`:
- owns user-corrected `ProblemParse` revisions and `ProblemSession.current_parse_id` selection authority;
- validates correction ownership, base revision freshness, correction size, idempotency fingerprint, and normalized problem semantics server-side;
- records correction lineage and corrected-field metadata without overwriting prior parse revisions or fabricating AI provider provenance;
- exposes review, correction, and revision-history APIs under the existing problem controller boundary;
- ensures canonicalization consumes the selected parse pointer instead of latest parse revision;
- exports correction metadata through the problem lifecycle contributor while excluding request hashes, idempotency keys, raw parser output, and raw student content from generic logs/metrics.

## solving
Receives canonical Problem, creates SolverRuns and Solution. Does not decide VERIFIED.

## verification
Owns verification planner, policy, runs and signals. Sole authority for overall verification status.

## tutoring
Owns tutor session state, hint policy and tutor-specific progression.

## attempt
Owns student-submitted work and attempt evaluation. Publishes evaluation facts.

## mistake
Owns taxonomy and structured diagnoses. Consumes attempt evidence.

## mastery
Owns User × Skill state/history. Consumes approved evidence events.

## studyplan
Reads mastery, exam context and review due state. Creates structured plans. Cannot mutate mastery directly.

## exam
Owns exam definitions, user targets, readiness and mock assessment orchestration.

## billing
Owns entitlement state, App Store product mapping, Apple transaction verification, subscription lifecycle projection, notification ingestion and reconciliation. Current Sprint 3.5/3.6 scope includes:
- `GET /api/v1/me/entitlements` for backend-authoritative access state.
- `GET /api/v1/me/billing/apple/configuration` for purchase availability, backend product catalog and stable `appAccountToken`.
- `POST /api/v1/me/billing/apple/transactions` for authenticated StoreKit verified transaction JWS submission with `Idempotency-Key`.
- `POST /api/v1/webhooks/apple/app-store` for unauthenticated App Store Server Notifications V2 signed payload ingestion.

Other modules query capabilities and entitlement state. They do not depend on StoreKit internals, App Store product IDs, client-provided tier flags, or local purchase UI state.

## ai
Owns model routing, provider adapters, prompt registry access and AI usage accounting. It does not own learning/business meaning.

Sprint 4.4 pulls forward the minimum `VISION_PARSE` capability boundary, local fixture provider, route/provenance/usage records, and provider-adapter contract required by recognition. Sprint 4.5 extends that subset to `PROBLEM_NORMALIZE` for parser normalization. Full solver/tutor/mistake-classifier routing remains Sprint 5.1+.

## admin
Read-heavy operational tooling. Any mutation goes through normal domain commands; no direct DB edit endpoint.

<!-- HYBRID_AI_STRATEGY_V3:START -->
## AI and future ModelOps contract

`ai` may depend on external provider adapters and inference clients. Product modules depend only on capability ports/application services.

Future `modelops` tooling may read governed evaluation/training artifacts but cannot directly mutate learner mastery, verification, billing, or exam state. Promotion of a model changes routing configuration, not domain ownership.
<!-- HYBRID_AI_STRATEGY_V3:END -->

## Sprint 5.8 verification execution boundary

`verification.application` owns authorized orchestration through a typed mathematical executor port and run repository port.
Only `verification.infrastructure.http` implements network transport; only its persistence adapter writes verification-owned rows.
Neither adapter can access problem/solving repositories or invoke AI providers. Domain/application code cannot depend on HTTP/JDBC adapters.
Existing owner-scoped canonical application interfaces remain the sole source authorities.
No public endpoint, iOS contract, provider route, paid inference or aggregate verification policy is introduced.

## Sprint 5.9 isolated calculus contract

Verification owns `CalculusExecution` and `CalculusWireCodec`. The codec is not a Spring
component, production executor or canonical-source reader. It validates method, scope,
versions, restriction references, exact witnesses and non-proof diagnostics. The owner
contract harness connects its bytes to the authenticated Python calculus HTTP path.
No calculus source assembly, network execution in an owner transaction, planner activation
or persistence adapter is added. Existing owned reads and immutable publication remain
unchanged. Source-backed calculus integration remains BLOCKED.

Verifier HTTP clients explicitly select HTTP/1.1, matching Uvicorn's transport. They do
not request h2c upgrades that can cause POST bodies to be skipped. Authentication,
body limits, deadlines, no-redirect/no-retry policy and transaction boundaries remain.

## Sprint 5.9 production integration closure

POST /api/v1/problem-sessions/{sessionId}/calculus-parse-revisions is an additive authenticated
problem-owned USER correction path. It accepts a strict problem-parse-v2 envelope and
preserves base revision, recognition lineage, review, rate and idempotency rules. Existing
parse/canonical/solve responses admit explicit v2 alternatives without changing v1 shapes.
The solving-owned local/test fixture admission is disabled by default and writes through
the normal job/candidate/projection lifecycle without model inference. The verification
module assembles requests through its owned source reader, selects a calculus policy and
uses a typed CalculusExecutor port. Authenticated /internal/v1/verify/calculus-readiness
exposes only fixed versions/flag; it is not public deployment approval. The new bounded
shared-kernel contract reader accepts only three bundled v2 schemas and local refs.

Status: IMPLEMENTED_PENDING_OWNER_VALIDATION / CI_PENDING / ROLLOUT_DISABLED.
See [closure report](../sprints/phase05_ai_solving_and_verification_core/SPRINT_5.9_PRODUCTION_INTEGRATION_CLOSURE_EXECUTION_REPORT.md).
