#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
report=".generated/sprint-10-1-owner/$(date -u +%Y%m%dT%H%M%SZ)-$$"
mkdir -p "$report"
echo "PENDING_MANUAL staging P95, physical VoiceOver, App Store Connect metadata and legal links"
gates=(preflight backend contracts documentation)
index=0
finish() {
  status=$?
  if (( status != 0 )); then
    echo "FAIL ${gates[index]}" | tee -a "$report/status"
    for ((i=index+1; i<${#gates[@]}; i++)); do echo "NOT_RUN ${gates[i]}" | tee -a "$report/status"; done
  fi
}
trap finish EXIT
for command in java python3 docker; do
  command -v "$command" >/dev/null || { echo "BLOCKED_ENVIRONMENT missing $command" >&2; exit 2; }
done
[[ -x ./mvnw ]] || { echo "BLOCKED_ENVIRONMENT Maven wrapper unavailable" >&2; exit 2; }
./mvnw --version >/dev/null || { echo "BLOCKED_ENVIRONMENT install Maven" >&2; exit 2; }
docker info >/dev/null || { echo "BLOCKED_ENVIRONMENT start Docker for PostgreSQL Testcontainers" >&2; exit 2; }
echo "PASS preflight" | tee "$report/status"
index=1
for suite in BillingOfferCatalogTest BillingOfferUpgradeTest EntitlementControllerTest AppleBillingApplicationServiceTest EntitlementApplicationServiceTest ModulithArchitectureTest; do
  ./mvnw -f services/api/pom.xml "-Dtest=$suite" test | tee "$report/$suite.log"
  python3 - "$suite" <<'PY'
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
files = list(Path("services/api/target/surefire-reports").glob("TEST-*." + sys.argv[1] + ".xml"))
if len(files) != 1: sys.exit("Missing unique Surefire report")
root = ET.parse(files[0]).getroot()
if int(root.get("tests", 0)) == 0 or any(int(root.get(key, 0)) for key in ("skipped", "errors", "failures")):
    sys.exit("Incomplete Surefire suite")
PY
done
echo "PASS backend" | tee -a "$report/status"
index=2
python3 scripts/quality/validate_json_schemas.py | tee "$report/schemas.log"
python3 scripts/quality/check_contracts.py | tee "$report/openapi.log"
echo "PASS contracts" | tee -a "$report/status"
index=3
python3 scripts/quality/docs_check.py | tee "$report/docs.log"
echo "PASS documentation" | tee -a "$report/status"
