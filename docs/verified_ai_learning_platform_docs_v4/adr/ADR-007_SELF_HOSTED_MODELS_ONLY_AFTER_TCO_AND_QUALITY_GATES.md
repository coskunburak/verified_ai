# ADR-007 — Self-Hosted Models Only After TCO and Quality Gates

- **Status:** Accepted
- **Decision:** No self-hosted production inference is introduced until a candidate passes quality, data, capacity, total-cost, operational, shadow, and rollback gates.

## Rationale

GPU hourly cost alone is not comparable to API pricing. Self-hosting adds idle capacity, autoscaling, observability, deployment, incident, security, and model-maintenance costs.

## Consequences

- External APIs remain the default and fallback.
- Self-hosted services are conditional architecture, not mandatory repository complexity in V1.
- A model replacement decision record is required before rollout.
