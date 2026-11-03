"""Manual attempt v1. Indices are ONE based; no aggregate VERIFIED status exists."""
import re
from fractions import Fraction
from typing import Literal
from uuid import UUID

from pydantic import Field, model_validator

from app.api.schemas.execution import FrozenModel, Statement, Variable, Restriction, ExactValue


class AttemptStep(FrozenModel):
    # None means normalization was unsupported; never silently omit a learner line.
    statement: Statement | None


class AttemptRequest(FrozenModel):
    schemaVersion: Literal["attempt-verification-v1"]
    executionId: UUID
    correlationId: str = Field(pattern=r"^[A-Za-z0-9_-]{1,64}$")
    original: Statement
    variables: list[Variable] = Field(max_length=1)
    restrictions: list[Restriction] = Field(max_length=48)
    steps: list[AttemptStep] = Field(min_length=1, max_length=12)


class CounterexampleEvidence(FrozenModel):
    schemaVersion: Literal["attempt-witness-v1"] = "attempt-witness-v1"
    witnessType: Literal["EXPRESSION_VALUES", "EQUATION_TRUTH"]
    variableValues: dict[str, ExactValue] = Field(max_length=1)
    previousStepEvaluation: str = Field(max_length=650)
    submittedStepEvaluation: str = Field(max_length=650)
    claimRefuted: Literal["EXPRESSION_IDENTITY", "EQUATION_EQUIVALENCE"]
    domainSatisfied: Literal[True] = True
    verifierVersion: Literal["bounded-attempt-v1"] = "bounded-attempt-v1"
    explanationCode: Literal["UNEQUAL_VALUES", "DIFFERENT_TRUTH"]

    @model_validator(mode="after")
    def sound_shape(self):
        if any(not re.fullmatch(r"[A-Za-z][A-Za-z0-9_]{0,15}", name) for name in self.variableValues):
            raise ValueError("invalid witness variable")
        equation = self.witnessType == "EQUATION_TRUTH"
        if self.claimRefuted != ("EQUATION_EQUIVALENCE" if equation else "EXPRESSION_IDENTITY") or self.explanationCode != ("DIFFERENT_TRUTH" if equation else "UNEQUAL_VALUES"):
            raise ValueError("inconsistent witness claim")
        values = (self.previousStepEvaluation, self.submittedStepEvaluation)
        if equation:
            if set(values) != {"true", "false"}:
                raise ValueError("invalid equation truth witness")
        elif any(not re.fullmatch(r"-?(0|[1-9][0-9]*)(/[1-9][0-9]*)?", v) for v in values) or Fraction(values[0]) == Fraction(values[1]):
            raise ValueError("invalid exact expression witness")
        return self


Verdict = Literal["VALID_EQUIVALENT", "FIRST_WRONG_STEP", "UNCERTAIN", "NOT_ASSESSED"]
Reason = Literal["EXACT_IDENTITY", "SAME_SOLUTION_SET", "NON_EQUIVALENT", "UNSUPPORTED_MATH",
                 "EARLIER_DIVERGENCE", "EARLIER_UNCERTAINTY", "TIMEOUT", "RESOURCE_FAILURE",
                 "WORKER_FAILURE", "ADMISSION_LIMIT", "DISABLED", "INVALID_RESPONSE"]


class StepResult(FrozenModel):
    index: int = Field(ge=1, le=12, strict=True)
    verdict: Verdict
    reasonCode: Reason
    mistakeCode: Literal["ALG_TRANSFORMATION_INVALID"] | None = None
    counterexampleStatus: Literal["AVAILABLE", "COUNTEREXAMPLE_UNAVAILABLE", "NOT_APPLICABLE"]
    counterexample: CounterexampleEvidence | None = None


