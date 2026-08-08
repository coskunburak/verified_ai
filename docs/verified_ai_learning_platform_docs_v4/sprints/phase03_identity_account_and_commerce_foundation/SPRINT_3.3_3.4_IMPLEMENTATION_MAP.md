# Sprint 3.3 + 3.4 Implementation Map

## Baseline

- NotebookLM MCP status: `CONNECTED_WITH_LOCAL_SOURCE_FALLBACK`.
- Git baseline: Sprint 3.1/3.2 implementation is present but uncommitted; this sprint must extend it without overwriting auth/session guarantees.
- Out of scope: StoreKit product loading, purchases, restore, App Store Server API, Server Notifications V2, account deletion/export lifecycle, distributed auth abuse hardening.

## Sprint 3.3 Ownership

- Capability: `CAP-PROFILE-001`.
- Requirements: `REQ-PROFILE-001`, `REQ-AUTH-002`, `REQ-PRIV-001`, `REQ-DATA-001`.
- Backend owner: `profile`.
- Domain concept: `LearningProfile`, distinct from `User`; profile edits cannot mutate mastery.
- API: authenticated current-user profile read/update at `/api/v1/me/learning-profile`.
- Database: new `learning_profiles` table with `unique(user_id)`, FK to `users`, typed enum/check constraints, timestamps, and optimistic `version`.
- iOS owner: `Features/Onboarding`.
- UI flow: authenticated session restores, profile is loaded, incomplete profile routes to resumable onboarding, completed profile routes to Home.
- Privacy: profile is student personal data, backend-authoritative, not training-eligible by default, not logged as raw answers/goal text.
- Telemetry: profile load/save/completion counters without profile values as labels.
- Tests: Testcontainers persistence/API tests, validation/ownership/optimistic conflict tests, iOS ViewModel/repository/bootstrap tests.

## Sprint 3.4 Ownership

- Capabilities: `CAP-BILL-001`, partial foundation for `CAP-BILL-004`.
- Requirements: `REQ-BILL-001`, `REQ-BILL-002`, `REQ-AUTH-002`, `REQ-PRIV-001`, `REQ-DATA-001`.
- Backend owner: `billing`.
- Domain concepts: `Entitlement`, `EntitlementTier`, `EntitlementStatus`, `PremiumCapability`, `AccessPolicy`.
- API: authenticated current-user entitlement read at `/api/v1/me/entitlements`; no client write endpoint for self-promotion.
- Database: new `entitlements` table with `unique(user_id)`, FK to `users`, tier/status/source checks, effective/expiry fields, environment, transaction placeholders, timestamps, and optimistic `version`.
- Default state: lazily initialized `FREE` / `ACTIVE` / `DEFAULT_FREE` for authenticated users with race-safe uniqueness and advisory locking.
- Access policy: backend `requireCapability(userId, capability)` allows free baseline capabilities and denies paid capabilities with stable `ENTITLEMENT_REQUIRED` Problem Details.
- iOS owner: `Features/Subscription` for entitlement state and reusable capability gates; no StoreKit purchase code in Sprint 3.4.
- Security: cached iOS entitlement is presentation-only and cannot overrule backend denial.
- Telemetry: entitlement resolution and access allowed/denied counters with low-cardinality labels only.
- Tests: default creation, concurrent initialization, current read, allowed/denied policy, no public self-promotion route, iOS entitlement bootstrap/gate tests.

## Rollout And Recovery

- No AI inference calls are added; expected model cost impact is zero.
- New migrations are additive and forward-only. Rollback requires disabling routes and leaving rows inert; no destructive migration is introduced.
- Launch bootstrap should load profile and entitlement after auth, with profile controlling navigation and entitlement controlling presentation/access gates.
- Existing debt `TD-AUTH-001` and `TD-AUTH-002` remain open.
