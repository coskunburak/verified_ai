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

Sprint 3.3/3.4 foundation metrics:
- `profile.load.success.total`
- `profile.save.success.total`
- `profile.save.failure.total`
- `onboarding.completed.total`
- `entitlement.resolution.total`
- `entitlement.access.allowed.total`
- `entitlement.access.denied.total`

Sprint 3.5/3.6 billing metrics:
- `billing.apple.configuration.requests.total`
- `billing.apple.purchase.submissions.total`
- `billing.apple.purchase.failures.total`
- `billing.apple.notifications.received.total`
- `billing.apple.notifications.duplicates.total`
- `billing.apple.notifications.processed.total`
- `billing.apple.notifications.failed.total`
- `billing.apple.reconciliation.requests.total`
- `billing.apple.reconciliation.failures.total`

Sprint 3.7/3.8 privacy and abuse metrics:
- `privacy.export.request.total`
- `privacy.export.download.total`
- `privacy.deletion.request.total`
- `privacy.deletion.success.total`
- `security.rate_limit.denied.total`
- `security.rate_limit.degraded_open.total`
- `security.request.rejected.total`

Sprint 4.2 problem asset upload metrics:
- `problem.asset.reservation.success.total`
- `problem.asset.reservation.failure.total`
- `problem.asset.upload.complete.success.total`
- `problem.asset.upload.complete.failure.total`
- `problem.asset.checksum_mismatch.total`
- `problem.asset.size_mismatch.total`
- `problem.asset.pending_expired.total`
- `problem.asset.presign.latency`
- `problem.asset.completion.verification.latency`

Sprint 4.4 recognition metrics:
- `ai.vision.recognition.started.total`
- `ai.vision.recognition.success.total`
- `ai.vision.recognition.failure.total`
- `ai.vision.recognition.timeout.total`
- `ai.vision.recognition.schema_invalid.total`
- `ai.vision.recognition.fallback.total`
- `ai.vision.recognition.provider.latency`
- `ai.vision.recognition.total.latency`
- `ai.vision.recognition.estimated_cost_micros`

Sprint 4.5 parser metrics:
- `ai.problem.parse.started.total`
- `ai.problem.parse.success.total`
- `ai.problem.parse.failure.total`
- `ai.problem.parse.schema_invalid.total`
- `ai.problem.parse.semantic_invalid.total`
- `ai.problem.parse.unsupported.total`
- `ai.problem.parse.review_required.total`
- `ai.problem.parse.fallback.total`
- `ai.problem.parse.provider.latency`
- `ai.problem.parse.total.latency`
- `ai.problem.parse.estimated_cost_micros`

Metric labels must remain low-cardinality and must not contain raw profile answers, user IDs, access tokens, provider secrets, filenames, local paths, object keys, OCR text, recognized text, parser expressions, constraints, raw provider/parser output, or raw problem content.

## SLOs

Define numeric SLOs from measured baseline for API availability, problem submission, solve completion and billing sync. AI correctness uses quality gates rather than availability SLO alone.

## Alerts

Actionable alerts:
- elevated solve failures,
- DB saturation,
- billing backlog,
- App Store notification verification failures,
- App Store notification processing failures,
- elevated purchase verification failures,
- App Store Server API reconciliation failures,
- elevated account deletion/export failures,
- rate-limit denied spikes or Redis limiter degraded-open spikes,
- upload storage unavailable, checksum/size mismatch spikes, or pending-upload expiry spikes,
- recognition timeout/schema-invalid/fallback spikes or estimated recognition cost spikes,
- parser schema-invalid/semantic-invalid/unsupported/review-required/fallback spikes or estimated parser cost spikes,
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
