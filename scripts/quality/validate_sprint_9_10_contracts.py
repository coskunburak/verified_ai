#!/usr/bin/env python3
"""Owner-run Sprint 9.10 contract gate (INV-EXAM-006 & Phase 09 Exit Gate). No app or DB access."""
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

    # 1. Validate exam-quality-gate.schema.json (Draft 2020-12 + conditional PASS/auth rules)
    qgate_path = base / "exam-quality-gate.schema.json"
    qgate_schema = json.loads(qgate_path.read_text(encoding="utf-8"))
    Draft202012Validator.check_schema(qgate_schema)
    assert qgate_schema.get("additionalProperties") is False
    for def_name, def_obj in qgate_schema.get("$defs", {}).items():
        if def_obj.get("type") == "object":
            assert def_obj.get("additionalProperties") is False, f"{def_name} missing additionalProperties:false"

    props = qgate_schema["properties"]
    assert props["schemaVersion"]["const"] == "exam-quality-gate-v1"
    assert props["phase"]["const"] == "PHASE_09"
    assert props["trainingEligible"]["const"] is False
    assert props["llmTokensUsed"]["const"] == 0
    assert props["inferenceCostUsd"]["const"] == 0

    qgate_validator = Draft202012Validator(qgate_schema, format_checker=FormatChecker())

    good_blocked = json.loads((base / "examples/valid/exam-quality-gate.blocked.json").read_text(encoding="utf-8"))
    good_failed = json.loads((base / "examples/valid/exam-quality-gate.failed-latency.json").read_text(encoding="utf-8"))
    good_passed = json.loads((base / "examples/valid/exam-quality-gate.passed-synthetic-fixture.json").read_text(encoding="utf-8"))
    for fixture in (good_blocked, good_failed, good_passed):
        qgate_validator.validate(fixture)

    invalid_qgate_files = sorted((base / "examples/invalid").glob("exam-quality-gate.*.json"))
    assert len(invalid_qgate_files) >= 5, f"Expected >=5 invalid quality-gate fixtures, found {len(invalid_qgate_files)}"
    for invalid_path in invalid_qgate_files:
        payload = json.loads(invalid_path.read_text(encoding="utf-8"))
        assert list(qgate_validator.iter_errors(payload)), f"Invalid fixture unexpectedly passed: {invalid_path}"

    # Required property mutation checks on exam-quality-gate
    for field in qgate_schema["required"]:
        mutated = copy.deepcopy(good_passed)
        del mutated[field]
        assert list(qgate_validator.iter_errors(mutated)), f"Missing required field {field} unexpectedly passed"

    # Negative invariant mutations on PASS threshold
    bad_350 = copy.deepcopy(good_passed)
    bad_350["checks"]["cntLos11P95"]["p95Ms"] = 350.0
    assert list(qgate_validator.iter_errors(bad_350)), "p95Ms == 350.0 must fail PASS schema gate"

    bad_samples = copy.deepcopy(good_passed)
    bad_samples["checks"]["cntLos10P95"]["sampleCount"] = 199
    assert list(qgate_validator.iter_errors(bad_samples)), "sampleCount < 200 must fail PASS schema gate"

    bad_formulas = copy.deepcopy(good_passed)
    bad_formulas["checks"]["accessibilityTrTR"]["verifiedFormulaCount"] = 49
    assert list(qgate_validator.iter_errors(bad_formulas)), "verifiedFormulaCount < 50 must fail PASS schema gate"

    # 2. Validate exam-integrity-proof-response.schema.json
    proof_path = base / "exam-integrity-proof-response.schema.json"
    proof_schema = json.loads(proof_path.read_text(encoding="utf-8"))
    Draft202012Validator.check_schema(proof_schema)
    assert proof_schema.get("additionalProperties") is False
    proof_validator = Draft202012Validator(proof_schema, format_checker=FormatChecker())
    good_proof = json.loads((base / "examples/valid/exam-integrity-proof-response.json").read_text(encoding="utf-8"))
    proof_validator.validate(good_proof)
    bad_proof = json.loads((base / "examples/invalid/exam-integrity-proof-response.training-violation.json").read_text(encoding="utf-8"))
    assert list(proof_validator.iter_errors(bad_proof)), "trainingEligible=true proof response must fail"

    bad_missing_hex = copy.deepcopy(good_proof)
    bad_missing_hex["sessionProofKeyHex"] = None
    assert list(proof_validator.iter_errors(bad_missing_hex)), "guard enabled with null key hex must fail"

    # 3. Validate updated exam-session-submit-request.schema.json (backward compatible + signed fixtures)
    submit_path = base / "exam-session-submit-request.schema.json"
    submit_schema = json.loads(submit_path.read_text(encoding="utf-8"))
    Draft202012Validator.check_schema(submit_schema)
    submit_validator = Draft202012Validator(submit_schema, format_checker=FormatChecker())
    good_legacy_submit = json.loads((base / "fixtures/exam-session-submit-request.valid.json").read_text(encoding="utf-8"))
    good_signed_submit = json.loads((base / "examples/valid/exam-session-submit-request.signed.json").read_text(encoding="utf-8"))
    submit_validator.validate(good_legacy_submit)
    submit_validator.validate(good_signed_submit)
    bad_signed_submit = json.loads((base / "examples/invalid/exam-session-submit-request.bad-signature.json").read_text(encoding="utf-8"))
    assert list(submit_validator.iter_errors(bad_signed_submit)), "non-hex submissionSignature must fail"

    # 4. Validate dual OpenAPI mirrors byte/semantic equivalence
    specs = [
        yaml.safe_load((ROOT / path).read_text(encoding="utf-8"))
        for path in (
            "packages/openapi/verified-ai-core-api.yaml",
            "packages/contracts/openapi/public-api.yaml",
        )
    ]

    for spec in specs:
        assert spec["components"]["schemas"]["ExamQualityGateReport"] == qgate_schema, "OpenAPI ExamQualityGateReport schema drift"
        assert spec["components"]["schemas"]["ExamIntegrityProofResponse"] == proof_schema, "OpenAPI ExamIntegrityProofResponse schema drift"

    for prefix in ("/v1", "/api/v1"):
        qg_path = f"{prefix}/admin/exams/quality-gate"
        assert specs[0]["paths"][qg_path] == specs[1]["paths"][qg_path], f"OpenAPI mirror mismatch on {qg_path}"
        op = specs[0]["paths"][qg_path]["get"]
        assert op["security"] == [{"bearerAuth": []}]
        assert "ROLE_ADMIN" in op.get("description", "")
        assert {"200", "401", "403", "429", "500", "503"} <= set(op["responses"].keys())
        resp_200 = op["responses"]["200"]
        assert resp_200["headers"]["Cache-Control"]["schema"]["const"] == "no-store"
        assert resp_200["content"]["application/json"]["schema"]["$ref"] == "#/components/schemas/ExamQualityGateReport"

        proof_ep = f"{prefix}/exams/sessions/{{sessionId}}/integrity-proof"
        assert specs[0]["paths"][proof_ep] == specs[1]["paths"][proof_ep], f"OpenAPI mirror mismatch on {proof_ep}"
        p_op = specs[0]["paths"][proof_ep]["get"]
        assert p_op["security"] == [{"bearerAuth": []}]
        assert {"200", "401", "403", "404", "409", "429", "500", "503"} <= set(p_op["responses"].keys())
        p_200 = p_op["responses"]["200"]
        assert p_200["headers"]["Cache-Control"]["schema"]["const"] == "no-store"
        assert p_200["content"]["application/json"]["schema"]["$ref"] == "#/components/schemas/ExamIntegrityProofResponse"

        sub_ep = f"{prefix}/exams/sessions/{{sessionId}}/submit"
        assert specs[0]["paths"][sub_ep] == specs[1]["paths"][sub_ep], f"OpenAPI mirror mismatch on {sub_ep}"

    print("PASS: Sprint 9.10 quality-gate, integrity-proof, submit schemas, fixtures, mutations, and OpenAPI mirrors verified.")


if __name__ == "__main__":
    validate()
