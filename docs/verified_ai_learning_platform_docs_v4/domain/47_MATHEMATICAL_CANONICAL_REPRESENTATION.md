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

In Sprint 4.6, Layer 4 is represented by two versioned JSON documents:
- `canonical-problem-v1`: durable backend-owned `CanonicalProblem` stored from an accepted `ProblemParse`.
- `verifier-input-v1`: narrower internal verifier handoff payload consumed by the Python math verifier.

Both documents use typed AST nodes with explicit `kind` discriminators. They do not include Java class metadata, Python code, SymPy strings, solution steps, verification verdicts, skill classification, or difficulty estimates.

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

Sprint 4.6 production v1 uses AST/json rather than `safe_symbolic_expression` strings for the backend-to-verifier contract.

## Sprint 4.6 canonical v1 scope

Canonical v1 supports:
- arithmetic expressions,
- algebraic expressions,
- single equations,
- single inequalities,
- source-explicit relational constraints that fit the same expression grammar.

Canonical v1 does not yet canonicalize calculus, systems, solution sets, units, diagrams, probability, statistics, linear algebra, or multi-part problems. Those structures remain parse-layer data until a later sprint defines safe canonical semantics.

Allowed AST nodes:
- `NUMBER`
- `VARIABLE`
- `UNARY`
- `BINARY`
- `FUNCTION`

Allowed operators:
- `NEGATE`
- `ADD`
- `SUBTRACT`
- `MULTIPLY`
- `DIVIDE`
- `POWER`

Allowed functions:
- `SQRT`
- `SIN`
- `COS`
- `TAN`
- `LOG`
- `EXP`

Numeric literals are stored as exact strings. Decimals are not converted to binary floating point.

## Canonical constraints and restrictions

Source-explicit parser constraints are stored separately from deterministic derived restrictions:
- `sourceConstraints`: constraints explicitly supported by the parse/source.
- `derivedRestrictions`: restrictions discovered by safe parsing, such as denominator and function-domain restrictions.

Denominators produce `DENOMINATOR_NON_ZERO` restrictions. Product denominators are decomposed into separate factor restrictions where possible. Algebraic cancellation must not remove a restriction, so `(x^2 - 1)/(x - 1)` retains the restriction `(x - 1) != 0`.

`sqrt(argument)` derives `SQRT_DOMAIN_NON_NEGATIVE`, `log(argument)` derives `LOG_DOMAIN_POSITIVE`, and `tan(argument)` derives `TAN_DOMAIN_COS_NON_ZERO`.

Default variable domain is `UNKNOWN` unless source-backed parser data states a stricter supported domain.

## Expression safety

Verifier accepts only allowlisted operations/functions. Parse into AST and enforce:
- node count,
- nesting depth,
- exponent magnitude,
- symbolic complexity,
- supported functions.

Sprint 4.6 v1 limits are:
- maximum expression length: 512 characters,
- maximum AST nodes: 120,
- maximum AST depth: 32,
- maximum numeric exponent magnitude: 12,
- maximum numeric literal digits: 64,
- maximum function nesting depth: 8.

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
