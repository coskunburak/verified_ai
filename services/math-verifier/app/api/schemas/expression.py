from __future__ import annotations

from pydantic import BaseModel, Field


class ExpressionPayload(BaseModel):
    expression: str = Field(min_length=1, max_length=256)

