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
