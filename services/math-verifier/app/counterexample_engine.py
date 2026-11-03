"""Bounded exact witnesses. Equation witnesses compare truth, never raw LHS values."""
from fractions import Fraction

from app.api.schemas.attempt import CounterexampleEvidence
from app.verifiers.canonical_execution import OutsideSubset, Undefined, exact, rational

CANDIDATES = tuple(map(Fraction, (0, 1, -1, 2, -2, 3, -3, 4, -4))) + (Fraction(1, 2), Fraction(-1, 2))


def evaluate(statement, values):
    if statement.kind == "EXPRESSION":
        return rational(statement.expression, values)
    return rational(statement.left, values) == rational(statement.right, values)


def witness(previous, submitted, variable, roots=()):
    # Roots are at most four exact rationals derived by the bounded linear/quadratic solver.
    candidates = list(dict.fromkeys(CANDIDATES + tuple(sorted(roots))))
    for value in candidates:
        values = {} if variable is None else {variable: value}
        try:
            before, after = evaluate(previous, values), evaluate(submitted, values)
        except (OutsideSubset, Undefined, ZeroDivisionError):
            continue
        if before == after:
            continue
        # Re-evaluate original ASTs before publication, not simplified polynomial projections.
        if evaluate(previous, values) != before or evaluate(submitted, values) != after:
            continue
        equation = previous.kind == "RELATION"
        render = lambda x: ("true" if x else "false") if isinstance(x, bool) else str(x)
        return CounterexampleEvidence(
            witnessType="EQUATION_TRUTH" if equation else "EXPRESSION_VALUES",
            variableValues={k: exact(v) for k, v in values.items()},
            previousStepEvaluation=render(before), submittedStepEvaluation=render(after),
            claimRefuted="EQUATION_EQUIVALENCE" if equation else "EXPRESSION_IDENTITY",
            explanationCode="DIFFERENT_TRUTH" if equation else "UNEQUAL_VALUES",
        )
    return None
