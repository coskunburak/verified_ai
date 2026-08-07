# Complete Production Repository and File Hierarchy

## Document purpose

This document is the canonical, exhaustive repository topology for the Verified AI Learning Platform. It defines where production code, tests, schemas, prompts, infrastructure, documentation, generated artifacts, developer tooling, operational runbooks, and AI evaluation assets belong.

It is intentionally more detailed than the individual iOS and backend hierarchy documents. If an engineer or coding agent needs to decide **where a new file belongs**, this document is the primary repository-structure reference.

This file does not replace domain ownership rules. Module boundaries remain governed by `domain/08_BOUNDED_CONTEXTS_AND_MODULE_BOUNDARIES.md`, `backend/20_BACKEND_MODULE_CONTRACTS.md`, and accepted ADRs.

---

# 1. Repository design principles

1. **Monorepo, multiple deployables.** The iOS application, Spring Boot API, internal Python math verifier, shared contracts, infrastructure, and documentation live in one versioned repository.
2. **Business capability before technical layer.** Backend and iOS feature code are organized around product/domain capabilities, not giant global `controllers`, `services`, `views`, or `utils` folders.
3. **No ambiguous dumping grounds.** `common`, `misc`, `helpers`, and `utils` are forbidden as catch-all locations.
4. **Generated files are isolated.** Generated API clients, schemas, coverage, build output, code generation, and derived datasets never live beside hand-authored source files without an explicit generated directory.
5. **Secrets are never repository content.** Only templates such as `.env.example` may be committed.
6. **AI prompts and evaluation data are first-class source artifacts.** They are version-controlled and reviewed like production code.
7. **Docs are executable context.** Architecture, domain, security, rollout, and operational documents are part of the implementation contract.
8. **Every deployable is independently buildable and testable.**
9. **Every critical runtime boundary has contracts and contract tests.**
10. **The hierarchy is optimized for humans and coding agents.** Naming must make ownership inferable without reading every file.

---

# 2. Canonical repository root

```text
verified-ai-learning-platform/
│
├── .github/
│   ├── CODEOWNERS
│   ├── dependabot.yml
│   ├── pull_request_template.md
│   ├── ISSUE_TEMPLATE/
│   │   ├── bug_report.yml
│   │   ├── feature_request.yml
│   │   ├── security_report.yml
│   │   └── ai_quality_regression.yml
│   └── workflows/
│       ├── ios-ci.yml
│       ├── backend-ci.yml
│       ├── math-verifier-ci.yml
│       ├── contract-ci.yml
│       ├── ai-evaluation.yml
│       ├── security-scan.yml
│       ├── dependency-audit.yml
│       ├── docs-link-check.yml
│       ├── staging-deploy.yml
│       ├── production-deploy.yml
│       ├── production-rollback.yml
│       └── release-tag.yml
│
├── .vscode/
│   ├── extensions.json
│   └── settings.json
│
├── apps/
│   └── ios/
│       ├── VerifiedLearning.xcodeproj/
│       ├── VerifiedLearning.xcworkspace/
│       ├── Config/
│       ├── VerifiedLearning/
│       ├── VerifiedLearningTests/
│       ├── VerifiedLearningUITests/
│       ├── Packages/
│       ├── Scripts/
│       ├── Resources/
│       ├── PreviewContent/
│       └── README.md
│
├── services/
│   ├── api/
│   │   ├── pom.xml
│   │   ├── Dockerfile
│   │   ├── docker-entrypoint.sh
│   │   ├── src/
│   │   ├── scripts/
│   │   └── README.md
│   │
│   └── math-verifier/
│       ├── pyproject.toml
│       ├── uv.lock
│       ├── Dockerfile
│       ├── app/
│       ├── tests/
│       ├── benchmarks/
│       └── README.md
│
├── packages/
│   ├── contracts/
│   ├── schemas/
│   ├── curriculum/
│   └── test-fixtures/
│
├── prompts/
│   ├── problem-parser/
│   ├── solver-primary/
│   ├── solver-secondary/
│   ├── arbiter/
│   ├── mistake-classifier/
│   ├── tutor/
│   ├── study-planner/
│   └── README.md
│
├── evaluations/
│   ├── golden-datasets/
│   ├── rubrics/
│   ├── runners/
│   ├── baselines/
│   ├── reports/
│   └── README.md
│
├── infra/
│   ├── local/
│   ├── terraform/
│   ├── docker/
│   ├── monitoring/
│   ├── database/
│   ├── security/
│   └── README.md
│
├── docs/
│   ├── product/
│   ├── domain/
│   ├── architecture/
│   ├── ios/
│   ├── backend/
│   ├── data/
│   ├── ai/
│   ├── learning/
│   ├── security/
│   ├── operations/
│   ├── quality/
│   ├── roadmap/
│   ├── integrations/
│   ├── adr/
│   └── sprints/
│
├── scripts/
│   ├── bootstrap/
│   ├── database/
│   ├── release/
│   ├── evaluation/
│   ├── quality/
│   └── maintenance/
│
├── tools/
│   ├── dev-cli/
│   ├── fixture-generator/
│   └── dataset-curator/
│
├── .editorconfig
├── .gitattributes
├── .gitignore
├── .env.example
├── docker-compose.yml
├── Makefile
├── LICENSE
├── SECURITY.md
├── CONTRIBUTING.md
└── README.md
```

---

# 3. Root-level file contracts

## `README.md`
Must contain only stable onboarding information: product summary, repository map, local bootstrap, canonical docs links, and common commands. It must not become a design dump.

## `CONTRIBUTING.md`
Defines branch naming, commit policy, required checks, review expectations, documentation update requirements, migration rules, and AI-agent contribution rules.

## `SECURITY.md`
Defines responsible disclosure, supported versions, vulnerability reporting process, secret-handling rules, and prohibited security-testing behavior against production.

## `.env.example`
Contains variable names and safe placeholders only. Real credentials are forbidden.

## `Makefile`
Provides stable human/agent commands such as:

```text
make bootstrap
make dev
make test
make test-ios
make test-api
make test-verifier
make eval-ai
make lint
make db-migrate
make db-reset-local
make docs-check
```

---

# 4. iOS application hierarchy

```text
apps/ios/VerifiedLearning/
│
├── App/
│   ├── VerifiedLearningApp.swift
│   ├── AppDelegate.swift
│   ├── AppEnvironment.swift
│   ├── AppDependencies.swift
│   ├── AppConfiguration.swift
│   ├── AppLifecycleCoordinator.swift
│   ├── AppRouter.swift
│   ├── DeepLinkRouter.swift
│   ├── RootView.swift
│   └── LaunchState.swift
│
├── Core/
│   ├── DesignSystem/
│   │   ├── Tokens/
│   │   │   ├── AppColor.swift
│   │   │   ├── AppTypography.swift
│   │   │   ├── AppSpacing.swift
│   │   │   ├── AppRadius.swift
│   │   │   ├── AppShadow.swift
│   │   │   ├── AppMotion.swift
│   │   │   └── AppHaptics.swift
│   │   ├── Components/
│   │   │   ├── Buttons/
│   │   │   ├── Inputs/
│   │   │   ├── Cards/
│   │   │   ├── Feedback/
│   │   │   ├── Navigation/
│   │   │   ├── Progress/
│   │   │   ├── Math/
│   │   │   └── Accessibility/
│   │   ├── Layout/
│   │   ├── Icons/
│   │   └── PreviewCatalog/
│   │
│   ├── Networking/
│   │   ├── APIClient.swift
│   │   ├── Endpoint.swift
│   │   ├── HTTPMethod.swift
│   │   ├── HTTPHeaders.swift
│   │   ├── HTTPRequest.swift
│   │   ├── HTTPResponse.swift
│   │   ├── NetworkError.swift
│   │   ├── APIProblemDetails.swift
│   │   ├── RequestEncoder.swift
│   │   ├── ResponseDecoder.swift
│   │   ├── AuthInterceptor.swift
│   │   ├── RetryPolicy.swift
│   │   ├── IdempotencyKeyProvider.swift
│   │   ├── NetworkReachability.swift
│   │   ├── ServerClock.swift
│   │   └── UploadClient.swift
│   │
│   ├── Persistence/
│   │   ├── PersistenceController.swift
│   │   ├── ModelContainerFactory.swift
│   │   ├── PersistenceMigrationPlan.swift
│   │   ├── CachePolicy.swift
│   │   ├── CacheInvalidation.swift
│   │   ├── SyncState.swift
│   │   └── Models/
│   │
│   ├── Security/
│   │   ├── KeychainStore.swift
│   │   ├── SecureTokenStore.swift
│   │   ├── DeviceIntegritySignals.swift
│   │   ├── SensitiveDataRedactor.swift
│   │   └── PrivacyScreenController.swift
│   │
│   ├── Observability/
│   │   ├── AppLogger.swift
│   │   ├── AnalyticsClient.swift
│   │   ├── AnalyticsEvent.swift
│   │   ├── CrashReporting.swift
│   │   ├── PerformanceTracing.swift
│   │   ├── CorrelationContext.swift
│   │   └── PrivacySafeMetadata.swift
│   │
│   ├── Localization/
│   │   ├── LocalizationKey.swift
│   │   ├── LocalePolicy.swift
│   │   ├── MathLocaleFormatter.swift
│   │   └── AccessibilityCopy.swift
│   │
│   ├── MathRendering/
│   │   ├── MathExpressionView.swift
│   │   ├── MathAccessibilityFormatter.swift
│   │   ├── MathTheme.swift
│   │   └── MathRenderError.swift
│   │
│   ├── FeatureFlags/
│   │   ├── FeatureFlag.swift
│   │   ├── FeatureFlagClient.swift
│   │   └── ExperimentAssignment.swift
│   │
│   └── Foundation/
│       ├── Clock.swift
│       ├── UUIDProvider.swift
│       ├── AsyncState.swift
│       ├── Loadable.swift
│       └── AppError.swift
│
├── SharedDomain/
│   ├── Identity/
│   ├── Curriculum/
│   ├── Problem/
│   ├── Solution/
│   ├── Verification/
│   ├── Learning/
│   ├── Exam/
│   └── Billing/
│
├── Features/
│   ├── Authentication/
│   ├── Onboarding/
│   ├── Home/
│   ├── ProblemCapture/
│   ├── ProblemReview/
│   ├── SolveProgress/
│   ├── Solution/
│   ├── Tutor/
│   ├── Attempts/
│   ├── MistakeBook/
│   ├── Mastery/
│   ├── StudyPlan/
│   ├── Practice/
│   ├── Exams/
│   ├── Reports/
│   ├── Library/
│   ├── Subscription/
│   ├── Notifications/
│   ├── Profile/
│   └── Settings/
│
└── Resources/
    ├── Assets.xcassets/
    ├── Localizable.xcstrings
    ├── PrivacyInfo.xcprivacy
    ├── LaunchScreen.storyboard
    └── SeedData/
```

---

# 5. Canonical iOS feature template

Every substantial feature follows this shape unless a documented exception is accepted:

```text
Features/<FeatureName>/
│
├── Domain/
│   ├── Model/
│   ├── Port/
│   ├── UseCase/
│   ├── Policy/
│   └── Error/
│
├── Data/
│   ├── DTO/
│   ├── Mapper/
│   ├── Remote/
│   ├── Local/
│   └── Repository/
│
├── Presentation/
│   ├── <Feature>View.swift
│   ├── <Feature>ViewModel.swift
│   ├── <Feature>Route.swift
│   ├── State/
│   ├── Components/
│   └── Preview/
│
└── Support/
    ├── Analytics/
    └── Accessibility/
```

Rules:

- Views never call `URLSession` directly.
- Views never query SwiftData directly for domain workflows.
- DTOs never leak into presentation.
- Repository protocols live in Domain; implementations live in Data.
- Business decisions live in use cases/policies, not SwiftUI body expressions.
- Analytics events are emitted through typed event abstractions.
- UI copy must be localizable.
- Feature-specific reusable UI stays in the feature, not global DesignSystem.

---

# 6. Detailed iOS feature examples

## `Features/ProblemCapture`

