from __future__ import annotations

from fastapi import APIRouter, Depends, Request

from app.api.dependencies import require_internal_auth
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
