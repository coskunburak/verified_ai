from __future__ import annotations

import re
from typing import Any, Dict, Optional, Set
import sympy as sp

DIAGNOSTIC_FEEDBACK: Dict[str, Dict[str, str]] = {
    "ALG_DOMAIN_EXCLUSION_MISSED": {
        "key": "TRAP_DOMAIN_EXCLUSION",
        "tr": "Bulunan köklerden biri paydayı sıfır yaparak ifadeyi tanımsız kılmaktadır. Tanım kümesi kısıtlamalarını kontrol ediniz.",
        "en": "One of the roots causes division by zero in the original equation. Check domain exclusions.",
    },
    "INEQ_NEGATIVE_MULT_NO_FLIP": {
        "key": "TRAP_INEQUALITY_SIGN_FLIP",
        "tr": "Eşitsizlik negatif bir sayıya bölünürken veya çarpılırken eşitsizlik yönü tersine dönmelidir.",
        "en": "When multiplying or dividing an inequality by a negative value, the inequality sign must flip.",
    },
    "ALG_SQUARE_BOTH_SIDES_EXTRANEOUS": {
        "key": "TRAP_EXTRANEOUS_ROOT",
        "tr": "Her iki tarafın karesi alındığında yabancı kökler ortaya çıkabilir. Bulunan kökleri orijinal denklemde deneyiniz.",
        "en": "Squaring both sides can introduce extraneous roots. Verify candidate roots in the original radical equation.",
    },
    "CALC_CHAIN_RULE_OMITTED": {
        "key": "TRAP_CHAIN_RULE_INNER",
        "tr": "Bileşke fonksiyon türevinde iç fonksiyonun türeviyle çarpmayı unutmayınız.",
        "en": "Remember to multiply by the derivative of the inner function when applying the chain rule.",
    },
}


def _clean_latex(expr: str) -> str:
    cleaned = expr.strip()
    cleaned = re.sub(r"^\$+|\$+$", "", cleaned)
    cleaned = cleaned.replace("\\left", "").replace("\\right", "")
    cleaned = cleaned.replace("\\cdot", "*").replace("\\times", "*")
    cleaned = cleaned.replace("\\,", "").replace("\\ ", " ")
    cleaned = re.sub(r"\\text\{([^}]*)\}", r"\1", cleaned)
    return cleaned.strip()


def _parse_root_set(text: str) -> Optional[Set[int]]:
    cleaned = _clean_latex(text)
    # Match patterns like "{3}", "3", "{3, 5}", "3, 5", "x = 3", "x = 3, x = 5"
    cleaned = cleaned.replace("{", "").replace("}", "")
    tokens = re.split(r"[,;]|\band\b", cleaned)
    roots = set()
    for tok in tokens:
        tok = tok.strip()
        if not tok:
            continue
        tok = re.sub(r"^[xX]\s*=\s*", "", tok)
        try:
            # Handle integers or simple fractions
            sp_val = sp.sympify(tok)
            if sp_val.is_number and sp_val.is_integer:
                roots.add(int(sp_val))
            else:
                return None
        except Exception:
            return None
    return roots if roots else None


def _normalize_inequality(text: str) -> Optional[str]:
    cleaned = _clean_latex(text).replace(" ", "")
    # Standardize x > k or k < x -> x > k
    m = re.match(r"^x([<>]=?)(-?\d+)$", cleaned)
    if m:
        op, val = m.groups()
        return f"x{op}{val}"
    m_rev = re.match(r"^(-?\d+)([<>]=?)x$", cleaned)
    if m_rev:
        val, op = m_rev.groups()
        flipped_op = ">" if "<" in op else "<"
        if "=" in op:
            flipped_op += "="
        return f"x{flipped_op}{val}"
    return None


