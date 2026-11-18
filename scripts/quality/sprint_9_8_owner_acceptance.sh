#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
case "${1:---VerifyHost}" in
  --VerifyHost|--verify-host|--full) ;;
  *) echo 'Usage: sprint_9_8_owner_acceptance.sh --VerifyHost'; exit 2 ;;
esac
bash scripts/quality/sprint_9_8_swift_check.sh
xcodebuild test -project apps/ios/VerifiedAI.xcodeproj -scheme VerifiedAI \
  -destination "${IOS_SIM_DESTINATION:-platform=iOS Simulator,name=iPhone 16 Pro}" \
  -derivedDataPath services/api/target/sprint98/DerivedData \
  -only-testing:VerifiedAITests/PreExamCramViewModelTests \
  -only-testing:VerifiedAITests/PersonalCheatSheetPDFRendererTests \
  -only-testing:VerifiedAITests/APIClientConditionalGETTests \
  -only-testing:VerifiedAITests/APIClientTests
python3 scripts/quality/docs_check.py
echo 'SPRINT_9_8_REMAINING_IOS_GATES_PASSED_REVIEW_RECORDED_BACKEND_EVIDENCE_BEFORE_RELEASE'
