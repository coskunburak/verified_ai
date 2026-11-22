#!/usr/bin/env bash
set -euo pipefail

if (( $# > 0 )); then
  echo "Usage: sprint_10_2_swift_check.sh" >&2
  exit 2
fi

cd "$(dirname "$0")/../.."
echo "PENDING_MANUAL physical VoiceOver, 320pt, xSmall-AX3, light/dark, Bold Text, Reduce Motion, EN/TR, live StoreKit Connect commerce"

for command in xcodebuild xcrun plutil python3; do
  command -v "$command" >/dev/null || { echo "BLOCKED_ENVIRONMENT missing $command" >&2; exit 2; }
done
xcrun --sdk iphonesimulator --show-sdk-path >/dev/null || {
  echo "BLOCKED_ENVIRONMENT install Xcode and an iOS simulator" >&2
  exit 2
}

report=".generated/sprint-10-2-ios/$(date -u +%Y%m%dT%H%M%SZ)-$$"
mkdir -p "$report"
trap 'echo "FAIL Sprint 10.2 iOS gate" >&2' ERR

plutil -lint apps/ios/VerifiedAI.xcodeproj/project.pbxproj
python3 scripts/quality/validate_sprint_10_2_contracts.py | tee "$report/contracts.log"

xcodebuild test \
  -project apps/ios/VerifiedAI.xcodeproj \
  -scheme VerifiedAI \
  -destination "${IOS_SIM_DESTINATION:-platform=iOS Simulator,name=iPhone 16 Pro}" \
  -derivedDataPath "$report/DerivedData" \
  -resultBundlePath "$report/Sprint102Subscription.xcresult" \
  -only-testing:VerifiedAITests/EntitlementViewModelTests \
  -only-testing:VerifiedAITests/PaywallViewModelTests \
  -only-testing:VerifiedAITests/LearningOsEntitlementV2DecoderTests \
  -only-testing:VerifiedAITests/LearningOsCapabilityGateTests \
  -only-testing:VerifiedAITests/LearningOsEntitlementRefreshTests \
  -only-testing:VerifiedAITests/LearningOsDowngradePresentationTests \
  SWIFT_STRICT_CONCURRENCY=complete | tee "$report/xcode.log"

test -e "$report/Sprint102Subscription.xcresult"
grep -Eq 'Executed [1-9][0-9]* tests?, with 0 failures' "$report/xcode.log"
if grep -Eq '[1-9][0-9]* skipped' "$report/xcode.log"; then
  echo "Skipped XCTest cases detected in $report/xcode.log" >&2
  exit 1
fi

echo "PASS local Sprint 10.2 subscription simulator tests"
