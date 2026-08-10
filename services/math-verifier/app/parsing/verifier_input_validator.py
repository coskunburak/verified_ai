from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal

import sympy as sp

from app.api.schemas.verifier_input import (
    BinaryNode,
    ExpressionNode,
    FunctionNode,
    NumberNode,
    UnaryNode,
    VariableNode,
    VerifierInputRequest,
    VerifierInputValidationResponse,
)
from app.domain.errors import UnsafeExpressionError
from app.parsing.parser_limits import (
    MAX_CANONICAL_AST_DEPTH,
    MAX_CANONICAL_AST_NODES,
    MAX_CANONICAL_EXPONENT_MAGNITUDE,
    MAX_CANONICAL_FUNCTION_NESTING_DEPTH,
    MAX_CANONICAL_NUMERIC_DIGITS,
)


@dataclass(frozen=True)
class Complexity:
    nodes: int
    depth: int
    function_depth: int


def validate_verifier_input(payload: VerifierInputRequest) -> VerifierInputValidationResponse:
    declared = {variable.symbol for variable in payload.variables}
    if len(declared) != len(payload.variables):
        raise UnsafeExpressionError("Verifier input declares duplicate variables")

    symbols = {symbol: sp.Symbol(symbol) for symbol in declared}
    for statement in payload.statements:
        if statement.kind == "EXPRESSION":
            if statement.expression is None or statement.left is not None or statement.right is not None:
                raise UnsafeExpressionError("Expression statement shape is invalid")
            _convert(statement.expression, symbols)
        else:
            if statement.left is None or statement.right is None or statement.expression is not None:
                raise UnsafeExpressionError("Relation statement shape is invalid")
            _convert(statement.left, symbols)
            _convert(statement.right, symbols)

    for restriction in payload.restrictions:
        _convert(restriction.left, symbols)
        _convert(restriction.right, symbols)

    return VerifierInputValidationResponse(
        statementCount=len(payload.statements),
        restrictionCount=len(payload.restrictions),
    )


def _convert(node: ExpressionNode, symbols: dict[str, sp.Symbol]) -> sp.Expr:
    complexity = _complexity(node)
    if (
        complexity.nodes > MAX_CANONICAL_AST_NODES
        or complexity.depth > MAX_CANONICAL_AST_DEPTH
        or complexity.function_depth > MAX_CANONICAL_FUNCTION_NESTING_DEPTH
    ):
        raise UnsafeExpressionError("Verifier input exceeds AST complexity limits")
    return _to_sympy(node, symbols)


def _to_sympy(node: ExpressionNode, symbols: dict[str, sp.Symbol]) -> sp.Expr:
    if isinstance(node, NumberNode):
        digit_count = sum(character.isdigit() for character in node.value)
        if digit_count > MAX_CANONICAL_NUMERIC_DIGITS:
            raise UnsafeExpressionError("Numeric literal exceeds verifier limits")
        return sp.Rational(node.value) if node.numericType in {"DECIMAL", "RATIONAL"} else sp.Integer(node.value)

    if isinstance(node, VariableNode):
        if node.symbol not in symbols:
            raise UnsafeExpressionError("Verifier input references undeclared variable")
        return symbols[node.symbol]

    if isinstance(node, UnaryNode):
        return -_to_sympy(node.operand, symbols)

    if isinstance(node, BinaryNode):
        left = _to_sympy(node.left, symbols)
        right = _to_sympy(node.right, symbols)
        match node.operator:
            case "ADD":
                return left + right
            case "SUBTRACT":
                return left - right
            case "MULTIPLY":
                return left * right
            case "DIVIDE":
                return left / right
            case "POWER":
                _validate_exponent(node.right)
                return left**right
        raise UnsafeExpressionError("Unsupported binary operator")

    if isinstance(node, FunctionNode):
        argument = _to_sympy(node.args[0], symbols)
        functions = {
            "SQRT": sp.sqrt,
            "SIN": sp.sin,
            "COS": sp.cos,
            "TAN": sp.tan,
            "LOG": sp.log,
            "EXP": sp.exp,
        }
        return functions[node.function](argument)

    raise UnsafeExpressionError("Unsupported expression node")


def _validate_exponent(node: ExpressionNode) -> None:
    negative = False
    candidate = node
    if isinstance(node, UnaryNode):
        if node.operator != "NEGATE":
            raise UnsafeExpressionError("Unsupported exponent unary operator")
        negative = True
        candidate = node.operand
    if not isinstance(candidate, NumberNode):
        raise UnsafeExpressionError("Symbolic exponents are outside verifier v1")
    value = Decimal(candidate.value)
    if negative:
        value = -value
    if abs(value) > Decimal(MAX_CANONICAL_EXPONENT_MAGNITUDE):
        raise UnsafeExpressionError("Exponent magnitude exceeds verifier limits")


def _complexity(node: ExpressionNode) -> Complexity:
    if isinstance(node, NumberNode | VariableNode):
        return Complexity(nodes=1, depth=1, function_depth=0)
    if isinstance(node, UnaryNode):
        operand = _complexity(node.operand)
        return Complexity(
            nodes=1 + operand.nodes,
            depth=1 + operand.depth,
            function_depth=operand.function_depth,
        )
    if isinstance(node, BinaryNode):
        left = _complexity(node.left)
        right = _complexity(node.right)
        return Complexity(
            nodes=1 + left.nodes + right.nodes,
            depth=1 + max(left.depth, right.depth),
            function_depth=max(left.function_depth, right.function_depth),
        )
    if isinstance(node, FunctionNode):
        argument = _complexity(node.args[0])
        return Complexity(
            nodes=1 + argument.nodes,
            depth=1 + argument.depth,
            function_depth=1 + argument.function_depth,
        )
    raise UnsafeExpressionError("Unsupported expression node")
