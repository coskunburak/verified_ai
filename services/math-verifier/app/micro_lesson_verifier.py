"""Deterministic SymPy & SHA-256 verifier for Sprint 8.6 Micro-Lesson Cards.

Enforces INV-MICRO-002 (pre-verified canonical math & SHA-256 sealing),
INV-MICRO-003 (3-beat bounded 60-180s duration & zero media URLs),
and DOC-83 §7 (zero stigmatizing lexicon). Zero runtime LLM or network imports.
"""
from __future__ import annotations

import hashlib
import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import sympy as sp
from sympy.parsing.sympy_parser import parse_expr, standard_transformations

from app.domain.errors import UnsafeExpressionError
from app.parsing.parser_limits import MAX_EXPRESSION_LENGTH, MAX_OPERATIONS
from app.parsing.safe_parser import parse_safe_expression
from app.parsing.symbol_table import ALLOWED_FUNCTIONS, ALLOWED_SYMBOL_NAMES, allowed_locals

VERIFIER_VERSION = "sympy-1.12-v1"
SUPPORTED_LOCALES = frozenset({"tr-TR", "en-US"})
CANONICAL_HASH_KEYS = (
    "schemaVersion",
    "mistakeCode",
    "locale",
    "title",
    "focusBadgeLabel",
    "estimatedDurationSeconds",
    "coreRule",
    "brokenVsValidCounterexample",
    "interactiveQuickCheck",
)

FORBIDDEN_MEDIA_PATTERN = re.compile(
    r"(https?://|\.mp4\b|\.mov\b|\.m3u8\b|\byoutube\b|\bvimeo\b)",
    re.IGNORECASE,
)

FORBIDDEN_LEXICON_EN = re.compile(
    r"\b(failed|failure|weakness|weaknesses|incompetent|dumb|stupid|hopeless|"
    r"bad\s+student|wrong\s+again|still\s+wrong|you\s+never|careless|lazy|"
    r"deficit|remedial|subpar|flunked|penalty|punish|shame|embarrassing)\b",
    re.IGNORECASE,
)

FORBIDDEN_LEXICON_TR = re.compile(
    r"(?:(?<!\w)|^)(başarısız|zayıflık|yetersiz|tembel|aptal|beceriksiz|umutsuz|"
    r"kötü\s+öğrenci|yine\s+yanlış|hala\s+yanlış|ceza|utanç|kusur|eksiklik)(?:(?!\w)|$)",
    re.IGNORECASE,
)

SAFE_RELATIONAL_CHARS = re.compile(r"^[0-9A-Za-z_+\-*/^()., <>=\n]+$")

EXTENDED_FUNCTIONS: dict[str, Any] = {
    **ALLOWED_FUNCTIONS,
    "pi": sp.pi,
    "Abs": sp.Abs,
    "Eq": sp.Eq,
    "Ne": sp.Ne,
    "Lt": sp.Lt,
    "Le": sp.Le,
    "Gt": sp.Gt,
    "Ge": sp.Ge,
    "And": sp.And,
    "Or": sp.Or,
    "Not": sp.Not,
    "Limit": sp.Limit,
    "diff": sp.diff,
    "integrate": sp.integrate,
    "simplify": sp.simplify,
    "Rational": sp.Rational,
    "oo": sp.oo,
}

EXTENDED_GLOBALS: dict[str, Any] = {
    "__builtins__": {},
    "Integer": sp.Integer,
    "Float": sp.Float,
    "Rational": sp.Rational,
    "Symbol": sp.Symbol,
    "Add": sp.Add,
    "Mul": sp.Mul,
    "Pow": sp.Pow,
    **EXTENDED_FUNCTIONS,
}

EXTRA_SYMBOLS = ("a", "b", "k", "x", "y")


@dataclass(frozen=True)
class MicroLessonVerificationResult:
    mistake_code: str
    locale: str
    verified: bool
    verification_hash: str
    verifier_version: str
    reason: str


class MicroLessonVerificationError(ValueError):
    """Raised when a micro-lesson card fails schema, lexicon, math, or hash verification."""


def load_canonical_mistake_codes(schema_path: Path | None = None) -> set[str]:
    if schema_path is None:
        schema_path = Path(__file__).resolve().parents[3] / "packages" / "schemas" / "mistake-category-v1.json"
    data = json.loads(schema_path.read_text(encoding="utf-8"))
    return {entry["code"] for entry in data.get("mappings", []) if "code" in entry}


def canonical_hash_envelope(card: dict[str, Any]) -> dict[str, Any]:
    missing = [key for key in CANONICAL_HASH_KEYS if key not in card]
    if missing:
        raise MicroLessonVerificationError(f"Missing canonical payload keys for hash: {missing}")
    return {key: card[key] for key in CANONICAL_HASH_KEYS}


