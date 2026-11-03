# Sprint 6.7 — exact worktree file manifest

Baseline: main at `4acc57ea8ee9b22536a8caa3f4f98ccc945a7855`. Inventory includes the prior manual implementation
and current full-product continuation; all changes remain uncommitted. Generated owner artifacts are not source
changes and are excluded. No tests, builds, validators, migrations or evaluations were executed by the agent.
V030–V032 are active forward migration source; staged V025 and earlier historical migrations retain their bytes.

Total: 148 source files (103 created, 45 modified).

## Backend and forward SQL

- Modified: `services/api/README.md`
- Modified: `services/api/pom.xml`
- Created: `services/api/src/main/java/com/verifiedai/attempt/api/AttemptExtractionController.java`
- Created: `services/api/src/main/java/com/verifiedai/attempt/api/AttemptLearningController.java`
- Created: `services/api/src/main/java/com/verifiedai/attempt/api/AttemptRequestLimitFilter.java`
- Created: `services/api/src/main/java/com/verifiedai/attempt/api/StudentAttemptController.java`
- Created: `services/api/src/main/java/com/verifiedai/attempt/application/AttemptLearningService.java`
- Created: `services/api/src/main/java/com/verifiedai/attempt/application/AttemptVerifierClient.java`
- Created: `services/api/src/main/java/com/verifiedai/attempt/application/StudentAttemptOrchestrator.java`
- Created: `services/api/src/main/java/com/verifiedai/attempt/domain/AttemptAnalysisPolicy.java`
- Created: `services/api/src/main/java/com/verifiedai/attempt/domain/AttemptAnalysisResult.java`
- Created: `services/api/src/main/java/com/verifiedai/attempt/domain/AttemptAnalysisVersion.java`
- Created: `services/api/src/main/java/com/verifiedai/attempt/domain/AttemptCaptureReview.java`
- Created: `services/api/src/main/java/com/verifiedai/attempt/domain/AttemptLearningEvidence.java`
- Created: `services/api/src/main/java/com/verifiedai/attempt/domain/AttemptOutcome.java`
- Created: `services/api/src/main/java/com/verifiedai/attempt/domain/AttemptSourceIdentity.java`
- Created: `services/api/src/main/java/com/verifiedai/attempt/domain/AttemptSourceType.java`
- Created: `services/api/src/main/java/com/verifiedai/attempt/domain/AttemptStatus.java`
- Created: `services/api/src/main/java/com/verifiedai/attempt/domain/CounterexampleEvidence.java`
- Created: `services/api/src/main/java/com/verifiedai/attempt/domain/LearnerMasteryPolicy.java`
- Created: `services/api/src/main/java/com/verifiedai/attempt/domain/MistakeCode.java`
- Created: `services/api/src/main/java/com/verifiedai/attempt/domain/StepVerdict.java`
- Created: `services/api/src/main/java/com/verifiedai/attempt/domain/StudentAttempt.java`
- Created: `services/api/src/main/java/com/verifiedai/attempt/domain/StudentAttemptStep.java`
- Created: `services/api/src/main/java/com/verifiedai/attempt/infrastructure/http/HttpAttemptVerifierClient.java`
- Created: `services/api/src/main/java/com/verifiedai/attempt/infrastructure/persistence/AttemptLearningRepository.java`
- Created: `services/api/src/main/java/com/verifiedai/attempt/infrastructure/persistence/StudentAttemptRepository.java`
- Created: `services/api/src/main/java/com/verifiedai/attempt/infrastructure/privacy/StudentAttemptPrivacyLifecycleContributor.java`
- Created: `services/api/src/main/java/com/verifiedai/problem/application/attempt/AttemptExtractionReader.java`
- Created: `services/api/src/main/java/com/verifiedai/problem/application/attempt/AttemptSourceDocument.java`
- Created: `services/api/src/main/java/com/verifiedai/problem/application/attempt/ManualStepNormalizer.java`
- Created: `services/api/src/main/java/com/verifiedai/problem/application/attempt/package-info.java`
- Created: `services/api/src/main/java/com/verifiedai/problem/application/canonicalization/AttemptMathParserV2.java`
- Created: `services/api/src/main/java/com/verifiedai/problem/application/canonicalization/CanonicalManualStepNormalizer.java`
- Modified: `services/api/src/main/java/com/verifiedai/problem/application/canonicalization/CanonicalProblemApplicationService.java`
- Modified: `services/api/src/main/java/com/verifiedai/problem/application/canonicalization/CanonicalProblemBuilder.java`
- Modified: `services/api/src/main/java/com/verifiedai/problem/application/solving/SolveSourceAccess.java`
- Modified: `services/api/src/main/java/com/verifiedai/sharedkernel/contracts/VersionedJsonContract.java`
- Modified: `services/api/src/main/java/com/verifiedai/sharedkernel/error/ApiErrorCode.java`
- Created: `services/api/src/main/java/com/verifiedai/tutoring/application/learning/TutorAssistanceReader.java`
- Created: `services/api/src/main/java/com/verifiedai/tutoring/application/learning/package-info.java`
- Modified: `services/api/src/main/resources/application.yml`
- Created: `services/api/src/main/resources/db/migration/platform/V030__reconcile_student_attempt_persistence.sql`
- Created: `services/api/src/main/resources/db/migration/platform/V031__attempt_learning_evidence.sql`
- Created: `services/api/src/main/resources/db/migration/platform/V032__canonical_real_set_source.sql`
- Created: `services/api/src/main/resources/db/planned/V025__create_student_attempt_and_step_verification_lifecycle.sql`
- Created: `services/api/src/test/java/com/verifiedai/attempt/api/StudentAttemptControllerTest.java`
- Created: `services/api/src/test/java/com/verifiedai/attempt/domain/AttemptAnalysisPolicyTest.java`
- Created: `services/api/src/test/java/com/verifiedai/attempt/domain/LearnerMasteryPolicyTest.java`
- Created: `services/api/src/test/java/com/verifiedai/attempt/infrastructure/AttemptVerifierClientTest.java`
- Created: `services/api/src/test/java/com/verifiedai/attempt/integration/StudentAttemptIntegrationTest.java`
- Created: `services/api/src/test/java/com/verifiedai/problem/application/canonicalization/AttemptMathParserV2Test.java`
- Created: `services/api/src/test/java/com/verifiedai/problem/application/canonicalization/CanonicalManualStepNormalizerTest.java`

