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
