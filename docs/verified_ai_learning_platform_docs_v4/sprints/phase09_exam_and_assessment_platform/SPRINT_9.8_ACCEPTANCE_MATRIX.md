# Sprint 9.8 acceptance matrix

Release state: `COMPLETED — OWNER ACCEPTANCE PASSED` (`2026-11-18`). Feature flag `verifiedai.exam.pre-exam-mode-enabled` remains `false` by default; production activation, commit, and push require separate owner authorization.

| Gate | Test / Script | Evidence | Status | Remaining Action |
| --- | --- | --- | --- | --- |
| H-15 contracts | `validate_sprint_9_8_contracts.py` | Owner console PASS | PASS — OWNER VERIFIED | None |
| Global schema/API inventory | `validate_json_schemas.py`, `check_contracts.py` | Owner PASS: 431 EN/TR localizations, Sprint 9.7 and H-15 schemas/OpenAPI verified | PASS — OWNER VERIFIED | None |
| Mathematical catalog | `test_pre_exam_formula_templates.py` | Owner PASS: 2 passed in 0.30s | PASS — OWNER VERIFIED | Two-card scope only |
| Domain | `ExamStartInstantResolverTest`, `PreExamReviewPolicyTest` | Six owner tests passed | PASS — OWNER VERIFIED | None |
| Entity tags | `PreExamEntityTagsTest` | Three owner tests passed | PASS — OWNER VERIFIED | None |
| Persistence | `PreExamStudySessionIntegrationTest` | Two initial passes plus targeted third pass (3 cases total) | PASS — OWNER VERIFIED | None |
| Privacy | `PreExamPrivacyLifecycleTest` | Owner-generated Surefire: one pass | PASS — OWNER VERIFIED | None |
| Backend services/controller/modules | `PreExamReviewServiceTest`, `PreExamControllerTest`, `ModulithArchitectureTest` | Initial run: four passed, one fixture error; corrected test passed | PASS — OWNER VERIFIED | None |
| HTTP integration | `PreExamHttpIntegrationTest` | Owner PASS: two passed | PASS — OWNER VERIFIED | None |
| iOS static | `sprint_9_8_swift_check.sh` | Owner PASS (`H15_STATIC_CHECKS_PASSED_HOST_TESTS_STILL_REQUIRED`) | PASS — OWNER VERIFIED | None |
| iOS runtime | `APIClientConditionalGETTests`, `APIClientTests`, `PreExamCramViewModelTests`, `PersonalCheatSheetPDFRendererTests` | Initial run: 14 passed, 1 PDF footer test failed; targeted rerun after font/drawing fix passed 3/3 (15 tests total: 2 + 3 + 7 + 3) | PASS — OWNER VERIFIED | None |
| Documentation | `docs_check.py` | Owner PASS (`documentation check passed: 435 Markdown files`) before final closure edits | STALE — closure documentation edits applied after PASS | Owner final `docs_check.py` confirmation before commit |

## Acceptance scope and limits

All required Sprint 9.8 contract, SymPy, backend, PostgreSQL V062, iOS static, iOS XCTest, and 435-file documentation gates have owner-verified PASS evidence (`2026-11-18`). Final `docs_check.py` confirmation is required after recording these closure edits.

PDF tests exercise one-page A4 output, Turkish text extraction, content omission and file deletion. AX3 hosting is an automated smoke check, not a manual simulator/device visual clipping or complete `tr-TR` VoiceOver reading-order certification; those manual inspections remain operational follow-ups. The two-card polynomial catalog (`difference-squares`, `square-sum`) has narrow coverage; other trap/skill pairs are excluded and sparse evidence is labeled generic (`INSUFFICIENT_EVIDENCE`). Exact exam starts are not fabricated. `llm_tokens_used = 0` and `training_eligible = false` apply to H-15 generation/storage; overall infrastructure costs are not claimed to be zero.
