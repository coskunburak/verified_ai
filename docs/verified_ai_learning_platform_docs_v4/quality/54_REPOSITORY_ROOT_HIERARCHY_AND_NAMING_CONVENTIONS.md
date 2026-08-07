# Repository Root Hierarchy and Naming Conventions

## Monorepo root

```text
verified-ai-learning-platform/
├── apps/
│   └── ios/
├── services/
│   ├── api/
│   └── math-verifier/
├── packages/
│   └── contracts/
├── infra/
│   ├── docker/
│   ├── local/
│   ├── staging/
│   └── production/
├── docs/
├── scripts/
├── tools/
├── .github/
│   └── workflows/
├── docker-compose.yml
├── Makefile
├── .editorconfig
├── .gitignore
├── SECURITY.md
├── CONTRIBUTING.md
└── README.md
```

## Naming

### Domain
Use business language, not technical aliases.
`VerificationRun` is better than `CheckEntity`.

### IDs
`ProblemSessionId`, `SkillId`, etc. in code. DB may use `id` with table context.

### APIs
Plural resources and stable paths. Commands use sub-resource/action only when REST resource modeling is awkward.

### Database
snake_case.

### Swift
Swift API Design Guidelines; feature folders use PascalCase.

### Java
Packages lowercase, types PascalCase, methods camelCase.

### Events
Past tense facts: `AttemptEvaluated`, not `EvaluateAttemptEvent`.

### Booleans
Prefer positive semantic names: `selected`, `active`, `recoverable`.

## No junk-drawer folders

Avoid uncontrolled:
- common/
- helpers/
- utils/
- misc/

A utility with domain meaning belongs to its owning module. Truly cross-cutting primitives require explicit shared-kernel justification.

<!-- HYBRID_AI_STRATEGY_V3:START -->
## Conditional ML roots

Future `ml/` and `services/model-inference/` roots are reserved by the exhaustive hierarchy document but are not created during V1. Experimental training code may not be placed in `scripts/`, `shared/`, Spring modules, or iOS targets as a shortcut.
<!-- HYBRID_AI_STRATEGY_V3:END -->

## V4 exhaustive hierarchy relationship

This document defines stable root naming conventions. The canonical deep tree is `quality/56_COMPLETE_PRODUCTION_REPOSITORY_FILE_HIERARCHY.md`; the documentation-only tree is `quality/65_CANONICAL_DOCUMENTATION_TREE_AND_SOURCE_OF_TRUTH_MAP.md`; deterministic placement rules are in `quality/66_FILE_PLACEMENT_OWNERSHIP_AND_DEPENDENCY_MATRIX.md`.
