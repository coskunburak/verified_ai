import copy
import json
import multiprocessing as mp
import os
import sys
import time
from pathlib import Path

import httpx
import pytest
from fastapi.testclient import TestClient

from app.api.schemas.execution import ExecutionRequest
from app.config import get_settings
from app.main import app
from app.policies.process_executor import ProcessBudget, ProcessExecutor

CASES = json.loads((Path(__file__).resolve().parents[4] / "packages/test-fixtures/verification/execution-v1/cases.json").read_text())


def stalled_worker(connection, payload, budget):
    time.sleep(60)


def crashing_worker(connection, payload, budget):
    os._exit(3)


def cpu_worker(connection, payload, budget):
    import resource
    resource.setrlimit(resource.RLIMIT_CPU, (1, 1))
    while True:
        pass


def request():
    return ExecutionRequest.model_validate(CASES[0]["request"])


@pytest.mark.asyncio
async def test_timeout_enforced_in_actual_asgi_threadpool_and_child_reaped(monkeypatch):
    from app.policies import process_executor
    monkeypatch.setenv("MATH_VERIFIER_EXECUTION_ENABLED", "true")
    monkeypatch.setattr(process_executor, "executor", ProcessExecutor(ProcessBudget(elapsed_seconds=0.2), stalled_worker))
    before = {p.pid for p in mp.active_children()}
    async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://verifier") as client:
        response = await client.post("/internal/v1/verify/execute", json=request().model_dump(mode="json"),
                                     headers={"X-Internal-Token": "local_math_verifier_token_change_me"})
    assert response.status_code == 200
    assert response.json()["reasonCode"] == "TIMEOUT"
    assert response.json()["proof"] is None
    assert {p.pid for p in mp.active_children()} == before


@pytest.mark.parametrize("worker", [crashing_worker, stalled_worker])
def test_worker_failure_never_becomes_mathematical_disproof(worker):
    executor = ProcessExecutor(ProcessBudget(elapsed_seconds=0.2), worker)
    result = executor.execute(request())
    assert result.outcome == "EXECUTION_FAILED"
    assert result.proof is None
    # Admission slots are released even on failure.
    assert executor._slots.acquire(blocking=False)
    executor._slots.release()


@pytest.mark.skipif(os.name != "posix", reason="POSIX CPU limit")
def test_cpu_exhaustion_terminates_and_reaps_worker():
    executor = ProcessExecutor(ProcessBudget(elapsed_seconds=3), cpu_worker)
    before = {p.pid for p in mp.active_children()}
    assert executor.execute(request()).outcome == "EXECUTION_FAILED"
    assert {p.pid for p in mp.active_children()} == before


def test_admission_has_no_unbounded_queue():
    executor = ProcessExecutor(ProcessBudget(concurrency=1))
    executor._slots.acquire()
    try:
        result = executor.execute(request())
        assert result.reasonCode == "ADMISSION_LIMIT"
        assert result.proof is None
    finally:
        executor._slots.release()


@pytest.mark.parametrize("token", ["", "short", "local_math_verifier_token_change_me"])
def test_production_rejects_development_or_missing_secrets(monkeypatch, token):
    monkeypatch.setenv("APP_ENV", "production")
    monkeypatch.setenv("MATH_VERIFIER_INTERNAL_TOKEN", token)
    with pytest.raises(ValueError):
        get_settings()


def test_execution_requires_internal_auth_and_is_disabled_by_default(monkeypatch):
    monkeypatch.delenv("MATH_VERIFIER_EXECUTION_ENABLED", raising=False)
    client = TestClient(app)
    assert client.post("/internal/v1/verify/execute", json=CASES[0]["request"]).status_code == 401
    result = client.post("/internal/v1/verify/execute", json=CASES[0]["request"],
                         headers={"X-Internal-Token": "local_math_verifier_token_change_me"})
    assert result.json()["reasonCode"] == "DISABLED"


def test_request_limit_precedes_json_parsing():
    response = TestClient(app).post("/internal/v1/verify/execute", content=b" " * 65537)
    assert response.status_code == 413


@pytest.mark.parametrize("field,value", [("kind", "PYTHON"), ("value", "__import__('os').system('id')")])
def test_injection_never_reaches_mathematical_worker(field, value):
    payload = copy.deepcopy(CASES[0]["request"])
    payload["original"]["expression"] = {"kind": "NUMBER", "numericType": "INTEGER", "value": "2"}
    payload["original"]["expression"][field] = value
    response = TestClient(app).post("/internal/v1/verify/execute", json=payload,
                                   headers={"X-Internal-Token": "local_math_verifier_token_change_me"})
    assert response.status_code == 422
    assert "__import__" not in response.text


def memory_worker(connection, payload, budget):
    from app.policies.process_executor import apply_resource_limits
    apply_resource_limits(budget)
    try:
        bytearray(budget.memory_bytes * 2)
    except MemoryError:
        connection.send_bytes(b"RESOURCE_FAILURE")
    finally:
        connection.close()


@pytest.mark.skipif(not sys.platform.startswith("linux"), reason="Linux RLIMIT_AS; required in verifier CI")
def test_actual_memory_limit_exhaustion_is_a_nonproof_resource_failure():
    result = ProcessExecutor(ProcessBudget(memory_bytes=256*1024*1024), memory_worker).execute(request())
    assert (result.outcome, result.reasonCode) == ("EXECUTION_FAILED", "RESOURCE_FAILURE")
    assert result.proof is None


def test_invalid_metric_labels_and_correlation_mismatch_are_rejected():
    from app.observability.metrics import record_execution
    with pytest.raises(ValueError):
        record_execution("student-id", "PROVEN", "EXACT_EQUAL", 0)
    response = TestClient(app).post("/internal/v1/verify/execute", json=CASES[0]["request"],
                                   headers={"X-Internal-Token": "local_math_verifier_token_change_me", "X-Request-Id": "different"})
    assert response.status_code == 422
    assert response.json()["code"] == "VERIFIER_CORRELATION_MISMATCH"


def test_duplicate_json_fields_are_rejected_before_pydantic():
    response = TestClient(app).post("/internal/v1/verify/execute", content='{"method":"ARITHMETIC_EVALUATION","method":"SYMBOLIC_EQUIVALENCE"}',
                                   headers={"Content-Type": "application/json", "X-Internal-Token": "local_math_verifier_token_change_me"})
    assert response.status_code == 422
    assert response.json()["code"] == "VERIFIER_INPUT_SCHEMA_INVALID"
