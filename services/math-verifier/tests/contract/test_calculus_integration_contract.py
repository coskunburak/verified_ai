import json
from pathlib import Path

import pytest
from fastapi.testclient import TestClient
from jsonschema import Draft202012Validator, FormatChecker

from app.main import app

ROOT = Path(__file__).resolve().parents[4]


def test_typed_parse_and_candidate_contracts_preserve_all_manual_integration_cases():
    source_schema = json.loads((ROOT / "packages/schemas/problem-parse-v2.schema.json").read_text())
    validator = Draft202012Validator(source_schema, format_checker=FormatChecker())
    cases = json.loads((ROOT / "packages/test-fixtures/verification/calculus-integration-v1/cases.json").read_text())
    for case in cases:
        validator.validate(case["parse"])
        assert case["parse"]["calculus"]["original"] != case["candidateExpression"]


@pytest.mark.parametrize("enabled", ["false", "true"])
def test_readiness_is_authenticated_and_reports_actual_flag_and_fixed_versions(monkeypatch, enabled):
    monkeypatch.setenv("APP_ENV", "test")
    monkeypatch.setenv("MATH_VERIFIER_CALCULUS_ENABLED", enabled)
    client = TestClient(app)
    assert client.get("/internal/v1/verify/calculus-readiness").status_code == 401
    response = client.get("/internal/v1/verify/calculus-readiness",
                          headers={"X-Internal-Token": "local_math_verifier_token_change_me"})
    assert response.status_code == 200
    assert response.json() == {"schemaVersion": "calculus-readiness-v1", "enabled": enabled == "true",
                               "algorithm": "bounded-calculus-v1", "requestVersion": "calculus-execution-v1",
                               "responseVersion": "calculus-execution-result-v1"}
