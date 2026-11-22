"""Validate synthetic Sprint 10.2 contracts without billing or learner data."""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
VENV_SITE = ROOT / "services/math-verifier/.venv/lib/python3.11/site-packages"
if VENV_SITE.exists() and str(VENV_SITE) not in sys.path:
    sys.path.insert(0, str(VENV_SITE))

import yaml
from jsonschema import Draft202012Validator, FormatChecker

EXPECTED_FIXTURES = {
    "free",
    "restored",
    "canceledUntilExpiry",
    "expired",
    "revoked",
    "proActive",
    "coachActive",
    "seasonActive",
    "graceWithinWindow",
    "graceExpired",
    "billingRetryBeforeExpiry",
    "billingRetryAfterExpiry",
    "refundImmediate",
    "renewedAfterExpiry",
    "tutorAddonNotActive",
    "flagDisabled",
}


def validate():
    schema = json.loads((ROOT / "packages/schemas/entitlement-v2.schema.json").read_text(encoding="utf-8"))
    Draft202012Validator.check_schema(schema)
    check = Draft202012Validator(schema, format_checker=FormatChecker())
    cases = json.loads((ROOT / "packages/schemas/fixtures/entitlement-v2.cases.json").read_text(encoding="utf-8"))
    assert EXPECTED_FIXTURES <= set(cases.keys()), set(EXPECTED_FIXTURES) - set(cases.keys())

    for name, case in cases.items():
        check.validate(case)
        if case["effectiveTier"] == "FREE":
            assert case["capabilities"] == ["BASIC_SOLVE"], name
            assert case["historyAccess"]["priorExams"] == "SUMMARY", name
            assert case["paidAccessActive"] is False, name
        if case["status"] in ("EXPIRED", "REVOKED"):
            assert case["effectiveTier"] == "FREE", name
            assert case["packageCode"] == "FREE", name
            assert case["capabilities"] == ["BASIC_SOLVE"], name
            assert case["learningOsCapabilities"] == ["ATTEMPT_VERIFY", "DETERMINISTIC_TWIN"], name

    negative_mutations = [
        (cases["free"], {"effectiveTier": "TUTOR_COPILOT_PACK"}),
        (cases["free"], {"learningOsCapabilities": ["MICRO_LESSON"]}),
        (cases["free"], {"capabilities": ["MOCK_EXAM"]}),
        (cases["free"], {"gracePeriodExpiresAt": "invalid"}),
        (cases["free"], {"historyAccess": {}}),
        (cases["free"], {"packageCode": "PRO"}),
        (cases["free"], {"training_eligible": True}),
        (cases["free"], {"userId": "local_dev"}),
        (cases["free"], {"dailyQuota": 1}),
        (cases["free"], {"email": "synthetic@example.invalid"}),
        (cases["free"], {"jws": "synthetic"}),
        (cases["free"], {"accessEvaluatedAt": "not-a-date"}),
        (cases["free"], {"historyAccess": {"mistakePassport": "INVALID", "skillMastery": "SUMMARY", "priorExams": "SUMMARY"}}),
        (cases["graceWithinWindow"], {"gracePeriodExpiresAt": None}),
        (cases["coachActive"], {"capabilities": ["BASIC_SOLVE", "BASIC_SOLVE"]}),
        (cases["coachActive"], {"learningOsCapabilities": ["ATTEMPT_VERIFY", "ATTEMPT_VERIFY"]}),
        (cases["proActive"], {"packageCode": "YKS_SEASON_PASS"}),
        (cases["expired"], {"effectiveTier": "PRO", "paidAccessActive": True}),
        (cases["flagDisabled"], {"learningOsCapabilities": ["HYBRID_TWIN"]}),
    ]
    for base, change in negative_mutations:
        assert not check.is_valid(base | change), (base.get("packageCode"), change)

    published = {k: v for k, v in schema.items() if k not in ("$schema", "$id")}
    paths = []
    specs = []
    for file in ("packages/contracts/openapi/public-api.yaml", "packages/openapi/verified-ai-core-api.yaml"):
        spec = yaml.safe_load((ROOT / file).read_text(encoding="utf-8"))
        specs.append(spec)
        assert spec["components"]["schemas"]["EntitlementV2"] == published, file
        assert "/api/v1/me/entitlements" in spec["paths"], file
        me_schema = spec["paths"]["/api/v1/me/entitlements"]["get"]["responses"]["200"]["content"]["application/json"]["schema"]
        assert me_schema == {"$ref": "#/components/schemas/EntitlementResponse"}, file
        ent_resp = spec["components"]["schemas"]["EntitlementResponse"]
        assert ent_resp["properties"]["tier"]["enum"] == ["FREE", "PRO", "PRO_PLUS"], file
        for prefix in ("/v1", "/api/v1"):
            operation = spec["paths"][prefix + "/billing/entitlements"]["get"]
            assert operation["security"] == [{"bearerAuth": []}]
            assert set(operation["responses"]) >= {"200", "401", "403", "503"}
            assert "Cache-Control" in operation["responses"]["200"]["headers"]
            paths.append(operation)
    assert all(path == paths[0] for path in paths)
    assert specs[0]["paths"]["/api/v1/me/entitlements"] == specs[1]["paths"]["/api/v1/me/entitlements"]

    app_yml = (ROOT / "services/api/src/main/resources/application.yml").read_text(encoding="utf-8")
    assert "    learning-os-tiers-enabled: false" in app_yml

    strings = json.loads((ROOT / "apps/ios/VerifiedAI/Resources/Localizable.xcstrings").read_text(encoding="utf-8"))["strings"]
    for key in ("billing.historyRetained", "billing.verifyAccess", "billing.retry", "billing.grace", "billing.resumeExam"):
        assert {"en", "tr"} <= strings[key]["localizations"].keys(), key

    project = (ROOT / "apps/ios/VerifiedAI.xcodeproj/project.pbxproj").read_text(encoding="utf-8")
    for name in (
        "CapabilityGate",
        "EntitlementAPI",
        "EntitlementViewModel",
        "EntitlementViewModelTests",
        "LearningOsCapabilityGateTests",
        "LearningOsEntitlementV2DecoderTests",
        "LearningOsEntitlementRefreshTests",
        "LearningOsDowngradePresentationTests",
    ):
        assert project.count(name + ".swift in Sources") == 2, name
    assert project.count("Sprint102.storekit in Resources") == 2


if __name__ == "__main__":
    validate()
    print("Sprint 10.2 contracts checked")
