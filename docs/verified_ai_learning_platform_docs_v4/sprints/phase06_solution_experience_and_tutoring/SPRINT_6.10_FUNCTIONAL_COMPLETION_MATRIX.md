# Sprint 6.10 functional completion matrix

Status: SOURCE_COMPLETE_PENDING_OWNER_ACCEPTANCE. Agent execution: NOT_RUN_BY_AGENT. Owner evidence: initial A/B/C/E/F/G PASS, subsequent iOS 257 PASS / 3 FAIL, then the 3 failed cases PASS in targeted run `20261106-135419-kQ3htO`. These separate source snapshots do not constitute full current-source acceptance.
Source review is not compilation, acceptance, device evidence or production authorization.

| ID | Workstream | Initial classification | Source delivery / reused behavior | Required acceptance |
|---|---|---|---|---|
| A | Dynamic Type | NEEDS_HARDENING | Complete: adaptive solve headings/graph actions; scrollable AX tutor composer; existing scaled native math and overflow scrolling retained | EN/TR AX XXXL UI tests; physical keyboard, landscape and all supported text sizes |
| B | VoiceOver | NEEDS_HARDENING | Complete: grouped negation/powers, explicit unsupported speech, retained interval inclusions/exclusions; native chart curve/sample labels and navigation hint; focus on terminal solve status; single hint focus delivery | MathPresentationTests and all critical UI tests; physical VoiceOver pronunciation/order/focus |
| C | Localization | NEEDS_HARDENING | Complete: EN/TR fallback/history/chart guidance; locale-aware sample/history display; count-neutral English grammar; full critical-prefix and literal-call inventory with placeholder parity | Gate G, EN/TR UI tests; bilingual mathematical glossary review |
| D | Motion and contrast | NEEDS_HARDENING | Complete: tutor Reduce Motion transaction; semantic system surfaces/text, readable light/dark status palettes and high-contrast fallback; system appearance restored | Device light/dark/high contrast and Reduce Motion review; no measured contrast claim |
| E | Recovery | NEEDS_HARDENING | Complete: pending tutor text/intent restored on authenticated refresh; per-page cancellation/account checks; bounded history. Existing exact keys, no reconnect submission, solve polling and source fences reused | TutorTests, PrimarySolveViewModelTests, SolveRecoveryStoreTests; offline/drop/timeout/relaunch scenarios |
| F | Performance | NEEDS_HARDENING | Complete: unchanged native LaTeX not reparsed; lazy bounded transcript; nearest sample avoids temporary flattened array; no global content cache | Authored 100-message and formatting measurement tests; oldest-device Instruments scenarios below |
| G | UX/invariants | ALREADY_IMPLEMENTED plus targeted hardening | Complete: preserved progressive disclosure, retry, hint/reveal confirmation, verified/unverified and saved/unassessed semantics, stable identifiers and Phase 7 bindings | Full iOS targets, Phase 6/7 backend, privacy, module and structured-work contracts |

## Remaining/external classification

- Source functionality intentionally left missing: none identified by this static review. No known in-scope defect is being hidden behind a flag.
- Tests: NOT_EXECUTED_BY_AGENT. Owner compilation/iOS execution and the three repaired tutor cases have recorded evidence; no recorded tutor failure remains unresolved. Full current-source acceptance and new runner selection self-tests remain unverified.
- EXTERNAL_VALIDATION_REQUIRED / BLOCKED_BY_EXTERNAL_GATE: physical-device VoiceOver, bilingual pronunciation, actual contrast, oldest-device performance, hosted Linux/CI and release evidence.
- NOT_APPLICABLE: new provider, paid inference, migration, API or durable draft schema. Unsent text remains view-model memory; only unresolved submitted work is durably recovered using the existing protected store.
- Existing unsupported visualization story-bridge and answer-reveal limitations remain explicit. They are not implemented by this hardening sprint.

## Owner device/performance protocol

Use synthetic fixtures only. Record device model, iOS build, app/source fingerprint, locale, text size, appearance, contrast/motion settings and thermal condition with each result. Attach captures/traces to the run directory; never include learner content.

| Scenario | Repeatable procedure | Measurements and acceptance |
|---|---|---|
| Transcript | 100 synthetic messages, EN/TR, default and AX XXXL; scroll end-to-end 10 times | Instruments hangs/hitches and memory; no hangs >250 ms, no inaccessible composer or lost draft |
| Math | Deep supported fraction/power/root expression and long exact literal; render at all text sizes | XCTest formatting measurement attachments plus Time Profiler; no clipping, no changed grouping; compare same-device baseline before assigning p95 budget |
| Graph | 100 Previous/Next actions across function/derivative, zoom and reset 20 times | Response timings, allocations and peak resident memory; samples remain approximate, holes remain excluded |
| Lifecycle | Present/dismiss each screen 20 times, interrupt pending fixture operation, relaunch/retry | Allocations/Memory Graph: no retained obsolete screen/model/task growth after settling; same pending key and one acknowledged operation |
| Keyboard | Hardware/software keyboard, portrait/landscape, AX XXXL | Every input/action/dismiss/retry reachable; focus order and hint/reveal distinction understandable |
| Appearance | Light/dark, increased contrast, Reduce Motion on/off | Text contrast target 4.5:1 normal / 3:1 large; state symbols and text remain sufficient without color/animation |

These are proposed acceptance thresholds/procedures, not measurements. Establish device baselines and attach owner observations; do not infer physical VoiceOver success from XCUITest.
