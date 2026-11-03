from __future__ import annotations

from fastapi import APIRouter, Depends, Request

from app.api.dependencies import require_internal_auth
from app.api.schemas.calculus import CalculusRequest, CalculusResult
from app.api.schemas.execution import ExecutionRequest, ExecutionResult
from app.api.schemas.numeric import NumericRequest, NumericResult
from app.api.schemas.verification_request import EquivalenceVerificationRequest
from app.api.schemas.verification_response import EquivalenceVerificationResponse
from app.api.schemas.verifier_input import VerifierInputRequest, VerifierInputValidationResponse
from app.parsing.verifier_input_validator import validate_verifier_input
from app.verifiers.equivalence import verify_equivalence

router = APIRouter(prefix="/internal/v1/verify", tags=["verify"])


@router.post(
    "/equivalence",
    response_model=EquivalenceVerificationResponse,
    dependencies=[Depends(require_internal_auth)],
)
async def equivalence(
    payload: EquivalenceVerificationRequest,
    request: Request,
) -> EquivalenceVerificationResponse:
    from app.config import get_settings
    from app.domain.errors import VerifierUnavailableError
    if get_settings().environment not in {"local", "test"}:
        raise VerifierUnavailableError()
    result = verify_equivalence(payload)
    return result.with_correlation_id(getattr(request.state, "correlation_id", "unavailable"))


@router.post(
    "/validate-input",
    response_model=VerifierInputValidationResponse,
    dependencies=[Depends(require_internal_auth)],
)
async def validate_input(
    payload: VerifierInputRequest,
    request: Request,
) -> VerifierInputValidationResponse:
    result = validate_verifier_input(payload)
    return result.with_correlation_id(getattr(request.state, "correlation_id", "unavailable"))


@router.post(
    "/execute",
    response_model=ExecutionResult,
    dependencies=[Depends(require_internal_auth)],
)
def execute_canonical(payload: ExecutionRequest, request: Request) -> ExecutionResult:
    import time

    from app.config import get_settings
    from app.domain.errors import VerificationError
    from app.observability.metrics import record_execution
    from app.policies.process_executor import executor

    supplied = request.headers.get("X-Request-Id")
    if supplied is not None and supplied != payload.correlationId:
        raise VerificationError("VERIFIER_CORRELATION_MISMATCH", "Execution correlation does not match the request", 422)
    request.state.correlation_id = payload.correlationId
    start = time.monotonic()
    result = executor.execute(payload) if get_settings().execution_enabled else executor.failure(payload, "DISABLED")
    record_execution(result.method, result.outcome, result.reasonCode, (time.monotonic() - start) * 1000)
    return result


@router.post("/calculus", response_model=CalculusResult, dependencies=[Depends(require_internal_auth)])
def execute_calculus(payload: CalculusRequest, request: Request) -> CalculusResult:
    import time

    from app.config import get_settings
    from app.domain.errors import VerificationError
    from app.observability.metrics import record_execution
    from app.policies.process_executor import executor

    supplied = request.headers.get("X-Request-Id")
    if supplied is not None and supplied != payload.correlationId:
        raise VerificationError("VERIFIER_CORRELATION_MISMATCH", "Execution correlation does not match the request", 422)
    request.state.correlation_id = payload.correlationId
    start = time.monotonic()
    result = executor.execute(payload) if get_settings().calculus_enabled else executor.failure(payload, "DISABLED")
    record_execution(result.method, result.outcome, result.reasonCode, (time.monotonic() - start) * 1000)
    return result


@router.get("/calculus-readiness", dependencies=[Depends(require_internal_auth)])
def calculus_readiness() -> dict[str, str | bool]:
    from app.config import get_settings

    return {"schemaVersion": "calculus-readiness-v1", "enabled": get_settings().calculus_enabled,
            "algorithm": "bounded-calculus-v1", "requestVersion": "calculus-execution-v1",
            "responseVersion": "calculus-execution-result-v1"}


@router.post("/numeric", response_model=NumericResult, dependencies=[Depends(require_internal_auth)])
def execute_numeric(payload: NumericRequest, request: Request) -> NumericResult:
    import time

    from app.config import get_settings
    from app.domain.errors import VerificationError
    from app.observability.metrics import record_numeric
    from app.policies.process_executor import executor

    supplied = request.headers.get("X-Request-Id")
    if supplied is not None and supplied != payload.correlationId:
        raise VerificationError("VERIFIER_CORRELATION_MISMATCH", "Execution correlation does not match the request", 422)
    request.state.correlation_id = payload.correlationId
    started = time.monotonic()
    result = executor.execute(payload) if get_settings().numeric_enabled else executor.failure(payload, "DISABLED")
    record_numeric(result.status, result.reasonCode, (time.monotonic() - started) * 1000)
    return result


from app.api.schemas.attempt import AttemptRequest, AttemptResult


@router.post("/attempt", response_model=AttemptResult, dependencies=[Depends(require_internal_auth)])
def execute_attempt(payload: AttemptRequest, request: Request) -> AttemptResult:
    import time
    from app.config import get_settings
    from app.domain.errors import VerificationError
    from app.observability.metrics import record_attempt
    from app.policies.process_executor import executor

    supplied = request.headers.get("X-Request-Id")
    if supplied is not None and supplied != payload.correlationId:
        raise VerificationError("VERIFIER_CORRELATION_MISMATCH", "Execution correlation mismatch", 422)
    request.state.correlation_id = payload.correlationId
    start = time.monotonic()
    result = executor.execute(payload) if get_settings().attempt_enabled else executor.failure(payload, "DISABLED")
    record_attempt(result, (time.monotonic()-start)*1000)
    return result


from app.api.schemas.attempt_v2 import Request as AttemptV2Request, Result as AttemptV2Result


@router.post("/attempt-v2", response_model=AttemptV2Result, dependencies=[Depends(require_internal_auth)])
def execute_attempt_v2(payload: AttemptV2Request, request: Request) -> AttemptV2Result:
    from app.config import get_settings
    from app.domain.errors import VerificationError
    from app.policies.process_executor import executor
    supplied = request.headers.get("X-Request-Id")
    if supplied != payload.correlationId:
        raise VerificationError("VERIFIER_CORRELATION_MISMATCH", "Execution correlation mismatch", 422)
    request.state.correlation_id = payload.correlationId
    import time
    from app.observability.metrics import record_attempt
    started=time.monotonic()
    result=executor.execute(payload) if get_settings().attempt_enabled else executor.failure(payload, "DISABLED")
    record_attempt(result,(time.monotonic()-started)*1000)
    return result


from app.api.schemas.attempt_learning import Request as LearningRequest, Result as LearningResult

@router.post("/attempt-learning", response_model=LearningResult, dependencies=[Depends(require_internal_auth)])
def execute_attempt_learning(payload: LearningRequest, request: Request) -> LearningResult:
    from app.config import get_settings
    from app.domain.errors import VerificationError
    from app.policies.process_executor import executor
    if request.headers.get("X-Request-Id") != payload.correlationId:
        raise VerificationError("VERIFIER_CORRELATION_MISMATCH", "Execution correlation mismatch", 422)
    request.state.correlation_id = payload.correlationId
    import time
    from app.observability.metrics import record_attempt_learning
    started=time.monotonic()
    result=executor.execute(payload) if get_settings().attempt_enabled else executor.failure(payload,"DISABLED")
    record_attempt_learning(result,(time.monotonic()-started)*1000)
    return result
