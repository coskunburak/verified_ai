#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
case "${1:---VerifyHost}" in
  --VerifyHost|--verify-host|--full) ;;
  *) echo 'Usage: sprint_9_9_owner_acceptance.sh --VerifyHost'; exit 2 ;;
esac
python3 scripts/quality/validate_sprint_9_9_contracts.py
python3 scripts/quality/validate_json_schemas.py
python3 scripts/quality/check_contracts.py
bash scripts/quality/sprint_9_9_swift_check.sh
xcodebuild test -project apps/ios/VerifiedAI.xcodeproj -scheme VerifiedAI \
  -destination "${IOS_SIM_DESTINATION:-platform=iOS Simulator,name=iPhone 16 Pro}" \
  -derivedDataPath services/api/target/sprint99/DerivedData \
  -only-testing:VerifiedAITests/ExamProgressHistoryViewModelTests
python3 scripts/quality/docs_check.py
echo 'SPRINT_9_9_IOS_AND_CONTRACT_GATES_PASSED_REVIEW_RECORDED_BACKEND_EVIDENCE_BEFORE_RELEASE'
