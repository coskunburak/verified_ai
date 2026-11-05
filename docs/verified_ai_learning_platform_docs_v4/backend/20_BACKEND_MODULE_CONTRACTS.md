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

## Sprint 5.10 verification composition

Verification owns `CompositeEvidenceAssessment`, `CompositeEvidencePolicy` and the internal
owner-authorized `CompositeVerification.current` read operation. It projects one immutable
run only after current source/plan and secondary-parent eligibility checks. It performs
no network or persistence writes. The optional `NumericExecutor` port uses strict
`NumericWireCodec` and existing bounded HTTP/1.1 transport, shares admission slots, and
is not called automatically by planning/execution. Numeric runtime/owner flags are false
by default and production is guarded. No public API, iOS consumer or final-status policy changes.

## Sprint 5.11 arbitration boundary

Verification owns policy, recovery advice, immutable decision storage and `/api/v1` decision
controllers. It consumes `SecondaryCorroborationQuery` through solving's named application
interface, with no dependency on solving persistence, provider routing or billing.
The query only reads bounded owned secondary records; it never invokes secondary solving.
Canonical REQUIRES_NEW reads complete before source-owner transactions. Policy evaluation
occurs between short preparation/publication transactions; publication rechecks the exact
snapshot. Current GET returns explicit null when disabled, absent, stale or expired retry
advice. POST accepts an owned solution/run key and UUID idempotency key, never a supplied
status/confidence/evidence body. Only primary solution aggregate publication is exposed.

### Sprint 5.11 calculus composition closure — 2026-10-31

Verification owns CalculusCompositePolicy, the tagged composite-calculus-evidence-v1
assessment and the three bounded calculus decision scopes. It reuses the existing
CalculusEvidence/CalculusExecution contracts and verifies full source metadata equality
before arbitration. No HTTP/verifier/provider call occurs during composition or decision
publication. Canonical reads precede owner locking, and current source/capability/run state
is rechecked before publication. New real-worker and race regressions await owner execution.

## Sprint 6.4 tutoring implementation

Tutoring owns sessions/messages and consumes problem `tutor-source` and profile `tutor-context` named
interfaces only. The problem facade owns selected-source readiness/lineage and account/problem locks.
Tutoring JDBC touches its own tables. Privacy participates in existing AccountDataLifecycleContributor;
identity deletes completed export snapshots during confirmed account deletion. No AI, verifier, billing
debit or assistant generation dependency is added. Persistence access requires active ownership, not a
premium inference entitlement. Detailed operations and limits are in the Sprint 6.4 implementation map.

## Sprint 6.5 deterministic tutoring

Tutoring additionally owns SocraticPolicy, TutorTurn and additive tutor_turns persistence. Existing
problem/profile named interfaces remain sufficient because this slice makes no mathematical assessment.
The AI gateway/registry/adapter is unchanged: TUTOR stays disabled. No direct inference or verifier call.
New turn operations use the existing active owner → problem locks with a five-second transaction timeout;
learner append, local response and phase update commit together. There is no external pending worker.
Same-key replay is the recovery contract. Legacy messages stay learner-only. Source staleness and
closure prevent new work while history/replay remain readable. Feature flag defaults disabled.

## Sprint 6.6 hint boundary

Problem's existing `tutor-source` interface adds TutorHintSource structural family classification.
Only TWO_STEP_LINEAR/UNSUPPORTED leaves that boundary; no canonical source text or answer enters Tutor.
Tutor owns HintPolicy, TutorHint and V029 storage. Progress reads merge turn/hint snapshots by shared
interaction count under the existing account → problem lock. Deterministic publication and storage are
atomic, with five-second request transactions and no external calls. Billing, mastery and AI routing
remain unchanged. A future authorized answer reader is a separate contract/release prerequisite.

## Sprint 6.7 attempt ownership

Attempt owns immutable attempts, twin/method artifacts, learning events/mastery and privacy contribution. Problem
publishes ManualStepNormalizer/AttemptExtractionReader/AttemptSourceDocument through its attempt interface;
SolveSourceAccess owns account/session locks and source revisions. Tutoring publishes a read-only assistance reader;
existing HintPolicy/SocraticPolicy remain sole hint/progression owners. AttemptLearningService prepares owned work
in short transactions, calls the bounded verifier outside locks, then rechecks source and publishes atomically.
A source change marks analysis stale; learning artifacts are not published against stale sources. Committed drill
history/completion remains recoverable. No provider, billing or hint authority is duplicated.

## Sprint 7.1 ownership extension

AttemptSubmissionRequest is a typed API command with strict bundled-schema decoding. StudentAttemptOrchestrator
owns final-answer acceptance, exact source binding, capacity, idempotency and capture-only semantics; the controller
only authenticates, routes and negotiates historical display. The same repository, source lock order and privacy
contributor remain authoritative. No new aggregate/repository, model client or cross-module infrastructure access.
The legacy verifier failure boundary returns reauthorized durable evidence; ordinary reads never re-evaluate it.
