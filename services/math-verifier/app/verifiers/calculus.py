"""Bounded structural differentiation and rational finite limits; no symbolic integration."""
from __future__ import annotations

import math
from fractions import Fraction

import sympy as sp

from app.api.schemas.calculus import CalculusResult, Certificate, Diagnostic, fraction
from app.api.schemas.verifier_input import (
    BinaryNode,
    FunctionNode,
    NumberNode,
    UnaryNode,
    VariableNode,
)
from app.verifiers.calculus_domains import (
    endpoints,
    interval_restrictions,
    neighborhood,
    neighborhood_restrictions,
    obligations,
    order_at,
    sides,
    validate_task,
)
from app.verifiers.canonical_execution import (
    OutsideSubset,
    Undefined,
    bounded,
    check_restrictions,
    exact,
    exponent,
    multiply,
    number,
    polynomial,
    polynomial_guard,
    rational,
)


class DifferentialRing:
    """Functions are opaque formal atoms: zero proves identity; nonzero need not disprove it."""
    def __init__(self, variable):
        self.variable = variable
        self.generators = sp.symbols("_x _a0:16")
        self.atoms = {}
        self.zero = self.constant(0)
        self.one = self.constant(1)

    def constant(self, value):
        value = Fraction(value)
        return sp.Poly(sp.Rational(value.numerator, value.denominator), *self.generators, domain="QQ")

    def scalar(self, value):
        return self.constant(value), self.constant(1)

    def add(self, a, b, sign=1):
        return polynomial_guard(multiply(a[0], b[1]) + sign * multiply(b[0], a[1])), multiply(a[1], b[1])

    def mul(self, a, b):
        return multiply(a[0], b[0]), multiply(a[1], b[1])

    def div(self, a, b):
        if b[0].is_zero:
            raise Undefined()
        return multiply(a[0], b[1]), multiply(a[1], b[0])

    def power(self, a, n):
        if n < 0:
            a = self.div(self.scalar(1), a)
        result = self.scalar(1)
        for _ in range(abs(n)):
            result = self.mul(result, a)
        return result

    def atom(self, function, argument):
        key = (function, argument.model_dump_json())
        if key not in self.atoms:
            if len(self.atoms) >= 16:
                raise OutsideSubset("COMPLEXITY_LIMIT")
            self.atoms[key] = self.generators[len(self.atoms) + 1]
        return sp.Poly(self.atoms[key], *self.generators, domain="QQ"), self.one

    def convert(self, node, differentiate=False):
        zero, one = self.scalar(0), self.scalar(1)
        if isinstance(node, NumberNode):
            return self.scalar(number(node)), zero
        if isinstance(node, VariableNode):
            return (sp.Poly(self.generators[0], *self.generators, domain="QQ"), self.one), one
        if isinstance(node, UnaryNode):
            value, derivative = self.convert(node.operand, differentiate)
            return self.mul(self.scalar(-1), value), self.mul(self.scalar(-1), derivative)
        if isinstance(node, BinaryNode):
            u, du = self.convert(node.left, differentiate)
            if node.operator == "POWER":
                n = exponent(node.right)
                return self.power(u, n), (self.mul(self.scalar(n), self.mul(self.power(u, n-1), du))
                                          if differentiate and n else zero)
            v, dv = self.convert(node.right, differentiate)
            if node.operator in {"ADD", "SUBTRACT"}:
                sign = 1 if node.operator == "ADD" else -1
                return self.add(u, v, sign), self.add(du, dv, sign) if differentiate else zero
            if node.operator == "MULTIPLY":
                return self.mul(u, v), self.add(self.mul(du, v), self.mul(u, dv)) if differentiate else zero
            return self.div(u, v), self.div(self.add(self.mul(du, v), self.mul(u, dv), -1), self.mul(v, v)) if differentiate else zero
        arg = node.args[0]
        if node.function == "TAN":
            raise OutsideSubset()
        value = self.atom(node.function, arg)
        if not differentiate:
            return value, zero
        u, du = self.convert(arg, True)
        if node.function == "SIN":
            factor = self.atom("COS", arg)
        elif node.function == "COS":
            factor = self.mul(self.scalar(-1), self.atom("SIN", arg))
        elif node.function == "EXP":
            factor = value
        elif node.function == "LOG":
            factor = self.div(one, u)
        else:
            factor = self.div(one, self.mul(self.scalar(2), value))
        return value, self.mul(factor, du)


