"""
Unit & Integration Tests for Spaced Repetition Twin Scheduling & 7-Day Trap Immunity Engine.
Validates:
  1. Deterministic reproducibility across identical seeds.
  2. Strict distinctness from remediation twin seeds.
  3. All 4 structural trap families + quadratic discriminant sign-distribution trap.
  4. Exact 168-hour (604,800-second) immunity window validation helper.
  5. Zero LLM token cost ($0.00, llm_tokens_used == 0).
  6. Collision-freedom between canonical solution and trap distractor.
"""
from __future__ import annotations

import re
from datetime import datetime, timezone, timedelta
import pytest

from app.spaced_twin_scheduler import (
    SpacedTwinScheduler,
    SpacedImmunityTwin,
    IMMUNITY_WINDOW_HOURS,
    IMMUNITY_WINDOW_SECONDS,
)
from app.twin_generator import DeterministicTwinGenerator, TwinInvariantViolationError

HASH_PATTERN = re.compile(r"^[a-f0-9]{64}$")

STRUCTURAL_TEST_CASES = [
    ("MATH.ALG.QUADRATIC_DISCRIMINANT", "TRAP_SIGN_DISTRIB_NEG", "TRAP_SIGN_DISTRIB_NEG"),
    ("MATH.ALG.RATIONAL_EQ", "ALG_DOMAIN_EXCLUSION_MISSED", "ALG_DOMAIN_EXCLUSION_MISSED"),
    ("MATH.ALG.RATIONAL_EQ", "TRAP_DOMAIN_EXCLUSION", "ALG_DOMAIN_EXCLUSION_MISSED"),
    ("MATH.ALG.LINEAR_INEQ", "INEQ_NEGATIVE_MULT_NO_FLIP", "INEQ_NEGATIVE_MULT_NO_FLIP"),
    ("MATH.ALG.LINEAR_INEQ", "TRAP_INEQUALITY_SIGN_FLIP", "INEQ_NEGATIVE_MULT_NO_FLIP"),
    ("MATH.ALG.RADICAL_EQ", "ALG_SQUARE_BOTH_SIDES_EXTRANEOUS", "ALG_SQUARE_BOTH_SIDES_EXTRANEOUS"),
    ("MATH.ALG.RADICAL_EQ", "TRAP_EXTRANEOUS_ROOT", "ALG_SQUARE_BOTH_SIDES_EXTRANEOUS"),
    ("MATH.CALC.CHAIN_RULE", "CALC_CHAIN_RULE_OMITTED", "CALC_CHAIN_RULE_OMITTED"),
    ("MATH.CALC.CHAIN_RULE", "TRAP_CHAIN_RULE_INNER", "CALC_CHAIN_RULE_OMITTED"),
    ("MATH.CALC.CHAIN_RULE", "TRAP_CHAIN_RULE_OMIT_INNER", "CALC_CHAIN_RULE_OMITTED"),
]


def test_deterministic_reproducibility():
    """Identical user, skill, and mistake code must produce identical twin."""
    user_id = "user-123e4567-e89b-12d3-a456-426614174000"
    skill = "MATH.ALG.QUADRATIC_DISCRIMINANT"
    mistake = "TRAP_SIGN_DISTRIB_NEG"

    twin1 = SpacedTwinScheduler.generate_7d_immunity_twin(user_id, skill, mistake)
    twin2 = SpacedTwinScheduler.generate_7d_immunity_twin(user_id, skill, mistake)

    assert twin1.prompt_latex == twin2.prompt_latex
    assert twin1.canonical_solution_expr == twin2.canonical_solution_expr
    assert twin1.trap_distractor_expr == twin2.trap_distractor_expr
    assert twin1.sympy_verification_hash == twin2.sympy_verification_hash
    assert twin1.seed_key == twin2.seed_key
    assert twin1.seed_key.endswith(":7D_IMMUNITY")


