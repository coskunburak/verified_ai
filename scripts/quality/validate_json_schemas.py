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

    # Sprint 8.6 Files
    micro_card_schema_path = ROOT / "packages/schemas/micro-lesson-card-v1.schema.json"
    micro_req_schema_path = ROOT / "packages/schemas/micro-lesson-complete-request-v1.schema.json"
    micro_res_schema_path = ROOT / "packages/schemas/micro-lesson-complete-response-v1.schema.json"
    micro_card_tr_fixture_path = ROOT / "packages/schemas/fixtures/micro-lesson-card-tr-v1.valid.json"
    micro_card_en_fixture_path = ROOT / "packages/schemas/fixtures/micro-lesson-card-en-v1.valid.json"
    micro_req_fixture_path = ROOT / "packages/schemas/fixtures/micro-lesson-complete-request-v1.valid.json"
    micro_res_fixture_path = ROOT / "packages/schemas/fixtures/micro-lesson-complete-response-v1.valid.json"

    # Sprint 8.7 schemas & fixtures
    ss_state_schema_path = ROOT / "packages/schemas/study-session-state-v1.schema.json"
    ss_start_req_schema_path = ROOT / "packages/schemas/study-session-start-request-v1.schema.json"
    ss_start_res_schema_path = ROOT / "packages/schemas/study-session-start-response-v1.schema.json"
    ss_ckpt_req_schema_path = ROOT / "packages/schemas/study-session-checkpoint-request-v1.schema.json"
    ss_ckpt_res_schema_path = ROOT / "packages/schemas/study-session-checkpoint-response-v1.schema.json"
    ss_comp_req_schema_path = ROOT / "packages/schemas/study-session-complete-request-v1.schema.json"
    ss_comp_res_schema_path = ROOT / "packages/schemas/study-session-complete-response-v1.schema.json"

    ss_state_fixture_path = ROOT / "packages/schemas/fixtures/study-session-state-v1.valid.json"
    ss_start_req_fixture_path = ROOT / "packages/schemas/fixtures/study-session-start-request-v1.valid.json"
    ss_start_res_fixture_path = ROOT / "packages/schemas/fixtures/study-session-start-response-v1.valid.json"
    ss_ckpt_req_fixture_path = ROOT / "packages/schemas/fixtures/study-session-checkpoint-request-v1.valid.json"
    ss_ckpt_res_fixture_path = ROOT / "packages/schemas/fixtures/study-session-checkpoint-response-v1.valid.json"
    ss_comp_req_fixture_path = ROOT / "packages/schemas/fixtures/study-session-complete-request-v1.valid.json"
    ss_comp_res_fixture_path = ROOT / "packages/schemas/fixtures/study-session-complete-response-v1.valid.json"

    # Sprint 8.8 schemas & fixtures
    gam_req_schema_path = ROOT / "packages/schemas/gamification-activity-record-request.schema.json"
    gam_res_schema_path = ROOT / "packages/schemas/gamification-summary-response.schema.json"
    gam_req_fixture_path = ROOT / "packages/schemas/fixtures/gamification-activity-record-request.valid.json"
    gam_res_fixture_path = ROOT / "packages/schemas/fixtures/gamification-summary-response.valid.json"
    SCHEMA_FIXTURE_PAIRS = (
        (gam_req_schema_path, gam_req_fixture_path, "gamification-activity-record-request"),
        (gam_res_schema_path, gam_res_fixture_path, "gamification-summary-response"),
    )

    # Sprint 8.9 schemas & fixtures
    s89_digest_schema_path = ROOT / "packages/schemas/weekly-digest-v1.schema.json"
    s89_share_schema_path = ROOT / "packages/schemas/weekly-share-packet-v1.schema.json"
    s89_digest_fixture_path = ROOT / "packages/schemas/fixtures/weekly_digest_v1.valid.json"
    s89_share_fixture_path = ROOT / "packages/schemas/fixtures/weekly_share_packet_v1.valid.json"
    S89_SCHEMA_FIXTURE_PAIRS = (
        (s89_digest_schema_path, s89_digest_fixture_path, "weekly-digest-v1"),
        (s89_share_schema_path, s89_share_fixture_path, "weekly-share-packet-v1"),
    )
    for _p, _f, _name in S89_SCHEMA_FIXTURE_PAIRS:
        assert _p.exists(), f"Missing Sprint 8.9 schema {_p}"
        assert _f.exists(), f"Missing Sprint 8.9 fixture {_f}"

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
    assert micro_card_schema_path.exists(), "Missing micro-lesson-card-v1.schema.json"
    assert micro_req_schema_path.exists(), "Missing micro-lesson-complete-request-v1.schema.json"
    assert micro_res_schema_path.exists(), "Missing micro-lesson-complete-response-v1.schema.json"
    assert micro_card_tr_fixture_path.exists(), "Missing micro-lesson-card-tr-v1.valid.json"
    assert micro_card_en_fixture_path.exists(), "Missing micro-lesson-card-en-v1.valid.json"
    assert micro_req_fixture_path.exists(), "Missing micro-lesson-complete-request-v1.valid.json"
    assert micro_res_fixture_path.exists(), "Missing micro-lesson-complete-response-v1.valid.json"

    for p in (
        ss_state_schema_path,
        ss_start_req_schema_path,
        ss_start_res_schema_path,
        ss_ckpt_req_schema_path,
        ss_ckpt_res_schema_path,
        ss_comp_req_schema_path,
        ss_comp_res_schema_path,
        ss_state_fixture_path,
        ss_start_req_fixture_path,
        ss_start_res_fixture_path,
        ss_ckpt_req_fixture_path,
        ss_ckpt_res_fixture_path,
        ss_comp_req_fixture_path,
        ss_comp_res_fixture_path,
    ):
        assert p.exists(), f"Missing Sprint 8.7 schema/fixture: {p}"

    for s_path, f_path, pair_name in SCHEMA_FIXTURE_PAIRS:
        assert s_path.exists(), f"Missing Sprint 8.8 schema ({pair_name}): {s_path}"
        assert f_path.exists(), f"Missing Sprint 8.8 fixture ({pair_name}): {f_path}"

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

    ml_card_schema = json.loads(micro_card_schema_path.read_text(encoding="utf-8"))
    ml_req_schema = json.loads(micro_req_schema_path.read_text(encoding="utf-8"))
    ml_res_schema = json.loads(micro_res_schema_path.read_text(encoding="utf-8"))
    ml_card_tr_fixture = json.loads(micro_card_tr_fixture_path.read_text(encoding="utf-8"))
    ml_card_en_fixture = json.loads(micro_card_en_fixture_path.read_text(encoding="utf-8"))
    ml_req_fixture = json.loads(micro_req_fixture_path.read_text(encoding="utf-8"))
    ml_res_fixture = json.loads(micro_res_fixture_path.read_text(encoding="utf-8"))
    validate_schema_structure(ml_card_schema, "micro-lesson-card-v1.schema.json")
    validate_schema_structure(ml_req_schema, "micro-lesson-complete-request-v1.schema.json")
    validate_schema_structure(ml_res_schema, "micro-lesson-complete-response-v1.schema.json")

    ss_state_schema = json.loads(ss_state_schema_path.read_text(encoding="utf-8"))
    ss_start_req_schema = json.loads(ss_start_req_schema_path.read_text(encoding="utf-8"))
    ss_start_res_schema = json.loads(ss_start_res_schema_path.read_text(encoding="utf-8"))
    ss_ckpt_req_schema = json.loads(ss_ckpt_req_schema_path.read_text(encoding="utf-8"))
    ss_ckpt_res_schema = json.loads(ss_ckpt_res_schema_path.read_text(encoding="utf-8"))
    ss_comp_req_schema = json.loads(ss_comp_req_schema_path.read_text(encoding="utf-8"))
    ss_comp_res_schema = json.loads(ss_comp_res_schema_path.read_text(encoding="utf-8"))

    ss_state_fixture = json.loads(ss_state_fixture_path.read_text(encoding="utf-8"))
    ss_start_req_fixture = json.loads(ss_start_req_fixture_path.read_text(encoding="utf-8"))
    ss_start_res_fixture = json.loads(ss_start_res_fixture_path.read_text(encoding="utf-8"))
    ss_ckpt_req_fixture = json.loads(ss_ckpt_req_fixture_path.read_text(encoding="utf-8"))
    ss_ckpt_res_fixture = json.loads(ss_ckpt_res_fixture_path.read_text(encoding="utf-8"))
    ss_comp_req_fixture = json.loads(ss_comp_req_fixture_path.read_text(encoding="utf-8"))
    ss_comp_res_fixture = json.loads(ss_comp_res_fixture_path.read_text(encoding="utf-8"))

    for schema_obj, schema_file in (
        (ss_state_schema, "study-session-state-v1.schema.json"),
        (ss_start_req_schema, "study-session-start-request-v1.schema.json"),
        (ss_start_res_schema, "study-session-start-response-v1.schema.json"),
        (ss_ckpt_req_schema, "study-session-checkpoint-request-v1.schema.json"),
        (ss_ckpt_res_schema, "study-session-checkpoint-response-v1.schema.json"),
        (ss_comp_req_schema, "study-session-complete-request-v1.schema.json"),
        (ss_comp_res_schema, "study-session-complete-response-v1.schema.json"),
    ):
        validate_schema_structure(schema_obj, schema_file)
        assert schema_obj.get("$schema") == "https://json-schema.org/draft/2020-12/schema", f"Not Draft 2020-12: {schema_file}"
        assert schema_obj.get("additionalProperties") is False, f"Root additionalProperties!=false in {schema_file}"
        for def_name, def_obj in schema_obj.get("$defs", {}).items():
            if def_obj.get("type") == "object":
                assert def_obj.get("additionalProperties") is False, f"$defs.{def_name} additionalProperties!=false in {schema_file}"

    sg_schema = json.loads(skill_graph_schema_path.read_text(encoding="utf-8"))
    sg_fixture = json.loads(skill_graph_fixture_path.read_text(encoding="utf-8"))
    vt_schema = json.loads(verified_twin_schema_path.read_text(encoding="utf-8"))
    vt_fixture = json.loads(verified_twin_fixture_path.read_text(encoding="utf-8"))

    validate_schema_structure(sg_schema, "skill-graph-v1.schema.json")
    validate_schema_structure(vt_schema, "verified-twin-v1.schema.json")

    gam_loaded_pairs = {}
    for s_path, f_path, pair_name in SCHEMA_FIXTURE_PAIRS:
        sc_obj = json.loads(s_path.read_text(encoding="utf-8"))
        fx_obj = json.loads(f_path.read_text(encoding="utf-8"))
        validate_schema_structure(sc_obj, s_path.name)
        assert sc_obj.get("$schema") == "https://json-schema.org/draft/2020-12/schema", f"Not Draft 2020-12: {s_path.name}"
        assert sc_obj.get("additionalProperties") is False, f"Root additionalProperties!=false in {s_path.name}"
        for def_name, def_obj in sc_obj.get("$defs", {}).items():
            if def_obj.get("type") == "object":
                assert def_obj.get("additionalProperties") is False, f"$defs.{def_name} additionalProperties!=false in {s_path.name}"
        gam_loaded_pairs[pair_name] = (sc_obj, fx_obj)

    s89_loaded_pairs = {}
    for s_path, f_path, pair_name in S89_SCHEMA_FIXTURE_PAIRS:
        sc_obj = json.loads(s_path.read_text(encoding="utf-8"))
        fx_obj = json.loads(f_path.read_text(encoding="utf-8"))
        validate_schema_structure(sc_obj, s_path.name)
        assert sc_obj.get("$schema") == "https://json-schema.org/draft/2020-12/schema", f"Not Draft 2020-12: {s_path.name}"
        assert sc_obj.get("additionalProperties") is False, f"Root additionalProperties!=false in {s_path.name}"
        for def_name, def_obj in sc_obj.get("$defs", {}).items():
            if def_obj.get("type") == "object":
                assert def_obj.get("additionalProperties") is False, f"$defs.{def_name} additionalProperties!=false in {s_path.name}"
        s89_loaded_pairs[pair_name] = (sc_obj, fx_obj)

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

        # Sprint 8.6 micro-lesson schema validation & negative mutations
        jsonschema.Draft202012Validator.check_schema(ml_card_schema)
        jsonschema.Draft202012Validator.check_schema(ml_req_schema)
        jsonschema.Draft202012Validator.check_schema(ml_res_schema)
        ml_card_val = jsonschema.Draft202012Validator(ml_card_schema, format_checker=jsonschema.FormatChecker())
        ml_req_val = jsonschema.Draft202012Validator(ml_req_schema, format_checker=jsonschema.FormatChecker())
        ml_res_val = jsonschema.Draft202012Validator(ml_res_schema, format_checker=jsonschema.FormatChecker())
        ml_card_val.validate(ml_card_tr_fixture)
        ml_card_val.validate(ml_card_en_fixture)
        ml_req_val.validate(ml_req_fixture)
        ml_res_val.validate(ml_res_fixture)

        bad_ml_card = copy.deepcopy(ml_card_tr_fixture)
        bad_ml_card["estimatedDurationSeconds"] = 240
        assert list(ml_card_val.iter_errors(bad_ml_card)), "Expected rejection when estimatedDurationSeconds > 180"

        bad_ml_card_2 = copy.deepcopy(ml_card_en_fixture)
        bad_ml_card_2["unexpectedExtra"] = True
        assert list(ml_card_val.iter_errors(bad_ml_card_2)), "Expected rejection on additionalProperties in micro-lesson card"

        bad_ml_req = copy.deepcopy(ml_req_fixture)
        bad_ml_req["dwellTimeMs"] = 500
        assert list(ml_req_val.iter_errors(bad_ml_req)), "Expected rejection when dwellTimeMs < 1000"

        bad_ml_res = copy.deepcopy(ml_res_fixture)
        bad_ml_res["trainingEligible"] = True
        assert list(ml_res_val.iter_errors(bad_ml_res)), "Expected rejection when trainingEligible != false"

        # Sprint 8.7 Draft 2020-12 positive & 6 negative mutation checks
        ss_pairs = (
            (ss_state_schema, ss_state_fixture, "study-session-state-v1"),
            (ss_start_req_schema, ss_start_req_fixture, "study-session-start-request-v1"),
            (ss_start_res_schema, ss_start_res_fixture, "study-session-start-response-v1"),
            (ss_ckpt_req_schema, ss_ckpt_req_fixture, "study-session-checkpoint-request-v1"),
            (ss_ckpt_res_schema, ss_ckpt_res_fixture, "study-session-checkpoint-response-v1"),
            (ss_comp_req_schema, ss_comp_req_fixture, "study-session-complete-request-v1"),
            (ss_comp_res_schema, ss_comp_res_fixture, "study-session-complete-response-v1"),
        )
        ss_vals = {}
        for sc, fx, name in ss_pairs:
            jsonschema.Draft202012Validator.check_schema(sc)
            v = jsonschema.Draft202012Validator(sc, format_checker=jsonschema.FormatChecker())
            v.validate(fx)
            ss_vals[name] = v

        # Negative mutation 1: training_eligible: true
        bad_ss_1 = copy.deepcopy(ss_state_fixture)
        bad_ss_1["training_eligible"] = True
        assert list(ss_vals["study-session-state-v1"].iter_errors(bad_ss_1)), "Expected rejection when training_eligible=true"

        # Negative mutation 2: reported_active_duration_seconds: 14401 / -1
        bad_ss_2 = copy.deepcopy(ss_ckpt_req_fixture)
        bad_ss_2["reported_active_duration_seconds"] = 14401
        assert list(ss_vals["study-session-checkpoint-request-v1"].iter_errors(bad_ss_2)), "Expected rejection when reported_active_duration_seconds=14401"
        bad_ss_2b = copy.deepcopy(ss_ckpt_req_fixture)
        bad_ss_2b["reported_active_duration_seconds"] = -1
        assert list(ss_vals["study-session-checkpoint-request-v1"].iter_errors(bad_ss_2b)), "Expected rejection when reported_active_duration_seconds=-1"

        # Negative mutation 3: invalid state: TERMINATED
        bad_ss_3 = copy.deepcopy(ss_state_fixture)
        bad_ss_3["state"] = "TERMINATED"
        assert list(ss_vals["study-session-state-v1"].iter_errors(bad_ss_3)), "Expected rejection when state=TERMINATED"

        # Negative mutation 4: total_steps: 0 or 51
        bad_ss_4a = copy.deepcopy(ss_start_req_fixture)
        bad_ss_4a["total_steps"] = 0
        assert list(ss_vals["study-session-start-request-v1"].iter_errors(bad_ss_4a)), "Expected rejection when total_steps=0"
        bad_ss_4b = copy.deepcopy(ss_start_req_fixture)
        bad_ss_4b["total_steps"] = 51
        assert list(ss_vals["study-session-start-request-v1"].iter_errors(bad_ss_4b)), "Expected rejection when total_steps=51"

        # Negative mutation 5: extra unexpected property
        bad_ss_5 = copy.deepcopy(ss_ckpt_res_fixture)
        bad_ss_5["unexpected_field"] = 123
        assert list(ss_vals["study-session-checkpoint-response-v1"].iter_errors(bad_ss_5)), "Expected rejection on unexpected_field"

        # Negative mutation 6: short idempotency_key
        bad_ss_6 = copy.deepcopy(ss_ckpt_req_fixture)
        bad_ss_6["idempotency_key"] = "short"
        assert list(ss_vals["study-session-checkpoint-request-v1"].iter_errors(bad_ss_6)), "Expected rejection when idempotency_key < 16 chars"

        # Sprint 8.8 Draft 2020-12 positive & negative mutation checks
        gam_vals = {}
        for pair_name, (sc_obj, fx_obj) in gam_loaded_pairs.items():
            jsonschema.Draft202012Validator.check_schema(sc_obj)
            v = jsonschema.Draft202012Validator(sc_obj, format_checker=jsonschema.FormatChecker())
            v.validate(fx_obj)
            gam_vals[pair_name] = v

        gam_req_sc, gam_req_fx = gam_loaded_pairs["gamification-activity-record-request"]
        gam_res_sc, gam_res_fx = gam_loaded_pairs["gamification-summary-response"]

        bad_gam_1 = copy.deepcopy(gam_res_fx)
        bad_gam_1["trainingEligible"] = True
        assert list(gam_vals["gamification-summary-response"].iter_errors(bad_gam_1)), "Expected rejection when trainingEligible=true"

        bad_gam_2a = copy.deepcopy(gam_res_fx)
        bad_gam_2a["streakFreezesRemaining"] = 3
        assert list(gam_vals["gamification-summary-response"].iter_errors(bad_gam_2a)), "Expected rejection when streakFreezesRemaining=3"

        bad_gam_2b = copy.deepcopy(gam_res_fx)
        bad_gam_2b["streakFreezesRemaining"] = -1
        assert list(gam_vals["gamification-summary-response"].iter_errors(bad_gam_2b)), "Expected rejection when streakFreezesRemaining=-1"

        bad_gam_3 = copy.deepcopy(gam_req_fx)
        bad_gam_3["evidenceType"] = "UNSUPPORTED"
        assert list(gam_vals["gamification-activity-record-request"].iter_errors(bad_gam_3)), "Expected rejection when evidenceType=UNSUPPORTED"

        bad_gam_4 = copy.deepcopy(gam_req_fx)
        bad_gam_4["xpPoints"] = 100
        assert list(gam_vals["gamification-activity-record-request"].iter_errors(bad_gam_4)), "Expected rejection on extra property xpPoints"

        # Sprint 8.9 Draft 2020-12 positive & negative mutation checks
        s89_vals = {}
        for pair_name, (sc_obj, fx_obj) in s89_loaded_pairs.items():
            jsonschema.Draft202012Validator.check_schema(sc_obj)
            v = jsonschema.Draft202012Validator(sc_obj, format_checker=jsonschema.FormatChecker())
            v.validate(fx_obj)
            s89_vals[pair_name] = v

        _, s89_digest_fx = s89_loaded_pairs["weekly-digest-v1"]
        _, s89_share_fx = s89_loaded_pairs["weekly-share-packet-v1"]

        bad_s89_1 = copy.deepcopy(s89_digest_fx)
        bad_s89_1["training_eligible"] = True
        assert list(s89_vals["weekly-digest-v1"].iter_errors(bad_s89_1)), "Expected rejection when digest training_eligible=true"

        bad_s89_2 = copy.deepcopy(s89_digest_fx)
        bad_s89_2["weekIso"] = "2026-13"
        assert list(s89_vals["weekly-digest-v1"].iter_errors(bad_s89_2)), "Expected rejection when weekIso=2026-13"

        bad_s89_3 = copy.deepcopy(s89_digest_fx)
        bad_s89_3["recommendations"][0]["evidenceRefs"] = []
        assert list(s89_vals["weekly-digest-v1"].iter_errors(bad_s89_3)), "Expected rejection when recommendations[0].evidenceRefs is empty"

        bad_s89_4a = copy.deepcopy(s89_share_fx)
        bad_s89_4a["rawTutorTranscript"] = "leaked turn"
        assert list(s89_vals["weekly-share-packet-v1"].iter_errors(bad_s89_4a)), "Expected rejection on extra property rawTutorTranscript"

        bad_s89_4b = copy.deepcopy(s89_share_fx)
        bad_s89_4b["studentEmail"] = "student@example.com"
        assert list(s89_vals["weekly-share-packet-v1"].iter_errors(bad_s89_4b)), "Expected rejection on extra property studentEmail"

        bad_s89_5 = copy.deepcopy(s89_share_fx)
        bad_s89_5["privacyAttestation"]["rawTutorTranscriptsIncluded"] = True
        assert list(s89_vals["weekly-share-packet-v1"].iter_errors(bad_s89_5)), "Expected rejection when privacyAttestation.rawTutorTranscriptsIncluded=true"
    else:
        # Fallback pure-python verification of invariants
        assert sg_fixture["schemaVersion"] == "skill-graph-v1"
        assert vt_fixture["schemaVersion"] == "verified-twin-v1"
        assert vt_fixture["trainingEligible"] is False
        assert sq_fixture["schemaVersion"] == "1.0.0"
        assert len(sq_fixture["items"]) <= 3

    # Sprint 8.7 pure-python contract & negative-mutation checks (always verified)
    def _validate_ss_object_keys(obj: dict, sc: dict, defs: dict) -> list[str]:
        errs: list[str] = []
        if sc.get("additionalProperties") is False:
            extra = set(obj.keys()) - set(sc.get("properties", {}).keys())
            if extra:
                errs.append(f"Unexpected properties: {sorted(extra)}")
        for req in sc.get("required", []):
            if req not in obj:
                errs.append(f"Missing required property: {req}")
        for k, prop in sc.get("properties", {}).items():
            if k not in obj:
                continue
            val = obj[k]
            if "$ref" in prop and isinstance(val, dict):
                ref_name = prop["$ref"].split("/")[-1]
                errs.extend(_validate_ss_object_keys(val, defs[ref_name], defs))
                continue
            if "const" in prop and val is not prop["const"] and val != prop["const"]:
                errs.append(f"{k} != {prop['const']}")
            if prop.get("type") == "boolean" and not isinstance(val, bool):
                errs.append(f"{k} not bool")
            if prop.get("type") == "integer":
                if isinstance(val, bool) or not isinstance(val, int):
                    errs.append(f"{k} not int")
                else:
                    if "minimum" in prop and val < prop["minimum"]:
                        errs.append(f"{k} < {prop['minimum']}")
                    if "maximum" in prop and val > prop["maximum"]:
                        errs.append(f"{k} > {prop['maximum']}")
            if prop.get("type") == "string":
                if not isinstance(val, str):
                    errs.append(f"{k} not str")
                else:
                    if "enum" in prop and val not in prop["enum"]:
                        errs.append(f"{k} invalid enum {val}")
                    if "minLength" in prop and len(val) < prop["minLength"]:
                        errs.append(f"{k} shorter than {prop['minLength']}")
                    if "maxLength" in prop and len(val) > prop["maxLength"]:
                        errs.append(f"{k} longer than {prop['maxLength']}")
            if prop.get("type") == "array":
                if not isinstance(val, list):
                    errs.append(f"{k} not list")
                else:
                    if "minItems" in prop and len(val) < prop["minItems"]:
                        errs.append(f"{k} fewer than minItems")
                    if "maxItems" in prop and len(val) > prop["maxItems"]:
                        errs.append(f"{k} more than maxItems")
                    item_ref = prop.get("items", {}).get("$ref", "")
                    if item_ref:
                        ref_name = item_ref.split("/")[-1]
                        for item in val:
                            if isinstance(item, dict):
                                errs.extend(_validate_ss_object_keys(item, defs[ref_name], defs))
        return errs

    for sc, fx, name in (
        (ss_state_schema, ss_state_fixture, "study-session-state-v1"),
        (ss_start_req_schema, ss_start_req_fixture, "study-session-start-request-v1"),
        (ss_start_res_schema, ss_start_res_fixture, "study-session-start-response-v1"),
        (ss_ckpt_req_schema, ss_ckpt_req_fixture, "study-session-checkpoint-request-v1"),
        (ss_ckpt_res_schema, ss_ckpt_res_fixture, "study-session-checkpoint-response-v1"),
        (ss_comp_req_schema, ss_comp_req_fixture, "study-session-complete-request-v1"),
        (ss_comp_res_schema, ss_comp_res_fixture, "study-session-complete-response-v1"),
    ):
        errs = _validate_ss_object_keys(fx, sc, sc.get("$defs", {}))
        assert not errs, f"Pure-python Sprint 8.7 validation failed for {name}: {errs}"

    # Pure-python verification of the 6 required negative mutations
    assert _validate_ss_object_keys({**ss_state_fixture, "training_eligible": True}, ss_state_schema, ss_state_schema.get("$defs", {})), "Expected training_eligible=True failure"
    assert _validate_ss_object_keys({**ss_ckpt_req_fixture, "reported_active_duration_seconds": 14401}, ss_ckpt_req_schema, ss_ckpt_req_schema.get("$defs", {})), "Expected duration 14401 failure"
    assert _validate_ss_object_keys({**ss_ckpt_req_fixture, "reported_active_duration_seconds": -1}, ss_ckpt_req_schema, ss_ckpt_req_schema.get("$defs", {})), "Expected duration -1 failure"
    assert _validate_ss_object_keys({**ss_state_fixture, "state": "TERMINATED"}, ss_state_schema, ss_state_schema.get("$defs", {})), "Expected state=TERMINATED failure"
    assert _validate_ss_object_keys({**ss_start_req_fixture, "total_steps": 0}, ss_start_req_schema, ss_start_req_schema.get("$defs", {})), "Expected total_steps=0 failure"
    assert _validate_ss_object_keys({**ss_start_req_fixture, "total_steps": 51}, ss_start_req_schema, ss_start_req_schema.get("$defs", {})), "Expected total_steps=51 failure"
    assert _validate_ss_object_keys({**ss_ckpt_res_fixture, "unexpected_field": 123}, ss_ckpt_res_schema, ss_ckpt_res_schema.get("$defs", {})), "Expected unexpected_field failure"
    assert _validate_ss_object_keys({**ss_ckpt_req_fixture, "idempotency_key": "short"}, ss_ckpt_req_schema, ss_ckpt_req_schema.get("$defs", {})), "Expected short idempotency_key failure"

    # Sprint 8.8 pure-python contract & negative-mutation checks (always verified)
    def _validate_gam_object_keys(obj: dict, sc: dict, defs: dict) -> list[str]:
        errs: list[str] = []
        if sc.get("additionalProperties") is False:
            extra = set(obj.keys()) - set(sc.get("properties", {}).keys())
            if extra:
                errs.append(f"Unexpected properties: {sorted(extra)}")
        for req in sc.get("required", []):
            if req not in obj:
                errs.append(f"Missing required property: {req}")
        for k, prop in sc.get("properties", {}).items():
            if k not in obj:
                continue
            val = obj[k]
            if "const" in prop and (val is not prop["const"] and val != prop["const"]):
                errs.append(f"{k} != {prop['const']}")
            ptype = prop.get("type")
            if ptype == ["string", "null"]:
                if val is not None and not isinstance(val, str):
                    errs.append(f"{k} must be string or null")
                elif "enum" in prop and val not in prop["enum"]:
                    errs.append(f"{k} invalid enum {val}")
            elif ptype == "boolean" and not isinstance(val, bool):
                errs.append(f"{k} not bool")
            elif ptype == "integer":
                if isinstance(val, bool) or not isinstance(val, int):
                    errs.append(f"{k} not int")
                else:
                    if "minimum" in prop and val < prop["minimum"]:
                        errs.append(f"{k} < {prop['minimum']}")
                    if "maximum" in prop and val > prop["maximum"]:
                        errs.append(f"{k} > {prop['maximum']}")
            elif ptype == "string":
                if not isinstance(val, str):
                    errs.append(f"{k} not str")
                else:
                    if "enum" in prop and val not in prop["enum"]:
                        errs.append(f"{k} invalid enum {val}")
                    if "minLength" in prop and len(val) < prop["minLength"]:
                        errs.append(f"{k} shorter than {prop['minLength']}")
                    if "maxLength" in prop and len(val) > prop["maxLength"]:
                        errs.append(f"{k} longer than {prop['maxLength']}")
                    if "pattern" in prop and not re.match(prop["pattern"], val):
                        errs.append(f"{k} does not match pattern {prop['pattern']}")
            elif ptype == "array":
                if not isinstance(val, list):
                    errs.append(f"{k} not list")
                else:
                    if "minItems" in prop and len(val) < prop["minItems"]:
                        errs.append(f"{k} fewer than minItems")
                    if "maxItems" in prop and len(val) > prop["maxItems"]:
                        errs.append(f"{k} more than maxItems")
                    item_ref = prop.get("items", {}).get("$ref", "")
                    if item_ref:
                        ref_name = item_ref.split("/")[-1]
                        for item in val:
                            if isinstance(item, dict):
                                errs.extend(_validate_gam_object_keys(item, defs[ref_name], defs))
        return errs

    for pair_name, (sc_obj, fx_obj) in gam_loaded_pairs.items():
        errs = _validate_gam_object_keys(fx_obj, sc_obj, sc_obj.get("$defs", {}))
        assert not errs, f"Pure-python Sprint 8.8 validation failed for {pair_name}: {errs}"

    gam_req_sc, gam_req_fx = gam_loaded_pairs["gamification-activity-record-request"]
    gam_res_sc, gam_res_fx = gam_loaded_pairs["gamification-summary-response"]
    assert _validate_gam_object_keys({**gam_res_fx, "trainingEligible": True}, gam_res_sc, gam_res_sc.get("$defs", {})), "Expected trainingEligible=True failure"
    assert _validate_gam_object_keys({**gam_res_fx, "streakFreezesRemaining": 3}, gam_res_sc, gam_res_sc.get("$defs", {})), "Expected streakFreezesRemaining=3 failure"
    assert _validate_gam_object_keys({**gam_res_fx, "streakFreezesRemaining": -1}, gam_res_sc, gam_res_sc.get("$defs", {})), "Expected streakFreezesRemaining=-1 failure"
    assert _validate_gam_object_keys({**gam_req_fx, "evidenceType": "UNSUPPORTED"}, gam_req_sc, gam_req_sc.get("$defs", {})), "Expected evidenceType=UNSUPPORTED failure"
    assert _validate_gam_object_keys({**gam_req_fx, "xpPoints": 100}, gam_req_sc, gam_req_sc.get("$defs", {})), "Expected extra xpPoints=100 failure"

    # Sprint 8.9 pure-python contract & negative-mutation checks (always verified)
    def _validate_s89_object_keys(obj: dict, sc: dict, defs: dict) -> list[str]:
        errs: list[str] = []
        if sc.get("additionalProperties") is False:
            extra = set(obj.keys()) - set(sc.get("properties", {}).keys())
            if extra:
                errs.append(f"Unexpected properties: {sorted(extra)}")
        for req in sc.get("required", []):
            if req not in obj:
                errs.append(f"Missing required property: {req}")
        for k, prop in sc.get("properties", {}).items():
            if k not in obj:
                continue
            val = obj[k]
            if "anyOf" in prop:
                any_ok = False
                for branch in prop["anyOf"]:
                    if branch.get("type") == "null" and val is None:
                        any_ok = True
                        break
                    if "$ref" in branch and isinstance(val, dict):
                        ref_name = branch["$ref"].split("/")[-1]
                        if not _validate_s89_object_keys(val, defs[ref_name], defs):
                            any_ok = True
                            break
                    elif branch.get("type") == "array" and isinstance(val, list):
                        min_i = branch.get("minItems", 0)
                        max_i = branch.get("maxItems")
                        if len(val) >= min_i and (max_i is None or len(val) <= max_i):
                            any_ok = True
                            break
                    elif branch.get("type") == "string" and isinstance(val, str):
                        any_ok = True
                        break
                    elif branch.get("type") == "integer" and isinstance(val, int) and not isinstance(val, bool):
                        any_ok = True
                        break
                    elif branch.get("type") == "boolean" and isinstance(val, bool):
                        any_ok = True
                        break
                    elif branch.get("type") == "object" and isinstance(val, dict):
                        if not _validate_s89_object_keys(val, branch, defs):
                            any_ok = True
                            break
                if not any_ok:
                    errs.append(f"{k} does not satisfy anyOf in schema")
                continue
            if "$ref" in prop:
                ref_name = prop["$ref"].split("/")[-1]
                if not isinstance(val, dict):
                    errs.append(f"{k} expected object for $ref {ref_name}")
                else:
                    errs.extend(_validate_s89_object_keys(val, defs[ref_name], defs))
                continue
            if "const" in prop:
                expected_const = prop["const"]
                if isinstance(expected_const, bool):
                    if not isinstance(val, bool) or val is not expected_const:
                        errs.append(f"{k} != {expected_const}")
                elif isinstance(expected_const, int):
                    if isinstance(val, bool) or val != expected_const:
                        errs.append(f"{k} != {expected_const}")
                elif val != expected_const:
                    errs.append(f"{k} != {expected_const}")
            ptype = prop.get("type")
            if ptype == ["string", "null"]:
                if val is not None and not isinstance(val, str):
                    errs.append(f"{k} must be string or null")
                elif isinstance(val, str):
                    if "enum" in prop and val not in prop["enum"]:
                        errs.append(f"{k} invalid enum {val}")
                    if "minLength" in prop and len(val) < prop["minLength"]:
                        errs.append(f"{k} shorter than {prop['minLength']}")
                    if "maxLength" in prop and len(val) > prop["maxLength"]:
                        errs.append(f"{k} longer than {prop['maxLength']}")
            elif ptype == ["integer", "null"]:
                if val is not None and (isinstance(val, bool) or not isinstance(val, int)):
                    errs.append(f"{k} must be integer or null")
                elif isinstance(val, int):
                    if "minimum" in prop and val < prop["minimum"]:
                        errs.append(f"{k} < {prop['minimum']}")
                    if "maximum" in prop and val > prop["maximum"]:
                        errs.append(f"{k} > {prop['maximum']}")
            elif ptype == ["boolean", "null"]:
                if val is not None and not isinstance(val, bool):
                    errs.append(f"{k} must be boolean or null")
            elif ptype == ["number", "null"]:
                if val is not None and (isinstance(val, bool) or not isinstance(val, (int, float))):
                    errs.append(f"{k} must be number or null")
                elif val is not None and isinstance(val, (int, float)):
                    if "minimum" in prop and val < prop["minimum"]:
                        errs.append(f"{k} < {prop['minimum']}")
                    if "maximum" in prop and val > prop["maximum"]:
                        errs.append(f"{k} > {prop['maximum']}")
            elif ptype == "object":
                if not isinstance(val, dict):
                    errs.append(f"{k} not object")
                else:
                    errs.extend(_validate_s89_object_keys(val, prop, defs))
            elif ptype == "boolean":
                if not isinstance(val, bool):
                    errs.append(f"{k} not bool")
            elif ptype == "integer":
                if isinstance(val, bool) or not isinstance(val, int):
                    errs.append(f"{k} not int")
                else:
                    if "minimum" in prop and val < prop["minimum"]:
                        errs.append(f"{k} < {prop['minimum']}")
                    if "maximum" in prop and val > prop["maximum"]:
                        errs.append(f"{k} > {prop['maximum']}")
            elif ptype == "number":
                if isinstance(val, bool) or not isinstance(val, (int, float)):
                    errs.append(f"{k} not number")
                else:
                    if "minimum" in prop and val < prop["minimum"]:
                        errs.append(f"{k} < {prop['minimum']}")
                    if "maximum" in prop and val > prop["maximum"]:
                        errs.append(f"{k} > {prop['maximum']}")
            elif ptype == "string":
                if not isinstance(val, str):
                    errs.append(f"{k} not str")
                else:
                    if "enum" in prop and val not in prop["enum"]:
                        errs.append(f"{k} invalid enum {val}")
                    if "minLength" in prop and len(val) < prop["minLength"]:
                        errs.append(f"{k} shorter than {prop['minLength']}")
                    if "maxLength" in prop and len(val) > prop["maxLength"]:
                        errs.append(f"{k} longer than {prop['maxLength']}")
                    if "pattern" in prop and not re.match(prop["pattern"], val):
                        errs.append(f"{k} does not match pattern {prop['pattern']}")
            elif ptype == "array":
                if not isinstance(val, list):
                    errs.append(f"{k} not list")
                else:
                    if "minItems" in prop and len(val) < prop["minItems"]:
                        errs.append(f"{k} fewer than minItems")
                    if "maxItems" in prop and len(val) > prop["maxItems"]:
                        errs.append(f"{k} more than maxItems")
                    items_schema = prop.get("items", {})
                    item_ref = items_schema.get("$ref", "")
                    if item_ref:
                        ref_name = item_ref.split("/")[-1]
                        for item in val:
                            if not isinstance(item, dict):
                                errs.append(f"{k} item not object")
                            else:
                                errs.extend(_validate_s89_object_keys(item, defs[ref_name], defs))
                    elif items_schema.get("type") == "object":
                        for item in val:
                            if not isinstance(item, dict):
                                errs.append(f"{k} item not object")
                            else:
                                errs.extend(_validate_s89_object_keys(item, items_schema, defs))
                    elif items_schema.get("type") == "string":
                        for item in val:
                            if not isinstance(item, str):
                                errs.append(f"{k} item not str")
                            elif "minLength" in items_schema and len(item) < items_schema["minLength"]:
                                errs.append(f"{k} item shorter than {items_schema['minLength']}")
                            elif "enum" in items_schema and item not in items_schema["enum"]:
                                errs.append(f"{k} item invalid enum {item}")
        for clause in [*sc.get("allOf", []), sc]:
            if_block = clause.get("if", {})
            then_block = clause.get("then", {})
            if if_block and then_block:
                if_props = if_block.get("properties", {})
                matched = True
                for ik, iprop in if_props.items():
                    if "const" in iprop:
                        if obj.get(ik) != iprop["const"]:
                            matched = False
                            break
                if matched:
                    for req_k in then_block.get("required", []):
                        if req_k not in obj or obj[req_k] is None:
                            errs.append(f"Conditional required property missing or null: {req_k}")
                    for tk, tprop in then_block.get("properties", {}).items():
                        tval = obj.get(tk)
                        if tprop.get("type") == "string":
                            if not isinstance(tval, str):
                                errs.append(f"Conditional {tk} must be string")
                            elif "minLength" in tprop and len(tval) < tprop["minLength"]:
                                errs.append(f"Conditional {tk} shorter than {tprop['minLength']}")
        return errs

    for pair_name, (sc_obj, fx_obj) in s89_loaded_pairs.items():
        errs = _validate_s89_object_keys(fx_obj, sc_obj, sc_obj.get("$defs", {}))
        assert not errs, f"Pure-python Sprint 8.9 validation failed for {pair_name}: {errs}"

    s89_dig_sc, s89_dig_fx = s89_loaded_pairs["weekly-digest-v1"]
    s89_shr_sc, s89_shr_fx = s89_loaded_pairs["weekly-share-packet-v1"]
    assert _validate_s89_object_keys({**s89_dig_fx, "training_eligible": True}, s89_dig_sc, s89_dig_sc.get("$defs", {})), "Expected digest training_eligible=True failure"
    assert _validate_s89_object_keys({**s89_dig_fx, "weekIso": "2026-13"}, s89_dig_sc, s89_dig_sc.get("$defs", {})), "Expected weekIso=2026-13 failure"
    mut_empty_refs = copy.deepcopy(s89_dig_fx)
    mut_empty_refs["recommendations"][0]["evidenceRefs"] = []
    assert _validate_s89_object_keys(mut_empty_refs, s89_dig_sc, s89_dig_sc.get("$defs", {})), "Expected empty evidenceRefs failure"
    assert _validate_s89_object_keys({**s89_shr_fx, "rawTutorTranscript": "x"}, s89_shr_sc, s89_shr_sc.get("$defs", {})), "Expected extra rawTutorTranscript failure"
    assert _validate_s89_object_keys({**s89_shr_fx, "studentEmail": "x@example.com"}, s89_shr_sc, s89_shr_sc.get("$defs", {})), "Expected extra studentEmail failure"
    mut_attest = copy.deepcopy(s89_shr_fx)
    mut_attest["privacyAttestation"]["rawTutorTranscriptsIncluded"] = True
    assert _validate_s89_object_keys(mut_attest, s89_shr_sc, s89_shr_sc.get("$defs", {})), "Expected privacyAttestation.rawTutorTranscriptsIncluded=True failure"

    # Sprint 8.10 Cold-Start Calibration & Study-Plan Quality Gate Contracts
    schemas_dir = ROOT / "packages/schemas"
    s810_pairs = [
        (
            "cold-start-request-v1",
            schemas_dir / "studyplan/cold-start-request-v1.schema.json",
            schemas_dir / "studyplan/fixtures/cold-start-request-v1.valid.json",
        ),
        (
            "cold-start-v1",
            schemas_dir / "studyplan/cold-start-v1.schema.json",
            schemas_dir / "studyplan/fixtures/cold-start-v1.valid.json",
        ),
        (
            "study-plan-quality-v1",
            schemas_dir / "analytics/study-plan-quality-v1.schema.json",
            schemas_dir / "analytics/fixtures/study-plan-quality-v1.valid.json",
        ),
    ]
    s810_loaded: dict[str, tuple[dict, dict]] = {}
    for pair_name, schema_file, fixture_file in s810_pairs:
        assert schema_file.exists(), f"Missing schema file: {schema_file}"
        assert fixture_file.exists(), f"Missing fixture file: {fixture_file}"
        sc_obj = json.loads(schema_file.read_text(encoding="utf-8"))
        fx_obj = json.loads(fixture_file.read_text(encoding="utf-8"))
        if jsonschema is not None:
            jsonschema.Draft202012Validator.check_schema(sc_obj)
            jsonschema.Draft202012Validator(sc_obj).validate(fx_obj)
        errs = _validate_s89_object_keys(fx_obj, sc_obj, sc_obj.get("$defs", {}))
        assert not errs, f"Sprint 8.10 pure-python schema check failed for {pair_name}: {errs}"
        s810_loaded[pair_name] = (sc_obj, fx_obj)

    s810_req_sc, s810_req_fx = s810_loaded["cold-start-request-v1"]
    s810_res_sc, s810_res_fx = s810_loaded["cold-start-v1"]
    s810_qual_sc, s810_qual_fx = s810_loaded["study-plan-quality-v1"]

    s810_negative_cases = [
        (s810_req_sc, {**s810_req_fx, "locale": "de-DE"}, "cold-start request invalid locale"),
        (s810_req_sc, {**s810_req_fx, "unexpectedPii": "secret"}, "cold-start request additionalProperties"),
        (s810_req_sc, {**s810_req_fx, "responses": s810_req_fx["responses"][:2]}, "cold-start request <3 items"),
        (s810_res_sc, {**s810_res_fx, "trainingEligible": True}, "cold-start response trainingEligible=True"),
        (s810_res_sc, {**s810_res_fx, "llmTokensUsed": 5}, "cold-start response llmTokensUsed!=0"),
        (s810_res_sc, {**s810_res_fx, "totalItems": 4}, "cold-start response totalItems!=3"),
        (s810_res_sc, {**s810_res_fx, "studentEmail": "x@example.com"}, "cold-start response extra PII field"),
        (s810_res_sc, {**s810_res_fx, "detectedTrapArchetypes": ["UNLISTED_TRAP"]}, "cold-start response unknown trap enum"),
        (s810_qual_sc, {**s810_qual_fx, "trainingEligible": True}, "quality report trainingEligible=True"),
        (s810_qual_sc, {**s810_qual_fx, "llmTokensUsed": 1}, "quality report llmTokensUsed!=0"),
        (s810_qual_sc, {**s810_qual_fx, "overallStatus": "SKIP"}, "quality report invalid overallStatus"),
        (s810_qual_sc, {**s810_qual_fx, "rawLearnerId": "abc"}, "quality report extra rawLearnerId"),
    ]
    for neg_sc, neg_payload, label in s810_negative_cases:
        if jsonschema is not None:
            try:
                jsonschema.Draft202012Validator(neg_sc).validate(neg_payload)
                raise AssertionError(f"Expected jsonschema failure for {label}")
            except jsonschema.exceptions.ValidationError:
                pass
        assert _validate_s89_object_keys(neg_payload, neg_sc, neg_sc.get("$defs", {})), f"Expected pure-python failure for {label}"

    # Sprint 9.1 Exam Definitions, Curriculum Mapping & Versioning Contracts
    s91_schema_files = {
        "exam-catalog": schemas_dir / "exam/v1/exam-definition-catalog-response.schema.json",
        "exam-detail": schemas_dir / "exam/v1/exam-definition-detail-response.schema.json",
        "exam-target-request": schemas_dir / "exam/v1/upsert-student-exam-target-request.schema.json",
        "exam-target-response": schemas_dir / "exam/v1/student-exam-target-response.schema.json",
        "exam-target-list": schemas_dir / "exam/v1/student-exam-target-list-response.schema.json",
    }
    s91_fixture_bindings = [
        ("exam-catalog", schemas_dir / "exam/v1/fixtures/exam-catalog-response.fixture.json"),
        ("exam-detail", schemas_dir / "exam/v1/fixtures/exam-detail-tyt-math.fixture.json"),
        ("exam-detail", schemas_dir / "exam/v1/fixtures/exam-detail-ayt-math.fixture.json"),
        ("exam-detail", schemas_dir / "exam/v1/fixtures/exam-detail-sat-math.fixture.json"),
        ("exam-target-request", schemas_dir / "exam/v1/fixtures/upsert-student-exam-target-request.fixture.json"),
        ("exam-target-response", schemas_dir / "exam/v1/fixtures/student-exam-target-response.fixture.json"),
        ("exam-target-list", schemas_dir / "exam/v1/fixtures/student-exam-target-list-response.fixture.json"),
    ]
    s91_schemas: dict[str, dict] = {}
    s91_fixtures: dict[str, dict] = {}
    for key, schema_file in s91_schema_files.items():
        assert schema_file.exists(), f"Missing Sprint 9.1 schema file: {schema_file}"
        sc_obj = json.loads(schema_file.read_text(encoding="utf-8"))
        assert sc_obj.get("$schema") == "https://json-schema.org/draft/2020-12/schema", f"Non-2020-12 schema: {schema_file}"
        assert sc_obj.get("additionalProperties") is False, f"additionalProperties must be false on {schema_file}"
        if jsonschema is not None:
            jsonschema.Draft202012Validator.check_schema(sc_obj)
        s91_schemas[key] = sc_obj

    for schema_key, fixture_file in s91_fixture_bindings:
        assert fixture_file.exists(), f"Missing Sprint 9.1 fixture file: {fixture_file}"
        sc_obj = s91_schemas[schema_key]
        fx_obj = json.loads(fixture_file.read_text(encoding="utf-8"))
        if jsonschema is not None:
            jsonschema.Draft202012Validator(sc_obj).validate(fx_obj)
        errs = _validate_s89_object_keys(fx_obj, sc_obj, sc_obj.get("$defs", {}))
        assert not errs, f"Sprint 9.1 pure-python schema check failed for {fixture_file.name}: {errs}"
        s91_fixtures[fixture_file.name] = fx_obj

    s91_catalog_sc = s91_schemas["exam-catalog"]
    s91_catalog_fx = s91_fixtures["exam-catalog-response.fixture.json"]
    s91_detail_sc = s91_schemas["exam-detail"]
    s91_detail_fx = s91_fixtures["exam-detail-tyt-math.fixture.json"]
    s91_req_sc = s91_schemas["exam-target-request"]
    s91_req_fx = s91_fixtures["upsert-student-exam-target-request.fixture.json"]
    s91_res_sc = s91_schemas["exam-target-response"]
    s91_res_fx = s91_fixtures["student-exam-target-response.fixture.json"]
    s91_list_sc = s91_schemas["exam-target-list"]
    s91_list_fx = s91_fixtures["student-exam-target-list-response.fixture.json"]

    mut_bad_jurisdiction = copy.deepcopy(s91_detail_fx)
    mut_bad_jurisdiction["jurisdiction"] = "MARS"
    s91_negative_cases = [
        (s91_catalog_sc, {**s91_catalog_fx, "trainingEligible": True}, "exam catalog trainingEligible=True"),
        (s91_catalog_sc, {**s91_catalog_fx, "llmTokensUsed": 5}, "exam catalog llmTokensUsed!=0"),
        (s91_detail_sc, mut_bad_jurisdiction, "exam detail unknown jurisdiction"),
        (s91_detail_sc, {**s91_detail_fx, "blueprintHash": "a" * 63}, "exam detail invalid 63-char blueprintHash"),
        (s91_detail_sc, {**s91_detail_fx, "llmTokensUsed": 2}, "exam detail llmTokensUsed!=0"),
        (s91_req_sc, {**s91_req_fx, "targetScore": -150.0}, "exam target request targetScore < -100"),
        (s91_req_sc, {**s91_req_fx, "studentEmail": "x@example.com"}, "exam target request extra studentEmail PII"),
        (s91_res_sc, {**s91_res_fx, "trainingEligible": True}, "exam target response trainingEligible=True"),
        (s91_res_sc, {**s91_res_fx, "llmTokensUsed": 9}, "exam target response llmTokensUsed!=0"),
        (s91_list_sc, {**s91_list_fx, "trainingEligible": True}, "exam target list trainingEligible=True"),
    ]
    for neg_sc, neg_payload, label in s91_negative_cases:
        if jsonschema is not None:
            try:
                jsonschema.Draft202012Validator(neg_sc).validate(neg_payload)
                raise AssertionError(f"Expected jsonschema failure for {label}")
            except jsonschema.exceptions.ValidationError:
                pass
        assert _validate_s89_object_keys(neg_payload, neg_sc, neg_sc.get("$defs", {})), f"Expected pure-python failure for {label}"

    # Sprint 9.2 Learner Exam Goal Setup, Candidate Profile & Snap-to-Plan Syllabus Contracts
    s92_schema_files = {
        "candidate-profile": schemas_dir / "exam/v1/candidate-profile.schema.json",
        "candidate-profile-request": schemas_dir / "exam/v1/upsert-candidate-profile-request.schema.json",
        "candidate-profile-history": schemas_dir / "exam/v1/candidate-profile-history-response.schema.json",
        "syllabus-ingestion": schemas_dir / "exam/v1/syllabus-ingestion.schema.json",
        "syllabus-ingest-json": schemas_dir / "exam/v1/syllabus-ingest-json-request.schema.json",
        "syllabus-confirm": schemas_dir / "exam/v1/syllabus-confirm-request.schema.json",
    }
    s92_fixture_bindings = [
        ("candidate-profile", schemas_dir / "exam/v1/fixtures/candidate-profile-yks-tyt.example.json"),
        ("candidate-profile-request", schemas_dir / "exam/v1/fixtures/upsert-candidate-profile-request.fixture.json"),
        ("candidate-profile-history", schemas_dir / "exam/v1/fixtures/candidate-profile-history-response.fixture.json"),
        ("syllabus-ingestion", schemas_dir / "exam/v1/fixtures/syllabus-ingestion-yks-tyt.example.json"),
        ("syllabus-confirm", schemas_dir / "exam/v1/fixtures/syllabus-confirm-request.fixture.json"),
    ]
    s92_schemas: dict[str, dict] = {}
    s92_fixtures: dict[str, dict] = {}
    for key, schema_file in s92_schema_files.items():
        assert schema_file.exists(), f"Missing Sprint 9.2 schema file: {schema_file}"
        sc_obj = json.loads(schema_file.read_text(encoding="utf-8"))
        assert sc_obj.get("$schema") == "https://json-schema.org/draft/2020-12/schema", f"Non-2020-12 schema: {schema_file}"
        assert sc_obj.get("additionalProperties") is False, f"additionalProperties must be false on {schema_file}"
        if jsonschema is not None:
            jsonschema.Draft202012Validator.check_schema(sc_obj)
        s92_schemas[key] = sc_obj

    for schema_key, fixture_file in s92_fixture_bindings:
        assert fixture_file.exists(), f"Missing Sprint 9.2 fixture file: {fixture_file}"
        sc_obj = s92_schemas[schema_key]
        fx_obj = json.loads(fixture_file.read_text(encoding="utf-8"))
        if jsonschema is not None:
            jsonschema.Draft202012Validator(sc_obj).validate(fx_obj)
        errs = _validate_s89_object_keys(fx_obj, sc_obj, sc_obj.get("$defs", {}))
        assert not errs, f"Sprint 9.2 pure-python schema check failed for {fixture_file.name}: {errs}"
        s92_fixtures[fixture_file.name] = fx_obj

    s92_prof_sc = s92_schemas["candidate-profile"]
    s92_prof_fx = s92_fixtures["candidate-profile-yks-tyt.example.json"]
    s92_req_sc = s92_schemas["candidate-profile-request"]
    s92_req_fx = s92_fixtures["upsert-candidate-profile-request.fixture.json"]
    s92_hist_sc = s92_schemas["candidate-profile-history"]
    s92_hist_fx = s92_fixtures["candidate-profile-history-response.fixture.json"]
    s92_syl_sc = s92_schemas["syllabus-ingestion"]
    s92_syl_fx = s92_fixtures["syllabus-ingestion-yks-tyt.example.json"]
    s92_conf_sc = s92_schemas["syllabus-confirm"]
    s92_conf_fx = s92_fixtures["syllabus-confirm-request.fixture.json"]

    mut_bad_acc = copy.deepcopy(s92_prof_fx)
    mut_bad_acc["accommodations"] = {**mut_bad_acc["accommodations"], "extraTimeMultiplier": 1.75}
    mut_bad_mime = copy.deepcopy(s92_syl_fx)
    mut_bad_mime["mimeType"] = "text/html"
    mut_big_byte = copy.deepcopy(s92_syl_fx)
    mut_big_byte["byteSize"] = 10485761

    s92_negative_cases = [
        (s92_prof_sc, {**s92_prof_fx, "trainingEligible": True}, "candidate profile trainingEligible=True"),
        (s92_prof_sc, {**s92_prof_fx, "llmTokensUsed": 3}, "candidate profile llmTokensUsed!=0"),
        (s92_prof_sc, {**s92_prof_fx, "dailyStudyMinutes": 10}, "candidate profile dailyStudyMinutes < 15"),
        (s92_prof_sc, {**s92_prof_fx, "feasibilityStatus": "IMPOSSIBLE"}, "candidate profile unknown feasibilityStatus"),
        (s92_prof_sc, mut_bad_acc, "candidate profile invalid extraTimeMultiplier"),
        (s92_prof_sc, {**s92_prof_fx, "studentEmail": "x@example.com"}, "candidate profile extra PII property"),
        (s92_req_sc, {**s92_req_fx, "dailyStudyMinutes": 1000}, "candidate profile request dailyStudyMinutes > 960"),
        (s92_hist_sc, {**s92_hist_fx, "trainingEligible": True}, "candidate history trainingEligible=True"),
        (s92_syl_sc, {**s92_syl_fx, "trainingEligible": True}, "syllabus ingestion trainingEligible=True"),
        (s92_syl_sc, mut_bad_mime, "syllabus ingestion text/html mimeType"),
        (s92_syl_sc, mut_big_byte, "syllabus ingestion byteSize > 10MB"),
        (s92_syl_sc, {**s92_syl_fx, "extraField": "nope"}, "syllabus ingestion additionalProperties"),
        (s92_conf_sc, {**s92_conf_fx, "mappings": []}, "syllabus confirm empty mappings"),
    ]
    for neg_sc, neg_payload, label in s92_negative_cases:
        if jsonschema is not None:
            try:
                jsonschema.Draft202012Validator(neg_sc).validate(neg_payload)
                raise AssertionError(f"Expected jsonschema failure for {label}")
            except jsonschema.exceptions.ValidationError:
                pass
        if isinstance(neg_payload, dict) and label not in (
            "candidate profile invalid extraTimeMultiplier",
            "syllabus ingestion text/html mimeType",
        ):
            assert _validate_s89_object_keys(neg_payload, neg_sc, neg_sc.get("$defs", {})), f"Expected pure-python failure for {label}"

    # Sprint 9.3 Question Bank, Blueprint, and Assessment Assembly Rules Contracts
    s93_schema_files = {
        "qbank-create": schemas_dir / "exam/v1/question-bank-item-create-request.schema.json",
        "qbank-response": schemas_dir / "exam/v1/question-bank-item-response.schema.json",
        "blueprint-create": schemas_dir / "exam/v1/assessment-blueprint-create-request.schema.json",
        "blueprint-response": schemas_dir / "exam/v1/assessment-blueprint-response.schema.json",
        "assembly-request": schemas_dir / "exam/v1/assessment-assembly-request.schema.json",
        "assembled-response": schemas_dir / "exam/v1/assembled-assessment-response.schema.json",
    }
    s93_fixture_bindings = [
        ("qbank-create", schemas_dir / "exam/v1/fixtures/question-bank-item-create-request.valid.json"),
        ("qbank-response", schemas_dir / "exam/v1/fixtures/question-bank-item-response.valid.json"),
        ("blueprint-create", schemas_dir / "exam/v1/fixtures/assessment-blueprint-create-request.valid.json"),
        ("blueprint-response", schemas_dir / "exam/v1/fixtures/assessment-blueprint-response.valid.json"),
        ("assembly-request", schemas_dir / "exam/v1/fixtures/assessment-assembly-request.valid.json"),
        ("assembled-response", schemas_dir / "exam/v1/fixtures/assembled-assessment-response.valid.json"),
    ]
    s93_schemas: dict[str, dict] = {}
    s93_fixtures: dict[str, dict] = {}
    for key, schema_file in s93_schema_files.items():
        assert schema_file.exists(), f"Missing Sprint 9.3 schema file: {schema_file}"
        sc_obj = json.loads(schema_file.read_text(encoding="utf-8"))
        assert sc_obj.get("$schema") == "https://json-schema.org/draft/2020-12/schema", f"Non-2020-12 schema: {schema_file}"
        assert sc_obj.get("additionalProperties") is False, f"additionalProperties must be false on {schema_file}"
        if jsonschema is not None:
            jsonschema.Draft202012Validator.check_schema(sc_obj)
        s93_schemas[key] = sc_obj

    for schema_key, fixture_file in s93_fixture_bindings:
        assert fixture_file.exists(), f"Missing Sprint 9.3 fixture file: {fixture_file}"
        sc_obj = s93_schemas[schema_key]
        fx_obj = json.loads(fixture_file.read_text(encoding="utf-8"))
        if jsonschema is not None:
            jsonschema.Draft202012Validator(sc_obj).validate(fx_obj)
        errs = _validate_s89_object_keys(fx_obj, sc_obj, sc_obj.get("$defs", {}))
        assert not errs, f"Sprint 9.3 pure-python schema check failed for {fixture_file.name}: {errs}"
        s93_fixtures[fixture_file.name] = fx_obj

    s93_qc_sc = s93_schemas["qbank-create"]
    s93_qc_fx = s93_fixtures["question-bank-item-create-request.valid.json"]
    s93_qr_sc = s93_schemas["qbank-response"]
    s93_qr_fx = s93_fixtures["question-bank-item-response.valid.json"]
    s93_bc_sc = s93_schemas["blueprint-create"]
    s93_bc_fx = s93_fixtures["assessment-blueprint-create-request.valid.json"]
    s93_br_sc = s93_schemas["blueprint-response"]
    s93_br_fx = s93_fixtures["assessment-blueprint-response.valid.json"]
    s93_ar_sc = s93_schemas["assembly-request"]
    s93_ar_fx = s93_fixtures["assessment-assembly-request.valid.json"]
    s93_as_sc = s93_schemas["assembled-response"]
    s93_as_fx = s93_fixtures["assembled-assessment-response.valid.json"]

    mut_visual_no_alt = copy.deepcopy(s93_qc_fx)
    mut_visual_no_alt["hasVisualDiagram"] = True
    mut_visual_no_alt["altTextDescription"] = "short"

    mut_leaked_answer = copy.deepcopy(s93_as_fx)
    mut_leaked_answer["items"] = copy.deepcopy(s93_as_fx["items"])
    mut_leaked_answer["items"][0]["isCorrect"] = True

    s93_negative_cases = [
        (s93_qc_sc, {**s93_qc_fx, "trainingEligible": True}, "qbank create trainingEligible=True"),
        (s93_qc_sc, {**s93_qc_fx, "difficultyBValue": 4.25}, "qbank create difficultyBValue > 3.0"),
        (s93_qc_sc, mut_visual_no_alt, "qbank create hasVisualDiagram=True with short altTextDescription"),
        (s93_qc_sc, {**s93_qc_fx, "unexpectedField": "x"}, "qbank create additionalProperties"),
        (s93_qr_sc, {**s93_qr_fx, "trainingEligible": True}, "qbank response trainingEligible=True"),
        (s93_qr_sc, {**s93_qr_fx, "contentSha256": "invalid-sha"}, "qbank response malformed contentSha256"),
        (s93_bc_sc, {**s93_bc_fx, "totalQuestionCount": 3}, "blueprint create totalQuestionCount < 5"),
        (s93_bc_sc, {**s93_bc_fx, "topicRules": []}, "blueprint create empty topicRules"),
        (s93_br_sc, {**s93_br_fx, "blueprintHash": "12345"}, "blueprint response malformed blueprintHash"),
        (s93_ar_sc, {**s93_ar_fx, "assemblySeed": "abc"}, "assembly request assemblySeed < 4 chars"),
        (s93_ar_sc, {**s93_ar_fx, "overrideTimeMultiplier": 2.50}, "assembly request overrideTimeMultiplier > 2.0"),
        (s93_as_sc, {**s93_as_fx, "trainingEligible": True}, "assembled response trainingEligible=True"),
        (s93_as_sc, {**s93_as_fx, "reproducibilityHash": "bad-hash"}, "assembled response malformed reproducibilityHash"),
        (s93_as_sc, mut_leaked_answer, "assembled response leaked answer key property on item"),
    ]
    for neg_sc, neg_payload, label in s93_negative_cases:
        if jsonschema is not None:
            try:
                jsonschema.Draft202012Validator(neg_sc).validate(neg_payload)
                raise AssertionError(f"Expected jsonschema failure for {label}")
            except jsonschema.exceptions.ValidationError:
                pass
        assert _validate_s89_object_keys(neg_payload, neg_sc, neg_sc.get("$defs", {})), f"Expected pure-python failure for {label}"

    # Sprint 9.4 (CNT-LOS-10 / INV-EXAM-003 / F28 Timed Mock Exam Runtime) schema & fixture checks
    s94_schema_dir = Path(__file__).resolve().parents[2] / "packages" / "schemas" / "exam" / "v1"
    s94_fixtures_dir = s94_schema_dir / "fixtures"
    s94_schema_files = [
        "exam-session-start-request.schema.json",
        "exam-session-response.schema.json",
        "exam-session-item-autosave-request.schema.json",
        "exam-session-item-autosave-response.schema.json",
        "exam-scratchpad-upload-request.schema.json",
        "exam-scratchpad-upload-response.schema.json",
        "exam-session-submit-request.schema.json",
        "exam-session-submit-response.schema.json",
        "exam-no-hint-guard-response.schema.json",
    ]
    s94_schemas = {}
    for fname in s94_schema_files:
        spath = s94_schema_dir / fname
        assert spath.exists(), f"Missing Sprint 9.4 schema: {spath}"
        sc = json.loads(spath.read_text(encoding="utf-8"))
        if jsonschema is not None:
            jsonschema.Draft202012Validator.check_schema(sc)
        s94_schemas[fname] = sc

    s94_fixture_bindings = [
        ("exam-session-start-request.schema.json", "exam-session-start-request.valid.json"),
        ("exam-session-response.schema.json", "exam-session-response.valid.json"),
        ("exam-session-item-autosave-request.schema.json", "exam-session-item-autosave-request.valid.json"),
        ("exam-session-item-autosave-response.schema.json", "exam-session-item-autosave-response.valid.json"),
        ("exam-scratchpad-upload-request.schema.json", "exam-scratchpad-upload-request.valid.json"),
        ("exam-scratchpad-upload-response.schema.json", "exam-scratchpad-upload-response.valid.json"),
        ("exam-session-submit-request.schema.json", "exam-session-submit-request.valid.json"),
        ("exam-session-submit-response.schema.json", "exam-session-submit-response.valid.json"),
        ("exam-no-hint-guard-response.schema.json", "exam-no-hint-guard-response.valid.json"),
    ]
    s94_valid_fixtures = {}
    for sname, fixname in s94_fixture_bindings:
        fpath = s94_fixtures_dir / fixname
        assert fpath.exists(), f"Missing Sprint 9.4 fixture: {fpath}"
        payload = json.loads(fpath.read_text(encoding="utf-8"))
        sc = s94_schemas[sname]
        if jsonschema is not None:
            jsonschema.Draft202012Validator(sc).validate(payload)
        errs = _validate_s89_object_keys(payload, sc, sc.get("$defs", {}))
        assert not errs, f"Sprint 9.4 pure-Python check failed for {fixname}: {errs}"
        s94_valid_fixtures[sname] = payload

    def _s94_mut(sname: str, **updates):
        copy = json.loads(json.dumps(s94_valid_fixtures[sname]))
        for k, v in updates.items():
            if v is ...:
                copy.pop(k, None)
            else:
                copy[k] = v
        return copy

    s94_negative_cases = [
        (s94_schemas["exam-session-start-request.schema.json"], _s94_mut("exam-session-start-request.schema.json", assemblyId=...), "s94 start request missing assemblyId"),
        (s94_schemas["exam-session-start-request.schema.json"], _s94_mut("exam-session-start-request.schema.json", extraCheatField=True), "s94 start request additionalProperties"),
        (s94_schemas["exam-session-response.schema.json"], _s94_mut("exam-session-response.schema.json", status="HINT_UNLOCKED"), "s94 session response invalid status enum"),
        (s94_schemas["exam-session-item-autosave-request.schema.json"], _s94_mut("exam-session-item-autosave-request.schema.json", action="REQUEST_HINT"), "s94 autosave request invalid action enum"),
        (s94_schemas["exam-session-item-autosave-response.schema.json"], _s94_mut("exam-session-item-autosave-response.schema.json", clockSync=...), "s94 autosave response missing clockSync"),
        (s94_schemas["exam-scratchpad-upload-request.schema.json"], _s94_mut("exam-scratchpad-upload-request.schema.json", mimeType="application/pdf"), "s94 scratchpad request disallowed mimeType"),
        (s94_schemas["exam-scratchpad-upload-response.schema.json"], _s94_mut("exam-scratchpad-upload-response.schema.json", asset=...), "s94 scratchpad response missing asset"),
        (s94_schemas["exam-session-submit-request.schema.json"], _s94_mut("exam-session-submit-request.schema.json", submissionReason="INVALID_REASON"), "s94 submit request invalid reason enum"),
        (s94_schemas["exam-session-submit-response.schema.json"], _s94_mut("exam-session-submit-response.schema.json", f28VerificationBatchManifest=...), "s94 submit response missing F28 manifest"),
        (s94_schemas["exam-no-hint-guard-response.schema.json"], _s94_mut("exam-no-hint-guard-response.schema.json", status="INVALID_STATUS"), "s94 no-hint guard invalid status enum"),
    ]
    for neg_sc, neg_payload, label in s94_negative_cases:
        if jsonschema is not None:
            try:
                jsonschema.Draft202012Validator(neg_sc).validate(neg_payload)
                raise AssertionError(f"Expected jsonschema failure for {label}")
            except jsonschema.exceptions.ValidationError:
                pass
        assert _validate_s89_object_keys(neg_payload, neg_sc, neg_sc.get("$defs", {})), f"Expected pure-python failure for {label}"

    # Strict const=false / const=0 / const=true enforcement checks via Draft202012 or explicit invariant assertions
    if jsonschema is not None:
        s94_const_cases = [
            (s94_schemas["exam-session-start-request.schema.json"], _s94_mut("exam-session-start-request.schema.json", trainingEligible=True), "s94 start trainingEligible=true"),
            (s94_schemas["exam-session-response.schema.json"], _s94_mut("exam-session-response.schema.json", noHintPolicyEnforced=False), "s94 session noHintPolicyEnforced=false"),
            (s94_schemas["exam-session-response.schema.json"], _s94_mut("exam-session-response.schema.json", llmTokensUsed=1), "s94 session llmTokensUsed=1"),
            (s94_schemas["exam-no-hint-guard-response.schema.json"], _s94_mut("exam-no-hint-guard-response.schema.json", hintAllowed=True), "s94 no-hint hintAllowed=true"),
            (s94_schemas["exam-scratchpad-upload-request.schema.json"], _s94_mut("exam-scratchpad-upload-request.schema.json", sha256Checksum="short"), "s94 scratchpad bad sha256Checksum"),
        ]
        for neg_sc, neg_payload, label in s94_const_cases:
            try:
                jsonschema.Draft202012Validator(neg_sc).validate(neg_payload)
                raise AssertionError(f"Expected jsonschema failure for {label}")
            except jsonschema.exceptions.ValidationError:
                pass

    # Sprint 9.5 (Scoring Engine, ÖSYM Net Policy & Evidence Traceability) schema & fixture checks
    s95_schema_dir = Path(__file__).resolve().parents[2] / "packages" / "schemas" / "exam" / "v1"
    s95_fixtures_dir = s95_schema_dir / "fixtures"
    s95_schema_files = [
        "exam-score-summary-response.schema.json",
        "exam-score-regrade-request.schema.json",
        "exam-score-regrade-response.schema.json",
    ]
    s95_schemas = {}
    for fname in s95_schema_files:
        spath = s95_schema_dir / fname
        assert spath.exists(), f"Missing Sprint 9.5 schema: {spath}"
        sc = json.loads(spath.read_text(encoding="utf-8"))
        if jsonschema is not None:
            jsonschema.Draft202012Validator.check_schema(sc)
        s95_schemas[fname] = sc

    s95_fixture_bindings = [
        ("exam-score-summary-response.schema.json", "exam-score-summary-response.valid.json"),
        ("exam-score-regrade-request.schema.json", "exam-score-regrade-request.valid.json"),
        ("exam-score-regrade-response.schema.json", "exam-score-regrade-response.valid.json"),
    ]
    s95_valid_fixtures = {}
    for sname, fixname in s95_fixture_bindings:
        fpath = s95_fixtures_dir / fixname
        assert fpath.exists(), f"Missing Sprint 9.5 fixture: {fpath}"
        payload = json.loads(fpath.read_text(encoding="utf-8"))
        sc = s95_schemas[sname]
        if jsonschema is not None:
            jsonschema.Draft202012Validator(sc).validate(payload)
        errs = _validate_s89_object_keys(payload, sc, sc.get("$defs", {}))
        assert not errs, f"Sprint 9.5 pure-Python check failed for {fixname}: {errs}"
        s95_valid_fixtures[sname] = payload

    def _s95_mut(sname: str, **updates):
        copy = json.loads(json.dumps(s95_valid_fixtures[sname]))
        for k, v in updates.items():
            if v is ...:
                copy.pop(k, None)
            else:
                copy[k] = v
        return copy

    s95_negative_cases = [
        (s95_schemas["exam-score-summary-response.schema.json"], _s95_mut("exam-score-summary-response.schema.json", scoringFormulaType="INVALID_POLICY"), "s95 summary invalid scoringFormulaType"),
        (s95_schemas["exam-score-summary-response.schema.json"], _s95_mut("exam-score-summary-response.schema.json", itemEvaluations=...), "s95 summary missing itemEvaluations"),
        (s95_schemas["exam-score-summary-response.schema.json"], _s95_mut("exam-score-summary-response.schema.json", normalizedPercentageScore=125.0), "s95 summary out-of-range percentage"),
        (s95_schemas["exam-score-regrade-request.schema.json"], _s95_mut("exam-score-regrade-request.schema.json", reasonCode="UNKNOWN_REASON"), "s95 regrade request invalid reasonCode"),
        (s95_schemas["exam-score-regrade-request.schema.json"], _s95_mut("exam-score-regrade-request.schema.json", reasonDetail=...), "s95 regrade request missing reasonDetail"),
        (s95_schemas["exam-score-regrade-response.schema.json"], _s95_mut("exam-score-regrade-response.schema.json", regradeAudit=...), "s95 regrade response missing regradeAudit"),
        (s95_schemas["exam-score-regrade-response.schema.json"], _s95_mut("exam-score-regrade-response.schema.json", extraCheatField=True), "s95 regrade response additionalProperties"),
    ]
    for neg_sc, neg_payload, label in s95_negative_cases:
        if jsonschema is not None:
            try:
                jsonschema.Draft202012Validator(neg_sc).validate(neg_payload)
                raise AssertionError(f"Expected jsonschema failure for {label}")
            except jsonschema.exceptions.ValidationError:
                pass
        assert _validate_s89_object_keys(neg_payload, neg_sc, neg_sc.get("$defs", {})), f"Expected pure-python failure for {label}"

    if jsonschema is not None:
        s95_const_cases = [
            (s95_schemas["exam-score-summary-response.schema.json"], _s95_mut("exam-score-summary-response.schema.json", llmTokensUsed=5), "s95 summary non-zero llmTokensUsed"),
            (s95_schemas["exam-score-summary-response.schema.json"], _s95_mut("exam-score-summary-response.schema.json", inferenceCostUsd=0.05), "s95 summary non-zero inferenceCostUsd"),
            (s95_schemas["exam-score-summary-response.schema.json"], _s95_mut("exam-score-summary-response.schema.json", trainingEligible=True), "s95 summary trainingEligible=true"),
            (s95_schemas["exam-score-regrade-request.schema.json"], _s95_mut("exam-score-regrade-request.schema.json", trainingEligible=True), "s95 regrade request trainingEligible=true"),
            (s95_schemas["exam-score-regrade-response.schema.json"], _s95_mut("exam-score-regrade-response.schema.json", llmTokensUsed=2), "s95 regrade response non-zero llmTokensUsed"),
        ]
        for neg_sc, neg_payload, label in s95_const_cases:
            try:
                jsonschema.Draft202012Validator(neg_sc).validate(neg_payload)
                raise AssertionError(f"Expected jsonschema failure for {label}")
            except jsonschema.exceptions.ValidationError:
                pass

    # Sprint 9.6 (CNT-LOS-11 Exam Readiness Model, 90% Credible Interval & Active-Trap Penalty) schema & fixture checks
    s96_schema_dir = Path(__file__).resolve().parents[2] / "packages" / "schemas" / "exam" / "v1"
    s96_fixtures_dir = s96_schema_dir / "fixtures"
    s96_schema_files = [
        "exam-readiness-projection.schema.json",
        "exam-readiness-recompute-request.schema.json",
    ]
    s96_schemas = {}
    for fname in s96_schema_files:
        spath = s96_schema_dir / fname
        assert spath.exists(), f"Missing Sprint 9.6 schema: {spath}"
        sc = json.loads(spath.read_text(encoding="utf-8"))
        if jsonschema is not None:
            jsonschema.Draft202012Validator.check_schema(sc)
        s96_schemas[fname] = sc

    s96_fixture_bindings = [
        ("exam-readiness-projection.schema.json", "exam-readiness-projection-valid.json"),
        ("exam-readiness-projection.schema.json", "exam-readiness-projection-preliminary-valid.json"),
        ("exam-readiness-recompute-request.schema.json", "exam-readiness-recompute-request-valid.json"),
    ]
    s96_valid_fixtures = {}
    for sname, fixname in s96_fixture_bindings:
        fpath = s96_fixtures_dir / fixname
        assert fpath.exists(), f"Missing Sprint 9.6 fixture: {fpath}"
        payload = json.loads(fpath.read_text(encoding="utf-8"))
        sc = s96_schemas[sname]
        if jsonschema is not None:
            jsonschema.Draft202012Validator(sc).validate(payload)
        errs = _validate_s89_object_keys(payload, sc, sc.get("$defs", {}))
        assert not errs, f"Sprint 9.6 pure-Python check failed for {fixname}: {errs}"
        s96_valid_fixtures[fixname] = payload

    s96_proj_valid = s96_valid_fixtures["exam-readiness-projection-valid.json"]
    s96_proj_prelim = s96_valid_fixtures["exam-readiness-projection-preliminary-valid.json"]
    assert s96_proj_valid["estimatedNetLow"] <= s96_proj_valid["estimatedNetMid"] <= s96_proj_valid["estimatedNetHigh"]
    assert s96_proj_prelim["epistemicConfidence"] < 0.25
    assert s96_proj_prelim["confidenceTier"] == "PRELIMINARY"
    assert s96_proj_prelim["calibrationRequired"] is True
    assert s96_proj_prelim["recommendedDiagnosticQuestionCount"] >= 10

    def _s96_mut(fixname: str, **updates):
        copy = json.loads(json.dumps(s96_valid_fixtures[fixname]))
        for k, v in updates.items():
            if v is ...:
                copy.pop(k, None)
            else:
                copy[k] = v
        return copy

    s96_negative_cases = [
        (s96_schemas["exam-readiness-projection.schema.json"], _s96_mut("exam-readiness-projection-valid.json", netLostToTraps=-1.5), "s96 negative netLostToTraps"),
        (s96_schemas["exam-readiness-projection.schema.json"], _s96_mut("exam-readiness-projection-valid.json", confidenceTier="INVALID"), "s96 invalid confidenceTier"),
        (s96_schemas["exam-readiness-projection.schema.json"], _s96_mut("exam-readiness-projection-valid.json", epistemicConfidence=1.5), "s96 epistemicConfidence > 1"),
        (s96_schemas["exam-readiness-projection.schema.json"], _s96_mut("exam-readiness-projection-valid.json", unexpectedCheatField=True), "s96 projection additionalProperties"),
        (s96_schemas["exam-readiness-recompute-request.schema.json"], _s96_mut("exam-readiness-recompute-request-valid.json", examCode=...), "s96 recompute missing examCode"),
        (s96_schemas["exam-readiness-recompute-request.schema.json"], _s96_mut("exam-readiness-recompute-request-valid.json", extraField="x"), "s96 recompute additionalProperties"),
    ]
    for neg_sc, neg_payload, label in s96_negative_cases:
        if jsonschema is not None:
            try:
                jsonschema.Draft202012Validator(neg_sc).validate(neg_payload)
                raise AssertionError(f"Expected jsonschema failure for {label}")
            except jsonschema.exceptions.ValidationError:
                pass
        assert _validate_s89_object_keys(neg_payload, neg_sc, neg_sc.get("$defs", {})), f"Expected pure-python failure for {label}"

    if jsonschema is not None:
        s96_const_cases = [
            (s96_schemas["exam-readiness-projection.schema.json"], _s96_mut("exam-readiness-projection-valid.json", trainingEligible=True), "s96 projection trainingEligible=true"),
            (s96_schemas["exam-readiness-projection.schema.json"], _s96_mut("exam-readiness-projection-valid.json", llmTokensUsed=5), "s96 projection llmTokensUsed=5"),
            (s96_schemas["exam-readiness-projection.schema.json"], _s96_mut("exam-readiness-projection-valid.json", inferenceCostUsd=0.25), "s96 projection non-zero inferenceCostUsd"),
            (s96_schemas["exam-readiness-projection.schema.json"], _s96_mut("exam-readiness-projection-valid.json", credibleIntervalLevel=0.95), "s96 projection wrong credibleIntervalLevel"),
            (s96_schemas["exam-readiness-recompute-request.schema.json"], _s96_mut("exam-readiness-recompute-request-valid.json", trainingEligible=True), "s96 recompute trainingEligible=true"),
        ]
        for neg_sc, neg_payload, label in s96_const_cases:
            try:
                jsonschema.Draft202012Validator(neg_sc).validate(neg_payload)
                raise AssertionError(f"Expected jsonschema failure for {label}")
            except jsonschema.exceptions.ValidationError:
                pass
    for card_fx in (ml_card_tr_fixture, ml_card_en_fixture):
        assert card_fx["schemaVersion"] == "1.0.0"
        assert card_fx["verificationStatus"] == "VERIFIED"
        assert re.fullmatch(r"[0-9a-f]{64}", card_fx["verificationHash"])
        beats = (
            card_fx["coreRule"]["estimatedSeconds"],
            card_fx["brokenVsValidCounterexample"]["estimatedSeconds"],
            card_fx["interactiveQuickCheck"]["estimatedSeconds"],
        )
        assert all(15 <= b <= 60 for b in beats), f"Beat out of bounds: {beats}"
        assert card_fx["estimatedDurationSeconds"] == sum(beats)
        assert 60 <= card_fx["estimatedDurationSeconds"] <= 180
        assert len(card_fx["interactiveQuickCheck"]["options"]) in (3, 4)
    assert 1000 <= ml_req_fixture["dwellTimeMs"] <= 600000
    assert ml_res_fixture["trainingEligible"] is False
    assert 1000 <= ml_res_fixture["dwellTimeMs"] <= 600000

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
                "MicroLessonCardResponse",
                "MicroLessonCompleteRequest",
                "MicroLessonCompleteResponse",
            ):
                assert schema_name in spec_schemas, f"Missing component schema {schema_name} in {spec_name}"

            assert "/v1/micro-lessons/{mistakeCode}" in spec_paths, f"Missing /v1/micro-lessons/{{mistakeCode}} in {spec_name}"
            ml_get_op = spec_paths["/v1/micro-lessons/{mistakeCode}"]["get"]
            assert ml_get_op["security"] == [{"bearerAuth": []}], f"Security bearerAuth missing on getMicroLessonCard in {spec_name}"
            for code in ("200", "400", "401", "404", "422", "503"):
                assert code in ml_get_op["responses"], f"HTTP {code} missing from getMicroLessonCard in {spec_name}"

            assert "/v1/micro-lessons/{mistakeCode}/complete" in spec_paths, f"Missing /v1/micro-lessons/{{mistakeCode}}/complete in {spec_name}"
            ml_post_op = spec_paths["/v1/micro-lessons/{mistakeCode}/complete"]["post"]
            assert ml_post_op["security"] == [{"bearerAuth": []}], f"Security bearerAuth missing on completeMicroLesson in {spec_name}"
            for code in ("200", "400", "401", "404", "422", "503"):
                assert code in ml_post_op["responses"], f"HTTP {code} missing from completeMicroLesson in {spec_name}"

            # Sprint 8.7 OpenAPI paths & component schemas
            for schema_name in (
                "StudySessionState",
                "StudySessionStartRequest",
                "StudySessionStartResponse",
                "StudySessionCheckpointRequest",
                "StudySessionCheckpointResponse",
                "StudySessionPauseRequest",
                "StudySessionResumeRequest",
                "StudySessionCompleteRequest",
                "StudySessionCompleteResponse",
            ):
                assert schema_name in spec_schemas, f"Missing Sprint 8.7 component schema {schema_name} in {spec_name}"

            expected_ss_endpoints = {
                "/v1/study-sessions/start": ("post", ("201", "400", "401", "409", "422", "503")),
                "/v1/study-sessions/active": ("get", ("200", "401", "404", "503")),
                "/v1/study-sessions/{sessionId}": ("get", ("200", "401", "404", "503")),
                "/v1/study-sessions/{sessionId}/checkpoint": ("put", ("200", "400", "401", "404", "409", "422", "503")),
                "/v1/study-sessions/{sessionId}/pause": ("post", ("200", "400", "401", "404", "409", "503")),
                "/v1/study-sessions/{sessionId}/resume": ("post", ("200", "400", "401", "404", "409", "503")),
                "/v1/study-sessions/{sessionId}/complete": ("post", ("200", "400", "401", "404", "409", "422", "503")),
            }
            for ss_path, (ss_method, expected_codes) in expected_ss_endpoints.items():
                assert ss_path in spec_paths, f"Missing Sprint 8.7 path {ss_path} in {spec_name}"
                ss_op = spec_paths[ss_path][ss_method]
                assert ss_op["security"] == [{"bearerAuth": []}], f"Security bearerAuth missing on {ss_path} in {spec_name}"
                for code in expected_codes:
                    assert code in ss_op["responses"], f"HTTP {code} missing from {ss_path} in {spec_name}"

            # Sprint 8.8 OpenAPI paths & component schemas
            for schema_name in (
                "GamificationActivityRecordRequest",
                "GamificationSummaryResponse",
            ):
                assert schema_name in spec_schemas, f"Missing Sprint 8.8 component schema {schema_name} in {spec_name}"

            expected_gam_endpoints = {
                "/v1/gamification/summary": ("get", ("200", "400", "401", "403", "503")),
                "/v1/gamification/activity": ("post", ("200", "400", "401", "403", "422", "503")),
            }
            for gam_path, (gam_method, expected_codes) in expected_gam_endpoints.items():
                assert gam_path in spec_paths, f"Missing Sprint 8.8 path {gam_path} in {spec_name}"
                gam_op = spec_paths[gam_path][gam_method]
                assert gam_op["security"] == [{"bearerAuth": []}], f"Security bearerAuth missing on {gam_path} in {spec_name}"
                for code in expected_codes:
                    assert code in gam_op["responses"], f"HTTP {code} missing from {gam_path} in {spec_name}"

            # Sprint 8.9 OpenAPI paths & component schemas
            for schema_name in (
                "WeeklyDigestV1",
                "WeeklySharePacketV1",
                "WeeklySharePacketRequest",
            ):
                assert schema_name in spec_schemas, f"Missing Sprint 8.9 component schema {schema_name} in {spec_name}"

            expected_s89_endpoints = {
                "/v1/reports/weekly/{weekIso}": ("get", ("200", "400", "401", "403", "404", "503")),
                "/v1/reports/weekly/{weekIso}/share-packet": ("post", ("200", "400", "401", "403", "404", "503")),
            }
            for s89_path, (s89_method, expected_codes) in expected_s89_endpoints.items():
                assert s89_path in spec_paths, f"Missing Sprint 8.9 path {s89_path} in {spec_name}"
                s89_op = spec_paths[s89_path][s89_method]
                assert s89_op["security"] == [{"bearerAuth": []}], f"Security bearerAuth missing on {s89_path} in {spec_name}"
                for code in expected_codes:
                    assert code in s89_op["responses"], f"HTTP {code} missing from {s89_path} in {spec_name}"

            # Sprint 8.10 OpenAPI paths & component schemas
            for schema_name in (
                "ColdStartDiagnosticRequestV1",
                "ColdStartDiagnosticResultV1",
                "StudyPlanQualityGateReportV1",
            ):
                assert schema_name in spec_schemas, f"Missing Sprint 8.10 component schema {schema_name} in {spec_name}"

            expected_s810_endpoints = {
                ("/v1/study-plan/cold-start", "get"): ("200", "400", "401", "403", "404", "503"),
                ("/v1/study-plan/cold-start", "post"): ("200", "400", "401", "403", "422", "503"),
                ("/v1/admin/study-plan/quality-gate", "get"): ("200", "401", "403", "503"),
            }
            for (s810_path, s810_method), expected_codes in expected_s810_endpoints.items():
                assert s810_path in spec_paths, f"Missing Sprint 8.10 path {s810_path} in {spec_name}"
                assert s810_method in spec_paths[s810_path], f"Missing method {s810_method} on {s810_path} in {spec_name}"
                s810_op = spec_paths[s810_path][s810_method]
                assert s810_op["security"] == [{"bearerAuth": []}], f"Security bearerAuth missing on {s810_path} in {spec_name}"
                for code in expected_codes:
                    assert code in s810_op["responses"], f"HTTP {code} missing from {s810_path} {s810_method} in {spec_name}"

            # Sprint 9.1 OpenAPI paths & component schemas
            for schema_name in (
                "ExamDefinitionCatalogResponse",
                "ExamDefinitionDetailResponse",
                "UpsertStudentExamTargetRequest",
                "StudentExamTargetResponse",
                "StudentExamTargetListResponse",
            ):
                assert schema_name in spec_schemas, f"Missing Sprint 9.1 component schema {schema_name} in {spec_name}"

            expected_s91_endpoints = {
                ("/v1/exams/definitions", "get"): ("200", "400", "401", "403", "503"),
                ("/v1/exams/definitions/{examCode}", "get"): ("200", "400", "401", "403", "404", "503"),
                ("/v1/exams/definitions/{examCode}/versions/{examVersion}", "get"): ("200", "400", "401", "403", "404", "503"),
                ("/v1/exams/targets", "post"): ("200", "201", "400", "401", "403", "404", "409", "422", "503"),
                ("/v1/exams/targets", "get"): ("200", "400", "401", "403", "503"),
                ("/v1/exams/targets/{targetId}", "get"): ("200", "400", "401", "403", "404", "503"),
            }
            for (s91_path, s91_method), expected_codes in expected_s91_endpoints.items():
                assert s91_path in spec_paths, f"Missing Sprint 9.1 path {s91_path} in {spec_name}"
                assert s91_method in spec_paths[s91_path], f"Missing method {s91_method} on {s91_path} in {spec_name}"
                s91_op = spec_paths[s91_path][s91_method]
                assert s91_op["security"] == [{"bearerAuth": []}], f"Security bearerAuth missing on {s91_path} in {spec_name}"
                for code in expected_codes:
                    assert code in s91_op["responses"], f"HTTP {code} missing from {s91_path} {s91_method} in {spec_name}"

            # Sprint 9.2 OpenAPI paths & component schemas
            for schema_name in (
                "UpsertCandidateProfileRequest",
                "CandidateProfileResponse",
                "CandidateProfileHistoryResponse",
                "SyllabusIngestJsonRequest",
                "SyllabusIngestionResponse",
                "SyllabusConfirmRequest",
            ):
                assert schema_name in spec_schemas, f"Missing Sprint 9.2 component schema {schema_name} in {spec_name}"

            expected_s92_endpoints = {
                ("/v1/candidate/profile", "put"): ("200", "201", "400", "401", "403", "404", "422", "503"),
                ("/v1/candidate/profile", "post"): ("200", "201", "400", "401", "403", "404", "422", "503"),
                ("/v1/candidate/profile", "get"): ("200", "400", "401", "403", "404", "503"),
                ("/v1/candidate/profile/history", "get"): ("200", "400", "401", "403", "503"),
                ("/v1/candidate/syllabus-ingest", "post"): ("200", "201", "400", "401", "403", "404", "413", "415", "422", "429", "503"),
                ("/v1/candidate/syllabus-uploads/{uploadId}", "get"): ("200", "400", "401", "403", "404", "503"),
                ("/v1/candidate/syllabus-uploads/{uploadId}/confirm", "post"): ("200", "400", "401", "403", "404", "422", "503"),
            }
            for (s92_path, s92_method), expected_codes in expected_s92_endpoints.items():
                assert s92_path in spec_paths, f"Missing Sprint 9.2 path {s92_path} in {spec_name}"
                assert s92_method in spec_paths[s92_path], f"Missing method {s92_method} on {s92_path} in {spec_name}"
                s92_op = spec_paths[s92_path][s92_method]
                assert s92_op["security"] == [{"bearerAuth": []}], f"Security bearerAuth missing on {s92_path} in {spec_name}"
                for code in expected_codes:
                    assert code in s92_op["responses"], f"HTTP {code} missing from {s92_path} {s92_method} in {spec_name}"

            # Sprint 9.3 OpenAPI paths & component schemas
            for schema_name in (
                "QuestionBankItemCreateRequest",
                "QuestionBankItemResponse",
                "AssessmentBlueprintCreateRequest",
                "AssessmentBlueprintResponse",
                "AssessmentAssemblyRequest",
                "AssembledAssessmentResponse",
            ):
                assert schema_name in spec_schemas, f"Missing Sprint 9.3 component schema {schema_name} in {spec_name}"

            expected_s93_endpoints = {
                ("/v1/exams/{examDefinitionId}/question-bank/items", "post"): ("201", "400", "401", "403", "404", "409", "422", "503"),
                ("/v1/exams/{examDefinitionId}/question-bank/items", "get"): ("200", "400", "401", "403", "503"),
                ("/v1/exams/{examDefinitionId}/question-bank/items/{itemId}/publish", "post"): ("200", "400", "401", "403", "404", "422", "503"),
                ("/v1/exams/{examDefinitionId}/blueprints", "post"): ("201", "400", "401", "403", "404", "409", "422", "503"),
                ("/v1/exams/{examDefinitionId}/blueprints", "get"): ("200", "400", "401", "403", "503"),
                ("/v1/exams/{examDefinitionId}/blueprints/{blueprintId}/activate", "post"): ("200", "400", "401", "403", "404", "422", "503"),
                ("/v1/exams/{examDefinitionId}/assessments/assemble", "post"): ("200", "201", "400", "401", "403", "404", "422", "503"),
                ("/v1/exams/candidates/{userId}/assessments/{assessmentId}", "get"): ("200", "400", "401", "403", "404", "503"),
            }
            for (s93_path, s93_method), expected_codes in expected_s93_endpoints.items():
                assert s93_path in spec_paths, f"Missing Sprint 9.3 path {s93_path} in {spec_name}"
                assert s93_method in spec_paths[s93_path], f"Missing method {s93_method} on {s93_path} in {spec_name}"
                s93_op = spec_paths[s93_path][s93_method]
                assert s93_op["security"] == [{"bearerAuth": []}], f"Security bearerAuth missing on {s93_path} in {spec_name}"
                for code in expected_codes:
                    assert code in s93_op["responses"], f"HTTP {code} missing from {s93_path} {s93_method} in {spec_name}"

            expected_s94_endpoints = {
                ("/v1/exams/sessions", "post"): {"200", "201", "400", "401", "403", "404", "409", "422", "503"},
                ("/v1/exams/sessions/{sessionId}", "get"): {"200", "400", "401", "403", "404", "503"},
                ("/v1/exams/sessions/{sessionId}/items/{itemPosition}", "patch"): {"200", "400", "401", "403", "404", "409", "422", "503"},
                ("/v1/exams/sessions/{sessionId}/items/{itemPosition}/scratchpad", "post"): {"200", "201", "400", "401", "403", "404", "409", "422", "503"},
                ("/v1/exams/sessions/{sessionId}/submit", "post"): {"200", "400", "401", "403", "404", "409", "422", "503"},
                ("/v1/exams/sessions/{sessionId}/hint-guard-check", "post"): {"200", "400", "401", "403", "404", "409", "503"},
                ("/api/v1/exams/sessions", "post"): {"200", "201", "400", "401", "403", "404", "409", "422", "503"},
                ("/api/v1/exams/sessions/{sessionId}", "get"): {"200", "400", "401", "403", "404", "503"},
                ("/api/v1/exams/sessions/{sessionId}/items/{itemPosition}", "patch"): {"200", "400", "401", "403", "404", "409", "422", "503"},
                ("/api/v1/exams/sessions/{sessionId}/items/{itemPosition}/scratchpad", "post"): {"200", "201", "400", "401", "403", "404", "409", "422", "503"},
                ("/api/v1/exams/sessions/{sessionId}/submit", "post"): {"200", "400", "401", "403", "404", "409", "422", "503"},
                ("/api/v1/exams/sessions/{sessionId}/hint-guard-check", "post"): {"200", "400", "401", "403", "404", "409", "503"},
            }
            for (s94_path, s94_method), expected_codes in expected_s94_endpoints.items():
                assert s94_path in spec_paths, f"Missing Sprint 9.4 path {s94_path} in {spec_name}"
                assert s94_method in spec_paths[s94_path], f"Missing method {s94_method} on {s94_path} in {spec_name}"
                s94_op = spec_paths[s94_path][s94_method]
                assert s94_op["security"] == [{"bearerAuth": []}], f"Security bearerAuth missing on {s94_path} in {spec_name}"
                for code in expected_codes:
                    assert code in s94_op["responses"], f"HTTP {code} missing from {s94_path} {s94_method} in {spec_name}"

            expected_s95_endpoints = {
                ("/v1/exams/sessions/{sessionId}/score-summary", "post"): {"200", "400", "401", "403", "404", "409", "422", "503"},
                ("/v1/exams/sessions/{sessionId}/score-summary", "get"): {"200", "400", "401", "403", "404", "409", "503"},
                ("/v1/exams/sessions/{sessionId}/regrade", "post"): {"200", "400", "401", "403", "404", "409", "422", "503"},
                ("/v1/exams/sessions/{sessionId}/regrade-audits", "get"): {"200", "400", "401", "403", "404", "409", "503"},
                ("/api/v1/exams/sessions/{sessionId}/score-summary", "post"): {"200", "400", "401", "403", "404", "409", "422", "503"},
                ("/api/v1/exams/sessions/{sessionId}/score-summary", "get"): {"200", "400", "401", "403", "404", "409", "503"},
                ("/api/v1/exams/sessions/{sessionId}/regrade", "post"): {"200", "400", "401", "403", "404", "409", "422", "503"},
                ("/api/v1/exams/sessions/{sessionId}/regrade-audits", "get"): {"200", "400", "401", "403", "404", "409", "503"},
            }
            for (s95_path, s95_method), expected_codes in expected_s95_endpoints.items():
                assert s95_path in spec_paths, f"Missing Sprint 9.5 path {s95_path} in {spec_name}"
                assert s95_method in spec_paths[s95_path], f"Missing method {s95_method} on {s95_path} in {spec_name}"
                s95_op = spec_paths[s95_path][s95_method]
                assert s95_op["security"] == [{"bearerAuth": []}], f"Security bearerAuth missing on {s95_path} in {spec_name}"
                for code in expected_codes:
                    assert code in s95_op["responses"], f"HTTP {code} missing from {s95_path} {s95_method} in {spec_name}"

            # Sprint 9.6 OpenAPI paths & component schemas
            for schema_name in (
                "ExamReadinessProjectionResponse",
                "ExamReadinessRecomputeRequest",
                "ActiveTrapImpactItem",
                "SectionReadinessBreakdown",
                "ExamReadinessTopicAllocation",
            ):
                assert schema_name in spec_schemas, f"Missing Sprint 9.6 component schema {schema_name} in {spec_name}"

            expected_s96_endpoints = {
                ("/v1/exams/readiness", "get"): {"200", "400", "401", "403", "404", "503"},
                ("/api/v1/exams/readiness", "get"): {"200", "400", "401", "403", "404", "503"},
                ("/v1/exams/readiness/recompute", "post"): {"200", "400", "401", "403", "404", "422", "503"},
                ("/api/v1/exams/readiness/recompute", "post"): {"200", "400", "401", "403", "404", "422", "503"},
            }
            for (s96_path, s96_method), expected_codes in expected_s96_endpoints.items():
                assert s96_path in spec_paths, f"Missing Sprint 9.6 path {s96_path} in {spec_name}"
                assert s96_method in spec_paths[s96_path], f"Missing method {s96_method} on {s96_path} in {spec_name}"
                s96_op = spec_paths[s96_path][s96_method]
                assert s96_op["security"] == [{"bearerAuth": []}], f"Security bearerAuth missing on {s96_path} in {spec_name}"
                for code in expected_codes:
                    assert code in s96_op["responses"], f"HTTP {code} missing from {s96_path} {s96_method} in {spec_name}"

        assert openapi_path.read_bytes() == openapi_core_path.read_bytes(), "OpenAPI mirror public-api.yaml != verified-ai-core-api.yaml"
    else:
        openapi_bytes = openapi_path.read_bytes()
        assert openapi_bytes == openapi_core_path.read_bytes(), "OpenAPI mirror public-api.yaml != verified-ai-core-api.yaml"
        openapi_text = openapi_bytes.decode("utf-8")
        for token in (
            "/v1/gamification/summary:",
            "/v1/gamification/activity:",
            "GamificationActivityRecordRequest:",
            "GamificationSummaryResponse:",
            "GamificationSummaryResponse",
            "GamificationActivityRecordRequest",
            "/v1/reports/weekly/{weekIso}:",
            "/v1/reports/weekly/{weekIso}/share-packet:",
            "WeeklyDigestV1:",
            "WeeklySharePacketV1:",
            "WeeklySharePacketRequest:",
            "/v1/study-plan/cold-start:",
            "/v1/admin/study-plan/quality-gate:",
            "ColdStartDiagnosticRequestV1:",
            "ColdStartDiagnosticResultV1:",
            "StudyPlanQualityGateReportV1:",
            "/v1/exams/definitions:",
            "/v1/exams/definitions/{examCode}:",
            "/v1/exams/definitions/{examCode}/versions/{examVersion}:",
            "/v1/exams/targets:",
            "/v1/exams/targets/{targetId}:",
            "ExamDefinitionCatalogResponse:",
            "ExamDefinitionDetailResponse:",
            "UpsertStudentExamTargetRequest:",
            "StudentExamTargetResponse:",
            "StudentExamTargetListResponse:",
            "/v1/exams/{examDefinitionId}/question-bank/items:",
            "/v1/exams/{examDefinitionId}/question-bank/items/{itemId}/publish:",
            "/v1/exams/{examDefinitionId}/blueprints:",
            "/v1/exams/{examDefinitionId}/blueprints/{blueprintId}/activate:",
            "/v1/exams/{examDefinitionId}/assessments/assemble:",
            "/v1/exams/candidates/{userId}/assessments/{assessmentId}:",
            "QuestionBankItemCreateRequest:",
            "QuestionBankItemResponse:",
            "AssessmentBlueprintCreateRequest:",
            "AssessmentBlueprintResponse:",
            "AssessmentAssemblyRequest:",
            "AssembledAssessmentResponse:",
            "ProblemBadRequest",
            "ProblemUnauthorized",
            "ProblemForbidden",
            "ProblemNotFound",
            "ProblemUnprocessableEntity",
            "ProblemServiceUnavailable",
            "ExamSessionStartRequest",
            "ExamSessionResponse",
            "ExamSessionItemAutosaveRequest",
            "ExamSessionItemAutosaveResponse",
            "ExamScratchpadUploadRequest",
            "ExamScratchpadUploadResponse",
            "ExamSessionSubmitRequest",
            "ExamSessionSubmitResponse",
            "ExamNoHintGuardResponse",
            "ExamScoreSummaryResponse",
            "ExamScoreRegradeRequest",
            "ExamScoreRegradeResponse",
            "ExamScoreRegradeAuditListResponse",
            "/v1/exams/readiness:",
            "/api/v1/exams/readiness:",
            "/v1/exams/readiness/recompute:",
            "/api/v1/exams/readiness/recompute:",
            "ExamReadinessProjectionResponse",
            "ExamReadinessRecomputeRequest",
            "ActiveTrapImpactItem",
            "SectionReadinessBreakdown",
            "ExamReadinessTopicAllocation",
        ):
            assert token in openapi_text, f"Missing Sprint 8.8..9.6 OpenAPI token in public-api.yaml: {token}"

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
        "micro_lesson.header.focus_opportunity",
        "micro_lesson.header.duration_badge",
        "micro_lesson.beat1.tab_title",
        "micro_lesson.beat2.tab_title",
        "micro_lesson.beat2.common_trap_label",
        "micro_lesson.beat2.verified_step_label",
        "micro_lesson.beat2.counterexample_label",
        "micro_lesson.beat3.tab_title",
        "micro_lesson.beat3.submit_cta",
        "micro_lesson.beat3.success_feedback",
        "micro_lesson.beat3.retry_feedback",
        "micro_lesson.cta.back_to_twin",
        "micro_lesson.cta.next_beat",
        "micro_lesson.cta.prev_beat",
        "micro_lesson.accessibility.drawer_label",
        "micro_lesson.error.verification_unavailable",
        "micro_lesson.cta.open_lesson",
        "study_session.resume.banner_title",
        "study_session.resume.banner_subtitle",
        "study_session.resume.cta_continue",
        "study_session.resume.cta_start_fresh",
        "study_session.status.active",
        "study_session.status.paused",
        "study_session.status.completed",
        "study_session.offline.saved_locally",
        "study_session.offline.syncing",
        "study_session.offline.synced",
        "study_session.action.pause",
        "study_session.action.complete",
        "study_session.progress.step_format",
        "study_session.complete.summary",
        "gamification.streak.badge.days_format",
        "gamification.streak.badge.accessibility_label",
        "gamification.streak.badge.accessibility_hint",
        "gamification.streak.status.active_verified_today.headline",
        "gamification.streak.status.active_verified_today.body",
        "gamification.streak.status.continuity_preserved_today.headline",
        "gamification.streak.status.continuity_preserved_today.body",
        "gamification.streak.status.freeze_protected.headline",
        "gamification.streak.status.freeze_protected.body",
        "gamification.streak.status.at_risk_today.headline",
        "gamification.streak.status.at_risk_today.body",
        "gamification.streak.status.fresh_start_ready.headline",
        "gamification.streak.status.fresh_start_ready.body",
        "gamification.sheet.title",
        "gamification.sheet.freezes_remaining_format",
        "gamification.sheet.section.ledger",
        "gamification.sheet.section.achievements",
        "gamification.badge.first_independent_twin.title",
        "gamification.badge.first_independent_twin.description",
        "gamification.badge.three_day_independent_streak.title",
        "gamification.badge.three_day_independent_streak.description",
        "gamification.badge.seven_day_independent_streak.title",
        "gamification.badge.seven_day_independent_streak.description",
        "gamification.badge.misconception_overcome.title",
        "gamification.badge.misconception_overcome.description",
        "gamification.badge.retention_guardian.title",
        "gamification.badge.retention_guardian.description",
        "gamification.badge.concept_architect.title",
        "gamification.badge.concept_architect.description",
        "weekly.report.title",
        "weekly.report.entry.section",
        "weekly.report.entry.title",
        "weekly.report.entry.subtitle",
        "weekly.report.previous_week",
        "weekly.report.next_week",
        "weekly.report.loading",
        "weekly.report.retry",
        "weekly.report.close",
        "weekly.report.empty_week.badge",
        "weekly.report.section.summary",
        "weekly.report.section.wins",
        "weekly.report.section.momentum",
        "weekly.report.section.mistakes",
        "weekly.report.section.recommendations",
        "weekly.report.kpi.solved",
        "weekly.report.kpi.independent_twins",
        "weekly.report.kpi.study_minutes",
        "weekly.report.kpi.active_days",
        "weekly.recommendation.why_button",
        "weekly.mistake.none",
        "weekly.mistake.trend.improving",
        "weekly.mistake.trend.stable",
        "weekly.mistake.trend.gentle_focus",
        "weekly.share.card.title",
        "weekly.share.card.subtitle",
        "weekly.share.button.preview",
        "weekly.share.sheet.nav_title",
        "weekly.share.sheet.heading",
        "weekly.share.recipient_role",
        "weekly.share.role.tutor",
        "weekly.share.role.parent",
        "weekly.share.alias.placeholder",
        "weekly.share.button.generate_pdf",
        "weekly.share.button.share_sheet",
        "weekly.privacy.attestation.title",
        "weekly.privacy.included_summary",
        "weekly.privacy.excluded_raw",
        "cold_start.card.badge.exploration",
        "cold_start.card.badge.preliminary",
        "cold_start.card.badge.calibrated",
        "cold_start.card.badge.revalidation",
        "cold_start.card.title",
        "cold_start.card.subtitle",
        "cold_start.card.cta.start",
        "cold_start.card.cta.retake",
        "cold_start.card.cta.offline_pending",
        "cold_start.confidence.preliminary",
        "cold_start.confidence.calibrated",
        "cold_start.conflict.resolved",
        "cold_start.revalidation.banner",
        "cold_start.flow.title",
        "cold_start.flow.step_counter",
        "cold_start.flow.timer_remaining",
        "cold_start.flow.setup_title",
        "cold_start.flow.exam_target",
        "cold_start.flow.daily_budget",
        "cold_start.flow.modality",
        "cold_start.flow.modality_override",
        "cold_start.flow.button.begin",
        "cold_start.flow.button.next",
        "cold_start.flow.button.finish",
        "cold_start.flow.button.skip",
        "cold_start.flow.button.done",
        "cold_start.tier.FOUNDATION_SOLID",
        "cold_start.tier.CALIBRATED_MIXED",
        "cold_start.tier.FOUNDATION_REPAIR",
        "cold_start.modality.PRACTICE_FIRST",
        "cold_start.modality.SOCRATIC_COACH",
        "cold_start.modality.MIXED",
        "cold_start.modality.VIDEO_HINTS_OFF",
        "cold_start.item.DIAG_ITEM_1_NUMBER_SENSE.title",
        "cold_start.item.DIAG_ITEM_2_QUADRATIC_INEQUALITY.title",
        "cold_start.item.DIAG_ITEM_3_FUNCTION_COMPOSITION.title",
        "cold_start.result.header",
        "cold_start.result.priors_title",
        "cold_start.result.rationales_title",
        "cold_start.result.zero_cost_note",
        "cold_start.offline.saved_message",
        "cold_start.error.retry",
        "exam.catalog.title",
        "exam.catalog.subtitle",
        "exam.catalog.filter.all",
        "exam.catalog.filter.tr",
        "exam.catalog.filter.intl",
        "exam.catalog.offline_banner",
        "exam.catalog.feature_disabled",
        "exam.catalog.questions_count",
        "exam.catalog.minutes_count",
        "exam.catalog.topic_weights_title",
        "exam.catalog.hash_label",
        "exam.target.section_title",
        "exam.target.score_label",
        "exam.target.date_label",
        "exam.target.save_button",
        "exam.target.saving_button",
        "exam.target.saved_banner",
        "exam.target.active_badge",
        "exam.target.superseded_badge",
        "exam.target.readiness_gap_title",
        "exam.target.estimated_score_label",
        "exam.target.gap_points_label",
        "exam.target.zero_cost_note",
        "exam.target.score_out_of_range",
        "exam.target.keyboard_done",
        "exam.scoring.CORRECT_MINUS_QUARTER_WRONG",
        "exam.scoring.RAW_CORRECT_ONLY",
        "exam.scoring.WEIGHTED_SECTION_RUBRIC",
        "exam.error.version_conflict",
        "exam.error.forbidden",
        "exam.error.not_found",
        "exam.error.unexpected",
        "home.exam_catalog.card_title",
        "home.exam_catalog.card_subtitle",
        "home.exam_catalog.open_button",
        "exam.catalog.open_candidate_profile",
        "home.candidate_profile.card_title",
        "home.candidate_profile.card_subtitle",
        "home.candidate_profile.open_button",
        "exam.candidate.title",
        "exam.candidate.subtitle",
        "exam.candidate.goal_section",
        "exam.candidate.exam_picker",
        "exam.candidate.budget_section",
        "exam.candidate.daily_minutes_label",
        "exam.candidate.weekly_minutes_label",
        "exam.candidate.feasibility_title",
        "exam.candidate.feasibility.OPTIMAL",
        "exam.candidate.feasibility.STRETCH",
        "exam.candidate.feasibility.AT_RISK",
        "exam.candidate.feasibility.UNREALISTIC",
        "exam.candidate.accommodations_section",
        "exam.candidate.extra_time_label",
        "exam.candidate.high_contrast",
        "exam.candidate.screen_reader",
        "exam.candidate.reduced_motion",
        "exam.candidate.save_button",
        "exam.candidate.saved_banner",
        "exam.candidate.history_title",
        "exam.candidate.error.invalid_score",
        "exam.candidate.error.date_in_past",
        "exam.candidate.error.budget_invalid",
        "exam.candidate.error.accommodation_invalid",
        "exam.syllabus.open_sheet_button",
        "exam.syllabus.sheet_title",
        "exam.syllabus.close_button",
        "exam.syllabus.sheet_subtitle",
        "exam.syllabus.privacy_notice",
        "exam.syllabus.choose_photo_button",
        "exam.syllabus.choose_pdf_button",
        "exam.syllabus.parsed_topics_title",
        "exam.syllabus.disambiguate_prompt",
        "exam.syllabus.confirm_mappings_button",
        "exam.syllabus.status.needs_disambiguation",
        "exam.syllabus.status.auto_confirmed",
        "exam.syllabus.status.user_confirmed",
        "exam.syllabus.error.quota_exceeded",
        "exam.syllabus.error.media_unsupported",
        "exam.syllabus.error.disambiguation_required",
        "exam.assembly.title",
        "exam.assembly.blueprint.section_header",
        "exam.assembly.mode.diagnostic",
        "exam.assembly.mode.section_drill",
        "exam.assembly.mode.full_simulation",
        "exam.assembly.mode.topic_mastery",
        "exam.assembly.duration.base",
        "exam.assembly.duration.effective_accommodated",
        "exam.assembly.distribution.topics",
        "exam.assembly.distribution.difficulty",
        "exam.assembly.seed.label",
        "exam.assembly.reproducibility_hash.label",
        "exam.assembly.action.assemble",
        "exam.assembly.status.ready",
        "exam.assembly.offline.cached_banner",
        "exam.assembly.error.pool_insufficient",
        "exam.assembly.startTimedSessionButton",
        "exam.session.title",
        "exam.session.timer.remaining_label",
        "exam.session.timer.grace_buffer_active",
        "exam.session.timer.clock_skew_notice",
        "exam.session.no_hint.badge",
        "exam.session.no_hint.notice",
        "exam.session.navigator.title",
        "exam.session.navigator.filter_all",
        "exam.session.navigator.filter_flagged",
        "exam.session.navigator.filter_omitted",
        "exam.session.action.flag_review",
        "exam.session.action.clear_answer",
        "exam.session.action.previous_item",
        "exam.session.action.next_item",
        "exam.session.scratchpad.title",
        "exam.session.scratchpad.attach_button",
        "exam.session.scratchpad.queued_badge",
        "exam.session.action.submit_exam",
        "exam.session.submit.confirmation_title",
        "exam.session.submit.receipt_title",
        "exam.session.view_score_summary",
        "exam.score.title",
        "exam.score.action.close",
        "exam.score.action.clear_topic_filter",
        "exam.score.action.trigger_regrade",
        "exam.score.loading",
        "exam.score.offline_banner",
        "exam.score.net_score.label",
        "exam.score.raw_correct.label",
        "exam.score.offset.label",
        "exam.score.range.label",
        "exam.score.count.correct",
        "exam.score.count.wrong",
        "exam.score.count.blank",
        "exam.score.count.time",
        "exam.score.formula.osym_4_1",
        "exam.score.formula.osym_3_1",
        "exam.score.formula.no_penalty",
        "exam.score.formula.weighted",
        "exam.score.section.topic_breakdown",
        "exam.score.section.difficulty_breakdown",
        "exam.score.section.item_evidence",
        "exam.score.section.regrade_audits",
        "exam.score.filter.all",
        "exam.score.filter.correct",
        "exam.score.filter.wrong",
        "exam.score.filter.blank",
        "exam.score.filter.flagged",
        "exam.score.regrade.reason_label",
        "exam.score.regrade.detail_placeholder",
        "exam.score.regrade.empty",
        "exam.score.error.offline_unavailable",
        "exam.score.error.feature_disabled",
        "exam.score.error.not_scorable",
        "exam.score.error.not_found",
        "exam.score.error.regrade_invalid",
        "exam.score.error.generic",
        "exam.readiness.title",
        "exam.readiness.subtitle",
        "exam.readiness.offline_banner",
        "exam.readiness.loading",
        "exam.readiness.recompute_button",
        "exam.readiness.interval.title",
        "exam.readiness.interval.mid_label",
        "exam.readiness.interval.target_label",
        "exam.readiness.interval.gap_label",
        "exam.readiness.zero_cost_note",
        "exam.readiness.tier.preliminary",
        "exam.readiness.tier.calibrating",
        "exam.readiness.tier.established",
        "exam.readiness.tier.high_confidence",
        "exam.readiness.preliminary.title",
        "exam.readiness.preliminary.body",
        "exam.readiness.preliminary.cta",
        "exam.readiness.traps.title",
        "exam.readiness.traps.empty",
        "exam.readiness.simulator.title",
        "exam.readiness.simulator.trap_slider",
        "exam.readiness.simulator.hours_slider",
        "exam.readiness.simulator.projected_label",
        "exam.readiness.sections.title",
        "exam.readiness.roi.title",
        "exam.readiness.rationale.active_trap_mitigation",
        "exam.readiness.rationale.retention_decay_recovery",
        "exam.readiness.rationale.high_blueprint_weight_low_mastery",
        "exam.readiness.rationale.calibration_evidence_gap",
        "exam.readiness.open_from_hub",
        "exam.readiness.open_from_score",
        "exam.readiness.error.offline_unavailable",
        "exam.readiness.error.feature_disabled",
        "exam.readiness.error.generic",
        "exam.preExam.title",
        "exam.preExam.openHint",
        "exam.preExam.generic",
        "exam.preExam.offline",
        "exam.preExam.share",
        "exam.preExam.shareHint",
        "exam.preExam.retry",
        "exam.preExam.error",
        "exam.preExam.cacheError",
        "exam.preExam.disabled",
        "exam.preExam.signIn",
        "exam.preExam.exportEventError",
        "exam.preExam.pdfError",
        "exam.preExam.time.DATE_ONLY_ESTIMATE",
        "exam.preExam.time.NO_TARGET_DATE",
        "exam.preExam.time.EXAM_PASSED",
        "exam.preExam.time.ACTIVE_48H_WINDOW",
        "exam.preExam.time.EARLY_PREVIEW_OUTSIDE_48H",
        "exam.progress.title",
        "exam.progress.openHint",
        "exam.progress.loading",
        "exam.progress.retry",
        "exam.progress.offlineBanner",
        "exam.progress.disabled",
        "exam.progress.signIn",
        "exam.progress.unsupported",
        "exam.progress.integrityError",
        "exam.progress.cacheError",
        "exam.progress.error",
        "exam.progress.zeroCostNote",
        "exam.progress.summary.title",
        "exam.progress.summary.deltaLabel",
        "exam.progress.trajectory.title",
        "exam.progress.empty.title",
        "exam.progress.empty.body",
        "exam.progress.insufficient.oneMock",
        "exam.progress.insufficient.twoMocks",
        "exam.progress.pacing.title",
        "exam.progress.pacing.empty",
        "exam.progress.pacing.fast",
        "exam.progress.pacing.onPace",
        "exam.progress.pacing.bottleneck",
        "exam.progress.pacing.insufficientData",
        "exam.progress.pacing.insufficientDetail",
        "exam.progress.traps.title",
        "exam.progress.traps.activeNowFormat",
        "exam.progress.traps.conqueredUnavailable",
        "exam.progress.traps.nonCausalNote",
        "exam.progress.traps.status.COMPUTED",
        "exam.progress.traps.status.INSUFFICIENT_DATA",
        "exam.progress.traps.status.ZERO_VARIANCE",
        "exam.progress.traps.status.NO_HISTORICAL_ACTIVE_TRAP_DATA",
        "exam.integrity.error.clock_resync",
        "exam.integrity.error.proof_expired",
        "exam.integrity.error.retry_conflict",
        "exam.integrity.error.signature_required",
        "exam.integrity.error.verification_needed",
        "exam.integrity.status.pending_submit_recovery",
    ]

    banned_lexicon = re.compile(
        r"\b(overdue|behind|fail|failure|failed|fail[s]?|punish|penalty|weak|weakness|weaknesses|poor|bad|stupid|dumb|incompetent|deficit|flaw|hopeless|remedial|abandoned|quitter|incomplete|streak)\b",
        re.IGNORECASE,
    )
    gamification_banned_lexicon = re.compile(
        r"\b(overdue|behind|fail|failure|failed|fail[s]?|punish|penalty|shame|guilt|slacker|lazy|weak|weakness|weaknesses|stupid|dumb|incompetent|hopeless|quitter|broken streak|lost streak|you broke|lose your|hurry|last chance|kaybettin|bozuldu|ceza|utanç|tembel|başarısız|yetersiz)\b",
        re.IGNORECASE,
    )

    for key in required_keys:
        assert key in strings, f"Missing localization key: {key}"
        entry = strings[key]
        for lang in ("en", "tr"):
            loc = entry.get("localizations", {}).get(lang, {}).get("stringUnit", {})
            val = loc.get("value")
            assert val and len(val.strip()) > 0, f"Missing {lang} translation for key: {key}"
            if key.startswith("studyplan.rebalance.") or key.startswith("micro_lesson.") or key.startswith("study_session."):
                m = banned_lexicon.search(val)
                assert not m, f"Stigmatizing lexicon word '{m.group(0)}' in {key}[{lang}]: {val}"
            elif key.startswith("gamification.") or key.startswith("weekly.") or key.startswith("cold_start.") or key.startswith("exam.candidate.") or key.startswith("exam.syllabus.") or key.startswith("home.candidate_profile.") or key.startswith("exam.assembly.") or key.startswith("exam.session.") or key.startswith("exam.score.") or key.startswith("exam.readiness.") or key.startswith("exam.preExam.") or key.startswith("exam.progress.") or key.startswith("exam.integrity."):
                m = gamification_banned_lexicon.search(val)
                assert not m, f"Stigmatizing lexicon word '{m.group(0)}' in {key}[{lang}]: {val}"

    print(f"PASS: Sprint 8.1..9.6 JSON schemas, negative mutations, OpenAPI 3.1 endpoints, and {len(required_keys)} EN/TR localizations verified.")


if __name__ == "__main__":
    run_validation()
    from validate_sprint_9_7_contracts import validate
    validate()
    from validate_sprint_9_8_contracts import validate as validate_9_8
    validate_9_8()
    from validate_sprint_9_9_contracts import validate as validate_9_9
    validate_9_9()
    from validate_sprint_9_10_contracts import validate as validate_9_10
    validate_9_10()
    from validate_sprint_10_1_contracts import validate as validate_10_1
    validate_10_1()
    from validate_sprint_10_2_contracts import validate as validate_10_2
    validate_10_2()
    from validate_sprint_10_3_contracts import validate as validate_10_3
    validate_10_3()
    from validate_sprint_10_4_contracts import validate as validate_10_4
    validate_10_4()
    from validate_sprint_10_5_contracts import validate as validate_10_5
    validate_10_5()
    from validate_sprint_10_6_contracts import validate as validate_10_6
    validate_10_6()
    from validate_sprint_10_7_contracts import validate as validate_10_7
    validate_10_7()
    from validate_sprint_10_8_contracts import validate as validate_10_8
    validate_10_8()
    from validate_sprint_10_9_contracts import validate as validate_10_9
    validate_10_9()
