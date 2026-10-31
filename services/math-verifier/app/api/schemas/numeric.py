"""Additive diagnostics only: neither proof descriptors nor proof outcomes are representable."""
from __future__ import annotations

import json
import re
from typing import Literal
from uuid import UUID

from pydantic import Field, model_validator

from app.api.schemas.execution import ExecutionRequest, FrozenModel

POLICY = "equivalence-binary64-v1"
Status = Literal["AGREEMENT", "MISMATCH", "INCONCLUSIVE", "UNSUPPORTED", "BLOCKED", "EXECUTION_FAILED"]
Reason = Literal[
    "SAMPLES_AGREE", "SAMPLES_DIFFER", "INSUFFICIENT_SAMPLES", "UNSTABLE_SAMPLES",
    "UNSUPPORTED_SUBSET", "COMPLEXITY_LIMIT", "UNKNOWN_DOMAIN", "INTEGER_DOMAIN",
    "TIMEOUT", "RESOURCE_FAILURE", "WORKER_FAILURE", "ADMISSION_LIMIT", "DISABLED", "INVALID_RESPONSE",
]


class NumericRequest(ExecutionRequest):
    schemaVersion: Literal["numeric-diagnostic-v1"]
    method: Literal["NUMERIC_SAMPLING"]
    scopeId: UUID
    policyVersion: Literal["equivalence-binary64-v1"]


class NumericResult(FrozenModel):
    schemaVersion: Literal["numeric-diagnostic-result-v1"]
    executionId: UUID
    correlationId: str = Field(pattern=r"^[A-Za-z0-9_-]{1,64}$")
    scopeId: UUID
    method: Literal["NUMERIC_SAMPLING"]
    policyVersion: Literal["equivalence-binary64-v1"]
    status: Status
    reasonCode: Reason
    restrictionIds: list[str] = Field(max_length=48)
    attempted: int = Field(ge=0, le=16, strict=True)
    admissible: int = Field(ge=0, le=16, strict=True)
    evaluated: int = Field(ge=0, le=16, strict=True)
    agreeing: int = Field(ge=0, le=16, strict=True)
    disagreeing: int = Field(ge=0, le=16, strict=True)
    unstable: int = Field(ge=0, le=16, strict=True)

    @model_validator(mode="after")
    def nonproof(self) -> NumericResult:
        allowed = {
            "AGREEMENT": {"SAMPLES_AGREE"}, "MISMATCH": {"SAMPLES_DIFFER"},
            "INCONCLUSIVE": {"INSUFFICIENT_SAMPLES", "UNSTABLE_SAMPLES"},
            "UNSUPPORTED": {"UNSUPPORTED_SUBSET", "COMPLEXITY_LIMIT", "INTEGER_DOMAIN"},
            "BLOCKED": {"UNKNOWN_DOMAIN"},
            "EXECUTION_FAILED": {"TIMEOUT", "RESOURCE_FAILURE", "WORKER_FAILURE", "ADMISSION_LIMIT", "DISABLED", "INVALID_RESPONSE"},
        }
        if self.reasonCode not in allowed[self.status]:
            raise ValueError("diagnostic status/reason mismatch")
        if len(set(self.restrictionIds)) != len(self.restrictionIds) or any(
            not re.fullmatch(r"[A-Za-z0-9_-]{1,96}", r) for r in self.restrictionIds
        ):
            raise ValueError("invalid restriction IDs")
        if not (self.agreeing + self.disagreeing == self.evaluated <= self.admissible <= self.attempted
                and self.evaluated + self.unstable <= self.attempted):
            raise ValueError("inconsistent sample counts")
        if self.status in {"AGREEMENT", "MISMATCH"} and (
            self.attempted != 16 or self.evaluated < 4 or self.unstable
            or (self.status == "AGREEMENT") != (self.disagreeing == 0)
        ):
            raise ValueError("insufficient stable evidence")
        if self.status in {"BLOCKED", "UNSUPPORTED", "EXECUTION_FAILED"} and self.attempted:
            raise ValueError("unavailable diagnostic has samples")
        if self.status == "INCONCLUSIVE" and (
            self.attempted != 16 or (self.reasonCode == "UNSTABLE_SAMPLES") != (self.unstable > 0)
            or self.reasonCode == "INSUFFICIENT_SAMPLES" and self.evaluated >= 4
        ):
            raise ValueError("invalid inconclusive diagnostic")
        return self

    def validate_for(self, request: NumericRequest) -> NumericResult:
        if self.attempted and (len(request.variables) > 1 or any(v.domain != "REAL" for v in request.variables)
                               or request.original.kind != "EXPRESSION" or request.candidate.kind != "EXPRESSION"):
            raise ValueError("samples outside supported numeric scope")
        if (self.executionId != request.executionId or self.correlationId != request.correlationId
                or self.scopeId != request.scopeId or self.restrictionIds != [r.id for r in request.restrictions]):
            raise ValueError("diagnostic scope mismatch")
        return self


def unavailable(request: NumericRequest, status: Status, reason: Reason) -> NumericResult:
    return NumericResult(schemaVersion="numeric-diagnostic-result-v1", method="NUMERIC_SAMPLING", policyVersion=POLICY,
                         executionId=request.executionId, correlationId=request.correlationId,
                         scopeId=request.scopeId, status=status, reasonCode=reason,
                         restrictionIds=[r.id for r in request.restrictions], attempted=0,
                         admissible=0, evaluated=0, agreeing=0, disagreeing=0, unstable=0)


def parse_result(data: bytes, request: NumericRequest) -> NumericResult:
    if len(data) > 16384:
        raise ValueError("diagnostic response too large")

    def unique_fields(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError("duplicate diagnostic field")
            result[key] = value
        return result

    payload = json.loads(data, object_pairs_hook=unique_fields)
    if not isinstance(payload, dict) or payload.get("executionId") != str(request.executionId) or payload.get("scopeId") != str(request.scopeId):
        raise ValueError("noncanonical diagnostic identity")
    return NumericResult.model_validate(payload).validate_for(request)
