#!/usr/bin/env bash
# Owner-only acceptance runner for CI backend and security scan failure remediation.
# Never executes automatic commits, push, deployment, or paid inference.
set -Eeuo pipefail

runner_mode=FULL
runner_selected=ABCD
if [[ "$#" -eq 1 && "$1" == "--full" ]]; then
    runner_mode=FULL
    runner_selected=ABCD
elif [[ "$#" -eq 2 && "$1" == "--gate" && "$2" =~ ^[ABC]$ ]]; then
    runner_mode=TARGETED
    runner_selected="$2D"
elif [[ "$#" -eq 0 ]]; then
    runner_mode=FULL
    runner_selected=ABCD
else
    printf 'Usage: %s [--full] | [--gate A|B|C]\n' "$0" >&2
    exit 2
fi

ci_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ci_root"

mkdir -p .generated/ci-backend-security-owner
ci_out="$(mktemp -d "$ci_root/.generated/ci-backend-security-owner/$(TZ=Europe/Istanbul date +%Y%m%d-%H%M%S)-XXXXXX")"
ln -sfn "$ci_out" .generated/ci-backend-security-owner/latest

exec > >(tee "$ci_out/acceptance.log") 2>&1

ci_exit=0
for gate in A B C D; do printf 'NOT_RUN\n' > "$ci_out/$gate.status"; done

blocked() {
    local gate="$1" reason="$2"
    printf '%s\n' "$reason" > "$ci_out/$gate.blocked"
    printf 'BLOCKED\n' > "$ci_out/$gate.status"
    printf 'Gate %s BLOCKED: %s\n' "$gate" "$reason" >&2
    exit 2
}

run_gate() {
    local gate="$1"; shift
    [[ "$runner_selected" == *"$gate"* ]] || return 0
    printf '=== Running Gate %s ===\n' "$gate"
    set +e
    (trap - EXIT INT TERM; set -Eeuo pipefail; "$@") > "$ci_out/$gate.log" 2>&1
    local code=$?
    set -e
    if [[ "$code" -eq 0 ]]; then
        printf 'PASS\n' > "$ci_out/$gate.status"
        printf 'Gate %s: PASS\n' "$gate"
    elif [[ "$code" -eq 2 && -f "$ci_out/$gate.blocked" ]]; then
        printf 'BLOCKED\n' > "$ci_out/$gate.status"
        printf 'Gate %s: BLOCKED (%s)\n' "$gate" "$(cat "$ci_out/$gate.blocked")"
        ci_exit=2
    else
        printf 'FAIL\n' > "$ci_out/$gate.status"
        printf 'Gate %s: FAIL (exit code %s)\n' "$gate" "$code"
        cat "$ci_out/$gate.log" | tail -n 40
        if [[ "$ci_exit" -eq 0 ]]; then ci_exit=1; fi
    fi
}

# Environment resolution
ci_java="${JAVA21_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
ci_path="$PATH"
if [[ -x "$ci_java/bin/java" ]]; then ci_path="$ci_java/bin:$ci_path"; fi
for candidate in /opt/homebrew/bin /usr/local/bin; do
    if [[ -d "$candidate" && ":$ci_path:" != *":$candidate:"* ]]; then
        ci_path="$candidate:$ci_path"
    fi
done
export PATH="$ci_path"

