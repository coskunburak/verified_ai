# Sprint 9.7 File Manifest

Date: 2026-11-17. Status: COMPLETED — OWNER HOST ACCEPTANCE PASSED; see [execution report](SPRINT_9.7_EXECUTION_REPORT.md).

Tracked deliverables and modified integration files (generated build outputs are excluded):

- `apps/ios/VerifiedAI.xcodeproj/project.pbxproj`
- `apps/ios/VerifiedAI/Core/Networking/APIClient.swift`
- `apps/ios/VerifiedAI/Core/Networking/HTTPRequest.swift`
- `apps/ios/VerifiedAI/Features/Exam/Data/ExamAPIService.swift`
- `apps/ios/VerifiedAI/Features/Exam/Data/ExamOfflineSQLiteStore.swift`
- `apps/ios/VerifiedAI/Features/Exam/Domain/ExamModels.swift`
- `apps/ios/VerifiedAI/Features/Exam/Presentation/ExamScoreSummaryView.swift`
- `apps/ios/VerifiedAI/Features/Exam/Presentation/ExamScoreSummaryViewModel.swift`
- `apps/ios/VerifiedAI/Features/Exam/Presentation/PostExamAttributionView.swift`
- `apps/ios/VerifiedAI/Features/Exam/Presentation/PostExamAttributionViewModel.swift`
- `apps/ios/VerifiedAI/Features/Exam/Presentation/PostExamRemediationDestination.swift`
- `apps/ios/VerifiedAITests/Exam/PostExamAttributionViewModelTests.swift`
- `docs/verified_ai_learning_platform_docs_v4/DOCUMENTATION_MANIFEST.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/00_SPRINT_MASTER_PLAN.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase09_exam_and_assessment_platform/PHASE_09_EXAM_AND_ASSESSMENT_PLATFORM.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase09_exam_and_assessment_platform/PHASE_09_TYT_AYT_BRIDGE_AND_REMAINING_SPRINTS_PLAN.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase09_exam_and_assessment_platform/SPRINT_9.7_EXECUTION_REPORT.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase09_exam_and_assessment_platform/SPRINT_9.7_FILE_MANIFEST.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase09_exam_and_assessment_platform/SPRINT_9.7_WEAK_AREA_PRIORITIZATION_AFTER_ASSESSMENTS.md`
- `evaluations/exports/yks-tr-phase13-seed/manifest.json`
- `evaluations/exports/yks-tr-phase13-seed/qwen_tyt_ayt_baseline_summary.json`
- `evaluations/exports/yks-tr-phase13-seed/seed_records.jsonl`
- `evaluations/runners/export_yks_phase13_clean_seed.py`
- `evaluations/runners/summarize_qwen_evaluation.py`
- `evaluations/tests/test_yks_phase13_clean_seed_export.py`
- `packages/contracts/openapi/public-api.yaml`
- `packages/openapi/verified-ai-core-api.yaml`
- `packages/schemas/exam/v1/exam-attribution-report.schema.json`
- `packages/schemas/exam/v1/exam-study-rebalance-request.schema.json`
- `packages/schemas/exam/v1/exam-study-rebalance-response.schema.json`
- `packages/schemas/exam/v1/examples/invalid/exam-attribution-report.missing-field.json`
- `packages/schemas/exam/v1/examples/invalid/exam-attribution-report.privacy-or-horizon.json`
- `packages/schemas/exam/v1/examples/invalid/exam-attribution-report.unknown-field.json`
- `packages/schemas/exam/v1/examples/invalid/exam-study-rebalance-request.missing-field.json`
- `packages/schemas/exam/v1/examples/invalid/exam-study-rebalance-request.privacy-or-horizon.json`
- `packages/schemas/exam/v1/examples/invalid/exam-study-rebalance-request.unknown-field.json`
- `packages/schemas/exam/v1/examples/invalid/exam-study-rebalance-response.missing-field.json`
- `packages/schemas/exam/v1/examples/invalid/exam-study-rebalance-response.privacy-or-horizon.json`
- `packages/schemas/exam/v1/examples/invalid/exam-study-rebalance-response.unknown-field.json`
- `packages/schemas/exam/v1/examples/valid/exam-attribution-report.json`
- `packages/schemas/exam/v1/examples/valid/exam-study-rebalance-request.json`
- `packages/schemas/exam/v1/examples/valid/exam-study-rebalance-response.json`
- `scripts/quality/check_contracts.py`
- `scripts/quality/sprint_9_7_owner_acceptance.sh`
- `scripts/quality/sprint_9_7_swift_check.sh`
- `scripts/quality/validate_json_schemas.py`
- `scripts/quality/validate_sprint_9_7_contracts.py`
- `services/api/src/main/java/com/verifiedai/exam/api/ExamAttributionController.java`
- `services/api/src/main/java/com/verifiedai/exam/application/ExamAttributionReportService.java`
- `services/api/src/main/java/com/verifiedai/exam/application/ExamNetGainRoiSimulatorService.java`
- `services/api/src/main/java/com/verifiedai/exam/application/ExamPostAssessmentAttributionService.java`
- `services/api/src/main/java/com/verifiedai/exam/config/ExamProperties.java`
- `services/api/src/main/java/com/verifiedai/exam/domain/ExamAttributionModels.java`
- `services/api/src/main/java/com/verifiedai/exam/events/ExamAttributionGeneratedEvent.java`
- `services/api/src/main/java/com/verifiedai/exam/events/ExamStudyRebalancePort.java`
- `services/api/src/main/java/com/verifiedai/exam/events/ExamStudyRebalanceRequestedEvent.java`
- `services/api/src/main/java/com/verifiedai/exam/infrastructure/persistence/ExamAttributionRepository.java`
- `services/api/src/main/java/com/verifiedai/exam/infrastructure/privacy/ExamPrivacyLifecycleContributor.java`
- `services/api/src/main/java/com/verifiedai/studyplan/rebalance/ExamAttributionStudyPlanListener.java`
- `services/api/src/main/resources/application.yml`
- `services/api/src/main/resources/db/migration/platform/V061__sprint_9_7_post_assessment_attribution.sql`
- `services/api/src/test/java/com/verifiedai/exam/application/ExamNetGainRoiSimulatorServiceTest.java`
- `services/api/src/test/java/com/verifiedai/exam/application/ExamPostAssessmentAttributionServiceTest.java`
- `services/api/src/test/java/com/verifiedai/exam/integration/ExamAttributionIntegrationTest.java`
- `services/api/src/test/java/com/verifiedai/exam/persistence/V061MigrationSqlConsistencyTest.java`
- `services/api/src/test/java/com/verifiedai/studyplan/rebalance/ExamAttributionStudyPlanListenerTest.java`

No V062–V064 migrations or later-sprint implementation files were added. Native remediation reuses existing Practice and Micro-Lesson destinations; their implementation files are unchanged.
