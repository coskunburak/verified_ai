# Sprint 7.7 file manifest

Paths are repository-relative. No files were deleted.

| Change | File | Purpose |
|---|---|---|
| Modified | `apps/ios/VerifiedAI/App/RootView.swift` | Inject the authenticated mastery transport into ProblemReview. |
| Modified | `apps/ios/VerifiedAI/Features/AttemptAnalysis/Data/AttemptAnalysisAPI.swift` | Request temporal representation 2 on the existing mastery route. |
| Modified | `apps/ios/VerifiedAI/Features/AttemptAnalysis/Data/FixtureAttemptAnalysisAPI.swift` | Provide a deterministic temporal UI example with an outcome-to-skill link. |
| Modified | `apps/ios/VerifiedAI/Features/AttemptAnalysis/Domain/AttemptAnalysisModels.swift` | Decode optional server estimates and validate temporal field consistency. |
| Modified | `apps/ios/VerifiedAI/Features/AttemptAnalysis/Presentation/AnalyzeMyWorkCaptureView.swift` | Display the shared accessible mastery and recall rings. |
| Modified | `apps/ios/VerifiedAI/Features/AttemptAnalysis/Presentation/AnalyzeMyWorkViewModel.swift` | Bind the returned skill to the selected outcome and fence stale responses. |
| Modified | `apps/ios/VerifiedAI/Features/ProblemReview/Presentation/Review/ProblemReviewView.swift` | Present account skill nodes on the existing details tab with retry. |
| Modified | `apps/ios/VerifiedAI/Features/ProblemReview/Presentation/Review/ProblemReviewViewModel.swift` | Own request, cancellation, failure and reset state for review skill detail. |
| Modified | `apps/ios/VerifiedAI/Resources/Localizable.xcstrings` | English and Turkish labels for mastery, confidence, recall and reassessment. |
| Modified | `apps/ios/VerifiedAI.xcodeproj/project.pbxproj` | Bundle the shared mastery fixture for Swift contract tests. |
| Modified | `apps/ios/VerifiedAITests/AttemptAnalysis/AttemptAnalysisTests.swift` | Test shared fixture decoding, legacy compatibility and review recovery. |
| Modified | `apps/ios/VerifiedAIUITests/AttemptAnalysisUITests.swift` | Assert English and Turkish accessibility output on the dual-ring view. |
| Modified | `docs/verified_ai_learning_platform_docs_v4/00_MASTER_INDEX.md` | Index the current 7.7 source handoff. |
| Modified | `docs/verified_ai_learning_platform_docs_v4/DOCUMENTATION_MANIFEST.md` | Synchronize the documentation inventory and byte/line counts. |
| Modified | `docs/verified_ai_learning_platform_docs_v4/architecture/90_LEARNING_OS_DOMAIN_CONTRACTS_AND_PLATFORM_INTEGRATION.md` | Reconcile CNT-LOS-04 with the implemented route and temporal representation. |
| Modified | `docs/verified_ai_learning_platform_docs_v4/sprints/00_SPRINT_MASTER_PLAN.md` | Record the owner-confirmed dependency and active 7.7 source status. |
| Modified | `docs/verified_ai_learning_platform_docs_v4/sprints/phase07_attempt_mistake_and_mastery_intelligence/PHASE_07_ATTEMPT_MISTAKE_AND_MASTERY_INTELLIGENCE.md` | Record the current phase handoff and 7.8 gate. |
| Added | `docs/verified_ai_learning_platform_docs_v4/sprints/phase07_attempt_mistake_and_mastery_intelligence/SPRINT_7.7_EXECUTION_REPORT.md` | Record baseline, decisions, rollout, verification status and deferred work. |
| Added | `docs/verified_ai_learning_platform_docs_v4/sprints/phase07_attempt_mistake_and_mastery_intelligence/SPRINT_7.7_FILE_MANIFEST.md` | Inventory changed paths and purposes. |
| Added | `docs/verified_ai_learning_platform_docs_v4/sprints/phase07_attempt_mistake_and_mastery_intelligence/SPRINT_7.7_IMPLEMENTATION_MAP.md` | Map requirements to components and owner tests. |
| Modified | `docs/verified_ai_learning_platform_docs_v4/sprints/phase07_attempt_mistake_and_mastery_intelligence/SPRINT_7.7_MASTERY_CONFIDENCE_HISTORY_DECAY_AND_REASSESSMENT.md` | Update current status, migration names and representation version. |
| Modified | `packages/contracts/openapi/public-api.yaml` | Describe representation negotiation, optional response fields and due reasons. |
| Modified | `packages/schemas/fixtures/mastery-skills-v1.json` | Provide the canonical synthetic temporal response. |
| Modified | `packages/schemas/mastery-skills-v1.schema.json` | Constrain the temporal field group and supported outcome skill references. |
| Modified | `scripts/quality/check_mastery_contracts.py` | Cover legacy, malformed and current temporal representations. |
| Added | `scripts/quality/sprint_7_7_owner_acceptance.sh` | Run the scoped synthetic owner suite with fresh evidence and failure propagation. |
| Modified | `services/api/src/main/java/com/verifiedai/attempt/application/AttemptMasteryEvidenceSource.java` | Adapt existing lineage-checked twin evidence to same-hazard checks. |
| Modified | `services/api/src/main/java/com/verifiedai/attempt/infrastructure/persistence/AttemptLearningRepository.java` | Publish completion inside the original transaction. |
| Modified | `services/api/src/main/java/com/verifiedai/mastery/EvidenceStratifiedMasteryPolicy.java` | Delegate temporal math while retaining the V038 posterior and confidence rules. |
| Added | `services/api/src/main/java/com/verifiedai/mastery/HalfLifeRetentionPolicy.java` | Implement bounded elapsed-time retention and half-life updates. |
| Modified | `services/api/src/main/java/com/verifiedai/mastery/MasteryApplicationService.java` | Separate queries from writes and publish temporal decisions. |
| Added | `services/api/src/main/java/com/verifiedai/mastery/SkillReassessmentTriggerService.java` | Define the threshold and delayed-check reasons. |
| Modified | `services/api/src/main/java/com/verifiedai/mastery/api/MasteryController.java` | Accept the optional representation selector on the authenticated route. |
| Modified | `services/api/src/main/java/com/verifiedai/mistake/LearnerMistakeDnaService.java` | Compute effective DNA state, due skills, and persist resolution on mutation. |
| Modified | `services/api/src/main/java/com/verifiedai/mistake/MistakeRecurrenceState.java` | Identify observation-only states when emitting projection metrics. |
| Added | `services/api/src/main/java/com/verifiedai/mistake/MistakeRetentionResolutionListener.java` | Consume completed twins and replay exact-hazard evidence over UTC time. |
| Modified | `services/api/src/main/java/com/verifiedai/mistake/infrastructure/MistakeDnaRepository.java` | Record, export and delete owner-specific retention audit history. |
| Modified | `services/api/src/main/resources/application.yml` | Add the default-off decay/reassessment feature flag. |
| Added | `services/api/src/main/resources/db/migration/platform/V039__mistake_retention_transitions.sql` | Create immutable source-linked resolution history and cache invalidation. |
| Modified | `services/api/src/test/java/com/verifiedai/attempt/api/MasteryControllerTest.java` | Exercise temporal auth, version selection, and validation errors. |
| Modified | `services/api/src/test/java/com/verifiedai/attempt/integration/EvidenceStratifiedMasteryIntegrationTest.java` | Exercise the accepted-event path, history, atomic rollback, concurrency, disputes and read purity. |
| Added | `services/api/src/test/java/com/verifiedai/mastery/HalfLifeRetentionCalculatorTest.java` | Cover extreme times, invalid numbers, bounds and time-independent historical mastery. |
| Added | `services/api/src/test/java/com/verifiedai/mastery/MasteryDecayReassessmentTest.java` | Test time-dependent due status and reason precedence. |
| Modified | `services/api/src/test/java/com/verifiedai/mastery/MasteryMigrationIntegrationTest.java` | Cover upgrading V038 to V039 and preserving legacy rows. |
| Added | `services/api/src/test/java/com/verifiedai/mistake/MistakeRetentionResolutionTest.java` | Exercise early/exact seven-day checks, recurrence, independence and relapse. |
