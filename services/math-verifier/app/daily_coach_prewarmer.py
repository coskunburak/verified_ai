"""
Deterministic Daily Coach Twin Pre-Warming Engine (Sprint 8.4).
Enforces ADR-011 ($0.00 LLM token cost), INV-COACH-001, INV-COACH-003, INV-COACH-006.
Zero external HTTP or LLM SDK imports are permitted in this module.
"""
from __future__ import annotations

import hashlib
import time
from dataclasses import dataclass
from typing import Any, Dict, List, Optional, Sequence

import sympy as sp


SUPPORTED_TASK_TYPES = frozenset({"TRAP_REPAIR", "SPACED_RETENTION", "EXAM_SYNTHESIS"})


@dataclass(frozen=True)
class CoachTaskDescriptor:
    task_order: int
    task_type: str
    target_skill_code: str
    target_mistake_code: Optional[str] = None


@dataclass(frozen=True)
class PrewarmedCoachTwin:
    task_order: int
    task_type: str
    target_skill_code: str
    target_mistake_code: Optional[str]
    assigned_twin_id: str
    prompt_preview_latex: str
    canonical_solution_latex: str
    counterexample_hint_latex: str
    step_diff_anchor_ast: Dict[str, Any]
    sympy_expr_str: str
    execution_time_ms: float
    llm_tokens_used: int = 0


