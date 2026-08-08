# Technical Debt and Maintenance Register

## Purpose

This register captures known non-blocking technical debt so it is not lost in chat history or execution reports. It is not a substitute for issue tracking; it is the canonical documentation mirror for debt that affects architecture, dependencies, release readiness, or production operations.

## Priority

| Priority | Meaning |
|---|---|
| P0 | Blocks release or risks user/security/data integrity. |
| P1 | Must be fixed before the affected phase exits. |
| P2 | Should be fixed before Production V1 launch. |
| P3 | Routine maintenance; track and batch with dependency work. |

## Register

| Debt ID | Priority | Area | Finding | Impact | Owner | Remediation trigger | Target phase | Status |
|---|---|---|---|---|---|---|---|---|
| TD-DEP-001 | P3 | Math verifier dependencies | Verifier tests pass with a Starlette/httpx deprecation warning from the dependency stack. | No Phase 2 blocker; future dependency update may break test client behavior if ignored. | Platform / verifier owner | Next dependency maintenance window or before verifier API expands in Phase 5. | Phase 5 or earlier maintenance | Open |
| TD-DEV-001 | P3 | Local developer tooling | GitHub CLI is not installed on the local machine used for Phase 2 validation. | Does not block local implementation or GitHub Actions, but limits local PR/check inspection. | Developer environment owner | Before first GitHub PR triage workflow that requires local `gh` commands. | Phase 3 | Open |
| TD-DEV-002 | P3 | Local infrastructure ports | Host port `5432` was occupied during Docker Compose validation; alternate `POSTGRES_PORT=55432` worked. | Does not affect internal Docker networking; can confuse new local setup if undocumented. | Platform owner | If repeated by another developer, standardize alternate local port profile. | Phase 3 or onboarding docs pass | Open |
| TD-AUTH-001 | P2 | Apple validation | Real device / Apple sandbox Sign in with Apple validation has not been executed in this local implementation pass. | Automated cryptographic verification and iOS client tests pass, but App Store capability and Apple Developer portal configuration still need environment validation. | Identity owner | Before TestFlight or before marking `CAP-ID-001` Complete. | Sprint 3.8 / launch readiness | Open |
| TD-AUTH-002 | P2 | Auth abuse limits | Sprint 3.1/3.2 implemented auth/session security controls but did not add distributed rate limiting for auth endpoints. Sprint 3.8 added Redis-backed limits, request bounds, degraded-open metrics, and revoked-session access-token checks. | Closed for Phase 3 endpoints; future high-cost Phase 4+ endpoints must add their own policies before launch. | Identity / platform owner | Completed by Sprint 3.8 backend tests. | Sprint 3.8 | Closed |
| TD-BILL-001 | P2 | StoreKit sandbox validation | Real App Store sandbox purchase validation has not been executed for the StoreKit 2 purchase pipeline. | Local iOS and backend tests prove repository behavior, but App Store Connect product state, sandbox tester behavior, and Apple-hosted transaction delivery remain unverified. | Billing / iOS owner | Before TestFlight commerce enablement or before marking `APPLE_SANDBOX_PURCHASE_STATUS=PASS`. | Launch readiness | Open |
| TD-BILL-002 | P2 | Server Notifications V2 validation | App Store Server Notifications V2 have not been delivered to a public HTTPS webhook. | Webhook signature verification, inbox persistence, and dedupe are implemented locally, but Apple delivery, retry behavior, and endpoint reachability are unverified. | Billing / platform owner | After a public staging API endpoint and App Store Connect notification URL are configured. | Launch readiness | Open |
| TD-BILL-003 | P2 | App Store Server API credentials | App Store Connect In-App Purchase API issuer ID, key ID, private key, Apple root certificates, and production App Apple ID are not available in this workspace. | Official Apple server-library integration compiles, but sandbox transaction lookup, history reconciliation, and test notification APIs cannot be externally validated. | Billing / platform owner | Before App Store sandbox/API status can be marked `PASS`. | Launch readiness | Open |
| TD-BILL-004 | P2 | Subscription lifecycle hardening | Full Apple sandbox lifecycle permutations and out-of-order notification/reconciliation sequences have not been externally exercised. | Local lifecycle tests cover core ACTIVE/GRACE/BILLING_RETRY/EXPIRED/REVOKED transitions, but Apple delivery ordering evidence is still missing. | Billing / platform owner | After external Apple sandbox credentials and webhook delivery are available. | Launch readiness | Open |
| TD-PRIV-001 | P2 | Future data lifecycle contributors | Phase 3 deletion/export covers identity, profile, sessions, and billing. Future asset/problem/attempt/mastery/tutor/AI metadata stores do not exist yet and must add lifecycle contributors before they ship. | Account deletion/export would become incomplete if future modules store user data without registering a contributor. | Owning feature module owner | During Phase 4+ schema introduction and again at Sprint 11.5 privacy hardening. | Phase 4+ / Sprint 11.5 | Open |

## Debt Rules

- A P0/P1 debt item cannot be carried across a phase boundary without an explicit exception in that phase's execution report.
- A debt item that affects a V1-required capability must reference the relevant capability ID from `roadmap/68_PRODUCTION_V1_CAPABILITY_COVERAGE_MATRIX.md` before implementation begins.
- Dependency debt must include upgrade, pin, replacement, or removal strategy before Production V1 launch if it affects runtime code.
- Debt that becomes a security/privacy issue moves to security handling immediately.
