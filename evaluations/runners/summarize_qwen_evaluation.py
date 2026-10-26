#!/usr/bin/env python3
"""Owner-run offline report. No inference, eval(), sympify(), or user-facing verification."""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
from fractions import Fraction
import hashlib
import json
import math
from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[2]


def checked(value: Fraction) -> Fraction:
    if max(value.numerator.bit_length(), value.denominator.bit_length()) > 4096:
        raise ValueError("numeric grading complexity bound")
    return value


def rational(node: dict, depth: int = 0) -> Fraction:
    if depth > 32:
        raise ValueError("bounded arithmetic only")
    kind = node.get("kind")
    if kind == "NUMBER":
        value = node.get("value", "")
        if not re.fullmatch(r"-?(0|[1-9][0-9]*)(\.[0-9]+)?", value) or len(value) > 80:
            raise ValueError("invalid numeric literal")
        return checked(Fraction(value))
    if kind == "UNARY" and node.get("operator") == "NEGATE":
        return -rational(node["operand"], depth + 1)
    if kind == "BINARY":
        a, b = rational(node["left"], depth + 1), rational(node["right"], depth + 1)
        op = node.get("operator")
        if op == "ADD":
            return checked(a + b)
        if op == "SUBTRACT":
            return checked(a - b)
        if op == "MULTIPLY":
            return checked(a * b)
        if op == "DIVIDE" and b:
            return checked(a / b)
        if op == "POWER" and b.denominator == 1 and abs(b) <= 12:
            if max(a.numerator.bit_length(), a.denominator.bit_length()) * abs(b.numerator) > 4096:
                raise ValueError("numeric grading complexity bound")
            return checked(a ** b.numerator)
    raise ValueError("outside independent numeric grader scope")


def equivalent(answer: dict, gold: dict) -> bool | None:
    if answer == gold:
        return True
    if answer.get("kind") != gold.get("kind"):
        return False
    try:
        if gold.get("kind") == "EXPRESSION":
            return rational(answer["expression"]) == rational(gold["expression"])
        if gold.get("kind") == "RELATION":
            if answer.get("relation") != gold.get("relation") or answer.get("left") != gold.get("left"):
                return None  # Reoriented/symbolic relations need independent review.
            return rational(answer["right"]) == rational(gold["right"])
    except (ValueError, KeyError, TypeError, ZeroDivisionError, OverflowError):
        return None
    return None


def load_json(path: Path):
    if path.stat().st_size > 8 * 1024 * 1024:
        raise ValueError("Evidence file exceeds bound")
    return json.loads(path.read_text(encoding="utf-8"))


