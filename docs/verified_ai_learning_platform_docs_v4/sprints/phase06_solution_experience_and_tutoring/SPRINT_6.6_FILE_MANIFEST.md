# Sprint 6.6 — file manifest

Repository root: `/Users/burakcoskun/Desktop/ai_verification_tutor`. Listed paths are exact and relative to that root.
Baseline: main at c9f1b5837ed1584dded8afeafe91fe07c1acf118, initially clean.
Bounded implementation/reporting complete for owner Git review. Evidence: TARGETED_RECONCILIATION_PASSED,
run 20261102-214452-cv4eye9f. Full current-source acceptance pending; production not authorized.
No commit/push/merge/staging performed by the agent. Subsequent preparation changes are Markdown only.
Generated execution evidence remains ignored under .generated/ and is not part of source publication.

## Backend

- Created: `services/api/src/main/java/com/verifiedai/problem/application/tutoring/TutorHintSource.java`
- Modified: `services/api/src/main/java/com/verifiedai/problem/application/tutoring/TutorSourceAccess.java`
- Modified: `services/api/src/main/java/com/verifiedai/sharedkernel/contracts/VersionedJsonContract.java`
- Modified: `services/api/src/main/java/com/verifiedai/sharedkernel/error/ApiErrorCode.java`
- Modified: `services/api/src/main/java/com/verifiedai/tutoring/api/TutorRequestLimitFilter.java`
- Modified: `services/api/src/main/java/com/verifiedai/tutoring/api/TutorSessionController.java`
- Modified: `services/api/src/main/java/com/verifiedai/tutoring/application/TutorSessionLifecycleContributor.java`
- Modified: `services/api/src/main/java/com/verifiedai/tutoring/application/TutorSessionService.java`
- Created: `services/api/src/main/java/com/verifiedai/tutoring/domain/HintPolicy.java`
- Modified: `services/api/src/main/java/com/verifiedai/tutoring/domain/SocraticPolicy.java`
- Created: `services/api/src/main/java/com/verifiedai/tutoring/domain/TutorHint.java`
- Modified: `services/api/src/main/java/com/verifiedai/tutoring/infrastructure/persistence/TutorStore.java`
- Modified: `services/api/src/main/resources/application.yml`

## iOS

- Modified: `apps/ios/VerifiedAI/Features/Tutor/Data/FixtureTutorAPI.swift`
- Modified: `apps/ios/VerifiedAI/Features/Tutor/Data/TutorAPI.swift`
- Modified: `apps/ios/VerifiedAI/Features/Tutor/Data/TutorPendingStore.swift`
- Modified: `apps/ios/VerifiedAI/Features/Tutor/Domain/TutorSession.swift`
- Modified: `apps/ios/VerifiedAI/Features/Tutor/Presentation/TutorView.swift`
- Modified: `apps/ios/VerifiedAI/Features/Tutor/Presentation/TutorViewModel.swift`
- Modified: `apps/ios/VerifiedAI/Resources/Localizable.xcstrings`

## Contracts

- Modified: `packages/contracts/openapi/public-api.yaml`
- Created: `packages/schemas/tutor-hint-v1.schema.json`

## Migrations

- Created: `services/api/src/main/resources/db/migration/platform/V029__create_progressive_tutor_hints.sql`

## Tests and evaluation

- Modified: `apps/ios/VerifiedAITests/Tutor/TutorTests.swift`
- Modified: `apps/ios/VerifiedAIUITests/TutorUITests.swift`
- Created: `evaluations/golden-datasets/tutoring/progressive-hints-v1.json`
- Modified: `services/api/src/test/java/com/verifiedai/tutoring/api/TutorSessionControllerTest.java`
- Created: `services/api/src/test/java/com/verifiedai/tutoring/domain/HintEvaluationTest.java`
- Created: `services/api/src/test/java/com/verifiedai/tutoring/domain/HintPolicyTest.java`
- Created: `services/api/src/test/java/com/verifiedai/tutoring/domain/HintSourceTest.java`
- Created: `services/api/src/test/java/com/verifiedai/tutoring/integration/ProgressiveHintIntegrationTest.java`

