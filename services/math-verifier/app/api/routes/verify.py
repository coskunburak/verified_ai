from __future__ import annotations

from fastapi import APIRouter, Depends, Request

from app.api.dependencies import require_internal_auth
from app.api.schemas.calculus import CalculusRequest, CalculusResult
from app.api.schemas.execution import ExecutionRequest, ExecutionResult
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
