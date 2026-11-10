# 91 — Learning OS Product, Learning, and Release Acceptance Gates

## Document Metadata

| Field | Value |
|---|---|
| **Document ID** | `DOC-91` |
| **Canonical Path** | `quality/91_LEARNING_OS_PRODUCT_LEARNING_AND_RELEASE_ACCEPTANCE_GATES.md` |
| **Title** | Learning OS Product, Learning, and Release Acceptance Gates |
| **Status** | `CANONICAL_QUALITY_AND_RELEASE_GOVERNANCE_SPECIFICATION` |
| **Owner** | Production Engineering Program Manager & Senior Mathematics Verification Engineer |
| **Date / Version** | `2026-11-07` / `v1.0.0` |
| **Baseline Commit** | `e39bafe88ea369f98c96a65497a94462dc79ef33` (`main`) |
| **Governing ADRs** | `adr/ADR-004_SEPARATE_PYTHON_MATH_VERIFIER.md`, `adr/ADR-006_PRODUCTION_STUDENT_DATA_NOT_TRAINING_DATA_BY_DEFAULT.md`, `adr/ADR-009_CANONICAL_SOLUTION_SET_ALGEBRA_AND_DOMAIN_GUARD.md`, `adr/ADR-011_FOCUSED_V1_COMMERCIAL_LAUNCH_GATE_BEFORE_PHASE_08_13.md` |

---

## 1. Purpose

This document defines the non-negotiable mathematical, pedagogical, privacy, operational, and commercial acceptance gates required to promote any **Verified AI Learning OS** sprint from `SOURCE_COMPLETE` to `LOCAL_ACCEPTANCE_PASSED`, `OWNER_ACCEPTED`, and `PRODUCTION_AUTHORIZED`.

In accordance with repository governance (`sprints/01_SPRINT_EXECUTION_STANDARD.md`), **implementation, documentation, local acceptance, owner sign-off, production release, and commercial validation are strictly separate states and must never be conflated.**

---

## 2. Sources and Evidence Classification

- `SOURCE_VERIFIED`: Grounded in `sprints/01_SPRINT_EXECUTION_STANDARD.md`, `quality/69_REQUIREMENTS_TRACEABILITY_MATRIX.md`, `operations/48_ANALYTICS_EVENT_CATALOG_AND_PRODUCT_METRICS.md`, `scripts/quality/docs_check.py`, and `scripts/quality/sprint_7_4_owner_acceptance.sh`.
- `EMPIRICAL_RESEARCH`: Grounded in learning outcome evaluation standards (Bastani et al., 2025; Dunlosky et al., 2013) and subscription funnel benchmarks (RevenueCat 2025–2026).
- `ENGINEERING_ESTIMATE`: Latency SLAs, golden dataset size thresholds, and Brier/ECE calibration bounds.

---

## 3. Existing Architecture Relationship

Every sprint in Verified AI already ships with an owner-executed acceptance runner (`scripts/quality/sprint_<phase>_<sprint>_owner_acceptance.sh --full`) verifying Categories A through F:
- **Category A:** Static Quality, Compilation & Architecture Boundaries (`ModularityTests`, lint, schema checks)
- **Category B:** Backend Domain, Service & Persistence Integration Tests (PostgreSQL + Flyway `V001`–`V036+`)
- **Category C:** Python Symbolic Verifier & Golden Mathematical Corpus Tests (`services/math-verifier/tests/`)
- **Category D:** iOS Client Build, View-Model & UI Snapshot/Flow Tests (`apps/ios/VerifiedAI/`)
- **Category E:** Privacy, Security, Spend-Guard & Fail-Closed Resilience Checks
- **Category F:** Documentation & Traceability Synchronization (`python3 scripts/quality/docs_check.py`)

This specification extends Categories A–F with **Learning OS Quality Gates (`Gates G-MATH, G-PED, G-PRIV, G-COM`)**.

---

## 4. Pillar 1: Mathematical Verification & Twin Soundness Gates (`G-MATH`)

No Learning OS feature may be enabled if it compromises symbolic mathematical truth:

