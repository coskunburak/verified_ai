# Caching, Storage, and File Assets

## Storage hierarchy

PostgreSQL: authoritative structured data.
Object storage: binaries.
Redis: ephemeral cache/coordination.
SwiftData: client cache/offline projection.

## Presigned upload flow

1. iOS computes SHA-256 over the local accepted bytes and requests an upload reservation.
2. Backend validates authentication, active account, entitlement capability, source, kind, content type, expected size, checksum, crop, dimensions/page count, and idempotency.
3. Backend creates a `ProblemSession` and PENDING `ProblemAsset` with server-owned object key metadata.
4. Backend returns a short-lived presigned PUT URL and required headers.
5. iOS uploads bytes directly to object storage without object-storage credentials.
6. iOS confirms completion through the backend.
7. Backend performs HEAD plus streamed SHA-256 verification against the private object.
8. The asset becomes AVAILABLE and the session becomes ASSET_UPLOADED.

## Object keys

Never trust user filename as storage key.

Example:
`problem-assets/{problemSessionId}/{problemAssetId}/original`

The object key is generated only by the backend. The client never supplies `object_key`, bucket, prefix, or authoritative asset identity. Ownership lives in PostgreSQL, not in path parsing.

Sprint 4.3 derived objects use:
`problem-assets/{problemSessionId}/{sourceAssetId}/derivatives/{derivativeId}/{kind}.jpg`

Derivative keys are also generated only by the backend. They are not returned to clients in public API responses.

## Sprint 4.2 storage contract

- Provider abstraction: `ProblemAssetStorage`.
- Current adapter: S3-compatible storage with MinIO local/Testcontainers coverage.
- Default local bucket: `verified-ai-problem-assets-local`.
- Supported upload content types: `image/jpeg` and `application/pdf`.
- Maximum original upload size: 20 MB.
- Presign TTL: 15 minutes by default.
- Private storage assumption: objects are not public; clients receive scoped presigned PUT URLs only.
- Integrity: backend deletes mismatched objects on size/content-type/checksum mismatch and keeps the asset non-AVAILABLE.

## Derived assets

Sprint 4.3 creates:
- `OCR_OPTIMIZED` JPEG derivative, selected as the preferred Sprint 4.4 recognition input when preprocessing succeeds.
- `THUMBNAIL` JPEG derivative for compact UI/history preview metadata.

Each derivative links to its immutable source `ProblemAsset`, stores checksum/dimensions/processor provenance, and has associated quality evidence. The original source object is never overwritten. PDF originals remain durable upload assets; Sprint 4.3 records a recoverable `PDF_UNSUPPORTED` preprocessing failure rather than rasterizing PDF pages.

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
