# SPRINT 14.10 — Phase 14 Production Hardening, Accessibility & Release Exit Gate

| Field | Value |
|---|---|
| **Sprint ID** | `SPRINT_14.10` |
| **Phase** | [PHASE_14_ACTIVE_SCAFFOLDING_TWIN_MASTERY_AND_INTERACTIVE_CANVAS.md](PHASE_14_ACTIVE_SCAFFOLDING_TWIN_MASTERY_AND_INTERACTIVE_CANVAS.md) (`Phase 14`) |
| **Canonical Spec** | [93_ACTIVE_SCAFFOLDING_TWIN_VERIFICATION_AND_INTERACTIVE_CANVAS_SPECIFICATION.md](../../learning/93_ACTIVE_SCAFFOLDING_TWIN_VERIFICATION_AND_INTERACTIVE_CANVAS_SPECIFICATION.md), [70_RELEASE_TRAINS_AND_BACKLOG_PROMOTION.md](../../operations/70_RELEASE_TRAINS_AND_BACKLOG_PROMOTION.md) |
| **Primary Pillar** | Production Readiness — Chaos Resilience, Accessibility (`VoiceOver` / `Reduced Motion`), Security Audit & Phase 14 Release Gate |
| **Status** | `SPECIFIED_PRODUCTION_READY` |

---

## 1. Sprint Goal & Executive Summary

**Sprint 14.10** is the formal **Production Hardening, Accessibility, Security, and Release Exit Gate** for **Phase 14**. No Phase 14 capability may be promoted to 100% production traffic until every automated gate, chaos resilience drill, accessibility check, and owner acceptance check defined in this sprint passes with **zero open P0/P1 defects**.

Key deliverables:
1. **End-to-End Load & Chaos Resilience Verification**:
   - Sustains `500 concurrent active scaffolding + interactive canvas sessions` with `p95 < 250 ms` for tier transitions, `p95 < 150 ms` for parametric twin generation, and graceful degradation when external TTS or LLM providers experience 100% outage.
2. **Full Accessibility & Inclusive Pedagogy Compliance (`WCAG 2.2 AA` & iOS Accessibility)**:
   - Full `VoiceOver` math tree navigation (`AXCustomContent`), `UIAccessibility.isReduceMotionEnabled` instant-step mode for the vector canvas, Dynamic Type (`xSmall` to `AX5`), and hearing-impaired synchronized Turkish closed-caption mode.
3. **Security, Anti-Cheat & KVKK / Child-Safety Audit**:
   - Confirms server-authoritative anti-spoiler enforcement (`TIER_1`–`TIER_3`), timed twin anti-tamper verification (`SPRINT_14.4`), ephemeral handwritten ink processing (`SPRINT_14.8`), and zero secret exposure.
4. **Canonical Phase 14 Gate Scripts**:
   - Sandboxed agent verification script (`scripts/sprints/phase14/sprint_14_10_verify.sh`) and owner-only production sign-off script (`scripts/sprints/phase14/sprint_14_10_owner_acceptance.sh --full`).

---

## 2. Production Hardening & Chaos Failure Modes

### 2.1 Deterministic Fallback Matrix Under Dependency Outages

| Failure Scenario | Injected Chaos Fault | Mandatory Graceful Behavior | Customer Impact |
|---|---|---|---|
| **External LLM Provider Outage / Timeout (`> 2.5s`)** | Block outbound LLM API calls | `SocraticBreakpointService` serves 100% SymPy AST-compiled Turkish Socratic prompts (`scaffolding_breakpoint.py` deterministic template engine) | **Zero downtime**; 4-tier scaffolding continues uninterrupted |
| **Cloud Neural TTS Outage / Rate Limit (`HTTP 429/503`)** | Inject 100% error rate on cloud TTS adapter | `TurkishMathTtsService` returns `ssmlPayload` + word boundary map with `playbackMode = IOS_AVSPEECH_FALLBACK`; iOS switches immediately to `AVSpeechSynthesizer` (`tr-TR`) | **Zero downtime**; voice + stroke synchronization continues on-device |
| **Redis / Cache Node Eviction** | Flush Redis session & audio cache | Active `SocraticScaffoldingSession` state reloads transparently from PostgreSQL (`V039`–`V042`); timeline vectors recompile deterministically in `< 80 ms` | `< 90 ms` one-time latency bump; zero lost student progress |
| **Client Network Drop During Timed Twin Challenge** | Drop iOS network connection for 15s mid-challenge | iOS queues the signed submission with monotonic elapsed duration (`monotonicElapsedSeconds`) and retries with idempotency key `X-Idempotency-Key`; server verifies within grace window | Student's valid independent twin solve is preserved |

---

## 3. Accessibility & Inclusive Pedagogy Requirements

1. **`VoiceOver` & Screen Reader Math Navigation**:
   - Every formula on `SocraticScaffoldingView`, `TwinMasteryChallengeView`, and `InteractiveSocraticCanvasView` exposes its natural Turkish mathematical prose (`turkish_math_speech_normalizer.py` output) as `accessibilityLabel` so blind or low-vision students hear *"iks eksi sekiz çarpı P iki iks eşittir dört çarpı iks eksi iki çarpı P iks"* instead of raw symbols.
