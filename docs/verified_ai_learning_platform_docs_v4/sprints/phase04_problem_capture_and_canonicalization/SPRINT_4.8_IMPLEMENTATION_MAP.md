# Sprint 4.8 Implementation Map

## Status

IMPLEMENTED - LOCAL QUALITY GATES GREEN

## Core invariant

ProblemParse revisions are immutable. User edits create new `source=USER` parse revisions, and `problem_sessions.current_parse_id` is the only selected-parse authority for canonicalization and later solving.

## Runtime flow

AI ProblemParse
-> parse review
-> user correction command
-> base revision and ownership validation
-> semantic document validation
-> immutable USER ProblemParse revision
-> `problem_sessions.current_parse_id` update
-> canonicalization from selected parse
-> revision history

## Backend implementation

- V014 migration extends `problem_parses` for nullable AI provenance, user correction metadata, parent lineage, idempotency fingerprinting, and source-aware constraints.
- `ProblemParseJpaEntity` now has separate AI and user-correction factories.
- `ProblemSessionJpaEntity` exposes the selected parse pointer and updates it through an explicit select operation.
- `ProblemParseCorrectionApplicationService` owns review, correction creation, revision conflict detection, idempotency replay/conflict handling, correction metrics, and revision history.
- `CanonicalProblemApplicationService` resolves the selected parse instead of latest revision.
- `ProblemAssetLifecycleContributor` exports correction metadata while excluding raw parser output, correction request hashes, and idempotency keys.

## Public API

- `GET /api/v1/problem-sessions/{sessionId}/parse-review`
- `POST /api/v1/problem-sessions/{sessionId}/parse-revisions`
- `GET /api/v1/problem-sessions/{sessionId}/parse-revisions`

Correction creation requires `Idempotency-Key` and rejects stale base revisions with `PARSE_REVISION_CONFLICT`.

## iOS implementation

- `ProblemReview` feature models review state, revision history, editable normalized problem documents, correction reasons, and API transport.
- `ProblemCaptureView` can present parse review from parsed/review-required states before completion.
- `ProblemReviewViewModel` owns draft mutation and idempotent correction submission.

## Privacy

User corrections are account-owned sensitive learning data. They are not verified truth, analytics payloads, or training data by default.

## Explicitly out of scope

- solving
- verification
- mastery updates
- model fine-tuning
- restoring arbitrary old revisions as current
- automatic AI retry after manual correction
