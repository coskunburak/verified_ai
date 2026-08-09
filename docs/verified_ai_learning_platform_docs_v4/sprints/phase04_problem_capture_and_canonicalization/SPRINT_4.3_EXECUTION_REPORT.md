# Sprint 4.3 Execution Report - Image Preprocessing and Capture Quality

## Executive Summary

Sprint 4.3 is complete locally. The backend now turns an authenticated user-owned AVAILABLE image `ProblemAsset` into private provenance-linked `OCR_OPTIMIZED` and `THUMBNAIL` derivatives, records durable quality evidence, and returns PASS/WARNING/FAILED recovery decisions. The sprint deliberately stops before OCR, recognition evidence, parsing, canonical problem creation, solving, or verification.

## NotebookLM MCP Status

`CONNECTED_WITH_LOCAL_SOURCE_FALLBACK`

NotebookLM MCP connected to notebook `00e25adc-dee0-4a9c-92d8-6c473d1ad2ac`. Semantic retrieval confirmed backend-owned deterministic preprocessing, immutable original evidence, separate derived asset metadata, quality evidence, recovery UX, and no OCR/parser/solver scope. Full source bodies were read from local canonical Markdown.

## Canonical Sources

Primary sources were the Phase 4 overview, Sprint 4.1 and Sprint 4.2 implementation maps/reports, Sprint 4.3 specification, domain invariants, API contracts, backend module contracts, data/storage/privacy docs, iOS architecture/UX docs, observability/runbooks, capability matrix, RTM, and technical debt register.

## Repository Baseline

- Starting commit: `3b37280 feat: complete sprint 4.2 presigned asset upload`
- Tags at start: none on HEAD.
- Sprint 4.2 handoff was verified before implementation: AVAILABLE original `ProblemAsset`, private object key, SHA-256/size/content-type metadata, crop metadata, owner, and `ProblemSession`.

## Sprint 4.2 Handoff

Sprint 4.3 consumes Sprint 4.2 only after backend completion marks the asset `AVAILABLE`. Source ownership, object identity, checksum, size, content type, crop, dimensions, and session are backend truth. The original object remains immutable.

## Scope

Implemented backend preprocessing, derived asset persistence, quality evidence, S3-compatible source read/derived write support, OpenAPI, rate limit policy, iOS preprocessing/recovery UX, privacy export/deletion coverage, tests, and documentation.

## Out-of-Scope

No OCR, Apple Vision text recognition, Vision LLM, OpenAI/Gemini calls, recognition evidence, parser output, canonical math, skill classification, solving, verification, prompt/model provenance, or Sprint 4.4+ endpoint was added.

## CAP Mapping

- `CAP-CAPTURE-003`: Complete.
- `CAP-CAPTURE-002`: remains Complete; consumed as the durable source boundary.
- `CAP-PRIV-001`: remains Partial overall; derived asset/evidence subcase is covered.
- `CAP-OPS-001`: remains Partial overall; preprocessing metrics were added.

## REQ Mapping

- `REQ-CAPTURE-003`: Satisfied.
- `REQ-CAPTURE-002`: preserved.
- `REQ-PROBLEM-001`: foundation maintained; assets and derivatives are not canonical problems.
- `REQ-PRIV-001`: maintained; no raw content/object keys/user IDs in metric labels.
- `REQ-PRIV-002`: extended for derivative/evidence export and deletion.
- `REQ-AUTH-002`, `REQ-DATA-001`, and `REQ-BILL-001`: maintained.

## TD Mapping

- `TD-CAPTURE-001`: open real-device capture validation.
- `TD-CAPTURE-002`: open production/staging object-storage validation.
- `TD-CAPTURE-003`: open PDF preprocessing policy debt.
- `TD-CAPTURE-004`: open staging/near-max real-image preprocessing validation.
- `TD-PRIV-001`: remains open for future stores.

## Preprocessing Architecture

Processing sequence:

