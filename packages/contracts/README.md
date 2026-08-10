# Contracts

Versioned API contracts live here. Runtime modules may generate adapters from these files, but generated code must not become the source of truth.

- `openapi/public-api.yaml` documents client-facing API routes. It exposes canonical problem display metadata only, not internal verifier AST payloads.
- `openapi/internal-math-verifier.yaml` documents internal verifier routes. Sprint 4.6 adds typed `verifier-input-v1` validation; raw expression equivalence remains a guarded compatibility endpoint.
