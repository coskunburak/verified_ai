"""Validate synthetic Sprint 10.3 quota, AI circuit-breaker, OpenAPI, SQL, and iOS contracts."""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
VENV_SITE = ROOT / "services/math-verifier/.venv/lib/python3.11/site-packages"
if VENV_SITE.exists() and str(VENV_SITE) not in sys.path:
    sys.path.insert(0, str(VENV_SITE))

import yaml
from jsonschema import Draft202012Validator, FormatChecker

EXPECTED_SPRINT_10_2_FIXTURES = {
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

EXPECTED_SPRINT_10_3_FIXTURES = {
    "freeQuotaRemaining",
    "proCostBreakerDegraded",
    "coachProNormalUsage",
    "yksSeasonPassQuotaExhausted",
}

REQUIRED_QUOTA_LOCALIZATION_KEYS = (
    "billing.quota.title",
    "billing.quota.subtitle",
    "billing.quota.remainingLabel",
    "billing.quota.hybridLabel",
    "billing.quota.resetsAtLabel",
    "billing.quota.deterministicUnlimitedBadge",
    "billing.quota.deterministicUnlimitedDetail",
    "billing.quota.exhaustedNotice",
    "billing.quota.circuitBreakerNotice",
    "billing.quota.noOverageNote",
)


def validate() -> None:
    schema = json.loads((ROOT / "packages/schemas/entitlement-v2.schema.json").read_text(encoding="utf-8"))
    Draft202012Validator.check_schema(schema)
    validator = Draft202012Validator(schema, format_checker=FormatChecker())

    cases = json.loads((ROOT / "packages/schemas/fixtures/entitlement-v2.cases.json").read_text(encoding="utf-8"))
    all_expected = EXPECTED_SPRINT_10_2_FIXTURES | EXPECTED_SPRINT_10_3_FIXTURES
    assert all_expected <= set(cases.keys()), sorted(all_expected - set(cases.keys()))
    assert len(cases) >= 20, len(cases)

    for name, case in cases.items():
        validator.validate(case)
        if "quotaSummary" in case:
            quota = case["quotaSummary"]
            assert quota["deterministicTwinsUnlimited"] is True, name
            assert quota["aiVerificationsUsed"] >= 0, name
            assert quota["aiVerificationsLimit"] >= 0, name
            assert quota["remainingAiChecks"] >= 0, name
            assert quota["hybridTwinsUsed"] >= 0, name
            assert quota["hybridTwinsLimit"] >= 0, name

    assert cases["freeQuotaRemaining"]["quotaSummary"]["remainingAiChecks"] == 2
    assert cases["proCostBreakerDegraded"]["quotaSummary"]["costCircuitBreakerTripped"] is True
    assert cases["proCostBreakerDegraded"]["quotaSummary"]["degradedToDeterministicOnly"] is True
    assert cases["coachProNormalUsage"]["quotaSummary"]["aiVerificationsLimit"] == 400
    assert cases["yksSeasonPassQuotaExhausted"]["packageCode"] == "YKS_SEASON_PASS"
    assert cases["yksSeasonPassQuotaExhausted"]["quotaSummary"]["remainingAiChecks"] == 0
    assert cases["yksSeasonPassQuotaExhausted"]["quotaSummary"]["degradedToDeterministicOnly"] is True

    base_quota = dict(cases["freeQuotaRemaining"]["quotaSummary"])
    negative_quota_mutations = [
        {**base_quota, "deterministicTwinsUnlimited": False},
        {**base_quota, "aiVerificationsUsed": -1},
        {**base_quota, "remainingAiChecks": -1},
        {**base_quota, "periodKey": "invalid-period"},
        {**base_quota, "resetsAt": "not-a-timestamp"},
        {**base_quota, "unexpectedField": True},
        {k: v for k, v in base_quota.items() if k != "resetsAt"},
    ]
    for mutated_quota in negative_quota_mutations:
        bad_case = {**cases["freeQuotaRemaining"], "quotaSummary": mutated_quota}
        assert not validator.is_valid(bad_case), mutated_quota

    published = {k: v for k, v in schema.items() if k not in ("$schema", "$id")}
    specs = []
    for file in ("packages/contracts/openapi/public-api.yaml", "packages/openapi/verified-ai-core-api.yaml"):
        spec = yaml.safe_load((ROOT / file).read_text(encoding="utf-8"))
        specs.append(spec)
        assert spec["components"]["schemas"]["EntitlementV2"] == published, file
        assert "/api/v1/me/entitlements" in spec["paths"], file
    assert specs[0]["paths"]["/v1/billing/entitlements"] == specs[1]["paths"]["/v1/billing/entitlements"]
    assert specs[0]["paths"]["/api/v1/billing/entitlements"] == specs[1]["paths"]["/api/v1/billing/entitlements"]

    for migration in (
        "V065__sprint_10_1_paywall_offer_catalog.sql",
        "V066__sprint_10_2_entitlement_state_and_transition_audit.sql",
        "V067__sprint_10_3_entitlement_quota_ledgers.sql",
    ):
        path = ROOT / "services/api/src/main/resources/db/migration/platform" / migration
        assert path.is_file(), migration

    v067_sql = (
        ROOT / "services/api/src/main/resources/db/migration/platform/V067__sprint_10_3_entitlement_quota_ledgers.sql"
    ).read_text(encoding="utf-8")
    for required_sql in (
        "entitlement_quota_ledgers",
        "entitlement_quota_consumptions",
        "GENERATED ALWAYS AS (accumulated_ai_cost_micros / 1000000.0) STORED",
        "CHECK (NOT training_eligible)",
        "ON DELETE CASCADE",
        "uq_entitlement_quota_ledgers_user_period",
        "uq_quota_consumptions_user_idem",
    ):
        assert required_sql in v067_sql, required_sql

    app_yml = yaml.safe_load((ROOT / "services/api/src/main/resources/application.yml").read_text(encoding="utf-8"))
    billing_cfg = app_yml["verifiedai"]["billing"]
    assert billing_cfg["learning-os-tiers-enabled"] is False
    assert str(billing_cfg["quota-ledger-enabled"]).endswith(":false}")
    quotas = billing_cfg["quotas"]
    assert quotas["free"]["daily-ai-verifications-limit"] == 3
    assert quotas["free"]["daily-handwriting-diagnostics-limit"] == 1
    assert quotas["free"]["monthly-ai-cost-cap-micros"] == 80000
    assert quotas["pro"]["monthly-ai-verifications-limit"] == 150
    assert quotas["pro"]["monthly-ai-cost-cap-micros"] == 2500000
    assert quotas["coach-pro"]["monthly-ai-verifications-limit"] == 400
    assert quotas["coach-pro"]["monthly-ai-cost-cap-micros"] == 4200000
    assert quotas["yks-season-pass"]["monthly-ai-verifications-limit"] == 400
    assert quotas["yks-season-pass"]["monthly-ai-cost-cap-micros"] == 4200000

    strings = json.loads(
        (ROOT / "apps/ios/VerifiedAI/Resources/Localizable.xcstrings").read_text(encoding="utf-8")
    )["strings"]
    for key in REQUIRED_QUOTA_LOCALIZATION_KEYS:
        assert key in strings, key
        assert {"en", "tr"} <= set(strings[key]["localizations"].keys()), key
        for lang in ("en", "tr"):
            assert strings[key]["localizations"][lang]["stringUnit"]["value"].strip(), (key, lang)

    pbxproj = (ROOT / "apps/ios/VerifiedAI.xcodeproj/project.pbxproj").read_text(encoding="utf-8")
    assert pbxproj.count("LearningOsQuotaSummaryTests.swift in Sources") == 2


if __name__ == "__main__":
    validate()
    print("Sprint 10.3 contracts checked")
