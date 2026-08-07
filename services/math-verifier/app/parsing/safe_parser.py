from __future__ import annotations

import re

import sympy as sp
from sympy.parsing.sympy_parser import (
    implicit_multiplication_application,
    parse_expr,
    standard_transformations,
)

from app.domain.errors import UnsafeExpressionError
from app.parsing.parser_limits import MAX_EXPRESSION_LENGTH, MAX_OPERATIONS
from app.parsing.symbol_table import ALLOWED_FUNCTIONS, ALLOWED_SYMBOL_NAMES, allowed_locals

SAFE_CHARACTERS = re.compile(r"^[0-9A-Za-z_+\-*/^()., =]+$")
TRANSFORMATIONS = standard_transformations + (implicit_multiplication_application,)
SAFE_GLOBALS = {
    "__builtins__": {},
    "Integer": sp.Integer,
    "Float": sp.Float,
    "Rational": sp.Rational,
    "Symbol": sp.Symbol,
    "Add": sp.Add,
    "Mul": sp.Mul,
    "Pow": sp.Pow,
    **ALLOWED_FUNCTIONS,
}


def parse_safe_expression(expression: str, variables: list[str] | None = None) -> sp.Expr:
    normalized = expression.strip().replace("^", "**")
    if len(normalized) > MAX_EXPRESSION_LENGTH:
        raise UnsafeExpressionError("Expression is too long")
    if not SAFE_CHARACTERS.fullmatch(normalized):
        raise UnsafeExpressionError()

    local_dict = allowed_locals(variables)
    identifiers = set(re.findall(r"[A-Za-z_][A-Za-z0-9_]*", normalized))
    allowed_identifiers = ALLOWED_SYMBOL_NAMES | set(ALLOWED_FUNCTIONS)
    if not identifiers.issubset(allowed_identifiers):
        raise UnsafeExpressionError()

    try:
        parsed = parse_expr(
            normalized,
            local_dict=local_dict,
            global_dict=SAFE_GLOBALS,
            transformations=TRANSFORMATIONS,
            evaluate=True,
        )
    except Exception as exc:
        raise UnsafeExpressionError("Expression could not be parsed") from exc

    if len(list(sp.preorder_traversal(parsed))) > MAX_OPERATIONS:
        raise UnsafeExpressionError("Expression is too complex")

    return parsed

