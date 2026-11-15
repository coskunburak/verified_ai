# Sprint 9.2 Execution Report — Learner Exam Goal Setup, Candidate Profile & "Snap-to-Plan" Syllabus Ingestion (`H-14`)

## Executive Summary
- **Status:** `LOCAL_ACCEPTANCE_PASSED` (owner host run completed `2026-11-15`; production rollout remains safely feature-flagged behind `verifiedai.exam.candidate-profile-enabled: false`, `verifiedai.exam.syllabus-ingestion-enabled: false`, and `verifiedai.exam.syllabus-live-ai-enabled: false` in `services/api/src/main/resources/application.yml` per `ADR-011`).
- Sprint 9.2 delivers the `V055` candidate profile, horizon history audit, extra-time accessibility accommodations, and Snap-to-Plan syllabus ingestion pipeline across Spring Modulith Java 21 (`com.verifiedai.exam` + `com.verifiedai.studyplan`), Draft 2020-12 JSON schemas and byte-identical OpenAPI 3.1 contracts (`/v1/candidate/**` and `/api/v1/candidate/**`), and native Swift 6 iOS flows (`Features/Exam/`).

## Implemented Invariants & Guardrails
- **Candidate Horizon & Pace Feasibility (`CandidatePaceCalculator`):** Enforces future target dates (`422 CANDIDATE_EXAM_DATE_IN_PAST`), study budget limits (`15–960` min/day, `weekly >= daily <= 6720`, `422 CANDIDATE_STUDY_BUDGET_INVALID`), valid accommodation multipliers (`1.00`, `1.25`, `1.50`, `2.00`, `422 CANDIDATE_ACCOMMODATION_INVALID`), target score bounds (`422`), ceiling-rounded section duration scaling, and deterministic feasibility statuses (`OPTIMAL`, `STRETCH`, `AT_RISK`, `UNREALISTIC`) alongside immutable append-only snapshots in `candidate_profile_horizon_history`.
- **Modulith Event Decoupling (`CandidateGoalUpdatedEvent`):** Published under `com.verifiedai.exam.events` (`@NamedInterface("events")`) and handled inside `com.verifiedai.studyplan.rebalance.CandidateGoalRebalanceListener`.
- **Snap-to-Plan Vision Boundary & Cost Controls (`SyllabusIngestionService`):** Routes extraction through `SyllabusVisionGateway` (`DeterministicSyllabusVisionGateway` active by default at `$0.00` LLM spend; `GeminiSyllabusVisionGateway` opted in only when `verifiedai.exam.syllabus-live-ai-enabled=true`, isolated from `ai.infrastructure` and vendor SDKs). Enforces media MIME/size checks (`10 MB`, JPEG/PNG/WebP/HEIC/PDF), SHA-256 hash cache deduplication (`0` quota burn on duplicate bytes), `3`/month unique quota (`429 SYLLABUS_QUOTA_EXCEEDED`), bilingual curriculum disambiguation (`0.70` similarity threshold via `CurriculumTopicDisambiguator`), `training_eligible = FALSE` PostgreSQL CHECK constraints, and 30-day `expires_at` lifecycle purging (`ExamPrivacyLifecycleContributor`).
- **Controller & Privacy Hardening:** `CandidateProfileController` dual-mounts `/v1/candidate` and `/api/v1/candidate` with BOLA (`X-User-Id` mismatch -> `403 CANDIDATE_PROFILE_ACCESS_DENIED`). `ExamPrivacyLifecycleContributor` exports and deletes candidate profile, horizon history, and sanitized syllabus metadata without raw binary leaks.
- **Native Swift 6 iOS Experience:** Implements `CandidateProfileViewModel`, `CandidateGoalSetupView`, `SyllabusSnapToPlanSheet` (with `PhotosPicker` and `.fileImporter`), `ExamOfflineSQLiteStore` (`cached_candidate_profiles`, `cached_syllabus_uploads`, cross-user isolated purge), `RootView` session reset cleanup, and EN/TR `Localizable.xcstrings` strings free of stigmatizing copy.

## Verification Results Observed in Workspace Sandbox (`BypassSandbox: false`)
- **Backend Unit, WebMvc, SQL Seed Consistency, Privacy, StudyPlan Listener & Modulith Boundary Suite:**
  - Command: `mvn -f services/api/pom.xml -Dtest=CandidateProfileServiceTest,SyllabusIngestionServiceTest,CandidateProfileControllerWebMvcTest,V055MigrationSqlConsistencyTest,ExamPrivacyLifecycleContributorTest,ExamPlatformControllerTest,ExamBlueprintValidatorAndScoringTest,V054SeedSqlConsistencyTest,StudyPlanRebalancingServiceTest,RebalanceControllerTest,ModularityTest,ModulithArchitectureTest,AiProviderBoundaryTest,RepositoryHierarchyTest test`
  - Result: **40 / 40 passed, 0 failures, 0 errors, 0 skipped (`BUILD SUCCESS`).**
- **Schemas, Negative Mutations, OpenAPI Mirrors & Localization:**
  - `python3 scripts/quality/validate_json_schemas.py`: **PASS** (`Sprint 8.1..9.2 JSON schemas, negative mutations, OpenAPI 3.1 endpoints, and 305 EN/TR localizations`).
  - `cmp -s packages/contracts/openapi/public-api.yaml packages/openapi/verified-ai-core-api.yaml`: **0 bytes diff.**
  - `python3 scripts/quality/check_contracts.py` & `python3 scripts/quality/check_prompt_immutability.py --base-ref HEAD` & `python3 scripts/quality/docs_check.py`: **PASS.**
- **Swift 6 Compilation & Typecheck (`xcrun --sdk iphonesimulator swiftc`):**
  - Exam feature module + Core dependencies (`-enable-testing -strict-concurrency=complete -D DEBUG`), `Sprint91ExamCatalogAndTargetTests.swift`, `CandidateProfileViewModelTests.swift`, and `ExamCatalogUITests.swift`: **0 errors, 0 warnings.**
  - All 16 touched/created iOS source and test files (including `AppDependencies.swift`, `RootView.swift`, `HomePlaceholderView.swift`, `CoachHubView.swift`): `swiftc -parse` **0 errors.**
## Owner Host Acceptance Evidence (`bash scripts/quality/sprint_9_2_owner_acceptance.sh --full --require-docker`)
- **Host Run Timestamp:** `2026-11-15T13:04:27+03:00`
- **Xcode Test Result Bundle:** `/Users/burakcoskun/Library/Developer/Xcode/DerivedData/VerifiedAI-eawqmwzsewuauxeqouswvmojdyol/Logs/Test/Test-VerifiedAI-2026.11.15_13-04-27-+0300.xcresult`
- **Xcode Outcome:** `** TEST SUCCEEDED **` (`CandidateProfileViewModelTests`, `Sprint91ExamCatalogAndTargetTests`, & `ExamCatalogUITests`)
- **Stage 5 (Docker PostgreSQL 16 Testcontainers & Flyway V054/V055):** Passed (`ExamMigrationV055IntegrationTest` & `ExamPlatformPostgresIntegrationTest`)
- **Runner Outcome:** `==> [Sprint 9.2 Acceptance] ALL REQUESTED STAGES COMPLETED SUCCESSFULLY`
