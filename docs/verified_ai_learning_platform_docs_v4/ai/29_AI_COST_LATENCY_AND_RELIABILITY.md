# AI Cost, Latency, and Reliability

## 1. Production principle

AI is variable COGS and an external reliability dependency. Optimize **cost per successful policy-compliant learning outcome**, not cost per raw call.

Canonical economics detail: `ai/60_AI_UNIT_ECONOMICS_AND_INFERENCE_COST_ENGINEERING.md`.

## 2. Cost attribution

Separate at minimum:

- vision/parse;
- classification;
- primary solve;
- secondary solve;
- arbitration;
- explanation;
- tutor;
- practice generation;
- mistake classification.

Aggregate by model/provider, capability, skill/difficulty, entitlement tier and cohort.

Sprint 4.4 records recognition-stage usage, estimated micro-USD cost, pricing version, provider latency, and total job latency with the raw RecognitionEvidence record.

Sprint 4.5 records parser-stage `PROBLEM_NORMALIZE` usage, estimated micro-USD cost, pricing version, provider latency, and total job latency with parse jobs/revisions. It still does not calculate cost per verified solution because canonical parsing, solving, and verification have not run yet.

## 3. Primary cost controls

- eliminate redundant calls;
- deterministic verification/algorithms before AI judges;
- compact image/context payloads;
- cheap approved parser/classifier routes;
- conditional secondary solving;
- cache immutable/reusable artifacts safely;
- quota/rate-limit abuse;
- strong model only for hard tail;
- future proprietary replacement only after evidence gates.

## 4. Latency budgets

Define stage and end-to-end budgets. Track p50/p95/p99. UI may show semantic progress but must never display final `VERIFIED` before verification completes.

## 5. Timeouts and retries

Each provider route defines connection/response/total timeouts and retryable error classes. Retry budgets are included in end-to-end deadline and cost budget.

The initial `VISION_PARSE` and `PROBLEM_NORMALIZE` routes have explicit provider timeout, max-attempt, max-response-size, and max-cost configuration. Local/test may use deterministic fixture routes; production must not route either capability to a fake provider when enabled.

Sprint 4.10 evaluation reports record Phase 4 ingestion latency and cost separately from provider-independent dataset validation. The deterministic `LOCAL_FIXTURE_REGRESSION` baseline has zero provider cost and must not be used as production provider economics. Connected evaluation requires explicit `AI_EVAL_MAX_TOTAL_COST_MICROS`, `AI_EVAL_MAX_CASES`, and `AI_EVAL_TIMEOUT` controls; budget exhaustion is `BLOCKED_BUDGET`, never partial PASS.

## 6. Reliability fallback

1. retry bounded transient failure;
2. approved fallback route;
3. conditional stronger route where quality requires it;
4. degrade nonessential explanation/tutor enrichment;
5. return explicit recoverable or `UNVERIFIED` state.

If verifier is unavailable, do not fabricate verification.

## 7. Provider health

Track:

- availability;
- 429/5xx;
- schema invalid;
- p95 latency;
- quota headroom;
- evaluation-quality drift;
- cost drift.

Sprint 4.10 quality drift response is: freeze promotion, inspect the failing slice, compare route/prompt/schema/provider provenance, review prior report and approved baseline, rollback the candidate route/config where applicable, and add a permanent regression fixture for novel trusted failures.

## 8. Secondary-solver SLO

Track invocation rate and marginal quality gain. Unexpected invocation spikes are both cost and quality alerts.

## 9. Quota accounting

Reserve/consume/refund usage atomically. Failed operations that produce no user value follow documented refund semantics.

## 10. Spend anomaly response

Cost pressure never permits dishonest output. Investigate abuse/retry/context regressions first; use documented feature/usage limits rather than silent quality collapse.

## 11. Future self-hosted route

A self-hosted model adds capacity SLOs (GPU utilization, queue depth, model availability, OOM, warmup). It must preserve external fallback and pass TCO gates.
