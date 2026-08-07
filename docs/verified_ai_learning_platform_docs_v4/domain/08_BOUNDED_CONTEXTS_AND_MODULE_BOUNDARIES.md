# Bounded Contexts and Module Boundaries

## Architecture style

Backend begins as a domain-driven modular monolith in Spring Boot. One deployable does not mean one undifferentiated codebase.

## Modules

### identity
Owns user identities, sessions and refresh token lifecycle.

### profile
Owns learner preferences/profile metadata.

### curriculum
Owns subjects, topics, skills, prerequisite graph and curricula.

### problem
Owns problem sessions, asset metadata and parse lifecycle.

### solving
Owns solver runs and reference solution generation.

### verification
Owns verification policy, runs and signals.

### tutoring
Owns tutor sessions, hint policy and tutor interaction state.

### attempt
Owns student attempts and evaluation orchestration.

### mistake
Owns mistake taxonomy and diagnoses.

### mastery
Owns per-skill mastery state/history.

### studyplan
Owns adaptive plan and study session planning.

### exam
Owns exam definitions, target exams, readiness and mock exam orchestration.

### billing
Owns App Store sync and entitlements.

### notification
Owns notification intent/scheduling.

### ai
Owns provider abstraction, routing, prompt registry access and AI usage ledger.

### admin
Owns secured operational/read tools.

## Dependency rules

Preferred:
feature module → domain port → infrastructure adapter.

Forbidden:
- mastery importing OpenAI/Gemini adapter,
- problem importing billing persistence internals,
- iOS calling verification service directly,
- admin mutating mastery rows directly.

## Cross-module communication

Use direct application calls for intentionally synchronous operations and domain/application events for post-commit reactions.

Example:
AttemptEvaluated → Mistake + Mastery + Analytics consumers.

## Public module API

Expose narrow application API packages. Keep internals inaccessible where practical. Use Spring Modulith verification or architecture tests to detect leakage.

## Future extraction candidates

Potential future services:
- math verifier already separate,
- notifications,
- heavy AI orchestration,
- reporting.

Do not extract a service simply because a module exists.

## Extraction criteria

Extract only when there is real need:
- fundamentally different runtime/scaling,
- independent deployment cadence,
- resource isolation,
- clean data ownership,
- team ownership/operational justification.

<!-- HYBRID_AI_STRATEGY_V3:START -->
## AI/ModelOps boundary clarification

The `ai` module owns provider-neutral capability invocation, route policy and inference provenance. Future training pipelines/model artifacts belong to a dedicated internal ModelOps/ML tooling boundary and must not make product modules dependent on ML frameworks.

`verification`, `mastery`, `studyplan`, `exam`, and `billing` remain authoritative for their semantics regardless of model route.
<!-- HYBRID_AI_STRATEGY_V3:END -->
