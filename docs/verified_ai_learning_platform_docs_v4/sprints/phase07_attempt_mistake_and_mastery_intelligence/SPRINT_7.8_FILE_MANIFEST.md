# Sprint 7.8 file manifest

Paths are repository-relative. No files were deleted.

| Change | File | Purpose |
|---|---|---|
| Added | `services/api/src/main/resources/db/migration/platform/V040__sprint_7_8_knowledge_graph_and_twin_remediation.sql` | Flyway DDL for consecutive failures, 60s locked twin challenge tables, append-only triggers, and training ineligible constraints. |
| Modified | `services/api/src/main/java/com/verifiedai/configuration/SecurityConfiguration.java` | Authenticate `/v1/**` under `@AuthenticationPrincipal`. |
| Added | `services/api/src/main/java/com/verifiedai/security/VerifiedJwtPrincipal.java` | Authenticated tenant/user security principal DTO. |
| Added | `services/api/src/main/java/com/verifiedai/security/VerifiedJwtPrincipalArgumentResolver.java` | Argument resolver for `@AuthenticationPrincipal VerifiedJwtPrincipal`. |
| Added | `services/api/src/main/java/com/verifiedai/security/VerifiedSecurityWebMvcConfigurer.java` | WebMvc configurer registering the JWT argument resolver. |
| Modified | `services/api/src/main/java/com/verifiedai/sharedkernel/error/ApiErrorCode.java` | Added error codes `RESOURCE_NOT_FOUND`, `TWIN_SOLUTION_LOCKED`, `ACTIVE_TWIN_ALREADY_EXISTS`, `UNPROCESSABLE_DOMAIN_RULE`. |
| Modified | `services/api/src/main/java/com/verifiedai/sharedkernel/error/ApiProblemException.java` | Un-finalized to permit `TwinSolutionLockedException` subclassing. |
| Modified | `services/api/src/main/java/com/verifiedai/sharedkernel/error/ApiExceptionHandler.java` | Made public for test suite visibility. |
| Added | `services/api/src/main/java/com/verifiedai/curriculum/api/CurriculumDagSnapshot.java` | Immutable DAG snapshot DTO for curriculum topology queries. |
| Added | `services/api/src/main/java/com/verifiedai/curriculum/api/CurriculumGraphQueryPort.java` | Public read port for curriculum graph queries. |
| Added | `services/api/src/main/java/com/verifiedai/curriculum/application/CurriculumGraphQueryAdapter.java` | Adapter providing curriculum DAG topology. |
| Added | `services/api/src/main/java/com/verifiedai/mastery/AttemptQueryPort.java` | Port for querying verified attempts and diagnosed mistakes without circular dependencies. |
| Added | `services/api/src/main/java/com/verifiedai/mastery/VerifiedAttemptSnapshot.java` | Immutable snapshot of verified attempt ownership and mistake classification. |
| Added | `services/api/src/main/java/com/verifiedai/attempt/application/AttemptQueryAdapter.java` | Adapter implementing `AttemptQueryPort` under BOLA isolation. |
| Modified | `services/api/src/main/java/com/verifiedai/mastery/package-info.java` | Declared Spring Modulith allowed module dependencies. |
| Added | `services/api/src/main/java/com/verifiedai/mastery/domain/CounterfactualTwinDrillEntity.java` | JPA entity for 60s locked counterfactual twin challenge drills. |
| Added | `services/api/src/main/java/com/verifiedai/mastery/domain/CounterfactualTwinSubmissionEntity.java` | JPA entity for immutable twin challenge submissions. |
| Added | `services/api/src/main/java/com/verifiedai/mastery/domain/LearnerSkillMasteryEntity.java` | JPA entity mapping `learner_skill_mastery` table with consecutive failures and direct practice counts. |
| Added | `services/api/src/main/java/com/verifiedai/mastery/domain/LearnerSkillMasteryId.java` | Composite primary key `(tenant_id, user_id, curriculum_code, skill_code)`. |
| Added | `services/api/src/main/java/com/verifiedai/mastery/domain/CounterfactualTwinRepository.java` | Spring Data repository for twin drills. |
| Added | `services/api/src/main/java/com/verifiedai/mastery/domain/CounterfactualTwinSubmissionRepository.java` | Spring Data repository for twin submissions. |
| Added | `services/api/src/main/java/com/verifiedai/mastery/domain/LearnerSkillMasteryRepository.java` | Spring Data repository for composite-keyed skill masteries. |
| Added | `services/api/src/main/java/com/verifiedai/mastery/domain/MasteryStateLockStore.java` | Interface for distributed or in-memory learner locks. |
| Added | `services/api/src/main/java/com/verifiedai/mastery/domain/InMemoryMasteryStateLockStore.java` | High-concurrency in-memory lock store implementation. |
| Added | `services/api/src/main/java/com/verifiedai/mastery/domain/OwnerKey.java` | Lock owner composite key record. |
| Added | `services/api/src/main/java/com/verifiedai/mastery/domain/LockHandle.java` | AutoCloseable lock handle. |
| Added | `services/api/src/main/java/com/verifiedai/mastery/application/KnowledgeGraphProjectionService.java` | Read-pure service projecting DAG with conservative prerequisite propagation and bottleneck detection. |
| Added | `services/api/src/main/java/com/verifiedai/mastery/application/CounterfactualTwinChallengeService.java` | Closed-loop twin remediation service enforcing 60s lock, 423 status, and unassisted credit. |
| Added | `services/api/src/main/java/com/verifiedai/mastery/application/MathVerifierTwinPort.java` | Math verifier client port for twin generation and verification. |
| Added | `services/api/src/main/java/com/verifiedai/mastery/application/LocalMathVerifierTwinAdapter.java` | Deterministic local twin adapter. |
| Added | `services/api/src/main/java/com/verifiedai/mastery/application/TwinSolutionLockedException.java` | HTTP 423 Locked problem exception. |
| Added | `services/api/src/main/java/com/verifiedai/mastery/application/ResourceNotFoundOrForbiddenException.java` | Uniform HTTP 404 Problem exception. |
| Added | `services/api/src/main/java/com/verifiedai/mastery/application/UnprocessableDomainRuleException.java` | HTTP 422 Problem exception. |
| Added | `services/api/src/main/java/com/verifiedai/mastery/application/ConflictDomainException.java` | HTTP 409 Problem exception. |
| Added | `services/api/src/main/java/com/verifiedai/mastery/web/MasteryGraphController.java` | REST Controller for `GET /v1/mastery/graph`. |
| Added | `services/api/src/main/java/com/verifiedai/mastery/web/CounterfactualTwinController.java` | REST Controller for `/v1/attempts/{attemptId}/twins` and `/v1/twins/{twinId}/submit`. |
| Added | `services/api/src/test/java/com/verifiedai/mastery/KnowledgeGraphProjectionServiceTest.java` | Unit tests for propagation, ancestor capping, and bottleneck detection. |
| Added | `services/api/src/test/java/com/verifiedai/mastery/CounterfactualTwinChallengeServiceTest.java` | Unit tests for 60s lock, unassisted credit, and early forfeit. |
| Added | `services/api/src/test/java/com/verifiedai/mastery/Sprint78KnowledgeGraphAndTwinIT.java` | Full integration tests verifying HTTP endpoints, headers, and BOLA isolation. |
| Added | `services/api/src/test/java/com/verifiedai/architecture/ModulithArchitectureTest.java` | Spring Modulith architectural boundaries test. |
| Added | `services/math-verifier/app/twin_synthesizer.py` | Python HMAC-seeded SymPy deterministic twin synthesizer. |
| Added | `services/math-verifier/tests/test_twin_synthesis_v78.py` | Pytest suite for deterministic reproducibility and isomorphic divergence. |
| Added | `packages/schemas/mastery/skill-graph-v1.schema.json` | JSON Schema Draft 2020-12 for skill graph projection. |
| Added | `packages/schemas/mastery/verified-twin-v1.schema.json` | JSON Schema Draft 2020-12 for verified twin challenges. |
| Added | `packages/schemas/fixtures/skill-graph-v1.json` | Synthetic canonical fixture for skill graph projection. |
| Added | `packages/schemas/fixtures/verified-twin-v1.json` | Synthetic canonical fixture for verified twin challenges. |
| Modified | `packages/contracts/openapi/public-api.yaml` | Added `/v1/mastery/graph`, `/v1/attempts/{attemptId}/twins`, `/v1/twins/{twinId}/submit` endpoints. |
| Added | `apps/ios/VerifiedAI/Features/MasteryGraph/Domain/SkillGraphModels.swift` | Swift domain models and port for skill graph explorer. |
| Added | `apps/ios/VerifiedAI/Features/MasteryGraph/Presentation/MasteryGraphViewModel.swift` | `@MainActor @Observable` view model with task fencing for graph projection. |
| Added | `apps/ios/VerifiedAI/Features/MasteryGraph/Presentation/MasteryGraphExplorerView.swift` | Screen 7 SwiftUI view for directed graph and bottleneck indicators. |
| Added | `apps/ios/VerifiedAI/Features/TwinChallenge/Domain/VerifiedTwinModels.swift` | Swift domain models and port for twin challenges. |
| Added | `apps/ios/VerifiedAI/Features/TwinChallenge/Presentation/TwinChallengeViewModel.swift` | `@MainActor @Observable` view model with monotonic 60s lock timer. |
| Added | `apps/ios/VerifiedAI/Features/TwinChallenge/Presentation/ProveItSoloTwinView.swift` | Screen 5 SwiftUI view for Prove It Solo challenge. |
| Added | `apps/ios/VerifiedAITests/Sprint78MasteryAndTwinTests.swift` | XCTest unit test suite for iOS view models and timers. |
| Modified | `apps/ios/VerifiedAI/Resources/Localizable.xcstrings` | Added 14 localized keys in English and Turkish. |
| Modified | `apps/ios/VerifiedAI.xcodeproj/project.pbxproj` | Registered new Swift files and fixtures in Xcode project. |
| Added | `mvnw` | Repository root executable Maven wrapper. |
| Added | `scripts/quality/validate_json_schemas.py` | Gate validator for schemas, negative mutations, OpenAPI, and strings. |
| Added | `scripts/quality/sprint_7_8_owner_acceptance.sh` | 7-stage deterministic owner acceptance suite. |
| Added | `docs/verified_ai_learning_platform_docs_v4/sprints/phase07_attempt_mistake_and_mastery_intelligence/SPRINT_7.8_EXECUTION_REPORT.md` | Formal execution report. |
| Added | `docs/verified_ai_learning_platform_docs_v4/sprints/phase07_attempt_mistake_and_mastery_intelligence/SPRINT_7.8_FILE_MANIFEST.md` | Inventory of all modified and created files. |
