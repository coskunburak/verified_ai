#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
echo "PENDING_MANUAL physical VoiceOver, 320pt, AX3, light/dark, EN/TR"
command -v xcodebuild >/dev/null && xcrun --sdk iphonesimulator --show-sdk-path >/dev/null || {
  echo "BLOCKED_ENVIRONMENT install Xcode and an iOS simulator" >&2
  exit 2
}
report=".generated/sprint-10-1-ios/$(date -u +%Y%m%dT%H%M%SZ)-$$"
mkdir -p "$report"
trap 'echo "FAIL iOS gate" >&2' ERR
plutil -lint apps/ios/VerifiedAI.xcodeproj/project.pbxproj
python3 scripts/quality/validate_sprint_10_1_contracts.py
xcodebuild test -project apps/ios/VerifiedAI.xcodeproj -scheme VerifiedAI \
  -destination "${IOS_SIM_DESTINATION:-platform=iOS Simulator,name=iPhone 16 Pro}" \
  -derivedDataPath "$report/DerivedData" -resultBundlePath "$report/Subscription.xcresult" \
  -only-testing:VerifiedAITests/PaywallViewModelTests -only-testing:VerifiedAITests/EntitlementViewModelTests \
  SWIFT_STRICT_CONCURRENCY=complete | tee "$report/xcode.log"
grep -Eq 'Executed [1-9][0-9]* tests?, with 0 failures' "$report/xcode.log"
echo "PASS local subscription simulator tests"
