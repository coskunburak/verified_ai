"""Deterministic $0.00 tests for Sprint 8.10 cold-start calibration verifier."""
from __future__ import annotations

import json

import pytest

from app.verifiers.cold_start_diagnostic import (
    CANONICAL_ITEM_ORDER,
    FAST_CORRECT_PRIOR,
    OTHER_INCORRECT_PRIOR,
    SLOW_CORRECT_PRIOR,
    TRAP_DISTRACTOR_PRIOR,
    verify_cold_start_diagnostic,
    verify_cold_start_item,
)


def _baseline_responses(answers: tuple[str | None, str | None, str | None], elapsed: tuple[int, int, int] = (30, 30, 30)):
    return [
        {
            "item_id": CANONICAL_ITEM_ORDER[idx],
            "answer": answers[idx],
            "elapsed_seconds": elapsed[idx],
            "timed_out": False,
            "skipped": False,
        }
        for idx in range(3)
    ]


def test_fast_and_slow_correct_priors_with_preliminary_confidence():
    fast = verify_cold_start_diagnostic(_baseline_responses(("A", "A", "A"), elapsed=(45, 60, 12)))
    assert fast["baseline_tier"] == "FOUNDATION_SOLID"
    assert fast["correct_count"] == 3
    assert fast["confidence_tier"] == "PRELIMINARY"
    assert fast["calibration_mode"] == "EXPLORATION"
    assert fast["llm_tokens_used"] == 0
    assert fast["deterministic"] is True
    assert fast["training_eligible"] is False
    assert fast["detected_trap_archetypes"] == []
    assert fast["initialized_priors"] == {
        "MATH.ARITHMETIC.ORDER_OF_OPERATIONS": FAST_CORRECT_PRIOR,
        "MATH.EQUATIONS.INEQUALITIES_BASIC": FAST_CORRECT_PRIOR,
        "MATH.FUNCTIONS.COMPOSITION": FAST_CORRECT_PRIOR,
    }

    slow = verify_cold_start_diagnostic(_baseline_responses((r"\frac{23}{20}", "2 <= x <= 3", "21"), elapsed=(61, 120, 180)))
    assert slow["baseline_tier"] == "FOUNDATION_SOLID"
    assert slow["initialized_priors"] == {
        "MATH.ARITHMETIC.ORDER_OF_OPERATIONS": SLOW_CORRECT_PRIOR,
        "MATH.EQUATIONS.INEQUALITIES_BASIC": SLOW_CORRECT_PRIOR,
        "MATH.FUNCTIONS.COMPOSITION": SLOW_CORRECT_PRIOR,
    }


@pytest.mark.parametrize(
    "item_id,answer,expected_trap",
    [
        ("DIAG_ITEM_1_NUMBER_SENSE", "B", "SIGN_INVERSION_TRAP"),
        ("DIAG_ITEM_1_NUMBER_SENSE", "7/20", "SIGN_INVERSION_TRAP"),
        ("DIAG_ITEM_1_NUMBER_SENSE", "C", "FRACTION_DENOMINATOR_TRAP"),
        ("DIAG_ITEM_1_NUMBER_SENSE", "5/9", "FRACTION_DENOMINATOR_TRAP"),
        ("DIAG_ITEM_2_QUADRATIC_INEQUALITY", "B", "INEQUALITY_POLARITY_TRAP"),
        ("DIAG_ITEM_2_QUADRATIC_INEQUALITY", "x <= 2 or x >= 3", "INEQUALITY_POLARITY_TRAP"),
        ("DIAG_ITEM_2_QUADRATIC_INEQUALITY", "C", "ROOT_BOUNDARY_TRAP"),
        ("DIAG_ITEM_2_QUADRATIC_INEQUALITY", "(2, 3)", "ROOT_BOUNDARY_TRAP"),
        ("DIAG_ITEM_3_FUNCTION_COMPOSITION", "B", "COMPOSITION_ORDER_TRAP"),
        ("DIAG_ITEM_3_FUNCTION_COMPOSITION", "81", "COMPOSITION_ORDER_TRAP"),
        ("DIAG_ITEM_3_FUNCTION_COMPOSITION", "C", "COMPOSITION_LINEARITY_TRAP"),
        ("DIAG_ITEM_3_FUNCTION_COMPOSITION", "24", "COMPOSITION_LINEARITY_TRAP"),
    ],
)
def test_distractor_trap_classification(item_id: str, answer: str, expected_trap: str):
    result = verify_cold_start_item(item_id, answer, elapsed_seconds=40, evidence_count=1)
    assert result["is_correct"] is False
    assert result["detected_trap_archetype"] == expected_trap
    assert result["initial_prior"] == TRAP_DISTRACTOR_PRIOR
    assert result["confidence_tier"] == "PRELIMINARY"
    assert result["llm_tokens_used"] == 0
    assert result["deterministic"] is True