| Gate ID | Gate Name | Metric Definition | Hard Acceptance Threshold | Verification Mechanism |
|---|---|---|---|---|
| **`G-MATH-01`** | **Zero False-Valid Step Acceptance** | Rate at which a mathematically invalid student step transformation ($S_{i-1} \not\Rightarrow S_i$) is marked `VALID` on the Golden Attempt Corpus. | **`0.0%` (`0` tolerances)** | Golden Attempt Evaluation Corpus (`services/math-verifier/tests/`) |
| **`G-MATH-02`** | **Valid Alternative Method Acceptance** | Rate at which a mathematically valid non-canonical student derivation path is accepted without false error flags (`V031` `attempt_verified_methods`). | **`>= 98.0%`** | Alternative-Method Golden Dataset (`Sprint 7.3` / `7.5`) |
| **`G-MATH-03`** | **First Meaningful Error Isolation** | Rate at which `first_error_step_index` (`V035`) pinpoints the exact first broken transformation while marking downstream algebraically consistent steps as `DOWNSTREAM_PROPAGATION`. | **`>= 96.0%`** (and **`0.0%`** downstream double-counting in Mistake DNA) | `AttemptEvaluation` + `MistakeDnaService` integration suite |
| **`G-MATH-04`** | **Counterexample Witness Validity** | Rate at which generated counterexamples $x = c$ (`counterexample_engine.py`) deterministically evaluate to finite unequal values on LHS vs RHS across $S_{i-1}$ and $S_i$. | **`100.0%`** | Symbolic & numeric substitution assertion prior to response return |
| **`G-MATH-05`** | **Verified Twin Structural Trap Preservation** | Rate at which generated Counterfactual Twins (`V031`, `learning/84`) have a SymPy-verified non-empty solution set AND preserve the target mistake hazard across all 8 math families. | **`100.0%`** (any candidate failing check is rejected fail-closed) | `test_attempt_learning_twins.py` + runtime `verification_hash` gate |
| **`G-MATH-06`** | **Fail-Closed Refusal Integrity** | Rate at which out-of-scope, illegible, or timed-out inputs return explicit `UNSUPPORTED` / `INSUFFICIENT_EVIDENCE` with zero mastery or Mistake DNA mutation. | **`100.0%`** | Refusal & Chaos Fault-Injection test suite |

---

## 5. Pillar 2: Pedagogical & Learning Outcome Gates (`G-PED`)

These gates ensure that the platform measures and produces **genuine, unassisted learning** rather than assisted homework completion:

| Gate ID | Gate Name | Metric Definition | Hard Acceptance Threshold | Verification Mechanism |
|---|---|---|---|---|
| **`G-PED-01`** | **Evidence-Stratified Mastery Separation** | Verification that `SOLUTION_REVEALED_ASSISTED` yields $\Delta M_{u,s} \le 0$ and `HINT_ASSISTED` never exceeds $M_{u,s} = 0.78$ without a subsequent `INDEPENDENT` pass. | **`100.0%` invariant compliance** | Property-based unit tests on `LearnerMasteryPolicy` (`Sprint 7.6`) |
| **`G-PED-02`** | **Mistake DNA Resolution Integrity** | Verification that a `RECURRING_PATTERN` mistake never transitions to `RESOLVED_PENDING_RETENTION` without $\ge 2$ consecutive `INDEPENDENT` passes (0 hints) on the target hazard, nor to `STABLE_RESOLVED` without a spaced pass $\ge 7\text{ days}$ later. | **`100.0%` state-machine compliance** | `MistakeDnaStateMachineTest` (`Sprint 7.5` / `7.8`) |
| **`G-PED-03`** | **Immediate Unassisted Twin Transfer Rate ($\text{UTTR}_{\text{imm}}$)** | Percentage of learners who, after viewing a First-Error Diagnosis + Counterexample, pass the immediately issued 60s-locked Verified Twin on their first `INDEPENDENT` attempt. | **`>= 55.0%`** (Cohort evaluation gate in `Sprint 11.9A` & `Phase 8`) | Cohort analytics query on `counterfactual_twin_drills` |
| **`G-PED-04`** | **Delayed 7-Day Trap Immunity Rate ($\text{DTIR}_{7\text{d}}$)** | Percentage of learners in `RESOLVED_PENDING_RETENTION` who pass their 7-day spaced retrieval check independently without repeating the target `mistake_code`. | **`>= 65.0%`** (Cohort evaluation gate in `Phase 8`) | Spaced review telemetry (`V042`) |
| **`G-PED-05`** | **Mastery & Readiness Psychometric Calibration** | Predictive accuracy of retention probability $R_{u,s}(t)$ against actual next unassisted attempt outcome $O_{u,a}$. | **Brier Score $\le 0.20$** AND **Expected Calibration Error ($\text{ECE}$) $\le 0.08$** | Automated calibration job (`Sprint 7.9` / `9.8`) |

