# Sprint 9.10 — Implementation Map (`SOURCE_IMPLEMENTED_PENDING_OWNER_ACCEPTANCE`)

- **Sprint:** `SPRINT_9.10` (Option A — Wave 5 of 5 / Phase 9 Exit Gate)
- **Canonical Baseline:** `main` @ `aaa69ab27e2f1d0098bd8e12257348b7c7e79bd1` (post-Sprint 9.9, Flyway `V063`)
- **Primary References:**
  - `docs/verified_ai_learning_platform_docs_v4/sprints/phase09_exam_and_assessment_platform/SPRINT_9.10_ASSESSMENT_INTEGRITY_ACCESSIBILITY_FAILURE_RECOVERY_AND_RELIABILITY.md`
  - `docs/verified_ai_learning_platform_docs_v4/sprints/phase09_exam_and_assessment_platform/PHASE_09_TYT_AYT_BRIDGE_AND_REMAINING_SPRINTS_PLAN.md`
  - `docs/verified_ai_learning_platform_docs_v4/adr/ADR-010_NATIVE_2D_MATH_RENDERING_AND_STEP_DIFF_HIGHLIGHTING.md`
  - `docs/verified_ai_learning_platform_docs_v4/domain/10_DOMAIN_INVARIANTS_AND_BUSINESS_RULES.md` (`INV-EXAM-006`)
  - `docs/verified_ai_learning_platform_docs_v4/architecture/90_LEARNING_OS_DOMAIN_CONTRACTS_AND_PLATFORM_INTEGRATION.md` (`CNT-LOS-10`, `CNT-LOS-11`)
  - `docs/verified_ai_learning_platform_docs_v4/quality/91_LEARNING_OS_PRODUCT_LEARNING_AND_RELEASE_ACCEPTANCE_GATES.md`

---

## 1. Subsystem Ownership & Boundary Table

| Subsystem | Owning Package / Path | Responsibility | Sprint 9.10 Files |
|---|---|---|---|
| Integrity Policy & Canonicalizer | `com.verifiedai.exam.domain`, `com.verifiedai.exam.application` | Canonical `exam-submit-hmac-v1` wire payload, HKDF-SHA256 session-scoped key derivation, constant-time HMAC-SHA256 verification, replay & skew enforcement | `ExamIntegrityCanonicalizer.java`, `AssessmentIntegrityGuard.java`, `ExamSessionRuntimeService.java`, `ExamProperties.java` |
| Persistence & Privacy Lifecycle | `com.verifiedai.exam.infrastructure.persistence`, `com.verifiedai.exam.infrastructure.privacy`, Flyway `platform` | Audit logs, atomic consumed nonces, child-first user purge, 30-day TTL purge, non-PII export | `V064__sprint_9_10_exam_integrity_audit_logs.sql`, `ExamIntegrityAuditRepository.java`, `ExamPrivacyLifecycleContributor.java` |
| HTTP Runtime & Admin Gate API | `com.verifiedai.exam.api` | Dual-mapped `/v1` & `/api/v1` session proof & submit signing DTOs; `ROLE_ADMIN` read-only quality gate endpoint (`no-store`) | `ExamSessionRuntimeController.java`, `AdminExamQualityGateController.java`, `ApiErrorCode.java` |
| Phase 9 Quality Gate Service | `com.verifiedai.exam.application` | Read-only Flyway `V054..V064` check, owner evidence manifest check (`CNT-LOS-10`, `CNT-LOS-11` P95 `< 350ms`), P0/P1 gate (`fail-closed` `BLOCKED` if missing) | `ExamQualityGateService.java` |
| iOS Domain, Keychain & Network | `apps/ios/VerifiedAI/Features/Exam/Domain`, `Data` | Proof response DTO, `ExamPendingSubmitIntent`, Keychain-backed session proof key storage, `CryptoKit` canonical HMAC signer, typed `ExamServiceError` | `ExamIntegrityModels.swift`, `ExamModels.swift`, `ExamAPIService.swift`, `ExamOfflineSQLiteStore.swift` |
| iOS Recovery & Accessibility | `apps/ios/VerifiedAI/Features/Exam/Presentation` | Durable submit intent reconciliation, scratchpad/outbox failure retention, monotonic countdown, bounded `tr-TR` Spoken Math VoiceOver & Dynamic Type | `ExamSessionViewModel.swift`, `ExamSessionView.swift`, `SpokenMathTurkishAccessibilityFormatter.swift` |
| Contracts, Fixtures & Quality Gates | `packages/schemas/exam/v1`, OpenAPI mirrors, `scripts/quality` | Draft 2020-12 schemas, negative mutations, dual OpenAPI equality, static Swift/l10n checks, owner acceptance runner | `exam-quality-gate.schema.json`, `exam-integrity-proof-response.schema.json`, `exam-session-submit-request.schema.json`, `validate_sprint_9_10_contracts.py`, `sprint_9_10_swift_check.sh`, `sprint_9_10_owner_acceptance.sh` |

---

## 2. Canonical Conflict Resolutions

