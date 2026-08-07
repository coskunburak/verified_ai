# Documentation V4 Changelog — Exhaustive Repository Hierarchy Release

## Release intent

V4 focuses on repository determinism and context completeness. No core product strategy was reversed. The API-first hybrid AI strategy, modular-monolith backend, PostgreSQL source of truth, native Swift/SwiftUI client, deterministic Python/SymPy verifier, and conditional Phase 13 proprietary-ML strategy remain accepted.

## Major changes

1. Expanded `quality/56_COMPLETE_PRODUCTION_REPOSITORY_FILE_HIERARCHY.md` from a broad production tree into an exhaustive path catalog covering:
   - all core iOS platform areas;
   - feature-by-feature iOS Domain/Data/Presentation/Test placement;
   - every Spring Boot bounded context/module;
   - migrations and backend test mirrors;
   - internal Python math-verifier internals;
   - contracts, schemas, curriculum, prompts and evaluations;
   - conditional Phase 13 ML/self-hosted inference paths;
   - infrastructure/IaC/observability;
   - scripts, tooling and generated artifacts;
   - placement decision algorithm and dependency matrix;
   - data, secrets, future client and final hierarchy invariants.
2. Added `quality/65_CANONICAL_DOCUMENTATION_TREE_AND_SOURCE_OF_TRUTH_MAP.md` containing the complete Markdown knowledge-base tree and reading/source-of-truth rules.
3. Added `quality/66_FILE_PLACEMENT_OWNERSHIP_AND_DEPENDENCY_MATRIX.md` for deterministic placement decisions by humans and coding agents.
4. Added hierarchy alignment notes to iOS, backend, root hierarchy, Codex standards, and AI-agent context documents.
5. Regenerated the documentation manifest after all updates.
6. Packaged the complete knowledge base, including every existing Phase and Sprint Markdown file, into the V4 ZIP release.

## Compatibility

V4 is semantically compatible with V3. When an older hierarchy example is less detailed than V4, V4's `quality/56` path rules take precedence unless a higher-priority ADR/domain/security invariant says otherwise.
