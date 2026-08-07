# Repository File Placement, Ownership, and Dependency Matrix

## Purpose

This document is the operational companion to the exhaustive hierarchy. It turns the repository tree into deterministic placement rules for humans and coding agents. Use it when a proposed file could plausibly fit in more than one location.

## Placement matrix

| Artifact / change | Canonical path | Owner | Required tests/evidence | Forbidden placement |
|---|---|---|---|---|
| SwiftUI screen | `apps/ios/.../Features/<Feature>/Presentation` | iOS feature | ViewModel + UI/accessibility for critical flow | `Core`, unrelated feature |
| iOS use case | `Features/<Feature>/Domain` | iOS feature | Unit | View, networking concrete layer |
| iOS API DTO | `Features/<Feature>/Data` | iOS feature | Mapping/contract | SharedDomain |
| Design token/component | `Core/DesignSystem` | iOS platform | Snapshot/accessibility where relevant | Individual feature when globally reused |
| Backend REST controller | `<module>/api` | Bounded context | API/authorization/contract | global `controllers` |
| Backend use-case orchestration | `<module>/application` | Bounded context | application/integration | controller, JPA entity |
| Domain invariant/value object | `<module>/domain` | Bounded context | domain tests | prompt, controller, database trigger as sole authority |
| JPA entity/repository adapter | `<module>/infrastructure/persistence` | Bounded context | Testcontainers integration | domain, another module |
| External provider adapter | owning module `infrastructure/external` | Bounded context/platform | contract/resilience | domain/application concrete provider dependency |
| AI provider adapter | `backend/ai/infrastructure/<provider>` | AI platform | mocked/provider contract + eval | solving/mastery domain |
| AI prompt | `/prompts/<capability>/<version>` | AI platform | golden eval | Java/Swift inline string except tiny noncanonical templates |
| AI JSON schema | `/packages/schemas/ai/...` | AI platform/contracts | schema fixtures | prompt prose only |
| Deterministic math rule | `services/math-verifier/app/verifiers` | Verification | unit/property/security benchmark | LLM prompt, iOS |
| Golden AI example | `evaluations/golden-datasets` | AI quality | immutability/provenance | production DB seed |
| Protected test example | `evaluations/protected-holdouts` | AI quality | restricted access | training dataset |
| Training-eligible dataset manifest | `ml/datasets` (Phase 13 only) | ML governance | lineage/consent/leakage | raw production export |
| Flyway migration | `services/api/src/main/resources/db/migration/<module>` | Data + module | migration integration | ad-hoc SQL in scripts |
| Object-storage lifecycle | `infra/terraform` / data docs | Platform/data | infra plan + retention review | application code only |
| Product analytics event definition | `operations/48...` + implementation owner | Product analytics | schema/event tests | random event strings |
| Feature flag definition | `operations/49...` + backend/client registry | Platform/product | default/kill-switch test | hidden boolean literals |
| ADR | `adr/` | Architecture | review/acceptance | sprint-only decision |
| Runbook | `operations/` | Operations | tabletop/validation where critical | code comments only |
| Sprint plan | `sprints/phaseXX...` | Delivery | exit-gate evidence | canonical invariant override |

## Ambiguity resolution

If two locations remain plausible, choose the path that owns the business invariant, not the path that happens to call the code. If ownership is genuinely unclear, treat that as an architecture gap and update bounded-context documentation before implementation.

## Naming invariants

- Prefer domain language from `domain/06_UBIQUITOUS_LANGUAGE_AND_GLOSSARY.md`.
- A type name must reveal purpose; avoid `Manager`, `Helper`, `Utils`, `CommonService`, `DataHandler`, or `Processor` unless the domain explicitly uses that term.
- `Service` is acceptable only when a narrower command/query/use-case/policy name would be misleading.
- DTO/entity/model names are not interchangeable: transport DTOs, persistence entities, and domain models remain separate.
- Boolean feature flags use positive semantic names and documented defaults.
- SQL migration names describe the domain mutation, not ticket numbers alone.

## Ownership escalation

A proposed dependency that violates the matrix requires one of: a declared module API, a domain event, a new port/adapter, a shared immutable contract, or an ADR-backed boundary change. Copying an infrastructure type across modules is never the shortcut.
