# Verified AI Learning Platform — Master Documentation Index

## Purpose

This repository is the canonical semantic and technical description of a production-grade, iOS-first AI mathematics learning platform. The documentation is intentionally optimized for four audiences at once:

1. Human engineers and product collaborators.
2. NotebookLM or other research-oriented AI systems that need deep project context.
3. Codex or other implementation agents that need exact domain rules, module ownership, architecture constraints, and file conventions.
4. Future maintainers who must be able to understand why the system is built the way it is without relying on chat history.

The documentation repeats a small number of critical invariants across multiple files on purpose. AI agents may read only a subset of the repository, so safety- and architecture-critical rules must remain locally discoverable.

---

## Product definition in one sentence

> The product converts raw student mathematics questions into structured problems, produces explainable AI-assisted solutions, verifies those solutions with independent and deterministic methods where possible, diagnoses student mistakes, maintains a persistent skill mastery model, and automatically determines what the learner should practice next.

The product is **not** positioned as another homework solver. Its durable value is the combination of:

- verified answers,
- transparent uncertainty,
- structured learning history,
- mistake intelligence,
- adaptive practice,
- exam-oriented planning,
- and a durable student knowledge graph.

---

## Canonical documentation map

### Product
- `product/01_PRODUCT_VISION_AND_POSITIONING.md` — product thesis, value proposition, positioning, non-goals.
- `product/02_PERSONAS_AND_JOBS_TO_BE_DONE.md` — users, jobs, pains, purchase motivations.
- `product/03_END_TO_END_USER_JOURNEYS.md` — complete flows, failure and recovery states.
- `product/04_FEATURE_CATALOG_AND_PRODUCT_RULES.md` — production feature inventory and behavioral rules.
- `product/05_MONETIZATION_AND_ENTITLEMENTS.md` — Free/Pro/Pro+ semantics, IAP, server-authoritative entitlement.

### Domain
- `domain/06_UBIQUITOUS_LANGUAGE_AND_GLOSSARY.md` — canonical vocabulary.
- `domain/07_DOMAIN_MODEL_AND_AGGREGATES.md` — entities, aggregates, value objects, ownership.
- `domain/08_BOUNDED_CONTEXTS_AND_MODULE_BOUNDARIES.md` — modular monolith boundaries.
- `domain/09_DOMAIN_EVENTS_AND_STATE_MACHINES.md` — events, lifecycle states, async transitions.
- `domain/10_DOMAIN_INVARIANTS_AND_BUSINESS_RULES.md` — rules that must never be violated.

### Architecture
- `architecture/11_SYSTEM_ARCHITECTURE_OVERVIEW.md` — system context and component responsibilities.
- `architecture/12_RUNTIME_AND_DEPLOYMENT_ARCHITECTURE.md` — runtime, environments, deployment and scaling.
- `architecture/13_ASYNC_PROCESSING_AND_JOB_ORCHESTRATION.md` — solve jobs, retries, idempotency.
- `architecture/14_API_DESIGN_AND_CONTRACTS.md` — API conventions and public contracts.

### iOS
- `ios/15_IOS_ARCHITECTURE_AND_FILE_HIERARCHY.md` — Swift/SwiftUI architecture and complete file layout.
- `ios/16_IOS_DESIGN_SYSTEM_AND_UX_ENGINEERING.md` — premium UX, design system, accessibility.
- `ios/17_IOS_NETWORKING_PERSISTENCE_AND_OFFLINE.md` — URLSession, SwiftData, caching and sync.
- `ios/18_IOS_AUTH_STOREKIT_AND_DEVICE_SECURITY.md` — Sign in with Apple, Keychain, StoreKit 2.

### Backend
- `backend/19_BACKEND_ARCHITECTURE_AND_FILE_HIERARCHY.md` — Spring Boot modular monolith structure.
- `backend/20_BACKEND_MODULE_CONTRACTS.md` — per-module responsibilities and legal dependencies.
- `backend/21_AUTHORIZATION_RATE_LIMITING_AND_ABUSE.md` — authz, premium guards, rate limits, abuse protection.

### Data
- `data/22_POSTGRESQL_DATA_MODEL.md` — relational schema, tables, keys and indexes.
- `data/23_DATA_LIFECYCLE_RETENTION_AND_AUDIT.md` — retention, deletion, PII, audit.
- `data/24_CACHING_STORAGE_AND_FILE_ASSETS.md` — Redis, object storage, cache ownership.

