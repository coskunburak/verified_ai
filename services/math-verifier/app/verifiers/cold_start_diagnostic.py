"""Deterministic $0.00 LLM-cost verification and BKT prior initialization for Sprint 8.10 cold-start diagnostics."""
from __future__ import annotations

import re
from dataclasses import dataclass
from typing import Any

import sympy as s

COLD_START_SCHEMA_VERSION = "cold-start-v1"
BASE_COLD_START_PRIOR = 0.25
FAST_CORRECT_PRIOR = 0.65
SLOW_CORRECT_PRIOR = 0.50
TRAP_DISTRACTOR_PRIOR = 0.15
OTHER_INCORRECT_PRIOR = 0.20
FAST_RESPONSE_THRESHOLD_SECONDS = 60
SLOW_RESPONSE_THRESHOLD_SECONDS = 180
EVIDENCE_CALIBRATED_MIN = 3

CANONICAL_ITEM_ORDER: tuple[str, ...] = (
    "DIAG_ITEM_1_NUMBER_SENSE",
    "DIAG_ITEM_2_QUADRATIC_INEQUALITY",
    "DIAG_ITEM_3_FUNCTION_COMPOSITION",
)


@dataclass(frozen=True)
class ColdStartItemSpec:
    item_id: str
    skill_id: str
    prompt_latex: str
    correct_option_id: str
    trap_option_map: dict[str, str]
    trap_answers_normalized: dict[str, str]


ITEM_SPECS: dict[str, ColdStartItemSpec] = {
    "DIAG_ITEM_1_NUMBER_SENSE": ColdStartItemSpec(
        item_id="DIAG_ITEM_1_NUMBER_SENSE",
        skill_id="MATH.ARITHMETIC.ORDER_OF_OPERATIONS",
        prompt_latex=r"\frac{3}{4} - \left(-\frac{2}{5}\right) = ?",
        correct_option_id="A",
        trap_option_map={
            "B": "SIGN_INVERSION_TRAP",
            "C": "FRACTION_DENOMINATOR_TRAP",
        },
        trap_answers_normalized={
            "7/20": "SIGN_INVERSION_TRAP",
            "0.35": "SIGN_INVERSION_TRAP",
            "5/9": "FRACTION_DENOMINATOR_TRAP",
            "1/9": "FRACTION_DENOMINATOR_TRAP",
            "1/1": "FRACTION_DENOMINATOR_TRAP",
        },
    ),
    "DIAG_ITEM_2_QUADRATIC_INEQUALITY": ColdStartItemSpec(
        item_id="DIAG_ITEM_2_QUADRATIC_INEQUALITY",
        skill_id="MATH.EQUATIONS.INEQUALITIES_BASIC",
        prompt_latex=r"-x^2 + 5x - 6 \ge 0",
        correct_option_id="A",
        trap_option_map={
            "B": "INEQUALITY_POLARITY_TRAP",
            "C": "ROOT_BOUNDARY_TRAP",
        },
        trap_answers_normalized={
            "(-INF,2]U[3,INF)": "INEQUALITY_POLARITY_TRAP",
            "(-INFINITY,2]U[3,INFINITY)": "INEQUALITY_POLARITY_TRAP",
            "X<=2ORX>=3": "INEQUALITY_POLARITY_TRAP",
            "(2,3)": "ROOT_BOUNDARY_TRAP",
            "2<X<3": "ROOT_BOUNDARY_TRAP",
            "{2,3}": "ROOT_BOUNDARY_TRAP",
        },
    ),
    "DIAG_ITEM_3_FUNCTION_COMPOSITION": ColdStartItemSpec(
        item_id="DIAG_ITEM_3_FUNCTION_COMPOSITION",
        skill_id="MATH.FUNCTIONS.COMPOSITION",
        prompt_latex=r"f(x) = 2x + 3,\; g(x) = x^2 \implies (f \circ g)(3) = ?",
        correct_option_id="A",
        trap_option_map={
            "B": "COMPOSITION_ORDER_TRAP",
            "C": "COMPOSITION_LINEARITY_TRAP",
        },
        trap_answers_normalized={
            "81": "COMPOSITION_ORDER_TRAP",
            "24": "COMPOSITION_LINEARITY_TRAP",
            "18": "COMPOSITION_LINEARITY_TRAP",
        },
    ),
}

_FRACTION_LATEX_RE = re.compile(r"\\(?:d|t)?frac\{([^{}]+)\}\{([^{}]+)\}")
_SAFE_NUMBER_OR_FRAC_RE = re.compile(r"^[+-]?(?:\d+(?:\.\d+)?|\d+/\d+)$")


