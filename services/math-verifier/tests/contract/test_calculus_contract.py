import copy
import json
from pathlib import Path

import pytest
from fastapi.testclient import TestClient
from jsonschema import Draft202012Validator, FormatChecker, ValidationError

from app.api.schemas.calculus import CalculusRequest
from app.main import app
from app.verifiers.calculus import execute

ROOT = Path(__file__).resolve().parents[4]
CASES = json.loads((ROOT / "packages/test-fixtures/verification/calculus-v1/cases.json").read_text())


def validator(name):
    return Draft202012Validator(json.loads((ROOT / f"packages/schemas/{name}.schema.json").read_text()), format_checker=FormatChecker())


def test_shared_schema_matches_typed_requests_and_method_specific_results():
    inputs, outputs = validator("calculus-execution"), validator("calculus-execution-result")
    for case in CASES:
        inputs.validate(case["request"])
        request = CalculusRequest.model_validate(case["request"])
        inputs.validate(request.model_dump(mode="json"))
        outputs.validate(execute(request).model_dump(mode="json"))


def test_real_authenticated_http_and_supervised_worker(monkeypatch):
    monkeypatch.setenv("APP_ENV", "test")
    monkeypatch.setenv("MATH_VERIFIER_CALCULUS_ENABLED", "true")
    p = next(c["request"] for c in CASES if c["id"] == "derivative-chain-sin")
    response = TestClient(app).post("/internal/v1/verify/calculus", json=p,
                                   headers={"X-Internal-Token": "local_math_verifier_token_change_me", "X-Request-Id": p["correlationId"]})
    assert response.status_code == 200
    assert response.json()["outcome"] == "PROVEN"
    assert response.json()["scope"] == p["scope"]
    validator("calculus-execution-result").validate(response.json())


def test_old_execution_schema_and_route_do_not_accept_calculus(monkeypatch):
    from pydantic import ValidationError as PydanticError

    from app.api.schemas.execution import ExecutionRequest

    with pytest.raises(PydanticError):
        ExecutionRequest.model_validate(CASES[0]["request"])
    response = TestClient(app).post("/internal/v1/verify/execute", json=CASES[0]["request"],
                                   headers={"X-Internal-Token": "local_math_verifier_token_change_me"})
    assert response.status_code == 422


def test_shared_result_schema_rejects_proof_method_mismatch_and_fake_numerical_proof():
    p = execute(CalculusRequest.model_validate(CASES[0]["request"])).model_dump(mode="json")
    bad = copy.deepcopy(p)
    bad["method"] = "FINITE_LIMIT"
    with pytest.raises(ValidationError):
        validator("calculus-execution-result").validate(bad)
    p["reasonCode"] = "NUMERICAL_DIAGNOSTIC"
    p["diagnostic"] = {"policy": "calculus-binary64-v1", "attempted": 16, "evaluated": 16, "agreeing": 16}
    with pytest.raises(ValidationError):
        validator("calculus-execution-result").validate(p)