def _parse_algebraic_expr(text: str) -> Optional[sp.Expr]:
    cleaned = _clean_latex(text)
    # Convert implicit multiplication and carets: 24x -> 24*x, ^ -> **
    cleaned = cleaned.replace("^", "**")
    # Insert * between number and letter: 24x -> 24*x
    cleaned = re.sub(r"(\d)([a-zA-Z])", r"\1*\2", cleaned)
    # Insert * between ) and ( or letter
    cleaned = re.sub(r"\)([a-zA-Z0-9(])", r")*\1", cleaned)
    cleaned = re.sub(r"([a-zA-Z0-9])\(", r"\1*(", cleaned)

    x = sp.Symbol("x", real=True)
    try:
        expr = sp.sympify(cleaned, locals={"x": x})
        return expr
    except Exception:
        return None


def evaluate_practice_submission(
    trap_family: str,
    prompt_latex: str,
    canonical_solution_expr: str,
    trap_distractor_expr: Optional[str],
    submitted_answer_latex: Optional[str],
) -> Dict[str, Any]:
    """
    Evaluates learner solution with SymPy equivalence and detects specific structural traps.
    """
    if not submitted_answer_latex or not submitted_answer_latex.strip():
        return {
            "is_correct": False,
            "trap_triggered": False,
            "trap_diagnostic_key": None,
            "trap_diagnostic_message_tr": None,
            "trap_diagnostic_message_en": None,
        }

    sub_str = submitted_answer_latex.strip()
    canon_str = canonical_solution_expr.strip()
    trap_str = trap_distractor_expr.strip() if trap_distractor_expr else None

    is_correct = False
    trap_triggered = False

    # 1. Root-set matching (for ALG_DOMAIN_EXCLUSION_MISSED and ALG_SQUARE_BOTH_SIDES_EXTRANEOUS)
    if trap_family in ("ALG_DOMAIN_EXCLUSION_MISSED", "ALG_SQUARE_BOTH_SIDES_EXTRANEOUS"):
        sub_roots = _parse_root_set(sub_str)
        canon_roots = _parse_root_set(canon_str)
        trap_roots = _parse_root_set(trap_str) if trap_str else None

        if sub_roots is not None and canon_roots is not None:
            if sub_roots == canon_roots:
                is_correct = True
            elif trap_roots is not None and sub_roots == trap_roots:
                trap_triggered = True

    # 2. Inequality matching (for INEQ_NEGATIVE_MULT_NO_FLIP)
    elif trap_family == "INEQ_NEGATIVE_MULT_NO_FLIP":
        sub_norm = _normalize_inequality(sub_str)
        canon_norm = _normalize_inequality(canon_str)
        trap_norm = _normalize_inequality(trap_str) if trap_str else None

        if sub_norm and canon_norm:
            if sub_norm == canon_norm:
                is_correct = True
            elif trap_norm and sub_norm == trap_norm:
                trap_triggered = True

    # 3. Algebraic derivative matching (for CALC_CHAIN_RULE_OMITTED)
    elif trap_family == "CALC_CHAIN_RULE_OMITTED":
        sub_expr = _parse_algebraic_expr(sub_str)
        canon_expr = _parse_algebraic_expr(canon_str)
        trap_expr = _parse_algebraic_expr(trap_str) if trap_str else None

        if sub_expr is not None and canon_expr is not None:
            try:
                diff_canon = sp.simplify(sub_expr - canon_expr)
                if diff_canon == 0:
                    is_correct = True
                elif trap_expr is not None:
                    diff_trap = sp.simplify(sub_expr - trap_expr)
                    if diff_trap == 0:
                        trap_triggered = True
            except Exception:
                pass

    # Generic fallback: direct string equivalence after cleaning
    if not is_correct and not trap_triggered:
        cleaned_sub = _clean_latex(sub_str).replace(" ", "")
        cleaned_canon = _clean_latex(canon_str).replace(" ", "")
        if cleaned_sub == cleaned_canon:
            is_correct = True
        elif trap_str:
            cleaned_trap = _clean_latex(trap_str).replace(" ", "")
            if cleaned_sub == cleaned_trap:
                trap_triggered = True

    diag = DIAGNOSTIC_FEEDBACK.get(trap_family, {}) if trap_triggered else {}

    return {
        "is_correct": is_correct,
        "trap_triggered": trap_triggered,
        "trap_diagnostic_key": diag.get("key"),
        "trap_diagnostic_message_tr": diag.get("tr"),
        "trap_diagnostic_message_en": diag.get("en"),
    }
