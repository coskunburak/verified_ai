"""Sprint 10.1 synthetic contract and client resource checks."""
import json
from pathlib import Path

import yaml
from jsonschema import Draft202012Validator, FormatChecker

ROOT = Path(__file__).resolve().parents[2]


def validate():
    specs = [yaml.safe_load((ROOT / path).read_text()) for path in (
        "packages/contracts/openapi/public-api.yaml", "packages/openapi/verified-ai-core-api.yaml")]
    schemas = ROOT / "packages/schemas"
    for file, model, endpoint in (("billing-offers-v1", "BillingOffersV1", "offers"),
                                  ("entitlement-v2", "EntitlementV2", "entitlements")):
        schema = json.loads((schemas / f"{file}.schema.json").read_text())
        Draft202012Validator.check_schema(schema)
        validator = Draft202012Validator(schema, format_checker=FormatChecker())
        fixtures = json.loads((schemas / "fixtures" / f"{file}.cases.json").read_text())
        for fixture in fixtures.values():
            validator.validate(fixture)
        published = {k: v for k, v in schema.items() if k not in ("$schema", "$id")}
        path = f"/v1/billing/{endpoint}"
        for spec in specs:
            assert spec["components"]["schemas"][model] == published
            assert spec["paths"][path] == spec["paths"]["/api" + path] == specs[0]["paths"][path]
            assert spec["paths"][path]["get"]["security"] == [{"bearerAuth": []}]
        sample = next(iter(fixtures.values()))
        invalid = [sample | {"email": "synthetic@example.invalid"}, {}, sample | {"training_eligible": True}]
        if endpoint == "offers":
            offer = sample["offers"][0]
            invalid += [sample | {"locale": "xx"}, sample | {"offers": [offer, offer]},
                        sample | {"offers": [offer | {"price": 1}]}, sample | {"enabled": False}]
        else:
            invalid += [sample | {"legacyTier": "COACH_PRO"}, sample | {"status": "CLIENT_GRANTED"},
                        sample | {"expiresAt": "yesterday"}]
        for fixture in invalid:
            assert not validator.is_valid(fixture), fixture
    for spec in specs:
        events = "/v1/billing/interactions/{kind}"
        assert spec["paths"][events] == spec["paths"]["/api" + events]
        assert spec["paths"][events]["post"]["security"] == [{"bearerAuth": []}]
    config = (ROOT / "services/api/src/main/resources/application.yml").read_text()
    assert "\nverifiedai:\n  billing:\n    contextual-paywall-enabled: false\n" in config
    ios = ROOT / "apps/ios"
    strings = json.loads((ios / "VerifiedAI/Resources/Localizable.xcstrings").read_text())["strings"]
    for key in ("billing.terms", "billing.privacy", "billing.renewal", "billing.trial", "billing.priceUnavailable",
                "billing.plan.pro", "billing.plan.coach_pro", "billing.plan.yks_season_pass"):
        assert set(strings[key]["localizations"]) >= {"tr", "en"}
    project = (ios / "VerifiedAI.xcodeproj/project.pbxproj").read_text()
    for source in ("PaywallTriggerCoordinator.swift", "ContextualPaywallSheetView.swift"):
        assert project.count(source + " in Sources") == 2
    print("Sprint 10.1 billing contracts and resources checked")


if __name__ == "__main__":
    validate()
