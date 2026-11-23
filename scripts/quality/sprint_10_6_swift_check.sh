#!/usr/bin/env bash
set -euo pipefail

if (( $# > 0 )); then
  echo "Usage: sprint_10_6_swift_check.sh" >&2
  exit 2
fi

cd "$(dirname "$0")/../.."
echo "PENDING_MANUAL physical device offline-to-online telemetry smoke check, staging P95 ingestion/query latency, owner production flag rollout sign-off"

for command in xcodebuild xcrun plutil python3; do
  command -v "$command" >/dev/null || { echo "BLOCKED_ENVIRONMENT missing $command" >&2; exit 2; }
done
xcrun --sdk iphonesimulator --show-sdk-path >/dev/null || {
  echo "BLOCKED_ENVIRONMENT install Xcode and an iOS simulator" >&2
  exit 2
}

PYTHON_BIN="python3"
if [[ -x "services/math-verifier/.venv/bin/python" ]] && services/math-verifier/.venv/bin/python -c "import yaml, jsonschema" >/dev/null 2>&1; then
  PYTHON_BIN="services/math-verifier/.venv/bin/python"
fi

report=".generated/sprint-10-6-ios/$(date -u +%Y%m%dT%H%M%SZ)-$$"
mkdir -p "$report"
trap 'echo "FAIL Sprint 10.6 iOS gate" >&2' ERR

plutil -lint apps/ios/VerifiedAI.xcodeproj/project.pbxproj
"$PYTHON_BIN" scripts/quality/validate_sprint_10_6_contracts.py | tee "$report/contracts.log"

xcodebuild test \
  -project apps/ios/VerifiedAI.xcodeproj \
  -scheme VerifiedAI \
  -destination "${IOS_SIM_DESTINATION:-platform=iOS Simulator,name=iPhone 16 Pro}" \
  -derivedDataPath "$report/DerivedData" \
  -resultBundlePath "$report/Sprint106Analytics.xcresult" \
  -only-testing:VerifiedAITests/AnalyticsEventCatalogAndSanitizerTests \
  -only-testing:VerifiedAITests/AnalyticsClientOfflineQueueTests \
  -only-testing:VerifiedAITests/AppRouterTests \
  -only-testing:VerifiedAITests/AccountSettingsViewModelTests \
  SWIFT_STRICT_CONCURRENCY=complete | tee "$report/xcode.log"

test -e "$report/Sprint106Analytics.xcresult"
grep -Eq 'Executed [1-9][0-9]* tests?, with 0 failures' "$report/xcode.log"
if grep -Eq '[1-9][0-9]* skipped' "$report/xcode.log"; then
  echo "Skipped XCTest cases detected in $report/xcode.log" >&2
  exit 1
fi

echo "PASS local Sprint 10.6 analytics simulator tests"
