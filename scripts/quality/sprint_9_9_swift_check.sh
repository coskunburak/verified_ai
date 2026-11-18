#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."

test -f apps/ios/VerifiedAI/Features/Exam/Domain/ExamProgressModels.swift
rg -q "ExamProgressModels.swift in Sources" apps/ios/VerifiedAI.xcodeproj/project.pbxproj
xcrun swiftc -frontend -parse apps/ios/VerifiedAI/Features/Exam/Domain/ExamProgressModels.swift

for path in \
  apps/ios/VerifiedAI/Features/Exam/Data/ExamAPIService.swift \
  apps/ios/VerifiedAI/Features/Exam/Data/ExamOfflineSQLiteStore.swift \
  apps/ios/VerifiedAI/Features/Exam/Presentation/ExamReadinessViewModel.swift \
  apps/ios/VerifiedAI/Features/Exam/Presentation/ExamReadinessHubView.swift \
  apps/ios/VerifiedAI/Features/Exam/Presentation/ExamScoreSummaryViewModel.swift \
  apps/ios/VerifiedAI/Features/Exam/Presentation/ExamScoreSummaryView.swift; do
  test -f "$path"
  xcrun swiftc -frontend -parse "$path"
done

for file in ExamProgressHistoryViewModel ExamProgressHistoryView; do
  path="apps/ios/VerifiedAI/Features/Exam/Presentation/$file.swift"
  test -f "$path"
  if rg -n 'LinearGradient|RadialGradient|AngularGradient|MeshGradient|\.shadow\(|\.glassEffect\(' "$path"; then
    echo "Forbidden Concept B visual effect in $path"; exit 1
  fi
  rg -q "$file.swift in Sources" apps/ios/VerifiedAI.xcodeproj/project.pbxproj
  xcrun swiftc -frontend -parse "$path"
done

plutil -lint apps/ios/VerifiedAI.xcodeproj/project.pbxproj
plutil -convert json -o /dev/null apps/ios/VerifiedAI/Resources/Localizable.xcstrings
rg -q '"exam\.progress\.title"' apps/ios/VerifiedAI/Resources/Localizable.xcstrings

rg -q "ExamProgressHistoryViewModelTests.swift in Sources" apps/ios/VerifiedAI.xcodeproj/project.pbxproj
xcrun swiftc -frontend -parse "apps/ios/VerifiedAITests/Exam/ExamProgressHistoryViewModelTests.swift"

echo 'SPRINT_9_9_STATIC_CHECKS_PASSED_HOST_TESTS_STILL_REQUIRED'
