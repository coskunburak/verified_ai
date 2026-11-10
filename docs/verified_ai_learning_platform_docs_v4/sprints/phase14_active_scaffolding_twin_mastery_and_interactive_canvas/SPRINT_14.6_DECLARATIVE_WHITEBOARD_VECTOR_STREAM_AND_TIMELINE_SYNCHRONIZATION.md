# Sprint 14.6 — Declarative Whiteboard Vector Stream and Timeline Synchronization

## Sprint mission

Design and implement the **Declarative Whiteboard Vector Stream (`WhiteboardTimelineDocument v1`)** and additive persistence (`PLANNED V041`) to replace Solvi-style static `.mp4` cloud video rendering with a deterministic, interactive, 60-fps native vector whiteboard stream at `<5%` of video bandwidth and storage cost.

## Why this sprint exists

Solvi's core marketing attraction is turning a math solution into an animated video lecture. However, rendering `.mp4` video files on backend GPU/CPU servers is slow (10–25s latency), expensive (`~$0.05–$0.12` per video), blurry on Retina displays, and **completely non-interactive** (the student cannot touch or edit equations inside an `.mp4`). By emitting a declarative vector timeline (`WhiteboardTimelineDocument v1`) containing keyframed 2D LaTeX AST nodes, animated Bezier pointers/underlines, and coordinate geometry strokes, the iOS client renders a razor-sharp 60-fps native whiteboard in `<300ms` that the student can pause and write on at any millisecond.

## Preconditions and dependencies

- `SPRINT_14.5` completed.
- `adr/ADR-010_NATIVE_2D_MATH_RENDERING_AND_STEP_DIFF_HIGHLIGHTING.md` (`SwiftMath` 2D rendering) and `Sprint 6.9` (`math_visualization.py`) reviewed.

## In scope

- Canonical schema `packages/schemas/tutoring/whiteboard-timeline-document.v1.schema.json`.
- Python deterministic timeline compiler `services/math-verifier/app/whiteboard_timeline_compiler.py` converting verified solution steps and scaffolding tiers into millisecond-indexed vector keyframes (`WRITE_LATEX_LINE`, `HIGHLIGHT_SUBEXPR`, `DRAW_ARROW_CONNECTOR`, `PLOT_CURVE_SEGMENT`, `PAUSE_FOR_STUDENT_CHECKPOINT`).
- Additive Flyway migration `PLANNED V041__create_interactive_canvas_stroke_streams.sql`.
- iOS native vector playback engine `WhiteboardVectorPlayerView.swift` using `SwiftMath` + `CoreGraphics` / `Canvas` at 60–120Hz ProMotion frame rates.

## Explicitly out of scope

- Neural voiceover audio synthesis (wired in `SPRINT_14.7`).
- Live Apple Pencil student handwriting input on the canvas (wired in `SPRINT_14.8`).

## Deliverables

1. **Semantic deliverable** — `WhiteboardTimelineDocument v1` specification with deterministic keyframe opcodes.
2. **Implementation deliverable** — `whiteboard_timeline_compiler.py`, `WhiteboardTimelineService.java`, and iOS `WhiteboardVectorPlayerView.swift`.
3. **Contract deliverable** — `GET /api/v1/problem-sessions/{sessionId}/whiteboard-timeline` and JSON schema validation.
4. **Quality deliverable** — Golden payload tests verifying 100% deterministic compilation and `<25 KB` payload size per solution.
5. **Operational deliverable** — Compiler latency and payload size histograms.
6. **Documentation deliverable** — Synchronized with `learning/93` and `ADR-010`.

---

<!-- LEARNING_OS_PRODUCTION_SPEC_V1:START -->
# Learning OS Production Implementation Specification v1 (`2026-11-11`)

## 1. Sprint Metadata & Status
- **Sprint ID:** `SPRINT_14.6` (Fast-track bridge alias: `SPRINT_8.6B-Part1`)
- **Canonical Title:** Declarative Whiteboard Vector Stream and Timeline Synchronization
- **Status:** `PLANNED` (`GATED_ON_SPRINT_14_5`)
- **Primary Spec Reference:** `learning/93_ACTIVE_SCAFFOLDING_TWIN_VERIFICATION_AND_INTERACTIVE_CANVAS_SPECIFICATION.md`, `adr/ADR-010_NATIVE_2D_MATH_RENDERING_AND_STEP_DIFF_HIGHLIGHTING.md`

## 2. Strategic Learning OS Objective & User Value
Delivers the visual magic of a private tutor writing step-by-step on a clean whiteboard — circling matching terms ($a \cdot 2^n x^{n+1}$ and $4a x^{n+1}$), drawing cancellation slashes, and sketching parabolas — with zero video buffering, instant scrubbing, and interactive pause points.

## 3. Entry Gate & Preconditions
- `SPRINT_14.2` scaffolding step decomposition and `ADR-010` 2D math renderer available.

