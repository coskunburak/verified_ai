#!/usr/bin/env bash
set -euo pipefail

if (( $# > 1 )); then
  echo "Usage: sprint_10_2_owner_acceptance.sh --full" >&2
  exit 2
fi

case "${1:---full}" in
  --full) ;;
  *)
    echo "Usage: sprint_10_2_owner_acceptance.sh --full" >&2
    exit 2
    ;;
esac

cd "$(dirname "$0")/../.."
report=".generated/sprint-10-2-owner/$(date -u +%Y%m%dT%H%M%SZ)-$$"
mkdir -p "$report"

echo "PENDING_MANUAL staging P95 latency, physical VoiceOver EN/TR 320pt AX3 light/dark, App Store Connect commercial metadata and HTTPS legal links, rollout sign-off"

gates=(preflight backend contracts documentation)
index=0
finish() {
  local status=$?
  if (( status != 0 )); then
    echo "FAIL ${gates[index]}" | tee -a "$report/status"
    for ((i = index + 1; i < ${#gates[@]}; i++)); do
      echo "NOT_RUN ${gates[i]}" | tee -a "$report/status"
    done
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
suites=(
  LearningOsCapabilityPolicyTest
  TierResolutionPolicyTest
  EntitlementEffectiveAccessTest
  EntitlementApplicationServiceTest
  BillingOfferCatalogTest
  ModulithArchitectureTest
  BillingOfferUpgradeTest
  Sprint102EntitlementMigrationTest
  AppleBillingApplicationServiceTest
  AppleBillingEntitlementTransitionTest
  EntitlementDowngradeLifecycleTest
  EntitlementControllerTest
  LearningOsEntitlementControllerTest
  LearningOsFeatureGuardMockMvcTest
  LearningOsNonPunitiveDowngradeTest
)

for suite in "${suites[@]}"; do
  attempt=1
  while true; do
    if ./mvnw -f services/api/pom.xml "-Dtest=$suite" test | tee "$report/$suite.log"; then
      break
    fi
    if (( attempt < 3 )) && grep -Eq 'Could not connect to Ryuk at|Timed out waiting for Ryuk container to start' "$report/$suite.log"; then
      echo "RETRY $suite after transient Testcontainers Ryuk port-forwarding timeout (attempt $attempt/3)" >&2
      docker ps -aq --filter "label=org.testcontainers.ryuk=true" | while read -r cid; do
        [[ -n "$cid" ]] && docker rm -f "$cid" >/dev/null 2>&1 || true
      done
      attempt=$((attempt + 1))
      sleep 3
      continue
    fi
    exit 1
  done
  python3 - "$suite" <<'PY'
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

suite = sys.argv[1]
files = list(Path("services/api/target/surefire-reports").glob("TEST-*." + suite + ".xml"))
if len(files) != 1:
    sys.exit(f"Missing unique Surefire report for {suite}: {files}")
root = ET.parse(files[0]).getroot()
tests = int(root.get("tests", 0))
skipped = int(root.get("skipped", 0))
errors = int(root.get("errors", 0))
failures = int(root.get("failures", 0))
if tests <= 0 or skipped != 0 or errors != 0 or failures != 0:
    sys.exit(f"Incomplete Surefire suite {suite}: tests={tests} skipped={skipped} errors={errors} failures={failures}")
PY
done
echo "PASS backend" | tee -a "$report/status"

index=2
python3 scripts/quality/validate_json_schemas.py | tee "$report/schemas.log"
python3 scripts/quality/validate_sprint_10_2_contracts.py | tee "$report/sprint102_contracts.log"
python3 scripts/quality/check_contracts.py | tee "$report/openapi.log"
echo "PASS contracts" | tee -a "$report/status"

index=3
python3 scripts/quality/docs_check.py | tee "$report/docs.log"
echo "PASS documentation" | tee -a "$report/status"

echo "LOCAL_REPORT_DIR=$report"
echo "SPRINT_10_2_OWNER_LOCAL_AUTOMATED_GATES_PASSED_MANUAL_GATES_PENDING"
