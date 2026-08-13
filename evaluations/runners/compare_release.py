#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any


PASS = 0
FAIL = 1
BLOCKED = 2


def load(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def metric_at(document: dict[str, Any], dotted: str) -> Any:
    current: Any = document
    for part in dotted.split("."):
        if not isinstance(current, dict) or part not in current:
            raise KeyError(dotted)
        current = current[part]
    return current


def compare(baseline: dict[str, Any], candidate: dict[str, Any], policy: dict[str, Any]) -> dict[str, Any]:
    reasons: list[str] = []
    regressions: list[dict[str, Any]] = []
    blocked = False
    failed = False
    if candidate.get("gateDecision") == "BLOCKED":
        blocked = True
        reasons.extend(candidate.get("gateReasons") or ["candidate gate decision is BLOCKED"])
    if candidate.get("gateDecision") == "FAIL":
        failed = True
        reasons.extend(candidate.get("gateReasons") or ["candidate gate decision is FAIL"])

    compatibility_fields = policy.get("compatibilityFields", [])
    for field in compatibility_fields:
        try:
            baseline_value = metric_at(baseline, field)
            candidate_value = metric_at(candidate, field)
        except KeyError:
            blocked = True
            reasons.append(f"BLOCKED_INCOMPARABLE_BASELINE missing {field}")
            continue
        if baseline_value != candidate_value:
            blocked = True
            reasons.append(f"BLOCKED_INCOMPARABLE_BASELINE {field}: baseline={baseline_value} candidate={candidate_value}")

    for gate in policy.get("hardGates", []):
        field = gate["field"]
        expected = gate["equals"]
        try:
            actual = metric_at(candidate, field)
        except KeyError:
            blocked = True
            reasons.append(f"missing hard gate field {field}")
            continue
        if actual != expected:
            failed = True
            reasons.append(f"hard gate failed {field}: expected {expected}, actual {actual}")

    for gate in policy.get("requiredSlices", []):
        slice_name = gate["slice"]
        minimum = gate.get("minimumCount", 1)
        slice_data = candidate.get("slices", {}).get(slice_name)
        if not slice_data:
            blocked = True
            reasons.append(f"missing required slice {slice_name}")
            continue
        if slice_data.get("count", 0) < minimum:
            blocked = True
            reasons.append(f"required slice {slice_name} below minimum {minimum}")

    for gate in policy.get("relativeGates", []):
        field = gate["field"]
        direction = gate["direction"]
        allowed_delta = float(gate.get("allowedDelta", 0))
        try:
            baseline_value = float(metric_at(baseline, field))
            candidate_value = float(metric_at(candidate, field))
        except (KeyError, TypeError, ValueError):
            blocked = True
            reasons.append(f"missing relative gate field {field}")
            continue
        delta = candidate_value - baseline_value
        violation = False
        if direction == "higher_is_better" and delta < -allowed_delta:
            violation = True
        if direction == "lower_is_better" and delta > allowed_delta:
            violation = True
        if violation:
            failed = True
            regression = {
                "metric": field,
                "baseline": baseline_value,
                "candidate": candidate_value,
                "delta": delta,
                "allowedDelta": allowed_delta,
                "severity": gate.get("severity", "P1"),
            }
            regressions.append(regression)
            reasons.append(f"relative gate failed {field}: baseline={baseline_value} candidate={candidate_value}")

    decision = "PASS"
    exitCode = PASS
    if blocked:
        decision = "BLOCKED"
        exitCode = BLOCKED
    elif failed:
        decision = "FAIL"
        exitCode = FAIL
    return {
        "decision": decision,
        "exitCode": exitCode,
        "reasons": reasons,
        "regressions": regressions,
        "baselineId": baseline.get("baselineId"),
        "candidateReportSha256": candidate.get("reportSha256"),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Compare Sprint 4.10 ingestion candidate report against approved baseline")
    parser.add_argument("--baseline", type=Path, required=True)
    parser.add_argument("--candidate", type=Path, required=True)
    parser.add_argument("--policy", type=Path, required=True)
    parser.add_argument("--out", type=Path)
    args = parser.parse_args()
    result = compare(load(args.baseline), load(args.candidate), load(args.policy))
    encoded = json.dumps(result, indent=2, sort_keys=True) + "\n"
    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(encoded, encoding="utf-8")
    print(encoded, end="")
    return int(result["exitCode"])


if __name__ == "__main__":
    raise SystemExit(main())
