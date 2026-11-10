# Sprint 14.7 — Turkish Mathematics Neural TTS and Synchronized Voice Pedagogy

## Sprint mission

Implement the **Turkish Mathematical Prosody & Speech Normalizer (`turkish_math_speech_normalizer.py`)**, content-addressed Neural TTS audio pipeline, and word-to-stroke timestamp synchronization so the interactive whiteboard speaks natural, classroom-grade Turkish mathematics synchronized to the exact millisecond a formula or highlight is drawn on-screen.

## Why this sprint exists

Generic Text-to-Speech engines mispronounce LaTeX and mathematical notation in Turkish (reading `P(2x) = a \cdot 2^n x^n` as gibberish punctuation or English symbols). Meanwhile, students using Solvi praise voice narration because hearing an explanation while watching formulas appear dual-codes information across auditory and visual working memory (Mayer's Multimedia Principle). This sprint builds a deterministic LaTeX-to-Turkish-Speech AST compiler paired with cached Neural TTS and exact stroke synchronization.

## Preconditions and dependencies

- `SPRINT_14.6` completed (`WhiteboardTimelineDocument v1` and `V041`).
- Object Storage (`Sprint 2.6` / `Sprint 4.2`) and `PostgresAiSpendGuard` (`V016`/`V017`) available.

## In scope

- Deterministic LaTeX-to-Turkish spoken prose compiler `services/math-verifier/app/turkish_math_speech_normalizer.py` (e.g., `\lim_{x \to 1} \frac{x^2 + ax + 10}{x - 1} = b` $\to$ *"iks bire giderken, iks kare artı a iks artı on bölü iks eksi bir limiti, b'ye eşittir"*).
- Provider-neutral `MathVoiceSynthesizerPort.java` with managed Neural TTS adapter (supporting word/sentence boundary timecodes) and deterministic iOS native `AVSpeechSynthesizer` (`tr-TR`) zero-cost offline fallback.
- Content-addressed audio caching in Object Storage keyed by `SHA256(normalized_speech_script + voice_id)` so repeated problems cost `$0.00`.
- Synchronized closed captions (`subtitleCueTimeline`) for silent/library study and deaf/hard-of-hearing accessibility.

## Explicitly out of scope

- Open-ended voice call streaming (full duplex phone calls); voice is synchronized to pedagogical steps and Socratic interventions.

## Deliverables

1. **Semantic deliverable** — Complete Turkish mathematical pronunciation grammar covering algebra, polynomials, functions, trigonometry, logarithms, sequences, limits, derivatives, and integrals.
2. **Implementation deliverable** — `turkish_math_speech_normalizer.py`, `MathVoiceSynthesizerService.java`, and iOS `SynchronizedAudioWhiteboardCoordinator.swift`.
3. **Contract deliverable** — `audio_sync_timeline` and `subtitleCueTimeline` extensions in `WhiteboardTimelineDocument v1`.
4. **Quality deliverable** — 100-expression Turkish mathematical pronunciation golden test suite + audio-stroke drift verification (`<80ms` sync tolerance).
5. **Operational deliverable** — TTS cache hit ratio, synthesis latency, and FinOps cost counters.
6. **Documentation deliverable** — Synchronized with `learning/93`.

---

<!-- LEARNING_OS_PRODUCTION_SPEC_V1:START -->
# Learning OS Production Implementation Specification v1 (`2026-11-11`)

## 1. Sprint Metadata & Status
- **Sprint ID:** `SPRINT_14.7` (Fast-track bridge alias: `SPRINT_8.6B-Part2`)
- **Canonical Title:** Turkish Mathematics Neural TTS and Synchronized Voice Pedagogy
- **Status:** `PLANNED` (`GATED_ON_SPRINT_14_6`)
- **Primary Spec Reference:** `learning/93_ACTIVE_SCAFFOLDING_TWIN_VERIFICATION_AND_INTERACTIVE_CANVAS_SPECIFICATION.md`

## 2. Strategic Learning OS Objective & User Value
Combines the best aspect of Solvi (engaging voiceover narration) with 100% mathematical accuracy and Turkish exam terminology (*"başkatsayı"*, *"iki kare farkı"*, *"yarım açı formülü"*, *"belirsizlik giderme"*), while allowing instant muting with synchronized subtitles in quiet study environments.

## 3. Entry Gate & Preconditions
- `SPRINT_14.6` vector timeline compiler passing all golden tests.

## 4. Domain Invariants & Mathematical Rules
1. **Zero Raw LaTeX in Speech Input (`INV-TTS-001`):** No raw LaTeX backslashes (`\frac`, `\sqrt`, `\alpha`, `\implies`) or unexpanded symbols (`!=`, `>=`, `^`) may ever be sent to a TTS synthesizer. `turkish_math_speech_normalizer.py` deterministically converts the SymPy/LaTeX AST into natural Turkish SSML/prose before synthesis.
2. **Audio-Stroke Synchronization Lock (`INV-TTS-002`):** Every `<mark name="step_k_token_m"/>` boundary in the speech script updates the corresponding `timestamp_ms` in `WhiteboardTimelineDocument` so the visual highlight appears within `±80ms` of the spoken mathematical term.
3. **Graceful Offline / Budget Fallback (`INV-TTS-003`):** If cloud TTS quota is reached or the learner is on a weak network, the iOS client seamlessly executes the pre-normalized Turkish speech script via on-device `AVSpeechSynthesizer` (`tr-TR` neural voice) using `AVSpeechSynthesizerDelegate.willSpeakRangeOfSpeechString` to drive the exact same vector stroke highlights at `$0.00` cost.

## 5. Owning Spring Modulith Backend Modules & Classes
- `services/api/src/main/java/com/verifiedai/tutoring/application/MathVoiceSynthesizerService.java`
- `services/api/src/main/java/com/verifiedai/tutoring/application/port/MathVoiceSynthesizerPort.java`
- `services/api/src/main/java/com/verifiedai/tutoring/infrastructure/tts/CloudNeuralMathTtsAdapter.java`

## 6. Python Math Verifier Impact (`services/math-verifier/`)
- `services/math-verifier/app/turkish_math_speech_normalizer.py` *(new)*:
  - Converts LaTeX formulas and step explanations into natural Turkish pedagogical speech scripts with embedded SSML `<mark>` tags for sub-expression highlighting.

## 7. Database Migration & Persistence Contract
- Populates `audio_sync_timeline` and `tts_audio_object_key` in `interactive_canvas_stroke_streams` (`V041`).

## 8. REST API Contracts
- Extends `GET /api/v1/problem-sessions/{sessionId}/whiteboard-timeline` with:
  - `normalizedSpeechSegments`: array of `{ stepIndex, ssmlMarkId, spokenTextTr, subtitleTextTr, startMs, endMs }`
  - `presignedAudioUrl`: nullable presigned URL to cached `.m4a`/`.mp3` audio stream.

## 9. iOS (`apps/ios/VerifiedAI/`) Scope
- `apps/ios/VerifiedAI/Features/Tutor/Presentation/SynchronizedAudioWhiteboardCoordinator.swift`
- `apps/ios/VerifiedAI/Features/Tutor/Presentation/WhiteboardSubtitleOverlayView.swift`
- Supports instant mute/unmute, subtitle toggle, and seamless on-device `AVSpeechSynthesizer` fallback.

## 10. AI Capability & Unit Economics Guardrail
- Content-addressed audio caching (`SHA-256`) ensures each unique step script is synthesized at most once globally.
- Guarded by `PostgresAiSpendGuard` (`<= $0.002` per uncached problem; `$0.00` on cache hit or on-device `AVSpeechSynthesizer` route).

## 11. Privacy, Safety & Compliance
- Only system-generated mathematical explanations are synthesized; no student voice recordings are collected.

## 12. Telemetry & Observability
- `verifiedai.tts.cache.hit_ratio`
- `verifiedai.tts.sync.drift_ms`
- `verifiedai.tts.fallback.on_device_count`

## 13. Feature Flags & Rollback Plan
- Feature flags: `verifiedai.tutoring.voice-pedagogy-enabled: false`, `verifiedai.tutoring.cloud-tts-enabled: false` (can run 100% on-device `AVSpeechSynthesizer` at `$0.00` cloud cost).

## 14. Automated Test Matrix (Categories A–F)
- **Gate A (Turkish Math Normalizer Suite):** `test_turkish_math_speech_normalizer.py` verifying 100 Turkish LaTeX formulas (polynomials, integrals, limits, logs, trig, sets, logic) produce zero raw LaTeX tokens and grammatically accurate Turkish math prose.
- **Gate B (Backend Caching & Spend Guard):** `MathVoiceSynthesizerServiceTest`.
- **Gate C (iOS Audio-Vector Sync & Captions):** `SynchronizedAudioWhiteboardCoordinatorTests`.

## 15. Owner-Only Acceptance Command
- `bash scripts/quality/sprint_14_7_owner_acceptance.sh --full`

## 16. Explicit Non-Goals
- Does not clone real celebrity or third-party teacher voices without explicit licensing.

## 17. Exit Gate & Downstream Consumers
- Unlocks `SPRINT_14.8` (Bidirectional PencilKit Canvas & Live Ink Step Verification).

## 18. Canonical Cross-References
- `learning/93_ACTIVE_SCAFFOLDING_TWIN_VERIFICATION_AND_INTERACTIVE_CANVAS_SPECIFICATION.md`
- `ai/60_AI_UNIT_ECONOMICS_AND_INFERENCE_COST_ENGINEERING.md`
<!-- LEARNING_OS_PRODUCTION_SPEC_V1:END -->
