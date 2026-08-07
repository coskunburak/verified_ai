# Production Development Sprint Master Plan

## Purpose

This directory is the end-to-end implementation program for the Verified AI Learning Platform. It starts before the first production code is written and continues through App Store launch and evidence-driven post-launch expansion.

A **Sprint Unit** in this documentation is a bounded delivery package, not necessarily a fixed two-week Scrum iteration. For a solo engineer, many units are approximately 2–5 focused workdays; difficult AI, verification, security, beta, or release units may require longer. Several independent units may run in parallel once additional engineers exist.

## Program hierarchy

Implementation work is organized as:

```text
Program
  -> Phase
      -> Sprint
          -> Work item / PR
              -> Release train
```

The Phase 1-13 sequence is the initial production program, not the complete lifetime roadmap of the product. Future Phase 14+ work is added through the evidence-driven expansion policy in `operations/70_RELEASE_TRAINS_AND_BACKLOG_PROMOTION.md`.

Canonical planning references:

- `roadmap/67_PRODUCT_PROGRAM_STRUCTURE.md` defines Program -> Phase -> Sprint ownership.
- `roadmap/68_PRODUCTION_V1_CAPABILITY_COVERAGE_MATRIX.md` defines Production V1 capability coverage.
- `quality/69_REQUIREMENTS_TRACEABILITY_MATRIX.md` maps requirements to source documents, phases, evidence, and telemetry.
- `operations/70_RELEASE_TRAINS_AND_BACKLOG_PROMOTION.md` defines release trains and backlog promotion states.
- `operations/71_TECHNICAL_DEBT_AND_MAINTENANCE_REGISTER.md` tracks accepted non-blocking maintenance debt.

## Planning principles

- Domain invariants and ADRs have higher precedence than sprint documents.
- A sprint cannot be declared complete because code compiles; its acceptance gates, tests, telemetry, documentation, and operational requirements must be satisfied.
- Production V1 intentionally narrows mathematics coverage while making reliability, verification, security, and learning-state architecture strong.
- New features do not bypass quality gates merely to hit a calendar date.
- AI model or prompt work is evaluated with regression datasets, not intuition alone.
- Database migrations are additive and reviewed; production state is never manually patched as routine workflow.
- Security, privacy, accessibility, localization readiness, analytics, support, and cost controls are built continuously rather than postponed to launch week.
- A feature implementation cannot exit its sprint without a capability mapping and requirement traceability when it affects Production V1 scope.

## Milestone interpretation

- **Architecture-ready:** Phase 1 complete.
- **Engineering platform-ready:** Phase 2 complete.
- **Authenticated commercial shell:** Phase 3 complete.
- **End-to-end problem ingestion:** Phase 4 complete.
- **Verified solver core:** Phase 5 complete.
- **High-quality learner-facing solver/tutor:** Phase 6 complete.
- **Learning intelligence MVP:** Phase 7 complete.
- **Adaptive learning product:** Phase 8 complete.
- **Exam preparation platform:** Phase 9 complete.
- **Commercial/product operations complete:** Phase 10 complete.
- **Production V1 launch:** Phase 11 complete.
- **V1.5/V2 expansion:** Phase 12.
- **Conditional proprietary-ML evolution:** Phase 13 only after explicit entry gates.

## Reality check for a solo engineer

This is an intentionally exhaustive production roadmap, not a promise that one engineer should build every post-launch capability before shipping. The correct release strategy is to preserve the architecture and quality bar while controlling scope. A focused solo implementation can validate the product earlier, but a fully hardened version of the complete roadmap is naturally a multi-month program.

## Phase 1 — Product Semantics & Architecture Baseline

Phase overview: `sprints/phase01_product_semantics_and_architecture/PHASE_01_PRODUCT_SEMANTICS_AND_ARCHITECTURE.md`

- `Sprint 1.1` — **Product Charter, Outcomes, and Release Success Metrics** — `phase01_product_semantics_and_architecture/SPRINT_1.1_PRODUCT_CHARTER_OUTCOMES_AND_RELEASE_SUCCESS_METRICS.md`
- `Sprint 1.2` — **Ubiquitous Language and Non-Negotiable Domain Invariants** — `phase01_product_semantics_and_architecture/SPRINT_1.2_UBIQUITOUS_LANGUAGE_AND_NON_NEGOTIABLE_DOMAIN_INVARIANTS.md`
- `Sprint 1.3` — **Curriculum, Topic, Skill, and Prerequisite Ontology** — `phase01_product_semantics_and_architecture/SPRINT_1.3_CURRICULUM_TOPIC_SKILL_AND_PREREQUISITE_ONTOLOGY.md`
- `Sprint 1.4` — **System Context, Runtime Boundaries, and Deployment Baseline** — `phase01_product_semantics_and_architecture/SPRINT_1.4_SYSTEM_CONTEXT_RUNTIME_BOUNDARIES_AND_DEPLOYMENT_BASELINE.md`
- `Sprint 1.5` — **API, Contract, Error, and Idempotency Baseline** — `phase01_product_semantics_and_architecture/SPRINT_1.5_API_CONTRACT_ERROR_AND_IDEMPOTENCY_BASELINE.md`
- `Sprint 1.6` — **Security, Privacy, Data Classification, and Threat Model Baseline** — `phase01_product_semantics_and_architecture/SPRINT_1.6_SECURITY_PRIVACY_DATA_CLASSIFICATION_AND_THREAT_MODEL_BASELINE.md`
- `Sprint 1.7` — **Engineering Standards, Dependency Policy, and Repository Governance** — `phase01_product_semantics_and_architecture/SPRINT_1.7_ENGINEERING_STANDARDS_DEPENDENCY_POLICY_AND_REPOSITORY_GOVERNANCE.md`
- `Sprint 1.8` — **Production Delivery Map, Quality Gates, and Phase Exit Criteria** — `phase01_product_semantics_and_architecture/SPRINT_1.8_PRODUCTION_DELIVERY_MAP_QUALITY_GATES_AND_PHASE_EXIT_CRITERIA.md`

