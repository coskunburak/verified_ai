# Sprint 7.2 implementation map

Baseline: main / 8591761b0019396cde94a5cba5beb18bcd639eb7. Existing owner edits in
the parser, tutoring and security tooling are preserved. Tests/builds are owner-only.

2026-11-06 repair: corrected SolveSource canonicalProblemId access in service/integration setup and replaced
index-retaining structured editor bindings with ID/source/predecessor-aware bindings. Added lifecycle regressions
and --failed-from owner selection for the unexecuted backend suite and recorded failed XCTest methods.
The execution report records owner failure evidence and the pending rerun; no current-source PASS is claimed.

## Reuse and gaps

ALREADY_IMPLEMENTED: StudentAttempt aggregate, owner/account/source transaction locks,
bounded request filter, immutable V030–V033 evidence, privacy lifecycle, native Vision,
gateway recognition reader, ManualStepNormalizer, protected pending storage, account and
generation fences, final-answer intent, EN/TR and A–G owner acceptance architecture.

Missing: independent immutable transcription observations, stable reviewed step identities,
many-to-many segmentation links, explicit syntax/review states, capture-only photo/manual
structured submission, normalized preview, source-aware acceptance for these contracts.

## Contract decided before implementation

Owning module: attempt. POST /api/v1/problem-sessions/{problemId}/attempts/structured-work
accepts attempt-structured-work-request-v1. GET /attempts/{id}/structured-work returns
attempt-structured-work-v1 or 404 for historical attempts without that artifact. Existing
submission v1/v2/v3 hashes and student-attempt v1/v2 contracts remain unchanged.

Request: source, engine (MANUAL/NATIVE_VISION/GATEWAY_RECOGNITION), nullable captureId,
assetSessionId, recognitionJobId, supersedesAttemptId; observations (id, rawText,
nullable confidence/box); steps (id, text, role INTERMEDIATE/FINAL_ANSWER/AMBIGUOUS,
observationIds, confirmed). All keys are required, nullable values are explicit.
1–12 steps and 0–12 observations (1–12 for OCR); manual observations empty; at most one explicit final answer.
All raw observations must remain linked, including after splitting/merging/reordering.
Unconfirmed/ambiguous steps persist as REVIEW_REQUIRED, with no normalized statement.
Confirmed unsupported syntax persists as UNSUPPORTED. Successful parsing is NORMALIZED,
never correctness. Original typed evidence is exact text; original OCR observations stay
separate from reviewed text. Native metadata is CLIENT_ATTESTED, gateway is SERVER_RECORDED.
No client provider/model/prompt authority fields are accepted.

Response: immutable ownerId, attemptId, problemSessionId, source, revision,
supersedesAttemptId, schemaVersion, normalizationVersion, createdAt, trainingEligible=false,
engine/provenance, capture/asset/job identity, nullable provider/model/prompt version,
observations, and ordered steps with stable IDs, originalText, reviewedText, normalizedStatement,
normalizationStatus, reviewStatus, role and observationIds. OriginalText is nullable for OCR
because many-to-many observations are the original transcription authority.

## Flow and persistence

Input → native/existing gateway extraction → explicit segmentation/text review → bounded
typed command → owned source lock → existing normalizer → immutable StudentAttempt plus
1:1 derived artifact in the same transaction. No verifier, mistake or mastery publication.
Legacy history projects exact reviewed content as unassessed; artifact owns full provenance.
Explicit final answer is also retained in the existing final_answer field. Revision creates
a new attempt referencing its predecessor, never alters accepted evidence. Revisions must
share owned canonical source and cannot fork a superseded revision. Existing capacity 30 applies. Revisions also freeze engine/capture/job and the entire observation collection.

V034 adds only attempt_structured_work; composite owner FK, unique predecessor, bounded JSON,
immutable update/membership guards, cascade deletion and owner index. Separate artifact is
necessary to freeze parser version and original observations without weakening V033 or
reinterpreting historical records. Export joins existing privacy lifecycle; DERIVED_LEARNING
retention, account/source deletion and backup expiration apply. No backfill or repair.

## iOS, operations and rollback

Capability flag is additive to context-v2; new structured rollout defaults off and requires
existing attempt production authorization. Native capture ID identifies local evidence only,
never claims server ownership. Review displays source crop, raw text, confidence warning,
editable text, movement and split/merge controls. Protected drafts/pending requests preserve
evidence and key; source/account/generation fences apply. EN/TR labels distinguish saved,
structured and verified. History artifact reads work while feature is disabled.

