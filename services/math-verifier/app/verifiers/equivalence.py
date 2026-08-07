from __future__ import annotations

import sympy as sp

from app.api.schemas.verification_request import EquivalenceVerificationRequest
from app.api.schemas.verification_response import EquivalenceVerificationResponse
from app.parsing.safe_parser import parse_safe_expression
from app.policies.execution_budget import wall_clock_timeout


def verify_equivalence(payload: EquivalenceVerificationRequest) -> EquivalenceVerificationResponse:
    with wall_clock_timeout(1):
        left = parse_safe_expression(payload.left, payload.variables)
        right = parse_safe_expression(payload.right, payload.variables)
        simplified = sp.simplify(left - right)
        equivalent = simplified == 0

    return EquivalenceVerificationResponse(
        status="VERIFIED" if equivalent else "CONTRADICTED",
        method=payload.method,
        equivalent=bool(equivalent),
        reason="symbolic_difference_zero" if equivalent else "symbolic_difference_nonzero",
    )

