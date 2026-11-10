# Sprint 14.5 — Misconception Root-Cause Diagnosis and Prerequisite Repair Bridge

## Sprint mission

Implement the **Causal Misconception Root-Cause Diagnostic Engine (`PrerequisiteRootCauseAnalyzer`)** and **MEB Maarif Modeli / ÖSYM Kazanım Repair Bridge** so the platform distinguishes whether a learner's mistake stems from the target advanced topic or a foundational prerequisite skill (e.g., Grade 10 Polynomial Degree vs. Grade 9 Exponent Rules $2^n=4 \implies n=3$) and delivers a 45-second targeted repair micro-card.

## Why this sprint exists

When a student gets an AYT Polynomial or Calculus question wrong, generic solvers and score sheets record *"Polinomlar: 1 Yanlış"*. In reality, 60%+ of advanced math errors are triggered by foundational operational slips (exponents, signs, factoring, trigonometric identities) rather than the headline topic. Misattributing a Grade 9 exponent slip to Grade 10 Polynomials wastes the student's study hours. This sprint connects step-level mistake signatures (`V036`) to the Prerequisite DAG (`domain/46_CURRICULUM_SKILL_ONTOLOGY_AND_TAXONOMY.md`) to pinpoint and heal the true root cause.

## Preconditions and dependencies

- `SPRINT_14.4` completed.
- `Sprint 7.4` (`attempt_mistake_classifications` in `V036`) and `Sprint 7.6` (`bkt-lite-v2` prerequisite propagation) contracts reviewed.

## In scope

- Deterministic root-cause attribution rules (`PrerequisiteRootCauseAnalyzer.java`) mapping step-level mistake codes + step context to `(headlineSkillCode, rootCauseSkillCode, causalConfidence)`.
- MEB Maarif Modeli (9–12. Sınıf) & ÖSYM (`TYT`/`AYT`) Kazanım mapping table for all 28 mistake codes.
- 45-Second "Hap Özeti (Prerequisite Repair Micro-Card)" generator (`PrerequisiteRepairCardService.java`) with 1-tap inline verification.
- iOS `RootCauseDiagnosticBannerView.swift` (*"Polinom mantığın doğru! Ancak $2^n=4$ adımında 9. Sınıf Üslü Sayılar kuralında işlem hatası yaptın."*).

## Explicitly out of scope

- Multi-week study schedule rebalancing (handled in `Sprint 8.4`–`8.5`).

## Deliverables

1. **Semantic deliverable** — Distinction between `HEADLINE_CONCEPT_GAP` and `PREREQUISITE_SUBSKILL_SLIP` with formal BKT routing rules.
2. **Implementation deliverable** — `PrerequisiteRootCauseAnalyzer.java`, `PrerequisiteRepairCardService.java`, and iOS `RootCauseDiagnosticBannerView.swift`.
3. **Contract deliverable** — `rootCauseDiagnosis` block added to `/api/v1/attempts/{attemptId}/evaluation` and `/api/v1/tutoring/scaffolding-sessions/{sessionId}`.
4. **Quality deliverable** — Unit and integration tests verifying that a prerequisite slip penalizes the prerequisite skill rather than corrupting the headline topic mastery.
5. **Operational deliverable** — Root-cause vs. headline attribution ratio metrics.
6. **Documentation deliverable** — Synchronized with `learning/83` and `learning/93`.

---

<!-- LEARNING_OS_PRODUCTION_SPEC_V1:START -->
# Learning OS Production Implementation Specification v1 (`2026-11-11`)

## 1. Sprint Metadata & Status
- **Sprint ID:** `SPRINT_14.5`
- **Canonical Title:** Misconception Root-Cause Diagnosis and Prerequisite Repair Bridge
- **Status:** `PLANNED` (`GATED_ON_SPRINT_14_4`)
- **Primary Spec Reference:** `learning/93_ACTIVE_SCAFFOLDING_TWIN_VERIFICATION_AND_INTERACTIVE_CANVAS_SPECIFICATION.md`, `learning/83_VERIFIED_MISTAKE_DNA_AND_MISTAKE_PASSPORT_SPECIFICATION.md`

## 2. Strategic Learning OS Objective & User Value
Delivers surgical diagnostic clarity that no chatbot or video app provides: tells the learner, parent, and teacher *exactly which prior grade-level sub-skill* caused an error inside an advanced problem, and repairs it on the spot in 45 seconds.

## 3. Entry Gate & Preconditions
- `SPRINT_14.4` completed; `CurriculumTaxonomyCatalog` active.

## 4. Domain Invariants & Mathematical Rules
1. **Split Mastery Attribution (`INV-DIAG-001`):** When step $S_k$ is verified as valid on the headline concept (e.g., the student correctly set up $a \cdot 2^n = 4a$ in a polynomial degree problem) but fails on an embedded prerequisite operation ($2^n = 4 \implies n = 3$), the BKT update applies:
   - `0.0` negative penalty to the headline skill (`AYT.MAT.POL.DEGREE`),
   - Targeted negative slip observation to the prerequisite skill (`TYT.MAT.EXP.EQUATIONS`).
