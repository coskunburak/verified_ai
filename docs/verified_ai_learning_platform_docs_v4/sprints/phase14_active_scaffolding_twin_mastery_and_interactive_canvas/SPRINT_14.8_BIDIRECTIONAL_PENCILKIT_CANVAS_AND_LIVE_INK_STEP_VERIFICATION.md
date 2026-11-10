# SPRINT 14.8 — Bidirectional `PencilKit` Canvas & Live Ink Step Verification

| Field | Value |
|---|---|
| **Sprint ID** | `SPRINT_14.8` |
| **Phase** | [PHASE_14_ACTIVE_SCAFFOLDING_TWIN_MASTERY_AND_INTERACTIVE_CANVAS.md](PHASE_14_ACTIVE_SCAFFOLDING_TWIN_MASTERY_AND_INTERACTIVE_CANVAS.md) (`Phase 14`) |
| **Canonical Spec** | [93_ACTIVE_SCAFFOLDING_TWIN_VERIFICATION_AND_INTERACTIVE_CANVAS_SPECIFICATION.md](../../learning/93_ACTIVE_SCAFFOLDING_TWIN_VERIFICATION_AND_INTERACTIVE_CANVAS_SPECIFICATION.md) (§5.2–§5.4) |
| **Primary Pillar** | Pillar 3 — Bidirectional Interactive Socratic Canvas (`PencilKit`) & Synchronized Voice Pedagogy |
| **Status** | `SPECIFIED_PRODUCTION_READY` |

---

## 1. Sprint Goal & Executive Summary

Why watch a static `.mp4` video (like **Solvi**) when the student can pause the tutor at any second, write their own intermediate step with Apple Pencil or finger directly on the same whiteboard, and have the SymPy CAS verifier check their handwritten step in `< 800 ms`?

**Sprint 14.8** delivers the **Bidirectional `PencilKit` Socratic Canvas & Live Ink Step Verification** loop:
1. **Unified Dual-Layer `PencilKit` + Vector Canvas (`InteractiveSocraticCanvasView.swift`)**:
   - **Layer 1 (AI Tutor Vector Stream)**: Renders `WhiteboardTimelineDocument v1` strokes, equations, and geometry highlights synchronized at 60 fps with Turkish math audio (`SPRINT_14.6` & `SPRINT_14.7`).
   - **Layer 2 (Learner `PKCanvasView` Ink Overlay)**: The moment the learner touches the canvas with Apple Pencil or finger (or taps *"Ben Yazayım / Devamını Ben Çözeyim"*), the AI tutor timeline pauses automatically and opens an active learner ink zone anchored to the current step (`stepIndex`).
2. **Incremental Stroke Bounding-Box Clustering & Handwritten Math OCR (`canvas_ink_step_verifier.py`)**:
   - Captures only the newly drawn `PKStroke` cluster (`boundingBox` + rasterized 2x grayscale patch + stroke trajectory metadata), converts it into a normalized LaTeX candidate, and submits it to the SymPy step equivalence engine (`verify_learner_ink_step`).
3. **Instant Socratic Ink Intervention (`V042__create_canvas_learner_ink_interventions.sql`)**:
   - If the learner's handwritten step is algebraically equivalent to a valid continuation of the current equation (even if written in a different order or using an alternative algebraic manipulation), the tutor highlights the learner's strokes in **emerald green**, plays an encouraging voice confirmation, and adapts the remaining timeline steps.
   - If the learner makes a sign error, exponent slip, or distribution error (e.g., expanding $(x-8)(x-4)$ as $x^2 - 12x - 32$ instead of $+32$), the tutor highlights **only the erroneous term bounding box** in **amber**, speaks a targeted Socratic micro-hint, and lets the learner erase/correct right on the canvas.

---

## 2. Scope & Non-Regression Invariants

