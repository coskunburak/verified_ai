# Monetization and Entitlements

## Principles

Monetization must reinforce product trust. Avoid misleading trials, hidden renewal terms, artificial friction and client-only premium state.

## Suggested tiers

### FREE
Purpose: prove the product.
Potential: limited solves, basic explanation, limited history and tutor usage.

### PRO
Primary learning subscription:
- meaningful solve allowance,
- verification,
- full history,
- mistake intelligence,
- mastery graph,
- adaptive plan,
- core exam features.

### PRO_PLUS
Heavy learner/exam tier:
- premium model fallback,
- advanced verification,
- PDF/course import,
- advanced exam analytics,
- expanded resource limits.

## Entitlement model

Fields:
- entitlement_id,
- user_id,
- tier,
- source,
- status,
- effective_at,
- expires_at,
- original_transaction_id,
- environment,
- last_verified_at.

Statuses:
- ACTIVE
- GRACE_PERIOD
- BILLING_RETRY
- EXPIRED
- REVOKED

## Authority

iOS purchase completion is not enough for server authorization.

Flow:
1. StoreKit transaction completes.
2. Client sends signed/transaction identity to backend.
3. Backend verifies server-side.
4. Entitlement updates.
5. `/me/entitlements` becomes authoritative client state.

Sprint 3.5/3.6 implementation note:
- iOS loads the product catalog from the backend before requesting StoreKit product metadata.
- The backend supplies a stable `appAccountToken`; iOS passes it into StoreKit purchases.
- iOS submits only StoreKit verified transaction JWS evidence to the backend.
- The backend verifies Apple-signed JWS, maps only backend-configured App Store product IDs to internal tiers, enforces `appAccountToken` and transaction ownership, and then updates the entitlement row.
- Server Notifications V2 and App Store Server API reconciliation may grant, preserve, downgrade, expire, or revoke App Store-sourced access through the same entitlement policy.

Client-provided product IDs, local purchase button state, and cached paywall state are never entitlement authority.

## Usage limits

Limits are server policy:
- solve count,
- premium route count,
- PDF pages,
- storage,
- mock exams.

Do not hard-code these values in Swift feature logic.

## Cost-aware resources

Internal resource units may reflect AI cost while UI remains simple. Avoid exposing raw token accounting to users unless a deliberate credit product is chosen.

## Restore purchases

Restore must query StoreKit, sync server state and remain idempotent.

Sprint 3.5 restore behavior calls `AppStore.sync()`, reads current and unfinished StoreKit transactions, submits verified transaction JWS evidence to the backend with deterministic idempotency keys, and finishes transactions only after backend acknowledgement.

## Analytics

Track paywall view, purchase start/completion/failure, restore, trial, renewal, grace period and expiration without leaking unnecessary billing PII.

## Billing evidence retention

Billing persistence stores decoded commercial fields, ownership keys, lifecycle state, JWS SHA-256 digests, notification IDs and processing results. It must not store raw signed transaction payloads, App Store private keys, full provider secrets, or high-cardinality user/payment identifiers in logs or metric labels.

Account deletion removes the commerce account token and revokes the local entitlement, but retains minimized billing event, subscription, and transaction references needed for refund, fraud, legal, and audit obligations. Retained billing records must not contain raw payment credentials or raw signed transaction payloads.

<!-- HYBRID_AI_STRATEGY_V3:START -->
## AI COGS and entitlement policy

Entitlement design must be economically testable. Each paid tier has an expected AI COGS envelope based on real usage distributions, not unlimited-model assumptions.

The backend may enforce fair-use and advanced-reasoning quotas. Limits must be transparent and product-semantic. Never expose raw token accounting as the primary consumer mental model.

Pricing experiments require monitoring of AI COGS per paid learner and gross-margin impact. A model-cost optimization cannot silently reduce verification quality for an existing paid promise.
<!-- HYBRID_AI_STRATEGY_V3:END -->
