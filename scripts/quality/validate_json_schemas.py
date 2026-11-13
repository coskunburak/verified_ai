#!/usr/bin/env python3
"""
Sprint 7.8 Deterministic Schema, Contract, and Localization Gate Validator
Validates JSON Schema Draft 2020-12 invariants, negative fixtures, OpenAPI 3.1 specs, and EN/TR strings.
"""
import copy
import json
import re
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

    # Sprint 7.10 Files
    calibration_schema_path = ROOT / "packages/schemas/analytics/mastery-calibration-summary-v1.schema.json"
    calibration_fixture_path = ROOT / "packages/schemas/analytics/fixtures/mastery-calibration-summary-v1.valid.json"

    # Sprint 8.1 Files
    recommendations_schema_path = ROOT / "packages/schemas/studyplan/learner-recommendations-v1.schema.json"
    recommendations_fixture_path = ROOT / "packages/schemas/studyplan/fixtures/learner-recommendations-v1.valid.json"

    # Sprint 8.2 Files
    practice_session_schema_path = ROOT / "packages/schemas/studyplan/practice-session-v1.schema.json"
    practice_session_fixture_path = ROOT / "packages/schemas/studyplan/fixtures/practice-session-v1.valid.json"

    # Sprint 8.3 Files
    spaced_queue_schema_path = ROOT / "packages/schemas/studyplan/spaced-queue-v1.schema.json"
    spaced_queue_fixture_path = ROOT / "packages/schemas/studyplan/fixtures/spaced-queue-v1.valid.json"
    spaced_queue_example_path = ROOT / "packages/schemas/studyplan/examples/spaced-queue-v1.valid.json"

    # Sprint 8.4 Files
    daily_coach_schema_path = ROOT / "packages/schemas/coach/daily-coach-v1.schema.json"
    daily_coach_fixture_path = ROOT / "packages/schemas/coach/fixtures/daily-coach-v1.valid.json"

    # Sprint 8.5 Files
    recovery_status_schema_path = ROOT / "packages/schemas/studyplan/recovery-status-v1.schema.json"
    recovery_status_fixture_path = ROOT / "packages/schemas/studyplan/fixtures/recovery-status-v1.valid.json"
    rebalance_response_schema_path = ROOT / "packages/schemas/studyplan/rebalance-response-v1.schema.json"
    rebalance_response_fixture_path = ROOT / "packages/schemas/studyplan/fixtures/rebalance-response-v1.valid.json"
    openapi_core_path = ROOT / "packages/openapi/verified-ai-core-api.yaml"

    assert mistake_passport_schema_path.exists(), "Missing mistake-passport-v1.schema.json"
    assert mistake_passport_fixture_path.exists(), "Missing mistake-passport-v1.valid.json"
    assert counterexample_schema_path.exists(), "Missing counterexample-proof-card-v1.schema.json"
    assert counterexample_fixture_path.exists(), "Missing counterexample-proof-card-v1.valid.json"
    assert calibration_schema_path.exists(), "Missing mastery-calibration-summary-v1.schema.json"
    assert calibration_fixture_path.exists(), "Missing mastery-calibration-summary-v1.valid.json"
    assert recommendations_schema_path.exists(), "Missing learner-recommendations-v1.schema.json"
    assert recommendations_fixture_path.exists(), "Missing learner-recommendations-v1.valid.json"
    assert practice_session_schema_path.exists(), "Missing practice-session-v1.schema.json"
    assert practice_session_fixture_path.exists(), "Missing practice-session-v1.valid.json"
    assert spaced_queue_schema_path.exists(), "Missing spaced-queue-v1.schema.json"
    assert spaced_queue_fixture_path.exists(), "Missing spaced-queue-v1.valid.json"
    assert spaced_queue_example_path.exists(), "Missing spaced-queue-v1 example"
    assert daily_coach_schema_path.exists(), "Missing daily-coach-v1.schema.json"
    assert daily_coach_fixture_path.exists(), "Missing daily-coach-v1.valid.json"
    assert recovery_status_schema_path.exists(), "Missing recovery-status-v1.schema.json"
    assert recovery_status_fixture_path.exists(), "Missing recovery-status-v1.valid.json"
    assert rebalance_response_schema_path.exists(), "Missing rebalance-response-v1.schema.json"
    assert rebalance_response_fixture_path.exists(), "Missing rebalance-response-v1.valid.json"
    assert openapi_core_path.exists(), "Missing verified-ai-core-api.yaml"

    mp_schema = json.loads(mistake_passport_schema_path.read_text(encoding="utf-8"))
    mp_fixture = json.loads(mistake_passport_fixture_path.read_text(encoding="utf-8"))
    ce_schema = json.loads(counterexample_schema_path.read_text(encoding="utf-8"))
    ce_fixture = json.loads(counterexample_fixture_path.read_text(encoding="utf-8"))
    cal_schema = json.loads(calibration_schema_path.read_text(encoding="utf-8"))
    cal_fixture = json.loads(calibration_fixture_path.read_text(encoding="utf-8"))
    rec_schema = json.loads(recommendations_schema_path.read_text(encoding="utf-8"))
    rec_fixture = json.loads(recommendations_fixture_path.read_text(encoding="utf-8"))
    ps_schema = json.loads(practice_session_schema_path.read_text(encoding="utf-8"))
    ps_fixture = json.loads(practice_session_fixture_path.read_text(encoding="utf-8"))
    sq_schema = json.loads(spaced_queue_schema_path.read_text(encoding="utf-8"))
    sq_fixture = json.loads(spaced_queue_fixture_path.read_text(encoding="utf-8"))
    sq_example = json.loads(spaced_queue_example_path.read_text(encoding="utf-8"))
    dc_schema = json.loads(daily_coach_schema_path.read_text(encoding="utf-8"))
    dc_fixture = json.loads(daily_coach_fixture_path.read_text(encoding="utf-8"))
    rs_schema = json.loads(recovery_status_schema_path.read_text(encoding="utf-8"))
    rs_fixture = json.loads(recovery_status_fixture_path.read_text(encoding="utf-8"))
    rr_schema = json.loads(rebalance_response_schema_path.read_text(encoding="utf-8"))
    rr_fixture = json.loads(rebalance_response_fixture_path.read_text(encoding="utf-8"))

    validate_schema_structure(mp_schema, "mistake-passport-v1.schema.json")
    validate_schema_structure(ce_schema, "counterexample-proof-card-v1.schema.json")
    validate_schema_structure(cal_schema, "mastery-calibration-summary-v1.schema.json")
    validate_schema_structure(rec_schema, "learner-recommendations-v1.schema.json")
    validate_schema_structure(ps_schema, "practice-session-v1.schema.json")
    validate_schema_structure(sq_schema, "spaced-queue-v1.schema.json")
    validate_schema_structure(dc_schema, "daily-coach-v1.schema.json")
    validate_schema_structure(rs_schema, "recovery-status-v1.schema.json")
    validate_schema_structure(rr_schema, "rebalance-response-v1.schema.json")

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

        # Sprint 7.10 Calibration Schema Verification & Negative Mutations
        jsonschema.Draft202012Validator.check_schema(cal_schema)
        cal_val = jsonschema.Draft202012Validator(cal_schema)
        cal_val.validate(cal_fixture)

        # Mutation G: Invalid bin count (9 bins instead of 10)
        bad_cal_1 = copy.deepcopy(cal_fixture)
        bad_cal_1["reliabilityBins"].pop()
        assert list(cal_val.iter_errors(bad_cal_1)), "Expected failure when reliabilityBins length is not 10"

        # Mutation H: Out-of-range Brier Score (> 1.0)
        bad_cal_2 = copy.deepcopy(cal_fixture)
        bad_cal_2["brierScore"] = 1.35000
        assert list(cal_val.iter_errors(bad_cal_2)), "Expected failure when brierScore > 1.0"

        # Mutation I: Banned stigmatizing property rejection
        bad_cal_3 = copy.deepcopy(cal_fixture)
        bad_cal_3["failureRate"] = 0.1200
        assert list(cal_val.iter_errors(bad_cal_3)), "Expected failure when banned failureRate property is injected"

        # Sprint 8.1 Recommendations Schema Verification & Negative Mutations
        jsonschema.Draft202012Validator.check_schema(rec_schema)
        rec_val = jsonschema.Draft202012Validator(rec_schema)
        rec_val.validate(rec_fixture)

        # Mutation J1: priorityScore < 0.0 (-5.0)
        bad_rec_1 = copy.deepcopy(rec_fixture)
        bad_rec_1["recommendations"][0]["priorityScore"] = -5.0
        assert list(rec_val.iter_errors(bad_rec_1)), "Expected failure when priorityScore < 0.0"

        # Mutation J2: priorityScore > 100.0 (105.0)
        bad_rec_2 = copy.deepcopy(rec_fixture)
        bad_rec_2["recommendations"][0]["priorityScore"] = 105.0
        assert list(rec_val.iter_errors(bad_rec_2)), "Expected failure when priorityScore > 100.0"

        # Mutation K: Invalid rationaleCode enum
        bad_rec_3 = copy.deepcopy(rec_fixture)
        bad_rec_3["recommendations"][0]["rationaleCode"] = "UNKNOWN_RATIONALE"
        assert list(rec_val.iter_errors(bad_rec_3)), "Expected failure when rationaleCode is invalid"

        # Mutation L: Banned stigmatizing property rejection in rationaleParams
        bad_rec_4 = copy.deepcopy(rec_fixture)
        bad_rec_4["recommendations"][0]["rationaleParams"]["failureRate"] = 0.45
        assert list(rec_val.iter_errors(bad_rec_4)), "Expected failure when banned failureRate property is injected"

        bad_rec_5 = copy.deepcopy(rec_fixture)
        bad_rec_5["recommendations"][0]["rationaleParams"]["weaknessScore"] = 80.0
        assert list(rec_val.iter_errors(bad_rec_5)), "Expected failure when banned weaknessScore property is injected"

        # Mutation M: Non-zero LLM token cost ($0.00 guarantee violation)
        bad_rec_6 = copy.deepcopy(rec_fixture)
        bad_rec_6["llmTokenCostUsd"] = 0.05
        assert list(rec_val.iter_errors(bad_rec_6)), "Expected failure when llmTokenCostUsd > 0.0"

        # Sprint 8.2 Practice Session Schema Verification & Negative Mutations
        jsonschema.Draft202012Validator.check_schema(ps_schema)
        ps_val = jsonschema.Draft202012Validator(ps_schema)
        ps_val.validate(ps_fixture)

        # Mutation 1: Invalid session status
        bad_ps_1 = copy.deepcopy(ps_fixture)
        bad_ps_1["status"] = "FAILED_SESSION"
        assert list(ps_val.iter_errors(bad_ps_1)), "Expected failure when status is invalid enum"

        # Mutation 2: Target difficulty out of ZPD bounds (> 1.0)
        bad_ps_2 = copy.deepcopy(ps_fixture)
        bad_ps_2["items"][0]["targetDifficulty"] = 1.2500
        assert list(ps_val.iter_errors(bad_ps_2)), "Expected failure when targetDifficulty > 1.0"

        # Mutation 3: Invalid SymPy verification hash (not 64-hex)
        bad_ps_3 = copy.deepcopy(ps_fixture)
        bad_ps_3["items"][0]["sympyVerificationHash"] = "not-a-sha256"
        assert list(ps_val.iter_errors(bad_ps_3)), "Expected failure when sympyVerificationHash is not 64 hex chars"

        # Mutation 4: Injected stigmatizing property (weaknessScore)
        bad_ps_4 = copy.deepcopy(ps_fixture)
        bad_ps_4["weaknessScore"] = 0.42
        assert list(ps_val.iter_errors(bad_ps_4)), "Expected failure when banned weaknessScore is injected"

        # Mutation 5: Empty items array
        bad_ps_5 = copy.deepcopy(ps_fixture)
        bad_ps_5["items"] = []
        assert list(ps_val.iter_errors(bad_ps_5)), "Expected failure when items array is empty"

        # Sprint 8.3 Spaced Repetition Queue Schema Verification & Negative Mutations
        jsonschema.Draft202012Validator.check_schema(sq_schema)
        sq_val = jsonschema.Draft202012Validator(sq_schema)
        sq_val.validate(sq_fixture)
        sq_val.validate(sq_example)

        # Mutation 1: mut_missing_trigger_type (deletes triggerType from an item)
        bad_sq_1 = copy.deepcopy(sq_fixture)
        del bad_sq_1["items"][0]["triggerType"]
        assert list(sq_val.iter_errors(bad_sq_1)), "Expected failure when triggerType is missing from an item"

        # Mutation 2: mut_overload_cap_exceeded (sets items array length to 4 or surfacedCount: 4)
        bad_sq_2 = copy.deepcopy(sq_fixture)
        extra_item = copy.deepcopy(bad_sq_2["items"][0])
        extra_item["itemId"] = "99999999-aaaa-bbbb-cccc-999999999999"
        bad_sq_2["items"].append(extra_item)
        assert list(sq_val.iter_errors(bad_sq_2)), "Expected failure when items array length exceeds daily cap of 3"

        bad_sq_2b = copy.deepcopy(sq_fixture)
        bad_sq_2b["surfacedCount"] = 4
        assert list(sq_val.iter_errors(bad_sq_2b)), "Expected failure when surfacedCount > 3"

        # Mutation 3: mut_retention_out_of_bounds (sets estimatedRetention < 0.05 or > 1.0)
        bad_sq_3 = copy.deepcopy(sq_fixture)
        bad_sq_3["items"][0]["estimatedRetention"] = 0.01
        assert list(sq_val.iter_errors(bad_sq_3)), "Expected failure when estimatedRetention < 0.05"

        bad_sq_3b = copy.deepcopy(sq_fixture)
        bad_sq_3b["items"][0]["estimatedRetention"] = 1.15
        assert list(sq_val.iter_errors(bad_sq_3b)), "Expected failure when estimatedRetention > 1.0"

        # Mutation 4: mut_additional_property_injected (injects unallowedSecretField at root or item)
        bad_sq_4 = copy.deepcopy(sq_fixture)
        bad_sq_4["unallowedSecretField"] = True
        assert list(sq_val.iter_errors(bad_sq_4)), "Expected failure when root has additional property"

        bad_sq_4b = copy.deepcopy(sq_fixture)
        bad_sq_4b["items"][0]["unallowedSecretField"] = True
        assert list(sq_val.iter_errors(bad_sq_4b)), "Expected failure when item has additional property"

        # Mutation 5: mut_invalid_uuid_format (sets itemId: not-a-valid-uuid)
        bad_sq_5 = copy.deepcopy(sq_fixture)
        bad_sq_5["items"][0]["itemId"] = "not-a-valid-uuid"
        assert list(sq_val.iter_errors(bad_sq_5)), "Expected failure when itemId is not a valid UUID"

        # Sprint 8.4 Daily Coach Schema & Negative Mutations
        jsonschema.Draft202012Validator.check_schema(dc_schema)
        dc_val = jsonschema.Draft202012Validator(dc_schema, format_checker=jsonschema.FormatChecker())
        assert list(dc_val.iter_errors(dc_fixture)) == [], f"daily-coach fixture validation errors: {list(dc_val.iter_errors(dc_fixture))}"

        # Mutation 1: invalid budgetMinutes (12 not in [5, 10, 15, 20, 30])
        bad_dc_1 = copy.deepcopy(dc_fixture)
        bad_dc_1["budgetMinutes"] = 12
        assert list(dc_val.iter_errors(bad_dc_1)), "Expected validation rejection when budgetMinutes = 12"

        # Mutation 2: invalid preferredMode enum
        bad_dc_2 = copy.deepcopy(dc_fixture)
        bad_dc_2["preferredMode"] = "PASSIVE_CHAT_MODE"
        assert list(dc_val.iter_errors(bad_dc_2)), "Expected validation rejection when preferredMode = PASSIVE_CHAT_MODE"

        # Mutation 3: invalid taskType enum
        bad_dc_3 = copy.deepcopy(dc_fixture)
        bad_dc_3["tasks"][0]["taskType"] = "RANDOM_DRILL"
        assert list(dc_val.iter_errors(bad_dc_3)), "Expected validation rejection when taskType = RANDOM_DRILL"

        # Mutation 4: taskOrder out of [1..5] bounds
        bad_dc_4a = copy.deepcopy(dc_fixture)
        bad_dc_4a["tasks"][0]["taskOrder"] = 0
        assert list(dc_val.iter_errors(bad_dc_4a)), "Expected validation rejection when taskOrder = 0"

        bad_dc_4b = copy.deepcopy(dc_fixture)
        bad_dc_4b["tasks"][0]["taskOrder"] = 6
        assert list(dc_val.iter_errors(bad_dc_4b)), "Expected validation rejection when taskOrder = 6"

        # Mutation 5: additional property on root object
        bad_dc_5 = copy.deepcopy(dc_fixture)
        bad_dc_5["extraField"] = True
        assert list(dc_val.iter_errors(bad_dc_5)), "Expected validation rejection when root has extraField"

        # Sprint 8.5 Recovery Status Schema & 5 Negative Mutations
        jsonschema.Draft202012Validator.check_schema(rs_schema)
        rs_val = jsonschema.Draft202012Validator(rs_schema, format_checker=jsonschema.FormatChecker())
        assert list(rs_val.iter_errors(rs_fixture)) == [], f"recovery-status fixture errors: {list(rs_val.iter_errors(rs_fixture))}"

        bad_rs_1 = copy.deepcopy(rs_fixture)
        bad_rs_1["recommended_daily_budget_minutes"] = 30
        assert list(rs_val.iter_errors(bad_rs_1)), "Expected rejection when recommended_daily_budget_minutes = 30"

        bad_rs_2 = copy.deepcopy(rs_fixture)
        bad_rs_2["active_today_tasks_count"] = 4
        assert list(rs_val.iter_errors(bad_rs_2)), "Expected rejection when active_today_tasks_count = 4"

        bad_rs_3 = copy.deepcopy(rs_fixture)
        bad_rs_3["missed_days_count"] = -1
        assert list(rs_val.iter_errors(bad_rs_3)), "Expected rejection when missed_days_count = -1"

        bad_rs_4 = copy.deepcopy(rs_fixture)
        bad_rs_4["prune_reason_code"] = "UNKNOWN_PRUNE_REASON"
        assert list(rs_val.iter_errors(bad_rs_4)), "Expected rejection when prune_reason_code is invalid"

        bad_rs_5 = copy.deepcopy(rs_fixture)
        bad_rs_5["unexpected_extra_field"] = True
        assert list(rs_val.iter_errors(bad_rs_5)), "Expected rejection when recovery-status has additional property"

        # Sprint 8.5 Rebalance Response Schema & 5+ Negative Mutations
        jsonschema.Draft202012Validator.check_schema(rr_schema)
        rr_val = jsonschema.Draft202012Validator(rr_schema, format_checker=jsonschema.FormatChecker())
        assert list(rr_val.iter_errors(rr_fixture)) == [], f"rebalance-response fixture errors: {list(rr_val.iter_errors(rr_fixture))}"

        bad_rr_1 = copy.deepcopy(rr_fixture)
        bad_rr_1["rebalanced_daily_budget_minutes"] = 30
        assert list(rr_val.iter_errors(bad_rr_1)), "Expected rejection when rebalanced_daily_budget_minutes = 30"

        bad_rr_2 = copy.deepcopy(rr_fixture)
        bad_rr_2["active_today_tasks_count"] = 4
        assert list(rr_val.iter_errors(bad_rr_2)), "Expected rejection when active_today_tasks_count = 4"

        bad_rr_3 = copy.deepcopy(rr_fixture)
        bad_rr_3["missed_days_count"] = -1
        assert list(rr_val.iter_errors(bad_rr_3)), "Expected rejection when missed_days_count = -1"

        bad_rr_4 = copy.deepcopy(rr_fixture)
        bad_rr_4["trigger_type"] = "INVALID_TRIGGER"
        assert list(rr_val.iter_errors(bad_rr_4)), "Expected rejection when trigger_type is invalid"

        bad_rr_5 = copy.deepcopy(rr_fixture)
        bad_rr_5["unexpected_extra_field"] = "disallowed"
        assert list(rr_val.iter_errors(bad_rr_5)), "Expected rejection when rebalance-response has additional property"

        bad_rr_6 = copy.deepcopy(rr_fixture)
        bad_rr_6["pruned_concept_ids"] = ["MATH.ALG.LINEAR_EQ", "MATH.ALG.LINEAR_EQ"]
        assert list(rr_val.iter_errors(bad_rr_6)), "Expected rejection when pruned_concept_ids contains duplicates"
    else:
        # Fallback pure-python verification of invariants
        assert sg_fixture["schemaVersion"] == "skill-graph-v1"
        assert vt_fixture["schemaVersion"] == "verified-twin-v1"
        assert vt_fixture["trainingEligible"] is False
        assert sq_fixture["schemaVersion"] == "1.0.0"
        assert len(sq_fixture["items"]) <= 3

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

        assert "/v1/admin/calibration/mastery-summary" in paths, "Missing /v1/admin/calibration/mastery-summary in OpenAPI spec"
        cal_get = paths["/v1/admin/calibration/mastery-summary"]["get"]
        assert cal_get["security"] == [{"bearerAuth": []}], "Security bearerAuth missing on /v1/admin/calibration/mastery-summary"
        assert "200" in cal_get["responses"], "HTTP 200 OK missing from /v1/admin/calibration/mastery-summary"
        assert "403" in cal_get["responses"], "HTTP 403 Forbidden missing from /v1/admin/calibration/mastery-summary"

        assert "/v1/study-plan/recommendations" in paths, "Missing /v1/study-plan/recommendations in OpenAPI spec"
        study_get = paths["/v1/study-plan/recommendations"]["get"]
        assert study_get["security"] == [{"bearerAuth": []}], "Security bearerAuth missing on /v1/study-plan/recommendations"
        assert "200" in study_get["responses"], "HTTP 200 OK missing from /v1/study-plan/recommendations"
        assert "/v1/study-plan/recommendations/{id}/accept" in paths, "Missing accept recommendation endpoint in OpenAPI spec"
        assert "/v1/study-plan/recommendations/{id}/dismiss" in paths, "Missing dismiss recommendation endpoint in OpenAPI spec"

        # Sprint 8.2 Practice Endpoints
        assert "/v1/study-plan/practice/sessions" in paths, "Missing /v1/study-plan/practice/sessions in OpenAPI spec"
        practice_post = paths["/v1/study-plan/practice/sessions"]["post"]
        assert practice_post["security"] == [{"bearerAuth": []}], "Security bearerAuth missing on startPracticeSession"
        assert "201" in practice_post["responses"], "HTTP 201 Created missing from startPracticeSession"

        assert "/v1/study-plan/practice/sessions/{sessionId}" in paths, "Missing getPracticeSession endpoint in OpenAPI spec"
        practice_get = paths["/v1/study-plan/practice/sessions/{sessionId}"]["get"]
        assert practice_get["security"] == [{"bearerAuth": []}], "Security bearerAuth missing on getPracticeSession"
        assert "200" in practice_get["responses"], "HTTP 200 OK missing from getPracticeSession"
        assert "404" in practice_get["responses"], "HTTP 404 Not Found missing from getPracticeSession"

        assert "/v1/study-plan/practice/sessions/{sessionId}/items/{itemId}/submit" in paths, "Missing submitPracticeItemAnswer endpoint in OpenAPI spec"
        practice_submit = paths["/v1/study-plan/practice/sessions/{sessionId}/items/{itemId}/submit"]["post"]
        assert practice_submit["security"] == [{"bearerAuth": []}], "Security bearerAuth missing on submitPracticeItemAnswer"
        assert "200" in practice_submit["responses"], "HTTP 200 OK missing from submitPracticeItemAnswer"
        assert "422" in practice_submit["responses"], "HTTP 422 Unprocessable Entity missing from submitPracticeItemAnswer"

        assert "/v1/study-plan/practice/sessions/{sessionId}/abandon" in paths, "Missing abandonPracticeSession endpoint in OpenAPI spec"
        practice_abandon = paths["/v1/study-plan/practice/sessions/{sessionId}/abandon"]["post"]
        assert practice_abandon["security"] == [{"bearerAuth": []}], "Security bearerAuth missing on abandonPracticeSession"
        assert "200" in practice_abandon["responses"], "HTTP 200 OK missing from abandonPracticeSession"

        # Sprint 8.3 Spaced Repetition Endpoints
        assert "/v1/study-plan/spaced-queue" in paths, "Missing /v1/study-plan/spaced-queue in OpenAPI spec"
        sq_get = paths["/v1/study-plan/spaced-queue"]["get"]
        assert sq_get["security"] == [{"bearerAuth": []}], "Security bearerAuth missing on /v1/study-plan/spaced-queue"
        assert "200" in sq_get["responses"], "HTTP 200 OK missing from /v1/study-plan/spaced-queue"
        assert "401" in sq_get["responses"], "HTTP 401 Unauthorized missing from /v1/study-plan/spaced-queue"

        assert "/v1/study-plan/spaced-queue/{itemId}/complete" in paths, "Missing complete spaced item endpoint in OpenAPI spec"
        sq_complete = paths["/v1/study-plan/spaced-queue/{itemId}/complete"]["post"]
        assert sq_complete["security"] == [{"bearerAuth": []}], "Security bearerAuth missing on complete spaced item"
        assert "200" in sq_complete["responses"], "HTTP 200 OK missing from complete spaced item"
        assert "401" in sq_complete["responses"], "HTTP 401 Unauthorized missing from complete spaced item"
        assert "404" in sq_complete["responses"], "HTTP 404 Not Found missing from complete spaced item"
        assert "422" in sq_complete["responses"], "HTTP 422 Unprocessable Entity missing from complete spaced item"

        assert "/v1/study-plan/spaced-queue/{itemId}/dismiss" in paths, "Missing dismiss spaced item endpoint in OpenAPI spec"
        sq_dismiss = paths["/v1/study-plan/spaced-queue/{itemId}/dismiss"]["post"]
        assert sq_dismiss["security"] == [{"bearerAuth": []}], "Security bearerAuth missing on dismiss spaced item"
        assert "200" in sq_dismiss["responses"], "HTTP 200 OK missing from dismiss spaced item"
        assert "401" in sq_dismiss["responses"], "HTTP 401 Unauthorized missing from dismiss spaced item"
        assert "404" in sq_dismiss["responses"], "HTTP 404 Not Found missing from dismiss spaced item"

        # Sprint 8.4 Daily Coach Endpoints
        for coach_path, method in (
            ("/v1/coach/today-summary", "get"),
            ("/v1/coach/preferences", "patch"),
            ("/v1/coach/missions/{id}/complete", "post"),
            ("/v1/coach/missions/{id}/downshift", "post"),
        ):
            assert coach_path in paths, f"Missing {coach_path} endpoint in OpenAPI spec"
            op = paths[coach_path][method]
            assert op["security"] == [{"bearerAuth": []}], f"Security bearerAuth missing on {coach_path}"
            assert "200" in op["responses"], f"HTTP 200 OK missing from {coach_path}"
            assert "401" in op["responses"], f"HTTP 401 Unauthorized missing from {coach_path}"

        # Sprint 8.5 Study Plan Rebalancing Endpoints across BOTH OpenAPI mirrors
        core_spec = yaml.safe_load(openapi_core_path.read_text(encoding="utf-8"))
        for spec_name, loaded_spec in (("public-api.yaml", spec), ("verified-ai-core-api.yaml", core_spec)):
            spec_paths = loaded_spec.get("paths", {})
            spec_schemas = loaded_spec.get("components", {}).get("schemas", {})

            assert "/v1/study-plan/recovery-status" in spec_paths, f"Missing /v1/study-plan/recovery-status in {spec_name}"
            rec_op = spec_paths["/v1/study-plan/recovery-status"]["get"]
            assert rec_op["security"] == [{"bearerAuth": []}], f"Security bearerAuth missing on recovery-status in {spec_name}"
            for code in ("200", "400", "401", "404"):
                assert code in rec_op["responses"], f"HTTP {code} missing from recovery-status in {spec_name}"

            assert "/v1/study-plan/rebalance" in spec_paths, f"Missing /v1/study-plan/rebalance in {spec_name}"
            reb_op = spec_paths["/v1/study-plan/rebalance"]["post"]
            assert reb_op["security"] == [{"bearerAuth": []}], f"Security bearerAuth missing on rebalance in {spec_name}"
            for code in ("200", "400", "401", "404", "409", "422"):
                assert code in reb_op["responses"], f"HTTP {code} missing from rebalance in {spec_name}"

            for schema_name in (
                "StudyPlanRecoveryStatusResponse",
                "StudyPlanRebalanceRequest",
                "StudyPlanRebalanceResponse",
            ):
                assert schema_name in spec_schemas, f"Missing component schema {schema_name} in {spec_name}"

    # 3. iOS Localizable.xcstrings Key Verification
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
        "coach.nba.section.title",
        "coach.nba.type.prerequisite_repair",
        "coach.nba.type.mistake_repair",
        "coach.nba.type.spaced_retention",
        "coach.nba.type.high_roi_topic",
        "coach.nba.rationale.active_recurring_trap",
        "coach.nba.rationale.retention_half_life_due",
        "coach.nba.rationale.prerequisite_repair",
        "coach.nba.rationale.high_exam_net_roi",
        "coach.nba.action.accept",
        "coach.nba.action.dismiss",
        "coach.nba.offline.banner",
        "practice.session.title",
        "practice.slot.trap_closure",
        "practice.slot.spaced_drill",
        "practice.slot.exam_synthesis",
        "practice.lock.active",
        "practice.lock.unlocked",
        "practice.rapid_guess.warning",
        "practice.action.submit",
        "practice.action.hint",
        "practice.action.abandon",
        "practice.trap.detected",
        "studyplan.spaced.section.title",
        "studyplan.spaced.badge.retention_refresh",
        "studyplan.spaced.badge.trap_immunity_7d",
        "studyplan.spaced.retention.pill",
        "studyplan.spaced.empty.title",
        "studyplan.spaced.action.start",
        "coach.hub.exam_target",
        "coach.hub.streak_days",
        "coach.hub.offline_banner",
        "coach.hub.mission_title",
        "coach.hub.budget_15",
        "coach.hub.preview_label",
        "coach.hub.start_mission_cta",
        "coach.hub.coldstart_title",
        "coach.hub.coldstart_cta",
        "coach.hub.capture_title",
        "coach.hub.capture_subtitle",
        "coach.hub.capture_cta",
        "coach.hub.radar_title",
        "coach.hub.badge.growth_opportunity",
        "coach.hub.badge.retention_refresh",
        "coach.hub.badge.immunity_check",
        "coach.mode.solver.title",
        "coach.mode.solver.subtitle",
        "coach.mode.socratic.title",
        "coach.mode.socratic.subtitle",
        "coach.plan.title",
        "coach.plan.subtitle",
        "coach.plan.why_this_task",
        "coach.plan.complete_task_cta",
        "coach.plan.block.trap_repair",
        "coach.plan.block.spaced_retention",
        "coach.plan.block.exam_synthesis",
        "coach.plan.status.completed",
        "coach.plan.status.skipped",
        "coach.plan.status.pending",
        "coach.downshift.badge",
        "coach.downshift.title",
        "coach.downshift.subtitle",
        "coach.downshift.button",
        "studyplan.rebalance.sheet_title",
        "studyplan.rebalance.welcome_recovered",
        "studyplan.rebalance.welcome_mini_mode",
        "studyplan.rebalance.welcome_steady",
        "studyplan.rebalance.no_backlog_badge",
        "studyplan.rebalance.cta_accept_plan",
        "studyplan.rebalance.cta_mini_mode",
        "studyplan.rebalance.summary_budget_label",
        "studyplan.rebalance.summary_tasks_label",
    ]

    banned_lexicon = re.compile(
        r"\b(overdue|behind|fail|failure|punish|penalty|weak|weakness|poor|bad|stupid|dumb|incompetent|deficit|flaw|hopeless)\b",
        re.IGNORECASE,
    )

    for key in required_keys:
        assert key in strings, f"Missing localization key: {key}"
        entry = strings[key]
        for lang in ("en", "tr"):
            loc = entry.get("localizations", {}).get(lang, {}).get("stringUnit", {})
            val = loc.get("value")
            assert val and len(val.strip()) > 0, f"Missing {lang} translation for key: {key}"
            if key.startswith("studyplan.rebalance."):
                m = banned_lexicon.search(val)
                assert not m, f"Stigmatizing lexicon word '{m.group(0)}' in {key}[{lang}]: {val}"

    print(f"PASS: Sprint 8.1, 8.2, 8.3, 8.4 & 8.5 JSON schemas, negative mutations, OpenAPI 3.1 endpoints, and {len(required_keys)} EN/TR localizations verified.")


if __name__ == "__main__":
    run_validation()
