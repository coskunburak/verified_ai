# Model Registry, Release, and Rollback Governance

## 1. Scope

This document covers both external model-route configurations and future proprietary model artifacts. A model change is a production dependency release.

## 2. Registry objects

### External route release

- route version,
- capability,
- provider/model identifier,
- prompt/schema versions,
- timeout/retry policy,
- evaluation report,
- cost benchmark,
- rollout status.

### Proprietary model release

Additionally:

- artifact URI/checksum,
- base model/license,
- training dataset IDs,
- training code commit,
- training configuration,
- quantization/runtime,
- model card,
- resource requirements.

## 3. Lifecycle states

- `EXPERIMENTAL`
- `OFFLINE_APPROVED`
- `SHADOW`
- `CANARY`
- `PRODUCTION`
- `DEPRECATED`
- `ROLLED_BACK`
- `RETIRED`

No route may jump directly from experiment to full production.

## 4. Promotion evidence

Each promotion requires stored evidence for:

- quality metrics,
- verifier compatibility,
- latency,
- cost,
- failure distribution,
- privacy/security approval where relevant.

## 5. Rollback

Rollback must be configuration-driven whenever possible and must not require an App Store release.

Maintain at least one previously approved route/config for critical capabilities.

## 6. Reproducibility

A maintainer must be able to answer:

- what model produced this result?
- under which prompt/schema?
- with which route policy?
- which evaluation approved it?
- which dataset trained it, if proprietary?

## 7. Kill switches

Support capability/model/provider kill switches with audited changes and bounded blast radius.

## 8. Change management

Provider aliases such as “latest” are not sufficient for release provenance when an immutable version/snapshot is available. Capture provider version metadata and evaluate provider migrations explicitly.

## Sprint 5.12 immutable solver release evidence

The existing PromptReleaseApproval now optionally pins solverAuthorizationReference and
solverAuthorizationSha256. STAGING/PRODUCTION SOLVE prompts require content validation;
the legacy validator constructor defaults to denial. Local/offline solver and ingestion
approval behavior remains compatible. SolverModelEligibility still independently blocks
production solver activation. No production artifact or route is enabled by this sprint.

The new validator reads only bounded classpath artifacts (512 KiB each), checks exact
byte SHA-256, strict JSON fields, duplicate keys and safe repository-style references.
The authorization schema solver-release-authorization-v1 requires scope
REAL_PROVIDER_AND_PROTECTED_HOLDOUT, outcome PASS, approvalReference, approvedBy,
approvedAt, and report/baseline reference+sha256 pairs. Identity must match the existing
registry approval. Candidate and baseline use solver-production-evidence-v1 and attest
independentLabels, representativeBenchmark, approvedThresholds, sufficientObservations,
realProvider, protectedHoldout, cost and latency; all are mandatory true, with zero
criticalFailures. Dataset/workload/schema/pricing/currency must be comparable.

Candidate artifacts bind exact prompt/schema hashes and the full AiRoutePlan record,
including fallbacks, timeouts, budgets, privacy, quality gate and stages. Models require
an immutable name@sha256 identity, not a mutable alias. These evidence attestations must
be prepared from separately reviewed restricted evidence, packaged deliberately into
the deployment classpath, and bound by a human-reviewed registry digest. No local fixture
runner emits such an authorization. Hash integrity is not independent evidence review;
reviewed registry changes and protected deployment controls are the trust anchor.

The offline Python comparator separately supports create-only reviewed LOCAL fixture
baselines; it never mints production authorization. Baselines cannot be rewritten by CI.
Rollback revalidates the exact previously approved route and artifacts. Model lifecycle
states remain distinct from prompt release stages; no automatic enum mapping is added.

Owner actions outside source control: require the AI Evaluation deterministic solver
and ingestion checks plus backend/verifier checks in branch protection; protect registry,
evidence and policy changes with required review; restrict the ai-evaluation environment
to trusted main with required reviewers; provision restricted holdout access and approved
provider, pricing, budget, thresholds and staged rollout evidence before enabling any
future protected solver workflow. None of these hosted settings is claimed as applied.

Solver release route timeout serialization is canonical ISO-8601 Duration text from
Duration.toString(), preserving nanoseconds (for example PT1.000000001S). An explicit
serializer makes this independent of optional Jackson time modules; numeric timeout
representations or different durations cannot substitute for the bound route.
