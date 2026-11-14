# Sprint 9.1 Execution Report — Exam Definitions, Curriculum Mapping, and Versioning

## Executive Summary
- **Status:** `LOCAL_ACCEPTANCE_PASSED` (owner host run completed `2026-11-15`; production rollout remains safely feature-flagged behind `verifiedai.exam.catalog-enabled: false` and `verifiedai.exam.target-binding-enabled: false` in `application.yml`).
- Sprint 9.1 implements the immutable, mathematically verified exam blueprint and student target binding foundation (`V054`) across Spring Modulith Java 21 (`com.verifiedai.exam`), Draft 2020-12 JSON schemas and byte-identical OpenAPI 3.1 contracts (`/v1/exams/**` and `/api/v1/exams/**`), and a native Swift 6 iOS catalog and target selector (`Features/Exam/`) backed by `ExamOfflineSQLiteStore` and EN/TR `Localizable.xcstrings` localization.

## Implemented Invariants & Guardrails
- **Weight Conservation & Active Taxonomy Alignment (`ExamBlueprintValidator`):** Enforces $|\sum w - Q_E| \le 10^{-4}$, $0 \le \text{minQuestions} \le w \le \text{maxQuestions} \le Q_E$, and canonical active skill/domain membership via `CurriculumTaxonomyCatalog` (`422 EXAM_CURRICULUM_SKILL_UNKNOWN` / `EXAM_WEIGHT_CONSERVATION_VIOLATION`).
- **Deterministic Blueprint Hashing & Scoring (`ExamBlueprintHashCalculator`, `ScoringFormulaPolicy`):** Generates canonical SHA-256 `blueprintHash` digests for all blueprints and evaluates `CORRECT_MINUS_QUARTER_WRONG`, `RAW_CORRECT_ONLY`, and `WEIGHTED_SECTION_RUBRIC` deterministically at `$0.00` LLM spend (`llmTokensUsed = 0`, `trainingEligible = FALSE`).
- **6 Canonical Seed Blueprints in `V054`:** Seeds `TR_YKS_TYT_MATH@2026.1`, `TR_YKS_TYT_MATH@2025.2` (`DEPRECATED`, superseded by `2026.1`), `TR_YKS_AYT_MATH@2026.1`, `INTL_SAT_MATH@2026.1`, `INTL_AP_CALC_BC@2026.1`, and `INTL_IB_MATH_AA_HL@2026.1`, all mapped exclusively to active `StaticCurriculumTaxonomyCatalog` skills.
- **Target Immutability, BOLA, Idempotency & GDPR Lifecycle:** Enforces BOLA ownership (`403 EXAM_ACCESS_DENIED`), idempotent target binding (`201 Created` / `200 OK` replay / `409 EXAM_VERSION_CONFLICT` on hash drift or non-`ACTIVE` version), prior active primary target superseding, Jackson 3 (`tools.jackson.*`) exclusive usage in `com.verifiedai.exam`, and full user export/purge via `ExamPrivacyLifecycleContributor` (`category() = "exam"`) and `ExamOfflineSQLiteStore.purgeUserData(userId:)`.

## Verification Results Observed in Workspace Sandbox (`BypassSandbox: false`)
- **Backend Unit, Controller, Privacy, V054 SQL Seed & Modulith Tests:**
  - Command: `mvn -f services/api/pom.xml "-Dtest=ModularityTest,ModulithArchitectureTest,RepositoryHierarchyTest,ExamBlueprintValidatorAndScoringTest,V054SeedSqlConsistencyTest,ExamPrivacyLifecycleContributorTest,ExamPlatformControllerTest" test`
  - Result: **14 / 14 passed, 0 failures, 0 errors, 0 skipped (`BUILD SUCCESS`).**
  - `V054SeedSqlConsistencyTest` parses `V054` SQL and `StaticCurriculumTaxonomyCatalog.java` directly and verifies weight conservation, active skill/domain validity, and SHA-256 `blueprint_hash` parity for all 6 seeded blueprints.
- **Schemas, OpenAPI Mirrors & Localization:**
  - `python3 scripts/quality/validate_json_schemas.py`: **PASS** (Sprint 8.1..9.1 schemas, 7 fixtures, negative mutations, OpenAPI `/v1/exams/**` contracts, and 261 EN/TR keys).
  - `cmp -s packages/contracts/openapi/public-api.yaml packages/openapi/verified-ai-core-api.yaml`: **0 bytes diff.**
  - `python3 scripts/quality/check_contracts.py` & `python3 scripts/quality/check_sprint_6_10_localization.py`: **PASS.**
- **Swift 6 Typecheck (Direct `swiftc` in Sandbox):**
  - `Exam` production module (`Core/{DesignSystem,Networking,Observability}` + `Features/Exam/**`), `Sprint91ExamCatalogAndTargetTests.swift`, and `ExamCatalogUITests.swift` compiled/typechecked with `-swift-version 6` (**0 errors, 0 warnings**); modified app integration files passed `swiftc -parse -swift-version 6`.

## Owner Host Acceptance Evidence (`bash scripts/quality/sprint_9_1_owner_acceptance.sh --full`)
- **Host Run Timestamp:** `2026-11-15T00:52:10+03:00`
- **Xcode Test Result Bundle:** `/Users/burakcoskun/Library/Developer/Xcode/DerivedData/VerifiedAI-eawqmwzsewuauxeqouswvmojdyol/Logs/Test/Test-VerifiedAI-2026.11.15_00-52-10-+0300.xcresult`
- **Xcode Outcome:** `** TEST SUCCEEDED **` (`Sprint91ExamCatalogAndTargetTests` & `ExamCatalogUITests`)
- **Runner Outcome:** `==> [Sprint 9.1 Acceptance] ALL REQUESTED STAGES COMPLETED SUCCESSFULLY` (note: pass `--require-docker` on host if explicitly enforcing Docker daemon availability for Stage 5 Testcontainers `ExamPlatformPostgresIntegrationTest`).
