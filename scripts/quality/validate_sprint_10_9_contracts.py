#!/usr/bin/env python3
"""Sprint 10.9 static contract, schema, OpenAPI dual-mirror, migration, backend, and iOS validator."""

from __future__ import annotations

import copy
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
VENV_SITE = ROOT / "services" / "math-verifier" / ".venv" / "lib" / "python3.11" / "site-packages"
if VENV_SITE.exists() and str(VENV_SITE) not in sys.path:
    sys.path.insert(0, str(VENV_SITE))

from jsonschema import Draft202012Validator, FormatChecker, ValidationError  # noqa: E402
import yaml  # noqa: E402

GROWTH_SCHEMA_DIR = ROOT / "packages" / "schemas" / "growth"
FIXTURES_DIR = GROWTH_SCHEMA_DIR / "fixtures"

SCHEMA_FIXTURE_PAIRS = [
    ("share-artifact-create-request-v1.schema.json", "share-artifact-create-request-v1.valid.json"),
    ("share-artifact-v1.schema.json", "share-artifact-v1.valid.json"),
    ("public-share-artifact-v1.schema.json", "public-share-artifact-v1.valid.json"),
    ("referral-summary-v1.schema.json", "referral-summary-v1.valid.json"),
    ("referral-attribute-request-v1.schema.json", "referral-attribute-request-v1.valid.json"),
    ("referral-attribution-result-v1.schema.json", "referral-attribution-result-v1.valid.json"),
    ("store-review-eligibility-v1.schema.json", "store-review-eligibility-v1.valid.json"),
    ("store-review-event-request-v1.schema.json", "store-review-event-request-v1.valid.json"),
]

EXPECTED_OPENAPI_PATHS = [
    ("/v1/growth/shares", "post"),
    ("/api/v1/growth/shares", "post"),
    ("/v1/growth/shares", "get"),
    ("/api/v1/growth/shares", "get"),
    ("/v1/growth/shares/{shareId}/revoke", "post"),
    ("/api/v1/growth/shares/{shareId}/revoke", "post"),
    ("/v1/growth/shares/public/{shareToken}", "get"),
    ("/api/v1/growth/shares/public/{shareToken}", "get"),
    ("/v1/growth/referrals/me", "get"),
    ("/api/v1/growth/referrals/me", "get"),
    ("/v1/growth/referrals/attribute", "post"),
    ("/api/v1/growth/referrals/attribute", "post"),
    ("/v1/growth/referrals/qualify", "post"),
    ("/api/v1/growth/referrals/qualify", "post"),
    ("/v1/growth/review-prompts/eligibility", "get"),
    ("/api/v1/growth/review-prompts/eligibility", "get"),
    ("/v1/growth/review-prompts/events", "post"),
    ("/api/v1/growth/review-prompts/events", "post"),
]

EXPECTED_OPENAPI_SCHEMAS = [
    "CreateShareArtifactRequest",
    "ShareableArtifactResponse",
    "ShareableArtifactListResponse",
    "PublicShareArtifactResponse",
    "ReferralSummaryResponse",
    "ReferralAttributeRequest",
    "ReferralAttributionResultResponse",
    "StoreReviewEligibilityResponse",
    "StoreReviewEventRequest",
    "StoreReviewEventResponse",
]

BANNED_STIGMATIZING_TERMS = [
    "overdue",
    "behind",
    "fail",
    "failure",
    "failed",
    "punish",
    "penalty",
    "shame",
    "guilt",
    "slacker",
    "lazy",
    "weak",
    "weakness",
    "stupid",
    "dumb",
    "incompetent",
    "hopeless",
    "quitter",
    "broken streak",
    "lost streak",
    "hurry",
    "last chance",
    "kaybettin",
    "bozuldu",
    "ceza",
    "utanç",
    "tembel",
    "başarısız",
    "yetersiz",
]

EXPECTED_SWIFT_FILES = [
    "GrowthModels.swift",
    "GrowthServicing.swift",
    "GrowthAPI.swift",
    "StoreKitReviewPrompter.swift",
    "FixtureGrowthAPI.swift",
    "GrowthSharingViewModel.swift",
    "GrowthShareAndReferralView.swift",
    "SharedArtifactPreviewSheet.swift",
    "GrowthLoopsAndDeepLinkTests.swift",
]


def _load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def _assert_rejected(validator: Draft202012Validator, payload: dict, label: str) -> None:
    try:
        validator.validate(payload)
    except ValidationError:
        return
    raise AssertionError(f"Expected ValidationError for negative mutation: {label}")


