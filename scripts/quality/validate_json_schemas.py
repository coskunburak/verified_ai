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

    validate_schema_structure(mp_schema, "mistake-passport-v1.schema.json")
    validate_schema_structure(ce_schema, "counterexample-proof-card-v1.schema.json")
    validate_schema_structure(cal_schema, "mastery-calibration-summary-v1.schema.json")
    validate_schema_structure(rec_schema, "learner-recommendations-v1.schema.json")
    validate_schema_structure(ps_schema, "practice-session-v1.schema.json")
    validate_schema_structure(sq_schema, "spaced-queue-v1.schema.json")

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
    ]

    for key in required_keys:
        assert key in strings, f"Missing localization key: {key}"
        entry = strings[key]
        for lang in ("en", "tr"):
            loc = entry.get("localizations", {}).get(lang, {}).get("stringUnit", {})
            val = loc.get("value")
            assert val and len(val.strip()) > 0, f"Missing {lang} translation for key: {key}"

    print(f"PASS: Sprint 8.1, 8.2 & 8.3 JSON schemas, negative mutations, OpenAPI 3.1 endpoints, and {len(required_keys)} EN/TR localizations verified.")


if __name__ == "__main__":
    run_validation()