### AI and verification
- `ai/25_AI_ORCHESTRATION_AND_MODEL_ROUTING.md` — provider-neutral AI architecture.
- `ai/26_PROMPT_SCHEMA_AND_VERSIONING.md` — structured outputs and prompt governance.
- `ai/27_VERIFICATION_ENGINE.md` — deterministic and multi-solver verification.
- `ai/28_AI_EVALUATION_AND_GOLDEN_DATASET.md` — regression evaluation and release gates.
- `ai/29_AI_COST_LATENCY_AND_RELIABILITY.md` — cost budgets, fallback and reliability.

### Learning intelligence
- `learning/30_MISTAKE_INTELLIGENCE.md` — error taxonomy and diagnosis pipeline.
- `learning/31_MASTERY_AND_KNOWLEDGE_GRAPH.md` — skill model and mastery semantics.
- `learning/32_ADAPTIVE_LEARNING_AND_STUDY_PLANNER.md` — next-best-action engine.
- `learning/33_EXAM_MODE_AND_ASSESSMENT_ENGINE.md` — mock exams, readiness and scoring.
- `learning/34_TUTORING_AND_PEDAGOGICAL_BEHAVIOR.md` — Socratic tutoring, hints and explanation depth.

### Security and operations
- `security/35_SECURITY_THREAT_MODEL.md` — trust boundaries, threats and mitigations.
- `security/36_PRIVACY_AND_STUDENT_DATA_PROTECTION.md` — privacy-by-design rules.
- `operations/37_OBSERVABILITY_SLOS_AND_INCIDENTS.md` — logs, metrics, traces, incidents.
- `operations/38_CI_CD_ENVIRONMENTS_AND_RELEASES.md` — CI/CD and release management.
- `operations/39_RUNBOOKS_AND_FAILURE_MODES.md` — operational playbooks.
- `operations/70_RELEASE_TRAINS_AND_BACKLOG_PROMOTION.md` — release trains, backlog promotion states, post-launch phase expansion policy.
- `operations/71_TECHNICAL_DEBT_AND_MAINTENANCE_REGISTER.md` — tracked non-blocking debt and maintenance obligations.

### Quality and delivery
- `quality/40_TEST_STRATEGY.md` — unit, integration, UI, property and AI evaluation strategy.
- `quality/41_ENGINEERING_STANDARDS_FOR_CODEX.md` — hard rules for Codex and contributors.
- `quality/42_DEFINITION_OF_DONE_AND_ACCEPTANCE.md` — quality bar per feature.
- `quality/69_REQUIREMENTS_TRACEABILITY_MATRIX.md` — requirement IDs mapped to sources, implementation phases, acceptance evidence, and telemetry.

### Roadmap and decisions
- `roadmap/43_PRODUCT_AND_TECHNICAL_ROADMAP.md` — feasibility → MVP → Production V1 → V1.5 → V2.
- `roadmap/67_PRODUCT_PROGRAM_STRUCTURE.md` — Program → Phase → Sprint → Work Item → Release Train planning model.
- `roadmap/68_PRODUCTION_V1_CAPABILITY_COVERAGE_MATRIX.md` — Production V1 capability coverage, status, and evidence map.
- `adr/ADR-001_POSTGRESQL_OVER_FIRESTORE.md`
- `adr/ADR-002_MODULAR_MONOLITH_OVER_MICROSERVICES.md`
- `adr/ADR-003_PROVIDER_NEUTRAL_AI.md`
- `adr/ADR-004_SEPARATE_PYTHON_MATH_VERIFIER.md`

### AI/MCP context
- `integrations/44_AI_AGENT_CONTEXT_AND_READING_ORDER.md` — optimized reading order for NotebookLM and Codex.
- `integrations/45_MCP_WORKFLOW_NOTEBOOKLM_CODEX.md` — MCP handoff and agent workflow.

---

## Source-of-truth hierarchy

When documents conflict, use this precedence:

1. `domain/10_DOMAIN_INVARIANTS_AND_BUSINESS_RULES.md`
2. Accepted ADRs in `/adr`
3. Relevant domain and architecture documents
4. API and data contracts
5. Product feature descriptions and Production V1 capability coverage
6. Requirement traceability and release-governance material
7. Roadmap and aspirational material

Never infer a business rule from a UI mockup or generated implementation when a higher-precedence semantic document exists.

---

## Non-negotiable implementation principles

