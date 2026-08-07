from __future__ import annotations

from pydantic import BaseModel, Field


class EquivalenceVerificationResponse(BaseModel):
    status: str
    method: str
    equivalent: bool | None
    reason: str
    verifierVersion: str = "sympy-equivalence-v0.1.0"
    correlationId: str = Field(default="unavailable")

    def with_correlation_id(self, correlation_id: str) -> EquivalenceVerificationResponse:
        return self.model_copy(update={"correlationId": correlation_id})