## Phase 2 — Repository & Platform Foundation

Phase overview: `sprints/phase02_repository_and_platform_foundation/PHASE_02_REPOSITORY_AND_PLATFORM_FOUNDATION.md`

- `Sprint 2.1` — **Monorepo Bootstrap and Developer Experience** — `phase02_repository_and_platform_foundation/SPRINT_2.1_MONOREPO_BOOTSTRAP_AND_DEVELOPER_EXPERIENCE.md`
- `Sprint 2.2` — **iOS Workspace, App Shell, Dependency Injection, and Navigation Foundation** — `phase02_repository_and_platform_foundation/SPRINT_2.2_IOS_WORKSPACE_APP_SHELL_DEPENDENCY_INJECTION_AND_NAVIGATION_FOUNDATION.md`
- `Sprint 2.3` — **Spring Boot Modular Monolith Bootstrap** — `phase02_repository_and_platform_foundation/SPRINT_2.3_SPRING_BOOT_MODULAR_MONOLITH_BOOTSTRAP.md`
- `Sprint 2.4` — **PostgreSQL, Flyway, Persistence, and Testcontainers Foundation** — `phase02_repository_and_platform_foundation/SPRINT_2.4_POSTGRESQL_FLYWAY_PERSISTENCE_AND_TESTCONTAINERS_FOUNDATION.md`
- `Sprint 2.5` — **Internal Python Math Verifier Bootstrap** — `phase02_repository_and_platform_foundation/SPRINT_2.5_INTERNAL_PYTHON_MATH_VERIFIER_BOOTSTRAP.md`
- `Sprint 2.6` — **Local Infrastructure with PostgreSQL, Redis, and Object Storage** — `phase02_repository_and_platform_foundation/SPRINT_2.6_LOCAL_INFRASTRUCTURE_WITH_POSTGRESQL_REDIS_AND_OBJECT_STORAGE.md`
- `Sprint 2.7` — **Continuous Integration Baseline** — `phase02_repository_and_platform_foundation/SPRINT_2.7_CONTINUOUS_INTEGRATION_BASELINE.md`
- `Sprint 2.8` — **Observability, Structured Logging, Tracing, and Developer Diagnostics** — `phase02_repository_and_platform_foundation/SPRINT_2.8_OBSERVABILITY_STRUCTURED_LOGGING_TRACING_AND_DEVELOPER_DIAGNOSTICS.md`

## Phase 3 — Identity, Account & Commerce Foundation

Phase overview: `sprints/phase03_identity_account_and_commerce_foundation/PHASE_03_IDENTITY_ACCOUNT_AND_COMMERCE_FOUNDATION.md`

- `Sprint 3.1` — **Sign in with Apple End-to-End Authentication** — `phase03_identity_account_and_commerce_foundation/SPRINT_3.1_SIGN_IN_WITH_APPLE_END_TO_END_AUTHENTICATION.md`
- `Sprint 3.2` — **Backend Sessions, Access Tokens, Refresh Rotation, and Revocation** — `phase03_identity_account_and_commerce_foundation/SPRINT_3.2_BACKEND_SESSIONS_ACCESS_TOKENS_REFRESH_ROTATION_AND_REVOCATION.md`
- `Sprint 3.3` — **Learner Profile and Production Onboarding** — `phase03_identity_account_and_commerce_foundation/SPRINT_3.3_LEARNER_PROFILE_AND_PRODUCTION_ONBOARDING.md`
- `Sprint 3.4` — **Entitlement Domain and Server-Authoritative Access Policy** — `phase03_identity_account_and_commerce_foundation/SPRINT_3.4_ENTITLEMENT_DOMAIN_AND_SERVER_AUTHORITATIVE_ACCESS_POLICY.md`
- `Sprint 3.5` — **StoreKit 2 Product Loading, Purchase, Restore, and Local UX** — `phase03_identity_account_and_commerce_foundation/SPRINT_3.5_STOREKIT_2_PRODUCT_LOADING_PURCHASE_RESTORE_AND_LOCAL_UX.md`
- `Sprint 3.6` — **App Store Server API and Server Notifications V2** — `phase03_identity_account_and_commerce_foundation/SPRINT_3.6_APP_STORE_SERVER_API_AND_SERVER_NOTIFICATIONS_V2.md`
- `Sprint 3.7` — **Account, Privacy Controls, Data Export, and Deletion Workflow** — `phase03_identity_account_and_commerce_foundation/SPRINT_3.7_ACCOUNT_PRIVACY_CONTROLS_DATA_EXPORT_AND_DELETION_WORKFLOW.md`
- `Sprint 3.8` — **Authentication Abuse, Rate Limits, Audit, and Security Hardening** — `phase03_identity_account_and_commerce_foundation/SPRINT_3.8_AUTHENTICATION_ABUSE_RATE_LIMITS_AUDIT_AND_SECURITY_HARDENING.md`

