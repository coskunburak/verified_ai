"""Separate mathematical execution contract. No student or source ownership data crosses it."""
from __future__ import annotations

from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, model_validator

from app.api.schemas.verifier_input import ExpressionNode

METHODS = (
    "ARITHMETIC_EVALUATION", "SYMBOLIC_EQUIVALENCE", "EQUATION_SUBSTITUTION",
    "DOMAIN_CONSTRAINT_CHECK", "SOLUTION_SET_COMPLETENESS",
)
Method = Literal[
    "ARITHMETIC_EVALUATION", "SYMBOLIC_EQUIVALENCE", "EQUATION_SUBSTITUTION",
    "DOMAIN_CONSTRAINT_CHECK", "SOLUTION_SET_COMPLETENESS",
]
Outcome = Literal["PROVEN", "DISPROVEN", "INCONCLUSIVE", "UNSUPPORTED", "BLOCKED", "EXECUTION_FAILED"]
Reason = Literal[
    "EXACT_EQUAL", "EXACT_UNEQUAL", "POLYNOMIAL_IDENTITY", "POLYNOMIAL_NONIDENTITY",
    "VALID_ROOT", "INVALID_ROOT", "DOMAIN_SATISFIED", "DOMAIN_VIOLATION", "UNDEFINED_SOURCE",
    "UNDEFINED_VALUE", "UNSUPPORTED_SUBSET", "COMPLEXITY_LIMIT", "UNKNOWN_DOMAIN",
    "MISSING_RESTRICTION", "CONTRADICTORY_CONSTRAINTS", "ASSIGNMENT_REQUIRED",
    "UNIQUE_SOLUTION", "INCOMPLETE_SOLUTION_SET", "ROOT_SET_NOT_REPRESENTABLE",
    "TIMEOUT", "RESOURCE_FAILURE", "WORKER_FAILURE", "ADMISSION_LIMIT", "DISABLED",
]
Descriptor = Literal[
    "EXACT_RATIONAL_COMPARISON", "POLYNOMIAL_COEFFICIENT_COMPARISON",
    "ORIGINAL_EQUATION_SUBSTITUTION", "DOMAIN_CHECK", "BOUNDED_POLYNOMIAL_ROOT_SET",
]


class FrozenModel(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)


class Statement(FrozenModel):
    kind: Literal["EXPRESSION", "RELATION"]
    expression: ExpressionNode | None = None
    relation: Literal["EQUALS"] | None = None
    left: ExpressionNode | None = None
    right: ExpressionNode | None = None

    @model_validator(mode="after")
    def shape(self) -> Statement:
        if self.kind == "EXPRESSION":
            valid = self.expression is not None and self.left is None and self.right is None and self.relation is None
        else:
            valid = self.expression is None and self.left is not None and self.right is not None and self.relation == "EQUALS"
        if not valid:
            raise ValueError("invalid statement shape")
        return self


class Variable(FrozenModel):
    symbol: str = Field(pattern=r"^[A-Za-z][A-Za-z0-9_]{0,15}$")
    domain: Literal["REAL", "INTEGER", "UNKNOWN"]


class Restriction(FrozenModel):
    id: str = Field(pattern=r"^[A-Za-z0-9_-]{1,96}$")
    relation: Literal["EQUALS", "NOT_EQUALS", "LESS_THAN", "LESS_THAN_OR_EQUAL", "GREATER_THAN", "GREATER_THAN_OR_EQUAL"]
    left: ExpressionNode
    right: ExpressionNode


class ExecutionRequest(FrozenModel):
    schemaVersion: Literal["verification-execution-v1"]
    executionId: UUID
    correlationId: str = Field(pattern=r"^[A-Za-z0-9_-]{1,64}$")
    method: Method
    original: Statement
    candidate: Statement
    variables: list[Variable] = Field(max_length=16)
    restrictions: list[Restriction] = Field(max_length=48)

    @model_validator(mode="after")
    def unique_identifiers(self) -> ExecutionRequest:
        if len({v.symbol for v in self.variables}) != len(self.variables):
            raise ValueError("duplicate variables")
        if len({r.id for r in self.restrictions}) != len(self.restrictions):
            raise ValueError("duplicate restriction IDs")
        return self


class ExactValue(FrozenModel):
    numerator: str = Field(pattern=r"^-?(0|[1-9][0-9]*)$", max_length=320)
    denominator: str = Field(pattern=r"^[1-9][0-9]*$", max_length=320)


class Proof(FrozenModel):
    descriptor: Descriptor
    leftValue: ExactValue | None = None
    rightValue: ExactValue | None = None
    distinctRootCount: int | None = Field(default=None, ge=0, le=2, strict=True)


class ExecutionResult(FrozenModel):
    schemaVersion: Literal["verification-execution-result-v1"] = "verification-execution-result-v1"
    executionId: UUID
    correlationId: str
    method: Method
    methodVersion: Literal["v1"] = "v1"
    verifierVersion: Literal["canonical-executor-v1"] = "canonical-executor-v1"
    outcome: Outcome
    reasonCode: Reason
    restrictionIds: list[str]
    proof: Proof | None = None

    @model_validator(mode="after")
    def proof_presence(self) -> ExecutionResult:
        if (self.outcome in {"PROVEN", "DISPROVEN"}) != (self.proof is not None):
            raise ValueError("proof descriptor/outcome mismatch")
        return self
