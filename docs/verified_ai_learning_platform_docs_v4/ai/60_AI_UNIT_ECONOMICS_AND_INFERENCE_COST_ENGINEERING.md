# AI Unit Economics and Inference Cost Engineering

## 1. Purpose

AI inference is a variable product cost. This document defines the accounting model, engineering budgets, optimization order, and operational dashboards required to keep a subscription learning product economically healthy.

## 2. Canonical units

Track costs at these levels:

- AI request,
- pipeline stage,
- problem session,
- successful solution,
- verified solution,
- tutor session,
- study session,
- learner/day,
- paid learner/month,
- entitlement tier,
- cohort/market.

## 3. Required stage attribution

At minimum:

- image/vision parse,
- text normalization,
- classification,
- primary solve,
- secondary solve,
- arbitration,
- explanation,
- tutor messages,
- practice generation,
- mistake classification,
- embeddings/reranking if used.

## 4. Cost ledger schema concept

Every inference usage record needs:

- trace/request IDs,
- user pseudonymous internal ID where permitted,
- capability,
- route policy version,
- provider/model,
- prompt/schema version,
- input/output/image units,
- provider-reported or estimated cost,
- latency,
- status,
- cache hit/miss,
- escalation reason,
- entitlement tier.

## 5. Engineering budget hierarchy

Budgets exist at:

1. per-request max cost;
2. per-solve expected cost;
3. per-user daily/monthly allowance;
4. per-tier expected COGS;
5. provider monthly budget;
6. total AI COGS/revenue threshold.

Budgets must not silently cause incorrect or misleading answers. When a quality-preserving route cannot be afforded under a product tier, the product must enforce a clear quota/feature rule rather than fake success.

## 6. Optimization order

Always optimize in this order unless evidence says otherwise:

1. eliminate unnecessary calls;
2. use deterministic computation;
3. reduce context/image payload;
4. cache/reuse stable artifacts;
5. route simple tasks to cheaper models;
6. make secondary solving conditional;
7. batch non-interactive work where provider economics support it;
8. replace high-volume bounded tasks with proprietary small models;
9. consider specialized self-hosted solver only after TCO gates.

## 7. Secondary-solver economics

Measure:

- percentage of solves escalating to secondary;
- verification success before secondary;
- incremental quality gain;
- incremental cost;
- skills/problem classes where secondary provides little value.

The escalation policy should be calibrated from data, not intuition.

## 8. Cache policy

Safe cache candidates:

- immutable curriculum metadata,
- normalized known public/reference questions where licensing permits,
- deterministic verification artifacts,
- repeated explanation components tied to immutable solution version.

Avoid cross-user caching of private content unless isolation and privacy semantics are explicit.

## 9. Margin safeguards

Monitor:

- AI COGS / net subscription revenue,
- COGS by Free/Pro/Pro+,
- top 1% expensive users,
- abuse-driven spend,
- provider price/config changes,
- unexplained token/context growth,
- retry storms.

## 10. Cost regression release gate

Any prompt/model change that materially raises expected cost must include:

- before/after quality,
- before/after latency,
- before/after cost,
- affected traffic percentage,
- expected monthly impact,
- rollback configuration.

## 11. Forecasting

Capacity/finance forecast uses:

`active learners × solves per learner × stage invocation rate × unit stage cost`

Forecast separately for median, p90 and stress scenarios.

## 12. Free-tier discipline

Free usage is a marketing/product investment, not unbounded inference. Free tiers require explicit:

- daily/monthly caps,
- expensive-route restrictions,
- abuse/rate limits,
- transparent UX.

## 13. Production dashboard

Minimum dashboard:

- total daily AI cost;
- cost per verified solution;
- cost per active/paid learner;
- primary vs secondary cost;
- provider/model share;
- cache savings;
- verification pass rate;
- revenue vs AI COGS;
- cost anomalies.

## 14. Decision rule for proprietary model work

A task becomes a candidate only if:

- it is measurable and stable;
- volume is material;
- recurring API cost is material;
- adequate eligible data exists;
- projected savings exceed training/serving/operations TCO with safety margin;
- quality can be preserved.