## Phase 4 — Problem Capture & Canonicalization

Phase overview: `sprints/phase04_problem_capture_and_canonicalization/PHASE_04_PROBLEM_CAPTURE_AND_CANONICALIZATION.md`

- `Sprint 4.1` — **Premium Camera Capture and Import Experience** — `phase04_problem_capture_and_canonicalization/SPRINT_4.1_PREMIUM_CAMERA_CAPTURE_AND_IMPORT_EXPERIENCE.md`
- `Sprint 4.2` — **Presigned Asset Upload and Object Storage Lifecycle** — `phase04_problem_capture_and_canonicalization/SPRINT_4.2_PRESIGNED_ASSET_UPLOAD_AND_OBJECT_STORAGE_LIFECYCLE.md`
- `Sprint 4.3` — **Image Preprocessing and Capture Quality Pipeline** — `phase04_problem_capture_and_canonicalization/SPRINT_4.3_IMAGE_PREPROCESSING_AND_CAPTURE_QUALITY_PIPELINE.md`
- `Sprint 4.4` — **Vision/OCR Ingestion and Raw Recognition Evidence** — `phase04_problem_capture_and_canonicalization/SPRINT_4.4_VISION_OCR_INGESTION_AND_RAW_RECOGNITION_EVIDENCE.md`
- `Sprint 4.5` — **Structured Problem Parser and Versioned Output Schema** — `phase04_problem_capture_and_canonicalization/SPRINT_4.5_STRUCTURED_PROBLEM_PARSER_AND_VERSIONED_OUTPUT_SCHEMA.md`
- `Sprint 4.6` — **Canonical Mathematical Representation and Safe Parsing** — `phase04_problem_capture_and_canonicalization/SPRINT_4.6_CANONICAL_MATHEMATICAL_REPRESENTATION_AND_SAFE_PARSING.md`
- `Sprint 4.7` — **Problem Classification: Subject, Topic, Skill, Type, and Difficulty** — `phase04_problem_capture_and_canonicalization/SPRINT_4.7_PROBLEM_CLASSIFICATION_SUBJECT_TOPIC_SKILL_TYPE_AND_DIFFICULTY.md`
- `Sprint 4.8` — **User-Correctable Parse Review and Revision History** — `phase04_problem_capture_and_canonicalization/SPRINT_4.8_USER_CORRECTABLE_PARSE_REVIEW_AND_REVISION_HISTORY.md`
- `Sprint 4.9` — **Problem Session, History, Retry, and Recovery Experience** — `phase04_problem_capture_and_canonicalization/SPRINT_4.9_PROBLEM_SESSION_HISTORY_RETRY_AND_RECOVERY_EXPERIENCE.md`
- `Sprint 4.10` — **Ingestion Golden Dataset, Accuracy Gates, and Production Hardening** — `phase04_problem_capture_and_canonicalization/SPRINT_4.10_INGESTION_GOLDEN_DATASET_ACCURACY_GATES_AND_PRODUCTION_HARDENING.md`

## Phase 5 — AI Solving & Verification Core

Phase overview: `sprints/phase05_ai_solving_and_verification_core/PHASE_05_AI_SOLVING_AND_VERIFICATION_CORE.md`

