#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."

for path in \
  apps/ios/VerifiedAI/Features/Exam/Domain/ExamIntegrityModels.swift \
  apps/ios/VerifiedAI/Features/Exam/Domain/ExamModels.swift \
  apps/ios/VerifiedAI/Features/Exam/Data/ExamAPIService.swift \
  apps/ios/VerifiedAI/Features/Exam/Data/ExamOfflineSQLiteStore.swift \
  apps/ios/VerifiedAI/Features/Exam/Presentation/ExamSessionViewModel.swift \
  apps/ios/VerifiedAI/Features/Exam/Presentation/ExamSessionView.swift \
  apps/ios/VerifiedAI/Features/Exam/Presentation/SpokenMathTurkishAccessibilityFormatter.swift; do
  test -f "$path"
  xcrun swiftc -frontend -parse "$path"
done

for src in ExamIntegrityModels SpokenMathTurkishAccessibilityFormatter; do
  rg -q "$src.swift in Sources" apps/ios/VerifiedAI.xcodeproj/project.pbxproj
done

for view_path in \
  apps/ios/VerifiedAI/Features/Exam/Presentation/ExamSessionView.swift \
  apps/ios/VerifiedAI/Features/Exam/Presentation/SpokenMathTurkishAccessibilityFormatter.swift; do
  if rg -n 'LinearGradient|RadialGradient|AngularGradient|MeshGradient|\.shadow\(|\.glassEffect\(' "$view_path"; then
    echo "Forbidden Concept B visual effect in $view_path"
    exit 1
  fi
done

plutil -lint apps/ios/VerifiedAI.xcodeproj/project.pbxproj
plutil -convert json -o /dev/null apps/ios/VerifiedAI/Resources/Localizable.xcstrings

for key in \
  exam.integrity.error.clock_resync \
  exam.integrity.error.proof_expired \
  exam.integrity.error.retry_conflict \
  exam.integrity.error.signature_required \
  exam.integrity.error.verification_needed \
  exam.integrity.status.pending_submit_recovery; do
  rg -q "\"$key\"" apps/ios/VerifiedAI/Resources/Localizable.xcstrings
done

for test_file in \
  SpokenMathTurkishAccessibilityFormatterTests \
  ExamTurkishYksAccessibilityTests \
  ExamSessionViewModelTests \
  ExamSessionSQLiteStoreTests; do
  test_path="apps/ios/VerifiedAITests/Exam/$test_file.swift"
  test -f "$test_path"
  rg -q "$test_file.swift in Sources" apps/ios/VerifiedAI.xcodeproj/project.pbxproj
  xcrun swiftc -frontend -parse "$test_path"
done

echo 'SPRINT_9_10_STATIC_CHECKS_PASSED_HOST_TESTS_STILL_REQUIRED'
