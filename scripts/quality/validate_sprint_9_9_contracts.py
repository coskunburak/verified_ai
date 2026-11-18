#!/usr/bin/env python3
"""Owner-run Sprint 9.9 contract gate (INV-EXAM-005). No application or database access."""
from __future__ import annotations

import copy
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
for candidate in sorted((ROOT / "services/math-verifier/.venv/lib").glob("python*/site-packages")) + sorted((ROOT / ".venv/lib").glob("python*/site-packages")):
    if candidate.is_dir() and str(candidate) not in sys.path:
        sys.path.insert(0, str(candidate))

import yaml
from jsonschema import Draft202012Validator, FormatChecker


def validate() -> None:
    base = ROOT / "packages/schemas/exam/v1"
    schema_path = base / "exam-analytics-progress.schema.json"
    schema = json.loads(schema_path.read_text(encoding="utf-8"))
    Draft202012Validator.check_schema(schema)
    assert schema.get("additionalProperties") is False
    for def_name, def_obj in schema.get("$defs", {}).items():
        if def_obj.get("type") == "object":
            assert def_obj.get("additionalProperties") is False, f"{def_name} missing additionalProperties:false"

    props = schema["properties"]
    assert props["schemaVersion"]["const"] == "exam-analytics-progress-v1"
    assert props["trainingEligible"]["const"] is False
    assert props["llmTokensUsed"]["const"] == 0
    assert props["inferenceCostUsd"]["const"] == 0
    assert schema["$defs"]["TrapRemediationSummary"]["properties"]["associationIsCausal"]["const"] is False

    validator = Draft202012Validator(schema, format_checker=FormatChecker())

    good_full = json.loads((base / "examples/valid/exam-analytics-progress.json").read_text(encoding="utf-8"))
    good_empty = json.loads((base / "examples/valid/exam-analytics-progress.empty.json").read_text(encoding="utf-8"))
    validator.validate(good_full)
    validator.validate(good_empty)

    invalid_files = sorted((base / "examples/invalid").glob("exam-analytics-progress.*.json"))
    assert len(invalid_files) >= 4, f"Expected >=4 invalid fixtures, found {len(invalid_files)}"
    for invalid_path in invalid_files:
        payload = json.loads(invalid_path.read_text(encoding="utf-8"))
        assert list(validator.iter_errors(payload)), f"Invalid fixture unexpectedly passed: {invalid_path}"

    # Required property mutation checks
    for field in schema["required"]:
        mutated = copy.deepcopy(good_full)
        del mutated[field]
        assert list(validator.iter_errors(mutated)), f"Missing required field {field} unexpectedly passed"

    # Domain-invariant negative mutations
    bad_empty_delta = copy.deepcopy(good_empty)
    bad_empty_delta["deltaNet"] = 1.5
    assert list(validator.iter_errors(bad_empty_delta)), "0-mock report with non-null deltaNet must fail"

    bad_computed_small_n = copy.deepcopy(good_full)
    bad_computed_small_n["trapRemediation"]["correlationSampleSize"] = 2
    assert list(validator.iter_errors(bad_computed_small_n)), "COMPUTED correlation with sampleSize < 3 must fail"

    specs = [
        yaml.safe_load((ROOT / path).read_text(encoding="utf-8"))
        for path in (
            "packages/openapi/verified-ai-core-api.yaml",
            "packages/contracts/openapi/public-api.yaml",
        )
    ]

    for spec in specs:
        assert spec["components"]["schemas"]["ExamAnalyticsProgressReport"] == schema

    for prefix in ("/v1", "/api/v1"):
        path = f"{prefix}/analytics/exams/progress"
        assert specs[0]["paths"][path] == specs[1]["paths"][path], f"OpenAPI mirror mismatch on {path}"
        op = specs[0]["paths"][path]["get"]
        assert op["security"] == [{"bearerAuth": []}]
        assert {"200", "400", "401", "403", "429", "500"} <= set(op["responses"].keys())
        assert any(p["name"] == "examCode" and p["in"] == "query" and p["required"] is True for p in op["parameters"])
        resp_200 = op["responses"]["200"]
        assert resp_200["headers"]["Cache-Control"]["schema"]["const"] == "no-store"
        assert resp_200["content"]["application/json"]["schema"]["$ref"] == "#/components/schemas/ExamAnalyticsProgressReport"

    print("PASS: Sprint 9.9 exam-analytics-progress schema, fixtures, mutations, and OpenAPI mirrors verified.")


if __name__ == "__main__":
    validate()