def numeric_value(node, variable, value):
    if isinstance(node, NumberNode):
        result = float(number(node))
    elif isinstance(node, VariableNode):
        result = value
    elif isinstance(node, UnaryNode):
        result = -numeric_value(node.operand, variable, value)
    elif isinstance(node, BinaryNode):
        a = numeric_value(node.left, variable, value)
        b = numeric_value(node.right, variable, value)
        if node.operator == "ADD":
            result = a + b
        elif node.operator == "SUBTRACT":
            result = a - b
        elif node.operator == "MULTIPLY":
            result = a * b
            if a and b and not result:
                raise ArithmeticError()
        elif node.operator == "DIVIDE":
            if abs(b) < 1e-100:
                raise ArithmeticError()
            result = a / b
            if a and not result:
                raise ArithmeticError()
        else:
            n = exponent(node.right)
            if a == 0 and n <= 0:
                raise ArithmeticError()
            result = a ** n
            if a and not result:
                raise ArithmeticError()
    else:
        a = numeric_value(node.args[0], variable, value)
        if node.function == "TAN":
            raise ArithmeticError()
        functions = {"SIN": math.sin, "COS": math.cos, "EXP": math.exp, "LOG": math.log, "SQRT": math.sqrt}
        result = functions[node.function](a)
        if node.function == "EXP" and result == 0:
            raise ArithmeticError()
    if not math.isfinite(result) or abs(result) > 1e100 or 0 < abs(result) < 1e-100:
        raise ArithmeticError()
    return result


def diagnostics(request):
    point = fraction(request.approach.target)
    lower, upper = endpoints(request)
    attempted = evaluated = agreeing = 0
    for side in sides(request):
        distance = Fraction(1)
        bound = lower if side < 0 else upper
        if bound is not None:
            distance = min(distance, abs(point - bound) / 2)
        for power in range(2, 10):
            attempted += 1
            x = bounded(point + side * distance / (2 ** power))
            try:
                if float(x) == float(point) or not check_restrictions(request, {request.variable.symbol: x}):
                    continue
                # Rational subexpressions at each original denominator/log/sqrt argument
                # are checked exactly, so rounding cannot admit a forbidden point.
                exact_point_domains(request.original, request.variable.symbol, x)
                expected = float(rational(request.candidate, {}))
                observed = numeric_value(request.original, request.variable.symbol, float(x))
                if (not math.isfinite(expected) or abs(expected) > 1e100
                        or 0 < abs(expected) < 1e-100):
                    continue
                evaluated += 1
                agreeing += int(math.isclose(observed, expected, rel_tol=1e-8, abs_tol=1e-8))
            except (ArithmeticError, ValueError, OutsideSubset, Undefined):
                continue
    return Diagnostic(policy="calculus-binary64-v1", attempted=attempted, evaluated=evaluated, agreeing=agreeing)


def exact_point_domains(node, variable, value):
    values = {variable: value}
    if isinstance(node, UnaryNode):
        exact_point_domains(node.operand, variable, value)
    elif isinstance(node, BinaryNode):
        exact_point_domains(node.left, variable, value)
        exact_point_domains(node.right, variable, value)
        if node.operator == "DIVIDE" and rational(node.right, values) == 0:
            raise Undefined()
        if node.operator == "POWER" and exponent(node.right) <= 0 and rational(node.left, values) == 0:
            raise Undefined()
    elif isinstance(node, FunctionNode):
        exact_point_domains(node.args[0], variable, value)
        if node.function in {"LOG", "SQRT"}:
            a = rational(node.args[0], values)
            if a < 0 or node.function == "LOG" and a == 0:
                raise Undefined()
        if node.function == "TAN":
            raise OutsideSubset()