def _clean_math_token(raw: str | None) -> str:
    if raw is None:
        return ""
    text = raw.strip()
    if not text:
        return ""
    for _ in range(3):
        updated = _FRACTION_LATEX_RE.sub(r"(\1)/(\2)", text)
        if updated == text:
            break
        text = updated
    text = (
        text.replace("\\left", "")
        .replace("\\right", "")
        .replace("\\cup", "U")
        .replace("\\infty", "INF")
        .replace("\\infinity", "INF")
        .replace("∞", "INF")
        .replace("\\le", "<=")
        .replace("\\leq", "<=")
        .replace("≤", "<=")
        .replace("\\ge", ">=")
        .replace("\\geq", ">=")
        .replace("≥", ">=")
        .replace("\\in", "IN")
        .replace("$", "")
    )
    text = re.sub(r"\s+", "", text)
    if text.startswith("XIN") or (text.upper().startswith("XIN") and len(text) > 3 and text[3] in "[("):
        text = text[3:]
    return text.upper()


def _parse_bounded_rational(raw: str | None) -> s.Rational | None:
    cleaned = _clean_math_token(raw)
    if not cleaned:
        return None
    cleaned = cleaned.replace("(", "").replace(")", "")
    if not _SAFE_NUMBER_OR_FRAC_RE.fullmatch(cleaned):
        return None
    if "/" in cleaned:
        num_str, den_str = cleaned.split("/", 1)
        den = int(den_str)
        if den == 0:
            return None
        return s.Rational(int(num_str), den)
    return s.Rational(cleaned)


def _evaluate_item_match(spec: ColdStartItemSpec, answer: str | None) -> tuple[bool, str | None]:
    cleaned = _clean_math_token(answer)
    if not cleaned:
        return False, None

    if cleaned == spec.correct_option_id:
        return True, None
    if cleaned in spec.trap_option_map:
        return False, spec.trap_option_map[cleaned]

    if spec.item_id == "DIAG_ITEM_1_NUMBER_SENSE":
        val = _parse_bounded_rational(answer)
        if val is not None:
            if val == s.Rational(23, 20):
                return True, None
            if val == s.Rational(7, 20):
                return False, "SIGN_INVERSION_TRAP"
            if val in (s.Rational(5, 9), s.Rational(1, 9), s.Rational(1, 1)):
                return False, "FRACTION_DENOMINATOR_TRAP"
        return False, None

    if spec.item_id == "DIAG_ITEM_2_QUADRATIC_INEQUALITY":
        canonical_forms = {"[2,3]", "2<=X<=3", "XIN[2,3]"}
        if cleaned in canonical_forms:
            return True, None
        if cleaned in spec.trap_answers_normalized:
            return False, spec.trap_answers_normalized[cleaned]
        return False, None

    if spec.item_id == "DIAG_ITEM_3_FUNCTION_COMPOSITION":
        val = _parse_bounded_rational(answer)
        if val is not None:
            if val == s.Rational(21):
                return True, None
            if val == s.Rational(81):
                return False, "COMPOSITION_ORDER_TRAP"
            if val in (s.Rational(24), s.Rational(18)):
                return False, "COMPOSITION_LINEARITY_TRAP"
        return False, None

    return False, None


def verify_cold_start_item(
    item_id: str,
    answer: str | None,
    *,
    elapsed_seconds: int = 0,
    timed_out: bool = False,
    skipped: bool = False,
    evidence_count: int = 1,
) -> dict[str, Any]:
    if item_id not in ITEM_SPECS:
        raise ValueError(f"Unknown cold-start diagnostic item_id: {item_id}")
    if elapsed_seconds < 0:
        raise ValueError("elapsed_seconds must be non-negative")
    if evidence_count < 0:
        raise ValueError("evidence_count must be non-negative")

    spec = ITEM_SPECS[item_id]
    confidence_tier = "PRELIMINARY" if evidence_count < EVIDENCE_CALIBRATED_MIN else "CALIBRATED"
    cleaned = _clean_math_token(answer)

    if skipped or timed_out or not cleaned or elapsed_seconds > SLOW_RESPONSE_THRESHOLD_SECONDS:
        status = "TIMED_OUT" if (timed_out or elapsed_seconds > SLOW_RESPONSE_THRESHOLD_SECONDS) else "SKIPPED"
        return {
            "item_id": spec.item_id,
            "skill_id": spec.skill_id,
            "is_correct": False,
            "verdict": status,
            "detected_trap_archetype": None,
            "initial_prior": OTHER_INCORRECT_PRIOR,
            "elapsed_seconds": int(elapsed_seconds),
            "confidence_tier": confidence_tier,
            "evidence_count": int(evidence_count),
            "llm_tokens_used": 0,
            "deterministic": True,
        }

    is_correct, trap_code = _evaluate_item_match(spec, answer)
    if is_correct:
        prior = FAST_CORRECT_PRIOR if elapsed_seconds <= FAST_RESPONSE_THRESHOLD_SECONDS else SLOW_CORRECT_PRIOR
        verdict = "CORRECT_FAST" if elapsed_seconds <= FAST_RESPONSE_THRESHOLD_SECONDS else "CORRECT_SLOW"
        return {
            "item_id": spec.item_id,
            "skill_id": spec.skill_id,
            "is_correct": True,
            "verdict": verdict,
            "detected_trap_archetype": None,
            "initial_prior": prior,
            "elapsed_seconds": int(elapsed_seconds),
            "confidence_tier": confidence_tier,
            "evidence_count": int(evidence_count),
            "llm_tokens_used": 0,
            "deterministic": True,
        }

    if trap_code is not None:
        return {
            "item_id": spec.item_id,
            "skill_id": spec.skill_id,
            "is_correct": False,
            "verdict": "TRAP_DISTRACTOR",
            "detected_trap_archetype": trap_code,
            "initial_prior": TRAP_DISTRACTOR_PRIOR,
            "elapsed_seconds": int(elapsed_seconds),
            "confidence_tier": confidence_tier,
            "evidence_count": int(evidence_count),
            "llm_tokens_used": 0,
            "deterministic": True,
        }

    return {
        "item_id": spec.item_id,
        "skill_id": spec.skill_id,
        "is_correct": False,
        "verdict": "INCORRECT_OTHER",
        "detected_trap_archetype": None,
        "initial_prior": OTHER_INCORRECT_PRIOR,
        "elapsed_seconds": int(elapsed_seconds),
        "confidence_tier": confidence_tier,
        "evidence_count": int(evidence_count),
        "llm_tokens_used": 0,
        "deterministic": True,
    }


