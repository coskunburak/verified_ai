# Small-Model Fine-Tuning and Self-Hosting Readiness

## 1. Purpose

This document defines when the platform should consider a proprietary model and how to avoid replacing a cheap, reliable API with an expensive internal platform problem.

## 2. Default answer

Before readiness gates are met, the answer to “should we train/self-host this?” is **no**.

## 3. Candidate order

Prefer migration in this order:

1. deterministic rule/algorithm;
2. cached/reused result;
3. lightweight classifier/ranker;
4. fine-tuned compact model;
5. self-hosted specialized solver;
6. never: frontier foundation model from scratch.

## 4. Suitable first proprietary tasks

- skill/topic classification;
- mistake classification;
- difficulty prediction;
- recommendation ranking;
- mastery/risk prediction.

These tasks have bounded outputs and objective evaluation metrics.

## 5. Poor first proprietary tasks

- open-ended tutoring;
- unrestricted multimodal reasoning;
- every-subject solver;
- general chat;
- broad safety-sensitive content generation.

## 6. Fine-tuning readiness gates

All must be true or explicitly waived in an ADR:

- stable task contract;
- sufficient high-quality eligible data;
- protected test set;
- existing baseline model and metrics;
- clear cost/latency or product-quality reason;
- reproducible training pipeline;
- experiment tracking;
- model artifact/version registry;
- security/privacy review;
- online rollback/fallback route.

## 7. Self-hosting readiness gates

In addition to fine-tuning gates:

- realistic traffic forecast;
- GPU utilization model;
- peak/concurrency model;
- inference latency benchmark;
- autoscaling/cold-start analysis;
- model memory/quantization profile;
- observability and on-call ownership;
- capacity fallback to external API;
- TCO calculation including engineering/operations.

## 8. Total cost of ownership

Compare:

### External API TCO
- token/image cost,
- network latency,
- provider minimums if any,
- retry/failure overhead.

### Self-hosted TCO
- accelerator hours,
- idle capacity,
- storage/model registry,
- autoscaling inefficiency,
- observability,
- deployment engineering,
- incident/on-call time,
- security patching,
- model upgrade work,
- data/training compute.

Do not compare “API token price” to “GPU hourly price” alone.

## 9. Shadow evaluation

A proprietary candidate initially receives copied production requests without affecting user results, using only eligible/privacy-safe inputs.

Compare:

- exact/semantic accuracy,
- verifier pass rate,
- correction rate,
- latency,
- cost,
- failure modes by skill/difficulty/language.

## 10. Canary policy

Promotion sequence:

- internal traffic,
- shadow,
- 1% eligible traffic,
- 5%,
- 25%,
- 50%,
- 100% only if evidence supports it.

Every stage has automatic/manual rollback thresholds.

## 11. External API remains fallback

A proprietary route should normally preserve external fallback for:

- low confidence,
- unsupported problem classes,
- model outage,
- hard-tail problems,
- sampled quality checks.

## 12. Model artifact requirements

Every model artifact must have:

- immutable ID/version,
- base model/license,
- dataset versions,
- training code revision,
- hyperparameters,
- evaluation report,
- safety/robustness notes,
- quantization format,
- serving requirements,
- release status.

## 13. Stop conditions

Terminate an experiment if:

- quality cannot reach baseline;
- cost advantage disappears at realistic utilization;
- operational complexity exceeds savings;
- required data is not eligible or sufficiently clean;
- external model improvements remove the business case.

## 14. Key principle

Owning a model is not a product milestone by itself. The milestone is a measurable improvement in learner value, reliability, latency, strategic independence, or sustainable unit economics.
