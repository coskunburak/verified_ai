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

## Analytics

Track paywall view, purchase start/completion/failure, restore, trial, renewal, grace period and expiration without leaking unnecessary billing PII.

<!-- HYBRID_AI_STRATEGY_V3:START -->
## AI COGS and entitlement policy

Entitlement design must be economically testable. Each paid tier has an expected AI COGS envelope based on real usage distributions, not unlimited-model assumptions.

The backend may enforce fair-use and advanced-reasoning quotas. Limits must be transparent and product-semantic. Never expose raw token accounting as the primary consumer mental model.

Pricing experiments require monitoring of AI COGS per paid learner and gross-margin impact. A model-cost optimization cannot silently reduce verification quality for an existing paid promise.
<!-- HYBRID_AI_STRATEGY_V3:END -->
