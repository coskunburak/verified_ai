"""Isolated calculus contract; never upgrades canonical/candidate/execution v1 semantics."""
from __future__ import annotations

from fractions import Fraction
from typing import Literal
from uuid import UUID

from pydantic import Field, model_validator

from app.api.schemas.execution import ExactValue, FrozenModel, Outcome, Restriction, Variable
from app.api.schemas.verifier_input import ExpressionNode

METHODS = ("DERIVATIVE_BACKCHECK", "INTEGRAL_DIFFERENTIATION_BACKCHECK", "FINITE_LIMIT")
Method = Literal["DERIVATIVE_BACKCHECK", "INTEGRAL_DIFFERENTIATION_BACKCHECK", "FINITE_LIMIT"]
Reason = Literal[
    "EXACT_DERIVATIVE", "EXACT_ANTIDERIVATIVE", "EXACT_FINITE_LIMIT", "EXACT_CONTRADICTION",
    "UNKNOWN_DOMAIN", "NON_REAL_DOMAIN", "DOMAIN_OBSTRUCTION", "UNSUPPORTED_SUBSET",
    "COMPLEXITY_LIMIT", "NONFINITE_LIMIT", "NUMERICAL_DIAGNOSTIC", "SYMBOLIC_UNRESOLVED",
    "TIMEOUT", "RESOURCE_FAILURE", "WORKER_FAILURE", "INVALID_RESPONSE", "ADMISSION_LIMIT", "DISABLED",
]


class Scope(FrozenModel):
    identity: UUID
    sourceRevision: int = Field(ge=1, le=2147483647, strict=True)
    candidateRevision: int = Field(ge=1, le=2147483647, strict=True)
    sourceVersion: Literal["calculus-task-v1"]
    candidateVersion: Literal["calculus-task-v1"]


class Interval(FrozenModel):
    kind: Literal["OPEN_INTERVAL"]
    lower: ExactValue | None
    upper: ExactValue | None

    @model_validator(mode="after")
    def ordered(self):
        for endpoint in (self.lower, self.upper):
            if endpoint is not None:
                fraction(endpoint)
        if (
            self.lower is not None
            and self.upper is not None
            and fraction(self.lower) >= fraction(self.upper)
        ):
            raise ValueError("empty calculus interval")
        return self


def fraction(value: ExactValue) -> Fraction:
    result = Fraction(int(value.numerator), int(value.denominator))
    if (max(result.numerator.bit_length(), result.denominator.bit_length()) > 1024
            or str(result.numerator) != value.numerator or str(result.denominator) != value.denominator):
        raise ValueError("unbounded or noncanonical exact value")
    return result


class Approach(FrozenModel):
    target: ExactValue
    direction: Literal["LEFT", "RIGHT", "TWO_SIDED"]


class CalculusRequest(FrozenModel):
    schemaVersion: Literal["calculus-execution-v1"]
    executionId: UUID
    correlationId: str = Field(pattern=r"^[A-Za-z0-9_-]{1,64}$")
    scope: Scope
    method: Method
    variable: Variable
    interval: Interval
    original: ExpressionNode
    candidate: ExpressionNode
    restrictions: list[Restriction] = Field(max_length=48)
    approach: Approach | None
    integralScope: Literal["ANTIDERIVATIVE_REPRESENTATIVE"] | None

    @model_validator(mode="after")
    def task_shape(self):
        if (self.method == "FINITE_LIMIT") != (self.approach is not None):
            raise ValueError("limit approach required only for limits")
        if (self.method == "INTEGRAL_DIFFERENTIATION_BACKCHECK") != (self.integralScope is not None):
            raise ValueError("integral scope required only for integrals")
        if len({r.id for r in self.restrictions}) != len(self.restrictions):
            raise ValueError("duplicate restriction IDs")
        if self.approach:
            fraction(self.approach.target)
        return self


class Certificate(FrozenModel):
    descriptor: Literal[
        "DERIVATIVE_IDENTITY", "ANTIDERIVATIVE_IDENTITY", "RATIONAL_NONIDENTITY",
        "RATIONAL_FINITE_LIMIT",
    ]
    scope: Literal["OPEN_INTERVAL", "ANTIDERIVATIVE_REPRESENTATIVE", "PUNCTURED_NEIGHBORHOOD"]
    leftValue: ExactValue | None
    rightValue: ExactValue | None