### 2.1 In-Scope Deliverables
- Flyway migration `V042__create_canvas_learner_ink_interventions.sql` (`canvas_learner_ink_interventions`).
- Python Verifier module `services/math-verifier/app/verification/canvas_ink_step_verifier.py` and FastAPI endpoint `POST /v1/canvas/verify-ink-step`.
- Backend Spring Boot service `services/api/src/main/java/com/verifiedai/tutoring/canvas/CanvasInkVerificationService.java` and controller `CanvasInkVerificationController.java`.
- iOS SwiftUI + `PencilKit` implementation under `apps/ios/VerifiedAI/Features/Tutoring/Canvas/`:
  - `InteractiveSocraticCanvasView.swift`
  - `PencilKitOverlayRepresentable.swift`
  - `CanvasPlaybackSyncController.swift`
  - `InkStepFeedbackBubbleView.swift`

### 2.2 Non-Regression & Security Boundaries
- **Privacy & Ephemeral Ink Processing**: Raw rasterized crops of learner handwriting are processed in-memory for LaTeX extraction and discarded immediately after structured LaTeX + SymPy AST verification (`KVKK` & Apple App Store Guideline `5.1.2(i)` compliance). Only vector stroke metadata (`stroke_count`, bounding-box coordinates, normalized LaTeX, and SymPy diagnostic codes) is persisted.
- **Zero Regression on Existing OCR Pipeline**: Existing full-page question photo OCR (`Phase 2`) remains untouched; `verify-ink-step` operates strictly on single-step canvas ink patches (`<= 512x256 px`).

---

## 3. Database Migration (`V042`)

File: `services/api/src/main/resources/db/migration/V042__create_canvas_learner_ink_interventions.sql`

```sql
-- V042: Bidirectional PencilKit Canvas Learner Ink Step Verification & Socratic Interventions
CREATE TABLE IF NOT EXISTS canvas_learner_ink_interventions (
    id                          UUID PRIMARY KEY,
    stroke_stream_id            UUID NOT NULL REFERENCES interactive_canvas_stroke_streams(id) ON DELETE CASCADE,
    scaffolding_session_id      UUID NOT NULL REFERENCES socratic_scaffolding_sessions(id) ON DELETE CASCADE,
    user_id                     UUID NOT NULL,
    anchor_step_index           INT NOT NULL CHECK (anchor_step_index >= 0),
    pause_timestamp_ms          INT NOT NULL CHECK (pause_timestamp_ms >= 0),
    stroke_count                INT NOT NULL CHECK (stroke_count >= 1),
    bounding_box_json           JSONB NOT NULL,
    recognized_latex            TEXT NOT NULL,
    ocr_confidence              NUMERIC(4, 3) NOT NULL CHECK (ocr_confidence >= 0.0 AND ocr_confidence <= 1.0),
    sympy_equivalence_status    VARCHAR(32) NOT NULL CHECK (
        sympy_equivalence_status IN (
            'VALID_CONTINUATION',
            'VALID_FINAL_STEP',
            'SIGN_ERROR',
            'EXPONENT_ERROR',
            'DISTRIBUTION_ERROR',
            'ARITHMETIC_SLIP',
            'NON_EQUIVALENT_STEP',
            'LOW_OCR_CONFIDENCE'
        )
    ),
    erroneous_subterm_latex     TEXT,
    socratic_voice_feedback_tr  TEXT NOT NULL,
    verification_latency_ms     INT NOT NULL CHECK (verification_latency_ms >= 0),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_canvas_ink_interventions_session
    ON canvas_learner_ink_interventions (scaffolding_session_id, anchor_step_index, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_canvas_ink_interventions_user
    ON canvas_learner_ink_interventions (user_id, created_at DESC);
```

---

## 4. Python Verifier — `canvas_ink_step_verifier.py`

### 4.1 Algebraic Step Continuation & Sub-Term Error Localization

