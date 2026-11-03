"""Exact bounded polynomial slice, independent of the absent canonical_v2 set contract.

Supports identities and real equations of degree <=2 with rational roots (or empty/all
real sets). Rejects variable denominators, functions, nonpositive powers, constraints,
unknown domains and mixed semantics. No simplify/solve/parser or numeric proof.
"""
from fractions import Fraction
from math import isqrt

import sympy as sp

from app.api.schemas.attempt import AttemptRequest, AttemptResult, StepResult, unavailable
from app.api.schemas.execution import ExecutionRequest
from app.api.schemas.verifier_input import BinaryNode, FunctionNode, UnaryNode
from app.counterexample_engine import witness
from app.verifiers.canonical_execution import (
    OutsideSubset, Undefined, difference, exponent, polynomial, rational, validate,
)


def total_polynomial(node, symbol):
    # Inspect original syntax before cancellation: x/x never becomes a global identity.
    if isinstance(node, FunctionNode):
        raise OutsideSubset()
    if isinstance(node, UnaryNode):
        total_polynomial(node.operand, symbol)
    if isinstance(node, BinaryNode):
        total_polynomial(node.left, symbol)
        total_polynomial(node.right, symbol)
        if node.operator == "DIVIDE":
            try:
                if rational(node.right, {}) == 0:
                    raise OutsideSubset()
            except (OutsideSubset, Undefined):
                raise OutsideSubset() from None
        if node.operator == "POWER" and exponent(node.right) <= 0:
            raise OutsideSubset()
    return polynomial(node, symbol)


def prepare(statement, request, symbol):
    if statement is None:
        raise OutsideSubset()
    validation = ExecutionRequest(schemaVersion="verification-execution-v1",
        executionId=request.executionId, correlationId=request.correlationId,
        method="SYMBOLIC_EQUIVALENCE", original=statement, candidate=statement,
        variables=request.variables, restrictions=[])
    validate(validation)
    for node in (statement.expression, statement.left, statement.right):
        if node is not None:
            total_polynomial(node, symbol)
    if statement.kind == "EXPRESSION":
        p, q = polynomial(statement.expression, symbol)
        return p.mul_ground(1 / q.LC())
    return difference(statement.left, statement.right, symbol)


def roots(poly):
    if poly.is_zero:
        return None  # all real numbers, distinct from the empty finite set
    degree = poly.degree()
    if degree == 0:
        return frozenset()
    if degree > 2:
        raise OutsideSubset()
    coefficients = [Fraction(int(c.p), int(c.q)) for c in poly.all_coeffs()]
    if degree == 1:
        a, b = coefficients
        return frozenset((-b / a,))
    a, b, c = coefficients
    disc = b*b - 4*a*c
    if disc < 0:
        return frozenset()
    n, d = isqrt(disc.numerator), isqrt(disc.denominator)
    if n*n != disc.numerator or d*d != disc.denominator:
        raise OutsideSubset()  # irrational sets need a reviewed representation
    root = Fraction(n, d)
    return frozenset(((-b-root)/(2*a), (-b+root)/(2*a)))


def execute(request: AttemptRequest) -> AttemptResult:
    if request.restrictions or any(v.domain != "REAL" for v in request.variables):
        return unavailable(request, "UNSUPPORTED_MATH")
    variable = request.variables[0].symbol if request.variables else None
    symbol = sp.Symbol(variable or "_constant")
    try:
        before = prepare(request.original, request, symbol)
        if request.original.kind == "RELATION":
            roots(before)
    except (OutsideSubset, Undefined, ValueError, ZeroDivisionError):
        return unavailable(request, "UNSUPPORTED_MATH")
    previous = request.original
    results, first, stop = [], None, None
    for index, step in enumerate(request.steps, 1):
        if stop:
            results.append(StepResult(index=index, verdict="NOT_ASSESSED", reasonCode=stop,
                                      counterexampleStatus="NOT_APPLICABLE"))
            continue
        try:
            if step.statement is None or step.statement.kind != previous.kind:
                raise OutsideSubset()
            after = prepare(step.statement, request, symbol)
            candidates = ()
            if previous.kind == "EXPRESSION":
                equal = before == after
                reason = "EXACT_IDENTITY"
            else:
                old_roots, new_roots = roots(before), roots(after)
                equal = old_roots == new_roots
                candidates = tuple((old_roots or frozenset()) | (new_roots or frozenset()))
                reason = "SAME_SOLUTION_SET"
            if equal:
                results.append(StepResult(index=index, verdict="VALID_EQUIVALENT", reasonCode=reason,
                                          counterexampleStatus="NOT_APPLICABLE"))
                before, previous = after, step.statement
            else:
                proof = witness(previous, step.statement, variable, candidates)
                results.append(StepResult(index=index, verdict="FIRST_WRONG_STEP", reasonCode="NON_EQUIVALENT",
                    mistakeCode="ALG_TRANSFORMATION_INVALID", counterexample=proof,
                    counterexampleStatus="AVAILABLE" if proof else "COUNTEREXAMPLE_UNAVAILABLE"))
                first, stop = index, "EARLIER_DIVERGENCE"
        except (OutsideSubset, Undefined, ValueError, ZeroDivisionError):
            results.append(StepResult(index=index, verdict="UNCERTAIN", reasonCode="UNSUPPORTED_MATH",
                                      counterexampleStatus="NOT_APPLICABLE"))
            stop = "EARLIER_UNCERTAINTY"
    return AttemptResult(executionId=request.executionId, correlationId=request.correlationId,
        outcome="STEP_ERROR_FOUND" if first else "NEEDS_REVIEW" if stop else "INCOMPLETE_VALID_SO_FAR",
        firstWrongStepIndex=first, domain="REAL_POLYNOMIAL", steps=results)