- `Sprint 5.1` — **Provider-Neutral AI Gateway and Capability Model** — `phase05_ai_solving_and_verification_core/SPRINT_5.1_PROVIDER_NEUTRAL_AI_GATEWAY_AND_CAPABILITY_MODEL.md`
- `Sprint 5.2` — **Prompt Registry, Schema Governance, and Prompt Release Workflow** — `phase05_ai_solving_and_verification_core/SPRINT_5.2_PROMPT_REGISTRY_SCHEMA_GOVERNANCE_AND_PROMPT_RELEASE_WORKFLOW.md`
- `Sprint 5.3` — **Model Router, Cost Budgets, Fallback, and Reliability Policy** — `phase05_ai_solving_and_verification_core/SPRINT_5.3_MODEL_ROUTER_COST_BUDGETS_FALLBACK_AND_RELIABILITY_POLICY.md`
- `Sprint 5.4` — **Primary Solver Pipeline and Structured Solution Candidate** — `phase05_ai_solving_and_verification_core/SPRINT_5.4_PRIMARY_SOLVER_PIPELINE_AND_STRUCTURED_SOLUTION_CANDIDATE.md`
- `Sprint 5.5` — **Conditional Independent Secondary Solver and Blind Agreement Analysis** — `phase05_ai_solving_and_verification_core/SPRINT_5.5_CONDITIONAL_INDEPENDENT_SECONDARY_SOLVER_AND_BLIND_AGREEMENT_ANALYSIS.md`
- `Sprint 5.6` — **Canonical Solution and Step Domain Model** — `phase05_ai_solving_and_verification_core/SPRINT_5.6_CANONICAL_SOLUTION_AND_STEP_DOMAIN_MODEL.md`
- `Sprint 5.7` — **Verification Planner and Verification Method Selection** — `phase05_ai_solving_and_verification_core/SPRINT_5.7_VERIFICATION_PLANNER_AND_VERIFICATION_METHOD_SELECTION.md`
- `Sprint 5.8` — **Arithmetic, Algebra, and Equation Verification** — `phase05_ai_solving_and_verification_core/SPRINT_5.8_ARITHMETIC_ALGEBRA_AND_EQUATION_VERIFICATION.md`
- `Sprint 5.9` — **Calculus Verification: Limits, Derivatives, and Basic Integrals** — `phase05_ai_solving_and_verification_core/SPRINT_5.9_CALCULUS_VERIFICATION_LIMITS_DERIVATIVES_AND_BASIC_INTEGRALS.md`
- `Sprint 5.10` — **Numerical, Symbolic, and Equivalence Composite Verification** — `phase05_ai_solving_and_verification_core/SPRINT_5.10_NUMERICAL_SYMBOLIC_AND_EQUIVALENCE_COMPOSITE_VERIFICATION.md`
- `Sprint 5.11` — **Arbitration, Uncertainty, Retry, and Human-Honest Verification Policy** — `phase05_ai_solving_and_verification_core/SPRINT_5.11_ARBITRATION_UNCERTAINTY_RETRY_AND_HUMAN_HONEST_VERIFICATION_POLICY.md`
- `Sprint 5.12` — **Golden Dataset, AI Evaluation, Cost Regression, and Model Release Gate** — `phase05_ai_solving_and_verification_core/SPRINT_5.12_GOLDEN_DATASET_AI_EVALUATION_COST_REGRESSION_AND_MODEL_RELEASE_GATE.md`

## Phase 6 — Solution Experience & Tutoring

Phase overview: `sprints/phase06_solution_experience_and_tutoring/PHASE_06_SOLUTION_EXPERIENCE_AND_TUTORING.md`

- `Sprint 6.1` — **Production Solution Results Experience** — `phase06_solution_experience_and_tutoring/SPRINT_6.1_PRODUCTION_SOLUTION_RESULTS_EXPERIENCE.md`
- `Sprint 6.2` — **Verification Transparency and Evidence UX** — `phase06_solution_experience_and_tutoring/SPRINT_6.2_VERIFICATION_TRANSPARENCY_AND_EVIDENCE_UX.md`
- `Sprint 6.3` — **Explanation Depth, Beginner-to-Deep Modes, and Personalization** — `phase06_solution_experience_and_tutoring/SPRINT_6.3_EXPLANATION_DEPTH_BEGINNER_TO_DEEP_MODES_AND_PERSONALIZATION.md`
- `Sprint 6.4` — **Tutor Session Domain and Conversation Persistence** — `phase06_solution_experience_and_tutoring/SPRINT_6.4_TUTOR_SESSION_DOMAIN_AND_CONVERSATION_PERSISTENCE.md`
- `Sprint 6.5` — **Socratic Tutor Behavior and Pedagogical State Machine** — `phase06_solution_experience_and_tutoring/SPRINT_6.5_SOCRATIC_TUTOR_BEHAVIOR_AND_PEDAGOGICAL_STATE_MACHINE.md`
- `Sprint 6.6` — **Progressive Hint System and Hint Cost Semantics** — `phase06_solution_experience_and_tutoring/SPRINT_6.6_PROGRESSIVE_HINT_SYSTEM_AND_HINT_COST_SEMANTICS.md`
- `Sprint 6.7` — **Analyze My Work: User Solution Capture and Step-Level Comparison** — `phase06_solution_experience_and_tutoring/SPRINT_6.7_ANALYZE_MY_WORK_USER_SOLUTION_CAPTURE_AND_STEP_LEVEL_COMPARISON.md`
- `Sprint 6.8` — **Alternative Solution Methods and Method Comparison** — `phase06_solution_experience_and_tutoring/SPRINT_6.8_ALTERNATIVE_SOLUTION_METHODS_AND_METHOD_COMPARISON.md`
- `Sprint 6.9` — **Interactive Mathematical Visualizations and Concept Explanations** — `phase06_solution_experience_and_tutoring/SPRINT_6.9_INTERACTIVE_MATHEMATICAL_VISUALIZATIONS_AND_CONCEPT_EXPLANATIONS.md`
- `Sprint 6.10` — **Solution/Tutor Accessibility, Localization, Performance, and UX Polish** — `phase06_solution_experience_and_tutoring/SPRINT_6.10_SOLUTION_TUTOR_ACCESSIBILITY_LOCALIZATION_PERFORMANCE_AND_UX_POLISH.md`

## Phase 7 — Attempts, Mistakes & Mastery Intelligence

Phase overview: `sprints/phase07_attempt_mistake_and_mastery_intelligence/PHASE_07_ATTEMPT_MISTAKE_AND_MASTERY_INTELLIGENCE.md`

