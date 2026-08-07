# AI Capacity, FinOps, and Provider Budget Operations

## 1. Objective

Keep AI infrastructure reliable while preventing cost surprises. FinOps is part of production operations, not an end-of-month accounting activity.

## 2. Budget scopes

- provider account/project;
- environment;
- capability;
- entitlement tier;
- daily/monthly application budget;
- future self-hosted accelerator pool.

## 3. Alerts

Alert on:

- spend velocity anomaly;
- cost per verified solution regression;
- retry/fallback spike;
- secondary-solver invocation spike;
- context/token-size regression;
- provider quota exhaustion risk;
- price/configuration change;
- self-hosted GPU saturation/idle-cost anomaly when applicable.

## 4. Safe response hierarchy

For cost pressure:

1. investigate anomaly/abuse;
2. disable accidental/redundant calls;
3. route eligible simple work cheaper;
4. lower nonessential background generation;
5. enforce documented quotas;
6. never downgrade verification truthfulness.

## 5. Provider capacity planning

Maintain documented rate limits, quota ceilings, fallback providers, and escalation contacts/processes.

## 6. Monthly review

Review:

- total AI COGS;
- cost by capability;
- cost by learner tier;
- quality per route;
- forecast next month/quarter;
- candidate tasks for optimization/proprietary replacement.

## 7. Self-hosted future operations

If a proprietary model is introduced, add:

- GPU utilization,
- queue depth,
- tokens/sec or requests/sec,
- model load time,
- OOM/restart metrics,
- autoscaling effectiveness,
- cost per inference at actual utilization.