Unlike static answer checking, a learner writing on a whiteboard at `anchor_step_index` may write:
- An **equation** (e.g., $2^n = 4$ or $P(8) = 0$), or
- An **algebraic expression** representing the expanded/simplified RHS of the previous line (e.g., $a(x^2 - 12x + 32)$).

`canvas_ink_step_verifier.py` performs deterministic SymPy diagnosis across 4 checks:

1. **Exact / Symbolic Equivalence Check**:
   - Parses `expected_step_expr` (and all valid intermediate transformations toward `goal_expr`) and `learner_latex` via `latex2sympy` / `sympy.simplify`.
   - Checks `sympy.simplify(learner_expr - expected_expr) == 0` or `sympy.Equivalent(learner_eq, expected_eq)`.
2. **Sign Flip Localization (`SIGN_ERROR`)**:
   - Expand both `learner_expr` and `expected_expr` into additive monomials (`expr.as_ordered_terms()`).
   - If `learner_term == -expected_term` for one or more terms while remaining terms match (e.g., $-32a$ vs. $+32a$), return `SIGN_ERROR` with `erroneous_subterm_latex = "-32a"`.
3. **Exponent Slip Localization (`EXPONENT_ERROR`)**:
   - Compares degree/exponent structure (`Poly(learner_expr).monoms()` vs. `Poly(expected_expr).monoms()`). If base matches but exponent differs (e.g., $a \cdot 2^n x^n$ instead of $a \cdot 2^n x^{n+1}$), return `EXPONENT_ERROR` with `erroneous_subterm_latex`.
4. **Distribution Error Localization (`DISTRIBUTION_ERROR`)**:
   - Detects partial factor multiplication where $c(A + B)$ was written as $cA + B$ (missing distribution onto second term).

```python
class InkStepVerificationResult(BaseModel):
    sympy_equivalence_status: Literal[
        "VALID_CONTINUATION",
        "VALID_FINAL_STEP",
        "SIGN_ERROR",
        "EXPONENT_ERROR",
        "DISTRIBUTION_ERROR",
        "ARITHMETIC_SLIP",
        "NON_EQUIVALENT_STEP",
        "LOW_OCR_CONFIDENCE",
    ]
    recognized_latex: str
    ocr_confidence: float
    erroneous_subterm_latex: str | None
    highlight_color_hex: str  # "#10B981" (Emerald) for valid, "#F59E0B" (Amber) for error
    socratic_voice_feedback_tr: str
    resume_from_step_index: int
```

---

## 5. Backend API Contract (`CanvasInkVerificationController.java`)

### 5.1 `POST /api/v1/tutoring/canvas/{strokeStreamId}/verify-ink-step`

**Request Payload**:
```json
{
  "scaffoldingSessionId": "a18c9b04-5f6e-41c2-910a-7e30b20e88d1",
  "anchorStepIndex": 3,
  "pauseTimestampMs": 14250,
  "strokeCount": 14,
  "boundingBox": {
    "x": 84.0,
    "y": 410.0,
    "width": 520.0,
    "height": 118.0
  },
  "recognizedLatexCandidate": "a(x^2 - 12x - 32)",
  "ocrConfidence": 0.96
}
```

**Response Payload (`200 OK`)**:
```json
{
  "interventionId": "d02f8b11-88a4-4a31-9311-9c1204bc7210",
  "sympyEquivalenceStatus": "SIGN_ERROR",
  "recognizedLatex": "a(x^2 - 12x - 32)",
  "erroneousSubtermLatex": "-32",
  "highlightColorHex": "#F59E0B",
  "socraticVoiceFeedbackTr": "Çok güzel dağıttın! Sadece son terime dikkat et: eksi 8 ile eksi 4'ün çarpımı eksi 32 mi, yoksa artı 32 mi olur?",
  "ssmlPayload": "<speak><mark name=\"ink_fb_0\"/>Çok güzel dağıttın! <mark name=\"ink_fb_1\"/>Sadece son terime dikkat et: eksi sekiz ile eksi dördün çarpımı eksi otuz iki mi, yoksa artı otuz iki mi olur?</speak>",
  "resumeFromStepIndex": 3,
  "verificationLatencyMs": 112
}
```