def execute(request):
    def result(outcome, reason, proof=None, diagnostic=None):
        return CalculusResult(schemaVersion="calculus-execution-result-v1", methodVersion="v1",
                              verifierVersion="bounded-calculus-v1",
                              executionId=request.executionId, correlationId=request.correlationId,
                              scope=request.scope, method=request.method, outcome=outcome,
                              reasonCode=reason, restrictionIds=[r.id for r in request.restrictions],
                              proof=proof, diagnostic=diagnostic)
    try:
        validate_task(request)
        symbol = sp.Symbol(request.variable.symbol)
        if request.method == "FINITE_LIMIT":
            neighborhood(request)
            neighborhood_restrictions(request, symbol)
            expected = rational(request.candidate, {})
            try:
                obligations(request.original, request, symbol, punctured=True)
                p, q = polynomial(request.original, symbol)
            except OutsideSubset as error:
                if error.reason != "UNSUPPORTED_SUBSET":
                    raise
                return result("INCONCLUSIVE", "NUMERICAL_DIAGNOSTIC", diagnostic=diagnostics(request))
            point = fraction(request.approach.target)
            pn, pv = order_at(p, point)
            qn, qv = order_at(q, point)
            if qn is None:
                raise Undefined()
            if pn is not None and pn < qn:
                return result("INCONCLUSIVE", "NONFINITE_LIMIT")
            value = Fraction(0) if pn is None or pn > qn else bounded(pv / qv)
            equal = value == expected
            return result("PROVEN" if equal else "DISPROVEN", "EXACT_FINITE_LIMIT" if equal else "EXACT_CONTRADICTION",
                          Certificate(descriptor="RATIONAL_FINITE_LIMIT", scope="PUNCTURED_NEIGHBORHOOD",
                                      leftValue=exact(value), rightValue=exact(expected)))
        interval_restrictions(request, symbol)
        obligations(request.original, request, symbol)
        obligations(request.candidate, request, symbol)
        ring = DifferentialRing(request.variable.symbol)
        integral = request.method == "INTEGRAL_DIFFERENTIATION_BACKCHECK"
        source = request.candidate if integral else request.original
        target = request.original if integral else request.candidate
        _, derivative = ring.convert(source, True)
        proposed, _ = ring.convert(target)
        residual = ring.add(derivative, proposed, -1)[0]
        if not residual.is_zero and ring.atoms:
            return result("INCONCLUSIVE", "SYMBOLIC_UNRESOLVED")
        equal = residual.is_zero
        return result("PROVEN" if equal else "DISPROVEN",
                      ("EXACT_ANTIDERIVATIVE" if integral else "EXACT_DERIVATIVE") if equal else "EXACT_CONTRADICTION",
                      Certificate(descriptor=("ANTIDERIVATIVE_IDENTITY" if integral else "DERIVATIVE_IDENTITY") if equal else "RATIONAL_NONIDENTITY",
                                  scope="ANTIDERIVATIVE_REPRESENTATIVE" if integral else "OPEN_INTERVAL",
                                  leftValue=None, rightValue=None))
    except OutsideSubset as error:
        if error.reason not in {"UNSUPPORTED_SUBSET", "COMPLEXITY_LIMIT", "UNKNOWN_DOMAIN", "NON_REAL_DOMAIN", "DOMAIN_OBSTRUCTION"}:
            return result("UNSUPPORTED", "UNSUPPORTED_SUBSET")
        return result(error.outcome, error.reason)
    except Undefined:
        return result("BLOCKED", "DOMAIN_OBSTRUCTION")
