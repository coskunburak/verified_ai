# Sprint 9.7 Execution Report

Date: 2026-11-17. Status: **COMPLETED — OWNER HOST ACCEPTANCE PASSED**.

## Delivered implementation

- V061 adds attribution and rebalance audit tables with foreign keys, uniqueness, zero-token checks, and training-ineligible checks. The FK uses the actual V058 `exam_session_item_evaluations(evaluation_id)` signature. The evidence default is `MASTERY_THRESHOLD`, which satisfies its CHECK constraint.
- Deterministic omission → diagnosed/distractor/DNA trap → low mastery → timing classification; null evidence and missing mastery use explicit fallbacks. The live V057 scratchpad schema stores attachment metadata only; no OCR diagnosis is fabricated. The pure classifier accepts diagnosed codes for a future trusted diagnosis adapter.
- H-03 uses BigDecimal arithmetic, the specified success-probability formula and rounding, and ROI/lost-net/skill tie-break ordering. All session items, including correct responses, contribute to skill success rates.
- Dual-path REST reports and synchronous `exam::events` StudyPlan port. Per-learner transaction advisory locks serialize generation/rebalance; same-key replay uses a persisted response, and changed request bodies return 409. Feature flags default off. Responses use no-store, principal-derived ownership, and localized TR/EN rationale.
- The existing planner is a seven-day sequence of DailyCoachSession records. Rebalance replaces only pending slots, preserves completed sessions/tasks, and retains the existing 25-minute/day and three-slot caps. `preferredDailyMinutes` is a ceiling; a request for 45 minutes does not override the smaller coach cap. `rebalancedStudyPlanId` identifies the first affected daily session, anchoring that schedule. Recovery totals are limited to scheduled minutes.
- Privacy export includes both new tables; deletion removes attribution and batch rows before scoring/session data.
- CNT-LOS-10/11 JSON Schemas, positive and negative fixtures, and both mirrored OpenAPI specs. New components are inline in the mirrors so relative references do not change meaning between spec locations.
- iOS Codable models, API calls, user-scoped SQLite cache, online-first/offline-only fallback, stable rebalance key and double-tap protection, privacy-minimal telemetry, score-summary navigation, and a flat Concept B screen with accessibility-size vertical layouts.
- Native remediation is presented directly from `PostExamAttributionView` through `PostExamRemediationDestination`. Twin recommendations open `PracticeSessionView` in `TRAP_VERIFICATION_DRILL`; evidenced mistake codes open `MicroLessonDrawerView`. Uncoded conceptual, timing and triage recommendations open existing `EXAM_ROI_SYNTHESIS` skill practice. No URL handler is required. The score-summary entry point retains the authenticated API instance and owner/session context; the readiness link remains available alongside it.
- Native practice start/submit/abandon and micro-lesson fetch/complete use the authenticated API client. Practice callbacks check response owner/session/skill and avoid the existing simulated-session defaults. A fresh in-memory practice cache isolates each remediation sheet. The existing user-scoped micro-lesson cache remains available; authorization and integrity failures cannot trigger its offline fallback. New native requests opt into ISO-8601 date decoding (whole/fractional seconds); other API decoding is unchanged.
- The native practice wrapper exposes load/submission errors, retry, countdown and dismissal after abandonment. Route selection ignores unknown report items and duplicate taps. The new destination is registered in the Xcode Sources build phase. No unrelated Tutor/Practice implementation files were modified.

## Bridge B4 evidence

- Exported 120 unique V060 static synthetic records. 3 records were rejected by the bounded math verifier. No student table or database connection is used.
- JSONL SHA-256: `ba8c8039f8ea09781582066397b845a90d43e9c82aa1d6fd18387779619413dc`.
- Verification scope is explicitly **answer-option equivalence only**: symbolic equality, final-answer linkage, and distractor inequality. Natural-language derivations are not claimed as verified steps.
- The golden corpus is evaluation-only / NOT_ELIGIBLE and is excluded from training exports. Its license is not silently changed.
- The Qwen summary is produced through `summarize_qwen_evaluation.phase13_baseline_gate`. It records zero model observations and null accuracy/equivalence/trap metrics, with `REQUIRES_MORE_EVIDENCE` and no SFT/LoRA entry approval. No real model inference was performed.

