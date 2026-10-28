# Verification Engine

## Purpose

Verification is the core trust differentiator. An answer is not verified because one or two language models agree. Verification combines independent solver evidence with deterministic mathematical checks wherever possible.

## Components

### VerificationPlanner
Maps ProblemType and representation to applicable checks.

Sprint 5.7 implements this as the internal owner-scoped `VerificationPlanning` query
under policy `verification-planner-v1`. Plans are ephemeral, deterministic selections
bound to exact canonical source/solution lineage; they contain no executed evidence
or verification verdict. Owner local Gates A–F passed; sprint CI remains pending.
See `sprints/phase05_ai_solving_and_verification_core/SPRINT_5.7_EXECUTION_REPORT.md`.

The current canonical capability registry advertises no executable method. The
existing symbolic-equivalence endpoint accepts strings and lacks an approved
canonical execution adapter; typed `validate-input` validates syntax only. Arithmetic,
substitution, domain checking and completeness executors are later work. Derivative
and integral backchecks remain unavailable until typed executors, mathematical
evaluation and release approval exist. Numerical sampling is supporting evidence.

### MathVerifierGateway
Calls the internal Python verifier service.

### SolverAgreementEvaluator
Compares normalized independent solver results.

### VerificationPolicy
Combines all evidence into overall status.

## Signal catalog

- SYMBOLIC_EQUIVALENCE
- NUMERIC_SAMPLING
- DERIVATIVE_BACKCHECK
- INTEGRAL_DIFFERENTIATION_BACKCHECK
- EQUATION_SUBSTITUTION
- SOLUTION_SET_COMPLETENESS where feasible
- MATRIX_OPERATION_CHECK
- UNIT_DIMENSION_CHECK
- DOMAIN_CONSTRAINT_CHECK
- SOLVER_AGREEMENT
- PARSE_STABILITY

## Status semantics

### VERIFIED
Required policy evidence passes and no contradictory material signal exists.

### PARTIALLY_VERIFIED
A meaningful subset is checked but full claim cannot be deterministically established.

### UNVERIFIED
Insufficient deterministic evidence, ambiguous input or contradictory result. UNVERIFIED does not mean wrong.

## Derivative example

Problem: differentiate `sin(x^2)`.
Candidate: `2x cos(x^2)`.

Verifier independently differentiates original, simplifies difference and may sample safe numeric points. Equivalent result produces strong evidence.

## Integral example

Candidate antiderivative F is differentiated and compared to integrand. Constant-of-integration equivalence is handled semantically.

## Equation example

Candidate roots are substituted into original. Domain restrictions are checked. Extraneous roots prevent full verification. Completeness is assessed where feasible.

## Numeric sampling

Numeric sampling is usually supporting evidence, not a mathematical proof. Use deterministic seeded points, skip singular/domain-invalid regions and version tolerances.

## Parse risk

A perfectly verified answer to a misread problem is wrong for the user. Verification summary must incorporate parse ambiguity as a separate risk dimension.

## Policy versioning

Every VerificationRun stores `policy_version`. New policy creates a new run rather than rewriting history.

## Verifier safety

- no arbitrary `eval`,
- expression parser allowlist,
- complexity limits,
- CPU/time limits,
- internal authentication,
- no public ingress.

## User-visible evidence

Examples:
- Symbolic check passed.
- Independent solution matched.
- Numeric back-check passed.

Do not expose private model reasoning.

## Coverage metric

Track verification status distribution per ProblemType. This is a strategic product metric because it shows where our trust promise is strongest.
