from __future__ import annotations

import json
from pathlib import Path

import pytest
from pydantic import ValidationError

from app.api.schemas.verifier_input import VerifierInputRequest
from app.domain.errors import UnsafeExpressionError
from app.parsing.verifier_input_validator import validate_verifier_input

FIXTURES = Path(__file__).resolve().parents[4] / "packages" / "test-fixtures" / "canonical" / "verifier-input-v1"


def load_fixture(name: str) -> dict:
    return json.loads((FIXTURES / name).read_text(encoding="utf-8"))


def test_valid_typed_ast_input_is_accepted_without_raw_expression_parsing() -> None:
    payload = VerifierInputRequest.model_validate(load_fixture("valid-equation-with-denominator.json"))

    result = validate_verifier_input(payload)

    assert result.status == "ACCEPTED"
    assert result.statementCount == 1
    assert result.restrictionCount == 1


def test_typed_ast_rejects_undeclared_variable() -> None:
    payload = VerifierInputRequest.model_validate(load_fixture("invalid-undeclared-variable.json"))

    with pytest.raises(UnsafeExpressionError):
        validate_verifier_input(payload)


def test_typed_ast_schema_rejects_unknown_node_kind() -> None:
    with pytest.raises(ValidationError):
        VerifierInputRequest.model_validate(load_fixture("invalid-unsafe-node.json"))


def test_typed_ast_rejects_excessive_numeric_exponent() -> None:
    payload = load_fixture("valid-equation-with-denominator.json")
    payload["statements"][0]["left"] = {
        "kind": "BINARY",
        "operator": "POWER",
        "left": {"kind": "VARIABLE", "symbol": "x"},
        "right": {"kind": "NUMBER", "numericType": "INTEGER", "value": "99"},
    }
    request = VerifierInputRequest.model_validate(payload)

    with pytest.raises(UnsafeExpressionError):
        validate_verifier_input(request)


@pytest.mark.parametrize("value", ["0.5", "2.0"])
def test_converter_rejects_fractional_or_decimal_exponent_types(value):
    from app.api.schemas.verifier_input import BinaryNode, NumberNode
    from app.parsing.verifier_input_validator import _convert
    node = BinaryNode(kind="BINARY", operator="POWER",
                      left=NumberNode(kind="NUMBER", numericType="INTEGER", value="2"),
                      right=NumberNode(kind="NUMBER", numericType="DECIMAL", value=value))
    with pytest.raises(UnsafeExpressionError):
        _convert(node, {})


def test_power_converter_uses_converted_mathematical_exponent():
    import sympy as sp

    from app.api.schemas.verifier_input import BinaryNode, NumberNode
    from app.parsing.verifier_input_validator import _convert
    node = BinaryNode(kind="BINARY", operator="POWER",
                      left=NumberNode(kind="NUMBER", numericType="INTEGER", value="2"),
                      right=NumberNode(kind="NUMBER", numericType="INTEGER", value="10"))
    converted = _convert(node, {})
    assert converted.exp == sp.Integer(10)
    assert converted.doit() == 1024


@pytest.mark.parametrize("operator,right", [("DIVIDE", "0"), ("POWER", "0"), ("POWER", "-1")])
def test_converter_rejects_explicit_undefined_values(operator, right):
    from app.api.schemas.verifier_input import BinaryNode, NumberNode
    from app.parsing.verifier_input_validator import _convert
    node = BinaryNode(kind="BINARY", operator=operator,
                      left=NumberNode(kind="NUMBER", numericType="INTEGER", value="0"),
                      right=NumberNode(kind="NUMBER", numericType="INTEGER", value=right))
    with pytest.raises(UnsafeExpressionError):
        _convert(node, {})