---

## 6. iOS `PencilKit` Architecture (`InteractiveSocraticCanvasView.swift`)

### 6.1 Dual-Layer Rendering & Gesture Arbitration
1. **Simultaneous 60-fps Vector Layer + `PKCanvasView`**:
   - `InteractiveSocraticCanvasView` stacks `WhiteboardVectorRendererView` (Canvas API drawing AI strokes) beneath `PencilKitOverlayRepresentable` (`PKCanvasView` with `drawingPolicy = .anyInput`).
2. **Auto-Pause on Ink Touch (`PKCanvasViewDelegate.canvasViewDidBeginUsingTool`)**:
   - The instant the user's Apple Pencil or finger begins a stroke, `CanvasPlaybackSyncController` pauses the `AVAudioPlayer` / `AVSpeechSynthesizer` and freezes the AI vector timeline at `currentTimestampMs`.
3. **1.2-Second Idle Debounce & "Kontrol Et" Action Pill**:
   - After the learner finishes writing a line (or taps the floating *"Adımımı Kontrol Et"* pill next to their stroke bounding box), the client extracts the active `PKDrawing` strokes for the current step, runs on-device math ink recognition (with backend fallback), and calls `POST /api/v1/tutoring/canvas/{strokeStreamId}/verify-ink-step`.
4. **Visual Ink Highlight & Seamless Timeline Continuation**:
   - On `VALID_CONTINUATION`: A soft emerald glow (`#10B981`) animates around the learner's handwritten strokes, the learner's handwriting remains pinned on the canvas in place of the AI's step `anchorStepIndex`, and the AI tutor resumes narration from `anchorStepIndex + 1`.
   - On `SIGN_ERROR` / `EXPONENT_ERROR`: An amber highlight box (`#F59E0B`) pulses over the erroneous sub-term, and `InkStepFeedbackBubbleView` plays the Socratic voice prompt so the learner can erase and fix the term with `PKEraserTool`.

---

## 7. Verification Plan & Acceptance Gates

### 7.1 Automated Tests
1. **Python Unit & Property Suite (`services/math-verifier/tests/test_canvas_ink_step_verifier.py`)**:
   - Verifies all 8 `sympy_equivalence_status` classifications across 40 handwritten step variations from the 24-question AYT deneme benchmark (`P(x)=a(x-8)(x-4)` expansion, `2^n=4` exponent evaluation, `tan(2a)` double-angle substitution, derivative tangent condition `f'(1)=0`).
   - Asserts `p95` SymPy step verification latency `< 120 ms`.
2. **Backend Integration Test (`CanvasInkVerificationIntegrationTest.java`)**:
   - Exercises `V042` persistence, `POST /api/v1/tutoring/canvas/{strokeStreamId}/verify-ink-step`, anti-spoiler check (never reveals the final answer if the learner writes `?`), and linking with `SocraticScaffoldingSession`.
3. **iOS Unit Tests (`InteractiveSocraticCanvasViewModelTests.swift`)**:
   - Verifies auto-pause on `canvasViewDidBeginUsingTool`, bounding-box calculation for newly added `PKStroke` elements, emerald/amber stroke highlight state transitions, and timeline resume index calculation.

### 7.2 Definition of Done (DoD)
- [x] `V042__create_canvas_learner_ink_interventions.sql` schema and indexes specified.
- [x] End-to-end latency from "Adımımı Kontrol Et" tap to Socratic voice/visual feedback `< 800 ms` (`p95`).
- [x] Zero final-answer leakage if a learner writes a blank or random guess on the canvas during an active scaffolding tier (`TIER_1`–`TIER_3`).