```text
ProblemCapture/
├── Domain/
│   ├── Model/
│   │   ├── CaptureSource.swift
│   │   ├── CapturedAsset.swift
│   │   ├── CaptureQuality.swift
│   │   └── UploadReservation.swift
│   ├── Port/
│   │   ├── ProblemCaptureRepository.swift
│   │   └── CaptureQualityEvaluating.swift
│   ├── UseCase/
│   │   ├── PrepareCapturedImageUseCase.swift
│   │   ├── ReserveProblemUploadUseCase.swift
│   │   └── SubmitCapturedProblemUseCase.swift
│   └── Error/
│       └── ProblemCaptureError.swift
├── Data/
│   ├── DTO/
│   │   ├── UploadReservationResponseDTO.swift
│   │   └── CreateProblemRequestDTO.swift
│   ├── Mapper/
│   │   └── ProblemCaptureMapper.swift
│   ├── Remote/
│   │   ├── ProblemCaptureAPI.swift
│   │   └── PresignedUploadClient.swift
│   └── Repository/
│       └── DefaultProblemCaptureRepository.swift
└── Presentation/
    ├── ProblemCaptureView.swift
    ├── ProblemCaptureViewModel.swift
    ├── CaptureReviewView.swift
    ├── CropAdjustmentView.swift
    └── Components/
        ├── CameraPermissionView.swift
        ├── CaptureShutterButton.swift
        ├── CaptureGuideOverlay.swift
        └── CaptureQualityBanner.swift
```

## `Features/Solution`

```text
Solution/
├── Domain/
│   ├── Model/
│   │   ├── SolutionPresentation.swift
│   │   ├── SolutionStepPresentation.swift
│   │   └── VerificationPresentation.swift
│   ├── Port/
│   │   └── SolutionRepository.swift
│   └── UseCase/
│       ├── LoadSolutionUseCase.swift
│       └── ReportSolutionIssueUseCase.swift
├── Data/
│   ├── DTO/
│   ├── Mapper/
│   ├── Remote/
│   └── Repository/
└── Presentation/
    ├── SolutionView.swift
    ├── SolutionViewModel.swift
    └── Components/
        ├── FinalAnswerCard.swift
        ├── VerificationStatusBadge.swift
        ├── VerificationEvidenceSheet.swift
        ├── SolutionStepCard.swift
        ├── WhyExplanationSheet.swift
        ├── UncertaintyCallout.swift
        └── ReportProblemButton.swift
```

---

# 7. iOS test hierarchy

```text
apps/ios/VerifiedLearningTests/
├── Core/
│   ├── Networking/
│   ├── Persistence/
│   ├── Security/
│   └── FeatureFlags/
├── Features/
│   ├── Authentication/
│   ├── ProblemCapture/
│   ├── Solution/
│   ├── Tutor/
│   ├── MistakeBook/
│   ├── Mastery/
│   ├── StudyPlan/
│   └── Subscription/
├── Contract/
│   └── APIFixtureCompatibilityTests.swift
├── Snapshot/
├── Accessibility/
├── Performance/
└── TestSupport/
    ├── Builders/
    ├── Fixtures/
    ├── Mocks/
    ├── Spies/
    └── TestClock.swift

apps/ios/VerifiedLearningUITests/
├── AuthenticationFlowUITests.swift
├── ProblemSolveFlowUITests.swift
├── TutorFlowUITests.swift
├── PurchaseFlowUITests.swift
├── RestorePurchaseUITests.swift
├── AccountDeletionUITests.swift
├── AccessibilitySmokeUITests.swift
└── Support/
```

---

# 8. Spring Boot API hierarchy

```text
services/api/src/main/java/com/verifiedlearning/
│
├── VerifiedLearningApplication.java
│
├── identity/
├── profile/
├── curriculum/
├── problem/
├── solving/
├── verification/
├── tutoring/
├── attempt/
├── mistake/
├── mastery/
├── studyplan/
├── exam/
├── billing/
├── notification/
├── analytics/
├── admin/
├── ai/
└── sharedkernel/
```

Every domain module is a Spring Modulith application module and uses the following internal topology:

```text
<module>/
├── api/
│   ├── rest/
│   ├── dto/
│   ├── mapper/
│   └── validation/
├── application/
│   ├── command/
│   ├── query/
│   ├── service/
│   ├── handler/
│   └── policy/
├── domain/
│   ├── model/
│   ├── value/
│   ├── event/
│   ├── exception/
│   └── port/
├── infrastructure/
│   ├── persistence/
│   │   ├── entity/
│   │   ├── repository/
│   │   └── mapper/
│   ├── client/
│   ├── messaging/
│   └── configuration/
└── package-info.java
```

---

# 9. Backend module ownership

## `identity`
Owns users, external identities, sessions, refresh-token families, device/session revocation, and authentication events.

## `profile`
Owns learner preferences, education level, locale, daily study target, and onboarding completion.

## `curriculum`
Owns canonical subjects, topics, skills, prerequisites, curricula, curriculum versions, and exam mappings.

## `problem`
Owns uploaded problem sessions, problem assets, parsing lifecycle, user corrections, canonical problem revisions, and problem status.

## `solving`
Owns solver runs, solution candidates, solution assembly, explanation generation, and solve orchestration decisions excluding verification verdicts.

## `verification`
Owns verification plans, signals, runs, evidence, overall verification policy, and the exclusive authority to assign verification status.

## `tutoring`
Owns tutor sessions, tutor turns, hint policy, Socratic behavior state, and tutoring safety constraints.

## `attempt`
Owns student attempts, attempt steps, submitted answers, evaluation state, and attempt scoring evidence.

## `mistake`
Owns detected mistakes, mistake taxonomy, mistake confidence, grouped mistake history, and correction lifecycle.

## `mastery`
Owns per-user/per-skill mastery, mastery history, mastery confidence, update policy, and knowledge-graph projections.

## `studyplan`
Owns study plans, plan items, recommendation decisions, spaced-repetition scheduling, practice sessions, and rebalancing.

## `exam`
Owns exam definitions, candidate exam profile, blueprints, mock-exam sessions, scoring, and readiness projections.

## `billing`
Owns App Store transaction ingestion, subscription state, entitlement grants, quotas, and billing audit.

## `notification`
Owns notification preferences, notification intents, scheduling requests, deduplication, and delivery tracking.

## `analytics`
Owns server-side product event normalization and privacy-safe operational product metrics. It does not own raw domain truth.

## `admin`
Owns privileged support workflows and read models. It must never bypass domain rules by direct table mutation.

## `ai`
Owns provider-neutral model capabilities, model routing, prompt registry access, AI usage ledger, retry/fallback policy, and provider adapters.

---

# 10. Example complete backend problem module

```text
problem/
├── api/
│   ├── rest/
│   │   ├── ProblemCommandController.java
│   │   ├── ProblemQueryController.java
│   │   └── ProblemUploadController.java
│   ├── dto/
│   │   ├── CreateProblemRequest.java
│   │   ├── CreateProblemResponse.java
│   │   ├── ProblemResponse.java
│   │   ├── ProblemStatusResponse.java
│   │   ├── CorrectProblemParseRequest.java
│   │   └── UploadReservationResponse.java
│   ├── mapper/
│   │   └── ProblemApiMapper.java
│   └── validation/
│       └── ProblemRequestValidator.java
├── application/
│   ├── command/
│   │   ├── CreateProblemCommand.java
│   │   ├── AttachProblemAssetCommand.java
│   │   ├── StartProblemParsingCommand.java
│   │   └── CorrectProblemParseCommand.java
│   ├── query/
│   │   ├── GetProblemQuery.java
│   │   └── GetProblemHistoryQuery.java
│   ├── service/
│   │   ├── ProblemCommandService.java
│   │   ├── ProblemQueryService.java
│   │   └── ProblemParsingCoordinator.java
│   ├── handler/
│   │   ├── AssetUploadedHandler.java
│   │   └── ParseCompletedHandler.java
│   └── policy/
│       ├── ProblemRetentionPolicy.java
│       └── ParseRevisionPolicy.java
├── domain/
│   ├── model/
│   │   ├── Problem.java
│   │   ├── ProblemAsset.java
│   │   ├── ProblemParse.java
│   │   └── ProblemParseRevision.java
│   ├── value/
│   │   ├── ProblemId.java
│   │   ├── ProblemStatus.java
│   │   ├── ProblemSourceType.java
│   │   └── CanonicalProblemRepresentation.java
│   ├── event/
│   │   ├── ProblemCreated.java
│   │   ├── ProblemAssetAttached.java
│   │   ├── ProblemParseCompleted.java
│   │   └── ProblemParseCorrected.java
│   ├── exception/
│   │   ├── ProblemNotFoundException.java
│   │   └── InvalidProblemStateTransitionException.java
│   └── port/
│       ├── ProblemRepository.java
│       ├── ProblemAssetStore.java
│       └── ProblemParserGateway.java
├── infrastructure/
│   ├── persistence/
│   │   ├── entity/
│   │   │   ├── ProblemJpaEntity.java
│   │   │   ├── ProblemAssetJpaEntity.java
│   │   │   └── ProblemParseRevisionJpaEntity.java
│   │   ├── repository/
│   │   │   ├── SpringDataProblemRepository.java
│   │   │   └── ProblemRepositoryAdapter.java
│   │   └── mapper/
│   │       └── ProblemPersistenceMapper.java
│   ├── client/
│   │   └── AiProblemParserAdapter.java
│   ├── messaging/
│   │   └── ProblemEventPublisher.java
│   └── configuration/
│       └── ProblemModuleConfiguration.java
└── package-info.java
```

---

# 11. Backend configuration and resources

```text
services/api/src/main/resources/
├── application.yml
├── application-local.yml
├── application-test.yml
├── application-staging.yml
├── application-production.yml
├── logback-spring.xml
├── db/
│   ├── migration/
│   │   ├── V001__identity.sql
│   │   ├── V002__profiles.sql
│   │   ├── V003__curriculum.sql
│   │   ├── V004__problem_ingestion.sql
│   │   ├── V005__solving.sql
│   │   ├── V006__verification.sql
│   │   ├── V007__attempt_and_mistake.sql
│   │   ├── V008__mastery.sql
│   │   ├── V009__study_planning.sql
│   │   ├── V010__exam.sql
│   │   └── V011__billing.sql
│   └── repeatable/
│       ├── R__reference_curriculum_seed.sql
│       └── R__reporting_views.sql
└── schemas/
    └── problem-details.json
```

Migration names above are illustrative sequencing; once committed, a migration file is immutable except under explicit local-only reset procedures.

---

# 12. Backend test hierarchy

```text
services/api/src/test/java/com/verifiedlearning/
├── architecture/
│   ├── ModulithVerificationTest.java
│   ├── PackageDependencyRulesTest.java
│   └── ForbiddenDependencyTest.java
├── identity/
├── problem/
├── solving/
├── verification/
├── attempt/
├── mistake/
├── mastery/
├── studyplan/
├── exam/
├── billing/
├── contract/
│   ├── ApiContractTest.java
│   ├── MathVerifierContractTest.java
│   └── AppStoreWebhookContractTest.java
├── integration/
│   ├── PostgreSqlIntegrationTest.java
│   ├── RedisIntegrationTest.java
│   └── ObjectStorageIntegrationTest.java
├── security/
├── performance/
└── support/
    ├── TestDataBuilder.java
    ├── TestClock.java
    ├── PostgresContainerSupport.java
    └── FixtureLoader.java
```

H2 is not an accepted substitute for PostgreSQL integration behavior.

---

# 13. Internal Python math verifier hierarchy

```text
services/math-verifier/
├── pyproject.toml
├── uv.lock
├── Dockerfile
├── app/
│   ├── main.py
│   ├── config.py
│   ├── api/
│   │   ├── health.py
│   │   ├── verify.py
│   │   ├── schemas.py
│   │   └── errors.py
│   ├── domain/
│   │   ├── expression.py
│   │   ├── canonical_math.py
│   │   ├── verification_request.py
│   │   ├── verification_result.py
│   │   ├── verification_signal.py
│   │   └── verification_method.py
│   ├── parsing/
│   │   ├── expression_parser.py
│   │   ├── symbol_policy.py
│   │   └── parse_safety.py
│   ├── verifiers/
│   │   ├── arithmetic.py
│   │   ├── algebra.py
│   │   ├── equations.py
│   │   ├── functions.py
│   │   ├── limits.py
│   │   ├── derivatives.py
│   │   ├── integrals.py
│   │   ├── matrices.py
│   │   ├── numeric_equivalence.py
│   │   └── composite.py
│   ├── policies/
│   │   ├── verification_budget.py
│   │   ├── random_point_policy.py
│   │   └── timeout_policy.py
│   ├── security/
│   │   ├── internal_auth.py
│   │   ├── input_limits.py
│   │   └── sandbox_policy.py
│   ├── observability/
│   │   ├── logging.py
│   │   ├── metrics.py
│   │   └── tracing.py
│   └── infrastructure/
│       └── sympy_adapter.py
├── tests/
│   ├── unit/
│   ├── property/
│   ├── contract/
│   ├── regression/
│   └── security/
└── benchmarks/
    ├── datasets/
    ├── runner.py
    └── reports/
```

