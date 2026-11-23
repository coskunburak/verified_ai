"""Validate Sprint 10.5 lifecycle messaging schemas, OpenAPI dual mirror, SQL, config, and iOS contracts."""
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

EXPECTED_LIFECYCLE_LOCALIZATION_KEYS = (
    "notification.template.account.security_alert.body",
    "notification.template.account.security_alert.title",
    "notification.template.billing.trial_ending_48h.body",
    "notification.template.billing.trial_ending_48h.title",
    "notification.template.exam_deadline.countdown.body",
    "notification.template.exam_deadline.countdown.title",
    "notification.template.spaced_review.due.body",
    "notification.template.spaced_review.due.title",
    "notification.template.study_plan.daily_mission.body",
    "notification.template.study_plan.daily_mission.title",
    "notification.trial_optin.action.enable",
    "notification.trial_optin.action.enabled",
    "notification.trial_optin.badge.active",
    "notification.trial_optin.badge.optional",
    "notification.trial_optin.enabled_banner",
    "notification.trial_optin.hint",
    "notification.trial_optin.subtitle",
    "notification.trial_optin.title",
)

BANNED_LEXICON = re.compile(
    r"\b(overdue|behind|fail|failure|failed|fail[s]?|punish|penalty|shame|guilt|slacker|lazy|weak|weakness|weaknesses|stupid|dumb|incompetent|hopeless|quitter|broken streak|lost streak|you broke|lose your|hurry|last chance|kaybettin|bozuldu|ceza|utanç|tembel|başarısız|yetersiz)\b",
    re.IGNORECASE,
)


def _load_json(rel_path: str) -> dict:
    return json.loads((ROOT / rel_path).read_text(encoding="utf-8"))