class Diagnostic(FrozenModel):
    policy: Literal["calculus-binary64-v1"]
    attempted: int = Field(ge=0, le=16, strict=True)
    evaluated: int = Field(ge=0, le=16, strict=True)
    agreeing: int = Field(ge=0, le=16, strict=True)

    @model_validator(mode="after")
    def counts(self):
        if not self.agreeing <= self.evaluated <= self.attempted:
            raise ValueError("invalid diagnostic counts")
        return self


class CalculusResult(FrozenModel):
    schemaVersion: Literal["calculus-execution-result-v1"]
    executionId: UUID
    correlationId: str = Field(pattern=r"^[A-Za-z0-9_-]{1,64}$")
    scope: Scope
    method: Method
    methodVersion: Literal["v1"]
    verifierVersion: Literal["bounded-calculus-v1"]
    outcome: Outcome
    reasonCode: Reason
    restrictionIds: list[str] = Field(max_length=48)
    proof: Certificate | None
    diagnostic: Diagnostic | None

    @model_validator(mode="after")
    def evidence(self):
        if len(set(self.restrictionIds)) != len(self.restrictionIds):
            raise ValueError("duplicate restriction references")
        import re
        if any(not re.fullmatch(r"[A-Za-z0-9_-]{1,96}", value) for value in self.restrictionIds):
            raise ValueError("invalid restriction reference")
        proof = self.proof
        if self.outcome in {"PROVEN", "DISPROVEN"}:
            if proof is None or self.diagnostic is not None:
                raise ValueError("exact evidence required")
            if self.method == "FINITE_LIMIT":
                if proof.descriptor != "RATIONAL_FINITE_LIMIT" or proof.scope != "PUNCTURED_NEIGHBORHOOD":
                    raise ValueError("invalid limit proof")
                if proof.leftValue is None or proof.rightValue is None:
                    raise ValueError("exact limit values required")
                equal = fraction(proof.leftValue) == fraction(proof.rightValue)
                if equal != (self.outcome == "PROVEN"):
                    raise ValueError("contradictory exact values")
            else:
                expected = "DERIVATIVE_IDENTITY" if self.method == "DERIVATIVE_BACKCHECK" else "ANTIDERIVATIVE_IDENTITY"
                scope = "OPEN_INTERVAL" if self.method == "DERIVATIVE_BACKCHECK" else "ANTIDERIVATIVE_REPRESENTATIVE"
                if proof.scope != scope or proof.descriptor != (expected if self.outcome == "PROVEN" else "RATIONAL_NONIDENTITY"):
                    raise ValueError("method/proof mismatch")
                if proof.leftValue is not None or proof.rightValue is not None:
                    raise ValueError("unexpected scalar certificate")
            reason = {"DERIVATIVE_BACKCHECK": "EXACT_DERIVATIVE", "INTEGRAL_DIFFERENTIATION_BACKCHECK": "EXACT_ANTIDERIVATIVE", "FINITE_LIMIT": "EXACT_FINITE_LIMIT"}[self.method]
            if self.reasonCode != (reason if self.outcome == "PROVEN" else "EXACT_CONTRADICTION"):
                raise ValueError("reason/proof mismatch")
        else:
            allowed = {
                "BLOCKED": {"UNKNOWN_DOMAIN", "NON_REAL_DOMAIN", "DOMAIN_OBSTRUCTION"},
                "UNSUPPORTED": {"UNSUPPORTED_SUBSET", "COMPLEXITY_LIMIT"},
                "INCONCLUSIVE": {"NONFINITE_LIMIT", "NUMERICAL_DIAGNOSTIC", "SYMBOLIC_UNRESOLVED"},
                "EXECUTION_FAILED": {"TIMEOUT", "RESOURCE_FAILURE", "WORKER_FAILURE", "INVALID_RESPONSE", "ADMISSION_LIMIT", "DISABLED"},
            }
            if proof is not None or self.reasonCode not in allowed.get(self.outcome, set()):
                raise ValueError("invalid nonproof outcome")
            if (self.reasonCode == "NUMERICAL_DIAGNOSTIC") != (self.diagnostic is not None):
                raise ValueError("diagnostic mismatch")
            if self.diagnostic is not None and self.method != "FINITE_LIMIT":
                raise ValueError("unsupported diagnostic method")
        return self

    def validate_for(self, request: CalculusRequest):
        if (self.executionId != request.executionId or self.correlationId != request.correlationId
                or self.scope != request.scope or self.method != request.method
                or self.restrictionIds != [r.id for r in request.restrictions]):
            raise ValueError("calculus response identity mismatch")
        return self
