# Mathematical Canonical Representation

## Purpose

Raw OCR text is not sufficient for reliable solving, equivalence checking, verification or attempt alignment. The system needs a normalized mathematical representation.

## Representation layers

### Layer 1 — Raw input
Image, handwritten text, typed text, PDF crop.

### Layer 2 — Extracted text/math
OCR/vision representation retaining source spans and uncertainty.

### Layer 3 — Semantic ProblemParse
Structured task, variables, expressions, constraints and canonical skill.

### Layer 4 — Verifier representation
Safe parser-compatible symbolic representation for deterministic math service.

## Example

Visual problem:
"Find the derivative of y = sin(x²)."

Semantic parse:
```json
{
  "problemType": "DERIVATIVE",
  "dependentVariable": "y",
  "independentVariables": ["x"],
  "expression": {"format": "SYMPY_SAFE", "value": "sin(x**2)"},
  "task": "DIFFERENTIATE",
  "constraints": []
}
```

## Representation formats

Possible fields:
- display_latex
- normalized_text
- safe_symbolic_expression
- AST/json structure.

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
