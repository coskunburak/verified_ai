# MCP Workflow: NotebookLM and Codex

## Goal

Use NotebookLM as a deep semantic/research layer and Codex as an implementation agent, both grounded in the same canonical repository documentation.

## What MCP should expose

- Markdown docs,
- source repository,
- build/config files,
- OpenAPI schema,
- migrations,
- tests.

Never expose production secrets.

## NotebookLM role

Best suited for:
- product/domain comprehension,
- comparing architectural alternatives,
- cross-document consistency review,
- discovering missing assumptions,
- preparing scoped implementation briefs.

NotebookLM responses should reference exact canonical document names.

## Codex role

Best suited for:
- implementing a bounded task,
- migrations,
- tests,
- refactors within architecture,
- documentation updates.

Codex should always be given/asked to read the relevant source-of-truth files first.

## Handoff pattern

NotebookLM creates a brief containing:
- objective,
- owning bounded contexts,
- invariants,
- API/schema impact,
- acceptance criteria,
- risks.

Codex receives:
"Implement this brief. First read 00, 10 and all feature-specific documents listed below."

## Documentation-first protocol

For large feature:
1. update semantic spec,
2. review domain/architecture,
3. create implementation plan,
4. implement,
5. test,
6. update docs/ADR.

## Avoid context flooding

Do not always send all documentation to Codex. Use reading recipes in `44_AI_AGENT_CONTEXT_AND_READING_ORDER.md`. Cross-cutting changes require broader context.

## Conflict rule

If chat instruction conflicts with repository docs:
- identify conflict,
- follow the latest explicitly confirmed product decision,
- update canonical docs/ADR immediately so future agents do not inherit stale architecture.

## Recommended task metadata

Each MCP coding task should contain:
- objective,
- owning module,
- relevant docs,
- invariants,
- acceptance tests,
- forbidden changes,
- database impact,
- API impact,
- rollout flag requirement.

## Example

```text
Objective:
Add user-correctable ProblemParse revisions.

Read:
00_MASTER_INDEX.md
10_DOMAIN_INVARIANTS...
14_API_DESIGN...
19_BACKEND_ARCHITECTURE...
22_POSTGRESQL_DATA_MODEL...
40_TEST_STRATEGY...
41_ENGINEERING_STANDARDS...

Rules:
- never overwrite old parse revision;
- only selected parse is used for solve;
- optimistic concurrency;
- correction is auditable.

Deliver:
backend endpoint + Flyway migration + tests + iOS mapping + docs update.
```

<!-- HYBRID_AI_STRATEGY_V3:START -->
## MCP rule for model-related implementation

NotebookLM research recommendations about new models or training are advisory until Codex checks them against ADR-005/006/007, documents 57–64, existing unit-economics evidence, and the current sprint exit gate. Codex must not convert speculative model recommendations directly into production architecture.
<!-- HYBRID_AI_STRATEGY_V3:END -->