## Verifier

- Modified: `services/math-verifier/README.md`
- Modified: `services/math-verifier/app/api/routes/verify.py`
- Created: `services/math-verifier/app/api/schemas/attempt.py`
- Created: `services/math-verifier/app/api/schemas/attempt_learning.py`
- Created: `services/math-verifier/app/api/schemas/attempt_v2.py`
- Created: `services/math-verifier/app/attempt_verification.py`
- Created: `services/math-verifier/app/attempt_verification_v2.py`
- Modified: `services/math-verifier/app/config.py`
- Created: `services/math-verifier/app/counterexample_engine.py`
- Modified: `services/math-verifier/app/observability/metrics.py`
- Modified: `services/math-verifier/app/policies/process_executor.py`
- Created: `services/math-verifier/app/verifiers/attempt_diagnosis.py`
- Created: `services/math-verifier/app/verifiers/attempt_diff.py`
- Created: `services/math-verifier/app/verifiers/attempt_learning.py`
- Created: `services/math-verifier/app/verifiers/canonical_v2.py`
- Created: `services/math-verifier/tests/test_attempt_v2_product.py`
- Created: `services/math-verifier/tests/test_attempt_verification_and_counterexample.py`

## iOS

- Modified: `apps/ios/README.md`
- Modified: `apps/ios/VerifiedAI.xcodeproj/project.pbxproj`
- Created by owner package resolution: `apps/ios/VerifiedAI.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved`
- Modified: `apps/ios/VerifiedAI/App/AppDependencies.swift`
- Modified: `apps/ios/VerifiedAI/App/RootView.swift`
- Created: `apps/ios/VerifiedAI/Features/AttemptAnalysis/Data/AttemptAnalysisAPI.swift`
- Created: `apps/ios/VerifiedAI/Features/AttemptAnalysis/Data/AttemptAnalysisPendingStore.swift`
- Created: `apps/ios/VerifiedAI/Features/AttemptAnalysis/Data/AttemptLearningAPI.swift`
- Created: `apps/ios/VerifiedAI/Features/AttemptAnalysis/Data/FixtureAttemptAnalysisAPI.swift`
- Created: `apps/ios/VerifiedAI/Features/AttemptAnalysis/Data/NativeAttemptExtraction.swift`
- Created: `apps/ios/VerifiedAI/Features/AttemptAnalysis/Domain/AttemptAnalysisModels.swift`
- Created: `apps/ios/VerifiedAI/Features/AttemptAnalysis/Presentation/AnalyzeMyWorkCaptureView.swift`
- Created: `apps/ios/VerifiedAI/Features/AttemptAnalysis/Presentation/AnalyzeMyWorkViewModel.swift`
- Created: `apps/ios/VerifiedAI/Features/AttemptAnalysis/Presentation/AttemptAnalysisResultsView.swift`
- Created: `apps/ios/VerifiedAI/Features/AttemptAnalysis/Presentation/AttemptLearningViewModel.swift`
- Created: `apps/ios/VerifiedAI/Features/AttemptAnalysis/Presentation/AttemptMathView.swift`
- Created: `apps/ios/VerifiedAI/Features/AttemptAnalysis/Presentation/AttemptStepEditorView.swift`
- Created: `apps/ios/VerifiedAI/Features/AttemptAnalysis/Presentation/AttemptStepReviewView.swift`
- Created: `apps/ios/VerifiedAI/Features/AttemptAnalysis/Presentation/CounterexampleProofCardView.swift`
- Created: `apps/ios/VerifiedAI/Features/AttemptAnalysis/Presentation/CounterfactualTwinSheetView.swift`
- Created: `apps/ios/VerifiedAI/Features/AttemptAnalysis/Presentation/FirstWrongStepDiffView.swift`
- Modified: `apps/ios/VerifiedAI/Features/ProblemCapture/Presentation/Capture/ProblemCaptureView.swift`
- Modified: `apps/ios/VerifiedAI/Features/ProblemCapture/Presentation/Capture/ProblemCaptureViewModel.swift`
- Modified: `apps/ios/VerifiedAI/Features/ProblemSolving/Presentation/Components/MathematicalExpressionView.swift`
- Modified: `apps/ios/VerifiedAI/Features/ProblemSolving/Presentation/Solution/PrimarySolveView.swift`
- Modified: `apps/ios/VerifiedAI/Features/ProblemSolving/Presentation/Solution/PrimarySolveViewModel.swift`
- Modified: `apps/ios/VerifiedAI/Resources/Localizable.xcstrings`
- Created: `apps/ios/VerifiedAITests/AttemptAnalysis/AttemptAnalysisTests.swift`
- Created: `apps/ios/VerifiedAIUITests/AttemptAnalysisUITests.swift`

