# Sprint 4.1 Implementation Map

## NotebookLM Evidence

`NOTEBOOKLM_MCP_STATUS = CONNECTED_WITH_LOCAL_SOURCE_FALLBACK`

NotebookLM MCP connected to notebook `00e25adc-dee0-4a9c-92d8-6c473d1ad2ac`, titled `Verified AI Mathematics Learning Platform Technical Specification`.

NotebookLM resolved the Sprint 4.1 semantic boundary:

- `CapturedAsset` is a local iOS pre-upload capture/import artifact.
- `ProblemAsset` is the backend-registered asset metadata/object-storage artifact introduced after upload.
- `CapturedAsset` and `ProblemAsset` are not canonical `Problem`.
- durable backend upload reservation belongs to Sprint 4.2.
- durable `ProblemSession` history/recovery belongs to later Phase 4 work and must not be created in Sprint 4.1.
- manual text input is not part of Sprint 4.1.
- camera, gallery/photo import, and PDF/document import are in Sprint 4.1 scope.
- raw student images/documents must not enter logs, analytics payloads, metric labels, or debug UI.

NotebookLM did not return complete source bodies, so checked-in Markdown files were used for complete body reads and exact ID traceability.

## Canonical Sources Consulted

- `00_MASTER_INDEX.md`
- `DOCUMENTATION_MANIFEST.md`
- repository `README.md`
- `integrations/44_AI_AGENT_CONTEXT_AND_READING_ORDER.md`
- `integrations/45_MCP_WORKFLOW_NOTEBOOKLM_CODEX.md`
- `quality/40_TEST_STRATEGY.md`
- `quality/41_ENGINEERING_STANDARDS_FOR_CODEX.md`
- `quality/42_DEFINITION_OF_DONE_AND_ACCEPTANCE.md`
- `quality/53_DEPENDENCY_AND_LIBRARY_GOVERNANCE.md`
- `quality/54_REPOSITORY_ROOT_HIERARCHY_AND_NAMING_CONVENTIONS.md`
- `quality/55_DEVELOPMENT_WORKFLOW_BRANCHING_AND_REVIEW.md`
- `quality/56_COMPLETE_PRODUCTION_REPOSITORY_FILE_HIERARCHY.md`
- `quality/65_CANONICAL_DOCUMENTATION_TREE_AND_SOURCE_OF_TRUTH_MAP.md`
- `quality/66_FILE_PLACEMENT_OWNERSHIP_AND_DEPENDENCY_MATRIX.md`
- `quality/69_REQUIREMENTS_TRACEABILITY_MATRIX.md`
- `roadmap/43_PRODUCT_AND_TECHNICAL_ROADMAP.md`
- `roadmap/67_PRODUCT_PROGRAM_STRUCTURE.md`
- `roadmap/68_PRODUCTION_V1_CAPABILITY_COVERAGE_MATRIX.md`
- `operations/70_RELEASE_TRAINS_AND_BACKLOG_PROMOTION.md`
- `operations/71_TECHNICAL_DEBT_AND_MAINTENANCE_REGISTER.md`
- `sprints/00_SPRINT_MASTER_PLAN.md`
- `sprints/phase04_problem_capture_and_canonicalization/PHASE_04_PROBLEM_CAPTURE_AND_CANONICALIZATION.md`
- `sprints/phase04_problem_capture_and_canonicalization/SPRINT_4.1_PREMIUM_CAMERA_CAPTURE_AND_IMPORT_EXPERIENCE.md`
- `product/01_PRODUCT_VISION_AND_POSITIONING.md`
- `product/03_END_TO_END_USER_JOURNEYS.md`
- `product/04_FEATURE_CATALOG_AND_PRODUCT_RULES.md`
- `product/05_MONETIZATION_AND_ENTITLEMENTS.md`
- `domain/06_UBIQUITOUS_LANGUAGE_AND_GLOSSARY.md`
- `domain/07_DOMAIN_MODEL_AND_AGGREGATES.md`
- `domain/08_BOUNDED_CONTEXTS_AND_MODULE_BOUNDARIES.md`
- `domain/09_DOMAIN_EVENTS_AND_STATE_MACHINES.md`
- `domain/10_DOMAIN_INVARIANTS_AND_BUSINESS_RULES.md`
- `ios/15_IOS_ARCHITECTURE_AND_FILE_HIERARCHY.md`
- `ios/16_IOS_DESIGN_SYSTEM_AND_UX_ENGINEERING.md`
- `ios/17_IOS_NETWORKING_PERSISTENCE_AND_OFFLINE.md`
- `ios/18_IOS_AUTH_STOREKIT_AND_DEVICE_SECURITY.md`
- `ios/52_LOCALIZATION_MATH_NOTATION_AND_ACCESSIBILITY_CONTENT.md`
- `security/35_SECURITY_THREAT_MODEL.md`
- `security/36_PRIVACY_AND_STUDENT_DATA_PROTECTION.md`
- `data/23_DATA_LIFECYCLE_RETENTION_AND_AUDIT.md`
- `data/24_CACHING_STORAGE_AND_FILE_ASSETS.md`
- `operations/37_OBSERVABILITY_SLOS_AND_INCIDENTS.md`
- `operations/48_ANALYTICS_EVENT_CATALOG_AND_PRODUCT_METRICS.md`
- `operations/49_FEATURE_FLAGS_REMOTE_CONFIG_AND_EXPERIMENTATION.md`
- `sprints/phase03_identity_account_and_commerce_foundation/PHASE_03_EXECUTION_REPORT.md`

