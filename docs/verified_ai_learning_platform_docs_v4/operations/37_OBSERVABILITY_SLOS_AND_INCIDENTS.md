# Observability, SLOs, and Incidents

## Observability pillars

Logs: structured JSON + traceId.
Metrics: Micrometer/OpenTelemetry.
Traces: API through external dependencies where practical.

## Metrics

HTTP:
- count,
- 4xx/5xx,
- p50/p95/p99 latency.

DB:
- pool saturation,
- query latency,
- slow query count.

AI:
- provider/model success,
- 429/5xx,
- schema failure,
- latency,
- tokens/cost.

Verification:
- latency,
- status distribution,
- timeouts,
- false-verification incident reports.

Product:
- solve completion,
- wrong-answer reports,
- tutor completion,
- mastery/study session events.

## SLOs

Define numeric SLOs from measured baseline for API availability, problem submission, solve completion and billing sync. AI correctness uses quality gates rather than availability SLO alone.

## Alerts

Actionable alerts:
- elevated solve failures,
- DB saturation,
- billing backlog,
- provider outage without fallback,
- verification quality anomaly,
- unexpected AI cost spike.

## Incident severity

SEV1: security breach, widespread false VERIFIED, billing corruption, major data loss.
SEV2: broad solve/entitlement outage.
SEV3: partial degradation.

## Post-incident record

Timeline, impact, detection, root cause, contributing factors, remediation, preventive tests/docs. Blameless format.

<!-- HYBRID_AI_STRATEGY_V3:START -->
## AI FinOps observability

Add first-class metrics for total AI spend, cost per verified solution, secondary-solver escalation rate, model/provider route share, cache hit savings, and paid-learner AI COGS.

Route/model changes require correlation between quality, latency and cost. See `operations/63_AI_CAPACITY_FINOPS_AND_PROVIDER_BUDGET_OPERATIONS.md`.
<!-- HYBRID_AI_STRATEGY_V3:END -->
