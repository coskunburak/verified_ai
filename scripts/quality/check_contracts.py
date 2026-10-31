#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]

REQUIRED_CONTRACTS = {
    "packages/schemas/verification-decision.schema.json": ["verification-decision-v1", "PARTIALLY_VERIFIED", "EXPLANATION_STEPS_UNCHECKED"],
    "packages/schemas/numeric-diagnostic.schema.json": ["numeric-diagnostic-v1", "equivalence-binary64-v1", "scopeId"],
    "packages/schemas/numeric-diagnostic-result.schema.json": ["numeric-diagnostic-result-v1", "SAMPLES_AGREE", "oneOf"],
    "packages/schemas/problem-parse-v2.schema.json": ["problem-parse-v2", "calculus", "approach", "integralScope"],
    "packages/schemas/canonical-problem-v2.schema.json": ["canonical-problem-v2", "acceptedParse", "sourceParse"],
    "packages/schemas/solution-candidate-v2.schema.json": ["solution-candidate-v2", "canonicalRevision", "FINITE_LIMIT"],
    "packages/schemas/calculus-execution.schema.json": [
        "calculus-execution-v1", "calculus-task-v1", "approach", "integralScope",
    ],
    "packages/schemas/calculus-execution-result.schema.json": [
        "calculus-execution-result-v1", "bounded-calculus-v1", "NUMERICAL_DIAGNOSTIC", "oneOf",
    ],
    "packages/schemas/verification-execution.schema.json": [
        "verification-execution-v1", "original", "candidate", "restrictions",
    ],
    "packages/schemas/verification-execution-result.schema.json": [
        "verification-execution-result-v1", "EXECUTION_FAILED", "restrictionIds", "proof",
    ],
    "packages/schemas/solution-candidate.schema.json": [
        "solution-candidate-v1", "canonicalProblemId", "derivedRestrictionIds", "finalAnswer",
    ],
    "packages/contracts/openapi/public-api.yaml": [
        "/api/v1/platform/health",
        "/api/v1/problem-sessions/{sessionId}/solves",
        "PrimarySolveResponse",
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
    "packages/schemas/ingestion-evaluation-case.schema.json": [
        "ingestion-evaluation-case-v1",
        "expectedRecognitionOutcome",
        "trainingEligibility",
    ],
    "packages/schemas/ingestion-evaluation-report.schema.json": [
        "ingestion-evaluation-report-v1",
        "gateDecision",
        "reportSha256",
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