## Repository Baseline

- Starting commit: `aa48259 feat: complete phase 3 identity account and commerce foundation`
- Starting tag: `phase-3-complete`
- Starting working tree: clean

## Capability And Requirement Ownership

- `CAP-CAPTURE-001` owns camera capture and gallery import. Sprint 4.1 may move this from `Pending` to `Partial` when local iOS capture/import/review evidence exists. Real-device camera validation remains separate evidence.
- `REQ-PRIV-001` governs raw student content exclusion from logs and analytics.
- `REQ-PROBLEM-001` governs the invariant that a raw asset is not a canonical `Problem`.
- `REQ-OPS-001` applies to privacy-safe operational traces and correlation-friendly logging, within the currently available iOS observability foundation.
- `REQ-BILL-001` remains relevant because premium UI state cannot become entitlement authority.
- `TD-PRIV-001` remains relevant: future durable asset/problem stores must add lifecycle contributors before shipping.

No backend `REQ-CAPTURE-*` row exists before this sprint. Sprint 4.1 will add a client-capture requirement row only if final implementation evidence needs more precise traceability than `CAP-CAPTURE-001` plus `REQ-PRIV-001` and `REQ-PROBLEM-001`.

## Existing Architecture Dependencies

- iOS app is SwiftUI, Observation, async/await, feature-first MVVM.
- `RootView` owns authenticated bootstrap and renders authenticated home after profile and entitlement loading.
- `HomePlaceholderView` is the current authenticated home shell and is the correct entry point for `Scan a Problem`.
- `AppDependencies` composes core services and feature services.
- `AppLogger` is the current observability mechanism; no full analytics transport exists yet.
- `CapabilityGate` consumes cached/server entitlement for presentation only. Backend remains authoritative for premium operations.

## Feature File Tree

```text
apps/ios/VerifiedAI/Features/ProblemCapture/
  Domain/
    ProblemCaptureModels.swift
    ProblemCapturePorts.swift
  Data/
    CapturedAssetStore.swift
    CaptureQualityAnalyzer.swift
    AVFoundationProblemCameraClient.swift
  Presentation/
    ProblemCaptureViewModel.swift
    ProblemCaptureView.swift

apps/ios/VerifiedAITests/ProblemCapture/
  ProblemCaptureViewModelTests.swift
  CaptureQualityAnalyzerTests.swift
  CropSelectionTests.swift
  CapturedAssetStoreTests.swift
```

## Capture Sources

Sprint 4.1 implements:

- camera capture through AVFoundation;
- photo-library import through `PhotosPicker`;
- file/PDF import through native document picker with UTType validation and first-page PDF preview.

Manual typed math input is excluded.

## Permission Policy

- Camera authorization is requested just in time.
- `.notDetermined`, `.authorized`, `.denied`, and `.restricted` are explicit states.
- denied/restricted camera access offers alternate import options and Settings recovery.
- Photos import uses scoped `PhotosPicker` rather than requesting broad library permission.

## Camera Architecture

- SwiftUI owns presentation only.
- `ProblemCameraClient` is a protocol used by `ProblemCaptureViewModel`.
- `AVFoundationProblemCameraClient` owns `AVCaptureSession`, `AVCaptureDeviceInput`, `AVCapturePhotoOutput`, photo capture, session queueing, autofocus configuration, session start/stop, and interruption/runtime-error observation.
- Camera preview is a SwiftUI bridge over `AVCaptureVideoPreviewLayer`.
- The camera stops when leaving camera mode, entering review/crop, cancellation, or background.

## Import Architecture