The verifier exposes only internal authenticated endpoints. It is never internet-facing and never receives end-user authorization tokens as trust credentials.

---

# 14. Shared contracts hierarchy

```text
packages/contracts/
├── openapi/
│   ├── public-api.yaml
│   └── internal-math-verifier.yaml
├── json-schema/
│   ├── canonical-problem.schema.json
│   ├── solver-result.schema.json
│   ├── verification-result.schema.json
│   ├── mistake-classification.schema.json
│   └── tutor-turn.schema.json
├── examples/
└── generated/
    ├── swift/
    └── java/
```

Generated code must include a header stating the generator and source schema. Hand edits inside `generated/` are prohibited.

---

# 15. Curriculum package

```text
packages/curriculum/
├── ontology/
│   ├── subjects.yaml
│   ├── topics.yaml
│   ├── skills.yaml
│   └── prerequisites.yaml
├── mappings/
│   ├── internal-math-v1.yaml
│   ├── sat-math.yaml
│   └── future/
├── validators/
├── fixtures/
└── README.md
```

Stable skill identifiers are data contracts. Renaming display text must never silently change skill identity.

---

# 16. Prompt repository

```text
prompts/
├── README.md
├── problem-parser/
│   ├── v001/
│   │   ├── system.md
│   │   ├── developer.md
│   │   ├── output.schema.json
│   │   ├── examples.jsonl
│   │   └── metadata.yaml
│   └── CHANGELOG.md
├── solver-primary/
├── solver-secondary/
├── arbiter/
├── mistake-classifier/
├── tutor/
└── study-planner/
```

`metadata.yaml` must declare capability, owner, release status, compatible schema version, evaluation baseline, and safety notes.

No production prompt is embedded as an unversioned multi-line string inside a controller or service.

---

# 17. AI evaluation hierarchy

```text
evaluations/
├── golden-datasets/
│   ├── parsing/
│   ├── algebra/
│   ├── calculus/
│   ├── verification/
│   ├── tutoring/
│   └── mistake-classification/
├── rubrics/
│   ├── answer-correctness.yaml
│   ├── explanation-quality.yaml
│   ├── uncertainty-honesty.yaml
│   └── pedagogical-quality.yaml
├── runners/
│   ├── run_parser_eval.py
│   ├── run_solver_eval.py
│   ├── run_verification_eval.py
│   └── compare_release.py
├── baselines/
│   └── production.json
└── reports/
    └── .gitkeep
```

Raw student production content is never copied into a committed evaluation dataset without an approved privacy-safe curation process.

---

# 18. Infrastructure hierarchy

```text
infra/
├── local/
│   ├── docker-compose.override.yml
│   ├── postgres/
│   ├── redis/
│   └── minio/
├── docker/
│   ├── api/
│   └── math-verifier/
├── terraform/
│   ├── modules/
│   │   ├── network/
│   │   ├── api-service/
│   │   ├── verifier-service/
│   │   ├── postgres/
│   │   ├── redis/
│   │   ├── object-storage/
│   │   ├── secrets/
│   │   ├── monitoring/
│   │   └── dns/
│   └── environments/
│       ├── staging/
│       └── production/
├── database/
│   ├── backup-policy.md
│   ├── restore-test.md
│   └── capacity.md
├── monitoring/
│   ├── dashboards/
│   ├── alerts/
│   └── slo/
└── security/
    ├── iam/
    ├── network-policies/
    └── secret-rotation/
```

Production infrastructure changes are code-reviewed and reproducible. Manual console changes must be treated as emergency exceptions and reconciled back into infrastructure-as-code.

---

# 19. Scripts hierarchy

```text
scripts/
├── bootstrap/
│   ├── bootstrap-macos.sh
│   ├── verify-toolchain.sh
│   └── seed-local.sh
├── database/
│   ├── migrate.sh
│   ├── reset-local.sh
│   ├── dump-schema.sh
│   └── verify-migrations.sh
├── release/
│   ├── tag-release.sh
│   ├── generate-release-notes.sh
│   └── verify-release-gates.sh
├── evaluation/
│   ├── run-golden-suite.sh
│   └── update-approved-baseline.sh
├── quality/
│   ├── check-doc-links.py
│   ├── check-forbidden-files.py
│   └── check-generated-drift.py
└── maintenance/
    ├── prune-local-assets.sh
    └── rotate-test-fixtures.py
```

Scripts must be idempotent where reasonably possible and fail loudly on partial execution.

---

# 20. Documentation hierarchy

```text
docs/
├── 00_MASTER_INDEX.md
├── product/
├── domain/
├── architecture/
├── ios/
├── backend/
├── data/
├── ai/
├── learning/
├── security/
├── operations/
├── quality/
├── integrations/
├── adr/
└── sprints/
    ├── 00_SPRINT_MASTER_PLAN.md
    ├── phase01_product_semantics_architecture/
    │   ├── PHASE_01_PRODUCT_SEMANTICS_AND_ARCHITECTURE.md
    │   ├── SPRINT_1.1_....md
    │   └── ...
    └── ...
```

Sprint documentation is an execution plan, not higher-precedence domain truth. If a sprint file conflicts with a domain invariant or accepted ADR, the sprint must be corrected.

---

# 21. File naming conventions

## Swift
- Types: `PascalCase.swift`.
- Protocols describe capability, e.g. `ProblemRepository.swift`, not `IProblemRepository.swift`.
- DTO names end in `DTO` only at transport boundaries.
- Use cases use verb-first names: `SubmitProblemUseCase.swift`.
- View models: `<Feature>ViewModel.swift`.

## Java
- Aggregate roots: singular nouns.
- Commands: `<Verb><Noun>Command`.
- Queries: `Get...Query`, `Find...Query`, `List...Query`.
- Domain events use past tense: `ProblemParsed`, `AttemptEvaluated`.
- Adapters communicate technology: `S3ProblemAssetStore`, `OpenAiModelAdapter`.
- Interfaces in ports communicate capability: `ProblemAssetStore`, `AiModelGateway`.

## Python
- Modules/functions: `snake_case`.
- Domain classes: `PascalCase`.
- Test file mirrors target: `test_derivatives.py`.

## SQL
- Tables and columns: `snake_case`.
- PK: `id` unless domain-specific reason is documented.
- FK: `<entity>_id`.
- Timestamp columns use explicit UTC semantics.

## Markdown
- Canonical semantic documents use numbered uppercase descriptive names.
- ADRs use `ADR-NNN_DECISION_TITLE.md`.
- Sprint files use `SPRINT_<phase>.<sequence>_<DELIVERABLE>.md`.

---

# 22. Forbidden hierarchy patterns

The following are architecture smells and require explicit justification:

```text
src/main/java/.../controller/*all controllers*
src/main/java/.../service/*all services*
src/main/java/.../repository/*all repositories*
Core/Utils/
Shared/Helpers/
Common/Misc/
GlobalManager.swift
GodService.java
AiService.java   # ambiguous ownership
DatabaseService.java
NetworkManager.swift
```

A new generic bucket should be considered a design failure until proven otherwise.

---

# 23. Generated and ignored directories

Never commit:

```text
DerivedData/
.build/
build/
target/
.pytest_cache/
.venv/
coverage/
reports/runtime/
.env
*.xcuserstate
```

Generated API clients may be committed only when the project deliberately uses checked-in generated clients and CI verifies drift.

---

# 24. Ownership and dependency direction

```text
Presentation
    ↓
Application / Use Cases
    ↓
Domain
    ↑
Infrastructure Adapters
```

Domain code must not depend on:

- SwiftUI,
- URLSession,
- JPA,
- Spring MVC,
- OpenAI SDK,
- Gemini SDK,
- Redis SDK,
- S3 SDK.

Those technologies are adapters around domain/application contracts.

---

# 25. Documentation-to-code traceability

Every major capability should be traceable:

```text
Product requirement
   ↓
Domain rule / invariant
   ↓
Architecture/module owner
   ↓
API + data contract
   ↓
Implementation files
   ↓
Tests
   ↓
Analytics / observability
   ↓
Sprint acceptance gate
```

For complex work, PR descriptions should reference the relevant canonical document and sprint file.

---

# 26. Repository growth rule

Before adding a new top-level directory, an ADR or explicit architecture-document update is required. Top-level directory proliferation is intentionally constrained.

Before adding a new backend module, answer:

1. Does it own a distinct business capability?
2. Does it own durable data or invariant decisions?
3. Can its public contract be described without implementation details?
4. Is the existing module boundary actually insufficient?

Before adding a new iOS global Core component, answer:

1. Is it used by at least two genuinely independent features?
2. Is it infrastructure/design-system behavior rather than feature policy?
3. Will making it global create unwanted coupling?

---

# 27. Final hierarchy invariant

> A contributor or coding agent must be able to infer **what a file owns, which layer it belongs to, and which dependencies it may legally use** from its path and name.

If that is not true, the hierarchy or name is not production-grade enough.

<!-- HYBRID_AI_STRATEGY_V3:START -->
## Hybrid AI / proprietary-model repository extensions

The following paths are part of the **canonical future hierarchy** but are created only when Phase 13 reaches the corresponding sprint. Do not materialize them during V1 merely to reserve folders.

```text
verified-ai-learning-platform/
├── ml/                                      # offline model-development workspace (conditional)
│   ├── README.md
│   ├── datasets/
│   │   ├── definitions/
│   │   ├── manifests/
│   │   └── quality_reports/
│   ├── experiments/
│   │   ├── skill_classifier/
│   │   ├── mistake_classifier/
│   │   ├── difficulty_predictor/
│   │   ├── mastery_predictor/
│   │   ├── recommendation_ranker/
│   │   └── math_solver_candidate/
│   ├── training/
│   │   ├── common/
│   │   ├── configs/
│   │   ├── pipelines/
│   │   └── evaluation/
│   ├── model_cards/
│   └── tests/
│
├── services/
│   └── model-inference/                     # optional; only after self-hosting gate
│       ├── app/
│       │   ├── api/
│       │   ├── inference/
│       │   ├── runtime/
│       │   ├── telemetry/
│       │   └── security/
│       ├── tests/
│       ├── Dockerfile
│       └── pyproject.toml
│
├── packages/
│   ├── ai-evaluation-contracts/
│   └── dataset-schemas/
│
└── infra/
    └── model-serving/                       # conditional GPU/runtime infrastructure
```

### Hard placement rules

- Training code does not live inside `services/api`.
- Experimental notebooks/scripts do not become production inference code by copy/paste.
- Production API communicates with a proprietary inference runtime only through the same internal AI capability adapter contract used for external providers.
- Dataset manifests/model cards are version-controlled metadata; large weights/datasets live in approved artifact/object stores, not Git.
- Provider/API and proprietary-model implementations remain interchangeable at the route layer.
<!-- HYBRID_AI_STRATEGY_V3:END -->


---

# 28. V4 exhaustive hierarchy expansion
This section supersedes any earlier abbreviated tree when file placement requires a more specific answer. It expands the canonical hierarchy down to feature/module-level files, tests, migrations, operational assets, and future conditional ML paths. The names below are architectural contracts and representative canonical filenames; implementation may add files when the same ownership and dependency rules are preserved.

## 28.1 Top-level ownership table

