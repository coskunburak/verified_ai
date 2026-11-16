# Sprint 9.6B — TYT/AYT Curriculum Expansion, Turkish New-Gen Question Bank & SymPy Benchmarks (Option A — Wave 1)

## Sprint mission

Expand the Phase 9 curriculum taxonomy, PostgreSQL question bank, and golden evaluation benchmarks to production ÖSYM **TYT Yeni Nesil Problem** and **AYT Zor Matematik** rigor before executing `Sprints 9.7–9.10`.

This sprint implements **Bridge `B1` + `B2` + `B3`** under **Approved Option A (Wave 1 of 5)** per `PHASE_09_TYT_AYT_BRIDGE_AND_REMAINING_SPRINTS_PLAN.md`.

## Why this sprint exists

As of `Sprint 9.6` (`V059`), `StaticCurriculumTaxonomyCatalog` contains 27 active skills across 7 topics and `V056` seeds only 16 English `TYT-MATH-2026-*` questions (`0` `AYT-MATH` items, `0` multi-paragraph Turkish word problems). Without native ÖSYM `2026.2` skills, >= 120 SymPy-verified Turkish questions with distractor trap mappings, and Turkish `evaluations/` regression slices, downstream `Sprint 9.7` (Mistake Attribution), `Sprint 9.8` (48-Hour Formula/Trap Sheet), `Sprint 9.9` (Pacing Analytics), and `Sprint 9.10` (`tr-TR` Spoken Math VoiceOver) cannot validate realistic Turkish exam behavior.

## Preconditions and dependencies

- Read `00_MASTER_INDEX.md`, `PHASE_09_EXAM_AND_ASSESSMENT_PLATFORM.md`, and `PHASE_09_TYT_AYT_BRIDGE_AND_REMAINING_SPRINTS_PLAN.md` before coding.
- Confirm `Sprint 9.6` (`V059`) achieved `LOCAL_ACCEPTANCE_PASSED` (`SPRINT_9.6_EXECUTION_REPORT.md`).
- Preserve `ADR-005` (API-first + deterministic `SymPy` verifier), `ADR-006` (`training_eligible = FALSE` for student data; SME/synthetic curation only), `ADR-010` (native 2D math & accessibility), and `ADR-011` (`verifiedai.exam.*` feature flags default to `false`).
- Preserve full backward compatibility for existing `2026.1` exam definitions (`V054`) and `V056` blueprint UUIDs (`94000000-...101`, `...102`).

## In scope

- **Bridge `B1` — ÖSYM Curriculum Ontology `2026.2`:** Expand `StaticCurriculumTaxonomyCatalog.java`, `MistakeTaxonomy.java`, and `V060` (`TR_YKS_TYT_MATH` and `TR_YKS_AYT_MATH` `2026.2` `ACTIVE` definitions with `2026.1` marked `DEPRECATED` without altering existing `student_exam_targets` snapshots).
- **Bridge `B2` — Turkish New-Gen TYT & Hard AYT Question Bank (`V060`):** Author and double-sign (`author_id <> reviewer_id`) **>= 60 Türkçe TYT** questions (including **>= 36** new-generation paragraph/table/logic problems) and **>= 60 zor Türkçe AYT** questions (Polynomials, Complex Numbers, Logarithms, Sequences, Trigonometry, Limits, Derivatives, Integrals, Probability, Analytic Geometry), each with 5 options (`A`–`E`), distractor-to-`mistake_code` rationales, `content_sha256`, `has_visual_diagram`/`alt_text_description`, and 5 new active blueprints (`BP-TYT-MATH-WORD-PROBLEMS-V2`, `BP-TYT-MATH-FULL-SIM-2026-V2`, `BP-AYT-MATH-DIAG-2026-V2`, `BP-AYT-MATH-LTI-DRILL-2026-V2`, `BP-AYT-MATH-FULL-SIM-2026-V2`).
- **Bridge `B3` — Turkish Golden Datasets & `SymPy` Benchmark Gate (`evaluations/`):** Build `evaluations/golden-datasets/parsing/yks-tr-ingestion-v1/` and `evaluations/golden-datasets/solving/yks-tr-hard-v1/` (`>= 60` verified `tr-TR` cases: `30` TYT word problems + `30` hard AYT problems) and enforce automated verification via `scripts/quality/validate_yks_tr_benchmarks.py`.
- **iOS Dynamic Type `AX3` Verification:** Verify multi-paragraph Turkish stems, 5-option layouts, and diagram `alt_text_description` accessibility in `apps/ios/VerifiedAI/Features/Exam/`.

