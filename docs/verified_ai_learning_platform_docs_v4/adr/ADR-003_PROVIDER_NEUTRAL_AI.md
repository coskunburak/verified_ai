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

## Sprint 4.4 sequencing note

Sprint 4.4 requires vision/OCR before the full Sprint 5.1 AI gateway. The accepted sequencing is to pull forward only the minimum provider-neutral `VISION_PARSE` capability boundary needed for recognition evidence. Problem code may call the internal AI capability port, but provider SDKs/adapters remain in the `ai` module. Solver, tutor, mistake-classification, arbitration, route optimization, and full AI usage-ledger completion remain Sprint 5.1+ scope.