| Path | Owner | Runtime? | May contain product logic? | Notes |
|---|---|---:|---:|---|
| `apps/ios` | iOS | Yes | Yes | Native Swift/SwiftUI client only. |
| `services/api` | Backend | Yes | Yes | Spring Boot modular monolith; source of truth for product policies. |
| `services/math-verifier` | Verification | Yes, internal | Deterministic math only | Never exposed directly to mobile clients. |
| `packages/contracts` | Platform | Build-time | No business orchestration | Versioned API/event schemas. |
| `packages/curriculum` | Learning domain | Build/runtime data | Canonical ontology only | Stable IDs and migrations required. |
| `prompts` | AI platform | Runtime source artifact | Prompt behavior only | Versioned; evaluated before release. |
| `evaluations` | AI quality | CI/offline | No runtime product logic | Golden/protected datasets and reports. |
| `ml` | Conditional Phase 13 | Offline/runtime later | Specialized ML only | Must not exist before readiness gates. |
| `infra` | Platform/operations | Deploy-time | No | IaC, monitoring, database operational configuration. |
| `docs` | All | No | Specifications | Canonical implementation context. |
| `scripts` | Platform | Dev/ops | No durable domain logic | Automation only. |
| `tools` | Internal tooling | Dev/offline | No production domain authority | Curators, fixture generators, developer CLI. |

# 29. Exhaustive iOS production file hierarchy
The iOS application is feature-first. `Core` contains platform capabilities that are business-agnostic; `SharedDomain` contains a deliberately tiny set of cross-feature immutable concepts; every business-facing capability belongs to `Features/<FeatureName>`.

```text
apps/ios/
├── VerifiedLearning.xcodeproj/
├── VerifiedLearning.xcworkspace/
├── Config/
│   ├── Base.xcconfig
│   ├── Debug.xcconfig
│   ├── Staging.xcconfig
│   ├── Release.xcconfig
│   ├── Secrets.xcconfig.example
│   └── Generated/                 # ignored; build-time generated config
├── VerifiedLearning/
│   ├── App/
│   │   ├── VerifiedLearningApp.swift
│   │   ├── AppEnvironment.swift
│   │   ├── AppDependencies.swift
│   │   ├── AppRouter.swift
│   │   ├── DeepLinkRouter.swift
│   │   ├── AppLifecycleHandler.swift
│   │   ├── AppLaunchState.swift
│   │   └── RootView.swift
│   ├── Core/
│   │   ├── Networking/
│   │   ├── Persistence/
│   │   ├── Security/
│   │   ├── Observability/
│   │   ├── DesignSystem/
│   │   ├── FeatureFlags/
│   │   ├── Localization/
│   │   ├── MathRendering/
│   │   ├── Media/
│   │   ├── Uploads/
│   │   ├── Notifications/
│   │   ├── StoreKit/
│   │   ├── Accessibility/
│   │   └── Utilities/             # narrowly scoped, reviewed; never catch-all
│   ├── SharedDomain/
│   ├── Features/
│   └── Resources/
├── VerifiedLearningTests/
├── VerifiedLearningUITests/
├── Packages/
├── Scripts/
└── README.md
```

## 29.1 Core platform subtrees

### `Core/Networking`

```text
Core/Networking/
├── APIClient.swift
├── APIEndpoint.swift
├── HTTPMethod.swift
├── HTTPRequest.swift
├── HTTPResponse.swift
├── APIError.swift
├── ProblemDetails.swift
├── RequestEncoder.swift
├── ResponseDecoder.swift
├── AuthenticationInterceptor.swift
├── RetryPolicy.swift
├── IdempotencyKeyProvider.swift
├── NetworkMonitor.swift
└── ServerSentEventClient.swift
```

### `Core/Persistence`

```text
Core/Persistence/
├── PersistenceController.swift
├── SwiftDataContainer.swift
├── PersistenceMigrationPlan.swift
├── LocalCachePolicy.swift
├── LocalCacheKey.swift
├── OfflineOperation.swift
├── OfflineOperationQueue.swift
└── PersistenceError.swift
```

### `Core/Security`

```text
Core/Security/
├── KeychainStore.swift
├── SecureTokenStore.swift
├── DeviceSecurityEvaluator.swift
├── SensitiveDataRedactor.swift
├── AppAttestationProvider.swift
└── SecurityEventLogger.swift
```

### `Core/Observability`

```text
Core/Observability/
├── AppLogger.swift
├── AnalyticsClient.swift
├── AnalyticsEvent.swift
├── CrashReporter.swift
├── PerformanceTracer.swift
├── MetricRecorder.swift
└── CorrelationContext.swift
```

### `Core/DesignSystem`

```text
Core/DesignSystem/
├── ColorTokens.swift
├── TypographyTokens.swift
├── SpacingTokens.swift
├── RadiusTokens.swift
├── ShadowTokens.swift
├── MotionTokens.swift
├── HapticTokens.swift
├── IconTokens.swift
├── Components/PrimaryButton.swift
├── Components/SecondaryButton.swift
├── Components/AsyncStateView.swift
├── Components/EmptyStateView.swift
├── Components/ErrorStateView.swift
├── Components/LoadingSkeleton.swift
├── Components/SheetHeader.swift
└── Components/MetricCard.swift
```

### `Core/FeatureFlags`

```text
Core/FeatureFlags/
├── FeatureFlag.swift
├── FeatureFlagClient.swift
├── FeatureFlagSnapshot.swift
└── FeatureFlagOverrideStore.swift
```

### `Core/Localization`

```text
Core/Localization/
├── LocalizedString.swift
├── LocaleManager.swift
├── MathLocaleFormatter.swift
└── Pluralization.swift
```

### `Core/MathRendering`

```text
Core/MathRendering/
├── MathExpressionView.swift
├── LaTeXRenderer.swift
├── MathAccessibilityLabelBuilder.swift
└── MathRenderingError.swift
```

### `Core/Media`

```text
Core/Media/
├── ImagePreprocessor.swift
├── DocumentScanner.swift
├── CameraAuthorization.swift
├── ImageCompressionPolicy.swift
└── ImageMetadataSanitizer.swift
```

### `Core/Uploads`

```text
Core/Uploads/
├── UploadClient.swift
├── UploadTask.swift
├── UploadProgress.swift
├── UploadRetryPolicy.swift
└── UploadChecksum.swift
```

### `Core/Notifications`

```text
Core/Notifications/
├── PushRegistrationService.swift
├── NotificationPermissionService.swift
└── NotificationDeepLinkResolver.swift
```

### `Core/StoreKit`

```text
Core/StoreKit/
├── StoreKitClient.swift
├── ProductCatalog.swift
├── TransactionObserver.swift
├── PurchaseResult.swift
└── StoreKitError.swift
```

### `Core/Accessibility`

```text
Core/Accessibility/
├── AccessibilityPreferences.swift
├── ReducedMotionPolicy.swift
└── DynamicTypeSupport.swift
```


## 29.2 Feature-by-feature canonical trees

### `Features/Authentication`

```text
Features/Authentication/
├── AuthenticationFeature.swift
├── AuthenticationRoute.swift
├── Domain/
│   ├── SignInWithAppleUseCase.swift
│   ├── RefreshSessionUseCase.swift
│   ├── SignOutUseCase.swift
│   ├── AuthenticationRepository.swift
│   ├── Session.swift
│   └── AuthenticationState.swift
├── Data/
│   ├── AuthenticationAPI.swift
│   └── DefaultAuthenticationRepository.swift
├── Presentation/
│   ├── AuthenticationView.swift
│   ├── AuthenticationViewModel.swift
│   ├── SignInWithAppleButton.swift
│   └── AuthenticationErrorView.swift
└── Tests/
    ├── Domain/
    ├── Data/
    ├── Presentation/
    └── Fixtures/
```
**Ownership rule:** `Authentication` owns its user-facing state and use cases. Cross-feature access occurs through explicit protocols, app navigation, or backend APIs; one feature must not import another feature’s presentation layer.

### `Features/Onboarding`

```text
Features/Onboarding/
├── OnboardingFeature.swift
├── OnboardingRoute.swift
├── Domain/
│   ├── LoadOnboardingStateUseCase.swift
│   ├── SaveLearningProfileUseCase.swift
│   ├── CreateExamGoalUseCase.swift
│   ├── OnboardingRepository.swift
│   ├── OnboardingDraft.swift
│   ├── EducationLevel.swift
│   └── StudyGoal.swift
├── Data/
│   ├── OnboardingAPI.swift
│   └── DefaultOnboardingRepository.swift
├── Presentation/
│   ├── OnboardingFlowView.swift
│   ├── OnboardingViewModel.swift
│   ├── EducationLevelStepView.swift
│   ├── GoalStepView.swift
│   ├── ExamDateStepView.swift
│   ├── DailyStudyTimeStepView.swift
│   └── OnboardingSummaryView.swift
└── Tests/
    ├── Domain/
    ├── Data/
    ├── Presentation/
    └── Fixtures/
```
**Ownership rule:** `Onboarding` owns its user-facing state and use cases. Cross-feature access occurs through explicit protocols, app navigation, or backend APIs; one feature must not import another feature’s presentation layer.

### `Features/Home`

```text
Features/Home/
├── HomeFeature.swift
├── HomeRoute.swift
├── Domain/
│   ├── LoadHomeDashboardUseCase.swift
│   ├── LoadTodayPlanUseCase.swift
│   ├── HomeRepository.swift
│   ├── HomeDashboard.swift
│   ├── HomeMetric.swift
│   └── TodayPlanSummary.swift
├── Data/
│   ├── HomeAPI.swift
│   └── DefaultHomeRepository.swift
├── Presentation/
│   ├── HomeView.swift
│   ├── HomeViewModel.swift
│   ├── TodayPlanCard.swift
│   ├── ReadinessCard.swift
│   ├── WeakSkillCard.swift
│   ├── StreakCard.swift
│   └── QuickActionGrid.swift
└── Tests/
    ├── Domain/
    ├── Data/
    ├── Presentation/
    └── Fixtures/
```
**Ownership rule:** `Home` owns its user-facing state and use cases. Cross-feature access occurs through explicit protocols, app navigation, or backend APIs; one feature must not import another feature’s presentation layer.

### `Features/ProblemCapture`

```text
Features/ProblemCapture/
├── ProblemCaptureFeature.swift
├── ProblemCaptureRoute.swift
├── Domain/
│   ├── CreateProblemSessionUseCase.swift
│   ├── RequestUploadURLUseCase.swift
│   ├── SubmitCapturedAssetUseCase.swift
│   ├── CaptureSource.swift
│   ├── CapturedAsset.swift
│   ├── CaptureQuality.swift
│   └── ProblemSessionDraft.swift
├── Data/
│   ├── ProblemCaptureRepository.swift
│   ├── ProblemCaptureAPI.swift
│   ├── PresignedUploadDTO.swift
│   ├── ProblemSubmissionDTO.swift
│   └── DefaultProblemCaptureRepository.swift
├── Presentation/
│   ├── ProblemCaptureView.swift
│   ├── ProblemCaptureViewModel.swift
│   ├── CameraScannerView.swift
│   ├── GalleryImportView.swift
│   ├── DocumentCropView.swift
│   ├── CaptureQualityOverlay.swift
│   └── MultiQuestionSelectionView.swift
└── Tests/
    ├── Domain/
    ├── Data/
    ├── Presentation/
    └── Fixtures/
```
**Ownership rule:** `ProblemCapture` owns its user-facing state and use cases. Cross-feature access occurs through explicit protocols, app navigation, or backend APIs; one feature must not import another feature’s presentation layer.

### `Features/ProblemSolve`

```text
Features/ProblemSolve/
├── ProblemSolveFeature.swift
├── ProblemSolveRoute.swift
├── Domain/
│   ├── RequestSolutionUseCase.swift
│   ├── ObserveSolveProgressUseCase.swift
│   ├── CancelSolveUseCase.swift
│   ├── SolveJob.swift
│   ├── SolveStage.swift
│   └── SolveProgress.swift
├── Data/
│   ├── ProblemSolveRepository.swift
│   ├── ProblemSolveAPI.swift
│   └── DefaultProblemSolveRepository.swift
├── Presentation/
│   ├── ProblemSolveProgressView.swift
│   ├── ProblemSolveViewModel.swift
│   ├── SolveStageRow.swift
│   └── SolveFailureRecoveryView.swift
└── Tests/
    ├── Domain/
    ├── Data/
    ├── Presentation/
    └── Fixtures/
```
**Ownership rule:** `ProblemSolve` owns its user-facing state and use cases. Cross-feature access occurs through explicit protocols, app navigation, or backend APIs; one feature must not import another feature’s presentation layer.

