# Contributing

## Source of Truth

Read `docs/verified_ai_learning_platform_docs_v4/00_MASTER_INDEX.md` before changing product, domain, architecture, API, data, security, or repository structure.

Higher-precedence documents are domain invariants, accepted ADRs, architecture/data/API contracts, then sprint execution docs.

## Branches and Commits

Use short-lived branches named for the work unit, for example `phase2/api-bootstrap` or `fix/docs-manifest`.

Commits should be scoped and reviewable. Do not mix unrelated formatting, generated artifacts, or local machine state with production changes.

## Required Checks

Before review, run the applicable commands:

```sh
make doctor
make docs-check
make secret-scan
make test-api
make test-verifier
make test-ios
```

If a command cannot run because the local environment is missing a prerequisite, record `NOT EXECUTED - ENVIRONMENT LIMITATION` with the exact command and reason.

## Documentation Updates

Semantic changes and documentation updates are one maintenance unit. Update canonical Markdown in the same change when adding or changing:

- domain language or invariants;
- API contracts;
- database schema;
- iOS state/navigation contracts;
- backend module boundaries;
- security or privacy behavior;
- dependency policy;
- AI/provider/model behavior.

## Migrations

All durable schema changes use forward Flyway migrations. Do not edit an applied production migration. Do not use H2 as a PostgreSQL substitute.

## Security and Secrets

Never commit real credentials, tokens, provider keys, certificates, provisioning profiles, `.env` files, local database dumps, raw student content, or generated metadata such as `.DS_Store`.

## AI-Agent Contributions

AI-generated changes require human review for domain ownership, security, migrations, concurrency, billing/entitlement authority, and prompt/model behavior. Agents must not introduce provider SDKs into domain/application modules or move product policy into prompts/client code.

