# ADR-003: Provider-Neutral AI

## Status
Accepted.

## Decision
Domain/application code depends on internal AI capability ports, not OpenAI/Gemini SDK objects.

## Reasons
- vendor cost can change,
- model quality varies by task,
- outage resilience,
- A/B evaluation,
- future providers/models.

## Consequences
- adapter mapping and internal schemas are required,
- provider switching/routing becomes feasible,
- tests can use fake adapters.
