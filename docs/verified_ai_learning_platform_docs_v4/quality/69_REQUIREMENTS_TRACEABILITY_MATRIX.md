# Requirements Traceability Matrix

## Purpose

This document maps non-negotiable requirements to source-of-truth documents, implementation phases, acceptance evidence, and operational evidence. It is the bridge between product intent and production verification.

Every high-impact feature PR should either reference an existing requirement ID or add a new requirement here in the same change.

## Status Values

- `Satisfied` means implemented and validated.
- `Foundation` means the platform foundation exists but product behavior is not complete.
- `Planned` means required for Production V1 and not implemented yet.
- `Conditional` means gated by later evidence.

## Core Requirements

| Requirement ID | Requirement | Source of truth | Owning phase/sprints | V1 required | Acceptance evidence | Telemetry/operations evidence | Current status |
|---|---|---|---|---:|---|---|---|
| REQ-TRUST-001 | AI output is untrusted input and cannot assign trusted truth by itself. | `domain/10_DOMAIN_INVARIANTS_AND_BUSINESS_RULES.md` | Phase 5 | Yes | Domain tests prevent direct `VERIFIED` assignment from solver output. | Verification status distribution and false-verified incident runbook. | Planned |
| REQ-VERIFY-001 | Only VerificationPolicy may assign overall `VERIFIED`. | `domain/10_DOMAIN_INVARIANTS_AND_BUSINESS_RULES.md`, `ai/27_VERIFICATION_ENGINE.md` | Sprint 5.7 through Sprint 5.11 | Yes | VerificationPolicy tests, deterministic contradiction tests, arbitration tests. | `verification_success_rate`, false-verified alerts, audit trace. | Planned |
| REQ-VERIFY-002 | `PARTIALLY_VERIFIED` and `UNVERIFIED` must explain what was and was not checked. | `ai/27_VERIFICATION_ENGINE.md` | Sprint 5.11, Sprint 6.2 | Yes | API/UI contract tests for uncertainty copy and evidence shape. | Unverified rate by problem type and verifier method. | Planned |
| REQ-MATH-001 | Math verifier is internal-only; mobile clients never call it directly. | ADR-004, `domain/10_DOMAIN_INVARIANTS_AND_BUSINESS_RULES.md` | Phase 2, Phase 5 | Yes | Network/API tests show verifier protected by internal token and API-owned access path. | Verifier request counts only from internal network/client identity. | Foundation |
| REQ-MATH-002 | Symbolic verification must enforce parser allowlists, complexity limits, timeouts, and controlled errors. | `domain/47_MATHEMATICAL_CANONICAL_REPRESENTATION.md`, ADR-004 | Phase 2, Sprint 5.8 through Sprint 5.10 | Yes | Parser/resource-limit tests, timeout/error-shape tests. | Verifier timeout/error metrics and logs without raw student content. | Foundation |
| REQ-AI-001 | AI provider integrations are isolated behind provider-neutral ports/adapters. | ADR-003, ADR-005, `ai/25_AI_ORCHESTRATION_AND_MODEL_ROUTING.md` | Sprint 5.1 | Yes | Architecture tests reject provider SDK imports in domain/application modules. | Route/provider provenance logged as operational metadata. | Planned |
| REQ-AI-002 | Secondary solving is conditional escalation, not unconditional per-question cost. | `ai/57_API_FIRST_HYBRID_AI_STRATEGY_AND_MODEL_EVOLUTION.md`, `ai/60_AI_UNIT_ECONOMICS_AND_INFERENCE_COST_ENGINEERING.md` | Sprint 5.5, Sprint 10.10 | Yes | Policy tests prove escalation triggers and non-trigger paths. | Secondary escalation rate, cost per verified solve. | Planned |
| REQ-EVAL-001 | High-impact prompt/model/router changes require golden-dataset regression evaluation before promotion. | `ai/28_AI_EVALUATION_AND_GOLDEN_DATASET.md` | Sprint 5.12 and model-route releases | Yes | Evaluation report attached to release artifact. | Quality regression dashboard and rollback record. | Planned |
| REQ-ID-001 | Sign in with Apple identity tokens must be verified server-side. | `ios/18_IOS_AUTH_STOREKIT_AND_DEVICE_SECURITY.md` | Sprint 3.1 | Yes | Signature, issuer, audience, nonce, expiry, replay tests. | Auth success/failure metrics without token logging. | Planned |
| REQ-AUTH-001 | Access and refresh sessions require rotation, revocation, and reuse detection. | `backend/21_AUTHORIZATION_RATE_LIMITING_AND_ABUSE.md` | Sprint 3.2 | Yes | Token rotation/reuse/revocation tests. | Suspicious reuse alerts and session audit records. | Planned |
| REQ-AUTH-002 | Authorization is checked per resource and never inferred from UUID opacity. | `domain/10_DOMAIN_INVARIANTS_AND_BUSINESS_RULES.md`, `security/35_SECURITY_THREAT_MODEL.md` | Phase 3 onward | Yes | Resource access tests for every protected endpoint. | Authorization-denied metrics by route and principal type. | Planned |
| REQ-PROFILE-001 | User identity/account state and LearningProfile state are separate aggregates. | `domain/07_DOMAIN_MODEL_AND_AGGREGATES.md` | Sprint 3.3 | Yes | Data/model tests show profile updates cannot mutate identity authority. | Profile completion funnel and validation errors. | Planned |
| REQ-BILL-001 | Client cannot grant entitlement; backend is authoritative. | `product/05_MONETIZATION_AND_ENTITLEMENTS.md` | Sprint 3.4, Sprint 10.2 | Yes | Server entitlement guard tests and tampered-client tests. | Entitlement decision logs and quota-denial metrics. | Planned |
| REQ-BILL-002 | Subscription lifecycle must use explicit states, not only `active=true`. | `product/05_MONETIZATION_AND_ENTITLEMENTS.md` | Sprint 3.4, Sprint 3.6 | Yes | State-machine transition tests for active, grace, retry, expired, revoked. | Billing notification backlog and reconciliation metrics. | Planned |
| REQ-PRIV-001 | Raw student content is not logged or sent to analytics by default. | `security/36_PRIVACY_AND_STUDENT_DATA_PROTECTION.md` | All feature phases | Yes | Logging/analytics tests and code review evidence. | Redaction checks, analytics schema review. | Planned |
| REQ-PRIV-002 | Account deletion/export must cover identity, assets, learning state, billing metadata retention, and AI operational metadata. | `data/23_DATA_LIFECYCLE_RETENTION_AND_AUDIT.md`, `security/36_PRIVACY_AND_STUDENT_DATA_PROTECTION.md` | Sprint 3.7, Sprint 11.5 | Yes | End-to-end deletion/export tests and retention exceptions documented. | Deletion job status, failure alerts, audit trail. | Planned |
| REQ-DATA-001 | PostgreSQL is the durable source of truth. | ADR-001, `data/22_POSTGRESQL_DATA_MODEL.md` | Phase 2 onward | Yes | Flyway/Testcontainers integration tests and schema review. | DB readiness, migration status, query/pool metrics. | Foundation |
| REQ-DATA-002 | Redis is disposable infrastructure and cannot own canonical learning/billing state. | `data/24_CACHING_STORAGE_AND_FILE_ASSETS.md` | Phase 2 onward | Yes | Architecture review and tests for cache-miss recovery. | Redis outage runbook and cache metrics. | Foundation |
| REQ-PROBLEM-001 | ProblemAsset is not the canonical Problem. | `domain/10_DOMAIN_INVARIANTS_AND_BUSINESS_RULES.md` | Sprint 4.2 through Sprint 4.6 | Yes | Data/API tests distinguish asset, raw recognition, parse, canonical problem. | Asset processing traces and parse status metrics. | Planned |
| REQ-PROBLEM-002 | User parse corrections create explicit revisions; historical parses are not silently overwritten. | `domain/10_DOMAIN_INVARIANTS_AND_BUSINESS_RULES.md` | Sprint 4.8 | Yes | Revision immutability tests and selected-parse solving tests. | Parse correction rate by source/problem type. | Planned |
| REQ-ATTEMPT-001 | Student attempt is never replaced by the reference solution. | `domain/10_DOMAIN_INVARIANTS_AND_BUSINESS_RULES.md` | Sprint 7.1 through Sprint 7.3 | Yes | Attempt immutability tests and comparison flow tests. | Attempt evaluation traces and mistake evidence metrics. | Planned |
| REQ-MASTERY-001 | Mastery belongs to User x Skill and stores score, confidence, evidence count, and algorithm version. | `learning/31_MASTERY_AND_KNOWLEDGE_GRAPH.md` | Sprint 7.6 through Sprint 7.8 | Yes | Mastery update tests and migration constraints. | Mastery update counts by algorithm version and skill. | Planned |
| REQ-ADAPT-001 | Study planner recommends supported eligible skills and cannot mark skills mastered. | `learning/32_ADAPTIVE_LEARNING_AND_STUDY_PLANNER.md` | Sprint 8.1 through Sprint 8.5 | Yes | Planner policy tests and unsupported-skill exclusion tests. | Recommendation acceptance, completion, and override metrics. | Planned |
| REQ-OPS-001 | Every request/job uses correlation/trace identifiers across iOS, API, database, verifier, and future AI providers. | `operations/37_OBSERVABILITY_SLOS_AND_INCIDENTS.md` | Phase 2 onward | Yes | Correlation propagation tests and log review. | Trace search by Problem ID and correlation ID. | Foundation |
| REQ-OPS-002 | Risky releases require feature flag, rollout plan, rollback plan, and monitoring. | `operations/49_FEATURE_FLAGS_REMOTE_CONFIG_AND_EXPERIMENTATION.md` | Sprint 10.7 onward | Yes | Release checklist and kill-switch tests. | Rollout metrics and rollback audit entries. | Planned |
| REQ-LAUNCH-001 | Production V1 launch requires beta evidence, App Store readiness, privacy manifest review, and rollback gates. | `operations/38_CI_CD_ENVIRONMENTS_AND_RELEASES.md` | Sprint 11.9 through Sprint 11.12 | Yes | TestFlight, release-candidate, App Store and rollout evidence package. | Launch dashboard, incident channel, rollout health gates. | Planned |
| REQ-ML-001 | Production student content is not training data by default. | ADR-006, `data/62_TRAINING_DATA_LINEAGE_CONSENT_AND_DATASET_PIPELINE.md` | Phase 13 conditional | Conditional | Data eligibility and lineage checks before dataset creation. | Dataset audit logs and revocation records. | Conditional |
| REQ-ML-002 | Proprietary/self-hosted models require quality, cost, capacity, privacy, security, and rollback evidence before promotion. | ADR-005, ADR-007, documents 57-64 | Phase 13 conditional | Conditional | Offline, shadow, canary, TCO and rollback evidence. | Model registry state and route canary metrics. | Conditional |

## PR Usage Rule

Every PR that touches a V1-required feature must cite at least one `REQ-*` ID in its description or implementation evidence. If no requirement exists, the PR must add one before implementation is considered complete.

## Coverage Consistency Rule

Every `V1 required = Yes` requirement must map to at least one capability in `roadmap/68_PRODUCTION_V1_CAPABILITY_COVERAGE_MATRIX.md`. Every V1-required capability must have at least one requirement ID before its implementation sprint exits.

