# Sprint 4.3 Implementation Map

## NotebookLM Evidence

`NOTEBOOKLM_MCP_STATUS = CONNECTED_WITH_LOCAL_SOURCE_FALLBACK`

NotebookLM MCP connected to notebook `00e25adc-dee0-4a9c-92d8-6c473d1ad2ac`, titled `Verified AI Mathematics Learning Platform Technical Specification`. A broad async query timed out, then a focused query in conversation `3a773319-fb5c-4dce-bea2-a0dfc776e75e` returned semantic confirmation:

- preprocessing is backend-owned, deterministic, and server-authoritative;
- raw original evidence remains separate from derived state;
- derived assets are private object-storage files with PostgreSQL metadata;
- every derived asset links to a source asset and stores provenance;
- Sprint 4.3 quality evidence covers processing/quality uncertainty and recovery;
- OCR, parsing, solving, verification, AI providers, and math semantics remain out of scope.

Full source bodies were read from local canonical Markdown.

## CAP IDs

- `CAP-CAPTURE-003`: Sprint 4.3 owner; moves from `Pending` to `Complete` only after derived assets, quality evidence, recovery UX, tests, and docs pass.
- `CAP-CAPTURE-002`: remains `Complete`; Sprint 4.3 consumes its AVAILABLE source asset contract.
- `CAP-PRIV-001`: remains `Partial` overall; derived asset export/deletion coverage is added.
- `CAP-OPS-001`: remains `Partial` overall; preprocessing metrics/runbook coverage is added.

## REQ IDs

- `REQ-CAPTURE-003`: new Sprint 4.3 requirement for deterministic preprocessing, derived provenance, quality evidence, and user recovery.
- `REQ-CAPTURE-002`: preserved; source upload/object lifecycle remains authoritative.
- `REQ-PROBLEM-001`: maintained; neither original nor derived asset becomes canonical `Problem`.
- `REQ-PRIV-001`: maintained; no raw image/PDF bytes, OCR text, object keys, URLs, filenames, or user IDs in analytics/metric labels.
- `REQ-PRIV-002`: extended with derived asset metadata/object cleanup.
- `REQ-AUTH-002`: maintained; all preprocessing access is principal-derived.
- `REQ-DATA-001`: maintained; PostgreSQL owns structured derivative state.

## TD IDs

- `TD-CAPTURE-001`: open real-device capture validation.
- `TD-CAPTURE-002`: open production/staging object-storage validation.
- `TD-CAPTURE-003`: new calibration debt for provisional quality thresholds until Sprint 4.10 golden dataset gates.
- `TD-CAPTURE-004`: new production preprocessing performance validation debt for staging/near-max real images.
- `TD-PRIV-001`: remains open for future stores; derived problem asset subcase is covered by this sprint.

## Source ProblemAsset Contract

Sprint 4.3 consumes only an authenticated user-owned `ProblemAsset` with:

- `status = AVAILABLE`;
- `asset_kind = IMAGE` for durable preprocessing in this sprint;
- private original object key;
- SHA-256, size, content type, crop, image dimensions, source type, and owning `ProblemSession`;
- immutable original object bytes and metadata from Sprint 4.2.

PDF originals remain durable inputs. Sprint 4.3 records `PDF_UNSUPPORTED` preprocessing failure rather than introducing full PDF rasterization/OCR-adjacent page handling.

## Derived Asset Model

Use separate tables rather than overloading original `problem_assets`:

- `problem_asset_derivatives` stores derived object metadata, derivative kind, processing status, selected-for-recognition flag, source asset FK, provenance, checksum, dimensions, size, content type, and processing timestamps.
- `problem_asset_quality_evidence` stores durable quality signals/outcomes for each derivative.

This preserves the invariant:

```text
Original ProblemAsset != Derived Asset != Recognition Evidence != Problem Parse != Canonical Problem
```

## Preprocessing Ownership

The backend owns durable preprocessing. iOS may show local capture guidance, but durable derivatives and quality evidence come from backend processing over private source bytes.

## Image Decoder Architecture

Use JVM-safe `ImageIO` and standard Java2D operations to avoid new heavy/native dependencies. Decode only bounded JPEG sources, verify actual decoder acceptance, and reject malformed/unsupported sources with stable preprocessing errors.

## PDF Policy

PDF upload remains supported from Sprint 4.2, but PDF raster preprocessing is not implemented in Sprint 4.3. A PDF source returns recoverable `ASSET_PREPROCESSING_UNSUPPORTED` and remains available for a later explicit PDF page/raster policy.

## Crop Pipeline

Use authoritative normalized crop fields from Sprint 4.2. Convert to pixel bounds after decode, enforce non-empty/minimum crop dimensions, and apply crop before any geometric/readability normalization.

