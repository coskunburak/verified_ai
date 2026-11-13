"""
Unit Tests for Daily Coach Twin Pre-Warming Engine (Sprint 8.4).
Validates:
  1. Deterministic reproducibility from (user_id, local_date, task_order, skill, mistake).
  2. Distinctness across task_order 1, 2, 3.
  3. Valid SymPy parsing of generated expressions and canonical LaTeX previews.
  4. Cold-start diagnostic 3-task mission synthesis.
  5. Batch execution latency < 25 ms for 3 tasks.
  6. $0.00 LLM cost invariant (zero external HTTP or LLM SDK imports).
"""
from __future__ import annotations

import ast
import re
import time
from pathlib import Path

import pytest
import sympy as sp

from app.daily_coach_prewarmer import (
    CoachTaskDescriptor,
    DailyCoachTwinPrewarmer,
    PrewarmedCoachTwin,
)
from app.parsing.safe_parser import parse_safe_expression


TWIN_ID_PATTERN = re.compile(r"^twin_84_[0-9a-f]{12}$")


def test_deterministic_reproducibility():
    desc = CoachTaskDescriptor(
        task_order=1,
        task_type="TRAP_REPAIR",
        target_skill_code="MATH.ALG.RATIONAL_EQ",
        target_mistake_code="ALG_DOMAIN_EXCLUSION_MISSED",
    )
    first = DailyCoachTwinPrewarmer.prewarm_task("user-111", "2026-11-13", desc)
    second = DailyCoachTwinPrewarmer.prewarm_task("user-111", "2026-11-13", desc)

    assert isinstance(first, PrewarmedCoachTwin)
    assert TWIN_ID_PATTERN.fullmatch(first.assigned_twin_id)
    assert first.assigned_twin_id == second.assigned_twin_id
    assert first.prompt_preview_latex == second.prompt_preview_latex
    assert first.canonical_solution_latex == second.canonical_solution_latex
    assert first.counterexample_hint_latex == second.counterexample_hint_latex
    assert first.step_diff_anchor_ast == second.step_diff_anchor_ast
    assert first.llm_tokens_used == 0


def test_distinctness_across_task_orders():
    descriptors = [
        CoachTaskDescriptor(order, "TRAP_REPAIR", "MATH.ALG.RATIONAL_EQ", "ALG_DOMAIN_EXCLUSION_MISSED")
        for order in (1, 2, 3)
    ]
    twins = DailyCoachTwinPrewarmer.prewarm_mission("user-222", "2026-11-13", descriptors)
    ids = {t.assigned_twin_id for t in twins}
    assert len(ids) == 3, "Each task_order must produce a distinct assigned_twin_id"


def test_valid_sympy_parsing_and_latex_synthesis():
    descriptors = [
        CoachTaskDescriptor(1, "TRAP_REPAIR", "MATH.ALG.RATIONAL_EQ", "ALG_DOMAIN_EXCLUSION_MISSED"),
        CoachTaskDescriptor(2, "SPACED_RETENTION", "MATH.ALG.QUADRATIC_DISCRIMINANT", "TRAP_SIGN_DISTRIB_NEG"),
        CoachTaskDescriptor(3, "EXAM_SYNTHESIS", "MATH.CALC.CHAIN_RULE_COMPOSITE", "CALC_CHAIN_RULE_OMITTED"),
    ]
    twins = DailyCoachTwinPrewarmer.prewarm_mission("user-333", "2026-11-13", descriptors)

    for twin in twins:
        parsed_expr = parse_safe_expression(twin.sympy_expr_str, variables=["x"])
        assert isinstance(parsed_expr, sp.Basic)
        rendered = sp.latex(parsed_expr)
        assert len(rendered) > 0
        assert len(twin.prompt_preview_latex) > 0
        assert len(twin.canonical_solution_latex) > 0
        assert len(twin.counterexample_hint_latex) > 0
        assert "sympy_srepr" in twin.step_diff_anchor_ast


def test_cold_start_diagnostic_mission_generation():
    cold_twins = DailyCoachTwinPrewarmer.generate_cold_start_mission("cold-user-001", "2026-11-13")
    assert len(cold_twins) == 3
    assert [t.task_order for t in cold_twins] == [1, 2, 3]
    assert [t.task_type for t in cold_twins] == ["TRAP_REPAIR", "SPACED_RETENTION", "EXAM_SYNTHESIS"]
    assert all(t.llm_tokens_used == 0 for t in cold_twins)


def test_batch_execution_latency_under_25ms():
    descriptors = [
        CoachTaskDescriptor(1, "TRAP_REPAIR", "MATH.ALG.LINEAR_INEQ", "INEQ_NEGATIVE_MULT_NO_FLIP"),
        CoachTaskDescriptor(2, "SPACED_RETENTION", "MATH.ALG.QUADRATIC_DISCRIMINANT", "TRAP_SIGN_DISTRIB_NEG"),
        CoachTaskDescriptor(3, "EXAM_SYNTHESIS", "MATH.CALC.CHAIN_RULE_COMPOSITE", "CALC_CHAIN_RULE_OMITTED"),
    ]
    # Warm-up one call so SymPy internal caches are initialized
    DailyCoachTwinPrewarmer.prewarm_mission("warmup-user", "2026-11-13", descriptors)

    start = time.perf_counter()
    twins = DailyCoachTwinPrewarmer.prewarm_mission("perf-user-001", "2026-11-13", descriptors)
    elapsed_ms = (time.perf_counter() - start) * 1000.0

    assert len(twins) == 3
    assert elapsed_ms < 25.0, f"3-task mission pre-warm took {elapsed_ms:.2f}ms (SLA < 25ms)"


def test_zero_llm_and_http_imports():
    source_path = Path(__file__).resolve().parents[1] / "app" / "daily_coach_prewarmer.py"
    tree = ast.parse(source_path.read_text(encoding="utf-8"))
    forbidden_modules = {"httpx", "requests", "urllib", "aiohttp", "openai", "anthropic", "google", "litellm"}

    imported = set()
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            for alias in node.names:
                imported.add(alias.name.split(".")[0])
        elif isinstance(node, ast.ImportFrom) and node.module:
            imported.add(node.module.split(".")[0])

    assert imported.isdisjoint(forbidden_modules), f"Forbidden external/LLM imports detected: {imported & forbidden_modules}"