- `Sprint 7.1` — **Attempt Domain and Learner Answer Submission** — `phase07_attempt_mistake_and_mastery_intelligence/SPRINT_7.1_ATTEMPT_DOMAIN_AND_LEARNER_ANSWER_SUBMISSION.md`
- `Sprint 7.2` — **Attempt Step Extraction and Structured Work Representation** — `phase07_attempt_mistake_and_mastery_intelligence/SPRINT_7.2_ATTEMPT_STEP_EXTRACTION_AND_STRUCTURED_WORK_REPRESENTATION.md`
- `Sprint 7.3` — **Attempt Evaluation and Step-Level Correctness** — `phase07_attempt_mistake_and_mastery_intelligence/SPRINT_7.3_ATTEMPT_EVALUATION_AND_STEP_LEVEL_CORRECTNESS.md`
- `Sprint 7.4` — **Mistake Taxonomy and Mistake Classification Pipeline** — `phase07_attempt_mistake_and_mastery_intelligence/SPRINT_7.4_MISTAKE_TAXONOMY_AND_MISTAKE_CLASSIFICATION_PIPELINE.md`
- `Sprint 7.5` — **Automatic Mistake Book and Review Workflows** — `phase07_attempt_mistake_and_mastery_intelligence/SPRINT_7.5_AUTOMATIC_MISTAKE_BOOK_AND_REVIEW_WORKFLOWS.md`
- `Sprint 7.6` — **Mastery Model V1 and Deterministic Update Policy** — `phase07_attempt_mistake_and_mastery_intelligence/SPRINT_7.6_MASTERY_MODEL_V1_AND_DETERMINISTIC_UPDATE_POLICY.md`
- `Sprint 7.7` — **Mastery Confidence, History, Decay, and Reassessment** — `phase07_attempt_mistake_and_mastery_intelligence/SPRINT_7.7_MASTERY_CONFIDENCE_HISTORY_DECAY_AND_REASSESSMENT.md`
- `Sprint 7.8` — **Knowledge Graph Projection and Learning State APIs** — `phase07_attempt_mistake_and_mastery_intelligence/SPRINT_7.8_KNOWLEDGE_GRAPH_PROJECTION_AND_LEARNING_STATE_APIS.md`
- `Sprint 7.9` — **Mastery and Mistake Dashboard UX** — `phase07_attempt_mistake_and_mastery_intelligence/SPRINT_7.9_MASTERY_AND_MISTAKE_DASHBOARD_UX.md`
- `Sprint 7.10` — **Learning Intelligence Evaluation and Bias/Calibration Hardening** — `phase07_attempt_mistake_and_mastery_intelligence/SPRINT_7.10_LEARNING_INTELLIGENCE_EVALUATION_AND_BIAS_CALIBRATION_HARDENING.md`

## Phase 8 — Adaptive Learning & Study Planning

Phase overview: `sprints/phase08_adaptive_learning_and_study_planning/PHASE_08_ADAPTIVE_LEARNING_AND_STUDY_PLANNING.md`

- `Sprint 8.1` — **Next-Best-Action Recommendation Engine** — `phase08_adaptive_learning_and_study_planning/SPRINT_8.1_NEXT_BEST_ACTION_RECOMMENDATION_ENGINE.md`
- `Sprint 8.2` — **Adaptive Practice Question Selection and Generation** — `phase08_adaptive_learning_and_study_planning/SPRINT_8.2_ADAPTIVE_PRACTICE_QUESTION_SELECTION_AND_GENERATION.md`
- `Sprint 8.3` — **Spaced Repetition Scheduling for Skills and Mistakes** — `phase08_adaptive_learning_and_study_planning/SPRINT_8.3_SPACED_REPETITION_SCHEDULING_FOR_SKILLS_AND_MISTAKES.md`
- `Sprint 8.4` — **Daily Study Plan Generation** — `phase08_adaptive_learning_and_study_planning/SPRINT_8.4_DAILY_STUDY_PLAN_GENERATION.md`
- `Sprint 8.5` — **Missed-Day Recovery and Study Plan Rebalancing** — `phase08_adaptive_learning_and_study_planning/SPRINT_8.5_MISSED_DAY_RECOVERY_AND_STUDY_PLAN_REBALANCING.md`
- `Sprint 8.6` — **AI Micro-Lessons Triggered by Diagnosed Weaknesses** — `phase08_adaptive_learning_and_study_planning/SPRINT_8.6_AI_MICRO_LESSONS_TRIGGERED_BY_DIAGNOSED_WEAKNESSES.md`
- `Sprint 8.7` — **Study Session Orchestration and Resume Semantics** — `phase08_adaptive_learning_and_study_planning/SPRINT_8.7_STUDY_SESSION_ORCHESTRATION_AND_RESUME_SEMANTICS.md`
- `Sprint 8.8` — **Goals, Streaks, Achievements, and Non-Manipulative Gamification** — `phase08_adaptive_learning_and_study_planning/SPRINT_8.8_GOALS_STREAKS_ACHIEVEMENTS_AND_NON_MANIPULATIVE_GAMIFICATION.md`
- `Sprint 8.9` — **Weekly Learning Report and Explainable Recommendations** — `phase08_adaptive_learning_and_study_planning/SPRINT_8.9_WEEKLY_LEARNING_REPORT_AND_EXPLAINABLE_RECOMMENDATIONS.md`
- `Sprint 8.10` — **Personalization Calibration, Cold Start, and Recommendation Quality Gates** — `phase08_adaptive_learning_and_study_planning/SPRINT_8.10_PERSONALIZATION_CALIBRATION_COLD_START_AND_RECOMMENDATION_QUALITY_GATES.md`