def test_mixed_foundation_repair_skipped_and_timeout_priors():
    mixed = verify_cold_start_diagnostic(_baseline_responses(("23/20", "B", "D"), elapsed=(35, 50, 90)))
    assert mixed["baseline_tier"] == "CALIBRATED_MIXED"
    assert mixed["correct_count"] == 1
    assert mixed["detected_trap_archetypes"] == ["INEQUALITY_POLARITY_TRAP"]
    assert mixed["initialized_priors"]["MATH.ARITHMETIC.ORDER_OF_OPERATIONS"] == FAST_CORRECT_PRIOR
    assert mixed["initialized_priors"]["MATH.EQUATIONS.INEQUALITIES_BASIC"] == TRAP_DISTRACTOR_PRIOR
    assert mixed["initialized_priors"]["MATH.FUNCTIONS.COMPOSITION"] == OTHER_INCORRECT_PRIOR

    repair = verify_cold_start_diagnostic(
        [
            {"item_id": CANONICAL_ITEM_ORDER[0], "answer": "", "elapsed_seconds": 10, "skipped": True},
            {"item_id": CANONICAL_ITEM_ORDER[1], "answer": "A", "elapsed_seconds": 181, "timed_out": True},
            {"item_id": CANONICAL_ITEM_ORDER[2], "answer": "D", "elapsed_seconds": 40, "timed_out": False},
        ]
    )
    assert repair["baseline_tier"] == "FOUNDATION_REPAIR"
    assert repair["correct_count"] == 0
    assert repair["initialized_priors"] == {
        "MATH.ARITHMETIC.ORDER_OF_OPERATIONS": OTHER_INCORRECT_PRIOR,
        "MATH.EQUATIONS.INEQUALITIES_BASIC": OTHER_INCORRECT_PRIOR,
        "MATH.FUNCTIONS.COMPOSITION": OTHER_INCORRECT_PRIOR,
    }


def test_calibrated_tier_threshold_and_100_run_determinism():
    payload = _baseline_responses(("23/20", "(2, 3)", "81"), elapsed=(55, 70, 35))
    first = verify_cold_start_diagnostic(
        payload,
        evidence_counts={
            "MATH.ARITHMETIC.ORDER_OF_OPERATIONS": 3,
            "MATH.EQUATIONS.INEQUALITIES_BASIC": 4,
            "MATH.FUNCTIONS.COMPOSITION": 5,
        },
    )
    assert first["confidence_tier"] == "CALIBRATED"
    assert first["calibration_mode"] == "ACTIVE_CALIBRATED"

    baseline_json = json.dumps(first, sort_keys=True)
    for _ in range(100):
        replay = verify_cold_start_diagnostic(
            payload,
            evidence_counts={
                "MATH.ARITHMETIC.ORDER_OF_OPERATIONS": 3,
                "MATH.EQUATIONS.INEQUALITIES_BASIC": 4,
                "MATH.FUNCTIONS.COMPOSITION": 5,
            },
        )
        assert json.dumps(replay, sort_keys=True) == baseline_json
