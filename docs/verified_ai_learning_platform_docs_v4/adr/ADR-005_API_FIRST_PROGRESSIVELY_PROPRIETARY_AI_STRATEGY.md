# ADR-005 — API-First, Progressively Proprietary AI Strategy

- **Status:** Accepted
- **Decision:** Use provider-neutral external foundation-model APIs for Production V1 while building proprietary verification and learning intelligence. Train proprietary small models only after measurable task, data, evaluation, and economics gates are met.

## Context

Training a frontier model is economically and technically unjustified for the initial product. External APIs provide superior baseline capabilities and allow product validation before large ML investment. At the same time, provider lock-in and uncontrolled inference cost are risks.

## Consequences

- All model access is behind internal capability ports/router.
- AI usage/cost provenance is mandatory from the beginning.
- Secondary solving is conditional rather than universal.
- Deterministic algorithms are preferred when sufficient.
- The architecture leaves room for proprietary model adapters later.
- “Own model” is not a launch requirement.

## Revisit when

A bounded task has material recurring cost/latency/strategic importance and sufficient eligible data plus evaluation infrastructure exists.
