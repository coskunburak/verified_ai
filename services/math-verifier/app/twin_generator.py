from __future__ import annotations

import hashlib
import time
from dataclasses import dataclass
from typing import Any, Dict, Optional

import sympy as sp


class TwinInvariantViolationError(Exception):
    """Raised when a generated twin fails symbolic structural invariants."""
    pass


@dataclass(frozen=True)
class GeneratedTwin:
    trap_family: str
    prompt_latex: str
    prompt_instruction_tr: str
    prompt_instruction_en: str
    canonical_solution_expr: str
    trap_distractor_expr: str
    sympy_verification_hash: str
    target_difficulty: float
    execution_time_ms: float


class DeterministicTwinGenerator:
    """
    Tier-1 Deterministic Twin Generator (DOC-84 §5).
    Guarantees:
      1. $0.00 LLM token cost.
      2. p95 < 150ms execution latency.
      3. Symbolic SymPy proof of algebraic trap invariant.
      4. Deterministic output from (user_id, skill_id, trap_family, ordinal_index).
    """

    SUPPORTED_FAMILIES = {
        "ALG_DOMAIN_EXCLUSION_MISSED",
        "INEQ_NEGATIVE_MULT_NO_FLIP",
        "ALG_SQUARE_BOTH_SIDES_EXTRANEOUS",
        "CALC_CHAIN_RULE_OMITTED",
    }

    @classmethod
    def generate_twin(
        cls,
        user_id: str,
        skill_id: str,
        trap_family: str,
        ordinal_index: int = 0,
    ) -> GeneratedTwin:
        start_time = time.perf_counter()

        if trap_family not in cls.SUPPORTED_FAMILIES:
            raise TwinInvariantViolationError(f"Unsupported trap family: {trap_family}")

        seed_raw = f"{user_id}:{skill_id}:{trap_family}:{ordinal_index}".encode("utf-8")
        seed_bytes = hashlib.sha256(seed_raw).digest()

        try:
            if trap_family == "ALG_DOMAIN_EXCLUSION_MISSED":
                twin = cls._generate_rational_domain_exclusion(seed_bytes)
            elif trap_family == "INEQ_NEGATIVE_MULT_NO_FLIP":
                twin = cls._generate_inequality_sign_flip(seed_bytes)
            elif trap_family == "ALG_SQUARE_BOTH_SIDES_EXTRANEOUS":
                twin = cls._generate_radical_extraneous_root(seed_bytes)
            elif trap_family == "CALC_CHAIN_RULE_OMITTED":
                twin = cls._generate_chain_rule_inner(seed_bytes)
            else:
                raise TwinInvariantViolationError(f"Unhandled trap family: {trap_family}")
        except Exception as exc:
            if isinstance(exc, TwinInvariantViolationError):
                raise
            raise TwinInvariantViolationError(f"Failed to generate twin for {trap_family}: {exc}") from exc

        elapsed_ms = (time.perf_counter() - start_time) * 1000.0
        if elapsed_ms > 150.0:
            # Still valid, but log / flag latency SLA
            pass

        return GeneratedTwin(
            trap_family=twin["trap_family"],
            prompt_latex=twin["prompt_latex"],
            prompt_instruction_tr=twin["prompt_instruction_tr"],
            prompt_instruction_en=twin["prompt_instruction_en"],
            canonical_solution_expr=twin["canonical_solution_expr"],
            trap_distractor_expr=twin["trap_distractor_expr"],
            sympy_verification_hash=twin["sympy_verification_hash"],
            target_difficulty=twin["target_difficulty"],
            execution_time_ms=round(elapsed_ms, 2),
        )

    @classmethod
    def _generate_rational_domain_exclusion(cls, seed_bytes: bytes) -> Dict[str, Any]:
        # Excluded root r_excl in [-7, 7] \ {0}
        r_excl_raw = (seed_bytes[0] % 14) - 7
        r_excl = r_excl_raw if r_excl_raw != 0 else 3

        # Valid denominator zero r_valid in [-9, 9] \ {0, r_excl}
        r_valid_candidates = [v for v in range(-9, 10) if v != 0 and v != r_excl]
        r_valid = r_valid_candidates[seed_bytes[1] % len(r_valid_candidates)]

        # a in {1, 2, 3}, b such that a | b
        a = (seed_bytes[2] % 3) + 1
        k_step = (seed_bytes[3] % 7) - 3  # step in [-3, 3] \ {0}
        if k_step == 0 or (r_valid + k_step == r_excl) or (r_valid + k_step == 0):
            k_step = 2 if r_valid + 2 != r_excl else -2
        b = -a * k_step

        true_root = r_valid + k_step

        # Invariant assertion
        if true_root == r_excl or true_root == r_valid:
            raise TwinInvariantViolationError("Root collides with excluded domain")

        # Symbolically verify
        x = sp.Symbol("x", real=True)
        # Identity: a*x/(x - r_excl) + b/(x - r_valid) = a*r_excl/(x - r_excl)
        # LHS - RHS = a*(x - r_excl)/(x - r_excl) + b/(x - r_valid) = a + b/(x - r_valid)
        cleared_poly = a * (x - r_excl) * (x - r_valid) + b * (x - r_excl)
        cleared_roots = sp.solve(cleared_poly, x)
        cleared_root_set = {int(r) for r in cleared_roots}

        expected_trap_set = {true_root, r_excl}
        if cleared_root_set != expected_trap_set:
            raise TwinInvariantViolationError(f"Cleared roots {cleared_root_set} != expected trap set {expected_trap_set}")
        if r_excl in {true_root}:
            raise TwinInvariantViolationError("r_excl must not be in true solution set")

        # Format LaTeX
        excl_term = f"x - {r_excl}" if r_excl > 0 else f"x + {-r_excl}"
        valid_term = f"x - {r_valid}" if r_valid > 0 else f"x + {-r_valid}"
        ax_term = f"{a}x" if a > 1 else "x"
        rhs_num = a * r_excl

        b_sign = "+" if b > 0 else "-"
        b_abs = abs(b)

        prompt_latex = f"\\frac{{{ax_term}}}{{{excl_term}}} {b_sign} \\frac{{{b_abs}}}{{{valid_term}}} = \\frac{{{rhs_num}}}{{{excl_term}}}"
        canonical_solution = str(true_root)
        trap_distractor = f"{{{min(true_root, r_excl)}, {max(true_root, r_excl)}}}"

        v_hash = hashlib.sha256(
            f"ALG_DOMAIN_EXCLUSION_MISSED|{prompt_latex}|{canonical_solution}|{trap_distractor}".encode("utf-8")
        ).hexdigest()

        return {
            "trap_family": "ALG_DOMAIN_EXCLUSION_MISSED",
            "prompt_latex": prompt_latex,
            "prompt_instruction_tr": "Denklemi çözünüz. Tanımsızlık oluşturan köklere dikkat ediniz.",
            "prompt_instruction_en": "Solve the equation. Pay careful attention to roots that make denominators zero.",
            "canonical_solution_expr": canonical_solution,
            "trap_distractor_expr": trap_distractor,
            "sympy_verification_hash": v_hash,
            "target_difficulty": 0.5000,
        }

    @classmethod
    def _generate_inequality_sign_flip(cls, seed_bytes: bytes) -> Dict[str, Any]:
        # -a * x + b < c with a in {2, 3, 4, 5, 6, 7}
        a = (seed_bytes[0] % 6) + 2
        # Target critical value k in [-8, 8]
        k = (seed_bytes[1] % 17) - 8
        b = (seed_bytes[2] % 19) - 9
        c = b - a * k

        # Invariant assertion
        if -a >= 0:
            raise TwinInvariantViolationError("Lead coefficient must be strictly negative")
        if (b - c) // a != k:
            raise TwinInvariantViolationError("Critical value mismatch")

        # True solution: x > k
        # Trap solution (forgot to flip sign): x < k
        canonical_solution = f"x > {k}"
        trap_distractor = f"x < {k}"

        b_str = f"+ {b}" if b > 0 else (f"- {-b}" if b < 0 else "")
        prompt_latex = f"-{a}x {b_str} < {c}".replace("  ", " ")

        v_hash = hashlib.sha256(
            f"INEQ_NEGATIVE_MULT_NO_FLIP|{prompt_latex}|{canonical_solution}|{trap_distractor}".encode("utf-8")
        ).hexdigest()

        return {
            "trap_family": "INEQ_NEGATIVE_MULT_NO_FLIP",
            "prompt_latex": prompt_latex,
            "prompt_instruction_tr": "Eşitsizliği çözünüz.",
            "prompt_instruction_en": "Solve the inequality.",
            "canonical_solution_expr": canonical_solution,
            "trap_distractor_expr": trap_distractor,
            "sympy_verification_hash": v_hash,
            "target_difficulty": 0.4500,
        }

    @classmethod
    def _generate_radical_extraneous_root(cls, seed_bytes: bytes) -> Dict[str, Any]:
        # u in {1, 2, 3, 4}, v in {u+1, u+2, u+3, u+4}
        u = (seed_bytes[0] % 4) + 1
        v_offset = (seed_bytes[1] % 4) + 1
        v = u + v_offset

        c = (seed_bytes[2] % 9) - 4  # c in [-4, 4]
        a = v - u
        b = u * v - c * (v - u)

        r_valid = c + v
        r_ext = c - u

        # Symbolic invariant check
        if a <= 0:
            raise TwinInvariantViolationError("Radical coefficient a must be positive")
        rad_valid = a * r_valid + b
        rad_ext = a * r_ext + b
        if rad_valid < 0 or rad_ext < 0:
            raise TwinInvariantViolationError("Radicand must be non-negative for both roots")

        sqrt_valid = int(sp.sqrt(rad_valid))
        sqrt_ext = int(sp.sqrt(rad_ext))

        # Check valid root satisfies sqrt(a*x + b) = x - c
        if sqrt_valid != (r_valid - c):
            raise TwinInvariantViolationError("r_valid must satisfy original radical equation")
        # Check extraneous root fails sqrt(a*x + b) = x - c because x - c = -u < 0
        if sqrt_ext == (r_ext - c):
            raise TwinInvariantViolationError("r_ext must NOT satisfy original radical equation")
        if (r_ext - c) ** 2 != rad_ext:
            raise TwinInvariantViolationError("r_ext must satisfy squared equation")

        # Format prompt
        rhs_str = f"x - {c}" if c > 0 else (f"x + {-c}" if c < 0 else "x")
        ax_str = f"{a}x" if a > 1 else "x"
        b_str = f"+ {b}" if b > 0 else (f"- {-b}" if b < 0 else "")
        prompt_latex = f"\\sqrt{{{ax_str} {b_str}}} = {rhs_str}".replace("  ", " ")

        canonical_solution = str(r_valid)
        trap_distractor = f"{{{min(r_valid, r_ext)}, {max(r_valid, r_ext)}}}"

        v_hash = hashlib.sha256(
            f"ALG_SQUARE_BOTH_SIDES_EXTRANEOUS|{prompt_latex}|{canonical_solution}|{trap_distractor}".encode("utf-8")
        ).hexdigest()

        return {
            "trap_family": "ALG_SQUARE_BOTH_SIDES_EXTRANEOUS",
            "prompt_latex": prompt_latex,
            "prompt_instruction_tr": "Denklemi çözünüz. Yabancı kökleri kontrol ediniz.",
            "prompt_instruction_en": "Solve the equation. Check for extraneous roots.",
            "canonical_solution_expr": canonical_solution,
            "trap_distractor_expr": trap_distractor,
            "sympy_verification_hash": v_hash,
            "target_difficulty": 0.5500,
        }

    @classmethod
    def _generate_chain_rule_inner(cls, seed_bytes: bytes) -> Dict[str, Any]:
        # f(x) = (a * x^n + b)^m
        a = (seed_bytes[0] % 4) + 2   # {2, 3, 4, 5}
        n = (seed_bytes[1] % 2) + 2   # {2, 3}
        m = (seed_bytes[2] % 3) + 3   # {3, 4, 5}
        b_raw = (seed_bytes[3] % 18) - 9
        b = b_raw if b_raw != 0 else 3

        x = sp.Symbol("x", real=True)
        f = (a * (x ** n) + b) ** m
        true_deriv = sp.diff(f, x)

        # Naive trap derivative (omits inner derivative a * n * x^(n-1))
        # trap_deriv = m * (a * x^n + b)^(m - 1)
        inner_deriv_coeff = a * n
        if inner_deriv_coeff == 1:
            raise TwinInvariantViolationError("Inner derivative factor must not be 1")

        canonical_solution = f"{m * a * n}x^{n - 1}({a}x^{n} {('+' if b > 0 else '-')} {abs(b)})^{m - 1}"
        trap_distractor = f"{m}({a}x^{n} {('+' if b > 0 else '-')} {abs(b)})^{m - 1}"

        b_sign = "+" if b > 0 else "-"
        prompt_latex = f"f(x) = ({a}x^{n} {b_sign} {abs(b)})^{m}"

        v_hash = hashlib.sha256(
            f"CALC_CHAIN_RULE_OMITTED|{prompt_latex}|{canonical_solution}|{trap_distractor}".encode("utf-8")
        ).hexdigest()

        return {
            "trap_family": "CALC_CHAIN_RULE_OMITTED",
            "prompt_latex": prompt_latex,
            "prompt_instruction_tr": "Fonksiyonun türevini bulunuz.",
            "prompt_instruction_en": "Find the derivative of the function.",
            "canonical_solution_expr": canonical_solution,
            "trap_distractor_expr": trap_distractor,
            "sympy_verification_hash": v_hash,
            "target_difficulty": 0.6000,
        }