safe_backend_run() {
    local -a test_environment=(
        "PATH=$ci_path"
        "TMPDIR=${TMPDIR:-/tmp}"
        "APP_ENV=test"
        "PYTHONDONTWRITEBYTECODE=1"
        "AI_GEMINI_ENABLED=false"
        "SOLVING_PRIMARY_ENABLED=false"
        "SOLVING_SECONDARY_ENABLED=false"
        "AI_VISION_RECOGNITION_PRIMARY_PROVIDER=LOCAL_FIXTURE"
        "AI_PROBLEM_PARSER_PRIMARY_PROVIDER=LOCAL_FIXTURE"
        "AI_PROBLEM_CLASSIFIER_PRIMARY_PROVIDER=LOCAL_FIXTURE"
        "ATTEMPT_ANALYSIS_ENABLED=false"
        "ATTEMPT_VISION_ENABLED=false"
        "ATTEMPT_PRODUCTION_AUTHORIZED=false"
        "MATH_VERIFIER_ATTEMPT_ENABLED=false"
        "MATH_VERIFIER_ATTEMPT_PRODUCTION_AUTHORIZED=false"
        "MATH_VERIFIER_VISUALIZATION_ENABLED=false"
        "MATH_VERIFIER_VISUALIZATION_PRODUCTION_AUTHORIZED=false"
        "MATH_VERIFIER_INTERNAL_TOKEN=local_math_verifier_token_change_me"
        "VERIFIEDAI_MATH_WIRE=true"
    )
    if [[ -x "$ci_java/bin/java" ]]; then test_environment+=("JAVA_HOME=$ci_java"); fi
    if [[ -x "$ci_root/services/math-verifier/.venv/bin/python" ]]; then
        test_environment+=("VERIFIEDAI_VERIFIER_PYTHON=$ci_root/services/math-verifier/.venv/bin/python")
    fi
    env -i "${test_environment[@]}" "$@"
}

gate_a() {
    # Gate A — Security
    for tool in python3 git; do PATH="$ci_path" command -v "$tool" >/dev/null 2>&1 || blocked A "Missing tool: $tool"; done
    PATH="$ci_path" command -v rg >/dev/null 2>&1 || blocked A "ripgrep (rg) is required on PATH for secret scanner (install via: brew install ripgrep)"

    printf 'A.1: Secret scanner unit tests\n'
    PATH="$ci_path" python3 -m unittest discover -s scripts/security -p test_secret_scan.py

    printf 'A.2: Full repository secret scan\n'
    PATH="$ci_path" scripts/security/secret_scan.sh

    printf 'A.3: Repository hygiene (.DS_Store check)\n'
    local ds_stores
    ds_stores="$(find . -name '.DS_Store' -print)"
    if [[ -n "$ds_stores" ]]; then
        printf 'Repository hygiene failed: found .DS_Store files:\n%s\n' "$ds_stores" >&2
        return 1
    fi

    printf 'A.4: Whitespace and diff hygiene\n'
    PATH="$ci_path" git diff --check
}

gate_b() {
    # Gate B — Targeted backend
    for tool in mvn docker; do PATH="$ci_path" command -v "$tool" >/dev/null 2>&1 || blocked B "Missing tool: $tool"; done
    PATH="$ci_path" java -version > "$ci_out/java-b.log" 2>&1 || blocked B "Java executable unavailable"
    python3 -c '
import re, sys
from pathlib import Path
log = Path(sys.argv[1]).read_text()
if not re.search(r"version \"21[.\"]", log):
    sys.exit(1)
' "$ci_out/java-b.log" || blocked B "Java 21 is required. Ensure JAVA21_HOME or PATH points to JDK 21."

    docker info > "$ci_out/docker-b.log" 2>&1 || blocked B "Docker is not running. Docker/Testcontainers required for PostgreSQL tests."

    mkdir -p "$ci_out/maven-targeted-reports"
    printf '<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"/>\n' > "$ci_out/maven-settings.xml"

    local targeted_tests="TutorSessionControllerTest,SocraticTurnIntegrationTest,TutorPersistenceIntegrationTest,AttemptMathParserV2Test"
    (
        cd services/api
        safe_backend_run mvn -s "$ci_out/maven-settings.xml" -gs "$ci_out/maven-settings.xml" \
            -Dspring.profiles.active=test \
            -Dsurefire.failIfNoSpecifiedTests=true \
            -Dsurefire.reportsDirectory="$ci_out/maven-targeted-reports" \
            "-Dtest=$targeted_tests" test
    )
}

