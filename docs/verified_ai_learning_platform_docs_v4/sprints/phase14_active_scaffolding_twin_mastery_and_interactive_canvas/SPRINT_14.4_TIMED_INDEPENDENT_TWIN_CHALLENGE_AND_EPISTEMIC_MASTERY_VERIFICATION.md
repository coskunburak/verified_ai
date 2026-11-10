# Sprint 14.4 — Timed Independent Twin Challenge and Epistemic Mastery Verification

## Sprint mission

Deliver the end-to-end **60–120 Second Timed Independent Twin Challenge ("İkiz Soru Meydan Okuması")**, additive persistence migration (`PLANNED V040`), epistemic mastery separation (`INDEPENDENT_TWIN_VERIFIED` vs. `SCAFFOLD_ASSISTED`), and iOS Challenge UX so learners prove true retention immediately after learning a solution.

## Why this sprint exists

Without an immediate, unassisted transfer test, neither the student, parent, nor teacher knows whether a tutoring session produced real understanding or passive nodding. This sprint closes the loop: once a question is solved (or scaffolded via `SPRINT_14.2`), the system launches a locked 60–120s Isomorphic Twin Challenge. Passing it without hints awards full BKT-Lite (`bkt-lite-v2`) mastery credit and transitions the learner's Mistake DNA state toward `MASTERED_CLOSED`.

## Preconditions and dependencies

- `SPRINT_14.3` completed (`yks_isomorphic_twin_generator.py`).
- `Sprint 7.5` (`LearnerMistakeDnaProfile`) and `Sprint 7.6` (`bkt-lite-v2` mastery policy) contracts reviewed.

## In scope

- Additive Flyway migration `PLANNED V040__create_verified_twin_mastery_verifications.sql`.
- `TwinMasteryChallengeService.java` and `TwinChallengeController.java` enforcing the server-authoritative countdown window and anti-peek lock.
- Integration with `com.verifiedai.mastery` (`BktLiteV2MasteryCalculator`) and `com.verifiedai.mistake` (`MistakeDnaRecurrenceService`).
- Native iOS `TwinMasteryChallengeView.swift` with countdown ring, multiple-choice / symbolic answer input, and celebratory Mastery Badge animation upon verification.

## Explicitly out of scope

- Multi-day spaced repetition scheduling (already covered in `Sprint 8.3`).

## Deliverables

1. **Semantic deliverable** — `TwinVerificationOutcome` (`MASTERED_INDEPENDENT`, `ASSISTED_PARTIAL`, `FAILED_RETRY`, `EXPIRED_ABANDONED`) and server-side timer validation rules.
2. **Implementation deliverable** — `TwinMasteryChallengeService.java`, `V040` migration, and iOS `TwinMasteryChallengeView.swift`.
3. **Contract deliverable** — `/api/v1/twin-challenges/**` REST contracts and JSON schemas.
4. **Quality deliverable** — Integration tests proving that peeking at a hint or exceeding the lock window prevents `MASTERED_INDEPENDENT` credit.
5. **Operational deliverable** — Conversion funnel metrics (`twin_challenge_started` $\to$ `twin_challenge_mastered`).
6. **Documentation deliverable** — Synchronized with `learning/84` and `learning/93`.

---

<!-- LEARNING_OS_PRODUCTION_SPEC_V1:START -->
# Learning OS Production Implementation Specification v1 (`2026-11-11`)

## 1. Sprint Metadata & Status
- **Sprint ID:** `SPRINT_14.4` (Fast-track bridge alias: `SPRINT_7.8B-Part2`)
- **Canonical Title:** Timed Independent Twin Challenge and Epistemic Mastery Verification
- **Status:** `PLANNED` (`GATED_ON_SPRINT_14_3`)
- **Primary Spec Reference:** `learning/93_ACTIVE_SCAFFOLDING_TWIN_VERIFICATION_AND_INTERACTIVE_CANVAS_SPECIFICATION.md`, `learning/84_VERIFIED_TWIN_PRACTICE_AND_INDEPENDENT_LEARNING_ENGINE.md`

## 2. Strategic Learning OS Objective & User Value
Turns every passive question-lookup into an active **Mastery Proof**: *"Mantığı gerçekten kavradığını kanıtlamak için 90 saniyede şu ikiz soruyu tek başına çöz!"* Passing the twin challenge is the sole path to recover full mastery credit after using Tier 3/4 scaffolding hints.

## 3. Entry Gate & Preconditions
- `SPRINT_14.3` twin generator passing 100% of 600-case property tests.

## 4. Domain Invariants & Mathematical Rules
1. **Server-Authoritative Solution Lock (`INV-TWIN-104`):** During the active challenge window (`[issued_at, issued_at + time_limit_seconds]`), the twin's full solution is withheld on the server. Requesting the solution early sets `solution_revealed_early = true` and disqualifies the attempt from `MASTERED_INDEPENDENT`.
2. **Epistemic Mastery Promotion (`INV-TWIN-105`):**
   - If the parent problem reached `TIER_4_FULL_SOLUTION` (`0.0` initial mastery gain) and the learner passes the Isomorphic Twin with `MASTERED_INDEPENDENT` within the time budget, `bkt-lite-v2` records an `INDEPENDENT` positive evidence observation ($\Delta P(L) \approx +0.15$) and increments `half_life_days`.
   - If the learner fails the twin, the system does **not** punish with negative shaming; it pinpoints the step breakpoint and offers a 2nd isomorphic seed (`Retry Twin`).
