# Mathematical Canonical Representation

## Purpose

Raw OCR text is not sufficient for reliable solving, equivalence checking, verification or attempt alignment. The system needs a normalized mathematical representation.

## Representation layers

### Layer 1 — Raw input
Image, handwritten text, typed text, PDF crop.

### Layer 2 — Extracted text/math
OCR/vision representation retaining source spans and uncertainty.

### Layer 3 — Semantic ProblemParse
Sprint 4.5 parser-level structure: subject/topic/task/problem type, parser-level expressions, variables, explicit constraints, explicit assumptions, source evidence references, visual-quality risk, and uncertainty. It is provider-independent and versioned, but it is not a canonical safe AST, not a verifier input, not a verified answer, and not authoritative skill/difficulty classification.

### Layer 4 — Verifier representation
Safe parser-compatible symbolic representation for deterministic math service.

## Example

Visual problem:
"Find the derivative of y = sin(x²)."

Semantic parse:
```json
{
  "schemaVersion": "problem-parse-v1",
  "supportStatus": "SUPPORTED",
  "subjectId": "MATH",
  "topicId": "MATH.CALCULUS.DIFFERENTIATION",
  "taskType": "DIFFERENTIATE",
  "problemType": "DERIVATIVE",
  "expressions": [
    {
      "role": "PRIMARY",
      "sourceText": "sin(x^2)",
      "normalizedText": "sin(x^2)",
      "displayLatex": "\\sin(x^2)",
      "sourceBlockIds": ["block-1"]
    }
  ],
  "variables": [{"symbol": "x", "role": "VARIABLE"}],
  "constraints": [],
  "assumptions": [],
  "sourceEvidenceRefs": [{"blockId": "block-1", "fieldPath": "expressions[0]"}]
}
```

## Representation formats

Possible fields:
- display_latex
- normalized_text
- safe_symbolic_expression
- AST/json structure.

In Sprint 4.5, `display_latex` and `normalized_text` are parser-level notation only. `safe_symbolic_expression` and AST/json verifier structures belong to Sprint 4.6 and later.

Never pass arbitrary user strings into Python eval.

## Expression safety

Verifier accepts only allowlisted operations/functions. Parse into AST and enforce:
- node count,
- nesting depth,
- exponent magnitude,
- symbolic complexity,
- supported functions.

## Equality versus equivalence

`x + x` and `2*x` are textually different but mathematically equivalent.

Store normalized representation but do not assume string equality means mathematical equality.

## Domain constraints

Represent assumptions explicitly:
- x > 0
- x ∈ R
- denominator ≠ 0
- integer-only conditions.

Verification without domain assumptions can produce false conclusions.

## Solution sets

Equation answers should use structured sets rather than comma-separated text.

## Units

Future physics/statistics support should represent units separately from numeric expression to enable dimensional validation.

## Display

User-facing LaTeX/rendering is derived from canonical representation. Do not use display string as backend computational source when a structured expression exists.