### `Features/Solution`

```text
Features/Solution/
├── SolutionFeature.swift
├── SolutionRoute.swift
├── Domain/
│   ├── LoadSolutionUseCase.swift
│   ├── LoadVerificationDetailsUseCase.swift
│   ├── RequestAlternateMethodUseCase.swift
│   ├── ReportSolutionUseCase.swift
│   ├── SolutionRepository.swift
│   ├── Solution.swift
│   ├── SolutionStep.swift
│   ├── VerificationSummary.swift
│   └── VerificationEvidence.swift
├── Data/
│   ├── SolutionAPI.swift
│   └── DefaultSolutionRepository.swift
├── Presentation/
│   ├── SolutionView.swift
│   ├── SolutionViewModel.swift
│   ├── FinalAnswerCard.swift
│   ├── SolutionStepView.swift
│   ├── ExplanationDepthPicker.swift
│   └── AlternateMethodCard.swift
└── Tests/
    ├── Domain/
    ├── Data/
    ├── Presentation/
    └── Fixtures/
```
**Ownership rule:** `Solution` owns its user-facing state and use cases. Cross-feature access occurs through explicit protocols, app navigation, or backend APIs; one feature must not import another feature’s presentation layer.

### `Features/Tutor`

```text
Features/Tutor/
├── TutorFeature.swift
├── TutorRoute.swift
├── Domain/
│   ├── StartTutorSessionUseCase.swift
│   ├── SendTutorTurnUseCase.swift
│   ├── RequestHintUseCase.swift
│   ├── EndTutorSessionUseCase.swift
│   ├── TutorRepository.swift
│   ├── TutorSession.swift
│   ├── TutorTurn.swift
│   ├── HintLevel.swift
│   └── TutorPedagogyMode.swift
├── Data/
│   ├── TutorAPI.swift
│   └── DefaultTutorRepository.swift
├── Presentation/
│   ├── TutorView.swift
│   ├── TutorViewModel.swift
│   ├── TutorMessageBubble.swift
│   ├── HintControl.swift
│   ├── StudentInputComposer.swift
│   └── TutorProgressHeader.swift
└── Tests/
    ├── Domain/
    ├── Data/
    ├── Presentation/
    └── Fixtures/
```
**Ownership rule:** `Tutor` owns its user-facing state and use cases. Cross-feature access occurs through explicit protocols, app navigation, or backend APIs; one feature must not import another feature’s presentation layer.

### `Features/Attempts`

```text
Features/Attempts/
├── AttemptsFeature.swift
├── AttemptsRoute.swift
├── Domain/
│   ├── SubmitAttemptUseCase.swift
│   ├── LoadAttemptFeedbackUseCase.swift
│   ├── CompareAttemptUseCase.swift
│   ├── AttemptsRepository.swift
│   ├── Attempt.swift
│   ├── AttemptStep.swift
│   └── AttemptEvaluation.swift
├── Data/
│   ├── AttemptsAPI.swift
│   └── DefaultAttemptsRepository.swift
├── Presentation/
│   ├── AttemptEditorView.swift
│   ├── AttemptViewModel.swift
│   ├── StepComparisonView.swift
│   └── AttemptFeedbackView.swift
└── Tests/
    ├── Domain/
    ├── Data/
    ├── Presentation/
    └── Fixtures/
```
**Ownership rule:** `Attempts` owns its user-facing state and use cases. Cross-feature access occurs through explicit protocols, app navigation, or backend APIs; one feature must not import another feature’s presentation layer.

### `Features/MistakeBook`

```text
Features/MistakeBook/
├── MistakeBookFeature.swift
├── MistakeBookRoute.swift
├── Domain/
│   ├── LoadMistakesUseCase.swift
│   ├── LoadMistakeDetailUseCase.swift
│   ├── PracticeMistakeGroupUseCase.swift
│   ├── MistakeRepository.swift
│   ├── Mistake.swift
│   ├── MistakeCategory.swift
│   ├── MistakeGroup.swift
│   └── MistakeTrend.swift
├── Data/
│   ├── MistakeAPI.swift
│   └── DefaultMistakeRepository.swift
├── Presentation/
│   ├── MistakeBookView.swift
│   ├── MistakeBookViewModel.swift
│   ├── MistakeCategoryCard.swift
│   ├── MistakeDetailView.swift
│   └── PracticeMistakesCTA.swift
└── Tests/
    ├── Domain/
    ├── Data/
    ├── Presentation/
    └── Fixtures/
```
**Ownership rule:** `MistakeBook` owns its user-facing state and use cases. Cross-feature access occurs through explicit protocols, app navigation, or backend APIs; one feature must not import another feature’s presentation layer.

### `Features/Mastery`

```text
Features/Mastery/
├── MasteryFeature.swift
├── MasteryRoute.swift
├── Domain/
│   ├── LoadMasteryGraphUseCase.swift
│   ├── LoadSkillMasteryUseCase.swift
│   ├── LoadMasteryHistoryUseCase.swift
│   ├── MasteryRepository.swift
│   ├── SkillMastery.swift
│   ├── MasteryConfidence.swift
│   ├── MasteryHistoryPoint.swift
│   └── KnowledgeGraphNode.swift
├── Data/
│   ├── MasteryAPI.swift
│   └── DefaultMasteryRepository.swift
├── Presentation/
│   ├── MasteryDashboardView.swift
│   ├── MasteryViewModel.swift
│   ├── KnowledgeGraphView.swift
│   ├── SkillMasteryDetailView.swift
│   └── MasteryTrendChart.swift
└── Tests/
    ├── Domain/
    ├── Data/
    ├── Presentation/
    └── Fixtures/
```
**Ownership rule:** `Mastery` owns its user-facing state and use cases. Cross-feature access occurs through explicit protocols, app navigation, or backend APIs; one feature must not import another feature’s presentation layer.

### `Features/StudyPlan`

```text
Features/StudyPlan/
├── StudyPlanFeature.swift
├── StudyPlanRoute.swift
├── Domain/
│   ├── LoadTodayStudyPlanUseCase.swift
│   ├── StartStudySessionUseCase.swift
│   ├── CompleteStudyPlanItemUseCase.swift
│   ├── RescheduleStudyPlanUseCase.swift
│   ├── StudyPlanRepository.swift
│   ├── StudyPlan.swift
│   ├── StudyPlanItem.swift
│   ├── StudySession.swift
│   └── StudyActivityType.swift
├── Data/
│   ├── StudyPlanAPI.swift
│   └── DefaultStudyPlanRepository.swift
├── Presentation/
│   ├── StudyPlanView.swift
│   ├── StudyPlanViewModel.swift
│   ├── TodaySessionView.swift
│   ├── StudyPlanItemRow.swift
│   └── SessionCompletionView.swift
└── Tests/
    ├── Domain/
    ├── Data/
    ├── Presentation/
    └── Fixtures/
```
**Ownership rule:** `StudyPlan` owns its user-facing state and use cases. Cross-feature access occurs through explicit protocols, app navigation, or backend APIs; one feature must not import another feature’s presentation layer.

### `Features/Exam`

```text
Features/Exam/
├── ExamFeature.swift
├── ExamRoute.swift
├── Domain/
│   ├── LoadExamGoalUseCase.swift
│   ├── StartMockExamUseCase.swift
│   ├── SubmitMockExamUseCase.swift
│   ├── LoadExamReadinessUseCase.swift
│   ├── ExamRepository.swift
│   ├── Exam.swift
│   ├── ExamGoal.swift
│   ├── MockExam.swift
│   ├── ExamReadiness.swift
│   └── ExamScoreEstimate.swift
├── Data/
│   ├── ExamAPI.swift
│   └── DefaultExamRepository.swift
├── Presentation/
│   ├── ExamDashboardView.swift
│   ├── ExamViewModel.swift
│   ├── MockExamView.swift
│   ├── ExamReadinessView.swift
│   └── ExamWeaknessView.swift
└── Tests/
    ├── Domain/
    ├── Data/
    ├── Presentation/
    └── Fixtures/
```
**Ownership rule:** `Exam` owns its user-facing state and use cases. Cross-feature access occurs through explicit protocols, app navigation, or backend APIs; one feature must not import another feature’s presentation layer.

### `Features/Library`

```text
Features/Library/
├── LibraryFeature.swift
├── LibraryRoute.swift
├── Domain/
│   ├── LoadSavedProblemsUseCase.swift
│   ├── LoadSavedSolutionsUseCase.swift
│   ├── SearchLibraryUseCase.swift
│   ├── LibraryRepository.swift
│   ├── LibraryItem.swift
│   └── LibraryFilter.swift
├── Data/
│   ├── LibraryAPI.swift
│   └── DefaultLibraryRepository.swift
├── Presentation/
│   ├── LibraryView.swift
│   ├── LibraryViewModel.swift
│   ├── LibrarySearchBar.swift
│   ├── SavedProblemRow.swift
│   └── SavedSolutionRow.swift
└── Tests/
    ├── Domain/
    ├── Data/
    ├── Presentation/
    └── Fixtures/
```
**Ownership rule:** `Library` owns its user-facing state and use cases. Cross-feature access occurs through explicit protocols, app navigation, or backend APIs; one feature must not import another feature’s presentation layer.

### `Features/Subscription`

```text
Features/Subscription/
├── SubscriptionFeature.swift
├── SubscriptionRoute.swift
├── Domain/
│   ├── LoadOfferingsUseCase.swift
│   ├── PurchaseProductUseCase.swift
│   ├── RestorePurchasesUseCase.swift
│   ├── SyncEntitlementsUseCase.swift
│   ├── SubscriptionRepository.swift
│   ├── Entitlement.swift
│   ├── SubscriptionPlan.swift
│   └── UsageQuota.swift
├── Data/
│   ├── SubscriptionAPI.swift
│   └── DefaultSubscriptionRepository.swift
├── Presentation/
│   ├── PaywallView.swift
│   ├── SubscriptionViewModel.swift
│   ├── PlanCard.swift
│   ├── PurchaseStateView.swift
│   └── QuotaUsageView.swift
└── Tests/
    ├── Domain/
    ├── Data/
    ├── Presentation/
    └── Fixtures/
```
**Ownership rule:** `Subscription` owns its user-facing state and use cases. Cross-feature access occurs through explicit protocols, app navigation, or backend APIs; one feature must not import another feature’s presentation layer.

### `Features/Notifications`

```text
Features/Notifications/
├── NotificationsFeature.swift
├── NotificationRoute.swift
├── Domain/
│   ├── LoadNotificationPreferencesUseCase.swift
│   ├── UpdateNotificationPreferencesUseCase.swift
│   ├── RegisterPushTokenUseCase.swift
│   ├── NotificationRepository.swift
│   ├── NotificationPreference.swift
│   └── NotificationCategory.swift
├── Data/
│   ├── NotificationAPI.swift
│   └── DefaultNotificationRepository.swift
├── Presentation/
│   ├── NotificationPreferencesView.swift
│   ├── NotificationViewModel.swift
│   └── NotificationCategoryToggle.swift
└── Tests/
    ├── Domain/
    ├── Data/
    ├── Presentation/
    └── Fixtures/
```
**Ownership rule:** `Notifications` owns its user-facing state and use cases. Cross-feature access occurs through explicit protocols, app navigation, or backend APIs; one feature must not import another feature’s presentation layer.

### `Features/Reports`

```text
Features/Reports/
├── ReportsFeature.swift
├── ReportsRoute.swift
├── Domain/
│   ├── LoadWeeklyReportUseCase.swift
│   ├── LoadLearningTrendUseCase.swift
│   ├── ReportsRepository.swift
│   ├── WeeklyLearningReport.swift
│   └── LearningTrend.swift
├── Data/
│   ├── ReportsAPI.swift
│   └── DefaultReportsRepository.swift
├── Presentation/
│   ├── WeeklyReportView.swift
│   ├── ReportsViewModel.swift
│   ├── ImprovementCard.swift
│   ├── MistakeDistributionView.swift
│   └── StudyTimeChart.swift
└── Tests/
    ├── Domain/
    ├── Data/
    ├── Presentation/
    └── Fixtures/
```
**Ownership rule:** `Reports` owns its user-facing state and use cases. Cross-feature access occurs through explicit protocols, app navigation, or backend APIs; one feature must not import another feature’s presentation layer.

