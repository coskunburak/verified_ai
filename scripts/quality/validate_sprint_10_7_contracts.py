#!/usr/bin/env python3
"""Sprint 10.7 static contract, schema, OpenAPI dual-mirror, migration, and iOS validator."""

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

FEATUREFLAGS_SCHEMA_DIR = ROOT / "packages" / "schemas" / "featureflags"
FIXTURES_DIR = FEATUREFLAGS_SCHEMA_DIR / "fixtures"

SCHEMA_FIXTURE_PAIRS = [
    ("remote-config-v1.schema.json", "remote-config-v1.valid.json"),
    ("feature-flag-list-v1.schema.json", "feature-flag-list-v1.valid.json"),
    ("feature-flag-update-request-v1.schema.json", "feature-flag-update-request-v1.valid.json"),
    ("feature-flag-kill-switch-request-v1.schema.json", "feature-flag-kill-switch-request-v1.valid.json"),
    ("experiment-update-request-v1.schema.json", "experiment-update-request-v1.valid.json"),
    ("feature-flag-audit-list-v1.schema.json", "feature-flag-audit-list-v1.valid.json"),
]

CANONICAL_FLAG_KEYS = [
    "secondary_solver_enabled",
    "advanced_verification_enabled",
    "tutor_v2_enabled",
    "adaptive_plan_v2_enabled",
    "pdf_upload_enabled",
    "verification_method_false_positive_kill_switch",
    "strong_model_pct",
    "proprietary_canary_pct",
    "daily_solve_quota_override",
    "client_platform_health_screen",
    "client_mistake_moment_paywall_ui",
]

CANONICAL_EXPERIMENT_KEYS = [
    "EXP-LOS-01",
    "EXP-LOS-02",
    "EXP-LOS-08",
    "EXP-LOS-09",
]

EXPECTED_OPENAPI_PATHS = [
    "/v1/config/remote",
    "/api/v1/config/remote",
    "/v1/admin/feature-flags",
    "/api/v1/admin/feature-flags",
    "/v1/admin/feature-flags/{flagKey}",
    "/api/v1/admin/feature-flags/{flagKey}",
    "/v1/admin/feature-flags/{flagKey}/kill-switch",
    "/api/v1/admin/feature-flags/{flagKey}/kill-switch",
    "/v1/admin/feature-flags/experiments",
    "/api/v1/admin/feature-flags/experiments",
    "/v1/admin/feature-flags/experiments/{experimentKey}",
    "/api/v1/admin/feature-flags/experiments/{experimentKey}",
    "/v1/admin/feature-flags/audit",
    "/api/v1/admin/feature-flags/audit",
]