3. **Cryptographic Verification Hash (`INV-TWIN-106`):** Every row in `verified_twin_mastery_verifications` stores the non-null `sympy_verification_hash` proving that both the generated twin and the learner's submitted answer were symbolically verified.

## 5. Owning Spring Modulith Backend Modules & Classes
- `services/api/src/main/java/com/verifiedai/attempt/application/TwinMasteryChallengeService.java`
- `services/api/src/main/java/com/verifiedai/attempt/domain/VerifiedTwinMasteryVerification.java`
- `services/api/src/main/java/com/verifiedai/attempt/web/TwinChallengeController.java`

## 6. Python Math Verifier Impact (`services/math-verifier/`)
- Uses `attempt_verification_v2.py` and `yks_isomorphic_twin_generator.py` to grade the learner's twin submission in `<80ms`.

## 7. Database Migration & Persistence Contract (`PLANNED V040`)
- **Migration File:** `services/api/src/main/resources/db/migration/platform/V040__create_verified_twin_mastery_verifications.sql` *(PLANNED)*
- **Tables:**
  - `verified_twin_mastery_verifications` (`id UUID PK`, `user_id UUID NOT NULL`, `parent_problem_session_id UUID NOT NULL`, `parent_attempt_id UUID NULL`, `twin_drill_id UUID NOT NULL REFERENCES counterfactual_twin_drills(id) ON DELETE CASCADE`, `family_code VARCHAR(64) NOT NULL`, `time_limit_seconds INT NOT NULL DEFAULT 90`, `time_spent_seconds INT`, `submitted_answer_latex TEXT`, `selected_option VARCHAR(8)`, `verification_outcome VARCHAR(32) NOT NULL`, `sympy_verification_hash VARCHAR(128) NOT NULL`, `mastery_delta_applied NUMERIC(5,4) NOT NULL DEFAULT 0.0000`, `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`).

## 8. REST API Contracts
- `POST /api/v1/problem-sessions/{sessionId}/twin-challenges` — Issues a new SymPy-verified Isomorphic Twin Challenge with server timestamp and countdown budget.
- `POST /api/v1/twin-challenges/{challengeId}/submit` — Submits the learner's option or symbolic expression; returns `verificationOutcome`, `masteryDeltaApplied`, updated skill mastery score, and full verified breakdown.

## 9. iOS (`apps/ios/VerifiedAI/`) Scope
- `apps/ios/VerifiedAI/Features/ProblemSolving/Presentation/TwinMasteryChallengeView.swift`
- `apps/ios/VerifiedAI/Features/ProblemSolving/Presentation/TwinMasteryChallengeViewModel.swift`
- `apps/ios/VerifiedAI/Features/ProblemSolving/Presentation/MasteryUnlockedBannerView.swift`

## 10. AI Capability & Unit Economics Guardrail
- `100%` deterministic generation and grading (`$0.00` LLM token cost per twin challenge).

## 11. Privacy, Safety & Compliance
- Object ownership verified (`user_id = currentUserId`); included in account deletion cascade (`StudentAttemptPrivacyLifecycleContributor`).

## 12. Telemetry & Observability
- `verifiedai.twin.challenge.started{family}`
- `verifiedai.twin.challenge.completed{family,outcome,time_spent_bucket}`
- `verifiedai.twin.mastery.promoted_count`

## 13. Feature Flags & Rollback Plan
- Feature flag: `verifiedai.attempt.timed-twin-challenge-enabled: false`.

## 14. Automated Test Matrix (Categories A–F)
- **Gate A (Domain & Anti-Cheat):** `TwinMasteryChallengeServiceTest` verifying clock-skew handling, early-reveal disqualification, and idempotent submission.
- **Gate B (Mastery Integration):** `TwinToMasteryPromotionIntegrationTest` verifying that `TIER_4` reveal (`0.0` gain) followed by `MASTERED_INDEPENDENT` twin pass awards exact `bkt-lite-v2` progression.
- **Gate C (iOS UI):** `TwinMasteryChallengeViewModelTests` covering countdown expiration, retry seed request, and VoiceOver announcements.

## 15. Owner-Only Acceptance Command
- `bash scripts/quality/sprint_14_4_owner_acceptance.sh --full`

## 16. Explicit Non-Goals
- Does not force a twin challenge when the user is in Timed Mock Exam Mode (`Phase 09`).

## 17. Exit Gate & Downstream Consumers
- Unlocks `SPRINT_14.5` (Misconception Root-Cause Diagnosis) and feeds the `Twin-Success` contextual paywall conversion moment (`roadmap/88`).

## 18. Canonical Cross-References
- `learning/84_VERIFIED_TWIN_PRACTICE_AND_INDEPENDENT_LEARNING_ENGINE.md`
- `learning/86_LEARNING_OUTCOMES_RETRIEVAL_PRACTICE_AND_MASTERY_EVALUATION.md`
- `learning/93_ACTIVE_SCAFFOLDING_TWIN_VERIFICATION_AND_INTERACTIVE_CANVAS_SPECIFICATION.md`
<!-- LEARNING_OS_PRODUCTION_SPEC_V1:END -->
