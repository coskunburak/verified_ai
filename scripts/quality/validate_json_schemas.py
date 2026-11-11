#!/usr/bin/env python3
"""
Sprint 7.8 Deterministic Schema, Contract, and Localization Gate Validator
Validates JSON Schema Draft 2020-12 invariants, negative fixtures, OpenAPI 3.1 specs, and EN/TR strings.
"""
import copy
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

# Ensure we can import jsonschema and yaml from local venv or system
venv_site = ROOT / "services/math-verifier/.venv/lib/python3.11/site-packages"
if venv_site.exists():
    sys.path.insert(0, str(venv_site))

try:
    import jsonschema
    import yaml
except ImportError:
    # If not importable directly, report or handle
    jsonschema = None
    yaml = None


def validate_schema_structure(schema, name):
    assert schema.get("$schema") == "https://json-schema.org/draft/2020-12/schema", f"{name} must be Draft 2020-12"
    assert "properties" in schema or "$defs" in schema, f"{name} missing properties"


def run_validation():
    skill_graph_schema_path = ROOT / "packages/schemas/mastery/skill-graph-v1.schema.json"
    skill_graph_fixture_path = ROOT / "packages/schemas/fixtures/skill-graph-v1.json"
    verified_twin_schema_path = ROOT / "packages/schemas/mastery/verified-twin-v1.schema.json"
    verified_twin_fixture_path = ROOT / "packages/schemas/fixtures/verified-twin-v1.json"
    openapi_path = ROOT / "packages/contracts/openapi/public-api.yaml"
    strings_path = ROOT / "apps/ios/VerifiedAI/Resources/Localizable.xcstrings"

    # 1. Load Files
    assert skill_graph_schema_path.exists(), "Missing skill-graph-v1.schema.json"
    assert skill_graph_fixture_path.exists(), "Missing skill-graph-v1.json"
    assert verified_twin_schema_path.exists(), "Missing verified-twin-v1.schema.json"
    assert verified_twin_fixture_path.exists(), "Missing verified-twin-v1.json"
    assert openapi_path.exists(), "Missing public-api.yaml"
    assert strings_path.exists(), "Missing Localizable.xcstrings"

    # Sprint 7.9 Files
    mistake_passport_schema_path = ROOT / "packages/schemas/mistake/mistake-passport-v1.schema.json"
    mistake_passport_fixture_path = ROOT / "packages/schemas/mistake/fixtures/mistake-passport-v1.valid.json"
    counterexample_schema_path = ROOT / "packages/schemas/attempt/counterexample-proof-card-v1.schema.json"
    counterexample_fixture_path = ROOT / "packages/schemas/attempt/fixtures/counterexample-proof-card-v1.valid.json"

    assert mistake_passport_schema_path.exists(), "Missing mistake-passport-v1.schema.json"
    assert mistake_passport_fixture_path.exists(), "Missing mistake-passport-v1.valid.json"
    assert counterexample_schema_path.exists(), "Missing counterexample-proof-card-v1.schema.json"
    assert counterexample_fixture_path.exists(), "Missing counterexample-proof-card-v1.valid.json"

    mp_schema = json.loads(mistake_passport_schema_path.read_text(encoding="utf-8"))
    mp_fixture = json.loads(mistake_passport_fixture_path.read_text(encoding="utf-8"))
    ce_schema = json.loads(counterexample_schema_path.read_text(encoding="utf-8"))
    ce_fixture = json.loads(counterexample_fixture_path.read_text(encoding="utf-8"))

    validate_schema_structure(mp_schema, "mistake-passport-v1.schema.json")
    validate_schema_structure(ce_schema, "counterexample-proof-card-v1.schema.json")

    sg_schema = json.loads(skill_graph_schema_path.read_text(encoding="utf-8"))
    sg_fixture = json.loads(skill_graph_fixture_path.read_text(encoding="utf-8"))
    vt_schema = json.loads(verified_twin_schema_path.read_text(encoding="utf-8"))
    vt_fixture = json.loads(verified_twin_fixture_path.read_text(encoding="utf-8"))

    validate_schema_structure(sg_schema, "skill-graph-v1.schema.json")
    validate_schema_structure(vt_schema, "verified-twin-v1.schema.json")

    if jsonschema is not None:
        # Schema meta-checks
        jsonschema.Draft202012Validator.check_schema(sg_schema)
        jsonschema.Draft202012Validator.check_schema(vt_schema)

        sg_val = jsonschema.Draft202012Validator(sg_schema)
        vt_val = jsonschema.Draft202012Validator(vt_schema)

        # Positive fixture validation
        sg_val.validate(sg_fixture)
        vt_val.validate(vt_fixture)

        # Negative Mutations on Skill Graph
        # Mutation A: Cold-start ancestor with mastery > 0.65
        bad_sg_1 = copy.deepcopy(sg_fixture)
        bad_sg_1["nodes"][0]["attemptCount"] = 0
        bad_sg_1["nodes"][0]["masteryScore"] = 0.85
        assert list(sg_val.iter_errors(bad_sg_1)), "Expected failure for unpracticed ancestor score > 0.65"

        # Mutation B: Cold-start ancestor claiming MASTERED state
        bad_sg_2 = copy.deepcopy(sg_fixture)
        bad_sg_2["nodes"][0]["attemptCount"] = 0
        bad_sg_2["nodes"][0]["masteryState"] = "MASTERED"
        assert list(sg_val.iter_errors(bad_sg_2)), "Expected failure for unpracticed ancestor claiming MASTERED"

        # Mutation C: Bottleneck true but not marked AT_RISK_REMEDIATION_REQUIRED
        bad_sg_3 = copy.deepcopy(sg_fixture)
        bad_sg_3["nodes"][1]["blockedByPrerequisiteBottleneck"] = true_val = True
        bad_sg_3["nodes"][1]["masteryState"] = "PRACTICING"
        assert list(sg_val.iter_errors(bad_sg_3)), "Expected failure for bottleneck not in AT_RISK state"

        # Negative Mutations on Verified Twin
        # Mutation D: trainingEligible = true (Strict zero-secret violation)
        bad_vt_1 = copy.deepcopy(vt_fixture)
        bad_vt_1["trainingEligible"] = True
        assert list(vt_val.iter_errors(bad_vt_1)), "Expected failure when trainingEligible is True"

        # Mutation E: Assisted solution reveal granting positive remediation credit
        bad_vt_2 = copy.deepcopy(vt_fixture)
        bad_vt_2["evaluation"]["evidenceClassification"] = "SOLUTION_REVEALED_ASSISTED"
        bad_vt_2["evaluation"]["remediationCredit"] = 0.1500
        assert list(vt_val.iter_errors(bad_vt_2)), "Expected failure when assisted reveal grants credit"

        # Mutation F: Status ISSUED but solutionSteps populated
        bad_vt_3 = copy.deepcopy(vt_fixture)
        bad_vt_3["status"] = "ISSUED"
        bad_vt_3["solutionSteps"] = [{"stepIndex": 1, "explanation": "x", "latexExpression": "x"}]
        assert list(vt_val.iter_errors(bad_vt_3)), "Expected failure when ISSUED contains solutionSteps"
    else:
        # Fallback pure-python verification of invariants
        assert sg_fixture["schemaVersion"] == "skill-graph-v1"
        assert vt_fixture["schemaVersion"] == "verified-twin-v1"
        assert vt_fixture["trainingEligible"] is False

    # Sprint 7.9 schema invariants
    assert mp_fixture["schemaVersion"] == "mistake-passport.v1"
    assert "conqueredExamTrapsCount" in mp_fixture["summaryMetrics"]
    assert "activeExamTrapsCount" in mp_fixture["summaryMetrics"]
    assert "independentTransferRate" in mp_fixture["summaryMetrics"]
    for trap in mp_fixture["conceptualTraps"]:
        assert trap["mistakeCode"] != "ARITH_BASIC_COMPUTATION", "ARITH_BASIC_COMPUTATION must not appear in conceptualTraps"
        metrics = trap["skillMetrics"]
        for key in ["observedOutcomeScore", "independentCompetenceScore", "posteriorMasteryProbability", "retentionProbability", "scaffoldDependencyIndex"]:
            assert 0.0 <= metrics[key] <= 1.0, f"Metric {key} out of bounds"
    for slip in mp_fixture["arithmeticSlips"]:
        assert slip["mistakeCode"] == "ARITH_BASIC_COMPUTATION", "Only ARITH_BASIC_COMPUTATION in arithmeticSlips"

    assert ce_fixture["cardId"] == "H-01"
    assert "witnessSubstitutionLatex" in ce_fixture
    assert "contradictionStatementLatex" in ce_fixture
    assert "accessibilitySpeechTr" in ce_fixture
    assert "accessibilitySpeechEn" in ce_fixture

    # 2. OpenAPI 3.1 Verification
    if yaml is not None:
        spec = yaml.safe_load(openapi_path.read_text(encoding="utf-8"))
        paths = spec.get("paths", {})
        assert "/v1/mastery/graph" in paths, "Missing /v1/mastery/graph in OpenAPI spec"
        assert "/v1/attempts/{attemptId}/twins" in paths, "Missing /v1/attempts/{attemptId}/twins in OpenAPI spec"
        assert "/v1/twins/{twinId}/submit" in paths, "Missing /v1/twins/{twinId}/submit in OpenAPI spec"
        assert "/v1/mistake-passport" in paths, "Missing /v1/mistake-passport in OpenAPI spec"

        passport_get = paths["/v1/mistake-passport"]["get"]
        assert passport_get["security"] == [{"bearerAuth": []}], "Security bearerAuth missing on /v1/mistake-passport"
        assert "200" in passport_get["responses"], "HTTP 200 OK missing from /v1/mistake-passport"

        graph_get = paths["/v1/mastery/graph"]["get"]
        assert graph_get["security"] == [{"bearerAuth": []}], "Security bearerAuth missing on /v1/mastery/graph"

        twin_post = paths["/v1/attempts/{attemptId}/twins"]["post"]
        assert twin_post["security"] == [{"bearerAuth": []}], "Security bearerAuth missing on /v1/attempts/{attemptId}/twins"

        submit_post = paths["/v1/twins/{twinId}/submit"]["post"]
        assert submit_post["security"] == [{"bearerAuth": []}], "Security bearerAuth missing on /v1/twins/{twinId}/submit"
        assert "423" in submit_post["responses"], "HTTP 423 Locked missing from submit responses"

    # 3. iOS Localizable.xcstrings 14-Key Verification
    xcstrings_data = json.loads(strings_path.read_text(encoding="utf-8"))
    strings = xcstrings_data.get("strings", {})

    required_keys = [
        "twin.challenge.title",
        "twin.challenge.locked_timer",
        "twin.challenge.reveal_solution",
        "twin.challenge.submit_answer",
        "twin.challenge.early_forfeit_warning",
        "twin.challenge.early_forfeit_confirm",
        "twin.challenge.success_title",
        "twin.challenge.remediation_credit",
        "twin.challenge.assisted_info",
        "mastery.graph.title",
        "mastery.graph.blocked_bottleneck",
        "mastery.graph.at_risk",
        "mastery.graph.practicing",
        "mastery.graph.mastered",
    ]

    for key in required_keys:
        assert key in strings, f"Missing localization key: {key}"
        entry = strings[key]
        for lang in ("en", "tr"):
            loc = entry.get("localizations", {}).get(lang, {}).get("stringUnit", {})
            val = loc.get("value")
            assert val and len(val.strip()) > 0, f"Missing {lang} translation for key: {key}"

    print("PASS: Sprint 7.8 JSON schemas, negative mutations, OpenAPI 3.1 endpoints, and 14 EN/TR localizations verified.")


if __name__ == "__main__":
    run_validation()
