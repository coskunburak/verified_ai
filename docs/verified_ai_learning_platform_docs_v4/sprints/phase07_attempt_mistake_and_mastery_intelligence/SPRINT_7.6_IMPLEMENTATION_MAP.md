# Sprint 7.6 Implementation Map — Evidence-Stratified Mastery Model (`bkt-lite-v2`) and Anti-Crutch Update Policy

## 1. Inspected Repository Baseline

- **Branch & Commit:** `main` at `48bf89804267e468a28209fcbf5dd0750d9c913e`.
- **Active Flyway Migration Head before Sprint 7.6:** `V037__learner_mistake_dispute_events.sql` (`V001`–`V023` and `V027`–`V037` on disk; `V024`–`V026` historical commercial-wedge placeholders superseded by `V030`–`V037`).
- **Next Sequential Flyway Version:** `V038__evidence_stratified_mastery_and_prerequisite_edges.sql`.
- **Prior Sprint 7.5 Acceptance State:** `SOURCE_COMPLETE_PENDING_OWNER_TEST` (only a `BLOCKED` FULL run `20261109-185846-gj0YdT` and a `TARGETED_REPAIR_PASSED` run `20261109-200633-1KasxL` exist in `.generated/sprint-7-5-owner`; no passing FULL Sprint 7.5 owner run yet).

## 2. Canonical References & Precedence

1. `domain/10_DOMAIN_INVARIANTS_AND_BUSINESS_RULES.md`
2. `adr/ADR-006_PRODUCTION_STUDENT_DATA_NOT_TRAINING_DATA_BY_DEFAULT.md`, `adr/ADR-009_CANONICAL_SOLUTION_SET_ALGEBRA_AND_DOMAIN_GUARD.md`, `adr/ADR-011_FOCUSED_V1_COMMERCIAL_LAUNCH_GATE_BEFORE_PHASE_08_13.md`
3. `learning/31_MASTERY_AND_KNOWLEDGE_GRAPH.md` and `learning/86_LEARNING_OUTCOMES_RETRIEVAL_PRACTICE_AND_MASTERY_EVALUATION.md`
4. `architecture/90_LEARNING_OS_DOMAIN_CONTRACTS_AND_PLATFORM_INTEGRATION.md` and `data/22_POSTGRESQL_DATA_MODEL.md`
5. `sprints/phase07_attempt_mistake_and_mastery_intelligence/SPRINT_7.6_MASTERY_MODEL_V1_AND_DETERMINISTIC_UPDATE_POLICY.md`

## 3. Existing-System Gap Analysis & Architectural Decisions

1. **Legacy `bkt-lite-v1` vs. Canonical Skill Ontology:** Existing `LearnerMasteryPolicy` (`bkt-lite-v1`) wrote `attempt-skill-v1:<MistakeCode>` rows from legacy disproof and twin events. Sprint 7.6 preserves `bkt-lite-v1` in `LegacyMasteryPolicy` / `LegacyMasteryWriter` when `verifiedai.mastery.evidence-stratified-enabled=false` and suppresses new `bkt-lite-v1` writes when `verifiedai.mastery.evidence-stratified-enabled=true` so dual-writing never occurs.
2. **Authoritative Assistance Provenance Boundary:** Solution disclosure occurs via `PrimarySolveService.get` (`solution_learning_disclosures`), hint usage via `tutor_hints`, and attempt/twin submission via `AttemptWorkService` / `AttemptLearningService`. Sprint 7.6 records immutable submission-time provenance in `attempt_mastery_context` (`solution_revealed`, `hints_used_count`, `provenance_complete`, `captured_at`). Historical records lacking `attempt_mastery_context` or with `provenance_complete=false` classify as `UNSUPPORTED` and never count as `INDEPENDENT`.
3. **Slip Parameter Discipline:** The repository taxonomy does not establish an authoritative `ARITHMETIC_SLIP` category mapping. Therefore `EvidenceStratifiedMasteryPolicy` uses the ratified Bayesian slip/guess/transition parameters ($P(L_0)=0.25$, $P(S)=0.08$, $P(G)=0.12$, $P(T_{\text{ind}})=0.35$, $P(T_{\text{twin}})=0.40$, $P(S_{\text{hint}})=0.12$, $P(G_{\text{hint}})=0.28$, $P(T_{\text{hint}})=0.18$) and never invents a `0.35` slip mapping for other categories.
4. **Demonstrated Independent Competence Denominator (`INV-LRN-COMP-001`):** $I_{u,s} = (n_{u,s}^{\text{ind}+} + 3.0 \times 0.25) / (n_{u,s}^{\text{total}} + 3.0)$, where $n_{u,s}^{\text{total}}$ is the 28-day exponentially decayed count ($\lambda_I = \ln(2)/28$) of all valid supported attempts (`INDEPENDENT`, `HINT_ASSISTED`, `SOLUTION_REVEALED_ASSISTED`) and $n_{u,s}^{\text{ind}+}$ counts only `INDEPENDENT` correct completions. Repeated assisted completions reduce $I_{u,s}$ toward `0` while `UNSUPPORTED` observations leave both numerator and denominator unchanged.
5. **Deterministic Dispute & OCR Correction Invalidation:** Rather than applying approximate inverse BKT transitions, database triggers (`invalidate_mastery_projection`) and `MasteryApplicationService` acquire the owner-scoped transactional lock (`store.lock(owner)`), reconstruct all valid `bkt-lite-v2` skill states and `learner_skill_mastery_transitions` from accepted, non-disputed, non-superseded `AttemptMasteryEvidenceSource` records in canonical `(observedAt, attemptId, evidenceId)` order, and clear stale skill projections.

