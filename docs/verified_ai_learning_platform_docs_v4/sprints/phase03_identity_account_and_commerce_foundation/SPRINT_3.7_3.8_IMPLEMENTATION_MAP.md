# Sprint 3.7/3.8 Implementation Map

## Status

Sprint 3.7 = Complete for current Phase 3 data stores.
Sprint 3.8 = Complete for current Phase 3 identity, billing, privacy, and abuse surfaces.
Phase 3 = Complete with external Apple validation debt still open as launch-readiness debt.

## NotebookLM Evidence

NotebookLM MCP notebook `00e25adc-dee0-4a9c-92d8-6c473d1ad2ac` was queried before implementation. Result used:
- account states come from `User.status`; disabled/deleted users must be blocked from learning actions;
- deletion locks account, revokes sessions/token families, stops personalization, deletes/anonymizes PII/profile/object assets/learning records, and retains minimized legal/billing/security evidence;
- exports include LearningProfile and user-owned history/evidence, but exclude tokens, secrets, internal telemetry, and fraud signals;
- authorization derives ownership from the authenticated principal, not client-supplied user IDs;
- rate limiting should combine user/IP/endpoint/cost/risk dimensions and protect auth, upload, solve, tutor, import, and billing sync.

NotebookLM did not return concrete `CAP-*`, `REQ-*`, or `TD-*` passages, so local canonical docs remained authoritative for IDs and status.

## CAP Mapping

| Capability | Status after Sprint 3.7/3.8 |
|---|---|
| `CAP-ID-001` | Partial; cryptographic/local implementation complete, real Apple sandbox/device validation remains `TD-AUTH-001`. |
| `CAP-ID-002` | Complete; rotation, revocation, reuse detection, logout, Keychain storage, and single-flight refresh remain validated. |
| `CAP-ID-003` | Partial; backend session revocation and revoked-token checks are implemented, user-visible device/session management remains later scope. |
| `CAP-PROFILE-001` | Complete; deletion now deletes profile rows and export includes profile data. |
| `CAP-BILL-001` | Complete; deletion revokes entitlement and removes app account token. |
| `CAP-BILL-002` | Partial; external App Store sandbox purchase evidence remains `TD-BILL-001`. |
| `CAP-BILL-003` | Partial; external App Store Server API/webhook validation remains `TD-BILL-002` and `TD-BILL-003`. |
| `CAP-BILL-004` | Partial; explicit lifecycle state machine exists, external ordering/retry evidence remains `TD-BILL-004`. |
| `CAP-PRIV-001` | Partial; current Phase 3 stores have export/deletion coverage, future Phase 4+ data stores must add lifecycle contributors. |

## REQ Mapping

| Requirement | Evidence |
|---|---|
| `REQ-AUTH-001` | Full backend suite and iOS tests continue to validate refresh/session lifecycle. |
| `REQ-AUTH-002` | `/me` endpoints derive user from bearer principal; access-token session-state filter blocks revoked/non-active accounts. |
| `REQ-PROFILE-001` | LearningProfile remains separate and contributes to export/delete lifecycle. |
| `REQ-BILL-001` | Backend remains entitlement authority; deletion revokes entitlement. |
| `REQ-BILL-002` | Explicit billing states remain durable and tested. |
| `REQ-BILL-003` | Backend-only Apple evidence interpretation remains; external Apple validation still open. |
| `REQ-PRIV-002` | Current identity/profile/session/billing stores export/delete through lifecycle contributors. |
| `REQ-DATA-001` | V006 Flyway migration validates on Testcontainers PostgreSQL. |
| `REQ-DATA-002` | Redis owns only disposable rate-limit counters; denied/degraded-open paths are tested. |

## TD Mapping

| Debt | Status |
|---|---|
| `TD-AUTH-001` | Open; real Apple sandbox/device validation not available locally. |
| `TD-AUTH-002` | Closed; Redis-backed distributed rate limiting and request bounds implemented and tested. |
| `TD-BILL-001` | Open; real StoreKit sandbox validation still external. |
| `TD-BILL-002` | Open; public App Store Server Notifications V2 delivery still external. |
| `TD-BILL-003` | Open; App Store Server API credentials unavailable locally. |
| `TD-BILL-004` | Open; external Apple lifecycle ordering/retry evidence pending. |
| `TD-PRIV-001` | Open; future data-owning modules must add lifecycle contributors before they ship. |

## Validation

- Backend: `mvn -q test` in `services/api` passed, 42 tests.
- iOS: `xcodebuild test -project apps/ios/VerifiedAI.xcodeproj -scheme VerifiedAI -destination "platform=iOS Simulator,name=iPhone 16 Pro"` passed, 30 tests.
- Contract: OpenAPI YAML parsed successfully with Ruby YAML.
