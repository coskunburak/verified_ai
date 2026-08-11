# iOS Architecture and File Hierarchy

## Stack

- Swift
- SwiftUI
- Observation
- async/await structured concurrency
- URLSession
- SwiftData
- Vision / VisionKit / AVFoundation
- StoreKit 2
- Keychain
- OSLog

## Architecture

Feature-first MVVM with explicit UseCase/Repository seams for behavior that benefits from testability or multiple data sources.

Flow:
`View → ViewModel → UseCase → Repository Protocol → Repository Implementation → API/Local`

Avoid ceremony for static/simple screens.

## Root hierarchy

```text
apps/ios/
├── VerifiedAI.xcodeproj
├── VerifiedAI/
│   ├── App/
│   │   ├── VerifiedAIApp.swift
│   │   ├── AppEnvironment.swift
│   │   ├── AppDependencies.swift
│   │   ├── AppRouter.swift
│   │   ├── DeepLinkRouter.swift
│   │   └── AppLifecycleHandler.swift
│   ├── Core/
│   │   ├── Networking/
│   │   │   ├── APIClient.swift
│   │   │   ├── Endpoint.swift
│   │   │   ├── HTTPMethod.swift
│   │   │   ├── HTTPRequest.swift
│   │   │   ├── HTTPResponse.swift
│   │   │   ├── NetworkError.swift
│   │   │   ├── NetworkMonitor.swift
│   │   │   ├── RequestInterceptor.swift
│   │   │   ├── AuthTokenProvider.swift
│   │   │   └── DTO/
│   │   ├── Persistence/
│   │   │   ├── PersistenceController.swift
│   │   │   ├── MigrationPlan.swift
│   │   │   ├── CachePolicy.swift
│   │   │   └── Models/
│   │   ├── Security/
│   │   │   ├── KeychainStore.swift
│   │   │   ├── SecureStorage.swift
│   │   │   └── DeviceSecurity.swift
│   │   ├── Observability/
│   │   │   ├── AppLogger.swift
│   │   │   ├── AnalyticsClient.swift
│   │   │   ├── CrashReporter.swift
│   │   │   └── PerformanceTracer.swift
│   │   ├── DesignSystem/
│   │   │   ├── Tokens/
│   │   │   ├── Components/
│   │   │   ├── Motion/
│   │   │   └── Accessibility/
│   │   └── Localization/
│   ├── SharedDomain/
│   │   ├── Identifiers/
│   │   ├── Problem/
│   │   ├── Curriculum/
│   │   ├── Learning/
│   │   └── Billing/
│   ├── Features/
│   │   ├── Account/
│   │   ├── Authentication/
│   │   ├── Onboarding/
│   │   ├── Home/
│   │   ├── ProblemCapture/
│   │   ├── ProblemReview/
│   │   ├── ProblemSolve/
│   │   ├── Solution/
│   │   ├── Tutor/
│   │   ├── Attempt/
│   │   ├── MistakeBook/
│   │   ├── Mastery/
│   │   ├── StudyPlan/
│   │   ├── Exam/
│   │   ├── Library/
│   │   └── Subscription/
│   └── Resources/
│       ├── Assets.xcassets
│       ├── Localizable.xcstrings
│       └── PrivacyInfo.xcprivacy
├── VerifiedAITests/
└── VerifiedAIUITests/
```

## Feature pattern

Example `ProblemCapture`:

```text
ProblemCapture/
├── Domain/
│   ├── ProblemCaptureModels.swift
│   └── ProblemCapturePorts.swift
├── Data/
│   ├── AVFoundationProblemCameraClient.swift
│   ├── CapturedAssetStore.swift
│   └── CaptureQualityAnalyzer.swift
├── Presentation/
│   ├── ProblemCaptureView.swift
│   └── ProblemCaptureViewModel.swift
```

Sprint 4.1 implements the local pre-upload subset of `ProblemCapture`. `CapturedAsset` is an iOS temporary artifact with source, file URL, preview bytes, dimensions, crop metadata, and local quality assessment. It is not a backend `ProblemAsset`, not a canonical `Problem`, and not a `ProblemSession`.

## Dependency injection

`AppDependencies` composes APIClient, repositories, secure storage, analytics, purchases and feature services. Views never reach into a global service locator.