```text
AVAILABLE source -> private read -> bounded decode -> EXIF orientation normalization -> authoritative crop -> conservative quality analysis -> conservative contrast stretch -> resize/encode derivatives -> checksum -> private storage write -> provenance/evidence persistence -> recovery response
```

## Processor Ownership

The backend owns durable preprocessing and processor/config versions. iOS still offers local capture guidance and recovery controls, but it does not create canonical durable derivatives.

## Source Asset Contract

Only authenticated, active-account, entitled, user-owned `AVAILABLE` source assets can be preprocessed. Image assets are processed; PDFs receive durable unsupported preprocessing state instead of implicit page rasterization.

## Derived Asset Model

Derived assets use `problem_asset_derivatives`, not new `ProblemAsset` rows. Each derivative has owner, session, source asset, kind, status, selected-for-recognition flag, object metadata, checksum, dimensions, quality outcome, processor/config provenance, and timestamps.

## Derivative Types

- `OCR_OPTIMIZED`: selected future Sprint 4.4 recognition input when ready.
- `THUMBNAIL`: UI preview only and never selected for recognition.

## Database Migration

`V008__create_problem_asset_preprocessing_lifecycle.sql` adds `problem_asset_derivatives`, `problem_asset_quality_evidence`, source/owner FK enforcement, uniqueness for current processor/config derivative keys, selected-recognition partial uniqueness, status/outcome checks, and source/owner/status indexes.

## Object Storage Model

`ProblemAssetStorage` now supports private bounded reads and private writes. S3-compatible storage writes backend-generated derivative keys under the source asset path and never returns public URLs or object keys to clients.

## Original Asset Immutability

The source object is only read. Transformations produce new derivative objects. Storage partial failures clean up written derived objects before returning failure.

## Provenance

Persisted provenance includes source asset ID, derivative kind, processor name/version, configuration version, transformation parameters, crop, orientation/perspective/contrast flags, content type, byte size, SHA-256 checksum, dimensions, and processing timestamps.

## Orientation Normalization

JPEG EXIF orientation tag `0x0112` is parsed without adding a new metadata dependency. Java2D transforms normalize orientations `1` through `8` before crop processing. Regression test coverage includes all eight EXIF orientation values.

## Crop Application

Normalized crop metadata from Sprint 4.1/4.2 is authoritative. It is converted to pixel coordinates after orientation normalization, bounded to the image, and rejected if outside bounds or below minimum configured crop dimensions.

## Perspective Correction

Perspective warp is intentionally not applied in Sprint 4.3 because there is no trusted document-boundary detector yet. The persisted flag is `perspectiveApplied=false`; risky framing is represented as quality evidence rather than an aggressive transform.

## Contrast Normalization

The processor applies conservative RGB luminance stretching only when contrast/readable range is low. It does not binarize, sharpen aggressively, inpaint, super-resolve, reconstruct, or hallucinate symbols.

## Compression / Encoding

Derivatives are encoded as JPEG with configured quality `0.92`. OCR-optimized long edge is capped at `2400`; thumbnail long edge is capped at `360`.

## Quality Signal Model

Quality outcomes are `PASS`, `WARNING`, or `FAILED`. Durable signal severities are `PASS`, `WARNING`, and `BLOCKING`, with signal score, threshold, policy code, and processor/config version persisted.

## Blur Detection

Blur uses sampled Laplacian variance over luminance. Threshold is versioned in `capture-quality-v1` and produces warnings rather than semantic claims.

## Glare Detection

Glare uses localized saturated-white grid ratio with an overall-saturation guard so a normal white page is not automatically treated as glare.

## Crop / Framing Risk

Crop/framing risk is based on crop area ratio and minimum pixel bounds. It warns on tight crops and rejects invalid/empty crop metadata.

## Threshold Versioning

Thresholds live in `ProblemAssetPreprocessingProperties` and `app.problem-assets.preprocessing`, with `processorName=DOCUMENT_PREPROCESSOR`, `processorVersion=1.0`, and `configurationVersion=capture-quality-v1`.

## Quality Outcome Policy

