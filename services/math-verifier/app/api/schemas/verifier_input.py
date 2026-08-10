from __future__ import annotations

import re
from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator

IDENTIFIER_PATTERN = re.compile(r"^[A-Za-z][A-Za-z0-9_]{0,15}$")


class NumberNode(BaseModel):
    model_config = ConfigDict(extra="forbid")

    kind: Literal["NUMBER"]
    numericType: Literal["INTEGER", "DECIMAL", "RATIONAL"]
    value: str = Field(pattern=r"^-?(?:0|[1-9][0-9]*)(?:\.[0-9]+)?$", max_length=80)


class VariableNode(BaseModel):
    model_config = ConfigDict(extra="forbid")

    kind: Literal["VARIABLE"]
    symbol: str

    @field_validator("symbol")
    @classmethod
    def symbol_is_safe(cls, value: str) -> str:
        if not IDENTIFIER_PATTERN.match(value):
            raise ValueError("unsafe variable symbol")
        return value


class UnaryNode(BaseModel):
    model_config = ConfigDict(extra="forbid")

    kind: Literal["UNARY"]
    operator: Literal["NEGATE"]
    operand: ExpressionNode


class BinaryNode(BaseModel):
    model_config = ConfigDict(extra="forbid")

    kind: Literal["BINARY"]
    operator: Literal["ADD", "SUBTRACT", "MULTIPLY", "DIVIDE", "POWER"]
    left: ExpressionNode
    right: ExpressionNode


class FunctionNode(BaseModel):
    model_config = ConfigDict(extra="forbid")

    kind: Literal["FUNCTION"]
    function: Literal["SQRT", "SIN", "COS", "TAN", "LOG", "EXP"]
    args: list[ExpressionNode] = Field(min_length=1, max_length=1)


ExpressionNode = Annotated[
    NumberNode | VariableNode | UnaryNode | BinaryNode | FunctionNode,
    Field(discriminator="kind"),
]


class VerifierVariable(BaseModel):
    model_config = ConfigDict(extra="forbid")

    symbol: str
    role: Literal["VARIABLE", "PARAMETER", "UNKNOWN"]
    domain: Literal["UNKNOWN", "REAL", "INTEGER"]
    sourceBlockIds: list[str] = Field(default_factory=list, max_length=16)

    @field_validator("symbol")
    @classmethod
    def symbol_is_safe(cls, value: str) -> str:
        if not IDENTIFIER_PATTERN.match(value):
            raise ValueError("unsafe variable symbol")
        return value


class VerifierStatement(BaseModel):
    model_config = ConfigDict(extra="forbid")

    kind: Literal["EXPRESSION", "RELATION"]
    sourceExpressionId: str = Field(min_length=1, max_length=64)
    relation: Literal[
        "EQUALS",
        "NOT_EQUALS",
        "LESS_THAN",
        "LESS_THAN_OR_EQUAL",
        "GREATER_THAN",
        "GREATER_THAN_OR_EQUAL",
    ] | None
    left: ExpressionNode | None
    right: ExpressionNode | None
    expression: ExpressionNode | None
    sourceBlockIds: list[str] = Field(default_factory=list, max_length=16)


class VerifierRestriction(BaseModel):
    model_config = ConfigDict(extra="forbid")

    id: str = Field(min_length=1, max_length=96)
    relation: Literal[
        "EQUALS",
        "NOT_EQUALS",
        "LESS_THAN",
        "LESS_THAN_OR_EQUAL",
        "GREATER_THAN",
        "GREATER_THAN_OR_EQUAL",
    ]
    left: ExpressionNode
    right: ExpressionNode
    reason: Literal[
        "SOURCE_EXPLICIT",
        "DENOMINATOR_NON_ZERO",
        "SQRT_DOMAIN_NON_NEGATIVE",
        "LOG_DOMAIN_POSITIVE",
        "TAN_DOMAIN_COS_NON_ZERO",
    ]
    provenance: Literal["SOURCE", "DERIVED"]
    sourceBlockIds: list[str] = Field(default_factory=list, max_length=16)


class ComplexityPolicy(BaseModel):
    model_config = ConfigDict(extra="forbid")

    version: Literal["canonical-math-limits-v1"]
    maxExpressionLength: Literal[512]
    maxAstNodes: Literal[120]
    maxAstDepth: Literal[32]
    maxExponentMagnitude: Literal[12]
    maxNumericDigits: Literal[64]
    maxFunctionNestingDepth: Literal[8]


class VerifierInputRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    schemaVersion: Literal["verifier-input-v1"]
    canonicalSchemaVersion: Literal["canonical-problem-v1"]
    problemType: Literal["ARITHMETIC_EXPRESSION", "ALGEBRAIC_EXPRESSION", "EQUATION", "INEQUALITY"]
    taskType: Literal["EVALUATE", "SIMPLIFY", "SOLVE_EQUATION", "SOLVE_INEQUALITY"]
    variables: list[VerifierVariable] = Field(default_factory=list, max_length=16)
    statements: list[VerifierStatement] = Field(min_length=1, max_length=8)
    restrictions: list[VerifierRestriction] = Field(default_factory=list, max_length=32)
    complexityPolicy: ComplexityPolicy


class VerifierInputValidationResponse(BaseModel):
    status: Literal["ACCEPTED"] = "ACCEPTED"
    schemaVersion: Literal["verifier-input-v1"] = "verifier-input-v1"
    verifierVersion: str = "typed-ast-validator-v0.1.0"
    statementCount: int
    restrictionCount: int
    correlationId: str = "unavailable"

    def with_correlation_id(self, correlation_id: str) -> VerifierInputValidationResponse:
        return self.model_copy(update={"correlationId": correlation_id})


UnaryNode.model_rebuild()
BinaryNode.model_rebuild()
FunctionNode.model_rebuild()