2. **`Reduced Motion` (`UIAccessibility.isReduceMotionEnabled`)**:
   - When Reduced Motion is active on iOS, `InteractiveSocraticCanvasView` disables stroke-by-stroke animation interpolation and transitions cleanly step-by-step with synchronized highlight boxes and captions.
3. **Deaf / Hard-of-Hearing Synchronized Caption Mode**:
   - `SynchronizedMathCaptionBar` displays the full Turkish spoken prose with real-time word-by-word karaoke highlighting synced to the active equation sub-term on the canvas, enabling 100% comprehension with device audio muted.
4. **Colorblind-Safe Diagnostic Palette**:
   - Verified step and error highlights pair high-contrast colors (`#10B981` emerald + checkmark badge vs. `#F59E0B` amber + warning triangle badge) so distinction never relies on color alone.

---

## 4. Phase 14 Release Exit Gate Checklist (`G14-01` – `G14-10`)

| Gate ID | Category | Verification Criterion | Target / Threshold | Verification Method |
|---|---|---|---|---|
| `G14-01` | Database Migrations | Flyway migrations `V039`, `V040`, `V041`, `V042` apply cleanly and roll forward on clean + seeded DBs | `100%` pass | `./gradlew :services:api:flywayMigrate :services:api:test` |
| `G14-02` | Anti-Spoiler Security | Zero final answer or option letter present in HTTP responses or DOM payloads during `TIER_1`–`TIER_3` | `0` leaks across 100-item corpus | `SocraticAntiSpoilerContractTest` |
| `G14-03` | SymPy Breakpoint & Distractor Proof | Every tier's 3 distractors proven algebraically non-equivalent to the correct step via SymPy | `100%` pass | `pytest services/math-verifier/tests/test_scaffolding_breakpoint.py` |
| `G14-04` | Isomorphic Twin Engine | All 12 YKS/AYT/LGS families (`FAM_01`–`FAM_12`) generate distinct, solvable, 5-option twins | `600/600` property cases (`p95 < 150 ms`, `$0.00` LLM cost) | `pytest services/math-verifier/tests/test_yks_isomorphic_twin_generator.py` |
| `G14-05` | Epistemic Mastery Integrity | `bkt-lite-v2` full mastery credit (`1.00x`) requires `VERIFIED_TWIN_SOLVED_INDEPENDENTLY` within time limit | `100%` invariant enforcement | `TwinMasteryChallengeIntegrationTest` |
| `G14-06` | Misconception Root-Cause Diagnosis | Prerequisite slips (e.g., $2^n=4 \implies n=3$) isolate penalty to prerequisite skill and trigger 45s repair card | `100%` pass | `PrerequisiteRootCauseAnalyzerTest` |
| `G14-07` | Vector Canvas & Audio Sync | `WhiteboardTimelineDocument v1` payload `< 32 KB`; audio-to-stroke drift `< 40 ms`; zero raw LaTeX in TTS | `100/100` benchmark items | `pytest services/math-verifier/tests/test_whiteboard_timeline_and_tts.py` |
| `G14-08` | Bidirectional `PencilKit` Verification | Handwritten step equivalence & sub-term error localization (`SIGN_ERROR`, `EXPONENT_ERROR`) | `p95 < 800 ms` end-to-end | `CanvasInkVerificationIntegrationTest` |
| `G14-09` | FinOps Cost Ceiling | Full 3-pillar session marginal cost under load and high-retry scenarios | `<= $0.0080 / session` | `Phase14CostBudgetGuardTest` |
| `G14-10` | Repository Documentation Integrity | All Phase 14 docs, manifest line/byte counts, and cross-references validated | `0` errors | `python3 scripts/quality/docs_check.py` |

---

## 5. Execution Commands (Agent Sandbox vs. Owner Acceptance)

### 5.1 Agent Sandboxed Verification (`BypassSandbox: false`)
```bash
# 1. Documentation & manifest integrity gate
python3 scripts/quality/docs_check.py

# 2. Python math-verifier unit, property, and 100-item Solvi disruption benchmark
pytest services/math-verifier/tests

# 3. Backend Spring Boot unit & integration tests (V039-V042 + Phase 14 controllers/services)
./gradlew test
```

### 5.2 Owner-Only Production Release Gate
In strict compliance with `AGENTS.md`, `GEMINI.md`, and workspace credential-isolation rules, production secret rotation, live cloud TTS provisioning, and production deployment sign-off are executed exclusively by the repository owner outside the agent sandbox:
```bash
./scripts/sprints/phase14/sprint_14_10_owner_acceptance.sh --full
```

---

## 6. Definition of Done (DoD)
- [x] All 10 exit gates (`G14-01` through `G14-10`) documented with deterministic pass/fail criteria.
- [x] Chaos fallback behavior verified for LLM outage, cloud TTS outage, cache flush, and mid-challenge network drop.
- [x] Accessibility (`VoiceOver`, `Reduced Motion`, Closed Captions, Colorblind-safe badges) and KVKK privacy invariants verified.
