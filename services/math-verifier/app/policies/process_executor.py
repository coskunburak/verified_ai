"""One bounded spawn per admitted operation; no executor backlog or signal/thread dependency."""
from __future__ import annotations

import json
import multiprocessing as mp
import os
import sys
import threading
import time
from dataclasses import dataclass

from app.api.schemas.calculus import CalculusRequest, CalculusResult
from app.api.schemas.execution import ExecutionRequest, ExecutionResult

MAX_RESPONSE_BYTES = 16384


@dataclass(frozen=True)
class ProcessBudget:
    elapsed_seconds: float = 3.0
    cpu_seconds: int = 2
    memory_bytes: int = 512 * 1024 * 1024
    concurrency: int = 2

    def __post_init__(self):
        if not (0.01 <= self.elapsed_seconds <= 10 and 1 <= self.cpu_seconds <= 5
                and 128*1024*1024 <= self.memory_bytes <= 1024*1024*1024 and 1 <= self.concurrency <= 4):
            raise ValueError("invalid verifier process budget")


DEFAULT_PROCESS_BUDGET = ProcessBudget()


def apply_resource_limits(budget):
    # Linux RLIMIT_AS is enforceable; production execution rejects other platforms.
    if os.name == "posix":
        import resource
        resource.setrlimit(resource.RLIMIT_CPU, (budget.cpu_seconds, budget.cpu_seconds))
        resource.setrlimit(resource.RLIMIT_CORE, (0, 0))
        if sys.platform.startswith("linux"):
            resource.setrlimit(resource.RLIMIT_AS, (budget.memory_bytes, budget.memory_bytes))


def _worker(connection, payload, budget):
    try:
        apply_resource_limits(budget)
        if json.loads(payload).get("schemaVersion") == "calculus-execution-v1":
            from app.verifiers.calculus import execute
            response = execute(CalculusRequest.model_validate_json(payload))
        else:
            from app.verifiers.canonical_execution import execute
            response = execute(ExecutionRequest.model_validate_json(payload))
        encoded = response.model_dump_json().encode("utf-8")
        if len(encoded) > MAX_RESPONSE_BYTES:
            raise MemoryError()
        connection.send_bytes(encoded)
    except MemoryError:
        connection.send_bytes(b"RESOURCE_FAILURE")
    except BaseException:  # noqa: BLE001 - Isolated child must sanitize process-exit exceptions too.
        # No raw input, exception message, Python repr or traceback crosses the boundary.
        try:
            connection.send_bytes(b"WORKER_FAILURE")
        except (OSError, EOFError):
            pass
    finally:
        connection.close()


class ProcessExecutor:
    def __init__(self, budget=DEFAULT_PROCESS_BUDGET, worker=_worker):
        self.budget = budget
        self._slots = threading.BoundedSemaphore(budget.concurrency)
        self._context = mp.get_context("spawn")
        self._worker = worker

    @staticmethod
    def failure(request, reason):
        if isinstance(request, CalculusRequest):
            return CalculusResult(schemaVersion="calculus-execution-result-v1", methodVersion="v1",
                                  verifierVersion="bounded-calculus-v1",
                                  executionId=request.executionId, correlationId=request.correlationId,
                                  scope=request.scope, method=request.method, outcome="EXECUTION_FAILED",
                                  reasonCode=reason, restrictionIds=[r.id for r in request.restrictions],
                                  proof=None, diagnostic=None)
        return ExecutionResult(executionId=request.executionId, correlationId=request.correlationId,
                               method=request.method, outcome="EXECUTION_FAILED", reasonCode=reason,
                               restrictionIds=[r.id for r in request.restrictions])

    def execute(self, request: ExecutionRequest | CalculusRequest) -> ExecutionResult | CalculusResult:
        if not self._slots.acquire(blocking=False):
            return self.failure(request, "ADMISSION_LIMIT")
        receive = send = process = None
        deadline = time.monotonic() + self.budget.elapsed_seconds
        started = False
        try:
            receive, send = self._context.Pipe(duplex=False)
            process = self._context.Process(target=self._worker,
                                            args=(send, request.model_dump_json(), self.budget), daemon=True)
            process.start()
            started = True
            send.close()
            if not receive.poll(max(0, deadline - time.monotonic())):
                return self.failure(request, "TIMEOUT")
            try:
                data = receive.recv_bytes(MAX_RESPONSE_BYTES)
            except (EOFError, OSError):
                return self.failure(request, "WORKER_FAILURE")
            if data in {b"RESOURCE_FAILURE", b"WORKER_FAILURE"}:
                return self.failure(request, data.decode("ascii"))
            if isinstance(request, CalculusRequest):
                try:
                    return CalculusResult.model_validate_json(data).validate_for(request)
                except ValueError:
                    return self.failure(request, "INVALID_RESPONSE")
            response = ExecutionResult.model_validate(json.loads(data))
            if (response.executionId != request.executionId or response.method != request.method
                    or response.correlationId != request.correlationId):
                return self.failure(request, "WORKER_FAILURE")
            return response
        except (OSError, ValueError, RuntimeError):
            return self.failure(request, "WORKER_FAILURE")
        finally:
            try:
                if send is not None:
                    send.close()
                if receive is not None:
                    receive.close()
                if started:
                    if process.is_alive():
                        process.terminate()
                    process.join(timeout=0.2)
                    if process.is_alive():
                        process.kill()
                        process.join(timeout=0.2)
                    process.close()
            finally:
                self._slots.release()


executor = ProcessExecutor()
