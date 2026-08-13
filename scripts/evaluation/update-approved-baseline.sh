#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage:
  scripts/evaluation/update-approved-baseline.sh --approve --source-report <report.json> [--baseline <baseline.json>]

Promotes a reviewed Sprint 4.10 ingestion evaluation report into the approved baseline file.
This script never runs providers and must not be used from CI automation.
USAGE
}

APPROVE=0
SOURCE_REPORT=""
BASELINE="evaluations/baselines/production-ingestion-v1.json"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --approve)
      APPROVE=1
      shift
      ;;
    --source-report)
      SOURCE_REPORT="${2:-}"
      shift 2
      ;;
    --baseline)
      BASELINE="${2:-}"
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ "$APPROVE" != "1" || -z "$SOURCE_REPORT" ]]; then
  usage >&2
  exit 2
fi

python3 - "$SOURCE_REPORT" "$BASELINE" <<'PY'
import json
import sys
from pathlib import Path

source = Path(sys.argv[1])
baseline = Path(sys.argv[2])
report = json.loads(source.read_text(encoding="utf-8"))
if report.get("reportSchemaVersion") != "ingestion-evaluation-report-v1":
    raise SystemExit("source report is not ingestion-evaluation-report-v1")
if report.get("gateDecision") != "PASS":
    raise SystemExit("source report gateDecision is not PASS")
if report.get("executionMode") != "LOCAL_FIXTURE_REGRESSION":
    raise SystemExit("only LOCAL_FIXTURE_REGRESSION baseline promotion is supported by this local script")
approved = {
    "baselineId": "production-ingestion-v1",
    "baselineSchemaVersion": "ingestion-approved-baseline-v1",
    "approvalStatus": "APPROVED_DETERMINISTIC_LOCAL_FIXTURE_BASELINE",
    "approvalScope": "LOCAL_FIXTURE_REGRESSION_ONLY",
    "approvedAt": report["completedAt"],
    "approvedBy": "manual --approve",
    "sourceReportSha256": report["reportSha256"],
    "sourceGitCommit": report["gitCommit"],
    "reportSchemaVersion": report["reportSchemaVersion"],
    "evaluationPolicyVersion": report["evaluationPolicyVersion"],
    "executionMode": report["executionMode"],
    "datasetId": report["datasetId"],
    "datasetVersion": report["datasetVersion"],
    "datasetSha256": report["datasetSha256"],
    "caseSchemaVersion": report["caseSchemaVersion"],
    "runtimeSchemaCompatibility": report["runtimeSchemaCompatibility"],
    "ontologyVersion": report["ontologyVersion"],
    "routeProvenance": report["routeProvenance"],
    "metrics": report["metrics"],
    "baselineLimitations": [
        "This is a deterministic local fixture baseline only.",
        "It is not real provider OCR/parser/classifier quality evidence.",
        "Connected provider and protected holdout remain blocked without external configuration."
    ],
}
baseline.write_text(json.dumps(approved, indent=2, sort_keys=True) + "\n", encoding="utf-8")
print(f"updated {baseline}")
PY
