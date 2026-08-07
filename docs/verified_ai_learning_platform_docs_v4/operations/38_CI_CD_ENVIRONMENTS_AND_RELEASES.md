# CI/CD, Environments, and Releases

## Workflows

`.github/workflows/`
- backend-ci.yml
- ios-ci.yml
- verifier-ci.yml
- security-scan.yml
- ai-evaluation.yml
- staging-deploy.yml
- production-deploy.yml

## Backend CI

- compile,
- style/static checks,
- unit tests,
- module boundary tests,
- Testcontainers integration tests,
- Flyway validate,
- dependency/security scan,
- Docker build.

## iOS CI

- build,
- lint/format policy,
- unit tests,
- UI smoke tests,
- archive validation.

## AI evaluation

Run on model/prompt changes and scheduled quality checks, not every documentation-only PR.

## Promotion

Local → CI → Staging → Production.

Staging and production secrets/data are isolated.

## Migrations

Use expand/contract for risky schema changes:
1. add compatible structure,
2. deploy dual-read/write if necessary,
3. backfill,
4. switch,
5. remove old later.

## Feature rollout

Risky features/models: 1% → 5% → 25% → 50% → 100% based on telemetry.

## Rollback

Code rollback must remain compatible with migration state. Model/prompt routing can be rolled back independently via version/config.

<!-- HYBRID_AI_STRATEGY_V3:START -->
## Model/route release pipeline

Prompt/model/router changes are release artifacts. Material changes require evaluation reports before production configuration promotion. Future proprietary model artifacts follow registry states: offline -> shadow -> canary -> production with rollback.
<!-- HYBRID_AI_STRATEGY_V3:END -->

<!-- MODEL_RELEASE_DETAIL_V3:START -->
## Conditional proprietary-model CI/CD

When Phase 13 begins, model training and artifact promotion use separate workflows from application deployment. Training jobs may publish immutable artifacts to the registry; they may not auto-promote to production. Production route promotion references an already evaluated artifact/release ID.
<!-- MODEL_RELEASE_DETAIL_V3:END -->
