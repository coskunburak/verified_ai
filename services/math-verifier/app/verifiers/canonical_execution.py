"""Bounded exact arithmetic and rational-polynomial proofs over typed canonical ASTs.

No parse_expr, sympify, simplify, solve, floating point, or model output is used as proof.
Original ASTs supply domain obligations before any polynomial cancellation can occur.
"""
from __future__ import annotations

from fractions import Fraction
from math import isqrt

import sympy as sp

from app.api.schemas.execution import ExactValue, ExecutionRequest, ExecutionResult, Proof
from app.api.schemas.verifier_input import (
    BinaryNode,
    FunctionNode,
    NumberNode,
    UnaryNode,
    VariableNode,
)

MAX_BITS = 1024
MAX_DEGREE = 12
MAX_TERMS = 128
MAX_TOTAL_NODES = 1200


class OutsideSubset(Exception):
    def __init__(self, reason="UNSUPPORTED_SUBSET", outcome="UNSUPPORTED"):
        self.reason, self.outcome = reason, outcome


class Undefined(Exception):
    pass


def bounded(value: Fraction) -> Fraction:
    if max(value.numerator.bit_length(), value.denominator.bit_length()) > MAX_BITS:
        raise OutsideSubset("COMPLEXITY_LIMIT")
    return value


def number(node: NumberNode) -> Fraction:
    if sum(c.isdigit() for c in node.value) > 64:
        raise OutsideSubset("COMPLEXITY_LIMIT")
    if node.numericType == "INTEGER" and "." in node.value:
        raise OutsideSubset()
    # RATIONAL v1 uses the same exact decimal lexeme; fractions are DIVIDE nodes.
    return bounded(Fraction(node.value))


def exponent(node) -> int:
    sign = 1
    if isinstance(node, UnaryNode):
        sign, node = -1, node.operand
    if not isinstance(node, NumberNode) or node.numericType != "INTEGER":
        raise OutsideSubset()
    value = sign * number(node)
    if value.denominator != 1 or abs(value) > 12:
        raise OutsideSubset()
    return int(value)


def validate(request: ExecutionRequest) -> None:
    declared = {v.symbol for v in request.variables}
    total = 0

    def walk(node, depth=1, functions=0):
        nonlocal total
        total += 1
        if total > MAX_TOTAL_NODES or depth > 32 or functions > 8:
            raise OutsideSubset("COMPLEXITY_LIMIT")
        if isinstance(node, NumberNode):
            number(node)
        elif isinstance(node, VariableNode):
            if node.symbol not in declared:
                raise OutsideSubset()
        elif isinstance(node, UnaryNode):
            walk(node.operand, depth + 1, functions)
        elif isinstance(node, BinaryNode):
            if node.operator == "POWER":
                exponent(node.right)
            walk(node.left, depth + 1, functions)
            walk(node.right, depth + 1, functions)
        elif isinstance(node, FunctionNode):
            if request.method in {"ARITHMETIC_EVALUATION", "SYMBOLIC_EQUIVALENCE", "SOLUTION_SET_COMPLETENESS"}:
                raise OutsideSubset()
            walk(node.args[0], depth + 1, functions + 1)
        else:
            raise OutsideSubset()

    for statement in (request.original, request.candidate):
        before = total
        for node in (statement.expression, statement.left, statement.right):
            if node is not None:
                walk(node)
        if total - before > 120:
            raise OutsideSubset("COMPLEXITY_LIMIT")
    for restriction in request.restrictions:
        before = total
        walk(restriction.left)
        walk(restriction.right)
        if total - before > 120:
            raise OutsideSubset("COMPLEXITY_LIMIT")


