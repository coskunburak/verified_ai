"""Validate synthetic Sprint 10.4 notification schemas, OpenAPI dual mirror, SQL, config, and iOS contracts."""
import copy
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
VENV_SITE = ROOT / "services/math-verifier/.venv/lib/python3.11/site-packages"
if VENV_SITE.exists() and str(VENV_SITE) not in sys.path:
    sys.path.insert(0, str(VENV_SITE))

import yaml
from jsonschema import Draft202012Validator, FormatChecker

EXPECTED_NOTIFICATION_LOCALIZATION_KEYS = (
    "notification.preferences.action.retry",
    "notification.preferences.categories.subtitle",
    "notification.preferences.categories.title",
    "notification.preferences.category.account_security.badge",
    "notification.preferences.category.account_security.subtitle",
    "notification.preferences.category.account_security.title",
    "notification.preferences.category.billing_trial.subtitle",
    "notification.preferences.category.billing_trial.title",
    "notification.preferences.category.exam_deadline.subtitle",
    "notification.preferences.category.exam_deadline.title",
    "notification.preferences.category.spaced_review.subtitle",
    "notification.preferences.category.spaced_review.title",
    "notification.preferences.category.study_plan.subtitle",
    "notification.preferences.category.study_plan.title",
    "notification.preferences.daily_cap.stepper_label",
    "notification.preferences.daily_cap.subtitle",
    "notification.preferences.daily_cap.title",
    "notification.preferences.entry.section",
    "notification.preferences.entry.subtitle",
    "notification.preferences.entry.title",
    "notification.preferences.error.conflict_reloaded",
    "notification.preferences.error.load",
    "notification.preferences.error.offline",
    "notification.preferences.error.quiet_hours_invalid",
    "notification.preferences.error.save",
    "notification.preferences.error.timezone_invalid",
    "notification.preferences.loading",
    "notification.preferences.master.hint",
    "notification.preferences.master.subtitle",
    "notification.preferences.master.title",
    "notification.preferences.notice.feature_disabled",
    "notification.preferences.permission.badge.authorized",
    "notification.preferences.permission.badge.denied",
    "notification.preferences.permission.badge.not_determined",
    "notification.preferences.permission.denied_banner",
    "notification.preferences.permission.open_settings",
    "notification.preferences.permission.open_settings_hint",
    "notification.preferences.preprompt.allow",
    "notification.preferences.preprompt.body",
    "notification.preferences.preprompt.not_now",
    "notification.preferences.preprompt.title",
    "notification.preferences.quiet_hours.end",
    "notification.preferences.quiet_hours.start",
    "notification.preferences.quiet_hours.subtitle",
    "notification.preferences.quiet_hours.title",
    "notification.preferences.save_button",
    "notification.preferences.save_hint",
    "notification.preferences.saved_banner",
    "notification.preferences.subtitle",
    "notification.preferences.timezone.label",
    "notification.preferences.title",
)

BANNED_LEXICON = re.compile(
    r"\b(overdue|behind|fail|failure|failed|fail[s]?|punish|penalty|shame|guilt|slacker|lazy|weak|weakness|weaknesses|stupid|dumb|incompetent|hopeless|quitter|broken streak|lost streak|you broke|lose your|hurry|last chance|kaybettin|bozuldu|ceza|utanç|tembel|başarısız|yetersiz)\b",
    re.IGNORECASE,
)


def _load_json(rel_path: str) -> dict:
    return json.loads((ROOT / rel_path).read_text(encoding="utf-8"))


