# Definition of Done and Acceptance

A feature is not done because its happy-path UI works.

## Product
- user value met,
- error/recovery defined,
- entitlement behavior defined,
- analytics events defined,
- accessibility reviewed.

## Domain
- vocabulary consistent,
- invariants tested,
- ownership/events documented.

## Backend
- API implemented,
- authorization,
- idempotency where required,
- migrations,
- observability,
- tests.

## iOS
- loading/success/error/offline states,
- localization,
- accessibility,
- previews/fixtures,
- relevant tests.

## AI
For AI features:
- strict schema,
- prompt version,
- timeout/fallback,
- usage accounting,
- golden-evaluation impact,
- no unjustified certainty.

## Security/privacy
- new threat/data flow reviewed,
- retention classification,
- no secret exposure.

## Operations
- dashboards/alerts for critical feature,
- runbook impact,
- feature flag for risky rollout.

## Example: Verified Solve acceptance

Done only when:
- parse correction works,
- async pipeline is durable,
- verifier assigns correct status,
- evidence is visible,
- UNVERIFIED degradation works,
- false VERIFIED regression gate passes,
- cost/latency are measured,
- premium guard is server-enforced if configured.

<!-- HYBRID_AI_STRATEGY_V3:START -->
## Additional DoD for AI-affecting changes

A change that affects AI behavior is not Done until:

- route/model/prompt/schema provenance is recorded;
- quality evaluation passes;
- cost/latency impact is quantified;
- fallback/rollback exists;
- privacy/training eligibility impact is reviewed;
- conditional secondary-solver behavior remains intentional.
<!-- HYBRID_AI_STRATEGY_V3:END -->