Current composition includes `AuthenticationAPI`, `AccountPrivacyAPI`, `LearningProfileAPI`, `EntitlementAPI`, `ProblemReviewAPI`, `ProblemSessionHistoryAPI`, `AuthenticationSessionStore`, `EntitlementDisplayCache`, `SwiftDataProblemSessionCache`, `AVFoundationProblemCameraClient`, `DefaultCapturedAssetStore`, and `DefaultCaptureQualityAnalyzer`. `RootView` owns the authenticated bootstrap order: restore session, load learning profile, route incomplete users through onboarding, then load entitlement before rendering the authenticated home shell. Account settings, problem capture, and problem history are presented from authenticated home. Account settings clear local session state after confirmed backend deletion; problem capture now progresses from local capture/import through upload, preprocessing, recognition, parse status, Sprint 4.8 parse review, Sprint 4.6 canonicalization command, and Sprint 4.7 classification command without creating solve or verification result UI. Sprint 4.9 history/resume loads cached session summaries first, refreshes authoritative history/detail from the backend, and invokes only existing stage-specific commands when the backend-derived `nextAction` requires an explicit user retry.

## Navigation

Use typed app-level routes for major flows. Feature-local navigation remains inside the feature. Deep links pass through auth/authorization checks.

## State design

Prefer explicit state enums over unrelated booleans.

Sprint 3.3/3.4 implemented examples:
- `OnboardingState` separates idle/loading/needs-onboarding/saving/ready/offline/failed.
- `EntitlementState` separates idle/loading/ready/offline-cached/failed.

Sprint 4.1 implemented `ProblemCaptureState` for idle/source selection/camera permission/camera ready/capturing/importing/processing/reviewing/editing crop/accepted/recoverable failure/terminal failure.
Sprint 4.4 extends `ProblemAssetUploadState` with starting recognition, recognizing, recognized, recognition review required, and recognition failure states. Polling is bounded and user-facing copy says "Reading the problem", not "Solving".
Sprint 4.7 extends the upload workflow with explicit canonicalizing/canonicalized/canonicalization-failed and starting-classification/classifying/classified/classification-review-required/classification-unsupported/classification-failed states. The client displays backend classification status, skill, difficulty, and confidence band but does not derive taxonomy, difficulty, confidence, or review status locally.
Sprint 4.8 adds `ProblemReviewState` for loading, editing, submitting, submitted, conflict, revision history, offline, and failed states. The iOS client can edit normalized parser-level fields and submit a correction with an idempotency key, but the backend remains authoritative for ownership, semantic validation, selected parse mutation, and stale revision conflicts.
Sprint 4.9 adds `ProblemSessionHistoryViewModel` state for offline cached history, server refresh, pagination, detail reconnect, active job refresh, exact-stage retry, and recoverable failure presentation. The client treats `ProblemSessionStage` and `ProblemSessionNextAction` as closed backend contracts and must not synthesize hidden pipeline actions on foreground/reconnect.
- `AccountSettingsState` separates idle/loading/exporting/deletion-requested/confirming-deletion/deleted/offline/failed.
- `CapabilityGate` consumes an `Entitlement` projection for presentation only; backend capability checks remain authoritative.

Example:
```swift
enum SolutionScreenState {
    case loading(stage: SolveStage)
    case loaded(SolutionViewData)
    case recoverableError(AppError)
    case fatalError(AppError)
}
```

## DTO/domain/view separation

Network DTO is not the domain model. Domain model is not necessarily the exact display model. Mapping gives API evolution and testing flexibility.

## Concurrency

- UI mutation on MainActor.
- Repositories async.
- Cancellation propagates when user leaves polling/tutor flows.
- Avoid detached tasks without lifecycle ownership.

<!-- HYBRID_AI_STRATEGY_V3:START -->
## iOS/model-independence rule

No feature, DTO, ViewModel or persisted client state should require a specific AI provider/model name to function. The app consumes product-semantic solution, verification, tutor and usage-limit contracts.

Provider/model routing is a backend concern. This preserves remote route changes, fallbacks and future proprietary models without App Store releases.
<!-- HYBRID_AI_STRATEGY_V3:END -->

## V4 canonical hierarchy alignment

For exhaustive per-feature filenames, Core platform subtrees, test mirrors, and placement rules, `quality/56_COMPLETE_PRODUCTION_REPOSITORY_FILE_HIERARCHY.md` is authoritative. This document governs iOS architectural intent; the exhaustive hierarchy governs exact repository placement. `quality/66_FILE_PLACEMENT_OWNERSHIP_AND_DEPENDENCY_MATRIX.md` resolves ambiguous placements.