Native crop previews use existing account-purged, backup-excluded protected local storage (2 MB each, at most 30).
Gateway coordinate origin is unspecified by the existing contract; only full-image review is shown for it.
The existing ASCII parser grammar is retained; no implicit multiplication/Unicode repair is invented.
Normalized statement size is capped at 1800 characters per step to preserve the bounded artifact envelope.

Metrics use bounded outcome/status tags and duration only. No content/account/provider tags.
Cost: zero new model calls; gateway reader consumes an existing recognition job only.
Rollback disables structured creation while preserving historical reads/export/deletion;
retain V034 and compatible reader. ADR-011/50-student TestFlight and production permission
remain external, unverified release gates.

## Acceptance matrix

Gate A inventory/immutable migration baseline; B domain, PostgreSQL upgrade, concurrency,
ownership, immutable evidence, retry and privacy; C strict schema/OpenAPI and synthetic math
goldens plus legacy contracts; D XCTest/UI review, retry, source/account isolation, EN/TR;
E fresh security/privacy backend evidence and source secret boundary; F canonical docs,
manifest/traceability/diff hygiene; G verifier regressions and evidence-writer adversarial tests.
Every gate records PASS/FAIL/BLOCKED with fresh source fingerprints. Agent executes none.

## Sprint file inventory

Only the following files belong to this sprint; pre-existing owner edits are excluded.

### Backend and migration

- services/api/src/main/java/com/verifiedai/attempt/api/AttemptRequestLimitFilter.java
- services/api/src/main/java/com/verifiedai/attempt/api/AttemptWorkController.java
- services/api/src/main/java/com/verifiedai/attempt/api/AttemptWorkRequest.java
- services/api/src/main/java/com/verifiedai/attempt/application/AttemptWorkNormalizer.java
- services/api/src/main/java/com/verifiedai/attempt/application/AttemptWorkService.java
- services/api/src/main/java/com/verifiedai/attempt/application/StudentAttemptOrchestrator.java
- services/api/src/main/java/com/verifiedai/attempt/domain/AttemptStructuredWork.java
- services/api/src/main/java/com/verifiedai/attempt/domain/AttemptWorkInput.java
- services/api/src/main/java/com/verifiedai/attempt/infrastructure/persistence/AttemptWorkRepository.java
- services/api/src/main/java/com/verifiedai/attempt/infrastructure/persistence/StudentAttemptRepository.java
- services/api/src/main/java/com/verifiedai/attempt/infrastructure/privacy/StudentAttemptPrivacyLifecycleContributor.java
- services/api/src/main/java/com/verifiedai/problem/application/attempt/AttemptExtractionReader.java
- services/api/src/main/java/com/verifiedai/sharedkernel/contracts/VersionedJsonContract.java
- services/api/src/main/resources/application.yml
- services/api/src/main/resources/db/migration/platform/V034__attempt_structured_work.sql

### Backend test sources

- services/api/src/test/java/com/verifiedai/attempt/api/AttemptWorkControllerTest.java
- services/api/src/test/java/com/verifiedai/attempt/api/AttemptWorkRequestTest.java
- services/api/src/test/java/com/verifiedai/attempt/application/AttemptWorkNormalizerTest.java
- services/api/src/test/java/com/verifiedai/attempt/application/AttemptWorkProvenanceTest.java
- services/api/src/test/java/com/verifiedai/attempt/integration/AttemptWorkIntegrationTest.java

### iOS

- apps/ios/VerifiedAI.xcodeproj/project.pbxproj
- apps/ios/VerifiedAI/Features/AttemptAnalysis/Data/AttemptAnalysisAPI.swift
- apps/ios/VerifiedAI/Features/AttemptAnalysis/Data/AttemptAnalysisPendingStore.swift
- apps/ios/VerifiedAI/Features/AttemptAnalysis/Data/FixtureAttemptAnalysisAPI.swift
- apps/ios/VerifiedAI/Features/AttemptAnalysis/Data/NativeAttemptExtraction.swift
- apps/ios/VerifiedAI/Features/AttemptAnalysis/Domain/AttemptAnalysisModels.swift
- apps/ios/VerifiedAI/Features/AttemptAnalysis/Presentation/AnalyzeMyWorkCaptureView.swift
- apps/ios/VerifiedAI/Features/AttemptAnalysis/Presentation/AnalyzeMyWorkViewModel.swift
- apps/ios/VerifiedAI/Features/AttemptAnalysis/Presentation/AttemptAnalysisResultsView.swift
- apps/ios/VerifiedAI/Features/AttemptAnalysis/Presentation/AttemptStepEditorView.swift
- apps/ios/VerifiedAI/Features/AttemptAnalysis/Presentation/AttemptStepReviewView.swift
- apps/ios/VerifiedAI/Resources/Localizable.xcstrings
- apps/ios/VerifiedAITests/AttemptAnalysis/AttemptAnalysisTests.swift
- apps/ios/VerifiedAIUITests/AttemptAnalysisUITests.swift

