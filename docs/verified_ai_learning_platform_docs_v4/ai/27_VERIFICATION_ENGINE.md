# Verification Engine

## Purpose

Verification is the core trust differentiator. An answer is not verified because one or two language models agree. Verification combines independent solver evidence with deterministic mathematical checks wherever possible.

## Components

### VerificationPlanner
Maps ProblemType and representation to applicable checks.

Sprint 5.7 implements this as the internal owner-scoped `VerificationPlanning` query
under policy `verification-planner-v1`. Plans are ephemeral, deterministic selections
bound to exact canonical source/solution lineage; they contain no executed evidence
or verification verdict. Owner local Gates A–F and implementation CI passed. Current-head
checks and final review are tracked in PR #31.
See `sprints/phase05_ai_solving_and_verification_core/SPRINT_5.7_EXECUTION_REPORT.md`.

At the Sprint 5.7 baseline, the canonical capability registry advertised no executable method. The
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

## Sprint 5.8 method execution (owner validation pending)

The internal `VerificationExecution` application operation prepares an owner-authorized canonical source/solution,
executes selected methods outside transactions, then rechecks the current source and exact plan under a short owner lock.
The new `verification-execution-v1` request and `verification-execution-result-v1` response are separate from typed syntax validation.
Method outcomes are PROVEN, DISPROVEN, INCONCLUSIVE, UNSUPPORTED, BLOCKED and EXECUTION_FAILED. None assigns overall VERIFIED.

| Method | Executable subset when approved | Conservative boundary |
| --- | --- | --- |
| Arithmetic | Exact rational arithmetic and decimal lexemes; integral powers of magnitude <=12 | Undefined source/value is inconclusive; 1024-bit intermediate budget |
| Equivalence | One REAL/INTEGER variable; rational polynomials of degree <=12 and <=128 terms | Retain original AST denominator/negative-power obligations; linear source restrictions; nonidentity on restricted domains is inconclusive |
| Substitution | One unambiguous exact rational assignment into the original equality | Check declared domain and all restrictions; non-rational function values remain unsupported |
| Domain | Exact rational restrictions, real sqrt/log arguments, rational denominators; zero-argument tangent | Global function-domain implication and nonzero transcendental values remain unsupported |
| Completeness | Degree <=2 rational polynomial numerator, exact rational roots, original denominator/restriction filtering | Only singleton final assignments can prove complete; multi-root and degenerate sets do not acquire implicit set syntax |

`x/x` versus `1` can prove equality on an explicitly restricted source domain x!=0. Without that restriction,
execution is inconclusive; cancellation never proves an unrestricted identity. sqrt(x^2) versus x remains unsupported globally.
Only coefficient comparison over an unrestricted infinite REAL/INTEGER domain can disprove polynomial identity without a concrete witness.
No numerical sampling, floating point, unrestricted simplify/solve, explanation parsing or AI validation is used.

Capability activation requires execution-enabled, owner-accepted and an explicit method allowlist. Defaults advertise zero executable methods.
Approved sets receive distinct `canonical-capabilities-v2-<identity>` versions; old plan semantics remain unchanged.
The bounded subset registry describes implementation support, not runtime health or proof coverage for every input.

V021 stores one immutable run aggregate with bounded method evidence, exact composite source/solution ownership FK,
method/policy/capability/verifier versions, domain assumptions, restriction references, proof descriptors and correlation/time provenance.
A request key binds one owner/session to one run scope. Same-key retries return the original evidence; a fresh attempt after a timeout uses a new key.
Concurrent computations may repeat bounded work but publish one aggregate. Stale computations are discarded before publication.
Explicit history reauthorizes ownership and labels current-source freshness independently; no historical row is promoted by mutation.
Account export includes these aggregates; canonical source/job/account deletion cascades through the FK. Direct evidence update/delete is prohibited.

The legacy string equivalence route is local/test-only and carries no product proof authority.
See `sprints/phase05_ai_solving_and_verification_core/SPRINT_5.8_EXECUTION_REPORT.md` for owner commands and operational limits.

Execution accepts only canonical variables with an explicit VARIABLE role; parameter/unknown roles are blocked before constructing the wire payload.
