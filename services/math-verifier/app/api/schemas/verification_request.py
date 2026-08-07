from __future__ import annotations

from pydantic import BaseModel, Field


class EquivalenceVerificationRequest(BaseModel):
    left: str = Field(min_length=1, max_length=256)
    right: str = Field(min_length=1, max_length=256)
    variables: list[str] = Field(default_factory=list, max_length=12)
    method: str = Field(default="symbolic_equivalence")