## Explicitly out of scope

- Any `Bridge B4` Phase 13 export contract changes or `Sprint 9.7–9.10` tables (`V061–V064`, owned by Waves 2–5).
- Training, fine-tuning, or exporting production learner data (`ADR-006`).
- External/live paid LLM judge calls inside automated acceptance gates.

<!-- LEARNING_OS_PRODUCTION_SPEC_V1:START -->
# Learning OS Production Implementation Specification v1 (`2026-11-16`)

## 1. Sprint Metadata & Status
- **Sprint ID:** `SPRINT_9.6B`
- **Option A Wave:** **Wave 1 of 5** (`Bridge B1 + B2 + B3`)
- **Canonical Title:** ÖSYM TYT/AYT Curriculum Expansion (`2026.2`), Turkish New-Generation & Hard AYT Question Bank, and SymPy Benchmark Gate
- **Status:** `PLANNED_READY_FOR_EXECUTION` (`Sprint 9.6` accepted; Option A Wave 1)
- **Primary Spec Reference:** `sprints/phase09_exam_and_assessment_platform/PHASE_09_TYT_AYT_BRIDGE_AND_REMAINING_SPRINTS_PLAN.md`

## 2. Strategic Objective & User Value
Eliminates thin English placeholder question pools by populating both `TR_YKS_TYT_MATH` and `TR_YKS_AYT_MATH` with authentic Turkish ÖSYM new-generation paragraph problems and high-difficulty AYT calculus/algebra items, where every distractor (`A–E`) maps to a trackable `MistakeTaxonomy` code and every numerical/symbolic key is verified deterministically by `SymPy`.

## 3. Entry Gate & Preconditions
- `Sprint 9.6` (`V059`) `LOCAL_ACCEPTANCE_PASSED`.
- `ADR-005`, `ADR-006`, `ADR-010`, `ADR-011` invariants preserved.

## 4. Domain Invariants & Mathematical Rules
1. **Weight Conservation (`INV-EXAM-TYT-WEIGHT-40`):** All `exam_topic_weights` for both `TR_YKS_TYT_MATH` `2026.2` and `TR_YKS_AYT_MATH` `2026.2` sum strictly to `40.0000`, verified by `ExamBlueprintValidator`.
2. **Non-Destructive Curriculum Versioning:** Activating `2026.2` marks `2026.1` `DEPRECATED` (`superseded_by_version = '2026.2'`) without breaking existing `student_exam_targets` snapshots (`V054`) or `V056` blueprint assembly tests.
3. **Dual-Signature Provenance & Zero-Student-Data (`V056`, `ADR-006`):** Every item in `V060` enforces `author_id <> reviewer_id`, `verification_status = 'VERIFIED'`, `training_eligible = FALSE`, valid 5-option `A–E` keys, `has_visual_diagram` + non-trivial Turkish `alt_text_description` (`>= 10` chars) where visual data exists, and deterministic `content_sha256` / `provenance_hash`.
4. **SymPy Mathematical Truth Gate:** No `V060` question or `yks-tr-hard-v1` golden case passes `validate_yks_tr_benchmarks.py` unless `services/math-verifier` (`SymPy`) confirms a unique valid correct option and distinct distractor values.

## 5. Owning Backend, Verifier & Evaluation Modules
- `com.verifiedai.curriculum`: `StaticCurriculumTaxonomyCatalog.java` (`MATH.WORD_PROBLEMS`, `MATH.ALGEBRA`, `MATH.SEQUENCES`, `MATH.TRIGONOMETRY`, `MATH.PROBABILITY`, `MATH.GEOMETRY` skills).
- `com.verifiedai.mistake`: `MistakeTaxonomy.java` (`WORD_VARIABLE_MISASSIGNMENT`, `DOM_LOG_NON_POSITIVE`, `DOM_EXTRANEOUS_ROOT_DENOM`, `TRIG_PERIOD_OMITTED`, `ABS_SINGLE_BRANCH_ONLY`, `CALC_LIMIT_INDETERMINATE_SUB`, `CALC_CHAIN_RULE_OMITTED`, `CALC_INTEGRAL_BOUNDS_SWAPPED`).
- `com.verifiedai.exam`: `ExamBlueprintValidator`, `JdbcExamQuestionBankRepository`, assembly services (consuming expanded `2026.2` pools without breaking `2026.1`).
- `evaluations/` & `scripts/quality/`:
  - `evaluations/golden-datasets/parsing/yks-tr-ingestion-v1/` (`manifest.yaml` / `.json`, `regression.jsonl`, `hard-tail.jsonl`)
  - `evaluations/golden-datasets/solving/yks-tr-hard-v1/` (`manifest.json` / `.yaml`, `regression.jsonl`, `hard-tail.jsonl`, `solver-evaluation-case-v1` schema, `locale = tr-TR`)
  - `scripts/quality/validate_yks_tr_benchmarks.py`