### `Features/Settings`

```text
Features/Settings/
├── SettingsFeature.swift
├── SettingsRoute.swift
├── Domain/
│   ├── LoadSettingsUseCase.swift
│   ├── UpdateSettingsUseCase.swift
│   ├── DeleteAccountUseCase.swift
│   ├── ExportDataUseCase.swift
│   ├── SettingsRepository.swift
│   ├── AppSettings.swift
│   └── PrivacyPreference.swift
├── Data/
│   ├── SettingsAPI.swift
│   └── DefaultSettingsRepository.swift
├── Presentation/
│   ├── SettingsView.swift
│   ├── SettingsViewModel.swift
│   ├── PrivacySettingsView.swift
│   ├── DataControlsView.swift
│   └── AccountDeletionView.swift
└── Tests/
    ├── Domain/
    ├── Data/
    ├── Presentation/
    └── Fixtures/
```
**Ownership rule:** `Settings` owns its user-facing state and use cases. Cross-feature access occurs through explicit protocols, app navigation, or backend APIs; one feature must not import another feature’s presentation layer.

### `Features/Support`

```text
Features/Support/
├── SupportFeature.swift
├── SupportRoute.swift
├── Domain/
│   ├── SubmitSupportCaseUseCase.swift
│   ├── SubmitWrongAnswerReportUseCase.swift
│   ├── SupportRepository.swift
│   ├── SupportCase.swift
│   └── ProblemReport.swift
├── Data/
│   ├── SupportAPI.swift
│   └── DefaultSupportRepository.swift
├── Presentation/
│   ├── SupportView.swift
│   ├── SupportViewModel.swift
│   ├── ReportProblemView.swift
│   └── SupportCaseSubmittedView.swift
└── Tests/
    ├── Domain/
    ├── Data/
    ├── Presentation/
    └── Fixtures/
```
**Ownership rule:** `Support` owns its user-facing state and use cases. Cross-feature access occurs through explicit protocols, app navigation, or backend APIs; one feature must not import another feature’s presentation layer.

# 30. Exhaustive Spring Boot modular-monolith hierarchy
Every backend bounded context is a Spring Modulith application module. The repeated internal structure is intentional: `api` is transport, `application` orchestrates use cases, `domain` owns rules, and `infrastructure` adapts persistence/external systems. Direct cross-module JPA access is forbidden.

```text
services/api/src/main/java/com/verifiedlearning/
├── VerifiedLearningApplication.java
├── bootstrap/
├── sharedkernel/
├── identity/
├── profile/
├── curriculum/
├── problem/
├── solving/
├── verification/
├── tutoring/
├── attempt/
├── mistake/
├── mastery/
├── studyplan/
├── exam/
├── billing/
├── notification/
├── analytics/
├── admin/
├── ai/
└── configuration/
```

## 30.1 `identity` module

```text
identity/
├── api/
│   ├── IdentityController.java
│   └── SessionController.java
├── application/
│   ├── command/
│   ├── query/
│   ├── port/
│   ├── IdentityApplicationService.java
│   ├── SignInWithAppleHandler.java
│   ├── RefreshSessionHandler.java
│   ├── RevokeSessionHandler.java
│   └── package-info.java
├── domain/
│   ├── model/
│   │   ├── UserIdentity.java
│   │   ├── RefreshTokenFamily.java
│   │   ├── SessionId.java
│   │   ├── UserId.java
│   │   ├── IdentityRepository.java
│   │   ├── RefreshTokenRepository.java
│   │   ├── AppleIdentityVerifier.java
│   │   ├── JwtTokenIssuer.java
│   ├── event/
│   ├── exception/
│   ├── policy/
│   └── port/
├── infrastructure/
│   ├── persistence/
│   ├── external/
│   ├── configuration/
│   ├── IdentityJpaEntity.java
│   ├── RefreshTokenJpaEntity.java
│   ├── IdentityRepositoryAdapter.java
│   ├── AppleIdentityVerifierAdapter.java
│   ├── JwtTokenIssuerAdapter.java
│   └── package-info.java
└── package-info.java
```
**Boundary rule:** `identity` may expose only documented application/domain contracts. Other modules cannot reach into `identity.infrastructure` or reuse its JPA entities.

## 30.2 `profile` module

```text
profile/
├── api/
│   ├── ProfileController.java
│   └── LearningProfileController.java
├── application/
│   ├── command/
│   ├── query/
│   ├── port/
│   ├── ProfileApplicationService.java
│   ├── CreateLearningProfileHandler.java
│   ├── UpdateLearningProfileHandler.java
│   └── package-info.java
├── domain/
│   ├── model/
│   │   ├── LearningProfile.java
│   │   ├── LearningPreference.java
│   │   ├── EducationLevel.java
│   │   ├── ProfileRepository.java
│   ├── event/
│   ├── exception/
│   ├── policy/
│   └── port/
├── infrastructure/
│   ├── persistence/
│   ├── external/
│   ├── configuration/
│   ├── ProfileJpaEntity.java
│   ├── ProfileRepositoryAdapter.java
│   └── package-info.java
└── package-info.java
```
**Boundary rule:** `profile` may expose only documented application/domain contracts. Other modules cannot reach into `profile.infrastructure` or reuse its JPA entities.

## 30.3 `curriculum` module

```text
curriculum/
├── api/
│   ├── CurriculumController.java
│   └── SkillController.java
├── application/
│   ├── command/
│   ├── query/
│   ├── port/
│   ├── CurriculumQueryService.java
│   └── package-info.java
├── domain/
│   ├── model/
│   │   ├── Curriculum.java
│   │   ├── Subject.java
│   │   ├── Topic.java
│   │   ├── Skill.java
│   │   ├── SkillPrerequisite.java
│   │   ├── CurriculumVersion.java
│   │   ├── CurriculumRepository.java
│   │   ├── SkillRepository.java
│   ├── event/
│   ├── exception/
│   ├── policy/
│   └── port/
├── infrastructure/
│   ├── persistence/
│   ├── external/
│   ├── configuration/
│   ├── CurriculumJpaEntity.java
│   ├── SkillJpaEntity.java
│   ├── CurriculumRepositoryAdapter.java
│   └── package-info.java
└── package-info.java
```
**Boundary rule:** `curriculum` may expose only documented application/domain contracts. Other modules cannot reach into `curriculum.infrastructure` or reuse its JPA entities.

## 30.4 `problem` module

```text
problem/
├── api/
│   ├── ProblemController.java
│   ├── ProblemAssetController.java
│   └── ProblemQueryController.java
├── application/
│   ├── command/
│   ├── query/
│   ├── port/
│   ├── ProblemApplicationService.java
│   ├── CreateProblemSessionHandler.java
│   ├── AttachProblemAssetHandler.java
│   ├── ConfirmProblemParseHandler.java
│   ├── ReviseProblemParseHandler.java
│   └── package-info.java
├── domain/
│   ├── model/
│   │   ├── Problem.java
│   │   ├── ProblemId.java
│   │   ├── ProblemSession.java
│   │   ├── ProblemAsset.java
│   │   ├── ProblemParse.java
│   │   ├── ProblemStatus.java
│   │   ├── ProblemRepository.java
│   │   ├── ProblemAssetStore.java
│   │   ├── S3ProblemAssetStore.java
│   ├── event/
│   ├── exception/
│   ├── policy/
│   └── port/
├── infrastructure/
│   ├── persistence/
│   ├── external/
│   ├── configuration/
│   ├── ProblemJpaEntity.java
│   ├── ProblemAssetJpaEntity.java
│   ├── ProblemRepositoryAdapter.java
│   └── package-info.java
└── package-info.java
```
**Boundary rule:** `problem` may expose only documented application/domain contracts. Other modules cannot reach into `problem.infrastructure` or reuse its JPA entities.

## 30.5 `solving` module

```text
solving/
├── api/
│   ├── SolvingController.java
│   └── SolveJobController.java
├── application/
│   ├── command/
│   ├── query/
│   ├── port/
│   ├── SolveProblemApplicationService.java
│   ├── SolvePipeline.java
│   ├── PrimarySolverStage.java
│   ├── ConditionalSecondarySolverStage.java
│   ├── ArbitrationStage.java
│   ├── ExplanationStage.java
│   └── package-info.java
├── domain/
│   ├── model/
│   │   ├── Solution.java
│   │   ├── SolutionStep.java
│   │   ├── SolveJob.java
│   │   ├── SolverRun.java
│   │   ├── SolverCandidate.java
│   │   ├── SolverGateway.java
│   │   ├── SolveJobRepository.java
│   │   ├── SolutionRepository.java
│   ├── event/
│   ├── exception/
│   ├── policy/
│   └── port/
├── infrastructure/
│   ├── persistence/
│   ├── external/
│   ├── configuration/
│   ├── SolveJobJpaEntity.java
│   ├── SolutionJpaEntity.java
│   ├── SolverGatewayAdapter.java
│   └── package-info.java
└── package-info.java
```
**Boundary rule:** `solving` may expose only documented application/domain contracts. Other modules cannot reach into `solving.infrastructure` or reuse its JPA entities.

## 30.6 `verification` module

```text
verification/
├── api/
│   └── VerificationController.java
├── application/
│   ├── command/
│   ├── query/
│   ├── port/
│   ├── VerificationApplicationService.java
│   ├── VerificationPlanner.java
│   ├── VerificationPolicy.java
│   └── package-info.java
├── domain/
│   ├── model/
│   │   ├── VerificationRun.java
│   │   ├── VerificationSignal.java
│   │   ├── VerificationMethod.java
│   │   ├── VerificationStatus.java
│   │   ├── MathVerifierGateway.java
│   │   ├── VerificationRepository.java
│   ├── event/
│   ├── exception/
│   ├── policy/
│   └── port/
├── infrastructure/
│   ├── persistence/
│   ├── external/
│   ├── configuration/
│   ├── VerificationRunJpaEntity.java
│   ├── VerificationRepositoryAdapter.java
│   ├── MathVerifierHttpAdapter.java
│   └── package-info.java
└── package-info.java
```
**Boundary rule:** `verification` may expose only documented application/domain contracts. Other modules cannot reach into `verification.infrastructure` or reuse its JPA entities.

## 30.7 `tutoring` module

```text
tutoring/
├── api/
│   ├── TutorController.java
│   └── TutorSessionController.java
├── application/
│   ├── command/
│   ├── query/
│   ├── port/
│   ├── TutorApplicationService.java
│   ├── StartTutorSessionHandler.java
│   ├── ProcessTutorTurnHandler.java
│   ├── GenerateHintHandler.java
│   └── package-info.java
├── domain/
│   ├── model/
│   │   ├── TutorSession.java
│   │   ├── TutorTurn.java
│   │   ├── Hint.java
│   │   ├── TutorMode.java
│   │   ├── TutorRepository.java
│   │   ├── TutorModelGateway.java
│   ├── event/
│   ├── exception/
│   ├── policy/
│   └── port/
├── infrastructure/
│   ├── persistence/
│   ├── external/
│   ├── configuration/
│   ├── TutorSessionJpaEntity.java
│   ├── TutorRepositoryAdapter.java
│   └── package-info.java
└── package-info.java
```
**Boundary rule:** `tutoring` may expose only documented application/domain contracts. Other modules cannot reach into `tutoring.infrastructure` or reuse its JPA entities.

## 30.8 `attempt` module

```text
attempt/
├── api/
│   └── AttemptController.java
├── application/
│   ├── command/
│   ├── query/
│   ├── port/
│   ├── AttemptApplicationService.java
│   ├── SubmitAttemptHandler.java
│   ├── EvaluateAttemptHandler.java
│   └── package-info.java
├── domain/
│   ├── model/
│   │   ├── Attempt.java
│   │   ├── AttemptStep.java
│   │   ├── AttemptEvaluation.java
│   │   ├── AttemptRepository.java
│   │   ├── AttemptEvaluator.java
│   ├── event/
│   ├── exception/
│   ├── policy/
│   └── port/
├── infrastructure/
│   ├── persistence/
│   ├── external/
│   ├── configuration/
│   ├── AttemptJpaEntity.java
│   ├── AttemptStepJpaEntity.java
│   ├── AttemptRepositoryAdapter.java
│   └── package-info.java
└── package-info.java
```
**Boundary rule:** `attempt` may expose only documented application/domain contracts. Other modules cannot reach into `attempt.infrastructure` or reuse its JPA entities.