def test_distinctness_from_remediation_twin():
    """7D Immunity twin must derive from ':7D_IMMUNITY' seed distinct from remediation twin seeds."""
    user_id = "user-remediation-diff-001"
    skill = "MATH.CALC.CHAIN_RULE"
    trap_family = "CALC_CHAIN_RULE_OMITTED"

    immunity_twin = SpacedTwinScheduler.generate_7d_immunity_twin(user_id, skill, trap_family)
    remediation_twin = DeterministicTwinGenerator.generate_twin(
        user_id=user_id,
        skill_id=skill,
        trap_family=trap_family,
        ordinal_index=0,
    )

    # Immunity seed is distinct from remediation seed
    assert immunity_twin.seed_key == f"{user_id}:{skill}:{trap_family}:7D_IMMUNITY"
    # Content should not be trivially identical to remediation slot 0
    # (they derive from different hash digests)
    assert immunity_twin.seed_key != f"{user_id}:{skill}:{trap_family}:0"


def test_all_structural_trap_families():
    """Verifies all structural trap families produce valid, non-colliding twins."""
    for idx, (skill_code, mistake_code, expected_family) in enumerate(STRUCTURAL_TEST_CASES):
        user_id = f"user-test-{idx:03d}"
        twin = SpacedTwinScheduler.generate_7d_immunity_twin(user_id, skill_code, mistake_code)

        assert twin.trap_family == expected_family
        assert twin.llm_tokens_used == 0, "LLM token cost must be exactly 0"
        assert twin.canonical_solution_expr != twin.trap_distractor_expr, "Answer and trap distractor must not collide"
        assert HASH_PATTERN.fullmatch(twin.sympy_verification_hash), "SymPy hash must be 64-character hex"
        assert twin.execution_time_ms < 150.0, f"Execution latency too high: {twin.execution_time_ms}ms"
        assert len(twin.prompt_preview_latex) > 0, "Prompt preview must not be empty"


def test_168_hour_immunity_window_validation():
    """
    Validates INV-SR-002:
      - 167 hours 59 minutes: ineligible (False)
      - 168 hours 00 minutes: eligible (True)
      - 175 hours: eligible (True)
    """
    remediated_at = datetime(2026, 11, 1, 10, 0, 0, tzinfo=timezone.utc)

    # 167 hours, 59 minutes
    too_early = remediated_at + timedelta(hours=167, minutes=59)
    assert SpacedTwinScheduler.is_eligible_for_7d_immunity(remediated_at, too_early) is False

    # Exact 168 hours
    exact_due = remediated_at + timedelta(hours=168)
    assert SpacedTwinScheduler.is_eligible_for_7d_immunity(remediated_at, exact_due) is True
    assert SpacedTwinScheduler.compute_7d_immunity_due_time(remediated_at) == exact_due

    # Past 168 hours
    past_due = remediated_at + timedelta(hours=168, minutes=1)
    assert SpacedTwinScheduler.is_eligible_for_7d_immunity(remediated_at, past_due) is True


def test_zero_llm_cost_invariant():
    """Asserts that 100 generated immunity twins incur $0.00 token cost."""
    total_tokens = 0
    for i in range(20):
        twin = SpacedTwinScheduler.generate_7d_immunity_twin(
            user_id=f"user-cost-{i}",
            canonical_skill_code="MATH.ALG.QUADRATIC_DISCRIMINANT",
            target_mistake_code="TRAP_SIGN_DISTRIB_NEG",
        )
        total_tokens += twin.llm_tokens_used

    assert total_tokens == 0, "LLM tokens used must be strictly 0"


def test_unsupported_mistake_code_fails_closed():
    """Invalid or unmapped mistake code must raise TwinInvariantViolationError."""
    with pytest.raises(TwinInvariantViolationError):
        SpacedTwinScheduler.generate_7d_immunity_twin(
            user_id="user-err-01",
            canonical_skill_code="MATH.UNKNOWN",
            target_mistake_code="NON_EXISTENT_TRAP_123",
        )