class AttemptResult(FrozenModel):
    schemaVersion: Literal["attempt-analysis-v1"] = "attempt-analysis-v1"
    verifierVersion: Literal["bounded-attempt-v1"] = "bounded-attempt-v1"
    taxonomyVersion: Literal["attempt-taxonomy-v1"] = "attempt-taxonomy-v1"
    executionId: UUID
    correlationId: str
    outcome: Literal["INCOMPLETE_VALID_SO_FAR", "STEP_ERROR_FOUND", "NEEDS_REVIEW"]
    firstWrongStepIndex: int | None = Field(default=None, ge=1, le=12, strict=True)
    domain: Literal["REAL_POLYNOMIAL", "UNSUPPORTED"]
    steps: list[StepResult] = Field(min_length=1, max_length=12)

    @model_validator(mode="after")
    def consistent(self):
        wrong = [s.index for s in self.steps if s.verdict == "FIRST_WRONG_STEP"]
        if [s.index for s in self.steps] != list(range(1, len(self.steps) + 1)):
            raise ValueError("non-contiguous steps")
        if wrong != ([] if self.firstWrongStepIndex is None else [self.firstWrongStepIndex]):
            raise ValueError("wrong-step mismatch")
        if (self.outcome == "STEP_ERROR_FOUND") != bool(wrong):
            raise ValueError("outcome mismatch")
        stopped = False
        stop_reason = None
        for step in self.steps:
            if stopped and step.verdict != "NOT_ASSESSED":
                raise ValueError("analysis after uncertainty/divergence")
            if not stopped and step.verdict == "NOT_ASSESSED":
                raise ValueError("missing preceding stop")
            if step.verdict == "NOT_ASSESSED" and step.reasonCode != stop_reason:
                raise ValueError("inconsistent stop reason")
            if step.verdict == "UNCERTAIN" and step.reasonCode not in {
                "UNSUPPORTED_MATH", "TIMEOUT", "RESOURCE_FAILURE", "WORKER_FAILURE",
                "ADMISSION_LIMIT", "DISABLED", "INVALID_RESPONSE",
            }:
                raise ValueError("invalid uncertainty reason")
            if step.verdict in {"FIRST_WRONG_STEP", "UNCERTAIN"}:
                stopped = True
                stop_reason = "EARLIER_DIVERGENCE" if step.verdict == "FIRST_WRONG_STEP" else "EARLIER_UNCERTAINTY"
            if (step.verdict == "FIRST_WRONG_STEP") != (step.mistakeCode is not None):
                raise ValueError("unevidenced classification")
            if (step.counterexampleStatus == "AVAILABLE") != (step.counterexample is not None):
                raise ValueError("witness status mismatch")
            if step.verdict == "VALID_EQUIVALENT" and (self.domain != "REAL_POLYNOMIAL" or step.reasonCode not in {"EXACT_IDENTITY", "SAME_SOLUTION_SET"}):
                raise ValueError("invalid proof reason")
            if step.verdict == "FIRST_WRONG_STEP" and (self.domain != "REAL_POLYNOMIAL" or step.reasonCode != "NON_EQUIVALENT" or step.counterexampleStatus == "NOT_APPLICABLE"):
                raise ValueError("invalid disproof reason")
            if step.verdict != "FIRST_WRONG_STEP" and step.counterexampleStatus != "NOT_APPLICABLE":
                raise ValueError("unexpected witness status")
            if step.counterexample is not None and step.verdict != "FIRST_WRONG_STEP":
                raise ValueError("witness without disproof")
        if (self.outcome == "INCOMPLETE_VALID_SO_FAR") != all(s.verdict == "VALID_EQUIVALENT" for s in self.steps):
            raise ValueError("unproved progress")
        return self

    def validate_for(self, request):
        if (self.executionId != request.executionId or self.correlationId != request.correlationId
                or len(self.steps) != len(request.steps)):
            raise ValueError("response binding mismatch")
        return self


def unavailable(request, reason="WORKER_FAILURE"):
    return AttemptResult(executionId=request.executionId, correlationId=request.correlationId,
                         outcome="NEEDS_REVIEW", domain="UNSUPPORTED", steps=[
        StepResult(index=i, verdict="UNCERTAIN" if i == 1 else "NOT_ASSESSED",
                   reasonCode=reason if i == 1 else "EARLIER_UNCERTAINTY",
                   counterexampleStatus="NOT_APPLICABLE") for i in range(1, len(request.steps) + 1)
    ])