## Orientation Pipeline

JPEG orientation is normalized deterministically with a lightweight EXIF orientation reader for tag `0x0112`, covering values `1` through `8` without adding a metadata dependency. The preprocessor applies the required Java2D affine transform before crop conversion, and output metadata records whether orientation normalization changed the decoded raster.

## Perspective Policy

Use a conservative policy: do not apply automatic perspective warp unless a trustworthy boundary exists. Sprint 4.3 records `perspectiveApplied=false` and emits a crop/framing warning when geometry is risky. This avoids symbol-destroying transforms.

## Contrast Normalization Policy

Apply conservative luminance stretch only. No AI enhancement, inpainting, super-resolution, aggressive sharpening, destructive binarization, or symbol reconstruction.

## Compression Policy

Encode OCR-optimized derivative as JPEG with configured quality. Resize only when the long edge exceeds a configured maximum, preserving minimum readable resolution for small notation.

## Thumbnail Policy

Generate a separate JPEG thumbnail derivative for UI/history preview. It is never selected as the future OCR input.

## OCR-Optimized Derivative Policy

Generate `OCR_OPTIMIZED` pixels only. No OCR, recognized text, coordinates, provider/model/prompt provenance, or parse output is created. This derivative is selected as the preferred Sprint 4.4 recognition input when preprocessing succeeds.

## Quality Signals

Durable signals:

- `BLUR`;
- `GLARE`;
- `CROP_FRAMING`;
- `CONTRAST_READABILITY`;
- `RESOLUTION`.

## Quality Thresholds

Thresholds are centralized and versioned under configuration version `capture-quality-v1`. They are provisional until Sprint 4.10 calibration.

## Provenance Schema

Persist:

- source asset ID;
- derivative kind;
- processor name/version/configuration version;
- ordered transform flags and bounded parameters;
- content type, size, checksum, width, height;
- quality outcome and timestamps.

## Database Migration

Add a forward-only Sprint 4.3 migration after V007:

- `problem_asset_derivatives`;
- `problem_asset_quality_evidence`;
- uniqueness for `(source_asset_id, derivative_kind, processor_name, processor_version, configuration_version)`;
- FK ownership consistency through `(source_asset_id, user_id)`;
- indexes for source lookup, owner lookup, selected recognition input, status, and quality outcome.

## Object Storage Changes

Extend `ProblemAssetStorage` with private read/write operations. Derived object keys are backend-generated:

```text
problem-assets/{problemSessionId}/{sourceAssetId}/derivatives/{derivativeId}/{kind}.jpg
```

No public URLs are created.

## API Changes

Add Sprint 4.3-only endpoints:

- `POST /api/v1/problem-assets/{id}/preprocess`;
- `GET /api/v1/problem-assets/{id}/preprocessing`.

Both are authenticated and owner-scoped. `POST` is idempotent for source + processor/config + derivative kind.

## iOS Recovery UX

After upload AVAILABLE, iOS triggers preprocessing and shows:

- preprocessing progress;
- ready/pass state;
- warning state with Retake, Edit Crop, Continue;
- recoverable failure with Retry, Retake, Edit Crop.

Continue only accepts the preprocessed asset for future recognition boundary; it does not invoke OCR.

## Privacy

Derived images remain sensitive student content. Export includes metadata and quality evidence only, with raw/derived binaries excluded. Account deletion removes original and derived private objects plus metadata.

## Security

Controls:

- principal-derived ownership;
- active-account checks;
- source must be AVAILABLE;
- bounded decode and pixel limits;
- stable sanitized errors;
- no object keys/URLs in logs or metrics;
- dedicated preprocess rate limit;
- original object immutability.

## Observability

Add low-cardinality metrics for preprocessing started/success/failure/warning, quality warning types, generated derivatives, and total/decode/crop/contrast/quality/encoding latency.

## Test Fixture Strategy

Use synthetic generated images only: sharp equation, blurred equation, glare, risky crop, low contrast, rotated dimensions, thin minus sign, decimal point, superscript, fraction bar, radical, inequality, colored graph/diagram.

## Mathematical-Meaning Preservation Strategy

Regression fixtures assert image-level preservation of sensitive strokes and symbols. Tests do not use OCR. Assertions check dimensions, crop geometry, dark-pixel/stroke retention, local contrast, and selected symbol-region preservation.

## Performance Budgets

Configured max decode pixels and long-edge resizing bound memory/CPU. Representative fixture timings are recorded in execution report; staging near-max validation remains `TD-CAPTURE-004`.

## Out-of-Scope Boundaries

No OCR, Vision text recognition, AI providers, parser tables, canonical math, classification, solving, verification, prompt/model provenance, or Sprint 4.4+ endpoints.