def validate() -> None:
    schema_specs = {
        "summary": (
            "packages/schemas/notification/lifecycle-summary-v1.schema.json",
            "LifecycleSummaryResponse",
            "packages/schemas/notification/fixtures/lifecycle-summary-v1.valid.json",
        ),
        "catalog": (
            "packages/schemas/notification/notification-template-catalog-v1.schema.json",
            None,
            "packages/schemas/notification/fixtures/notification-template-catalog-v1.valid.json",
        ),
    }

    schemas: dict[str, dict] = {}
    validators: dict[str, Draft202012Validator] = {}
    fixtures: dict[str, dict] = {}

    for key, (schema_rel, _, fixture_rel) in schema_specs.items():
        schema = _load_json(schema_rel)
        assert schema.get("$schema") == "https://json-schema.org/draft/2020-12/schema", schema_rel
        assert schema.get("additionalProperties") is False, schema_rel
        assert schema["properties"]["trainingEligible"]["const"] is False, schema_rel
        assert schema["properties"]["llmTokensUsed"]["const"] == 0, schema_rel
        for def_name, def_obj in schema.get("$defs", {}).items():
            if def_obj.get("type") == "object":
                assert def_obj.get("additionalProperties") is False, (schema_rel, def_name)
        Draft202012Validator.check_schema(schema)
        val = Draft202012Validator(schema, format_checker=FormatChecker())
        fx = _load_json(fixture_rel)
        val.validate(fx)
        schemas[key] = schema
        validators[key] = val
        fixtures[key] = fx

    summary_fx = fixtures["summary"]
    decisions = {item["decision"] for item in summary_fx["recentEvaluations"]}
    assert {
        "SCHEDULED",
        "SKIPPED_ALREADY_COMPLETED",
        "SKIPPED_COOLDOWN_ACTIVE",
        "CANCELLED_OBSOLETE",
    } <= decisions

    catalog_fx = fixtures["catalog"]
    assert len(catalog_fx["templates"]) == 5

    # Negative mutations for Sprint 10.5 schemas
    for key in ("summary", "catalog"):
        bad_te = copy.deepcopy(fixtures[key])
        bad_te["trainingEligible"] = True
        assert not validators[key].is_valid(bad_te), f"trainingEligible=true accepted by {key}"

        bad_tokens = copy.deepcopy(fixtures[key])
        bad_tokens["llmTokensUsed"] = 1
        assert not validators[key].is_valid(bad_tokens), f"llmTokensUsed=1 accepted by {key}"

        bad_pii_email = copy.deepcopy(fixtures[key])
        bad_pii_email["studentEmail"] = "student@example.com"
        assert not validators[key].is_valid(bad_pii_email), f"studentEmail accepted by {key}"

        bad_pii_token = copy.deepcopy(fixtures[key])
        bad_pii_token["deviceToken"] = "abcdef0123456789"
        assert not validators[key].is_valid(bad_pii_token), f"deviceToken accepted by {key}"

    bad_decision = copy.deepcopy(summary_fx)
    bad_decision["recentEvaluations"][0]["decision"] = "SPAM_BLAST"
    assert not validators["summary"].is_valid(bad_decision)

    bad_missing_user = copy.deepcopy(summary_fx)
    del bad_missing_user["userId"]
    assert not validators["summary"].is_valid(bad_missing_user)

    bad_deep_link = copy.deepcopy(catalog_fx)
    bad_deep_link["templates"][0]["deepLinkPath"] = "https://evil.example/phish"
    assert not validators["catalog"].is_valid(bad_deep_link)

    bad_missing_templates = copy.deepcopy(catalog_fx)
    del bad_missing_templates["templates"]
    assert not validators["catalog"].is_valid(bad_missing_templates)

    # Dual OpenAPI byte-for-byte mirror & lifecycle-summary contract parity
    public_path = ROOT / "packages/contracts/openapi/public-api.yaml"
    core_path = ROOT / "packages/openapi/verified-ai-core-api.yaml"
    assert public_path.read_bytes() == core_path.read_bytes(), "OpenAPI dual mirrors must be byte-for-byte identical"

    spec = yaml.safe_load(public_path.read_text(encoding="utf-8"))
    for prefix in ("/v1", "/api/v1"):
        full_path = prefix + "/notifications/lifecycle-summary"
        assert full_path in spec["paths"], full_path
        get_op = spec["paths"][full_path]["get"]
        assert "Cache-Control" in get_op["responses"]["200"]["headers"], full_path
        assert (
            get_op["responses"]["200"]["content"]["application/json"]["schema"]["$ref"]
            == "#/components/schemas/LifecycleSummaryResponse"
        ), full_path

    published_summary = {k: v for k, v in schemas["summary"].items() if k not in ("$schema", "$id")}
    assert spec["components"]["schemas"]["LifecycleSummaryResponse"] == published_summary

    # Migrations V065..V069 + V069 DDL tokens
    mig_dir = ROOT / "services/api/src/main/resources/db/migration/platform"
    for migration in (
        "V065__sprint_10_1_paywall_offer_catalog.sql",
        "V066__sprint_10_2_entitlement_state_and_transition_audit.sql",
        "V067__sprint_10_3_entitlement_quota_ledgers.sql",
        "V068__sprint_10_4_notification_infrastructure_and_preferences.sql",
        "V069__sprint_10_5_lifecycle_messaging_campaigns.sql",
    ):
        assert (mig_dir / migration).is_file(), migration

    v069_sql = (mig_dir / "V069__sprint_10_5_lifecycle_messaging_campaigns.sql").read_text(encoding="utf-8")
    for token in (
        "lifecycle_messaging_cooldowns",
        "lifecycle_messaging_evaluations",
        "CHECK (NOT training_eligible)",
        "ON DELETE CASCADE",
        "idx_lifecycle_messaging_cooldowns_until",
        "idx_lifecycle_messaging_evaluations_user_time",
        "idx_lifecycle_messaging_evaluations_user_idem",
    ):
        assert token in v069_sql, token

    # Regression pin & no global @EnableScheduling
    mig_104_test = (
        ROOT / "services/api/src/test/java/com/verifiedai/notification/application/Sprint104NotificationMigrationTest.java"
    ).read_text(encoding="utf-8")
    assert '.target("68")' in mig_104_test, 'Sprint104NotificationMigrationTest must pin .target("68")'

    app_java = (ROOT / "services/api/src/main/java/com/verifiedai/VerifiedAiApplication.java").read_text(
        encoding="utf-8"
    )
    assert "@EnableScheduling" not in app_java, "VerifiedAiApplication must not enable global scheduling"

    # Fail-closed application.yml flags
    app_yml = yaml.safe_load((ROOT / "services/api/src/main/resources/application.yml").read_text(encoding="utf-8"))
    notif_cfg = app_yml["verifiedai"]["notification"]
    for flag in (
        "enabled",
        "dispatch-worker-enabled",
        "apns-live-delivery-enabled",
        "lifecycle-campaigns-enabled",
        "lifecycle-scheduler-enabled",
    ):
        assert str(notif_cfg[flag]).endswith(":false}"), (flag, notif_cfg[flag])

    # Localizable.xcstrings EN/TR & banned lexicon checks
    strings = json.loads(
        (ROOT / "apps/ios/VerifiedAI/Resources/Localizable.xcstrings").read_text(encoding="utf-8")
    )["strings"]
    for key in EXPECTED_LIFECYCLE_LOCALIZATION_KEYS:
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
        "TrialEndReminderOptInCard.swift",
        "LifecycleDeepLinkAndOptInTests.swift",
    ):
        count = pbxproj.count(f"/* {swift_file} in Sources */")
        assert count == 2, (swift_file, count)


if __name__ == "__main__":
    validate()
    print("Sprint 10.5 contracts checked")
