# Dependency and Library Governance

## Principle

Every dependency adds security, maintenance and architectural cost. Prefer platform/framework capabilities when they are sufficient.

## iOS

Prefer first-party:
- SwiftUI,
- URLSession,
- SwiftData,
- Vision/VisionKit,
- StoreKit 2,
- OSLog.

Add third-party package only when it materially reduces complexity and has acceptable maintenance/license/security posture.

## Backend

Core dependencies should align with Spring ecosystem. Avoid adding overlapping libraries for HTTP, JSON, DI or validation without clear need.

## Python verifier

Keep dependency set narrow: FastAPI, SymPy, NumPy and only justified additions.

## Version policy

- pin versions through build lock/resolution mechanisms,
- scheduled upgrade review,
- emergency security patches prioritized,
- major framework upgrades tested in a branch with migrations/evaluations.

## Supply chain

CI runs dependency vulnerability scanning. Review licenses for any library shipped or used server-side.

## AI SDKs

Provider SDKs belong only in infrastructure adapter modules. If an SDK makes provider-neutral mapping harder, a direct HTTP client may be preferable.

<!-- HYBRID_AI_STRATEGY_V3:START -->
## ML dependency isolation

PyTorch/transformers/training frameworks must not enter production Spring/iOS dependency graphs merely for experiments. Future training and self-hosted inference dependencies live in isolated tool/service boundaries and require explicit approval.
<!-- HYBRID_AI_STRATEGY_V3:END -->