def validate_schemas_and_fixtures() -> None:
    format_checker = FormatChecker()
    for schema_name, fixture_name in SCHEMA_FIXTURE_PAIRS:
        schema_path = GROWTH_SCHEMA_DIR / schema_name
        fixture_path = FIXTURES_DIR / fixture_name
        assert schema_path.is_file(), f"Missing schema: {schema_path}"
        assert fixture_path.is_file(), f"Missing fixture: {fixture_path}"

        schema = _load_json(schema_path)
        assert schema.get("$schema") == "https://json-schema.org/draft/2020-12/schema", (
            f"{schema_name} must be Draft 2020-12"
        )
        assert schema.get("additionalProperties") is False, f"{schema_name} must set additionalProperties=false"
        Draft202012Validator.check_schema(schema)
        validator = Draft202012Validator(schema, format_checker=format_checker)

        fixture = _load_json(fixture_path)
        validator.validate(fixture)

        mutated_train = copy.deepcopy(fixture)
        mutated_train["trainingEligible"] = True
        _assert_rejected(validator, mutated_train, f"{schema_name}:trainingEligible=true")

        mutated_extra = copy.deepcopy(fixture)
        mutated_extra["unexpectedExtra"] = True
        _assert_rejected(validator, mutated_extra, f"{schema_name}:unexpectedExtra")

        first_required = schema["required"][0]
        mutated_missing = copy.deepcopy(fixture)
        mutated_missing.pop(first_required, None)
        _assert_rejected(validator, mutated_missing, f"{schema_name}:missing:{first_required}")

        if "llmTokensUsed" in fixture:
            mutated_llm = copy.deepcopy(fixture)
            mutated_llm["llmTokensUsed"] = 1
            _assert_rejected(validator, mutated_llm, f"{schema_name}:llmTokensUsed=1")

        if schema_name == "public-share-artifact-v1.schema.json":
            mutated_unredacted = copy.deepcopy(fixture)
            mutated_unredacted["rawContentRedacted"] = False
            _assert_rejected(validator, mutated_unredacted, f"{schema_name}:rawContentRedacted=false")

            mutated_uid = copy.deepcopy(fixture)
            mutated_uid["userId"] = "11111111-2222-3333-4444-555555555555"
            _assert_rejected(validator, mutated_uid, f"{schema_name}:injected_userId")

            mutated_raw = copy.deepcopy(fixture)
            mutated_raw["summaryPayload"]["rawProblemText"] = "2x + 3 = 7"
            _assert_rejected(validator, mutated_raw, f"{schema_name}:injected_rawProblemText")

        if schema_name == "referral-attribute-request-v1.schema.json":
            mutated_code = copy.deepcopy(fixture)
            mutated_code["referralCode"] = "bad_0O1I"
            _assert_rejected(validator, mutated_code, f"{schema_name}:invalid_referralCode")

        if schema_name == "store-review-event-request-v1.schema.json":
            mutated_trigger = copy.deepcopy(fixture)
            mutated_trigger["triggerMoment"] = "RANDOM_POPUP"
            _assert_rejected(validator, mutated_trigger, f"{schema_name}:invalid_triggerMoment")


def validate_openapi_dual_mirrors() -> None:
    public_path = ROOT / "packages" / "contracts" / "openapi" / "public-api.yaml"
    core_path = ROOT / "packages" / "openapi" / "verified-ai-core-api.yaml"
    public_bytes = public_path.read_bytes()
    core_bytes = core_path.read_bytes()
    assert public_bytes == core_bytes, "OpenAPI dual mirrors public-api.yaml and verified-ai-core-api.yaml diverged"

    spec = yaml.safe_load(public_bytes.decode("utf-8"))
    paths = spec.get("paths", {})
    assert len(EXPECTED_OPENAPI_PATHS) == 18
    for path, method in EXPECTED_OPENAPI_PATHS:
        assert path in paths, f"Missing Sprint 10.9 path in OpenAPI: {path}"
        op = paths[path].get(method)
        assert op is not None, f"Missing method {method.upper()} on {path}"
        if path.endswith("/shares/public/{shareToken}"):
            assert op.get("security") == [], f"Public share path must declare security: [] on {path}"
        else:
            assert op.get("security") == [{"bearerAuth": []}], f"Protected path must require bearerAuth on {path}"
        responses = op.get("responses", {})
        success_codes = [c for c in ("200", "201") if c in responses]
        assert success_codes, f"Missing 200/201 response on {method.upper()} {path}"
        for code in success_codes:
            headers = responses[code].get("headers", {})
            assert "Cache-Control" in headers, f"Missing Cache-Control header on {method.upper()} {path} {code}"

    schemas = spec.get("components", {}).get("schemas", {})
    for schema_name in EXPECTED_OPENAPI_SCHEMAS:
        assert schema_name in schemas, f"Missing OpenAPI component schema: {schema_name}"


