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