## Contracts and schemas

- Modified: `packages/contracts/README.md`
- Modified: `packages/contracts/openapi/internal-math-verifier.yaml`
- Modified: `packages/contracts/openapi/public-api.yaml`
- Modified: `packages/schemas/README.md`
- Created: `packages/schemas/attempt-analysis-v1.schema.json`
- Created: `packages/schemas/attempt-analysis-v2.schema.json`
- Created: `packages/schemas/attempt-context-v1.schema.json`
- Created: `packages/schemas/attempt-context-v2-response.schema.json`
- Created: `packages/schemas/attempt-extraction-v1.schema.json`
- Created: `packages/schemas/attempt-learning-result-v1.schema.json`
- Created: `packages/schemas/attempt-learning-v1.schema.json`
- Created: `packages/schemas/attempt-submission-v1.schema.json`
- Created: `packages/schemas/attempt-submission-v2.schema.json`
- Created: `packages/schemas/attempt-taxonomy-v1.json`
- Created: `packages/schemas/attempt-taxonomy-v2.json`
- Created: `packages/schemas/attempt-verification-v1.schema.json`
- Created: `packages/schemas/attempt-verification-v2.schema.json`
- Created: `packages/schemas/canonical-problem-v3.schema.json`
- Created: `packages/schemas/counterfactual-twin-v1.schema.json`
- Created: `packages/schemas/mistake-passport-v1.schema.json`
- Created: `packages/schemas/step-diff-v1.schema.json`
- Created: `packages/schemas/student-attempt-response.schema.json`
- Created: `packages/schemas/student-attempt-v1.schema.json`