2. **Deterministic Kazanım Code Resolution (`INV-DIAG-002`):** Every diagnosed root cause maps to a canonical MEB Maarif / ÖSYM Kazanım identifier (e.g., `MEB.9.1.3.2` — *Üslü ifadeleri içeren denklemleri çözer*, `MEB.10.3.1.1` — *Polinom kavramını ve derecesini açıklar*).

## 5. Owning Spring Modulith Backend Modules & Classes
- `services/api/src/main/java/com/verifiedai/mistake/application/PrerequisiteRootCauseAnalyzer.java`
- `services/api/src/main/java/com/verifiedai/mistake/application/PrerequisiteRepairCardService.java`
- `services/api/src/main/java/com/verifiedai/mistake/domain/RootCauseAttribution.java`

## 6. Python Math Verifier Impact (`services/math-verifier/`)
- `services/math-verifier/app/verifiers/attempt_diagnosis.py`: tags each step operation with its atomic mathematical operator domain (`EXPONENT_SOLVE`, `RADICAL_SIMPLIFY`, `TRIG_DOUBLE_ANGLE`, `LOG_BASE_RULE`, `VIETA_SUM_PRODUCT`, `DERIVATIVE_POWER_RULE`) so root-cause separation is `100%` deterministic.

## 7. Database Migration & Persistence Contract
- Adds nullable columns `root_cause_skill_code VARCHAR(64)` and `attribution_kind VARCHAR(32)` (`HEADLINE_CONCEPT` | `PREREQUISITE_SLIP`) to `attempt_mistake_classifications` via additive `ALTER TABLE` in `V040`.

## 8. REST API Contracts
- Extends `GET /api/v1/attempts/{attemptId}/mistake-classification` and `POST /api/v1/tutoring/scaffolding-sessions/{sessionId}/respond` with:
  ```json
  "rootCauseDiagnosis": {
    "attributionKind": "PREREQUISITE_SLIP",
    "headlineSkillCode": "AYT.MAT.POL.02",
    "rootCauseSkillCode": "TYT.MAT.EXP.01",
    "mebKazanimCode": "MEB.9.1.3.2",
    "diagnosticSummaryTr": "Polinom derece eşitleme mantığın doğru, ancak 2^n = 4 üslü denkleminde 9. sınıf üslü sayı hatası yaptın.",
    "repairMicroCardId": "REPAIR-EXP-01"
  }
  ```

## 9. iOS (`apps/ios/VerifiedAI/`) Scope
- `apps/ios/VerifiedAI/Features/AttemptAnalysis/Presentation/RootCauseDiagnosticBannerView.swift`
- `apps/ios/VerifiedAI/Features/AttemptAnalysis/Presentation/PrerequisiteRepairMicroSheetView.swift`

## 10. AI Capability & Unit Economics Guardrail
- `100%` deterministic rule-and-AST attribution + pre-verified repair micro-cards (`$0.00` LLM token cost).

## 11. Privacy, Safety & Compliance
- Inherits `attempt_mistake_classifications` privacy and deletion lifecycle (`V036`).

## 12. Telemetry & Observability
- `verifiedai.diagnosis.root_cause.attributed{kind=HEADLINE_CONCEPT|PREREQUISITE_SLIP}`
- `verifiedai.diagnosis.repair_card.completed`

## 13. Feature Flags & Rollback Plan
- Feature flag: `verifiedai.mistake.prerequisite-root-cause-enabled: false`.

## 14. Automated Test Matrix (Categories A–F)
- **Gate A (Python Operator Domain Tagging):** `test_prerequisite_operator_tagging.py` across 40 multi-skill YKS problems.
- **Gate B (Backend Attribution & BKT Protection):** `PrerequisiteRootCauseAnalyzerTest` verifying that headline skill mastery is protected when a prerequisite slip occurs.
- **Gate C (iOS UI):** `RootCauseDiagnosticBannerViewTests`.

## 15. Owner-Only Acceptance Command
- `bash scripts/quality/sprint_14_5_owner_acceptance.sh --full`

## 16. Explicit Non-Goals
- Does not generate unverified external YouTube links.

## 17. Exit Gate & Downstream Consumers
- Unlocks `SPRINT_14.6` (Declarative Whiteboard Vector Stream) and enriches `MistakePassport` (`Sprint 7.7`).

## 18. Canonical Cross-References
- `learning/83_VERIFIED_MISTAKE_DNA_AND_MISTAKE_PASSPORT_SPECIFICATION.md`
- `learning/93_ACTIVE_SCAFFOLDING_TWIN_VERIFICATION_AND_INTERACTIVE_CANVAS_SPECIFICATION.md`
- `domain/46_CURRICULUM_SKILL_ONTOLOGY_AND_TAXONOMY.md`
<!-- LEARNING_OS_PRODUCTION_SPEC_V1:END -->
