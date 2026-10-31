import copy
import json
from pathlib import Path

import pytest
from fastapi.testclient import TestClient
from jsonschema import Draft202012Validator, FormatChecker, ValidationError
from pydantic import ValidationError as ModelError

from app.api.schemas.execution import ExecutionRequest
from app.api.schemas.numeric import NumericRequest, NumericResult
from app.main import app
from app.verifiers.numeric_sampling import execute

ROOT = Path(__file__).resolve().parents[4]
CASES = json.loads((ROOT / "packages/test-fixtures/verification/numeric-v1/cases.json").read_text())


def validator(name):
    return Draft202012Validator(json.loads((ROOT / f"packages/schemas/{name}.schema.json").read_text()), format_checker=FormatChecker())


@pytest.mark.parametrize("case", CASES, ids=lambda c: c["id"])
def test_shared_strict_schemas_and_nonproof_results(case):
    inputs, outputs = validator("numeric-diagnostic"), validator("numeric-diagnostic-result")
    inputs.validate(case["request"])
    request = NumericRequest.model_validate(case["request"])
    inputs.validate(request.model_dump(mode="json"))
    result = execute(request)
    outputs.validate(result.model_dump(mode="json"))
    assert NumericResult.model_validate_json(result.model_dump_json()).validate_for(request) == result
    with pytest.raises(ModelError):
        ExecutionRequest.model_validate(case["request"])


@pytest.mark.parametrize("field,value", [("status", "PROVEN"), ("proof", {}), ("method", "SYMBOLIC_EQUIVALENCE"), ("policyVersion", "v2"), ("attempted", 17), ("reasonCode", "EXACT_EQUAL"), ("unknown", True)])
def test_shared_schema_and_runtime_reject_contract_forgery(field, value):
    value_json = execute(NumericRequest.model_validate(CASES[0]["request"])).model_dump(mode="json")
    value_json[field] = value
    with pytest.raises(ValidationError):
        validator("numeric-diagnostic-result").validate(value_json)
    with pytest.raises(ModelError):
        NumericResult.model_validate(value_json)


def test_authenticated_real_worker_is_diagnostic_only(monkeypatch):
    monkeypatch.setenv("APP_ENV", "test")
    monkeypatch.setenv("MATH_VERIFIER_NUMERIC_ENABLED", "true")
    payload = copy.deepcopy(CASES[0]["request"])
    response = TestClient(app).post("/internal/v1/verify/numeric", json=payload,
                                   headers={"X-Internal-Token": "local_math_verifier_token_change_me", "X-Request-Id": payload["correlationId"]})
    assert response.status_code == 200
    assert response.json()["status"] == "AGREEMENT"
    validator("numeric-diagnostic-result").validate(response.json())
    assert "proof" not in response.json()


@pytest.mark.parametrize("field", ["schemaVersion", "method", "policyVersion", "scopeId", "unstable"])
def test_required_response_fields_cannot_be_defaulted(field):
    result = execute(NumericRequest.model_validate(CASES[0]["request"])).model_dump(mode="json")
    del result[field]
    with pytest.raises(ValidationError):
        validator("numeric-diagnostic-result").validate(result)
    with pytest.raises(ModelError):
        NumericResult.model_validate(result)