---

## 6. Pillar 3: Privacy, Minor Safety & App Store Compliance Gates (`G-PRIV`)

| Gate ID | Gate Name | Hard Acceptance Requirement | Verification Mechanism |
|---|---|---|---|
| **`G-PRIV-01`** | **100% Privacy Lifecycle Contributor Coverage** | Every user-scoped table in `V001`–`V049+` is registered in `AccountPrivacyLifecycleContributor` and purged/exported within `< 500 ms` on account deletion/export. | `AccountDeletionCascadeIntegrationTest` |
| **`G-PRIV-02`** | **Apple Guideline `5.1.2(i)` Pre-AI Consent Gate** | Zero external LLM/Vision API requests (`OpenAI`, `Anthropic`, `Gemini`) can execute for a user without a persisted affirmative `AI_THIRD_PARTY_PROCESSING` consent record; revoking consent falls back cleanly to deterministic SymPy mode. | Integration test on `AiOrchestrationService` + iOS UI test |
| **`G-PRIV-03`** | **Zero PII / Chat / Photo Leakage in Share Packets** | Weekly Tutor/Parent PDF packets (`Sprint 8.6`) and linked Tutor views (`Sprint 12.7`) physically exclude raw `TutorTurn` chat logs, uncropped camera photos, email/phone, and time-of-day activity timestamps. | Contract schema assertion + RBAC security test (`security/89`) |
| **`G-PRIV-04`** | **On-Device Image Crop & EXIF Stripping** | 100% of uploaded problem/attempt images have GPS, device serial, and EXIF metadata stripped prior to network upload. | iOS/Android image upload pipeline unit test |

---

## 7. Pillar 4: Commercial, Retention & Operational Gates (`G-COM` / `ADR-011`)

Before unlocking Phase 8–13 full engineering scale (or promoting V1 to public launch), the platform must clear the **Focused Commercial Wedge Gate (`ADR-011`)**:

| Gate ID | Stage | Metric Definition | `GO` Threshold | `PIVOT` Band | `STOP / FREEZE` Condition |
|---|---|---|---|---|---|
| **`G-COM-01`** | `Sprint 11.9A` (50-Student TestFlight Beta) | **Cohort Day-7 Retention (`D7`)** (learners completing $\ge 1$ verified attempt or twin on Day 7+) | **`>= 20.0%`** | `10.0% – 19.9%` | `< 8.0%` after 2 UX iterations |
| **`G-COM-02`** | `Sprint 11.9A` (50-Student TestFlight Beta) | **Twin Challenge Engagement Rate** (attempts with a mistake diagnosis where learner submits the Verified Twin) | **`>= 35.0%`** | `20.0% – 34.9%` | `< 15.0%` |
| **`G-COM-03`** | `Sprint 6.2B + 11.9A` | **Blended Variable AI Inference Cost per Verified Attempt** | **`<= $0.0100`** (Target `$0.0058`) | `$0.0101 – $0.0180` | `> $0.0250` (requires immediate route/cache fix) |
| **`G-COM-04`** | `Sprint 11.9A` / `Sprint 10.2` | **Commercial Trial / Purchase Intent** (TestFlight WTP commitment or StoreKit trial start rate) | **`>= 5.0%`** of active testers | `2.5% – 4.9%` | `< 1.5%` |
| **`G-COM-05`** | Public V1 Launch (`Month 3–6`) | **Net Contribution Margin per Paid Subscriber** (after 15% store fee, refunds, and variable AI cost) | **`>= 68.0%`** | `50.0% – 67.9%` | `< 45.0%` |

---

## 8. Privacy-Safe Telemetry Schema Contract (`operations/48` Alignment)

All product and learning analytics events emitted to `com.verifiedai.analytics` obey three strict schema invariants:
1. **Pseudonymous Actor IDs Only:** Events carry `user_id` (internal UUID) and `session_id`; never raw email, full name, IP address, or Apple IDFA.
2. **Canonical Math Codes Only:** Learning events carry `canonical_skill_code`, `mistake_code`, `taxonomy_version`, `evidence_type`, `confidence`, and `latency_ms`; never raw user free-text chat inputs.
3. **Mandatory Learning OS Funnel Events:**
   - `attempt_submitted` -> `attempt_evaluated` (`first_error_step_index`, `overall_outcome`) -> `attempt_mistake_classified` (`mistake_code`, `confidence`) -> `twin_challenge_issued` (`variant_tier`, `generation_tier`) -> `twin_challenge_submitted` (`evidence_type`, `outcome`) -> `mistake_dna_state_transitioned` (`from_state`, `to_state`) -> `skill_mastery_updated` (`mastery_version`, `prior_score`, `posterior_score`, `half_life_days`).