def rational(node, values: dict[str, Fraction]) -> Fraction:
    if isinstance(node, NumberNode):
        return number(node)
    if isinstance(node, VariableNode):
        if node.symbol not in values:
            raise OutsideSubset("ASSIGNMENT_REQUIRED", "BLOCKED")
        return values[node.symbol]
    if isinstance(node, UnaryNode):
        return -rational(node.operand, values)
    if isinstance(node, BinaryNode):
        left = rational(node.left, values)
        if node.operator == "POWER":
            power = exponent(node.right)
            if left == 0 and power <= 0:
                raise Undefined()
            if max(left.numerator.bit_length(), left.denominator.bit_length()) * abs(power) > MAX_BITS:
                raise OutsideSubset("COMPLEXITY_LIMIT")
            return bounded(left ** power)
        right = rational(node.right, values)
        if node.operator == "ADD":
            return bounded(left + right)
        if node.operator == "SUBTRACT":
            return bounded(left - right)
        if node.operator == "MULTIPLY":
            return bounded(left * right)
        if right == 0:
            raise Undefined()
        return bounded(left / right)
    arg = rational(node.args[0], values)
    if node.function == "SQRT":
        if arg < 0:
            raise Undefined()
        a, b = isqrt(arg.numerator), isqrt(arg.denominator)
        if a*a != arg.numerator or b*b != arg.denominator:
            raise OutsideSubset()
        return Fraction(a, b)
    if node.function == "LOG" and arg <= 0:
        raise Undefined()
    if (node.function == "LOG" and arg == 1) or (node.function in {"SIN", "TAN"} and arg == 0):
        return Fraction(0)
    if node.function in {"COS", "EXP"} and arg == 0:
        return Fraction(1)
    raise OutsideSubset()



def defined(node, values):
    """Domain proof does not require evaluating a defined transcendental value."""
    if isinstance(node, (NumberNode, VariableNode)):
        rational(node, values)
    elif isinstance(node, UnaryNode):
        defined(node.operand, values)
    elif isinstance(node, BinaryNode):
        defined(node.left, values)
        defined(node.right, values)
        if node.operator == "DIVIDE" and rational(node.right, values) == 0:
            raise Undefined()
        if node.operator == "POWER" and exponent(node.right) <= 0 and rational(node.left, values) == 0:
            raise Undefined()
    else:
        defined(node.args[0], values)
        argument = rational(node.args[0], values)
        if node.function == "SQRT" and argument < 0 or node.function == "LOG" and argument <= 0:
            raise Undefined()
        if node.function == "TAN" and argument != 0:
            # Canonical v1 has no pi literal. Nonzero arguments need a future exact trig contract.
            raise OutsideSubset()


def relation(name, left, right) -> bool:
    return {
        "EQUALS": left == right, "NOT_EQUALS": left != right,
        "LESS_THAN": left < right, "LESS_THAN_OR_EQUAL": left <= right,
        "GREATER_THAN": left > right, "GREATER_THAN_OR_EQUAL": left >= right,
    }[name]


def check_restrictions(request, values) -> bool:
    return all(relation(r.relation, rational(r.left, values), rational(r.right, values)) for r in request.restrictions)


def assignment(request):
    if len(request.variables) != 1 or request.candidate.kind != "RELATION":
        raise OutsideSubset("ASSIGNMENT_REQUIRED", "BLOCKED")
    var = request.variables[0]
    if var.domain == "UNKNOWN":
        raise OutsideSubset("UNKNOWN_DOMAIN", "BLOCKED")
    left, right = request.candidate.left, request.candidate.right
    if isinstance(right, VariableNode):
        left, right = right, left
    if not isinstance(left, VariableNode) or left.symbol != var.symbol:
        raise OutsideSubset("ASSIGNMENT_REQUIRED", "BLOCKED")
    try:
        value = rational(right, {})
    except Undefined:
        raise OutsideSubset("UNDEFINED_VALUE", "INCONCLUSIVE") from None
    return var, value


def polynomial_guard(poly):
    if (not poly.is_zero and poly.total_degree() > MAX_DEGREE) or len(poly.terms()) > MAX_TERMS:
        raise OutsideSubset("COMPLEXITY_LIMIT")
    for coefficient in poly.coeffs():
        bounded(Fraction(int(coefficient.p), int(coefficient.q)))
    return poly