## 4. Scope & Non-Goals

- **In Scope:** `bkt-lite-v2` mathematical policy, anti-crutch update rules (`0.78` cap for hint-assisted), `V038` migration, transition audit ledger (`learner_skill_mastery_transitions`), `GET /api/v1/mastery/skills`, iOS `Analyze My Work` mastery evidence presentation separating attempt correctness from skill mastery, EN/TR localization, VoiceOver + Dynamic Type support, privacy export/deletion, and fail-closed manual owner acceptance runner.
- **Out of Scope:** Delayed retrieval scheduling (`48h`/`7d`/`21d` — Sprint 7.7), full knowledge graph dashboard UX (Sprint 7.8/7.9), and daily study planner (Phase 8).

## 5. Module & File Inventory

| Layer | Path | Responsibility |
|---|---|---|
| Migration | `services/api/src/main/resources/db/migration/platform/V038__evidence_stratified_mastery_and_prerequisite_edges.sql` | Additive `bkt-lite-v2` columns on `learner_skill_mastery`, `solution_learning_disclosures`, `attempt_mastery_context`, `learner_skill_mastery_transitions`, and `mastery_prerequisite_edges`. |
| Mastery Domain/App | `services/api/src/main/java/com/verifiedai/mastery/LearningEvidenceType.java` | Canonical `INDEPENDENT`, `HINT_ASSISTED`, `SOLUTION_REVEALED_ASSISTED`, `UNSUPPORTED` enum. |
| Mastery Domain/App | `services/api/src/main/java/com/verifiedai/mastery/EvidenceStratifiedMasteryPolicy.java` | Pure deterministic `bkt-lite-v2` policy, Bayesian posterior, anti-crutch cap (`0.78`), twin transfer boost (`0.40`), $I_{u,s}$, $C_{u,s}$, half-life decay, and status classification. |
| Mastery Domain/App | `services/api/src/main/java/com/verifiedai/mastery/MasteryApplicationService.java` | Owner-locked deterministic recalculation, `GET /api/v1/mastery/skills` read projection, feature-flag gate (`verifiedai.mastery.evidence-stratified-enabled`). |
| Mastery Domain/App | `services/api/src/main/java/com/verifiedai/mastery/MasteryEvidenceSource.java`, `MasteryEvidenceChanged.java`, `LegacyMasteryPolicy.java`, `LegacyMasteryWriter.java` | Open-closed evidence port, domain event, and legacy `bkt-lite-v1` isolation. |
| Mastery API/Infra | `services/api/src/main/java/com/verifiedai/mastery/api/MasteryController.java`, `infrastructure/MasteryRepository.java`, `infrastructure/MasteryPrivacyLifecycleContributor.java` | Authenticated REST controller, JDBC persistence, and privacy export/deletion contributor. |
| Attempt & Solving | `services/api/src/main/java/com/verifiedai/attempt/application/AttemptMasteryEvidenceSource.java`, `AttemptLearningRepository.java`, `AttemptMistakeRepository.java`, `PrimarySolveService.java`, `SolveStore.java` | Immutable assistance provenance capture, verified twin lineage validation, and solution-disclosure tracking. |
| Contracts/Schemas | `packages/contracts/openapi/public-api.yaml`, `packages/schemas/mastery-skills-v1.schema.json`, `packages/schemas/fixtures/mastery-skills-v1.json`, `scripts/quality/check_mastery_contracts.py` | OpenAPI 3.1 endpoint, strict JSON Schema (`additionalProperties: false`), golden fixture, and contract validator. |
| iOS | `apps/ios/VerifiedAI/Features/AttemptAnalysis/Data/AttemptAnalysisAPI.swift`, `FixtureAttemptAnalysisAPI.swift`, `Domain/AttemptAnalysisModels.swift`, `Presentation/AnalyzeMyWorkCaptureView.swift`, `Presentation/AnalyzeMyWorkViewModel.swift`, `Resources/Localizable.xcstrings` | Typed mastery client, presentation models, `AnalyzeMyWorkCaptureView` mastery evidence card, retry/account-switch guards, and EN/TR strings. |
| Quality/Runner | `scripts/quality/sprint_7_6_owner_acceptance.sh`, `scripts/quality/sprint_7_6_evidence.py`, `services/math-verifier/tests/test_sprint_7_6_evidence.py` | Fail-closed single-command manual owner acceptance runner and synthetic evidence-writer tests. |