1. **Schema Identifier:** Unified to `exam-quality-gate-v1` (`packages/schemas/exam/v1/exam-quality-gate.schema.json`) per `PHASE_09` §4.4; superseded `exam-quality-v1` in `SPRINT_9.10` spec.
2. **Admin Controller Package:** Placed in `com.verifiedai.exam.api.AdminExamQualityGateController` so `com.verifiedai.admin` does not depend across unexposed internal `exam` packages, preserving Spring Modulith architecture boundaries.
3. **Owner Acceptance CLI Flag:** Primary `--VerifyHost`, also accepting `--verify-host` and `--full` aliases (exits `2` on unknown flags).
4. **Python Math Verifier Boundary:** `services/math-verifier/` is untouched. Presentation-side semantic spoken speech preservation (`tr-TR`) is implemented and verified on iOS (`SpokenMathTurkishAccessibilityFormatter.swift` + 50 golden fixtures).
5. **Submit Packet vs Autosave Scope:** Current `POST /submit` sends `{submissionReason, clientTimestamp, trainingEligible}` while answers are persisted server-side via autosave (`ExamSessionRuntimeService`). `INV-EXAM-006` binds session ownership, `Idempotency-Key`, nonce, timestamp, canonical submit body, and server-side saved-items digest on submit; it does not falsely claim cryptographic tamper-proofing against a compromised local client device.

---

## 3. Security Threat Model & Trust Boundaries

- **Protected Threats:**
  - Cross-learner submission (BOLA): verified via authenticated JWT subject + session owner check before proof issuance and submit.
  - Transit/proxy alteration of `submit` request fields or path replay between users/sessions: bound by `exam-submit-hmac-v1` canonical LF-separated payload (`POST`, normalized `/v1/exams/sessions/{sessionId}/submit`, `userId`, `sessionId`, `submissionReason`, `Idempotency-Key`, UTC millisecond `clientTimestamp`, `keyId`, `nonce`, canonical body `SHA-256`).
  - Replay & concurrent duplicate submit: blocked by single-use `(session_id, nonce_hash)` primary key constraint in `exam_integrity_used_nonces` and DB-backed `SESSION_SUBMIT` idempotency check.
  - Client clock manipulation: `serverDeadlineAt` and `graceDeadlineAt` remain strictly server-authoritative (`Clock`); client skew outside `±30,000 ms` is rejected (`403 EXAM_INTEGRITY_CLOCK_SKEW_REJECTED`) when guard is enabled without extending deadlines.
- **Explicit Non-Protected / Out-of-Scope Threats:**
  - A compromised/jailbroken client holding its own ephemeral session proof key can re-sign forged parameters within allowed limits; therefore server-side deadline calculation, autosave persistence, and score computation remain the authoritative source of truth.
  - Invasive device proctoring, webcam recording, or third-party anti-cheat kernel drivers remain explicitly out of scope.

---

## 4. Feature Flag & Fail-Closed Rollout Matrix

| `integrity-guard-enabled` | Master Key State | Unsigned Submit | Signed Valid Submit | Invalid MAC / Unknown `kid` | Reused Nonce / Conflicting Idempotency |
|---|---|---|---|---|---|
| `false` (default) | Optional | `200` legacy compatible; no verified metric | Accepted via legacy submit flow; no verified metric | Legacy idempotency/validation only | `409` on conflicting `Idempotency-Key` |
| `true` | Valid (`>= 32` bytes) | `403 EXAM_INTEGRITY_SIGNATURE_REQUIRED` + audit row | `200` + `VERIFIED_SUBMIT` audit + nonce consumed + `exam_integrity_verified` metric | `403 EXAM_INTEGRITY_SIGNATURE_INVALID` + audit row (nonce NOT consumed) | `409 EXAM_INTEGRITY_REPLAY_CONFLICT` / `EXAM_SESSION_IDEMPOTENCY_CONFLICT` + audit row |
| `true` | Missing / `< 32` bytes | `503 EXAM_INTEGRITY_CONFIGURATION_UNAVAILABLE` (fail-closed) | `503` fail-closed | `503` fail-closed | `503` fail-closed |

---

## 5. Transaction & Recovery State Machine Boundaries

1. **Rejected Submit Audit Persistence:** `ExamSessionRuntimeService.submitSession` runs under `@Transactional(noRollbackFor = ApiProblemException.class)`. Audit rows (`SIGNATURE_REJECTED`, `CLOCK_SKEW_REJECTED`, `NONCE_REPLAY_REJECTED`) inserted prior to throwing `ApiProblemException` commit cleanly without terminating the active `ExamSession`.
2. **Idempotent Retry Ordering:** Check `SESSION_SUBMIT` idempotency record by `(session_id, idempotency_key)` first. Matching `payload_hash` returns the cached `200` receipt without consuming a second nonce or emitting duplicate audit/metrics/score events; mismatched `payload_hash` fails with `409`.
3. **iOS Durable Intent Lifecycle:**
   - States: `draft -> pendingUpload -> inFlight -> acknowledged` with `retryableFailure`, `pendingReconciliation` (on relaunch after `inFlight`), `rejected`, and `expired`.
   - Same `Idempotency-Key`, canonical body, `canonicalTimestamp`, `nonce`, `keyId`, and `signature` are persisted in `ExamOfflineSQLiteStore` (`exam_pending_submit_intents` table inside Application Support durable storage) while the proof key remains strictly isolated in `KeychainStore` (`SecureStorage`).
