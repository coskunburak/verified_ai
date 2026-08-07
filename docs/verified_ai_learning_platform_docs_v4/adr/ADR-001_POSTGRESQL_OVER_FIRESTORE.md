# ADR-001: PostgreSQL Over Firestore

## Status
Accepted.

## Context
The product has strongly relational and transactional domains: attempts, skills, mistakes, mastery, plans, exams, entitlements and audit.

## Decision
PostgreSQL is the canonical application database. Firebase services may be used selectively for mobile tooling, but Firestore is not the source of truth.

## Consequences

Benefits:
- relational integrity,
- powerful queries,
- transactions,
- analytics/reporting,
- mature migration discipline.

Costs:
- backend/database operations must be managed,
- offline sync is application-designed rather than automatically inherited from Firestore.

## Rejected alternative
Firestore as main data model would push relational learning logic into documents and application-level joins/consistency rules.
