"""Exact open-interval and punctured-neighborhood obligations, before any cancellation."""
from fractions import Fraction
from types import SimpleNamespace

import sympy as sp

from app.api.schemas.calculus import fraction
from app.api.schemas.execution import Statement
from app.api.schemas.verifier_input import BinaryNode, FunctionNode, UnaryNode
from app.verifiers.canonical_execution import (
    OutsideSubset,
    Undefined,
    bounded,
    difference,
    exponent,
    multiply,
    polynomial,
    relation,
    validate,
)


def validate_task(request):
    # Reuse the released AST budgets without extending the released request contract.
    validate(SimpleNamespace(
        variables=[request.variable], method=request.method,
        original=Statement(kind="EXPRESSION", expression=request.original),
        candidate=Statement(kind="EXPRESSION", expression=request.candidate),
        restrictions=request.restrictions,
    ))
    if request.variable.domain == "UNKNOWN":
        raise OutsideSubset("UNKNOWN_DOMAIN", "BLOCKED")
    if request.variable.domain != "REAL":
        raise OutsideSubset("NON_REAL_DOMAIN", "BLOCKED")


def endpoints(request):
    return (None if request.interval.lower is None else fraction(request.interval.lower),
            None if request.interval.upper is None else fraction(request.interval.upper))


def interior(request):
    lower, upper = endpoints(request)
    if lower is None:
        return Fraction(0) if upper is None else bounded(upper - 1)
    return bounded(lower + 1) if upper is None else bounded((lower + upper) / 2)


def sym(value):
    return sp.Rational(value.numerator, value.denominator)


def nonzero_on_interval(poly, request):
    if poly.is_zero:
        return False
    lower, upper = endpoints(request)
    count = int(poly.count_roots(-sp.oo if lower is None else sym(lower),
                                sp.oo if upper is None else sym(upper)))
    # count_roots includes endpoints; calculus intervals are explicitly open.
    if lower is not None and poly.eval(sym(lower)) == 0:
        count -= 1
    if upper is not None and poly.eval(sym(upper)) == 0:
        count -= 1
    return count == 0


def interval_sign(node, request, symbol):
    try:
        p, q = polynomial(node, symbol)
    except OutsideSubset as error:
        if error.reason != "UNSUPPORTED_SUBSET":
            raise
        if isinstance(node, BinaryNode) and node.operator in {"MULTIPLY", "DIVIDE"}:
            return interval_sign(node.left, request, symbol) * interval_sign(node.right, request, symbol)
        if isinstance(node, FunctionNode) and node.function == "EXP":
            return 1
        if isinstance(node, FunctionNode) and node.function == "SQRT":
            require_sign(node.args[0], request, symbol, positive=True)
            return 1
        raise
    if not nonzero_on_interval(p, request) or not nonzero_on_interval(q, request):
        raise OutsideSubset("DOMAIN_OBSTRUCTION", "BLOCKED")
    point = sym(interior(request))
    return 1 if p.eval(point) / q.eval(point) > 0 else -1


def require_sign(node, request, symbol, positive=False):
    if interval_sign(node, request, symbol) < 0 and positive:
        raise OutsideSubset("DOMAIN_OBSTRUCTION", "BLOCKED")


def obligations(node, request, symbol, punctured=False):
    if isinstance(node, UnaryNode):
        obligations(node.operand, request, symbol, punctured)
    elif isinstance(node, BinaryNode):
        obligations(node.left, request, symbol, punctured)
        obligations(node.right, request, symbol, punctured)
        required = node.right if node.operator == "DIVIDE" else (
            node.left if node.operator == "POWER" and exponent(node.right) <= 0 else None)
        if required is not None:
            if punctured:
                p, q = polynomial(required, symbol)
                if p.is_zero or q.is_zero:
                    raise Undefined()
            else:
                require_sign(required, request, symbol)
    elif isinstance(node, FunctionNode):
        if punctured or node.function == "TAN":
            raise OutsideSubset()
        obligations(node.args[0], request, symbol)
        if node.function in {"LOG", "SQRT"}:
            # Strict positivity also proves differentiability of sqrt, not merely definition.
            require_sign(node.args[0], request, symbol, positive=True)


def interval_restrictions(request, symbol):
    for r in request.restrictions:
        obligations(r.left, request, symbol)
        obligations(r.right, request, symbol)
        _p, q = polynomial(r.left, symbol)
        _, s = polynomial(r.right, symbol)
        numerator, denominator = difference(r.left, r.right, symbol), multiply(q, s)
        if numerator.is_zero:
            valid = relation(r.relation, 0, 0)
        elif r.relation == "EQUALS":
            valid = False
        elif r.relation == "NOT_EQUALS":
            valid = nonzero_on_interval(numerator, request)
        else:
            point = sym(interior(request))
            valid = nonzero_on_interval(numerator, request) and relation(
                r.relation, numerator.eval(point) / denominator.eval(point), 0)
        if not valid:
            raise OutsideSubset("DOMAIN_OBSTRUCTION", "BLOCKED")


def sides(request):
    return (-1, 1) if request.approach.direction == "TWO_SIDED" else (
        (-1,) if request.approach.direction == "LEFT" else (1,))


def neighborhood(request):
    point = fraction(request.approach.target)
    lower, upper = endpoints(request)
    for side in sides(request):
        if side < 0 and (lower is not None and point <= lower or upper is not None and point > upper):
            raise OutsideSubset("DOMAIN_OBSTRUCTION", "BLOCKED")
        if side > 0 and (lower is not None and point < lower or upper is not None and point >= upper):
            raise OutsideSubset("DOMAIN_OBSTRUCTION", "BLOCKED")


def order_at(poly, point):
    for order in range(13):
        value = poly.eval(sym(point))
        if value != 0:
            return order, bounded(Fraction(int(value.p), int(value.q)))
        poly = poly.diff()
    return None, Fraction(0)


def local_sign(poly, point, side):
    order, value = order_at(poly, point)
    if order is None:
        return 0
    return (1 if value > 0 else -1) * (side ** order)


def neighborhood_restrictions(request, symbol):
    point = fraction(request.approach.target)
    for r in request.restrictions:
        obligations(r.left, request, symbol, punctured=True)
        obligations(r.right, request, symbol, punctured=True)
        _, q = polynomial(r.left, symbol)
        _, s = polynomial(r.right, symbol)
        p, denominator = difference(r.left, r.right, symbol), multiply(q, s)
        for side in sides(request):
            sign = local_sign(p, point, side) * local_sign(denominator, point, side)
            if not relation(r.relation, sign, 0):
                raise OutsideSubset("DOMAIN_OBSTRUCTION", "BLOCKED")
