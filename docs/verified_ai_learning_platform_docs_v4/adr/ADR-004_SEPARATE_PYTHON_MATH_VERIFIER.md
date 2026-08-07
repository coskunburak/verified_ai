# ADR-004: Separate Python Math Verifier

## Status
Accepted.

## Context
Main backend is Java/Spring, while symbolic mathematics has a strong Python/SymPy ecosystem.

## Decision
Keep business backend in Spring Boot and run a small internal Python/FastAPI verification service.

## Boundaries
Verifier:
- receives mathematical representations,
- performs deterministic checks,
- returns structured evidence,
- owns no canonical student state,
- is not public,
- is never called directly by iOS.

## Consequences
A second runtime must be operated, but mathematical tooling is cleaner and verification CPU can scale independently.
