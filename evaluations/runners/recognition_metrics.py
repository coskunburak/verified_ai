#!/usr/bin/env python3
from __future__ import annotations

import re
import unicodedata
from dataclasses import dataclass
from typing import Any


NORMALIZATION_VERSION = "recognition-eval-normalization-v1"
TOKENIZER_VERSION = "recognition-math-tokenizer-v1"
CRITICAL_SYMBOLS = ("-", "+", "=", "!=", "<", "<=", ">", ">=", "^", "/", "sqrt", "±", "≤", "≥", "≠")
TOKEN_PATTERN = re.compile(
    r"(?:[0-9]+(?:[.,][0-9]+)?)|(?:[A-Za-z]+)|(?:<=|>=|!=|==|->|[≤≥≠±])|(?:[+\-*/^=<>()[\]{}?,])"
)


@dataclass(frozen=True)
class RecognitionMetrics:
    normalized_exact_match_rate: float
    character_error_rate: float
    math_token_error_rate: float
    critical_symbol_error_rate: float
    block_extraction_accuracy: float
    block_kind_accuracy: float
    reading_order_accuracy: float
    coordinate_validity_rate: float
    ambiguity_review_recall: float


def normalize_recognition_text(text: str | None) -> str:
    if text is None:
        return ""
    normalized = unicodedata.normalize("NFC", text)
    normalized = normalized.replace("\r\n", "\n").replace("\r", "\n")
    normalized = re.sub(r"[ \t\f\v]+", " ", normalized)
    normalized = re.sub(r"\n+", "\n", normalized)
    return normalized.strip()


def tokenize_math(text: str | None) -> list[str]:
    return TOKEN_PATTERN.findall(normalize_recognition_text(text))


def levenshtein(a: list[Any], b: list[Any]) -> int:
    if not a:
        return len(b)
    if not b:
        return len(a)
    previous = list(range(len(b) + 1))
    for i, item_a in enumerate(a, start=1):
        current = [i]
        for j, item_b in enumerate(b, start=1):
            current.append(
                min(
                    previous[j] + 1,
                    current[j - 1] + 1,
                    previous[j - 1] + (0 if item_a == item_b else 1),
                )
            )
        previous = current
    return previous[-1]


def rate(numerator: float, denominator: float) -> float:
    if denominator == 0:
        return 1.0
    return numerator / denominator


def score_cases(cases: list[dict[str, Any]]) -> RecognitionMetrics:
    exact = 0
    cer_total = 0.0
    token_error_total = 0.0
    critical_error_total = 0
    critical_total = 0
    block_total = 0.0
    kind_total = 0.0
    order_total = 0.0
    coordinate_total = 0.0
    ambiguity_expected = 0
    ambiguity_reviewed = 0
    count = 0
    for case in cases:
        expected = case["expected"].get("recognition", {})
        observed = case.get("fixtureObservation", {}).get("recognition", {})
        expected_text = normalize_recognition_text(expected.get("normalizedText"))
        observed_text = normalize_recognition_text(observed.get("normalizedText"))
        if not expected_text and not observed_text and expected.get("blockCount", 0) == 0:
            exact += 1
        elif expected_text == observed_text:
            exact += 1
        cer_total += rate(
            levenshtein(list(observed_text), list(expected_text)),
            max(1, len(expected_text)),
        )
        token_error_total += rate(
            levenshtein(tokenize_math(observed_text), tokenize_math(expected_text)),
            max(1, len(tokenize_math(expected_text))),
        )
        for symbol in expected.get("criticalSymbols", []) or []:
            critical_total += 1
            if symbol not in observed_text:
                critical_error_total += 1
        expected_blocks = expected.get("blockCount", 0)
        observed_blocks = observed.get("blockCount", 0)
        block_total += 1.0 if expected_blocks == observed_blocks else 0.0
        kind_total += float(observed.get("blockKindAccuracy", 1.0 if expected_blocks == 0 else 0.0))
        order_total += float(observed.get("readingOrderAccuracy", 1.0 if expected_blocks == 0 else 0.0))
        coordinate_total += 1.0 if bool(observed.get("coordinateValid")) == bool(expected.get("coordinateValid", True)) else 0.0
        if case["expected"].get("expectedRecognitionOutcome") == "REVIEW_REQUIRED":
            ambiguity_expected += 1
            if observed.get("reviewRequired") is True:
                ambiguity_reviewed += 1
        count += 1
    return RecognitionMetrics(
        normalized_exact_match_rate=rate(exact, count),
        character_error_rate=rate(cer_total, count),
        math_token_error_rate=rate(token_error_total, count),
        critical_symbol_error_rate=rate(critical_error_total, critical_total),
        block_extraction_accuracy=rate(block_total, count),
        block_kind_accuracy=rate(kind_total, count),
        reading_order_accuracy=rate(order_total, count),
        coordinate_validity_rate=rate(coordinate_total, count),
        ambiguity_review_recall=rate(ambiguity_reviewed, ambiguity_expected),
    )


def to_dict(metrics: RecognitionMetrics) -> dict[str, float | str]:
    return {
        "normalizationVersion": NORMALIZATION_VERSION,
        "tokenizerVersion": TOKENIZER_VERSION,
        "normalizedExactMatchRate": metrics.normalized_exact_match_rate,
        "characterErrorRate": metrics.character_error_rate,
        "mathTokenErrorRate": metrics.math_token_error_rate,
        "criticalSymbolErrorRate": metrics.critical_symbol_error_rate,
        "blockExtractionAccuracy": metrics.block_extraction_accuracy,
        "blockKindAccuracy": metrics.block_kind_accuracy,
        "readingOrderAccuracy": metrics.reading_order_accuracy,
        "coordinateValidityRate": metrics.coordinate_validity_rate,
        "ambiguityReviewRecall": metrics.ambiguity_review_recall,
    }