EXPECTED_OPENAPI_SCHEMAS = [
    "RemoteConfigResponse",
    "FeatureFlagListResponse",
    "FeatureFlagUpdateRequest",
    "FeatureFlagKillSwitchRequest",
    "ExperimentUpdateRequest",
    "FeatureFlagAuditListResponse",
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
    "RemoteConfigModels.swift",
    "RemoteFeatureFlagAPI.swift",
    "RemoteFeatureFlagCacheStore.swift",
    "RemoteFeatureFlagClient.swift",
    "RemoteFeatureFlagClientTests.swift",
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
        schema_path = FEATUREFLAGS_SCHEMA_DIR / schema_name
        fixture_path = FIXTURES_DIR / fixture_name
        assert schema_path.is_file(), f"Missing schema: {schema_path}"
        assert fixture_path.is_file(), f"Missing fixture: {fixture_path}"

        schema = _load_json(schema_path)
        Draft202012Validator.check_schema(schema)
        validator = Draft202012Validator(schema, format_checker=format_checker)

        fixture = _load_json(fixture_path)
        validator.validate(fixture)

        # Common negative mutations across all 6 schemas
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

        if "rolloutBasisPoints" in fixture:
            mutated_rollout = copy.deepcopy(fixture)
            mutated_rollout["rolloutBasisPoints"] = 10001
            _assert_rejected(validator, mutated_rollout, f"{schema_name}:rolloutBasisPoints=10001")

        if "reason" in fixture:
            mutated_reason = copy.deepcopy(fixture)
            mutated_reason["reason"] = "ok"
            _assert_rejected(validator, mutated_reason, f"{schema_name}:reason=ok")

        if schema_name == "remote-config-v1.schema.json":
            mutated_leak = copy.deepcopy(fixture)
            mutated_leak["flags"].append(
                {
                    "flagKey": "secondary_solver_enabled",
                    "category": "KILL_SWITCH",
                    "valueType": "BOOLEAN",
                    "enabled": False,
                    "killSwitchActive": False,
                    "reason": "FLAG_DISABLED",
                }
            )
            _assert_rejected(validator, mutated_leak, f"{schema_name}:server_only_flag_leak")

        if schema_name == "feature-flag-list-v1.schema.json":
            mutated_item_rollout = copy.deepcopy(fixture)
            mutated_item_rollout["flags"][0]["rolloutBasisPoints"] = 10001
            _assert_rejected(validator, mutated_item_rollout, f"{schema_name}:flag.rolloutBasisPoints=10001")

            mutated_item_train = copy.deepcopy(fixture)
            mutated_item_train["experiments"][0]["trainingEligible"] = True
            _assert_rejected(validator, mutated_item_train, f"{schema_name}:experiment.trainingEligible=true")

        if schema_name == "feature-flag-audit-list-v1.schema.json":
            mutated_entry_reason = copy.deepcopy(fixture)
            mutated_entry_reason["entries"][0]["reason"] = "ok"
            _assert_rejected(validator, mutated_entry_reason, f"{schema_name}:entry.reason=ok")

            mutated_entry_train = copy.deepcopy(fixture)
            mutated_entry_train["entries"][0]["trainingEligible"] = True
            _assert_rejected(validator, mutated_entry_train, f"{schema_name}:entry.trainingEligible=true")


def validate_openapi_dual_mirrors() -> None:
    public_path = ROOT / "packages" / "contracts" / "openapi" / "public-api.yaml"
    core_path = ROOT / "packages" / "openapi" / "verified-ai-core-api.yaml"
    public_bytes = public_path.read_bytes()
    core_bytes = core_path.read_bytes()
    assert public_bytes == core_bytes, "OpenAPI dual mirrors public-api.yaml and verified-ai-core-api.yaml diverged"

    spec = yaml.safe_load(public_bytes.decode("utf-8"))
    paths = spec.get("paths", {})
    for path in EXPECTED_OPENAPI_PATHS:
        assert path in paths, f"Missing Sprint 10.7 path in OpenAPI: {path}"

    for remote_path in ("/v1/config/remote", "/api/v1/config/remote"):
        responses = paths[remote_path]["get"]["responses"]
        assert "200" in responses and "304" in responses, f"Missing 200/304 responses on {remote_path}"
        headers_200 = responses["200"].get("headers", {})
        assert "Cache-Control" in headers_200 and "ETag" in headers_200, (
            f"Missing Cache-Control/ETag headers on {remote_path} 200 response"
        )
        headers_304 = responses["304"].get("headers", {})
        assert "Cache-Control" in headers_304 and "ETag" in headers_304, (
            f"Missing Cache-Control/ETag headers on {remote_path} 304 response"
        )

    schemas = spec.get("components", {}).get("schemas", {})
    for schema_name in EXPECTED_OPENAPI_SCHEMAS:
        assert schema_name in schemas, f"Missing OpenAPI component schema: {schema_name}"


def validate_migrations_and_pins() -> None:
    mig_dir = ROOT / "services" / "api" / "src" / "main" / "resources" / "db" / "migration" / "platform"
    for num in range(65, 72):
        matches = list(mig_dir.glob(f"V0{num}__*.sql"))
        assert matches, f"Missing migration V0{num} in {mig_dir}"

    v071_path = mig_dir / "V071__sprint_10_7_feature_flags_remote_config_and_experiments.sql"
    assert v071_path.is_file(), f"Missing {v071_path}"
    v071 = v071_path.read_text(encoding="utf-8")

    for table in (
        "feature_flag_definitions",
        "experiment_definitions",
        "experiment_assignments",
        "feature_flag_audit_logs",
    ):
        assert f"CREATE TABLE {table}" in v071, f"Missing table {table} in V071"

    for ck_name in (
        "ck_ff_def_training_ineligible CHECK (NOT training_eligible)",
        "ck_exp_def_training_ineligible CHECK (NOT training_eligible)",
        "ck_exp_assign_training_ineligible CHECK (NOT training_eligible)",
        "ck_ff_audit_training_ineligible CHECK (NOT training_eligible)",
    ):
        assert ck_name in v071, f"V071 must enforce '{ck_name}'"
    assert "ON DELETE CASCADE" in v071, "V071 must define ON DELETE CASCADE"
    for key in CANONICAL_FLAG_KEYS:
        assert f"'{key}'" in v071, f"Missing seeded canonical flag key '{key}' in V071"
    for exp_key in CANONICAL_EXPERIMENT_KEYS:
        assert f"'{exp_key}'" in v071, f"Missing seeded experiment key '{exp_key}' in V071"

    mig_106 = (
        ROOT / "services" / "api" / "src" / "test" / "java" / "com" / "verifiedai"
        / "analytics" / "application" / "Sprint106AnalyticsMigrationTest.java"
    ).read_text(encoding="utf-8")
    assert '.target("70")' in mig_106, "Sprint106AnalyticsMigrationTest must remain pinned at .target(\"70\")"

    mig_107 = (
        ROOT / "services" / "api" / "src" / "test" / "java" / "com" / "verifiedai"
        / "featureflags" / "application" / "Sprint107FeatureFlagMigrationTest.java"
    ).read_text(encoding="utf-8")
    assert '.target("70")' in mig_107 and '.target("71")' in mig_107, (
        "Sprint107FeatureFlagMigrationTest must test .target(\"70\") -> .target(\"71\") upgrade"
    )


def validate_backend_invariants() -> None:
    app_java = (
        ROOT / "services" / "api" / "src" / "main" / "java" / "com" / "verifiedai" / "VerifiedAiApplication.java"
    ).read_text(encoding="utf-8")
    assert "@EnableScheduling" not in app_java, "VerifiedAiApplication must never declare @EnableScheduling"

    ff_main_dir = ROOT / "services" / "api" / "src" / "main" / "java" / "com" / "verifiedai" / "featureflags"
    for java_file in ff_main_dir.rglob("*.java"):
        text = java_file.read_text(encoding="utf-8")
        assert "@Autowired" not in text, f"Field injection (@Autowired) prohibited in {java_file}"

    app_yml = (ROOT / "services" / "api" / "src" / "main" / "resources" / "application.yml").read_text(
        encoding="utf-8"
    )
    for snippet in (
        "featureflags:",
        "VERIFIEDAI_FEATURE_FLAGS_ENABLED:false}",
        "VERIFIEDAI_FEATURE_FLAGS_CLIENT_CONFIG_ENABLED:false}",
        "VERIFIEDAI_FEATURE_FLAGS_ADMIN_MUTATIONS_ENABLED:false}",
    ):
        assert snippet in app_yml, f"Missing fail-closed featureflags config snippet in application.yml: {snippet}"


def validate_ios_localization_and_pbxproj() -> None:
    xcstrings_path = ROOT / "apps" / "ios" / "VerifiedAI" / "Resources" / "Localizable.xcstrings"
    catalog = json.loads(xcstrings_path.read_text(encoding="utf-8"))
    strings = catalog.get("strings", {})
    for key in (
        "feature_flags.kill_switch.pdf_disabled",
        "feature_flags.remote_config.degraded_notice",
    ):
        assert key in strings, f"Missing localization key: {key}"
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
    print("validate_sprint_10_7_contracts: OK (6 schemas + negative mutations, OpenAPI dual mirror, V071, pins, iOS)")


if __name__ == "__main__":
    validate()
