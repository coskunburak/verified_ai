# Caching, Storage, and File Assets

## Storage hierarchy

PostgreSQL: authoritative structured data.
Object storage: binaries.
Redis: ephemeral cache/coordination.
SwiftData: client cache/offline projection.

## Presigned upload flow

1. iOS requests upload intent.
2. Backend validates auth, quota, type and expected size.
3. Backend creates PENDING asset metadata.
4. iOS uploads directly to object storage.
5. Upload completion is confirmed.
6. Backend validates checksum/metadata.
7. Asset becomes AVAILABLE.

## Object keys

Never trust user filename as storage key.

Example:
`problems/{userId}/{sessionId}/{assetId}/original.jpg`

## Derived assets

May include:
- normalized/cropped image,
- thumbnail,
- OCR-optimized derivative.

Each derivative links to source and has retention policy.

## Redis

Example namespaced TTL keys:
- `rate:user:{id}:solve`
- `cache:skilltree:{version}`
- `lock:job:{id}`

Never keep permanent solution/mastery solely in Redis.

## Cache invalidation

Reference curriculum can be immutable/versioned.
User mastery/entitlement caches update after corresponding domain event and retain short TTL.

## iOS cache

Cache what improves UX, not a complete backend mirror.

<!-- HYBRID_AI_STRATEGY_V3:START -->
## Model/dataset artifact storage clarification

Future model binaries, dataset manifests and evaluation reports belong to dedicated versioned artifact storage with restricted credentials. They must not share public/problem-asset access semantics.
<!-- HYBRID_AI_STRATEGY_V3:END -->
