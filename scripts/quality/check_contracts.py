#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]

REQUIRED_CONTRACTS = {
    "packages/contracts/openapi/public-api.yaml": [
        "/api/v1/platform/health",
        "/api/v1/platform/readiness",
        "ProblemDetails",
    ],
    "packages/contracts/openapi/internal-math-verifier.yaml": [
        "/internal/v1/verify/equivalence",
        "X-Internal-Token",
        "EquivalenceVerificationResponse",
    ],
    "packages/schemas/domain/problem-details.schema.json": [
        "ProblemDetails",
        "traceId",
        "details",
    ],
}


def main() -> int:
    failures: list[str] = []
    for relative_path, required_terms in REQUIRED_CONTRACTS.items():
        path = ROOT / relative_path
        if not path.exists():
            failures.append(f"missing contract: {relative_path}")
            continue
        text = path.read_text(encoding="utf-8")
        for term in required_terms:
            if term not in text:
                failures.append(f"{relative_path} missing required term: {term}")

    if failures:
        print("contract check failed:")
        for failure in failures:
            print(f"- {failure}")
        return 1

    print("contract check passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

