# Sprint 3.7 Execution Report

## Status

Complete for current Phase 3 data stores. Future asset/problem/attempt/mastery/tutor/AI stores are covered by the lifecycle contributor contract and tracked by `TD-PRIV-001` until those stores exist.

## Implemented Backend

- Added V006 migration for `data_exports`, `privacy_events`, and deletion account states.
- Added current-user account endpoints under `/api/v1/me/account`.
- Added data export request/status/download endpoints under `/api/v1/me/data-exports`.
- Added deletion request/status/confirmation endpoints under `/api/v1/me/deletion-request`.
- Added `AccountDataLifecycleContributor` and current contributors for LearningProfile and Billing.
- Account deletion requests mark the user `DELETION_REQUESTED`; profile/billing mutations require `ACTIVE`.
- Confirmed deletion revokes active sessions and refresh tokens, deletes profile state, removes commerce account token, revokes entitlement, and retains minimized billing/security audit evidence.
- Deleted Apple identity mappings remain tombstoned so the same Apple subject cannot silently create a fresh account.

## Implemented iOS

- Added `Features/Account` domain models, API service, `AccountSettingsViewModel`, and `AccountSettingsView`.
- Wired account settings into authenticated home and `RootView`.
- Confirmed backend deletion clears Keychain-backed session state and resets authenticated view models.
- Added iOS account settings tests for load, export/download, confirmed deletion local session clearing, and offline mutation blocking.

## API and Data Contract

- Updated `public-api.yaml` with account, data-export, and deletion endpoints and schemas.
- Updated API, data model, lifecycle, privacy, iOS, operations, and error-taxonomy docs.
- Export JSON includes account/session/profile/billing categories and excludes token secrets, token hashes, raw Apple signed payloads, raw payment credentials, internal fraud signals, and unrestricted telemetry.

## Validation

- Backend full suite: `mvn -q test` passed, 42 tests.
- iOS full test invocation: `xcodebuild test -project apps/ios/VerifiedAI.xcodeproj -scheme VerifiedAI -destination "platform=iOS Simulator,name=iPhone 16 Pro"` passed, 30 tests.
- OpenAPI YAML parse passed.

## Remaining Debt

- `TD-PRIV-001` tracks lifecycle contributors for future Phase 4+ stores.
- External Apple validation debts remain open and are not Sprint 3.7 blockers.
