# ADR-010 — Native 2D Mathematical Rendering and Step-Diff Highlighting on iOS

- **Status:** Accepted (2026-10-31)
- **Decision:** Replace the plain-text string rendering in `MathematicalExpressionView.swift` with native 2D mathematical typesetting (`SwiftMath` / CoreGraphics `MTMathUILabel`) while preserving `accessibilityMathematicallySpoken` VoiceOver descriptions and deterministic sub-expression step-diff highlighting.

## Context (Bağlam ve UX Denetimi)

Inspection of `apps/ios/VerifiedAI/Features/ProblemSolving/Presentation/Components/MathematicalExpressionView.swift` confirmed that Sprint 6.1/6.2 renders mathematical formulas using a plain `Text(trimmedExpression)` with `.monospaced` font design. While acceptable as a structural placeholder, high school (YKS TYT/AYT) and university Calculus students cannot read nested fractions, integrals, limits, radicals, or exponents in linear ASCII/raw LaTeX form. Competing head-to-head with Photomath, Symbolab, and ThetaWise requires publication-quality 2D mathematical typesetting with zero WebView layout jitter.

## Decision Details

1. **Native CoreGraphics Math Rendering (`SwiftMath`):**
   - Integrate `SwiftMath` (native iOS `MTMathUILabel` bridged via `UIViewRepresentable` inside `MathematicalExpressionView.swift`) for synchronous, zero-WebView-overhead 2D rendering of LaTeX expressions inside `ScrollView` and `LazyVStack` step cards.
   - Maintain a deterministic fallback to the current monospaced `Text` view if a malformed LaTeX token fails native layout parsing.
2. **Sub-Expression Diff & Trap Highlighting:**
   - Support color-coded semantic token highlighting (`\textcolor` or bounding-box overlays) driven by `math-verifier` step-equivalence and `Analyze My Work` diagnostics so the exact altered term, sign error, or violated domain restriction is visually pinpointed on the student's screen.
3. **Accessibility Preservation:**
   - Keep the existing `accessibilityMathematicallySpoken` localized Turkish and English accessibility readout pipeline intact so VoiceOver users receive natural spoken mathematics regardless of visual rendering mode.

## Consequences

- Adds a vetted, lightweight native math rendering dependency governed under `quality/53_DEPENDENCY_AND_LIBRARY_GOVERNANCE.md`.
- Dramatically improves student comprehension, trust perception, and conversion readiness on iOS.
