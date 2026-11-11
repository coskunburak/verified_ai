# Sprint 7.9 file manifest

Paths are repository-relative. No files were deleted.

| Change | File | Purpose |
|---|---|---|
| Added | `services/api/src/main/resources/db/migration/platform/V041__mistake_passport_read_model_indexes.sql` | Flyway DDL for read-model tables, twin drill extensions, and 5 covering/partial indexes for $<50\text{ ms}$ reads. |
| Added | `services/api/src/main/java/com/verifiedai/security/AuthenticatedLearnerPrincipal.java` | Security interface providing type-safe learner identity extraction for `@AuthenticationPrincipal`. |
| Modified | `services/api/src/main/java/com/verifiedai/security/VerifiedJwtPrincipal.java` | Implements `AuthenticatedLearnerPrincipal` returning `userId()` for `learnerId()`. |
| Modified | `services/api/src/main/java/com/verifiedai/security/VerifiedJwtPrincipalArgumentResolver.java` | Extends argument resolution to support `AuthenticatedLearnerPrincipal`. |
| Added | `services/api/src/main/java/com/verifiedai/mistake/passport/dto/MistakePassportResponseDto.java` | Immutable records for Mistake Passport API projection, summary metrics, 5-metric snapshots, and counterexamples. |
| Added | `services/api/src/main/java/com/verifiedai/mistake/passport/repository/MistakePassportProjectionRepository.java` | Deterministic JDBC projection repository joining mistake DNA, mastery states, transitions, and active twin challenges. |
| Added | `services/api/src/main/java/com/verifiedai/mistake/passport/service/MistakePassportReadModelService.java` | Service computing summary KPIs across conceptual traps, lifecycle filtering, and arithmetic slip partitioning. |
| Added | `services/api/src/main/java/com/verifiedai/mistake/passport/controller/MistakePassportQueryController.java` | BOLA-protected REST controller exposing `GET /v1/mistake-passport` with `Cache-Control: no-store, must-revalidate`. |
| Added | `services/api/src/test/java/com/verifiedai/mistake/passport/MistakePassportReadModelServiceTest.java` | Unit tests for KPI calculations, arithmetic partitioning, and status filtering. |
| Added | `services/api/src/test/java/com/verifiedai/mistake/passport/MistakePassportIntegrationTest.java` | MockMvc integration tests for BOLA isolation, 5-metric separation, arithmetic exclusion, and status filtering. |
| Added | `packages/schemas/mistake/mistake-passport-v1.schema.json` | JSON Schema Draft 2020-12 specification for Mistake Passport projection. |
| Added | `packages/schemas/mistake/fixtures/mistake-passport-v1.valid.json` | Canonical positive fixture for Mistake Passport projection. |
| Added | `packages/schemas/attempt/counterexample-proof-card-v1.schema.json` | JSON Schema Draft 2020-12 specification for Card `H-01` counterexample proof card. |
| Added | `packages/schemas/attempt/fixtures/counterexample-proof-card-v1.valid.json` | Canonical positive fixture for Card `H-01`. |
| Modified | `packages/contracts/openapi/public-api.yaml` | Added `GET /v1/mistake-passport` endpoint and component schemas. |
| Modified | `apps/ios/VerifiedAI/Features/AttemptAnalysis/Presentation/AttemptMathView.swift` | Made `NativeMathLabel` public to support native 2D math rendering across views. |
| Added | `apps/ios/VerifiedAI/Features/AttemptAnalysis/Views/StepDiffNativeMathRenderer.swift` | Native 2D mathematical sub-expression renderer without WebViews. |
| Added | `apps/ios/VerifiedAI/Features/AttemptAnalysis/Views/CounterexampleProofCardView.swift` | Screen 3 Card `H-01` concrete counterexample proof card with VoiceOver accessibility. |
| Added | `apps/ios/VerifiedAI/Features/MistakePassport/Domain/MistakePassportModels.swift` | Swift domain models and API protocol for Mistake Passport. |
| Added | `apps/ios/VerifiedAI/Features/MistakePassport/Cache/MistakePassportCacheStore.swift` | Thread-safe cache store with immediate logout purge support. |
| Added | `apps/ios/VerifiedAI/Features/MistakePassport/ViewModels/MistakePassportViewModel.swift` | `@MainActor @Observable` view model with generation-fencing race protection. |
| Added | `apps/ios/VerifiedAI/Features/MistakePassport/Views/ExamTrapRadarCardView.swift` | Radar trap card view with non-stigmatizing framing and metric indicators. |
| Added | `apps/ios/VerifiedAI/Features/MistakePassport/Views/MistakePassportView.swift` | Screen 6 Mistake Passport view with hero counters, category tabs, and offline stale banner. |
| Added | `apps/ios/VerifiedAI/Features/MasteryGraph/Presentation/DualRingSkillNodeView.swift` | Screen 7 dual-ring skill node view ($M_{u,s}$ inner, $R_{u,s}(t)$ outer) with active trap badge. |
| Modified | `apps/ios/VerifiedAI/Features/MasteryGraph/Presentation/MasteryGraphExplorerView.swift` | Integrated `DualRingSkillNodeView` into curriculum tier cards. |
| Added | `apps/ios/VerifiedAITests/Sprint79MistakePassportTests.swift` | XCTest unit test suite for view models, generation fencing, logout purge, and proof cards. |
| Modified | `apps/ios/VerifiedAI/Resources/Localizable.xcstrings` | Added 26 localized string keys in English and Turkish. |
| Modified | `apps/ios/VerifiedAI.xcodeproj/project.pbxproj` | Registered new Swift files and fixtures in Xcode project build phases. |
| Modified | `scripts/quality/validate_json_schemas.py` | Added validation for Sprint 7.9 schemas, fixtures, and OpenAPI paths. |
| Added | `scripts/quality/sprint_7_9_owner_acceptance.sh` | 7-stage deterministic owner acceptance suite. |
| Added | `docs/verified_ai_learning_platform_docs_v4/sprints/phase07_attempt_mistake_and_mastery_intelligence/SPRINT_7.9_EXECUTION_REPORT.md` | Formal execution report. |
| Added | `docs/verified_ai_learning_platform_docs_v4/sprints/phase07_attempt_mistake_and_mastery_intelligence/SPRINT_7.9_FILE_MANIFEST.md` | Inventory of all modified and created files. |
