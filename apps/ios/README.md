# VerifiedAI iOS

SwiftUI application shell for the Verified AI Learning Platform.

The iOS client owns presentation, navigation, local cache/projection, and secure token storage. It does not call AI providers, PostgreSQL, Redis, object storage credentials, or the internal math verifier directly.

## Local Validation

```sh
xcodebuild test -project apps/ios/VerifiedAI.xcodeproj -scheme VerifiedAI -destination 'platform=iOS Simulator,name=iPhone 16 Pro'
```