def compute_verification_hash(card: dict[str, Any]) -> str:
    envelope = canonical_hash_envelope(card)
    canonical_json = json.dumps(envelope, sort_keys=True, ensure_ascii=False, separators=(",", ":"))
    return hashlib.sha256(canonical_json.encode("utf-8")).hexdigest()


def _iter_strings(value: Any):
    if isinstance(value, str):
        yield value
    elif isinstance(value, dict):
        for v in value.values():
            yield from _iter_strings(v)
    elif isinstance(value, list):
        for item in value:
            yield from _iter_strings(item)


def _check_lexicon_and_media(card: dict[str, Any]) -> None:
    for text in _iter_strings(canonical_hash_envelope(card)):
        if FORBIDDEN_MEDIA_PATTERN.search(text):
            raise MicroLessonVerificationError(f"Forbidden external media URL detected: {text!r}")
        en_match = FORBIDDEN_LEXICON_EN.search(text)
        if en_match:
            raise MicroLessonVerificationError(
                f"DOC-83 §7 forbidden English term '{en_match.group(0)}' in text: {text!r}"
            )
        tr_match = FORBIDDEN_LEXICON_TR.search(text)
        if tr_match:
            raise MicroLessonVerificationError(
                f"DOC-83 §7 forbidden Turkish term '{tr_match.group(0)}' in text: {text!r}"
            )


def _parse_extended_sympy(expr_text: str) -> Any:
    normalized = expr_text.strip().replace("^", "**")
    if not normalized or len(normalized) > MAX_EXPRESSION_LENGTH:
        raise UnsafeExpressionError("Expression length out of bounds")
    if not SAFE_RELATIONAL_CHARS.fullmatch(normalized):
        raise UnsafeExpressionError(f"Unsafe characters in expression: {expr_text!r}")

    # Prefer core safe_parser when expression uses only standard arithmetic syntax
    try:
        if not any(op in normalized for op in ("<", ">", "Eq(", "Ne(", "Lt(", "Le(", "Gt(", "Ge(", "And(", "Or(", "Limit(", "diff(", "integrate(", "Rational(", "oo")):
            return parse_safe_expression(normalized, list(EXTRA_SYMBOLS))
    except UnsafeExpressionError:
        pass

    identifiers = set(re.findall(r"[A-Za-z_][A-Za-z0-9_]*", normalized))
    allowed_ids = ALLOWED_SYMBOL_NAMES | set(EXTENDED_FUNCTIONS) | set(EXTRA_SYMBOLS)
    if not identifiers.issubset(allowed_ids):
        disallowed = sorted(identifiers - allowed_ids)
        raise UnsafeExpressionError(f"Disallowed identifiers: {disallowed}")

    local_dict = allowed_locals(list(EXTRA_SYMBOLS))
    for sym in EXTRA_SYMBOLS:
        local_dict[sym] = sp.Symbol(sym, real=True)

    # Handle bare inequality operators (e.g. "-2*x < 6") via standard transformations
    parsed = parse_expr(
        normalized,
        local_dict=local_dict,
        global_dict=EXTENDED_GLOBALS,
        transformations=standard_transformations,
        evaluate=True,
    )
    if hasattr(parsed, "doit") and isinstance(parsed, (sp.Limit, sp.Derivative, sp.Integral)):
        parsed = parsed.doit()
    if isinstance(parsed, sp.Basic) and len(list(sp.preorder_traversal(parsed))) > MAX_OPERATIONS:
        raise UnsafeExpressionError("Expression exceeds maximum AST node limit")
    return parsed


def _evaluate_at_point(expr: Any, bindings: dict[str, Any]) -> Any:
    if isinstance(expr, (bool, sp.logic.boolalg.BooleanTrue, sp.logic.boolalg.BooleanFalse)):
        return bool(expr)
    subs_map: dict[sp.Symbol, Any] = {}
    if hasattr(expr, "free_symbols"):
        for s in expr.free_symbols:
            if s.name in bindings:
                val = bindings[s.name]
                subs_map[s] = sp.Rational(val) if isinstance(val, int) else sp.sympify(val)
    for name, val in bindings.items():
        sym = sp.Symbol(name, real=True)
        if sym not in subs_map:
            subs_map[sym] = sp.Rational(val) if isinstance(val, int) else sp.sympify(val)
    evaluated = expr.subs(subs_map) if hasattr(expr, "subs") else expr
    if hasattr(evaluated, "doit"):
        evaluated = evaluated.doit()
    if isinstance(evaluated, (bool, sp.logic.boolalg.BooleanTrue, sp.logic.boolalg.BooleanFalse)):
        return bool(evaluated)
    return sp.simplify(evaluated)