def multiply(left, right):
    if not left.is_zero and not right.is_zero and (
        left.total_degree() + right.total_degree() > MAX_DEGREE
        or len(left.terms()) * len(right.terms()) > MAX_TERMS
    ):
        raise OutsideSubset("COMPLEXITY_LIMIT")
    return polynomial_guard(left * right)


def polynomial(node, symbol):
    def constant(n):
        return sp.Poly(sp.Rational(n.numerator, n.denominator), symbol, domain=sp.QQ)
    one = constant(Fraction(1))
    if isinstance(node, NumberNode):
        return constant(number(node)), one
    if isinstance(node, VariableNode):
        if node.symbol != str(symbol):
            raise OutsideSubset()
        return sp.Poly(symbol, symbol, domain=sp.QQ), one
    if isinstance(node, UnaryNode):
        p, q = polynomial(node.operand, symbol)
        return -p, q
    if not isinstance(node, BinaryNode):
        raise OutsideSubset()
    p, q = polynomial(node.left, symbol)
    if node.operator == "POWER":
        power = exponent(node.right)
        if power <= 0 and p.is_zero:
            raise Undefined()
        if power < 0:
            p, q = q, p
        out_p, out_q = one, one
        for _ in range(abs(power)):
            out_p, out_q = multiply(out_p, p), multiply(out_q, q)
        return out_p, out_q
    r, s = polynomial(node.right, symbol)
    if node.operator == "MULTIPLY":
        return multiply(p, r), multiply(q, s)
    if node.operator == "DIVIDE":
        if r.is_zero:
            raise Undefined()
        return multiply(p, s), multiply(q, r)
    sign = 1 if node.operator == "ADD" else -1
    return polynomial_guard(multiply(p, s) + sign * multiply(r, q)), multiply(q, s)


def difference(left, right, symbol):
    p, q = polynomial(left, symbol)
    r, s = polynomial(right, symbol)
    return polynomial_guard(multiply(p, s) - multiply(r, q))


