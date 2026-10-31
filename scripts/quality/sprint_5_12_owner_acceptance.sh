#!/usr/bin/env bash
# Owner-only execution. No paid inference, baseline promotion, deployment or Git mutation.
set -Eeuo pipefail
solver_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$solver_root"
mkdir -p .generated/sprint-5-12-owner
solver_output="$(mktemp -d "$solver_root/.generated/sprint-5-12-owner/$(TZ=Europe/Istanbul date +%Y%m%d-%H%M%S)-XXXXXX")"
exec > >(tee "$solver_output/acceptance.log") 2>&1
export PYTHONDONTWRITEBYTECODE=1
export VERIFIER_PYTHON="${VERIFIER_PYTHON:-$solver_root/services/math-verifier/.venv/bin/python}"
unset QWEN_OWNER_RUN
solver_python="$VERIFIER_PYTHON"
solver_overall=0
solver_stage=prerequisites
for solver_gate in A B C D E F G H I J; do printf 'NOT_RUN\n' > "$solver_output/$solver_gate.status"; done
finish() {
    local exit_code=$?
    trap - EXIT ERR
    set +e
    if [[ "$exit_code" -ne 0 ]]; then
        solver_overall="$exit_code"
        printf 'FAIL\n' > "$solver_output/$solver_stage.status"
    fi
    if command -v python3 >/dev/null 2>&1; then
        python3 - "$solver_output" "$solver_overall" <<'SUMMARY'
import json
import sys
from pathlib import Path
out = Path(sys.argv[1])
stages = {key: (out / f"{key}.status").read_text().strip() for key in "ABCDEFGHIJ"}
passed = int(sys.argv[2]) == 0 and all(value == "PASS" for value in stages.values())
summary = {
    "version": "sprint-5-12-owner-v1", "localAcceptance": "PASS" if passed else "FAIL",
    "exitCode": int(sys.argv[2]) if int(sys.argv[2]) else (0 if passed else 1),
    "stages": stages, "hostedCI": "NOT_RUN", "paidModels": "SKIPPED_BY_POLICY",
    "realModelEvaluation": "BLOCKED", "protectedHoldout": "BLOCKED",
    "productionPromotion": "BLOCKED", "productionRollout": "DISABLED",
    "limitations": ["Local fixture acceptance is not real-model quality evidence or release approval.",
                    "Independent labels, representative holdout and approved thresholds remain external requirements."],
}
(out / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
print(json.dumps(summary, indent=2))
sys.exit(summary["exitCode"])
SUMMARY
        solver_overall=$?
    else
        solver_overall=2
        printf '{"localAcceptance":"BLOCKED","productionPromotion":"BLOCKED","reason":"python3 missing","exitCode":2}\n' > "$solver_output/summary.json"
    fi
    printf '\nEvidence: %s\nExit: %s\n' "$solver_output" "$solver_overall"
    exit "$solver_overall"
}
trap finish EXIT
trap 'printf "Failure: stage=%s line=%s status=%s\n" "$solver_stage" "$LINENO" "$?" >&2' ERR
run_stage() {
    solver_stage="$1"
    shift
    local stage_exit
    set +e
    (set -Eeuo pipefail; "$@") > >(tee "$solver_output/$solver_stage.log") 2>&1
    stage_exit=$?
    set -e
    printf '%s\n' "$stage_exit" > "$solver_output/$solver_stage.exit"
    if [[ "$stage_exit" -eq 0 ]]; then
        printf 'PASS\n' > "$solver_output/$solver_stage.status"
    else
        printf 'FAIL\n' > "$solver_output/$solver_stage.status"
        solver_overall=1
    fi
}
[[ -x "$solver_python" ]] || { printf 'Required verifier Python environment missing: %s\n' "$solver_python"; exit 2; }
command -v python3 >/dev/null
command -v git >/dev/null
git rev-parse HEAD > "$solver_output/head.txt"
git branch --show-current > "$solver_output/branch.txt"
git status --short > "$solver_output/working-tree.txt"
# Capture identities only. Raw patches and source payloads are not exported by this wrapper.
run_tests() {
    "$solver_python" - "$1" "$solver_output/$solver_stage-tests.json" <<'TESTS'
import json
import sys
import unittest
from pathlib import Path
name = "evaluations.runners.test_solver_evaluation." + sys.argv[1]
suite = unittest.defaultTestLoader.loadTestsFromName(name)
count = suite.countTestCases()
result = unittest.TextTestRunner(verbosity=2).run(suite)
passed = (count > 0 and result.testsRun == count and result.wasSuccessful()
          and not result.skipped and not result.expectedFailures and not result.unexpectedSuccesses)
evidence = {"suite": name, "discovered": count, "executed": result.testsRun,
            "failures": len(result.failures), "errors": len(result.errors),
            "skipped": len(result.skipped), "expectedFailures": len(result.expectedFailures),
            "unexpectedSuccesses": len(result.unexpectedSuccesses), "status": "PASS" if passed else "FAIL"}
Path(sys.argv[2]).write_text(json.dumps(evidence, indent=2) + "\n")
sys.exit(0 if passed else 1)
TESTS
}
dataset_stage() {
    run_tests DatasetTests
    "$solver_python" evaluations/runners/solver_evaluation.py --mode validate --output "$solver_output/dataset"
}
release_stage() {
    run_tests ReleaseTests
    "$solver_python" evaluations/runners/solver_evaluation.py --mode local --output "$solver_output/evaluation"
}
run_stage A dataset_stage
run_stage B run_tests RunnerTests
run_stage C run_tests HonestyTests
run_stage D run_tests EconomicsTests
run_stage E release_stage
# Reuse the established complete backend/Linux/iOS/security run, including its skip audit.
# Select only a newly created artifact directory, never a historical successful result.
solver_stage=F
python3 - "$solver_output" <<'BEFORE'
import json
import sys
from pathlib import Path
root = Path(".generated/sprint-5-11-owner")
Path(sys.argv[1], "platform-before.json").write_text(json.dumps(sorted(str(p.resolve()) for p in root.glob("*") if p.is_dir())))
BEFORE
set +e
bash scripts/quality/sprint_5_11_owner_acceptance.sh > >(tee "$solver_output/platform.log") 2>&1
solver_platform_exit=$?
set -e
printf '%s\n' "$solver_platform_exit" > "$solver_output/platform.exit"
if [[ "$solver_platform_exit" -ne 0 ]]; then solver_overall=1; fi
platform_evidence() {
    python3 - "$solver_output" <<'PLATFORM'
import json
import sys
from pathlib import Path
out = Path(sys.argv[1])
before = set(json.loads((out / "platform-before.json").read_text()))
new = [p.resolve() for p in Path(".generated/sprint-5-11-owner").glob("*") if p.is_dir() and str(p.resolve()) not in before]
if len(new) != 1:
    raise SystemExit("Missing or ambiguous fresh platform evidence directory")
child = new[0]
summary = json.loads((child / "summary.json").read_text())
(out / "platform-reference.json").write_text(json.dumps({"directory": str(child), "summary": summary}, indent=2) + "\n")
if summary.get("gates", {}).get("backend") != "PASS":
    raise SystemExit("Backend regression did not complete successfully")
import xml.etree.ElementTree as ET
required = {"SolverReleaseEvidenceTest", "SolverReleaseRegistryTest"}
seen = set()
for report in (child / "maven-reports" / "surefire-reports").glob("TEST-*.xml"):
    root = ET.parse(report).getroot()
    name = root.attrib.get("name", "").split(".")[-1]
    if name not in required:
        continue
    cases = root.findall("testcase")
    if not cases or any(case.find(kind) is not None for case in cases for kind in ("skipped", "error", "failure")):
        raise SystemExit("New release suite missing execution or failed: " + name)
    seen.add(name)
if required - seen:
    raise SystemExit("Missing release governance suites: " + ", ".join(sorted(required - seen)))
PLATFORM
}
run_stage F platform_evidence
require_platform_stage() {
    python3 - "$solver_output/platform-reference.json" "$1" <<'REQUIRE'
import json
import sys
from pathlib import Path
reference = json.loads(Path(sys.argv[1]).read_text())
if reference["summary"].get("gates", {}).get(sys.argv[2]) != "PASS":
    raise SystemExit("Required platform gate lacks successful evidence: " + sys.argv[2])
print("Reused fresh platform evidence: " + sys.argv[2])
REQUIRE
}
platform_regression() {
    require_platform_stage linux
    AI_EVAL_MODE=local AI_EVAL_OUT_DIR="$solver_output/ingestion" \
        AI_EVAL_MANIFEST=evaluations/golden-datasets/parsing/ingestion-v1/manifest.yaml \
        AI_EVAL_COVERAGE=evaluations/rubrics/ingestion-coverage-v1.yaml \
        AI_EVAL_BASELINE=evaluations/baselines/production-ingestion-v1.json \
        AI_EVAL_POLICY=evaluations/baselines/ingestion-release-gates-v1.yaml make eval-ai
    "$solver_python" -m unittest evaluations/runners/test_ingestion_evaluation.py
}
run_stage G platform_regression
run_stage H require_platform_stage E
run_stage I require_platform_stage H
final_audit() {
    python3 - "$solver_output" <<'AUDIT'
import hashlib
import json
import sys
from pathlib import Path
out = Path(sys.argv[1])
for key in "ABCDEFGHI":
    if (out / f"{key}.status").read_text().strip() != "PASS" or int((out / f"{key}.exit").read_text()) != 0:
        raise SystemExit("Required stage failed or missing: " + key)
for key in "ABCDE":
    evidence = json.loads((out / f"{key}-tests.json").read_text())
    if evidence["status"] != "PASS" or evidence["executed"] <= 0 or evidence["executed"] != evidence["discovered"]:
        raise SystemExit("Missing required test execution: " + key)
if int((out / "platform.exit").read_text()) != 0:
    raise SystemExit("Platform acceptance failed")
reference = json.loads((out / "platform-reference.json").read_text())
if reference["summary"].get("overall") != "PASS":
    raise SystemExit("Platform final audit failed")
for directory, scope in (("dataset", "DATASET_CONTRACT_VALIDATION"), ("evaluation", "LOCAL_FIXTURE_REGRESSION")):
    report = json.loads((out / directory / "solver-evaluation-report.json").read_text())
    if report.get("gate", {}).get("decision") != "PASS" or report.get("gate", {}).get("evidenceScope") != scope:
        raise SystemExit("Missing successful scope-specific solver evidence: " + directory)
    if report.get("productionPromotion", {}).get("decision") != "BLOCKED":
        raise SystemExit("Local solver evidence must not authorize production")
    stored_hash = report.pop("reportSha256", None)
    canonical = json.dumps(report, sort_keys=True, separators=(",", ":"), ensure_ascii=True, allow_nan=False).encode("utf-8")
    if stored_hash != hashlib.sha256(canonical).hexdigest():
        raise SystemExit("Solver report integrity mismatch: " + directory)
    if not (out / directory / "solver-evaluation-summary.md").is_file():
        raise SystemExit("Missing human summary: " + directory)
ingestion = json.loads((out / "ingestion/ingestion-evaluation-report.json").read_text())
comparison = json.loads((out / "ingestion/ingestion-release-comparison.json").read_text())
if ingestion.get("gateDecision") != "PASS" or ingestion.get("executionMode") != "LOCAL_FIXTURE_REGRESSION":
    raise SystemExit("Missing successful deterministic Phase 4 evidence")
if comparison.get("decision") != "PASS" or comparison.get("exitCode") != 0:
    raise SystemExit("Phase 4 approved-baseline comparison failed")
stored_hash = ingestion.get("reportSha256")
if comparison.get("candidateReportSha256") != stored_hash:
    raise SystemExit("Phase 4 comparison references a different report")
ingestion["reportSha256"] = ""
canonical = json.dumps(ingestion, sort_keys=True, separators=(",", ":"), ensure_ascii=True, allow_nan=False).encode("utf-8")
if stored_hash != hashlib.sha256(canonical).hexdigest():
    raise SystemExit("Phase 4 report integrity mismatch")
if not (out / "ingestion/ingestion-evaluation-summary.md").is_file():
    raise SystemExit("Missing Phase 4 human summary")
print("A-I evidence reconciled. Production promotion remains BLOCKED; paid models are SKIPPED_BY_POLICY.")
AUDIT
}
run_stage J final_audit
exit "$solver_overall"