class DailyCoachTwinPrewarmer:
    """
    Deterministic SymPy twin pre-warmer for the 3-block Daily Coach Hub mission.
    Produces canonical LaTeX previews, solution expressions, Socratic counterexample
    proof cards, and Solver step-diff anchor ASTs at $0.00 LLM cost.
    """

    @staticmethod
    def compute_seed_digest(
        user_id: str,
        local_date: str,
        task_order: int,
        target_skill_code: str,
        target_mistake_code: Optional[str],
    ) -> bytes:
        mistake_part = target_mistake_code or "NONE"
        raw = f"{user_id}:{local_date}:{task_order}:{target_skill_code}:{mistake_part}".encode("utf-8")
        return hashlib.sha256(raw).digest()

    @classmethod
    def prewarm_task(
        cls,
        user_id: str,
        local_date: str,
        descriptor: CoachTaskDescriptor,
    ) -> PrewarmedCoachTwin:
        if descriptor.task_type not in SUPPORTED_TASK_TYPES:
            raise ValueError(f"Unsupported task_type: {descriptor.task_type}")
        if not (1 <= descriptor.task_order <= 5):
            raise ValueError(f"task_order must be between 1 and 5, got {descriptor.task_order}")
        if not descriptor.target_skill_code or not descriptor.target_skill_code.strip():
            raise ValueError("target_skill_code must not be empty")

        start = time.perf_counter()
        digest = cls.compute_seed_digest(
            user_id=user_id,
            local_date=local_date,
            task_order=descriptor.task_order,
            target_skill_code=descriptor.target_skill_code,
            target_mistake_code=descriptor.target_mistake_code,
        )
        assigned_twin_id = f"twin_84_{digest.hex()[:12]}"

        family = cls._resolve_template_family(
            task_type=descriptor.task_type,
            skill_code=descriptor.target_skill_code,
            mistake_code=descriptor.target_mistake_code,
        )

        if family == "RATIONAL_DOMAIN":
            payload = cls._build_rational_domain_twin(digest)
        elif family == "INEQUALITY_FLIP":
            payload = cls._build_inequality_flip_twin(digest)
        elif family == "SIGN_DISCRIMINANT":
            payload = cls._build_discriminant_sign_twin(digest)
        elif family == "RADICAL_EXTRANEOUS":
            payload = cls._build_radical_extraneous_twin(digest)
        else:
            payload = cls._build_chain_rule_synthesis_twin(digest)

        elapsed_ms = round((time.perf_counter() - start) * 1000.0, 3)

        return PrewarmedCoachTwin(
            task_order=descriptor.task_order,
            task_type=descriptor.task_type,
            target_skill_code=descriptor.target_skill_code,
            target_mistake_code=descriptor.target_mistake_code,
            assigned_twin_id=assigned_twin_id,
            prompt_preview_latex=payload["prompt_preview_latex"],
            canonical_solution_latex=payload["canonical_solution_latex"],
            counterexample_hint_latex=payload["counterexample_hint_latex"],
            step_diff_anchor_ast=payload["step_diff_anchor_ast"],
            sympy_expr_str=payload["sympy_expr_str"],
            execution_time_ms=elapsed_ms,
            llm_tokens_used=0,
        )

    @classmethod
    def prewarm_mission(
        cls,
        user_id: str,
        local_date: str,
        descriptors: Sequence[CoachTaskDescriptor],
    ) -> List[PrewarmedCoachTwin]:
        return [cls.prewarm_task(user_id, local_date, desc) for desc in descriptors]

    @classmethod
    def generate_cold_start_mission(
        cls,
        user_id: str,
        local_date: str,
    ) -> List[PrewarmedCoachTwin]:
        cold_start_descriptors = [
            CoachTaskDescriptor(
                task_order=1,
                task_type="TRAP_REPAIR",
                target_skill_code="MATH.ALG.RATIONAL_EQ",
                target_mistake_code="ALG_DOMAIN_EXCLUSION_MISSED",
            ),
            CoachTaskDescriptor(
                task_order=2,
                task_type="SPACED_RETENTION",
                target_skill_code="MATH.ALG.LINEAR_INEQ",
                target_mistake_code="INEQ_NEGATIVE_MULT_NO_FLIP",
            ),
            CoachTaskDescriptor(
                task_order=3,
                task_type="EXAM_SYNTHESIS",
                target_skill_code="MATH.CALC.CHAIN_RULE_COMPOSITE",
                target_mistake_code="CALC_CHAIN_RULE_OMITTED",
            ),
        ]
        return cls.prewarm_mission(user_id, local_date, cold_start_descriptors)

    @staticmethod
    def _resolve_template_family(
        task_type: str,
        skill_code: str,
        mistake_code: Optional[str],
    ) -> str:
        code = (mistake_code or "").upper()
        skill = skill_code.upper()
        if "DOMAIN" in code or "DENOMINATOR" in code or "RATIONAL" in skill:
            return "RATIONAL_DOMAIN"
        if "INEQ" in code or "INEQ" in skill:
            return "INEQUALITY_FLIP"
        if "SIGN" in code or "DISCRIMINANT" in skill or "QUADRATIC" in skill:
            return "SIGN_DISCRIMINANT"
        if "RADICAL" in skill or "EXTRANEOUS" in code or "SQUARE_BOTH_SIDES" in code:
            return "RADICAL_EXTRANEOUS"
        if "CHAIN" in code or "EXPONENT" in code or "CALC" in skill or task_type == "EXAM_SYNTHESIS":
            return "CHAIN_RULE"
        if task_type == "TRAP_REPAIR":
            return "RATIONAL_DOMAIN"
        if task_type == "SPACED_RETENTION":
            return "SIGN_DISCRIMINANT"
        return "CHAIN_RULE"

    @staticmethod
    def _build_rational_domain_twin(digest: bytes) -> Dict[str, Any]:
        x = sp.Symbol("x", real=True)
        excluded_root = ((digest[0] % 5) + 2) * (-1 if digest[1] % 2 else 1)
        shift = (digest[2] % 4) + 1
        numerator = sp.expand((x - excluded_root) * (x + shift))
        denominator = x - excluded_root
        expr = numerator / denominator
        simplified = sp.simplify(expr)
        witness_val = excluded_root

        prompt_latex = f"{sp.latex(expr)} = {sp.latex(simplified)}, \\quad x \\neq {excluded_root}"
        solution_latex = f"x \\in \\mathbb{{R}} \\setminus \\{{{excluded_root}\\}}"
        counterexample_latex = (
            f"x = {witness_val} \\implies {sp.latex(denominator)} = 0 \\text{{ (tanımsız)}}"
        )
        return {
            "prompt_preview_latex": prompt_latex,
            "canonical_solution_latex": solution_latex,
            "counterexample_hint_latex": counterexample_latex,
            "sympy_expr_str": str(expr),
            "step_diff_anchor_ast": {
                "family": "DENOMINATOR_ZERO_DOMAIN",
                "sympy_srepr": sp.srepr(expr),
                "simplified_expr": str(simplified),
                "excluded_value": int(excluded_root),
                "expected_latex": solution_latex,
            },
        }

    @staticmethod
    def _build_inequality_flip_twin(digest: bytes) -> Dict[str, Any]:
        x = sp.Symbol("x", real=True)
        coeff = -((digest[0] % 4) + 2)
        offset = (digest[1] % 5) + 1
        rhs = ((digest[2] % 4) + 1) * abs(coeff)
        lhs = coeff * (x - offset)
        ineq = sp.StrictGreaterThan(lhs, rhs)
        bound = sp.Rational(rhs, coeff) + offset
        solved_rel = sp.StrictLessThan(x, bound)
        witness_val = int(bound) + 2
        lhs_witness = int(lhs.subs(x, witness_val))

        prompt_latex = sp.latex(ineq)
        solution_latex = sp.latex(solved_rel)
        counterexample_latex = (
            f"x = {witness_val} \\implies {coeff}({witness_val} - {offset}) = {lhs_witness} \\not> {rhs}"
        )
        return {
            "prompt_preview_latex": prompt_latex,
            "canonical_solution_latex": solution_latex,
            "counterexample_hint_latex": counterexample_latex,
            "sympy_expr_str": str(lhs - rhs),
            "step_diff_anchor_ast": {
                "family": "SIGN_INVERSION",
                "sympy_srepr": sp.srepr(ineq),
                "divisor": int(coeff),
                "flipped_operator": "<",
                "expected_latex": solution_latex,
            },
        }

    @staticmethod
    def _build_discriminant_sign_twin(digest: bytes) -> Dict[str, Any]:
        x = sp.Symbol("x", real=True)
        a = (digest[0] % 3) + 1
        b = -((digest[1] % 5) + 3)
        c = -((digest[2] % 4) + 1)
        poly = a * x**2 + b * x + c
        eq = sp.Eq(poly, 0)
        delta_val = b**2 - 4 * a * c
        trap_delta = b**2 - 4 * a * abs(c)

        prompt_latex = sp.latex(eq)
        solution_latex = f"\\Delta = ({b})^2 - 4({a})({c}) = {delta_val}"
        counterexample_latex = (
            f"(-4)({a})({c}) = +{abs(4 * a * c)} \\neq -{abs(4 * a * c)} "
            f"\\implies \\Delta = {delta_val} \\neq {trap_delta}"
        )
        return {
            "prompt_preview_latex": prompt_latex,
            "canonical_solution_latex": solution_latex,
            "counterexample_hint_latex": counterexample_latex,
            "sympy_expr_str": str(poly),
            "step_diff_anchor_ast": {
                "family": "SIGN_INVERSION",
                "sympy_srepr": sp.srepr(poly),
                "canonical_delta": int(delta_val),
                "trap_delta": int(trap_delta),
                "expected_latex": solution_latex,
            },
        }

    @staticmethod
    def _build_radical_extraneous_twin(digest: bytes) -> Dict[str, Any]:
        x = sp.Symbol("x", real=True)
        k = (digest[0] % 4) + 2
        expr = sp.sqrt(x + k) - (x - k)
        eq = sp.Eq(sp.sqrt(x + k), x - k)
        # Valid root satisfies x - k >= 0; solve (x + k) = (x - k)^2
        candidates = sp.solve(sp.Eq(x + k, (x - k) ** 2), x)
        valid_roots = [r for r in candidates if r.is_real and (r - k) >= 0]
        extraneous_roots = [r for r in candidates if r.is_real and (r - k) < 0]
        valid_str = ", ".join(sp.latex(r) for r in valid_roots)
        ext_str = ", ".join(sp.latex(r) for r in extraneous_roots) if extraneous_roots else "0"

        prompt_latex = sp.latex(eq)
        solution_latex = f"x = {valid_str}"
        counterexample_latex = f"x = {ext_str} \\implies x - {k} < 0"
        return {
            "prompt_preview_latex": prompt_latex,
            "canonical_solution_latex": solution_latex,
            "counterexample_hint_latex": counterexample_latex,
            "sympy_expr_str": str(expr),
            "step_diff_anchor_ast": {
                "family": "DENOMINATOR_ZERO_DOMAIN",
                "sympy_srepr": sp.srepr(expr),
                "expected_latex": solution_latex,
            },
        }

    @staticmethod
    def _build_chain_rule_synthesis_twin(digest: bytes) -> Dict[str, Any]:
        x = sp.Symbol("x", real=True)
        a = (digest[0] % 3) + 2
        b = (digest[1] % 5) + 1
        n = (digest[2] % 3) + 3
        inner = a * x**2 + b
        outer = inner**n
        derivative = sp.factor(sp.diff(outer, x))
        trap_omitted = n * inner ** (n - 1)
        witness_x = 1
        true_at_1 = int(derivative.subs(x, witness_x))
        trap_at_1 = int(trap_omitted.subs(x, witness_x))

        prompt_latex = f"\\frac{{d}}{{dx}}\\left[{sp.latex(outer)}\\right]"
        solution_latex = sp.latex(derivative)
        counterexample_latex = (
            f"x = {witness_x} \\implies {true_at_1} \\neq {trap_at_1} \\text{{ (iç türev: }} {sp.latex(sp.diff(inner, x))}\\text{{)}}"
        )
        return {
            "prompt_preview_latex": prompt_latex,
            "canonical_solution_latex": solution_latex,
            "counterexample_hint_latex": counterexample_latex,
            "sympy_expr_str": str(outer),
            "step_diff_anchor_ast": {
                "family": "EXPONENT_ADDITION_CONFUSION",
                "sympy_srepr": sp.srepr(outer),
                "derivative_srepr": sp.srepr(derivative),
                "inner_derivative": str(sp.diff(inner, x)),
                "expected_latex": solution_latex,
            },
        }