## 4. Domain Invariants & Mathematical Rules
1. **100% Symbolic Fidelity (`INV-WB-001`):** Every `WRITE_LATEX_LINE` and `HIGHLIGHT_SUBEXPR` opcode in `WhiteboardTimelineDocument` is compiled directly from the SymPy-verified canonical step AST (`CanonicalMathDocument v2`); the compiler never invents unverified equations.
2. **Zero Video Encoding Overhead (`INV-WB-002`):** Server-side `.mp4` / FFmpeg rendering is strictly forbidden. All animations are represented as compact JSON vector keyframes (`<= 32 KB` compressed per problem) rendered natively on the client GPU.
3. **Interactive Checkpoint Pause Locks (`INV-WB-003`):** Every step boundary includes a `PAUSE_FOR_STUDENT_CHECKPOINT` keyframe that automatically pauses timeline playback until the learner completes the Socratic micro-checkpoint (`SPRINT_14.2`) or taps continue.

## 5. Owning Spring Modulith Backend Modules & Classes
- `services/api/src/main/java/com/verifiedai/tutoring/application/WhiteboardTimelineService.java`
- `services/api/src/main/java/com/verifiedai/tutoring/domain/InteractiveCanvasStrokeStream.java`
- `services/api/src/main/java/com/verifiedai/tutoring/web/WhiteboardTimelineController.java`

## 6. Python Math Verifier Impact (`services/math-verifier/`)
- `services/math-verifier/app/whiteboard_timeline_compiler.py` *(new)*:
  - Deterministically computes bounding-box anchor coordinates, sub-expression token IDs (e.g., highlighting $(x-8)$ on LHS and $P(8)=0$ on RHS), and smooth Bezier curve control points for step transitions.

## 7. Database Migration & Persistence Contract (`PLANNED V041`)
- **Migration File:** `services/api/src/main/resources/db/migration/platform/V041__create_interactive_canvas_stroke_streams.sql` *(PLANNED)*
- **Tables:**
  - `interactive_canvas_stroke_streams` (`id UUID PK`, `problem_session_id UUID NOT NULL`, `scaffolding_session_id UUID NULL REFERENCES socratic_scaffolding_sessions(id) ON DELETE CASCADE`, `schema_version VARCHAR(16) NOT NULL DEFAULT 'v1'`, `step_count INT NOT NULL`, `total_duration_ms INT NOT NULL`, `vector_strokes_json JSONB NOT NULL`, `audio_sync_timeline JSONB NOT NULL DEFAULT '[]'::jsonb`, `tts_audio_object_key VARCHAR(512) NULL`, `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`).

## 8. REST API Contracts
- `GET /api/v1/problem-sessions/{sessionId}/whiteboard-timeline` — Returns the compiled `WhiteboardTimelineDocument v1` with keyframe opcodes and step pause markers.

## 9. iOS (`apps/ios/VerifiedAI/`) Scope
- `apps/ios/VerifiedAI/Features/Tutor/Presentation/WhiteboardVectorPlayerView.swift`
- `apps/ios/VerifiedAI/Features/Tutor/Presentation/WhiteboardTimelineControllerModel.swift`
- Supports play/pause, `0.75x / 1.0x / 1.5x` speed control, step-by-step scrubbing, and Reduced Motion instant-render accessibility fallback.

## 10. AI Capability & Unit Economics Guardrail
- `100%` deterministic AST-to-vector compilation in Python (`$0.00` AI inference cost, `<60ms` compile time).

## 11. Privacy, Safety & Compliance
- Contains only mathematical solution steps; cascades on session/account deletion.

## 12. Telemetry & Observability
- `verifiedai.whiteboard.timeline.compiled{step_count}`
- `verifiedai.whiteboard.playback.completed_ratio`

## 13. Feature Flags & Rollback Plan
- Feature flag: `verifiedai.tutoring.whiteboard-vector-player-enabled: false`.

## 14. Automated Test Matrix (Categories A–F)
- **Gate A (Python Compiler):** `test_whiteboard_timeline_compiler.py` validating schema conformance, monotone keyframe timestamps, and exact LaTeX parity across all 24 AYT benchmark problems.
- **Gate B (Backend API):** `WhiteboardTimelineControllerTest`.
- **Gate C (iOS Playback & Accessibility):** `WhiteboardVectorPlayerViewTests` including `UIAccessibility.isReduceMotionEnabled` verification.

## 15. Owner-Only Acceptance Command
- `bash scripts/quality/sprint_14_6_owner_acceptance.sh --full`

## 16. Explicit Non-Goals
- Does not stream pixel video buffers (`H.264`/`HEVC`).

## 17. Exit Gate & Downstream Consumers
- Unlocks `SPRINT_14.7` (Synchronized Turkish Math Neural TTS) and `SPRINT_14.8` (Bidirectional PencilKit Canvas).

## 18. Canonical Cross-References
- `learning/93_ACTIVE_SCAFFOLDING_TWIN_VERIFICATION_AND_INTERACTIVE_CANVAS_SPECIFICATION.md`
- `adr/ADR-010_NATIVE_2D_MATH_RENDERING_AND_STEP_DIFF_HIGHLIGHTING.md`
<!-- LEARNING_OS_PRODUCTION_SPEC_V1:END -->
