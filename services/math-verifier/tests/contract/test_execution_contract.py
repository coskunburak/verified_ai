import json
from pathlib import Path

from fastapi.testclient import TestClient
from jsonschema import Draft202012Validator

from app.api.schemas.execution import ExecutionRequest
from app.main import app
from app.policies.process_executor import ProcessExecutor

ROOT = Path(__file__).resolve().parents[4]
CASES = json.loads((ROOT / "packages/test-fixtures/verification/execution-v1/cases.json").read_text())


def test_shared_fixture_crosses_real_process_boundary_exactly():
    payload = ExecutionRequest.model_validate(CASES[2]["request"])
    result = ProcessExecutor().execute(payload)
    assert result.outcome == "PROVEN"
    assert result.proof.leftValue.numerator == "3"
    assert result.proof.leftValue.denominator == "10"
    assert result.executionId == payload.executionId
    assert result.correlationId == payload.correlationId


def test_authenticated_execution_endpoint_returns_versioned_method_evidence(monkeypatch):
    monkeypatch.setenv("MATH_VERIFIER_EXECUTION_ENABLED", "true")
    response = TestClient(app).post("/internal/v1/verify/execute", json=CASES[0]["request"],
                                   headers={"X-Internal-Token": "local_math_verifier_token_change_me"})
    assert response.status_code == 200
    body = response.json()
    assert body["schemaVersion"] == "verification-execution-result-v1"
    assert body["outcome"] == "PROVEN"
    assert body["method"] == CASES[0]["request"]["method"]
    assert body["correlationId"] == "shared-fixture"
    assert "VERIFIED" not in response.text


def test_published_schemas_match_shared_fixtures_and_real_results():
    from app.verifiers.canonical_execution import execute
    request_schema = json.loads((ROOT / "packages/schemas/verification-execution.schema.json").read_text())
    result_schema = json.loads((ROOT / "packages/schemas/verification-execution-result.schema.json").read_text())
    inputs, outputs = Draft202012Validator(request_schema), Draft202012Validator(result_schema)
    for case in CASES:
        inputs.validate(case["request"])
        request = ExecutionRequest.model_validate(case["request"])
        inputs.validate(request.model_dump(mode="json"))
        outputs.validate(execute(request).model_dump(mode="json"))
