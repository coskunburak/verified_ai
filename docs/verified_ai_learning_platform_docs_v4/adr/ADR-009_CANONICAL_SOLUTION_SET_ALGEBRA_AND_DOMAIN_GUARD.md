# ADR-009 — Canonical Solution Set Algebra and Domain Constraint Guard

- **Status:** Accepted (2026-10-31)
- **Decision:** Upgrade `CanonicalMathDocument` to `canonical_v2` and extend `CanonicalSolution` and `services/math-verifier` to represent and deterministically verify multi-root solution sets (`FiniteSet`), intervals (`Interval`, `Union`), piecewise expressions (`Piecewise`), systems of equations (`EquationSystem`), and automated domain restrictions (`DomainConstraintGuard` / `extraneous_roots`).

## Context (Bağlam ve Teknik Açık)

Inspection of `services/api/src/main/java/com/verifiedai/solving/domain/model/canonical/CanonicalSolution.java` and `services/math-verifier/app/verifiers/canonical_execution.py` revealed that `canonical_v1` requires a single `Equality` (`variable = value`) for `EQUATION` verification (`SOLVE_FINAL_EQUATION_NOT_ISOLATED`). This blocks:

1. Quadratic and polynomial equations with multiple real roots (e.g., $x^2 - 5x + 6 = 0 \implies x \in \{2, 3\}$).
2. No-solution (`EmptySet`) and identity (`Reals`) outcomes.
3. Inequalities yielding open/closed intervals or unions (e.g., $x \in (-2, 3] \cup [5, \infty)$).
4. Extraneous root traps (`YKS TYT/AYT Tanım Kümesi Tuzakları`) where an algebraic transformation introduces a root that violates a denominator ($\text{denom}(x) = 0$), even-root radicand ($g(x) < 0$), or logarithm argument ($h(x) \le 0$).

## Decision Details (Mimari ve Semantik Sözleşme)

1. **Additive Schema Evolution (`canonical_v2`):**
   - Preserve full backward compatibility with `canonical_v1` single-equality expressions.
   - Add AST node kinds to `domain/47_MATHEMATICAL_CANONICAL_REPRESENTATION.md`, `CanonicalSolution.java`, and `services/math-verifier`:
     - `FiniteSet` (`elements: List<CanonicalNode>`), `EmptySet`, `Interval` (`start`, `end`, `leftOpen`, `rightOpen`), `Union`, `Intersection`, `Piecewise`, `EquationSystem`, `AbsoluteValue` (`Abs`), `Logarithm` (`Log`), `Trigonometric` (`Sin`, `Cos`, `Tan`).
2. **Deterministic Domain Constraint Guard (`DomainConstraintGuard`):**
   - Before verifying candidate roots or step transitions, `math-verifier` extracts the natural domain $D_{\text{natural}}$ of the original problem statement using SymPy (`continuous_domain` + explicit denominator/radicand/logarithm inspection).
   - Every candidate root $r \in S_{\text{candidate}}$ is tested against $D_{\text{natural}}$. Any root $r \notin D_{\text{natural}}$ is flagged in `VerificationEvidence.extraneous_roots` and triggers `CONTRADICTED` if the candidate solution included $r$ as a valid root.
3. **Counterexample Generation (`CounterexampleEngine`):**
   - When an equation, inequality, or algebraic step transition fails symbolic equivalence (`simplify(step_i - step_{i+1}) != 0`), `math-verifier` searches a deterministic rational/integer probe grid $x \in \{0, \pm 1, \pm 2, \pm \frac{1}{2}, \pm 3, \pm 5\}$ within $D_{\text{natural}}$ to return a concrete numerical counterexample (`counterexample: {variable, value, lhs_eval, rhs_eval}`) for pedagogical UX display.

## Consequences

- `services/math-verifier` remains 100% deterministic, stateless, and `eval`-free.
- Quadratic equations, rational equations with extraneous roots, radical equations, and interval inequalities become first-class `VERIFIED` citizens in Production V1.