## Phase 9 — Exam & Assessment Platform

Phase overview: `sprints/phase09_exam_and_assessment_platform/PHASE_09_EXAM_AND_ASSESSMENT_PLATFORM.md`

- `Sprint 9.1` — **Exam Definitions, Curriculum Mapping, and Versioning** — `phase09_exam_and_assessment_platform/SPRINT_9.1_EXAM_DEFINITIONS_CURRICULUM_MAPPING_AND_VERSIONING.md`
- `Sprint 9.2` — **Learner Exam Goal Setup and Candidate Profile** — `phase09_exam_and_assessment_platform/SPRINT_9.2_LEARNER_EXAM_GOAL_SETUP_AND_CANDIDATE_PROFILE.md`
- `Sprint 9.3` — **Question Bank, Blueprint, and Assessment Assembly Rules** — `phase09_exam_and_assessment_platform/SPRINT_9.3_QUESTION_BANK_BLUEPRINT_AND_ASSESSMENT_ASSEMBLY_RULES.md`
- `Sprint 9.4` — **Timed Mock Exam Runtime** — `phase09_exam_and_assessment_platform/SPRINT_9.4_TIMED_MOCK_EXAM_RUNTIME.md`
- `Sprint 9.5` — **Scoring Engine and Evidence Traceability** — `phase09_exam_and_assessment_platform/SPRINT_9.5_SCORING_ENGINE_AND_EVIDENCE_TRACEABILITY.md`
- `Sprint 9.6` — **Exam Readiness Model and Confidence Semantics** — `phase09_exam_and_assessment_platform/SPRINT_9.6_EXAM_READINESS_MODEL_AND_CONFIDENCE_SEMANTICS.md`
- `Sprint 9.7` — **Weak-Area Prioritization After Assessments** — `phase09_exam_and_assessment_platform/SPRINT_9.7_WEAK_AREA_PRIORITIZATION_AFTER_ASSESSMENTS.md`
- `Sprint 9.8` — **Final Review and Pre-Exam Study Mode** — `phase09_exam_and_assessment_platform/SPRINT_9.8_FINAL_REVIEW_AND_PRE_EXAM_STUDY_MODE.md`
- `Sprint 9.9` — **Exam Analytics, Reports, and Progress Comparison** — `phase09_exam_and_assessment_platform/SPRINT_9.9_EXAM_ANALYTICS_REPORTS_AND_PROGRESS_COMPARISON.md`
- `Sprint 9.10` — **Assessment Integrity, Accessibility, Failure Recovery, and Reliability** — `phase09_exam_and_assessment_platform/SPRINT_9.10_ASSESSMENT_INTEGRITY_ACCESSIBILITY_FAILURE_RECOVERY_AND_RELIABILITY.md`

## Phase 10 — Monetization, Growth & Product Operations

Phase overview: `sprints/phase10_monetization_growth_and_product_operations/PHASE_10_MONETIZATION_GROWTH_AND_PRODUCT_OPERATIONS.md`

- `Sprint 10.1` — **Paywall Architecture, Offer Presentation, and Pricing Configuration** — `phase10_monetization_growth_and_product_operations/SPRINT_10.1_PAYWALL_ARCHITECTURE_OFFER_PRESENTATION_AND_PRICING_CONFIGURATION.md`
- `Sprint 10.2` — **Free, Pro, Pro+ Feature Guards and Entitlement Enforcement** — `phase10_monetization_growth_and_product_operations/SPRINT_10.2_FREE_PRO_PRO_FEATURE_GUARDS_AND_ENTITLEMENT_ENFORCEMENT.md`
- `Sprint 10.3` — **Usage Quotas, AI Budgets, Credits, and Fair-Use Enforcement** — `phase10_monetization_growth_and_product_operations/SPRINT_10.3_USAGE_QUOTAS_AI_BUDGETS_CREDITS_AND_FAIR_USE_ENFORCEMENT.md`
- `Sprint 10.4` — **Notification Infrastructure and Preference Center** — `phase10_monetization_growth_and_product_operations/SPRINT_10.4_NOTIFICATION_INFRASTRUCTURE_AND_PREFERENCE_CENTER.md`
- `Sprint 10.5` — **Lifecycle Messaging and Learning-Relevant Re-Engagement** — `phase10_monetization_growth_and_product_operations/SPRINT_10.5_LIFECYCLE_MESSAGING_AND_LEARNING_RELEVANT_RE_ENGAGEMENT.md`
- `Sprint 10.6` — **Analytics Event Catalog and Product Metrics Implementation** — `phase10_monetization_growth_and_product_operations/SPRINT_10.6_ANALYTICS_EVENT_CATALOG_AND_PRODUCT_METRICS_IMPLEMENTATION.md`
- `Sprint 10.7` — **Feature Flags, Remote Configuration, A/B Tests, and Kill Switches** — `phase10_monetization_growth_and_product_operations/SPRINT_10.7_FEATURE_FLAGS_REMOTE_CONFIGURATION_A_B_TESTS_AND_KILL_SWITCHES.md`
- `Sprint 10.8` — **Admin and Support Console Foundations** — `phase10_monetization_growth_and_product_operations/SPRINT_10.8_ADMIN_AND_SUPPORT_CONSOLE_FOUNDATIONS.md`
- `Sprint 10.9` — **Organic Growth Loops, Sharing, Referral, and Store Optimization Hooks** — `phase10_monetization_growth_and_product_operations/SPRINT_10.9_ORGANIC_GROWTH_LOOPS_SHARING_REFERRAL_AND_STORE_OPTIMIZATION_HOOKS.md`
- `Sprint 10.10` — **Unit Economics, AI Cost Dashboards, and Margin Safeguards** — `phase10_monetization_growth_and_product_operations/SPRINT_10.10_UNIT_ECONOMICS_AI_COST_DASHBOARDS_AND_MARGIN_SAFEGUARDS.md`