## 6. Database Migration & Persistence Contract (`PLANNED V060`)
- **Migration File:** `services/api/src/main/resources/db/migration/platform/V060__yks_tyt_ayt_curriculum_expansion_and_question_bank.sql`
- **Schema Usage (No breaking DDL to `V054`/`V056` tables; pure curated production seed & blueprint extension + consistency tests):**
  - Inserts `2026.2` `ACTIVE` rows in `exam_definitions`, `exam_sections`, `exam_topic_weights` for `TR_YKS_TYT_MATH` and `TR_YKS_AYT_MATH`, and transitions `2026.1` to `DEPRECATED` (`superseded_at`, `superseded_by_version = '2026.2'`).
  - Inserts **>= 120 Turkish questions** (`>= 60` TYT, `>= 60` AYT) into `exam_question_bank_items`, 5 options (`A–E`) per item into `exam_question_options` with canonical `mistake_code` distractor rationales, and provenance records into `exam_question_provenance_records`.
  - Registers and activates 5 new `exam_assessment_blueprints` + `exam_blueprint_topic_rules` covering full 40Q TYT/AYT simulations, 12Q new-gen TYT word problems, 10Q AYT diagnostic, and 12Q AYT Limit-Derivative-Integral drills.

## 7. REST API Contracts
- Preserves existing `V054`–`V059` REST contracts (`GET /v1/exams/catalog`, `POST /v1/exams/assessments/assemble`, `GET /v1/exams/readiness`) while serving `2026.2` Turkish blueprints and >= 120 questions cleanly with zero "insufficient verified items" fallback warnings on those blueprints.

## 8. iOS (`apps/ios/VerifiedAI/`) Scope
- Verifies long-stem Turkish new-gen question layout, 5-option `A–E` selection cards, diagram accessibility labels, and `Dynamic Type AX3` wrapping without clipping in `Features/Exam/` tests/snapshots.

## 9. AI Capability & Unit Economics Guardrail
- **`$0.00` runtime LLM cost** for assembly/verification gates; all `V060` items and `yks-tr-*` golden benchmarks enforce `training_eligible = FALSE` / `NOT_ELIGIBLE`.

## 10. Privacy, Safety & Compliance
- Zero student PII or learner upload data enters `V060` or `evaluations/golden-datasets/`. Verified via SHA-256 manifest checks and `secret_scan.sh`.

## 11. Automated Test Matrix (Categories A–E)
- **Category A (Domain & Taxonomy):** `StaticCurriculumTaxonomyCatalogTest`, `MistakeTaxonomyTest`, `ExamBlueprintValidatorTest`.
- **Category B (Flyway & SQL Integrity):** `V060MigrationSqlConsistencyTest`, PostgreSQL migration test verifying `2026.1 -> 2026.2`, `40.0000` weight sums, `author_id <> reviewer_id`, valid 5-option keys, `content_sha256`, and deterministic full-40Q assembly for both `TR_YKS_TYT_MATH` and `TR_YKS_AYT_MATH`.
- **Category C (Python SymPy & Golden Benchmarks):** `python3 scripts/quality/validate_yks_tr_benchmarks.py` + `evaluations/runners/solver_evaluation.py --mode local`.
- **Category D/E (iOS UI & AX3 Layout):** XCTest layout/snapshot verification for multi-paragraph Turkish TYT word problems and symbolic AYT items.

## 12. Owner-Only Acceptance Command
- `bash scripts/quality/sprint_9_6b_owner_acceptance.sh --VerifyHost`

## 13. Exit Gate & Downstream Consumers
- Unlocks **Wave 2 (`Sprint 9.7` + `Bridge B4` — `V061` Post-Assessment Mistake Attribution & Phase 13 Seed Export)** with full Turkish TYT/AYT question pools and distractor trap mappings in place.
<!-- LEARNING_OS_PRODUCTION_SPEC_V1:END -->
