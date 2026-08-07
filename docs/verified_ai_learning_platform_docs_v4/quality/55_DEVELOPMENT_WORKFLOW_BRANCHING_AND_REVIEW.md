# Development Workflow, Branching, and Review

## Work unit

A feature starts from semantic issue/brief containing:
- objective,
- user outcome,
- owning bounded context,
- invariants,
- acceptance criteria,
- API/data impact,
- rollout risk.

## Branch strategy

Prefer short-lived feature branches and frequent integration. Avoid long-running architectural branches that diverge from main.

## Pull request content

PR should state:
- what changed,
- why,
- docs consulted,
- tests,
- migration impact,
- screenshots for UI,
- AI evaluation delta if applicable,
- rollout/flag plan.

## Review order

1. Domain correctness.
2. Security/privacy.
3. Data/migration correctness.
4. Failure/idempotency.
5. Test coverage.
6. Performance/cost.
7. Code style.

## Codex-generated PRs

Require human review of:
- domain ownership,
- migrations,
- security,
- prompt/model behavior,
- concurrency,
- billing.

## Documentation sync

Semantic behavior changes and documentation are one atomic change from project-maintenance perspective.

<!-- HYBRID_AI_STRATEGY_V3:START -->
## AI/model change review requirements

PRs changing prompts, route policy, provider/model selection, evaluation datasets, or proprietary model artifacts must include:

- evaluation baseline/candidate report;
- expected cost and latency delta;
- affected traffic/capability;
- rollout flag/config;
- rollback path;
- data/training eligibility review if datasets are involved.

“Provider model upgraded” is not sufficient review evidence.
<!-- HYBRID_AI_STRATEGY_V3:END -->
