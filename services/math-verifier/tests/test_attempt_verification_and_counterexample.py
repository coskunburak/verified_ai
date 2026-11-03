"""Synthetic typed mathematics; never handwriting accuracy or production evidence."""
import copy
import json
import multiprocessing as mp
import time
from pathlib import Path

import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError

from app.api.schemas.attempt import AttemptRequest, AttemptResult, unavailable
from app.attempt_verification import execute
from app.counterexample_engine import evaluate, witness
from app.main import app
from app.policies.process_executor import ProcessBudget, ProcessExecutor

ROOT = Path(__file__).resolve().parents[3]
DATA = json.loads((ROOT / "evaluations/golden-datasets/attempts/manual-polynomial-v1.json").read_text())


@pytest.mark.parametrize("reason", ["EXACT_IDENTITY", "SAME_SOLUTION_SET", "NON_EQUIVALENT", "EARLIER_DIVERGENCE", "EARLIER_UNCERTAINTY"])
def test_uncertainty_cannot_carry_proof_or_skipped_reason(reason):
    request = AttemptRequest.model_validate(DATA["cases"][0]["request"])
    payload = unavailable(request).model_dump(mode="json")
    payload["steps"][0]["reasonCode"] = reason
    with pytest.raises(ValidationError):
        AttemptResult.model_validate(payload)


def test_skipped_steps_must_preserve_the_actual_stop_reason():
    payload = copy.deepcopy(DATA["cases"][0]["request"])
    payload["steps"] = [payload["steps"][0]] * 2
    request = AttemptRequest.model_validate(payload)
    response = unavailable(request).model_dump(mode="json")
    response["steps"][1]["reasonCode"] = "EARLIER_DIVERGENCE"
    with pytest.raises(ValidationError):
        AttemptResult.model_validate(response)


@pytest.mark.parametrize("case", DATA["cases"], ids=lambda c: c["id"])
def test_golden(case):
    request = AttemptRequest.model_validate(case["request"])
    result = execute(request)
    assert result.outcome == case["expected"]["outcome"]
    assert result.firstWrongStepIndex == case["expected"]["firstWrongStepIndex"]
    assert result == execute(request)  # deterministic candidate ordering
    stopped = False
    for index, step in enumerate(result.steps):
        if stopped:
            assert step.verdict == "NOT_ASSESSED"
        stopped |= step.verdict in {"FIRST_WRONG_STEP", "UNCERTAIN"}
        if step.counterexample:
            from fractions import Fraction
            values = {k: Fraction(int(v.numerator), int(v.denominator)) for k, v in step.counterexample.variableValues.items()}
            previous = request.original if index == 0 else request.steps[index-1].statement
            assert evaluate(previous, values) != evaluate(request.steps[index].statement, values)
            assert step.counterexample.domainSatisfied
        assert (step.mistakeCode == "ALG_TRANSFORMATION_INVALID") == (step.verdict == "FIRST_WRONG_STEP")


def test_no_witness_is_not_a_proof():
    case = next(c for c in DATA["cases"] if c["id"] == "bounded-witness-unavailable")
    result = execute(AttemptRequest.model_validate(case["request"]))
    assert result.steps[0].counterexampleStatus == "COUNTEREXAMPLE_UNAVAILABLE"
    assert result.steps[0].counterexample is None


@pytest.mark.parametrize("mutation", ["empty", "too_many", "unsafe_symbol", "unknown_field", "malformed", "scalar_index"])
def test_strict_input(mutation):
    payload = copy.deepcopy(DATA["cases"][0]["request"])
    if mutation == "empty": payload["steps"] = []
    if mutation == "too_many": payload["steps"] *= 7
    if mutation == "unsafe_symbol": payload["variables"][0]["symbol"] = "__import__('os')"
    if mutation == "unknown_field": payload["confidence"] = 1
    if mutation == "malformed": payload["steps"] = ["x=2"]
    if mutation == "scalar_index": payload["steps"][0]["statement"] = "x=2"
    with pytest.raises(ValidationError): AttemptRequest.model_validate(payload)