## 30.9 `mistake` module

```text
mistake/
├── api/
│   ├── MistakeController.java
│   └── MistakeQueryController.java
├── application/
│   ├── command/
│   ├── query/
│   ├── port/
│   ├── MistakeApplicationService.java
│   ├── DetectMistakesHandler.java
│   ├── GroupMistakesHandler.java
│   └── package-info.java
├── domain/
│   ├── model/
│   │   ├── Mistake.java
│   │   ├── MistakeCategory.java
│   │   ├── MistakeEvidence.java
│   │   ├── MistakeGroup.java
│   │   ├── MistakeRepository.java
│   │   ├── MistakeClassifierGateway.java
│   ├── event/
│   ├── exception/
│   ├── policy/
│   └── port/
├── infrastructure/
│   ├── persistence/
│   ├── external/
│   ├── configuration/
│   ├── MistakeJpaEntity.java
│   ├── MistakeRepositoryAdapter.java
│   └── package-info.java
└── package-info.java
```
**Boundary rule:** `mistake` may expose only documented application/domain contracts. Other modules cannot reach into `mistake.infrastructure` or reuse its JPA entities.

## 30.10 `mastery` module

```text
mastery/
├── api/
│   ├── MasteryController.java
│   └── KnowledgeGraphController.java
├── application/
│   ├── command/
│   ├── query/
│   ├── port/
│   ├── MasteryApplicationService.java
│   ├── UpdateMasteryHandler.java
│   ├── RecalculateMasteryHandler.java
│   ├── MasteryPolicy.java
│   └── package-info.java
├── domain/
│   ├── model/
│   │   ├── SkillMastery.java
│   │   ├── MasterySnapshot.java
│   │   ├── MasteryConfidence.java
│   │   ├── KnowledgeGraph.java
│   │   ├── MasteryRepository.java
│   ├── event/
│   ├── exception/
│   ├── policy/
│   └── port/
├── infrastructure/
│   ├── persistence/
│   ├── external/
│   ├── configuration/
│   ├── SkillMasteryJpaEntity.java
│   ├── MasteryHistoryJpaEntity.java
│   ├── MasteryRepositoryAdapter.java
│   └── package-info.java
└── package-info.java
```
**Boundary rule:** `mastery` may expose only documented application/domain contracts. Other modules cannot reach into `mastery.infrastructure` or reuse its JPA entities.

## 30.11 `studyplan` module

```text
studyplan/
├── api/
│   ├── StudyPlanController.java
│   └── StudySessionController.java
├── application/
│   ├── command/
│   ├── query/
│   ├── port/
│   ├── StudyPlanApplicationService.java
│   ├── GenerateStudyPlanHandler.java
│   ├── RebalanceStudyPlanHandler.java
│   ├── CompleteStudyItemHandler.java
│   └── package-info.java
├── domain/
│   ├── model/
│   │   ├── StudyPlan.java
│   │   ├── StudyPlanItem.java
│   │   ├── StudySession.java
│   │   ├── StudyPlanRepository.java
│   │   ├── AdaptiveSelectionGateway.java
│   ├── event/
│   ├── exception/
│   ├── policy/
│   └── port/
├── infrastructure/
│   ├── persistence/
│   ├── external/
│   ├── configuration/
│   ├── StudyPlanJpaEntity.java
│   ├── StudyPlanItemJpaEntity.java
│   ├── StudyPlanRepositoryAdapter.java
│   └── package-info.java
└── package-info.java
```
**Boundary rule:** `studyplan` may expose only documented application/domain contracts. Other modules cannot reach into `studyplan.infrastructure` or reuse its JPA entities.

## 30.12 `exam` module

```text
exam/
├── api/
│   ├── ExamController.java
│   ├── MockExamController.java
│   └── ExamReadinessController.java
├── application/
│   ├── command/
│   ├── query/
│   ├── port/
│   ├── ExamApplicationService.java
│   ├── CreateExamGoalHandler.java
│   ├── GenerateMockExamHandler.java
│   ├── ScoreMockExamHandler.java
│   ├── CalculateReadinessHandler.java
│   └── package-info.java
├── domain/
│   ├── model/
│   │   ├── Exam.java
│   │   ├── ExamGoal.java
│   │   ├── MockExam.java
│   │   ├── ExamAttempt.java
│   │   ├── ExamReadiness.java
│   │   ├── ExamRepository.java
│   │   ├── ExamBlueprintRepository.java
│   ├── event/
│   ├── exception/
│   ├── policy/
│   └── port/
├── infrastructure/
│   ├── persistence/
│   ├── external/
│   ├── configuration/
│   ├── ExamJpaEntity.java
│   ├── MockExamJpaEntity.java
│   ├── ExamRepositoryAdapter.java
│   └── package-info.java
└── package-info.java
```
**Boundary rule:** `exam` may expose only documented application/domain contracts. Other modules cannot reach into `exam.infrastructure` or reuse its JPA entities.

## 30.13 `billing` module

```text
billing/
├── api/
│   ├── BillingController.java
│   ├── EntitlementController.java
│   └── AppStoreNotificationController.java
├── application/
│   ├── command/
│   ├── query/
│   ├── port/
│   ├── BillingApplicationService.java
│   ├── ProcessAppStoreNotificationHandler.java
│   ├── SyncEntitlementHandler.java
│   ├── EnforceQuotaHandler.java
│   └── package-info.java
├── domain/
│   ├── model/
│   │   ├── Subscription.java
│   │   ├── Entitlement.java
│   │   ├── UsageQuota.java
│   │   ├── BillingRepository.java
│   │   ├── AppStoreGateway.java
│   ├── event/
│   ├── exception/
│   ├── policy/
│   └── port/
├── infrastructure/
│   ├── persistence/
│   ├── external/
│   ├── configuration/
│   ├── SubscriptionJpaEntity.java
│   ├── EntitlementJpaEntity.java
│   ├── BillingRepositoryAdapter.java
│   ├── AppStoreServerApiAdapter.java
│   └── package-info.java
└── package-info.java
```
**Boundary rule:** `billing` may expose only documented application/domain contracts. Other modules cannot reach into `billing.infrastructure` or reuse its JPA entities.

## 30.14 `notification` module

```text
notification/
├── api/
│   ├── NotificationPreferenceController.java
│   └── DeviceTokenController.java
├── application/
│   ├── command/
│   ├── query/
│   ├── port/
│   ├── NotificationApplicationService.java
│   ├── RegisterDeviceTokenHandler.java
│   ├── ScheduleLearningReminderHandler.java
│   └── package-info.java
├── domain/
│   ├── model/
│   │   ├── NotificationPreference.java
│   │   ├── DevicePushToken.java
│   │   ├── NotificationMessage.java
│   │   ├── NotificationRepository.java
│   │   ├── PushGateway.java
│   ├── event/
│   ├── exception/
│   ├── policy/
│   └── port/
├── infrastructure/
│   ├── persistence/
│   ├── external/
│   ├── configuration/
│   ├── NotificationPreferenceJpaEntity.java
│   ├── DevicePushTokenJpaEntity.java
│   ├── NotificationRepositoryAdapter.java
│   └── package-info.java
└── package-info.java
```
**Boundary rule:** `notification` may expose only documented application/domain contracts. Other modules cannot reach into `notification.infrastructure` or reuse its JPA entities.

## 30.15 `analytics` module

```text
analytics/
├── api/
│   ├── AnalyticsIngestController.java
│   └── MetricsQueryController.java
├── application/
│   ├── command/
│   ├── query/
│   ├── port/
│   ├── AnalyticsApplicationService.java
│   ├── RecordProductEventHandler.java
│   └── package-info.java
├── domain/
│   ├── model/
│   │   ├── AnalyticsEvent.java
│   │   ├── MetricDefinition.java
│   │   ├── AnalyticsRepository.java
│   ├── event/
│   ├── exception/
│   ├── policy/
│   └── port/
├── infrastructure/
│   ├── persistence/
│   ├── external/
│   ├── configuration/
│   ├── AnalyticsEventJpaEntity.java
│   ├── AnalyticsRepositoryAdapter.java
│   └── package-info.java
└── package-info.java
```
**Boundary rule:** `analytics` may expose only documented application/domain contracts. Other modules cannot reach into `analytics.infrastructure` or reuse its JPA entities.

## 30.16 `admin` module

```text
admin/
├── api/
│   ├── AdminUserController.java
│   ├── AdminProblemTraceController.java
│   ├── AdminAiCostController.java
│   ├── AdminFeatureFlagController.java
│   └── AdminSupportController.java
├── application/
│   ├── command/
│   ├── query/
│   ├── port/
│   ├── AdminApplicationService.java
│   ├── AdminAuditService.java
│   └── package-info.java
├── domain/
│   ├── model/
│   │   ├── AdminPermission.java
│   │   ├── SupportCase.java
│   │   ├── AdminAuditEvent.java
│   │   ├── AdminRepository.java
│   ├── event/
│   ├── exception/
│   ├── policy/
│   └── port/
├── infrastructure/
│   ├── persistence/
│   ├── external/
│   ├── configuration/
│   ├── AdminAuditJpaEntity.java
│   ├── AdminRepositoryAdapter.java
│   └── package-info.java
└── package-info.java
```
**Boundary rule:** `admin` may expose only documented application/domain contracts. Other modules cannot reach into `admin.infrastructure` or reuse its JPA entities.

## 30.17 `ai` module

```text
ai/
├── api/
│   └── AiInternalController.java
├── application/
│   ├── command/
│   ├── query/
│   ├── port/
│   ├── AiOrchestrationService.java
│   ├── AiUsageService.java
│   ├── ProviderHealthService.java
│   ├── ModelCapabilityPolicy.java
│   └── package-info.java
├── domain/
│   ├── model/
│   │   ├── ModelRouter.java
│   │   ├── PromptRegistry.java
│   │   ├── AiProvider.java
│   │   ├── AiCapability.java
│   │   ├── AiRequest.java
│   │   ├── AiResponse.java
│   │   ├── AiUsage.java
│   │   ├── ModelRouteDecision.java
│   │   ├── AiModelGateway.java
│   │   ├── PromptStore.java
│   │   ├── AiUsageRepository.java
│   ├── event/
│   ├── exception/
│   ├── policy/
│   └── port/
├── infrastructure/
│   ├── persistence/
│   ├── external/
│   ├── configuration/
│   ├── OpenAiModelAdapter.java
│   ├── GeminiModelAdapter.java
│   ├── AiUsageJpaEntity.java
│   ├── AiUsageRepositoryAdapter.java
│   └── package-info.java
└── package-info.java
```
**Boundary rule:** `ai` may expose only documented application/domain contracts. Other modules cannot reach into `ai.infrastructure` or reuse its JPA entities.

# 31. Backend resources, migrations, schemas, and test mirrors

```text
services/api/src/main/resources/
├── application.yml
├── application-local.yml
├── application-test.yml
├── application-staging.yml
├── application-prod.yml
├── db/migration/
│   ├── identity/
│   ├── profile/
│   ├── curriculum/
│   ├── problem/
│   ├── solving/
│   ├── verification/
│   ├── tutoring/
│   ├── attempt/
│   ├── mistake/
│   ├── mastery/
│   ├── studyplan/
│   ├── exam/
│   ├── billing/
│   ├── notification/
│   ├── analytics/
│   ├── admin/
│   └── ai/
├── prompts/                 # packaged release metadata only; canonical prompt source stays /prompts
├── schemas/                 # packaged validated schemas generated from /packages/schemas
└── logback-spring.xml

services/api/src/test/java/com/verifiedlearning/
├── architecture/
├── contract/
├── integration/
├── security/
├── support/
└── <module>/
    ├── domain/
    ├── application/
    ├── api/
    └── infrastructure/
```
Migration files must be immutable after production application. Corrections require a new forward migration. Test packages mirror production ownership and must not create a parallel architecture.

# 32. Internal math-verifier exhaustive hierarchy

