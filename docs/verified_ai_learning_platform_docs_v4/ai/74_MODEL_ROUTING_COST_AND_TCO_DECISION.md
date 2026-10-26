# Model routing cost and TCO decision

Date: 2026-10-26. Decision: API-first; all model rankings PROVISIONAL. No paid benchmark,
GPU allocation or measured inference throughput. Prices and arithmetic are planning
inputs, not approved production spending limits (runtime defaults remain zero).

## Evidence and assumptions

Verified public listing: [Together pricing](https://www.together.ai/pricing) lists
Qwen3.5 9B at $0.17 input / $0.25 output per million tokens and Qwen3.5-397B-A17B
at $0.60 / $3.60. On-demand GPU cluster H100 is listed at $3.99/GPU-hour; dedicated
inference H100 is a different product, listed at $5.49. Prices can change; verify
region, minimum allocation, taxes, image billing and exact deployment before purchase.
The following model uses the cluster listing, not a claim that one GPU can be leased
alone at that rate. No discount, cache saving or free tier is assumed.

Hypotheses (not measurements): 2,000 input / 1,000 output tokens per primary solve;
OCR 1,000 equivalent input / 200 output tokens; normalization 500/200 tokens;
secondary invocation 10%; one additional stronger fallback on 10% of D requests;
retry overhead 5% of all inference cost. Secondary solving is separate from fallback.
OCR token equivalence is uncertain: actual image charging must replace this assumption.
Reasoning tokens must be included in output; test 5x output as a sensitivity scenario.
Provider fees are included in listed token rates; taxes/payment fees are excluded.

A managed proprietary API uses hypothetical $1/$5 per million primary input/output
for sensitivity comparison, not a verified provider quote. B uses hosted Qwen3.5 9B.
D uses B plus conditional Qwen3.5-397B-A17B fallback. All use hosted OCR/normalization
and the stronger route for optional secondary. These are architecture scenarios, not
claims that an arbitrary model is eligible for each task.

Per-stage cost: OCR $0.000220; normalization $0.000135; B primary $0.000590;
strong call $0.004800; A primary $0.007000. Per logical solve including retries:
A $0.00822675; B $0.00149625; D $0.00200025. Expected provider calls per solve:
A/B 1.05×(3+0.10)=3.255; D 1.05×(3+0.10+0.10)=3.36.
A/B/D add a hypothetical $100/month hosting/monitoring allowance; human operations
are assumed included here and must be replaced with an actual time-cost estimate.

C self-hosts only primary inference: 730 hours/month × $3.99 = $2,912.70/GPU-month,
plus $500 operator time + $50 storage + $100 common hosting. Hypothetical aggregate
throughput 100 output tokens/s per GPU at 60% utilization over 30 days; 155,520
primary jobs/GPU-month. These are capacity assumptions, NOT model hardware requirements.
We assume 5% primary traffic falls back to hosted B, plus the same hosted OCR,
normalization and secondary costs. C variable cost = $0.000907725/solve.

## Monthly scenarios (USD, rounded)

| Paid learners | Solves/learner | Logical solves | Expected A/B calls | A managed | B hosted open-weight | D hybrid | C average-capacity GPUs | C total | GPUs for 10x peak assumption |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 100 | 30 | 3,000 | 9,765 | 124.68 | 104.49 | 106.00 | 1 | 3,565.42 | 1 |
| 100 | 100 | 10,000 | 32,550 | 182.27 | 114.96 | 120.00 | 1 | 3,571.78 | 1 |
| 1,000 | 30 | 30,000 | 97,650 | 346.80 | 144.89 | 160.01 | 1 | 3,589.93 | 2 |
| 1,000 | 100 | 100,000 | 325,500 | 922.68 | 249.62 | 300.02 | 1 | 3,653.47 | 7 |
| 10,000 | 30 | 300,000 | 976,500 | 2,568.02 | 548.88 | 700.08 | 2 | 6,747.72 | 20 |
| 10,000 | 100 | 1,000,000 | 3,255,000 | 8,326.75 | 1,596.25 | 2,100.25 | 7 | 21,946.62 | 65 |

C totals provision average capacity, not the 10x peak column. They do not meet an
interactive peak SLO without extra capacity, queuing or more API overflow. Cold start,
weight loading and idle headroom are not free; reservation avoids cold starts but
pays idle cost. Storage must cover exact model/tokenizer/quantization plus rollback
artifacts. Hardware fit, context length, batching, GPU memory and tokens/s remain
UNKNOWN until a licensed exact-revision benchmark. Multi-GPU expert storage may
invalidate the one-GPU unit entirely. Do not conclude self-hosting is cheaper from
these arithmetic scenarios. Recompute with measured capacity and operator burden.

## Decision and sensitivity

Use hosted APIs for the first approved solver benchmark. Compare B/D quality to A;
no quality-equivalent baseline exists yet. Smaller token price is insufficient if
more retries, longer reasoning, poor Turkish fidelity or lower correctness raise cost.
Cost per correct answer = all inference spend / independently graded correct outcomes;
zero correct outcomes means undefined, not zero. Cost per verified solution is
UNAVAILABLE (only a future projection can use an assumed verification yield).

Vary output tokens 1x/5x, fallback 0/10/30%, secondary 0/10/25%, retry 0/5/20%, peak
3x/10x and utilization 10/30/60%. Report stress cases before business approval.
Revenue is not modeled as guaranteed; taxes, subscriber acquisition, support, payment
fees and gross-margin targets require owner inputs. Numeric business quotas remain
pending approval; no scenario numbers are copied into runtime configuration.

## Sprint 5.4-RM controlled evaluation accounting

The earlier hosted-first benchmark recommendation is superseded for this work package by the owner's
Qwen3.5-4B isolated self-hosted evaluation preference; ADR-005/007 production gates are unchanged.
No scenario prices above were copied into runtime policy. Fixture remains explicitly zero-cost.
VLLM_SELF_HOSTED accepts only owner-approved PREPAID_CAPACITY_FIXED_ALLOCATION with positive
input/output allocations, image=0, exact pricing version and existing aggregate spend reservations.
This is internal capacity allocation, not actual GPU invoice cost or support for a token-billed API.
Unknown usage retains committed ceilings and NULL reported cost; no automatic replay/refund.
Marginal cost and fully allocated GPU/storage/network/operator TCO need separate measured evidence.
Default real-provider budgets remain zero. No real model/economics measurements exist.
[Integration and limitations](../sprints/phase05_ai_solving_and_verification_core/SPRINT_5.4_REAL_MODEL_INTEGRATION_MAP.md).