1. AI output is untrusted input.
2. Only the verification policy may assign `VERIFIED`.
3. The backend is authoritative for identity, entitlement, learning state, mastery and billing.
4. The iOS app never contains AI provider secrets.
5. PostgreSQL is the primary source of truth.
6. Redis is disposable infrastructure, never canonical learning storage.
7. iOS communicates only with the Spring Boot API, never directly with the math verifier.
8. Provider-specific AI code is isolated behind internal ports/adapters.
9. The product must explicitly expose uncertainty when deterministic verification is impossible.
10. A student's wrong attempt is valuable learning data and must never be overwritten by the reference solution.
11. High-impact prompt/model changes require golden-dataset regression evaluation.
12. The first production scope is mathematics, intentionally narrower than generic assistants.
13. The competitive advantage is not the LLM. It is verification + learning intelligence + structured history + adaptive decisions.

---

## Recommended reading paths

### New engineer
00 → 01 → 06 → 07 → 08 → 10 → 11 → 19 → 22 → 25 → 27 → 31 → 41

### iOS engineer
00 → 01 → 03 → 10 → 14 → 15 → 16 → 17 → 18 → 42

### Backend/Codex backend task
00 → 06 → 07 → 08 → 10 → 19 → 20 → 22 → 25 → 27 → 35 → 40 → 41

### AI/learning task
00 → 06 → 07 → 10 → 25 → 26 → 27 → 28 → 30 → 31 → 32 → 34

### Program/release planning task
00 → 67 → 68 → 69 → 70 → 71 → 43 → sprints/00

### NotebookLM research session
Read all files, but begin with 00, 01, 06, 10, 11, 27, 30, 31, 32, 43, 44, 67, 68, 69.

---

## Documentation maintenance rule

Any feature that changes one or more of the following must update the corresponding Markdown documentation in the same change:

- domain vocabulary,
- aggregate ownership,
- API contract,
- database schema,
- verification semantics,
- mastery semantics,
- entitlement behavior,
- security boundary,
- user-visible product promise,
- model/prompt policy.

Documentation is part of the product and part of the implementation contract.

---

## Extended semantic and engineering specifications

- `domain/46_CURRICULUM_SKILL_ONTOLOGY_AND_TAXONOMY.md` — stable skill IDs, prerequisites, ontology evolution and exam mapping.
- `domain/47_MATHEMATICAL_CANONICAL_REPRESENTATION.md` — safe normalized math representation used by solvers and verifier.
- `operations/48_ANALYTICS_EVENT_CATALOG_AND_PRODUCT_METRICS.md` — canonical analytics vocabulary and north-star metrics.
- `operations/49_FEATURE_FLAGS_REMOTE_CONFIG_AND_EXPERIMENTATION.md` — progressive rollout, experiments and kill switches.
- `backend/50_ADMIN_SUPPORT_AND_INTERNAL_TOOLS.md` — operational support, problem trace and privileged tooling.
- `architecture/51_ERROR_TAXONOMY_AND_RECOVERY_CONTRACT.md` — stable error codes and client recovery semantics.
- `ios/52_LOCALIZATION_MATH_NOTATION_AND_ACCESSIBILITY_CONTENT.md` — math-aware localization and accessibility rules.
- `quality/53_DEPENDENCY_AND_LIBRARY_GOVERNANCE.md` — package/dependency policy.
- `quality/54_REPOSITORY_ROOT_HIERARCHY_AND_NAMING_CONVENTIONS.md` — monorepo hierarchy and naming.
- `quality/55_DEVELOPMENT_WORKFLOW_BRANCHING_AND_REVIEW.md` — implementation/PR workflow for humans and Codex.

## Complete repository hierarchy and sprint execution program

