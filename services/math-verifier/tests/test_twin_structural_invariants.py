from __future__ import annotations

import re
import pytest

from app.answer_evaluator import evaluate_practice_submission
from app.twin_generator import (
    DeterministicTwinGenerator,
    TwinInvariantViolationError,
)

TRAP_FAMILIES = [
    ("ALG_DOMAIN_EXCLUSION_MISSED", "MATH.ALG.RATIONAL_EQ"),
    ("INEQ_NEGATIVE_MULT_NO_FLIP", "MATH.ALG.LINEAR_INEQ"),
    ("ALG_SQUARE_BOTH_SIDES_EXTRANEOUS", "MATH.ALG.RADICAL_EQ"),
    ("CALC_CHAIN_RULE_OMITTED", "MATH.CALC.CHAIN_RULE"),
]

HASH_PATTERN = re.compile(r"^[a-f0-9]{64}$")


def test_twenty_five_seeds_across_all_four_families():
    """
    Test 25 deterministic seeds across all 4 trap families (100 generated twins total).
    Assert:
      1. Every twin completes in < 150 ms.
      2. Produces valid 64-hex sympy_verification_hash.
      3. S_true != S_trap.
      4. Correctly verifies canonical solution and detects trap distractor.
    """
    total_twins = 0

    for trap_family, skill_id in TRAP_FAMILIES:
        for seed_idx in range(25):
            user_id = f"user-{seed_idx:04d}-uuid"
            twin = DeterministicTwinGenerator.generate_twin(
                user_id=user_id,
                skill_id=skill_id,
                trap_family=trap_family,
                ordinal_index=seed_idx,
            )

            total_twins += 1

            # SLA assertion
            assert twin.execution_time_ms < 150.0, f"Latency SLA violated: {twin.execution_time_ms}ms"

            # Hash assertion
            assert HASH_PATTERN.fullmatch(twin.sympy_verification_hash), (
                f"Invalid hash format: {twin.sympy_verification_hash}"
            )

            # Invariant assertion S_true != S_trap
            assert twin.canonical_solution_expr != twin.trap_distractor_expr, (
                "Canonical solution and trap distractor must not be identical"
            )

            # Verification of canonical answer
            eval_correct = evaluate_practice_submission(
                trap_family=twin.trap_family,
                prompt_latex=twin.prompt_latex,
                canonical_solution_expr=twin.canonical_solution_expr,
                trap_distractor_expr=twin.trap_distractor_expr,
                submitted_answer_latex=twin.canonical_solution_expr,
            )
            assert eval_correct["is_correct"] is True, f"Failed on canonical answer for {trap_family}"
            assert eval_correct["trap_triggered"] is False

            # Detection of trap distractor
            eval_trap = evaluate_practice_submission(
                trap_family=twin.trap_family,
                prompt_latex=twin.prompt_latex,
                canonical_solution_expr=twin.canonical_solution_expr,
                trap_distractor_expr=twin.trap_distractor_expr,
                submitted_answer_latex=twin.trap_distractor_expr,
            )
            assert eval_trap["is_correct"] is False, f"Trap answer falsely accepted as correct for {trap_family}"
            assert eval_trap["trap_triggered"] is True, f"Failed to detect trap trigger for {trap_family}"
            assert eval_trap["trap_diagnostic_key"] is not None

    assert total_twins == 100, f"Expected 100 total twins, got {total_twins}"


def test_unsupported_trap_family_fails_closed():
    """
    Test fail-closed behavior on invalid or corrupted trap family.
    """
    with pytest.raises(TwinInvariantViolationError):
        DeterministicTwinGenerator.generate_twin(
            user_id="user-001",
            skill_id="MATH.UNKNOWN",
            trap_family="NON_EXISTENT_TRAP_FAMILY",
            ordinal_index=0,
        )


def test_empty_or_corrupt_submission_handling():
    """
    Test that empty, whitespace, or invalid submissions do not throw unhandled exceptions.
    """
    res = evaluate_practice_submission(
        trap_family="ALG_DOMAIN_EXCLUSION_MISSED",
        prompt_latex="\\frac{x}{x-2} = 3",
        canonical_solution_expr="3",
        trap_distractor_expr="{2, 3}",
        submitted_answer_latex=None,
    )
    assert res["is_correct"] is False
    assert res["trap_triggered"] is False

    res_empty = evaluate_practice_submission(
        trap_family="ALG_DOMAIN_EXCLUSION_MISSED",
        prompt_latex="\\frac{x}{x-2} = 3",
        canonical_solution_expr="3",
        trap_distractor_expr="{2, 3}",
        submitted_answer_latex="   ",
    )
    assert res_empty["is_correct"] is False
    assert res_empty["trap_triggered"] is False

    res_garbage = evaluate_practice_submission(
        trap_family="CALC_CHAIN_RULE_OMITTED",
        prompt_latex="f(x) = (2x+1)^3",
        canonical_solution_expr="6(2x+1)^2",
        trap_distractor_expr="3(2x+1)^2",
        submitted_answer_latex="invalid!#@$math",
    )
    assert res_garbage["is_correct"] is False
    assert res_garbage["trap_triggered"] is False