- Import loaders validate type and size before storing.
- Images are inspected with ImageIO, dimension-bounded, metadata-minimized, and stored under an app-owned temporary directory using unpredictable filenames.
- PDF imports are validated as PDF, copied to the temporary directory, and represented locally by a first-page preview image.
- Unsupported/corrupt/oversized imports produce recoverable local errors.

## Temporary Storage Policy

- Directory: `FileManager.default.temporaryDirectory/VerifiedAIProblemCapture`
- Filenames: generated UUIDs, never user-provided filenames.
- Protection: iOS file-protection attributes are applied to the directory and written files.
- Retention: ephemeral; workflow cancel/retake/replace/accept cleanup is explicit, and launch/workflow cleanup removes expired temporary assets.
- Termination policy: local capture workflow state is intentionally not restored after process death in Sprint 4.1; stale temp files are pruned on next cleanup.

## Quality Analysis Policy

- Quality analysis is deterministic local guidance only.
- Required issues: blur, glare, framing/crop risk.
- No OCR, text recognition, subject classification, skill classification, difficulty estimation, answer detection, or verification status.
- Thresholds are named constants and covered by synthetic tests. Initial thresholds are guidance and require later calibration against Phase 4.10 ingestion datasets.

## Crop Model

- Crop is non-destructive metadata on `CapturedAsset`.
- Coordinates are normalized `x`, `y`, `width`, `height` in `[0, 1]`.
- Mapping utilities convert normalized crops to display and pixel rectangles.
- UI uses large accessible slider controls plus reset/full-image controls, not tiny drag-only handles.

## Navigation

- Authenticated Home exposes `Scan a Problem`.
- Problem Capture is presented as a modal flow owned by `RootView`.
- Accepting an asset produces `AcceptedCapturedAsset` and stops at a local handoff boundary. No upload or solving route is opened in Sprint 4.1.

## Accessibility And Localization

- Primary controls have VoiceOver labels, hints, identifiers, and button traits.
- Quality warnings are text and icon, not color-only.
- Crop controls are slider-based and Dynamic Type friendly.
- User-facing copy avoids OCR/AI/verification claims.
- New strings are centralized through a feature-local copy namespace and entered in the String Catalog.

## Telemetry

Current iOS telemetry uses `AppLogger`. Sprint 4.1 emits privacy-safe operational event names only:

- `problem_capture.opened`
- `problem_capture.source_selected`
- `problem_capture.permission_denied`
- `problem_capture.camera_ready`
- `problem_capture.capture_succeeded`
- `problem_capture.capture_failed`
- `problem_capture.import_succeeded`
- `problem_capture.import_failed`
- `problem_capture.quality_warning`
- `problem_capture.crop_edited`
- `problem_capture.retake`
- `problem_capture.accepted`

No event includes raw bytes, text, filenames, local paths, image metadata, or user identifiers.

## Security Risks

Evaluated risks:

- camera unavailable or simulator-only capture;
- denied/restricted permission dead ends;
- malicious imported files;
- oversized files;
- compressed image dimension bombs;
- corrupt images/PDFs;
- GPS/EXIF metadata exposure;
- path traversal through user filenames;
- late async callbacks after replacement/cancel;
- temporary sensitive file accumulation.

## Testing Strategy

- View-model tests cover source selection, permission mapping, capture/import success and failure, crop edit, warnings, retake, accept, and late-result protection.
- Quality analyzer tests use synthetic non-student fixtures.
- Crop model tests cover validation, clamping, aspect-fit conversion, and pixel mapping.
- Temporary storage tests cover write, validation, unique filenames, cleanup, and unsupported/corrupt content.
- UI tests cover simulator-practical entry/source/review fixture paths where the app shell allows.

## Real Device Validation Requirements

Simulator tests cannot validate actual camera hardware. Sprint 4.1 reports these separately:

- `REAL_DEVICE_CAMERA_CAPTURE`
- `REAL_DEVICE_PERMISSION_FLOW`
- `REAL_DEVICE_BACKGROUND_RECOVERY`
- `REAL_DEVICE_FOCUS_CAPTURE_QUALITY`

If no physical device is available, status remains `NOT_RUN` or `BLOCKED` with a manual checklist.

## Explicit Out Of Scope

- upload reservation;
- presigned URLs;
- S3/MinIO upload;
- backend `ProblemSession`;
- backend `ProblemAsset`;
- database migration V007;
- OpenAPI upload/problem endpoints;
- OCR or Vision text recognition;
- parser prompts;
- canonical `Problem`;
- solving;
- verification;
- classification;
- AI provider SDKs.

