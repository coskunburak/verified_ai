import json
import multiprocessing as mp
import os
import sys
import time
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app.api.schemas.numeric import NumericRequest
from app.config import get_settings
from app.main import app
from app.observability.metrics import execution_metrics, record_numeric
from app.policies.process_executor import ProcessBudget, ProcessExecutor, apply_resource_limits

PAYLOAD = json.loads((Path(__file__).resolve().parents[4] / "packages/test-fixtures/verification/numeric-v1/cases.json").read_text())[0]["request"]
HEADERS = {"X-Internal-Token": "local_math_verifier_token_change_me"}


def stalled(connection, payload, budget):
    time.sleep(60)


def crashed(connection, payload, budget):
    os._exit(3)


def invalid_scope(connection, payload, budget):
    from app.verifiers.numeric_sampling import execute
    result = execute(NumericRequest.model_validate_json(payload)).model_dump(mode="json")
    result["scopeId"] = "00000000-0000-0000-0000-000000000099"
    connection.send_bytes(json.dumps(result).encode())
    connection.close()


def duplicated_result(connection, payload, budget):
    from app.verifiers.numeric_sampling import execute
    result = execute(NumericRequest.model_validate_json(payload)).model_dump_json()
    connection.send_bytes(result.replace('"attempted":16', '"attempted":16,"attempted":16').encode())
    connection.close()


def oversized(connection, payload, budget):
    connection.send_bytes(b"x" * 16385)
    connection.close()


def cpu(connection, payload, budget):
    apply_resource_limits(budget)
    while True:
        pass


def memory(connection, payload, budget):
    apply_resource_limits(budget)
    try:
        bytearray(budget.memory_bytes * 2)
    except MemoryError:
        connection.send_bytes(b"RESOURCE_FAILURE")
    finally:
        connection.close()


@pytest.mark.parametrize("worker", [stalled, crashed, invalid_scope, oversized, duplicated_result])
def test_failure_reaps_worker_and_releases_shared_slot(worker):
    before = {p.pid for p in mp.active_children()}
    executor = ProcessExecutor(ProcessBudget(elapsed_seconds=1), worker)
    result = executor.execute(NumericRequest.model_validate(PAYLOAD))
    assert result.status == "EXECUTION_FAILED"
    assert result.attempted == 0
    assert {p.pid for p in mp.active_children()} == before
    assert executor._slots.acquire(blocking=False)
    executor._slots.release()


@pytest.mark.skipif(not sys.platform.startswith("linux"), reason="Linux owner acceptance required")
@pytest.mark.parametrize("worker", [cpu, memory])
def test_linux_memory_and_cpu_limits_apply_to_numeric(worker):
    before = {p.pid for p in mp.active_children()}
    result = ProcessExecutor(ProcessBudget(elapsed_seconds=3, cpu_seconds=1, memory_bytes=256*1024*1024), worker).execute(NumericRequest.model_validate(PAYLOAD))
    assert result.status == "EXECUTION_FAILED"
    assert {p.pid for p in mp.active_children()} == before


def test_disabled_authentication_correlation_and_size(monkeypatch):
    monkeypatch.delenv("MATH_VERIFIER_NUMERIC_ENABLED", raising=False)
    client = TestClient(app)
    assert client.post("/internal/v1/verify/numeric", json=PAYLOAD).status_code == 401
    result = client.post("/internal/v1/verify/numeric", json=PAYLOAD, headers=HEADERS)
    assert result.json()["reasonCode"] == "DISABLED"
    assert client.post("/internal/v1/verify/numeric", json=PAYLOAD, headers=HEADERS | {"X-Request-Id": "other"}).status_code == 422
    assert client.post("/internal/v1/verify/numeric", content=b" " * 65537, headers=HEADERS).status_code == 413
    duplicate = json.dumps(PAYLOAD).replace('"method": "NUMERIC_SAMPLING"', '"method": "NUMERIC_SAMPLING", "method": "NUMERIC_SAMPLING"')
    assert client.post("/internal/v1/verify/numeric", content=duplicate, headers=HEADERS).status_code == 422


def test_no_admission_queue_and_invalid_response_is_specific():
    request = NumericRequest.model_validate(PAYLOAD)
    executor = ProcessExecutor(ProcessBudget(concurrency=1))
    executor._slots.acquire()
    try:
        assert executor.execute(request).reasonCode == "ADMISSION_LIMIT"
    finally:
        executor._slots.release()
    assert ProcessExecutor(worker=invalid_scope).execute(request).reasonCode == "INVALID_RESPONSE"
    assert ProcessExecutor(worker=duplicated_result).execute(request).reasonCode == "INVALID_RESPONSE"


def test_production_guard_and_finite_labels(monkeypatch):
    monkeypatch.setenv("APP_ENV", "production")
    monkeypatch.setenv("MATH_VERIFIER_INTERNAL_TOKEN", "owner-test-only-token-not-a-production-credential")
    monkeypatch.setenv("MATH_VERIFIER_NUMERIC_ENABLED", "true")
    with pytest.raises(ValueError, match="separate production approval"):
        get_settings()
    record_numeric("MISMATCH", "SAMPLES_DIFFER", 1)
    assert any('method="NUMERIC_SAMPLING"' in line for line in execution_metrics())
    with pytest.raises(ValueError):
        record_numeric("MISMATCH", "private-expression", 1)