def report(rows: list[dict], reviews: dict, schema: dict) -> dict:
    from jsonschema import Draft202012Validator, FormatChecker

    checker = Draft202012Validator(schema, format_checker=FormatChecker())
    counts = Counter()
    slices = defaultdict(Counter)
    latencies, input_tokens, output_tokens, allocations = [], [], [], []
    manual = defaultdict(list)
    for row in rows:
        failure = row.get("failure")
        slices[row["slice"]][failure or row["status"]] += 1
        counts["cases"] += 1
        future = row["expectedDisposition"] == "SOURCE_REJECTED"
        counts["future_or_ambiguous_cases"] += future
        counts["false_unsupported_acceptance"] += future and bool(row.get("semanticAccepted"))
        counts["safe_source_rejections"] += row["status"] == "SOURCE_REJECTED"
        counts["fabricated_verified_claims"] += failure == "FALSE_VERIFICATION_CLAIM" or row.get("verificationPerformed") is not False
        counts["timeouts"] += failure in {"TIMEOUT", "DEADLINE_EXCEEDED"}
        counts["retries"] += max(0, row.get("attemptCount", 0) - 1)
        counts["attempts"] += row.get("attemptCount", 0)
        if row.get("attemptCount", 0):
            latencies.append(row["latencyMs"])
        committed = row.get("committedUsage") or {}
        allocations.append(committed.get("estimatedCostMicros", 0))
        usage = row.get("reportedUsage")
        if usage:
            input_tokens.append(usage["inputTokens"])
            output_tokens.append(usage["outputTokens"])
        elif row.get("attemptCount", 0):
            counts["unknown_usage_attempts"] += 1
        candidate = row.get("candidate")
        if candidate:
            valid = not list(checker.iter_errors(candidate))
            counts["independent_schema_pass"] += valid
            counts["independent_schema_fail"] += not valid
            counts["semantic_accepted"] += bool(row.get("semanticAccepted"))
            counts["restriction_preserved_on_accepted"] += bool(row.get("semanticAccepted"))
            gold = row.get("referenceAnswer")
            if gold:
                counts["exact_answers"] += candidate["finalAnswer"] == gold
                grade = equivalent(candidate["finalAnswer"], gold)
                counts["independently_graded_answers"] += grade is not None
                counts["equivalent_answers"] += grade is True
                counts["answer_review_required"] += grade is None
        elif row.get("attemptCount", 0):
            # Rejected raw payloads are not retained; do not invent an exact schema pass rate.
            counts["schema_status_unavailable"] += 1
        review = reviews.get(row["caseId"])
        if review:
            if not review.get("reviewerId") or review.get("independent") is not True:
                raise ValueError("Manual review must identify an independent reviewer")
            total_steps, valid_steps = review.get("reviewedStepCount"), review.get("validStepCount")
            if total_steps is not None or valid_steps is not None:
                if (type(total_steps) is not int or type(valid_steps) is not int
                        or not 1 <= total_steps <= 24 or not 0 <= valid_steps <= total_steps):
                    raise ValueError("Independent step counts must be bounded and consistent")
                counts["independently_reviewed_steps"] += total_steps
                counts["independently_valid_steps"] += valid_steps
            for metric in ("stepValidity", "turkishQuality", "inventedAssumption", "answerEquivalent"):
                value = review.get(metric)
                if value is not None:
                    if not isinstance(value, bool):
                        raise ValueError("Manual rubric values must be boolean or null")
                    manual[metric].append(value)
    n = max(1, counts["cases"] - counts["future_or_ambiguous_cases"])
    latency = sorted(latencies)
    correct = counts["equivalent_answers"]
    return {
        "decision": "REQUIRES_MORE_EVIDENCE",
        "evidenceScope": "SYNTHETIC_SMOKE_NOT_RELEASE_CORPUS",
        "verificationPerformed": False,
        "counts": dict(counts),
        "semanticAcceptanceRate": counts["semantic_accepted"] / n,
        "schemaAcceptanceLowerBound": counts["independent_schema_pass"] / n,
        "schemaAcceptanceExact": None if counts["schema_status_unavailable"] else counts["independent_schema_pass"] / n,
        "exactAnswerRate": counts["exact_answers"] / n,
        "equivalentAnswerLowerBound": correct / n,
        "independentStepValidity": counts["independently_valid_steps"] / counts["independently_reviewed_steps"]
        if counts["independently_reviewed_steps"] else None,
        "averageLatencyMs": sum(latency) / len(latency) if latency else None,
        "p95LatencyMs": latency[math.ceil(.95 * len(latency)) - 1] if latency else None,
        "timeoutRate": counts["timeouts"] / max(1, counts["attempts"]),
        "retryRate": counts["retries"] / max(1, counts["attempts"]),
        "reportedInputTokens": sum(input_tokens),
        "reportedOutputTokens": sum(output_tokens),
        "tokensComplete": counts["unknown_usage_attempts"] == 0,
        "committedAllocationMicros": sum(allocations),
        "allocatedCostPerCorrectAnswerUsd": sum(allocations) / 1_000_000 / correct if correct else None,
        "actualInvoiceCostUsd": None,
        "fullyAllocatedGpuTcoUsd": None,
        "peakGpuMemoryMiB": None,
        "manualReviewedMetrics": {k: {"reviewedCount": len(v), "positiveRate": sum(v) / len(v)} for k, v in manual.items()},
        "manualMetricsMissing": [k for k in ("stepValidity", "turkishQuality", "inventedAssumption", "answerEquivalent") if not manual[k]],
        "perSliceFailures": {k: dict(v) for k, v in slices.items()},
        "unmeasured": ["production false-verification rate", "OCR/handwriting accuracy", "protected holdout",
                       "Q4 non-inferiority", "capacity under concurrent load", "online shadow/canary"],
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("attempts", type=Path)
    parser.add_argument("--reviews", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    if args.attempts.stat().st_size > 32 * 1024 * 1024:
        raise ValueError("Run exceeds evidence size bound")
    rows = [json.loads(line) for line in args.attempts.read_text(encoding="utf-8").splitlines() if line]
    if not rows or len(rows) > 240 or len({r["caseId"] for r in rows}) != len(rows):
        raise ValueError("Run must contain 1..240 unique case records")
    for key in ("model", "runtimeImageDigest", "deploymentEvidenceSha256", "corpusSha256", "promptSha256", "schemaSha256", "structuredOutputs", "pricingVersion", "allocationMicros"):
        if len({r.get(key) for r in rows}) != 1 or rows[0].get(key) is None:
            raise ValueError("Cannot mix release identities in one evidence run")
    corpus_path = ROOT / "evaluations/fixtures/solver/qwen-smoke-v1.json"
    if hashlib.sha256(corpus_path.read_bytes()).hexdigest() != rows[0]["corpusSha256"]:
        raise ValueError("Corpus identity mismatch")
    schema_path = ROOT / "packages/schemas/solution-candidate.schema.json"
    if hashlib.sha256(schema_path.read_bytes()).hexdigest() != rows[0]["schemaSha256"]:
        raise ValueError("Schema identity mismatch")
    references = {c["id"]: c for c in load_json(corpus_path)["cases"]}
    for row in rows:
        case = references[row["caseId"]]
        if row.get("referenceAnswer") != case["referenceAnswer"] or row["expectedDisposition"] != case["expectedDisposition"]:
            raise ValueError("Gold reference mismatch")
    result = report(rows, load_json(args.reviews) if args.reviews else {}, load_json(schema_path))
    result["runSha256"] = hashlib.sha256(args.attempts.read_bytes()).hexdigest()
    result["corpusCoverage"] = {"observed": len(rows), "total": len(references), "complete": len(rows) == len(references)}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("x", encoding="utf-8") as output:
        json.dump(result, output, ensure_ascii=False, indent=2)
        output.write("\n")
    print("Report written; release remains REQUIRES_MORE_EVIDENCE.")
    return 1 if result["counts"].get("fabricated_verified_claims", 0) or result["counts"].get("false_unsupported_acceptance", 0) else 0


if __name__ == "__main__":
    raise SystemExit(main())
