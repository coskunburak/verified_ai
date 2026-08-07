# Technical Debt and Maintenance Register

## Purpose

This register captures known non-blocking technical debt so it is not lost in chat history or execution reports. It is not a substitute for issue tracking; it is the canonical documentation mirror for debt that affects architecture, dependencies, release readiness, or production operations.

## Priority

| Priority | Meaning |
|---|---|
| P0 | Blocks release or risks user/security/data integrity. |
| P1 | Must be fixed before the affected phase exits. |
| P2 | Should be fixed before Production V1 launch. |
| P3 | Routine maintenance; track and batch with dependency work. |

## Register

| Debt ID | Priority | Area | Finding | Impact | Owner | Remediation trigger | Target phase | Status |
|---|---|---|---|---|---|---|---|---|
| TD-DEP-001 | P3 | Math verifier dependencies | Verifier tests pass with a Starlette/httpx deprecation warning from the dependency stack. | No Phase 2 blocker; future dependency update may break test client behavior if ignored. | Platform / verifier owner | Next dependency maintenance window or before verifier API expands in Phase 5. | Phase 5 or earlier maintenance | Open |
| TD-DEV-001 | P3 | Local developer tooling | GitHub CLI is not installed on the local machine used for Phase 2 validation. | Does not block local implementation or GitHub Actions, but limits local PR/check inspection. | Developer environment owner | Before first GitHub PR triage workflow that requires local `gh` commands. | Phase 3 | Open |
| TD-DEV-002 | P3 | Local infrastructure ports | Host port `5432` was occupied during Docker Compose validation; alternate `POSTGRES_PORT=55432` worked. | Does not affect internal Docker networking; can confuse new local setup if undocumented. | Platform owner | If repeated by another developer, standardize alternate local port profile. | Phase 3 or onboarding docs pass | Open |

## Debt Rules

- A P0/P1 debt item cannot be carried across a phase boundary without an explicit exception in that phase's execution report.
- A debt item that affects a V1-required capability must reference the relevant capability ID from `roadmap/68_PRODUCTION_V1_CAPABILITY_COVERAGE_MATRIX.md` before implementation begins.
- Dependency debt must include upgrade, pin, replacement, or removal strategy before Production V1 launch if it affects runtime code.
- Debt that becomes a security/privacy issue moves to security handling immediately.

