# Phase 4 Execution Report

## Status

LOCAL IMPLEMENTATION EVIDENCE PRESENT - PRODUCTION EXIT NOT DECLARED COMPLETE

Phase 4 problem capture and canonicalization work is implemented locally through Sprint 4.10. The phase is not declared fully production-complete because real-device capture validation, connected representative AI evaluation, and protected holdout evaluation remain unavailable in this workspace.

Phase 5 was not started.

## Implementation Summary

- Sprint 4.1: local camera/gallery/PDF import, capture review, crop, and accepted local asset state.
- Sprint 4.2: backend-authorized presigned upload, `ProblemSession` and `ProblemAsset` durability, checksums, object lifecycle, and privacy deletion/export coverage.
- Sprint 4.3: backend image preprocessing, immutable derivative assets, quality evidence, and recovery outcomes without OCR.
- Sprint 4.4: provider-neutral `VISION_PARSE`, durable recognition jobs/evidence, untrusted-output validation, prompt/schema provenance, and privacy-safe metrics.
- Sprint 4.5: provider-neutral `PROBLEM_NORMALIZE`, immutable parser revisions, parser schema validation, source-evidence validation, unsupported/review handling, and parser privacy lifecycle coverage.
- Sprint 4.6: safe canonical mathematical representation, typed AST limits, verifier-input handoff, canonical persistence, and deterministic validation.
- Sprint 4.7: curriculum-backed problem classification with durable jobs/results, bounded candidate validation, confidence policy, and iOS presentation flow.
- Sprint 4.8: selected-parse authority, user correction revisions, correction lineage, stale-base conflict handling, and review UI flow.
- Sprint 4.9: backend-authoritative problem-session history, detail, retry, resume, and iOS cached history/recovery experience.
- Sprint 4.10: synthetic non-training ingestion golden dataset, deterministic evaluation runner, release-gate comparator, approved local fixture baseline, and CI wiring.

## Capability Coverage

Complete for local repository evidence:

- `CAP-CAPTURE-002`
- `CAP-CAPTURE-003`
- `CAP-PROBLEM-001`
- `CAP-PROBLEM-002`
- `CAP-PROBLEM-003`
- `CAP-PROBLEM-004`
- `CAP-PROBLEM-005`

Partial with explicit external or future evidence:

- `CAP-CAPTURE-001`: real-device camera/focus/picker validation remains `TD-CAPTURE-001`.
- `CAP-PROBLEM-006`: local deterministic ingestion evaluation exists, but connected provider and protected holdout evidence remain blocked.
- `CAP-EVAL-001`: Sprint 4.10 covers only the ingestion subset; broader solve/verify/tutor evaluation remains Sprint 5.12 or later.
- `CAP-PRIV-001`: Phase 4 lifecycle contributors exist for problem assets, preprocessing, recognition, parser, canonical, and classification data; future attempt/mastery/tutor stores remain later scope.

## Requirements Traceability

Satisfied or locally covered for Phase 4 scope:

- `REQ-CAPTURE-001`
- `REQ-CAPTURE-002`
- `REQ-CAPTURE-003`
- `REQ-CAPTURE-004`
- `REQ-PROBLEM-001`
- `REQ-PROBLEM-002`
- `REQ-PROBLEM-004`
- `REQ-PROBLEM-005`

Foundation status with known limitations:

- `REQ-AI-001`: provider-neutral ingestion routes exist; full AI gateway/router remains Phase 5.
- `REQ-PROBLEM-003`: accepted parser output has schema, semantic, source-evidence, unsupported, and review handling; representative provider accuracy remains open.
- `REQ-PRIV-001`: Phase 4 paths preserve content-free logs/metrics; future feature phases must repeat review.
- `REQ-PRIV-002`: Phase 4 problem data participates in lifecycle contributors; future stores remain open.
- `REQ-INGEST-EVAL-001`: local deterministic ingestion gate exists; connected provider and protected holdout evidence are blocked.
- `REQ-EVAL-001`: Sprint 4.10 contributes ingestion evaluation only; global AI evaluation remains future scope.

## Validation Evidence

Final aggregate local gate:

- `make check` passed with Docker/Testcontainers and CoreSimulator access.
- Backend API: 213 tests, 0 failures, 0 errors.
- Math verifier: 20 tests passed, with the existing Starlette/httpx deprecation warning tracked as `TD-DEP-001`.
- iOS simulator: `xcodebuild test` succeeded on iPhone 16 Pro simulator.
- Docs: `documentation check passed: 270 Markdown files`.
- Contracts: `contract check passed`.
- Secret scan: `secret scan passed`.

Latest Sprint 4.10 local evaluation:

- `make eval-ai` PASS in `LOCAL_FIXTURE_REGRESSION` mode.
- Dataset checksum: `769befe32df0f8646aec4c5fa49c777bb4b39b990e92fd70af18811f6270a50f`.
- Latest report checksum: `0c4c9dcca1531906fdc815df0beb90f26b29b6704fd3d1326da33d8af5618da7`.
- 18 cases, 13 critical cases.
- Zero false authoritative accept, false ready-for-solve, unsafe false accept, invented source reference, cross-user accepted, stale-lineage accepted, and prompt-instruction-executed counts.

Historical sprint reports record prior local validation for Sprints 4.1 through 4.9. The aggregate local gate was rerun for this report under the required Docker/Testcontainers and CoreSimulator permissions.

## Architecture Review

Problem ingestion remains backend-authoritative after asset upload. The iOS app owns capture and display state but does not create canonical problems, call AI providers, call PostgreSQL, access object-storage credentials, or call the math verifier directly.

AI behavior remains provider-neutral through capability route provenance. Prompt/model/schema identity is explicit in evaluation assets and reports. Evaluation tooling is repository-local and does not change product runtime persistence.

## Security And Privacy Review

Phase 4 preserves the rule that AI output is untrusted input. OCR/parser/classification outputs are validated before downstream use, unsafe canonical inputs are rejected, and source references must map to known evidence.

Sprint 4.10 evaluation assets are synthetic and non-training-eligible. Protected holdout data is intentionally absent from the repository. Evaluation artifacts avoid raw student content, provider secrets, signed URLs, object keys, prompt payloads, and raw provider outputs.

## Open Debt

- `TD-CAPTURE-001`: real iPhone camera, focus/exposure, Photos picker, Files/PDF picker, low-light, and glare validation.
- `TD-AI-001`: real vision provider and connected representative evaluation route.
- `TD-AI-002`: representative recognition accuracy calibration.
- `TD-AI-003`: real parser provider and connected representative evaluation route.
- `TD-AI-004`: representative parser/canonicalization/classification accuracy calibration.
- `TD-AI-005`: protected ingestion holdout payload and restricted evaluation workflow.
- `TD-PRIV-001`: future attempt/mastery/tutor data lifecycle contributors.

## Phase 5 Handoff

Phase 5 may consume:

- durable `ProblemSession` state and exact recovery actions;
- selected parse authority and immutable parse revision lineage;
- safe canonical math and verifier-input handoff contracts;
- current canonical and classification summaries;
- local ingestion evaluation reports as regression evidence for Phase 4 routes.

Phase 5 must not treat local fixture evaluation as production provider accuracy. Solver, verification verdict, tutoring, attempt, mastery, and broader AI evaluation gates must be implemented in their own sprints.
