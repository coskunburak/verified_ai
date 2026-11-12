from __future__ import annotations

from typing import Optional
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from app.answer_evaluator import evaluate_practice_submission
from app.twin_generator import DeterministicTwinGenerator, TwinInvariantViolationError

router = APIRouter(prefix="/v1/verifier", tags=["practice-verifier"])


class GenerateTwinRequest(BaseModel):
    user_id: str = Field(..., alias="userId")
    skill_id: str = Field(..., alias="skillId")
    trap_family: str = Field(..., alias="trapFamily")
    ordinal_index: int = Field(0, alias="ordinalIndex")

    class Config:
        populate_by_name = True


class GenerateTwinResponse(BaseModel):
    trap_family: str = Field(..., alias="trapFamily")
    prompt_latex: str = Field(..., alias="promptLatex")
    prompt_instruction_tr: str = Field(..., alias="promptInstructionTr")
    prompt_instruction_en: str = Field(..., alias="promptInstructionEn")
    canonical_solution_expr: str = Field(..., alias="canonicalSolutionExpr")
    trap_distractor_expr: str = Field(..., alias="trapDistractorExpr")
    sympy_verification_hash: str = Field(..., alias="sympyVerificationHash")
    target_difficulty: float = Field(..., alias="targetDifficulty")
    execution_time_ms: float = Field(..., alias="executionTimeMs")

    class Config:
        populate_by_name = True


class EvaluatePracticeRequest(BaseModel):
    trap_family: str = Field(..., alias="trapFamily")
    prompt_latex: str = Field(..., alias="promptLatex")
    canonical_solution_expr: str = Field(..., alias="canonicalSolutionExpr")
    trap_distractor_expr: Optional[str] = Field(None, alias="trapDistractorExpr")
    submitted_answer_latex: Optional[str] = Field(None, alias="submittedAnswerLatex")

    class Config:
        populate_by_name = True


class EvaluatePracticeResponse(BaseModel):
    is_correct: bool = Field(..., alias="isCorrect")
    trap_triggered: bool = Field(..., alias="trapTriggered")
    trap_diagnostic_key: Optional[str] = Field(None, alias="trapDiagnosticKey")
    trap_diagnostic_message_tr: Optional[str] = Field(None, alias="trapDiagnosticMessageTr")
    trap_diagnostic_message_en: Optional[str] = Field(None, alias="trapDiagnosticMessageEn")

    class Config:
        populate_by_name = True


@router.post("/twin/generate", response_model=GenerateTwinResponse)
async def generate_twin(req: GenerateTwinRequest) -> GenerateTwinResponse:
    try:
        twin = DeterministicTwinGenerator.generate_twin(
            user_id=req.user_id,
            skill_id=req.skill_id,
            trap_family=req.trap_family,
            ordinal_index=req.ordinal_index,
        )
        return GenerateTwinResponse(
            trapFamily=twin.trap_family,
            promptLatex=twin.prompt_latex,
            promptInstructionTr=twin.prompt_instruction_tr,
            promptInstructionEn=twin.prompt_instruction_en,
            canonicalSolutionExpr=twin.canonical_solution_expr,
            trapDistractorExpr=twin.trap_distractor_expr,
            sympyVerificationHash=twin.sympy_verification_hash,
            targetDifficulty=twin.target_difficulty,
            executionTimeMs=twin.execution_time_ms,
        )
    except TwinInvariantViolationError as exc:
        raise HTTPException(status_code=422, detail=str(exc))


@router.post("/practice/evaluate", response_model=EvaluatePracticeResponse)
async def evaluate_practice(req: EvaluatePracticeRequest) -> EvaluatePracticeResponse:
    result = evaluate_practice_submission(
        trap_family=req.trap_family,
        prompt_latex=req.prompt_latex,
        canonical_solution_expr=req.canonical_solution_expr,
        trap_distractor_expr=req.trap_distractor_expr,
        submitted_answer_latex=req.submitted_answer_latex,
    )
    return EvaluatePracticeResponse(
        isCorrect=result["is_correct"],
        trapTriggered=result["trap_triggered"],
        trapDiagnosticKey=result["trap_diagnostic_key"],
        trapDiagnosticMessageTr=result["trap_diagnostic_message_tr"],
        trapDiagnosticMessageEn=result["trap_diagnostic_message_en"],
    )