```text
services/math-verifier/
├── pyproject.toml
├── uv.lock
├── Dockerfile
├── README.md
├── app/
│   ├── main.py
│   ├── config.py
│   ├── api/
│   │   ├── routes/health.py
│   │   ├── routes/verify.py
│   │   ├── dependencies.py
│   │   └── schemas/
│   │       ├── expression.py
│   │       ├── verification_request.py
│   │       └── verification_response.py
│   ├── domain/
│   │   ├── expression.py
│   │   ├── verification.py
│   │   ├── verification_method.py
│   │   └── errors.py
│   ├── parsing/
│   │   ├── safe_parser.py
│   │   ├── symbol_table.py
│   │   └── parser_limits.py
│   ├── verifiers/
│   │   ├── arithmetic.py
│   │   ├── algebra.py
│   │   ├── equations.py
│   │   ├── functions.py
│   │   ├── limits.py
│   │   ├── derivatives.py
│   │   ├── integrals.py
│   │   ├── matrices.py
│   │   ├── statistics.py
│   │   ├── numeric.py
│   │   └── equivalence.py
│   ├── policies/
│   │   ├── execution_budget.py
│   │   ├── random_substitution.py
│   │   └── verification_policy.py
│   ├── security/
│   │   ├── internal_auth.py
│   │   ├── expression_sandbox.py
│   │   └── resource_limits.py
│   └── observability/
│       ├── logging.py
│       ├── metrics.py
│       └── tracing.py
├── tests/
│   ├── unit/
│   ├── property/
│   ├── integration/
│   ├── security/
│   └── fixtures/
└── benchmarks/
    ├── datasets/
    ├── runners/
    └── reports/
```
The verifier must never execute arbitrary model-generated Python or SymPy strings without bounded safe parsing and resource limits.

# 33. Contracts, schemas, curriculum, prompts, and evaluations

```text
packages/
├── contracts/
│   ├── openapi/
│   │   ├── public-api.yaml
│   │   └── internal-math-verifier.yaml
│   ├── events/
│   │   ├── problem-events/
│   │   ├── solution-events/
│   │   ├── learning-events/
│   │   └── billing-events/
│   └── generated/
├── schemas/
│   ├── ai/problem-parse/
│   ├── ai/solution-candidate/
│   ├── ai/mistake-classification/
│   ├── ai/tutor-turn/
│   └── domain/
├── curriculum/
│   ├── versions/
│   ├── subjects/
│   ├── topics/
│   ├── skills/
│   ├── prerequisites/
│   ├── exam-mappings/
│   ├── migrations/
│   └── validation/
└── test-fixtures/
    ├── api/
    ├── math/
    ├── users/
    └── storekit/

prompts/
├── problem-parser/<version>/
├── primary-solver/<version>/
├── secondary-solver/<version>/
├── arbiter/<version>/
├── explanation/<version>/
├── tutor/<version>/
├── mistake-classifier/<version>/
├── study-planner/<version>/
└── README.md

evaluations/
├── golden-datasets/
│   ├── ingestion/
│   ├── algebra/
│   ├── calculus/
│   ├── verification/
│   ├── tutoring/
│   └── mistakes/
├── protected-holdouts/          # access restricted; never used for prompt/training iteration
├── rubrics/
├── runners/
├── baselines/
├── regression-thresholds/
├── reports/
└── README.md
```

# 34. Conditional proprietary-ML hierarchy (Phase 13 only)

The following subtree is prohibited before the model-replacement gates in `quality/64_AI_MODEL_REPLACEMENT_DECISION_GATES.md` are satisfied. Empty placeholder directories should not be created merely to signal future intent.

```text
ml/
├── README.md
├── datasets/
│   ├── manifests/
│   ├── eligibility/
│   ├── curated/
│   ├── splits/
│   └── lineage/
├── experiments/
│   ├── skill-classifier/
│   ├── mistake-classifier/
│   ├── difficulty-predictor/
│   ├── mastery-predictor/
│   ├── recommendation-ranker/
│   └── specialized-math-model/
├── training/
│   ├── configs/
│   ├── pipelines/
│   ├── evaluation/
│   └── reproducibility/
├── registry/
│   ├── model-cards/
│   ├── release-manifests/
│   └── rollback-manifests/
└── serving-contracts/

services/model-inference/        # created only after self-hosting TCO gate
├── Dockerfile
├── app/
├── tests/
├── load-tests/
└── README.md
```

# 35. Infrastructure-as-code, observability, and environment hierarchy

```text
infra/
├── local/
│   ├── docker-compose.yml
│   ├── docker-compose.observability.yml
│   ├── env/.env.example
│   └── seed/
├── terraform/
│   ├── modules/
│   │   ├── network/
│   │   ├── api-service/
│   │   ├── verifier-service/
│   │   ├── postgres/
│   │   ├── redis/
│   │   ├── object-storage/
│   │   ├── secrets/
│   │   ├── observability/
│   │   ├── alerting/
│   │   └── dns-tls/
│   └── environments/
│       ├── dev/
│       ├── staging/
│       └── prod/
├── monitoring/
│   ├── dashboards/
│   │   ├── api.json
│   │   ├── ai-unit-economics.json
│   │   ├── verification-quality.json
│   │   ├── postgres.json
│   │   └── product-slos.json
│   ├── alerts/
│   ├── recording-rules/
│   └── synthetic-checks/
├── database/
│   ├── backup/
│   ├── restore/
│   ├── pitr/
│   └── performance/
├── security/
│   ├── policies/
│   ├── secret-rotation/
│   ├── iam/
│   └── vulnerability-scanning/
└── README.md
```

# 36. Repository automation, developer tools, and generated artifacts

```text
scripts/
├── bootstrap/bootstrap-local.sh
├── database/migrate.sh
├── database/reset-local.sh
├── database/seed-local.sh
├── release/create-release.sh
├── release/rollback.sh
├── evaluation/run-golden-suite.sh
├── evaluation/compare-model-baseline.sh
├── quality/check-doc-links.sh
├── quality/check-module-boundaries.sh
├── quality/check-generated-files.sh
└── maintenance/prune-expired-assets.sh

tools/
├── dev-cli/
├── fixture-generator/
├── curriculum-validator/
├── prompt-linter/
├── dataset-curator/
└── trace-inspector/

.generated/                        # ignored
├── openapi-clients/
├── schemas/
├── reports/
└── docs-index/

build/                             # ignored
coverage/                          # ignored
DerivedData/                       # ignored
.pytest_cache/                     # ignored
.venv/                             # ignored
target/                            # ignored
```

# 37. File-placement decision procedure for humans and Codex

When adding a file, apply this decision order without skipping steps:

1. **Is it a user-visible product capability?** Place it in the owning iOS feature or backend bounded context, not in Core/shared.
2. **Is it a reusable platform mechanism with no business semantics?** Place it in iOS `Core`, backend configuration/platform infrastructure, or a dedicated package.
3. **Does it encode a domain invariant?** It belongs to the owning backend `domain` package. Never place invariants in controllers, DTOs, Swift views, or prompt text alone.
4. **Does it orchestrate a use case?** Backend `application`; iOS `Domain/UseCases`.
5. **Does it translate HTTP/database/provider details?** `infrastructure` or iOS `Data`.
6. **Is it an external contract?** `packages/contracts`/`packages/schemas`; generate adapters from there when practical.
7. **Is it AI behavior?** Prompt source in `/prompts`; schemas in `/packages/schemas`; runtime routing in backend `ai`; quality evidence in `/evaluations`.
8. **Is it deterministic mathematics?** Internal verifier service, not prompts or client code.
9. **Is it a future learned model?** `/ml` only after Phase 13 gate.
10. **Is it documentation?** Put it in the domain-specific docs folder and update `00_MASTER_INDEX.md` if canonical.
11. **Is no location obvious?** Stop and update the architecture; do not create `misc`, `utils`, `helpers`, or `common` as an escape hatch.

# 38. Ownership and forbidden dependency matrix

| Source | Allowed direct dependency | Forbidden direct dependency |
|---|---|---|
| iOS Presentation | Same feature Domain, Core UI/platform | Other feature Presentation/Data, backend SDK internals |
| iOS Domain | SharedDomain, pure Swift/Foundation | SwiftUI Views, URLSession concrete client, StoreKit concrete APIs |
| iOS Data | Feature Domain, Core networking/persistence | Other feature internals |
| Backend API | Same module Application, DTO mappers | JPA repositories from another module, provider SDKs |
| Backend Application | Same module Domain, declared module APIs | Controllers, concrete external provider details |
| Backend Domain | Same domain/value objects, shared kernel primitives | Spring MVC/JPA/AI SDK/HTTP |
| Backend Infrastructure | Same module Domain/Application ports | Another module’s infrastructure/JPA entities |
| Math verifier | Safe math libraries, internal contracts | User identity/billing/learning policy |
| AI adapters | AI domain ports + provider SDK | Mastery/business policy implementation |
| Prompt repository | Versioned behavior text/schema references | Secrets, database credentials, hidden product policy |
| Evaluations | Frozen datasets/runners | Runtime mutation of production state |
| ML experiments | Eligible curated datasets | Raw production student data by default |

# 39. Test file placement and required mirror rules

- Every backend module has domain/application/API/infrastructure tests in the same ownership namespace.
- Every iOS feature has ViewModel/use-case/repository tests; critical purchase/auth/solve/delete-account flows also have UI tests.
- Integration tests that cross modules belong in `services/api/src/test/.../integration`, not inside a random module.
- Contract tests belong under `contract/` and use canonical contracts from `packages/contracts`.
- AI regression tests and golden suites live under `evaluations`, while code-level router/prompt registry tests remain with backend code.
- Mathematical property tests live with the verifier and must exercise resource limits and adversarial expressions as well as correctness.
- Load tests are operational assets and must not be mixed into unit-test packages.

# 40. Data and migration file placement rules

Relational business state belongs in PostgreSQL. Large binary assets belong in object storage. Redis is ephemeral coordination/cache only. Each schema change requires a forward Flyway migration grouped by owning domain. SQL filenames use immutable monotonic versions and semantic descriptions, for example `V042__problem_add_parse_revision.sql`. Seed data is environment-specific and never masquerades as migration logic. Production migration rollback is achieved by forward corrective migrations and backup/PITR procedures, not by editing already-applied SQL.

# 41. Secrets, configuration, and environment file rules

Only non-secret templates are committed. iOS environment identifiers are delivered through xcconfig/build settings; API/provider secrets live exclusively server-side in a secret manager. Backend `application-*.yml` may reference environment variables/secret names but must not contain credentials. Test secrets are synthetic. Any generated local secrets file is ignored. Provider API keys are prohibited from Swift source, prompt files, fixtures, screenshots, documentation examples, and CI logs.

# 42. Documentation repository hierarchy rule

The documentation corpus itself is a first-class hierarchy. `quality/65_CANONICAL_DOCUMENTATION_TREE_AND_SOURCE_OF_TRUTH_MAP.md` enumerates every Markdown artifact and its role. Any canonical new document must be added to the Master Index and manifest. Sprint documents may reference canonical documents but may not silently redefine their invariants.

# 43. Future Android and web expansion placement

If product evidence justifies additional clients, add `apps/android/` and/or `apps/web/` as peers of `apps/ios`. Do not move backend/domain logic into a cross-platform client package to avoid duplication. Shared contracts may be generated into each client from `packages/contracts`; semantic business authority remains server-side. Existing iOS feature naming should guide analogous client capability naming without forcing implementation-level coupling.

# 44. Final V4 hierarchy invariants

1. Every production file has one inferable owner.
2. No catch-all folder is allowed to become an architectural bypass.
3. Domain invariants remain server-owned even when mirrored for UX on the client.
4. AI provider code is isolated behind ports/adapters and never leaks into product-domain modules or iOS.
5. Verification is its own deterministic capability and is not replaced by model confidence prose.
6. Production student data does not become ML training data through file placement or pipeline convenience.
7. Conditional ML/self-hosting directories are created only after formal quality/economics/privacy gates.
8. Tests mirror ownership; generated outputs are isolated; secrets never enter source.
9. Documentation and sprint artifacts are implementation contracts and must evolve with code.
10. If a required path is missing, architecture must be updated explicitly before inventing a local convention.
