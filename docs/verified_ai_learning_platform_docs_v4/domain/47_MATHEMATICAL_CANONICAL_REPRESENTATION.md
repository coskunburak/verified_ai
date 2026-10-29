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

## Sprint 5.4 candidate boundary

Canonical v1 remains unchanged. A Solution Candidate references the selected canonical/parse revision,
preserves exact assumption/source-constraint/derived-restriction IDs, and stores bounded untrusted AST
claims separately. Single variable-left final relations and expression answers are supported; general
solution sets/multipart/geometry/calculus remain unsupported. Candidate structural/semantic acceptance
is not proof. Candidate prose is plain untrusted content, never code or verifier input authority.

## Sprint 5.8 execution representation

Canonical NUMBER lexemes remain exact base-ten strings. RATIONAL v1 uses this same numeric lexeme grammar;
a fractional quotient is represented by a DIVIDE AST, not a slash-bearing NUMBER string.
No existing schema version is reinterpreted or extended with root-set syntax.

The new internal execution contract carries typed original/candidate statements, declared variable domains and source restrictions.
Integer powers require INTEGER numeric nodes (optionally unary NEGATE), magnitude <=12; symbolic/fractional exponents are unsupported.
Zero to a nonpositive power and zero denominators cannot produce mathematical proof.
Typed validation now constructs unevaluated SymPy syntax to prevent eager expansion; validation still does not establish definedness or correctness.
Execution uses independently bounded rational/polynomial algorithms and retains original AST domain obligations.

The single final assignment can represent a candidate singleton for completeness only when independently derived roots match it exactly.
General finite sets, empty sets and infinite sets require a future versioned canonical answer contract; prose is never parsed as a set.

## Sprint 5.9 calculus representation boundary

`calculus-task-v1` names an internal, nonpersistent verification task. It contains one
explicit variable/domain, an open connected real interval with exact rational endpoints,
original/candidate ASTs, restriction references, scope identity and source/candidate
revisions. Limits additionally require a finite rational target and LEFT/RIGHT/TWO_SIDED;
integrals require ANTIDERIVATIVE_REPRESENTATIVE. No implicit C, complete family,
closed/disconnected interval, infinite target or higher-order task is introduced.

Existing canonical-problem-v1, verifier-input-v1 and solution-candidate-v1 remain unchanged.
ProblemParse calculus task labels lack authoritative approach/direction metadata and do
not become an accepted calculus source. No display text or model explanation supplies
missing metadata. Internal scope identifiers correlate responses; they confer no source
authorization. End-to-end calculus canonicalization/publication is BLOCKED pending
versioned source acquisition and owned source assembly. OWNER_VALIDATION_PENDING.

## Sprint 5.9 production integration closure

The closure adds problem-parse-v2 USER revisions with a bounded original AST, exactly
one REAL variable, open interval, typed restrictions and explicit approach/direction or
ANTIDERIVATIVE_REPRESENTATIVE scope. Existing correction evidence/review/currentness
rules apply; normalized expression AST must agree with the typed original. Free-text
assumptions do not become mathematical obligations. canonical-problem-v2 uses existing
owned canonical identity/revision columns and preserves the admitted parse and metadata.
solution-candidate-v2 proposes only an expression with exact source/canonical revision
references; canonical-solution-v2 is an immutable unverified projection. Published v1
schemas and calculus-execution-v1 wire meanings remain unchanged. The task-v1 wire scope
is an authorized server projection/correlation, not canonical authority.

Status: IMPLEMENTED_PENDING_OWNER_VALIDATION / CI_PENDING / ROLLOUT_DISABLED.
See [closure report](../sprints/phase05_ai_solving_and_verification_core/SPRINT_5.9_PRODUCTION_INTEGRATION_CLOSURE_EXECUTION_REPORT.md).
