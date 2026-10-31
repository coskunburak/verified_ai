"""Bounded deterministic binary64 diagnostics. Agreement and mismatch are never proof."""
from __future__ import annotations

import hashlib
import json
import math
from fractions import Fraction

from app.api.schemas.numeric import POLICY, NumericRequest, NumericResult, unavailable
from app.api.schemas.verifier_input import (
    BinaryNode,
    FunctionNode,
    NumberNode,
    UnaryNode,
    VariableNode,
)
from app.verifiers.canonical_execution import (
    OutsideSubset,
    Undefined,
    exponent,
    number,
    rational,
    validate,
)

ABS_TOL = REL_TOL = 1e-10


class Unstable(Exception):
    pass


def checked(value, error=0.0):
    if (not math.isfinite(value) or not math.isfinite(error) or abs(value) > 1e100
            or 0 < abs(value) < 1e-250 or error > 1e90):
        raise Unstable()
    return value, error + 4 * math.ulp(value)


def evaluate(node, values):
    if isinstance(node, NumberNode):
        return checked(float(number(node)))
    if isinstance(node, VariableNode):
        return checked(float(values[node.symbol]))
    if isinstance(node, UnaryNode):
        value, error = evaluate(node.operand, values)
        return -value, error
    if isinstance(node, BinaryNode):
        a, ea = evaluate(node.left, values)
        if node.operator == "POWER":
            power = exponent(node.right)
            if power <= 0 and abs(a) <= max(1e-10, 16 * ea):
                raise Unstable()
            value = a ** power
            if a != 0 and value == 0:
                raise Unstable()
            error = abs(power * a ** (power - 1)) * ea if power else 0.0
            return checked(value, error)
        b, eb = evaluate(node.right, values)
        if node.operator == "ADD":
            return checked(a + b, ea + eb)
        if node.operator == "SUBTRACT":
            return checked(a - b, ea + eb)
        if node.operator == "MULTIPLY":
            value = a * b
            if a and b and value == 0:
                raise Unstable()
            return checked(value, abs(a) * eb + abs(b) * ea + ea * eb)
        if abs(b) <= max(1e-10, 16 * eb):
            raise Unstable()
        value = a / b
        if a and value == 0:
            raise Unstable()
        return checked(value, (ea + abs(value) * eb) / (abs(b) - eb))
    if isinstance(node, FunctionNode):
        a, ea = evaluate(node.args[0], values)
        if node.function == "TAN":
            raise OutsideSubset()
        if node.function == "LOG":
            if a <= max(1e-10, 16 * ea):
                raise Unstable()
            return checked(math.log(a), ea / (a - ea))
        if node.function == "SQRT":
            if a <= max(1e-10, 16 * ea):
                raise Unstable()
            return checked(math.sqrt(a), ea / (2 * math.sqrt(a - ea)))
        if node.function == "EXP":
            if abs(a) > 200:
                raise Unstable()
            return checked(math.exp(a), math.exp(a + ea) * ea)
        if abs(a) > 1e6:
            raise Unstable()
        return checked(math.sin(a) if node.function == "SIN" else math.cos(a), ea)
    raise OutsideSubset()


def sample_points(request):
    payload = request.model_dump(mode="json", exclude={"executionId", "correlationId", "scopeId"})
    seed = hashlib.sha256((POLICY + json.dumps(payload, sort_keys=True, separators=(",", ":"))).encode()).digest()
    anchors = [Fraction(n) for n in (0, 1, -1, 2, -2, 4, -4, 8, -8)]
    seen = set()
    for index in range(16):
        if index < len(anchors):
            point = anchors[index]
        else:
            digest = hashlib.sha256(seed + bytes([index])).digest()
            numerator = int.from_bytes(digest[:4], "big") % 257 - 128
            for offset in range(16):
                point = Fraction((numerator + offset + 128) % 257 - 128, 16)
                if point not in seen:
                    break
        seen.add(point)
        yield point


def restrictions_hold(request, values):
    for restriction in request.restrictions:
        a, b = rational(restriction.left, values), rational(restriction.right, values)
        # Reject samples close to an excluded boundary, even when exact rational comparison succeeds.
        if restriction.relation in {"NOT_EQUALS", "LESS_THAN", "GREATER_THAN"} and abs(a - b) <= Fraction(1, 10**10):
            return False
        holds = {"EQUALS": a == b, "NOT_EQUALS": a != b, "LESS_THAN": a < b,
                 "LESS_THAN_OR_EQUAL": a <= b, "GREATER_THAN": a > b, "GREATER_THAN_OR_EQUAL": a >= b}
        if not holds[restriction.relation]:
            return False
    return True


def compare(left, right):
    a, ea = left
    b, eb = right
    tolerance = ABS_TOL + REL_TOL * max(abs(a), abs(b))
    uncertainty = ea + eb + 4 * math.ulp(a - b)
    if uncertainty > tolerance / 16:
        raise Unstable()
    difference = abs(a - b)
    if difference + uncertainty <= tolerance:
        return True
    if difference - uncertainty > tolerance:
        return False
    raise Unstable()


def execute(request: NumericRequest) -> NumericResult:
    if any(v.domain == "UNKNOWN" for v in request.variables):
        return unavailable(request, "BLOCKED", "UNKNOWN_DOMAIN")
    if any(v.domain == "INTEGER" for v in request.variables):
        return unavailable(request, "UNSUPPORTED", "INTEGER_DOMAIN")
    if len(request.variables) > 1 or request.original.kind != "EXPRESSION" or request.candidate.kind != "EXPRESSION":
        return unavailable(request, "UNSUPPORTED", "UNSUPPORTED_SUBSET")
    try:
        validate(request)
        admissible = evaluated = agreeing = disagreeing = unstable = 0
        for point in sample_points(request):
            values = {v.symbol: point for v in request.variables}
            try:
                if not restrictions_hold(request, values):
                    continue
                left = evaluate(request.original.expression, values)
                right = evaluate(request.candidate.expression, values)
                admissible += 1
                agrees = compare(left, right)
                evaluated += 1
                agreeing += int(agrees)
                disagreeing += int(not agrees)
            except Undefined:
                continue
            except (Unstable, ArithmeticError, ValueError):
                unstable += 1
        if unstable:
            status, reason = "INCONCLUSIVE", "UNSTABLE_SAMPLES"
        elif evaluated < 4:
            status, reason = "INCONCLUSIVE", "INSUFFICIENT_SAMPLES"
        elif disagreeing:
            status, reason = "MISMATCH", "SAMPLES_DIFFER"
        else:
            status, reason = "AGREEMENT", "SAMPLES_AGREE"
        return NumericResult(schemaVersion="numeric-diagnostic-result-v1", method="NUMERIC_SAMPLING", policyVersion=POLICY,
                             executionId=request.executionId, correlationId=request.correlationId,
                             scopeId=request.scopeId, status=status, reasonCode=reason,
                             restrictionIds=[r.id for r in request.restrictions], attempted=16,
                             admissible=admissible, evaluated=evaluated, agreeing=agreeing,
                             disagreeing=disagreeing, unstable=unstable)
    except OutsideSubset as error:
        return unavailable(request, "UNSUPPORTED", "COMPLEXITY_LIMIT" if error.reason == "COMPLEXITY_LIMIT" else "UNSUPPORTED_SUBSET")