def validate_migrations_and_pins() -> None:
    mig_dir = ROOT / "services" / "api" / "src" / "main" / "resources" / "db" / "migration" / "platform"
    for num in range(65, 74):
        matches = list(mig_dir.glob(f"V0{num}__*.sql"))
        assert matches, f"Missing migration V0{num} in {mig_dir}"

    v073_path = mig_dir / "V073__sprint_10_9_organic_growth_sharing_referrals_and_review_hooks.sql"
    assert v073_path.is_file(), f"Missing {v073_path}"
    v073 = v073_path.read_text(encoding="utf-8")

    for token in (
        "CREATE TABLE growth_referral_codes",
        "CREATE TABLE growth_referral_attributions",
        "CREATE TABLE growth_shareable_artifacts",
        "CREATE TABLE growth_store_review_events",
        "CHECK (NOT training_eligible)",
        "ON DELETE CASCADE",
        "ck_grw_ref_attr_no_self",
        "ck_grw_share_redacted_mandatory",
    ):
        assert token in v073, f"Missing DDL token '{token}' in V073"

    mig_107 = (
        ROOT / "services" / "api" / "src" / "test" / "java" / "com" / "verifiedai"
        / "featureflags" / "application" / "Sprint107FeatureFlagMigrationTest.java"
    ).read_text(encoding="utf-8")
    assert '.target("71")' in mig_107, "Sprint107FeatureFlagMigrationTest must remain pinned at .target(\"71\")"

    mig_108 = (
        ROOT / "services" / "api" / "src" / "test" / "java" / "com" / "verifiedai"
        / "admin" / "application" / "Sprint108AdminMigrationTest.java"
    ).read_text(encoding="utf-8")
    assert '.target("72")' in mig_108, "Sprint108AdminMigrationTest must remain pinned at .target(\"72\")"

    mig_109 = (
        ROOT / "services" / "api" / "src" / "test" / "java" / "com" / "verifiedai"
        / "growth" / "application" / "Sprint109GrowthMigrationTest.java"
    ).read_text(encoding="utf-8")
    assert '.target("72")' in mig_109 and '.target("73")' in mig_109, (
        "Sprint109GrowthMigrationTest must test .target(\"72\") -> .target(\"73\") upgrade"
    )


def validate_backend_invariants() -> None:
    app_java = (
        ROOT / "services" / "api" / "src" / "main" / "java" / "com" / "verifiedai" / "VerifiedAiApplication.java"
    ).read_text(encoding="utf-8")
    assert "@EnableScheduling" not in app_java, "VerifiedAiApplication must never declare @EnableScheduling"

    growth_main_dir = ROOT / "services" / "api" / "src" / "main" / "java" / "com" / "verifiedai" / "growth"
    assert growth_main_dir.is_dir(), f"Missing module directory: {growth_main_dir}"
    for java_file in growth_main_dir.rglob("*.java"):
        text = java_file.read_text(encoding="utf-8")
        assert "@Autowired" not in text, f"Field injection (@Autowired) prohibited in {java_file}"

    app_yml = (ROOT / "services" / "api" / "src" / "main" / "resources" / "application.yml").read_text(
        encoding="utf-8"
    )
    for snippet in (
        "growth:",
        "VERIFIEDAI_GROWTH_SHARING_ENABLED:false}",
        "VERIFIEDAI_GROWTH_REFERRAL_ENABLED:false}",
        "VERIFIEDAI_GROWTH_STORE_REVIEW_ENABLED:false}",
    ):
        assert snippet in app_yml, f"Missing fail-closed growth config snippet in application.yml: {snippet}"


def validate_ios_localization_and_pbxproj() -> None:
    xcstrings_path = ROOT / "apps" / "ios" / "VerifiedAI" / "Resources" / "Localizable.xcstrings"
    catalog = json.loads(xcstrings_path.read_text(encoding="utf-8"))
    strings = catalog.get("strings", {})
    growth_keys = [k for k in strings if k.startswith("growth.")]
    assert len(growth_keys) >= 40, f"Expected >= 40 growth.* localization keys, found {len(growth_keys)}"
    for prefix in ("growth.share.", "growth.referral.", "growth.review."):
        assert any(k.startswith(prefix) for k in growth_keys), f"Missing localization prefix {prefix}"

    for key in growth_keys:
        locs = strings[key].get("localizations", {})
        for lang in ("en", "tr"):
            value = locs.get(lang, {}).get("stringUnit", {}).get("value", "")
            assert value.strip(), f"Empty {lang} translation for {key}"
            lowered = value.lower()
            for banned in BANNED_STIGMATIZING_TERMS:
                assert banned not in lowered, f"Banned stigmatizing term '{banned}' found in {key}[{lang}]"

    pbxproj = (
        ROOT / "apps" / "ios" / "VerifiedAI.xcodeproj" / "project.pbxproj"
    ).read_text(encoding="utf-8")
    for filename in EXPECTED_SWIFT_FILES:
        marker = f"/* {filename} in Sources */"
        count = pbxproj.count(marker)
        assert count == 2, f"Expected count == 2 for '{marker}' in project.pbxproj, got {count}"


def validate() -> None:
    validate_schemas_and_fixtures()
    validate_openapi_dual_mirrors()
    validate_migrations_and_pins()
    validate_backend_invariants()
    validate_ios_localization_and_pbxproj()
    print(
        "validate_sprint_10_9_contracts: OK "
        "(8 schemas + negative mutations, 18 OpenAPI paths dual-mirror, V073, pins, growth.*, pbxproj)"
    )


if __name__ == "__main__":
    validate()
