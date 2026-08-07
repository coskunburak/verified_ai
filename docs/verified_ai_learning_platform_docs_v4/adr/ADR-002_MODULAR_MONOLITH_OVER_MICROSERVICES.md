# ADR-002: Modular Monolith Over Microservices

## Status
Accepted.

## Context
Initial team is small and product/domain is evolving rapidly.

## Decision
Use one Spring Boot deployable with strongly enforced domain modules. Keep the Python math verifier separate only because its runtime/library needs are distinct.

## Benefits
- simpler deployment,
- local transactions,
- easier debugging,
- lower operational overhead,
- future service extraction remains possible.

## Tradeoff
Strong module discipline and architecture tests are required to prevent a big-ball-of-mud monolith.

## Extraction trigger
Extract only when runtime scaling, deployment cadence, data ownership or team ownership creates measurable need.