---

## 9. Immediate Next Gate: Resolving Sprint 7.4 Owner Acceptance

At baseline commit `e39bafe88ea369f98c96a65497a94462dc79ef33`:
- `Sprint 7.4` (`Mistake Taxonomy & Classification Pipeline`) is `SOURCE_COMPLETE` / `PENDING_OWNER_TEST` (`PRODUCTION_NOT_AUTHORIZED`).
- Before accepting or releasing `Sprint 7.5`, the repository owner supplies genuine FULL Sprint 7.4 evidence. Isolated Sprint 7.5 source work is authorized while this gate remains open. The historical gate procedure is:
  1. Static documentation validation (`python3 scripts/quality/docs_check.py`).
  2. Full Sprint 7.4 owner acceptance (`bash scripts/quality/sprint_7_4_owner_acceptance.sh --full`) verifying that Categories A–F pass cleanly and updating `sprints/phase07_attempt_mistake_and_mastery_intelligence/SPRINT_7.4_EXECUTION_REPORT.md` with the fresh run ID.

---

## 10. Risks and Open Decisions

- **Risk `R-GATE-01` (Small Sample Variance in 50-Student Cohort):** With $N = 50$ students in `Sprint 11.9A`, a `20%` D7 rate has a $95\%$ Wilson confidence interval of roughly `[11.2%, 33.0%]`.
  - *Mitigation:* Pair quantitative cohort thresholds (`G-COM-01..04`) with structured 15-minute exit interviews with at least 12 active and 8 churned testers before recording the `ADR-011` `GO / PIVOT` decision.
- **Open Decision `OD-GATE-01`:** Minimum sample size of spaced retrieval attempts required per skill before enforcing `G-PED-05` (`ECE <= 0.08`) as a blocking CI release gate (recommended: $N \ge 200$ spaced retrieval events across the cohort; informational below $N = 200$).

---

## 11. Cross-References

- `sprints/01_SPRINT_EXECUTION_STANDARD.md`
- `sprints/02_FOCUSED_COMMERCIAL_WEDGE_SPRINT_PROGRAM.md`
- `quality/69_REQUIREMENTS_TRACEABILITY_MATRIX.md`
- `operations/48_ANALYTICS_EVENT_CATALOG_AND_PRODUCT_METRICS.md`
- `learning/83_VERIFIED_MISTAKE_DNA_AND_MISTAKE_PASSPORT_SPECIFICATION.md`
- `learning/84_VERIFIED_TWIN_PRACTICE_AND_INDEPENDENT_LEARNING_ENGINE.md`
- `learning/86_LEARNING_OUTCOMES_RETRIEVAL_PRACTICE_AND_MASTERY_EVALUATION.md`
- `roadmap/87_LEARNING_OS_STRATEGIC_PHASE_AND_SPRINT_EXPANSION_PROGRAM.md`
- `operations/92_LEARNING_OS_ASSUMPTIONS_EXPERIMENTS_AND_EVIDENCE_LEDGER.md`

## Sprint 7.5 owner-only verification

Current status is SOURCE_COMPLETE_PENDING_OWNER_TEST. The single runner `scripts/quality/sprint_7_5_owner_acceptance.sh --full`
records fresh A–G results, source fingerprints, raw logs and cleanup in `.generated/sprint-7-5-owner/<run-id>/`.
It checks original FULL Sprint 7.4 evidence against committed runtime/test/contracts; targeted repairs cannot satisfy entry.
No old acceptance output is rewritten or imported as a current test result. Missing entry evidence, tools or required tests
returns BLOCKED/nonzero; failing checks return FAIL/nonzero. Independent gates continue to produce useful owner evidence.
The authored local 30-attempt synthetic rollback p95 test targets <250 ms; staging/production latency and cohort accuracy
remain separate unverified gates. No numerical classification confidence or mastery/remediation success is inferred.
Agent status: NOT RUN — MANUAL OWNER EXECUTION REQUIRED.