## Phase 11 — Production Hardening, Beta & Launch

Phase overview: `sprints/phase11_production_hardening_beta_and_launch/PHASE_11_PRODUCTION_HARDENING_BETA_AND_LAUNCH.md`

- `Sprint 11.1` — **iOS Performance, Memory, Battery, and Network Profiling** — `phase11_production_hardening_beta_and_launch/SPRINT_11.1_IOS_PERFORMANCE_MEMORY_BATTERY_AND_NETWORK_PROFILING.md`
- `Sprint 11.2` — **Backend Load Testing, Concurrency, and Capacity Baseline** — `phase11_production_hardening_beta_and_launch/SPRINT_11.2_BACKEND_LOAD_TESTING_CONCURRENCY_AND_CAPACITY_BASELINE.md`
- `Sprint 11.3` — **PostgreSQL Query Plans, Indexing, Pooling, and Data Growth Review** — `phase11_production_hardening_beta_and_launch/SPRINT_11.3_POSTGRESQL_QUERY_PLANS_INDEXING_POOLING_AND_DATA_GROWTH_REVIEW.md`
- `Sprint 11.4` — **Security Testing, Abuse Simulation, and Privilege Review** — `phase11_production_hardening_beta_and_launch/SPRINT_11.4_SECURITY_TESTING_ABUSE_SIMULATION_AND_PRIVILEGE_REVIEW.md`
- `Sprint 11.5` — **Privacy, Retention, Account Deletion, and Compliance Readiness** — `phase11_production_hardening_beta_and_launch/SPRINT_11.5_PRIVACY_RETENTION_ACCOUNT_DELETION_AND_COMPLIANCE_READINESS.md`
- `Sprint 11.6` — **Failure Injection, AI Provider Outage, and Chaos Scenarios** — `phase11_production_hardening_beta_and_launch/SPRINT_11.6_FAILURE_INJECTION_AI_PROVIDER_OUTAGE_AND_CHAOS_SCENARIOS.md`
- `Sprint 11.7` — **Incident Response, Alerting, Runbooks, and Operational Readiness** — `phase11_production_hardening_beta_and_launch/SPRINT_11.7_INCIDENT_RESPONSE_ALERTING_RUNBOOKS_AND_OPERATIONAL_READINESS.md`
- `Sprint 11.8` — **Backup, Point-in-Time Recovery, and Disaster Recovery Test** — `phase11_production_hardening_beta_and_launch/SPRINT_11.8_BACKUP_POINT_IN_TIME_RECOVERY_AND_DISASTER_RECOVERY_TEST.md`
- `Sprint 11.9` — **Closed TestFlight Beta and Structured Feedback Program** — `phase11_production_hardening_beta_and_launch/SPRINT_11.9_CLOSED_TESTFLIGHT_BETA_AND_STRUCTURED_FEEDBACK_PROGRAM.md`
- `Sprint 11.10` — **Beta Iteration, Bug Burn-Down, and Release Candidate Freeze** — `phase11_production_hardening_beta_and_launch/SPRINT_11.10_BETA_ITERATION_BUG_BURN_DOWN_AND_RELEASE_CANDIDATE_FREEZE.md`
- `Sprint 11.11` — **App Store Submission, Privacy Manifest, Metadata, and Launch Readiness** — `phase11_production_hardening_beta_and_launch/SPRINT_11.11_APP_STORE_SUBMISSION_PRIVACY_MANIFEST_METADATA_AND_LAUNCH_READINESS.md`
- `Sprint 11.12` — **Production Launch, Progressive Rollout, War Room, and Rollback Gates** — `phase11_production_hardening_beta_and_launch/SPRINT_11.12_PRODUCTION_LAUNCH_PROGRESSIVE_ROLLOUT_WAR_ROOM_AND_ROLLBACK_GATES.md`

## Phase 12 — Post-Launch Scale & Product Expansion

