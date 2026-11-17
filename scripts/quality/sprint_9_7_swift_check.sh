#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p services/api/target/sprint97/swift
# Each invocation gets a fresh module cache; copied/moved PCH files embed absolute paths.
MODULE_CACHE="$(mktemp -d "$PWD/services/api/target/sprint97/swift/module-cache.XXXXXX")"
trap 'rm -rf "$MODULE_CACHE"' EXIT
CORE_EXAM_SOURCES=(

    apps/ios/VerifiedAI/Core/DesignSystem/Tokens/ColorTokens.swift
    apps/ios/VerifiedAI/Core/DesignSystem/Tokens/TypographyTokens.swift
    apps/ios/VerifiedAI/Core/DesignSystem/Tokens/SpacingTokens.swift
    apps/ios/VerifiedAI/Core/DesignSystem/Tokens/RadiusTokens.swift
    apps/ios/VerifiedAI/Core/DesignSystem/Components/DesignSystemComponents.swift
    apps/ios/VerifiedAI/Core/DesignSystem/Components/DesignSystemGalleryPreview.swift
    apps/ios/VerifiedAI/Core/Networking/APIClient.swift
    apps/ios/VerifiedAI/Core/Networking/HTTPRequest.swift
    apps/ios/VerifiedAI/Core/Networking/HTTPResponse.swift
    apps/ios/VerifiedAI/Core/Networking/HTTPMethod.swift
    apps/ios/VerifiedAI/Core/Networking/Endpoint.swift
    apps/ios/VerifiedAI/Core/Networking/NetworkError.swift
    apps/ios/VerifiedAI/Core/Networking/ProblemDetails.swift
    apps/ios/VerifiedAI/Core/Networking/AuthTokenProvider.swift
    apps/ios/VerifiedAI/Core/Networking/RequestInterceptor.swift
    apps/ios/VerifiedAI/Core/Observability/CorrelationContext.swift
    apps/ios/VerifiedAI/Core/Observability/AppLogger.swift
    apps/ios/VerifiedAI/Features/Exam/Domain/ExamModels.swift
    apps/ios/VerifiedAI/Features/Exam/Data/ExamAPIService.swift
    apps/ios/VerifiedAI/Features/Exam/Data/ExamOfflineSQLiteStore.swift
    apps/ios/VerifiedAI/Features/Exam/Presentation/ExamCatalogAndTargetViewModel.swift
    apps/ios/VerifiedAI/Features/Exam/Presentation/CandidateProfileViewModel.swift
    apps/ios/VerifiedAI/Features/Exam/Presentation/ExamBlueprintAssemblyViewModel.swift
    apps/ios/VerifiedAI/Features/Exam/Presentation/ExamCatalogAndTargetView.swift
    apps/ios/VerifiedAI/Features/Exam/Presentation/CandidateGoalSetupView.swift
    apps/ios/VerifiedAI/Features/Exam/Presentation/SyllabusSnapToPlanSheet.swift
    apps/ios/VerifiedAI/Features/Exam/Presentation/ExamBlueprintAssemblyView.swift
    apps/ios/VerifiedAI/Features/Exam/Presentation/ExamSessionViewModel.swift
    apps/ios/VerifiedAI/Features/Exam/Presentation/ExamSessionView.swift
    apps/ios/VerifiedAI/Features/Exam/Presentation/ExamScoreSummaryViewModel.swift
    apps/ios/VerifiedAI/Features/Exam/Presentation/ExamScoreSummaryView.swift
    apps/ios/VerifiedAI/Features/Exam/Presentation/ExamReadinessViewModel.swift
    apps/ios/VerifiedAI/Features/Exam/Presentation/ExamReadinessHubView.swift
    apps/ios/VerifiedAI/Features/Exam/Presentation/PostExamAttributionViewModel.swift
    apps/ios/VerifiedAI/Features/Exam/Presentation/PostExamAttributionView.swift
    apps/ios/VerifiedAI/Features/Exam/Presentation/PostExamRemediationDestination.swift
    apps/ios/VerifiedAI/Features/StudyPlan/Practice/PracticeSessionModels.swift
    apps/ios/VerifiedAI/Features/StudyPlan/Practice/PracticeSQLiteCache.swift
    apps/ios/VerifiedAI/Features/StudyPlan/Practice/PracticeSessionViewModel.swift
    apps/ios/VerifiedAI/Features/StudyPlan/Practice/PracticeSessionView.swift
    apps/ios/VerifiedAI/Features/StudyPlan/Practice/PracticeQuestionCardView.swift
    apps/ios/VerifiedAI/Features/Tutor/Domain/MicroLessonModels.swift
    apps/ios/VerifiedAI/Features/Tutor/Data/MicroLessonSQLiteCache.swift
    apps/ios/VerifiedAI/Features/Tutor/Presentation/MicroLessonViewModel.swift
    apps/ios/VerifiedAI/Features/Tutor/Presentation/MicroLessonDrawerView.swift
)
xcrun swiftc -parse "${CORE_EXAM_SOURCES[@]}" apps/ios/VerifiedAITests/Exam/PostExamAttributionViewModelTests.swift
# Native practice views require the app's SwiftMath package. Parse all listed views here;
# the required xcodebuild host gate compiles/executes them with the real package.
MODEL_API_SOURCES=()
for source in "${CORE_EXAM_SOURCES[@]}"; do
  case "$source" in
    */Features/*View.swift|*/PostExamRemediationDestination.swift) ;;
    *) MODEL_API_SOURCES+=("$source") ;;
  esac
done
xcrun --sdk iphonesimulator swiftc -typecheck -swift-version 6 -strict-concurrency=complete -DDEBUG \
  -module-cache-path "$MODULE_CACHE" -target arm64-apple-ios17.0-simulator "${MODEL_API_SOURCES[@]}"
printf 'PASS: Sprint 9.7 Swift parse and model/API strict-concurrency typecheck; native views and XCTest require the host xcodebuild gate\n'
