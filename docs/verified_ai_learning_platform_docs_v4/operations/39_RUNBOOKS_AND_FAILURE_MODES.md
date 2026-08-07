# Runbooks and Failure Modes

## AI provider outage

1. Confirm provider-specific failures.
2. Enable/verify fallback route.
3. Disable nonessential secondary calls if capacity/cost requires.
4. Communicate degraded behavior if user impact is visible.
5. Monitor fallback latency/cost.

## Math verifier outage

Solver may continue, but affected answers become UNVERIFIED or verification is deferred. Never silently mark VERIFIED.

## PostgreSQL saturation

- inspect pool/query latency,
- identify expensive queries,
- reduce nonessential background load,
- optimize/index/scale,
- do not blindly increase connection count.

## Redis outage

Caches and throttling may degrade/reset, but canonical learning/billing state remains safe.

## Billing notification backlog

Use authoritative transaction/server state sync, process events idempotently and monitor backlog age.

## Verification quality incident

If false VERIFIED is suspected:
1. disable affected policy/method via feature/config,
2. downgrade new affected results,
3. identify policy/model/problem-type scope,
4. run golden regression,
5. reverify affected historical cases if needed,
6. communicate material impact transparently.

## Cost spike

Inspect traffic, abusive accounts, route changes, retries and prompt/context growth. Emergency downgrade of noncritical route is allowed if trust invariants remain intact.

<!-- HYBRID_AI_STRATEGY_V3:START -->
## Additional runbooks required

Maintain runbooks for:

- provider quota exhaustion;
- sudden inference cost spike;
- secondary-solver escalation storm;
- model quality regression;
- future self-hosted inference saturation/outage;
- emergency route rollback.
<!-- HYBRID_AI_STRATEGY_V3:END -->
