"""Unit and contract tests for Sprint 8.6 deterministic SymPy micro-lesson verifier."""
from __future__ import annotations

import copy
import json
import re
import time
from pathlib import Path

import pytest

from app.micro_lesson_verifier import (
    MicroLessonVerificationError,
    compute_verification_hash,
    load_canonical_mistake_codes,
    verify_card,
    verify_catalog,
)

REPO_ROOT = Path(__file__).resolve().parents[3]
CATALOG_PATH = REPO_ROOT / "services" / "api" / "src" / "main" / "resources" / "micro_lessons" / "micro_lesson_catalog_v1.json"
MISTAKE_SCHEMA_PATH = REPO_ROOT / "packages" / "schemas" / "mistake-category-v1.json"
V049_SQL_PATH = REPO_ROOT / "services" / "api" / "src" / "main" / "resources" / "db" / "migration" / "platform" / "V049__micro_lesson_cards.sql"
VERIFIER_SOURCE_PATH = REPO_ROOT / "services" / "math-verifier" / "app" / "micro_lesson_verifier.py"


def _load_catalog_cards() -> list[dict]:
    payload = json.loads(CATALOG_PATH.read_text(encoding="utf-8"))
    return payload["cards"]


def test_all_34_catalog_cards_sympy_and_hash_verified() -> None:
    results = verify_catalog(CATALOG_PATH, MISTAKE_SCHEMA_PATH)
    assert len(results) == 34
    assert all(r.verified for r in results)
    locales_by_code: dict[str, set[str]] = {}
    for r in results:
        locales_by_code.setdefault(r.mistake_code, set()).add(r.locale)
    canonical_codes = load_canonical_mistake_codes(MISTAKE_SCHEMA_PATH)
    assert len(canonical_codes) == 17
    assert set(locales_by_code.keys()) == canonical_codes
    assert all(locs == {"tr-TR", "en-US"} for locs in locales_by_code.values())


def test_v049_sql_hashes_match_catalog_json_exactly() -> None:
    sql_text = V049_SQL_PATH.read_text(encoding="utf-8")
    pattern = re.compile(
        r"VALUES\s*\('([0-9a-f-]{36})',\s*'([A-Z0-9_]+)',\s*'(tr-TR|en-US)',.*?'VERIFIED',\s*'([0-9a-f]{64})',\s*'sympy-1\.12-v1'\)",
        re.DOTALL,
    )
    matches = pattern.findall(sql_text)
    assert len(matches) == 34

    cards_by_key = {(c["mistakeCode"], c["locale"]): c for c in _load_catalog_cards()}
    for card_id, code, locale, sql_hash in matches:
        card = cards_by_key[(code, locale)]
        assert card["cardId"] == card_id
        assert card["verificationHash"] == sql_hash
        assert compute_verification_hash(card) == sql_hash


def test_tampered_card_rejected_by_sha256_hash_guard() -> None:
    codes = load_canonical_mistake_codes(MISTAKE_SCHEMA_PATH)
    tampered = copy.deepcopy(_load_catalog_cards()[0])
    tampered["coreRule"]["canonicalLatexFormula"] = "-a(b - c) = -ab - ac"
    with pytest.raises(MicroLessonVerificationError, match="SHA-256 verificationHash mismatch"):
        verify_card(tampered, codes, verify_hash=True)


def test_invalid_sympy_equivalence_rejected() -> None:
    codes = load_canonical_mistake_codes(MISTAKE_SCHEMA_PATH)
    broken = copy.deepcopy(_load_catalog_cards()[0])
    broken["brokenVsValidCounterexample"]["sympyValidExpr"] = "-2*x - 6"
    broken["verificationHash"] = compute_verification_hash(broken)
    with pytest.raises(MicroLessonVerificationError, match="not mathematically equivalent"):
        verify_card(broken, codes, verify_hash=True)


def test_trap_equal_to_valid_rejected() -> None:
    codes = load_canonical_mistake_codes(MISTAKE_SCHEMA_PATH)
    same_trap = copy.deepcopy(_load_catalog_cards()[0])
    same_trap["brokenVsValidCounterexample"]["sympyTrapExpr"] = same_trap["brokenVsValidCounterexample"]["sympyValidExpr"]
    same_trap["verificationHash"] = compute_verification_hash(same_trap)
    with pytest.raises(MicroLessonVerificationError, match="equivalent to sympyValidExpr"):
        verify_card(same_trap, codes, verify_hash=True)


@pytest.mark.parametrize(
    "bad_text",
    [
        "This weakness shows you failed the problem again.",
        "Bu zayıflık nedeniyle başarısız ve tembel görünüyorsun.",
    ],
)
def test_doc83_stigmatizing_lexicon_rejected(bad_text: str) -> None:
    codes = load_canonical_mistake_codes(MISTAKE_SCHEMA_PATH)
    card = copy.deepcopy(_load_catalog_cards()[0])
    card["coreRule"]["conciseExplanation"] = bad_text
    card["verificationHash"] = compute_verification_hash(card)
    with pytest.raises(MicroLessonVerificationError, match="DOC-83"):
        verify_card(card, codes, verify_hash=True)


def test_external_media_url_and_duration_bounds_rejected() -> None:
    codes = load_canonical_mistake_codes(MISTAKE_SCHEMA_PATH)
    media_card = copy.deepcopy(_load_catalog_cards()[0])
    media_card["coreRule"]["conciseExplanation"] = "Watch https://example.com/clip.mp4 for details."
    media_card["verificationHash"] = compute_verification_hash(media_card)
    with pytest.raises(MicroLessonVerificationError, match="media URL"):
        verify_card(media_card, codes, verify_hash=True)

    short_card = copy.deepcopy(_load_catalog_cards()[0])
    short_card["coreRule"]["estimatedSeconds"] = 10
    short_card["estimatedDurationSeconds"] = 70
    short_card["verificationHash"] = compute_verification_hash(short_card)
    with pytest.raises(MicroLessonVerificationError, match="15..60"):
        verify_card(short_card, codes, verify_hash=True)


def test_zero_runtime_llm_or_network_imports() -> None:
    source = VERIFIER_SOURCE_PATH.read_text(encoding="utf-8")
    forbidden_patterns = (
        r"\bimport\s+openai\b",
        r"\bfrom\s+openai\b",
        r"\bimport\s+anthropic\b",
        r"\bfrom\s+anthropic\b",
        r"\bgoogle\.generativeai\b",
        r"\bimport\s+httpx\b",
        r"\bimport\s+requests\b",
        r"\burllib\.request\b",
    )
    for pattern in forbidden_patterns:
        assert not re.search(pattern, source), f"Forbidden runtime dependency matched: {pattern}"


def test_catalog_verification_within_performance_budget() -> None:
    codes = load_canonical_mistake_codes(MISTAKE_SCHEMA_PATH)
    cards = _load_catalog_cards()
    # Warm SymPy cache once, then measure per-card verification latency
    verify_card(cards[0], codes, verify_hash=True)
    durations_ms: list[float] = []
    t0 = time.perf_counter()
    for card in cards:
        c0 = time.perf_counter()
        verify_card(card, codes, verify_hash=True)
        durations_ms.append((time.perf_counter() - c0) * 1000.0)
    total_ms = (time.perf_counter() - t0) * 1000.0
    assert total_ms < 2500.0, f"Full 34-card verification too slow: {total_ms:.1f}ms"