def validate() -> None:
    schema_specs = {
        "pref": (
            "packages/schemas/notification/notification-preferences-v1.schema.json",
            "NotificationPreferencesResponse",
            [
                "packages/schemas/notification/fixtures/notification-preferences-v1.valid.json",
                "packages/schemas/notification/fixtures/notification-preferences-optin-v1.valid.json",
            ],
        ),
        "patch": (
            "packages/schemas/notification/notification-preferences-patch-request-v1.schema.json",
            "NotificationPreferencesPatchRequest",
            ["packages/schemas/notification/fixtures/notification-preferences-patch-request-v1.valid.json"],
        ),
        "dev_req": (
            "packages/schemas/notification/notification-device-register-request-v1.schema.json",
            "NotificationDeviceRegisterRequest",
            ["packages/schemas/notification/fixtures/notification-device-register-request-v1.valid.json"],
        ),
        "dev_res": (
            "packages/schemas/notification/notification-device-v1.schema.json",
            "NotificationDeviceResponse",
            ["packages/schemas/notification/fixtures/notification-device-v1.valid.json"],
        ),
        "deliv_list": (
            "packages/schemas/notification/notification-delivery-list-v1.schema.json",
            "NotificationDeliveryListResponse",
            ["packages/schemas/notification/fixtures/notification-delivery-list-v1.valid.json"],
        ),
    }

    schemas: dict[str, dict] = {}
    validators: dict[str, Draft202012Validator] = {}
    fixtures: dict[str, list[dict]] = {}

    for key, (schema_rel, _, fixture_rels) in schema_specs.items():
        schema = _load_json(schema_rel)
        assert schema.get("$schema") == "https://json-schema.org/draft/2020-12/schema", schema_rel
        assert schema.get("additionalProperties") is False, schema_rel
        assert schema["properties"]["trainingEligible"]["const"] is False, schema_rel
        for def_name, def_obj in schema.get("$defs", {}).items():
            if def_obj.get("type") == "object":
                assert def_obj.get("additionalProperties") is False, (schema_rel, def_name)
        Draft202012Validator.check_schema(schema)
        val = Draft202012Validator(schema, format_checker=FormatChecker())
        schemas[key] = schema
        validators[key] = val
        fixtures[key] = []
        for frel in fixture_rels:
            fx = _load_json(frel)
            val.validate(fx)
            fixtures[key].append(fx)

    pref_optout = fixtures["pref"][0]
    assert pref_optout["pushEnabled"] is False
    assert pref_optout["studyPlanEnabled"] is False
    assert pref_optout["spacedReviewEnabled"] is False
    assert pref_optout["examDeadlineEnabled"] is False
    assert pref_optout["billingTrialReminderEnabled"] is False
    assert pref_optout["accountSecurityEnabled"] is True
    assert pref_optout["trainingEligible"] is False

    pref_optin = fixtures["pref"][1]
    assert pref_optin["pushEnabled"] is True
    assert pref_optin["billingTrialReminderEnabled"] is True

    dev_res_fx = fixtures["dev_res"][0]
    assert "deviceToken" not in dev_res_fx
    assert len(dev_res_fx["tokenLast4"]) == 4

    deliv_fx = fixtures["deliv_list"][0]
    assert any(
        item["category"] == "BILLING_TRIAL_REMINDER" and item["intentType"] == "TRIAL_ENDING_48H_REMINDER"
        for item in deliv_fx["items"]
    )

    # Negative mutations required by Sprint 10.4 contract spec
    bad_pref_sec = copy.deepcopy(pref_optout)
    bad_pref_sec["accountSecurityEnabled"] = False
    assert not validators["pref"].is_valid(bad_pref_sec)

    bad_patch_sec = copy.deepcopy(fixtures["patch"][0])
    bad_patch_sec["accountSecurityEnabled"] = False
    assert not validators["patch"].is_valid(bad_patch_sec)

    for bad_cap in (-1, 6):
        bad_pref_cap = copy.deepcopy(pref_optout)
        bad_pref_cap["dailyCap"] = bad_cap
        assert not validators["pref"].is_valid(bad_pref_cap)

        bad_patch_cap = copy.deepcopy(fixtures["patch"][0])
        bad_patch_cap["dailyCap"] = bad_cap
        assert not validators["patch"].is_valid(bad_patch_cap)

    for key in schema_specs:
        bad_te = copy.deepcopy(fixtures[key][0])
        bad_te["trainingEligible"] = True
        assert not validators[key].is_valid(bad_te), f"trainingEligible=true accepted by {key}"

    bad_dev_token_leak = copy.deepcopy(dev_res_fx)
    bad_dev_token_leak["deviceToken"] = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    assert not validators["dev_res"].is_valid(bad_dev_token_leak)

    bad_deliv_link = copy.deepcopy(deliv_fx)
    bad_deliv_link["items"][0]["deepLinkPath"] = "https://evil.example/phish"
    assert not validators["deliv_list"].is_valid(bad_deliv_link)

    bad_deliv_cat = copy.deepcopy(deliv_fx)
    bad_deliv_cat["items"][0]["category"] = "VIRAL_SPAM"
    assert not validators["deliv_list"].is_valid(bad_deliv_cat)

    bad_deliv_status = copy.deepcopy(deliv_fx)
    bad_deliv_status["items"][0]["status"] = "UNKNOWN_STATUS"
    assert not validators["deliv_list"].is_valid(bad_deliv_status)

    bad_patch_missing_ver = copy.deepcopy(fixtures["patch"][0])
    del bad_patch_missing_ver["expectedVersion"]
    assert not validators["patch"].is_valid(bad_patch_missing_ver)

    bad_patch_neg_ver = copy.deepcopy(fixtures["patch"][0])
    bad_patch_neg_ver["expectedVersion"] = -1
    assert not validators["patch"].is_valid(bad_patch_neg_ver)

    # Dual OpenAPI byte-for-byte parity & path/schema checks
    public_path = ROOT / "packages/contracts/openapi/public-api.yaml"
    core_path = ROOT / "packages/openapi/verified-ai-core-api.yaml"
    assert public_path.read_bytes() == core_path.read_bytes(), "OpenAPI dual mirrors must be byte-for-byte identical"

    spec = yaml.safe_load(public_path.read_text(encoding="utf-8"))
    for suffix in (
        "/notifications/preferences",
        "/notifications/devices",
        "/notifications/devices/{deviceId}",
        "/notifications/deliveries",
        "/notifications/deliveries/{deliveryId}/cancel",
    ):
        assert "/v1" + suffix in spec["paths"], suffix
        assert "/api/v1" + suffix in spec["paths"], suffix
        for op in spec["paths"]["/api/v1" + suffix].values():
            success_codes = [code for code in op["responses"] if code.startswith("2")]
            assert success_codes, suffix
            for code in success_codes:
                assert "Cache-Control" in op["responses"][code]["headers"], (suffix, code)

    for key, (schema_rel, comp_name, _) in schema_specs.items():
        published = {k: v for k, v in schemas[key].items() if k not in ("$schema", "$id")}
        assert spec["components"]["schemas"][comp_name] == published, (comp_name, schema_rel)

    # Migrations V065..V068 + V068 DDL tokens
    mig_dir = ROOT / "services/api/src/main/resources/db/migration/platform"
    for migration in (
        "V065__sprint_10_1_paywall_offer_catalog.sql",
        "V066__sprint_10_2_entitlement_state_and_transition_audit.sql",
        "V067__sprint_10_3_entitlement_quota_ledgers.sql",
        "V068__sprint_10_4_notification_infrastructure_and_preferences.sql",
    ):
        assert (mig_dir / migration).is_file(), migration

    v068_sql = (mig_dir / "V068__sprint_10_4_notification_infrastructure_and_preferences.sql").read_text(
        encoding="utf-8"
    )
    for token in (
        "notification_preferences",
        "notification_devices",
        "notification_deliveries",
        "notification_delivery_events",
        "CHECK (NOT training_eligible)",
        "ON DELETE CASCADE",
        "uq_notification_deliveries_user_idem",
        "uq_notification_devices_active_token_sha256",
        "idx_notification_deliveries_due_dispatch",
    ):
        assert token in v068_sql, token

    # Regression pin & no global @EnableScheduling
    quota_mig_test = (
        ROOT / "services/api/src/test/java/com/verifiedai/billing/application/Sprint103QuotaMigrationTest.java"
    ).read_text(encoding="utf-8")
    assert '.target("67")' in quota_mig_test, "Sprint103QuotaMigrationTest must pin .target(\"67\")"

    app_java = (ROOT / "services/api/src/main/java/com/verifiedai/VerifiedAiApplication.java").read_text(
        encoding="utf-8"
    )
    assert "@EnableScheduling" not in app_java, "VerifiedAiApplication must not enable global scheduling"

    # application.yml flags default to false
    app_yml = yaml.safe_load((ROOT / "services/api/src/main/resources/application.yml").read_text(encoding="utf-8"))
    notif_cfg = app_yml["verifiedai"]["notification"]
    for flag in ("enabled", "dispatch-worker-enabled", "apns-live-delivery-enabled"):
        assert str(notif_cfg[flag]).endswith(":false}"), (flag, notif_cfg[flag])

    # Localizable.xcstrings EN/TR & banned lexicon checks
    strings = json.loads(
        (ROOT / "apps/ios/VerifiedAI/Resources/Localizable.xcstrings").read_text(encoding="utf-8")
    )["strings"]
    for key in EXPECTED_NOTIFICATION_LOCALIZATION_KEYS:
        assert key in strings, key
        locs = strings[key]["localizations"]
        assert {"en", "tr"} <= set(locs.keys()), key
        for lang in ("en", "tr"):
            val = locs[lang]["stringUnit"]["value"]
            assert val and val.strip(), (key, lang)
            match = BANNED_LEXICON.search(val)
            assert not match, f"Stigmatizing lexicon word '{match.group(0)}' in {key}[{lang}]: {val}"

    # project.pbxproj registration checks (== 2 each)
    pbxproj = (ROOT / "apps/ios/VerifiedAI.xcodeproj/project.pbxproj").read_text(encoding="utf-8")
    for swift_file in (
        "NotificationModels.swift",
        "NotificationServicing.swift",
        "NotificationAPI.swift",
        "PushPermissionManager.swift",
        "FixtureNotificationAPI.swift",
        "NotificationPreferencesViewModel.swift",
        "NotificationPreferencesView.swift",
        "NotificationPreferencesViewModelTests.swift",
    ):
        count = pbxproj.count(f"/* {swift_file} in Sources */")
        assert count == 2, (swift_file, count)


if __name__ == "__main__":
    validate()
    print("Sprint 10.4 contracts checked")
