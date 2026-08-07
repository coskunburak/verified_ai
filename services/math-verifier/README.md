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