def test_complexity_fails_closed():
    payload = copy.deepcopy(DATA["cases"][0]["request"])
    node = {"kind": "VARIABLE", "symbol": "x"}
    for _ in range(35): node = {"kind": "UNARY", "operator": "NEGATE", "operand": node}
    payload["steps"][0]["statement"] = {"kind": "EXPRESSION", "expression": node}
    result = execute(AttemptRequest.model_validate(payload))
    assert result.outcome == "NEEDS_REVIEW" and result.firstWrongStepIndex is None


def test_undefined_substitutions_are_never_published():
    from app.api.schemas.execution import Statement
    source = Statement.model_validate({"kind":"EXPRESSION","expression":{"kind":"BINARY","operator":"DIVIDE",
        "left":{"kind":"NUMBER","numericType":"INTEGER","value":"1"},
        "right":{"kind":"NUMBER","numericType":"INTEGER","value":"0"}}})
    other = Statement.model_validate({"kind":"EXPRESSION","expression":{"kind":"NUMBER","numericType":"INTEGER","value":"3"}})
    assert witness(source, other, None) is None


def stalled(connection, payload, budget):
    time.sleep(10)


def test_timeout_reaps_child_without_false_accusation():
    before = {p.pid for p in mp.active_children()}
    request = AttemptRequest.model_validate(DATA["cases"][0]["request"])
    result = ProcessExecutor(ProcessBudget(elapsed_seconds=0.1), stalled).execute(request)
    assert result.outcome == "NEEDS_REVIEW"
    assert result.steps[0].reasonCode == "TIMEOUT" and result.firstWrongStepIndex is None
    assert {p.pid for p in mp.active_children()} == before


def test_internal_auth_disable_and_real_process(monkeypatch):
    monkeypatch.setenv("APP_ENV", "test")
    monkeypatch.setenv("MATH_VERIFIER_INTERNAL_TOKEN", "local_math_verifier_token_change_me")
    monkeypatch.setenv("MATH_VERIFIER_ATTEMPT_ENABLED", "false")
    client = TestClient(app)
    payload = DATA["cases"][0]["request"]
    assert client.post("/internal/v1/verify/attempt", json=payload).status_code == 401
    headers = {"X-Internal-Token": "local_math_verifier_token_change_me"}
    disabled = client.post("/internal/v1/verify/attempt", json=payload, headers=headers)
    assert disabled.json()["steps"][0]["reasonCode"] == "DISABLED"
    monkeypatch.setenv("MATH_VERIFIER_ATTEMPT_ENABLED", "true")
    result = client.post("/internal/v1/verify/attempt", json=payload, headers=headers)
    assert result.status_code == 200
    assert AttemptResult.model_validate(result.json()).outcome == "INCOMPLETE_VALID_SO_FAR"
    headers["X-Request-Id"] = "wrong"
    assert client.post("/internal/v1/verify/attempt", json=payload, headers=headers).status_code == 422


def test_response_binding_rejects_cross_request():
    request = AttemptRequest.model_validate(DATA["cases"][0]["request"])
    result = execute(request)
    with pytest.raises(ValueError): result.validate_for(request.model_copy(update={"correlationId": "different"}))


def test_unknown_domain_and_constraints_are_not_discarded():
    payload = copy.deepcopy(DATA["cases"][0]["request"])
    payload["variables"][0]["domain"] = "UNKNOWN"
    assert execute(AttemptRequest.model_validate(payload)).outcome == "NEEDS_REVIEW"
    payload["variables"][0]["domain"] = "REAL"
    payload["restrictions"] = [{"id":"positive","relation":"GREATER_THAN","left":{"kind":"VARIABLE","symbol":"x"},
        "right":{"kind":"NUMBER","numericType":"INTEGER","value":"0"}}]
    assert execute(AttemptRequest.model_validate(payload)).outcome == "NEEDS_REVIEW"