## Owner acceptance tooling

- Created: `scripts/quality/check_hint_contracts.py`
- Created: `scripts/quality/sprint_6_6_owner_acceptance.sh`
- Created: `scripts/quality/sprint_6_6_reconcile_evidence.py`
- Created: `scripts/quality/sprint_6_6_reconcile_evidence.sh`

## Documentation

- Modified: `README.md`
- Modified: `apps/ios/README.md`
- Modified: `docs/verified_ai_learning_platform_docs_v4/00_MASTER_INDEX.md`
- Modified: `docs/verified_ai_learning_platform_docs_v4/DOCUMENTATION_MANIFEST.md`
- Modified: `docs/verified_ai_learning_platform_docs_v4/architecture/14_API_DESIGN_AND_CONTRACTS.md`
- Modified: `docs/verified_ai_learning_platform_docs_v4/backend/20_BACKEND_MODULE_CONTRACTS.md`
- Modified: `docs/verified_ai_learning_platform_docs_v4/data/22_POSTGRESQL_DATA_MODEL.md`
- Modified: `docs/verified_ai_learning_platform_docs_v4/data/23_DATA_LIFECYCLE_RETENTION_AND_AUDIT.md`
- Modified: `docs/verified_ai_learning_platform_docs_v4/domain/06_UBIQUITOUS_LANGUAGE_AND_GLOSSARY.md`
- Modified: `docs/verified_ai_learning_platform_docs_v4/domain/10_DOMAIN_INVARIANTS_AND_BUSINESS_RULES.md`
- Modified: `docs/verified_ai_learning_platform_docs_v4/ios/17_IOS_NETWORKING_PERSISTENCE_AND_OFFLINE.md`
- Modified: `docs/verified_ai_learning_platform_docs_v4/learning/31_MASTERY_AND_KNOWLEDGE_GRAPH.md`
- Modified: `docs/verified_ai_learning_platform_docs_v4/learning/34_TUTORING_AND_PEDAGOGICAL_BEHAVIOR.md`
- Modified: `docs/verified_ai_learning_platform_docs_v4/quality/69_REQUIREMENTS_TRACEABILITY_MATRIX.md`
- Modified: `docs/verified_ai_learning_platform_docs_v4/roadmap/68_PRODUCTION_V1_CAPABILITY_COVERAGE_MATRIX.md`
- Modified: `docs/verified_ai_learning_platform_docs_v4/sprints/00_SPRINT_MASTER_PLAN.md`
- Modified: `docs/verified_ai_learning_platform_docs_v4/sprints/phase06_solution_experience_and_tutoring/PHASE_06_SOLUTION_EXPERIENCE_AND_TUTORING.md`
- Created: `docs/verified_ai_learning_platform_docs_v4/sprints/phase06_solution_experience_and_tutoring/SPRINT_6.6_EXECUTION_REPORT.md`
- Created: `docs/verified_ai_learning_platform_docs_v4/sprints/phase06_solution_experience_and_tutoring/SPRINT_6.6_FILE_MANIFEST.md`
- Created: `docs/verified_ai_learning_platform_docs_v4/sprints/phase06_solution_experience_and_tutoring/SPRINT_6.6_IMPLEMENTATION_MAP.md`
- Modified: `docs/verified_ai_learning_platform_docs_v4/sprints/phase06_solution_experience_and_tutoring/SPRINT_6.6_PROGRESSIVE_HINT_SYSTEM_AND_HINT_COST_SEMANTICS.md`
- Modified: `evaluations/README.md`
- Modified: `packages/contracts/README.md`
- Modified: `packages/schemas/README.md`
- Modified: `services/api/README.md`
