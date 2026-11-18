# Sprint 9.8 contract decision

Status: COMPLETED — OWNER ACCEPTANCE PASSED (2026-11-18); feature-flagged off by default.

H-15 uses `/v1/exams/pre-exam-review/{examCode}` and `/v1/exams/cheat-sheet/{examCode}`, with equivalent `/api/v1` aliases. Export notifications use `POST /v1/exams/pre-exam-review/sessions/{sessionId}/export-events` and its alias. Canonical codes are `TR_YKS_TYT_MATH` and `TR_YKS_AYT_MATH`; no examTrack axis is introduced.

A candidate LocalDate does not establish an exam start Instant. Date-only responses have null targetExamAt and hoursRemaining and DATE_ONLY_ESTIMATE precision. Missing dates use NO_TARGET_DATE; past dates use EXAM_PASSED. Exact countdowns require a trusted start source. Evidence status is independent of time status. Missing mastery never qualifies a personalized card. Generic references are explicitly separate.

GET responses require authentication, owner checks, no-store and quoted resource-specific ETags. Weak If-None-Match comparison follows GET semantics, including lists and wildcard. A 304 has no body; clients may reuse only an unexpired same-owner snapshot, otherwise retry unconditionally. Authorization, decode and ownership failures never permit offline fallback. Feature disabled returns 403; absent and foreign sessions return 404. Export event keys are owner/session scoped and deduplicated until snapshot expiry.

Schema versions are pre-exam-review-v1 and exam-cheat-sheet-v1. Catalog version is pre-exam-catalog-v1. ETags exclude their own value and include owner, evidence, policy and catalog fingerprints. Separate review/sheet ETags prevent cross-resource 304 reuse. Session identity and generation timestamps remain stable while source state is unchanged and the snapshot remains fresh. Snapshots expire after at most 24 hours, or earlier when a time boundary changes.

API budgets are ten personalized traps and twelve formulas. Generic references are a distinct array. The one-page PDF uses measured selection and an explicit omitted-item count; the app retains the complete list. No raw LaTeX is drawn as formatted mathematics.

V062 introduces private snapshots and deduplicated export events. Rollback disables the feature; do not roll back prior migrations or drop private data during normal deployment. Account deletion removes export events through cascade and snapshots explicitly. TTL cleanup removes expired snapshots. Runtime LLM tokens remain zero and training eligibility false. Owner acceptance is required before rollout.