Phase overview: `sprints/phase12_post_launch_scale_and_product_expansion/PHASE_12_POST_LAUNCH_SCALE_AND_PRODUCT_EXPANSION.md`

- `Sprint 12.1` — **Production Feedback Triage and Evidence-Driven Roadmap Reset** — `phase12_post_launch_scale_and_product_expansion/SPRINT_12.1_PRODUCTION_FEEDBACK_TRIAGE_AND_EVIDENCE_DRIVEN_ROADMAP_RESET.md`
- `Sprint 12.2` — **AI Model, Prompt, Cache, and Cost Optimization** — `phase12_post_launch_scale_and_product_expansion/SPRINT_12.2_AI_MODEL_PROMPT_CACHE_AND_COST_OPTIMIZATION.md`
- `Sprint 12.3` — **Advanced Calculus and Trigonometry Coverage** — `phase12_post_launch_scale_and_product_expansion/SPRINT_12.3_ADVANCED_CALCULUS_AND_TRIGONOMETRY_COVERAGE.md`
- `Sprint 12.4` — **Linear Algebra, Probability, and Statistics Coverage** — `phase12_post_launch_scale_and_product_expansion/SPRINT_12.4_LINEAR_ALGEBRA_PROBABILITY_AND_STATISTICS_COVERAGE.md`
- `Sprint 12.5` — **PDF, Lecture Note, and Course Workspace Ingestion** — `phase12_post_launch_scale_and_product_expansion/SPRINT_12.5_PDF_LECTURE_NOTE_AND_COURSE_WORKSPACE_INGESTION.md`
- `Sprint 12.6` — **Teacher and Classroom Mode** — `phase12_post_launch_scale_and_product_expansion/SPRINT_12.6_TEACHER_AND_CLASSROOM_MODE.md`
- `Sprint 12.7` — **Parent/Guardian Progress Experience** — `phase12_post_launch_scale_and_product_expansion/SPRINT_12.7_PARENT_GUARDIAN_PROGRESS_EXPERIENCE.md`
- `Sprint 12.8` — **Additional Exam Verticals and Regional Curriculum Packs** — `phase12_post_launch_scale_and_product_expansion/SPRINT_12.8_ADDITIONAL_EXAM_VERTICALS_AND_REGIONAL_CURRICULUM_PACKS.md`
- `Sprint 12.9` — **Scale Thresholds, Service Extraction Criteria, and Architecture Evolution** — `phase12_post_launch_scale_and_product_expansion/SPRINT_12.9_SCALE_THRESHOLDS_SERVICE_EXTRACTION_CRITERIA_AND_ARCHITECTURE_EVOLUTION.md`
- `Sprint 12.10` — **Android, Web, and Cross-Platform Expansion Strategy** — `phase12_post_launch_scale_and_product_expansion/SPRINT_12.10_ANDROID_WEB_AND_CROSS_PLATFORM_EXPANSION_STRATEGY.md`

<!-- HYBRID_AI_STRATEGY_V3:START -->
# Sprint Program v3 — Hybrid AI, Unit Economics, and Proprietary-Model Evolution

## New program-wide rules

1. Production V1 is API-first and provider-neutral; no foundation model is trained from scratch.
2. The second solver is a **conditional escalation**, not an unconditional per-question cost.
3. Every AI-affecting sprint must state expected invocation rate, route tier, cost impact, evaluation and rollback.
4. Production student content is not training data by default.
5. Deterministic algorithms and explainable baselines precede learned replacements.
6. Proprietary model work is moved into a conditional **Phase 13** with explicit entry gates.
7. Self-hosted inference requires TCO and capacity evidence, not only GPU-price comparison.
8. Every sprint now contains a `Production Execution Specification v3` section and a required evidence package.

## Phase 13 — Proprietary ML Evolution and Model Independence (Conditional)

- Sprint 13.1 — AI Unit Economics Decision Baseline and Replacement Opportunity Map
- Sprint 13.2 — Training Data Eligibility, Consent, Lineage, and Governance Foundation
- Sprint 13.3 — Dataset Curation, Label Quality, Deduplication, and Leakage Prevention Pipeline
- Sprint 13.4 — Proprietary Skill and Topic Classifier Baseline
- Sprint 13.5 — Proprietary Mistake Classifier Baseline and Error Provenance
- Sprint 13.6 — Difficulty Prediction Model Calibration and Slice Evaluation
- Sprint 13.7 — Mastery Prediction Candidate with Offline Shadow Evaluation
- Sprint 13.8 — Recommendation Ranking Candidate and Learning Outcome Evaluation
- Sprint 13.9 — Fine-Tuning Experiment Infrastructure, Model Registry, and Reproducibility
- Sprint 13.10 — Specialized Mathematics Solver Candidate Fine-Tuning and Hard-Tail Evaluation
- Sprint 13.11 — Self-Hosted Inference Serving, Capacity, Security, and Total-Cost Benchmark
- Sprint 13.12 — Hybrid Model Router Shadow/Canary Rollout, Fallback, and Rollback

Phase 13 may be skipped indefinitely if evidence shows APIs/deterministic routes remain the superior solution.
<!-- HYBRID_AI_STRATEGY_V3:END -->
