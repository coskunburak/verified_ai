# Internal Math Verifier

FastAPI/SymPy service for deterministic mathematical checks. It is internal-only, owns no canonical student state, and must never be called directly by the iOS client.

## Local Commands

```sh
python3 -m venv .venv
. .venv/bin/activate
pip install -e ".[dev]"
python -m pytest
uvicorn app.main:app --reload --port 8090
```

## Internal Auth

Requests to internal verification endpoints require `X-Internal-Token`. Local development uses the non-secret placeholder from `.env.example`.


## Sprint 5.8 execution (owner tests pending)

The authenticated /internal/v1/verify/execute route uses verification-execution-v1 and returns method evidence only.
Enable only after owner acceptance with MATH_VERIFIER_EXECUTION_ENABLED=true; default is disabled.
Production requires Linux and an injected internal secret of at least 32 characters; the local default is rejected.
The string equivalence route is available only in local/test environments.

Workers use spawn, immediate finite admission (two slots, no queue), 3 s wall deadline, 2 s CPU and Linux 512 MiB RLIMIT_AS.
The supervisor terminates/kills/reaps over-budget workers. Memory enforcement is unavailable on macOS; Linux owner/CI validation is mandatory.
Requests are at most 64 KiB, responses 16 KiB. Rational intermediates use at most 1024 bits; polynomials degree <=12 and <=128 terms.
Completeness is degree <=2 with rational roots and singleton candidate syntax. No general solving or sampling.

The Dockerfile owner-tests target contains dev dependencies; the default production target excludes them.
jsonschema is a test-only MIT-licensed dependency for independent published-contract validation; remove it if that validation moves to centralized contract tooling.
No new production dependency, AI call or per-request provider charge is introduced.
Typical work is one spawn and bounded exact operations per selected method; at most five sequential method calls per run.
At the deadline, a five-method run can consume roughly 15 seconds of verifier elapsed budget plus bounded Java transport/cleanup overhead.
Capacity and latency require owner measurement before rollout. Container OOM can interrupt the service and remains a non-proof transport failure.

Rollback disables both Java execution activation and this route's feature flag, retaining V021 immutable history.
New attempts after a failed published run use a fresh request key; same-key retries preserve the original outcome.

## Sprint 5.9 internal calculus checkpoint

`/internal/v1/verify/calculus` is separately versioned, token authenticated and disabled
by default. `MATH_VERIFIER_CALCULUS_ENABLED=true` is accepted only with APP_ENV local/test;
production activation fails until source-backed canonical calculus integration exists.
There is no new production Java executor bean or persistence path. Existing v1 remains unchanged.

Supported proof scope: one real variable on an explicit nonempty open connected interval;
bounded rational expressions; SIN/COS/EXP chain rules; LOG/SQRT with independently
established strict argument positivity. Formal function atoms support sufficient coefficient
identity proofs, not general transcendental equivalence. Antiderivatives prove representatives
only. Finite rational-polynomial limits use explicit direction and orders of vanishing,
including removable holes. Poles remain non-proof NONFINITE_LIMIT outcomes.

Limits outside the exact subset may return a `calculus-binary64-v1` diagnostic: eight
points per required side, offsets of min(1, half distance to interval boundary) / 2^k for
k=2..9, binary64, absolute/relative tolerance 1e-8. Every original domain obligation and
restriction is checked at each admitted point; unsupported obligations skip that point.
Nonfinite values, absolute values above 1e100 or nonzero below 1e-100, detected underflow,
near-zero denominators and float-coincident approach points are rejected. Counts report
attempted/evaluated/agreeing samples only; agreement and disagreement never prove anything.

Same worker/AST budgets as v1; at most 16 function atoms and degree 12/128-term formal
polynomials. Exact polynomial Sturm root counting is supervised. The inspected local
SymPy metadata is 1.14.0; the existing >=1.13,<2 dependency is unchanged. Owner tests must
establish compatibility for the installed version; no dependencies or validation were run
by the implementation agent. Zero additional paid inference and no cache or training.

Owner commands and scope blockers are in
`docs/verified_ai_learning_platform_docs_v4/sprints/phase05_ai_solving_and_verification_core/SPRINT_5.9_EXECUTION_REPORT.md`
from the repository root. Local owner A–H gates passed: Linux verifier 186 passing,
backend 566 passing with two paid-model skips, BUILD SUCCESS, lint/docs/contracts/secrets
and immutability. Final evidence-documentation refresh awaits docs-check.
PARTIAL / source integration BLOCKED / ROLLOUT_DISABLED / HOSTED_CI_NOT_RUN.

Java verifier clients explicitly select HTTP/1.1. The Uvicorn service does not support
cleartext HTTP/2 (h2c) upgrades; upgrade requests can skip POST body parsing with httptools.
The owner cross-runtime harness uses the same HTTP/1.1 policy and classifies non-JSON
transport failures without logging request/response mathematical payloads.

## Sprint 5.9 source integration closure

The API now has a versioned source-to-evidence path using the existing calculus worker.
The internal authenticated calculus-readiness route reports the actual Python flag and
fixed algorithm/request/result versions. Java has independent disabled calculus/owner
acceptance flags and requires compatible readiness before availability. Production guards
remain unchanged. Worker mathematics and resource budgets are unchanged.

Closure tests are authored but NOT_RUN_BY_AGENT / OWNER_VALIDATION_PENDING / CI_PENDING.
See docs/verified_ai_learning_platform_docs_v4/sprints/phase05_ai_solving_and_verification_core/SPRINT_5.9_PRODUCTION_INTEGRATION_CLOSURE_EXECUTION_REPORT.md
for the single complete owner block. The earlier isolated acceptance does not establish
passing closure evidence. Production rollout remains disabled.