## Owner verification evidence (2026-11-17)

The owner executed the tests locally under the human-in-the-loop protocol. The agent did not run tests/builds during the final repair and closure turns.

| Check | Result |
| --- | --- |
| JSON Schema / OpenAPI / contract inventory | Owner reported PASS |
| Python clean-seed exporter | 120 records exported; 23 tests passed |
| Backend attribution / ROI / V061 / StudyPlan / Modulith | 16 tests passed |
| PostgreSQL integration | Ownership/flags test passed; both remaining methods passed after repairs (2 tests, 0 failures/errors/skips) |
| iOS XCTest | Owner reported TEST SUCCEEDED for runs at 13:48:59 and 13:51:27 |
| Full owner acceptance | SPRINT_9_7_HOST_ACCEPTANCE_PASSED |
| Documentation | documentation check passed: 430 Markdown files |

The integration repairs aligned repository/service/test injection with the runtime Jackson 3 JsonMapper and planned daily-coach tasks before flushing a parent with a positive task count. Child rows are saved after the parent to preserve the FK. Closure edits only record these results and refresh documentation metrics; the documentation gate was not rerun after those edits.

Phase 13 model entry remains unapproved: the Qwen baseline is REQUIRES_MORE_EVIDENCE. Sprint acceptance does not establish model quality or authorize training.

## Reproduction and remaining acceptance

Run `bash scripts/quality/sprint_9_7_owner_acceptance.sh --VerifyHost` and `python3 scripts/quality/docs_check.py`.
The owner script fails when Docker or CoreSimulator is unavailable. `--quick` labels its result `LOCAL_CHECKS_PASSED_HOST_NOT_VERIFIED` and is not full acceptance.

Sprint 9.7 is closed on the owner-reported acceptance evidence above. The Swift script parses the listed views/tests and typechecks model/API dependencies; the required Xcode host gate compiles native views with the real SwiftMath package and executes XCTest. AX3 hosting is a smoke test, not a pixel-level clipping or VoiceOver audit. Real Qwen evidence is separately required before approving Phase 13 model entry. No production feature flags were enabled, no secrets were read, and no Sprint 9.8–9.10 work was introduced.


## Owner command sequence (repository root)

Use the existing math-verifier virtual environment for pytest/SymPy. Run in order; stop at the first failure and return only its failing test/output. Subsequent retries target that test/class only.

```bash
cd /Users/burakcoskun/Desktop/ai_verification_tutor
python3 scripts/quality/validate_json_schemas.py
python3 scripts/quality/check_contracts.py
services/math-verifier/.venv/bin/python3 evaluations/runners/export_yks_phase13_clean_seed.py
services/math-verifier/.venv/bin/python3 -m pytest -q evaluations/tests/test_yks_phase13_clean_seed_export.py
python3 scripts/quality/docs_check.py
```

```bash
mvn -o -f services/api/pom.xml test -Dtest=ExamPostAssessmentAttributionServiceTest,ExamNetGainRoiSimulatorServiceTest,V061MigrationSqlConsistencyTest,ExamAttributionStudyPlanListenerTest,ModulithArchitectureTest
mvn -o -f services/api/pom.xml test -Dtest=ExamAttributionIntegrationTest
```

```bash
bash scripts/quality/sprint_9_7_swift_check.sh
xcodebuild test -project apps/ios/VerifiedAI.xcodeproj -scheme VerifiedAI \
  -derivedDataPath services/api/target/sprint97/DerivedData \
  -destination 'platform=iOS Simulator,name=iPhone 16 Pro' \
  -only-testing:VerifiedAITests/PostExamAttributionViewModelTests
```

After the individual stages pass, run the requested aggregate gates once:

```bash
bash scripts/quality/sprint_9_7_owner_acceptance.sh --VerifyHost
python3 scripts/quality/docs_check.py
```

If the installed simulator has another name, use that destination in the Xcode command and pass the same value through `IOS_SIM_DESTINATION` to the owner acceptance script. Do not use `--quick` as evidence of host acceptance. Maven uses this repository's `pom.xml`; Gradle is not used.