gate_c() {
    # Gate C — Full backend CI parity
    for tool in mvn docker make; do PATH="$ci_path" command -v "$tool" >/dev/null 2>&1 || blocked C "Missing tool: $tool"; done
    PATH="$ci_path" java -version > "$ci_out/java-c.log" 2>&1 || blocked C "Java executable unavailable"
    python3 -c '
import re, sys
from pathlib import Path
log = Path(sys.argv[1]).read_text()
if not re.search(r"version \"21[.\"]", log):
    sys.exit(1)
' "$ci_out/java-c.log" || blocked C "Java 21 is required."

    docker info > "$ci_out/docker-c.log" 2>&1 || blocked C "Docker required for full backend CI parity."

    printf 'C.1: Prompt immutability gate\n'
    python3 -m unittest discover -s scripts/quality -p test_prompt_immutability.py

    printf 'C.2: Pinned MinIO test image\n'
    if ! docker image inspect verified-ai/minio-test:2024-07-16-3535197 >/dev/null 2>&1; then
        make test-minio-image
    fi

    printf 'C.3: Python verifier cross-runtime contract dependency\n'
    if [[ ! -x "$ci_root/services/math-verifier/.venv/bin/python" ]]; then
        python3 -m venv "$ci_root/services/math-verifier/.venv"
        "$ci_root/services/math-verifier/.venv/bin/python" -m pip install -e "$ci_root/services/math-verifier[dev]" || blocked C "Failed to provision Python verifier dependencies"
    fi

    printf 'C.4: Full Maven test suite\n'
    mkdir -p "$ci_out/maven-full-reports"
    printf '<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"/>\n' > "$ci_out/maven-settings.xml"
    (
        cd services/api
        safe_backend_run mvn -s "$ci_out/maven-settings.xml" -gs "$ci_out/maven-settings.xml" \
            -Dverifiedai.test.minio-image=verified-ai/minio-test:2024-07-16-3535197 \
            -Dspring.profiles.active=test \
            -Dsurefire.reportsDirectory="$ci_out/maven-full-reports" test
    )
}

