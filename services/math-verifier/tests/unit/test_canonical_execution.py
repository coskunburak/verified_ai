import copy
import json
from pathlib import Path

import pytest
from pydantic import ValidationError

from app.api.schemas.execution import ExecutionRequest
from app.verifiers.canonical_execution import execute

FIXTURES = Path(__file__).resolve().parents[4] / "packages/test-fixtures/verification/execution-v1/cases.json"
CASES = json.loads(FIXTURES.read_text())


@pytest.mark.parametrize("case", CASES, ids=lambda c: c["id"])
def test_reviewable_exact_mathematical_cases(case):
    request = ExecutionRequest.model_validate(case["request"])
    result = execute(request)
    assert result.outcome == case["expectedOutcome"]
    assert result.reasonCode == case["expectedReason"]
    assert (result.proof is not None) == (result.outcome in {"PROVEN", "DISPROVEN"})
    assert execute(request).model_dump_json() == result.model_dump_json()
    assert result.method == request.method


def test_decimal_proof_is_exact_and_has_no_binary_float():
    result = execute(ExecutionRequest.model_validate(CASES[2]["request"]))
    assert result.proof.leftValue.numerator == "3"
    assert result.proof.leftValue.denominator == "10"
    assert result.proof.leftValue == result.proof.rightValue


@pytest.mark.parametrize("mutation", [
    lambda p: p.update(schemaVersion="verification-execution-v99"),
    lambda p: p.update(method="NUMERIC_SAMPLING"),
    lambda p: p.update(displayLatex="__import__('os')"),
    lambda p: p["original"]["expression"].update(kind="PYTHON"),
])
def test_strict_wire_contract(mutation):
    payload = copy.deepcopy(CASES[0]["request"])
    mutation(payload)
    with pytest.raises(ValidationError):
        ExecutionRequest.model_validate(payload)


def test_unrelated_or_undeclared_symbols_cannot_be_executed():
    payload = copy.deepcopy(CASES[0]["request"])
    payload["original"]["expression"] = {"kind": "VARIABLE", "symbol": "x"}
    assert execute(ExecutionRequest.model_validate(payload)).outcome == "UNSUPPORTED"


def test_depth_and_total_operation_budgets_fail_closed():
    payload = copy.deepcopy(CASES[0]["request"])
    node = {"kind": "NUMBER", "numericType": "INTEGER", "value": "1"}
    for _ in range(33):
        node = {"kind": "UNARY", "operator": "NEGATE", "operand": node}
    payload["original"]["expression"] = node
    result = execute(ExecutionRequest.model_validate(payload))
    assert (result.outcome, result.reasonCode) == ("UNSUPPORTED", "COMPLEXITY_LIMIT")


def test_large_intermediate_values_are_rejected_before_expansion():
    payload = copy.deepcopy(CASES[0]["request"])
    node = {"kind": "NUMBER", "numericType": "INTEGER", "value": "9" * 64}
    for _ in range(3):
        node = {"kind": "BINARY", "operator": "POWER", "left": node,
                "right": {"kind": "NUMBER", "numericType": "INTEGER", "value": "12"}}
    payload["original"]["expression"] = node
    assert execute(ExecutionRequest.model_validate(payload)).reasonCode == "COMPLEXITY_LIMIT"


@pytest.mark.parametrize("function,value,outcome", [
    ("LOG", "2", "PROVEN"), ("LOG", "-1", "DISPROVEN"),
    ("SQRT", "2", "PROVEN"), ("TAN", "0", "PROVEN"), ("TAN", "1", "UNSUPPORTED"),
])
def test_domain_check_does_not_require_irrational_or_transcendental_evaluation(function, value, outcome):
    payload = copy.deepcopy(next(c["request"] for c in CASES if c["id"] == "valid-linear-root"))
    payload["method"] = "DOMAIN_CONSTRAINT_CHECK"
    payload["original"]["left"] = {"kind": "FUNCTION", "function": function,
                                      "args": [{"kind": "VARIABLE", "symbol": "x"}]}
    payload["candidate"]["right"]["value"] = value
    assert execute(ExecutionRequest.model_validate(payload)).outcome == outcome