PASS returns `CONTINUE`; WARNING returns `RETAKE`, `EDIT_CROP`, and `CONTINUE`; FAILED returns recovery actions without selecting a recognition derivative. Unsupported PDF and malformed image failures are durable and recoverable.

## User Recovery UX

iOS now models preprocessing, ready, warning, and failure phases. Warning users can Retake, Edit Crop, or Continue; failure users can Retry, Edit Crop, or Retake. Continue only accepts the preprocessed asset for the future recognition boundary.

## Privacy Lifecycle

`ProblemAssetLifecycleContributor` exports derivative/evidence metadata, excludes raw and derived binaries, and deletes source plus derivative object keys during confirmed account deletion before row cleanup.

## Account Export

Export category `problemAssets` includes original asset metadata, derivative metadata, quality evidence, and `rawBinaryIncluded=false` / `derivedBinaryIncluded=false`.

## Account Deletion

Deletion removes private original object keys and private derivative object keys, then deletes problem sessions/assets with cascade cleanup for derivative/evidence rows.

## Security Controls

Controls include principal-derived ownership, active account check, entitlement guard, AVAILABLE-only source lock, per-resource lookup by user, max source bytes, max decode pixels, crop bounds, sanitized problem details, no object keys in response, dedicated preprocess rate limit, and no raw content telemetry.

## Performance

Synthetic preprocessor fixture suite ran 6 tests in `0.406 s`. Full backend suite with Testcontainers completed successfully in the local Docker/JDK 21 environment. Staging near-limit image corpus and production object-storage throughput validation remain `TD-CAPTURE-004`.

## Observability

Added Micrometer metrics for preprocessing success/failure/warning, generated derivatives, warning signals, and total/decode/crop/contrast/quality/encoding latency with low-cardinality labels.

## Tests

- Backend full suite: PASS, `70` tests, `0` failures, `0` errors.
- iOS full suite: PASS, `68` tests on iPhone 16 Pro simulator, exit code `0`.
- Math verifier regression: PASS, `13` tests, `1` existing dependency warning.
- `doctor`: PASS.
- `lint`: PASS.
- `contracts-check`: PASS.
- `secret-scan`: PASS.
- `git diff --check`: PASS.

## Fixture Catalog

Synthetic fixtures cover sharp equation, localized glare patch, tight crop, low resolution, low contrast, malformed bytes, dark mathematical mark preservation, and EXIF orientations `1` through `8`.

## Mathematical Meaning Preservation Evidence

No OCR is used. Image-level tests assert derivatives are produced without dropping dark mathematical marks after contrast normalization and without applying risky perspective warp. The generated math fixture includes thin strokes, a fraction bar, minus-like marks, and equation text regions.

## Local MinIO Integration

S3-compatible integration validates presigned/source behavior from Sprint 4.2 plus Sprint 4.3 private `readBytes` and derivative `putObject` behavior against Testcontainers MinIO.

## Known Limitations

PDF page rasterization is not implemented. Perspective detection/warp is intentionally disabled. Quality thresholds are provisional. Real-device EXIF/camera corpus and production storage/IAM throughput remain launch-readiness validation.

## Documentation Changes

Updated API contracts, storage/data lifecycle docs, PostgreSQL data model, rate-limit docs, capability matrix, RTM, TD register, OpenAPI, Sprint 4.3 implementation map, and this execution report.

## Git Status

Sprint 4.3 changes are intended for checkpoint commit:

```text
feat: complete sprint 4.3 image preprocessing and capture quality
```

## Sprint Exit Decision

`SPRINT_4.3 = COMPLETE`

All local exit gates are satisfied, with production/staging validation explicitly tracked as non-blocking launch-readiness debt.

## Sprint 4.4 Readiness

`SPRINT_4.4_READINESS = READY`

Sprint 4.4 can consume the selected `OCR_OPTIMIZED` derivative, source/derivative provenance, quality evidence, crop geometry, processor/config versions, and backend-owned secure storage path without guessing which asset to process.