def verify_cold_start_diagnostic(
    responses: list[dict[str, Any]],
    *,
    evidence_counts: dict[str, int] | None = None,
) -> dict[str, Any]:
    if len(responses) != len(CANONICAL_ITEM_ORDER):
        raise ValueError(f"Expected {len(CANONICAL_ITEM_ORDER)} diagnostic responses, got {len(responses)}")

    by_id: dict[str, dict[str, Any]] = {}
    for entry in responses:
        item_id = entry.get("item_id")
        if item_id in by_id:
            raise ValueError(f"Duplicate cold-start diagnostic item_id: {item_id}")
        by_id[item_id] = entry

    missing = [iid for iid in CANONICAL_ITEM_ORDER if iid not in by_id]
    if missing:
        raise ValueError(f"Missing canonical cold-start item(s): {missing}")

    evidence_lookup = evidence_counts or {}
    item_results: list[dict[str, Any]] = []
    initialized_priors: dict[str, float] = {}
    detected_traps: list[str] = []
    correct_count = 0
    total_elapsed = 0
    any_preliminary = False

    for item_id in CANONICAL_ITEM_ORDER:
        entry = by_id[item_id]
        spec = ITEM_SPECS[item_id]
        ev_count = int(entry.get("evidence_count", evidence_lookup.get(spec.skill_id, 1)))
        res = verify_cold_start_item(
            item_id,
            entry.get("answer"),
            elapsed_seconds=int(entry.get("elapsed_seconds", 0)),
            timed_out=bool(entry.get("timed_out", False)),
            skipped=bool(entry.get("skipped", False)),
            evidence_count=ev_count,
        )
        item_results.append(res)
        initialized_priors[spec.skill_id] = res["initial_prior"]
        total_elapsed += res["elapsed_seconds"]
        if res["is_correct"]:
            correct_count += 1
        trap = res["detected_trap_archetype"]
        if trap and trap not in detected_traps:
            detected_traps.append(trap)
        if res["confidence_tier"] == "PRELIMINARY":
            any_preliminary = True

    if correct_count == 3:
        baseline_tier = "FOUNDATION_SOLID"
    elif correct_count >= 1:
        baseline_tier = "CALIBRATED_MIXED"
    else:
        baseline_tier = "FOUNDATION_REPAIR"

    confidence_tier = "PRELIMINARY" if any_preliminary else "CALIBRATED"
    calibration_mode = "EXPLORATION" if confidence_tier == "PRELIMINARY" else "ACTIVE_CALIBRATED"

    return {
        "schema_version": COLD_START_SCHEMA_VERSION,
        "status": "COMPLETED",
        "baseline_tier": baseline_tier,
        "correct_count": correct_count,
        "total_items": len(CANONICAL_ITEM_ORDER),
        "confidence_tier": confidence_tier,
        "calibration_mode": calibration_mode,
        "detected_trap_archetypes": detected_traps,
        "initialized_priors": initialized_priors,
        "item_results": item_results,
        "attempt_duration_seconds": total_elapsed,
        "llm_tokens_used": 0,
        "deterministic": True,
        "training_eligible": False,
    }
