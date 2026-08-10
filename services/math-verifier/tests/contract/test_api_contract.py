import json
from pathlib import Path

from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)
FIXTURES = Path(__file__).resolve().parents[4] / "packages" / "test-fixtures" / "canonical" / "verifier-input-v1"


def test_health_contract() -> None:
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json()["status"] == "UP"


def test_metrics_endpoint_exposes_metric_names() -> None:
    response = client.get("/metrics")

    assert response.status_code == 200
    assert "math_verifier_requests_total" in response.text


def test_equivalence_requires_internal_auth() -> None:
    response = client.post("/internal/v1/verify/equivalence", json={"left": "x", "right": "x", "variables": ["x"]})

    assert response.status_code == 401
    assert response.json()["code"] == "INTERNAL_AUTH_REQUIRED"


def test_equivalence_contract_success() -> None:
    response = client.post(
        "/internal/v1/verify/equivalence",
        headers={"X-Internal-Token": "local_math_verifier_token_change_me", "X-Request-Id": "contract-test"},
        json={"left": "x + x", "right": "2*x", "variables": ["x"]},
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload["status"] == "VERIFIED"
    assert payload["correlationId"] == "contract-test"
    assert response.headers["X-Request-Id"] == "contract-test"


def test_equivalence_contract_invalid_expression() -> None:
    response = client.post(
        "/internal/v1/verify/equivalence",
        headers={"X-Internal-Token": "local_math_verifier_token_change_me"},
        json={"left": "unknown + 1", "right": "1", "variables": []},
    )

    assert response.status_code == 422
    assert response.json()["code"] == "UNSAFE_EXPRESSION"


def test_verifier_input_requires_internal_auth() -> None:
    payload = json.loads((FIXTURES / "valid-equation-with-denominator.json").read_text(encoding="utf-8"))

    response = client.post("/internal/v1/verify/validate-input", json=payload)

    assert response.status_code == 401
    assert response.json()["code"] == "INTERNAL_AUTH_REQUIRED"


def test_verifier_input_contract_success() -> None:
    payload = json.loads((FIXTURES / "valid-equation-with-denominator.json").read_text(encoding="utf-8"))

    response = client.post(
        "/internal/v1/verify/validate-input",
        headers={"X-Internal-Token": "local_math_verifier_token_change_me", "X-Request-Id": "ast-contract"},
        json=payload,
    )

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ACCEPTED"
    assert body["schemaVersion"] == "verifier-input-v1"
    assert body["statementCount"] == 1
    assert body["restrictionCount"] == 1
    assert body["correlationId"] == "ast-contract"


def test_verifier_input_contract_rejects_unsafe_node_schema() -> None:
    payload = json.loads((FIXTURES / "invalid-unsafe-node.json").read_text(encoding="utf-8"))

    response = client.post(
        "/internal/v1/verify/validate-input",
        headers={"X-Internal-Token": "local_math_verifier_token_change_me"},
        json=payload,
    )

    assert response.status_code == 422
    assert response.json()["code"] == "VERIFIER_INPUT_SCHEMA_INVALID"