def global_domain(request, symbol):
    """Prove a nonempty ordinary domain for linear restrictions; do not use vacuous truth."""
    if len(request.variables) != 1 or request.variables[0].domain == "UNKNOWN":
        raise OutsideSubset("UNKNOWN_DOMAIN", "BLOCKED")
    lower = upper = None
    lower_closed = upper_closed = True
    excluded = set()
    for restriction in request.restrictions:
        # Nonconstant denominators in constraints would require independent obligations.
        _p, q = polynomial(restriction.left, symbol)
        _r, s = polynomial(restriction.right, symbol)
        if q.total_degree() > 0 or s.total_degree() > 0:
            raise OutsideSubset()
        poly = difference(restriction.left, restriction.right, symbol)
        divisor = q.LC() * s.LC()
        poly = polynomial_guard(poly.mul_ground(1 / divisor))
        if poly.is_zero or poly.degree() == 0:
            if not relation(restriction.relation, poly.eval(0), 0):
                raise OutsideSubset("CONTRADICTORY_CONSTRAINTS", "BLOCKED")
            continue
        if poly.degree() != 1:
            raise OutsideSubset()
        a, b = (Fraction(int(c.p), int(c.q)) for c in poly.all_coeffs())
        root, op = -b/a, restriction.relation
        if a < 0:
            op = {"GREATER_THAN": "LESS_THAN", "GREATER_THAN_OR_EQUAL": "LESS_THAN_OR_EQUAL",
                  "LESS_THAN": "GREATER_THAN", "LESS_THAN_OR_EQUAL": "GREATER_THAN_OR_EQUAL"}.get(op, op)
        if op == "NOT_EQUALS":
            excluded.add(root)
        elif op == "EQUALS":
            if lower is None or root > lower:
                lower, lower_closed = root, True
            if upper is None or root < upper:
                upper, upper_closed = root, True
        elif op.startswith("GREATER"):
            closed = op.endswith("OR_EQUAL")
            if lower is None or root > lower:
                lower, lower_closed = root, closed
            elif root == lower:
                lower_closed = lower_closed and closed
        else:
            closed = op.endswith("OR_EQUAL")
            if upper is None or root < upper:
                upper, upper_closed = root, closed
            elif root == upper:
                upper_closed = upper_closed and closed
    if lower is not None and upper is not None and (lower > upper or lower == upper and (
        not lower_closed or not upper_closed or lower in excluded
    )):
        raise OutsideSubset("CONTRADICTORY_CONSTRAINTS", "BLOCKED")
    if request.variables[0].domain == "INTEGER" and lower is not None and upper is not None:
        first = -(-lower.numerator // lower.denominator)
        last = upper.numerator // upper.denominator
        if not lower_closed and lower == first:
            first += 1
        if not upper_closed and upper == last:
            last -= 1
        if last < first or last-first+1 <= len(excluded) and all(Fraction(n) in excluded for n in range(first, last+1)):
            raise OutsideSubset("CONTRADICTORY_CONSTRAINTS", "BLOCKED")

    def required(node, op):
        # Exact structural matching preserves source restrictions, including cancellation holes.
        if not any(r.relation == op and r.left == node and isinstance(r.right, NumberNode)
                   and number(r.right) == 0 for r in request.restrictions):
            try:
                if relation(op, rational(node, {}), Fraction(0)):
                    return
            except (OutsideSubset, Undefined):
                pass
            raise OutsideSubset("MISSING_RESTRICTION", "INCONCLUSIVE")

    def obligations(node):
        if isinstance(node, UnaryNode):
            obligations(node.operand)
        elif isinstance(node, BinaryNode):
            obligations(node.left)
            obligations(node.right)
            if node.operator == "DIVIDE":
                required(node.right, "NOT_EQUALS")
            if node.operator == "POWER" and exponent(node.right) <= 0:
                required(node.left, "NOT_EQUALS")
        elif isinstance(node, FunctionNode):
            # Polynomial global proofs deliberately exclude functions.
            raise OutsideSubset()

    for statement in (request.original, request.candidate):
        for node in (statement.expression, statement.left, statement.right):
            if node is not None:
                obligations(node)


def exact(value):
    return ExactValue(numerator=str(value.numerator), denominator=str(value.denominator))


def execute(request: ExecutionRequest) -> ExecutionResult:
    def result(outcome, reason, descriptor=None, left=None, right=None, count=None):
        proof = None if descriptor is None else Proof(
            descriptor=descriptor, leftValue=None if left is None else exact(left),
            rightValue=None if right is None else exact(right), distinctRootCount=count,
        )
        return ExecutionResult(executionId=request.executionId, correlationId=request.correlationId,
                               method=request.method, outcome=outcome, reasonCode=reason,
                               restrictionIds=[r.id for r in request.restrictions], proof=proof)
    try:
        validate(request)
        method = request.method
        if any(v.domain == "UNKNOWN" for v in request.variables):
            raise OutsideSubset("UNKNOWN_DOMAIN", "BLOCKED")
        if method == "ARITHMETIC_EVALUATION":
            if request.variables or request.original.kind != "EXPRESSION" or request.candidate.kind != "EXPRESSION":
                raise OutsideSubset()
            if not check_restrictions(request, {}):
                raise OutsideSubset("CONTRADICTORY_CONSTRAINTS", "BLOCKED")
            try:
                left = rational(request.original.expression, {})
            except Undefined:
                return result("INCONCLUSIVE", "UNDEFINED_SOURCE")
            right = rational(request.candidate.expression, {})
            return result("PROVEN" if left == right else "DISPROVEN", "EXACT_EQUAL" if left == right else "EXACT_UNEQUAL",
                          "EXACT_RATIONAL_COMPARISON", left, right)
        if method == "SYMBOLIC_EQUIVALENCE":
            if request.original.kind != "EXPRESSION" or request.candidate.kind != "EXPRESSION" or len(request.variables) != 1:
                raise OutsideSubset()
            symbol = sp.Symbol(request.variables[0].symbol)
            global_domain(request, symbol)
            diff = difference(request.original.expression, request.candidate.expression, symbol)
            if diff.is_zero:
                return result("PROVEN", "POLYNOMIAL_IDENTITY", "POLYNOMIAL_COEFFICIENT_COMPARISON")
            # A nonzero polynomial can vanish on a restricted domain. Do not infer disproof.
            if request.restrictions:
                return result("INCONCLUSIVE", "POLYNOMIAL_NONIDENTITY")
            return result("DISPROVEN", "POLYNOMIAL_NONIDENTITY", "POLYNOMIAL_COEFFICIENT_COMPARISON")
        if method == "DOMAIN_CONSTRAINT_CHECK" and request.original.kind == "EXPRESSION":
            if request.candidate.kind != "EXPRESSION":
                raise OutsideSubset()
            if not request.variables:
                if not check_restrictions(request, {}):
                    raise Undefined()
                defined(request.original.expression, {})
                defined(request.candidate.expression, {})
            else:
                global_domain(request, sp.Symbol(request.variables[0].symbol))
            return result("PROVEN", "DOMAIN_SATISFIED", "DOMAIN_CHECK")
        if request.original.kind != "RELATION":
            raise OutsideSubset()
        var, value = assignment(request)
        values = {var.symbol: value}
        try:
            valid_domain = (var.domain != "INTEGER" or value.denominator == 1) and check_restrictions(request, values)
            if valid_domain:
                defined(request.original.left, values)
                defined(request.original.right, values)
                if method != "DOMAIN_CONSTRAINT_CHECK":
                    left, right = rational(request.original.left, values), rational(request.original.right, values)
        except Undefined:
            valid_domain = False
        if not valid_domain:
            return result("DISPROVEN", "DOMAIN_VIOLATION", "DOMAIN_CHECK")
        if method == "DOMAIN_CONSTRAINT_CHECK":
            return result("PROVEN", "DOMAIN_SATISFIED", "DOMAIN_CHECK")
        if method == "EQUATION_SUBSTITUTION":
            return result("PROVEN" if left == right else "DISPROVEN", "VALID_ROOT" if left == right else "INVALID_ROOT",
                          "ORIGINAL_EQUATION_SUBSTITUTION", left, right)
        symbol = sp.Symbol(var.symbol)
        poly = difference(request.original.left, request.original.right, symbol)
        if poly.is_zero or poly.degree() == 0:
            return result("INCONCLUSIVE", "ROOT_SET_NOT_REPRESENTABLE")
        if poly.degree() > 2:
            raise OutsideSubset()
        coefficients = [Fraction(int(c.p), int(c.q)) for c in poly.all_coeffs()]
        if poly.degree() == 1:
            a, b = coefficients
            roots = {-b/a}
        else:
            a, b, c = coefficients
            discriminant = b*b - 4*a*c
            if discriminant < 0:
                roots = set()
            else:
                n, d = isqrt(discriminant.numerator), isqrt(discriminant.denominator)
                if n*n != discriminant.numerator or d*d != discriminant.denominator:
                    raise OutsideSubset()
                delta = Fraction(n, d)
                roots = {(-b-delta)/(2*a), (-b+delta)/(2*a)}
        admitted = set()
        for root in roots:
            if var.domain == "INTEGER" and root.denominator != 1:
                continue
            try:
                at = {var.symbol: root}
                if check_restrictions(request, at) and rational(request.original.left, at) == rational(request.original.right, at):
                    admitted.add(root)
            except Undefined:
                continue
        # A singleton assignment is the only set claim expressible in canonical-solution-v1.
        complete = admitted == {value}
        return result("PROVEN" if complete else "DISPROVEN", "UNIQUE_SOLUTION" if complete else "INCOMPLETE_SOLUTION_SET",
                      "BOUNDED_POLYNOMIAL_ROOT_SET", count=len(admitted))
    except OutsideSubset as exc:
        return result(exc.outcome, exc.reason)
    except Undefined:
        return result("INCONCLUSIVE", "UNDEFINED_VALUE")
