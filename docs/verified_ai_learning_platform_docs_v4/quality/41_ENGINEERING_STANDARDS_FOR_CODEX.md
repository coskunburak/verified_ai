# Engineering Standards for Codex and AI Coding Agents

## Mandatory reading

Before changing code, read the Master Index plus relevant domain, invariants, architecture and ADR documents.

## Change discipline

Before implementation identify:
1. owning bounded context,
2. affected aggregate/invariant,
3. API/schema impact,
4. tests,
5. documentation changes.

## Forbidden shortcuts

Do not:
- call AI provider directly from controller,
- let client assign VERIFIED,
- store provider key in iOS,
- add giant generic utils package,
- expose JPA entities from API,
- update mastery from controller,
- skip Flyway for schema change,
- bypass entitlement guard,
- trust LLM JSON merely because it parsed.

## Backend style

- package by feature,
- constructor injection,
- explicit enums/value objects,
- small application services,
- domain repository ports,
- typed config,
- stable domain/API errors.

## iOS style

- feature ownership,
- explicit screen state,
- async/await,
- MainActor for UI mutation,
- dependency injection,
- DTO/domain/view separation,
- no random global network singleton in views.

## Required tests

Business rule → domain/unit test.
DB change → migration + integration test.
Endpoint → security/contract test.
Prompt/schema → schema fixture + AI evaluation.

## Documentation

New term → glossary.
New invariant → invariant doc.
Architectural decision → ADR.
New table → data model.
New prompt family → AI docs.

## Phase 1 Repository Governance Baseline

Until Phase 2 explicitly bootstraps the monorepo, Codex must not create runtime source roots merely to satisfy architecture prose. When Phase 2 begins, repository creation follows `quality/56_COMPLETE_PRODUCTION_REPOSITORY_FILE_HIERARCHY.md` exactly.

### Allowed Phase 1 outputs

- canonical Markdown updates;
- sprint-local implementation maps;
- evidence reports;
- ADRs only for durable decisions not already covered;
- validation notes or contract outlines that do not masquerade as runtime implementation.

### Deferred to Phase 2 or later

- Swift/Xcode project bootstrap;
- Spring Boot application bootstrap;
- Flyway migrations;
- Docker Compose/local infrastructure;
- Python math-verifier runtime;
- CI/CD workflows;
- prompt/schema/evaluation repositories unless a later sprint explicitly starts them.

### Forbidden repository shortcuts

- new catch-all `utils`, `helpers`, `misc`, or `common` folders;
- provider SDK types in domain/application modules;
- Swift UI state as authority for entitlement, mastery, verification, billing, or exam scoring;
- prompt text as the only location for product policy;
- generated clients/schemas mixed with hand-authored source;
- committed local metadata such as `.DS_Store`;
- secret-bearing `.env`, token, key, or provider credential files.

### Dependency admission evidence

Every new dependency must state purpose, owner, alternatives considered where material, maintenance/security/license posture, and removal strategy. AI/ML/training frameworks are isolated to future approved tooling or Phase 13 boundaries and never enter iOS or Spring product modules as convenience imports.

### Generated-file policy

Generated code, OpenAPI clients, coverage, evaluation reports, build output, and derived datasets must live in explicit generated/report/artifact locations and be ignored or versioned according to `quality/56`. Generated output cannot become the only source of a domain contract.

## AI-generated code review checklist

- correct module?
- ownership correct?
- secret-safe?
- authorization?
- idempotent?
- transaction boundary?
- timeout/fallback?
- observability?
- migration?
- tests?
- duplicate concept?

When uncertain, consult source-of-truth docs instead of inventing a second pattern.

<!-- HYBRID_AI_STRATEGY_V3:START -->
## Mandatory Codex rules for AI/model work

Codex must not:

- introduce a self-hosted or fine-tuned model because it appears cheaper;
- collect/store user content for training without explicit approved eligibility design;
- make the secondary solver unconditional without policy/economics evidence;
- call provider SDKs from product modules;
- replace deterministic verification with an LLM judge;
- bypass golden-dataset evaluation for model/prompt/router changes.

Before model-related tasks, read documents 57–64 and ADR-005 through ADR-007.
<!-- HYBRID_AI_STRATEGY_V3:END -->

## V4 file-placement requirement

Before creating or moving a file, Codex must consult `quality/56_COMPLETE_PRODUCTION_REPOSITORY_FILE_HIERARCHY.md` and, when placement is ambiguous, `quality/66_FILE_PLACEMENT_OWNERSHIP_AND_DEPENDENCY_MATRIX.md`. Codex must not create `helpers`, `utils`, `misc`, `common`, new top-level directories, new backend modules, or future `ml/` infrastructure as a convenience without an explicit canonical architecture update.
