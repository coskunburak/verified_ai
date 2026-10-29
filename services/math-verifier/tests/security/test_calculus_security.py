import copy
import json
import multiprocessing as mp
import os
import sys
import time
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app.api.schemas.calculus import CalculusRequest
from app.config import get_settings
from app.main import app
from app.policies.process_executor import ProcessBudget, ProcessExecutor, apply_resource_limits

CASES = json.loads((Path(__file__).resolve().parents[4] / "packages/test-fixtures/verification/calculus-v1/cases.json").read_text())
HEADERS = {"X-Internal-Token": "local_math_verifier_token_change_me"}


def request():
    return CalculusRequest.model_validate(CASES[0]["request"])


def stalled_worker(connection, payload, budget):
    time.sleep(60)


def crashing_worker(connection, payload, budget):
    os._exit(3)


def cpu_worker(connection, payload, budget):
    apply_resource_limits(budget)
    while True:
        pass


def memory_worker(connection, payload, budget):
    apply_resource_limits(budget)
    try:
        bytearray(budget.memory_bytes * 2)
    except MemoryError:
        connection.send_bytes(b"RESOURCE_FAILURE")
    finally:
        connection.close()


def wrong_scope_worker(connection, payload, budget):
    from app.verifiers.calculus import execute
    result = execute(CalculusRequest.model_validate_json(payload)).model_dump(mode="json")
    result["scope"]["sourceRevision"] += 1
    connection.send_bytes(json.dumps(result).encode())
    connection.close()


def oversized_worker(connection, payload, budget):
    connection.send_bytes(b"x" * 16385)
    connection.close()


@pytest.mark.parametrize("worker", [stalled_worker, crashing_worker, wrong_scope_worker, oversized_worker])
def test_process_failures_never_become_mathematical_disproof_and_release_slots(worker):
    before = {p.pid for p in mp.active_children()}
    executor = ProcessExecutor(ProcessBudget(elapsed_seconds=1), worker)
    result = executor.execute(request())
    assert result.outcome == "EXECUTION_FAILED"
    assert result.proof is None
    assert result.diagnostic is None
    assert {p.pid for p in mp.active_children()} == before
    assert executor._slots.acquire(blocking=False)
    executor._slots.release()


@pytest.mark.skipif(not sys.platform.startswith("linux"), reason="Owner Linux resource acceptance")
@pytest.mark.parametrize("worker", [cpu_worker, memory_worker])
def test_linux_calculus_resource_failure_is_reaped_and_nonproof(worker):
    before = {p.pid for p in mp.active_children()}
    result = ProcessExecutor(ProcessBudget(elapsed_seconds=3, cpu_seconds=1, memory_bytes=256*1024*1024), worker).execute(request())
    assert result.outcome == "EXECUTION_FAILED"
    assert result.proof is None
    assert {p.pid for p in mp.active_children()} == before


def test_calculus_uses_existing_shared_admission_pool():
    executor = ProcessExecutor(ProcessBudget(concurrency=1))
    executor._slots.acquire()
    try:
        result = executor.execute(request())
        assert result.reasonCode == "ADMISSION_LIMIT"
        assert result.proof is None
    finally:
        executor._slots.release()


def test_calculus_authentication_default_disable_and_correlation(monkeypatch):
    monkeypatch.delenv("MATH_VERIFIER_CALCULUS_ENABLED", raising=False)
    client = TestClient(app)
    assert client.post("/internal/v1/verify/calculus", json=CASES[0]["request"]).status_code == 401
    response = client.post("/internal/v1/verify/calculus", json=CASES[0]["request"], headers=HEADERS)
    assert response.json()["reasonCode"] == "DISABLED"
    assert response.json()["proof"] is None
    response = client.post("/internal/v1/verify/calculus", json=CASES[0]["request"], headers=HEADERS | {"X-Request-Id": "different"})
    assert response.status_code == 422


def test_production_activation_fails_until_canonical_prerequisite_exists(monkeypatch):
    monkeypatch.setenv("APP_ENV", "production")
    monkeypatch.setenv("MATH_VERIFIER_INTERNAL_TOKEN", "owner-test-only-token-not-a-production-credential")
    monkeypatch.setenv("MATH_VERIFIER_CALCULUS_ENABLED", "true")
    with pytest.raises(ValueError, match="source-backed canonical"):
        get_settings()


def test_byte_limits_duplicate_fields_and_sanitized_injection():
    client = TestClient(app)
    assert client.post("/internal/v1/verify/calculus", content=b" "*65537, headers=HEADERS).status_code == 413
    duplicate = client.post("/internal/v1/verify/calculus", content='{"method":"FINITE_LIMIT","method":"DERIVATIVE_BACKCHECK"}', headers=HEADERS)
    assert duplicate.status_code == 422
    p = copy.deepcopy(CASES[0]["request"])
    p["original"]["value"] = "__import__('os').system('id')"
    response = client.post("/internal/v1/verify/calculus", json=p, headers=HEADERS)
    assert response.status_code == 422
    assert "__import__" not in response.text


def test_metric_labels_are_bounded_and_include_diagnostics():
    from app.observability.metrics import execution_metrics, record_execution
    record_execution("FINITE_LIMIT", "INCONCLUSIVE", "NUMERICAL_DIAGNOSTIC", 1)
    assert any('reason="NUMERICAL_DIAGNOSTIC"' in line for line in execution_metrics())
    with pytest.raises(ValueError):
        record_execution("FINITE_LIMIT", "INCONCLUSIVE", "private-expression", 1)


def test_scope_mismatch_has_machine_readable_invalid_response():
    result = ProcessExecutor(worker=wrong_scope_worker).execute(request())
    assert result.reasonCode == "INVALID_RESPONSE"
    assert result.outcome == "EXECUTION_FAILED"
