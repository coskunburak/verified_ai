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

## Sprint 6.7 native math dependency

SwiftMath is pinned with Xcode exactVersion 1.7.3, product SwiftMath, repository
[upstream SwiftMath](https://github.com/mgriebling/SwiftMath), [pinned release](https://github.com/mgriebling/SwiftMath/releases/tag/1.7.3).
It materially supplies native fractions/radicals/layout used by both existing typed math and attempt semantic diff.
No raw user LaTeX enters the renderer: local typed AST conversion supplies bounded supported commands.
Upstream package/API source was reviewed; the agent ran no package fetch/build or dependency scanner. The owner
run 20261103-233019-8g1dlw resolved SwiftMath and produced Package.resolved at revision
fa8244ed032f4a1ade4cb0571bf87d2f1a9fd2d7. The lockfile is preserved; shipped-revision auditing remains pending. MIT code notices and bundled font
GUST/OFL notices must be retained in distribution. Package/license/security/device approval remains a release
prerequisite; exact pinning alone is not a vulnerability or license-compliance attestation.
