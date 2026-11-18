#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
test -f apps/ios/VerifiedAI/Features/Exam/Domain/PreExamModels.swift
rg -q "PreExamModels.swift in Sources" apps/ios/VerifiedAI.xcodeproj/project.pbxproj
xcrun swiftc -frontend -parse apps/ios/VerifiedAI/Features/Exam/Domain/PreExamModels.swift
for path in \
  apps/ios/VerifiedAI/Core/Networking/APIClient.swift \
  apps/ios/VerifiedAI/Core/Networking/HTTPRequest.swift \
  apps/ios/VerifiedAI/Core/Networking/HTTPResponse.swift \
  apps/ios/VerifiedAI/Features/Exam/Data/ExamAPIService.swift \
  apps/ios/VerifiedAI/Features/Exam/Data/ExamOfflineSQLiteStore.swift; do
  test -f "$path"
  xcrun swiftc -frontend -parse "$path"
done
for file in PreExamCramView PreExamCramViewModel PersonalCheatSheetPDFRenderer; do
  path="apps/ios/VerifiedAI/Features/Exam/Presentation/$file.swift"
  test -f "$path"
  if rg -n 'LinearGradient|RadialGradient|AngularGradient|MeshGradient|\.shadow\(' "$path"; then
    echo "Forbidden H-15 visual effect in $path"; exit 1
  fi
  rg -q "$file.swift in Sources" apps/ios/VerifiedAI.xcodeproj/project.pbxproj
  xcrun swiftc -frontend -parse "$path"
done
plutil -lint apps/ios/VerifiedAI.xcodeproj/project.pbxproj
for name in PreExamCramViewModelTests PersonalCheatSheetPDFRendererTests APIClientConditionalGETTests; do
  rg -q "$name.swift in Sources" apps/ios/VerifiedAI.xcodeproj/project.pbxproj
  xcrun swiftc -frontend -parse "apps/ios/VerifiedAITests/Exam/$name.swift"
done
echo 'H15_STATIC_CHECKS_PASSED_HOST_TESTS_STILL_REQUIRED'
