# Sprint 9.8 execution report

Status: COMPLETED — OWNER ACCEPTANCE PASSED (2026-11-18). All five Sprint 9.8 packages, backend/PostgreSQL/SymPy gates, global schema/API/localization gates, iOS static/XCTest gates, and the 435-file documentation check passed owner verification. Feature flag `verifiedai.exam.pre-exam-mode-enabled` remains `false` by default; production activation requires separate owner authorization.

Base HEAD: bf274194bba331e7b2daca85a7fdecd06021b32b; branch main. No commit or push. Unrelated prompt preserved. Agent ran no tests/builds/Docker/acceptance scripts. Lightweight diff inspection is not runtime validation.

## Delivered source

1. V062 private snapshots/export deduplication, strict review/sheet schemas and fixtures, mirrored dual-prefix API contracts.
2. Date-only-safe window resolver, mastery policy, shared catalog loader, profile/signal assembly, transactional owner lock, source fingerprints, resource-specific ETags, authenticated endpoints, feature guard, privacy export/delete/expiry and bounded telemetry.
3. Single Java resource catalog/hash with offline Python SymPy polynomial identities, counterexample witnesses and display parity. Two canonical factoring cards; unsupported pairs are excluded.
4. Swift Codable models with typed time/evidence enums; opt-in conditional transport and no-content POST; one-record atomic SQLite bundles keyed by owner/exam; expiry, identity and generation guards; no 401/403 fallback; two navigation entrypoints; flat accessible localized UI; measured one-page A4 renderer and local sharing cleanup. New sources/tests registered in Xcode.
5. Targeted Java/Python/Swift test sources (including 401 cache-purge and corrupted-cache online-recovery hardening), expanded static Swift gate (`PreExamModels`, networking/SQLite files), Sprint 9.8 contract wiring in `validate_json_schemas.py` and `check_contracts.py`, remaining-host owner entrypoint, file inventory and acceptance matrix.

## Owner evidence

Owner-verified PASS evidence (`2026-11-18`): H-15 contracts (`validate_sprint_9_8_contracts.py`), global schema/localization/API inventory (`validate_json_schemas.py` with `431` EN/TR keys, `check_contracts.py`), six domain tests (`ExamStartInstantResolverTest`, `PreExamReviewPolicyTest`), three entity-tag tests (`PreExamEntityTagsTest`), three V062 persistence cases (`PreExamStudySessionIntegrationTest`, two initial passes plus targeted third pass), privacy lifecycle (`PreExamPrivacyLifecycleTest`), backend service/controller/module tests (`PreExamReviewServiceTest`, `PreExamControllerTest`, `ModulithArchitectureTest`, passing after fixture correction), two SymPy formula tests (`test_pre_exam_formula_templates.py`), two HTTP integration tests (`PreExamHttpIntegrationTest`), static Swift checks (`sprint_9_8_swift_check.sh`), 15 iOS XCTest cases (`APIClientConditionalGETTests` 2/2, `APIClientTests` 3/3, `PreExamCramViewModelTests` 7/7, and `PersonalCheatSheetPDFRendererTests` 3/3 on targeted rerun after the `HelveticaNeue`/`NSStringDrawingOptions` fix), and `docs_check.py` (`435` Markdown files prior to final closure edits). See [acceptance matrix](SPRINT_9.8_ACCEPTANCE_MATRIX.md) for the full audit trail.

## Architecture and security decisions

Canonical exam identifiers and existing alias mapping are preserved. A LocalDate never becomes a claimed official start Instant. Known mastery below 0.50 or missing mastery cannot personalize a card. Raw evidence and catalog fingerprints invalidate server snapshots. Review and sheet validators differ; Swift only accepts same-owner same-exam unexpired snapshots and retries a cacheless 304 unconditionally.

Cache generations advance on existing logout/account-delete purge paths, preventing late responses from restoring deleted data. Cancellation and integrity errors cannot cause offline fallback. Cache JSON bundles atomically contain both resources and their ETags/expiry. Existing HTTP send behavior is preserved; the new transport shares auth interception and one eligible refresh retry. Actual HTTP status controls fallback eligibility.

PDF renderer measures text at an 11pt minimum in two columns and creates exactly one A4 page. Full cards that cannot fit are omitted with an explicit count; app displays the complete arrays. Mathematical display text is used rather than pretending raw LaTeX is formatted. Temporary files are protected and deleted on replacement or share dismissal; external copies created by the user's share destination remain outside app control. Crash-time OS temporary-file cleanup is not guaranteed.

## Remaining release limits

- **Rollout state:** `verifiedai.exam.pre-exam-mode-enabled` defaults to `false`; Sprint 9.8 acceptance does not activate the feature in staging or production.
- **AI economics:** `llm_tokens_used = 0` for H-15 generation and PDF rendering runs on-device; overall platform infrastructure costs are not claimed to be zero.
- **Privacy:** `training_eligible = false`, owner-scoped snapshots, user-isolated offline SQLite cache, privacy lifecycle export/deletion, and 24-hour TTL purge are enforced.
- **Mathematical scope:** `pre-exam-catalog-v1` covers two verified algebra identities (`difference-squares`, `square-sum`), not universal TYT/AYT coverage; insufficient evidence falls back explicitly to generic references.
- **Accessibility & manual verification:** Automated `AX3` hosting and PDF text-extraction tests passed, while manual simulator/device visual inspection and complete `tr-TR` VoiceOver reading-order verification remain operational follow-ups. No Sprint 9.9 or Phase 10 work was started.
