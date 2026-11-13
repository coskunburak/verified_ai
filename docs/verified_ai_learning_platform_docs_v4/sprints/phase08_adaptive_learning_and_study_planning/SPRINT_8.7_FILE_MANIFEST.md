# Sprint 8.7 File Manifest

## Database Migrations
- `services/api/src/main/resources/db/migration/platform/V050__study_session_orchestration.sql`

## JSON Schemas & Fixtures
- `packages/schemas/study-session-state-v1.schema.json`
- `packages/schemas/study-session-start-request-v1.schema.json`
- `packages/schemas/study-session-start-response-v1.schema.json`
- `packages/schemas/study-session-checkpoint-request-v1.schema.json`
- `packages/schemas/study-session-checkpoint-response-v1.schema.json`
- `packages/schemas/study-session-complete-request-v1.schema.json`
- `packages/schemas/study-session-complete-response-v1.schema.json`
- `packages/schemas/fixtures/study-session-state-v1.valid.json`
- `packages/schemas/fixtures/study-session-start-request-v1.valid.json`
- `packages/schemas/fixtures/study-session-start-response-v1.valid.json`
- `packages/schemas/fixtures/study-session-checkpoint-request-v1.valid.json`
- `packages/schemas/fixtures/study-session-checkpoint-response-v1.valid.json`
- `packages/schemas/fixtures/study-session-complete-request-v1.valid.json`
- `packages/schemas/fixtures/study-session-complete-response-v1.valid.json`

## OpenAPI & Localization
- `packages/contracts/openapi/public-api.yaml`
- `packages/openapi/verified-ai-core-api.yaml`
- `apps/ios/VerifiedAI/Resources/Localizable.xcstrings`

## Backend (`services/api`)
- `services/api/src/main/java/com/verifiedai/sharedkernel/error/ApiErrorCode.java`
- `services/api/src/main/java/com/verifiedai/studyplan/rebalance/StudyPlanRebalancingService.java`
- `services/api/src/main/resources/application.yml`
- `services/api/src/main/resources/application-test.yml`
- `services/api/src/main/java/com/verifiedai/studyplan/session/StudySessionOrchestrationProperties.java`
- `services/api/src/main/java/com/verifiedai/studyplan/session/StudySessionConfiguration.java`
- `services/api/src/main/java/com/verifiedai/studyplan/session/StudySessionState.java`
- `services/api/src/main/java/com/verifiedai/studyplan/session/StudySessionSourceContext.java`
- `services/api/src/main/java/com/verifiedai/studyplan/session/StudySessionStepType.java`
- `services/api/src/main/java/com/verifiedai/studyplan/session/StudySessionStepStatus.java`
- `services/api/src/main/java/com/verifiedai/studyplan/session/StudySessionBlueprintStatus.java`
- `services/api/src/main/java/com/verifiedai/studyplan/session/StudySessionInterruptionReason.java`
- `services/api/src/main/java/com/verifiedai/studyplan/session/StudySessionNotFoundException.java`
- `services/api/src/main/java/com/verifiedai/studyplan/session/IllegalSessionStateTransitionException.java`
- `services/api/src/main/java/com/verifiedai/studyplan/session/StudySessionInvalidCheckpointException.java`
- `services/api/src/main/java/com/verifiedai/studyplan/session/StudySessionStepBlueprintItem.java`
- `services/api/src/main/java/com/verifiedai/studyplan/session/StudySessionResumeSnapshot.java`
- `services/api/src/main/java/com/verifiedai/studyplan/session/StudySessionStateDto.java`
- `services/api/src/main/java/com/verifiedai/studyplan/session/StudySessionStartRequest.java`
- `services/api/src/main/java/com/verifiedai/studyplan/session/StudySessionStartResponse.java`
- `services/api/src/main/java/com/verifiedai/studyplan/session/StudySessionCheckpointRequest.java`
- `services/api/src/main/java/com/verifiedai/studyplan/session/StudySessionCheckpointResponse.java`
- `services/api/src/main/java/com/verifiedai/studyplan/session/StudySessionPauseRequest.java`
- `services/api/src/main/java/com/verifiedai/studyplan/session/StudySessionResumeRequest.java`
- `services/api/src/main/java/com/verifiedai/studyplan/session/StudySessionCompleteRequest.java`
- `services/api/src/main/java/com/verifiedai/studyplan/session/StudySessionCompleteResponse.java`
- `services/api/src/main/java/com/verifiedai/studyplan/session/StudySessionRecord.java`
- `services/api/src/main/java/com/verifiedai/studyplan/session/StudySessionCheckpointRecord.java`
- `services/api/src/main/java/com/verifiedai/studyplan/session/StudySessionRepository.java`
- `services/api/src/main/java/com/verifiedai/studyplan/session/StudySessionJdbcRepository.java`
- `services/api/src/main/java/com/verifiedai/studyplan/session/StudySessionStateMachine.java`
- `services/api/src/main/java/com/verifiedai/studyplan/session/StudySessionOrchestrationService.java`
- `services/api/src/main/java/com/verifiedai/studyplan/session/StudySessionController.java`
- `services/api/src/main/java/com/verifiedai/studyplan/session/StudySessionPrivacyLifecycleContributor.java`
- `services/api/src/test/java/com/verifiedai/studyplan/session/InMemoryStudySessionRepository.java`
- `services/api/src/test/java/com/verifiedai/studyplan/session/StudySessionStateMachineTest.java`
- `services/api/src/test/java/com/verifiedai/studyplan/session/StudySessionOrchestrationServiceTest.java`
- `services/api/src/test/java/com/verifiedai/studyplan/session/StudySessionControllerIntegrationTest.java`
- `services/api/src/test/java/com/verifiedai/studyplan/rebalance/StudyPlanRebalancingServiceTest.java`

## Native iOS Client & Tests (`apps/ios`)
- `apps/ios/VerifiedAI/Core/Networking/HTTPMethod.swift`
- `apps/ios/VerifiedAI/Features/StudySession/StudySessionModels.swift`
- `apps/ios/VerifiedAI/Features/StudySession/StudySessionOfflineCheckpointStore.swift`
- `apps/ios/VerifiedAI/Features/StudySession/StudySessionCoordinator.swift`
- `apps/ios/VerifiedAI/Features/StudySession/StudySessionResumeView.swift`
- `apps/ios/VerifiedAITests/Sprint87StudySessionTests.swift`

## Quality & Acceptance Scripts
- `scripts/quality/validate_json_schemas.py`
- `scripts/quality/sprint_8_7_owner_acceptance.sh`

## Documentation & Manifests
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase08_adaptive_learning_and_study_planning/SPRINT_8.7_STUDY_SESSION_ORCHESTRATION_AND_RESUME_SEMANTICS.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase08_adaptive_learning_and_study_planning/SPRINT_8.7_EXECUTION_REPORT.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase08_adaptive_learning_and_study_planning/SPRINT_8.7_FILE_MANIFEST.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/00_SPRINT_MASTER_PLAN.md`
- `docs/verified_ai_learning_platform_docs_v4/DOCUMENTATION_MANIFEST.md`
