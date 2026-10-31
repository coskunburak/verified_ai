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

## Sprint 5.9 internal calculus checkpoint

The separately versioned `calculus-execution-v1` and `calculus-execution-result-v1`
contracts implement first derivatives, antiderivative representative backchecks and
finite rational limits. Exact structural differentiation plus polynomial-ring coefficient
comparison proves scoped identities; rational nonidentity on a nonempty open interval
can disprove them. Function atoms provide sufficient identity certificates only, never
nonidentity proofs. Domain obligations are checked on original ASTs before cancellation.
Limits use polynomial orders of vanishing and source restrictions in an explicitly
sided punctured neighborhood. Poles yield INCONCLUSIVE/NONFINITE_LIMIT; no infinity
or nonexistence claim is certified. Numerical limit diagnostics are always INCONCLUSIVE.

The internal task representation is not durable canonical calculus. Production planner
capabilities remain unchanged and reject calculus activation. Python calculus defaults
off and enabling it outside local/test is rejected. Source-backed approach metadata,
canonical/candidate versions and an authorized publication path remain BLOCKED.
OWNER_VALIDATION_PENDING; see `sprints/phase05_ai_solving_and_verification_core/SPRINT_5.9_EXECUTION_REPORT.md`.

## Sprint 5.9 production integration closure

A versioned calculus planner selects derivative backcheck, antiderivative representative
backcheck or FINITE_LIMIT from authoritative v2 sources. The generic v1 execution contract
cannot execute FINITE_LIMIT. Explicit local/test owner flags and authenticated Python
readiness are required for availability. Shared HTTP/1.1 transport calls the unchanged
bounded worker outside transactions. Short publication transactions reauthorize ownership
and recheck current source/plan before writing tagged calculus-evidence-v1 into the existing
immutable VerificationRun. Exact certificates and bounded numerical INCONCLUSIVE diagnostics
remain distinct; no overall VERIFIED state is assigned.

Status: IMPLEMENTED_PENDING_OWNER_VALIDATION / CI_PENDING / ROLLOUT_DISABLED.
See [closure report](../sprints/phase05_ai_solving_and_verification_core/SPRINT_5.9_PRODUCTION_INTEGRATION_CLOSURE_EXECUTION_REPORT.md).

## Sprint 5.10 composite evidence and numerical diagnostics

`CompositeEvidencePolicy` produces `composite-evidence-v1`, a deterministic assessment
of one current plan/run/candidate/source. EXACT_SUPPORT means all required exact method
obligations are proven in a nonempty supported source scope; it never assigns VERIFIED.
Exact contradictions retain their original method/purpose. Conflicting exact domain
claims, complete-root-set proof versus invalid-root evidence, and numerical disagreement
are explicit. Missing domain or equation completeness
remains unresolved even when other methods succeed. Empty affine source domains block
composition; unsupported feasibility stays unresolved. Input evidence order has no effect.

`CompositeVerification.current` reuses owned canonical/planning reads (including secondary
parent preconditions), finishes them before the short source-owner transaction, and
rechecks the fresh plan before projecting immutable evidence. Stale/absent eligible runs
return no assessment; authorization and malformed stored evidence fail closed. Optional
diagnostics must match the exact execution, correlation, source-derived scope and original
ASTs. No worker/network call or durable write occurs in this read path.

The additive numeric endpoint and Java port implement `equivalence-binary64-v1`:
16 deterministic distinct attempted points on the 1/16 grid in [-8,8], at least four stable evaluations,
absolute and relative tolerance 1e-10. Agreement and mismatch cannot carry proof.
Original AST definedness, exact rational source restrictions, cancellation/error estimates,
near-singularity, nonfinite/overflow/underflow checks precede classification. Unknown and
integer domains are not sampled as reals. A bounded sample window cannot establish domain
coverage or disprove a globally quantified statement. Optional sampling is never automatic.

New numeric flags default false and production activation is rejected in both runtimes.
`numeric-capabilities-v1` is an implementation/acceptance inventory, not exact planner
approval; the old capability identity and five-method behavior remain unchanged.
Calculus integration exists after PR #35 but remains excluded from this composite v1 and
production-disabled. Local A–H owner gates passed; the final evidence-documentation
docs-check and hosted CI remain pending. See the Sprint 5.10 execution report.

## Sprint 5.11 arbitration implementation boundary

`VerificationPolicy` is the sole aggregate status authority (`verification-policy-v1`).
It recomposes the exact run with `composite-evidence-v1` or the tagged
`composite-calculus-evidence-v1` against the authoritative plan,
source and solution. Source/version integrity and review gates precede proof coverage.
Exact disproof or conflicting evidence blocks promotion, including partial promotion;
secondary disagreement/incompatible assumptions/invalid independence/stale lineage requires
explicit reconciliation. Agreement alone adds no proof. A complete exact final-answer
claim may become VERIFIED only behind accepted release flags. A meaningful proven subset
may be PARTIALLY_VERIFIED with explicit missing methods. Domain-only proof is insufficient.
EXPLANATION_STEPS_UNCHECKED remains visible even for a VERIFIED final answer.

Decisions are immutable V023 records referencing the original run and evidence IDs.
Publication reauthorizes source ownership and rechecks latest primary, latest run, plan and
secondary snapshot after evaluation. Read-only GET never invokes a verifier, solver or
usage reservation. New evidence/source/solution/corroboration invalidates the current
projection without rewriting history. Retry is advice, capped at three runs and five minutes;
there is no automatic retry or paid arbitration. Public GET/POST contracts and iOS display
are separate from generation state. All tests are NOT_RUN_BY_AGENT.

Status: IMPLEMENTED_PENDING_OWNER_VALIDATION for the 2026-10-31 calculus closure.
CalculusCompositePolicy requires matching interval, target/direction, integral scope and
restriction ASTs as well as immutable run/source/candidate identity. An exact certificate
establishes domain obligations only in its declared scope. Public calculus verdicts use
OPEN_INTERVAL, ANTIDERIVATIVE_REPRESENTATIVE or PUNCTURED_NEIGHBORHOOD, never generic
FINAL_ANSWER. Numeric diagnostics stay nonproof; exact disproof and secondary conflicts
block promotion. The antiderivative representative does not establish a complete family.
Optional numeric diagnostics can be evaluated by the pure policy, but are not durably
published or automatically collected by arbitration. The production path uses durable
exact evidence only. Production activation is prohibited; local/test flags default false.
See [implementation map](../sprints/phase05_ai_solving_and_verification_core/SPRINT_5.11_IMPLEMENTATION_MAP.md)
and [execution report](../sprints/phase05_ai_solving_and_verification_core/SPRINT_5.11_EXECUTION_REPORT.md).