- `quality/56_COMPLETE_PRODUCTION_REPOSITORY_FILE_HIERARCHY.md` — exhaustive monorepo/iOS/backend/verifier/contracts/prompts/evaluations/infra/tests/docs hierarchy and file-placement rules.
- `sprints/00_SPRINT_MASTER_PLAN.md` — start-to-finish production execution program.
- `sprints/phase01_product_semantics_and_architecture/` through `sprints/phase12_post_launch_scale_and_product_expansion/` — phase overviews and individually executable Sprint 1.1 ... Sprint 12.x documents.
- `sprints/phase01_product_semantics_and_architecture/SPRINT_0_DOCUMENTATION_INGESTION_AND_REPOSITORY_BASELINE.md` — Codex Sprint 0 ingestion and repository baseline evidence.
- `sprints/phase01_product_semantics_and_architecture/PHASE_01_EXECUTION_REPORT.md` — Phase 1 completion evidence, decisions, risks and Phase 2 readiness.
- `sprints/phase02_repository_and_platform_foundation/PHASE_02_PRE_IMPLEMENTATION_BASELINE.md` — Phase 2 NotebookLM/docs/toolchain baseline before repository edits.
- `sprints/phase02_repository_and_platform_foundation/PHASE_02_IMPLEMENTATION_MAP.md` — mapping from Phase 2 sprint requirements to implemented repository artifacts.
- `sprints/phase02_repository_and_platform_foundation/PHASE_02_FINAL_REPOSITORY_TREE.md` — final Phase 2 repository tree excluding generated artifacts.
- `sprints/phase02_repository_and_platform_foundation/PHASE_02_EXECUTION_REPORT.md` — Phase 2 validation evidence, decisions, known notes and Phase 3 readiness.
- `sprints/phase03_identity_account_and_commerce_foundation/SPRINT_3.1_EXECUTION_REPORT.md` — Sprint 3.1 Sign in with Apple implementation evidence, decisions, validation status and external Apple sandbox note.
- `sprints/phase03_identity_account_and_commerce_foundation/SPRINT_3.2_EXECUTION_REPORT.md` — Sprint 3.2 session, access token, refresh rotation and revocation implementation evidence.
- `sprints/phase03_identity_account_and_commerce_foundation/SPRINT_3.3_3.4_IMPLEMENTATION_MAP.md` — Sprint 3.3 and Sprint 3.4 implementation mapping from source requirements to repository artifacts.
- `sprints/phase03_identity_account_and_commerce_foundation/SPRINT_3.3_EXECUTION_REPORT.md` — Sprint 3.3 learner profile and production onboarding implementation evidence.
- `sprints/phase03_identity_account_and_commerce_foundation/SPRINT_3.4_EXECUTION_REPORT.md` — Sprint 3.4 entitlement domain and server-authoritative access-policy implementation evidence.
- `sprints/phase03_identity_account_and_commerce_foundation/APP_STORE_CONNECT_SETUP_CHECKLIST.md` — App Store Connect, product catalog, credential, certificate and webhook setup checklist for commerce validation.
- `sprints/phase03_identity_account_and_commerce_foundation/SPRINT_3.5_EXECUTION_REPORT.md` — Sprint 3.5 StoreKit 2 product loading, purchase, restore, transaction listener and backend purchase-evidence implementation evidence.
- `sprints/phase03_identity_account_and_commerce_foundation/SPRINT_3.6_EXECUTION_REPORT.md` — Sprint 3.6 App Store Server API, Server Notifications V2, subscription lifecycle and entitlement reconciliation implementation evidence.
- `sprints/phase03_identity_account_and_commerce_foundation/SPRINT_3.7_3.8_IMPLEMENTATION_MAP.md` — Sprint 3.7/3.8 mapping from capability, requirement and debt IDs to implemented artifacts.
- `sprints/phase03_identity_account_and_commerce_foundation/SPRINT_3.7_EXECUTION_REPORT.md` — Sprint 3.7 account privacy, data export and deletion implementation evidence.
- `sprints/phase03_identity_account_and_commerce_foundation/SPRINT_3.8_EXECUTION_REPORT.md` — Sprint 3.8 authentication abuse, rate limit, audit and security-hardening implementation evidence.
- `sprints/phase03_identity_account_and_commerce_foundation/PHASE_03_EXECUTION_REPORT.md` — Phase 3 completion evidence, local validation, open Apple validation debt and Phase 4 handoff.
- `sprints/phase04_problem_capture_and_canonicalization/SPRINT_4.1_IMPLEMENTATION_MAP.md` — Sprint 4.1 local capture/import source mapping, scope boundaries and implementation plan.
- `sprints/phase04_problem_capture_and_canonicalization/SPRINT_4.1_EXECUTION_REPORT.md` — Sprint 4.1 premium camera capture/import implementation evidence, validation status and real-device debt.
- `sprints/phase04_problem_capture_and_canonicalization/SPRINT_4.2_IMPLEMENTATION_MAP.md` — Sprint 4.2 presigned upload, ProblemSession/ProblemAsset ownership, storage, privacy and validation mapping.
- `sprints/phase04_problem_capture_and_canonicalization/SPRINT_4.2_EXECUTION_REPORT.md` — Sprint 4.2 durable asset upload implementation evidence, tests, storage validation and Sprint 4.3 readiness.

For implementation agents, sprint documents are execution plans. They never override domain invariants, accepted ADRs, security rules, or canonical API/data contracts.

<!-- HYBRID_AI_STRATEGY_V3:START -->
## API-first / progressively proprietary AI source-of-truth additions

The accepted AI strategy is now explicitly documented as:

> External provider-neutral foundation-model APIs for Production V1 + internal deterministic verification and learning intelligence; proprietary small models only after measurable data/evaluation/economics gates; no frontier foundation-model training from scratch.

### New canonical documents