def _are_mathematically_equivalent(target: Any, valid: Any, bindings: dict[str, Any]) -> bool:
    if isinstance(target, (sp.Basic, bool)) and isinstance(valid, (sp.Basic, bool)):
        is_target_bool = isinstance(target, (bool, sp.logic.boolalg.Boolean, sp.Rel))
        is_valid_bool = isinstance(valid, (bool, sp.logic.boolalg.Boolean, sp.Rel))
        if is_target_bool and is_valid_bool:
            # Verify logical equivalence over real univariate inequalities / equations
            x_sym = sp.Symbol("x", real=True)
            t_real = target.subs({s: x_sym for s in target.free_symbols if s.name == "x"}) if hasattr(target, "subs") else target
            v_real = valid.subs({s: x_sym for s in valid.free_symbols if s.name == "x"}) if hasattr(valid, "subs") else valid
            try:
                if hasattr(t_real, "as_set") and hasattr(v_real, "as_set"):
                    if t_real.as_set() == v_real.as_set():
                        return True
            except Exception:
                pass
            try:
                if sp.simplify(sp.Equivalent(t_real, v_real)) == sp.true:
                    return True
            except Exception:
                pass
            # Fallback: test at counterexample point and multiple real sample points (skipping poles)
            for sample in (-5, -3, -1, 0, 1, 2, 5, 7):
                t_err = v_err = False
                try:
                    t_val = _evaluate_at_point(t_real, {"x": sample})
                except (TypeError, ZeroDivisionError):
                    t_err = True
                    t_val = None
                try:
                    v_val = _evaluate_at_point(v_real, {"x": sample})
                except (TypeError, ZeroDivisionError):
                    v_err = True
                    v_val = None
                if t_err and v_err:
                    continue
                if t_err != v_err or t_val != v_val:
                    return False
            return True

        diff_expr = sp.simplify(target - valid)
        if diff_expr == 0:
            return True
        # Handle real-domain identities like sqrt(x^2) == Abs(x) or log(x^2) == 2*log(Abs(x))
        x_sym = sp.Symbol("x", real=True)
        y_sym = sp.Symbol("y", real=True)
        real_subs = {}
        for s in diff_expr.free_symbols:
            if s.name == "x":
                real_subs[s] = x_sym
            elif s.name == "y":
                real_subs[s] = y_sym
        if real_subs:
            real_diff = sp.simplify(diff_expr.subs(real_subs))
            if real_diff == 0:
                return True
        if bindings:
            return _evaluate_at_point(target, bindings) == _evaluate_at_point(valid, bindings)
    return target == valid