## Owner quality and synthetic fixtures

- Modified: `evaluations/README.md`
- Created: `evaluations/golden-datasets/attempts/manual-polynomial-v1.json`
- Created: `scripts/quality/check_attempt_contracts.py`
- Created: `scripts/quality/evaluate_attempt_golden.py`
- Created: `scripts/quality/sprint_6_7_evidence.py`
- Created: `scripts/quality/sprint_6_7_owner_acceptance.sh`

## Documentation

- Modified: `README.md`
- Modified: `docs/verified_ai_learning_platform_docs_v4/00_MASTER_INDEX.md`
- Modified: `docs/verified_ai_learning_platform_docs_v4/DOCUMENTATION_MANIFEST.md`
- Modified: `docs/verified_ai_learning_platform_docs_v4/architecture/14_API_DESIGN_AND_CONTRACTS.md`
- Modified: `docs/verified_ai_learning_platform_docs_v4/backend/20_BACKEND_MODULE_CONTRACTS.md`
- Modified: `docs/verified_ai_learning_platform_docs_v4/data/22_POSTGRESQL_DATA_MODEL.md`
- Modified: `docs/verified_ai_learning_platform_docs_v4/data/23_DATA_LIFECYCLE_RETENTION_AND_AUDIT.md`
- Modified: `docs/verified_ai_learning_platform_docs_v4/domain/10_DOMAIN_INVARIANTS_AND_BUSINESS_RULES.md`
- Modified: `docs/verified_ai_learning_platform_docs_v4/domain/47_MATHEMATICAL_CANONICAL_REPRESENTATION.md`
- Modified: `docs/verified_ai_learning_platform_docs_v4/ios/15_IOS_ARCHITECTURE_AND_FILE_HIERARCHY.md`
- Modified: `docs/verified_ai_learning_platform_docs_v4/ios/17_IOS_NETWORKING_PERSISTENCE_AND_OFFLINE.md`
- Modified: `docs/verified_ai_learning_platform_docs_v4/learning/30_MISTAKE_INTELLIGENCE.md`
- Modified: `docs/verified_ai_learning_platform_docs_v4/learning/34_TUTORING_AND_PEDAGOGICAL_BEHAVIOR.md`
- Modified: `docs/verified_ai_learning_platform_docs_v4/learning/79_PEDAGOGICAL_DIAGNOSIS_HANDWRITING_UX_AND_RETENTION_ENGINE.md`
- Modified: `docs/verified_ai_learning_platform_docs_v4/quality/53_DEPENDENCY_AND_LIBRARY_GOVERNANCE.md`
- Modified: `docs/verified_ai_learning_platform_docs_v4/quality/69_REQUIREMENTS_TRACEABILITY_MATRIX.md`
- Modified: `docs/verified_ai_learning_platform_docs_v4/sprints/phase06_solution_experience_and_tutoring/PHASE_06_SOLUTION_EXPERIENCE_AND_TUTORING.md`
- Created: `docs/verified_ai_learning_platform_docs_v4/sprints/phase06_solution_experience_and_tutoring/SPRINT_6.7_EXECUTION_REPORT.md`
- Created: `docs/verified_ai_learning_platform_docs_v4/sprints/phase06_solution_experience_and_tutoring/SPRINT_6.7_FILE_MANIFEST.md`
- Created: `docs/verified_ai_learning_platform_docs_v4/sprints/phase06_solution_experience_and_tutoring/SPRINT_6.7_IMPLEMENTATION_MAP.md`