- `ai/57_API_FIRST_HYBRID_AI_STRATEGY_AND_MODEL_EVOLUTION.md`
- `ai/58_PROPRIETARY_DATASET_GOVERNANCE_AND_TRAINING_ELIGIBILITY.md`
- `ai/59_SMALL_MODEL_FINE_TUNING_AND_SELF_HOSTING_READINESS.md`
- `ai/60_AI_UNIT_ECONOMICS_AND_INFERENCE_COST_ENGINEERING.md`
- `ai/61_MODEL_REGISTRY_RELEASE_AND_ROLLBACK_GOVERNANCE.md`
- `data/62_TRAINING_DATA_LINEAGE_CONSENT_AND_DATASET_PIPELINE.md`
- `operations/63_AI_CAPACITY_FINOPS_AND_PROVIDER_BUDGET_OPERATIONS.md`
- `quality/64_AI_MODEL_REPLACEMENT_DECISION_GATES.md`

### New accepted ADRs

- `adr/ADR-005_API_FIRST_PROGRESSIVELY_PROPRIETARY_AI_STRATEGY.md`
- `adr/ADR-006_PRODUCTION_STUDENT_DATA_NOT_TRAINING_DATA_BY_DEFAULT.md`
- `adr/ADR-007_SELF_HOSTED_MODELS_ONLY_AFTER_TCO_AND_QUALITY_GATES.md`

### Sprint program extension

The delivery program now includes conditional Phase 13 with 12 proprietary-ML/model-independence sprints. Every sprint document has been expanded with a Production Execution Specification v3 covering domain ownership, data/API changes, AI economics, training-data guardrails, security, observability, testing, rollout and evidence requirements.

### Precedence update

ADR-005/006/007 and documents 57–64 are mandatory reading for any AI model, inference-cost, fine-tuning, dataset or self-hosting decision.
<!-- HYBRID_AI_STRATEGY_V3:END -->

<!-- SPRINT_STANDARD_V3:START -->
## Sprint execution standard and v3 changelog

- `sprints/01_SPRINT_EXECUTION_STANDARD.md` — mandatory structure and evidence requirements for every production sprint.
- `DOCUMENTATION_V3_CHANGELOG.md` — summary of the hybrid-AI, cost-engineering and Phase 13 documentation revision.
- `DOCUMENTATION_V5_CHANGELOG.md` — summary of the program-governance, V1 coverage and traceability revision.
<!-- SPRINT_STANDARD_V3:END -->

<!-- HIERARCHY_V4:START -->
## V4 exhaustive repository and documentation hierarchy

Repository placement is now governed by three complementary canonical documents:

- `quality/56_COMPLETE_PRODUCTION_REPOSITORY_FILE_HIERARCHY.md` — exhaustive code/runtime/infra/test/AI/ML repository topology down to feature/module-level files.
- `quality/65_CANONICAL_DOCUMENTATION_TREE_AND_SOURCE_OF_TRUTH_MAP.md` — complete Markdown knowledge-base tree, source-of-truth precedence and reading bundles.
- `quality/66_FILE_PLACEMENT_OWNERSHIP_AND_DEPENDENCY_MATRIX.md` — deterministic artifact placement, ownership, tests, forbidden locations and ambiguity resolution.

For any Codex task that creates files, `quality/56` is mandatory context. For any ambiguous placement, `quality/66` must be consulted before creating a new directory.

V4 does not change the accepted API-first hybrid-AI strategy or Phase 13 gates; it makes their repository locations explicit.
<!-- HIERARCHY_V4:END -->

<!-- PROGRAM_GOVERNANCE_V5:START -->
## Program, V1 coverage, traceability, and release-governance additions

The Phase 1-13 sprint program is the initial production program, not the complete lifetime roadmap of the product. Future phases are added through evidence-driven program governance rather than arbitrary numbering.

New canonical governance documents:

- `roadmap/67_PRODUCT_PROGRAM_STRUCTURE.md`
- `roadmap/68_PRODUCTION_V1_CAPABILITY_COVERAGE_MATRIX.md`
- `quality/69_REQUIREMENTS_TRACEABILITY_MATRIX.md`
- `operations/70_RELEASE_TRAINS_AND_BACKLOG_PROMOTION.md`
- `operations/71_TECHNICAL_DEBT_AND_MAINTENANCE_REGISTER.md`

Any V1 feature implementation should map to a capability ID and at least one requirement ID before its sprint exits. Post-launch Phase 14+ work must follow the phase expansion policy in `operations/70_RELEASE_TRAINS_AND_BACKLOG_PROMOTION.md`.
<!-- PROGRAM_GOVERNANCE_V5:END -->