def verify_card(
    card: dict[str, Any],
    allowed_mistake_codes: set[str] | None = None,
    *,
    verify_hash: bool = True,
) -> MicroLessonVerificationResult:
    codes = allowed_mistake_codes or load_canonical_mistake_codes()
    mistake_code = card.get("mistakeCode")
    locale = card.get("locale")

    if mistake_code not in codes:
        raise MicroLessonVerificationError(f"Unknown or non-canonical mistakeCode: {mistake_code!r}")
    if locale not in SUPPORTED_LOCALES:
        raise MicroLessonVerificationError(f"Unsupported locale: {locale!r}")

    _check_lexicon_and_media(card)

    core_rule = card["coreRule"]
    counterexample = card["brokenVsValidCounterexample"]
    quick_check = card["interactiveQuickCheck"]

    beat_durations = [
        core_rule["estimatedSeconds"],
        counterexample["estimatedSeconds"],
        quick_check["estimatedSeconds"],
    ]
    for sec in beat_durations:
        if not isinstance(sec, int) or not (15 <= sec <= 60):
            raise MicroLessonVerificationError(f"Beat estimatedSeconds must be in 15..60, got {sec!r}")

    total_sec = card["estimatedDurationSeconds"]
    if not isinstance(total_sec, int) or total_sec != sum(beat_durations) or not (60 <= total_sec <= 180):
        raise MicroLessonVerificationError(
            f"estimatedDurationSeconds={total_sec!r} violates 60..180 or sum(beats)={sum(beat_durations)}"
        )

    # Validate Beat 1 (Core Rule)
    if not str(core_rule.get("canonicalLatexFormula", "")).strip():
        raise MicroLessonVerificationError("coreRule.canonicalLatexFormula cannot be blank")
    sympy_check_expr = str(core_rule.get("sympyCheckExpr", "")).strip()
    if not sympy_check_expr:
        raise MicroLessonVerificationError("coreRule.sympyCheckExpr cannot be blank")
    if "==" in sympy_check_expr:
        lhs_str, rhs_str = sympy_check_expr.split("==", 1)
        lhs_expr = _parse_extended_sympy(lhs_str)
        rhs_expr = _parse_extended_sympy(rhs_str)
        if not _are_mathematically_equivalent(lhs_expr, rhs_expr, counterexample.get("counterexampleBindings", {})):
            raise MicroLessonVerificationError(f"coreRule.sympyCheckExpr failed equivalence: {sympy_check_expr}")
    else:
        _parse_extended_sympy(sympy_check_expr)

    # Validate Beat 2 (Broken vs. Valid Counterexample)
    bindings = counterexample.get("counterexampleBindings", {})
    if not isinstance(bindings, dict):
        raise MicroLessonVerificationError("counterexampleBindings must be a dict")

    target_expr = _parse_extended_sympy(counterexample["sympyTargetExpr"])
    valid_expr = _parse_extended_sympy(counterexample["sympyValidExpr"])
    trap_expr = _parse_extended_sympy(counterexample["sympyTrapExpr"])

    if not _are_mathematically_equivalent(target_expr, valid_expr, bindings):
        raise MicroLessonVerificationError(
            f"{mistake_code} ({locale}): sympyTargetExpr and sympyValidExpr are not mathematically equivalent"
        )

    valid_at_pt = _evaluate_at_point(valid_expr, bindings)
    trap_at_pt = _evaluate_at_point(trap_expr, bindings)
    if valid_at_pt == trap_at_pt:
        raise MicroLessonVerificationError(
            f"{mistake_code} ({locale}): sympyTrapExpr is equivalent to sympyValidExpr at counterexample {bindings} ({valid_at_pt} == {trap_at_pt})"
        )

    # Validate Beat 3 (Interactive Quick Check)
    options = quick_check["options"]
    if not (3 <= len(options) <= 4):
        raise MicroLessonVerificationError(f"quickCheck options count must be 3 or 4, got {len(options)}")
    option_ids = [opt["optionId"] for opt in options]
    if len(set(option_ids)) != len(option_ids):
        raise MicroLessonVerificationError("Duplicate optionId in interactiveQuickCheck.options")
    option_texts = [opt["latexOrText"].strip() for opt in options]
    if len(set(option_texts)) != len(option_texts):
        raise MicroLessonVerificationError("Duplicate option latexOrText in interactiveQuickCheck.options")
    correct_options = [opt for opt in options if opt.get("isCorrect") is True]
    if len(correct_options) != 1:
        raise MicroLessonVerificationError(f"Expected exactly 1 correct option, got {len(correct_options)}")
    if correct_options[0]["optionId"] != quick_check["correctOptionId"]:
        raise MicroLessonVerificationError("correctOptionId does not match the option with isCorrect=true")

    computed_hash = compute_verification_hash(card)
    if verify_hash:
        recorded_hash = card.get("verificationHash", "")
        if not re.fullmatch(r"[0-9a-f]{64}", str(recorded_hash)):
            raise MicroLessonVerificationError(f"Invalid 64-hex verificationHash: {recorded_hash!r}")
        if recorded_hash != computed_hash:
            raise MicroLessonVerificationError(
                f"SHA-256 verificationHash mismatch for {mistake_code} ({locale}): "
                f"expected {computed_hash}, got {recorded_hash}"
            )

    return MicroLessonVerificationResult(
        mistake_code=mistake_code,
        locale=locale,
        verified=True,
        verification_hash=computed_hash,
        verifier_version=VERIFIER_VERSION,
        reason="SYMPY_AND_SHA256_VERIFIED",
    )


def verify_catalog(
    catalog_path: Path,
    mistake_schema_path: Path | None = None,
) -> list[MicroLessonVerificationResult]:
    codes = load_canonical_mistake_codes(mistake_schema_path)
    payload = json.loads(catalog_path.read_text(encoding="utf-8"))
    cards = payload.get("cards", [])

    seen_pairs: set[tuple[str, str]] = set()
    results: list[MicroLessonVerificationResult] = []
    for card in cards:
        res = verify_card(card, codes, verify_hash=True)
        pair = (res.mistake_code, res.locale)
        if pair in seen_pairs:
            raise MicroLessonVerificationError(f"Duplicate (mistakeCode, locale) pair: {pair}")
        seen_pairs.add(pair)
        results.append(res)

    expected_pairs = {(code, loc) for code in codes for loc in SUPPORTED_LOCALES}
    missing = sorted(expected_pairs - seen_pairs)
    if missing:
        raise MicroLessonVerificationError(f"Catalog missing (mistakeCode, locale) pairs: {missing}")

    return results