## 6. Database Schema & Migration Design (`V038`)

- Extends `learner_skill_mastery` with `independent_evidence_count`, `assisted_evidence_count`, `independent_competence_score`, `independent_pass_count`, `capped_by_assistance`, `evidence_fingerprint`, `projected_at`, and `last_confidence_evidence_at` with bounded check constraints (`ck_mastery_policy`, `ck_mastery_v2_counts`).
- Creates `solution_learning_disclosures`, `attempt_mastery_context`, `learner_skill_mastery_transitions`, and `mastery_prerequisite_edges` with immutability triggers (`protect_learning_artifact()`) and invalidation triggers (`invalidate_mastery_projection()`).

## 7. API Contract & Error Taxonomy

- `GET /api/v1/mastery/skills` returns `mastery-skills-v1` (`algorithmVersion: bkt-lite-v2`, `enabled`, `skills`, `outcomes`).
- Returns `401 AUTH_UNAUTHENTICATED` when unauthenticated and `enabled: false` with empty `skills`/`outcomes` when `verifiedai.mastery.evidence-stratified-enabled=false`.

## 8. iOS UX, Localization & Accessibility

- Renders the mastery evidence badge (`attempt.mastery.evidence`) inside `AnalyzeMyWorkCaptureView` with explicit separation between single-attempt correctness and cumulative skill mastery.
- Never renders an independent mastery claim for `HINT_ASSISTED`, `SOLUTION_REVEALED_ASSISTED`, or `UNSUPPORTED` evidence.
- Supports English and Turkish localization, VoiceOver combined accessibility elements, Dynamic Type, and account-switch/suspend stale-response generation fences.

## 9. AI, Verification & Cost Guardrails

- Zero LLM calls (`expectedNewInferenceCost = $0.00`). All mastery transitions are pure deterministic arithmetic over verified symbolic and classification evidence.

## 10. Privacy, Security & Training-Data Guardrails

- `trainingEligible: false` enforced across DB constraints (`attempt_mastery_context`, `learner_skill_mastery_transitions`), JSON Schema, and iOS decoders (`ADR-006`).
- `MasteryPrivacyLifecycleContributor` exports and deletes `bkt-lite-v2` skill rows, transition rows, mastery context rows, and solution disclosure rows under the account privacy lock.

## 11. Observability & Auditability

- Every `bkt-lite-v2` update records an immutable row in `learner_skill_mastery_transitions` with `prior_score`, `posterior_score`, `prior_half_life_days`, `posterior_half_life_days`, `evidence_type`, `policy_version`, `evidence_fingerprint`, and `provenance`.

## 12. Rollout, Feature Flag & Rollback Strategy

- Controlled by `verifiedai.mastery.evidence-stratified-enabled` (`MASTERY_EVIDENCE_STRATIFIED_ENABLED`, default `false`).
- Setting `MASTERY_EVIDENCE_STRATIFIED_ENABLED=false` immediately disables `bkt-lite-v2` projections without destructive schema rollback while preserving all `bkt-lite-v1` and `bkt-lite-v2` rows.