### Contracts and fixtures

- packages/contracts/openapi/public-api.yaml
- packages/schemas/attempt-context-v2-response.schema.json
- packages/schemas/attempt-structured-work-request-v1.schema.json
- packages/schemas/attempt-structured-work-v1.schema.json
- packages/schemas/fixtures/attempt-structured-work-gateway-v1.json
- packages/schemas/fixtures/attempt-structured-work-golden.json
- packages/schemas/fixtures/attempt-structured-work-invalid-v1.json
- packages/schemas/fixtures/attempt-structured-work-native-v1.json
- packages/schemas/fixtures/attempt-structured-work-request-v1.json
- packages/schemas/fixtures/attempt-structured-work-v1.json

### Acceptance and Python test sources

- scripts/quality/check_structured_work_contracts.py
- scripts/quality/sprint_7_2_evidence.py
- scripts/quality/sprint_7_2_owner_acceptance.sh
- services/math-verifier/tests/test_sprint_7_2_evidence.py
- services/math-verifier/tests/test_structured_work_golden.py

### Canonical documentation

- docs/verified_ai_learning_platform_docs_v4/00_MASTER_INDEX.md
- docs/verified_ai_learning_platform_docs_v4/DOCUMENTATION_MANIFEST.md
- docs/verified_ai_learning_platform_docs_v4/architecture/14_API_DESIGN_AND_CONTRACTS.md
- docs/verified_ai_learning_platform_docs_v4/backend/20_BACKEND_MODULE_CONTRACTS.md
- docs/verified_ai_learning_platform_docs_v4/data/22_POSTGRESQL_DATA_MODEL.md
- docs/verified_ai_learning_platform_docs_v4/data/23_DATA_LIFECYCLE_RETENTION_AND_AUDIT.md
- docs/verified_ai_learning_platform_docs_v4/domain/07_DOMAIN_MODEL_AND_AGGREGATES.md
- docs/verified_ai_learning_platform_docs_v4/domain/09_DOMAIN_EVENTS_AND_STATE_MACHINES.md
- docs/verified_ai_learning_platform_docs_v4/domain/10_DOMAIN_INVARIANTS_AND_BUSINESS_RULES.md
- docs/verified_ai_learning_platform_docs_v4/ios/17_IOS_NETWORKING_PERSISTENCE_AND_OFFLINE.md
- docs/verified_ai_learning_platform_docs_v4/learning/79_PEDAGOGICAL_DIAGNOSIS_HANDWRITING_UX_AND_RETENTION_ENGINE.md
- docs/verified_ai_learning_platform_docs_v4/quality/69_REQUIREMENTS_TRACEABILITY_MATRIX.md
- docs/verified_ai_learning_platform_docs_v4/sprints/phase07_attempt_mistake_and_mastery_intelligence/PHASE_07_ATTEMPT_MISTAKE_AND_MASTERY_INTELLIGENCE.md
- docs/verified_ai_learning_platform_docs_v4/sprints/phase07_attempt_mistake_and_mastery_intelligence/SPRINT_7.2_ATTEMPT_STEP_EXTRACTION_AND_STRUCTURED_WORK_REPRESENTATION.md
- docs/verified_ai_learning_platform_docs_v4/sprints/phase07_attempt_mistake_and_mastery_intelligence/SPRINT_7.2_EXECUTION_REPORT.md
- docs/verified_ai_learning_platform_docs_v4/sprints/phase07_attempt_mistake_and_mastery_intelligence/SPRINT_7.2_FUNCTIONAL_COMPLETION_MATRIX.md
- docs/verified_ai_learning_platform_docs_v4/sprints/phase07_attempt_mistake_and_mastery_intelligence/SPRINT_7.2_IMPLEMENTATION_MAP.md
