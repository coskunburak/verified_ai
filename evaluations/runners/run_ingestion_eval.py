#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import os
import platform
import subprocess
import sys
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import ingestion_metrics
import parser_metrics
import recognition_metrics
import validate_ingestion_dataset


ROOT = Path(__file__).resolve().parents[2]
DEFAULT_OUT = ROOT / ".generated/evaluations/latest"
MODE_MAP = {
    "dataset": "DATASET_CONTRACT_VALIDATION",
    "local": "LOCAL_FIXTURE_REGRESSION",
    "connected": "CONNECTED_REPRESENTATIVE_EVALUATION",
    "holdout": "PROTECTED_HOLDOUT_RELEASE_GATE",
}


def now_iso() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def display_path(path: Path) -> str:
    try:
        return path.resolve().relative_to(ROOT).as_posix()
    except ValueError:
        return path.as_posix()


def git_commit() -> str:
    try:
        return subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    except (OSError, subprocess.CalledProcessError):
        return "UNKNOWN"


def report_hash(report: dict[str, Any]) -> str:
    without_hash = dict(report)
    without_hash["reportSha256"] = ""
    encoded = json.dumps(without_hash, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def make_report(cases: list[dict[str, Any]], validation: validate_ingestion_dataset.ValidationResult, execution_mode: str) -> dict[str, Any]:
    started = now_iso()
    recognition = recognition_metrics.to_dict(recognition_metrics.score_cases(cases))
    parser = parser_metrics.to_dict(parser_metrics.score_cases(cases))
    canonical = ingestion_metrics.score_canonicalization(cases)
    classification = ingestion_metrics.score_classification(cases)
    e2e = ingestion_metrics.score_end_to_end(cases)
    critical_failures = critical_failure_records(cases, parser, e2e)
    gate_decision = "PASS"
    gate_reasons: list[str] = []
    if execution_mode == "CONNECTED_REPRESENTATIVE_EVALUATION":
        gate_decision = "BLOCKED"
        gate_reasons.append("BLOCKED_NO_APPROVED_PROVIDER_ROUTE")
    elif execution_mode == "PROTECTED_HOLDOUT_RELEASE_GATE":
        gate_decision = "BLOCKED"
        gate_reasons.append("BLOCKED_PROTECTED_HOLDOUT")
    elif critical_failures:
        gate_decision = "FAIL"
        gate_reasons.extend(record["reason"] for record in critical_failures)
    route_provenance = validation.manifest.get("routeCompatibility", [])
    report = {
        "reportSchemaVersion": "ingestion-evaluation-report-v1",
        "evaluationPolicyVersion": validation.manifest["evaluationPolicyVersion"],
        "runId": f"ingestion-{uuid.uuid4()}",
        "executionMode": execution_mode,
        "datasetId": validation.manifest["datasetId"],
        "datasetVersion": validation.manifest["datasetVersion"],
        "datasetSha256": validation.dataset_sha256,
        "caseSchemaVersion": validation.manifest["caseSchemaVersion"],
        "runtimeSchemaCompatibility": validation.manifest["runtimeSchemaCompatibility"],
        "ontologyVersion": validation.manifest["ontologyVersion"],
        "gitCommit": git_commit(),
        "startedAt": started,
        "completedAt": now_iso(),
        "routeProvenance": [
            {
                **route,
                "provider": "LOCAL_FIXTURE" if execution_mode == "LOCAL_FIXTURE_REGRESSION" else "BLOCKED",
                "model": "local-fixture" if execution_mode == "LOCAL_FIXTURE_REGRESSION" else "BLOCKED",
                "pricingVersion": "fixture-zero-cost-v1",
                "fallbackUsed": False,
            }
            for route in route_provenance
        ],
        "runtime": {
            "pythonVersion": platform.python_version(),
            "javaVersion": os.environ.get("JAVA_VERSION", "not-invoked-by-python-runner"),
            "os": platform.platform(),
            "connectedProvider": execution_mode == "CONNECTED_REPRESENTATIVE_EVALUATION",
            "maxTotalCostMicros": int(os.environ.get("AI_EVAL_MAX_TOTAL_COST_MICROS", "0")),
            "maxCases": int(os.environ.get("AI_EVAL_MAX_CASES", "0")),
            "timeoutSeconds": int(os.environ.get("AI_EVAL_TIMEOUT", "0")),
        },
        "counts": {
            "total": len(cases),
            "evaluated": len(cases) if execution_mode != "DATASET_CONTRACT_VALIDATION" else 0,
            "skipped": 0 if execution_mode != "DATASET_CONTRACT_VALIDATION" else len(cases),
            "blocked": 0 if gate_decision != "BLOCKED" else len(cases),
            "passed": len(cases) if gate_decision == "PASS" else 0,
            "failed": 0 if gate_decision != "FAIL" else len(critical_failures),
            "critical": sum(1 for case in cases if case["criticality"] == "CRITICAL"),
        },
        "metrics": {
            "recognition": recognition,
            "parser": parser,
            "canonicalization": canonical,
            "classification": classification,
            "endToEnd": e2e,
            "latency": {
                "pipelineP50Ms": e2e["pipelineP50LatencyMs"],
                "pipelineP95Ms": e2e["pipelineP95LatencyMs"],
            },
            "cost": {
                "pipelineAverageCostMicros": e2e["pipelineAverageCostMicros"],
                "pipelineP95CostMicros": e2e["pipelineP95CostMicros"],
                "stageAverageCostMicros": e2e["stageAverageCostMicros"],
            },
        },
        "slices": slice_summary(cases),
        "criticalFailures": critical_failures,
        "regressions": [],
        "gateDecision": gate_decision,
        "gateReasons": gate_reasons,
        "connectedProviderStatus": "BLOCKED_NO_APPROVED_PROVIDER_ROUTE"
        if execution_mode == "CONNECTED_REPRESENTATIVE_EVALUATION"
        else "NOT_REQUESTED",
        "protectedHoldoutStatus": "BLOCKED_PROTECTED_HOLDOUT"
        if execution_mode == "PROTECTED_HOLDOUT_RELEASE_GATE"
        else validation.manifest.get("protectedHoldout", {}).get("status", "NOT_REQUESTED"),
        "reportSha256": "",
    }
    report["reportSha256"] = report_hash(report)
    return report


def critical_failure_records(cases: list[dict[str, Any]], parser: dict[str, Any], e2e: dict[str, Any]) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    if e2e["falseAuthoritativeAcceptCount"]:
        records.append({"reason": "critical_false_authoritative_accept_count_nonzero", "count": e2e["falseAuthoritativeAcceptCount"]})
    if e2e["falseReadyForSolveCount"]:
        records.append({"reason": "critical_false_ready_for_solve_count_nonzero", "count": e2e["falseReadyForSolveCount"]})
    if e2e["unsafeInputFalseAcceptCount"]:
        records.append({"reason": "unsafe_input_false_accept_count_nonzero", "count": e2e["unsafeInputFalseAcceptCount"]})
    if parser["inventedSourceReferenceCount"]:
        bad_cases = [
            case["id"]
            for case in cases
            if set(case.get("fixtureObservation", {}).get("parse", {}).get("sourceBlockIds", []) or [])
            - set(case.get("expected", {}).get("parse", {}).get("sourceBlockIds", []) or [])
        ]
        records.append({"reason": "invented_source_reference_count_nonzero", "count": parser["inventedSourceReferenceCount"], "caseIds": bad_cases})
    if e2e["staleLineageAcceptedCount"]:
        records.append({"reason": "stale_lineage_accepted_count_nonzero", "count": e2e["staleLineageAcceptedCount"]})
    if e2e["crossUserAccessAcceptedCount"]:
        records.append({"reason": "cross_user_access_accepted_count_nonzero", "count": e2e["crossUserAccessAcceptedCount"]})
    if e2e["promptInstructionExecutedCount"]:
        records.append({"reason": "prompt_instruction_executed_count_nonzero", "count": e2e["promptInstructionExecutedCount"]})
    return records


def slice_summary(cases: list[dict[str, Any]]) -> dict[str, Any]:
    summary: dict[str, dict[str, Any]] = {}
    for case in cases:
        session = case.get("fixtureObservation", {}).get("session", {})
        for slice_name in case.get("slices", []):
            entry = summary.setdefault(slice_name, {"count": 0, "criticalCount": 0, "readyForSolveCount": 0, "reviewRequiredCount": 0, "unsupportedCount": 0, "caseIds": []})
            entry["count"] += 1
            entry["criticalCount"] += 1 if case.get("criticality") == "CRITICAL" else 0
            entry["readyForSolveCount"] += 1 if session.get("readyForSolve") else 0
            entry["reviewRequiredCount"] += 1 if session.get("reviewRequired") else 0
            entry["unsupportedCount"] += 1 if session.get("unsupported") else 0
            entry["caseIds"].append(case["id"])
    return summary


def write_summary(report: dict[str, Any], path: Path) -> None:
    lines = [
        "# Ingestion Evaluation Summary",
        "",
        f"- Decision: {report['gateDecision']}",
        f"- Execution mode: {report['executionMode']}",
        f"- Dataset: {report['datasetId']} / {report['datasetVersion']}",
        f"- Dataset checksum: `{report['datasetSha256']}`",
        f"- Report checksum: `{report['reportSha256']}`",
        f"- Cases: {report['counts']['total']} total, {report['counts']['critical']} critical",
        f"- Connected provider: {report['connectedProviderStatus']}",
        f"- Protected holdout: {report['protectedHoldoutStatus']}",
        "",
        "## Critical Trust Metrics",
        "",
        f"- false_authoritative_accept_count: {report['metrics']['endToEnd']['falseAuthoritativeAcceptCount']}",
        f"- false_ready_for_solve_count: {report['metrics']['endToEnd']['falseReadyForSolveCount']}",
        f"- unsafe_input_false_accept_count: {report['metrics']['endToEnd']['unsafeInputFalseAcceptCount']}",
        f"- invented_source_reference_count: {report['metrics']['parser']['inventedSourceReferenceCount']}",
    ]
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description="Run Sprint 4.10 deterministic ingestion evaluation")
    parser.add_argument("--manifest", type=Path, default=validate_ingestion_dataset.DEFAULT_MANIFEST)
    parser.add_argument("--coverage", type=Path, default=validate_ingestion_dataset.DEFAULT_COVERAGE)
    parser.add_argument("--mode", choices=sorted(MODE_MAP), default=os.environ.get("AI_EVAL_MODE", "local"))
    parser.add_argument("--out-dir", type=Path, default=DEFAULT_OUT)
    args = parser.parse_args()

    execution_mode = MODE_MAP[args.mode]
    try:
        validation = validate_ingestion_dataset.validate_dataset(args.manifest, args.coverage)
    except ValueError as exc:
        print(exc, file=sys.stderr)
        return 1

    report = make_report(validation.cases, validation, execution_mode)
    args.out_dir.mkdir(parents=True, exist_ok=True)
    report_path = args.out_dir / "ingestion-evaluation-report.json"
    summary_path = args.out_dir / "ingestion-evaluation-summary.md"
    report_path.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    write_summary(report, summary_path)
    print(f"wrote {display_path(report_path)}")
    print(f"wrote {display_path(summary_path)}")
    print(f"decision={report['gateDecision']} reportSha256={report['reportSha256']}")
    return 0 if report["gateDecision"] == "PASS" else 2


if __name__ == "__main__":
    raise SystemExit(main())