gate_d() {
    # Gate D — Result reporting
    printf '=== Gate D: Generating Acceptance Evidence ===\n'
    git rev-parse HEAD > "$ci_out/head.txt" 2>/dev/null || printf 'UNKNOWN\n' > "$ci_out/head.txt"
    git branch --show-current > "$ci_out/branch.txt" 2>/dev/null || printf 'UNKNOWN\n' > "$ci_out/branch.txt"
    git status --short > "$ci_out/working-tree.txt" 2>/dev/null || true
    git diff --stat > "$ci_out/diff-stat.txt" 2>/dev/null || true

    python3 - "$ci_out" "$runner_mode" <<'PYREPORT'
import json, os, sys, xml.etree.ElementTree as ET
from pathlib import Path
from datetime import datetime, timezone

out = Path(sys.argv[1])
mode = sys.argv[2]

gates = {}
for g in ('A', 'B', 'C'):
    st_file = out / f"{g}.status"
    bl_file = out / f"{g}.blocked"
    status = st_file.read_text().strip() if st_file.exists() else "NOT_RUN"
    blocked_reason = bl_file.read_text().strip() if bl_file.exists() else None
    gates[g] = {"status": status, "blockedReason": blocked_reason}

# Parse test reports
test_totals = {"run": 0, "failures": 0, "errors": 0, "skipped": 0}
failure_inventory = []

report_dirs = [out / "maven-targeted-reports", out / "maven-full-reports"]
seen_cases = set()

for rdir in report_dirs:
    if not rdir.exists():
        continue
    for xml_path in rdir.glob("TEST-*.xml"):
        try:
            tree = ET.parse(xml_path)
            root = tree.getroot()
            for tc in root.iter("testcase"):
                cname = tc.get("classname", "")
                name = tc.get("name", "")
                key = f"{cname}.{name}"
                if key in seen_cases:
                    continue
                seen_cases.add(key)
                test_totals["run"] += 1
                fail = tc.find("failure")
                err = tc.find("error")
                skip = tc.find("skipped")
                if fail is not None:
                    test_totals["failures"] += 1
                    msg = fail.get("message", "Failure")
                    failure_inventory.append(f"{key}: {msg}")
                elif err is not None:
                    test_totals["errors"] += 1
                    msg = err.get("message", "Error")
                    failure_inventory.append(f"{key}: {msg}")
                elif skip is not None:
                    test_totals["skipped"] += 1
        except Exception:
            pass

# Check overall status
has_blocked = any(g["status"] == "BLOCKED" for g in gates.values())
has_fail = any(g["status"] == "FAIL" for g in gates.values()) or test_totals["failures"] > 0 or test_totals["errors"] > 0
all_selected_pass = True
required_gates = ('A', 'B', 'C') if mode == "FULL" else ('A', 'B')
for g in required_gates:
    if gates[g]["status"] != "PASS":
        all_selected_pass = False

if has_blocked:
    overall = "BLOCKED"
elif has_fail:
    overall = "FAIL"
elif all_selected_pass:
    overall = "PASS"
else:
    overall = "PARTIAL"

summary_data = {
    "task": "CI Backend and Security Scan Failure Remediation",
    "timestamp": datetime.now(timezone.utc).isoformat(),
    "mode": mode,
    "overall": overall,
    "head": (out / "head.txt").read_text().strip(),
    "branch": (out / "branch.txt").read_text().strip(),
    "gates": gates,
    "testTotals": test_totals,
    "failureInventory": failure_inventory
}

(out / "summary.json").write_text(json.dumps(summary_data, indent=2) + "\n")
(out / "failure-inventory.json").write_text(json.dumps({"failures": failure_inventory}, indent=2) + "\n")

md_lines = [
    "# CI Backend and Security Remediation Acceptance Summary",
    "",
    f"- **Overall Status**: `{overall}`",
    f"- **Execution Mode**: `{mode}`",
    f"- **Commit**: `{summary_data['head']}` (`{summary_data['branch']}`)",
    f"- **Timestamp**: `{summary_data['timestamp']}`",
    "",
    "## Gate Results",
    f"- **Gate A (Security)**: `{gates['A']['status']}`" + (f" ({gates['A']['blockedReason']})" if gates['A']['blockedReason'] else ""),
    f"- **Gate B (Targeted Backend)**: `{gates['B']['status']}`" + (f" ({gates['B']['blockedReason']})" if gates['B']['blockedReason'] else ""),
    f"- **Gate C (Full Backend CI Parity)**: `{gates['C']['status']}`" + (f" ({gates['C']['blockedReason']})" if gates['C']['blockedReason'] else ""),
    "",
    "## Test Execution Metrics",
    f"- **Total Tests Executed**: {test_totals['run']}",
    f"- **Failures**: {test_totals['failures']}",
    f"- **Errors**: {test_totals['errors']}",
    f"- **Skipped**: {test_totals['skipped']}",
]

if failure_inventory:
    md_lines.extend(["", "## Failure Inventory"] + [f"- `{f}`" for f in failure_inventory])

(out / "summary.md").write_text("\n".join(md_lines) + "\n")
print("\n" + "\n".join(md_lines) + "\n")
PYREPORT

    printf 'PASS\n' > "$ci_out/D.status"
    printf 'Evidence written to: %s\n' "$ci_out"
}

finish() {
    local incoming=$?
    trap - EXIT INT TERM
    set +e
    if [[ "$runner_selected" == *"D"* && ! -f "$ci_out/D.status" ]]; then
        gate_d
    elif [[ "$runner_selected" == *"D"* && "$(cat "$ci_out/D.status" 2>/dev/null)" == "NOT_RUN" ]]; then
        gate_d
    fi
    exit "$ci_exit"
}
trap finish EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

run_gate A gate_a
run_gate B gate_b
if [[ "$runner_mode" == "FULL" ]]; then
    run_gate C gate_c
fi
gate_d
